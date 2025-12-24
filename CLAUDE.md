# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Git 工作流规范

### 提交规范（重要）
遇到设计问题，永远站在游戏设计师的角度考虑问题，以方便设计师对游戏进行改造为重要目的。
积累多个修改后再一次性提交。

```bash
git add .
git commit -m "feat: 简短描述修改内容"

# 推送到 GitHub
git push axmltools clean-main:main
```

### 提交消息格式

| 前缀 | 用途 |
|------|------|
| `feat:` | 新功能 |
| `fix:` | Bug修复 |
| `refactor:` | 代码重构 |
| `docs:` | 文档更新 |
| `style:` | 代码格式调整 |
| `chore:` | 构建/配置变更 |

### GitHub 仓库

- **远程仓库**: https://github.com/xiaohan1105/axmltools
- **远程名称**: `axmltools`
- **工作分支**: `clean-main`（无敏感历史记录）
- **推送命令**: `git push axmltools clean-main:main`

**重要安全提示**：
- 使用 SSH 或 Personal Access Token (PAT) 进行身份验证
- 如果使用 PAT，通过 Git Credential Manager 安全存储，避免在远程 URL 中明文包含 token
- 推荐使用 SSH 密钥: `git remote set-url axmltools git@github.com:xiaohan1105/axmltools.git`

### 敏感信息处理

- **禁止**将 API Key、密码等敏感信息提交到代码中
- 使用环境变量占位符：`${ENV_VAR:default-value}`
- 示例：`apikey: ${AI_QWEN_APIKEY:your-api-key}`

---

## 项目概述

dbxmlTool 是一个游戏配置数据管理工具，用于 MySQL 数据库与 XML 文件之间的双向转换。基于 JavaFX 构建桌面 GUI，集成多个 AI 服务用于数据智能处理和翻译。

**主要功能**：
- 数据库 ↔ XML 双向转换（支持多线程分页、事务处理）
- Aion游戏机制可视化浏览器（27个机制分类）
- AI智能对话代理（自然语言查询和修改游戏数据）
- AI驱动的数据分析和设计洞察
- 主题系统和批量转换（支持AI辅助改写）
- 关系分析和依赖图谱

## 构建和运行命令

**主类入口**: `red.jiuzhou.ui.Dbxmltool`

```bash
# 编译项目
mvnd clean compile

# 运行应用（JavaFX 应用）
mvnd exec:java

# 打包（包含依赖的 fat jar）
mvnd clean package

# 运行测试
mvnd test

# 运行单个测试类
mvnd test -Dtest=YourTestClassName

# 运行单个测试方法
mvnd test -Dtest=YourTestClassName#testMethodName

# 如果系统没有安装 mvnd，可以使用标准 Maven（速度较慢）
mvn clean compile
mvn exec:java
mvn clean package
```

**注意**: 推荐使用 `mvnd` (Maven Daemon) 以获得更快的构建速度。如果未安装，可访问 https://github.com/apache/maven-mvnd 下载。

## 技术栈

| 层级 | 技术 |
|-----|------|
| 应用框架 | Spring Boot 2.7.18 |
| GUI框架 | JavaFX (JFoenix 8.0.10, ControlsFX 8.40.12) |
| 数据库 | MySQL 8.0 + Spring JDBC |
| XML处理 | Dom4j 2.1.3 |
| 配置管理 | YAML (SnakeYAML, Jackson) |
| JSON处理 | Fastjson 1.2.83 |
| 日志 | SLF4j + Logback |
| 工具库 | Hutool 5.3.9 |
| AI服务 | DashScope SDK 2.21.0, 火山引擎 SDK |
| 翻译 | 阿里云翻译API |
| 构建工具 | Maven (推荐 mvnd) |
| Java版本 | Java 8 (1.8) |

## 核心架构

### 包结构概览

```
red.jiuzhou
├── agent/            # AI智能对话代理系统（新）
│   ├── core/         # 代理核心（会话管理、消息处理、Prompt构建）
│   ├── tools/        # 工具集（查询、修改、分析、历史记录）
│   ├── execution/    # 操作执行引擎
│   ├── security/     # SQL安全过滤
│   ├── history/      # 操作日志
│   └── ui/           # 对话界面（AgentChatStage）
├── ai/               # AI模型集成（4个服务商）
├── analysis/         # 数据分析引擎
│   ├── enhanced/     # AI增强分析
│   └── aion/         # Aion游戏专用分析
│       ├── AionMechanismCategory.java   # 27个机制分类枚举
│       ├── AionMechanismDetector.java   # 机制检测器
│       ├── XmlFieldParser.java          # XML字段解析器
│       ├── DetectionResult.java         # 检测结果
│       ├── AionMechanismView.java       # 视图模型
│       ├── IdNameResolver.java          # ID到NAME转换缓存
│       └── mechanism/                   # 机制关系图（节点、边、图）
├── api/              # REST API接口
│   └── common/       # 通用模型
├── dbxml/            # 数据库与XML双向转换（核心）
├── relationship/     # 关系分析
├── tabmapping/       # 表映射管理
├── theme/            # 主题管理系统
│   └── rules/        # 转换规则
├── ui/               # JavaFX用户界面
│   ├── features/     # 特性注册系统
│   ├── mapping/      # 表映射UI
│   └── components/   # UI增强组件（状态栏、搜索树、快捷键）
├── util/             # 工具类库
└── xmltosql/         # XML到SQL/DDL转换
```

### AI智能对话代理系统 (`red.jiuzhou.agent`)

基于Tool Calling的游戏数据智能对话系统，支持自然语言查询和修改游戏配置数据。

**核心组件**：
- `GameDataAgent.java` - 对话代理核心引擎
- `ConversationManager.java` - 会话管理（维护多轮对话上下文）
- `PromptBuilder.java` - 动态Prompt构建器
- `SchemaMetadataService.java` - 数据库Schema元数据服务
- `ToolRegistry.java` - 工具注册中心（查询、修改、分析、历史）
- `SqlSecurityFilter.java` - SQL安全过滤器（防止危险操作）
- `OperationLogger.java` - 操作审计日志
- `AgentChatStage.java` - 对话界面窗口

**典型对话示例**：
- "查询所有稀有度大于4的物品" → 自动生成SQL查询
- "将物品1000的名称改为'神器'" → 生成UPDATE语句并执行
- "分析技能表的数据分布" → 调用分析工具

### Aion机制浏览器 (`red.jiuzhou.analysis.aion`)

专为Aion游戏设计的机制分类和可视化工具。

**核心类**：
- `AionMechanismCategory.java` - 27个机制分类枚举（定义正则匹配模式、优先级、颜色和图标）
- `AionMechanismDetector.java` - 机制检测器（包含文件夹级别映射 `folderMappings`）
- `MechanismOverrideConfig.java` - 手动覆盖配置加载器（v2.0新增）
- `XmlFieldParser.java` - XML字段解析器
- `IdNameResolver.java` - ID到NAME转换缓存服务
- `MechanismRelationshipService.java` - 机制间依赖关系分析

**三层级导航**：机制层（27个系统卡片）→ 文件层 → 字段层

**字段引用检测**：自动识别 `item_id`、`npc_id`、`skill_id`、`quest_id` 等字段的跨表引用关系

**混合配置系统（v2.0）**：
- **自动预归类** - 多层检测策略（文件夹/精确/正则）智能识别文件机制
- **手动覆盖** - 设计师可通过 `mechanism_manual_overrides.yml` 调整分类
- **优先级** - 手动覆盖(0.99) > 排除列表(0.95) > 自动检测(0.3-0.98)
- **无需编译** - 修改配置文件后重启应用即可生效
- **详细文档** - 参见 `docs/MECHANISM_DYNAMIC_CLASSIFICATION.md`

### 数据转换层 (`red.jiuzhou.dbxml`)

核心模块，处理数据库与XML的双向转换。

| 类名 | 职责 |
|-----|------|
| `DbToXmlGenerator` | 数据库导出为XML，多线程分页处理 |
| `XmlToDbGenerator` | XML导入到数据库，支持事务和批量操作 |
| `WorldDbToXmlGenerator` | World类型数据的特殊导出处理 |
| `WorldXmlToDbGenerator` | World类型数据的特殊导入处理 |
| `TableConf` / `TabConfLoad` | 表配置定义和加载 |
| `TableForestBuilder` | 构建表的父子层级关系树 |

### UI层 (`red.jiuzhou.ui`)

基于JavaFX的桌面应用界面。

| 类名 | 职责 |
|-----|------|
| `Dbxmltool` | 主应用入口（Spring Boot + JavaFX） |
| `MenuTabPaneExample` | 左侧目录树和Tab页管理 |
| `AionMechanismExplorerStage` | Aion机制浏览器窗口 |
| `DesignerInsightStage` | 设计洞察窗口 |
| `ThemeStudioStage` | 主题工作室窗口 |
| `AgentChatStage` | AI对话代理窗口 |
| `GameToolsStage` | 游戏工具集窗口 |

**工具栏按钮**：
- `🎮 机制浏览器` - 打开Aion机制浏览器
- `📊 设计洞察` - 打开设计洞察分析
- `💬 AI对话` - 打开智能对话代理

**UI增强组件 (`ui.components`)**：
- `EnhancedStatusBar` - 增强状态栏（显示任务进度、资源使用）
- `HotkeyManager` - 全局快捷键管理器
- `SearchableTreeView` - 可搜索的树形视图

**特性系统 (`ui.features`)**：
- `FeatureRegistry.defaultRegistry()` - 特性注册中心，注册所有可启动的功能模块
- `FeatureDescriptor` - 特性描述符（id、名称、描述、分类、启动器）
- `FeatureCategory` - 特性分类枚举
- `StageFeatureLauncher` - Stage窗口启动器实现
- `FeatureTaskExecutor` - 特性任务执行器（后台任务管理）

### AI服务层 (`red.jiuzhou.ai`)

集成多个AI服务提供商。

| 类名 | 职责 |
|-----|------|
| `AiModelFactory` | AI模型工厂（工厂模式） |
| `TongYiClient` | 通义千问客户端 |
| `DoubaoClient` | 豆包AI客户端 |
| `KimiClient` | Kimi AI客户端 |
| `DeepSeekClient` | DeepSeek AI客户端 |

## 配置文件

### 环境配置说明

项目使用 `application.yml` 作为主配置文件，但该文件包含敏感信息（数据库密码、API密钥），已加入 `.gitignore`。

**首次运行配置步骤**：
1. 复制 `src/main/resources/application.yml.example` 为 `application.yml`
2. 修改数据库连接信息（url、username、password）
3. 配置AI服务的API密钥（支持环境变量）
4. 配置Aion游戏数据路径（xmlPath、localizedPath）

### application.yml 关键配置

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/xmldb_suiyue?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: "your-password"  # 修改为实际密码

# AI服务配置（推荐使用环境变量）
ai:
  qwen:
    apikey: ${AI_QWEN_APIKEY:your-qwen-api-key}
    model: qwen-plus
  doubao:
    apikey: ${AI_DOUBAO_APIKEY:your-doubao-api-key}
    model: doubao-seed-1-6-250615
  kimi:
    apikey: ${AI_KIMI_APIKEY:your-kimi-api-key}
    model: Moonshot-Kimi-K2-Instruct
  deepseek:
    apikey: ${AI_DEEPSEEK_APIKEY:your-deepseek-api-key}
    model: deepseek-r1

# Aion XML路径配置
aion:
  xmlPath: D:\AionReal58\AionMap\XML
  localizedPath: D:\AionReal58\AionMap\XML\China

# 翻译服务配置（阿里云）
ALIYUN:
  ACCESS_KEY_ID: ${ALIYUN_ACCESS_KEY_ID:your_access_key_id}
  ACCESS_KEY_SECRET: ${ALIYUN_ACCESS_KEY_SECRET:your_access_key_secret}
```

**配置优先级**：环境变量 > application.yml 中的默认值

## 数据流

```
XML文件 ←→ XmlToDbGenerator/DbToXmlGenerator ←→ MySQL数据库
                     ↓
           Analysis Engine（统计分析 + AI增强）
                     ↓
           Aion Mechanism Explorer（机制可视化）
                     ↓
           Designer Insights（策划洞察）
```

## 编码规范

- 所有代码文件使用 **UTF-8** 编码
- 使用中文注释和日志
- 遵循 Spring Boot 和 JavaFX 最佳实践
- 敏感配置使用环境变量注入
- **Java 8兼容**：不使用Java 9+特性（如String.repeat()）

## 常见开发场景

### 添加新的游戏机制分类

1. 在 `AionMechanismCategory.java` 枚举中添加新分类
2. 配置正则匹配模式、优先级、颜色和图标
3. 如需文件夹级别匹配，在 `AionMechanismDetector.java` 的 `folderMappings` 中添加

### 添加新的特性模块

1. 在 `FeatureRegistry.defaultRegistry()` 中注册新特性（位于 `Dbxmltool.java`）
2. 创建对应的 Stage 类（继承 `javafx.stage.Stage`）
3. 实现 `FeatureLauncher` 接口或使用 `StageFeatureLauncher`
4. 配置 `FeatureDescriptor`（id、名称、描述、分类、图标）

### 添加新的AI模型

1. 在 `red.jiuzhou.ai` 包下创建新的 Client 类（实现 `AiModelClient` 接口）
2. 在 `AiModelFactory.getClient()` 中添加创建逻辑
3. 在 `application.yml` 中添加配置项（使用环境变量占位符）
4. 更新 `application.yml.example` 模板文件

### 添加Agent工具

1. 在 `red.jiuzhou.agent.tools` 包下创建新的工具类（实现 `AgentTool` 接口）
2. 在 `ToolRegistry` 中注册新工具
3. 实现 `execute()` 方法，定义工具的参数Schema和执行逻辑
4. 在 `PromptBuilder` 中添加工具描述（用于Tool Calling）

## 关键配置文件

| 文件 | 用途 |
|------|------|
| `src/main/resources/application.yml` | 主配置文件（数据库连接、AI服务、路径配置）**不提交到Git** |
| `src/main/resources/application.yml.example` | 配置模板（无敏感信息，提交到Git） |
| `src/main/resources/CONF/` | 表映射配置目录（YAML格式） |
| `src/main/resources/LeftMenu.json` | 左侧目录树结构配置（动态生成，不提交） |
| `src/main/resources/logback-spring.xml` | 日志配置（SLF4j + Logback） |
| `src/main/resources/tabMapping.json` | 表映射关系定义 |
| `.gitignore` | Git忽略规则（已配置忽略 application.yml、日志文件等） |

**首次克隆后的配置步骤**：
```bash
# 1. 复制配置模板
cp src/main/resources/application.yml.example src/main/resources/application.yml

# 2. 编辑 application.yml，填入实际的数据库密码、API密钥和路径
# 3. 编译并运行
mvnd clean compile
mvnd exec:java
```

## 安全审计

**操作日志**：
- Agent系统的所有数据修改操作均记录在 `audit.log` 中
- 日志格式：`[时间] [用户] [操作类型] [SQL语句] [影响行数]`
- 日志文件不提交到Git（已在 `.gitignore` 中配置）

**SQL安全过滤**：
- `SqlSecurityFilter.java` 拦截危险SQL操作（DROP、TRUNCATE等）
- 仅允许SELECT、INSERT、UPDATE、DELETE操作
- Agent修改操作需经过安全检查

## 文档

- `docs/MECHANISM_EXPLORER_GUIDE.md` - 机制浏览器使用指南
- `CLAUDE.md` - 本文件，为AI助手提供项目上下文
