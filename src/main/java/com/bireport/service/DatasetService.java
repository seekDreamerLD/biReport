package com.bireport.service;

import com.bireport.auth.TokenService;
import com.bireport.common.BizException;
import com.bireport.common.SqlSafety;
import com.bireport.dto.ConfigDTOs.DatasetField;
import com.bireport.entity.Dataset;
import com.bireport.entity.User;
import com.bireport.mapper.DatasetMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bireport.service.query.QueryExecutor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DatasetService {

    private final DatasetMapper datasetMapper;
    private final DataSourceService dataSourceService;
    private final QueryExecutor queryExecutor;
    private final ObjectMapper objectMapper;

    public List<Dataset> listAll() {
        return datasetMapper.selectList(Wrappers.<Dataset>lambdaQuery().orderByDesc(Dataset::getId));
    }

    public Dataset require(Long id) {
        Dataset ds = datasetMapper.selectById(id);
        if (ds == null) {
            throw new BizException("数据集不存在");
        }
        return ds;
    }

    public Dataset create(User user, String name, Long datasourceId, String sqlText, List<DatasetField> fields) {
        validateFields(fields);
        Dataset dataset = new Dataset();
        dataset.setName(name);
        dataset.setDatasourceId(datasourceId);
        dataset.setSqlText(sqlText);
        dataset.setOwnerId(user.getId());
        dataset.setFieldsJson(toJson(fields));
        datasetMapper.insert(dataset);
        return dataset;
    }

    public Dataset update(User user, Long id, String name, String sqlText, List<DatasetField> fields) {
        Dataset dataset = require(id);
        requireEditable(user, dataset);
        if (name != null && !name.isBlank()) {
            dataset.setName(name);
        }
        if (sqlText != null) {
            dataset.setSqlText(sqlText);
        }
        if (fields != null) {
            validateFields(fields);
            dataset.setFieldsJson(toJson(fields));
        }
        datasetMapper.updateById(dataset);
        return dataset;
    }

    public void delete(User user, Long id) {
        Dataset dataset = require(id);
        requireEditable(user, dataset);
        datasetMapper.deleteById(id);
    }

    public QueryExecutor.PreviewResult preview(User user, Long datasourceId, String sqlText) {
        SqlSafety.validateSelectOnly(sqlText);
        var ds = dataSourceService.getVisible(datasourceId, user);
        return queryExecutor.preview(ds, sqlText, 100);
    }

    public void requireEditable(User user, Dataset dataset) {
        if (!"admin".equals(user.getRole()) && !user.getId().equals(dataset.getOwnerId())) {
            throw new BizException("只有创建者或管理员可以修改该数据集");
        }
    }

    private void validateFields(List<DatasetField> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new BizException("请至少声明一个字段");
        }
        for (DatasetField f : fields) {
            SqlSafety.validateIdentifier(f.getName());
        }
    }

    public List<DatasetField> parseFields(String json) {
        try {
            return objectMapper.readValue(json == null ? "[]" : json, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new BizException("字段元数据解析失败");
        }
    }

    private String toJson(List<DatasetField> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (Exception e) {
            throw new BizException("字段元数据序列化失败");
        }
    }
}
