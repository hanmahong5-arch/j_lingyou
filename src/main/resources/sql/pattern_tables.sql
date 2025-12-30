-- ============================================
-- Aion 游戏领域数据模式收集系统 - 数据库表结构
-- 用于学习服务端认可的数据模式，支持批量生成、修改和验证
-- ============================================

-- 1. 模式分类表 - 存储27个机制分类的模式定义
CREATE TABLE IF NOT EXISTS pattern_schema (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    mechanism_code VARCHAR(50) NOT NULL UNIQUE COMMENT '机制代码（如ITEM, SKILL, NPC）',
    mechanism_name VARCHAR(100) NOT NULL COMMENT '机制名称（中文）',
    mechanism_icon VARCHAR(10) DEFAULT NULL COMMENT '机制图标（emoji）',
    mechanism_color VARCHAR(20) DEFAULT NULL COMMENT '机制颜色（十六进制）',
    typical_fields TEXT COMMENT '典型字段列表（JSON数组）',
    typical_structure TEXT COMMENT 'XML结构模板',
    file_count INT DEFAULT 0 COMMENT '关联文件数量',
    field_count INT DEFAULT 0 COMMENT '字段总数',
    sample_count INT DEFAULT 0 COMMENT '样本数量',
    description TEXT COMMENT '机制描述',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_mechanism_code (mechanism_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模式分类表';

-- 2. 字段模式表 - 每个机制下的字段定义和值域
CREATE TABLE IF NOT EXISTS pattern_field (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    schema_id INT NOT NULL COMMENT '所属模式ID',
    field_name VARCHAR(100) NOT NULL COMMENT '字段名称',
    field_path VARCHAR(500) DEFAULT NULL COMMENT '字段XPath路径',
    is_attribute TINYINT(1) DEFAULT 0 COMMENT '是否为XML属性（0=元素，1=属性）',

    -- 类型推断结果
    inferred_type VARCHAR(20) NOT NULL DEFAULT 'STRING' COMMENT '推断类型：STRING/INTEGER/DECIMAL/BOOLEAN/ENUM/REFERENCE/BONUS_ATTR',
    value_domain_type VARCHAR(20) DEFAULT 'UNBOUNDED' COMMENT '值域类型：UNBOUNDED/RANGE/ENUM/REFERENCE',

    -- 值域定义
    value_min VARCHAR(100) DEFAULT NULL COMMENT '最小值（数值类型）',
    value_max VARCHAR(100) DEFAULT NULL COMMENT '最大值（数值类型）',
    value_enum TEXT DEFAULT NULL COMMENT '枚举值列表（JSON数组）',
    reference_target VARCHAR(200) DEFAULT NULL COMMENT '引用目标（表名.字段名）',

    -- 属性增益特殊字段
    is_bonus_attr TINYINT(1) DEFAULT 0 COMMENT '是否为属性增益字段',
    bonus_attr_slot VARCHAR(20) DEFAULT NULL COMMENT '属性槽位（如bonus_attr1, bonus_attr_a1）',

    -- 统计信息
    occurrence_rate DECIMAL(5,4) DEFAULT 0 COMMENT '出现率（0-1）',
    null_rate DECIMAL(5,4) DEFAULT 0 COMMENT '空值率（0-1）',
    distinct_count INT DEFAULT 0 COMMENT '不同值数量',
    total_count INT DEFAULT 0 COMMENT '总出现次数',

    -- 样本值
    sample_values TEXT DEFAULT NULL COMMENT '样本值列表（JSON数组，最多10个）',

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_schema_id (schema_id),
    INDEX idx_field_name (field_name),
    INDEX idx_inferred_type (inferred_type),
    INDEX idx_is_bonus_attr (is_bonus_attr),
    CONSTRAINT fk_field_schema FOREIGN KEY (schema_id) REFERENCES pattern_schema(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字段模式表';

-- 3. 值域分布表 - 枚举字段的值分布统计
CREATE TABLE IF NOT EXISTS pattern_value (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    field_id INT NOT NULL COMMENT '所属字段ID',
    value_content VARCHAR(500) NOT NULL COMMENT '值内容',
    value_display VARCHAR(200) DEFAULT NULL COMMENT '值显示名（中文翻译）',

    -- 统计信息
    occurrence_count INT DEFAULT 1 COMMENT '出现次数',
    percentage DECIMAL(5,4) DEFAULT 0 COMMENT '占比（0-1）',

    -- 来源追踪
    source_files TEXT DEFAULT NULL COMMENT '来源文件列表（JSON数组）',
    first_seen_file VARCHAR(500) DEFAULT NULL COMMENT '首次发现的文件',

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_field_id (field_id),
    INDEX idx_value_content (value_content(100)),
    INDEX idx_occurrence (occurrence_count DESC),
    CONSTRAINT fk_value_field FOREIGN KEY (field_id) REFERENCES pattern_field(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='值域分布表';

-- 4. 引用关系表 - 字段间的跨表引用关系
CREATE TABLE IF NOT EXISTS pattern_ref (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',

    -- 源端
    source_schema_id INT NOT NULL COMMENT '源模式ID',
    source_field_id INT NOT NULL COMMENT '源字段ID',
    source_field_name VARCHAR(100) NOT NULL COMMENT '源字段名（冗余，便于查询）',

    -- 目标端
    target_schema_id INT DEFAULT NULL COMMENT '目标模式ID',
    target_field_name VARCHAR(100) DEFAULT NULL COMMENT '目标字段名',
    target_table_name VARCHAR(100) DEFAULT NULL COMMENT '目标表名',

    -- 引用类型
    ref_type VARCHAR(20) NOT NULL DEFAULT 'ID_REFERENCE' COMMENT '引用类型：ID_REFERENCE/NAME_REFERENCE/BONUS_ATTR',
    confidence DECIMAL(3,2) DEFAULT 0.5 COMMENT '置信度（0-1）',

    -- 验证信息
    is_verified TINYINT(1) DEFAULT 0 COMMENT '是否已人工验证',
    sample_pairs TEXT DEFAULT NULL COMMENT '样本引用对（JSON数组）',

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_source_schema (source_schema_id),
    INDEX idx_source_field (source_field_id),
    INDEX idx_target_schema (target_schema_id),
    INDEX idx_ref_type (ref_type),
    CONSTRAINT fk_ref_source_schema FOREIGN KEY (source_schema_id) REFERENCES pattern_schema(id) ON DELETE CASCADE,
    CONSTRAINT fk_ref_source_field FOREIGN KEY (source_field_id) REFERENCES pattern_field(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='引用关系表';

-- 5. 属性词汇表 - 126个服务端属性定义
CREATE TABLE IF NOT EXISTS attr_dictionary (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    attr_code VARCHAR(100) NOT NULL UNIQUE COMMENT '属性代码（如str, max_hp, physical_attack）',
    attr_name VARCHAR(100) DEFAULT NULL COMMENT '属性名称（中文）',
    attr_category VARCHAR(50) DEFAULT NULL COMMENT '属性分类（基础属性/战斗属性/特殊属性）',

    -- 值域信息
    typical_min DECIMAL(15,2) DEFAULT NULL COMMENT '典型最小值',
    typical_max DECIMAL(15,2) DEFAULT NULL COMMENT '典型最大值',
    value_unit VARCHAR(20) DEFAULT NULL COMMENT '值单位（点/百分比）',
    is_percentage TINYINT(1) DEFAULT 0 COMMENT '是否为百分比值',

    -- 使用统计
    used_in_items INT DEFAULT 0 COMMENT '在物品中使用次数',
    used_in_titles INT DEFAULT 0 COMMENT '在称号中使用次数',
    used_in_pets INT DEFAULT 0 COMMENT '在宠物中使用次数',
    used_in_skills INT DEFAULT 0 COMMENT '在技能中使用次数',
    used_in_buffs INT DEFAULT 0 COMMENT '在Buff中使用次数',
    total_usage INT DEFAULT 0 COMMENT '总使用次数',

    -- 来源信息
    source_file VARCHAR(200) DEFAULT NULL COMMENT '定义来源文件',
    description TEXT DEFAULT NULL COMMENT '属性描述',

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_attr_code (attr_code),
    INDEX idx_attr_category (attr_category),
    INDEX idx_total_usage (total_usage DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='属性词汇表';

-- 6. 样本数据表 - 真实配置样本
CREATE TABLE IF NOT EXISTS pattern_sample (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    schema_id INT NOT NULL COMMENT '所属模式ID',

    -- 来源信息
    source_file VARCHAR(500) NOT NULL COMMENT '源文件路径',
    source_file_name VARCHAR(200) NOT NULL COMMENT '源文件名',

    -- 记录标识
    record_id VARCHAR(100) DEFAULT NULL COMMENT '记录ID（如物品ID）',
    record_name VARCHAR(200) DEFAULT NULL COMMENT '记录名称',

    -- 原始数据
    raw_xml TEXT COMMENT '原始XML片段',
    parsed_json TEXT COMMENT '解析后的JSON',

    -- 属性增益信息
    has_bonus_attr TINYINT(1) DEFAULT 0 COMMENT '是否包含属性增益',
    bonus_attr_count INT DEFAULT 0 COMMENT '属性增益数量',
    bonus_attr_summary TEXT DEFAULT NULL COMMENT '属性增益摘要（JSON）',

    -- 模板适用性
    is_template_candidate TINYINT(1) DEFAULT 0 COMMENT '是否适合作为模板',
    template_score DECIMAL(3,2) DEFAULT 0 COMMENT '模板适用性评分（0-1）',

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_schema_id (schema_id),
    INDEX idx_source_file (source_file(100)),
    INDEX idx_record_id (record_id),
    INDEX idx_has_bonus_attr (has_bonus_attr),
    INDEX idx_is_template_candidate (is_template_candidate),
    CONSTRAINT fk_sample_schema FOREIGN KEY (schema_id) REFERENCES pattern_schema(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='样本数据表';

-- 7. 生成模板表 - 批量生成的模板定义
CREATE TABLE IF NOT EXISTS data_template (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    schema_id INT NOT NULL COMMENT '所属模式ID',

    -- 模板基本信息
    template_name VARCHAR(100) NOT NULL COMMENT '模板名称',
    template_code VARCHAR(50) NOT NULL UNIQUE COMMENT '模板代码（唯一标识）',
    template_type VARCHAR(20) NOT NULL DEFAULT 'CREATE' COMMENT '模板类型：CREATE/MODIFY/CLONE',

    -- 模板内容
    template_xml TEXT NOT NULL COMMENT 'XML模板（包含占位符）',
    placeholder_list TEXT COMMENT '占位符列表（JSON数组）',

    -- 默认值和生成器
    default_values TEXT COMMENT '默认值映射（JSON对象）',
    value_generators TEXT COMMENT '值生成器配置（JSON对象）',

    -- 验证规则
    validation_rules TEXT COMMENT '验证规则（JSON数组）',

    -- 元信息
    description TEXT COMMENT '模板描述',
    usage_count INT DEFAULT 0 COMMENT '使用次数',
    is_active TINYINT(1) DEFAULT 1 COMMENT '是否启用',

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(50) DEFAULT NULL COMMENT '创建者',

    INDEX idx_schema_id (schema_id),
    INDEX idx_template_code (template_code),
    INDEX idx_template_type (template_type),
    INDEX idx_is_active (is_active),
    CONSTRAINT fk_template_schema FOREIGN KEY (schema_id) REFERENCES pattern_schema(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生成模板表';

-- 8. 模板参数表 - 模板的占位符定义
CREATE TABLE IF NOT EXISTS template_param (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    template_id INT NOT NULL COMMENT '所属模板ID',

    -- 参数基本信息
    param_name VARCHAR(100) NOT NULL COMMENT '参数名称（显示用）',
    param_code VARCHAR(50) NOT NULL COMMENT '参数代码（占位符标识）',
    param_type VARCHAR(20) NOT NULL DEFAULT 'STRING' COMMENT '参数类型：STRING/INTEGER/DECIMAL/BOOLEAN/ENUM/BONUS_ATTR',

    -- 参数约束
    is_required TINYINT(1) DEFAULT 1 COMMENT '是否必填',
    default_value VARCHAR(500) DEFAULT NULL COMMENT '默认值',
    min_value VARCHAR(100) DEFAULT NULL COMMENT '最小值',
    max_value VARCHAR(100) DEFAULT NULL COMMENT '最大值',
    enum_values TEXT DEFAULT NULL COMMENT '枚举选项（JSON数组）',

    -- 值生成器
    generator_type VARCHAR(20) DEFAULT NULL COMMENT '生成器类型：SEQUENCE/RANDOM/FORMULA/LOOKUP/BONUS_ATTR',
    generator_config TEXT DEFAULT NULL COMMENT '生成器配置（JSON对象）',

    -- 显示配置
    display_order INT DEFAULT 0 COMMENT '显示顺序',
    display_hint VARCHAR(200) DEFAULT NULL COMMENT '输入提示',
    display_group VARCHAR(50) DEFAULT NULL COMMENT '显示分组',

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_template_id (template_id),
    INDEX idx_param_code (param_code),
    INDEX idx_display_order (display_order),
    UNIQUE KEY uk_template_param (template_id, param_code),
    CONSTRAINT fk_param_template FOREIGN KEY (template_id) REFERENCES data_template(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模板参数表';

-- ============================================
-- 视图定义
-- ============================================

-- 字段模式汇总视图
CREATE OR REPLACE VIEW v_field_summary AS
SELECT
    pf.id,
    pf.field_name,
    pf.inferred_type,
    pf.value_domain_type,
    pf.is_bonus_attr,
    pf.occurrence_rate,
    pf.distinct_count,
    ps.mechanism_code,
    ps.mechanism_name
FROM pattern_field pf
JOIN pattern_schema ps ON pf.schema_id = ps.id;

-- 属性使用统计视图
CREATE OR REPLACE VIEW v_attr_usage AS
SELECT
    attr_code,
    attr_name,
    attr_category,
    used_in_items,
    used_in_titles,
    used_in_pets,
    used_in_skills,
    used_in_buffs,
    total_usage,
    CASE
        WHEN total_usage > 100 THEN '高频'
        WHEN total_usage > 20 THEN '中频'
        ELSE '低频'
    END AS usage_level
FROM attr_dictionary
ORDER BY total_usage DESC;

-- 引用关系网络视图
CREATE OR REPLACE VIEW v_ref_network AS
SELECT
    pr.id,
    ps1.mechanism_code AS source_mechanism,
    pr.source_field_name,
    ps2.mechanism_code AS target_mechanism,
    pr.target_field_name,
    pr.ref_type,
    pr.confidence
FROM pattern_ref pr
JOIN pattern_schema ps1 ON pr.source_schema_id = ps1.id
LEFT JOIN pattern_schema ps2 ON pr.target_schema_id = ps2.id;
