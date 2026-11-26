# Phân tích Lab 1

## 1. Đầu vào, đầu ra, tác nhân

**Người dùng client** nhập IP server, port và nickname; sau đó chọn chế độ gửi, người nhận và nội dung. Đầu ra là tin nhắn, file, âm thanh hoặc hình ảnh phía người nhận.

**Server** lắng nghe kết nối, giữ danh sách client online, xác định danh tính người gửi theo socket, chuyển dữ liệu và điều phối cuộc gọi.

**Môi trường mạng**: cùng máy để học TCP; cùng mạng LAN để kiểm tra broadcast/multicast và cuộc gọi giữa máy thật.

## 2. Tại sao chọn Client–Server?

Đề cho phép Client–Server hoặc P2P. Client–Server thuận tiện cho bài này vì một server quản lý tập trung danh sách online, kiểm tra tên trùng và định tuyến chat riêng. Có thể chỉ vào server để giải thích một thông điệp đi tới người nhận thế nào.

P2P cũng hợp lệ, nhưng phải xử lý tìm peer, giữ nhiều kết nối và phân phối trạng thái. Bộ source chọn một mô hình để bạn học rõ luồng trước.

## 3. Chia thành bốn phần trách nhiệm

| Phần | Trách nhiệm | Không nên đặt vào phần này |
|---|---|---|
| Common | Cấu hình và giao thức chung | Nút bấm giao diện |
| Server | Quản lý client, định tuyến, điều phối cuộc gọi, phát UDP | Thu micro/webcam |
| Client | Kết nối, nhận UDP, điều khiển cuộc gọi, hiển thị | Quyết định danh tính sender thay server |
| Media | Micro, loa, WAV, webcam/JPEG | Danh sách người dùng online |

Đây là lý do tách file. Khi lỗi nằm ở mạng, bạn đọc file mạng; khi lỗi ở micro, bạn đọc service âm thanh.

## 4. Phân biệt các cách gửi

| Cách gửi | Ý nghĩa trong chương trình | Luồng thực tế |
|---|---|---|
| Unicast/chat riêng | Một người gửi tới một người cụ thể | Client → TCP server → TCP người nhận |
| Chat chung TCP | Server chuyển một tin đến tất cả kết nối TCP | Client → TCP server → từng TCP socket |
| Broadcast UDP | Một datagram gửi đến địa chỉ broadcast của subnet | Client yêu cầu TCP → server UDP broadcast → các client đang lắng nghe |
| Multicast UDP | Một datagram gửi đến địa chỉ IP nhóm | Client yêu cầu TCP → server UDP multicast → các client đã join nhóm |

**Chat chung qua vòng lặp TCP không phải IP broadcast.** Vì đề nhắc multicast/broadcast và hỏi TCP/UDP, source triển khai thêm hai kênh UDP thật.

Ở bản này, yêu cầu “gửi UDP” đi từ client tới server qua TCP. Việc phân phối một-nhiều từ server tới các client dùng UDP. Vì thế nếu chặn UDP ở máy nhận thì tin UDP không đến, dù yêu cầu gửi ban đầu vẫn đi qua TCP thành công.

## 5. Những bài toán lập trình mạng cần giải quyết

### 5.1. Nhiều client

`ServerSocket.accept()` nhận kết nối mới. Mỗi kết nối được giao cho một `ClientSession`. Bên trong session có luồng đọc riêng và hàng đợi gửi/luồng ghi riêng. Nếu một client chưa gửi gì, việc nó đang chờ đọc không chặn những client khác.

Các client online được quản lý bằng `ConcurrentHashMap`. Mỗi tên ứng với một session. Khi mất kết nối, session đóng socket và được gỡ khỏi map.

### 5.2. TCP không tự chia tin nhắn

Không thể giả định một lần `write` tương ứng một lần `read`. Vì vậy `PacketCodec` quy định:

1. Magic 4 byte xác định giao thức `VKU1`.
2. Năm trường header; trước mỗi trường có số nguyên 4 byte ghi độ dài UTF-8.
3. Số nguyên 4 byte ghi độ dài payload.
4. Payload đúng số byte đã khai báo.

`readFully()` tiếp tục đọc cho đủ dữ liệu hoặc báo lỗi khi stream bị ngắt. Giới hạn độ dài được kiểm tra trước khi cấp phát mảng.

### 5.3. Không cho nhiều luồng ghi xen kẽ lên một socket

Text, audio, video có thể phát sinh từ các luồng khác nhau. Các luồng đưa `Packet` vào hàng đợi; một luồng writer duy nhất lấy ra và mã hóa. Nhờ vậy header của tin A không xen vào payload của tin B.

Hàng đợi có giới hạn. Khi nghẽn, media có thể bỏ frame/chunk. Một client quá chậm khiến hàng đợi server đầy với tin thông thường sẽ bị đóng kết nối để tránh chặn các client khác.

### 5.4. Giao diện không được chờ socket hoặc thiết bị

Swing cập nhật giao diện trên EDT. Các thao tác kết nối, đọc socket, đọc file, thu âm và webcam chạy ở luồng nền. Khi cần hiển thị, callback dùng `SwingUtilities.invokeLater()`.

### 5.5. Multicast cần đúng card mạng

Một máy có thể có Wi-Fi, Ethernet, VPN và card VirtualBox. `NetworkInterface` chỉ rõ đường phát/nhận multicast. Client gọi `joinGroup()` để tham gia, `leaveGroup()` để rời. Nhiều client trên cùng máy dùng `setReuseAddress(true)` trước khi bind cổng nhận.

### 5.6. Cuộc gọi cần trạng thái

Các trạng thái khái niệm là: rảnh → chờ người nhận → đã chấp nhận/đang gọi → kết thúc. Mỗi lời mời có ID riêng.

- Caller gửi `CALL_INVITE`.
- Callee trả `CALL_ACCEPT` hoặc `CALL_REJECT`.
- Chỉ sau ACCEPT server mới chuyển `AUDIO` và `VIDEO` của đúng hai người.
- `CALL_END`, offline hoặc lời mời hết hạn sẽ dọn trạng thái.

Đây là signaling. Phần thu/phát media nằm trong service riêng.

## 6. Phân tích audio và video

**Text:** UTF-8; giới hạn 2000 ký tự/tin để gói UDP vẫn gọn. Emoji cũng là ký tự Unicode.

**Ghi âm:** PCM mono, 16000 mẫu/giây, 16 bit/mẫu → 32000 byte/giây. Mười giây khoảng 320000 byte dữ liệu PCM, thêm header WAV. Bản ghi được gửi bằng packet `VOICE` qua TCP.

**Thoại trực tiếp:** đọc khoảng 3200 byte micro mỗi lần, tương đương 100 ms âm thanh. Gửi packet `AUDIO`; bên nhận dùng `SourceDataLine` phát ra loa.

**Video trực tiếp:** webcam chụp ảnh, resize 320×240, mã hóa JPEG, gửi packet `VIDEO` khoảng 8 lần/giây. Bên nhận giải mã và cập nhật khung hình.

Chọn TCP cho media giúp dễ triển khai và giải thích trên LAN, nhưng khi mất gói TCP phải chờ truyền lại nên có thể làm hình/tiếng trễ. Source chưa triển khai RTP/WebRTC hay đồng bộ AV như ứng dụng thương mại.

## 7. Thứ tự tự học/thực hiện đề xuất

1. Chạy một server và một client, hiểu HELLO/WELCOME.
2. Chạy hai client, hiểu USERS và TEXT riêng.
3. Chạy sáu client, thử chat chung và ngắt một client.
4. Đọc codec, thử file và emoji.
5. Đọc broadcast và multicast; thử join/leave.
6. Đọc WAV, rồi gọi thoại.
7. Đọc webcam/JPEG và trình tự gọi video.
8. Demo toàn bộ theo bảng kiểm thử; sau đó mới quyết định thêm database nếu còn thời gian.
