package com.bireport.service.query;

import com.bireport.common.BizException;
import com.bireport.common.SqlSafety;
import com.bireport.dto.ConfigDTOs.ChartConfig;
import com.bireport.dto.ConfigDTOs.ChartDim;
import com.bireport.dto.ConfigDTOs.ChartMeasure;
import com.bireport.dto.ConfigDTOs.DataFilter;
import com.bireport.dto.ConfigDTOs.DatasetField;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将图表配置编译为参数化 SQL：
 * SELECT 维度表达式, 聚合度量 FROM ( 数据集SQL ) bi_t [WHERE ...] [GROUP BY ...] [ORDER BY ...] LIMIT ?
 */
public final class SqlBuilder {

    public record BuiltSql(String sql, List<Object> params) {
    }

    private static final Set<String> AGGS = Set.of("sum", "avg", "count", "countDistinct", "max", "min");

    private SqlBuilder() {
    }

    public static BuiltSql build(String datasetSql, ChartConfig config,
                                 List<DatasetField> fields, List<DataFilter> extraFilters) {
        SqlSafety.validateSelectOnly(datasetSql);
        Set<String> known = fields.stream().map(DatasetField::getName).collect(Collectors.toSet());

        List<Object> params = new ArrayList<>();
        List<String> selects = new ArrayList<>();
        List<String> groupBys = new ArrayList<>();
        List<String> wheres = new ArrayList<>();

        boolean detail = "detail".equals(config.getChartType());

        if (!detail) {
            for (ChartDim dim : config.getDims()) {
                requireField(known, dim.getField());
                String expr = dimExpression(dim);
                selects.add(expr + " AS " + q(dim.getField()));
                groupBys.add(expr);
            }
            for (ChartMeasure m : config.getMeasures()) {
                requireField(known, m.getField());
                String agg = m.getAgg() == null ? "sum" : m.getAgg();
                if (!AGGS.contains(agg)) {
                    throw new BizException("不支持的聚合方式: " + agg);
                }
                String expr = "countDistinct".equals(agg)
                        ? "COUNT(DISTINCT " + col(m.getField()) + ")"
                        : agg.toUpperCase() + "(" + col(m.getField()) + ")";
                selects.add(expr + " AS " + q(m.getField() + "__" + agg));
            }
            if (selects.isEmpty()) {
                selects.add("COUNT(*) AS total_cnt");
            }
        } else {
            List<String> cols = config.getDetailFields() == null || config.getDetailFields().isEmpty()
                    ? fields.stream().map(DatasetField::getName).toList()
                    : config.getDetailFields();
            for (String c : cols) {
                requireField(known, c);
                selects.add(col(c) + " AS " + q(c));
            }
            if (selects.isEmpty()) {
                throw new BizException("明细表未配置展示字段");
            }
        }

        List<DataFilter> allFilters = new ArrayList<>();
        if (config.getFilters() != null) {
            allFilters.addAll(config.getFilters());
        }
        if (extraFilters != null) {
            allFilters.addAll(extraFilters);
        }
        for (DataFilter f : allFilters) {
            requireField(known, f.getField());
            String w = filterExpression(f, params);
            if (w != null) {
                wheres.add(w);
            }
        }

        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(String.join(", ", selects));
        sql.append(" FROM ( ").append(datasetSql.trim()).append(" ) bi_t");
        if (!wheres.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", wheres));
        }
        if (!detail && !groupBys.isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", groupBys));
        }
        Map<String, String> sort = config.getSort();
        if (sort != null && sort.get("field") != null && !sort.get("field").isBlank()) {
            String field = sort.get("field");
            String dir = "asc".equalsIgnoreCase(sort.get("dir")) ? "ASC" : "DESC";
            sql.append(" ORDER BY ").append(q(resolveAlias(config, field))).append(" ").append(dir);
        }
        int limit = config.getLimit() == null || config.getLimit() <= 0 ? 1000 : Math.min(config.getLimit(), 100000);
        sql.append(" LIMIT ").append(limit);
        return new BuiltSql(sql.toString(), params);
    }

    /** 数据集字段去重值（筛选器下拉选项） */
    public static BuiltSql buildDistinct(String datasetSql, String field, List<DatasetField> fields) {
        SqlSafety.validateSelectOnly(datasetSql);
        requireField(fields.stream().map(DatasetField::getName).collect(Collectors.toSet()), field);
        String sql = "SELECT DISTINCT " + col(field) + " AS v FROM ( " + datasetSql.trim()
                + " ) bi_t WHERE " + col(field) + " IS NOT NULL ORDER BY v LIMIT 1000";
        return new BuiltSql(sql, new ArrayList<>());
    }

    /** 排序字段 → SELECT 别名：维度别名=字段名；度量别名=字段__agg */
    private static String resolveAlias(ChartConfig config, String field) {
        boolean isMeasure = config.getMeasures() != null
                && config.getMeasures().stream().anyMatch(m -> m.getField().equals(field));
        if ("detail".equals(config.getChartType()) || !isMeasure) {
            return field;
        }
        ChartMeasure m = config.getMeasures().stream()
                .filter(x -> x.getField().equals(field)).findFirst().orElseThrow();
        String agg = m.getAgg() == null ? "sum" : m.getAgg();
        return field + "__" + agg;
    }

    private static String dimExpression(ChartDim dim) {
        String col = col(dim.getField());
        String level = dim.getDateLevel();
        if (level == null || level.isBlank() || "none".equals(level)) {
            return col;
        }
        return switch (level) {
            case "year" -> "DATE_FORMAT(" + col + ", '%Y')";
            case "quarter" -> "CONCAT(YEAR(" + col + "), '-Q', QUARTER(" + col + "))";
            case "month" -> "DATE_FORMAT(" + col + ", '%Y-%m')";
            case "week" -> "DATE_FORMAT(" + col + ", '%x-W%v')";
            case "day" -> "DATE_FORMAT(" + col + ", '%Y-%m-%d')";
            default -> throw new BizException("不支持的日期粒度: " + level);
        };
    }

    private static String filterExpression(DataFilter f, List<Object> params) {
        String col = col(f.getField());
        String op = f.getOp() == null ? "eq" : f.getOp();
        List<Object> values = f.getValues() == null ? new ArrayList<>() : f.getValues();
        switch (op) {
            case "eq" -> {
                if (values.isEmpty()) return null;
                params.add(values.get(0));
                return col + " = ?";
            }
            case "ne" -> {
                if (values.isEmpty()) return null;
                params.add(values.get(0));
                return col + " <> ?";
            }
            case "like" -> {
                if (values.isEmpty()) return null;
                params.add("%" + values.get(0) + "%");
                return col + " LIKE ?";
            }
            case "gt" -> { params.add(values.get(0)); return col + " > ?"; }
            case "gte" -> { params.add(values.get(0)); return col + " >= ?"; }
            case "lt" -> { params.add(values.get(0)); return col + " < ?"; }
            case "lte" -> { params.add(values.get(0)); return col + " <= ?"; }
            case "in", "notIn" -> {
                if (values.isEmpty()) return null;
                String holders = String.join(", ", java.util.Collections.nCopies(values.size(), "?"));
                params.addAll(values);
                return col + ("in".equals(op) ? " IN (" : " NOT IN (") + holders + ")";
            }
            case "between" -> {
                if (values.size() < 2) return null;
                params.add(values.get(0));
                params.add(values.get(1));
                return col + " BETWEEN ? AND ?";
            }
            default -> throw new BizException("不支持的筛选操作: " + op);
        }
    }

    private static void requireField(Set<String> known, String field) {
        if (field == null || !known.contains(field)) {
            throw new BizException("字段不存在或未在数据集中声明: " + field);
        }
    }

    private static String col(String field) {
        SqlSafety.validateIdentifier(field);
        return "bi_t.`" + field + "`";
    }

    private static String q(String alias) {
        return "`" + alias + "`";
    }
}
