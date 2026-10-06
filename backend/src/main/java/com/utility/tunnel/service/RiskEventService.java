package com.utility.tunnel.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.utility.tunnel.common.BizException;
import com.utility.tunnel.common.Dict;
import com.utility.tunnel.entity.Facility;
import com.utility.tunnel.entity.PatrolPoint;
import com.utility.tunnel.entity.RiskEvent;
import com.utility.tunnel.entity.TunnelSegment;
import com.utility.tunnel.mapper.FacilityMapper;
import com.utility.tunnel.mapper.PatrolPointMapper;
import com.utility.tunnel.mapper.RiskEventMapper;
import com.utility.tunnel.mapper.TunnelSegmentMapper;

/**
 * 巡检异常 / 风险事件业务。
 *
 * <p>闭环规则：</p>
 * <ul>
 *   <li>值守人员从巡检点或附属设备登记风险，必须关联舱段、发现对象、严重等级、描述、发现时间；
 *       登记一律为「待处置」，事务执行——参数非法整体回滚，不会留下半条事件。
 *       运营中舱段登记风险即同事务自动封控为「维护」。</li>
 *   <li>状态只能逐级推进：待处置 → 处理中 → 已解除；推进 / 解除带处置说明，
 *       解除时必须留下解除人及解除时间。</li>
 *   <li>舱段存在未解除事件时不得从「维护 / 停用」恢复「运营」；同一舱段有多条未解除事件时，
 *       只有最后一条解除后才能恢复。条件更新 + 行锁保证重复提交、并发请求都不会提前放行。</li>
 * </ul>
 */
@Service
public class RiskEventService {

    private final RiskEventMapper riskMapper;
    private final TunnelSegmentMapper segmentMapper;
    private final PatrolPointMapper patrolMapper;
    private final FacilityMapper facilityMapper;

    public RiskEventService(RiskEventMapper riskMapper,
                            TunnelSegmentMapper segmentMapper,
                            PatrolPointMapper patrolMapper,
                            FacilityMapper facilityMapper) {
        this.riskMapper = riskMapper;
        this.segmentMapper = segmentMapper;
        this.patrolMapper = patrolMapper;
        this.facilityMapper = facilityMapper;
    }

    public List<RiskEvent> list(Long segmentId, String status) {
        return riskMapper.search(segmentId, status);
    }

    public RiskEvent get(Long id) {
        RiskEvent e = riskMapper.selectDetailById(id);
        if (e == null) {
            throw new BizException("风险事件不存在：" + id);
        }
        return e;
    }

    /** 登记风险事件：整体一个事务，任一校验失败回滚，不会留下半条事件 */
    @Transactional
    public RiskEvent create(RiskEvent input) {
        if (input.getSegmentId() == null) {
            throw new BizException("必须关联舱段");
        }
        // 锁舱段行：与「恢复运营」事务串行，避免一边登记风险一边被恢复
        TunnelSegment segment = segmentMapper.selectByIdForUpdate(input.getSegmentId());
        if (segment == null) {
            throw new BizException("所属舱段不存在：" + input.getSegmentId());
        }
        if (input.getSourceType() == null || !Dict.isValidRiskSourceType(input.getSourceType())) {
            throw new BizException("发现对象类型不合法，应为：巡检点 / 附属设备");
        }
        if (input.getSourceId() == null) {
            throw new BizException("必须指定发现对象（巡检点或附属设备）");
        }
        // 发现对象必须存在，且必须确实挂在事件关联的舱段下
        Long objectSegmentId = resolveObjectSegmentId(input.getSourceType(), input.getSourceId());
        if (!objectSegmentId.equals(input.getSegmentId())) {
            throw new BizException("发现对象不属于所选舱段，请重新选择舱段与发现对象");
        }
        if (input.getSeverity() == null || !Dict.isValidRiskSeverity(input.getSeverity())) {
            throw new BizException("严重等级不合法，应为：一般 / 重要 / 紧急");
        }
        if (input.getDescription() == null || input.getDescription().isBlank()) {
            throw new BizException("风险描述不能为空");
        }
        if (input.getFoundAt() == null) {
            throw new BizException("必须填写发现时间");
        }

        RiskEvent e = new RiskEvent();
        e.setSegmentId(input.getSegmentId());
        e.setSourceType(input.getSourceType());
        e.setSourceId(input.getSourceId());
        e.setSeverity(input.getSeverity());
        e.setDescription(input.getDescription().trim());
        e.setStatus(Dict.RISK_STATUS_OPEN);
        e.setFoundAt(input.getFoundAt());
        riskMapper.insert(e);
        // 运营中的舱段登记风险即自动封控为「维护」：保证「有未解除风险的舱段不是运营态」恒成立。
        // 已处于维护 / 停用的舱段维持原封控状态。与事件插入同一事务，失败一起回滚。
        if ("运营".equals(segment.getStatus())) {
            segmentMapper.updateStatus(input.getSegmentId(), "维护");
        }
        return riskMapper.selectDetailById(e.getId());
    }

    /**
     * 推进状态：仅允许 待处置 → 处理中，可附处置说明。
     * 处理中只能经 {@link #resolve} 解除（必须留解除人与解除时间）；
     * 已解除为终态，重复提交一律拒绝。
     */
    @Transactional
    public RiskEvent advance(Long id, String handleNote) {
        RiskEvent locked = getForUpdate(id);
        if (Dict.RISK_STATUS_RESOLVED.equals(locked.getStatus())) {
            throw new BizException("事件已解除，不能继续推进");
        }
        if (!Dict.RISK_STATUS_OPEN.equals(locked.getStatus())) {
            throw new BizException("事件已在处理中，请通过「解除」操作关闭事件");
        }
        String note = normalizeNote(handleNote);
        int rows = riskMapper.advanceStatus(id, Dict.RISK_STATUS_OPEN, Dict.RISK_STATUS_PROCESSING, note);
        if (rows == 0) {
            // 并发：状态已被其他请求推进
            throw new BizException("事件状态已变化，请刷新后重试");
        }
        return riskMapper.selectDetailById(id);
    }

    /** 解除风险（处理中 → 已解除）：必须填写处置说明与解除人，记录解除时间 */
    @Transactional
    public RiskEvent resolve(Long id, String handleNote, String resolver) {
        RiskEvent locked = getForUpdate(id);
        if (Dict.RISK_STATUS_RESOLVED.equals(locked.getStatus())) {
            // 已解除事件重复提交：拒绝，绝不能因此误判封控可放行
            throw new BizException("该事件已解除，请勿重复提交");
        }
        if (!Dict.RISK_STATUS_PROCESSING.equals(locked.getStatus())) {
            throw new BizException("仅「处理中」事件可以解除，请先推进状态");
        }
        String note = normalizeNote(handleNote);
        if (note == null || note.isBlank()) {
            throw new BizException("解除风险必须填写处置说明");
        }
        if (resolver == null || resolver.isBlank()) {
            throw new BizException("必须填写解除人");
        }
        int rows = riskMapper.resolve(id, note, resolver.trim(), LocalDateTime.now());
        if (rows == 0) {
            throw new BizException("事件状态已变化，请刷新后重试");
        }
        return riskMapper.selectDetailById(id);
    }

    private RiskEvent getForUpdate(Long id) {
        RiskEvent e = riskMapper.selectByIdForUpdate(id);
        if (e == null) {
            throw new BizException("风险事件不存在：" + id);
        }
        return e;
    }

    /** 校验发现对象存在，并返回其实际所属舱段 id */
    private Long resolveObjectSegmentId(String sourceType, Long sourceId) {
        if (Dict.RISK_SOURCE_PATROL.equals(sourceType)) {
            PatrolPoint p = patrolMapper.selectById(sourceId);
            if (p == null) {
                throw new BizException("巡检点不存在：" + sourceId);
            }
            return p.getSegmentId();
        }
        Facility f = facilityMapper.selectById(sourceId);
        if (f == null) {
            throw new BizException("附属设备不存在：" + sourceId);
        }
        return f.getSegmentId();
    }

    private String normalizeNote(String note) {
        return note == null ? null : note.trim();
    }
}
