@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ============================================
echo   Luma Music - Windows Release Builder
echo ============================================
echo.

REM --- Detect version from composeApp/build.gradle.kts (or pass it: build-release.bat 3.0.2) ---
set VERSION=
for /f "usebackq tokens=2 delims==" %%A in (`findstr /C:"packageVersion" "composeApp\build.gradle.kts"`) do (
    set VERSION=%%A
)
set VERSION=%VERSION: =%
set VERSION=%VERSION:"=%

if not "%~1"=="" set VERSION=%~1

if "%VERSION%"=="" (
    echo ERROR: Could not detect version from composeApp\build.gradle.kts.
    echo Pass it manually, e.g.:  build-release.bat 3.0.2
    pause
    exit /b 1
)

echo Version detected: %VERSION%
echo.

REM --- Stage + commit any pending app-icon changes (safe no-op if nothing changed) ---
git rev-parse --is-inside-work-tree >nul 2>&1
if not errorlevel 1 (
    git add composeApp\src\desktopMain\resources\icon.png composeApp\src\desktopMain\resources\icon.ico >nul 2>&1
    git diff --cached --quiet
    if errorlevel 1 (
        echo Committing app icon update...
        git commit -m "chore: update app icon" >nul 2>&1
    ) else (
        echo No icon changes to commit.
    )
    echo.
) else (
    echo Not a git repository - skipping commit step.
    echo.
)

REM --- Build the Windows distributable with Gradle ---
echo Building distributable with Gradle (this can take a while)...
call gradlew.bat :composeApp:createDistributable
if errorlevel 1 (
    echo.
    echo ERROR: Gradle build failed.
    pause
    exit /b 1
)

set "DIST_DIR=composeApp\build\compose\binaries\main\app\LumaMusic"
if not exist "%DIST_DIR%" (
    echo ERROR: Expected build output not found at "%DIST_DIR%"
    pause
    exit /b 1
)

REM --- Package it the same way as the existing releases: LumaMusic-vX.Y.Z-windows\LumaMusic\... ---
set "OUT_NAME=LumaMusic-v%VERSION%-windows"

echo Packaging %OUT_NAME% ...
if exist "..\%OUT_NAME%" rd /s /q "..\%OUT_NAME%"
mkdir "..\%OUT_NAME%\LumaMusic"
xcopy "%DIST_DIR%" "..\%OUT_NAME%\LumaMusic\" /E /I /Q /Y >nul

echo Zipping...
if exist "..\%OUT_NAME%.zip" del /q "..\%OUT_NAME%.zip"
powershell -NoProfile -Command "Compress-Archive -Path '..\%OUT_NAME%' -DestinationPath '..\%OUT_NAME%.zip' -CompressionLevel Optimal"

echo.
echo ============================================
echo   Done!
echo   Folder: ..\%OUT_NAME%
echo   Zip:    ..\%OUT_NAME%.zip
echo ============================================
pause
