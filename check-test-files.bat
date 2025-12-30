@echo off
chcp 65001 >nul
REM ========================================
REM 检查测试所需的XML文件和配置文件
REM ========================================

echo.
echo ========================================
echo 检查测试文件准备情况
echo ========================================
echo.

setlocal enabledelayedexpansion

REM 定义要测试的核心表
set TABLES=skills items npc_templates quests airports airline abyss abyss_rank

set TOTAL=0
set XML_EXISTS=0
set CONFIG_EXISTS=0
set BOTH_EXISTS=0

echo 检查结果：
echo ----------------------------------------

for %%T in (%TABLES%) do (
    set /a TOTAL+=1
    set HAS_XML=0
    set HAS_CONFIG=0

    echo.
    echo [%%T]

    REM 检查XML文件（多个可能位置）
    if exist "D:\AionReal58\AionMap\XML\%%T.xml" (
        echo   ✓ XML: D:\AionReal58\AionMap\XML\%%T.xml
        set HAS_XML=1
        set /a XML_EXISTS+=1
    ) else if exist "D:\AionReal58\AionMap\XML\China\%%T.xml" (
        echo   ✓ XML: D:\AionReal58\AionMap\XML\China\%%T.xml
        set HAS_XML=1
        set /a XML_EXISTS+=1
    ) else (
        echo   ✗ XML: 未找到 %%T.xml
    )

    REM 检查配置文件（多个可能位置）
    if exist "src\main\resources\CONF\D\AionReal58\AionMap\XML\%%T.json" (
        echo   ✓ 配置: src\main\resources\CONF\D\AionReal58\AionMap\XML\%%T.json
        set HAS_CONFIG=1
        set /a CONFIG_EXISTS+=1
    ) else if exist "src\main\resources\CONF\D\AionReal58\AionMap\XML\China\%%T.json" (
        echo   ✓ 配置: src\main\resources\CONF\D\AionReal58\AionMap\XML\China\%%T.json
        set HAS_CONFIG=1
        set /a CONFIG_EXISTS+=1
    ) else (
        echo   ✗ 配置: 未找到 %%T.json
    )

    REM 统计完整的表
    if !HAS_XML! equ 1 if !HAS_CONFIG! equ 1 (
        echo   ✅ 状态: 可以测试
        set /a BOTH_EXISTS+=1
    ) else (
        echo   ❌ 状态: 不完整，无法测试
    )
)

echo.
echo ========================================
echo 检查汇总
echo ========================================
echo 总表数: !TOTAL!
echo 有XML文件: !XML_EXISTS!
echo 有配置文件: !CONFIG_EXISTS!
echo 完整（可测试）: !BOTH_EXISTS!
echo 不完整: !TOTAL! - !BOTH_EXISTS! = !TOTAL!-!BOTH_EXISTS!
echo.

if !BOTH_EXISTS! gtr 0 (
    echo ✅ 有 !BOTH_EXISTS! 个表可以进行测试
    echo.
    echo 下一步：
    echo   1. 启动应用: quick-start.bat
    echo   2. 查看测试计划: IMPORT_EXPORT_TEST_PLAN.md
    echo   3. 开始测试第一个表
) else (
    echo ❌ 没有完整的表可以测试
    echo.
    echo 请检查：
    echo   1. XML文件路径是否正确
    echo   2. 配置文件是否已生成
)

echo.
pause
