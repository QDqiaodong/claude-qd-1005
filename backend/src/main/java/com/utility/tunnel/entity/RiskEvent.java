package com.utility.tunnel.entity;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 巡检异常 / 风险事件：值守人员从巡检点或附属设备登记的风险。
 *
 * <p>sourceType ∈ {PATROL, FACILITY}（巡检点 / 附属设备）；
 * severity ∈ {一般, 重要, 紧急}；
 * status ∈ {待处置, 处理中, 已解除}，只能逐级推进。</p>
 *
 * <p>status 非「已解除」即视为未解除风险：该舱段在最后一条未解除事件解除前，
 * 不得从「维护 / 停用」恢复为「运营」。解除时必须留下 resolver / resolvedAt。</p>
 *
 * <p>segCode / sourceName 等字段不由表列提供，由列表 / 详情 SQL 关联查询带出，
 * 用于页面展示「封控原因」与关联对象基本信息。</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class RiskEvent {

    private Long id;

    /** 所属舱段 id（必须与发现对象实际所在舱段一致） */
    private Long segmentId;

    /** 发现对象类型：PATROL 巡检点 / FACILITY 附属设备 */
    private String sourceType;

    /** 发现对象 id（巡检点或设备的主键） */
    private Long sourceId;

    /** 严重等级：一般 / 重要 / 紧急 */
    private String severity;

    /** 异常 / 风险描述 */
    private String description;

    /** 待处置 / 处理中 / 已解除 */
    private String status;

    /** 处置说明：推进处理中与解除时填写 */
    private String handleNote;

    /** 发现时间 */
    private LocalDateTime foundAt;

    /** 解除人 */
    private String resolver;

    /** 解除时间 */
    private LocalDateTime resolvedAt;

    private LocalDateTime createdAt;

    // ---------------- 关联展示字段（SQL 带出，非表列） ----------------

    /** 舱段编码 */
    private String segCode;

    /** 舱段名称 */
    private String segName;

    /** 舱段当前状态：运营 / 维护 / 停用 */
    private String segStatus;

    /** 发现对象展示名（点位编码 / 设备类型） */
    private String sourceName;

    /** 发现对象附属信息（巡检点位置 / 设备状态） */
    private String sourceDetail;

    /** 该舱段当前未解除事件总数：0 才允许舱段恢复运营 */
    private Integer openCount;
}
