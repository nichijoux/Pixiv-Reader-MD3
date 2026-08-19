# Pixiv Reader - Release 打包脚本
# 用法：双击 build-release.bat，或命令行执行 pwsh -File build-release.ps1
# 产物：app\build\outputs\apk\release\app-release.apk
# 若已配置 JAVA_HOME 则沿用，否则使用默认 JDK 21

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectRoot

# JDK：未设置或无效时用默认路径
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $env:JAVA_HOME = 'C:\Users\nichijoux\.jdks\jbr-21.0.11'
}
if (-not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    Write-Host "[错误] 未找到 JDK：$env:JAVA_HOME" -ForegroundColor Red
    Write-Host "请设置 JAVA_HOME 环境变量后重试。"
    exit 1
}
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Write-Host "[1/2] 构建 release APK（离线模式）..."
& .\gradlew.bat :app:assembleRelease --console=plain --offline
if ($LASTEXITCODE -ne 0) {
    Write-Host "`n[失败] 构建失败，请查看上方错误信息（^e: 开头行为编译错误）。" -ForegroundColor Red
    exit $LASTEXITCODE
}

$Apk = Join-Path $ProjectRoot 'app\build\outputs\apk\release\app-release.apk'
$Apksigner = Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools\36.0.0\apksigner.bat'
Write-Host "[2/2] 验证签名..."
if (Test-Path $Apksigner) {
    & $Apksigner verify --print-certs $Apk *> $null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[OK] 签名验证通过。"
    } else {
        Write-Host "[警告] 签名验证未通过（APK 仍已生成）。" -ForegroundColor Yellow
    }
} else {
    Write-Host "[警告] 未找到 apksigner，跳过签名验证。" -ForegroundColor Yellow
}

$size = (Get-Item $Apk).Length
Write-Host ""
Write-Host "============================================================"
Write-Host "打包完成："
Write-Host "  APK: $Apk"
Write-Host ("  大小: {0:N0} bytes ({1:N1} MB)" -f $size, ($size / 1MB))
Write-Host "============================================================"
