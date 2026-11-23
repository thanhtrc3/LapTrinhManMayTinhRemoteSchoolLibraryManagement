# Danh Sách Kiểm Tra & Khắc Phục Lỗi (Bug List & Resolution Log)

Theo dõi và rà soát lỗi các tầng theo checklist 18 kịch bản (mục 6 trong kế hoạch phân công):

| Mã lỗi | Mô tả vấn đề | Tầng bị ảnh hưởng | Cách khắc phục | Trạng thái |
|---|---|---|---|---|
| **BUG-01** | Trả sách quá hạn nhưng không tăng số lần vi phạm nếu gọi nhiều lần trong cùng ngày | Nghiệp vụ (C) | Kiểm tra `LocalDate.now().isAfter(dueDate)` và tăng `violationCount` trực tiếp trên thực thể User trong `storage` | **Đã sửa & Test xanh** (`BorrowReturnTest`) |
| **BUG-02** | Khóa tài khoản sau 3 lần quá hạn (3-strike) vẫn cho phép gia hạn sách cũ | Nghiệp vụ (C) | Chặn gia hạn khi sách đã `OVERDUE` hoặc user bị `BLOCKED` | **Đã sửa & Test xanh** (`BusinessBoundaryTest`) |
| **BUG-03** | Hai sinh viên cùng mượn cuốn sách cuối cùng bị tranh chấp dữ liệu (Race Condition) | Dữ liệu / Đồng thời (C) | Triển khai `ConcurrentHashMap<Integer, ReentrantLock>` khóa hạt mịn theo từng `bookId`, kết hợp đồng bộ danh sách bản sao | **Đã sửa & Test xanh** (`LibraryServiceConcurrencyTest`) |
| **BUG-04** | Đặt trước sách khi vẫn còn bản sao khả dụng trong kho gây lãng phí tài nguyên | Nghiệp vụ (C) | Kiểm tra `availableCopies > 0` và từ chối với mã lỗi `ERROR` ("Sách còn trong kho, không cần đặt trước") | **Đã sửa & Test xanh** (`RenewReserveTest`) |
| **BUG-05** | Hết hạn giữ chỗ 48h không tự động giải phóng hoặc bàn giao cho người đặt trước kế tiếp | Nghiệp vụ (C) | Hàm `expireReservations()` tự động quét, chuyển trạng thái `EXPIRED` và gọi `assignReservedCopy()` theo thứ tự FIFO | **Đã sửa & Test xanh** (`RenewReserveTest`) |
| **BUG-06** | Xóa tựa sách khi đang có sinh viên mượn bản sao dẫn đến mất liên kết dữ liệu | Dữ liệu (C) | `deleteBook()` kiểm tra trạng thái toàn bộ bản sao, chỉ cho phép xóa khi tất cả bản sao là `AVAILABLE` | **Đã sửa & Test xanh** (`CatalogTest`) |
| **BUG-07** | Nhập số ngày mượn bất hợp lý (`days <= 0` hoặc `days > 30`) | Nghiệp vụ (C) | Kẹp giá trị tự động trong dải hợp lệ 1..30 ngày (mặc định 14 ngày) | **Đã sửa & Test xanh** (`LibraryServiceTest`) |

---
**Kết quả kiểm tra mục 6:** 0 lỗi tồn đọng phía tầng dữ liệu và nghiệp vụ.
