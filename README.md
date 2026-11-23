# Hệ thống Quản lý Mượn/Trả Sách Thư viện Trường học (bản đơn giản)

Ứng dụng Java Client/Server qua **TCP Socket**: server đa luồng phục vụ nhiều client,
client là giao diện Swing cho sinh viên và thủ thư. Dữ liệu lưu bằng file nhị phân
`.dat` thuần Java, không dùng database.

Đây là bản rút gọn của [SPECIFICATION.md](SPECIFICATION.md) — chỉ các chức năng chính.
Các tính năng nâng cao (gia hạn, QR, E-Book, UDP tự dò server, Multicast,
audit log, quét quá hạn 00:00…) để làm sau.

## Yêu cầu

- JDK 17 trở lên. Các file `.bat` tự tìm JDK theo thứ tự: `javac` trên PATH →
  `JAVA_HOME` → `C:\Program Files\Java\*` → Android Studio JBR → Eclipse Adoptium.
- Không cần cài Maven: `mvnw.cmd` (Maven Wrapper) tự tải Maven 3.9.9 về `~/.m2`
  ở lần chạy đầu tiên.

## Chạy

```bat
build.bat        :: build vào target\classes
run-server.bat   :: khởi động server, mặc định cổng 8888
run-client.bat   :: mở giao diện đăng nhập
test.bat         :: chạy toàn bộ test JUnit
```

Hoặc gọi Maven trực tiếp: `mvnw.cmd test`, `mvnw.cmd -Dtest=LibraryServiceTest test`.

Chạy server ở cổng khác: `run-server.bat 9000` rồi nhập cổng 9000 ở màn hình đăng nhập.

## Tài khoản mẫu (tự tạo lần đầu)

| Tài khoản | Mật khẩu | Vai trò |
|---|---|---|
| `admin` | `admin123` | Thủ thư |
| `sv001` | `123` | Sinh viên |
| `sv002` | `123` | Sinh viên |
| `sv003` | `123` | Sinh viên |

## Chức năng hiện có

- Đăng nhập, phân quyền sinh viên / thủ thư qua TCP.
- Sinh viên: tìm sách (theo tựa, tác giả, thể loại), mượn (1–30 ngày, mặc định 14),
  xem sách đang mượn, trả sách. Chặn mượn khi: quá 3 cuốn, đang có sách quá hạn,
  mượn trùng một cuốn, sách đã hết bản sao.
- Thủ thư: thêm / sửa / xóa tựa sách và số bản sao, xem toàn bộ phiếu mượn.
  Không xóa được sách đang có người mượn, không giảm số bản sao xuống dưới số đang mượn.
- **Đặt trước (FIFO):** khi sách hết bản sao, sinh viên bấm "Đặt trước" để xếp hàng.
  Khi có người trả, bản sao được giữ riêng cho người đặt đầu tiên trong 48 giờ
  (`READY`); họ mượn được dù kho đang 0. Quá hạn nhận thì tự chuyển cho người kế tiếp
  hoặc trả về kho. Sinh viên hủy được phiếu đặt trước của mình. Khi có bản đang giữ
  chỗ, cột "Còn/Tổng" hiện thêm dạng `0/2 (giữ 1)`.
- Tự đánh dấu **QUÁ HẠN** khi quá hạn trả.
- **Realtime:** server đẩy sự kiện tới mọi client đang kết nối mỗi khi có người
  mượn / trả / thêm / sửa / xóa sách; bảng tự cập nhật, không cần bấm "Làm mới".
- Dữ liệu lưu ở `data/users.dat`, `data/books.dat`, `data/borrows.dat`,
  `data/reservations.dat`.

## Kết nối trong LAN

Server lắng nghe trên mọi card mạng. Máy client nhập IP LAN của máy chủ
(ví dụ `192.168.1.10`) và cổng `8888` ở màn hình đăng nhập. Nhớ mở firewall cho cổng 8888.

## Cấu trúc mã nguồn

```
src/main/java/com/library/
  common/model/   User, Book, BorrowRecord, enum...
  common/net/     Request, Response, CommandType
  server/         DataStorage (.dat), LibraryService (nghiệp vụ),
                  LibraryServer (TCP + thread pool), ClientHandler
  client/         LibraryClient (TCP), ui/ (LoginFrame, StudentDashboard,
                  LibrarianDashboard, BookDialog)
src/test/java/com/library/
  service/LibraryServiceTest.java   kiểm tra nghiệp vụ (JUnit 5)
  server/ServerNetworkTest.java     kiểm tra server + client qua TCP thật (JUnit 5)
```

## Ghi chú triển khai

- Hết hạn giữ chỗ được kiểm tra "lười" mỗi khi đọc/ghi phiếu đặt trước (giống cách
  đánh dấu quá hạn), chưa có tác vụ hẹn giờ 00:00.
- `LibraryService` dùng `synchronized` (một khóa chung). Khi cần nhiều sinh viên mượn
  song song thật sự thì đổi sang `ReentrantLock` theo từng `bookId`.
- Client có luồng nền đọc mọi gói tin: `Response` trả cho lệnh đang chờ,
  `RealtimeEvent` đẩy cho listener. Listener chạy trên luồng nền nên giao diện phải
  bọc `SwingUtilities.invokeLater` (đã làm sẵn trong 2 dashboard).
- Lệnh mạng vẫn chạy trên luồng giao diện nên với mạng LAN chậm có thể đứng giao diện
  trong lúc chờ; chuyển sang luồng nền khi cần.
- Chưa có session/token: mỗi lệnh tin vào `username` client gửi lên. Đủ cho demo,
  không đủ cho môi trường thật.
- Dùng Maven Wrapper + JUnit 5: `mvnw.cmd test` chạy toàn bộ test, `mvnw.cmd -Dtest=ServerNetworkTest test` chạy riêng một lớp. Mỗi lớp test gom hết lỗi rồi báo một lần nên một lượt chạy thấy đủ danh sách hỏng.
