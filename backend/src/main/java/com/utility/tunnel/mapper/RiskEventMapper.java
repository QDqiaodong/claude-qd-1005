package com.utility.tunnel.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.utility.tunnel.entity.RiskEvent;

@Mapper
public interface RiskEventMapper {

    int insert(RiskEvent event);

    /** 关联舱段 / 巡检点 / 设备带出展示信息；可按舱段、状态筛选 */
    List<RiskEvent> search(@Param("segmentId") Long segmentId,
                           @Param("status") String status);

    /** 详情：关联查询舱段与发现对象基本信息 */
    RiskEvent selectDetailById(@Param("id") Long id);

    /** 行级锁取事件（推进 / 解除事务内使用，串行化状态流转） */
    RiskEvent selectByIdForUpdate(@Param("id") Long id);

    /**
     * 条件推进：仅当当前状态为 #{fromStatus} 时写入 #{toStatus} 与处置说明。
     * 返回 0 说明已被并发请求先推进（重复提交 / 并发抢跑），事务回滚不产生半条更新。
     */
    int advanceStatus(@Param("id") Long id,
                      @Param("fromStatus") String fromStatus,
                      @Param("toStatus") String toStatus,
                      @Param("handleNote") String handleNote);

    /**
     * 条件解除：仅「处理中」可解除，写入处置说明、解除人与解除时间。
     * 返回 0 说明不是处理中（含已解除事件的重复提交），拒绝放行。
     */
    int resolve(@Param("id") Long id,
                @Param("handleNote") String handleNote,
                @Param("resolver") String resolver,
                @Param("resolvedAt") LocalDateTime resolvedAt);

    /** 统计舱段未解除事件（LOCK IN SHARE MODE，恢复运营事务内与登记/解除串行；MySQL 8 / MariaDB 通用） */
    int countOpenBySegmentForUpdate(@Param("segmentId") Long segmentId);

    /** 取舱段最新一条未解除事件，用于封控原因 / 报错提示 */
    RiskEvent selectLatestOpenBySegment(@Param("segmentId") Long segmentId);
}
