@echo off
chcp 65001 >nul
echo ========================================================
echo DANG BIEN DICH DU AN RMI NGAN HANG DONG BO 2 SERVER
echo ========================================================

if not exist "target\classes" mkdir "target\classes"
dir /s /b src\main\java\*.java > sources.txt

javac -cp "lib\mysql-connector-j-9.5.0.jar" -d "target\classes" -encoding UTF-8 @sources.txt

if %ERRORLEVEL% equ 0 (
    echo [OK] Bien dich thanh cong! Toan bo class nam trong thu muc target\classes
) else (
    echo [ERROR] Co loi xay ra khi bien dich!
)

del sources.txt
pause
