@echo off
REM ========================================
REM 启动器 - 强制在新窗口中运行
REM ========================================

echo 正在打开新窗口启动工具...

REM 在新窗口中运行 start-safe.bat
start "dbxmlTool 启动" cmd /k "cd /d "%~dp0" && start-safe.bat"

echo.
echo 已在新窗口中打开启动脚本。
echo 请查看新窗口的输出。
echo.
echo 本窗口可以关闭。
pause
