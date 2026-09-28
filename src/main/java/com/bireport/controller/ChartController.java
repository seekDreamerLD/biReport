package com.bireport.controller;

import com.bireport.auth.TokenService;
import com.bireport.common.Result;
import com.bireport.dto.ConfigDTOs.ChartConfig;
import com.bireport.entity.Chart;
import com.bireport.service.ChartService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/charts")
@RequiredArgsConstructor
public class ChartController {

    private final ChartService chartService;

    @GetMapping("/list")
    public Result<List<Chart>> list() {
        return Result.ok(chartService.listAll());
    }

    @GetMapping("/{id}")
    public Result<Chart> get(@PathVariable Long id) {
        return Result.ok(chartService.require(id));
    }

    @PostMapping("/create")
    public Result<Chart> create(@RequestBody Req req) {
        return Result.ok(chartService.create(TokenService.currentUser(),
                req.getName(), req.getDatasetId(), req.getConfig()));
    }

    @PutMapping("/{id}")
    public Result<Chart> update(@PathVariable Long id, @RequestBody Req req) {
        return Result.ok(chartService.update(TokenService.currentUser(), id, req.getName(), req.getConfig()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        chartService.delete(TokenService.currentUser(), id);
        return Result.ok();
    }

    @Data
    public static class Req {
        private String name;
        private Long datasetId;
        private ChartConfig config;
    }
}
