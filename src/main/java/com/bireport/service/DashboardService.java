package com.bireport.service;

import com.bireport.common.BizException;
import com.bireport.entity.Dashboard;
import com.bireport.entity.User;
import com.bireport.mapper.DashboardMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardMapper dashboardMapper;
    private final ObjectMapper objectMapper;

    public List<Dashboard> listAll() {
        return dashboardMapper.selectList(Wrappers.<Dashboard>lambdaQuery().orderByDesc(Dashboard::getId));
    }

    public Dashboard require(Long id) {
        Dashboard d = dashboardMapper.selectById(id);
        if (d == null) {
            throw new BizException("仪表板不存在");
        }
        return d;
    }

    public Dashboard create(User user, String name, String configJson) {
        Dashboard d = new Dashboard();
        d.setName(name);
        d.setConfigJson(configJson == null || configJson.isBlank()
                ? "{\"canvas\":{\"width\":1920,\"height\":1080,\"theme\":\"light\"},\"components\":[],\"links\":[]}"
                : configJson);
        d.setShareEnabled(0);
        d.setOwnerId(user.getId());
        dashboardMapper.insert(d);
        return d;
    }

    public Dashboard update(User user, Long id, String name, String configJson) {
        Dashboard d = require(id);
        requireEditable(user, d);
        if (name != null && !name.isBlank()) {
            d.setName(name);
        }
        if (configJson != null) {
            d.setConfigJson(configJson);
        }
        dashboardMapper.updateById(d);
        return d;
    }

    public void delete(User user, Long id) {
        Dashboard d = require(id);
        requireEditable(user, d);
        dashboardMapper.deleteById(id);
    }

    public void requireEditable(User user, Dashboard d) {
        if (!"admin".equals(user.getRole()) && !user.getId().equals(d.getOwnerId())) {
            throw new BizException("只有创建者或管理员可以修改该仪表板");
        }
    }

    /** 发布：生成/复用 share token */
    public Dashboard publish(User user, Long id) {
        Dashboard d = require(id);
        requireEditable(user, d);
        if (d.getShareToken() == null || d.getShareToken().isBlank()) {
            d.setShareToken(UUID.randomUUID().toString().replace("-", ""));
        }
        d.setShareEnabled(1);
        dashboardMapper.updateById(d);
        return d;
    }

    public Dashboard unpublish(User user, Long id) {
        Dashboard d = require(id);
        requireEditable(user, d);
        d.setShareEnabled(0);
        dashboardMapper.updateById(d);
        return d;
    }

    /** 通过 share token 获取已发布的仪表板 */
    public Dashboard requirePublishedByToken(String token) {
        Dashboard d = dashboardMapper.selectOne(
                Wrappers.<Dashboard>lambdaQuery().eq(Dashboard::getShareToken, token));
        if (d == null || d.getShareEnabled() == null || d.getShareEnabled() != 1) {
            throw new BizException("分享链接不存在或已被取消发布");
        }
        return d;
    }

    /** 从画布配置中提取引用的全部图表 id */
    public List<Long> extractChartIds(String configJson) {
        List<Long> ids = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(configJson);
            for (JsonNode comp : root.path("components")) {
                if ("chart".equals(comp.path("type").asText())) {
                    long chartId = comp.path("props").path("chartId").asLong(0);
                    if (chartId > 0) {
                        ids.add(chartId);
                    }
                }
            }
        } catch (Exception ignored) {
            // 配置异常时返回空集合
        }
        return ids;
    }
}
