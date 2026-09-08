@echo off
setlocal
cd /d "%~dp0"

set "SENTINEL_WORKSPACE=%~1"
if not defined SENTINEL_WORKSPACE for %%I in ("%~dp0..") do set "SENTINEL_WORKSPACE=%%~fI"
set "SENTINEL_PORT=%~2"
if not defined SENTINEL_PORT set "SENTINEL_PORT=8787"

echo Preparing the latest Project Sentinel build...
call mvnw.cmd --batch-mode --no-transfer-progress package
if errorlevel 1 (
  echo Build failed. The web server was not started.
  exit /b 1
)

echo Open http://127.0.0.1:%SENTINEL_PORT%/ in your browser.
echo Scanning workspace: %SENTINEL_WORKSPACE%
java -jar "target\workspace-agent-0.2.0.jar" --serve "%SENTINEL_WORKSPACE%" "%SENTINEL_PORT%"
