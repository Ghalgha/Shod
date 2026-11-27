@echo off
setlocal enabledelayedexpansion

set "VERIFY_ONLY=0"
if "%~1"=="" goto args_ok
if /I "%~1"=="--verify-native-only" (
    set "VERIFY_ONLY=1"
    goto args_ok
)
echo Usage: build_apk.bat [--verify-native-only]
exit /b 2

:args_ok
for %%I in ("%~dp0..") do set "PROJECT_ROOT=%%~fI\\"
set "PROVENANCE_SCRIPT=%PROJECT_ROOT%scripts\\native_client_provenance.ps1"
set "SERVER_PROVENANCE_SCRIPT=%PROJECT_ROOT%scripts\\server_asset_provenance.ps1"
cd /d "%PROJECT_ROOT%"

echo === Shod APK Build Script ===
echo === Output: 4 APKs (universal, arm64-v8a, armeabi-v7a, x86_64) ===
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%SERVER_PROVENANCE_SCRIPT%" -Mode Verify
if %errorlevel% neq 0 (
    echo.
    echo Run rust-server\\build_linux.bat first to rebuild the verified server asset.
    if "%VERIFY_ONLY%"=="0" if not defined CI pause
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%PROVENANCE_SCRIPT%" -Mode Verify
if %errorlevel% neq 0 (
    echo.
    echo Run rust-client\\build_so.bat first to rebuild verified Rust native libraries.
    if "%VERIFY_ONLY%"=="0" if not defined CI pause
    exit /b 1
)
if "%VERIFY_ONLY%"=="1" (
    echo Native verification completed successfully.
    exit /b 0
)

call "%PROJECT_ROOT%scripts\\find_android_sdk.bat"
if %errorlevel% neq 0 (
    if not defined CI pause
    exit /b 1
)
set "ANDROID_HOME=%SDK_PATH%"

echo Incremental build...

echo Building release APKs...
call "%PROJECT_ROOT%gradlew.bat" :app:assembleRelease --no-daemon

if %errorlevel% neq 0 (
    echo.
    echo BUILD FAILED! Please check the errors above.
    if not defined CI pause
    exit /b 1
)

set "APK_DIR=%PROJECT_ROOT%app\\build\\outputs\\apk\\release"
set "RELEASE_DIR=%PROJECT_ROOT%app\\release"

if not exist "%RELEASE_DIR%" mkdir "%RELEASE_DIR%"
if errorlevel 1 (
    echo ERROR: Cannot create release directory.
    if not defined CI pause
    exit /b 1
)

echo.
echo Copying APKs to release folder...

if exist "%APK_DIR%\\app-x86-release.apk" del /Q "%APK_DIR%\\app-x86-release.apk"

call :copy_apk "%APK_DIR%\\app-universal-release.apk" "%RELEASE_DIR%\\Shod-universal.apk"
if errorlevel 1 goto copy_failed
call :copy_apk "%APK_DIR%\\app-arm64-v8a-release.apk" "%RELEASE_DIR%\\Shod-arm64-v8a.apk"
if errorlevel 1 goto copy_failed
call :copy_apk "%APK_DIR%\\app-armeabi-v7a-release.apk" "%RELEASE_DIR%\\Shod-armeabi-v7a.apk"
if errorlevel 1 goto copy_failed
call :copy_apk "%APK_DIR%\\app-x86_64-release.apk" "%RELEASE_DIR%\\Shod-x86_64.apk"
if errorlevel 1 goto copy_failed

echo.
echo === DONE ===
echo Output directory: app\\release\\
echo.
echo   Shod-universal.apk    - all architectures in one APK
echo   Shod-arm64-v8a.apk    - 64-bit ARM only
echo   Shod-armeabi-v7a.apk  - 32-bit ARM only
echo   Shod-x86_64.apk       - 64-bit x86 only
echo.
if not defined CI pause
exit /b 0

:copy_apk
if not exist "%~1" (
    echo   [ERROR] Source APK not found: %~1
    exit /b 1
)
if exist "%~2" del /Q "%~2"
if exist "%~2" (
    echo   [ERROR] Cannot remove old APK: %~2
    exit /b 1
)
copy /Y "%~1" "%~2" >nul
if errorlevel 1 (
    echo   [ERROR] Copy failed: %~1
    exit /b 1
)
if not exist "%~2" (
    echo   [ERROR] Copied APK is missing: %~2
    exit /b 1
)
for %%F in ("%~2") do (
    if %%~zF LEQ 0 (
        echo   [ERROR] Copied APK is empty: %~2
        exit /b 1
    )
    echo   [OK] %%~nxF  [%%~zF bytes]
)
exit /b 0

:copy_failed
echo.
echo APK copy failed. Build is not successful.
if not defined CI pause
exit /b 1
