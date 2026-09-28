package com.bireport.controller;

import com.bireport.auth.TokenService;
import com.bireport.common.Result;
import com.bireport.entity.DataSource;
import com.bireport.entity.User;
import com.bireport.service.DataSourceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/datasources")
@RequiredArgsConstructor
public class DataSourceController {

    private final DataSourceService dataSourceService;

    @GetMapping("/list")
    public Result<List<DataSource>> list() {
        return Result.ok(dataSourceService.listVisible(TokenService.currentUser()));
    }

    @PostMapping("/create")
    public Result<DataSource> create(@RequestBody CreateReq req) {
        User user = TokenService.currentUser();
        return Result.ok(dataSourceService.create(user, req.getName(), req.getType(), req.getConfig()));
    }

    @PutMapping("/{id}")
    public Result<DataSource> update(@PathVariable Long id, @RequestBody CreateReq req) {
        User user = TokenService.currentUser();
        return Result.ok(dataSourceService.update(user, id, req.getName(), req.getConfig()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dataSourceService.delete(TokenService.currentUser(), id);
        return Result.ok();
    }

    @PostMapping("/test")
    public Result<Void> test(@RequestBody CreateReq req) {
        dataSourceService.testConnection(req.getType(), req.getConfig());
        return Result.ok();
    }

    @PostMapping("/upload")
    public Result<DataSource> upload(@RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "name", required = false) String name) {
        return Result.ok(dataSourceService.upload(TokenService.currentUser(), name, file));
    }

    @GetMapping("/{id}/tables")
    public Result<List<String>> tables(@PathVariable Long id) {
        return Result.ok(dataSourceService.listTables(dataSourceService.getVisible(id, TokenService.currentUser())));
    }

    @GetMapping("/{id}/columns")
    public Result<List<DataSourceService.ColumnInfo>> columns(@PathVariable Long id, @RequestParam String table) {
        return Result.ok(dataSourceService.listColumns(
                dataSourceService.getVisible(id, TokenService.currentUser()), table));
    }

    @Data
    public static class CreateReq {
        private String name;
        private String type;
        private Map<String, Object> config;
    }
}
