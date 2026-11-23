# Hệ thống Quản lý Mượn/Trả Sách Thư viện Trường học

Ứng dụng Java Client/Server qua TCP Socket, xây dựng theo kế hoạch 3 tuần cho 3 thành viên.

- Đặc tả đầy đủ (18 tính năng): [SPECIFICATION.md](SPECIFICATION.md)
- Kế hoạch chia việc & hợp đồng dùng chung: [docs/ke-hoach-chia-viec-3-tuan.md](docs/ke-hoach-chia-viec-3-tuan.md)

## Trạng thái hiện tại (D1 — Hợp đồng dùng chung)

Đã hoàn tất tầng hợp đồng dùng chung (`common/model`, `common/net`, `common/util`, `LibraryException`) và các test đơn vị JUnit 5 (`SerializationTest`, `FuzzySearchTest`). Demo tạm ban đầu đã được gỡ bỏ để triển khai theo đúng hợp đồng mới từ D2 trở đi.

## Yêu cầu & Lệnh chạy

- JDK 17 trở lên (`find-java.bat` tự tìm `javac` trên PATH, `JAVA_HOME`, `C:\Program Files\Java\*`, Android Studio JBR, hoặc Eclipse Adoptium).
- Không cần cài Maven trước: dùng Maven Wrapper (`mvnw.cmd`).

```bat
mvnw.cmd test    :: biên dịch và chạy toàn bộ test JUnit
test.bat         :: gọi find-java.bat rồi chạy mvnw.cmd test
build.bat        :: biên dịch vào target\classes (bỏ qua test)
```
