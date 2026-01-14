-- PostgreSQL 数据库初始化脚本
-- 用于 灵游 (j_lingyou) 项目

-- 1. 创建数据库（在 psql 中以 postgres 用户执行）
-- CREATE DATABASE xmldb_suiyue WITH ENCODING = 'UTF8';

-- 2. 连接到数据库后执行以下脚本
-- \c xmldb_suiyue

-- 3. 创建编码元数据表 (完整版本，与 Java 代码一致)
CREATE TABLE IF NOT EXISTS file_encoding_metadata (
    table_name VARCHAR(100) NOT NULL,
    map_type VARCHAR(50) NOT NULL DEFAULT '',
    original_encoding VARCHAR(20) NOT NULL,
    has_bom BOOLEAN DEFAULT FALSE,
    xml_version VARCHAR(10) DEFAULT '1.0',
    original_file_path TEXT,
    original_file_hash VARCHAR(64),
    file_size_bytes BIGINT,
    last_import_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_export_time TIMESTAMP NULL,
    last_validation_time TIMESTAMP NULL,
    last_validation_result BOOLEAN NULL,
    import_count INT DEFAULT 1,
    export_count INT DEFAULT 0,
    validation_count INT DEFAULT 0,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (table_name, map_type)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_encoding ON file_encoding_metadata(original_encoding);
CREATE INDEX IF NOT EXISTS idx_last_import ON file_encoding_metadata(last_import_time);
CREATE INDEX IF NOT EXISTS idx_last_export ON file_encoding_metadata(last_export_time);
CREATE INDEX IF NOT EXISTS idx_validation_result ON file_encoding_metadata(last_validation_result);

-- 4. 创建聊天记忆表
CREATE TABLE IF NOT EXISTS chat_memory (
    id BIGSERIAL PRIMARY KEY,
    memory_id VARCHAR(100) NOT NULL,
    message_type VARCHAR(20) NOT NULL,
    message_content TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_memory_id ON chat_memory (memory_id);

-- 5. 创建工作流审计日志表
CREATE TABLE IF NOT EXISTS workflow_audit_log (
    id BIGSERIAL PRIMARY KEY,
    workflow_type VARCHAR(50) NOT NULL,
    table_name VARCHAR(255),
    operation VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    details TEXT,
    error_message TEXT,
    execution_time_ms BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_workflow_type ON workflow_audit_log (workflow_type);
CREATE INDEX IF NOT EXISTS idx_created_at ON workflow_audit_log (created_at);

-- 6. 创建数据快照表
CREATE TABLE IF NOT EXISTS data_snapshots (
    id BIGSERIAL PRIMARY KEY,
    table_name VARCHAR(255) NOT NULL,
    snapshot_type VARCHAR(50) NOT NULL,
    row_count INTEGER NOT NULL,
    data_json TEXT,
    checksum VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    description TEXT
);
CREATE INDEX IF NOT EXISTS idx_ds_table_name ON data_snapshots (table_name);
CREATE INDEX IF NOT EXISTS idx_ds_created_at ON data_snapshots (created_at);

-- 7. 创建字段引用表
CREATE TABLE IF NOT EXISTS field_references (
    id BIGSERIAL PRIMARY KEY,
    source_table VARCHAR(255) NOT NULL,
    source_field VARCHAR(255) NOT NULL,
    target_table VARCHAR(255) NOT NULL,
    target_field VARCHAR(255) NOT NULL,
    reference_type VARCHAR(50) DEFAULT 'FK',
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_field_ref UNIQUE (source_table, source_field, target_table, target_field)
);
CREATE INDEX IF NOT EXISTS idx_fr_source ON field_references (source_table, source_field);
CREATE INDEX IF NOT EXISTS idx_fr_target ON field_references (target_table, target_field);

-- 触发器：自动更新 updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_file_encoding_metadata_updated_at
    BEFORE UPDATE ON file_encoding_metadata
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_field_references_updated_at
    BEFORE UPDATE ON field_references
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- 8. 创建属性字典表
CREATE TABLE IF NOT EXISTS attr_dictionary (
    id BIGSERIAL PRIMARY KEY,
    attr_code VARCHAR(255) NOT NULL UNIQUE,
    attr_name VARCHAR(255),
    attr_category VARCHAR(100),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ad_category ON attr_dictionary (attr_category);

-- 完成
SELECT '数据库初始化完成！' AS message;
