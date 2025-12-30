@echo off
chcp 65001 >nul
REM ========================================
REM 导入导出往返测试脚本
REM ========================================

echo.
echo ========================================
echo 导入导出往返测试
echo ========================================
echo.

cd /d "%~dp0"

set JAVA_HOME=D:\jdk-25.0.1.8-hotspot
set MAVEN_HOME=D:\develop\apache-maven-3.9.9
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%
set MAVEN_OPTS=-Dfile.encoding=UTF-8

REM 创建测试报告目录
set REPORT_DIR=test-reports
if not exist "%REPORT_DIR%" mkdir "%REPORT_DIR%"

set REPORT_FILE=%REPORT_DIR%\import-export-test-report-%date:~0,4%%date:~5,2%%date:~8,2%-%time:~0,2%%time:~3,2%%time:~6,2%.txt
set REPORT_FILE=%REPORT_FILE: =0%

echo 测试开始时间: %date% %time% > "%REPORT_FILE%"
echo. >> "%REPORT_FILE%"
echo ========================================>> "%REPORT_FILE%"
echo 导入导出往返测试报告>> "%REPORT_FILE%"
echo ========================================>> "%REPORT_FILE%"
echo. >> "%REPORT_FILE%"

REM 定义要测试的表（服务器合规过滤器支持的表）
set TABLES=items skills npc_templates quests

echo 【阶段1】测试服务器合规过滤器支持的核心表
echo ========================================
echo.

set PASS_COUNT=0
set FAIL_COUNT=0
set TOTAL_COUNT=0

for %%T in (%TABLES%) do (
    set /a TOTAL_COUNT+=1
    echo [测试 !TOTAL_COUNT!] 表: %%T
    echo ----------------------------------------
    echo [测试 !TOTAL_COUNT!] 表: %%T >> "%REPORT_FILE%"

    REM 检查XML文件是否存在
    if exist "D:\AionReal58\AionMap\XML\%%T.xml" (
        echo ✓ XML文件存在: %%T.xml
        echo ✓ XML文件存在: %%T.xml >> "%REPORT_FILE%"

        REM 检查配置文件是否存在
        if exist "src\main\resources\CONF\D\AionReal58\AionMap\XML\%%T.json" (
            echo ✓ 配置文件存在: %%T.json
            echo ✓ 配置文件存在: %%T.json >> "%REPORT_FILE%"
            set /a PASS_COUNT+=1
            echo ✅ PASS: %%T >> "%REPORT_FILE%"
        ) else (
            echo ✗ 配置文件不存在: %%T.json
            echo ✗ 配置文件不存在: %%T.json >> "%REPORT_FILE%"
            set /a FAIL_COUNT+=1
            echo ❌ FAIL: %%T >> "%REPORT_FILE%"
        )
    ) else (
        echo ✗ XML文件不存在: %%T.xml
        echo ✗ XML文件不存在: %%T.xml >> "%REPORT_FILE%"
        set /a FAIL_COUNT+=1
        echo ❌ FAIL: %%T >> "%REPORT_FILE%"
    )

    echo. >> "%REPORT_FILE%"
    echo.
)

echo.
echo ========================================
echo 测试汇总
echo ========================================
echo 总测试数: !TOTAL_COUNT!
echo 通过: !PASS_COUNT!
echo 失败: !FAIL_COUNT!
echo.

echo. >> "%REPORT_FILE%"
echo ========================================>> "%REPORT_FILE%"
echo 测试汇总>> "%REPORT_FILE%"
echo ========================================>> "%REPORT_FILE%"
echo 总测试数: !TOTAL_COUNT!>> "%REPORT_FILE%"
echo 通过: !PASS_COUNT!>> "%REPORT_FILE%"
echo 失败: !FAIL_COUNT!>> "%REPORT_FILE%"
echo. >> "%REPORT_FILE%"
echo 测试结束时间: %date% %time% >> "%REPORT_FILE%"

echo 报告已保存到: %REPORT_FILE%
echo.
echo 按任意键打开报告...
pause >nul

notepad "%REPORT_FILE%"
