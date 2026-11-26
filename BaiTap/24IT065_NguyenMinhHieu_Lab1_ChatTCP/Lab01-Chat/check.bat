@echo off
setlocal
cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1
java -Djava.net.preferIPv4Stack=true -cp "target\test-classes;target\lab01-chat-1.0.0.jar" vn.vku.chat.IntegrationChecks %*
if errorlevel 1 exit /b 1
endlocal
