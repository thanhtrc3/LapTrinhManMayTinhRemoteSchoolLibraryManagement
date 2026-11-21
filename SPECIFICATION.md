# BẢN ĐẶC TẢ KỸ THUẬT VÀ KIẾN TRÚC HỆ THỐNG TOÀN DIỆN
## Đề tài: Hệ thống Quản lý Mượn/Trả Sách Thư viện Trường học (Remote School Library Management)
**Học phần:** Lập trình Mạng Máy tính (Computer Network Programming)

---

## 1. TỔNG QUAN HỆ THỐNG & MỤC TIÊU
- **Tên hệ thống:** Remote School Library Management System.
- **Mục tiêu:** Xây dựng hệ thống quản lý mượn/trả sách, quản lý tài khoản, tựa sách và từng bản sao vật lý từ xa theo mô hình Khách/Chủ (Client/Server) hoạt động đa nền tảng mạng máy tính (Localhost / LAN Wi-Fi / Internet qua TCP Tunnel).
- **Hệ thống 18 Tính năng Nâng cao:**
  1. Quản lý từng bản sao sách vật lý (`BookCopy`).
  2. Gia hạn sách (+7 ngày, tối đa 2 lần).
  3. Đặt trước sách khi hết kho (Hàng đợi FIFO).
  4. Xử lý tranh chấp mượn đồng thời (Khóa hạt mịn `ReentrantLock` theo từng sách).
  5. Cập nhật số lượng sách thời gian thực (Real-time Broadcast).
  6. Tác vụ ngầm quét lúc 00:00 hàng ngày (Midnight Scheduled Overdue Check).
  7. Nhật ký hệ thống (Audit Log) & Khóa quyền mượn khi vi phạm quá 3 lần (3-Strike Rule).
  8. Tìm kiếm mờ thông minh (Fuzzy Search - chấp nhận sai chính tả / không dấu).
  9. Quét mã QR bản sao (Quick Scan hỗ trợ máy quét USB và file ảnh).
  10. Đề xuất sách thông minh theo lịch sử tìm kiếm (Smart Recommendation).
  11. Hạn chót nhận sách đặt trước & Cảnh báo trước 6 tiếng (Hold Expiry Alert).
  12. Tối ưu hóa Server bằng **Thread Pool (`ExecutorService`)**.
  13. Tải sách điện tử / File mềm E-Book qua Socket stream (Bài toán FTP chuẩn).
  14. Hỗ trợ kết nối từ xa qua Internet bằng **TCP Tunnel (Ngrok)**.
  15. Tự động dò tìm Server trong mạng LAN bằng **UDP Broadcast (`DatagramSocket` - Bài 4 & 7)**.
  16. Kênh loa phát thanh thông báo Thư viện bằng **Multicast Socket (`MulticastSocket` - Bài 8)**.
  17. Phân quyền tải tài liệu nội bộ theo vị trí mạng (**`InetAddress.isSiteLocalAddress()` - Bài 4**).
  18. Giám sát băng thông, độ trễ và trạng thái Socket (**Socket Traffic & Latency Monitor - Bài 5**).

---

## 2. KIẾN TRÚC MẠNG ĐA GIAO THỨC (MULTI-PROTOCOL NETWORK ARCHITECTURE)

```
       +-------------------------------------------------------------------------+
       |                           SERVER THƯ VIỆN                               |
       |  +---------------------+   +---------------------+   +---------------+  |
       |  | TCP Server Socket   |   | UDP Discovery Svc   |   | Multicast Svc |  |
       |  | (Port 8888)         |   | (Port 8889)         |   | (224.0.0.100) |  |
       |  | - Thread Pool       |   | - Nhận broadcast    |   | - Phát tin    |  |
       |  | - ReentrantLocks    |   | - Phản hồi IP Server|   |   chung toàn  |  |
       |  | - Persistent Streams|   |                     |   |   trường      |  |
       |  +---------------------+   +---------------------+   +---------------+  |
       +-------------------------------------------------------------------------+
                    ^                            ^                     |
                    | (TCP Requests/Realtime)    | (UDP Broadcast)     | (Multicast)
                    v                            v                     v
       +-------------------------------------------------------------------------+
       |                           CLIENT (SINH VIÊN / THỦ THƯ)                  |
       |  - UI Swing (Event Dispatch Thread)                                     |
       |  - TCP Background Listener Thread (Nhận phản hồi & Realtime Broadcast)  |
       |  - UDP Auto-Discovery Client (Tự tìm Server không cần gõ IP)            |
       |  - Multicast Listener Thread (Nhận tin loa phát thanh thư viện)         |
       +-------------------------------------------------------------------------+
```

### 2.1. Tầng TCP Socket (Kênh giao dịch & Realtime chính - Bài 5 & 6)
- **Port:** `8888` (Mặc định).
- **Thread Pool:** Dùng `ExecutorService` (`Executors.newCachedThreadPool()` hoặc giới hạn số worker) để quản lý luồng phục vụ Client, bảo đảm Server không bị quá tải RAM (OutOfMemory).
- **Persistent Connection:** Socket mở liên tục trong suốt phiên làm việc.
- **Quy tắc Stream:**
  1. Luôn khởi tạo `ObjectOutputStream` và gọi `flush()` **trước** khi tạo `ObjectInputStream` để tránh Stream Header Deadlock.
  2. Luôn gọi `oos.reset()` trước mỗi lần truyền dữ liệu cập nhật để xóa bộ đệm ngầm của Java.
- **Truyền File E-Book:** Truyền luồng byte qua Socket có kiểm tra vị trí mạng `InetAddress.isSiteLocalAddress()`:
  - Nếu kết nối từ Wi-Fi trường (Localhost/Private IP): Gửi toàn văn file PDF.
  - Nếu kết nối từ Internet ngoài: Gửi bản xem trước (Preview 5 trang).

### 2.2. Tầng UDP Socket (Tự động dò tìm Server trong mạng LAN - Bài 4 & 7)
- **Port:** `8889`.
- Khi Client mở lên hoặc khi bấm nút **"🔍 Tự tìm Server"**:
  1. Client phát một gói tin `DatagramPacket` với nội dung `"DISCOVER_LIBRARY_SERVER"` tới địa chỉ Broadcast `255.255.255.255:8889`.
  2. `UdpDiscoveryServer` trên máy chủ nhận gói tin, lập tức gửi lại gói tin chứa IP của mình.
  3. Client nhận được IP, tự động điền vào ô kết nối và kích hoạt TCP Socket mà người dùng không cần gõ tay.

### 2.3. Tầng Multicast Socket (Kênh phát thanh thông báo toàn trường - Bài 8)
- **Địa chỉ Nhóm Multicast:** `224.0.0.100`, Port `9999`.
- Khi Thủ thư gửi thông báo (ví dụ: *"Thư viện đóng cửa sau 15 phút"* hoặc *"Nhập thêm 20 sách mới"*):
  1. Server đóng gói `MulticastMessage` và gửi qua `MulticastSocket`.
  2. Tất cả Client của sinh viên đang mở đều đã `joinGroup("224.0.0.100")`, nhận tin tức thì và hiển thị popup thông báo.

---

## 3. CÁC THỰC THỂ DỮ LIỆU CỐT LÕI (CORE DOMAIN MODELS)
Tất cả các thực thể cài đặt `java.io.Serializable` kèm `serialVersionUID`:

1. **`User`:** `username`, `password`, `fullName`, `role` (`STUDENT`, `LIBRARIAN`), `email`, `maxBorrowQuota` (3 cuốn), `violationCount` (số lần vi phạm quá hạn), `isBlocked` (khóa mượn khi vi phạm >= 3 lần).
2. **`Book`:** `id`, `isbn`, `title`, `author`, `category`, `publishYear`, `totalCopies`, `availableCopies`, `hasEbook` (boolean), `ebookFileName`.
3. **`BookCopy`:** `copyId` (ví dụ `BK01-C01`), `bookId`, `barcode`, `qrData`, `status` (`AVAILABLE`, `BORROWED`, `RESERVED`, `MAINTENANCE`).
4. **`BorrowRecord`:** `id`, `copyId`, `bookId`, `bookTitle`, `studentId`, `borrowDate`, `dueDate`, `returnDate`, `status` (`BORROWING`, `RETURNED`, `OVERDUE`), `renewCount` (tối đa 2 lần).
5. **`ReservationRecord`:** `id`, `bookId`, `bookTitle`, `studentId`, `reservationDate`, `expiryDate` (hạn chót nhận sách), `status` (`PENDING`, `FULFILLED`, `CANCELLED`, `EXPIRED`), `assignedCopyId`.
6. **`AuditLog`:** `id`, `timestamp`, `action` (`BORROW`, `RETURN`, `RENEW`, `RESERVE`, `OVERDUE_STRIKE`, `UNBLOCK`, `EBOOK_DOWNLOAD`), `actorUsername`, `details`.
7. **`SearchHistoryItem`:** `studentId`, `searchKeyword`, `searchCategory`, `timestamp`.

---

## 4. CHI TIẾT CÁC NGHIỆP VỤ HỆ THỐNG

### 4.1. Tìm kiếm thông minh (Fuzzy Search & Đề xuất)
- **Fuzzy Search Algorithm:**
  - Chuẩn hóa chuỗi (loại bỏ dấu tiếng Việt, chữ thường).
  - So khớp dựa trên khoảng cách Levenshtein (Levenshtein Distance <= 2) hoặc so khớp tiền tố/hậu tố.
  - Gõ `lap trinh mang`, `lập trinh mang`, `lap trinh mag` đều trả về đúng sách *Lập trình mạng*.
- **Đề xuất sách (Recommendation):**
  - Ghi nhận lịch sử tìm kiếm của sinh viên.
  - Gợi ý danh sách sách cùng thể loại hoặc cùng tác giả với các sách sinh viên hay tìm kiếm.

### 4.2. Mượn sách (Borrow) & Khóa hạt mịn theo từng sách
- **Khóa hạt mịn (`ReentrantLock` per `BookID`):**
  - Quản lý bằng `ConcurrentHashMap<Integer, ReentrantLock> bookLocks`.
  - Sinh viên mượn sách A và sinh viên mượn sách B chạy song song hoàn toàn.
  - Chỉ khi 2 sinh viên cùng mượn cuốn sách cuối cùng của tựa sách A, lock của tựa sách A mới được kích hoạt:
    - Người đến trước (FIFO): mượn thành công, gán `BookCopy` trạng thái `BORROWED`, `availableCopies--`.
    - Người đến sau: nhận thông báo sách đã hết và hệ thống **tự động hiển thị nút gợi ý "Đặt trước sách này"**.
- **Kiểm tra điều kiện:**
  - Chặn nếu đang có sách quá hạn chưa trả.
  - Chặn nếu sinh viên bị khóa mượn (`isBlocked == true` do >= 3 lần vi phạm).
  - Chặn nếu đã đạt trần 3 cuốn.

### 4.3. Trả sách (Return) & Tự động luân chuyển Đặt trước
- Khi sinh viên trả sách (hoặc quét mã QR để trả):
  - Đổi trạng thái phiếu mượn sang `RETURNED`.
  - Kiểm tra hàng đợi Đặt trước của tựa sách này (`status == PENDING`, sắp xếp theo thời gian đặt cũ nhất - FIFO):
    - **Nếu có người chờ:** Chuyển bản sao sang `RESERVED` giữ chỗ cho sinh viên đó. Thiết lập `expiryDate` (hạn chót đến nhận là 48h). Gửi sự kiện Realtime và Email/Alert riêng cho sinh viên đó.
    - **Nếu không có ai chờ:** Chuyển bản sao về `AVAILABLE`, `availableCopies++`.
  - Nếu trả sách bị quá hạn (`OVERDUE`) -> Tăng `violationCount++`. Nếu `violationCount >= 3` -> Tự động chuyển `isBlocked = true`.

### 4.4. Gia hạn sách (Renew)
- Sinh viên bấm nút **"Gia hạn (+7 ngày)"**.
- Điều kiện: Chưa quá hạn, `renewCount < 2`, và tựa sách này **không có ai đang xếp hàng đặt trước** (ưu tiên người đang chờ mượn).

### 4.5. Đặt trước sách (Reserve) & Cảnh báo hạn nhận trước 6 tiếng
- Khi `availableCopies == 0`, sinh viên bấm **"Đặt trước"**.
- Khi sách được trả và chuyển sang giữ chỗ:
  - Hệ thống đếm ngược hạn chót nhận sách (48 tiếng).
  - Trước khi hết hạn 6 tiếng, hệ thống gửi thông báo cảnh báo: *"Còn 6 tiếng để đến nhận sách, sau đó quyền ưu tiên sẽ chuyển cho người khác"*.
  - Nếu quá 48 tiếng mà không đến nhận: Hệ thống tự động chuyển trạng thái phiếu đặt sang `EXPIRED`, chuyển bản sao cho người đặt tiếp theo hoặc trả về kho.

### 4.6. Tải E-Book & Phân quyền vị trí mạng (`InetAddress`)
- Client gửi yêu cầu `DOWNLOAD_EBOOK(bookId)`.
- Server kiểm tra `socket.getInetAddress().isSiteLocalAddress()` hoặc dải Private IP của trường:
  - Nếu ở trường: Đọc và truyền toàn bộ file PDF qua Socket stream.
  - Nếu ở ngoài: Đọc và truyền bản xem trước (Preview) 5 trang.
- Ghi nhận vào `AuditLog`.

### 4.7. Tác vụ ngầm 00:00 & Giám sát Socket
- **Midnight Scheduled Task:**
  - Chạy tự động lúc 00:00 hàng ngày trên Server.
  - Quét tất cả phiếu `BORROWING`: nếu `currentDate > dueDate` -> chuyển thành `OVERDUE`.
- **Socket Traffic Monitor:**
  - Giám sát số client online, IP/port từng client, tốc độ truyền tải byte và độ trễ ping.

---

## 5. LƯU TRỮ DỮ LIỆU THUẦN JAVA (.DAT)
- Thư mục `data/`:
  - `users.dat`, `books.dat`, `copies.dat`, `borrows.dat`, `reservations.dat`, `audit.dat`, `search_history.dat`.
- Thư mục `data/ebooks/`: Chứa các file PDF tài liệu mẫu.
- **Seed Data:** Tự động nạp sẵn tài khoản (`admin`/`admin123`, `sv001`/`123`, `sv002`/`123`) cùng 5 tựa sách, 15 bản sao và các file PDF mẫu nếu lần đầu khởi động.

---

## 6. GIAO DIỆN NGƯỜI DÙNG JAVA SWING
- **LoginFrame:** Đăng nhập, nút **⚙ Cài đặt kết nối**, nút **🔍 Tự dò tìm Server LAN**.
- **StudentDashboard:**
  - Tab 1: **Tra cứu & Mượn sách:** Thanh tìm kiếm Fuzzy, ô quét QR, bảng sách kèm số lượng, nút Mượn, nút Đặt trước, nút Tải E-Book.
  - Tab 2: **Sách đang mượn:** Bảng sách cá nhân, ngày hẹn trả, cảnh báo đỏ quá hạn, nút Trả sách, nút Gia hạn (+7 ngày).
  - Tab 3: **Sách đã đặt trước:** Danh sách giữ chỗ, hạn chót nhận sách, cảnh báo trước 6 tiếng, nút Hủy đặt trước.
  - Tab 4: **Gợi ý cho bạn:** Danh sách sách được đề xuất dựa trên lịch sử tìm kiếm.
  - **Popup Loa phát thanh:** Tự động bật lên khi nhận thông điệp Multicast từ thư viện.
- **LibrarianDashboard:**
  - Tab 1: **Quản lý Tựa sách & Bản sao:** Bảng sách, CRUD sách, danh sách bản sao, xuất ảnh mã QR.
  - Tab 2: **Quản lý Mượn/Trả & Đặt trước:** Danh sách toàn trường, lọc quá hạn, tìm kiếm theo MSSV.
  - Tab 3: **Nhật ký (Audit Log) & Xử phạt vi phạm:** Xem log, danh sách sinh viên bị khóa mượn, nút Mở khóa vi phạm.
  - Tab 4: **Giám sát mạng & Loa phát thanh:** Phát thông báo Multicast, xem danh sách Client online, giám sát độ trễ & lưu lượng.
