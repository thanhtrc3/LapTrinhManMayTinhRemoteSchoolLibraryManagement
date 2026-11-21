# Remote School Library Management System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây dựng hoàn chỉnh Hệ thống Quản lý Mượn/Trả Sách Thư viện Trường học (Remote School Library Management) hoạt động qua mạng máy tính theo mô hình Khách/Chủ (Client/Server) đa luồng, hỗ trợ Realtime Broadcast, giao diện Swing và lưu trữ nhị phân thuần Java.

**Architecture:** 
- Phía Server: `ServerSocket` đa luồng (`ClientHandler`), đồng bộ hóa (`synchronized`) kho sách, phát quảng bá sự kiện Realtime (`broadcast`) đến các Client đang online khi có thay đổi dữ liệu, ghi log chi tiết Console.
- Phía Client: Giao diện đồ họa Java Swing với luồng ngầm (`Background Listener Thread`) nhận tín hiệu Socket để cập nhật UI tự động (`SwingUtilities.invokeLater`), cấu hình IP & Port linh hoạt (hỗ trợ file `config.properties` và placeholder `127.0.0.1:8888`).
- Giao thức & Lưu trữ: Java Object Serialization (`Serializable`, `oos.reset()`, tạo `ObjectOutputStream` trước `ObjectInputStream`) và lưu trữ tệp nhị phân `.dat`.

**Tech Stack:** Pure Java (JDK 17+/21+), Java Swing, Java Socket API (`java.net`), Java I/O (`java.io`), Maven structure & Windows Batch scripts.

**Spec:** [SPECIFICATION.md](file:///d:/Code/Chưa%20biết%20đặt%20tên%20gì%20AI%20nhớ%20sửa%20tên/SPECIFICATION.md)

---

## Global Constraints
- Chỉ sử dụng thư viện chuẩn của Java JDK (không sử dụng framework ngoài như Spring/Web/Netty hay thư viện bên thứ ba).
- Toàn bộ source code và file text lưu định dạng `UTF-8`.
- Luôn gọi `oos.reset()` khi gửi lại dữ liệu qua kết nối duy trì `Persistent Connection`.
- Luôn khởi tạo `new ObjectOutputStream` + `flush()` trước khi gọi `new ObjectInputStream`.
- Tất cả phương thức thay đổi kho sách trên Server phải dùng `synchronized` để bảo đảm Thread-Safety.
- Scripts `.bat` trên Windows phải có `chcp 65001`, `-encoding UTF-8` và `-Dfile.encoding=UTF-8`.

---

## Review Focus
1. **Overselling Race Condition:** Hai sinh viên cùng mượn 1 cuốn sách cuối cùng cùng lúc -> Chỉ 1 người thành công, người kia nhận thông báo hết sách, số lượng không bị âm.
2. **Stale Data on Persistent Stream:** Dữ liệu thay đổi trên Server nhưng Client nhận lại bản cũ do cache của `ObjectOutputStream` -> Ngăn ngừa triệt để bằng `oos.reset()`.
3. **Stream Initialization Deadlock:** Khởi tạo sai thứ tự Input/Output stream làm treo ứng dụng -> Phải tạo OutputStream trước.
4. **Empty IP/Port fallback:** Người dùng xóa sạch ô IP/Port trong Cài đặt -> Tự động quay về mặc định `127.0.0.1:8888`, không gây `NullPointerException` hoặc `NumberFormatException`.
5. **Realtime UI Refresh Safety:** Server đẩy dữ liệu về luồng ngầm nhưng cập nhật UI trên Swing không đúng luồng -> Phải bọc qua `SwingUtilities.invokeLater()`.

---

### Task 1: Thiết lập cấu trúc dự án & Các mô hình dữ liệu dùng chung (Shared Models & Packets)

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/library/common/model/Role.java`
- Create: `src/main/java/com/library/common/model/User.java`
- Create: `src/main/java/com/library/common/model/Book.java`
- Create: `src/main/java/com/library/common/model/BorrowStatus.java`
- Create: `src/main/java/com/library/common/model/BorrowRecord.java`
- Create: `src/main/java/com/library/common/network/CommandType.java`
- Create: `src/main/java/com/library/common/network/ResponseStatus.java`
- Create: `src/main/java/com/library/common/network/Request.java`
- Create: `src/main/java/com/library/common/network/Response.java`
- Create: `src/main/java/com/library/common/network/RealtimeEvent.java`
- Test: `src/test/java/com/library/common/SerializationTest.java`

**Steps:**
- [ ] Tạo `pom.xml` cấu hình chuẩn Maven với Java 17+, plugin compiler encoding UTF-8, JUnit 5 cho test.
- [ ] Định nghĩa các enum: `Role` (`LIBRARIAN`, `STUDENT`), `BorrowStatus` (`BORROWING`, `RETURNED`, `OVERDUE`).
- [ ] Cài đặt các class thực thể: `User`, `Book`, `BorrowRecord` thực thi `Serializable` kèm `serialVersionUID`.
- [ ] Cài đặt các class gói tin mạng: `CommandType`, `ResponseStatus`, `Request`, `Response`, `RealtimeEvent` thực thi `Serializable`.
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
  - Đọc/ghi các tệp `data/users.dat`, `data/books.dat`, `data/borrows.dat` bằng `ObjectInputStream` / `ObjectOutputStream`.
  - Tự động nạp sẵn dữ liệu mẫu (Seed Data) nếu tệp chưa tồn tại: 1 tài khoản Thủ thư (`admin`/`admin123`), 2 tài khoản Sinh viên (`sv001`/`123`, `sv002`/`123`), và 10 đầu sách phong phú.
- [ ] Cài đặt `LibraryService`:
  - `authenticate(username, password)`
  - `getAllBooks()`, `searchBooks(keyword, onlyAvailable)`
  - `synchronized borrowBook(studentId, bookId, daysToBorrow)`: Kiểm tra hạn mức mượn sinh viên (ví dụ tối đa 3 cuốn đang mượn), kiểm tra `availableQuantity > 0`, trừ số lượng, tạo `BorrowRecord` kèm ngày hẹn trả.
  - `synchronized returnBook(borrowRecordId)`: Đổi trạng thái `RETURNED`, tăng lại `availableQuantity` của sách.
  - `synchronized addBook(...)`, `updateBook(...)`, `deleteBook(...)`.
  - `getUserBorrows(studentId)`, `getAllBorrows()`.
- [ ] Viết test tương tranh đa luồng `LibraryServiceConcurrencyTest.java`:
  - Tạo 1 cuốn sách có số lượng `availableQuantity = 1`.
  - Bắn 5 threads cùng gọi `borrowBook()` cùng 1 thời điểm.
  - Kiểm tra assert: Đúng 1 thread thành công, 4 thread bị từ chối, `availableQuantity == 0`, không bị âm.
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
  - Vòng lặp `while(running)` đọc `Request` từ client, gọi `LibraryService` xử lý và gửi `Response` về.
  - Phương thức `sendEvent(RealtimeEvent event)`: Gọi `oos.reset()` rồi `oos.writeObject(event)` và `oos.flush()`.
  - Bắt ngoại lệ ngắt kết nối (Disconnect/EOF), đóng socket an toàn, báo Server gỡ khỏi `activeClients`.
- [ ] Cài đặt `LibraryServer`:
  - `ServerSocket` mở tại port (mặc định 8888).
  - Quản lý `CopyOnWriteArrayList<ClientHandler> activeClients`.
  - Phương thức `broadcastEvent(RealtimeEvent event)`: Lặp qua toàn bộ `activeClients` và gửi sự kiện Realtime.
  - In log chi tiết với timestamp: `[TIMESTAMP] [CONNECT]`, `[LOGIN]`, `[BORROW]`, `[DISCONNECT]`, hiển thị số lượng client online.
- [ ] Cài đặt `ServerMain`: Điểm vào (Entry point) khởi động Server từ dòng lệnh.
- [ ] Viết `ServerNetworkIntegrationTest.java`: Khởi động Server trên một port test ngẫu nhiên, mở 2 socket client kết nối, đăng nhập, test nhận gói tin phản hồi và nhận broadcast realtime.
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
  - `void onBookDataChanged(List<Book> updatedBooks);`
  - `void onNotificationReceived(String message);`
- [ ] Cài đặt `LibraryClient`:
  - Quản lý `Socket`, `ObjectOutputStream`, `ObjectInputStream`.
  - Khởi tạo luồng ngầm `Background Listener Thread` chạy liên tục lắng nghe `in.readObject()`.
  - Phân tách bản tin: Nếu là `Response` tương ứng với Request vừa gửi thì chuyển giao cho hàm gọi; nếu là `RealtimeEvent` thì bắn callback sang các `RealtimeListener` đã đăng ký.
  - Phương thức `sendRequest(Request req)` đồng bộ hoặc future để lấy `Response`.
  - Phương thức `testConnection(host, port)` trả về kết quả nhanh kiểm tra Server có đang online hay không.
- [ ] Viết test `ClientConfigTest.java`: Kiểm tra fallback giá trị mặc định khi chuỗi rỗng hoặc xóa sạch ô nhập.
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
  - Ô nhập IP và Port có placeholder `127.0.0.1` và `8888`.
  - Nút "Kiểm tra kết nối" (Ping test): Báo xanh nếu kết nối được, báo đỏ nếu server tắt.
  - Nút "Lưu": Lưu vào `config.properties`.
- [ ] Cài đặt `LoginFrame`:
  - Thiết kế sạch sẽ, hiện đại: Username, Password, Nút Đăng nhập, Nút "⚙ Cấu hình Server".
  - Bắt lỗi thông báo trực quan (sai mật khẩu, server chưa bật...).
- [ ] Cài đặt `StudentDashboard`:
  - Tab 1: "Danh mục sách": Bảng `JTable` hiển thị sách, thanh tìm kiếm từ khóa, lọc tình trạng (Còn sách / Tất cả), nút "Mượn sách" (hiện dialog cho phép chọn hạn trả 1-30 ngày).
  - Tab 2: "Sách đang mượn": Bảng `JTable` danh sách sách của cá nhân, hiển thị ngày mượn, ngày hẹn trả, cảnh báo chữ đỏ nếu quá hạn, nút "Trả sách".
  - Đăng ký `RealtimeListener`: Khi nhận sự kiện thay đổi sách, tự động gọi `SwingUtilities.invokeLater()` cập nhật lại `JTable` ngay lập tức.
- [ ] Cài đặt `LibrarianDashboard`:
  - Tab 1: "Quản lý kho sách": Bảng `JTable`, nút Thêm, Sửa, Xóa, Cập nhật số lượng qua `BookDialog`.
  - Tab 2: "Quản lý mượn/trả": Bảng `JTable` hiển thị toàn bộ phiếu mượn của trường, lọc sách quá hạn, tìm kiếm theo mã sinh viên.
  - Đăng ký `RealtimeListener`: Cập nhật tự động dữ liệu kho sách và phiếu mượn realtime.
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
- [ ] Tạo file `build.bat`:
  - Lệnh `chcp 65001`.
  - Quét toàn bộ file `.java` và biên dịch bằng `javac -encoding UTF-8 -d bin ...`.
- [ ] Tạo file `run-server.bat`:
  - Chạy `java -Dfile.encoding=UTF-8 -cp bin com.library.server.ServerMain`.
- [ ] Tạo file `run-client.bat`:
  - Chạy `java -Dfile.encoding=UTF-8 -cp bin com.library.client.ClientMain`.
- [ ] Viết kiểm thử `EndToEndSimulationTest.java`:
  - Tự động dựng Server ngầm.
  - Tạo 2 Client kết nối đồng thời.
  - Client 1 (Sinh viên 1) mượn sách.
  - Xác nhận Client 2 (Sinh viên 2) nhận được sự kiện Realtime cập nhật số lượng sách giảm đi mà không cần làm gì.
  - Client 1 trả sách, xác nhận số lượng sách hồi phục realtime.
- [ ] Viết `README.md` hướng dẫn chi tiết cách chạy bằng file `.bat` và cách chạy qua IDE cho giáo viên chấm bài.
- [ ] Chạy kiểm thử toàn bộ dự án (`mvn test` hoặc qua script) và xác nhận 100% hoạt động mượt mà.
