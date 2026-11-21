# BẢN ĐẶC TẢ KỸ THUẬT VÀ KIẾN TRÚC HỆ THỐNG
## Đề tài: Hệ thống Quản lý Mượn/Trả Sách Thư viện Trường học (Remote School Library Management)
**Học phần:** Lập trình Mạng Máy tính (Computer Network Programming)

---

## 1. TỔNG QUAN HỆ THỐNG & MỤC TIÊU
- **Tên hệ thống:** Remote School Library Management System.
- **Mục tiêu:** Xây dựng hệ thống quản lý mượn/trả sách từ xa theo mô hình Khách/Chủ (Client/Server) chạy trên nền tảng mạng máy tính (LAN / Wi-Fi / Localhost).
- **Ngôn ngữ phát triển:** Java thuần (JDK 17+ hoặc JDK 21+), không phụ thuộc framework web, tuân thủ đúng nội dung chuẩn của môn học.

---

## 2. KIẾN TRÚC MẠNG & MÔ HÌNH HOẠT ĐỘNG
### 2.1. Mô hình Client/Server & Multithreading
- **Server:**
  - Mở cổng `ServerSocket` (mặc định: `8888`) lắng nghe kết nối đến từ các Client.
  - Áp dụng mô hình **Concurrent Server (Đa luồng)**: Vòng lặp `serverSocket.accept()` tiếp nhận kết nối và bàn giao mỗi Client cho một tiểu trình `ClientHandler` (thực thi `Runnable`).
  - Quản lý danh sách kết nối đang hoạt động: `List<ClientHandler> activeClients` được bảo vệ an toàn luồng (`CopyOnWriteArrayList` hoặc `Collections.synchronizedList`).
  - Ghi log thời gian thực chi tiết trên Console Server: IP/Port client, thời điểm kết nối, thông tin tài khoản đăng nhập, hành động mượn/trả/sửa, thời điểm ngắt kết nối và tổng số client đang online.
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
  - Trên màn hình có mục **Cài đặt kết nối (Settings)**: Có 2 ô nhập Host và Port với placeholder hiển thị `127.0.0.1` và `8888`.
  - **Quy tắc:** Nếu để trống hoặc xóa sạch ô nhập, hệ thống tự động quay về giá trị mặc định (`127.0.0.1:8888`). Khi nhập giá trị mới và lưu lại, Client sẽ kết nối theo địa chỉ mới.

---

## 3. GIAO THỨC TRUYỀN THÔNG & TUẦN TỰ HÓA ĐỐI TƯỢNG (SERIALIZATION)
### 3.1. Cấu trúc Gói tin (Data Envelope)
Client và Server trao đổi trực tiếp thông qua các Đối tượng Java (POJO) cài đặt giao diện `java.io.Serializable`:
- **`Request`**:
  - `command` (Enum / String): `LOGIN`, `LOGOUT`, `GET_BOOKS`, `BORROW_BOOK`, `RETURN_BOOK`, `ADD_BOOK`, `UPDATE_BOOK`, `DELETE_BOOK`, `GET_USER_BORROWS`, `GET_ALL_BORROWS`.
  - `payload` (Object): Dữ liệu truyền đi kèm (thông tin đăng nhập, đối tượng sách, mã phiếu mượn, số ngày mượn...).
- **`Response`**:
  - `status`: `SUCCESS`, `FAILED`, `ERROR`.
  - `message`: Chuỗi thông báo kết quả trả lời người dùng.
  - `data` (Object): Dữ liệu kết quả truy vấn (danh sách sách, đối tượng User, danh sách phiếu mượn...).
- **`RealtimeEvent`**:
  - `eventType`: `BOOK_DATA_CHANGED`, `NOTIFICATION`.
  - `data`: Danh sách sách mới nhất hoặc thông báo hệ thống để Client tự động cập nhật lại UI.

### 3.2. Tiêu chuẩn Kỹ thuật Bắt buộc
1. **Tránh Deadlock lúc khởi tạo luồng Stream:**
   - Cả Client và Server đều khởi tạo `ObjectOutputStream` và gọi `flush()` **TRƯỚC** khi khởi tạo `ObjectInputStream`.
2. **Xóa Cache luồng đối tượng (`oos.reset()`):**
   - Trên kết nối duy trì (Persistent Connection), luôn gọi `oos.reset()` trước/sau mỗi lần gửi `writeObject()`. Điều này triệt tiêu hoàn toàn lỗi Java tái sử dụng tham chiếu cũ trong bộ đệm ngầm, đảm bảo dữ liệu cập nhật (số lượng sách, trạng thái) được gửi đi đúng thực tế.

---

## 4. XỬ LÝ ĐỒNG BỘ HÓA (SYNCHRONIZATION) & TRÁNH TRANH CHẤP TÀI NGUYÊN
- **Vấn đề tranh chấp (Race Condition):** Khi 2 hay nhiều sinh viên cùng bấm "Mượn" một cuốn sách duy nhất còn lại trong kho ở cùng một phần nghìn giây.
- **Giải pháp:** 
  - Toàn bộ phương thức thao tác thay đổi kho sách (`borrowBook`, `returnBook`, `addBook`, `updateBook`, `deleteBook`) trên Server được đặt trong khối đồng bộ hóa:
    ```java
    public synchronized Response handleBorrowBook(String studentId, int bookId, int daysToBorrow) {
        // 1. Kiểm tra giới hạn mượn của sinh viên (tối đa N cuốn đang mượn chưa trả)
        // 2. Kiểm tra số lượng tồn kho khả dụng (availableQuantity > 0)
        // 3. Trừ số lượng sách: availableQuantity--
        // 4. Tạo phiếu mượn mới (BorrowRecord) và lưu vào file
        // 5. Kích hoạt Server.broadcastDataChanged()
        // 6. Trả lời kết quả cho Client hiện tại
    }
    ```
  - Đảm bảo tính nhất quán (Atomicity & Consistency): Không bao giờ xảy ra tình trạng số lượng sách bị âm hoặc 2 người mượn vượt quá số lượng trong kho.

---

## 5. CƠ CHẾ CẬP NHẬT THỜI GIAN THỰC (REALTIME BROADCAST)
- Khi xảy ra bất kỳ thao tác thay đổi dữ liệu nào thành công (Thủ thư thêm/sửa/xóa sách; Sinh viên mượn/trả sách):
  1. Server lập tức gọi hàm `broadcast(new RealtimeEvent(BOOK_DATA_CHANGED, updatedBooks))`.
  2. Vòng lặp duyệt qua toàn bộ các `ClientHandler` trong `activeClients` và ghi gói tin ra Socket.
  3. Phía Client, `Background Listener Thread` bắt được gói tin `RealtimeEvent`, bọc trong `SwingUtilities.invokeLater()` để cập nhật trực tiếp dữ liệu lên bảng `JTable`.
  4. Người dùng tại các máy khác nhìn thấy số lượng và trạng thái sách thay đổi ngay tức thì mà không cần bấm F5 hay tải lại trang.

---

## 6. LƯU TRỮ DỮ LIỆU (PERSISTENCE LAYER)
- Sử dụng hoàn toàn tệp nhị phân tuần tự hóa thuần Java:
  - `data/books.dat`: Lưu trữ danh sách đối tượng `Book` (ID, tiêu đề, tác giả, thể loại, năm xuất bản, tổng số lượng, số lượng khả dụng).
  - `data/users.dat`: Lưu trữ danh sách đối tượng `User` (Username, Password, FullName, Role: `LIBRARIAN` hoặc `STUDENT`, QuotaLimit).
  - `data/borrows.dat`: Lưu trữ danh sách đối tượng `BorrowRecord` (ID, BookID, BookTitle, StudentID, BorrowDate, DueDate, ReturnDate, Status: `BORROWED`, `RETURNED`, `OVERDUE`).
- **Khởi tạo dữ liệu mẫu (Seed Data):** Nếu các file `.dat` chưa tồn tại trong lần đầu chạy Server, hệ thống tự động tạo thư mục `data/` và nạp sẵn tài khoản mẫu (`admin/admin123`, `sv001/123`, `sv002/123`) cùng 10 đầu sách mẫu phong phú.

---

## 7. YÊU CẦU NGHIỆP VỤ & PHÂN QUYỀN
### 7.1. Vai trò Sinh viên (Student / Reader)
- Đăng nhập vào hệ thống bằng Mã sinh viên.
- Tra cứu / Tìm kiếm sách theo từ khóa (tên sách, tác giả, thể loại) với bộ lọc tình trạng (Tất cả / Còn sách).
- Chọn sách và bấm **Mượn sách**:
  - Hộp thoại chọn thời hạn hẹn trả (tùy chỉnh từ 1 đến 30 ngày).
  - Bị chặn mượn nếu sách đã hết hoặc sinh viên đã đạt hạn mức mượn tối đa.
- Xem danh sách sách cá nhân đang mượn (kèm ngày mượn, ngày hẹn trả, trạng thái quá hạn được cảnh báo màu nổi bật).
- Chọn sách đang mượn và bấm **Trả sách**.

### 7.2. Vai trò Thủ thư (Librarian / Admin)
- Đăng nhập quyền Quản trị viên.
- Quản lý kho sách: Thêm sách mới, Sửa thông tin, Xóa sách, Cập nhật số lượng sách nhập kho.
- Giám sát toàn bộ hoạt động: Xem danh sách tất cả các phiếu mượn/trả trong toàn trường.
- Thống kê & Báo cáo: Lọc các sách đang bị mượn quá hạn, tìm kiếm lịch sử mượn theo từng sinh viên.

---

## 8. MÔI TRƯỜNG, MÃ HÓA & KỊCH BẢN CHẠY (SCRIPTS)
- **Hỗ trợ tiếng Việt trọn vẹn:**
  - Toàn bộ mã nguồn viết bằng encoding `UTF-8`.
  - Các tệp script Windows `.bat` đều chạy lệnh chuyển trang mã `chcp 65001`, cấu hình cờ biên dịch `-encoding UTF-8` và cờ máy ảo Java `-Dfile.encoding=UTF-8`.
- **Hệ thống tệp thực thi:**
  - `pom.xml`: Cấu hình Maven chuẩn dự án Java.
  - `build.bat`: Biên dịch toàn bộ source code bằng `javac` thuần vào thư mục `bin/`.
  - `run-server.bat`: Khởi động Server Console trên cổng 8888.
  - `run-client.bat`: Khởi chạy ứng dụng Client giao diện Swing.
