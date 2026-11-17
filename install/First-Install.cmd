@echo off
rem Double-click launcher for First-Install.ps1. Extra arguments (e.g. -Serial X, -Lang en) are passed on.
rem UTF-8 console so the Portuguese accents print correctly.
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0First-Install.ps1" %*
set RC=%ERRORLEVEL%
echo.
pause
exit /b %RC%
