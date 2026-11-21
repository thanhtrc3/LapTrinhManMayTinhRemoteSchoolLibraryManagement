# BẢN ĐẶC TẢ KỸ THUẬT VÀ KIẾN TRÚC HỆ THỐNG TOÀN DIỆN
## Đề tài: Hệ thống Quản lý Mượn/Trả Sách Thư viện Trường học (Remote School Library Management)
**Học phần:** Lập trình Mạng Máy tính (Computer Network Programming)

---

## 1. TỔNG QUAN HỆ THỐNG & MỤC TIÊU
- **Tên hệ thống:** Remote School Library Management System.
- **Mục tiêu:** Xây dựng hệ thống quản lý mượn/trả sách, quản lý tài khoản, tựa sách và từng bản sao vật lý từ xa theo mô hình Khách/Chủ (Client/Server) hoạt động trên nền tảng mạng máy tính (LAN / Wi-Fi / Localhost / Internet qua TCP Tunnel).
- **Phạm vi công nghệ mạng chuẩn giáo trình môn học:**
  - **TCP Socket đa luồng (Bài 5 & 6):** Truyền nhận dữ liệu tin cậy, Persistent Connection, Java Object Serialization, Thread Pool (`ExecutorService`), Khóa hạt mịn (`ReentrantLock`).
  - **Truyền file qua Socket (Bài 5 & 6):** Tải sách điện tử (E-Book PDF/Text) trực tiếp qua luồng byte Socket.
  - **UDP Socket Broadcast (Bài 4 & 7):** Tự động phát hiện và kết nối Server trong mạng LAN (Auto-Discovery) không cần gõ IP.
  - **Multicast Socket (Bài 8):** Kênh loa phát thanh thư viện One-to-Many (`224.0.0.100`) phát thông báo tức thì tới toàn bộ máy trạm.
  - **Quản lý địa chỉ mạng (Bài 4):** Lớp `InetAddress`, phân quyền tải tài liệu nội bộ theo vị trí mạng (`isSiteLocalAddress`).

---

## 2. KIẾN TRÚC MẠNG & CÔNG NGHỆ TRUYỀN THÔNG

### 2.1. Động cơ TCP Server & Tối ưu hóa Concurrency
- **Server:**
  - Mở cổng `ServerSocket(8888)` tiếp nhận kết nối.
  - Dùng **Thread Pool (`ExecutorService`)** quản lý các tiểu trình `ClientHandler`, tái sử dụng luồng, tránh sập Server khi hàng trăm Client kết nối đồng thời.
  - **Khóa hạt mịn (`ReentrantLock` theo từng BookID):** Thay vì khóa toàn bộ Server, mỗi tựa sách có một Lock riêng. Người mượn sách A và người mượn sách B diễn ra hoàn toàn song song. Chỉ khi 2 người cùng mượn 1 cuốn sách cuối cùng thì mới cạnh tranh theo hàng đợi FIFO.
  - Quản lý danh sách kết nối: `CopyOnWriteArrayList<ClientHandler> activeClients`.
  - Giám sát thông số mạng: Đo độ trễ (Ping latency), tốc độ truyền file (MB/s), hiển thị IP/port client online trên Console Server.
- **Client (Ứng dụng Swing):**
  - Kết nối `Socket` duy trì (Persistent Connection).
  - Tách 2 luồng: Luồng giao diện (UI Event Dispatch Thread) và Luồng ngầm (Background Listener Thread) liên tục nhận dữ liệu và sự kiện Realtime (`SwingUtilities.invokeLater()`).

### 2.2. Giao thức UDP Auto-Discovery (Dò tìm Server tự động - Bài 7)
- Phía Server: Mở thêm 1 tiểu trình chạy `DatagramSocket(8889)` lắng nghe tín hiệu `DISCOVER_SERVER_REQUEST`. Khi nhận được, gửi ngược lại gói tin chứa thông tin IP và Port TCP của Server.
- Phía Client: Khi mở app, gửi gói tin UDP Broadcast tới `255.255.255.255:8889`. Nhận phản hồi và tự động điền IP vào ô kết nối mà người dùng không cần gõ tay.

### 2.3. Kênh Loa phát thanh Multicast (Bài 8)
- Sử dụng địa chỉ IP Multicast lớp D: `224.0.0.100`, Port `9999`.
- Khi Thủ thư gửi thông báo chung (thư viện sắp đóng cửa, có sách mới), Server gửi 1 gói tin qua `MulticastSocket`.
- Tất cả Client đang mở đều `joinGroup()` kênh này và hiển thị popup thông báo tức thì.

### 2.4. Phân quyền vị trí mạng theo IP (Bài 4)
- Sử dụng `InetAddress` để kiểm tra IP kết nối của Client:
  - Nếu kết nối từ trong mạng trường học (`isSiteLocalAddress() == true`): Cho phép tải toàn văn E-Book.
  - Nếu kết nối từ xa ngoài Internet: Chỉ cho phép đọc bản xem trước (Preview) để bảo vệ bản quyền.

### 2.5. Hỗ trợ kết nối từ xa qua Internet (TCP Tunnel)
- Tương thích 100% với Ngrok / Playit.gg. Khi cần demo qua Internet, Server chạy `ngrok tcp 8888`, Client nhập host/port tunnel là mượn/tải sách từ xa bình thường.

---

## 3. CÁC THỰC THỂ DỮ LIỆU CỐT LÕI (CORE ENTITIES)
Tất cả các thực thể đều cài đặt giao diện `java.io.Serializable`:

1. **`User` (Tài khoản người dùng):**
   - `username`, `password`, `fullName`, `email`, `role` (`LIBRARIAN` hoặc `STUDENT`), `maxQuota` (3 cuốn), `violationCount` (số lần vi phạm quá hạn), `isBlocked` (khóa mượn khi vi phạm quá 3 lần).
2. **`Book` (Tựa sách):**
   - `id`, `isbn`, `title`, `author`, `category`, `publishYear`, `totalCopies`, `availableCopies`, `ebookFileName` (đường dẫn file E-Book PDF/Text), `searchCount` (số lượt tìm kiếm để phục vụ thuật toán đề xuất).
3. **`BookCopy` (Bản sao sách vật lý):**
   - `copyId` (ví dụ: `BK01-C01`), `bookId`, `status` (`AVAILABLE`, `BORROWED`, `RESERVED`, `MAINTENANCE`), `qrCodeData`.
4. **`BorrowRecord` (Phiếu mượn / trả):**
   - `id`, `copyId`, `bookId`, `bookTitle`, `studentId`, `borrowDate`, `dueDate`, `returnDate`, `status` (`BORROWING`, `RETURNED`, `OVERDUE`), `renewCount` (tối đa 2 lần).
5. **`ReservationRecord` (Phiếu đặt trước sách):**
   - `id`, `bookId`, `bookTitle`, `studentId`, `reservationDate`, `status` (`PENDING`, `FULFILLED`, `CANCELLED`), `assignedCopyId`, `expiryDate` (thời hạn 24-48h đến lấy sách).
6. **`AuditLog` (Nhật ký truy vết hệ thống):**
   - `id`, `timestamp`, `action` (`LOGIN`, `BORROW`, `RETURN`, `RENEW`, `RESERVE`, `OVERDUE_STRIKE`, `DOWNLOAD_EBOOK`), `actor`, `details`.

---

## 4. CHI TIẾT 18 NGHIỆP VỤ HỆ THỐNG

### Nhóm I: Nghiệp vụ Thư viện & Trải nghiệm Người dùng
1. **Quản lý Bản sao sách vật lý (`BookCopy`):** Quản lý chi tiết từng cuốn vật lý cụ thể, trạng thái và mã QR.
2. **Gia hạn sách (Renew):** Cho phép sinh viên gia hạn thêm 7 ngày (tối đa 2 lần) nếu chưa quá hạn và không có người chờ đặt trước.
3. **Đặt trước sách (Reserve / Hold):** Khi sách hết kho (`availableCopies == 0`), xếp hàng FIFO. Khi có người trả sách, hệ thống tự động giữ chỗ ưu tiên.
4. **Xử lý tranh chấp đồng thời (FIFO Concurrency):** Hai người mượn cuốn cuối cùng, người trước được duyệt; người sau nhận thông báo hết sách kèm gợi ý "Đặt trước".
5. **Cập nhật số lượng Realtime:** Broadcast qua Socket, giao diện mọi máy tự động cập nhật ngay lập tức mà không cần F5.
6. **Tự động quét lúc 00:00 hàng ngày (Midnight Scheduled Job):** Server tự động quét nửa đêm tính toán các phiếu mượn quá hạn trong ngày.
7. **Nhật ký hệ thống (Audit Log) & Khóa mượn vi phạm 3 lần (3-Strike Rule):** Trả trễ hạn quá 3 lần -> Tự động khóa mượn sách. Thủ thư có quyền mở khóa.
8. **Tìm kiếm mờ thông minh (Fuzzy Search):** Thuật toán so khớp chuỗi chấp nhận gõ sai chính tả nhẹ hoặc gõ tiếng Việt không dấu.
9. **Quét mã QR Code (Quick Scan):** Quét mã QR của bản sao để tra cứu nhanh hoặc trả sách nhanh (hỗ trợ máy quét barcode hoặc file ảnh).
10. **Đề xuất sách theo lịch sử tìm kiếm (Smart Recommendation):** Gợi ý sách cùng thể loại/chủ đề dựa trên các từ khóa sinh viên hay tìm kiếm.

### Nhóm II: Nghiệp vụ Mạng Nâng cao Chuẩn Môn học
11. **Thời hạn giữ chỗ & Cảnh báo trước 6 tiếng:** Thông báo hạn chót nhận sách đặt trước, nhắc nhở trước 6 tiếng trước khi hủy giữ chỗ.
12. **Tối ưu Server:** Thread Pool (`ExecutorService`) + Khóa hạt mịn (`ReentrantLock` theo từng sách).
13. **Tải sách mềm / E-Book qua Socket Stream:** Truyền tải file PDF bài giảng/sách điện tử qua Socket để đọc tại nhà.
14. **Hỗ trợ kết nối từ xa qua Internet:** Tương thích TCP Tunnel (Ngrok) mượn/tải sách từ xa.
15. **Tự động dò tìm Server bằng UDP Broadcast (Bài 4 & 7):** Client tự tìm thấy Server trong mạng LAN không cần gõ IP.
16. **Kênh "Loa phát thanh" Multicast (Bài 8):** Phát thông báo toàn trường tới mọi máy Client qua `224.0.0.100`.
17. **Phân quyền tài liệu theo vị trí mạng (`InetAddress` - Bài 4):** Trong trường tải bản full; ngoài trường xem bản preview.
18. **Giám sát lưu lượng & Trạng thái Socket (Bài 5):** Thống kê tốc độ truyền file (MB/s), ping latency, IP/port client online.

---

## 5. LƯU TRỮ DỮ LIỆU & BẢNG MÃ
- **Lưu trữ nhị phân thuần Java:** Đọc/ghi các tệp `data/*.dat` bằng `ObjectInputStream` / `ObjectOutputStream`. Tự động tạo thư mục `data/` và nạp sẵn tài khoản mẫu, sách mẫu và các file E-Book mẫu khi khởi động lần đầu.
- **Bảng mã tiếng Việt:** Toàn bộ source code UTF-8, các script `.bat` đều cấu hình `chcp 65001`, `-encoding UTF-8` và `-Dfile.encoding=UTF-8`.
