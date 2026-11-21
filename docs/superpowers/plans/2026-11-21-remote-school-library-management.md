# Remote School Library Management System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây dựng hoàn chỉnh Hệ thống Quản lý Mượn/Trả Sách Thư viện Trường học (Remote School Library Management) hoạt động qua mạng máy tính theo mô hình Khách/Chủ (Client/Server) đa luồng, hỗ trợ toàn bộ 18 nghiệp vụ và kỹ thuật mạng chuẩn giáo trình (TCP Socket đa luồng, Thread Pool, Khóa hạt mịn ReentrantLock, E-Book File Transfer qua Socket, UDP Auto-Discovery, Multicast Announcement Channel, Quản lý địa chỉ InetAddress, Realtime Broadcast, Swing GUI và lưu trữ nhị phân thuần Java).

**Architecture:** 
- Phía Server: `ServerSocket` đa luồng kết hợp `ExecutorService` (Thread Pool), khóa hạt mịn `ReentrantLock` theo BookID, UDP Discovery Responder (`DatagramSocket:8889`), Multicast Broadcaster (`MulticastSocket:9999`), xử lý hàng đợi đặt trước (FIFO reservation queue), tải file E-Book qua Socket, Scheduled Job lúc 00:00 kiểm tra quá hạn, ghi log giám sát mạng chi tiết.
- Phía Client: Giao diện đồ họa Java Swing với luồng ngầm (`Background Listener Thread`), UDP Auto-Discovery Client tự tìm Server trong LAN, Multicast Listener bắt loa phát thanh, hỗ trợ Fuzzy Search, quét mã QR, cấu hình IP/Port linh hoạt hoặc chạy qua TCP Tunnel.
- Giao thức & Lưu trữ: Java Object Serialization (`Serializable`, `oos.reset()`, tạo `ObjectOutputStream` trước `ObjectInputStream`) và lưu trữ tệp nhị phân `.dat`.

**Tech Stack:** Pure Java (JDK 17+/21+), Java Swing, Java Socket API (`java.net`), Java I/O (`java.io`), Maven structure & Windows Batch scripts.

**Spec:** [SPECIFICATION.md](file:///d:/Code/LapTrinhManMayTinhRemoteSchoolLibraryManagement/SPECIFICATION.md)

---

## Global Constraints
- Chỉ sử dụng thư viện chuẩn của Java JDK (không sử dụng framework ngoài như Spring/Web/Netty hay thư viện bên thứ ba).
- Toàn bộ source code và file text lưu định dạng `UTF-8`.
- Luôn gọi `oos.reset()` khi gửi lại dữ liệu qua kết nối duy trì `Persistent Connection`.
- Luôn khởi tạo `new ObjectOutputStream` + `flush()` trước khi gọi `new ObjectInputStream`.
- Các thao tác tài nguyên dùng `ReentrantLock` theo từng BookID để bảo đảm Thread-Safety và tối đa hóa hiệu năng song song.
- Scripts `.bat` trên Windows phải có `chcp 65001`, `-encoding UTF-8` và `-Dfile.encoding=UTF-8`.

---

### Task 1: Thiết lập cấu trúc dự án & Các mô hình dữ liệu dùng chung (Shared Models & Packets)

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/library/common/model/Role.java`
- Create: `src/main/java/com/library/common/model/User.java`
- Create: `src/main/java/com/library/common/model/Book.java`
- Create: `src/main/java/com/library/common/model/CopyStatus.java`
- Create: `src/main/java/com/library/common/model/BookCopy.java`
- Create: `src/main/java/com/library/common/model/BorrowStatus.java`
- Create: `src/main/java/com/library/common/model/BorrowRecord.java`
- Create: `src/main/java/com/library/common/model/ReservationStatus.java`
- Create: `src/main/java/com/library/common/model/ReservationRecord.java`
- Create: `src/main/java/com/library/common/model/AuditLog.java`
- Create: `src/main/java/com/library/common/network/CommandType.java`
- Create: `src/main/java/com/library/common/network/ResponseStatus.java`
- Create: `src/main/java/com/library/common/network/Request.java`
- Create: `src/main/java/com/library/common/network/Response.java`
- Create: `src/main/java/com/library/common/network/RealtimeEvent.java`
- Create: `src/main/java/com/library/common/network/NetworkConstants.java`
- Test: `src/test/java/com/library/common/SerializationTest.java`

**Steps:**
- [ ] Tạo `pom.xml` chuẩn Maven với Java 17+, plugin compiler UTF-8, JUnit 5 cho kiểm thử.
- [ ] Định nghĩa các enum: `Role`, `CopyStatus`, `BorrowStatus`, `ReservationStatus`.
- [ ] Cài đặt các class thực thể: `User` (email, violationCount, isBlocked), `Book` (ebookFileName, searchCount), `BookCopy` (qrCodeData), `BorrowRecord`, `ReservationRecord` (expiryDate), `AuditLog` thực thi `Serializable`.
- [ ] Cài đặt các class gói tin mạng: `CommandType` (bao gồm `BORROW_BOOK`, `RETURN_BOOK`, `RENEW_BOOK`, `RESERVE_BOOK`, `CANCEL_RESERVATION`, `DOWNLOAD_EBOOK`, `UNBLOCK_USER`,...), `ResponseStatus`, `Request`, `Response`, `RealtimeEvent`, `NetworkConstants` (port 8888, UDP port 8889, Multicast 224.0.0.100:9999).
- [ ] Viết test `SerializationTest.java` kiểm tra tuần tự hóa và phục hồi toàn vẹn các đối tượng.
- [ ] Chạy kiểm thử xác nhận Task 1 hoàn tất.

---

### Task 2: Tầng lưu trữ nhị phân thuần Java & Logic nghiệp vụ thông minh (Storage & Smart Service)

**Files:**
- Create: `src/main/java/com/library/server/storage/DataStorage.java`
- Create: `src/main/java/com/library/server/service/FuzzySearchHelper.java`
- Create: `src/main/java/com/library/server/service/LibraryService.java`
- Test: `src/test/java/com/library/server/LibraryServiceConcurrencyTest.java`

**Steps:**
- [ ] Cài đặt `DataStorage`:
  - Đọc/ghi các tệp `data/users.dat`, `data/books.dat`, `data/copies.dat`, `data/borrows.dat`, `data/reservations.dat`, `data/audit.dat`.
  - Tự động nạp Seed Data: 1 Thủ thư, 2 Sinh viên, 5 tựa sách kèm các bản sao vật lý và file tài liệu E-Book mẫu trong `data/ebooks/`.
- [ ] Cài đặt `FuzzySearchHelper`: Thuật toán tính khoảng cách Levenshtein và chuẩn hóa bỏ dấu tiếng Việt để tìm kiếm sách khi gõ sai hoặc gõ không dấu.
- [ ] Cài đặt `LibraryService`:
  - Quản lý khóa hạt mịn `ConcurrentHashMap<Integer, ReentrantLock> bookLocks`.
  - `authenticate(username, password)`.
  - `searchBooks(keyword)` tích hợp Fuzzy Search & ghi nhận lịch sử tìm kiếm để phục vụ `getRecommendations(username)`.
  - `borrowBook(studentId, bookId, daysToBorrow)`: Kiểm tra 3-strike rule, kiểm tra quá hạn, chọn bản sao khả dụng (hoặc bản sao đã giữ chỗ), khóa ReentrantLock theo bookId, xếp hàng FIFO mượn sách.
  - `returnBook(borrowRecordId)`: Đổi trạng thái, kiểm tra hàng đợi đặt trước PENDING (tự động chuyển bản sao thành RESERVED giữ chỗ và cấp hạn 48h).
  - `renewBook(borrowRecordId)`: Gia hạn +7 ngày nếu chưa quá hạn, renewCount < 2 và không có ai đặt trước.
  - `reserveBook(studentId, bookId)`: Khi hết sách, thêm vào hàng đợi đặt trước PENDING.
  - `cancelReservation(reservationId, studentId)`.
  - `checkOverdueAndApplyStrikes()`: Quét quá hạn và áp dụng quy tắc vi phạm 3 lần (3-Strike Rule).
  - `unblockUser(username)`.
  - CRUD tựa sách và bản sao vật lý.
  - `getAuditLogs()`.
- [ ] Viết test tương tranh đa luồng `LibraryServiceConcurrencyTest.java` kiểm tra mượn đồng thời, đặt trước và gia hạn an toàn luồng.
- [ ] Chạy kiểm thử xác nhận Task 2 hoàn tất.

---

### Task 3: Động cơ mạng Server: TCP Thread Pool, UDP Discovery, Multicast Loa & E-Book Transfer

**Files:**
- Create: `src/main/java/com/library/server/network/ClientHandler.java`
- Create: `src/main/java/com/library/server/network/UdpDiscoveryServer.java`
- Create: `src/main/java/com/library/server/network/MulticastAnnouncementServer.java`
- Create: `src/main/java/com/library/server/network/LibraryServer.java`
- Create: `src/main/java/com/library/server/ServerMain.java`
- Test: `src/test/java/com/library/server/ServerNetworkIntegrationTest.java`

**Steps:**
- [ ] Cài đặt `ClientHandler`:
  - Quản lý Socket kết nối với Client, tạo `ObjectOutputStream` trước `ObjectInputStream`.
  - Vòng lặp đọc Request và gửi Response (gọi `oos.reset()`).
  - Xử lý tải E-Book: Kiểm tra `isSiteLocalAddress()` theo quy định bản quyền, truyền byte stream file E-Book qua Socket.
  - Ghi nhận tốc độ truyền và độ trễ ping.
- [ ] Cài đặt `UdpDiscoveryServer`: Mở `DatagramSocket(8889)`, nhận gói tin UDP Broadcast từ Client và gửi phản hồi thông tin IP/Port TCP.
- [ ] Cài đặt `MulticastAnnouncementServer`: Gửi thông báo loa phát thanh toàn trường tới nhóm `224.0.0.100:9999` qua `MulticastSocket`.
- [ ] Cài đặt `LibraryServer`:
  - Dùng `Executors.newCachedThreadPool()` quản lý ClientHandler.
  - Khởi chạy song song TCP Server, UDP Discovery Server và Scheduled Job 00:00 (quét quá hạn mỗi đêm).
  - Phương thức `broadcastEvent(RealtimeEvent)` gửi sự kiện Realtime tới mọi client online.
  - In log console chi tiết: IP/Port kết nối, đăng nhập, mượn, trả, tải file, số client online.
- [ ] Cài đặt `ServerMain`: Khởi động toàn bộ dịch vụ Server.
- [ ] Viết `ServerNetworkIntegrationTest.java` kiểm thử kết nối TCP, UDP discovery, tải file và broadcast.
- [ ] Chạy kiểm thử xác nhận Task 3 hoàn thành.

---

### Task 4: Tầng điều khiển mạng phía Client & Bộ lắng nghe Realtime / UDP / Multicast

**Files:**
- Create: `src/main/java/com/library/client/config/ClientConfig.java`
- Create: `src/main/java/com/library/client/network/UdpDiscoveryClient.java`
- Create: `src/main/java/com/library/client/network/MulticastNotificationListener.java`
- Create: `src/main/java/com/library/client/network/RealtimeListener.java`
- Create: `src/main/java/com/library/client/network/LibraryClient.java`
- Test: `src/test/java/com/library/client/ClientConfigTest.java`

**Steps:**
- [ ] Cài đặt `ClientConfig`: Quản lý file `config.properties`, hàm fallback mặc định `127.0.0.1:8888`.
- [ ] Cài đặt `UdpDiscoveryClient`: Phát gói tin UDP Broadcast tới `255.255.255.255:8889` để tự động lấy IP/Port Server trong mạng LAN.
- [ ] Cài đặt `MulticastNotificationListener`: Mở `MulticastSocket`, tham gia nhóm `224.0.0.100:9999` để nhận loa phát thanh thông báo.
- [ ] Cài đặt `LibraryClient`:
  - Quản lý Socket TCP, luồng ngầm nhận Response và RealtimeEvent.
  - Phương thức tải file E-Book lưu về máy.
  - Tích hợp Ping test đo độ trễ mạng.
- [ ] Viết test `ClientConfigTest.java` kiểm tra fallback giá trị mặc định.
- [ ] Chạy kiểm thử xác nhận Task 4 hoàn tất.

---

### Task 5: Giao diện đồ họa Java Swing (GUI) Toàn diện

**Files:**
- Create: `src/main/java/com/library/client/ui/SettingsDialog.java`
- Create: `src/main/java/com/library/client/ui/LoginFrame.java`
- Create: `src/main/java/com/library/client/ui/StudentDashboard.java`
- Create: `src/main/java/com/library/client/ui/LibrarianDashboard.java`
- Create: `src/main/java/com/library/client/ui/BookDialog.java`
- Create: `src/main/java/com/library/client/ui/AnnouncementDialog.java`
- Create: `src/main/java/com/library/client/ClientMain.java`

**Steps:**
- [ ] Cài đặt `SettingsDialog`:
  - Nút "Tự động tìm Server trong LAN" (gọi UDP Discovery).
  - Ô nhập Host & Port có placeholder `127.0.0.1:8888`.
  - Nút "Kiểm tra kết nối" (Ping test) hiển thị độ trễ mili-giây.
- [ ] Cài đặt `LoginFrame`: Form đăng nhập, nút Cấu hình Server.
- [ ] Cài đặt `StudentDashboard`:
  - Tab 1: Tra cứu & Mượn sách: Hỗ trợ tìm kiếm mờ (Fuzzy), ô quét nhanh mã QR, nút Mượn, nút Đặt trước, nút "Tải E-Book đọc tại nhà", khu vực "Gợi ý sách cho bạn".
  - Tab 2: Sách đang mượn: Danh sách sách cá nhân, nút Trả sách, nút Gia hạn (+7 ngày), cảnh báo đỏ quá hạn.
  - Tab 3: Sách đã đặt trước: Trạng thái hàng đợi, thời hạn đến nhận sách (cảnh báo trước 6 tiếng), nút Hủy đặt trước.
  - Tự động nhận Realtime cập nhật bảng JTable và popup nhận loa phát thanh Multicast.
- [ ] Cài đặt `LibrarianDashboard`:
  - Tab 1: Quản lý tựa sách & bản sao vật lý (Thêm/Sửa/Xóa, xem mã QR).
  - Tab 2: Quản lý Mượn/Trả toàn trường (lọc quá hạn, tìm theo mã SV).
  - Tab 3: Quản lý Đặt trước sách.
  - Tab 4: Nhật ký hệ thống (Audit Log) & Mở khóa tài khoản bị phạt 3 lần.
  - Tab 5: Giám sát mạng & Phát loa Multicast: Nút phát thông báo toàn trường, bảng hiển thị IP/port client và tốc độ truyền file.
- [ ] Cài đặt `ClientMain`: Điểm vào khởi chạy ứng dụng Client Swing.

---

### Task 6: Windows Scripts UTF-8, Đóng gói & Kiểm thử mô phỏng End-to-End

**Files:**
- Create: `build.bat`
- Create: `run-server.bat`
- Create: `run-client.bat`
- Create: `README.md`
- Test: `src/test/java/com/library/EndToEndSimulationTest.java`

**Steps:**
- [ ] Tạo file `build.bat`: Cấu hình `chcp 65001`, biên dịch toàn bộ bằng `javac -encoding UTF-8` vào `bin/`.
- [ ] Tạo file `run-server.bat`: `chcp 65001`, `java -Dfile.encoding=UTF-8 -cp bin com.library.server.ServerMain`.
- [ ] Tạo file `run-client.bat`: `chcp 65001`, `java -Dfile.encoding=UTF-8 -cp bin com.library.client.ClientMain`.
- [ ] Viết `EndToEndSimulationTest.java`:
  - Server khởi động ngầm (hỗ trợ cả TCP, UDP và Multicast).
  - Client 1 và Client 2 kết nối đồng thời.
  - Kiểm tra dò tìm UDP, mượn sách đồng thời, đặt trước, gia hạn, tải file E-Book và nhận sự kiện Realtime.
- [ ] Viết `README.md` hướng dẫn chi tiết cách chạy bằng script .bat và cách chạy demo với Ngrok hoặc qua mạng LAN cho giảng viên.
- [ ] Commit và Push toàn bộ lên GitHub repository.
