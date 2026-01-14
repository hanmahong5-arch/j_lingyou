-- =====================================================
-- 快速修复 file_encoding_metadata 表（PostgreSQL）
-- 直接复制到 pgAdmin 或任何 PostgreSQL 客户端执行
-- =====================================================

-- 1. 删除旧表
DROP TABLE IF EXISTS file_encoding_metadata CASCADE;

-- 2. 创建新表
CREATE TABLE file_encoding_metadata (
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

-- 3. 创建索引
CREATE INDEX idx_encoding ON file_encoding_metadata(original_encoding);
CREATE INDEX idx_last_import ON file_encoding_metadata(last_import_time);
CREATE INDEX idx_last_export ON file_encoding_metadata(last_export_time);
CREATE INDEX idx_validation_result ON file_encoding_metadata(last_validation_result);

-- 4. 创建自动更新触发器
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS update_file_encoding_metadata_updated_at ON file_encoding_metadata;
CREATE TRIGGER update_file_encoding_metadata_updated_at
    BEFORE UPDATE ON file_encoding_metadata
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 完成
SELECT '✅ file_encoding_metadata 表修复完成！' AS status;
