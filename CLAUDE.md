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
- **PostgreSQL 16** (已从 MySQL 迁移)

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
| 数据库 | **PostgreSQL 16** + Spring JDBC |
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

## PostgreSQL 数据库规范

⚠️ **重要**: 项目已从 MySQL 迁移到 PostgreSQL 16

### SQL 编写规范

1. **标识符引用**: 所有表名和列名必须使用双引号 `"` (不是反引号 `` ` ``)
   ```java
   // ✅ 正确
   "SELECT * FROM \"table_name\" WHERE \"column_name\" = ?"

   // ❌ 错误
   "SELECT * FROM table_name WHERE column_name = ?"  // 会被转为小写
   ```

2. **参数化查询**: 强制使用 PreparedStatement，禁止字符串拼接
   ```java
   // ✅ 正确
   String sql = "INSERT INTO \"users\" (\"name\", \"age\") VALUES (?, ?)";
   jdbcTemplate.update(sql, name, age);

   // ❌ 错误
   String sql = "INSERT INTO users VALUES ('" + name + "', " + age + ")";
   ```

3. **函数差异**: 使用 PostgreSQL 特有函数
   - `RANDOM()` 代替 `RAND()`
   - `COALESCE()` 代替 `IFNULL()`
   - `current_schema()` 代替 `DATABASE()`
   - `split_part()` 代替 `SUBSTRING_INDEX()`

4. **建表语法**:
   ```sql
   -- PostgreSQL
   CREATE TABLE "table_name" (
       id BIGSERIAL PRIMARY KEY,
       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
   );

   -- 触发器实现自动更新 updated_at
   CREATE TRIGGER ...
   ```

### 常见陷阱 Common Pitfalls

- ❌ 表名不加引号导致大小写不匹配
- ❌ 使用 `AUTO_INCREMENT` 而不是 `BIGSERIAL`
- ❌ 使用 `ENGINE=InnoDB` 等 MySQL 特有语法
- ❌ 使用 `ON UPDATE CURRENT_TIMESTAMP` (需要用触发器实现)

### 参考文档

- 详细迁移报告: `doc/postgresql_migration_2026-01-14.md`
- 透明编码架构: `docs/TRANSPARENT_ENCODING_ARCHITECTURE.md`
