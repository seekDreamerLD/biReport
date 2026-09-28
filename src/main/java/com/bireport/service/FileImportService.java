package com.bireport.service;

import com.bireport.common.BizException;
import com.bireport.entity.DataSource;
import com.bireport.entity.User;
import com.bireport.mapper.DataSourceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Excel/CSV 文件导入：解析 → 列类型推断 → 主库建表 upload_xxx → 批量插入 → 登记数据源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileImportService {

    private static final Pattern IDENT = Pattern.compile("[\\w\\u4e00-\\u9fa5]+");
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm")
    };
    private static final int MAX_ROWS = 200_000;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DataSource importFile(User user, DataSourceMapper mapper, String name, MultipartFile file) {
        String fileName = file.getOriginalFilename() == null ? "data" : file.getOriginalFilename();
        String lower = fileName.toLowerCase();
        List<String[]> rows;
        try {
            if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
                rows = parseExcel(file.getBytes());
            } else if (lower.endsWith(".csv") || lower.endsWith(".txt")) {
                rows = parseCsv(file.getBytes());
            } else {
                throw new BizException("仅支持 .xlsx / .xls / .csv 文件");
            }
        } catch (IOException e) {
            throw new BizException("文件读取失败: " + e.getMessage());
        }
        if (rows.isEmpty()) {
            throw new BizException("文件内容为空");
        }
        if (rows.size() > MAX_ROWS + 1) {
            throw new BizException("数据行数超过上限 " + MAX_ROWS + " 行");
        }

        String[] header = rows.get(0);
        List<String> columns = sanitizeColumns(header);
        List<String[]> dataRows = rows.subList(1, rows.size());
        if (dataRows.isEmpty()) {
            throw new BizException("文件没有数据行");
        }

        String table = "upload_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 9000 + 1000);
        List<String> types = inferTypes(columns, dataRows);

        createTable(table, columns, types);
        int inserted = insertRows(table, columns, types, dataRows);

        DataSource ds = new DataSource();
        ds.setName(name == null || name.isBlank() ? fileName : name);
        ds.setType("upload");
        Map<String, Object> config = new HashMap<>();
        config.put("table", table);
        config.put("fileName", fileName);
        config.put("rowCount", inserted);
        try {
            ds.setConfigJson(objectMapper.writeValueAsString(config));
        } catch (Exception e) {
            throw new BizException("配置序列化失败");
        }
        ds.setOwnerId(user.getId());
        mapper.insert(ds);
        log.info("文件导入完成: {} -> 表 {} 共 {} 行", fileName, table, inserted);
        return ds;
    }

    private List<String[]> parseExcel(byte[] bytes) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            List<String[]> rows = new ArrayList<>();
            for (Row row : sheet) {
                int last = row.getLastCellNum();
                if (last <= 0) {
                    continue;
                }
                String[] values = new String[last];
                boolean hasValue = false;
                for (int i = 0; i < last; i++) {
                    Cell cell = row.getCell(i);
                    String v = readCell(cell, formatter);
                    values[i] = v;
                    if (v != null && !v.isBlank()) {
                        hasValue = true;
                    }
                }
                if (hasValue) {
                    rows.add(values);
                }
                if (rows.size() > MAX_ROWS + 1) {
                    break;
                }
            }
            return rows;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("Excel 解析失败: " + e.getMessage());
        }
    }

    private String readCell(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                }
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 9.0E15) {
                    yield String.valueOf((long) d);
                }
                yield BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> formatter.formatCellValue(cell).trim();
            default -> null;
        };
    }

    private List<String[]> parseCsv(byte[] bytes) {
        Charset charset = detectCharset(bytes);
        List<String[]> rows = new ArrayList<>();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes), charset);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .build().parse(reader)) {
            for (CSVRecord record : parser) {
                String[] values = new String[record.size()];
                boolean hasValue = false;
                for (int i = 0; i < record.size(); i++) {
                    values[i] = record.get(i);
                    if (values[i] != null && !values[i].isBlank()) {
                        hasValue = true;
                    }
                }
                if (hasValue) {
                    rows.add(values);
                }
                if (rows.size() > MAX_ROWS + 1) {
                    break;
                }
            }
            return rows;
        } catch (Exception e) {
            throw new BizException("CSV 解析失败: " + e.getMessage());
        }
    }

    private Charset detectCharset(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes));
            return StandardCharsets.UTF_8;
        } catch (Exception e) {
            return Charset.forName("GBK");
        }
    }

    private List<String> sanitizeColumns(String[] header) {
        List<String> columns = new ArrayList<>();
        for (int i = 0; i < header.length; i++) {
            String raw = header[i] == null ? "" : header[i].trim();
            if (raw.isEmpty()) {
                raw = "col_" + (i + 1);
            }
            // 保留中英文数字下划线，其余替换为下划线
            raw = raw.replaceAll("[^\\w\\u4e00-\\u9fa5]+", "_");
            while (raw.startsWith("_")) {
                raw = raw.substring(1);
            }
            if (raw.isEmpty()) {
                raw = "col_" + (i + 1);
            }
            if (raw.matches("^\\d.*")) {
                raw = "c_" + raw;
            }
            String candidate = raw;
            int suffix = 2;
            while (columns.contains(candidate)) {
                candidate = raw + "_" + suffix++;
            }
            columns.add(candidate);
        }
        return columns;
    }

    private List<String> inferTypes(List<String> columns, List<String[]> rows) {
        int sample = Math.min(rows.size(), 1000);
        List<String> types = new ArrayList<>();
        for (int c = 0; c < columns.size(); c++) {
            boolean allInt = true, allNumber = true, allDate = true, allEmpty = true;
            for (int r = 0; r < sample; r++) {
                String v = c < rows.get(r).length ? rows.get(r)[c] : null;
                if (v == null || v.isBlank()) {
                    continue;
                }
                allEmpty = false;
                if (!v.matches("^-?\\d+$")) {
                    allInt = false;
                }
                if (!v.matches("^-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?$")) {
                    allNumber = false;
                }
                if (parseDate(v) == null) {
                    allDate = false;
                }
                if (!allInt && !allNumber && !allDate) {
                    break;
                }
            }
            if (allEmpty) {
                types.add("VARCHAR(255)");
            } else if (allInt) {
                types.add("BIGINT");
            } else if (allNumber) {
                types.add("DECIMAL(18,4)");
            } else if (allDate) {
                types.add("DATETIME");
            } else {
                types.add("VARCHAR(255)");
            }
        }
        return types;
    }

    private LocalDateTime parseDate(String v) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                if (fmt.equals(DATE_FORMATS[0]) || fmt.equals(DATE_FORMATS[2])) {
                    return java.time.LocalDate.parse(v, fmt).atStartOfDay();
                }
                return LocalDateTime.parse(v, fmt);
            } catch (Exception ignored) {
                // 尝试下一种格式
            }
        }
        return null;
    }

    private void createTable(String table, List<String> columns, List<String> types) {
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS `").append(table).append("` (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("`").append(columns.get(i)).append("` ").append(types.get(i));
        }
        sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbcTemplate.execute(sql.toString());
    }

    private int insertRows(String table, List<String> columns, List<String> types, List<String[]> rows) {
        String holders = String.join(", ", java.util.Collections.nCopies(columns.size(), "?"));
        String sql = "INSERT INTO `" + table + "` (" +
                columns.stream().map(c -> "`" + c + "`").reduce((a, b) -> a + ", " + b).orElse("") +
                ") VALUES (" + holders + ")";
        int count = 0;
        final int BATCH = 1000;
        List<Object[]> batch = new ArrayList<>(BATCH);
        for (String[] row : rows) {
            Object[] params = new Object[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                String v = i < row.length ? row[i] : null;
                params[i] = convert(v, types.get(i));
            }
            batch.add(params);
            if (batch.size() >= BATCH) {
                jdbcTemplate.batchUpdate(sql, batch);
                count += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batch);
            count += batch.size();
        }
        return count;
    }

    private Object convert(String v, String type) {
        if (v == null || v.isBlank()) {
            return null;
        }
        if ("BIGINT".equals(type)) {
            try {
                return Long.parseLong(v.trim());
            } catch (NumberFormatException e) {
                return v;
            }
        }
        if ("DECIMAL(18,4)".equals(type)) {
            try {
                return new BigDecimal(v.trim());
            } catch (NumberFormatException e) {
                return v;
            }
        }
        if ("DATETIME".equals(type)) {
            LocalDateTime dt = parseDate(v.trim());
            return dt == null ? v : Timestamp.valueOf(dt);
        }
        return v;
    }
}
