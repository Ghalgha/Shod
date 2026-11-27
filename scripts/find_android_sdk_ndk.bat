@echo off
setlocal enabledelayedexpansion

set "SDK_PATH="

if defined ANDROID_HOME (
    if exist "%ANDROID_HOME%" (
        set "SDK_PATH=%ANDROID_HOME%"
    )
)

if not defined SDK_PATH (
    if defined ANDROID_SDK_ROOT (
        if exist "%ANDROID_SDK_ROOT%" (
            set "SDK_PATH=%ANDROID_SDK_ROOT%"
        )
    )
)

if not defined SDK_PATH (
    if exist "%PROJECT_ROOT%local.properties" (
        for /f "tokens=1,* delims==" %%A in ('type "%PROJECT_ROOT%local.properties" ^| findstr /R /C:"^sdk\.dir="') do (
            set "SDK_PATH=%%B"
        )
        if defined SDK_PATH (
            set "SDK_PATH=!SDK_PATH:\\=\!"
            set "SDK_PATH=!SDK_PATH:\:=:!"
            if not exist "!SDK_PATH!" (
                set "SDK_PATH="
            )
        )
    )
)

if not defined SDK_PATH (
    if exist "%LOCALAPPDATA%\Android\Sdk" (
        set "SDK_PATH=%LOCALAPPDATA%\Android\Sdk"
    ) else if exist "%ProgramFiles%\Android\android-sdk" (
        set "SDK_PATH=%ProgramFiles%\Android\android-sdk"
    ) else if exist "%ProgramFiles(x86)%\Android\android-sdk" (
        set "SDK_PATH=%ProgramFiles(x86)%\Android\android-sdk"
    )
)

if not defined SDK_PATH (
    echo Error: Android SDK not found.
    echo        Set ANDROID_HOME or ANDROID_SDK_ROOT, define sdk.dir in local.properties,
    echo        or install Android Studio to the default location.
    exit /b 1
)
echo Using SDK: %SDK_PATH%

set "NDK_PATH="
set "NDK_VERSION="

if defined ANDROID_NDK_HOME call :try_ndk "%ANDROID_NDK_HOME%"
if not defined NDK_PATH if defined ANDROID_NDK_ROOT call :try_ndk "%ANDROID_NDK_ROOT%"
if not defined NDK_PATH if defined NDK_HOME call :try_ndk "%NDK_HOME%"
if not defined NDK_PATH call :try_ndk "%SDK_PATH%\ndk"
if not defined NDK_PATH call :try_ndk "%LOCALAPPDATA%\Android\Sdk\ndk"
if not defined NDK_PATH call :try_ndk "%ProgramFiles%\Android\ndk"

if not defined NDK_PATH (
    echo Error: Valid Android NDK toolchain not found.
    echo        Set ANDROID_NDK_HOME, ANDROID_NDK_ROOT or NDK_HOME,
    echo        or install NDK through Android Studio.
    exit /b 1
)

if defined NDK_VERSION (
    echo Using NDK: %NDK_VERSION%
) else (
    echo Using NDK: %NDK_PATH%
)

endlocal & set "SDK_PATH=%SDK_PATH%" & set "NDK_PATH=%NDK_PATH%" & set "NDK_VERSION=%NDK_VERSION%" & set "TOOLCHAIN=%NDK_PATH%\toolchains\llvm\prebuilt\windows-x86_64\bin"
exit /b 0

:try_ndk
if "%~1"=="" exit /b 0

if exist "%~1\toolchains\llvm\prebuilt\windows-x86_64\bin" (
    set "NDK_PATH=%~1"
    set "NDK_VERSION="
    exit /b 0
)

for /f "delims=" %%D in ('dir /b /ad /o-n "%~1" 2^>nul') do (
    if not defined NDK_PATH if exist "%~1\%%D\toolchains\llvm\prebuilt\windows-x86_64\bin" (
        set "NDK_PATH=%~1\%%D"
        set "NDK_VERSION=%%D"
    )
)
exit /b 0
