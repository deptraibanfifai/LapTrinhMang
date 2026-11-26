# Kế hoạch triển khai: Tạo Nhóm Chat & Chức năng Gọi (Audio/Video)

## Repository Research

### Kiến trúc hiện tại
- **ChatServer.java** (`ConcurrentHashMap<String, ClientHandler> clients`): 1 thread / client, giao tiếp qua `DataInputStream/DataOutputStream` qua TCP socket. Các gói tin hiện có: `LOGIN, WELCOME, ERROR, USERS, CHAT, FILE, INFO`.
- **ChatClient.java**: Giao diện Swing (`JFrame`). Recipient hiện có `GROUP = "Tất cả mọi người"` (tương đương `Protocol.EVERYONE = "*"`) + danh sách user riêng lẻ. Có `ExecutorService sender` (1 thread) gửi tin đi, `Thread receiver` đọc tin về.
- **Protocol.java**: `DEFAULT_PORT=5000`, `MAX_FILE_SIZE=20MB`, `MAX_MESSAGE_LENGTH=4000`, hàm `readFile()` + `safeFileName()`.
- **Test**: `ChatIntegrationTest.java` (test end-to-end).

### Ràng buộc / Dependencies
- Dự án là **Java thuần (Swing)**, không có build tool (Ant/Maven/Gradle). Tức là **không thể add JAR lib tự động** (ví dụ `webcam-capture` cho webcam thật). Do đó:
  - **Audio**: Sử dụng `javax.sound.sampled` (có sẵn trong JDK) để Capture mic → phát loa.
  - **Video**: Tạo UI đầy đủ (nút bật/tắt, khung video). Video thực tế sẽ dùng `java.awt.Robot` chụp màn hình và gửi frame JPEG làm "video feed demo" (để chứng minh pipeline gửi/nhận video chạy OK — sau này thay bằng thư viện webcam thật rất dễ).
- Giao thức: Kế thừa mô hình gói tin `UTF` qua TCP hiện có (DataInputStream/DataOutputStream). Audio/Video bytes sẽ đi qua **TCP socket riêng** (hoặc UDP) để không chặn kênh chat. Chọn **TCP riêng cho đơn giản** (không cần xử lý mất gói trong bài tập Lập trình mạng).

---

## Files and Modules (files bị thay / mới)

| File | Thay đổi dự kiến |
|---|---|
| `src/Protocol.java` | Thêm hằng định danh nhóm, audio format, các helper validate group name, encode/decode UDP/TCP port, giới hạn frame size. |
| `src/ChatServer.java` | Thêm `groups` map, `GroupInfo` class. Xử lý các gói tin mới: `CREATE_GROUP`, `GROUP_CHAT`, `GROUP_FILE`, `CALL_*`. Thêm `CallRelay` để forward audio/video bytes giữa 2 client trong cuộc gọi. Thêm cột/thông tin nhóm vào UI server (log). |
| `src/ChatClient.java` | Thêm: (1) Nút "Tạo nhóm" → Dialog (tên nhóm + checklist member). (2) Recipient box hiển thị cả nhóm (icon 👥). (3) Xử lý `GROUP_CHAT`, `GROUP_FILE`. (4) Call UI: Nút 📞 gọi, cửa sổ Call dialog (ảnh đại diện, thời gian gọi, 4 nút: Mic 🎤, Loa 🔊, Video 📹, Cúp 📴). (5) Thread `MicRecorder` và `SpeakerPlayer` (javax.sound). (6) Thread `VideoSender` (Robot chụp màn hình) + `VideoReceiver` (hiển thị ảnh). |
| `tests/ChatIntegrationTest.java` | (Không bắt buộc) Thêm test tạo nhóm/gửi tin nhóm nếu thời gian cho phép. |

---

## Implementation Steps (thứ tự phụ thuộc)

### Step 1 — Mở rộng giao thức (Protocol.java)
- Thêm hằng:
  ```
  String GROUP_PREFIX = "grp_";
  int    MAX_GROUP_NAME_LEN = 30;
  int    MAX_GROUP_MEMBERS  = 20;
  int    AUDIO_BUFFER_SIZE  = 4096;
  int    VIDEO_MAX_FRAME    = 1024 * 1024; // 1MB / frame
  AudioFormat AUDIO_FMT = new AudioFormat(44100f, 16, 1, true, false);
  ```
- Thêm helper `safeGroupName(String)`, `isGroupId(String)`.

### Step 2 — Thêm logic Group vào ChatServer.java
- Class inner `GroupInfo { String id, name, creator; Set<String> members; LocalTime createdAt; }`
- Field: `ConcurrentHashMap<String, GroupInfo> groups = new ConcurrentHashMap<>();`
- Thêm packet handlers mới trong `ClientHandler.run()` loop:
  - `CREATE_GROUP`: Validate → tạo groupId unique ("grp_" + UUID) → lưu `groups` → gửi `GROUP_JOINED` cho tất cả member trong group → `broadcastGroups()` cho từng user.
  - `GROUP_CHAT`: Kiểm tra user có trong group không → forward packet `GROUP_CHAT` tới toàn bộ member.
  - `GROUP_FILE`: Tương tự `GROUP_CHAT` + bytes.
  - Helper `sendGroupsToClient(String userName)`: gửi danh sách nhóm user tham gia.
- Gọi `sendGroupsToClient()` sau khi user đăng nhập WELCOME thành công.

### Step 3 — Thêm UI & logic Group vào ChatClient.java
- Thêm nút **"👥 Tạo nhóm"** cạnh `fileButton` trong `buildBottomPanel()`.
- Khi click: hiển thị `JDialog` gồm:
  - `JTextField` nhập tên nhóm (1-30 ký tự).
  - `JList<String>` với `CheckBoxListRenderer` hoặc `JCheckBox[]` danh sách user online đang có (lấy từ recipientBox ngoại trừ bản thân).
  - Nút OK/Hủy → gửi gói `CREATE_GROUP` lên server.
- **RecipientBox**: Loại hiện tại. Khi nhận gói `GROUPS_LIST`, add các nhóm vào đầu danh sách (prefix icon text `👥 [Tên nhóm]`). Lưu `Map<String, String> displayToId` (hiển thị → id thật).
- Hàm `recipient()`: Nếu lựa chọn là nhóm → trả về `groupId` (bắt đầu bằng `grp_`).
- `sendMessage()` / `sendFile()`: Nếu recipient là group → gửi gói `GROUP_CHAT` / `GROUP_FILE` thay vì `CHAT`.
- `receive()`: Thêm case xử lý:
  - `GROUPS_LIST`: Cập nhập recipientBox với các nhóm.
  - `GROUP_CREATED`: Thông báo (append) nhóm đã tạo.
  - `GROUP_CHAT`: Append tin `[Nhóm - TênNhóm] Người gửi: nội dung`.
  - `GROUP_FILE`: Lưu file + log tương tự FILE nhưng ghi rõ tên nhóm.

### Step 4 — Thêm Call Signaling packets vào ChatServer.java & Protocol
- Định nghĩa các gói `CALL_*` gửi qua kênh chat TCP hiện có (signaling):
  - `CALL_INVITE` (caller→server→callee): `callId, from, to, boolean hasVideo, int audioPort, int videoPort`
  - `CALL_ACCEPT` (callee→server→caller): `callId, from, to, int audioPort, int videoPort`
  - `CALL_REJECT` (callee→server→caller): `callId, from, to, String reason`
  - `CALL_END` (either→server→other): `callId, from, to`
  - `CALL_CONTROL` (either→server→other): `callId, from, to, {MUTE_MIC|UNMUTE_MIC|MUTE_SPK|UNMUTE_SPK|VIDEO_ON|VIDEO_OFF}`
- Server: map `ConcurrentHashMap<String, CallInfo> activeCalls` với `CallInfo { callId, caller, callee, status }`. Khi nhận CALL_INVITE → tìm callee trong `clients` → forward packet. Nếu callee offline → gửi CALL_REJECT ngay.
- **Call Relay (TCP)**: Server mở thêm 1 `ServerSocket` cho media (ví dụ port = port + 1). Khi accept → đọc header `[CALL_ID byte len + UTF callId + UTF side ("CALLER"|"CALLEE") + UTF streamType ("AUDIO"|"VIDEO")]` → ghép 2 đầu vào 1 cặp và forward bytes luồng giữa chúng (input một bên → output bên kia, 2 thread/cặp).

### Step 5 — Thêm Call UI & logic media vào ChatClient.java
#### 5a Signaling & Call Window
- Trong `buildBottomPanel()` thêm 2 nút (hoặc trong recipient panel):
  - Nút **"📞 Gọi"** — chỉ enabled khi recipient KHÔNG phải là nhóm (chỉ gọi 1-1). Click → gửi `CALL_INVITE` → mở `CallDialog` trạng thái "Đang gọi...".
  - Khi **nhận được `CALL_INVITE`**: `JOptionPane.showConfirmDialog` (hoặc custom dialog) hiện "[Tên] đang gọi bạn. Nhận? [Có / Không]" → YES→gửi `CALL_ACCEPT`, NO→gửi `CALL_REJECT`.
- Class `CallDialog extends JDialog` (modal-less):
  - Avatar / tên người đối thoại, thời gian gọi (bộ đếm Timer).
  - 4 nút: `🎤 Mic` (tắt/bật — toggle màu xám/xanh), `🔊 Loa` (tắt/bật), `📹 Video` (bật/tắt khung video), `📴 Cúp` (đỏ).
  - Panel video (`JLabel` với `ImageIcon`) hiển thị phía dưới (ẩn khi video off).
  - State: `boolean micMuted, spkMuted, videoEnabled, inCall`.

#### 5b Streaming Audio (javax.sound)
- Mở `ServerSocket` (2 port: audio + video) trên client nhận (hoặc cả 2 bên) → gửi port kèm trong `CALL_INVITE` / `CALL_ACCEPT`.
- Hoặc (đơn giản hơn): Cả 2 client connect tới **server media relay port** (Step 4) và gửi header định danh → `MicRecorder` thread đọc `TargetDataLine` → ghi `OutputStream` relay. `SpeakerPlayer` thread đọc `InputStream` relay → ghi `SourceDataLine`.
- Nút Mic mute → `line.stop()` (hoặc bỏ ghi bytes). Nút Loa mute → `line.stop()` (hoặc bỏ đọc bytes).

#### 5c Streaming Video (Robot screen-capture demo)
- `VideoSender` thread (khi `videoEnabled = true`): `Robot.createScreenCapture(whole screen)` → scale xuống (ví dụ 640x360) → encode JPEG (ImageIO) → kiểm tra kích thước <= VIDEO_MAX_FRAME → ghi OutputStream: `writeInt(length) + write(bytes)`. Lặp mỗi ~200ms (5 fps).
- `VideoReceiver` thread: Đọc `readInt()` length → `readFully(bytes)` → decode JPEG → set `ImageIcon` lên `JLabel` video panel.
- Nút Video toggle → gởi `CALL_CONTROL VIDEO_ON/OFF` + start/stop sender thread.

### Step 6 — Log & UI Server (ChatServer)
- Log các event: tạo nhóm, tham gia nhóm, gửi tin nhóm, bắt đầu/kết thúc cuộc gọi, bật/tắt mic/video.
- Nếu có thời gian: Thêm panel hoặc bảng phụ hiển thị danh sách nhóm đang tồn tại (tên, số thành viên).

### Step 7 — Build & kiểm thử
- Biên dịch 3 file: `javac -d out/production/ChatApp src/Protocol.java src/ChatServer.java src/ChatClient.java`
- Kiểm tra `GetDiagnostics` / `javac` không có lỗi.
- Test thủ công:
  - Tạo 3 client: A, B, C.
  - A tạo nhóm "Nhóm 1" thêm B + C → B & C thấy nhóm xuất hiện → chat nhóm → gửi file nhóm.
  - A gọi B (audio + video) → B hiện thông báo nhận → accept → CallDialog hiện đếm thời gian → test mute mic/lọa, bật/tắt video → cúp máy.

---

## Dependencies and Considerations
- **Java Sound API** (`javax.sound.sampled`): Được bao gồm trong JDK tiêu chuẩn. Không cần JAR ngoài.
- **Video Webcam thật**: **KHÔNG** làm (cần JAR `webcam-capture-0.3.12.jar` + bridj, không thể add tự do). Thay bằng `Robot.createScreenCapture` để demo pipeline video. Code sẽ tách riêng `VideoSource` interface → sau này add webcam chỉ việc viết 1 implementation mới.
- **Thread safety**: Dùng `volatile boolean` cho cờ `micMuted`, `inCall`, `videoEnabled`. Dùng `synchronized` hoặc `ConcurrentHashMap` cho các collection dùng chung.
- **Hiệu suất**: Video 5 fps, 640x360 JPEG ≈ 50-150KB/frame ≈ 0.5-1MB/s, chạy OK trên LAN.
- **Tương thích ngược**: Các packet cũ `CHAT, FILE, USERS, INFO` không đổi → client cũ (nếu có) vẫn kết nối được (chỉ không thấy tính năng mới).

---

## Validation
- `javac` toàn bộ 3 file → 0 lỗi.
- `GetDiagnostics` (IDE) → 0 warning nghiêm trọng.
- Test thủ công checklist (phải vượt qua hết):
  1. ✅ Login 3 client (A, B, C) bình thường, chat riêng / chat tổng được.
  2. ✅ A nhấn "Tạo nhóm" → nhập tên, chọn B + C → OK → B & C thấy 👥 Nhóm trong hộp "Gửi đến".
  3. ✅ A chọn nhóm → gửi tin → B & C thấy tin với tag `[Nhóm]`.
  4. ✅ Gửi file qua nhóm → cả 2 member nhận.
  5. ✅ A chọn B → bấm "Gọi" → B hiện popup "Đang gọi" → Accept → CallDialog hiện 2 đầu.
  6. ✅ Mic: Nói vào mic A → B nghe được. Bấm tắt Mic A → B không nghe.
  7. ✅ Loa: Bấm tắt Loa B → B không nghe A nói → Bật lại nghe được.
  8. ✅ Video: Bật Video A → Panel video B nhận hình chụp màn hình A (cập nhật mỗi ~0.2s).
  9. ✅ Cúp máy → CallDialog đóng, 2 bên nhận thông báo "Kết thúc cuộc gọi".
  10. ✅ Dừng server, đóng toàn bộ client → không có thread treo.

---

## Risks
| Risk | Xử lý / Fallback |
|---|---|
| `javax.sound` không lấy được mic trên 1 số laptop (driver/permission) | Báo lỗi rõ ràng trong log + hộp thoại "Không truy cập được thiết bị ghi âm. Cuộc gọi không có audio". Call vẫn chạy (chỉ video + signaling). |
| Chụp màn hình (Robot) quá chậm / lag | Giảm fps xuống 2-3/s, giảm độ phân giải JPEG thấp hơn (0.5 quality). |
| Media Relay ServerSocket port bị chiếm (port+1) | Thử port+2, port+3… tối đa 5 lần; log báo và hủy cuộc gọi nếu hết port khả dụng. |
| Nhiều thread gây deadlock/race khi toggle mic-video-cúp | Luôn kiểm tra `inCall == true` trước khi ghi socket. `close()` call socket từ finally block. |
| Sinh viên chưa học UDP nên code TCP streaming khó debug | Đưa nhiều log INFO `[Audio] đã nhận X byte`, `[Video] frame #N` vào log client và server để theo dõi luồng. |
