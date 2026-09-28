package com.bireport.service;

import com.bireport.auth.TokenService;
import com.bireport.common.BizException;
import com.bireport.common.SqlSafety;
import com.bireport.entity.DataSource;
import com.bireport.entity.Dataset;
import com.bireport.entity.User;
import com.bireport.mapper.DataSourceMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bireport.service.query.QueryExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataSourceService {

    private final DataSourceMapper dataSourceMapper;
    private final QueryExecutor queryExecutor;
    private final ObjectMapper objectMapper;
    private final FileImportService fileImportService;

    /** 内置演示数据源全局可见，其余只能看到自己创建的（admin 全部） */
    public List<DataSource> listVisible(User user) {
        List<DataSource> all = dataSourceMapper.selectList(
                Wrappers.<DataSource>lambdaQuery().orderByDesc(DataSource::getId));
        if ("admin".equals(user.getRole())) {
            return all;
        }
        return all.stream()
                .filter(ds -> "builtin_demo".equals(ds.getType()) || user.getId().equals(ds.getOwnerId()))
                .toList();
    }

    public DataSource getVisible(Long id, User user) {
        DataSource ds = dataSourceMapper.selectById(id);
        if (ds == null) {
            throw new BizException("数据源不存在");
        }
        boolean visible = "builtin_demo".equals(ds.getType()) || user.getId().equals(ds.getOwnerId())
                || "admin".equals(user.getRole());
        if (!visible) {
            throw new BizException("无权访问该数据源");
        }
        return ds;
    }

    public DataSource create(User user, String name, String type, Map<String, Object> config) {
        DataSource ds = new DataSource();
        ds.setName(name);
        ds.setType(type);
        try {
            ds.setConfigJson(objectMapper.writeValueAsString(config));
        } catch (Exception e) {
            throw new BizException("配置序列化失败");
        }
        ds.setOwnerId(user.getId());
        dataSourceMapper.insert(ds);
        return ds;
    }

    public DataSource update(User user, Long id, String name, Map<String, Object> config) {
        DataSource ds = requireEditable(user, id);
        if (name != null && !name.isBlank()) {
            ds.setName(name);
        }
        if (config != null && !config.isEmpty()) {
            try {
                ds.setConfigJson(objectMapper.writeValueAsString(config));
            } catch (Exception e) {
                throw new BizException("配置序列化失败");
            }
            queryExecutor.evict(id);
        }
        dataSourceMapper.updateById(ds);
        return ds;
    }

    public void delete(User user, Long id) {
        DataSource ds = requireEditable(user, id);
        if ("builtin_demo".equals(ds.getType())) {
            throw new BizException("内置演示数据源不可删除");
        }
        dataSourceMapper.deleteById(ds.getId());
        queryExecutor.evict(id);
    }

    /** 公开嵌入场景：按数据集直接定位数据源，不做用户级可见性校验（数据已由 share token 授权） */
    public DataSource getVisibleBySystem(Dataset dataset) {
        DataSource ds = dataSourceMapper.selectById(dataset.getDatasourceId());
        if (ds == null) {
            throw new BizException("数据集引用的数据源不存在");
        }
        return ds;
    }

    public DataSource requireEditable(User user, Long id) {
        DataSource ds = dataSourceMapper.selectById(id);
        if (ds == null) {
            throw new BizException("数据源不存在");
        }
        if (!"admin".equals(user.getRole()) && !user.getId().equals(ds.getOwnerId())) {
            throw new BizException("只有创建者或管理员可以修改该数据源");
        }
        return ds;
    }

    public void testConnection(String type, Map<String, Object> config) {
        if (!"mysql".equals(type)) {
            throw new BizException("该数据源类型无需测试连接");
        }
        queryExecutor.testExternalConnection(config);
    }

    public List<String> listTables(DataSource ds) {
        try {
            if ("upload".equals(ds.getType())) {
                JsonNode node = objectMapper.readTree(ds.getConfigJson());
                return List.of(node.path("table").asText());
            }
            if ("mysql".equals(ds.getType())) {
                QueryExecutor.PreviewResult r = queryExecutor.preview(ds,
                        "SELECT table_name AS name FROM information_schema.tables WHERE table_schema = DATABASE()", 500);
                return r.rows().stream().map(row -> String.valueOf(row.get(0))).toList();
            }
            // builtin_demo：演示表
            QueryExecutor.PreviewResult r = queryExecutor.preview(ds,
                    "SELECT table_name AS name FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() AND table_name LIKE 'demo_%'", 100);
            return r.rows().stream().map(row -> String.valueOf(row.get(0))).toList();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("获取表列表失败: " + e.getMessage());
        }
    }

    public record ColumnInfo(String name, String type) {
    }

    public List<ColumnInfo> listColumns(DataSource ds, String table) {
        SqlSafety.validateIdentifier(table);
        QueryExecutor.PreviewResult r = queryExecutor.preview(ds,
                "SELECT column_name, data_type FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = '" + table + "' ORDER BY ordinal_position", 200);
        return r.rows().stream()
                .map(row -> new ColumnInfo(String.valueOf(row.get(0)), String.valueOf(row.get(1))))
                .toList();
    }

    public DataSource upload(User user, String name, org.springframework.web.multipart.MultipartFile file) {
        return fileImportService.importFile(user, dataSourceMapper, name, file);
    }
}
