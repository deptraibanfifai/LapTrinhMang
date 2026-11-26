@echo off
setlocal
cd /d "%~dp0"
if not exist target\lab01-chat-1.0.0.jar (
  echo Run build.bat first.
  pause
  exit /b 1
)
for /l %%i in (1,1,6) do start "Chat client %%i" java -Djava.net.preferIPv4Stack=true -cp "%~dp0target\lab01-chat-1.0.0.jar" vn.vku.chat.client.ClientMain
endlocal
