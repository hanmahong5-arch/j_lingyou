# ========================================
# PowerShell 启动脚本（更可靠）
# ========================================

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "PowerShell 启动脚本" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 设置控制台编码
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# 切换到脚本目录
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptPath

Write-Host "当前目录: $PWD" -ForegroundColor Yellow
Write-Host ""

# 设置环境变量
$env:JAVA_HOME = "D:\jdk-25.0.1.8-hotspot"
$env:MAVEN_HOME = "D:\develop\apache-maven-3.9.9"
$env:PATH = "$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:PATH"

Write-Host "环境配置:" -ForegroundColor Green
Write-Host "  JAVA_HOME: $env:JAVA_HOME"
Write-Host "  MAVEN_HOME: $env:MAVEN_HOME"
Write-Host ""

# 检查 Java
Write-Host "检查 Java..." -ForegroundColor Green
try {
    $javaVersion = & java -version 2>&1
    Write-Host "Java 版本:" -ForegroundColor Green
    $javaVersion | ForEach-Object { Write-Host "  $_" }
    Write-Host ""
} catch {
    Write-Host "[错误] 找不到 Java！" -ForegroundColor Red
    Write-Host "请检查 JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Red
    Write-Host ""
    Read-Host "按 Enter 退出"
    exit 1
}

# 检查 Maven
Write-Host "检查 Maven..." -ForegroundColor Green
try {
    $mavenVersion = & mvn -version 2>&1 | Select-String "Apache Maven"
    Write-Host "Maven 版本:" -ForegroundColor Green
    Write-Host "  $mavenVersion"
    Write-Host ""
} catch {
    Write-Host "[错误] 找不到 Maven！" -ForegroundColor Red
    Write-Host "请检查 MAVEN_HOME: $env:MAVEN_HOME" -ForegroundColor Red
    Write-Host ""
    Read-Host "按 Enter 退出"
    exit 1
}

# 检查 pom.xml
Write-Host "检查 pom.xml..." -ForegroundColor Green
if (-not (Test-Path "pom.xml")) {
    Write-Host "[错误] 找不到 pom.xml！" -ForegroundColor Red
    Write-Host "请确认当前目录是项目根目录: $PWD" -ForegroundColor Red
    Write-Host ""
    Read-Host "按 Enter 退出"
    exit 1
}
Write-Host "[成功] 找到 pom.xml" -ForegroundColor Green
Write-Host ""

# 检查配置文件
Write-Host "检查配置文件..." -ForegroundColor Green
if (-not (Test-Path "src\main\resources\application.yml")) {
    Write-Host "[警告] 找不到 application.yml！" -ForegroundColor Yellow
    if (Test-Path "src\main\resources\application.yml.example") {
        Write-Host "[提示] 发现模板文件 application.yml.example" -ForegroundColor Yellow
        Write-Host ""
        $copy = Read-Host "是否复制模板文件？(Y/N)"
        if ($copy -eq "Y" -or $copy -eq "y") {
            Copy-Item "src\main\resources\application.yml.example" "src\main\resources\application.yml"
            Write-Host "[成功] 已复制配置文件" -ForegroundColor Green
            Write-Host "[重要] 请编辑 application.yml 修改数据库配置！" -ForegroundColor Yellow
            Write-Host ""
            Read-Host "修改完成后按 Enter 继续"
        }
    }
}
Write-Host ""

# 开始编译
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "[步骤 1/2] 清理并编译" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "执行: mvn clean compile" -ForegroundColor Yellow
Write-Host "这可能需要几分钟，请耐心等待..." -ForegroundColor Yellow
Write-Host ""

& mvn clean compile

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "[失败] 编译失败！错误代码: $LASTEXITCODE" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "请查看上方的编译错误信息" -ForegroundColor Red
    Write-Host ""
    Read-Host "按 Enter 退出"
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "[成功] 编译完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

# 开始运行
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "[步骤 2/2] 启动应用" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$env:MAVEN_OPTS = "-Dfile.encoding=UTF-8"

Write-Host "执行: mvn javafx:run" -ForegroundColor Yellow
Write-Host ""

& mvn javafx:run

$runError = $LASTEXITCODE

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
if ($runError -ne 0) {
    Write-Host "[失败] 应用启动失败！错误代码: $runError" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "常见问题排查：" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "1. 数据库连接失败" -ForegroundColor Yellow
    Write-Host "   - 检查 MySQL 服务是否启动" -ForegroundColor Gray
    Write-Host "   - 检查 application.yml 中的数据库配置" -ForegroundColor Gray
    Write-Host "   - 确认数据库 xmldb_suiyue 是否存在" -ForegroundColor Gray
    Write-Host ""
    Write-Host "2. 端口被占用" -ForegroundColor Yellow
    Write-Host "   - 检查端口 8081 是否被占用" -ForegroundColor Gray
    Write-Host "   - 修改 application.yml 中的 server.port" -ForegroundColor Gray
    Write-Host ""
    Write-Host "3. 配置文件问题" -ForegroundColor Yellow
    Write-Host "   - 确认 application.yml 存在且配置正确" -ForegroundColor Gray
    Write-Host ""
} else {
    Write-Host "[成功] 应用已正常退出" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
}
Write-Host ""

Read-Host "按 Enter 关闭窗口"
