-- BI 平台元数据表（全部幂等，可重复执行）

CREATE TABLE IF NOT EXISTS bi_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    nickname      VARCHAR(64)  NOT NULL DEFAULT '',
    role          VARCHAR(20)  NOT NULL DEFAULT 'viewer' COMMENT 'admin/editor/viewer',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户';

CREATE TABLE IF NOT EXISTS bi_datasource (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    type        VARCHAR(20)  NOT NULL COMMENT 'builtin_demo/mysql/upload',
    config_json TEXT         NULL COMMENT '连接配置 JSON',
    owner_id    BIGINT       NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_owner (owner_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '数据源';

CREATE TABLE IF NOT EXISTS bi_dataset (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(128) NOT NULL,
    datasource_id BIGINT       NOT NULL,
    sql_text      MEDIUMTEXT   NOT NULL,
    fields_json   TEXT         NULL COMMENT '字段元数据 JSON',
    owner_id      BIGINT       NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_owner (owner_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '数据集';

CREATE TABLE IF NOT EXISTS bi_chart (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    dataset_id  BIGINT       NOT NULL,
    config_json MEDIUMTEXT   NOT NULL COMMENT '图表配置 JSON',
    owner_id    BIGINT       NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_owner (owner_id),
    KEY idx_dataset (dataset_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '图表';

CREATE TABLE IF NOT EXISTS bi_dashboard (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(128) NOT NULL,
    config_json   LONGTEXT     NOT NULL COMMENT '画布配置 JSON',
    share_token   VARCHAR(64)  NULL,
    share_enabled TINYINT      NOT NULL DEFAULT 0 COMMENT '1已发布 0未发布',
    owner_id      BIGINT       NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_owner (owner_id),
    KEY idx_share_token (share_token)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '仪表板/报表页';
