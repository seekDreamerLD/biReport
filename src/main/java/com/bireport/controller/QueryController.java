package com.bireport.controller;

import com.bireport.auth.TokenService;
import com.bireport.common.Result;
import com.bireport.dto.ConfigDTOs.QueryReq;
import com.bireport.service.QueryService;
import com.bireport.service.query.QueryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class QueryController {

    private final QueryService queryService;

    @PostMapping("/query")
    public Result<QueryResult> query(@RequestBody QueryReq req) {
        return Result.ok(queryService.queryForChart(TokenService.currentUser(), req));
    }

    @PostMapping("/query/distinct")
    public Result<QueryResult> distinct(@RequestBody DistinctReq req) {
        return Result.ok(queryService.distinct(TokenService.currentUser(), req.getChartId(), req.getField()));
    }

    // ================= 公开嵌入接口（share token 鉴权，免登录） =================

    @PostMapping("/public/query")
    public Result<QueryResult> publicQuery(@RequestBody QueryReq req) {
        return Result.ok(queryService.publicQuery(req.getShareToken(), req));
    }

    @PostMapping("/public/query/distinct")
    public Result<QueryResult> publicDistinct(@RequestBody DistinctReq req) {
        return Result.ok(queryService.publicDistinct(req.getShareToken(), req.getChartId(), req.getField()));
    }

    @lombok.Data
    public static class DistinctReq {
        private Long chartId;
        private String field;
        private String shareToken;
    }
}
