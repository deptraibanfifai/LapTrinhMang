@echo off
chcp 65001 >nul
title RMI BANK CLIENT - GIAO DIEN NGAN HANG

echo ============================================================================
echo   KHOI DONG CLIENT GIAO DIEN NGAN HANG (SWING GUI)
echo ============================================================================

java -cp "target\classes;lib\mysql-connector-j-9.5.0.jar" vku.bank.rmi.client.BankClientMain

pause
