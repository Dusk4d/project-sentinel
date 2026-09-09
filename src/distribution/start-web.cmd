@echo off
setlocal
set "SENTINEL_WORKSPACE=%~1"
if not defined SENTINEL_WORKSPACE set "SENTINEL_WORKSPACE=%CD%"
set "SENTINEL_PORT=%~2"
if not defined SENTINEL_PORT set "SENTINEL_PORT=8787"
echo Open http://127.0.0.1:%SENTINEL_PORT%/ in your browser.
if "%~3"=="" (
  call "%~dp0project-sentinel.cmd" --serve "%SENTINEL_WORKSPACE%" "%SENTINEL_PORT%"
) else (
  call "%~dp0project-sentinel.cmd" --serve "%SENTINEL_WORKSPACE%" "%SENTINEL_PORT%" "%~3"
)
