-- 服务器配置文件清单表
-- 记录服务器启动时实际加载的 XML 文件，作为"文件层的唯一真理"

CREATE TABLE IF NOT EXISTS server_config_files (
    id INT AUTO_INCREMENT PRIMARY KEY,

    -- 文件标识
    file_name VARCHAR(200) NOT NULL COMMENT 'XML文件名（不含路径）',
    file_path VARCHAR(500) COMMENT '完整文件路径',
    table_name VARCHAR(100) COMMENT '对应的数据库表名',

    -- 服务器加载信息
    is_server_loaded BOOLEAN DEFAULT FALSE COMMENT '是否被服务器加载',
    load_priority INT DEFAULT 0 COMMENT '加载优先级（1=核心，2=重要，3=一般）',
    server_module VARCHAR(100) COMMENT '所属服务器模块（MainServer/NPCServer/等）',

    -- 文件元数据
    file_category VARCHAR(50) COMMENT '文件分类（items/skills/quests/等）',
    file_encoding VARCHAR(20) COMMENT '文件编码',
    file_size BIGINT COMMENT '文件大小（字节）',

    -- 验证信息
    last_validation_time DATETIME COMMENT '最后验证时间',
    validation_status VARCHAR(20) COMMENT '验证状态（valid/invalid/missing）',
    validation_errors TEXT COMMENT '验证错误信息（JSON格式）',

    -- 依赖关系
    depends_on VARCHAR(500) COMMENT '依赖的其他文件（JSON数组）',
    referenced_by VARCHAR(500) COMMENT '被哪些文件引用（JSON数组）',

    -- 设计师标注
    designer_notes TEXT COMMENT '设计师备注',
    is_critical BOOLEAN DEFAULT FALSE COMMENT '是否核心配置文件',
    is_deprecated BOOLEAN DEFAULT FALSE COMMENT '是否已废弃',

    -- 统计信息
    import_count INT DEFAULT 0 COMMENT '导入次数',
    export_count INT DEFAULT 0 COMMENT '导出次数',
    last_import_time DATETIME COMMENT '最后导入时间',
    last_export_time DATETIME COMMENT '最后导出时间',

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_file_name (file_name),
    KEY idx_server_loaded (is_server_loaded),
    KEY idx_load_priority (load_priority),
    KEY idx_table_name (table_name),
    KEY idx_file_category (file_category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务器配置文件清单 - 文件层的唯一真理';


-- 服务器日志分析记录表
CREATE TABLE IF NOT EXISTS server_log_analysis (
    id INT AUTO_INCREMENT PRIMARY KEY,

    log_file_path VARCHAR(500) NOT NULL COMMENT '日志文件路径',
    log_date DATE NOT NULL COMMENT '日志日期',
    server_module VARCHAR(100) COMMENT '服务器模块',

    -- 分析结果
    files_found INT DEFAULT 0 COMMENT '发现的文件数量',
    errors_found INT DEFAULT 0 COMMENT '发现的错误数量',
    warnings_found INT DEFAULT 0 COMMENT '发现的警告数量',

    analysis_summary TEXT COMMENT '分析摘要（JSON格式）',

    analyzed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    KEY idx_log_date (log_date),
    KEY idx_server_module (server_module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务器日志分析记录';
