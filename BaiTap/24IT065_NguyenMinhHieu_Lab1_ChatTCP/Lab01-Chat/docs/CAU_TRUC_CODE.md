# Bản đồ mã nguồn

```text
Lab01-Chat/
  pom.xml
  README.md
  build.bat / build.sh
  server.bat / server.sh
  client.bat / client.sh
  demo-6-clients.bat
  check.bat
  docs/
    PHAN_TICH.md
    CAU_TRUC_CODE.md
    KIEM_THU.md
  src/main/java/vn/vku/chat/
    common/
      Config.java
      Packet.java
      PacketCodec.java
      NetUtil.java
    server/
      ServerMain.java
      ChatServer.java
      ClientSession.java
      UdpPublisher.java
    client/
      ClientMain.java
      ClientFrame.java
      ChatClient.java
      UdpReceiver.java
      CallController.java
    media/
      AudioService.java
      VoiceRecorder.java
      CameraService.java
  src/test/java/vn/vku/chat/
    IntegrationChecks.java
    client/UdpChecks.java
  target/
    lab01-chat-1.0.0.jar
```

## 1. Common

| File | Tìm gì trong file? |
|---|---|
| `Config.java` | TCP/UDP port, địa chỉ multicast, giới hạn file/text/video |
| `Packet.java` | Các trường type, sender, target, id, text, data |
| `PacketCodec.java` | `write/read`: framing; `encode/decode`: chuyển packet thành datagram và ngược lại |
| `NetUtil.java` | Liệt kê/chọn card, lấy IPv4/broadcast, tạo luồng daemon |

## 2. Server

| File | Tìm gì trong file? |
|---|---|
| `ServerMain.java` | Hàm main, cửa sổ server, nút START/STOP, chế độ console |
| `ChatServer.java` | `start`: accept; `register`: tên trùng; `usersChanged`: online; `handle`: phân loại packet; `route`: riêng/chung; `signal`: cuộc gọi; `remove`: offline |
| `ClientSession.java` | Handshake, đọc socket, hàng đợi ghi riêng, đóng kết nối |
| `UdpPublisher.java` | `publish`: gửi tới broadcast của card hoặc nhóm multicast |

## 3. Client

| File | Tìm gì trong file? |
|---|---|
| `ClientMain.java` | Điểm khởi động một cửa sổ client |
| `ClientFrame.java` | UI, `connect`, `sendText`, `sendFile`, `recordVoice`, `invite`, `onPacket`, `saveAttachment` |
| `ChatClient.java` | Constructor kết nối/handshake; `start` mở luồng đọc/ghi; `send` xếp packet; `joinGroup`; `disconnect` |
| `UdpReceiver.java` | Bind broadcast, join/leave multicast, nhận datagram, lọc đúng phiên server |
| `CallController.java` | Lời mời, đồng ý/từ chối, bắt đầu media, nhận media, kết thúc và giải phóng thiết bị |

## 4. Media

| File | Tìm gì trong file? |
|---|---|
| `AudioService.java` | Định dạng PCM, đọc micro, hàng đợi audio nhận, phát loa |
| `VoiceRecorder.java` | Ghi 10 giây, tạo WAV, phát WAV |
| `CameraService.java` | Mở webcam, ảnh JPEG 320×240, gửi và preview |

## 5. Lần theo một chức năng từ nút bấm đến người nhận

### Gửi text riêng

1. Người dùng bấm Gửi → `ClientFrame.sendText()`.
2. Tạo `Packet(type=TEXT, target=tên người nhận)` → `ChatClient.send()`.
3. Writer của client dùng `PacketCodec.write()` ghi bytes lên socket.
4. `ClientSession.run()` dùng `PacketCodec.read()` đọc đủ một packet.
5. `ChatServer.handle()` gán lại sender theo session.
6. `ChatServer.route()` tìm session của người nhận và xếp packet vào hàng đợi.
7. Writer của session ghi packet đến socket người nhận; đồng thời echo cho người gửi.
8. `ChatClient` bên nhận đọc packet → `ClientFrame.onPacket()` → hiển thị.

### Gửi multicast

1. Checkbox tham gia → `ChatClient.joinGroup(true)` → `UdpReceiver.join()`.
2. `joinGroup()` của Java đăng ký card vào IP nhóm; client báo trạng thái `GROUP_JOIN` lên server.
3. Gửi text ở chế độ nhóm → packet `UDP_GROUP` qua TCP.
4. Server kiểm tra người gửi đã tham gia → `UdpPublisher.publish(..., true)`.
5. Server dùng UDP gửi tới `239.192.5.10:5502`.
6. Socket multicast của các thành viên nhận → `ClientFrame.onPacket()` hiển thị `[UDP MULTICAST]`.
7. Rời nhóm → `leaveGroup()` và đóng socket nhóm. Source không dùng TCP làm đường nhận dự phòng cho UDP.

### Gọi video

1. Nút gọi → `CallController.invite()` → server → popup bên nhận.
2. Người nhận đồng ý → `CALL_ACCEPT`; server đánh dấu accepted và báo lại caller kèm loại VIDEO.
3. Hai bên gọi `AudioService.start()` và `CameraService.start()`.
4. Frame JPEG/chunk PCM → `ChatClient.send()` → server kiểm tra ID + hai bên → người kia.
5. `CallController.receiveMedia()` giao audio cho loa và video cho luồng giải mã.
6. Nút kết thúc → `CALL_END`; hai phía đóng micro, loa và webcam.

## 6. Các loại packet

| Type | Ý nghĩa |
|---|---|
| HELLO / WELCOME | Xin kết nối và chấp nhận phiên |
| USERS | Danh sách tên online, phân tách bằng xuống dòng |
| TEXT | Chat TCP riêng hoặc chung |
| FILE / VOICE | File đính kèm / WAV ghi âm |
| GROUP_JOIN / GROUP_LEAVE | Báo tham gia/rời nhóm |
| UDP_BROADCAST / UDP_GROUP | Yêu cầu server phân phối text bằng UDP |
| BROADCAST / MULTICAST | Packet UDP thực tế client nhận |
| CALL_INVITE / CALL_ACCEPT / CALL_REJECT / CALL_END | Signaling cuộc gọi |
| AUDIO / VIDEO | PCM / JPEG trong cuộc gọi đã chấp nhận |
| INFO / ERROR | Thông tin hoặc lỗi |

`target="*"` nghĩa là tất cả trong chat TCP. Với cuộc gọi, `id` là mã cuộc gọi. Với UDP nhận, `id` là mã phiên server để tránh trộn các buổi demo cùng mạng.
