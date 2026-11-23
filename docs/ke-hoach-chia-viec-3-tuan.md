# KẾ HOẠCH CHIA VIỆC 3 TUẦN — Hệ thống Quản lý Mượn/Trả Sách Thư viện Trường học

**Nhóm:** 3 người · **Thời gian:** 23/11/2026 → 13/12/2026 (15 ngày làm việc) · **Demo:** 14–16/12/2026
**Nguồn yêu cầu:** `SPECIFICATION.md` (18 tính năng — **không cắt tính năng nào**)
**Cách chia:** theo tầng — A: Server & Mạng · B: Client & Giao diện · C: Dữ liệu, Nghiệp vụ, Kiểm thử

---

## 0. Cách đọc tài liệu này

- Mỗi ngày có đúng 1 task/người. Task ghi: **File** cần tạo, **Việc** phải làm (đủ cụ thể để người không biết code vẫn viết đúng), **Nghiệm thu** (cách biết là xong — chạy lệnh gì, thấy gì).
- Không tự đổi tên class/method/package trong mục 2. Ba người code song song trên 3 máy, chỉ khớp nhau được nếu tên khớp tuyệt đối.
- Không thêm thư viện ngoài. Không đổi giao diện sang theme màu mè.
- Hết mỗi ngày: commit + push, nhắn nhóm 1 dòng "A xong D3".

---

## 1. Chốt kỹ thuật (không tự ý đổi)

### 1.1 Build & chạy

Dùng **Maven** (không dùng `javac` tay — để có JUnit và 1 lệnh kiểm thử duy nhất):

| Việc | Lệnh |
|---|---|
| Build + chạy toàn bộ test | `mvnw.cmd test` |
| Chạy 1 class test | `mvnw.cmd -Dtest=LibraryServiceTest test` |
| Chạy server | `run-server.bat` |
| Chạy client | `run-client.bat` |

`pom.xml`: Java 17, `<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>`, `junit-jupiter` 5.10.2 (scope `test`), `<mainClass>` không cần.
`run-server.bat` / `run-client.bat`: `chcp 65001` → `mvnw.cmd -DskipTests package` → `java -Dfile.encoding=UTF-8 -cp target/classes com.library.server.ServerMain` (và `com.library.client.ClientMain`).

> Không cần cài Maven: repo đã có `mvnw.cmd` (Maven Wrapper), lần chạy đầu tự tải Maven 3.9.9 về `~/.m2`. Lần đầu cần Internet; sau đó chạy offline được.

### 1.2 Cấu trúc thư mục

```
pom.xml
src/main/java/com/library/
  common/model/    Role, User, Book, CopyStatus, BookCopy, BorrowStatus, BorrowRecord,
                   ReservationStatus, ReservationRecord, AuditAction, AuditLog, SearchHistoryItem
  common/net/      CommandType, ResponseStatus, Request, Response, EventType, RealtimeEvent,
                   MulticastMessage, ServerStats, EventPublisher
  common/util/     FuzzySearchUtil
  server/storage/  DataStorage
  server/service/  LibraryService, LibraryException
  server/net/      ClientHandler, LibraryServer, SessionRegistry, UdpDiscoveryServer,
                   MulticastAnnouncementService
  server/          ServerMain
  client/config/   ClientConfig
  client/net/      LibraryClient, UdpDiscoveryClient, MulticastClientListener, RealtimeListener
  client/ui/       LoginFrame, SettingsDialog, StudentDashboard, LibrarianDashboard,
                   BookDialog, QRCodeDialog
  client/          ClientMain
src/test/java/com/library/
  common/ storage/ service/   → C sở hữu
  server/                     → A sở hữu
  client/                     → B sở hữu
data/                          users.dat books.dat copies.dat borrows.dat reservations.dat
                               audit.dat search_history.dat  ebooks/
```

### 1.3 Quy ước nhóm

- **Nhánh:** `feat/server` (A), `feat/client` (B), `feat/data` (C). Merge vào `main` mỗi tối. Không ai commit vào nhánh người khác.
- **Không sửa file của người khác.** Cần đổi → nhắn trong nhóm, người sở hữu sửa.
- **Giao diện:** Java Swing mặc định (Metal L&F), chỉ `BorderLayout` / `GridBagLayout` / `JTable` / `JButton` / `JTextField`. Không icon emoji, không màu tự chọn (chỉ đỏ cho quá hạn), không font ngoài, không ảnh nền. Mục tiêu: nhìn như công cụ, không như slide.
- **Mọi thao tác mạng chạy ngoài luồng UI**; cập nhật UI bằng `SwingUtilities.invokeLater(...)`.
- **Số liệu cố định (không tự đổi):** port TCP `8888`, UDP `8889`, multicast `224.0.0.100:9999`; hạn mức mượn `3` cuốn; hạn mượn mặc định `14` ngày (chọn 1–30); gia hạn `+7` ngày, tối đa `2` lần; giữ chỗ `48` giờ, cảnh báo trước `6` giờ; vi phạm `>= 3` → khóa; Levenshtein `<= 2`.

---

## 2. Hợp đồng dùng chung (D1 — A và C làm, cả nhóm chốt)

Đây là phần **quan trọng nhất**. Sai tên ở đây thì D2 trở đi cả nhóm tắc.

### 2.1 Lớp model — C viết (`common/model`)

Tất cả `implements Serializable`, `private static final long serialVersionUID = 1L;`, field `private`, có getter/setter, có constructor rỗng + constructor đầy đủ.

| Class | Field |
|---|---|
| `Role` (enum) | `STUDENT, LIBRARIAN` |
| `User` | `username, password, fullName, Role role, email, int maxBorrowQuota = 3, int violationCount, boolean blocked` |
| `Book` | `int id, isbn, title, author, category, int publishYear, int totalCopies, int availableCopies, boolean hasEbook, String ebookFileName` |
| `CopyStatus` (enum) | `AVAILABLE, BORROWED, RESERVED, MAINTENANCE` |
| `BookCopy` | `String copyId, int bookId, String barcode, String qrData, CopyStatus status` |
| `BorrowStatus` (enum) | `BORROWING, RETURNED, OVERDUE` |
| `BorrowRecord` | `long id, String copyId, int bookId, String bookTitle, String studentId, LocalDate borrowDate, dueDate, returnDate, BorrowStatus status, int renewCount` |
| `ReservationStatus` (enum) | `PENDING, FULFILLED, CANCELLED, EXPIRED` |
| `ReservationRecord` | `long id, int bookId, String bookTitle, studentId, LocalDateTime reservationDate, expiryDate, ReservationStatus status, String assignedCopyId` |
| `AuditAction` (enum) | `BORROW, RETURN, RENEW, RESERVE, OVERDUE_STRIKE, UNBLOCK, EBOOK_DOWNLOAD` |
| `AuditLog` | `long id, LocalDateTime timestamp, AuditAction action, String actorUsername, String details` |
| `SearchHistoryItem` | `String studentId, searchKeyword, searchCategory; LocalDateTime timestamp` |

### 2.2 Lớp giao thức — A viết (`common/net`)

```java
public enum CommandType { LOGIN, LOGOUT, PING, GET_BOOKS, GET_COPIES, GET_RECOMMENDATIONS,
  RECORD_SEARCH, BORROW, RETURN, RENEW, RESERVE, CANCEL_RESERVATION, GET_MY_BORROWS,
  GET_MY_RESERVATIONS, GET_ALL_BORROWS, GET_ALL_RESERVATIONS, DOWNLOAD_EBOOK, GET_AUDIT_LOGS,
  GET_BLOCKED_USERS, UNBLOCK_USER, ADD_BOOK, UPDATE_BOOK, DELETE_BOOK, ADD_COPY, REMOVE_COPY,
  ANNOUNCE, GET_SERVER_STATS, QUICK_SCAN }

public enum ResponseStatus { OK, ERROR, UNAUTHORIZED, NOT_FOUND, OUT_OF_STOCK, BLOCKED, QUOTA_EXCEEDED }

public class Request implements Serializable {
  private long requestId;                 // client tự tăng từ 1
  private CommandType command;
  private Map<String, Object> payload;    // luôn khác null (HashMap mới khi không có tham số)
  // Request(CommandType, Map), no-arg ctor, getters/setters
}

public class Response implements Serializable {
  private long requestId;
  private ResponseStatus status;
  private String message;                 // tiếng Việt, hiện thẳng lên UI
  private Object data;
  // Response(long, ResponseStatus, String, Object), getters
}

public enum EventType { BOOKS_CHANGED, BORROW_CHANGED, RESERVATION_ALERT, HOLD_EXPIRING,
  VIOLATION_BLOCKED, ANNOUNCEMENT }

public class RealtimeEvent implements Serializable {
  private EventType type;
  private String targetUsername;          // null = gửi mọi client
  private String message;
  private Object data;
}

public class MulticastMessage implements Serializable {
  // String title, String content, long sentAt, String sender
}

public class ServerStats implements Serializable {
  private int onlineClients;
  private List<String> clientAddresses;   // "IP:port"
  private long avgPingMs;
  private long totalBytesSent;
}

public interface EventPublisher { void publish(RealtimeEvent event); }
```

### 2.3 Bảng lệnh (A dùng để dispatch, B dùng để gọi, C dùng trong service)

| Command | payload key | `data` trả về |
|---|---|---|
| `LOGIN` | `username`, `password` | `User` |
| `PING` | — | `Long` (server time millis) |
| `GET_BOOKS` | `keyword` (null được), `category` (null được), `onlyAvailable` (Boolean) | `List<Book>` |
| `GET_COPIES` | `bookId` | `List<BookCopy>` |
| `GET_RECOMMENDATIONS` | — | `List<Book>` |
| `RECORD_SEARCH` | `keyword`, `category` | — |
| `BORROW` | `bookId`, `days` | `BorrowRecord` |
| `RETURN` | `copyId` | `BorrowRecord` |
| `RENEW` | `borrowRecordId` (Long) | `BorrowRecord` |
| `RESERVE` | `bookId` | `ReservationRecord` |
| `CANCEL_RESERVATION` | `reservationId` (Long) | — |
| `GET_MY_BORROWS` / `GET_MY_RESERVATIONS` | — | `List<BorrowRecord>` / `List<ReservationRecord>` |
| `GET_ALL_BORROWS` / `GET_ALL_RESERVATIONS` | — | như trên (mọi sinh viên) |
| `DOWNLOAD_EBOOK` | `bookId` | `byte[]` |
| `GET_AUDIT_LOGS` | — | `List<AuditLog>` |
| `GET_BLOCKED_USERS` | — | `List<User>` |
| `UNBLOCK_USER` | `username` | `User` |
| `ADD_BOOK` | `title, author, category, publishYear, copies` | `Book` |
| `UPDATE_BOOK` | `bookId, title, author, category, publishYear` | `Book` |
| `DELETE_BOOK` | `bookId` | — |
| `ADD_COPY` | `bookId` | `BookCopy` |
| `REMOVE_COPY` | `copyId` | — |
| `ANNOUNCE` | `title`, `content` | — (server phát multicast) |
| `GET_SERVER_STATS` | — | `ServerStats` |
| `QUICK_SCAN` | `barcode` | `BookCopy` |
| `GET_USERS` | — | `List<User>` (chỉ `LIBRARIAN`) |
| `ADD_USER` | `username, password, fullName, email` | `User` (role `STUDENT`) |
| `RESET_PASSWORD` | `username`, `password` | `User` |

**Quy tắc:** lệnh không có trong bảng `GET_MY_*` dùng user đã đăng nhập trong session của kết nối đó — client **không** gửi `studentId`. `LOGIN` là lệnh duy nhất được phép trước khi có session; các lệnh khác trước `LOGIN` → `ResponseStatus.UNAUTHORIZED`.

### 2.4 Nghiệm thu D1 (cả nhóm)

- [ ] `mvnw.cmd test` chạy xanh với 2 test: `SerializationTest` (serialize/deserialize `Request` + `Book`) và `FuzzySearchTest`.
- [ ] `FuzzySearchUtil.normalize("Lập Trình Mạng")` → `"lap trinh mang"`; `matches("lap trinh mag", "Lập trình mạng")` → `true`; `matches("toan roi rac", "Lập trình mạng")` → `false`.
- [ ] Merge xong, tag git `contract-v1`, cả 3 người `git pull` trước khi làm D2.

---

## 3. Phân công

### 3.1 Người A — Server & Mạng

| Ngày | Task | File | Việc | Nghiệm thu |
|---|---|---|---|---|
| D1 | A1 Hợp đồng mạng | `pom.xml`, toàn bộ `common/net/*` | Viết đúng mục 2.2 + `pom.xml` mục 1.1 | Test serialize `Request`/`Response`/`MulticastMessage` xanh |
| D2 | A2 Khung TCP | `server/net/ClientHandler`, `LibraryServer`, `SessionRegistry`, `server/ServerMain` | `ServerSocket(8888)`; `ExecutorService pool = Executors.newFixedThreadPool(50)`; mỗi kết nối 1 `ClientHandler`: tạo `ObjectOutputStream` → `flush()` → rồi mới `ObjectInputStream`; vòng `readObject()`; xử lý `LOGIN`/`PING`/`GET_BOOKS`, lệnh khác trả `ERROR "Chưa hỗ trợ"`; `SessionRegistry` giữ `CopyOnWriteArrayList<ClientHandler>` + map `username → handler`; đóng kết nối trong `finally` (gỡ khỏi registry). *Nếu C chưa xong `LibraryService` ngày D2: tạm dùng `LibraryServiceStub` trả danh sách rỗng, D4 nối lại hàm thật.* | `mvnw.cmd -Dtest=ServerLoginTest test` xanh; chạy `run-server.bat` rồi `run-client.bat` thấy log "client connected" |
| D3 | A3 UDP Discovery | `server/net/UdpDiscoveryServer` | `DatagramSocket(8889)`, `setBroadcast(true)`; nhận `"DISCOVER_LIBRARY_SERVER"` → trả `"LIBRARY_SERVER:" + InetAddress.getLocalHost().getHostAddress()` về đúng `packet.getAddress():getPort()`; chạy thread riêng, có `stop()` | Test `UdpDiscoveryTest`: client gửi broadcast tới `127.0.0.1:8889`, nhận đúng chuỗi trong 2s |
| D4 | A4 Dispatch đầy đủ | `ClientHandler` | Implement toàn bộ bảng 2.3: gọi `LibraryService` (signature mục 3.3), `catch (LibraryException e)` → `new Response(request.getRequestId(), e.getStatus(), e.getMessage(), null)`, `catch (Exception)` → `ERROR`. `DOWNLOAD_EBOOK`: gọi `service.getEbook(bookId, socket.getInetAddress().isSiteLocalAddress())`, trả `byte[]`, cộng dồn `totalBytesSent` | Test `EbookStreamTest`: tải 1 ebook, `byte[].length > 0` |
| D5 | A5 Realtime broadcast | `LibraryServer` implement `EventPublisher`; `ClientHandler.sendEvent` | `sendEvent` phải `synchronized` trên `oos` và gọi `oos.reset()` **trước** `writeObject` + `flush()`. `publish`: `targetUsername == null` → mọi client, ngược lại gửi 1 người. Client chết khi broadcast → `try/catch`, gỡ khỏi registry, không làm chết server | Test `RealtimeBroadcastTest`: 2 client, 1 client mượn sách → client kia nhận `BOOKS_CHANGED` |
| D6 | A6 Multicast | `server/net/MulticastAnnouncementService` | `MulticastSocket`, `joinGroup` không cần; `send(new DatagramPacket(bytes, len, InetAddress.getByName("224.0.0.100"), 9999))` với `ttl = 1`; `announce(title, content)` → tạo `MulticastMessage`, serialize, gửi. Lệnh `ANNOUNCE` → gọi hàm này + `publish(ANNOUNCEMENT)` | Test `MulticastSendTest`: socket joinGroup 224.0.0.100:9999 nhận được message |
| D7 | A7 Server stats | `ServerStats`, `GET_SERVER_STATS` | `onlineClients`, `clientAddresses` (từ registry), `avgPingMs` (đo bằng `System.nanoTime()` khi xử lý `PING`, lưu trung bình 10 lần gần nhất), `totalBytesSent` (cộng `byte[].length` mỗi ebook + kích thước mỗi `Response` gửi đi) | Test `ServerStatsTest`: 2 client online → `onlineClients == 2` |
| D8 | A8 Tác vụ ngầm | `LibraryServer` | `ScheduledExecutorService`: (1) mỗi phút gọi `service.expireReservations()`; (2) mỗi phút gọi `service.checkOverdue()`; (3) schedule riêng tính đúng 00:00 mỗi ngày cho `checkOverdue()`. Bắt mọi exception trong task để scheduler sống | Test `SchedulerTest`: gọi tay `service` với 1 phiếu due hôm qua → sau `checkOverdue()` status `OVERDUE` |
| D9 | A9 Test đồng thời | `src/test/java/com/library/server/` | 10 luồng TCP thật cùng `BORROW` cuốn cuối: đúng 1 `OK`, 9 `OUT_OF_STOCK`; không luồng nào treo; server không chết | `mvnw.cmd -Dtest=ConcurrentBorrowNetworkTest test` xanh, chạy 3 lần liên tiếp đều xanh |
| D10 | A10 Dọn lỗi tuần 2 | — | Chạy lại toàn bộ test; sửa lỗi nhóm báo; merge `feat/server` → `main` | `mvnw.cmd test` xanh trên `main` |
| D11 | A11 TCP Tunnel | `docs/tunnel.md` ngắn 10 dòng | Hướng dẫn Ngrok: `ngrok tcp 8888` → client nhập host/port Ngrok ở `SettingsDialog`; kiểm tra `isSiteLocalAddress()` trả `false` khi qua tunnel → chỉ nhận preview | Chạy thật 1 lần với tunnel, chụp màn hình log |
| D12 | A12 Bền bỉ | `ClientHandler`, `LibraryServer` | Test tay: client tắt đột ngột giữa lúc broadcast, gửi `Request` rác (`Object` lạ), payload thiếu key, payload sai kiểu → server trả `ERROR`, **không** exception thoát ra `main` | Bảng 4 ca ở trên, cả 4 ca server vẫn phục vụ client mới |
| D13 | A13 Hỗ trợ e2e | — | Chạy `EndToEndFullSystemTest` cùng C, sửa phía server | Test e2e xanh |
| D14 | A14 Sửa lỗi tổng | — | Sửa hết lỗi checklist mục 6 phần server | Checklist mục 6: 0 lỗi phía server |
| D15 | A15 Báo cáo | `docs/bao-cao/server.md` | 1 trang: kiến trúc, thread pool, tại sao `oos.reset()`, khóa hạt mịn, kèm ảnh chụp log | Đọc 3 phút hiểu, không cần hỏi |

### 3.2 Người B — Client & Giao diện

| Ngày | Task | File | Việc | Nghiệm thu |
|---|---|---|---|---|
| D1 | B1 Khung client | `client/config/ClientConfig`, `client/net/LibraryClient`, `client/net/RealtimeListener` | `ClientConfig`: đọc/ghi `config.properties` (`server.ip`, `server.port`), để trống → `127.0.0.1:8888`. `LibraryClient`: `connect(host, port)` mở socket, tạo stream **cùng thứ tự với server** (out + flush trước, in sau), 1 thread daemon đọc liên tục, phân biệt `Response` (theo `requestId`) và `RealtimeEvent` → gọi `RealtimeListener.onEvent`. API đồng bộ: `Response send(Request r)` dùng `wait/notify` trên `ConcurrentHashMap<Long, Response>` với timeout 10s. `static boolean testConnection(host, port)` mở socket riêng, gửi `PING`, chờ 2s | Test `ClientConnectTest`: server giả lập (A2 xong) → `login("sv001","123")` trả `OK` |
| D2 | B2 Đăng nhập | `client/ui/SettingsDialog`, `LoginFrame`, `client/ClientMain` | `LoginFrame`: ô Host (placeholder `127.0.0.1`), Port (`8888`), Username, Password, 3 nút `Đăng nhập` / `Kiểm tra kết nối` / `Tự tìm Server trong LAN` + nút `Cài đặt`. Ô để trống khi đăng nhập → dùng mặc định. Ping test đổi `JLabel` "Kết nối OK" (xanh lá) / "Không kết nối được" (đỏ). Login sai → `JOptionPane` hiện `message` từ server. Login đúng → đóng frame, mở dashboard theo `role`. `SettingsDialog` (`JDialog`): 2 ô Host/Port + `Lưu` (ghi `config.properties`) + `Hủy` | Chạy thật: sai mật khẩu báo đúng câu; ping test khi server tắt báo đỏ trong 2s |
| D3 | B3 Tab tra cứu | `client/ui/StudentDashboard` (Tab 1) | `JTextField` tìm kiếm + `JComboBox` thể loại (lấy distinct từ danh sách sách) + `JCheckBox "Chỉ sách còn"` + `JButton "Tìm"`, Enter cũng tìm. `JTable` cột: ID, Tựa, Tác giả, Thể loại, Năm, Còn/Tổng. Chọn 1 dòng → bật nút `Mượn` (hỏi số ngày qua `JSpinner` 1–30, mặc định 14), `Đặt trước`, `Tải E-Book`. Mọi lệnh gọi trong `SwingWorker` (không đóng băng UI). `OUT_OF_STOCK` → `JOptionPane` + tự bật nút `Đặt trước` | Chạy thật: gõ `lap trinh` ra sách; mượn xong số "Còn" giảm 1 ngay không cần bấm lại |
| D4 | B4 Tab mượn / đặt trước | `StudentDashboard` (Tab 2, Tab 3) | Tab2 bảng: Tựa, Mã bản sao, Ngày mượn, Hạn trả, Trạng thái, Gia hạn. Dòng `OVERDUE` tô đỏ chữ trắng. Nút `Trả sách` (hỏi xác nhận, dùng `copyId` dòng đang chọn), `Gia hạn (+7 ngày)` — bị từ chối thì hiện `message` của server. Tab3 bảng: Tựa, Ngày đặt, Hạn nhận, Trạng thái, nút `Hủy đặt trước`; dòng còn `< 6h` tô vàng | Chạy thật: mượn → thấy ở Tab2; trả → biến mất khỏi Tab2 |
| D5 | B5 Realtime & gợi ý | `StudentDashboard` (Tab 4 + listener) | Tab4 bảng sách gợi ý (`GET_RECOMMENDATIONS`). Đăng ký `RealtimeListener`: `BOOKS_CHANGED`/`BORROW_CHANGED` → refresh tab đang mở; `RESERVATION_ALERT` → `JOptionPane` + refresh Tab3; `VIOLATION_BLOCKED` → popup "Tài khoản bị khóa mượn". Không refresh nếu đang có dialog mở | 2 client: client1 mượn → client2 thấy số lượng đổi trong 1s mà không bấm gì |
| D6 | B6 Quản lý sách | `LibrarianDashboard` (Tab 1), `BookDialog`, `QRCodeDialog` | Tab1 `JTable` sách + `JButton` Thêm/Sửa/Xóa/Sửa số bản sao/Xem QR. `BookDialog` (`JDialog`, `GridBagLayout`): các ô text + `JSpinner` số bản sao. Xóa sách đang có bản sao mượn → hiện `message` lỗi của server. Xem bản sao: bảng phụ `copyId, barcode, status`. `QRCodeDialog`: hiện `qrData` trong `JTextArea` (monospace) + nút `Lưu thành .txt` ghi vào `qr/<copyId>.txt` (thư mục chạy của client, **không** ghi vào `data/` của server) | Thêm sách 2 bản → bảng có 3 cột số đúng; xóa sách đang mượn bị chặn |
| D7 | B7 Quản lý mượn trả | `LibrarianDashboard` (Tab 2) | Bảng toàn trường: MSSV, Tựa, Mã bản sao, Mượn, Hạn, Trả, Trạng thái. Ô tìm MSSV (lọc client-side), `JCheckBox "Chỉ quá hạn"`, `JButton` `Làm mới`, `Thu hồi (Trả)` (chọn dòng → `RETURN`). Đếm trên `JLabel`: "Đang mượn: N · Quá hạn: M" | Lọc quá hạn ra đúng số; thu hồi xong bảng tự cập nhật |
| D8 | B8 Tài khoản, nhật ký & xử phạt | `LibrarianDashboard` (Tab 3) | **Trên:** bảng sinh viên (MSSV, Họ tên, Email, Vi phạm, Trạng thái) + `JButton "Thêm sinh viên"` (dialog 4 ô: username/mật khẩu/họ tên/email), `"Đặt lại mật khẩu"`, `"Mở khóa vi phạm"`. **Dưới:** bảng audit: Thời gian, Hành động, Người dùng, Chi tiết (mới nhất trên cùng). Sau mở khóa → dòng cập nhật, ghi audit `UNBLOCK` | Test tay: tạo 3 vi phạm → user hiện "Bị khóa"; mở khóa → "Bình thường"; thêm `sv004` → login được bằng mật khẩu vừa tạo |
| D9 | B9 Giám sát & loa | `LibrarianDashboard` (Tab 4) | Nửa trên: ô Tiêu đề + `JTextArea` nội dung + `JButton "Phát thông báo toàn trường"` (`ANNOUNCE`). Nửa dưới: `JTable` client online (địa chỉ IP:port), `JLabel` "Online: N · Ping TB: X ms · Đã gửi: Y KB", `JButton "Làm mới"` (`GET_SERVER_STATS`), `Timer` 5s tự làm mới | Bấm phát → 2 client khác nhận popup |
| D10 | B10 Loa & quét QR | `client/net/MulticastClientListener`, `StudentDashboard` (ô quét) | `MulticastSocket(9999)` + `joinGroup(224.0.0.100)` trong thread daemon; nhận → `SwingUtilities.invokeLater` hiện `JOptionPane` "THÔNG BÁO THƯ VIỆN: title — content". Lỗi mạng → log, không crash. Ô "Quét mã" trên Tab1: nhận barcode từ máy quét USB (máy quét = bàn phím gõ + Enter) hoặc gõ tay → `QUICK_SCAN` → chọn sẵn đúng dòng trong bảng | Bấm phát từ client thủ thư → client sinh viên hiện popup < 1s; quét barcode chọn đúng sách |
| D11 | B11 Tải E-Book | `StudentDashboard` (nút Tải) | `JFileChooser` chọn chỗ lưu → `DOWNLOAD_EBOOK` → ghi `byte[]` ra file; `JProgressBar` giả lập 3 bước (gửi yêu cầu / nhận dữ liệu / ghi đĩa) trên `SwingWorker`. Xong → `JOptionPane` đường dẫn + `Desktop.open` (bắt exception nếu máy không có trình đọc PDF) | Tải từ localhost ra file mở được bằng trình đọc PDF |
| D12 | B12 Làm gọn giao diện | tất cả `client/ui` | Kiểm tra: không emoji, không màu tuỳ tiện, mọi bảng có tiêu đề cột, mọi nút có tooltip 1 dòng, cửa sổ resize không vỡ (`BorderLayout` + `JScrollPane`), `StudentDashboard` 900x600, `LibrarianDashboard` 1000x650, căn giữa màn hình | Mở 2 cửa sổ ở 1280x720 và 1920x1080, không cắt chữ, không tràn nút |
| D13 | B13 Chạy checklist | — | Chạy mục 6 với 3 client, ghi bug ra `docs/bug-list.md` | Danh sách bug có mã bước (6.x) |
| D14 | B14 Sửa lỗi UI | — | Sửa hết bug B13 của mình | Checklist 6: 0 lỗi phía client UI |
| D15 | B15 Báo cáo | `docs/bao-cao/client.md` | 1 trang: luồng request/response, tại sao dùng `SwingWorker`, ảnh chụp 2 dashboard | Đọc 3 phút hiểu |

### 3.3 Người C — Dữ liệu, Nghiệp vụ, Kiểm thử

| Ngày | Task | File | Việc | Nghiệm thu |
|---|---|---|---|---|
| D1 | C1 Model + tìm kiếm mờ | `common/model/*`, `common/util/FuzzySearchUtil`, `server/service/LibraryException` | `LibraryException extends Exception { ResponseStatus status; + getStatus() }`. Viết đúng bảng 2.1. `FuzzySearchUtil`: `static String normalize(String)` dùng `java.text.Normalizer` NFD + `replaceAll("\\p{InCombiningDiacriticalMarks}+","")` + `đ→d` + lowercase + gộp khoảng trắng; `static int levenshtein(String, String)`; `static boolean matches(String keyword, String... fields)` = chuẩn hoá rồi true nếu 1 field chứa keyword, hoặc tiền tố khớp, hoặc Levenshtein(keyword, từ bất kỳ trong field) `<= 2` | `FuzzySearchTest` (6 case) + `SerializationTest` xanh |
| D2 | C2 Lưu trữ | `server/storage/DataStorage` | 1 `Path dataDir = Path.of("data")`; `load/save` từng `List<T>` bằng `ObjectOutputStream`/`ObjectInputStream` (file rỗng/thiếu → list rỗng). Seed lần đầu (`users.dat` chưa có): `admin/admin123` (LIBRARIAN), `sv001/123`, `sv002/123`, `sv003/123` (STUDENT); 5 tựa sách tiếng Việt có dấu (1 tựa "Lập trình mạng máy tính"), 15 `BookCopy` `BK01-C01`… `BK05-C03`; ghi `data/ebooks/<id>_full.pdf` và `<id>_preview.pdf` (mảng byte PDF 1 trang tối thiểu, preview khác nội dung) | Test `DataStorageTest`: xoá `data/` → chạy load → đúng 5 sách, 15 bản sao, 15 file PDF; load lần 2 không seed lại |
| D3 | C3 Tìm kiếm & gợi ý | `server/service/LibraryService` (phần 1) | `User authenticate(String username, String password)` — sai → `throw new LibraryException(UNAUTHORIZED, "Sai tài khoản hoặc mật khẩu")`. `List<Book> searchBooks(String keyword, String category, boolean onlyAvailable)` dùng `FuzzySearchUtil` (keyword null/rỗng = tất cả). `void recordSearch(...)` ghi `SearchHistoryItem` (giữ tối đa 50/sinh viên). `List<Book> getRecommendations(String studentId)` = 3 thể loại xuất hiện nhiều nhất trong 20 lần tìm gần nhất → sách cùng thể loại chưa mượn, tối đa 5 | `LibraryServiceTest`: `searchBooks("lap trinh mag", null, false)` trả đúng cuốn Lập trình mạng; gợi ý trả đúng thể loại đã tìm |
| D4 | C4 Mượn / trả / khóa | `LibraryService` (phần 2), `LibraryException` | `ConcurrentHashMap<Integer, ReentrantLock> bookLocks` + `getLock(bookId)` (`computeIfAbsent`). `BorrowRecord borrowBook(String studentId, int bookId, int days)`: lock theo bookId, `try/finally unlock`; chặn khi `blocked` → `BLOCKED`, đang mượn `>= maxBorrowQuota` → `QUOTA_EXCEEDED`, có phiếu `OVERDUE` → `BLOCKED "Còn sách quá hạn"`, còn bản sao `AVAILABLE` hay `RESERVED` của chính mình; hết → `OUT_OF_STOCK`. `days` kẹp 1..30. `returnBook(String studentId, String copyId)`: `RETURNED` + `returnDate`; nếu `LocalDate.now() > dueDate` → `violationCount++` + audit `OVERDUE_STRIKE`, `>= 3` → `blocked = true` + event `VIOLATION_BLOCKED`; sau đó **auto-allocation** (D5). Mọi thay đổi ghi `audit` + `storage.save(...)` | `BorrowReturnTest` (8 case) xanh, gồm ca quá hạn lần 3 → blocked |
| D5 | C5 Gia hạn / đặt trước / hết hạn | `LibraryService` (phần 3) | `BorrowRecord renewBook(String studentId, long borrowRecordId)` (+7 ngày): từ chối nếu `OVERDUE`, `renewCount >= 2`, hoặc có `ReservationRecord` `PENDING` cùng bookId → `ERROR` với message riêng; ngược lại `dueDate += 7`, `renewCount++`. `ReservationRecord reserveBook(String studentId, int bookId)`: chỉ khi `availableCopies == 0` (còn hàng → `ERROR "Sách còn trong kho, không cần đặt trước"`); không cho trùng `PENDING` cùng sách. `cancelReservation`. `void expireReservations()`: `PENDING` quá `expiryDate` → `EXPIRED`, nhả bản sao `RESERVED` cho người `PENDING` kế tiếp (FIFO theo `reservationDate`) hoặc về `AVAILABLE`; `HOLD_EXPIRING` 1 lần khi còn `<= 6h`. Hàm `assignReservedCopy(bookId, copyId)` gọi từ `returnBook` (FIFO, `expiryDate = now + 48h`, event `RESERVATION_ALERT`) | Test `RenewReserveTest`: gia hạn bị chặn khi có người chờ; trả sách → người chờ đầu tiên được `RESERVED` + hạn 48h; quá 48h → chuyển người kế |
| D6 | C6 E-Book, audit, CRUD, khóa | `LibraryService` (phần 4) | `byte[] getEbook(int bookId, boolean siteLocal)` đọc `_full.pdf` khi `siteLocal`, ngược lại `_preview.pdf`; ghi audit `EBOOK_DOWNLOAD`. `List<AuditLog> getAuditLogs()`, `List<User> getBlockedUsers()`, `User unblockUser(String username)` (reset `violationCount = 0`, `blocked = false`, audit `UNBLOCK`). `void checkOverdue()`: phiếu `BORROWING` có `dueDate < today` → `OVERDUE`. CRUD: `addBook(title, author, category, year, copies)`, `updateBook`, `deleteBook` (chặn nếu có bản sao không `AVAILABLE`), `addCopy(bookId)`, `removeCopy(copyId)` (chỉ `AVAILABLE`), `getCopies(bookId)`, `getAllBorrows()`, `getAllReservations()`, `getBorrowsByStudent`, `getReservationsByStudent`. Quản lý tài khoản: `addUser(username, password, fullName, email)` (trùng username → `ERROR`), `resetPassword(username, newPassword)`, `getUsers()` | Test `CatalogTest`: xoá sách đang mượn bị chặn; thêm 1 bản sao → copyId nối tiếp đúng; thêm user trùng username bị chặn |
| D7 | C7 Test đồng thời | `src/test/java/com/library/service/` | 10 thread cùng `borrowBook` cuốn cuối: đúng 1 thành công, 9 `OUT_OF_STOCK`; 2 thread mượn 2 sách khác nhau chạy song song **không** chặn nhau (đo thời gian < 2x thời gian 1 lock) | `mvnw.cmd -Dtest=LibraryServiceConcurrencyTest test` xanh, chạy 5 lần đều xanh |
| D8 | C8 Test nghiệp vụ còn lại | `src/test/java/com/library/service/` | Ca biên: hạn mức 3 cuốn, khoá khi blocked, `days = 0` và `days = 99` bị kẹp, gia hạn lần 3 bị chặn, đặt trước khi còn sách bị chặn, huỷ đặt trước nhả chỗ, quá hạn 3 lần → blocked, mở khoá reset | `mvnw.cmd test` xanh toàn bộ; bảng "case → xanh" ghi trong commit message |
| D9 | C9 Script & README | `build.bat`, `run-server.bat`, `run-client.bat`, `README.md` | 3 file `.bat` theo mục 1.1; `README.md`: yêu cầu JDK 17 + Maven, 3 lệnh chạy, tài khoản seed, cách đổi IP/port, cách test LAN (2 máy cùng wifi, lấy IP bằng `ipconfig`), cách tunnel Ngrok | Máy khác (hoặc thư mục clone mới) chạy đúng 3 lệnh trong README là lên hệ thống |
| D10 | C10 Test e2e | `src/test/java/com/library/EndToEndFullSystemTest` | Khởi động server (TCP+UDP+Multicast) trên port test; 2 client TCP thật; chạy 6 bước mục 6 (1,2,4,5,13→ tải ebook,16→ multicast); assert từng bước | `mvnw.cmd -Dtest=EndToEndFullSystemTest test` xanh |
| D11 | C11 Dọn dữ liệu | `DataStorage` | Thêm `resetSeed()` (chỉ dùng trong test) + xoá `data/*.dat` cũ; kiểm tra mở app lần đầu luôn có dữ liệu mẫu | Xoá `data/`, chạy server → seed lại đúng, không lỗi |
| D12 | C12 Sửa lỗi tuần 3 | — | Sửa lỗi nghiệp vụ từ `docs/bug-list.md` | Checklist mục 6: 0 lỗi phía nghiệp vụ |
| D13 | C13 Hỗ trợ test tổng | — | Ngồi cùng A+B chạy 3 client, sửa phía dữ liệu | 3 người chạy hết mục 6 không lỗi |
| D14 | C14 Báo cáo | `docs/bao-cao/nghiep-vu.md` | 1 trang: sơ đồ trạng thái sách/bản sao/phiếu, 5 luồng nghiệp vụ, 3-strike | Đọc 3 phút hiểu |
| D15 | C15 Review & nộp | — | `mvnw.cmd test` lần cuối; dọn `data/` trước khi nộp; zip theo yêu cầu; kiểm tra UTF-8 tiếng Việt trên máy khác | Nộp được, chạy được từ bản zip |

---

## 4. Lịch tổng thể

| Tuần | Mốc phải xong | Ai |
|---|---|---|
| **Tuần 1** (23–29/11) | Hợp đồng chung chốt + TCP login chạy được + toàn bộ nghiệp vụ mượn/trả/gia hạn/đặt trước có test + UI tra cứu & mượn chạy thật | Cả 3 — D5 họp 30 phút, demo nội bộ |
| **Tuần 2** (30/11–06/12) | Đủ 18 tính năng hoạt động trên 2 client (trừ phần báo cáo) | Cả 3 — D10 họp 30 phút, chạy 3 client |
| **Tuần 3** (07–13/12) | Test e2e xanh, checklist mục 6 sạch, báo cáo + slide xong | Cả 3 — D13 chạy thử demo như buổi nộp |

**Luật trượt:** nếu 1 task trượt 1 ngày, cắt phần **làm đẹp** (B12) và **báo cáo chi tiết** — **không** cắt 18 tính năng.

---

## 5. Ánh xạ 18 tính năng → người → task

| # | Tính năng | Người | Task | Kiểm ở mục 6 |
|---|---|---|---|---|
| 1 | Quản lý từng bản sao vật lý | C | D2, D6 | 3 |
| 2 | Gia hạn +7 ngày, tối đa 2 | C/B | C5 / B4 | 9 |
| 3 | Đặt trước FIFO khi hết kho | C | C5 | 10 |
| 4 | Khóa hạt mịn `ReentrantLock` | C | C4, C7 | 11 |
| 5 | Cập nhật số lượng realtime | A/B | A5 / B5 | 12 |
| 6 | Tác vụ ngầm 00:00 | A | A8 | 14 |
| 7 | Audit log + 3-strike | C/B | C4, C6 / B8 | 15 |
| 8 | Fuzzy search không dấu | C | C1, C3 | 7 |
| 9 | Quét mã QR / barcode | B | B10 (quyết định mục 7.1) | 8 |
| 10 | Gợi ý sách theo lịch sử | C/B | C3 / B5 | 13 |
| 11 | Hạn nhận 48h + cảnh báo 6h | C/B | C5 / B4 | 10 |
| 12 | Thread Pool | A | A2 | 16 |
| 13 | Tải E-Book qua socket | A/B/C | A4 / B11 / C6 | 5 |
| 14 | TCP Tunnel (Ngrok) | A | A11 | 18 |
| 15 | UDP Broadcast dò server | A/B | A3 / B2 | 1 |
| 16 | Multicast loa thông báo | A/B | A6 / B10 | 16 |
| 17 | `isSiteLocalAddress()` phân quyền tải | A/C | A4 / C6 | 5 |
| 18 | Giám sát socket/băng thông | A/B | A7 / B9 | 17 |

**Ngoài 18 tính năng, đề bài còn bắt buộc "quản lý tài khoản"** → C6 (`addUser`, `resetPassword`, `getUsers`) + B8 (bảng sinh viên, thêm/đặt lại mật khẩu).

---

## 6. Checklist 18 kịch bản (chạy trước khi nộp — đây là "đúng yêu cầu ban đầu")

Chuẩn bị: xoá `data/`, chạy `run-server.bat`, mở 2 client (`sv001`, `sv002`).

1. Bật server → client bấm `Tự tìm Server trong LAN` → tự điền host, `Kiểm tra kết nối` xanh.
2. Tắt server → `Kiểm tra kết nối` báo đỏ, không treo UI quá 3s.
3. Thủ thư thêm sách "Kỹ thuật lập trình" 2 bản → bảng có "Còn 2/2", copyId `BK06-C01`, `BK06-C02`.
4. Gõ `ky thuat lap trinh` (không dấu, sai 1 ký tự) → vẫn ra sách trên.
5. Chọn sách có E-Book → `Tải E-Book` từ localhost → file mở được (full). Qua Ngrok → chỉ preview.
6. `sv001` mượn 1 cuốn 14 ngày → `availableCopies` giảm 1, Tab2 của `sv001` có dòng mới, `sv002` thấy số lượng đổi ở Tab1 **không cần bấm gì**.
7. 2 client cùng bấm `Mượn` cuốn cuối cùng → đúng 1 người `OK`, người kia nhận "Sách đã hết" và nút `Đặt trước` được bật.
8. Quét barcode `BK01-C02` vào ô quét → bảng tự chọn đúng dòng.
9. `sv001` gia hạn → hạn trả +7 ngày. Gia hạn lần 2 OK, lần 3 bị chặn.
10. `sv002` đặt trước cuốn hết → `sv001` trả cuốn đó → `sv002` nhận popup "được giữ chỗ, hạn 48h", bản sao `RESERVED`.
11. 10 client cùng mượn cuốn cuối (test tự động) → 1 thành công, 9 thất bại, server không chết.
12. Thủ thư phát loa "Thư viện đóng cửa sau 15 phút" → cả 2 client sinh viên hiện popup.
13. Tab4 `sv001` có sách gợi ý cùng thể loại với từ khoá vừa tìm.
14. Đổi ngày hệ thống/tạo phiếu due hôm qua → chạy `checkOverdue()` → phiếu thành `OVERDUE`, dòng Tab2 tô đỏ.
15. Trả trễ 3 lần → `sv001` bị khóa, mượn bị chặn; thủ thư Tab3 thấy trong danh sách khóa, bấm `Mở khóa` → mượn lại được.
16. Mở 50 client test → server vẫn phản hồi < 1s (thread pool, không tạo 50 thread).
17. Thủ thư Tab4 → thấy số client online, ping TB, số KB đã gửi, tăng khi tải ebook.
18. Tunnel Ngrok (hoặc 2 máy cùng wifi): client ở máy khác nhập host:port → login được, mọi thao tác chạy, tải ebook chỉ ra preview.

---

## 7. Hai quyết định lệch so với `SPECIFICATION.md` (ghi vào báo cáo, không tự đổi tiếp)

**7.1 QR không xuất ảnh.** Spec đòi "xuất ảnh mã QR" nhưng cũng đòi "chỉ dùng thư viện chuẩn JDK" — sinh ảnh QR cần ZXing (thư viện ngoài). Giữ tính năng, đổi hình thức: `qrData = "LIB|BK01-C01|BC-BK01-C01"`, hiện dạng text trong `QRCodeDialog`, lưu `.txt`; "quét" = nhập/scan barcode là `copyId`. Đúng bản chất bài học (client-server + xử lý dữ liệu), không cần thư viện ngoài.

**7.2 E-Book không cắt trang PDF.** Spec đòi "gửi preview 5 trang" — cắt PDF cần PDFBox. Thay bằng: server có sẵn 2 file `<bookId>_full.pdf` và `<bookId>_preview.pdf`, chọn theo `InetAddress.isSiteLocalAddress()`. Vẫn là bài toán truyền file qua socket stream + phân quyền theo vị trí mạng.

---

## 8. Rủi ro & cách chặn

| Rủi ro | Chặn |
|---|---|
| Ba người hiểu khác nhau về `Request/Response` → D2 tắc cả nhóm | D1 chốt + tag `contract-v1`; ai lệch thì sửa theo tag, không "sáng tạo" |
| Deadlock stream (`ObjectInputStream` tạo trước) | Ghi thành 1 comment `// FIXME stream order` trong `ClientHandler` + `LibraryClient`; test login phải xanh từ D2 |
| Test đồng thời xanh giả (1 luồng chạy trước) | Dùng `CountDownLatch` cho 10 thread xuất phát cùng lúc; chạy 5 lần |
| Mất 1 người giữa tuần 3 | A/B/C đều có test riêng; task báo cáo (D15) và làm UI (B12) là thứ cắt đầu tiên |
| Tiếng Việt lỗi font trên máy khác | Mọi file UTF-8 không BOM, `.bat` có `chcp 65001` + `-Dfile.encoding=UTF-8`; D15 test trên bản zip |
