package com.bireport.controller;

import com.bireport.auth.TokenService;
import com.bireport.common.Result;
import com.bireport.entity.Dashboard;
import com.bireport.service.DashboardService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboards")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/list")
    public Result<List<Dashboard>> list() {
        return Result.ok(dashboardService.listAll());
    }

    @GetMapping("/{id}")
    public Result<Dashboard> get(@PathVariable Long id) {
        return Result.ok(dashboardService.require(id));
    }

    @PostMapping("/create")
    public Result<Dashboard> create(@RequestBody Req req) {
        return Result.ok(dashboardService.create(TokenService.currentUser(), req.getName(), req.getConfigJson()));
    }

    @PutMapping("/{id}")
    public Result<Dashboard> update(@PathVariable Long id, @RequestBody Req req) {
        return Result.ok(dashboardService.update(TokenService.currentUser(), id, req.getName(), req.getConfigJson()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dashboardService.delete(TokenService.currentUser(), id);
        return Result.ok();
    }

    @PostMapping("/{id}/publish")
    public Result<Dashboard> publish(@PathVariable Long id) {
        return Result.ok(dashboardService.publish(TokenService.currentUser(), id));
    }

    @PostMapping("/{id}/unpublish")
    public Result<Dashboard> unpublish(@PathVariable Long id) {
        return Result.ok(dashboardService.unpublish(TokenService.currentUser(), id));
    }

    @Data
    public static class Req {
        private String name;
        private String configJson;
    }
}
