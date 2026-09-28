package com.bireport.common;

import java.util.regex.Pattern;

/**
 * 只读 SQL 防护：数据集 SQL 与查询引擎产出的 SQL 必须是单条 SELECT。
 */
public final class SqlSafety {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(drop|delete|update|insert|alter|create|truncate|grant|revoke|call|lock|rename|replace|merge|set|use|handler|load|outfile|dumpfile|sleep|benchmark)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern COMMENT = Pattern.compile("(--|#)|(/\\*)", Pattern.CASE_INSENSITIVE);

    private SqlSafety() {
    }

    public static void validateSelectOnly(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new BizException("SQL 不能为空");
        }
        String trimmed = sql.trim();
        if (trimmed.endsWith(";") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        if (trimmed.contains(";")) {
            throw new BizException("只允许执行单条 SELECT 语句");
        }
        if (!trimmed.toLowerCase().startsWith("select") && !trimmed.toLowerCase().startsWith("with")) {
            throw new BizException("只允许执行 SELECT 查询");
        }
        if (COMMENT.matcher(trimmed).find()) {
            throw new BizException("SQL 中不允许包含注释");
        }
        if (FORBIDDEN.matcher(trimmed).find()) {
            throw new BizException("SQL 中包含被禁止的关键字");
        }
    }

    /** 标识符（字段名/表名）白名单校验：字母数字下划线中文，防注入 */
    public static String validateIdentifier(String name) {
        if (name == null || name.isBlank()) {
            throw new BizException("标识符不能为空");
        }
        if (!name.matches("[\\w\\u4e00-\\u9fa5.$]+")) {
            throw new BizException("非法标识符: " + name);
        }
        return name;
    }
}
