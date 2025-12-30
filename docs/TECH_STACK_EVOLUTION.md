# 技术栈演进方案

## 设计原则

1. **降低熵值** - 用成熟方案替代自建轮子
2. **保持兼容** - Java 8 + Spring Boot 2.x 生态
3. **渐进引入** - 不破坏现有功能
4. **实用优先** - 解决实际痛点

---

## 当前技术栈分析

| 领域 | 当前方案 | 问题 |
|------|---------|------|
| JSON | Fastjson 1.2.83 | 历史安全漏洞，不推荐 |
| 搜索 | 自建GlobalSearchEngine | 无索引，大文件慢 |
| 缓存 | 自建HashMap缓存 | 无过期策略，内存泄漏风险 |
| 验证 | 自建Validator | 规则硬编码，不易扩展 |
| 表单 | 手工构建JavaFX | 重复代码多 |
| Diff | 无 | 缺失关键能力 |
| 版本 | 无 | 缺失关键能力 |
| 表达式 | 无 | 批量生成缺少公式计算 |

---

## 推荐引入的开源技术

### 第一层：核心基础设施 (必须引入)

#### 1. Guava - Google核心库
**Stars**: 49.8k+ | **成熟度**: 极高 | **侵入性**: 低

```xml
<dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <version>32.1.3-jre</version>
</dependency>
```

**解决问题**：
| 功能 | 替代现有 | 价值 |
|------|---------|------|
| `LoadingCache` | 自建HashMap缓存 | 自动过期、大小限制、统计 |
| `EventBus` | 自建事件通知 | 解耦组件间通信 |
| `RateLimiter` | 无 | API调用限流 |
| `Preconditions` | 手工校验 | 优雅的参数校验 |
| `ImmutableCollections` | 普通集合 | 线程安全、防误改 |

**示例用法**：
```java
// 缓存：自动加载、10分钟过期、最多1000条
LoadingCache<String, PatternSchema> schemaCache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .build(new CacheLoader<String, PatternSchema>() {
        public PatternSchema load(String key) {
            return schemaDao.findByCode(key);
        }
    });

// 事件总线：解耦数据变更通知
EventBus eventBus = new EventBus();
eventBus.register(new DataChangeListener());
eventBus.post(new DataChangedEvent(tableName, recordId));
```

---

#### 2. Jackson (完整版) - 替代Fastjson
**Stars**: 8.9k+ | **成熟度**: 极高 | **安全性**: 高

```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.15.3</version>
</dependency>
```

**迁移收益**：
- 消除Fastjson安全风险（CVE历史）
- 业界标准，文档丰富
- 更好的类型处理
- 已部分引入，只需统一

**迁移方式**：
```java
// 旧代码
JSON.parseObject(json, MyClass.class);
JSON.toJSONString(obj);

// 新代码
ObjectMapper mapper = new ObjectMapper();
mapper.readValue(json, MyClass.class);
mapper.writeValueAsString(obj);

// 兼容层（渐进迁移）
public class JsonUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static <T> T parse(String json, Class<T> clazz) {
        return MAPPER.readValue(json, clazz);
    }
}
```

---

### 第二层：搜索与索引

#### 3. Apache Lucene (嵌入式) - 本地全文搜索
**Stars**: 2.4k (核心项目) | **成熟度**: 极高 | **Elasticsearch基础**

```xml
<dependency>
    <groupId>org.apache.lucene</groupId>
    <artifactId>lucene-core</artifactId>
    <version>9.9.1</version>
</dependency>
<dependency>
    <groupId>org.apache.lucene</groupId>
    <artifactId>lucene-queryparser</artifactId>
    <version>9.9.1</version>
</dependency>
<dependency>
    <groupId>org.apache.lucene</groupId>
    <artifactId>lucene-analyzers-smartcn</artifactId>
    <version>8.11.2</version> <!-- 中文分词 -->
</dependency>
```

**解决问题**：
- 全局语义搜索（毫秒级）
- 支持中文分词
- 模糊匹配、拼音搜索
- 嵌入式，无需部署服务

**架构设计**：
```
┌─────────────────────────────────────────────┐
│           SearchIndexService                │
├─────────────────────────────────────────────┤
│  indexXmlRecord(file, record)               │
│  search(query) → List<SearchHit>            │
│  rebuildIndex()                             │
└─────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────┐
│           Lucene Index                      │
│  ┌─────────────────────────────────────┐    │
│  │ Document                            │    │
│  │  - id: "100001"                     │    │
│  │  - name: "烈焰之剑"                 │    │
│  │  - name_pinyin: "lieyanzhijian"     │    │
│  │  - mechanism: "ITEM"                │    │
│  │  - file: "item_templates.xml"       │    │
│  │  - content: "全文内容..."           │    │
│  └─────────────────────────────────────┘    │
└─────────────────────────────────────────────┘
```

---

#### 4. H2 Database - 嵌入式索引库
**Stars**: 4.1k+ | **成熟度**: 极高

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <version>2.2.224</version>
</dependency>
```

**用途**：
- 本地元数据缓存
- 搜索索引持久化
- 操作历史存储
- 脱机工作支持

**与MySQL分工**：
| 数据类型 | 存储位置 | 原因 |
|---------|---------|------|
| 游戏配置数据 | MySQL | 服务端同步 |
| 搜索索引 | H2 | 本地加速 |
| 操作历史 | H2 | 本地审计 |
| 用户偏好 | H2 | 本地配置 |

---

### 第三层：版本控制与Diff

#### 5. JGit - Eclipse Git实现
**Stars**: 1.5k (Eclipse项目) | **成熟度**: 极高

```xml
<dependency>
    <groupId>org.eclipse.jgit</groupId>
    <artifactId>org.eclipse.jgit</artifactId>
    <version>6.7.0.202309050840-r</version>
</dependency>
```

**解决问题**：
- 配置文件版本管理
- 变更历史追踪
- Diff对比
- 分支管理
- 回滚能力

**架构设计**：
```java
// 每个机制一个Git仓库
public class ConfigVersionManager {
    private Repository repository;

    // 保存配置变更
    public void commit(String file, String message, String author) {
        Git git = new Git(repository);
        git.add().addFilepattern(file).call();
        git.commit()
            .setMessage(message)
            .setAuthor(author, "designer@game.com")
            .call();
    }

    // 获取文件历史
    public List<RevCommit> getHistory(String file) {
        return git.log().addPath(file).call();
    }

    // Diff对比
    public String diff(String file, String oldCommit, String newCommit) {
        // 返回统一Diff格式
    }

    // 回滚到指定版本
    public void revert(String file, String commitId) {
        git.checkout()
            .setStartPoint(commitId)
            .addPath(file)
            .call();
    }
}
```

---

#### 6. java-diff-utils - 文本Diff
**Stars**: 1.2k | **成熟度**: 高 | **轻量**

```xml
<dependency>
    <groupId>io.github.java-diff-utils</groupId>
    <artifactId>java-diff-utils</artifactId>
    <version>4.12</version>
</dependency>
```

**用途**：内存中的快速Diff（不需要Git仓库时）

```java
// 生成Diff
List<String> original = Files.readAllLines(oldFile);
List<String> revised = Files.readAllLines(newFile);
Patch<String> patch = DiffUtils.diff(original, revised);

// 生成统一Diff格式
List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(
    "old.xml", "new.xml", original, patch, 3);
```

---

### 第四层：表达式与规则引擎

#### 7. Spring Expression Language (SpEL)
**Stars**: 55k+ (Spring Framework) | **已包含**

**解决问题**：批量生成时的公式计算

```java
// 批量生成时的公式
ExpressionParser parser = new SpelExpressionParser();

// 等级公式: 基础值 + 5*序号
Expression levelExpr = parser.parseExpression("baseLevel + 5 * index");

// 攻击力公式: 基础值 * 1.1^序号
Expression attackExpr = parser.parseExpression("baseAttack * T(Math).pow(1.1, index)");

// 计算
StandardEvaluationContext context = new StandardEvaluationContext();
context.setVariable("baseLevel", 45);
context.setVariable("baseAttack", 150);
context.setVariable("index", 3);

int level = levelExpr.getValue(context, Integer.class);  // 60
int attack = attackExpr.getValue(context, Integer.class); // 200
```

---

#### 8. Easy Rules - 轻量规则引擎
**Stars**: 4.8k | **成熟度**: 高 | **轻量**

```xml
<dependency>
    <groupId>org.jeasy</groupId>
    <artifactId>easy-rules-core</artifactId>
    <version>4.1.0</version>
</dependency>
<dependency>
    <groupId>org.jeasy</groupId>
    <artifactId>easy-rules-spel</artifactId>
    <version>4.1.0</version>
</dependency>
```

**用途**：数据验证规则、自动修复规则

```java
// 定义验证规则
@Rule(name = "物品等级检查", description = "物品等级必须在1-80之间")
public class ItemLevelRule {
    @Condition
    public boolean when(@Fact("item") Item item) {
        return item.getLevel() < 1 || item.getLevel() > 80;
    }

    @Action
    public void then(@Fact("item") Item item, @Fact("errors") List<String> errors) {
        errors.add("物品[" + item.getId() + "]等级超出范围: " + item.getLevel());
    }
}

// 使用
Rules rules = new Rules(new ItemLevelRule(), new ItemQualityRule(), ...);
RulesEngine engine = new DefaultRulesEngine();
engine.fire(rules, facts);
```

---

### 第五层：UI增强

#### 9. RichTextFX - 代码编辑器
**Stars**: 1.2k | **JavaFX专用**

```xml
<dependency>
    <groupId>org.fxmisc.richtext</groupId>
    <artifactId>richtextfx</artifactId>
    <version>0.11.2</version>
</dependency>
```

**用途**：XML/JSON编辑器增强
- 语法高亮
- 行号显示
- 代码折叠
- 自动缩进
- 括号匹配

---

#### 10. FormsFX - 动态表单
**Stars**: 600+ | **JavaFX专用**

```xml
<dependency>
    <groupId>com.dlsc.formsfx</groupId>
    <artifactId>formsfx-core</artifactId>
    <version>11.6.0</version>
</dependency>
```

**注意**：FormsFX需要Java 11+，如需Java 8兼容，可自建轻量表单框架。

**替代方案 - 自建动态表单**：
```java
// 基于PatternField自动生成表单控件
public class DynamicFormBuilder {
    public Node buildField(PatternField field) {
        switch (field.getInferredType()) {
            case INTEGER:
                return createNumberField(field);
            case ENUM:
                return createComboBox(field);
            case BOOLEAN:
                return createCheckBox(field);
            case REFERENCE:
                return createReferenceSelector(field);
            case BONUS_ATTR:
                return createBonusAttrEditor(field);
            default:
                return createTextField(field);
        }
    }
}
```

---

## 引入优先级

### Phase 1 - 立即引入 (低风险高收益)

| 技术 | Stars | 作用 | 工作量 |
|------|-------|------|--------|
| **Guava** | 49.8k | 缓存、事件、工具 | 1天 |
| **Jackson完整版** | 8.9k | 替代Fastjson | 2天 |
| **java-diff-utils** | 1.2k | 变更对比 | 1天 |

### Phase 2 - 短期引入 (中等风险)

| 技术 | Stars | 作用 | 工作量 |
|------|-------|------|--------|
| **H2 Database** | 4.1k | 本地索引库 | 2天 |
| **Lucene** | 核心项目 | 全文搜索 | 3天 |
| **SpEL增强使用** | 已有 | 公式计算 | 1天 |

### Phase 3 - 中期引入 (需要设计)

| 技术 | Stars | 作用 | 工作量 |
|------|-------|------|--------|
| **JGit** | 1.5k | 版本管理 | 5天 |
| **Easy Rules** | 4.8k | 验证规则引擎 | 3天 |
| **RichTextFX** | 1.2k | 代码编辑器 | 2天 |

---

## 架构演进图

```
┌─────────────────────────────────────────────────────────────────┐
│                        设计师工作台 UI                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ 智能搜索     │  │ 表单编辑器   │  │ Diff视图     │          │
│  │ (Lucene)     │  │ (动态生成)   │  │ (diff-utils) │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
├─────────────────────────────────────────────────────────────────┤
│                        服务层                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ SearchService│  │ ValidationSvc│  │ VersionSvc   │          │
│  │ (Lucene)     │  │ (Easy Rules) │  │ (JGit)       │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ CacheService │  │ EventBus     │  │ ExpressionSvc│          │
│  │ (Guava)      │  │ (Guava)      │  │ (SpEL)       │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
├─────────────────────────────────────────────────────────────────┤
│                        数据层                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ MySQL        │  │ H2 (本地)    │  │ Git仓库      │          │
│  │ 游戏配置     │  │ 索引/历史    │  │ 版本管理     │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘
```

---

## 预期收益

| 指标 | 当前 | 引入后 |
|------|------|--------|
| 全局搜索速度 | 秒级 | 毫秒级 |
| 缓存命中率 | 无统计 | 可监控 |
| 安全漏洞风险 | Fastjson | 消除 |
| 代码重复度 | 高 | 降低40% |
| 版本回滚能力 | 无 | 完整 |
| 验证规则扩展 | 硬编码 | 配置化 |

---

## 不推荐引入的技术

| 技术 | 原因 |
|------|------|
| Elasticsearch | 过重，需要独立部署 |
| Drools | 过于复杂，学习成本高 |
| 任何Java 11+专属库 | 破坏兼容性 |
| 重量级ORM (如Hibernate) | 现有Spring JDBC足够 |
