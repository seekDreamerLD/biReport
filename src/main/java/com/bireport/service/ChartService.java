package com.bireport.service;

import com.bireport.common.BizException;
import com.bireport.dto.ConfigDTOs.ChartConfig;
import com.bireport.entity.Chart;
import com.bireport.entity.User;
import com.bireport.mapper.ChartMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChartService {

    private final ChartMapper chartMapper;
    private final ObjectMapper objectMapper;

    public List<Chart> listAll() {
        return chartMapper.selectList(Wrappers.<Chart>lambdaQuery().orderByDesc(Chart::getId));
    }

    public Chart require(Long id) {
        Chart chart = chartMapper.selectById(id);
        if (chart == null) {
            throw new BizException("图表不存在");
        }
        return chart;
    }

    public Chart create(User user, String name, Long datasetId, ChartConfig config) {
        Chart chart = new Chart();
        chart.setName(name);
        chart.setDatasetId(datasetId);
        chart.setConfigJson(toJson(config));
        chart.setOwnerId(user.getId());
        chartMapper.insert(chart);
        return chart;
    }

    public Chart update(User user, Long id, String name, ChartConfig config) {
        Chart chart = require(id);
        requireEditable(user, chart);
        if (name != null && !name.isBlank()) {
            chart.setName(name);
        }
        if (config != null) {
            chart.setConfigJson(toJson(config));
        }
        chartMapper.updateById(chart);
        return chart;
    }

    public void delete(User user, Long id) {
        Chart chart = require(id);
        requireEditable(user, chart);
        chartMapper.deleteById(id);
    }

    public void requireEditable(User user, Chart chart) {
        if (!"admin".equals(user.getRole()) && !user.getId().equals(chart.getOwnerId())) {
            throw new BizException("只有创建者或管理员可以修改该图表");
        }
    }

    public ChartConfig parseConfig(Chart chart) {
        try {
            return objectMapper.readValue(chart.getConfigJson(), ChartConfig.class);
        } catch (Exception e) {
            throw new BizException("图表配置解析失败");
        }
    }

    private String toJson(ChartConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new BizException("图表配置序列化失败");
        }
    }
}
