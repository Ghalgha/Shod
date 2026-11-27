@echo off
setlocal enabledelayedexpansion

set "SDK_PATH="
set "SDK_CANDIDATE="

if defined ANDROID_HOME (
    call :try_sdk "%ANDROID_HOME%"
)

if not defined SDK_PATH if defined ANDROID_SDK_ROOT (
    call :try_sdk "%ANDROID_SDK_ROOT%"
)

if not defined SDK_PATH (
    if exist "%PROJECT_ROOT%local.properties" (
        for /f "tokens=1,* delims==" %%A in ('type "%PROJECT_ROOT%local.properties" ^| findstr /R /C:"^sdk\.dir="') do (
            set "SDK_CANDIDATE=%%B"
        )
        if defined SDK_CANDIDATE (
            set "SDK_CANDIDATE=!SDK_CANDIDATE:\\=\!"
            set "SDK_CANDIDATE=!SDK_CANDIDATE:\:=:!"
            call :try_sdk "!SDK_CANDIDATE!"
        )
    )
)

if not defined SDK_PATH (
    call :try_sdk "%LOCALAPPDATA%\Android\Sdk"
)

if not defined SDK_PATH (
    call :try_sdk "%ProgramFiles%\Android\android-sdk"
)

if not defined SDK_PATH (
    call :try_sdk "%ProgramFiles(x86)%\Android\android-sdk"
)

if not defined SDK_PATH (
    echo Error: A valid Android SDK with platform android-37 was not found.
    echo        Set ANDROID_HOME or ANDROID_SDK_ROOT, define sdk.dir in local.properties,
    echo        or install the Android 37 platform through Android Studio.
    exit /b 1
)

echo Using SDK: %SDK_PATH%
endlocal & set "SDK_PATH=%SDK_PATH%"
exit /b 0

:try_sdk
if not exist "%~1\platforms\android-37\android.jar" (
    if not "%~1"=="" echo Ignoring invalid Android SDK: %~1
    exit /b 0
)
set "SDK_PATH=%~1"
exit /b 0
