@echo off
setlocal
cd /d "%~dp0"
call mvn.cmd -DskipTests package
if errorlevel 1 exit /b 1
echo Build OK: target\lab01-chat-1.0.0.jar
endlocal
