# PowerShell 脚本：清理旧版本 Java，只保留 JDK 25
# 运行方式：以管理员身份打开 PowerShell，执行 .\scripts\cleanup-old-java.ps1

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  清理旧版本 Java 脚本" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. 列出所有已安装的 Java
Write-Host "[1/5] 扫描已安装的 Java 版本..." -ForegroundColor Yellow

$javaInstalls = @()

# 检查 Eclipse Adoptium/Temurin
$adoptiumPath = "C:\Program Files\Eclipse Adoptium"
if (Test-Path $adoptiumPath) {
    Get-ChildItem $adoptiumPath -Directory | ForEach-Object {
        $javaInstalls += @{
            Name = $_.Name
            Path = $_.FullName
            Type = "Adoptium"
            Keep = $_.Name -like "jdk-25*"
        }
    }
}

# 检查 Oracle JDK
$oraclePaths = @(
    "C:\Program Files\Java",
    "C:\Program Files (x86)\Java"
)
foreach ($path in $oraclePaths) {
    if (Test-Path $path) {
        Get-ChildItem $path -Directory | ForEach-Object {
            $javaInstalls += @{
                Name = $_.Name
                Path = $_.FullName
                Type = "Oracle"
                Keep = $_.Name -like "*25*"
            }
        }
    }
}

# 检查 Amazon Corretto
$correttoPath = "C:\Program Files\Amazon Corretto"
if (Test-Path $correttoPath) {
    Get-ChildItem $correttoPath -Directory | ForEach-Object {
        $javaInstalls += @{
            Name = $_.Name
            Path = $_.FullName
            Type = "Corretto"
            Keep = $_.Name -like "*25*"
        }
    }
}

# 检查 Azul Zulu
$zuluPath = "C:\Program Files\Zulu"
if (Test-Path $zuluPath) {
    Get-ChildItem $zuluPath -Directory | ForEach-Object {
        $javaInstalls += @{
            Name = $_.Name
            Path = $_.FullName
            Type = "Zulu"
            Keep = $_.Name -like "*25*"
        }
    }
}

# 检查 Microsoft OpenJDK
$msPath = "C:\Program Files\Microsoft"
if (Test-Path $msPath) {
    Get-ChildItem $msPath -Directory | Where-Object { $_.Name -like "jdk*" } | ForEach-Object {
        $javaInstalls += @{
            Name = $_.Name
            Path = $_.FullName
            Type = "Microsoft"
            Keep = $_.Name -like "*25*"
        }
    }
}

# 显示扫描结果
Write-Host ""
Write-Host "发现 $($javaInstalls.Count) 个 Java 安装:" -ForegroundColor White
foreach ($java in $javaInstalls) {
    $status = if ($java.Keep) { "[保留]" } else { "[删除]" }
    $color = if ($java.Keep) { "Green" } else { "Red" }
    Write-Host "  $status $($java.Type): $($java.Name)" -ForegroundColor $color
    Write-Host "         路径: $($java.Path)" -ForegroundColor Gray
}

# 2. 查找通过 Windows 安装程序安装的 Java
Write-Host ""
Write-Host "[2/5] 扫描 Windows 安装程序中的 Java..." -ForegroundColor Yellow

$installedPrograms = Get-WmiObject -Class Win32_Product | Where-Object {
    $_.Name -like "*Java*" -or $_.Name -like "*JDK*" -or $_.Name -like "*JRE*" -or $_.Name -like "*Temurin*" -or $_.Name -like "*OpenJDK*"
}

if ($installedPrograms) {
    Write-Host "发现已安装的程序:" -ForegroundColor White
    foreach ($prog in $installedPrograms) {
        $keep = $prog.Name -like "*25*"
        $status = if ($keep) { "[保留]" } else { "[卸载]" }
        $color = if ($keep) { "Green" } else { "Red" }
        Write-Host "  $status $($prog.Name)" -ForegroundColor $color
    }
}

# 3. 确认删除
Write-Host ""
Write-Host "[3/5] 准备清理..." -ForegroundColor Yellow
$toDelete = $javaInstalls | Where-Object { -not $_.Keep }
$toUninstall = $installedPrograms | Where-Object { $_.Name -notlike "*25*" }

if ($toDelete.Count -eq 0 -and $toUninstall.Count -eq 0) {
    Write-Host "没有需要清理的旧版本 Java" -ForegroundColor Green
} else {
    Write-Host "将要删除 $($toDelete.Count) 个目录，卸载 $($toUninstall.Count) 个程序" -ForegroundColor Yellow
    Write-Host ""
    $confirm = Read-Host "确认清理? (y/N)"

    if ($confirm -eq "y" -or $confirm -eq "Y") {
        # 4. 卸载程序
        Write-Host ""
        Write-Host "[4/5] 卸载旧版本 Java 程序..." -ForegroundColor Yellow
        foreach ($prog in $toUninstall) {
            Write-Host "  卸载: $($prog.Name)..." -ForegroundColor Yellow
            try {
                $prog.Uninstall() | Out-Null
                Write-Host "  完成" -ForegroundColor Green
            } catch {
                Write-Host "  失败: $_" -ForegroundColor Red
            }
        }

        # 5. 删除残留目录
        Write-Host ""
        Write-Host "[5/5] 删除残留目录..." -ForegroundColor Yellow
        foreach ($java in $toDelete) {
            Write-Host "  删除: $($java.Path)..." -ForegroundColor Yellow
            try {
                Remove-Item -Path $java.Path -Recurse -Force
                Write-Host "  完成" -ForegroundColor Green
            } catch {
                Write-Host "  失败: $_" -ForegroundColor Red
            }
        }

        # 清理空父目录
        $parentDirs = @(
            "C:\Program Files\Java",
            "C:\Program Files (x86)\Java",
            "C:\Program Files\Amazon Corretto",
            "C:\Program Files\Zulu"
        )
        foreach ($dir in $parentDirs) {
            if ((Test-Path $dir) -and ((Get-ChildItem $dir).Count -eq 0)) {
                Remove-Item $dir -Force
                Write-Host "  删除空目录: $dir" -ForegroundColor Gray
            }
        }

        Write-Host ""
        Write-Host "========================================" -ForegroundColor Green
        Write-Host "  清理完成！" -ForegroundColor Green
        Write-Host "========================================" -ForegroundColor Green
    } else {
        Write-Host "已取消清理操作" -ForegroundColor Yellow
    }
}

# 验证当前 Java 版本
Write-Host ""
Write-Host "当前 Java 版本:" -ForegroundColor Cyan
try {
    & java -version
} catch {
    Write-Host "未检测到 Java，请运行 install-jdk25.ps1 安装 JDK 25" -ForegroundColor Red
}

Write-Host ""
Write-Host "环境变量 JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Gray
Write-Host ""
