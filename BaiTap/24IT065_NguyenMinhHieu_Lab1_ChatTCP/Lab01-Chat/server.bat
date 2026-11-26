@echo off
setlocal
cd /d "%~dp0"
if not exist target\lab01-chat-1.0.0.jar (
  echo Run build.bat first.
  pause
  exit /b 1
)
java -Djava.net.preferIPv4Stack=true -cp target\lab01-chat-1.0.0.jar vn.vku.chat.server.ServerMain %*
if errorlevel 1 pause
endlocal
