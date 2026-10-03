@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-dev.ps1" -OpenBrowser %*
if errorlevel 1 (
  echo.
  echo Startup failed. See the error above and logs in .runtime.
  pause
  exit /b 1
)
endlocal
