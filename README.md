# 灵游 (LingYou)

游戏配置数据智能管理平台 - 专为游戏设计师打造

## 功能特性

- **数据库 ↔ XML 双向转换** - 支持多线程分页、事务处理、透明编码转换
- **AI 智能对话代理** - 自然语言查询和修改游戏数据
- **Aion 游戏机制浏览器** - 27 个机制分类可视化
- **主题工作室** - 批量数据转换与 AI 辅助改写
- **设计洞察** - AI 驱动的数据分析和设计建议

## 技术栈

| 层级 | 技术 |
|-----|------|
| Java 版本 | **Java 25 (LTS)** |
| GUI 框架 | OpenJFX 25 + JFoenix + ControlsFX |
| 应用框架 | Spring Boot 4.0.1 |
| 数据库 | **PostgreSQL 16** + Spring JDBC |
| XML 处理 | Dom4j 2.1.4 |
| AI 服务 | LangChain4j + DashScope SDK |

## 快速开始

### 环境要求

- JDK 25 LTS
- Maven 3.9+
- **PostgreSQL 16** (项目已从 MySQL 迁移)

### 配置

1. 复制配置模板：
```bash
cp src/main/resources/application.yml.example src/main/resources/application.yml
```

2. 编辑 `application.yml`，配置数据库连接和 AI 服务密钥

### 运行

```bash
# Windows
run.bat

# 或使用 Maven
mvn javafx:run
```

## 项目结构

```
src/main/java/red/jiuzhou/
├── agent/        # AI 智能对话代理
├── ai/           # AI 模型集成
├── analysis/     # 游戏机制分析
├── dbxml/        # 数据库与 XML 转换核心
├── langchain/    # LangChain4j 集成
├── ui/           # JavaFX 用户界面
└── util/         # 工具类库
```

## 许可证

MIT License
