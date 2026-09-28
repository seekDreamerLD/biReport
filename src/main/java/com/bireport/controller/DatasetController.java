package com.bireport.controller;

import com.bireport.auth.TokenService;
import com.bireport.common.Result;
import com.bireport.dto.ConfigDTOs.DatasetField;
import com.bireport.entity.Dataset;
import com.bireport.service.DatasetService;
import com.bireport.service.query.QueryExecutor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/datasets")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetService datasetService;

    @GetMapping("/list")
    public Result<List<Dataset>> list() {
        return Result.ok(datasetService.listAll());
    }

    @GetMapping("/{id}")
    public Result<Dataset> get(@PathVariable Long id) {
        return Result.ok(datasetService.require(id));
    }

    @PostMapping("/create")
    public Result<Dataset> create(@RequestBody Req req) {
        return Result.ok(datasetService.create(TokenService.currentUser(),
                req.getName(), req.getDatasourceId(), req.getSqlText(), req.getFields()));
    }

    @PutMapping("/{id}")
    public Result<Dataset> update(@PathVariable Long id, @RequestBody Req req) {
        return Result.ok(datasetService.update(TokenService.currentUser(),
                id, req.getName(), req.getSqlText(), req.getFields()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        datasetService.delete(TokenService.currentUser(), id);
        return Result.ok();
    }

    @PostMapping("/preview")
    public Result<QueryExecutor.PreviewResult> preview(@RequestBody PreviewReq req) {
        return Result.ok(datasetService.preview(TokenService.currentUser(),
                req.getDatasourceId(), req.getSqlText()));
    }

    @Data
    public static class Req {
        private String name;
        private Long datasourceId;
        private String sqlText;
        private List<DatasetField> fields;
    }

    @Data
    public static class PreviewReq {
        private Long datasourceId;
        private String sqlText;
    }
}
