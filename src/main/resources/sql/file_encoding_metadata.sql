-- =====================================================
-- 文件编码元数据表
-- 功能：记录每个XML文件的原始编码信息，确保往返一致性
-- 日期：2025-12-28
-- =====================================================

-- 删除旧表（如果存在）
DROP TABLE IF EXISTS file_encoding_metadata;

-- 创建编码元数据表（支持 World 表的 mapType 区分）
CREATE TABLE file_encoding_metadata (
    table_name VARCHAR(100) NOT NULL COMMENT '表名（与数据库表对应）',
    map_type VARCHAR(50) NOT NULL DEFAULT '' COMMENT 'World表专用：China/Korea/Japan等，普通表为空字符串',
    original_encoding VARCHAR(20) NOT NULL COMMENT '原始编码：UTF-16BE, UTF-16LE, UTF-8, GBK等',
    has_bom BOOLEAN DEFAULT FALSE COMMENT '是否有BOM标记',
    xml_version VARCHAR(10) DEFAULT '1.0' COMMENT 'XML版本声明',
    original_file_path TEXT COMMENT '原始XML文件路径',
    original_file_hash VARCHAR(64) COMMENT '原始文件MD5哈希（用于往返一致性验证）',
    file_size_bytes BIGINT COMMENT '原始文件大小（字节）',
    last_import_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '最后导入时间',
    last_export_time TIMESTAMP NULL COMMENT '最后导出时间',
    last_validation_time TIMESTAMP NULL COMMENT '最后验证时间',
    last_validation_result BOOLEAN NULL COMMENT '最后验证结果（true=一致，false=不一致）',
    import_count INT DEFAULT 1 COMMENT '导入次数统计',
    export_count INT DEFAULT 0 COMMENT '导出次数统计',
    validation_count INT DEFAULT 0 COMMENT '验证次数统计',
    notes TEXT COMMENT '备注信息',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (table_name, map_type),
    INDEX idx_encoding (original_encoding),
    INDEX idx_last_import (last_import_time),
    INDEX idx_last_export (last_export_time),
    INDEX idx_validation_result (last_validation_result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='XML文件编码元数据表 - 保证导入导出往返一致性（支持World表多版本）';

-- 示例数据（可选）
-- INSERT INTO file_encoding_metadata
-- (table_name, original_encoding, has_bom, original_file_path, file_size_bytes)
-- VALUES
-- ('skill_base', 'UTF-16BE', TRUE, 'D:\\AionReal58\\AionMap\\XML\\skill_base.xml', 85983232);

SELECT '✅ file_encoding_metadata 表创建成功' AS status;
