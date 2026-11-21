# Remote School Library Management System Implementation Plan (Cập nhật Toàn diện 18 Tính năng)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây dựng hoàn chỉnh Hệ thống Quản lý Mượn/Trả Sách Thư viện Trường học (Remote School Library Management) tích hợp trọn vẹn 18 tính năng mạng và nghiệp vụ: TCP Socket đa luồng (Thread Pool), UDP Auto-Discovery, Multicast Announcement, E-Book stream transfer, Fuzzy Search, QR Code, Khóa hạt mịn theo sách (Fine-grained Lock), Hàng đợi Đặt trước (FIFO), Gia hạn, Tính quá hạn 00:00, Audit Log & 3-strike rule, Gợi ý sách và Swing GUI.

**Architecture:** 
- **Server:** `ServerSocket(8888)` chạy trên `ExecutorService` (Thread Pool), `UdpDiscoveryServer(8889)` hỗ trợ dò tìm LAN, `MulticastSocket(224.0.0.100:9999)` phát loa thông báo, khóa hạt mịn `ConcurrentHashMap<Integer, ReentrantLock>` bảo vệ từng cuốn sách chống Race Condition, quản lý Persistent Connection với `oos.reset()`, tác vụ quét định kỳ lúc 00:00 (Midnight Overdue Task).
- **Client:** Giao diện đồ họa Java Swing (Student & Librarian Dashboards), luồng ngầm lắng nghe Realtime TCP (`SwingUtilities.invokeLater()`), luồng ngầm Multicast nhận loa phát thanh, UDP broadcast tự động phát hiện IP máy chủ, cấu hình IP/Port linh hoạt hỗ trợ kết nối nội bộ hoặc từ xa qua TCP Tunnel (Ngrok).
- **Lưu trữ & Bảo mật:** File nhị phân `.dat` thuần Java trong thư mục `data/` (`users.dat`, `books.dat`, `copies.dat`, `borrows.dat`, `reservations.dat`, `audit.dat`, `search_history.dat`) và thư mục `data/ebooks/`.

**Tech Stack:** Pure Java (JDK 17+/21+), Java Swing, Java Socket API (`java.net`), Java I/O (`java.io`), Maven structure & Windows Batch scripts UTF-8.

**Spec:** [SPECIFICATION.md](file:///d:/Code/LapTrinhManMayTinhRemoteSchoolLibraryManagement/SPECIFICATION.md)

---

## Global Constraints
- Chỉ sử dụng thư viện chuẩn của Java JDK (không dùng framework web, database SQL ngoài hay thư viện bên thứ ba).
- Toàn bộ source code và file text lưu định dạng `UTF-8`.
- Luôn gọi `oos.reset()` khi gửi lại dữ liệu qua kết nối duy trì `Persistent Connection`.
- Luôn khởi tạo `new ObjectOutputStream` + `flush()` trước khi gọi `new ObjectInputStream`.
- Dùng `ReentrantLock` theo từng `bookId` để bảo đảm Thread-Safety mức hạt mịn (không block toàn server).
- Scripts `.bat` trên Windows phải có `chcp 65001`, `-encoding UTF-8` và `-Dfile.encoding=UTF-8`.

---

## Review Focus
1. **Fine-grained Concurrency & FIFO:** 2 người cùng mượn cuốn sách cuối cùng của cùng 1 đầu sách -> Người đến trước mượn thành công, người đến sau nhận thông báo hết và gợi ý Đặt trước. Người mượn sách khác không bị nghẽn.
2. **Reservation Auto-allocation:** Khi sách được trả, nếu có người trong hàng đợi Đặt trước (`PENDING`), bản sao tự động chuyển sang `RESERVED` cho người đặt đầu tiên, đếm ngược hạn nhận và cảnh báo trước 6 tiếng.
3. **Stream Protocol Safety:** Không bao giờ bị Deadlock khi khởi tạo stream; không nhận dữ liệu cũ nhờ `oos.reset()`.
4. **E-Book Stream & Intranet Access:** Tải file qua Socket kiểm tra đúng `isSiteLocalAddress()` để cấp bản Full hay Preview.
5. **UDP Discovery & Multicast Safety:** Gói tin UDP broadcast tìm đúng IP Server; gói Multicast không làm đơ giao diện Swing của Client.

---

### Task 1: Cấu trúc dự án Maven & Các mô hình dữ liệu dùng chung (Shared Models & Packets)

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
- Create: `src/main/java/com/library/common/model/AuditAction.java`
- Create: `src/main/java/com/library/common/model/AuditLog.java`
- Create: `src/main/java/com/library/common/model/SearchHistoryItem.java`
- Create: `src/main/java/com/library/common/network/CommandType.java`
- Create: `src/main/java/com/library/common/network/ResponseStatus.java`
- Create: `src/main/java/com/library/common/network/Request.java`
- Create: `src/main/java/com/library/common/network/Response.java`
- Create: `src/main/java/com/library/common/network/RealtimeEvent.java`
- Create: `src/main/java/com/library/common/network/MulticastMessage.java`
- Create: `src/main/java/com/library/common/util/FuzzySearchUtil.java`
- Test: `src/test/java/com/library/common/SerializationTest.java`
- Test: `src/test/java/com/library/common/FuzzySearchTest.java`

**Steps:**
- [ ] Tạo `pom.xml` cấu hình chuẩn Maven với Java 17/21, encoding UTF-8, JUnit 5.
- [ ] Định nghĩa các enum: `Role`, `CopyStatus`, `BorrowStatus`, `ReservationStatus`, `AuditAction`.
- [ ] Cài đặt các class thực thể: `User` (kèm `violationCount`, `isBlocked`), `Book`, `BookCopy`, `BorrowRecord`, `ReservationRecord`, `AuditLog`, `SearchHistoryItem` cài đặt `Serializable`.
- [ ] Cài đặt các class gói tin: `CommandType` (bao gồm `LOGIN`, `GET_BOOKS`, `BORROW`, `RETURN`, `RENEW`, `RESERVE`, `DOWNLOAD_EBOOK`, `GET_RECOMMENDATIONS`, `GET_AUDIT_LOGS`, `UNBLOCK_USER`...), `ResponseStatus`, `Request`, `Response`, `RealtimeEvent`, `MulticastMessage`.
- [ ] Cài đặt `FuzzySearchUtil`: Thuật toán Levenshtein Distance kết hợp loại bỏ dấu tiếng Việt để tìm kiếm gần đúng khi gõ sai chính tả.
- [ ] Viết test `SerializationTest.java` và `FuzzySearchTest.java`. Chạy kiểm thử xác nhận Task 1 hoàn tất.

---

### Task 2: Tầng lưu trữ nhị phân, Quản lý khóa hạt mịn & Logic nghiệp vụ (Storage & Thread-Safe Service)

**Files:**
- Create: `src/main/java/com/library/server/storage/DataStorage.java`
- Create: `src/main/java/com/library/server/service/LibraryService.java`
- Test: `src/test/java/com/library/server/LibraryServiceConcurrencyTest.java`

**Steps:**
- [ ] Cài đặt `DataStorage`:
  - Đọc/ghi các tệp `.dat` trong thư mục `data/` (`users.dat`, `books.dat`, `copies.dat`, `borrows.dat`, `reservations.dat`, `audit.dat`, `search_history.dat`).
  - Tự động nạp sẵn dữ liệu mẫu (Seed Data) và tạo thư mục `data/ebooks/` với file PDF mẫu nếu chưa tồn tại.
- [ ] Cài đặt `LibraryService`:
  - Quản lý `ConcurrentHashMap<Integer, ReentrantLock> bookLocks`.
  - `authenticate(username, password)`.
  - `searchBooks(keyword, category, onlyAvailable)` tích hợp `FuzzySearchUtil`.
  - `recordSearch(studentId, keyword, category)` & `getRecommendations(studentId)`.
  - `borrowBook(studentId, bookId, daysToBorrow)`: Áp dụng lock theo `bookId`, kiểm tra hạn mức (tối đa 3), kiểm tra sách quá hạn, kiểm tra `isBlocked`, cấp bản sao `AVAILABLE` hoặc bản sao đang `RESERVED` cho sinh viên này, tạo `BorrowRecord`, ghi `AuditLog`. Nếu hết sách, trả về `OUT_OF_STOCK` gợi ý Đặt trước.
  - `returnBook(borrowRecordId)`: Đổi trạng thái `RETURNED`. Nếu có phiếu đặt trước `PENDING`, chuyển bản sao sang `RESERVED` và cấp hạn nhận 48h; nếu không chuyển về `AVAILABLE`. Nếu trả trễ hạn, tăng `violationCount++` (>= 3 thì khóa `isBlocked = true`).
  - `renewBook(borrowRecordId)`: Kiểm tra chưa quá hạn, `renewCount < 2`, không có ai đang chờ đặt trước -> gia hạn +7 ngày.
  - `reserveBook(studentId, bookId)`: Khi `availableCopies == 0`, đưa vào hàng đợi `PENDING`.
  - `cancelReservation(reservationId, studentId)`.
  - `getEbookData(bookId, isSiteLocal)`: Đọc file PDF từ `data/ebooks/`, cắt trang Preview nếu `isSiteLocal == false`.
  - `checkOverdueMidnight()`: Tác vụ quét lúc 00:00, chuyển các phiếu quá hạn sang `OVERDUE`.
  - `getAuditLogs()`, `unblockUser(username)`.
  - CRUD tựa sách và bản sao vật lý.
- [ ] Viết test `LibraryServiceConcurrencyTest.java`:
  - 10 threads cùng mượn 1 cuốn sách cuối cùng -> Đúng 1 thread thành công, 9 thread nhận gợi ý đặt trước.
  - Test tự động chuyển giao bản sao cho người đặt trước khi người mượn trả sách.
  - Test vi phạm 3 lần bị khóa mượn.
- [ ] Chạy kiểm thử xác nhận Task 2 hoàn tất.

---

### Task 3: Động cơ mạng Server Đa giao thức (TCP Thread Pool, UDP Discovery, Multicast & Realtime Broadcast)

**Files:**
- Create: `src/main/java/com/library/server/network/UdpDiscoveryServer.java`
- Create: `src/main/java/com/library/server/network/MulticastAnnouncementService.java`
- Create: `src/main/java/com/library/server/network/ClientHandler.java`
- Create: `src/main/java/com/library/server/network/LibraryServer.java`
- Create: `src/main/java/com/library/server/ServerMain.java`
- Test: `src/test/java/com/library/server/ServerNetworkIntegrationTest.java`

**Steps:**
- [ ] Cài đặt `UdpDiscoveryServer`:
  - Mở `DatagramSocket(8889)`.
  - Nhận gói tin broadcast `"DISCOVER_LIBRARY_SERVER"`, phản hồi lại IP của Server cho Client.
- [ ] Cài đặt `MulticastAnnouncementService`:
  - Quản lý `MulticastSocket`, gửi gói tin `MulticastMessage` tới địa chỉ `224.0.0.100:9999`.
- [ ] Cài đặt `ClientHandler`:
  - Khởi tạo `ObjectOutputStream` + `flush()` trước, sau đó `ObjectInputStream`.
  - Vòng lặp `readObject()` xử lý các lệnh: Mượn, Trả, Gia hạn, Đặt trước, Tải E-Book (truyền mảng byte stream), Lấy đề xuất, Giám sát ping.
  - Hàm `sendEvent(RealtimeEvent)`: Gọi `oos.reset()` trước khi gửi.
  - Đóng kết nối an toàn, gỡ khỏi `activeClients`, cập nhật log.
- [ ] Cài đặt `LibraryServer`:
  - Quản lý `ExecutorService` (Thread Pool) cho các kết nối TCP.
  - Quản lý `CopyOnWriteArrayList<ClientHandler> activeClients`.
  - Tích hợp `ScheduledExecutorService` chạy tự động vào 00:00 mỗi đêm (`checkOverdueMidnight`).
  - Hàm `broadcast(RealtimeEvent)` phát sóng thời gian thực đến toàn bộ Client online.
  - Đo lường thông số mạng (Ping, lưu lượng truyền E-Book).
- [ ] Cài đặt `ServerMain`: Khởi động đồng thời TCP Server (8888), UDP Discovery (8889), Multicast Service và Midnight Scheduler.
- [ ] Viết test `ServerNetworkIntegrationTest.java`. Chạy kiểm thử xác nhận Task 3 hoàn thành.

---

### Task 4: Tầng điều khiển mạng Client, Tự tìm Server & Bộ lắng nghe Realtime/Multicast

**Files:**
- Create: `src/main/java/com/library/client/config/ClientConfig.java`
- Create: `src/main/java/com/library/client/network/UdpDiscoveryClient.java`
- Create: `src/main/java/com/library/client/network/MulticastClientListener.java`
- Create: `src/main/java/com/library/client/network/RealtimeListener.java`
- Create: `src/main/java/com/library/client/network/LibraryClient.java`
- Test: `src/test/java/com/library/client/ClientNetworkTest.java`

**Steps:**
- [ ] Cài đặt `ClientConfig`: Đọc/ghi `config.properties`, fallback về `127.0.0.1:8888` nếu ô nhập để trống.
- [ ] Cài đặt `UdpDiscoveryClient`: Phát gói tin broadcast UDP `255.255.255.255:8889`, nhận phản hồi IP Server tự động.
- [ ] Cài đặt `MulticastClientListener`: `joinGroup("224.0.0.100")` trên port 9999, lắng nghe thông điệp loa phát thanh từ thư viện.
- [ ] Cài đặt `RealtimeListener` interface: Nhận sự kiện `DATA_CHANGED` và `RESERVATION_ALERT`.
- [ ] Cài đặt `LibraryClient`:
  - Khởi tạo kết nối TCP, tạo luồng ngầm lắng nghe `in.readObject()`.
  - Phân tách bản tin phản hồi (Response) và sự kiện Realtime (RealtimeEvent).
  - Cung cấp các phương thức gọi API mạng: `login`, `borrowBook`, `returnBook`, `renewBook`, `reserveBook`, `downloadEbook`, `getRecommendations`...
  - Phương thức `testConnection(host, port)` trả về kết quả Ping test.
- [ ] Viết test `ClientNetworkTest.java`. Chạy kiểm thử xác nhận Task 4 hoàn tất.

---

### Task 5: Giao diện đồ họa Java Swing Hoàn chỉnh (Student & Librarian Dashboards)

**Files:**
- Create: `src/main/java/com/library/client/ui/SettingsDialog.java`
- Create: `src/main/java/com/library/client/ui/LoginFrame.java`
- Create: `src/main/java/com/library/client/ui/StudentDashboard.java`
- Create: `src/main/java/com/library/client/ui/LibrarianDashboard.java`
- Create: `src/main/java/com/library/client/ui/BookDialog.java`
- Create: `src/main/java/com/library/client/ui/QRCodeViewerDialog.java`
- Create: `src/main/java/com/library/client/ClientMain.java`

**Steps:**
- [ ] Cài đặt `SettingsDialog` & `LoginFrame`:
  - Ô nhập Host/Port có placeholder `127.0.0.1` và `8888`.
  - Nút **"🔍 Tự tìm Server trong mạng LAN"** (gọi `UdpDiscoveryClient`).
  - Nút **"Kiểm tra kết nối"** (Ping test) đổi màu trạng thái xanh/đỏ.
- [ ] Cài đặt `StudentDashboard`:
  - Tab 1: **Tra cứu & Mượn sách:** Thanh tìm kiếm Fuzzy, ô quét nhanh mã QR, bảng danh mục sách, nút **Mượn sách** (chọn hạn 1-30 ngày), nút **Đặt trước** (khi sách hết), nút **Tải E-Book (PDF)**.
  - Tab 2: **Sách đang mượn:** Bảng sách cá nhân, hạn trả, cảnh báo đỏ quá hạn, nút **Trả sách**, nút **Gia hạn (+7 ngày)**.
  - Tab 3: **Sách đã đặt trước:** Danh sách giữ chỗ, hạn chót nhận sách, cảnh báo trước 6 tiếng, nút **Hủy đặt trước**.
  - Tab 4: **Gợi ý cho bạn:** Hiển thị các cuốn sách được đề xuất theo lịch sử tìm kiếm.
  - Đăng ký `RealtimeListener` (tự động cập nhật JTable qua `SwingUtilities.invokeLater()`) và `MulticastClientListener` (bật popup khi thư viện phát loa thông báo).
- [ ] Cài đặt `LibrarianDashboard`:
  - Tab 1: **Quản lý Tựa sách & Bản sao:** Bảng tựa sách, CRUD sách, xem/thêm bản sao vật lý, nút xem/xuất mã QR (`QRCodeViewerDialog`).
  - Tab 2: **Quản lý Mượn/Trả & Đặt trước:** Danh sách toàn trường, lọc quá hạn, tìm theo MSSV.
  - Tab 3: **Nhật ký (Audit Log) & Xử phạt vi phạm:** Xem lịch sử, danh sách sinh viên bị khóa mượn, nút **Mở khóa vi phạm**.
  - Tab 4: **Giám sát mạng & Loa phát thanh:** Form gửi thông báo Multicast toàn trường, bảng giám sát Client online, độ trễ Ping và lưu lượng truyền E-Book.
- [ ] Cài đặt `ClientMain`: Điểm vào khởi chạy ứng dụng Client Swing.

---

### Task 6: Kịch bản Windows Scripts UTF-8, Đóng gói & Kiểm thử Mô phỏng Tổng thể (End-to-End Simulation)

**Files:**
- Create: `build.bat`
- Create: `run-server.bat`
- Create: `run-client.bat`
- Create: `README.md`
- Test: `src/test/java/com/library/EndToEndFullSystemTest.java`

**Steps:**
- [ ] Tạo file `build.bat`: Cấu hình `chcp 65001`, quét toàn bộ file `.java` biên dịch bằng `javac -encoding UTF-8 -d bin`.
- [ ] Tạo file `run-server.bat`: `chcp 65001`, `java -Dfile.encoding=UTF-8 -cp bin com.library.server.ServerMain`.
- [ ] Tạo file `run-client.bat`: `chcp 65001`, `java -Dfile.encoding=UTF-8 -cp bin com.library.client.ClientMain`.
- [ ] Viết test `EndToEndFullSystemTest.java`:
  - Khởi động Server (TCP + UDP + Multicast).
  - Client 1 dùng UDP Discovery tự tìm thấy Server và kết nối TCP.
  - Client 1 mượn bản sao cuối cùng -> Client 2 nhận Realtime trừ số lượng -> Client 2 bấm Đặt trước.
  - Client 1 Trả sách -> Client 2 tự động nhận thông báo giữ chỗ ưu tiên kèm hạn nhận.
  - Client 1 tải E-Book qua Socket stream thành công.
  - Server phát tin Multicast -> Cả 2 Client nhận thông báo cùng lúc.
- [ ] Viết `README.md` hướng dẫn chạy script, hướng dẫn kết nối mạng LAN và hướng dẫn kết nối từ xa qua Ngrok.
- [ ] Commit toàn bộ mã nguồn lên GitHub.
