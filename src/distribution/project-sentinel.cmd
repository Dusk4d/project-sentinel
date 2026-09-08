@echo off
setlocal
set "SENTINEL_JAR=%~dp0workspace-agent.jar"
if not exist "%SENTINEL_JAR%" (
  echo Missing distribution file: %SENTINEL_JAR%
  exit /b 1
)
java -jar "%SENTINEL_JAR%" %*
