-- ========================================================
-- DATABASE CHO HỆ THỐNG NGÂN HÀNG ĐỒNG BỘ 2 SERVER (RMI)
-- Môn: Lập trình mạng (Lab 5) - VKU
-- ========================================================

CREATE DATABASE IF NOT EXISTS `bank_db` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `bank_db`;

-- 1. Bảng tài khoản khách hàng (Accounts)
DROP TABLE IF EXISTS `transactions`;
DROP TABLE IF EXISTS `sync_logs`;
DROP TABLE IF EXISTS `accounts`;

CREATE TABLE `accounts` (
    `account_number` VARCHAR(20) NOT NULL PRIMARY KEY,
    `holder_name` VARCHAR(100) NOT NULL,
    `pin` VARCHAR(20) NOT NULL,
    `balance` DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    `updated_at` BIGINT NOT NULL,
    `updated_by` VARCHAR(50) NOT NULL,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Bảng lịch sử giao dịch (Transactions)
CREATE TABLE `transactions` (
    `tx_id` VARCHAR(50) NOT NULL PRIMARY KEY,
    `account_number` VARCHAR(20) NOT NULL,
    `tx_type` VARCHAR(20) NOT NULL, -- DEPOSIT, WITHDRAW, TRANSFER_OUT, TRANSFER_IN
    `amount` DECIMAL(15, 2) NOT NULL,
    `target_account` VARCHAR(20) DEFAULT NULL,
    `description` VARCHAR(255) DEFAULT NULL,
    `created_at` BIGINT NOT NULL,
    `created_by` VARCHAR(50) NOT NULL,
    CONSTRAINT `fk_tx_account` FOREIGN KEY (`account_number`) REFERENCES `accounts` (`account_number`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Bảng nhật ký đồng bộ giữa 2 Server (Sync Logs)
CREATE TABLE `sync_logs` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `event_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `server_name` VARCHAR(50) NOT NULL,
    `action_name` VARCHAR(50) NOT NULL,
    `message` TEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================================
-- DỮ LIỆU MẪU BAN ĐẦU
-- ========================================================
INSERT INTO `accounts` (`account_number`, `holder_name`, `pin`, `balance`, `updated_at`, `updated_by`, `deleted`) VALUES
('1001', 'Nguyen Van An', '1234', 10000000.00, 1700000000000, 'INIT', 0),
('1002', 'Tran Thi Binh', '1234', 5000000.00, 1700000000000, 'INIT', 0),
('1003', 'Le Van Cuong', '1234', 20000000.00, 1700000000000, 'INIT', 0),
('1004', 'Pham Minh Duc', '1234', 1500000.00, 1700000000000, 'INIT', 0);

INSERT INTO `sync_logs` (`server_name`, `action_name`, `message`) VALUES
('SYSTEM', 'INIT', 'Khoi tao co so du lieu ngan hang bank_db voi 4 tai khoan mau.');
