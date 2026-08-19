@echo off
rem Pixiv Reader release build launcher (ASCII only, see build-release.ps1)
cd /d "%~dp0"
where pwsh >nul 2>&1
if errorlevel 1 (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-release.ps1"
) else (
    pwsh -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-release.ps1"
)
set "EXIT=%errorlevel%"
echo.
pause
exit /b %EXIT%
