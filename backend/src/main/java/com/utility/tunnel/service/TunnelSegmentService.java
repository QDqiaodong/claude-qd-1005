package com.utility.tunnel.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.utility.tunnel.common.BizException;
import com.utility.tunnel.common.Dict;
import com.utility.tunnel.entity.SegmentWithChildren;
import com.utility.tunnel.entity.TunnelSegment;
import com.utility.tunnel.mapper.TunnelSegmentMapper;

/**
 * 廊体舱段业务。
 *
 * <p>核心规则：新建舱段长度 lengthM 必须大于 0；断面形状与状态需在字典范围内。</p>
 */
@Service
public class TunnelSegmentService {

    private final TunnelSegmentMapper segmentMapper;

    public TunnelSegmentService(TunnelSegmentMapper segmentMapper) {
        this.segmentMapper = segmentMapper;
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
}
