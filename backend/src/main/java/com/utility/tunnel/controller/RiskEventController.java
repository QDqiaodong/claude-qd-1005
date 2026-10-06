package com.utility.tunnel.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.utility.tunnel.common.ApiResponse;
import com.utility.tunnel.common.BizException;
import com.utility.tunnel.entity.RiskEvent;
import com.utility.tunnel.service.RiskEventService;

/** 巡检异常 / 风险事件接口：登记 → 处理中 → 已解除 闭环 */
@RestController
@RequestMapping("/api/risk-events")
public class RiskEventController {

    private final RiskEventService riskService;

    public RiskEventController(RiskEventService riskService) {
        this.riskService = riskService;
    }

    /** 列表：可按舱段、状态筛选，行内带当前封控原因所需的未解除数 */
    @GetMapping
    public ApiResponse<List<RiskEvent>> list(@RequestParam(required = false) Long segmentId,
                                             @RequestParam(required = false) String status) {
        return ApiResponse.success(riskService.list(segmentId, status));
    }

    /** 详情：含关联舱段与巡检点 / 设备的基本信息 */
    @GetMapping("/{id}")
    public ApiResponse<RiskEvent> get(@PathVariable Long id) {
        return ApiResponse.success(riskService.get(id));
    }

    /** 登记风险事件（待处置） */
    @PostMapping
    public ApiResponse<RiskEvent> create(@RequestBody RiskEvent input) {
        return ApiResponse.success("风险事件已登记，舱段封控", riskService.create(input));
    }

    /** 推进状态：待处置 → 处理中（可带处置说明） */
    @PostMapping("/{id}/advance")
    public ApiResponse<RiskEvent> advance(@PathVariable Long id,
                                          @RequestBody(required = false) Map<String, String> body) {
        String note = body == null ? null : body.get("handleNote");
        return ApiResponse.success("已推进为处理中", riskService.advance(id, note));
    }

    /** 解除风险：处理中 → 已解除，必须带处置说明 handleNote 与解除人 resolver */
    @PostMapping("/{id}/resolve")
    public ApiResponse<RiskEvent> resolve(@PathVariable Long id,
                                          @RequestBody(required = false) Map<String, String> body) {
        if (body == null) {
            throw new BizException("解除请求必须提供处置说明与解除人");
        }
        return ApiResponse.success("风险已解除",
                riskService.resolve(id, body.get("handleNote"), body.get("resolver")));
    }
}
