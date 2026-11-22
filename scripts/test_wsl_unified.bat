@echo off
setlocal
chcp 65001 >nul
title Shod WSL Checks
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0test_wsl_unified.ps1" %*
set "SHOD_EXIT_CODE=%ERRORLEVEL%"
if "%SHOD_EXIT_CODE%"=="0" (echo [OK] WSL checks completed.) else (echo [ERROR] WSL checks failed with exit code %SHOD_EXIT_CODE%.)
if not defined CI if not "%SHOD_WSL_NO_PAUSE%"=="1" pause
exit /b %SHOD_EXIT_CODE%
