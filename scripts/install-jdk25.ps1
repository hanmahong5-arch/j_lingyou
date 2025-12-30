# PowerShell: Install JDK 25 LTS (Eclipse Temurin)
# Run: .\scripts\install-jdk25.ps1

param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$JDK_VERSION = "25"
$JDK_FULL_VERSION = "25.0.1+9"
$JDK_URL_VERSION = "25.0.1%2B9"

Write-Host "========================================"
Write-Host "  JDK $JDK_VERSION LTS (Eclipse Temurin)"
Write-Host "========================================"
Write-Host ""

# Check current Java
Write-Host "[1/5] Checking Java..." -ForegroundColor Yellow
try {
    $currentJava = & java -version 2>&1 | Select-String "version"
    Write-Host "Current: $currentJava" -ForegroundColor Gray
} catch {
    Write-Host "No Java detected" -ForegroundColor Gray
}

# Check if JDK 25 installed
$jdkPath = "C:\Program Files\Eclipse Adoptium\jdk-$JDK_VERSION*"
if ((Test-Path $jdkPath) -and !$Force) {
    Write-Host "JDK $JDK_VERSION already installed. Use -Force to reinstall" -ForegroundColor Green
    $installedPath = (Get-Item $jdkPath).FullName
    Write-Host "Path: $installedPath" -ForegroundColor Gray
} else {
    # Download JDK 25
    Write-Host "[2/5] Downloading Eclipse Temurin JDK $JDK_VERSION..." -ForegroundColor Yellow
    $downloadUrl = "https://github.com/adoptium/temurin$JDK_VERSION-binaries/releases/download/jdk-$JDK_URL_VERSION/OpenJDK${JDK_VERSION}U-jdk_x64_windows_hotspot_${JDK_FULL_VERSION}.msi"
    $msiPath = "$env:TEMP\OpenJDK$JDK_VERSION.msi"

    Write-Host "URL: $downloadUrl" -ForegroundColor Gray

    try {
        Invoke-WebRequest -Uri $downloadUrl -OutFile $msiPath -UseBasicParsing
        Write-Host "Download complete: $msiPath" -ForegroundColor Green
    } catch {
        Write-Host "Download failed. Please download manually:" -ForegroundColor Red
        Write-Host "https://adoptium.net/temurin/releases/?version=$JDK_VERSION" -ForegroundColor Cyan
        exit 1
    }

    # Install JDK
    Write-Host "[3/5] Installing JDK $JDK_VERSION..." -ForegroundColor Yellow
    $msiArgs = "/i `"$msiPath`" /quiet ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome"
    Start-Process msiexec.exe -Wait -ArgumentList $msiArgs
    Write-Host "Installation complete" -ForegroundColor Green

    # Cleanup
    Remove-Item $msiPath -Force
}

# Configure environment
Write-Host "[4/5] Configuring environment..." -ForegroundColor Yellow
$jdkHome = (Get-Item "C:\Program Files\Eclipse Adoptium\jdk-$JDK_VERSION*" | Select-Object -First 1).FullName

if ($jdkHome) {
    # Set JAVA_HOME
    [Environment]::SetEnvironmentVariable("JAVA_HOME", $jdkHome, "Machine")
    Write-Host "JAVA_HOME = $jdkHome" -ForegroundColor Green

    # Update PATH
    $machinePath = [Environment]::GetEnvironmentVariable("Path", "Machine")
    $javaPath = "$jdkHome\bin"
    if ($machinePath -notlike "*$javaPath*") {
        $newPath = "$javaPath;$machinePath"
        [Environment]::SetEnvironmentVariable("Path", $newPath, "Machine")
        Write-Host "Added to PATH: $javaPath" -ForegroundColor Green
    }

    # Refresh session
    $env:JAVA_HOME = $jdkHome
    $env:Path = "$jdkHome\bin;$env:Path"
}

# Verify
Write-Host "[5/5] Verifying..." -ForegroundColor Yellow
Write-Host ""
& "$jdkHome\bin\java.exe" -version

Write-Host ""
Write-Host "========================================"
Write-Host "  JDK $JDK_VERSION LTS installed!"
Write-Host "========================================"
Write-Host ""
Write-Host "Restart terminal for PATH to take effect"
Write-Host ""
Write-Host "Run project: mvnd javafx:run"
Write-Host ""
