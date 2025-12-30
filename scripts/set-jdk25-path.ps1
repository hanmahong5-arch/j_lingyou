#Requires -RunAsAdministrator
# JDK 25 环境变量配置脚本
# 使用方法：右键 -> 以管理员身份运行

$JDK_PATH = "D:\jdk-25.0.1.8-hotspot"

Write-Host "正在配置 JDK 25 环境变量..." -ForegroundColor Cyan

# 1. 设置 JAVA_HOME
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", $JDK_PATH, "Machine")
Write-Host "[OK] JAVA_HOME = $JDK_PATH" -ForegroundColor Green

# 2. 更新 PATH（移除旧的 Java 路径，添加新路径）
$machinePath = [System.Environment]::GetEnvironmentVariable("Path", "Machine")
$pathParts = $machinePath -split ";" | Where-Object {
    $_ -and ($_ -notmatch "(?i)\\java\\|\\jdk|\\jre")
}
$newPath = "$JDK_PATH\bin;" + ($pathParts -join ";")
[System.Environment]::SetEnvironmentVariable("Path", $newPath, "Machine")
Write-Host "[OK] PATH 已更新" -ForegroundColor Green

# 3. 刷新当前会话
$env:JAVA_HOME = $JDK_PATH
$env:Path = "$JDK_PATH\bin;$env:Path"

# 4. 验证
Write-Host "`n验证安装：" -ForegroundColor Yellow
& "$JDK_PATH\bin\java.exe" -version

Write-Host "`n配置完成！请重启 IDE 和终端使配置生效。" -ForegroundColor Green
Write-Host "按任意键退出..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
