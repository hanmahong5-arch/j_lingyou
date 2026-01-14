-- =====================================================
-- 修复 file_encoding_metadata 表结构
-- 问题：旧表结构缺少字段，导致 Java 代码 INSERT 失败
-- 解决：删除旧表，创建新表（符合 PostgreSQL 语法）
-- 日期：2026-01-14
-- =====================================================

-- 1. 备份现有数据（如果需要）
-- CREATE TABLE file_encoding_metadata_backup AS SELECT * FROM file_encoding_metadata;

-- 2. 删除旧表
DROP TABLE IF EXISTS file_encoding_metadata CASCADE;

-- 3. 创建新表（完整结构）
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

-- 4. 创建索引
CREATE INDEX idx_encoding ON file_encoding_metadata(original_encoding);
CREATE INDEX idx_last_import ON file_encoding_metadata(last_import_time);
CREATE INDEX idx_last_export ON file_encoding_metadata(last_export_time);
CREATE INDEX idx_validation_result ON file_encoding_metadata(last_validation_result);

-- 5. 添加列注释
COMMENT ON TABLE file_encoding_metadata IS 'XML文件编码元数据表 - 保证导入导出往返一致性';
COMMENT ON COLUMN file_encoding_metadata.table_name IS '表名（与数据库表对应）';
COMMENT ON COLUMN file_encoding_metadata.map_type IS 'World表专用：China/Korea/Japan等，普通表为空字符串';
COMMENT ON COLUMN file_encoding_metadata.original_encoding IS '原始编码：UTF-16BE, UTF-16LE, UTF-8, GBK等';
COMMENT ON COLUMN file_encoding_metadata.has_bom IS '是否有BOM标记';
COMMENT ON COLUMN file_encoding_metadata.xml_version IS 'XML版本声明';
COMMENT ON COLUMN file_encoding_metadata.original_file_path IS '原始XML文件路径';
COMMENT ON COLUMN file_encoding_metadata.original_file_hash IS '原始文件MD5哈希（用于往返一致性验证）';
COMMENT ON COLUMN file_encoding_metadata.file_size_bytes IS '原始文件大小（字节）';
COMMENT ON COLUMN file_encoding_metadata.last_import_time IS '最后导入时间';
COMMENT ON COLUMN file_encoding_metadata.last_export_time IS '最后导出时间';
COMMENT ON COLUMN file_encoding_metadata.import_count IS '导入次数统计';
COMMENT ON COLUMN file_encoding_metadata.export_count IS '导出次数统计';

-- 6. 创建触发器（自动更新 updated_at）
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

-- 7. 验证表结构
\d file_encoding_metadata

SELECT '✅ file_encoding_metadata 表修复完成！' AS status;
