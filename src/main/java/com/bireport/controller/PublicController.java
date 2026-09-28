package com.bireport.controller;

import com.bireport.common.Result;
import com.bireport.entity.Chart;
import com.bireport.mapper.ChartMapper;
import com.bireport.service.DashboardService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 嵌入页公开接口：免登录，凭 share token 访问。
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final DashboardService dashboardService;
    private final ChartMapper chartMapper;

    @GetMapping("/dashboards/{token}")
    public Result<Map<String, Object>> getPublished(@PathVariable String token) {
        var d = dashboardService.requirePublishedByToken(token);
        // 画布引用的图表配置一并下发，嵌入页免登录渲染
        Map<String, Object> charts = new HashMap<>();
        for (Long chartId : dashboardService.extractChartIds(d.getConfigJson())) {
            Chart chart = chartMapper.selectById(chartId);
            if (chart != null) {
                charts.put(String.valueOf(chartId),
                        Map.of("id", chart.getId(), "name", chart.getName(), "config", chart.getConfigJson()));
            }
        }
        return Result.ok(Map.of(
                "id", d.getId(),
                "name", d.getName(),
                "config", d.getConfigJson(),
                "charts", charts,
                "updatedAt", String.valueOf(d.getUpdatedAt())));
    }
}
