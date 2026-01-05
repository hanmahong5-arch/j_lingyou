# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Git 工作流

```bash
git add .
git commit -m "feat: 简短描述"
git push lingyou clean-main:main
```

| 前缀 | 用途 |
|------|------|
| `feat:` | 新功能 |
| `fix:` | Bug修复 |
| `refactor:` | 代码重构 |
| `docs:` | 文档更新 |
| `chore:` | 构建/配置变更 |

**GitHub**: https://github.com/hanmahong5-arch/j_lingyou

## 项目概述

**灵游 (j_lingyou)** - 游戏配置数据智能管理平台

**主要功能**：
- 数据库 ↔ XML 双向转换（透明编码、往返一致性验证）
- AI 智能对话代理（自然语言查询和修改游戏数据）
- Aion 游戏机制可视化浏览器（27个机制分类）
- 主题工作室和批量转换

## 环境要求

- **JDK 25 LTS**
- Maven 3.9+
- MySQL 9.x

## 快速启动

```bash
# Windows
run.bat

# 或
mvn javafx:run
```

**主类入口**: `red.jiuzhou.ui.Dbxmltool`

## 技术栈

| 层级 | 技术 |
|-----|------|
| Java | **Java 25 (LTS)** |
| GUI | OpenJFX 25 + JFoenix + ControlsFX |
| 框架 | Spring Boot 4.0.1 |
| 数据库 | MySQL 9.1 + Spring JDBC |
| XML | Dom4j 2.1.4 |
| AI | LangChain4j + DashScope SDK |

## 包结构

```
red.jiuzhou
├── agent/        # AI智能对话代理（Tool Calling）
├── ai/           # AI模型集成
├── analysis/     # 游戏机制分析
├── dbxml/        # 数据库与XML转换核心
├── langchain/    # LangChain4j 集成
├── ui/           # JavaFX用户界面
└── util/         # 工具类库
```

## 配置

1. 复制 `src/main/resources/application.yml.example` 为 `application.yml`
2. 配置数据库连接和 AI 服务密钥
3. 敏感信息使用环境变量：`${AI_QWEN_APIKEY:default}`

## 编码规范

- UTF-8 编码
- 中文注释和日志
- 游戏设计师优先：站在设计师角度考虑问题
- Java 25 特性：虚拟线程、Record Patterns 等
