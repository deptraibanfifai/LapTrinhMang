# Lab 2 — Java FTP Server + MySQL (Laragon)

Project gồm hai ứng dụng Java: FTP Server có giao diện bật/tắt và xem log; FTP Client có giao diện kết nối, xem danh sách file, tải lên và tải xuống. Server lưu tài khoản và lịch sử đăng nhập/truyền file trong MySQL. File truyền được lưu trong thư mục `storage` của dự án.

## Yêu cầu

- JDK 17 trở lên
- Maven 3.8 trở lên
- Laragon với MySQL đang chạy
- FileZilla Client để kết nối thử (không cần cài FileZilla Server)

## 1. Tạo database trong Laragon

1. Mở Laragon, chọn **Start All**.
2. Chọn **Database** để mở HeidiSQL (hoặc mở phpMyAdmin).
3. Mở file `database.sql`, chạy toàn bộ nội dung. Script tạo database `lab2_ftp`, bảng `ftp_users`, `transfer_history` và tài khoản mẫu.
4. Mặc định Laragon dùng MySQL `localhost:3306`, user `root`, mật khẩu trống. Nếu máy bạn đặt mật khẩu, đặt biến môi trường `DB_PASSWORD` trước khi chạy.

Tài khoản FTP mẫu: **student / 123456**.

## 2. Chạy chương trình

Mở project bằng IntelliJ IDEA, chờ Maven tải thư viện rồi chạy lần lượt hai ứng dụng:

1. Chạy `FtpServerApp` trước. Bấm **Khởi động FTP Server**. Server lắng nghe cổng `2121`, thư mục gốc là `storage`.
2. Chạy `FtpClientApp` ở một lần chạy khác. Ứng dụng điền sẵn host `127.0.0.1`, port `2121`, user `student`, password `123456`. Bấm **Kết nối**.

Có thể chạy qua Maven terminal. Server:

```bash
mvn clean compile exec:java
```

FTP Client (mở terminal thứ hai, ở cùng thư mục project):

```bash
mvn -Dexec.mainClass=vku.ftp.FtpClientApp exec:java
```

Có thể cấu hình bằng biến môi trường:

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/lab2_ftp?...` | JDBC URL |
| `DB_USER` | `root` | MySQL username |
| `DB_PASSWORD` | rỗng | MySQL password |
| `FTP_PORT` | `2121` | Cổng điều khiển FTP |
| `FTP_ROOT` | `storage` | Thư mục lưu file |

Ví dụ PowerShell nếu MySQL có mật khẩu:

```powershell
$env:DB_PASSWORD = "mat_khau_mysql"
mvn clean compile exec:java
```

## 3. Kết nối bằng FileZilla Client

Trong Site Manager, nhập:

- Host: `127.0.0.1`
- Port: `2121`
- Protocol: `FTP - File Transfer Protocol`
- Encryption: `Only use plain FTP (insecure)` (dùng cho lab nội bộ)
- Logon Type: `Normal`
- User: `student`
- Password: `123456`
- Transfer mode: `Passive`

Chọn **Connect**. Thử upload, tải xuống, tạo thư mục và xóa file. File sẽ nằm trong `storage`; lịch sử nằm trong bảng `transfer_history`.

## 4. Xem dữ liệu MySQL

```sql
USE lab2_ftp;
SELECT id, username, enabled, created_at FROM ftp_users;
SELECT username, action, file_name, file_size, client_ip, created_at
FROM transfer_history ORDER BY id DESC;
```

Thêm người dùng. Mật khẩu được lưu dạng SHA-256:

```sql
INSERT INTO ftp_users(username, password_sha256)
VALUES ('user2', SHA2('pass123', 256));
```

Khóa tài khoản: `UPDATE ftp_users SET enabled=FALSE WHERE username='user2';`

## Hai ứng dụng Java

- `FtpServerApp`: FTP Server, xác thực người dùng từ MySQL và ghi nhật ký truyền file.
- `FtpClientApp`: FTP Client Swing, kết nối tới server, duyệt thư mục, tải file lên và xuống.

## FTP command đã hỗ trợ

`USER`, `PASS`, `SYST`, `FEAT`, `TYPE`, `NOOP`, `PWD`, `CWD`, `CDUP`, `PASV`, `LIST`, `NLST`, `RETR`, `STOR`, `DELE`, `MKD`, `RMD`, `SIZE`, `QUIT`.

## Cấu trúc thư mục

```text
FTPServerMySQL/
├── database.sql
├── pom.xml
├── README.md
├── storage/                 # File upload/download
└── src/main/java/vku/ftp/FtpServerApp.java
```

## Giới hạn của bản lab

Đây là FTP thường, không mã hóa mật khẩu/nội dung truyền tải; chỉ nên chạy trong máy cá nhân hoặc mạng lab tin cậy. Không mở cổng ra Internet. Chưa hỗ trợ FTPS, resume (REST), active mode và nhiều phiên bản nâng cao của FTP. Server dùng passive data ports `21000–21020`.
