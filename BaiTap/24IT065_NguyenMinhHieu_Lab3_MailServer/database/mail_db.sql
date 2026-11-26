-- File: database/mail_db.sql
CREATE DATABASE IF NOT EXISTS mail_db;
USE mail_db;

-- Bảng người dùng
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    full_name VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP NULL
);

-- Bảng email
CREATE TABLE emails (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sender_id INT,
    sender_email VARCHAR(150) NOT NULL,
    receiver_email VARCHAR(150) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT,
    status ENUM('sent', 'received', 'draft', 'deleted') DEFAULT 'sent',
    is_read BOOLEAN DEFAULT FALSE,
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    received_at TIMESTAMP NULL,
    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Bảng đính kèm file
CREATE TABLE attachments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    email_id INT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT DEFAULT 0,
    file_type VARCHAR(100),
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (email_id) REFERENCES emails(id) ON DELETE CASCADE
);

-- Bảng folder (Inbox, Sent, Draft, Trash)
CREATE TABLE folders (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    email_id INT NOT NULL,
    folder_name ENUM('inbox', 'sent', 'draft', 'trash', 'spam') NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (email_id) REFERENCES emails(id) ON DELETE CASCADE
);

-- Insert user mẫu
INSERT INTO users (username, password, email, full_name) VALUES
('admin', 'admin123', 'admin@localhost.com', 'Administrator'),
('user1', 'user123', 'user1@localhost.com', 'User One'),
('user2', 'user234', 'user2@localhost.com', 'User Two');