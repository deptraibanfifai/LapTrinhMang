# Kiểm thử và chuẩn bị vấn đáp

## 1. Kết quả đã thực hiện — 29/10/2026

| Nhóm | Kết quả |
|---|---|
| Build Java 17, đóng JAR kèm dependency | Thành công |
| `IntegrationChecks` | 45 kiểm tra đạt với 6 kết nối TCP thật đồng thời |
| `UdpChecks lo` — multicast loopback | Đạt 3 tình huống: chỉ người join nhận; hai thành viên cùng nhận; leave thì ngừng nhận |
| Broadcast UDP tới nhiều máy LAN | Chưa kiểm tra; môi trường kiểm thử chỉ có loopback, không có địa chỉ broadcast |
| Giao diện desktop Windows/Linux | Chưa kiểm tra trực quan trong môi trường hiện tại |
| Micro, loa, WAV từ micro thật | Chưa kiểm tra trên thiết bị thật |
| Webcam và gọi hai chiều giữa hai máy | Chưa kiểm tra trên thiết bị thật |
| Thử nạp driver webcam trong Linux hiện tại | Chưa nạp được vì hệ thống thiếu `libv4l2.so.0`; cần gói Video4Linux tương ứng khi chạy webcam trên Linux |

Trong bài kiểm thử tự động, audio/video được kiểm tra **chuyển tiếp payload và quyền tham gia cuộc gọi**. Đây không phải kiểm tra chất lượng thu/phát thực tế hoặc xác minh nội dung PCM/JPEG từ phần cứng.

## 2. Chạy lại kiểm tra tự động

Windows:

```powershell
./check.bat
```

Ubuntu:

```bash
mvn -DskipTests package
java -Djava.net.preferIPv4Stack=true -cp target/test-classes:target/lab01-chat-1.0.0.jar vn.vku.chat.IntegrationChecks
```

Main kiểm thử là chương trình Java thường, không phải JUnit, nên `mvn test` không tự chạy 45 kiểm tra này. Dùng lệnh trên hoặc file `check.bat`. Bài kiểm tra tự tạo/đóng server cổng TCP ngẫu nhiên.

Các kiểm tra gồm codec Unicode/emoji, tách hai packet nối nhau, từ chối độ dài sai, 6 client, online, tên trùng/sai, không tin sender giả, chat riêng/chung, tính nguyên vẹn file 128 KiB, payload ghi âm, offline, join/leave, invite/accept/reject/busy/end, chặn media ngoài cuộc gọi và dọn cuộc gọi khi client tắt.

Thử UDP với card LAN cụ thể (thay `ten-card-mang` bằng tên hiển thị trong ứng dụng):

```powershell
java -Djava.net.preferIPv4Stack=true -cp "target/test-classes;target/lab01-chat-1.0.0.jar" vn.vku.chat.client.UdpChecks ten-card-mang
```

Trên Ubuntu thay dấu `;` trong classpath bằng `:`. `UdpChecks` tự mở hai client không GUI. Nếu card có địa chỉ broadcast, bài kiểm tra yêu cầu cả hai nhận được broadcast. Kết quả `SKIP` không được xem là broadcast đã đạt. Kiểm tra này vẫn chỉ diễn ra trên một máy; demo nhiều máy phải thực hiện bảng sau.

## 3. Bảng kiểm tra thủ công để đủ bài chấm

Tích đạt sau khi **bạn thực sự chạy và thấy kết quả**.

| STT | Cách thử | Kết quả phải thấy | Đạt |
|---|---|---|---|
| 1 | Khởi động server, kết nối 6 client tên khác nhau | Cả 6 online và thấy danh sách đủ 6 tên | [ ] |
| 2 | Kết nối tên trùng | Bị từ chối, client trước vẫn hoạt động | [ ] |
| 3 | A chat riêng B | Chỉ A/B thấy nội dung | [ ] |
| 4 | A gửi chat chung TCP | Cả 6 nhận tin `[TCP chung]` | [ ] |
| 5 | Gửi tiếng Việt và emoji | Nội dung không lỗi mã hóa | [ ] |
| 6 | Gửi broadcast UDP | Cả 6 client cùng subnet nhận `[UDP BROADCAST]` | [ ] |
| 7 | Chỉ A/B/C join, A gửi multicast | Chỉ A/B/C nhận `[UDP MULTICAST]` | [ ] |
| 8 | C leave, A gửi lại multicast | A/B nhận, C không nhận tin mới | [ ] |
| 9 | Chưa join mà gửi nhóm | Ứng dụng báo chưa tham gia | [ ] |
| 10 | A gửi ảnh/PDF/file nhị phân riêng B | B lưu/mở đúng; các client khác không nhận file | [ ] |
| 11 | So sánh SHA-256 file gốc và file nhận | Hai hash giống nhau | [ ] |
| 12 | Gửi file >20 MiB hoặc file rỗng | Bị từ chối có thông báo | [ ] |
| 13 | Ghi âm 10 giây và gửi | Người nhận bấm phát nghe được bản ghi | [ ] |
| 14 | Gọi thoại giữa 2 máy | Popup mời, đồng ý, hai phía nghe được | [ ] |
| 15 | Gọi video giữa 2 máy | Thấy webcam local/remote và nghe thoại | [ ] |
| 16 | Từ chối lời mời | Caller nhận lý do; có thể gọi lại | [ ] |
| 17 | Không trả lời 30 giây | Hai phía trở về rảnh | [ ] |
| 18 | A/B đang gọi, C gọi B | C nhận thông báo bận | [ ] |
| 19 | Một bên bấm kết thúc | Hai bên dừng gọi, thiết bị được giải phóng | [ ] |
| 20 | Đóng đột ngột một client đang gọi | Người kia được báo, online cập nhật | [ ] |
| 21 | Dừng server rồi khởi động lại | Client báo mất kết nối, có thể kết nối lại | [ ] |

Trên Windows so hash bằng:

```powershell
Get-FileHash "duong-dan-file-goc" -Algorithm SHA256
Get-FileHash "duong-dan-file-nhan" -Algorithm SHA256
```

## 4. Kịch bản demo ngắn theo đúng yêu cầu

1. Giới thiệu mô hình Client–Server; chỉ ra cửa sổ server và 6 client.
2. Gửi text có tiếng Việt và biểu cảm; thử riêng và chung.
3. Chuyển sang broadcast UDP và gửi một tin tới tất cả.
4. Cho ba người join multicast, gửi; cho một người leave, gửi lại.
5. Gửi file, mở ở người nhận và đối chiếu hash nếu cần.
6. Ghi âm, phát lại; gọi thoại/video giữa hai máy thật.
7. Từ chối một lời mời hoặc đóng một client để thể hiện xử lý tình huống.
8. Mở source và giải thích luồng TCP, UDP, đóng gói packet và đa luồng.

## 5. Câu hỏi vấn đáp thường gặp

**Vì sao không chỉ làm một file Java?**

Để tách phần giao diện, socket, xử lý server và thiết bị. Có thể tìm đúng chức năng mà không phải đọc toàn bộ chương trình.

**Server có nghe 6 cổng TCP cho 6 người không?**

Không. Một `ServerSocket` nghe cổng 5500. Mỗi lần accept trả về một socket kết nối riêng; các kết nối được phân biệt bằng địa chỉ/cổng hai đầu.

**Vì sao phải dùng nhiều luồng?**

Vì đọc socket và thu thiết bị có thể chờ. Mỗi session có luồng đọc/ghi riêng; giao diện không chạy các thao tác chờ đó trên EDT.

**Chat chung có phải broadcast không?**

Chat chung TCP trong ứng dụng là server lặp qua các kết nối. IP broadcast thực sự nằm ở `UdpPublisher`, gửi datagram tới địa chỉ broadcast của subnet, nhận ở cổng 5501.

**Multicast khác broadcast thế nào?**

Broadcast phát tới subnet; multicast phát tới IP nhóm và ứng dụng join nhóm để nhận. Trong demo, chỉ những client tích tham gia nhóm mở socket join `239.192.5.10`.

**Tại sao chọn card mạng?**

Máy có thể có nhiều card, gồm cả VPN/máy ảo. Chọn card giúp multicast đi ra đúng mạng và nhận trên đúng interface.

**Nếu mất một datagram UDP thì sao?**

Phiên bản này không gửi lại, vì UDP không bảo đảm giao nhận hay thứ tự. Nếu cần độ tin cậy phải thêm sequence number, ACK và retransmission, hoặc dùng TCP.

**TCP có tự biết ranh giới tin nhắn không?**

Không. Source dùng header độ dài và `readFully()` để đọc đúng số byte của từng trường/payload.

**Ai quyết định sender?**

Server lấy tên từ session sau HELLO và ghi đè sender trong packet. Client không được tự mạo danh bằng cách sửa trường sender. Đây vẫn là nhận diện nickname theo kết nối, chưa có xác thực mật khẩu.

**File và ảnh có chuyển thành text không?**

Không. File nằm ở `byte[] data`; codec ghi độ dài rồi ghi nguyên bytes. Ảnh webcam được mã hóa JPEG trước khi đưa vào payload.

**Audio truyền như thế nào?**

Gọi thoại dùng PCM mono 16 kHz, 16 bit; mỗi chunk khoảng 3200 byte. Ghi âm dùng cùng PCM rồi đóng thành WAV và gửi dưới dạng `VOICE`.

**Video có phải WebRTC không?**

Không. Bản lab chụp webcam, nén từng frame JPEG và chuyển qua TCP. Cách này dễ đọc nhưng chưa có các kỹ thuật tối ưu/đồng bộ của WebRTC.

**Có database chưa?**

Chưa. Đề ghi database là phần khuyến khích. Bản này ưu tiên hoàn thành và hiểu socket, text/media/file, multicast/broadcast và 6 client. Có thể bổ sung lưu lịch sử sau khi demo các phần này đạt.

## 6. Xử lý lỗi nhanh

| Biểu hiện | Kiểm tra |
|---|---|
| Không tìm thấy `mvn` hoặc `javac` | PATH, JDK 17, Maven; chỉ chạy JAR thì chưa cần Maven |
| `Address already in use` | Đã có server khác trên cổng 5500; dừng bản cũ hoặc đổi TCP port cả hai đầu |
| `Connection refused` | Server đã START chưa, IP/port đúng chưa |
| TCP chạy nhưng UDP không nhận | Đúng card LAN, cùng subnet, UDP 5501/5502, router/AP không chặn; thử `UdpChecks` |
| Multicast không nhận | Đã tích tham gia, đúng card, firewall và hỗ trợ multicast của mạng |
| UDP nhầm card khi test localhost | Server/client phải cùng chọn card vì UDP đi qua card đó dù TCP dùng 127.0.0.1 |
| Ghi âm/gọi thoại không có tiếng | Quyền micro, thiết bị mặc định, định dạng PCM, âm lượng; dùng tai nghe |
| Webcam không mở | Quyền camera, camera đang bị tiến trình khác dùng, driver và tương thích thư viện trên máy |
| Linux báo thiếu `libv4l2.so.0` | Tra gói bằng `apt-cache search '^libv4l-0'`, cài gói Video4Linux đúng bản Ubuntu (`libv4l-0` hoặc `libv4l-0t64` tùy bản) |
| Gửi file lớn làm gọi bị trễ | File/media chung socket TCP; đợi truyền file xong hoặc giảm kích thước file |
| Không mở được MP4/PDF | File đã nhận chưa, ứng dụng mặc định của hệ điều hành có hỗ trợ loại file không |

Nếu UI báo lỗi thiết bị, không được xem là audio/video đã đạt chỉ vì cuộc gọi có trạng thái “đang gọi”. Phải xác nhận thu/phát trên hai máy thật.
