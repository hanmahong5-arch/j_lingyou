@echo off
REM ========================================
REM dbxmlTool 启动脚本
REM ========================================

REM 设置 UTF-8 编码（解决中文乱码）
chcp 65001 >nul 2>&1

REM 设置环境变量
set JAVA_HOME=D:\jdk-25.0.1.8-hotspot
set MAVEN_HOME=D:\develop\apache-maven-3.9.9
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%

REM 切换到脚本所在目录
cd /d "%~dp0"

echo ========================================
echo 正在启动 dbxmlTool...
echo ========================================
echo.
echo 项目目录: %CD%
echo JAVA_HOME: %JAVA_HOME%
echo.

REM 检查 Java 是否可用
where java >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [错误] 找不到 Java！
    echo 请检查 JAVA_HOME 配置: %JAVA_HOME%
    echo.
    pause
    exit /b 1
)

REM 检查 Maven 是否可用
where mvn >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [错误] 找不到 Maven！
    echo 请检查 MAVEN_HOME 配置: %MAVEN_HOME%
    echo.
    pause
    exit /b 1
)

REM 显示版本信息
echo Java 版本:
java -version 2>&1 | findstr "version"
echo.
echo Maven 版本:
mvn -version 2>&1 | findstr "Apache Maven"
echo.

echo ========================================
echo 开始运行 JavaFX 应用...
echo ========================================
echo.

REM 设置 Maven 输出编码
set MAVEN_OPTS=-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8

REM 运行应用
mvn javafx:run

REM 检查启动结果
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ========================================
    echo [失败] 应用启动失败！错误代码: %ERRORLEVEL%
    echo ========================================
    echo.
    echo 常见问题排查：
    echo 1. 检查数据库连接配置（application.yml）
    echo 2. 确认 MySQL 服务已启动
    echo 3. 检查端口 8081 是否被占用
    echo 4. 查看上方错误日志
    echo.
) else (
    echo.
    echo ========================================
    echo 应用已正常退出
    echo ========================================
    echo.
)

REM 保持窗口打开（无论成功或失败）
pause
