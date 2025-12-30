# 启动问题排查指南

## 问题：run.bat 一闪而过

如果双击 `run.bat` 后窗口一闪而过，说明启动过程中遇到了错误。以下是详细的排查步骤。

---

## 🔧 快速排查（3步）

### 步骤 1：运行诊断脚本

双击运行：**diagnose.bat**

这个脚本会检查：
- ✅ 当前目录是否正确
- ✅ Java 和 Maven 是否可用
- ✅ 配置文件是否存在
- ✅ 源代码是否完整

**如果发现 [×] 标记，按照提示解决对应问题。**

---

### 步骤 2：编译并运行

双击运行：**compile-and-run.bat**

这个脚本会：
1. 清理项目（`mvn clean`）
2. 编译代码（`mvn compile`）
3. 启动应用（`mvn javafx:run`）

**如果编译失败，查看错误信息，可能是代码问题。**
**如果运行失败，查看下方的常见问题。**

---

### 步骤 3：调试模式启动

如果前两步都失败，运行：**run-debug.bat**

这个脚本会显示非常详细的调试信息，帮助定位问题。

---

## 📋 常见问题及解决方法

### 问题 1：找不到 Java 或 Maven

**症状**：
```
[错误] 找不到 Java！
```

**原因**：
- JAVA_HOME 路径配置错误
- JDK 未安装或安装路径不对

**解决方法**：
1. 检查 JDK 25 是否已安装在 `D:\jdk-25.0.1.8-hotspot`
2. 如果路径不同，编辑 `run.bat`、`diagnose.bat`、`compile-and-run.bat` 中的第 10 行：
   ```batch
   set JAVA_HOME=你的实际JDK路径
   ```
3. 或者运行：
   ```bash
   .\scripts\install-jdk25.ps1
   ```

---

### 问题 2：找不到配置文件

**症状**：
```
[警告] 找不到 application.yml 配置文件！
```

**原因**：
- 首次运行，配置文件尚未创建

**解决方法**：
1. 复制模板文件：
   ```bash
   copy src\main\resources\application.yml.example src\main\resources\application.yml
   ```
2. 编辑 `src\main\resources\application.yml`，修改以下配置：
   - 数据库 URL、用户名、密码
   - AI 服务 API Key（可选）
   - Aion XML 路径

---

### 问题 3：数据库连接失败

**症状**：
```
Caused by: java.sql.SQLException: Access denied for user 'root'@'localhost'
```
或
```
Communications link failure
```

**原因**：
- MySQL 服务未启动
- 数据库连接信息错误（URL、用户名、密码）
- 数据库不存在

**解决方法**：

1. **启动 MySQL 服务**：
   - Windows: 打开"服务"（services.msc），启动 MySQL 服务
   - 或命令行：`net start MySQL`

2. **检查数据库配置**（`application.yml`）：
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/xmldb_suiyue?useSSL=false&serverTimezone=Asia/Shanghai
       username: root
       password: 你的实际密码
   ```

3. **创建数据库**（如果数据库不存在）：
   ```sql
   CREATE DATABASE xmldb_suiyue CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

4. **测试连接**：
   ```bash
   mysql -u root -p
   # 输入密码后，如果能进入 MySQL 提示符，说明服务正常
   ```

---

### 问题 4：端口被占用

**症状**：
```
Port 8081 was already in use
```

**原因**：
- 端口 8081 已被其他程序占用
- 或者之前的实例没有正常关闭

**解决方法**：

**方法 1：修改端口**（推荐）
- 编辑 `application.yml`：
  ```yaml
  server:
    port: 8082  # 改成其他端口
  ```

**方法 2：释放端口**
1. 查找占用端口的进程：
   ```bash
   netstat -ano | findstr :8081
   ```
2. 记下最后一列的 PID（进程ID）
3. 结束进程：
   ```bash
   taskkill /F /PID <进程ID>
   ```

---

### 问题 5：编译错误

**症状**：
```
[ERROR] COMPILATION ERROR
```

**常见原因及解决**：

**原因 1：Lombok 注解处理器问题**
```
cannot find symbol: method builder()
```
**解决**：
```bash
mvn clean install -U
```

**原因 2：依赖下载失败**
```
Could not resolve dependencies
```
**解决**：
1. 检查网络连接
2. 清理 Maven 缓存：
   ```bash
   mvn dependency:purge-local-repository
   mvn clean install
   ```

**原因 3：Java 版本不匹配**
```
invalid target release: 25
```
**解决**：
- 确认使用 JDK 25
- 运行 `java -version` 检查版本

**原因 4：新增文件语法错误**
- 检查最近修改的文件
- 查看完整的编译错误日志

---

### 问题 6：JavaFX 运行时错误

**症状**：
```
Error: JavaFX runtime components are missing
```

**原因**：
- JavaFX 依赖未正确加载

**解决方法**：
```bash
mvn clean install
mvn javafx:run
```

---

## 🛠️ 高级排查

### 查看完整日志

1. **Maven 详细日志**：
   ```bash
   mvn javafx:run -X > maven-debug.log 2>&1
   ```
   然后查看 `maven-debug.log` 文件

2. **应用程序日志**：
   - 位置：项目根目录下的 `logs/` 文件夹
   - 查看最新的日志文件

### 清理并重新开始

如果上述方法都不行，尝试完全清理：

```bash
# 1. 清理 Maven 缓存
mvn clean

# 2. 删除 target 目录（如果存在）
rmdir /s /q target

# 3. 重新下载依赖
mvn dependency:resolve

# 4. 编译
mvn clean compile

# 5. 运行
mvn javafx:run
```

---

## 📞 获取帮助

如果以上方法都无法解决问题，请：

1. **收集信息**：
   - 运行 `diagnose.bat` 的完整输出
   - 运行 `run-debug.bat` 的完整输出
   - Java 版本（`java -version`）
   - Maven 版本（`mvn -version`）

2. **查看文档**：
   - `README.md` - 项目说明
   - `CLAUDE.md` - 开发指南
   - `docs/` 目录下的其他文档

3. **检查日志**：
   - Maven 输出中的错误信息
   - `logs/` 目录下的应用日志

---

## ✅ 成功启动的标志

当应用成功启动时，你会看到：

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v4.0.1)

应用程序启动,当前数据库: xmldb_suiyue
Tomcat initialized with port 8081 (http)
应用程序界面初始化完成
```

然后会弹出 JavaFX 窗口。

---

## 🔄 快速命令参考

| 命令 | 用途 |
|-----|------|
| `diagnose.bat` | 诊断环境问题 |
| `compile-and-run.bat` | 编译并运行 |
| `run-debug.bat` | 调试模式启动 |
| `run.bat` | 标准启动（原版）|
| `mvn clean compile` | 清理并编译 |
| `mvn javafx:run` | 运行应用 |
| `mvn clean install` | 完整构建 |

---

**最后更新**: 2025-12-29
**适用版本**: dbxmlTool (Spring Boot 4.0.1 + JavaFX 25)
