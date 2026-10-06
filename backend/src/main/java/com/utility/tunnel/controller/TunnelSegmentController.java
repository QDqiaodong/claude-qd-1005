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
import com.utility.tunnel.entity.SegmentWithChildren;
import com.utility.tunnel.entity.TunnelSegment;
import com.utility.tunnel.service.TunnelSegmentService;

/** 廊体舱段接口 */
@RestController
@RequestMapping("/api/segments")
public class TunnelSegmentController {

    private final TunnelSegmentService segmentService;

    public TunnelSegmentController(TunnelSegmentService segmentService) {
        this.segmentService = segmentService;
    }

    @GetMapping
    public ApiResponse<List<TunnelSegment>> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(segmentService.list(status));
    }

    @GetMapping("/{id}")
    public ApiResponse<TunnelSegment> get(@PathVariable Long id) {
        return ApiResponse.success(segmentService.get(id));
    }

    /** 一条查询带出舱段及全部子对象（入廊管线 / 巡检点 / 附属设备） */
    @GetMapping("/{id}/with-children")
    public ApiResponse<SegmentWithChildren> withChildren(@PathVariable Long id) {
        return ApiResponse.success(segmentService.withChildren(id));
    }

    @PostMapping
    public ApiResponse<TunnelSegment> create(@RequestBody TunnelSegment input) {
        return ApiResponse.success("舱段已建档", segmentService.create(input));
    }

    /** 舱段封控：风险处置期间置为「维护 / 停用」 */
    @PostMapping("/{id}/seal")
    public ApiResponse<TunnelSegment> seal(@PathVariable Long id,
                                           @RequestBody(required = false) Map<String, String> body) {
        String target = body == null ? null : body.get("status");
        if (target == null || target.isBlank()) {
            throw new BizException("封控必须指定目标状态：维护 / 停用");
        }
        return ApiResponse.success("舱段已封控为「" + target + "」", segmentService.seal(id, target.trim()));
    }

    /** 解除封控、恢复运营：全部风险事件解除（最后一条解除）后才允许 */
    @PostMapping("/{id}/resume")
    public ApiResponse<TunnelSegment> resume(@PathVariable Long id) {
        return ApiResponse.success("封控已解除，舱段恢复运营", segmentService.resume(id));
    }
}
