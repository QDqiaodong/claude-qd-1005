package com.utility.tunnel.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.utility.tunnel.common.BizException;
import com.utility.tunnel.common.Dict;
import com.utility.tunnel.entity.RiskEvent;
import com.utility.tunnel.entity.SegmentWithChildren;
import com.utility.tunnel.entity.TunnelSegment;
import com.utility.tunnel.mapper.RiskEventMapper;
import com.utility.tunnel.mapper.TunnelSegmentMapper;

/**
 * 廊体舱段业务。
 *
 * <p>核心规则：新建舱段长度 lengthM 必须大于 0；断面形状与状态需在字典范围内。</p>
 *
 * <p>封控闭环：风险未解除的舱段不能从「维护 / 停用」恢复为「运营」；
 * 同一舱段存在多条未解除事件时，只允许在最后一条事件解除后恢复运营。
 * 恢复事务内锁舱段行并以 LOCK IN SHARE MODE 锁定未解除事件计数，
 * 与风险登记 / 解除串行，重复提交或并发请求都无法提前放行。</p>
 */
@Service
public class TunnelSegmentService {

    private final TunnelSegmentMapper segmentMapper;
    private final RiskEventMapper riskMapper;

    public TunnelSegmentService(TunnelSegmentMapper segmentMapper, RiskEventMapper riskMapper) {
        this.segmentMapper = segmentMapper;
        this.riskMapper = riskMapper;
    }

    public List<TunnelSegment> list(String status) {
        if (status != null && !status.isBlank()) {
            return segmentMapper.findByStatus(status);
        }
        return segmentMapper.findAll();
    }

    public TunnelSegment get(Long id) {
        TunnelSegment s = segmentMapper.selectById(id);
        if (s == null) {
            throw new BizException("舱段不存在：" + id);
        }
        return s;
    }

    /** 一条 SQL 带出舱段及其全部子对象（入廊管线 / 巡检点 / 附属设备） */
    public SegmentWithChildren withChildren(Long id) {
        SegmentWithChildren s = segmentMapper.selectWithChildren(id);
        if (s == null) {
            throw new BizException("舱段不存在：" + id);
        }
        return s;
    }

    @Transactional
    public TunnelSegment create(TunnelSegment input) {
        if (input.getSegCode() == null || input.getSegCode().isBlank()) {
            throw new BizException("舱段编码不能为空");
        }
        if (input.getLengthM() == null || input.getLengthM() <= 0) {
            throw new BizException("舱段长度必须大于 0");
        }
        if (input.getShape() == null || !Dict.isValidSegmentShape(input.getShape())) {
            throw new BizException("断面形状不合法，应为：圆形 / 矩形 / 马蹄形");
        }
        if (input.getStatus() == null || !Dict.isValidSegmentStatus(input.getStatus())) {
            throw new BizException("舱段状态不合法，应为：运营 / 维护 / 停用");
        }
        TunnelSegment s = new TunnelSegment();
        s.setSegCode(input.getSegCode().trim());
        s.setSegName(input.getSegName() == null ? "" : input.getSegName().trim());
        s.setShape(input.getShape());
        s.setLengthM(input.getLengthM());
        s.setStatus(input.getStatus());
        segmentMapper.insert(s);
        return s;
    }

    /**
     * 舱段封控：置为「维护 / 停用」（风险处置期间禁止运营）。
     * 运营中的舱段发现风险时也可先封控；已是目标状态则幂等返回。
     */
    @Transactional
    public TunnelSegment seal(Long id, String targetStatus) {
        if (targetStatus == null || (!"维护".equals(targetStatus) && !"停用".equals(targetStatus))) {
            throw new BizException("封控状态不合法，应为：维护 / 停用");
        }
        TunnelSegment s = lockSegment(id);
        if (!targetStatus.equals(s.getStatus())) {
            segmentMapper.updateStatus(id, targetStatus);
            s.setStatus(targetStatus);
        }
        return s;
    }

    /**
     * 解除封控、恢复运营。
     *
     * <p>前置条件：当前为「维护 / 停用」，且舱段下未解除事件数为 0。
     * 先锁舱段行（与风险登记互斥），再用 LOCK IN SHARE MODE 锁定未解除事件计数（与风险解除互斥），
     * 保证同一舱段多条未解除事件时只有最后一条解除后才能放行；
     * 任何一条事件的重复提交都不会造成错误解封。</p>
     */
    @Transactional
    public TunnelSegment resume(Long id) {
        TunnelSegment s = lockSegment(id);
        if ("运营".equals(s.getStatus())) {
            throw new BizException("舱段当前已在运营，无需恢复");
        }
        int open = riskMapper.countOpenBySegmentForUpdate(id);
        if (open > 0) {
            RiskEvent latest = riskMapper.selectLatestOpenBySegment(id);
            String reason = latest == null ? "存在未解除的风险事件"
                    : "仍有 " + open + " 条未解除风险，最新：[" + latest.getSeverity() + "] "
                        + latest.getDescription();
            throw new BizException("舱段仍处于封控中，不能恢复运营：" + reason
                    + "（须解除全部风险事件，最后一条解除后方可恢复）");
        }
        segmentMapper.updateStatus(id, "运营");
        s.setStatus("运营");
        return s;
    }

    private TunnelSegment lockSegment(Long id) {
        TunnelSegment s = segmentMapper.selectByIdForUpdate(id);
        if (s == null) {
            throw new BizException("舱段不存在：" + id);
        }
        return s;
    }
}
