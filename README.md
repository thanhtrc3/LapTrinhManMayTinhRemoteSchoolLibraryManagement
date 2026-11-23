# Hệ thống Quản lý Mượn/Trả Sách Thư viện Trường học

Ứng dụng Java Client/Server qua TCP Socket kết hợp UDP Discovery và Multicast, xây dựng theo mô hình kiến trúc phân tầng 3 người trong 3 tuần.

- **Đặc tả đầy đủ (18 tính năng):** [SPECIFICATION.md](SPECIFICATION.md)
- **Kế hoạch chia việc & hợp đồng dùng chung:** [docs/ke-hoach-chia-viec-3-tuan.md](docs/ke-hoach-chia-viec-3-tuan.md)

---

## 1. Yêu cầu môi trường & Lệnh chạy

### Yêu cầu
- **Java:** JDK 17 trở lên.
- **Maven:** Sử dụng trực tiếp Maven Wrapper có sẵn (`mvnw` trên macOS/Linux hoặc `mvnw.cmd` trên Windows), không cần cài đặt Maven thủ công.

### 3 Lệnh chạy chính (Windows / macOS / Linux)

| Thao tác | Lệnh Windows | Lệnh macOS / Linux |
|---|---|---|
| **Biên dịch dự án** | `build.bat` hoặc `mvnw.cmd compile` | `./mvnw compile` |
| **Chạy kiểm thử JUnit** | `test.bat` hoặc `mvnw.cmd test` | `./mvnw test` |
| **Khởi chạy Server** | `run-server.bat` | `./mvnw package && java -cp target/classes com.library.server.ServerMain` |
| **Khởi chạy Client** | `run-client.bat` | `java -cp target/classes com.library.client.ClientMain` |

---

## 2. Dữ liệu mẫu (Seed Data)

Khi khởi động lần đầu, hệ thống tự động sinh dữ liệu nhị phân thuần Java (`.dat`) trong thư mục `data/` và các tài liệu PDF trong `data/ebooks/`:

### Tài khoản đăng nhập mẫu:
- **Thủ thư (Librarian):**
  - Tài khoản: `admin`
  - Mật khẩu: `admin123`
- **Sinh viên (Student):**
  - Tài khoản: `sv001` / Mật khẩu: `123` (Nguyễn Văn An)
  - Tài khoản: `sv002` / Mật khẩu: `123` (Trần Thị Bình)
  - Tài khoản: `sv003` / Mật khẩu: `123` (Lê Minh Châu)

### Dữ liệu sách & Bản sao mẫu:
- 5 tựa sách tiếng Việt có dấu:
  1. *Lập trình mạng máy tính* (BK01)
  2. *Cấu trúc dữ liệu và Giải thuật* (BK02)
  3. *Lập trình Java căn bản* (BK03)
  4. *Cơ sở dữ liệu nâng cao* (BK04)
  5. *Toán rời rạc ứng dụng* (BK05)
- 15 bản sao vật lý (`BK01-C01` đến `BK05-C03`) kèm mã vạch và mã QR riêng biệt.

---

## 3. Cấu hình IP, Port và Dò tìm tự động

- **Mặc định:**
  - Cổng TCP Socket: `8888`
  - Cổng UDP Discovery: `8889`
  - Địa chỉ Multicast Loa thông báo: `224.0.0.100:9999`
- **Cấu hình Client:**
  - Client có thể mở hộp thoại **Cài đặt kết nối** (`SettingsDialog`) trên màn hình đăng nhập hoặc chỉnh sửa trực tiếp file `config.properties`:
    ```properties
    server.ip=127.0.0.1
    server.port=8888
    ```
  - Tính năng **Tự tìm Server trong LAN (UDP Broadcast)**: Bấm nút trên Client để tự động gửi gói tin UDP dò tìm IP Server trong mạng nội bộ mà không cần gõ thủ công.

---

## 4. Hướng dẫn Test thực tế trong mạng LAN & Tunnel

### 4.1. Chạy trên 2 máy cùng mạng Wi-Fi (LAN)
1. **Trên máy Server:**
   - Mở Terminal/CMD, gõ `ipconfig` (Windows) hoặc `ifconfig` (macOS/Linux) để xem địa chỉ IPv4 nội bộ (ví dụ: `192.168.1.15`).
   - Chạy `run-server.bat`.
2. **Trên máy Client:**
   - Khởi chạy Client, nhập IP Server: `192.168.1.15`, Port: `8888` (hoặc bấm **Tự tìm Server**).
   - Đăng nhập và thực hiện mượn/trả sách.

### 4.2. Chạy qua Internet bằng TCP Tunnel (Ngrok)
Khi cần kết nối từ xa ngoài mạng trường học:
1. Cài đặt [Ngrok](https://ngrok.com/) và mở đường hầm TCP:
   ```bash
   ngrok tcp 8888
   ```
2. Ngrok sẽ cấp đường dẫn dạng: `tcp://0.tcp.ap.ngrok.io:12345`.
3. Client nhập:
   - Host: `0.tcp.ap.ngrok.io`
   - Port: `12345`
4. Server phân quyền `isSiteLocalAddress()`:
   - Client qua Tunnel sẽ nhận bản xem trước E-Book (Preview).
   - Client trong mạng nội bộ trường học sẽ được tải toàn văn bản đầy đủ (Full E-Book).
