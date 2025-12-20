# 动态语义发现系统 - 使用说明

## 概述

动态语义发现系统是AI助手的核心增强功能，能够**自动扫描数据库**，发现所有包含"type"的字段（如`element_type`、`npc_type`、`quality`等），分析其值域，并自动构建自然语言→SQL的语义映射。

**核心价值**：
- 🤖 **自动适应**：无需手工配置，自动理解你的游戏数据结构
- 📊 **智能推断**：自动识别字段的语义类型（元素、品质、等级等）
- 🎯 **精准匹配**：设计师说"紫色品质"，AI自动转换为正确的SQL条件
- 🔄 **动态更新**：数据库变化后重新扫描即可同步

---

## 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                   动态语义发现系统                          │
└─────────────────────────────────────────────────────────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
┌───────────────┐  ┌──────────────────┐  ┌──────────────────┐
│ TypeFieldDiscovery │  │ DynamicSemanticBuilder │  │ GameSemanticEnhancer │
│ (字段发现)     │  │ (映射构建)       │  │ (语义增强)       │
└───────────────┘  └──────────────────┘  └──────────────────┘
        │                    │                    │
        │                    │                    │
        ▼                    ▼                    ▼
┌─────────────────────────────────────────────────────────────┐
│                    预设上下文管理UI                         │
│         SemanticContextManagerStage.java                    │
└─────────────────────────────────────────────────────────────┘
```

---

## 核心组件

### 1. TypeFieldDiscovery（字段发现器）

**文件**: `red.jiuzhou.agent.texttosql.TypeFieldDiscovery`

**功能**：
- 扫描`information_schema`，发现所有type相关字段
- 支持的字段名模式：
  - `%type%`：如`element_type`、`npc_type`、`skill_type`
  - `%kind%`：如`item_kind`
  - `%category%`：如`quest_category`
  - 特定名称：`rank`、`grade`、`level`、`quality`、`rarity`、`element`、`class`
- 分析字段值域（使用`SELECT DISTINCT`）
- 自动推断语义类型：
  - **元素属性**（Element）：fire、water、wind等
  - **品质等级**（Quality）：common、rare、epic等
  - **NPC等级**（NPC Rank）：normal、elite、boss等
  - **职业类别**（Class）：warrior、mage等
  - **布尔标志**（Boolean Flag）：0/1、true/false
  - **通用分类**（Category）

**示例输出**：
```
发现字段: client_skill.element_type
- 字段类型: int(11)
- 语义类型: ELEMENT (元素属性)
- 值域: [1, 2, 3, 4, 5, 6] → [火, 水, 风, 土, 光, 暗]
- 总记录数: 1523
```

---

### 2. DynamicSemanticBuilder（映射构建器）

**文件**: `red.jiuzhou.agent.texttosql.DynamicSemanticBuilder`

**功能**：
- 基于发现的type字段，自动生成语义映射
- 为每个值生成多个自然语言别名：
  ```java
  // 示例：element_type = 1 (火属性)
  关键词: ["1", "火属性", "火系", "火元素"]
  SQL条件: "element_type = 1"
  ```
- 构建**预设上下文**（Preset Context）：
  - 按语义类型分组（元素、品质、NPC等）
  - 按表分组（物品、技能、NPC）
  - 支持设计师启用/禁用

**核心类**：
```java
// 语义映射
SemanticMapping {
    String keyword;        // "紫色品质"
    String sqlCondition;   // "quality = 3 OR quality = 'epic'"
    String tableName;      // "item_armors"
    String columnName;     // "quality"
    boolean enabled;       // 是否启用
}

// 预设上下文
PresetContext {
    String name;           // "品质等级"
    List<SemanticMapping> mappings;
    boolean enabled;
}
```

---

### 3. GameSemanticEnhancer（语义增强器）

**文件**: `red.jiuzhou.agent.texttosql.GameSemanticEnhancer`

**增强内容**：
- **静态语义**：预定义的游戏术语映射（原有功能）
- **动态语义**：从数据库自动发现的映射（新功能）
- 自动合并：动态发现的映射会补充静态语义库
- 生成增强提示词：
  ```markdown
  # 游戏语义映射表（预定义）
  ## 品质/稀有度
  - "紫色品质" → `quality = 3 OR quality = 'epic'`

  ---

  # 动态语义映射（自动发现）
  ## 元素属性
  ### 表: client_skill
  **字段: element_type**
  - "火属性" → `element_type = 1`
  - "水属性" → `element_type = 2`
  ```

**关键方法**：
```java
// 初始化动态语义
initializeDynamicSemantics()

// 合并动态语义到静态库
mergeDynamicSemantics()

// 生成完整提示词
String generateSemanticPrompt()

// 重新加载
reloadDynamicSemantics()
```

---

## 使用指南

### 方式1：自动模式（推荐）

AI助手启动时自动扫描数据库，无需任何配置。

```java
// GameDataAgent.java 初始化时自动调用
agent.initialize(jdbcTemplate);
// → PromptBuilder自动调用 GameSemanticEnhancer
// → GameSemanticEnhancer自动扫描数据库
```

**效果**：
- 用户输入："查询所有火属性技能"
- AI自动理解：`SELECT * FROM client_skill WHERE element_type = 1 LIMIT 20`

---

### 方式2：手动管理模式

设计师可以通过UI查看和管理发现的语义映射。

#### 打开上下文管理器

1. 打开AI对话窗口（点击"💬 AI对话"按钮）
2. 点击工具栏的"🧠 上下文管理"按钮
3. 进入语义上下文管理界面

![上下文管理器](./images/context-manager.png)

#### 界面说明

```
┌─────────────────────────────────────────────────────────────┐
│  🧠 AI上下文管理                                            │
│  [🔄 重新扫描] [📥 导出报告] [➕ 添加自定义]                │
├──────────┬──────────────────────────┬───────────────────────┤
│ 预设上下文 │    语义映射              │    详细信息           │
│          │                          │                       │
│ ☑ 元素属性│ ☑ 火属性 → element_type=1│ === 语义映射详情 ===  │
│   (32)   │ ☑ 水属性 → element_type=2│ 关键词: 火属性        │
│          │ ☑ 风属性 → element_type=3│ SQL: element_type = 1 │
│ ☑ 品质等级│                          │ 表: client_skill      │
│   (25)   │                          │ 字段: element_type    │
│          │                          │ 类型: 🤖 自动发现      │
│ ☑ NPC等级│                          │ 使用次数: 15          │
│   (18)   │                          │                       │
└──────────┴──────────────────────────┴───────────────────────┘
```

#### 操作说明

**1. 启用/禁用预设上下文**
- 左侧列表：点击复选框启用/禁用整个上下文
- 中间表格：单独启用/禁用某个映射

**2. 查看详细信息**
- 点击预设上下文：查看该上下文的概览
- 点击语义映射：查看映射的详细信息和示例查询

**3. 重新扫描**
- 数据库结构变化后点击"🔄 重新扫描"
- 重新发现type字段和值域
- 自动更新语义映射

**4. 导出报告**
- 点击"📥 导出报告"
- 生成type字段发现报告（Markdown格式）
- 显示在右侧详情区

**5. 添加自定义映射**
- 点击"➕ 添加自定义"
- 填写关键词、SQL条件、表名、字段名
- 示例：
  ```
  关键词: 传说武器
  SQL条件: quality = 4 AND item_type = 'weapon'
  表名: item_weapons
  字段名: quality
  描述: 传说品质的武器
  ```

---

## 实际应用示例

### 示例1：元素类型查询

**场景**：游戏有`client_skill`表，`element_type`字段（1=火，2=水，3=风...）

**发现过程**：
```sql
-- 1. 发现字段
SELECT column_name, column_type
FROM information_schema.columns
WHERE table_name = 'client_skill' AND column_name LIKE '%type%'
→ 发现 element_type (int)

-- 2. 分析值域
SELECT element_type, COUNT(*) FROM client_skill GROUP BY element_type
→ 发现值: [1(385条), 2(271条), 3(198条), 4(142条), 5(89条), 6(67条)]

-- 3. 推断语义
基于字段名"element"和值域分布 → 判定为 ELEMENT（元素属性）

-- 4. 生成映射
1 → ["1", "火属性", "火系", "火元素"] → element_type = 1
2 → ["2", "水属性", "水系", "水元素"] → element_type = 2
...
```

**使用效果**：
```
用户: "查询所有火属性技能"
AI理解: element_type = 1
生成SQL: SELECT * FROM client_skill WHERE element_type = 1 LIMIT 20
返回: 385条火属性技能记录 ✅
```

---

### 示例2：品质等级查询

**场景**：`item_armors`表，`quality`字段（0=普通，1=精良，2=稀有，3=史诗，4=传说）

**发现过程**：
```sql
-- 1. 发现字段
SELECT column_name FROM information_schema.columns
WHERE table_name = 'item_armors' AND column_name = 'quality'
→ 发现 quality (int)

-- 2. 分析值域
SELECT quality, COUNT(*) FROM item_armors GROUP BY quality
→ [0(1250), 1(987), 2(654), 3(321), 4(89)]

-- 3. 推断语义
字段名"quality" + 值域0-4 → QUALITY（品质等级）

-- 4. 生成映射
0 → ["0", "普通", "白色品质", "白装"] → quality = 0
3 → ["3", "史诗", "紫色品质", "紫装"] → quality = 3
4 → ["4", "传说", "橙色品质", "橙装"] → quality = 4
```

**使用效果**：
```
用户: "查询所有紫装"
AI理解: quality = 3
生成SQL: SELECT * FROM item_armors WHERE quality = 3 LIMIT 20
返回: 321条史诗品质装备 ✅
```

---

### 示例3：混合条件查询

**场景**：查询"紫色品质的火属性武器"

**AI推理过程**：
```
1. 解析用户意图:
   - "紫色品质" → 检索语义映射 → quality = 3
   - "火属性" → 检索语义映射 → element_type = 1
   - "武器" → 检索表别名 → item_weapons

2. 参考Few-Shot示例:
   "查询所有紫色品质的物品" → SELECT * FROM item_armors WHERE quality = 3

3. 应用语义映射:
   SELECT * FROM item_weapons
   WHERE quality = 3 AND element_type = 1
   LIMIT 20

4. 执行并返回结果
```

---

## 预设上下文分类

系统自动生成以下预设上下文：

| 上下文ID | 名称 | 描述 | 示例映射 |
|---------|------|------|---------|
| `element` | 元素属性 | 包含所有元素相关映射 | 火属性→element_type=1 |
| `quality` | 品质等级 | 包含所有品质相关映射 | 紫装→quality=3 |
| `npc_rank` | NPC等级 | 包含所有NPC等级映射 | BOSS→npc_grade>=3 |
| `class` | 职业类别 | 包含所有职业映射 | 战士→class_id=1 |
| `boolean_flag` | 布尔标志 | 包含所有开关标志 | 启用→is_enabled=1 |
| `item_query` | 物品查询 | 物品表相关所有映射 | 综合物品相关 |
| `skill_query` | 技能查询 | 技能表相关所有映射 | 综合技能相关 |
| `npc_query` | NPC查询 | NPC表相关所有映射 | 综合NPC相关 |

---

## 高级功能

### 1. 自定义映射优先级

- **用户自定义映射**：优先级最高，覆盖自动发现的映射
- **静态预定义映射**：中等优先级
- **动态发现映射**：基础优先级

### 2. 使用统计

系统记录每个映射的使用次数：
```java
SemanticMapping {
    int useCount;  // 使用次数
}
```

高频使用的映射会在Few-Shot示例中优先推荐。

### 3. 失败自动修正

结合`QuerySelfCorrection`（查询自我修正），如果AI生成的SQL执行失败：
```
1. 检测错误类型（表名错误、字段名错误等）
2. 尝试修正（基于编辑距离找相似名称）
3. 重新执行
4. 如果成功，记录反馈优化映射权重
```

---

## 配置和扩展

### 禁用动态语义（如需要）

```java
GameSemanticEnhancer enhancer = new GameSemanticEnhancer(jdbcTemplate);
enhancer.setDynamicSemanticsEnabled(false);  // 禁用动态语义
```

### 添加自定义语义映射

```java
DynamicSemanticBuilder builder = enhancer.getDynamicBuilder();
builder.addUserMapping(
    "神话装备",                           // 关键词
    "quality = 5 OR quality = 'mythic'",  // SQL条件
    "item_armors",                        // 表名
    "quality",                            // 字段名
    "神话品质的装备"                       // 描述
);
```

### 重新加载语义

数据库结构变化后：
```java
enhancer.reloadDynamicSemantics();
```

---

## 性能考虑

### 扫描性能

- **初次扫描**：约5-10秒（取决于表数量和数据量）
- **缓存机制**：扫描结果缓存在内存中
- **增量更新**：仅在手动"重新扫描"时更新

### 优化建议

1. **限制扫描范围**：仅扫描值域较小的字段（≤50个不同值）
2. **异步初始化**：在后台线程初始化动态语义
3. **持久化缓存**：将扫描结果保存到文件，下次启动直接加载

---

## 故障排查

### 问题1：动态语义未生效

**症状**：AI仍然不理解"紫色品质"等术语

**排查**：
```java
// 检查动态语义是否启用
enhancer.getDynamicBuilder() != null

// 查看日志
[INFO] 开始初始化动态语义...
[INFO] 发现 37 个type相关字段
[INFO] 合并了 152 个动态语义映射到静态语义库
```

**解决**：
- 确认数据库连接正常
- 检查是否有权限查询`information_schema`
- 尝试手动重新扫描

### 问题2：语义映射不准确

**症状**：AI将"火属性"映射到错误的值

**排查**：
1. 打开上下文管理器
2. 查看"火属性"的SQL条件
3. 对比数据库实际值

**解决**：
- 禁用错误的自动映射
- 添加正确的自定义映射
- 或修改`DynamicSemanticBuilder.generateKeywords()`逻辑

---

## 开发者指南

### 添加新的语义类型

在`TypeFieldDiscovery.SemanticType`枚举中添加：
```java
public enum SemanticType {
    ELEMENT("元素属性"),
    QUALITY("品质等级"),
    NEW_TYPE("新类型"),  // 新增
    ...
}
```

在`inferSemanticType()`中添加推断逻辑：
```java
if (columnName.contains("new_field_pattern")) {
    return SemanticType.NEW_TYPE;
}
```

在`DynamicSemanticBuilder.generateKeywords()`中添加关键词生成逻辑：
```java
case NEW_TYPE:
    if (lowerValue.equals("value1")) {
        keywords.add("中文别名1");
    }
    break;
```

---

## 未来计划

### 1. 向量数据库集成
使用Embedding模型提升相似度检索：
```
用户: "查询高级装备"
向量检索: 找到最相似的映射 → "史诗品质" (余弦相似度 0.87)
```

### 2. 机器学习优化
从成功查询中自动学习：
```java
exampleLibrary.recordFeedback(userQuery, successfulSql, true);
// 自动提取新的语义映射
```

### 3. 多语言支持
支持英文、日文等其他语言的术语映射。

### 4. Schema关系图谱
构建表间关系图，支持复杂JOIN查询的自动生成。

---

## 总结

动态语义发现系统实现了AI助手从"通用工具"到"游戏数据专家"的转变：

✅ **自动适应**：无需配置，自动理解你的数据库结构
✅ **智能推断**：基于字段名和值域自动识别语义类型
✅ **精准映射**：设计师用自然语言，AI生成准确SQL
✅ **可视化管理**：通过UI查看、启用/禁用语义映射
✅ **持续优化**：记录使用统计，自动调整权重

这是一个**真正理解你的游戏数据**的AI助手！
