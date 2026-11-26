# Mini Browser – HTTP GET · POST · HEAD

Ứng dụng Java Swing cho phép nhập URL và gửi 3 loại request: **GET**, **POST**, **HEAD**.

| Phương thức | Kết quả hiển thị |
|---|---|
| **GET / POST** | Trang HTML (render bằng Swing), mã nguồn HTML, **độ dài**, số thẻ `<p>`, `<div>`, `<span>`, `<img>` |
| **HEAD** | Thông tin tài nguyên: status, Content-Type, Content-Length, Last-Modified, ETag, Server, ... và toàn bộ header |

## 1. Yêu cầu

- JDK **11 trở lên** (dùng `java.net.http.HttpClient`)
- Không cần thư viện ngoài

## 2. Cấu trúc

```
.
├── README.md
└── src/com/library/BrowserApp.java
```

## 3. Biên dịch và chạy

Từ thư mục gốc của project:

```bash
# Windows / Linux / macOS
javac -encoding UTF-8 -d out src/com/library/BrowserApp.java
java -cp out com.library.BrowserApp
```

> Phải có `-encoding UTF-8` để tiếng Việt trong giao diện không bị lỗi font.

Nếu dùng IntelliJ / Eclipse / NetBeans: mở project, chạy class `com.library.BrowserApp` (có sẵn hàm `main`).

## 4. Cách sử dụng

1. Gõ URL vào ô địa chỉ (không có `http://` thì tự thêm `https://`).
2. Chọn phương thức ở ô bên phải URL: `GET`, `POST` hoặc `HEAD`.
3. Nếu chọn `POST`, ô **Body (POST)** hiện ra, nhập dữ liệu dạng `key=value&key2=value2`.
4. Bấm **Gửi request** (hoặc nhấn Enter trong ô URL).
5. Xem kết quả:
   - 8 thẻ thống kê: Status, Thời gian, Độ dài, Dung lượng, `<p>`, `<div>`, `<span>`, `<img>`
   - Tab **Trang**: nội dung HTML đã render (bấm link để mở tiếp bằng GET)
   - Tab **Mã nguồn HTML**: HTML gốc
   - Tab **Thông tin / Headers**: thông tin tài nguyên và toàn bộ header
6. Nút **←** quay lại trang trước.

## 5. Kịch bản test

### Test 1 – GET trang đơn giản

- URL: `https://example.com`, phương thức `GET`
- Kỳ vọng: Status `200`, trang "Example Domain" hiển thị, `<p>` ≈ 2, `<div>` ≈ 1, `<span>` = 0, `<img>` = 0 (số liệu có thể đổi nếu website thay đổi)

### Test 2 – GET trang local có số liệu biết trước (khuyến nghị)

Tạo file `test.html` trong một thư mục bất kỳ:

```html
<!DOCTYPE html>
<html><head><meta charset="UTF-8"><title>Trang test</title></head>
<body>
<div><h1>Xin chào</h1>
<p>Đoạn 1 với <span>span 1</span> và <span>span 2</span></p>
<p>Đoạn 2</p></div>
<div><p>Đoạn 3</p><span>span 3</span>
<img src="a.png"><img src="b.png"></div>
<!-- <p>không tính vì nằm trong comment</p> -->
</body></html>
```

Chạy server trong thư mục đó (cần Python 3):

```bash
python -m http.server 8000
```

Trong Mini Browser: `http://localhost:8000/test.html`, phương thức `GET`.

| Thẻ | Kết quả đúng |
|---|---|
| `<p>` | **3** |
| `<div>` | **2** |
| `<span>` | **3** |
| `<img>` | **2** |

Cột **Độ dài** là số ký tự, **Dung lượng** là số byte (bằng dung lượng file khi file toàn ký tự ASCII; khác nhau do tiếng Việt UTF-8).

### Test 3 – POST

- URL: `https://httpbin.org/post`, phương thức `POST`
- Body: `name=Hieu&message=Hello`
- Kỳ vọng: Status `200`, trang trả về JSON có phần `"form"` chứa `name` và `message` đúng như đã gửi (hiển thị dạng text trong tab Trang).

Cách khác: `https://postman-echo.com/post`.

### Test 4 – HEAD

- URL: `https://example.com`, phương thức `HEAD`
- Kỳ vọng: tự chuyển sang tab **Thông tin / Headers**, hiển thị Status, Content-Type, Content-Length (nếu có), Last-Modified, ETag, Server, ...; các thẻ `<p>`, `<div>`, `<span>`, `<img>` hiện `—` vì HEAD không có body.
- Thử thêm: `http://localhost:8000/test.html` với `HEAD` → thấy `Content-Length` và `Last-Modified` của file.

### Test 5 – Chuyển hướng (redirect)

- URL: `https://httpbin.org/redirect/1`, phương thức `GET`
- Kỳ vọng: ô URL đổi thành URL cuối cùng; tab Thông tin ghi "(đã chuyển hướng)".

### Test 6 – Các trường hợp lỗi

| Nhập | Kỳ vọng |
|---|---|
| (để trống URL) | Thanh trạng thái báo "URL đang trống" |
| `ftp://abc.com` | "Chỉ hỗ trợ http và https" |
| `http://abc def` | "URL không hợp lệ" |
| `https://khong-ton-tai-12345.com` | Báo lỗi kết nối, ứng dụng không bị treo |
| `https://httpbin.org/status/404` | Status `404` màu đỏ |
| `http://localhost:8000/test.html` với `POST` (server Python) | Status `501` (server Python không hỗ trợ POST) |

### Test 7 – Link và nút Back

1. GET `https://example.com`, bấm link "More information..." trong tab Trang.
2. Trình duyệt tải trang mới bằng GET; bấm **←** để quay lại `example.com`.

## 6. Cách tính các số liệu

- **Độ dài**: số ký tự của HTML sau khi giải mã (charset lấy từ `Content-Type`, hoặc thẻ `<meta charset>`, mặc định UTF-8).
- **Dung lượng**: số byte nhận về.
- **Đếm thẻ**: dùng regex không phân biệt hoa/thường, `<p` chỉ khớp thẻ `<p>` (không tính `<pre>`, `<param>`); bỏ qua nội dung trong comment `<!-- -->`, `<script>`, `<style>`.

## 7. Giới hạn

- Phần render dùng `JEditorPane` (chỉ hỗ trợ HTML 3.2 và một phần CSS 1), nên các trang hiện đại nhiều CSS/JavaScript sẽ hiển thị chưa đẹp. Nội dung gốc vẫn xem được ở tab **Mã nguồn HTML**.
- Script bị bỏ khi render; trang quá lớn chỉ render 400.000 ký tự đầu.
- Nội dung không phải văn bản (ảnh, PDF, ...) chỉ hiển thị thông tin, không hiển thị nội dung.
