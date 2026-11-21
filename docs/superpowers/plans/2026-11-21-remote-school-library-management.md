# Remote School Library Management System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây dựng hoàn chỉnh Hệ thống Quản lý Mượn/Trả Sách Thư viện Trường học (Remote School Library Management) hoạt động qua mạng máy tính theo mô hình Khách/Chủ (Client/Server) đa luồng, hỗ trợ quản lý tài khoản, tựa sách và từng bản sao (BookCopy), tìm kiếm, mượn, trả, gia hạn (renew), đặt trước (reserve), tính quá hạn (overdue), xử lý an toàn đồng thời (synchronized) và đồng bộ Realtime Broadcast.

**Architecture:** 
- Phía Server: `ServerSocket` đa luồng (`ClientHandler`), đồng bộ hóa (`synchronized`) kho sách và bản sao, xử lý hàng đợi đặt trước (FIFO reservation queue), phát quảng bá sự kiện Realtime (`broadcast`) đến các Client đang online khi có thay đổi dữ liệu, ghi log chi tiết Console.
- Phía Client: Giao diện đồ họa Java Swing với luồng ngầm (`Background Listener Thread`) nhận tín hiệu Socket để cập nhật UI tự động (`SwingUtilities.invokeLater`), cấu hình IP & Port linh hoạt (hỗ trợ file `config.properties`, placeholder `127.0.0.1:8888`, ping test).
- Giao thức & Lưu trữ: Java Object Serialization (`Serializable`, `oos.reset()`, tạo `ObjectOutputStream` trước `ObjectInputStream`) và lưu trữ tệp nhị phân `.dat`.

**Tech Stack:** Pure Java (JDK 17+/21+), Java Swing, Java Socket API (`java.net`), Java I/O (`java.io`), Maven structure & Windows Batch scripts.

**Spec:** [SPECIFICATION.md](file:///d:/Code/LapTrinhManMayTinhRemoteSchoolLibraryManagement/SPECIFICATION.md)

---

## Global Constraints
- Chỉ sử dụng thư viện chuẩn của Java JDK (không sử dụng framework ngoài như Spring/Web/Netty hay thư viện bên thứ ba).
- Toàn bộ source code và file text lưu định dạng `UTF-8`.
- Luôn gọi `oos.reset()` khi gửi lại dữ liệu qua kết nối duy trì `Persistent Connection`.
- Luôn khởi tạo `new ObjectOutputStream` + `flush()` trước khi gọi `new ObjectInputStream`.
- Tất cả phương thức thay đổi kho sách, bản sao, phiếu mượn, đặt trước trên Server phải dùng `synchronized` để bảo đảm Thread-Safety.
- Scripts `.bat` trên Windows phải có `chcp 65001`, `-encoding UTF-8` và `-Dfile.encoding=UTF-8`.

---

## Review Focus
1. **Overselling & Copy Race Condition:** Nhiều sinh viên cùng mượn 1 bản sao khả dụng duy nhất tại cùng một thời điểm -> Đúng 1 người mượn được, bản sao chuyển `BORROWED`, không bị âm.
2. **Reservation FIFO Integrity:** Khi sách được trả, nếu có sinh viên đang đặt trước (`PENDING`), bản sao tự động được giữ chỗ (`RESERVED`) cho người đặt trước đầu tiên trong hàng đợi.
3. **Renewal Constraints:** Sinh viên chỉ được gia hạn khi chưa quá hạn, chưa vượt quá 2 lần, và không có sinh viên khác đang chờ đặt trước cuốn sách đó.
4. **Stale Data on Persistent Stream:** Dữ liệu thay đổi trên Server nhưng Client nhận lại bản cũ do cache của `ObjectOutputStream` -> Ngăn ngừa triệt để bằng `oos.reset()`.
5. **Stream Initialization Deadlock:** Khởi tạo sai thứ tự Input/Output stream làm treo ứng dụng -> Phải tạo OutputStream trước.
6. **Empty IP/Port fallback:** Người dùng xóa sạch ô IP/Port trong Cài đặt -> Tự động quay về mặc định `127.0.0.1:8888`, không gây ngoại lệ.
7. **Realtime UI Refresh Safety:** Cập nhật UI trên Swing từ luồng ngầm phải bọc qua `SwingUtilities.invokeLater()`.

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
- Create: `src/main/java/com/library/common/network/CommandType.java`
- Create: `src/main/java/com/library/common/network/ResponseStatus.java`
- Create: `src/main/java/com/library/common/network/Request.java`
- Create: `src/main/java/com/library/common/network/Response.java`
- Create: `src/main/java/com/library/common/network/RealtimeEvent.java`
- Test: `src/test/java/com/library/common/SerializationTest.java`

**Steps:**
- [ ] Tạo `pom.xml` cấu hình chuẩn Maven với Java 17+, plugin compiler encoding UTF-8, JUnit 5 cho test.
- [ ] Định nghĩa các enum: `Role` (`LIBRARIAN`, `STUDENT`), `CopyStatus` (`AVAILABLE`, `BORROWED`, `RESERVED`, `MAINTENANCE`), `BorrowStatus` (`BORROWING`, `RETURNED`, `OVERDUE`), `ReservationStatus` (`PENDING`, `FULFILLED`, `CANCELLED`).
- [ ] Cài đặt các class thực thể: `User`, `Book`, `BookCopy`, `BorrowRecord`, `ReservationRecord` thực thi `Serializable` kèm `serialVersionUID`.
- [ ] Cài đặt các class gói tin mạng: `CommandType` (bao gồm `BORROW_BOOK`, `RETURN_BOOK`, `RENEW_BOOK`, `RESERVE_BOOK`, `CANCEL_RESERVATION`,...), `ResponseStatus`, `Request`, `Response`, `RealtimeEvent` thực thi `Serializable`.
- [ ] Viết test `SerializationTest.java`: Tuần tự hóa một đối tượng `Request` & `Response` sang mảng byte và phục hồi lại để đảm bảo tính toàn vẹn dữ liệu.
- [ ] Chạy kiểm thử xác nhận Task 1 hoàn tất.

---

### Task 2: Tầng lưu trữ nhị phân thuần Java & Logic nghiệp vụ đồng bộ hóa (Storage & Thread-Safe Service)

**Files:**
- Create: `src/main/java/com/library/server/storage/DataStorage.java`
- Create: `src/main/java/com/library/server/service/LibraryService.java`
- Test: `src/test/java/com/library/server/LibraryServiceConcurrencyTest.java`

**Steps:**
- [ ] Cài đặt `DataStorage`:
  - Đọc/ghi các tệp `data/users.dat`, `data/books.dat`, `data/copies.dat`, `data/borrows.dat`, `data/reservations.dat` bằng `ObjectInputStream` / `ObjectOutputStream`.
  - Tự động nạp sẵn dữ liệu mẫu (Seed Data) nếu tệp chưa tồn tại: 1 Thủ thư (`admin`/`admin123`), 2 Sinh viên (`sv001`/`123`, `sv002`/`123`), 5 tựa sách kèm các bản sao vật lý.
- [ ] Cài đặt `LibraryService`:
  - `authenticate(username, password)`.
  - `getAllBooks()`, `searchBooks(keyword, onlyAvailable)`, `getBookCopies(bookId)`.
  - `synchronized borrowBook(studentId, bookId, daysToBorrow)`: Kiểm tra hạn mức, kiểm tra sách quá hạn, lấy bản sao `AVAILABLE` (hoặc bản sao đã `RESERVED` cho sinh viên đó), chuyển bản sao thành `BORROWED`, tạo `BorrowRecord`.
  - `synchronized returnBook(borrowRecordId)`: Đổi trạng thái `RETURNED`. Nếu có phiếu đặt trước (`PENDING`), chuyển bản sao thành `RESERVED` giữ chỗ cho người đặt đầu tiên; nếu không chuyển về `AVAILABLE`.
  - `synchronized renewBook(borrowRecordId)`: Kiểm tra chưa quá hạn, `renewCount < 2`, không có ai đặt trước cuốn sách đó -> gia hạn +7 ngày.
  - `synchronized reserveBook(studentId, bookId)`: Khi `availableCopies == 0`, tạo `ReservationRecord` vào hàng chờ `PENDING`.
  - `synchronized cancelReservation(reservationId, studentId)`.
  - `synchronized addBook(...)`, `updateBook(...)`, `deleteBook(...)`, `addCopy(...)`, `deleteCopy(...)`.
  - `getUserBorrows(studentId)`, `getUserReservations(studentId)`, `getAllBorrows()`, `getAllReservations()`.
  - `checkOverdue()`: Cập nhật tự động trạng thái `OVERDUE` cho các phiếu mượn quá hạn.
- [ ] Viết test tương tranh đa luồng `LibraryServiceConcurrencyTest.java`:
  - Kiểm tra 5 threads cùng tranh mượn 1 bản sao duy nhất -> Chỉ 1 thread thành công.
  - Kiểm tra đặt trước và tự động chuyển giao bản sao khi trả sách.
  - Kiểm tra giới hạn gia hạn tối đa 2 lần và chặn gia hạn khi quá hạn.
- [ ] Chạy kiểm thử xác nhận Task 2 vượt qua an toàn.

---

### Task 3: Động cơ mạng Server, Quản lý đa luồng & Realtime Broadcast Engine

**Files:**
- Create: `src/main/java/com/library/server/network/ClientHandler.java`
- Create: `src/main/java/com/library/server/network/LibraryServer.java`
- Create: `src/main/java/com/library/server/ServerMain.java`
- Test: `src/test/java/com/library/server/ServerNetworkIntegrationTest.java`

**Steps:**
- [ ] Cài đặt `ClientHandler`:
  - Nhận `Socket` và tham chiếu tới `LibraryServer`.
  - Khởi tạo `ObjectOutputStream` và `flush()` TRƯỚC, sau đó khởi tạo `ObjectInputStream`.
  - Vòng lặp `while(running)` đọc `Request` từ client, phân phối tới `LibraryService` (xử lý đăng nhập, sách, bản sao, mượn, trả, gia hạn, đặt trước) và gửi `Response` về.
  - Phương thức `sendEvent(RealtimeEvent event)`: Gọi `oos.reset()` rồi `oos.writeObject(event)` và `oos.flush()`.
  - Bắt ngoại lệ ngắt kết nối an toàn, gỡ khỏi `activeClients`, cập nhật log.
- [ ] Cài đặt `LibraryServer`:
  - `ServerSocket` mở tại port (mặc định 8888).
  - Quản lý `CopyOnWriteArrayList<ClientHandler> activeClients`.
  - Phương thức `broadcastEvent(RealtimeEvent event)`: Lặp qua toàn bộ `activeClients` và gửi sự kiện Realtime.
  - In log chi tiết với timestamp: `[TIMESTAMP] [CONNECT]`, `[LOGIN]`, `[BORROW]`, `[RETURN]`, `[RENEW]`, `[RESERVE]`, `[DISCONNECT]`, hiển thị số lượng client online.
- [ ] Cài đặt `ServerMain`: Khởi động Server từ dòng lệnh.
- [ ] Viết `ServerNetworkIntegrationTest.java`: Khởi động Server trên port ngẫu nhiên, test client kết nối, mượn, trả, gia hạn, đặt trước và nhận broadcast realtime.
- [ ] Chạy kiểm thử xác nhận Task 3 hoàn thành.

---

### Task 4: Tầng điều khiển mạng phía Client & Bộ lắng nghe Realtime ngầm (Client Network Core)

**Files:**
- Create: `src/main/java/com/library/client/config/ClientConfig.java`
- Create: `src/main/java/com/library/client/network/RealtimeListener.java`
- Create: `src/main/java/com/library/client/network/LibraryClient.java`
- Test: `src/test/java/com/library/client/ClientConfigTest.java`

**Steps:**
- [ ] Cài đặt `ClientConfig`:
  - Đọc/ghi tệp `config.properties` (`server.ip`, `server.port`).
  - Giá trị mặc định `127.0.0.1` và `8888`.
  - Hàm `resolveHost(String input)` và `resolvePort(String input)`: Nếu input rỗng/khoảng trắng -> Trả về mặc định `127.0.0.1` và `8888`.
- [ ] Cài đặt interface `RealtimeListener`:
  - `void onDataChanged();`
  - `void onNotification(String message);`
- [ ] Cài đặt `LibraryClient`:
  - Quản lý `Socket`, `ObjectOutputStream`, `ObjectInputStream`.
  - Khởi tạo luồng ngầm `Background Listener Thread` chạy liên tục lắng nghe `in.readObject()`.
  - Phân tách bản tin: `Response` chuyển về hàm gọi đồng bộ; `RealtimeEvent` chuyển cho các `RealtimeListener`.
  - Phương thức gửi các lệnh: `login`, `borrowBook`, `returnBook`, `renewBook`, `reserveBook`, `cancelReservation`, `getBooks`,...
  - Phương thức `testConnection(host, port)` trả về kết quả kiểm tra Server online.
- [ ] Viết test `ClientConfigTest.java`: Kiểm tra fallback giá trị mặc định khi chuỗi rỗng.
- [ ] Chạy kiểm thử xác nhận Task 4 hoàn tất.

---

### Task 5: Giao diện đồ họa Java Swing (GUI) cho Thủ thư và Sinh viên

**Files:**
- Create: `src/main/java/com/library/client/ui/SettingsDialog.java`
- Create: `src/main/java/com/library/client/ui/LoginFrame.java`
- Create: `src/main/java/com/library/client/ui/StudentDashboard.java`
- Create: `src/main/java/com/library/client/ui/LibrarianDashboard.java`
- Create: `src/main/java/com/library/client/ui/BookDialog.java`
- Create: `src/main/java/com/library/client/ClientMain.java`

**Steps:**
- [ ] Cài đặt `SettingsDialog`:
  - Ô nhập Host & Port có placeholder `127.0.0.1` và `8888`.
  - Nút "Kiểm tra kết nối" (Ping test): Báo xanh nếu kết nối tốt, báo đỏ nếu lỗi.
  - Nút "Lưu": Lưu vào `config.properties`.
- [ ] Cài đặt `LoginFrame`: Form đăng nhập, nút Đăng nhập, nút "⚙ Cấu hình Server".
- [ ] Cài đặt `StudentDashboard`:
  - Tab 1: "Tra cứu & Mượn sách": Bảng sách (`JTable`), tìm kiếm, nút **Mượn sách** (dialog chọn hạn trả 1-30 ngày), nút **Đặt trước** (khi sách hết).
  - Tab 2: "Sách đang mượn": Bảng danh sách sách mượn, cảnh báo đỏ nếu quá hạn, nút **Trả sách**, nút **Gia hạn (+7 ngày)**.
  - Tab 3: "Sách đã đặt trước": Bảng danh sách sách đang chờ đặt trước, nút **Hủy đặt trước**.
  - Đăng ký `RealtimeListener`: Khi có sự kiện Realtime, tự động gọi `SwingUtilities.invokeLater()` cập nhật lại dữ liệu.
- [ ] Cài đặt `LibrarianDashboard`:
  - Tab 1: "Quản lý tựa sách & Bản sao": Bảng tựa sách, nút Thêm/Sửa/Xóa sách; bảng bản sao vật lý của sách, nút Thêm/Xóa bản sao.
  - Tab 2: "Quản lý Mượn / Trả": Bảng toàn bộ phiếu mượn trường, lọc quá hạn, tìm theo mã SV.
  - Tab 3: "Quản lý Đặt trước": Bảng giám sát danh sách sinh viên đặt trước sách.
  - Đăng ký `RealtimeListener`: Cập nhật bảng tức thì khi có biến động.
- [ ] Cài đặt `ClientMain`: Điểm vào khởi chạy giao diện Swing của Client.

---

### Task 6: Kịch bản Windows Scripts UTF-8, Đóng gói & Kiểm thử tổng thể (End-to-End Simulation)

**Files:**
- Create: `build.bat`
- Create: `run-server.bat`
- Create: `run-client.bat`
- Create: `README.md`
- Test: `src/test/java/com/library/EndToEndSimulationTest.java`

**Steps:**
- [ ] Tạo file `build.bat`: `chcp 65001`, `javac -encoding UTF-8 -d bin ...`.
- [ ] Tạo file `run-server.bat`: `chcp 65001`, `java -Dfile.encoding=UTF-8 -cp bin com.library.server.ServerMain`.
- [ ] Tạo file `run-client.bat`: `chcp 65001`, `java -Dfile.encoding=UTF-8 -cp bin com.library.client.ClientMain`.
- [ ] Viết kiểm thử `EndToEndSimulationTest.java`:
  - Server khởi động ngầm.
  - 2 Client kết nối đồng thời.
  - Mô phỏng luồng hoàn chỉnh: Sinh viên 1 mượn bản sao cuối cùng -> Sách hết khả dụng -> Sinh viên 2 bấm Đặt trước -> Sinh viên 1 Trả sách -> Hệ thống tự động chuyển giao bản sao cho Sinh viên 2 giữ chỗ realtime.
  - Kiểm tra Sinh viên 1 gia hạn sách thành công.
- [ ] Viết `README.md` hướng dẫn chi tiết cách chạy demo cho giảng viên.
- [ ] Commit & Push lên GitHub repository.
