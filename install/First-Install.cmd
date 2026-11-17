@echo off
rem Double-click launcher for First-Install.ps1. Extra arguments (e.g. -Serial X) are passed on.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0First-Install.ps1" %*
set RC=%ERRORLEVEL%
echo.
pause
exit /b %RC%
