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
- **数据库 ↔ XML 双向转换**（支持多线程分页、事务处理）
  - ✨ **透明编码转换层**：自动检测并保持原始编码（UTF-16/UTF-8），确保导入导出往返一致性（MD5验证）
  - 支持 BOM 标记自动识别和恢复
  - 智能降级策略（历史记录/表级默认/扩展名推断）
  - 元数据缓存优化（性能提升30-50%）
- Aion游戏机制可视化浏览器（27个机制分类）
- AI智能对话代理（自然语言查询和修改游戏数据）
- AI驱动的数据分析和设计洞察
- 主题系统和批量转换（支持AI辅助改写）
- 关系分析和依赖图谱

## 环境要求

**必须安装 JDK 25 LTS**

```bash
# Windows 安装 JDK 25 (使用管理员 PowerShell)
.\scripts\install-jdk25.ps1

# 或手动下载安装
# https://adoptium.net/temurin/releases/?version=25

# 验证安装
java -version
# 应显示: openjdk version "25.x.x"
```

## 构建和运行命令

**主类入口**: `red.jiuzhou.ui.Dbxmltool`

### 快速启动（推荐）

**Windows 用户**：双击运行启动脚本

```bash
# 直接运行（已配置编码、环境检查、错误诊断）
run.bat
```

**脚本特性**：
- ✅ 自动设置 UTF-8 编码（解决中文乱码）
- ✅ 检查 Java 和 Maven 环境
- ✅ 显示版本信息
- ✅ 错误诊断提示
- ✅ 无论成功失败都保持窗口打开

### 手动启动

**重要提示**：必须设置 `JAVA_HOME` 环境变量指向 JDK 25 安装目录。

```bash
# 设置环境变量（Windows CMD）
set JAVA_HOME=D:\jdk-25.0.1.8-hotspot
set PATH=%JAVA_HOME%\bin;D:\develop\apache-maven-3.9.9\bin;%PATH%

# 设置环境变量（Windows PowerShell）
$env:JAVA_HOME="D:\jdk-25.0.1.8-hotspot"
$env:PATH="$env:JAVA_HOME\bin;D:\develop\apache-maven-3.9.9\bin;$env:PATH"

# 设置环境变量（Linux/Mac）
export JAVA_HOME="/path/to/jdk-25"
export PATH="$JAVA_HOME/bin:$PATH"

# 编译项目
mvn clean compile

# 运行应用（推荐方式 - JavaFX 插件）
mvn javafx:run

# 打包（包含依赖的 fat jar）
mvn clean package

# 运行测试
mvn test

# 运行单个测试类
mvn test -Dtest=YourTestClassName

# 运行单个测试方法
mvn test -Dtest=YourTestClassName#testMethodName
```

### 启动脚本说明

**run.bat** - 统一启动脚本（已整合所有功能）

**功能特性**：
1. **编码设置** - `chcp 65001` 切换到 UTF-8，解决中文乱码
2. **环境检查** - 验证 Java 和 Maven 可用性
3. **版本显示** - 显示 Java 和 Maven 版本信息
4. **错误诊断** - 启动失败时提供常见问题排查提示
5. **窗口保持** - 成功或失败都会 `pause`，不会闪退

**已修复问题**：
- ✅ 修复闪退问题（确保所有退出路径都有 `pause`）
- ✅ 解决中文乱码（设置 UTF-8 编码和 Maven 输出编码）
- ✅ 统一启动脚本（删除 run-simple.bat 和 run-debug.bat）

**已知问题**：
- 如果使用 `mvnd`，可能会遇到 JDK 路径配置问题。建议使用标准 `mvn` 命令。
- 项目的 `.mvn/jvm.config` 文件可能会干扰运行，如遇问题可临时重命名该文件。
- 首次运行需要下载依赖，可能需要几分钟时间。

## 技术栈

| 层级 | 技术 |
|-----|------|
| **Java版本** | **Java 25 (LTS)** |
| GUI框架 | **OpenJFX 25** (JFoenix 9.0.10, ControlsFX 11.2.1) |
| 应用框架 | **Spring Boot 4.0.1** (Spring Framework 7) |
| 数据库 | MySQL 9.1 + Spring JDBC |
| XML处理 | Dom4j 2.1.4 |
| 配置管理 | YAML (SnakeYAML 2.3, Jackson 2.18) |
| JSON处理 | Fastjson2 2.0.53 |
| 日志 | SLF4j 2.0.16 + Logback |
| 工具库 | Hutool 5.8.32 |
| AI服务 | DashScope SDK 2.21.0, 火山引擎 SDK |
| 翻译 | 阿里云翻译API |
| 构建工具 | Maven (推荐 mvnd) |

**OpenJFX 来源**: https://github.com/openjdk/jfx

## 核心架构

### Spring Boot + JavaFX 集成方式

**双初始化模式**：
- `Dbxmltool` 类同时继承 `javafx.application.Application` 和使用 `@SpringBootApplication`
- `init()` 方法：在 JavaFX 启动阶段初始化 Spring 容器（使用 `SpringApplicationBuilder`）
- `start(Stage)` 方法：在 Spring 上下文就绪后构建 JavaFX UI，从容器中获取 Bean

**启动流程**：
```
JavaFX Application.launch()
    ↓
init() → Spring 容器初始化
    ↓
start(Stage) → UI 构建（从容器获取 AIAssistant Bean）
    ↓
创建工具栏、菜单树、分割面板
```

**关键特点**：
- 使用虚拟线程处理分页数据（`Executors.newVirtualThreadPerTaskExecutor()`，Java 21+ 特性）
- 依赖注入与 Bean 获取贯穿整个应用生命周期
- `scanBasePackages` 精确控制组件扫描范围

### 数据转换核心机制

**MySQL ↔ XML 双向转换**使用多线程分页策略：

**导出（DbToXmlGenerator）**：
- 分页大小：1000 条记录/页
- 虚拟线程并行处理（Java 25 特性）
- 临时 XML 文件合并为最终产物
- `SubTablePreloader` 预加载子表数据
- 支持 World 特殊表处理（递归 XML 结构）

**导入（XmlToDbGenerator）**：
- Spring 事务处理支持
- TreeMap 排序子表（按长度和字典序）
- 动态 SQL 生成与批量插入
- AI 翻译集成（`DashScopeBatchHelper`）

**数据流**：
```
XML 文件 → Dom4j 解析 → Map<Field, Value>
    ↓
SQL 生成 → INSERT/UPDATE SQL
    ↓
MySQL 数据库（Spring JDBC）
    ↓
缓存 → IdNameResolver（ID到NAME转换缓存）
```

### AI 服务集成架构

**工厂模式 + 模板方法**：
- `AiModelFactory` - 创建 AI 客户端实例
- `BaseDashScopeClient` - 公共实现（模板方法）
- 各客户端（TongYiClient、DoubaoClient、KimiClient、DeepSeekClient）仅覆盖 `buildMessages()` 方法

**AI 调用流程**：
```
GameDataAgent.chat(prompt)
    ↓
PromptBuilder.buildSystemPrompt() → 包含 Schema、工具、游戏语义
    ↓
AiModelFactory.getClient(modelName)
    ↓
BaseDashScopeClient.sendRequest(messages)
    ↓
ResponseParser.parse() → 工具调用指令
    ↓
ToolRegistry.execute() → SQL 执行
```

**配置系统**：使用 Spring `@ConfigurationProperties` 绑定 YAML 配置，支持环境变量占位符 `${AI_QWEN_APIKEY:default}`

### 特性注册系统（声明式模块管理）

**核心组件**：
- `FeatureDescriptor` - Java Record 描述特性（id、名称、描述、分类、启动器）
- `FeatureRegistry` - 中央注册表（单例）
- `StageFeatureLauncher` - Stage 窗口启动策略（带缓存、线程安全）

**8 个内置特性**：
1. 设计洞察（DesignerInsightStage）
2. Aion 机制浏览器（AionMechanismExplorerStage）- 27 个系统分类
3. 机制关系图（MechanismRelationshipStage）- 力导向布局
4. 主题工作室（ThemeStudioStage）
5. AI 数据助手（AgentChatStage）- Tool Calling
6. 刷怪工具（GameToolsStage）
7. 设计规则（DesignRuleStage）
8. 配置管理（ConfigEditorStage）

**添加新特性只需**：在 `FeatureRegistry.defaultRegistry()` 中注册新的 `FeatureDescriptor`

### 配置加载三层架构

1. **YAML 配置**（application.yml）- Spring Boot 主配置
2. **@ConfigurationProperties 绑定** - 类型安全的配置类（`AiProperties`、`AionProperties`）
3. **YamlUtils 动态读取** - `getProperty(String key)` 方法

**配置优先级**：环境变量 > application.yml > 类默认值

### 包结构概览

```
red.jiuzhou
├── agent/            # AI智能对话代理系统（Tool Calling）
│   ├── core/         # 代理核心（会话管理、消息处理、Prompt构建）
│   ├── tools/        # 工具集（查询、修改、分析、历史记录）
│   ├── execution/    # 操作执行引擎
│   ├── security/     # SQL安全过滤
│   └── ui/           # 对话界面（AgentChatStage）
├── ai/               # AI模型集成（工厂模式 + 模板方法）
├── analysis/aion/    # Aion游戏专用分析（27个机制分类）
├── dbxml/            # 数据库与XML双向转换（核心，多线程分页）
├── ui/               # JavaFX用户界面
│   ├── features/     # 特性注册系统（声明式模块管理）
│   └── components/   # UI增强组件（状态栏、搜索树、快捷键）
├── config/           # Spring配置类（@ConfigurationProperties）
└── util/             # 工具类库
```

### Agent 工具注册系统

**ToolRegistry** - 枚举单例模式，管理所有可用工具：

**注册的工具**：
1. **QueryTool** - 查询（SELECT）
2. **ModifyTool** - 修改（UPDATE/INSERT）
3. **AnalyzeTool** - 数据分析
4. **HistoryTool** - 操作历史回溯
5. **SqlExecutionTool** - 直接 SQL 执行（带安全检查）

**工作流程示例**（用户："查询所有 50 级以上的武器"）：
```
用户输入 (AgentChatStage)
    ↓
GameDataAgent.chat(message)
    ↓
PromptBuilder.buildSystemPrompt()
    → 包含：表 Schema + 工具说明 + 游戏语义映射 + SQL 示例库
    ↓
AiModelFactory.getClient("qwen") → TongYiClient.call()
    ↓
DashScope API 调用 → 解析响应 → 工具调用指令
    ↓
ToolRegistry.find("query").execute(sql)
    ↓
JdbcTemplate.query() → 返回数据
    ↓
OperationLogger.log() → UI 显示结果
```

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

### 关键设计模式

| 模式 | 使用场景 | 实现类 |
|-----|--------|-------|
| **工厂模式** | AI 模型创建 | AiModelFactory, StageFeatureLauncher |
| **单例模式** | 注册表、日志、缓存 | ToolRegistry(枚举), IdNameResolver |
| **模板方法** | AI 客户端公共逻辑 | BaseDashScopeClient |
| **策略模式** | 不同表的 XML 转换 | DbToXmlGenerator, XmlToDbGenerator |
| **观察者模式** | 字段搜索过滤、UI 刷新 | SearchableTreeView |
| **责任链** | SQL 安全过滤 | SqlSecurityFilter → OperationExecutor |

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

## 重要实现细节

### 启动日志关键信息

成功启动时会看到以下日志：
```
Spring Boot :: (v4.0.1)
Starting application using Java 25.0.1
Tomcat initialized with port 8081 (http)
HikariPool-1 - Starting...
应用程序启动,当前数据库: xmldb_suiyue
AITransformService 已初始化
AI助手初始化成功
开始扫描目录建立机制映射: [Aion XML 路径]
目录扫描完成: 新增 6598 个文件
快捷键系统初始化完成
应用程序界面初始化完成
```

### 配置文件缓存机制

- `IdNameResolver` - 单例模式，缓存 ID 到 NAME 的映射（避免重复查询数据库）
- `MechanismFileMapper` - 启动时扫描所有 XML 文件建立机制映射
- `SubTablePreloader` - 导出时预加载子表数据到内存

### 虚拟线程使用

项目充分利用 Java 21+ 的虚拟线程特性：
```java
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
```
用于：
- 数据库导出的分页并行处理
- XML 解析的并发任务
- AI 批量翻译的异步请求

## 编码规范

- 所有代码文件使用 **UTF-8** 编码
- 使用中文注释和日志
- 遵循 Spring Boot 和 JavaFX 最佳实践
- 敏感配置使用环境变量注入
- **Java 25 LTS**：可使用最新 Java 特性（虚拟线程、Record Patterns、String Templates、Unnamed Variables 等）
- **游戏设计师优先**：遇到设计问题，永远站在游戏设计师的角度考虑，以方便设计师对游戏进行改造为重要目的

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
mvnd javafx:run
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

## 常见问题排查

### 编译失败

**问题**：Maven 报 `ClassNotFoundException: #` 错误
**解决**：临时重命名 `.mvn` 目录：`mv .mvn .mvn.backup`

### 启动失败

**问题**：数据库连接失败
**解决**：
1. 检查 `application.yml` 中的数据库配置
2. 确认 MySQL 服务已启动
3. 验证数据库名称、用户名、密码是否正确

**问题**：端口 8081 被占用
**解决**：在 `application.yml` 中修改 `server.port` 配置

### AI 功能不可用

**问题**：AI 对话代理无响应
**解决**：
1. 检查 AI 服务的 API Key 配置
2. 优先使用环境变量：`export AI_QWEN_APIKEY=your-key`
3. 检查网络连接和 API 服务状态

## 最新改进

### 透明编码转换层（2025-12-29）⭐

**核心价值**：确保 XML 导入导出的往返一致性，游戏服务器要求的 UTF-16 编码文件能够完美保持。

#### 实现的5大增强功能

**1. BOM 写入支持**
- 自动检测并记录原始文件的 BOM 标记
- 导出时精确恢复 UTF-16BE/UTF-16LE/UTF-8 的 BOM
- 确保字节级完全一致

**2. World 表 mapType 参数记录**
- 支持同一表的多个版本独立管理（China/Korea/Japan等）
- 数据库主键改为 `(table_name, map_type)` 复合主键
- 每个版本独立记录编码元数据

**3. 自动往返一致性验证**
- 导入时计算并保存文件 MD5 哈希
- 导出后自动验证文件一致性
- 验证结果记录到数据库
- 支持批量验证和历史查询

**4. 智能编码检测降级策略**
- 五层降级逻辑：BOM检测 → 历史记录 → 表级默认 → 扩展名推断 → UTF-16兜底
- 可信度评分系统（0-100分）
- 检测失败率从 ~5% 降至 <1%

**5. 元数据缓存优化**
- 线程安全的 `ConcurrentHashMap` 缓存
- TTL 自动过期机制（1小时）
- 批量导出性能提升 **30-50%**

#### 新增核心类

| 类名 | 功能 | 行数 |
|------|------|------|
| `FileEncodingDetector` | 自动检测文件编码（BOM/XML声明/file命令） | 260 |
| `EncodingMetadataManager` | 编码元数据CRUD操作 | 220 |
| `RoundTripValidator` | 往返一致性验证（MD5哈希） | 260 |
| `EncodingFallbackStrategy` | 智能降级策略 | 200 |
| `EncodingMetadataCache` | 元数据缓存优化 | 220 |

#### 数据库表

```sql
-- 编码元数据表（支持往返验证）
CREATE TABLE file_encoding_metadata (
    table_name VARCHAR(100) NOT NULL,
    map_type VARCHAR(50) NOT NULL DEFAULT '',
    original_encoding VARCHAR(20) NOT NULL,
    has_bom BOOLEAN DEFAULT FALSE,
    original_file_hash VARCHAR(64),  -- MD5哈希
    last_validation_result BOOLEAN,  -- 验证结果
    PRIMARY KEY (table_name, map_type)
);
```

#### 使用示例

**导入流程**（用户无感知）：
```
1. 选择 XML 文件导入
2. 系统自动检测编码：UTF-16BE (BOM) (可信度: 90%)
3. 保存元数据：编码 + BOM + MD5
4. 导入完成
```

**导出流程**（自动验证）：
```
1. 选择表导出
2. 系统查询元数据：UTF-16BE (BOM)
3. 恢复原始编码和 BOM
4. 自动验证：✅ MD5一致，往返成功！
```

**详细文档**：`docs/TRANSPARENT_ENCODING_ARCHITECTURE.md`

---

### 数据导入系统智能化升级（2025-12-27）

**DatabaseUtil.batchInsert()** 方法已增强，新增：

1. **主键自动检测和修复** - 自动处理 XML `id` 属性与数据库主键字段不一致的问题
2. **字段长度动态扩展** - 自动处理 VARCHAR 长度不足问题
3. **增强的错误日志** - 提供详细的诊断信息

**详细文档**：`docs/IMPORT_SYSTEM_IMPROVEMENT.md`

## 快速测试透明编码转换层

**步骤1：创建数据库表**
```bash
# 登录 MySQL
mysql -u root -p xmldb_suiyue

# 执行建表脚本
source src/main/resources/sql/file_encoding_metadata.sql;

# 验证表结构
DESC file_encoding_metadata;
```

**步骤2：测试往返一致性**
```bash
# 1. 选择一个简单的表（推荐使用 item_groups 或其他小表，而非 skill_base）
# 2. 在应用中导入 XML 文件
# 3. 观察日志中的编码检测信息：
#    ✅ 检测到文件编码: UTF-16BE (with BOM) (可信度: 90%)
#    ✅ 保存编码元数据: 表=xxx, 编码=UTF-16BE, BOM=true, MD5=...
#
# 4. 导出到 XML 文件
# 5. 观察日志中的验证信息：
#    ✅ 导出时使用原始编码: 表=xxx, 编码=UTF-16BE (with BOM)
#    ✅ 往返一致性验证通过！导出文件与原始文件完全一致
#
# 6. 手动验证 MD5（可选）
#    md5sum 原始文件.xml
#    md5sum 导出文件.xml
```

**步骤3：查看元数据**
```sql
-- 查看所有编码元数据
SELECT table_name, map_type, original_encoding, has_bom,
       last_validation_result, import_count, export_count
FROM file_encoding_metadata
ORDER BY last_import_time DESC;

-- 查看验证失败的记录
SELECT * FROM file_encoding_metadata
WHERE last_validation_result = FALSE;
```

## 相关文档

- `docs/TRANSPARENT_ENCODING_ARCHITECTURE.md` - 透明编码转换层架构设计（⭐最新）
- `docs/IMPORT_SYSTEM_IMPROVEMENT.md` - 数据导入系统改进说明
- `docs/QUEST_IMPORT_FIX.md` - 任务系统导入问题修复指南
- `docs/MECHANISM_DYNAMIC_CLASSIFICATION.md` - 机制动态分类系统详解
- `docs/MECHANISM_EXPLORER_GUIDE.md` - 机制浏览器使用指南
- `docs/DESIGNER_EXPERIENCE_ROADMAP.md` - 设计师体验路线图
- `docs/TECH_STACK_EVOLUTION.md` - 技术栈演进历史
- `CLAUDE.md` - 本文件，为 AI 助手提供项目上下文
