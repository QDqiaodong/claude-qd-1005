package com.utility.tunnel.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.utility.tunnel.common.ApiResponse;
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
}
