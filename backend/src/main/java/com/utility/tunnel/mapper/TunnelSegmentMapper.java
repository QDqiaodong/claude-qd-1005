package com.utility.tunnel.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.utility.tunnel.entity.SegmentWithChildren;
import com.utility.tunnel.entity.TunnelSegment;

@Mapper
public interface TunnelSegmentMapper {

    int insert(TunnelSegment segment);

    List<TunnelSegment> findAll();

    List<TunnelSegment> findByStatus(@Param("status") String status);

    TunnelSegment selectById(@Param("id") Long id);

    /** 行级锁取舱段（封控 / 恢复 / 风险登记事务内使用，串行化封控状态判断） */
    TunnelSegment selectByIdForUpdate(@Param("id") Long id);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    SegmentWithChildren selectWithChildren(@Param("id") Long id);

    int countAll();

    int countByStatus(@Param("status") String status);
}
