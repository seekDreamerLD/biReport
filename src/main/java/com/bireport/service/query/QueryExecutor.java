package com.bireport.service.query;

import com.bireport.common.BizException;
import com.bireport.entity.DataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 查询执行器：内置演示/上传数据走主库 JdbcTemplate，
 * 外部 MySQL 数据源按配置动态创建连接并缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryExecutor {

    public static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /** datasourceId -> (configFingerprint, DataSource) */
    private final Map<Long, FingerprintedDataSource> externalDataSources = new ConcurrentHashMap<>();

    private record FingerprintedDataSource(String fingerprint, javax.sql.DataSource ds) {
    }

    public QueryResult execute(DataSource dsEntity, String sql, List<Object> params) {
        JdbcTemplate jt = templateFor(dsEntity);
        long start = System.currentTimeMillis();
        List<Map<String, Object>> raw;
        try {
            raw = jt.queryForList(sql, params == null ? new Object[]{} : params.toArray());
        } catch (org.springframework.dao.DataAccessException e) {
            Throwable root = e.getRootCause() != null ? e.getRootCause() : e;
            log.error("查询执行失败: {}", root.getMessage());
            throw new BizException("查询执行失败: " + root.getMessage());
        }
        QueryResult result = new QueryResult();
        result.setCostMs(System.currentTimeMillis() - start);
        if (!raw.isEmpty()) {
            for (Map.Entry<String, Object> entry : raw.get(0).entrySet()) {
                result.getColumns().add(new QueryResult.Column(entry.getKey(), guessType(entry.getValue())));
            }
        }
        for (Map<String, Object> row : raw) {
            List<Object> values = new ArrayList<>(row.size());
            for (QueryResult.Column column : result.getColumns()) {
                values.add(normalize(row.get(column.getName())));
            }
            result.getRows().add(values);
        }
        return result;
    }

    public record PreviewResult(List<QueryResult.Column> columns, List<List<Object>> rows) {
    }

    /** 数据集预览：包一层 LIMIT */
    public PreviewResult preview(DataSource dsEntity, String datasetSql, int limit) {
        com.bireport.common.SqlSafety.validateSelectOnly(datasetSql);
        String sql = "SELECT * FROM ( " + datasetSql.trim() + " ) bi_t LIMIT " + Math.min(limit, 200);
        JdbcTemplate jt = templateFor(dsEntity);
        List<Map<String, Object>> raw;
        try {
            raw = jt.queryForList(sql);
        } catch (org.springframework.dao.DataAccessException e) {
            Throwable root = e.getRootCause() != null ? e.getRootCause() : e;
            throw new BizException("SQL 执行失败: " + root.getMessage());
        }
        List<QueryResult.Column> columns = new ArrayList<>();
        List<List<Object>> rows = new ArrayList<>();
        if (!raw.isEmpty()) {
            raw.get(0).keySet().forEach(k -> columns.add(new QueryResult.Column(k, "string")));
        }
        for (Map<String, Object> row : raw) {
            List<Object> values = new ArrayList<>();
            for (QueryResult.Column c : columns) {
                values.add(normalize(row.get(c.getName())));
            }
            rows.add(values);
        }
        return new PreviewResult(columns, rows);
    }

    public void testExternalConnection(Map<String, Object> config) {
        javax.sql.DataSource ds = buildExternalDataSource(config);
        try {
            new JdbcTemplate(ds).queryForList("SELECT 1");
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            throw new BizException("连接失败: " + root.getMessage());
        }
    }

    private JdbcTemplate templateFor(DataSource dsEntity) {
        if ("mysql".equals(dsEntity.getType())) {
            String fp = fingerprint(dsEntity);
            FingerprintedDataSource cached = externalDataSources.get(dsEntity.getId());
            if (cached == null || !cached.fingerprint().equals(fp)) {
                javax.sql.DataSource created = buildExternalDataSource(parseConfig(dsEntity.getConfigJson()));
                cached = new FingerprintedDataSource(fp, created);
                externalDataSources.put(dsEntity.getId(), cached);
            }
            return new JdbcTemplate(cached.ds());
        }
        // builtin_demo / upload 均在主库
        return jdbcTemplate;
    }

    private String fingerprint(DataSource dsEntity) {
        return String.valueOf(dsEntity.getConfigJson() == null ? "" : dsEntity.getConfigJson().hashCode());
    }

    private javax.sql.DataSource buildExternalDataSource(Map<String, Object> config) {
        String host = str(config.get("host"), "127.0.0.1");
        String port = str(config.get("port"), "3306");
        String database = str(config.get("database"), null);
        String username = str(config.get("username"), null);
        String password = str(config.get("password"), "");
        if (database == null || username == null) {
            throw new BizException("外部数据源配置不完整");
        }
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=60000";
        DriverManagerDataSource ds = new DriverManagerDataSource(url, username, password);
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        return ds;
    }

    private Map<String, Object> parseConfig(String json) {
        try {
            return objectMapper.readValue(json == null ? "{}" : json,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (Exception e) {
            throw new BizException("数据源配置解析失败");
        }
    }

    private String str(Object v, String def) {
        return v == null ? def : String.valueOf(v);
    }

    private String guessType(Object v) {
        if (v == null) {
            return "string";
        }
        if (v instanceof Number) {
            return "number";
        }
        return "string";
    }

    private Object normalize(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Timestamp ts) {
            return ts.toLocalDateTime().format(DATETIME_FMT);
        }
        if (v instanceof Date d) {
            return d.toLocalDate().format(DATE_FMT);
        }
        if (v instanceof Time t) {
            return t.toLocalTime().toString();
        }
        if (v instanceof LocalDateTime ldt) {
            return ldt.format(DATETIME_FMT);
        }
        if (v instanceof LocalDate ld) {
            return ld.format(DATE_FMT);
        }
        if (v instanceof byte[] bytes) {
            return new String(bytes);
        }
        if (v instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().toPlainString();
        }
        return v;
    }

    public void evict(Long datasourceId) {
        externalDataSources.remove(datasourceId);
    }
}
