@echo off
REM ========================================
REM 绝对不会闪退的启动脚本
REM ========================================

REM 在脚本最开始就设置错误时不退出
setlocal

REM 先暂停一次，确保能看到
echo.
echo ========================================
echo 安全启动脚本
echo ========================================
echo.
echo 如果你能看到这段文字，说明脚本正在运行
echo 按任意键继续...
pause

REM 切换编码
chcp 65001

REM 切换到脚本目录
cd /d "%~dp0"

echo.
echo 当前目录: %CD%
echo.
pause

REM 设置环境变量
set "JAVA_HOME=D:\jdk-25.0.1.8-hotspot"
set "MAVEN_HOME=D:\develop\apache-maven-3.9.9"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"

echo.
echo 环境变量已设置:
echo JAVA_HOME: %JAVA_HOME%
echo MAVEN_HOME: %MAVEN_HOME%
echo.
pause

REM 测试 Java
echo.
echo 测试 Java 命令...
where java
if errorlevel 1 (
    echo [错误] 找不到 Java！
    echo 请检查 JAVA_HOME: %JAVA_HOME%
    pause
    goto :end
)
echo.
echo Java 版本:
java -version
echo.
pause

REM 测试 Maven
echo.
echo 测试 Maven 命令...
where mvn
if errorlevel 1 (
    echo [错误] 找不到 Maven！
    echo 请检查 MAVEN_HOME: %MAVEN_HOME%
    pause
    goto :end
)
echo.
echo Maven 版本:
call mvn -version
echo.
pause

REM 检查 pom.xml
echo.
echo 检查 pom.xml...
if not exist "pom.xml" (
    echo [错误] 找不到 pom.xml！
    echo 请确认当前目录是项目根目录
    pause
    goto :end
)
echo [成功] 找到 pom.xml
echo.
pause

REM 开始编译
echo.
echo ========================================
echo 准备开始编译...
echo ========================================
echo.
echo 按任意键开始编译（可能需要几分钟）
pause

echo.
echo 执行: mvn clean compile
echo.

call mvn clean compile

if errorlevel 1 (
    echo.
    echo ========================================
    echo [失败] 编译失败！
    echo ========================================
    echo.
    echo 请查看上方的错误信息
    echo.
    pause
    goto :end
)

echo.
echo ========================================
echo [成功] 编译完成！
echo ========================================
echo.
pause

REM 开始运行
echo.
echo ========================================
echo 准备启动应用...
echo ========================================
echo.
echo 按任意键启动
pause

set "MAVEN_OPTS=-Dfile.encoding=UTF-8"

echo.
echo 执行: mvn javafx:run
echo.

call mvn javafx:run

if errorlevel 1 (
    echo.
    echo ========================================
    echo [失败] 启动失败！
    echo ========================================
    echo.
    echo 请查看上方的错误信息
    echo.
) else (
    echo.
    echo ========================================
    echo 应用已退出
    echo ========================================
    echo.
)

:end
echo.
echo ========================================
echo 脚本执行完毕
echo ========================================
echo.
echo 按任意键关闭窗口...
pause
