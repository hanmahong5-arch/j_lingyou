@echo off
REM ========================================
REM dbxmlTool 启动脚本（调试版本）
REM ========================================

REM 无论如何都要在结束时暂停
setlocal enabledelayedexpansion

REM 设置 UTF-8 编码
chcp 65001 >nul 2>&1

echo ========================================
echo dbxmlTool 启动调试脚本
echo ========================================
echo.

REM 显示当前目录
echo [调试] 当前目录: %CD%
echo [调试] 脚本目录: %~dp0
echo.

REM 切换到脚本所在目录
cd /d "%~dp0"
echo [调试] 已切换到: %CD%
echo.

REM 设置环境变量
set JAVA_HOME=D:\jdk-25.0.1.8-hotspot
set MAVEN_HOME=D:\develop\apache-maven-3.9.9
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%

echo [调试] JAVA_HOME: %JAVA_HOME%
echo [调试] MAVEN_HOME: %MAVEN_HOME%
echo.

REM 检查 JAVA_HOME 目录是否存在
if not exist "%JAVA_HOME%" (
    echo [错误] JAVA_HOME 目录不存在: %JAVA_HOME%
    echo.
    echo 请修改脚本中的 JAVA_HOME 路径，或者：
    echo 1. 确认 JDK 25 已安装
    echo 2. 修改第 12 行的路径
    echo.
    goto :error
)

REM 检查 MAVEN_HOME 目录是否存在
if not exist "%MAVEN_HOME%" (
    echo [错误] MAVEN_HOME 目录不存在: %MAVEN_HOME%
    echo.
    echo 请修改脚本中的 MAVEN_HOME 路径
    echo.
    goto :error
)

REM 检查 java.exe 是否存在
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [错误] 找不到 java.exe: %JAVA_HOME%\bin\java.exe
    echo.
    goto :error
)

REM 检查 mvn.cmd 是否存在
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo [错误] 找不到 mvn.cmd: %MAVEN_HOME%\bin\mvn.cmd
    echo.
    goto :error
)

echo [调试] Java 路径: %JAVA_HOME%\bin\java.exe [存在]
echo [调试] Maven 路径: %MAVEN_HOME%\bin\mvn.cmd [存在]
echo.

REM 显示版本信息
echo ========================================
echo 环境检查
echo ========================================
echo.

echo Java 版本:
java -version 2>&1
echo.

echo Maven 版本:
mvn -version 2>&1
echo.

REM 检查 pom.xml 是否存在
if not exist "pom.xml" (
    echo [错误] 找不到 pom.xml 文件！
    echo 请确认当前目录是否为项目根目录: %CD%
    echo.
    goto :error
)

echo [调试] 找到 pom.xml: %CD%\pom.xml
echo.

REM 检查 application.yml 是否存在
if not exist "src\main\resources\application.yml" (
    echo [警告] 找不到 application.yml 配置文件！
    echo 请从 application.yml.example 复制并修改配置。
    echo.
    echo 是否继续尝试启动？（可能会失败）
    choice /C YN /M "继续(Y) 或 退出(N)"
    if errorlevel 2 goto :error
)

echo ========================================
echo 开始启动应用
echo ========================================
echo.

REM 设置 Maven 参数
set MAVEN_OPTS=-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8

echo [调试] MAVEN_OPTS: %MAVEN_OPTS%
echo.
echo 执行命令: mvn javafx:run
echo.
echo ----------------------------------------
echo Maven 输出:
echo ----------------------------------------
echo.

REM 运行应用
mvn javafx:run

REM 保存错误码
set EXIT_CODE=%ERRORLEVEL%

echo.
echo ----------------------------------------
echo Maven 执行完成
echo ----------------------------------------
echo.

if %EXIT_CODE% NEQ 0 (
    echo [失败] 应用启动失败！错误代码: %EXIT_CODE%
    echo.
    echo 常见问题排查：
    echo 1. 数据库连接失败
    echo    - 检查 src\main\resources\application.yml
    echo    - 确认 MySQL 服务已启动
    echo    - 验证数据库连接信息（URL、用户名、密码）
    echo.
    echo 2. 端口被占用
    echo    - 检查端口 8081 是否被其他程序占用
    echo    - 可在 application.yml 中修改端口
    echo.
    echo 3. 依赖问题
    echo    - 运行 mvn clean install 重新下载依赖
    echo.
    echo 4. 编译错误
    echo    - 检查上方的错误日志
    echo    - 查看是否有类找不到或语法错误
    echo.
) else (
    echo [成功] 应用已正常退出
    echo.
)

goto :end

:error
echo.
echo ========================================
echo 启动失败！
echo ========================================
echo.

:end
echo 按任意键关闭窗口...
pause >nul
