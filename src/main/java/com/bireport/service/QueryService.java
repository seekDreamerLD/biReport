package com.bireport.service;

import com.bireport.common.BizException;
import com.bireport.dto.ConfigDTOs.DataFilter;
import com.bireport.dto.ConfigDTOs.QueryReq;
import com.bireport.entity.Chart;
import com.bireport.entity.DataSource;
import com.bireport.entity.Dataset;
import com.bireport.entity.User;
import com.bireport.service.query.QueryExecutor;
import com.bireport.service.query.QueryResult;
import com.bireport.service.query.SqlBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 查询编排：图表 → 数据集 → 数据源 → SQL 构建 → 执行。
 */
@Service
@RequiredArgsConstructor
public class QueryService {

    private final ChartService chartService;
    private final DatasetService datasetService;
    private final DataSourceService dataSourceService;
    private final QueryExecutor queryExecutor;
    private final DashboardService dashboardService;

    public QueryResult queryForChart(User user, QueryReq req) {
        Chart chart = chartService.require(req.getChartId());
        Dataset dataset = datasetService.require(chart.getDatasetId());
        DataSource ds = dataSourceService.getVisible(dataset.getDatasourceId(), user);
        var fields = datasetService.parseFields(dataset.getFieldsJson());
        var config = chartService.parseConfig(chart);
        SqlBuilder.BuiltSql built = SqlBuilder.build(dataset.getSqlText(), config, fields, req.getExtraFilters());
        return queryExecutor.execute(ds, built.sql(), built.params());
    }

    public record ChartQuery(QueryResult result, String sql) {
    }

    /** 按图表配置直接查询（ChatBI 使用），返回结果与生成的 SQL */
    public ChartQuery queryByConfig(User user, Long datasetId, com.bireport.dto.ConfigDTOs.ChartConfig config) {
        Dataset dataset = datasetService.require(datasetId);
        DataSource ds = dataSourceService.getVisible(dataset.getDatasourceId(), user);
        var fields = datasetService.parseFields(dataset.getFieldsJson());
        SqlBuilder.BuiltSql built = SqlBuilder.build(dataset.getSqlText(), config, fields, null);
        return new ChartQuery(queryExecutor.execute(ds, built.sql(), built.params()), built.sql());
    }

    public QueryResult distinct(User user, Long chartId, String field) {
        Chart chart = chartService.require(chartId);
        Dataset dataset = datasetService.require(chart.getDatasetId());
        DataSource ds = dataSourceService.getVisible(dataset.getDatasourceId(), user);
        var fields = datasetService.parseFields(dataset.getFieldsJson());
        SqlBuilder.BuiltSql built = SqlBuilder.buildDistinct(dataset.getSqlText(), field, fields);
        return queryExecutor.execute(ds, built.sql(), built.params());
    }

    /** 公开嵌入查询：校验 share token 与图表归属 */
    public QueryResult publicQuery(String token, QueryReq req) {
        var dashboard = dashboardService.requirePublishedByToken(token);
        List<Long> chartIds = dashboardService.extractChartIds(dashboard.getConfigJson());
        if (req.getChartId() == null || !chartIds.contains(req.getChartId())) {
            throw new BizException("该图表不在分享范围内");
        }
        Chart chart = chartService.require(req.getChartId());
        Dataset dataset = datasetService.require(chart.getDatasetId());
        DataSource ds = dataSourceService.getVisibleBySystem(dataset);
        var fields = datasetService.parseFields(dataset.getFieldsJson());
        var config = chartService.parseConfig(chart);
        SqlBuilder.BuiltSql built = SqlBuilder.build(dataset.getSqlText(), config, fields, req.getExtraFilters());
        return queryExecutor.execute(ds, built.sql(), built.params());
    }

    public QueryResult publicDistinct(String token, Long chartId, String field) {
        var dashboard = dashboardService.requirePublishedByToken(token);
        List<Long> chartIds = dashboardService.extractChartIds(dashboard.getConfigJson());
        if (!chartIds.contains(chartId)) {
            throw new BizException("该图表不在分享范围内");
        }
        Chart chart = chartService.require(chartId);
        Dataset dataset = datasetService.require(chart.getDatasetId());
        DataSource ds = dataSourceService.getVisibleBySystem(dataset);
        var fields = datasetService.parseFields(dataset.getFieldsJson());
        SqlBuilder.BuiltSql built = SqlBuilder.buildDistinct(dataset.getSqlText(), field, fields);
        return queryExecutor.execute(ds, built.sql(), built.params());
    }
}
