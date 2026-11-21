# BẢN ĐẶC TẢ KỸ THUẬT VÀ KIẾN TRÚC HỆ THỐNG (CẬP NHẬT ĐẦY ĐỦ)
## Đề tài: Hệ thống Quản lý Mượn/Trả Sách Thư viện Trường học (Remote School Library Management)
**Học phần:** Lập trình Mạng Máy tính (Computer Network Programming)

---

## 1. TỔNG QUAN HỆ THỐNG & MỤC TIÊU
- **Tên hệ thống:** Remote School Library Management System.
- **Mục tiêu:** Xây dựng hệ thống quản lý mượn/trả sách, quản lý tài khoản, tựa sách và từng bản sao vật lý từ xa theo mô hình Khách/Chủ (Client/Server) hoạt động trên nền tảng mạng máy tính (LAN / Wi-Fi / Localhost).
- **Phạm vi nghiệp vụ toàn diện:** Quản lý tài khoản, tựa sách và từng bản sao (copies), tìm kiếm, mượn, trả, **gia hạn (renew)**, **đặt trước (reserve)**, kiểm tra số lượng khả dụng realtime, **tính quá hạn (overdue)** và **xử lý an toàn đa luồng đồng thời (concurrent thread-safe)**.
- **Nền tảng công nghệ:** Java thuần (JDK 17+ hoặc JDK 21+), Java TCP Socket (`ServerSocket` & `Socket`), Java Object Serialization, Java Swing GUI, không phụ thuộc framework web hay cơ sở dữ liệu bên ngoài.

---

## 2. KIẾN TRÚC MẠNG & MÔ HÌNH HOẠT ĐỘNG
### 2.1. Mô hình Client/Server & Multithreading
- **Server:**
  - Mở cổng `ServerSocket` (mặc định: `8888`) lắng nghe kết nối đến từ các Client.
  - Áp dụng mô hình **Concurrent Server (Đa luồng)**: Vòng lặp `serverSocket.accept()` tiếp nhận kết nối và bàn giao mỗi Client cho một tiểu trình `ClientHandler` (thực thi `Runnable`).
  - Quản lý danh sách kết nối đang hoạt động: `CopyOnWriteArrayList<ClientHandler> activeClients` để đảm bảo an toàn đa luồng khi duyệt broadcast.
  - Ghi log thời gian thực chi tiết trên Console Server: IP/Port client, thời điểm kết nối, tài khoản đăng nhập, mọi thao tác nghiệp vụ (Mượn, Trả, Gia hạn, Đặt trước, CRUD sách/bản sao), thời điểm ngắt kết nối và số Client đang online.
- **Client:**
  - Khởi tạo kết nối `Socket(host, port)` tới Server.
  - Sử dụng **Persistent Connection**: Duy trì kết nối xuyên suốt phiên làm việc.
  - Kiến trúc Client chia 2 luồng:
    1. **Luồng giao diện (UI Event Dispatch Thread):** Xử lý tương tác nút bấm, form nhập, bảng hiển thị không bị nghẽn (non-blocking).
    2. **Luồng ngầm (Background Listener Thread):** Liên tục lắng nghe gói tin từ Server (`in.readObject()`) để nhận kết quả phản hồi và bắt các sự kiện Realtime Broadcast.

### 2.2. Vị trí & Cấu hình IP/Port
- **Mặc định:** `Host = 127.0.0.1`, `Port = 8888`.
- **Cấu hình động:**
  - Lưu trữ trong tệp `config.properties` ở thư mục chạy ứng dụng.
  - Trên màn hình Đăng nhập có nút **⚙ Cài đặt kết nối (Settings)**: Có 2 ô nhập Host và Port với placeholder hiển thị `127.0.0.1` và `8888`.
  - **Quy tắc:** Nếu để trống hoặc xóa sạch ô nhập, hệ thống tự động quay về giá trị mặc định (`127.0.0.1:8888`). Khi nhập giá trị mới và lưu lại, Client sẽ kết nối theo địa chỉ mới.
  - Tích hợp nút **"Kiểm tra kết nối" (Ping Test)** để kiểm tra trạng thái Server trước khi đăng nhập.

---

## 3. CÁC THỰC THỂ DỮ LIỆU CỐT LÕI (CORE ENTITIES)
Tất cả các thực thể đều cài đặt giao diện `java.io.Serializable` kèm `serialVersionUID`:

1. **`User` (Tài khoản người dùng):**
   - `username` (Mã sinh viên hoặc mã thủ thư), `password`, `fullName`, `role` (`LIBRARIAN` hoặc `STUDENT`), `maxBorrowQuota` (mặc định 3 cuốn).
2. **`Book` (Tựa sách / Danh mục):**
   - `id`, `isbn`, `title`, `author`, `category`, `publishYear`.
   - `totalCopies` (tổng số bản sao vật lý), `availableCopies` (số bản sao đang sẵn sàng cho mượn).
3. **`BookCopy` (Bản sao sách vật lý):**
   - `copyId` (ví dụ: `BK01-C01`, `BK01-C02`), `bookId`, `status` (`AVAILABLE`, `BORROWED`, `RESERVED`, `MAINTENANCE`).
4. **`BorrowRecord` (Phiếu mượn / trả):**
   - `id`, `copyId`, `bookId`, `bookTitle`, `studentId`, `borrowDate`, `dueDate`, `returnDate`.
   - `status` (`BORROWING`, `RETURNED`, `OVERDUE`).
   - `renewCount` (số lần đã gia hạn, tối đa 2 lần).
5. **`ReservationRecord` (Phiếu đặt trước sách):**
   - `id`, `bookId`, `bookTitle`, `studentId`, `reservationDate`.
   - `status` (`PENDING`, `FULFILLED`, `CANCELLED`).
   - `assignedCopyId` (bản sao được giữ chỗ khi có người trả sách).

---

## 4. CHI TIẾT CÁC NGHIỆP VỤ HỆ THỐNG (BUSINESS LOGIC)

### 4.1. Nghiệp vụ Quản lý Sách & Bản sao (Books & Copies)
- **Thủ thư:**
  - Thêm tựa sách mới kèm số lượng bản sao ban đầu (hệ thống tự động sinh các `BookCopy` tương ứng: `BK{id}-C01`, `BK{id}-C02`...).
  - Thêm bản sao lẻ cho một tựa sách đã có.
  - Sửa thông tin tựa sách (tên, tác giả, thể loại, năm XB).
  - Xóa bản sao (chỉ cho phép xóa bản sao ở trạng thái `AVAILABLE`).
  - Xóa tựa sách (chỉ cho phép xóa khi toàn bộ các bản sao đều đang `AVAILABLE`, không có ai đang mượn hay đặt trước).
- **Sinh viên & Thủ thư:**
  - Xem danh sách sách kèm số lượng khả dụng (`availableCopies / totalCopies`).
  - Tìm kiếm sách theo từ khóa (tên sách, tác giả, thể loại) kết hợp bộ lọc (Tất cả / Chỉ sách còn khả dụng).
  - Xem chi tiết danh sách từng bản sao và trạng thái của từng bản sao.

### 4.2. Nghiệp vụ Mượn sách (Borrow)
- **Luồng xử lý:**
  1. Kiểm tra tài khoản sinh viên:
     - Chưa đạt giới hạn mượn tối đa (`currentBorrowingCount < maxBorrowQuota`).
     - Không có cuốn sách nào đang trong tình trạng **Quá hạn (OVERDUE)** (nếu có sách quá hạn -> bắt buộc phải trả trước khi được mượn tiếp).
  2. Kiểm tra sách:
     - Nếu sinh viên đã có phiếu đặt trước đang ở trạng thái giữ chỗ (`RESERVED`) cho cuốn sách này -> Lấy đúng bản sao `assignedCopyId`.
     - Nếu không, kiểm tra `availableCopies > 0`, chọn 1 bản sao có trạng thái `AVAILABLE`.
  3. Cập nhật trạng thái bản sao thành `BORROWED`.
  4. Tạo `BorrowRecord`:
     - Ngày mượn: ngày hiện tại.
     - Ngày hẹn trả: ngày hiện tại + số ngày mượn (sinh viên chọn từ 1 đến 30 ngày, mặc định 14 ngày).
     - `renewCount = 0`, `status = BORROWING`.
  5. Cập nhật `availableCopies` của `Book`, ghi lưu dữ liệu, kích hoạt `broadcastDataChanged()`.

### 4.3. Nghiệp vụ Trả sách (Return)
- **Luồng xử lý:**
  1. Xác định `BorrowRecord` cần trả và bản sao `BookCopy` tương ứng.
  2. Cập nhật `BorrowRecord`: `returnDate = currentDate`, `status = RETURNED`.
  3. Kiểm tra hàng đợi Đặt trước (**Reservation Queue** - FIFO) của tựa sách này:
     - **Nếu có sinh viên đang chờ đặt trước (`status == PENDING`):**
       - Lấy phiếu đặt trước lâu nhất của sách này.
       - Chuyển trạng thái bản sao `BookCopy` thành `RESERVED`.
       - Gắn `assignedCopyId` vào phiếu đặt trước, chuyển trạng thái phiếu thành `FULFILLED` (sẵn sàng cho người đặt mượn).
       - Phát thông báo Realtime riêng tới sinh viên được giữ chỗ.
     - **Nếu không có ai đặt trước:**
       - Chuyển trạng thái bản sao `BookCopy` về `AVAILABLE`.
       - Tăng `availableCopies` của `Book` lên 1.
  4. Ghi lưu dữ liệu, kích hoạt `broadcastDataChanged()`.

### 4.4. Nghiệp vụ Gia hạn sách (Renew)
- **Luồng xử lý:**
  1. Sinh viên chọn cuốn sách đang mượn và gửi yêu cầu Gia hạn.
  2. Kiểm tra các điều kiện an toàn:
     - Sách **chưa bị quá hạn** (`status != OVERDUE` và `dueDate >= currentDate`).
     - Số lần gia hạn chưa vượt quá mức cho phép (`renewCount < 2`).
     - Tựa sách này **không có ai đang đặt trước** trong hàng chờ (`PENDING`), ưu tiên người đang xếp hàng.
  3. Nếu đủ điều kiện:
     - Tăng hạn trả thêm 7 ngày: `dueDate = dueDate.plusDays(7)`.
     - Tăng số lần gia hạn: `renewCount++`.
  4. Ghi lưu dữ liệu và thông báo gia hạn thành công.

### 4.5. Nghiệp vụ Đặt trước sách (Reserve / Hold)
- **Luồng xử lý:**
  1. Khi một tựa sách **hết bản sao khả dụng** (`availableCopies == 0`), sinh viên có thể chọn chức năng **"Đặt trước"**.
  2. Kiểm tra điều kiện:
     - Sinh viên không đang mượn cuốn sách này.
     - Sinh viên chưa có phiếu đặt trước nào đang chờ (`PENDING`) cho cuốn sách này.
  3. Tạo `ReservationRecord` với `status = PENDING`, thời gian đặt = hiện tại.
  4. Hỗ trợ sinh viên thao tác **"Hủy đặt trước" (Cancel Reservation)** nếu không còn nhu cầu.

### 4.6. Nghiệp vụ Tính toán Quá hạn (Overdue Calculation)
- Mỗi khi truy vấn danh sách phiếu mượn hoặc khi Client kết nối:
  - Hệ thống tự động so sánh `dueDate` với `currentDate`.
  - Nếu `currentDate > dueDate` và `status == BORROWING` -> Tự động chuyển thành `OVERDUE`.
- Trên giao diện Client:
  - Phiếu mượn quá hạn hiển thị màu đỏ cảnh báo nổi bật kèm số ngày trễ hạn.
  - Sinh viên bị khóa chức năng mượn sách mới cho đến khi hoàn trả sách quá hạn.

---

## 5. XỬ LÝ ĐỒNG BỘ HÓA (SYNCHRONIZATION) & AN TOÀN ĐA LUỒNG
- **Nguy cơ tranh chấp (Race Condition):**
  - Nhiều sinh viên cùng bấm mượn cuốn sách/bản sao cuối cùng tại cùng 1 tích tắc.
  - Vừa có người trả sách, vừa có người bấm mượn và người bấm đặt trước đồng thời.
- **Giải pháp:**
  - Toàn bộ các phương thức thay đổi trạng thái trong `LibraryService` đều được đồng bộ hóa với từ khóa `synchronized`:
    - `public synchronized Response borrowBook(...)`
    - `public synchronized Response returnBook(...)`
    - `public synchronized Response renewBook(...)`
    - `public synchronized Response reserveBook(...)`
    - `public synchronized Response cancelReservation(...)`
    - `public synchronized Response addBook/updateBook/deleteBook(...)`
  - Đảm bảo tính nguyên tử (Atomicity): Không bao giờ xảy ra tình trạng âm kho, tranh chấp bản sao hoặc sai lệch hàng đợi đặt trước.

---

## 6. GIAO THỨC TRUYỀN THÔNG & REALTIME BROADCAST
### 6.1. Gói tin Mạng (Data Packets)
- **`CommandType`:**
  - `LOGIN`, `LOGOUT`, `PING`
  - `GET_BOOKS`, `GET_BOOK_COPIES`, `ADD_BOOK`, `UPDATE_BOOK`, `DELETE_BOOK`, `ADD_COPY`, `DELETE_COPY`
  - `BORROW_BOOK`, `RETURN_BOOK`, `RENEW_BOOK`, `RESERVE_BOOK`, `CANCEL_RESERVATION`
  - `GET_MY_BORROWS`, `GET_MY_RESERVATIONS`, `GET_ALL_BORROWS`, `GET_ALL_RESERVATIONS`
- **`Request`:** Chứa `command`, `payload`, `token/username`.
- **`Response`:** Chứa `status` (`SUCCESS`, `FAILED`, `ERROR`), `message`, `data`.
- **`RealtimeEvent`:** Chứa `eventType` (`DATA_CHANGED`, `RESERVATION_AVAILABLE`), `data`.

### 6.2. Tiêu chuẩn Kỹ thuật Luồng Object
1. **Tránh Deadlock khởi tạo:** Luôn tạo `ObjectOutputStream` và `flush()` **TRƯỚC** khi tạo `ObjectInputStream`.
2. **Xóa Cache luồng (`oos.reset()`):** Luôn gọi `oos.reset()` khi gửi dữ liệu cập nhật qua Persistent Connection để tránh Java tái sử dụng tham chiếu cũ trong bộ đệm.
3. **Phát sóng Realtime (Broadcast):** Khi có bất kỳ thay đổi nào về sách, bản sao, phiếu mượn, Server lập tức gửi `RealtimeEvent` tới toàn bộ `activeClients`. Client dùng `SwingUtilities.invokeLater()` để cập nhật trực tiếp bảng `JTable`.

---

## 7. LƯU TRỮ DỮ LIỆU (PERSISTENCE LAYER)
- Sử dụng các tệp tin nhị phân đối tượng `.dat` thuần Java đặt trong thư mục `data/`:
  - `data/users.dat`: Danh sách `User`.
  - `data/books.dat`: Danh sách `Book`.
  - `data/copies.dat`: Danh sách `BookCopy`.
  - `data/borrows.dat`: Danh sách `BorrowRecord`.
  - `data/reservations.dat`: Danh sách `ReservationRecord`.
- **Tự động khởi tạo dữ liệu mẫu (Seed Data):**
  - Nếu tệp chưa tồn tại, hệ thống tự động sinh dữ liệu mẫu:
    - 1 Thủ thư (`admin`/`admin123`).
    - 2 Sinh viên (`sv001`/`123`, `sv002`/`123`).
    - 5 tựa sách nổi bật môn Mạng & CNTT, mỗi tựa sách kèm 2-3 bản sao vật lý.

---

## 8. GIAO DIỆN NGƯỜI DÙNG JAVA SWING
- **Màn hình Đăng nhập:**
  - Form đăng nhập tài khoản/mật khẩu, nút Đăng nhập.
  - Nút **⚙ Cài đặt kết nối** mở hộp thoại cấu hình Host/Port (placeholder `127.0.0.1:8888`), kèm nút **Kiểm tra kết nối**.
- **Dashboard Sinh viên:**
  - Tab 1: **Tra cứu & Mượn sách:** Bảng sách kèm số lượng khả dụng, thanh tìm kiếm từ khóa, bộ lọc; nút **Mượn sách** (chọn hạn trả 1-30 ngày), nút **Đặt trước sách** (khi sách hết).
  - Tab 2: **Sách đang mượn:** Danh sách sách đang giữ, ngày mượn, hạn trả, trạng thái quá hạn (chữ đỏ), nút **Trả sách**, nút **Gia hạn sách (+7 ngày)**.
  - Tab 3: **Sách đã đặt trước:** Danh sách sách đang chờ đặt trước, nút **Hủy đặt trước**.
- **Dashboard Thủ thư:**
  - Tab 1: **Quản lý tựa sách & Bản sao:** Bảng tựa sách, nút Thêm sách, Sửa sách, Xóa sách; bảng chi tiết danh sách bản sao của sách được chọn, nút Thêm bản sao, Xóa bản sao.
  - Tab 2: **Quản lý Mượn / Trả:** Xem toàn bộ phiếu mượn của trường, lọc phiếu quá hạn, tìm kiếm theo mã sinh viên.
  - Tab 3: **Quản lý Đặt trước:** Xem toàn bộ danh sách đặt trước của các sinh viên.

---

## 9. CẤU TRÚC BUILD & SCRIPTS
- Cấu hình chuẩn Maven `pom.xml` (Java 21/17, UTF-8, JUnit 5).
- Scripts Windows:
  - `build.bat`: Chuyển mã `chcp 65001`, biên dịch toàn bộ bằng `javac -encoding UTF-8`.
  - `run-server.bat`: Chạy Server với cờ `-Dfile.encoding=UTF-8`.
  - `run-client.bat`: Mở Client Swing với cờ `-Dfile.encoding=UTF-8`.
