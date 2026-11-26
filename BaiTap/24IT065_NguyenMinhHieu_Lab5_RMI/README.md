# LAB 5: HỆ THỐNG RMI NGÂN HÀNG ĐỒNG BỘ HÓA DỮ LIỆU TRÊN 2 SERVER

> **Môn học:** Lập trình mạng (LTM) - VKU  
> **Chủ đề:** Hệ thống Ngân hàng phân tán ứng dụng Java RMI, kết nối cơ sở dữ liệu MySQL và cơ chế đồng bộ hóa dữ liệu 2 chiều giữa 2 Server (Active-Active Replication, Chịu lỗi Failover & Catch-up Sync).

---

## 1. TỔNG QUAN HỆ THỐNG

Dự án mô phỏng hệ thống giao dịch ngân hàng phân tán đáp ứng đầy đủ yêu cầu của **Lab 5**:
* **2 Server chạy trên 2 máy thực (Đều có giao diện đồ họa GUI Dashboard riêng):** Mỗi máy Server có một giao diện trực quan hiển thị trạng thái hoạt động, danh sách tài khoản & số dư thời gian thực trong MySQL cục bộ, lịch sử giao dịch và nhật ký đồng bộ RMI. Server vừa phục vụ Client qua RMI, vừa đóng vai trò Peer trao đổi và đồng bộ dữ liệu giao dịch với Server đối tác.
* **Các Client chạy trên máy ảo hoặc máy trạm (Giao diện Swing):** Cung cấp giao diện đồ họa trực quan (Java Swing), cho phép đăng nhập tài khoản, xem số dư, nạp tiền, rút tiền, chuyển khoản và tra cứu lịch sử giao dịch.
* **Cơ chế chịu lỗi (Fault-Tolerance & Failover):** Nếu Server 1 gặp sự cố (mất mạng/tắt server), Client tự động chuyển sang Server 2. Các giao dịch trong thời gian Server đối tác offline được lưu vào hàng đợi và tự động đồng bộ bù ngay khi kết nối lại.

---

## 2. CẤU TRÚC DỰ ÁN

```text
D:\LTM\BaiTap\RMI\
├── bank_db.sql                      # File script tạo database và bảng MySQL
├── pom.xml                          # Cấu hình Maven (nếu mở trong IntelliJ)
├── compile.bat                      # Script biên dịch nhanh mã nguồn
├── run_server1.bat                  # Script khởi động Server 1
├── run_server2.bat                  # Script khởi động Server 2
├── run_client.bat                   # Script khởi động giao diện Client
├── lib/
│   └── mysql-connector-j-9.5.0.jar  # Thư viện MySQL JDBC Driver đi kèm
└── src/main/java/vku/bank/rmi/
    ├── model/
    │   ├── Account.java             # Thực thể tài khoản ngân hàng
    │   ├── Transaction.java         # Thực thể giao dịch ngân hàng
    │   └── SyncPackage.java         # Gói dữ liệu đồng bộ 2 chiều
    ├── remote/
    │   └── BankRemote.java          # RMI Interface từ xa
    ├── server/
    │   ├── BankStore.java           # Tầng truy xuất MySQL (JDBC Transaction)
    │   ├── BankServiceImpl.java     # Triển khai RMI & Engine đồng bộ Peer
    │   ├── ServerLogListener.java   # Listener cập nhật sự kiện lên GUI
    │   └── BankServerGUI.java       # Giao diện và điểm khởi động Server
    └── client/
        ├── BankClientGUI.java       # Giao diện Swing cho khách hàng
        └── BankClientMain.java      # Điểm khởi động Client
```

---

## 3. CÀI ĐẶT CƠ SỞ DỮ LIỆU MYSQL

1. Bật MySQL trên cả 2 máy (sử dụng **Laragon**, **XAMPP** hoặc **MySQL Workbench**).
2. Mở công cụ quản lý CSDL (ví dụ HeidiSQL trong Laragon hoặc phpMyAdmin) và mở file `bank_db.sql`.
3. Chạy script `bank_db.sql` để tạo database `bank_db` và các bảng:
   * `accounts`: Lưu thông tin tài khoản, mật khẩu PIN, số dư, mốc thời gian cập nhật `updated_at`.
   * `transactions`: Lưu chi tiết giao dịch (Nạp, Rút, Chuyển khoản, Mã GD, Server tạo).
   * `sync_logs`: Lưu nhật ký các sự kiện đồng bộ RMI (`PUSH`, `PULL`, `SYNC`, `QUEUE`).
4. **Tài khoản mẫu được tạo sẵn để test:**
   * `1001` - Nguyễn Văn An (PIN: `1234`) - Số dư ban đầu: `10,000,000 ₫`
   * `1002` - Trần Thị Bình (PIN: `1234`) - Số dư ban đầu: `5,000,000 ₫`
   * `1003` - Lê Văn Cường (PIN: `1234`) - Số dư ban đầu: `20,000,000 ₫`
   * `1004` - Phạm Minh Đức (PIN: `1234`) - Số dư ban đầu: `1,500,000 ₫`

---

## 4. HƯỚNG DẪN CHẠY THỬ TRÊN CÙNG 1 MÁY (LOCALHOST TEST)

Dự án đã được cấu hình sẵn để bạn có thể kiểm tra đầy đủ mọi chức năng trên 1 máy tính:

1. **Bước 1 - Khởi động Server 1:**
   * Nhấp đúp vào `run_server1.bat` (Server 1 lắng nghe tại Port RMI `1099`, kết nối sang Server 2 tại Port `1100`).
2. **Bước 2 - Khởi động Server 2:**
   * Nhấp đúp vào `run_server2.bat` (Server 2 lắng nghe tại Port RMI `1100`, kết nối sang Server 1 tại Port `1099`).
3. **Bước 3 - Khởi động Client:**
   * Nhấp đúp vào `run_client.bat`.
   * Bấm nút **"KẾT NỐI SERVER"** (mặc định kết nối Server 1).
   * Chọn tài khoản mẫu (ví dụ `1001`) và bấm **"Đăng Nhập"**.
   * Thử nạp tiền hoặc chuyển tiền -> Mở tab **"Đồng Bộ 2 Server & Đối Soát"** để xem nhật ký PUSH/PULL thời gian thực.

---

## 5. HƯỚNG DẪN TRIỂN KHAI TRÊN 2 MÁY THỰC & MÁY ẢO (ĐÁP ỨNG ĐÚNG CÁCH CHẤM THI)

### Bước 1: Xác định địa chỉ IP của 2 máy thực
* Trên mỗi máy thực, mở CMD/PowerShell gõ `ipconfig` để lấy địa chỉ IPv4 trong mạng LAN (ví dụ kết nối chung Wifi hoặc mạng phòng Lab):
  * **Máy thực 1:** Giả sử IP là `192.168.1.10`
  * **Máy thực 2:** Giả sử IP là `192.168.1.20`

### Bước 2: Chỉnh sửa IP trong file BAT hoặc chạy lệnh trực tiếp

* **Trên Máy thực 1 (Server 1):**
  * Mở chuột phải chọn Edit file `run_server1.bat`:
    ```bat
    SET SERVER_NAME=SERVER-1
    SET MY_IP=192.168.1.10
    SET REGISTRY_PORT=1099
    SET SERVICE_PORT=10991
    SET PEER_IP=192.168.1.20
    SET PEER_REGISTRY_PORT=1099
    ```
  * Chạy file `run_server1.bat`.

* **Trên Máy thực 2 (Server 2):**
  * Mở chuột phải chọn Edit file `run_server2.bat`:
    ```bat
    SET SERVER_NAME=SERVER-2
    SET MY_IP=192.168.1.20
    SET REGISTRY_PORT=1099
    SET SERVICE_PORT=10991
    SET PEER_IP=192.168.1.10
    SET PEER_REGISTRY_PORT=1099
    ```
  * Chạy file `run_server2.bat`.

*(Lưu ý: Mở cổng Firewall 1099 và 10991 trên cả 2 máy để cho phép máy khác kết nối qua mạng).*

### Bước 3: Chạy Client trên Máy ảo
* Copy thư mục project sang máy ảo (hoặc chạy từ máy khác trong cùng dải mạng).
* Nhấp đúp vào `run_client.bat`.
* Trên giao diện Client:
  * Nhập ô **Server 1**: IP `192.168.1.10`, Port `1099`.
  * Nhập ô **Server 2**: IP `192.168.1.20`, Port `1099`.
  * Chọn Server muốn kết nối và trải nghiệm các chức năng.

---

## 6. KỊCH BẢN DEMO ĐẠT ĐIỂM TỐI ĐA KHI THẦY CÔ HỎI THI

1. **Kịch bản 1: Kiểm tra tính đúng đắn và đồng bộ tức thời (Real-time Replication)**
   * Client kết nối đến **Server 1**.
   * Đăng nhập tài khoản `1001` (đang có 10,000,000 ₫).
   * Nạp thêm `2,000,000 ₫` -> Số dư tăng lên `12,000,000 ₫`.
   * Mở CSDL MySQL trên **Máy thực 2 (Server 2)** hoặc chuyển Client sang kết nối **Server 2**: Số dư của `1001` trên Server 2 cũng đã lập tức nhảy lên `12,000,000 ₫`.

2. **Kịch bản 2: Kiểm tra khả năng chịu lỗi (Fault Tolerance & Failover)**
   * Đang thao tác trên **Server 1**, tiến hành tắt cửa sổ dòng lệnh của Server 1 (hoặc ngắt kết nối mạng máy 1).
   * Trên Client bấm nút giao dịch (rút tiền hoặc làm mới) -> Client phát hiện Server 1 mất kết nối, tự động kích hoạt **Failover** chuyển sang **Server 2** mượt mà, thông báo cho người dùng và không làm gián đoạn trải nghiệm.

3. **Kịch bản 3: Kiểm tra cơ chế hàng đợi và đồng bộ bù (Catch-up / Eventual Consistency)**
   * Tắt hẳn **Server 2** (Server 1 vẫn đang chạy).
   * Trên Client kết nối Server 1, thực hiện chuyển khoản `1,000,000 ₫` từ TK `1001` sang TK `1002`.
   * Server 1 ghi nhận giao dịch thành công, ghi vào hàng đợi `QUEUE` do Server 2 đang offline.
   * Khởi động lại **Server 2**: Hệ thống ngầm phát hiện Server 2 đã online trở lại, tự động gửi bù giao dịch bị thiếu sang Server 2. Khi kiểm tra CSDL Server 2, cả TK `1001` và `1002` đều được cập nhật hoàn toàn chính xác!
