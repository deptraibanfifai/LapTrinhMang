@echo off
chcp 65001 >nul
title RMI BANK SERVER 2 - GUI DASHBOARD

:: ============================================================================
:: CẤU HÌNH CHO SERVER 2 (CÓ GIAO DIỆN ĐỒ HỌA GUI)
:: Khi chạy trên 2 máy thực, hãy thay 127.0.0.1 bằng IP thực tế của từng máy
:: Ví dụ:
:: SET MY_IP=192.168.1.20 (IP máy thực 2)
:: SET PEER_IP=192.168.1.10 (IP máy thực 1)
:: ============================================================================
SET SERVER_NAME=SERVER-2
SET MY_IP=127.0.0.1
SET REGISTRY_PORT=1100
SET SERVICE_PORT=11001
SET PEER_IP=127.0.0.1
SET PEER_REGISTRY_PORT=1099

:: Cấu hình MySQL Cục bộ
SET DB_URL=jdbc:mysql://localhost:3306/bank_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh
SET DB_USER=root
SET DB_PASS=

echo ============================================================================
echo   KHOI DONG GIAO DIEN BIEU DO DASHBOARD: %SERVER_NAME%
echo   RMI Port: %REGISTRY_PORT% ^| Peer: %PEER_IP%:%PEER_REGISTRY_PORT%
echo ============================================================================

java -cp "target\classes;lib\mysql-connector-j-9.5.0.jar" vku.bank.rmi.server.BankServerGUI %SERVER_NAME% %MY_IP% %REGISTRY_PORT% %SERVICE_PORT% %PEER_IP% %PEER_REGISTRY_PORT% "%DB_URL%" "%DB_USER%" "%DB_PASS%"

pause
