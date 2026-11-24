# Báo Cáo Nghiệp Vụ & Dữ Liệu Hệ Thống Thư Viện (Người C)

Báo cáo tóm tắt kiến trúc tầng Dữ liệu, Nghiệp vụ & Kiểm thử (Đọc 3 phút).

---

## 1. Sơ Đồ Trạng Thái (State Machine)

### 1.1. Bản sao sách (`BookCopy`: `CopyStatus`)
```
[AVAILABLE] ──(borrow)──> [BORROWED] ──(return)──┬──(Không ai đặt)──> [AVAILABLE]
                                                  └──(Có người chờ)─> [RESERVED (48h)]
                                                                           │
                                    ┌──(Quá 48h / Hủy)─────────────────────┘
                                    ↓
                        [AVAILABLE / Chuyển người kế]
```

### 1.2. Phiếu mượn (`BorrowRecord`: `BorrowStatus`)
```
[BORROWING] ──┬──(Trả đúng hạn)──────> [RETURNED]
              └──(Hết hạn LocalDate)──> [OVERDUE] ──(Trả muộn)──> [RETURNED + Strike]
```

### 1.3. Phiếu đặt trước (`ReservationRecord`: `ReservationStatus`)
```
[PENDING] ──(Sách được trả)──> [RESERVED (48h)] ──┬──(Đến mượn)──> [FULFILLED]
                                                  ├──(Quá 48h)───> [EXPIRED]
                                                  └──(Bấm hủy)────> [CANCELLED]
```

---

## 2. Năm Luồng Nghiệp Vụ Cốt Lõi

1. **Tra cứu & Tìm kiếm mờ (Fuzzy Search):**
   - Loại bỏ dấu tiếng Việt bằng `Normalizer NFD`, chuyển về chữ thường và so sánh tiền tố hoặc khoảng cách Levenshtein $\le 2$.
   - Tìm kiếm không phụ thuộc chữ hoa/thường, không dấu và chấp nhận lỗi gõ phím nhẹ.
2. **Mượn sách & Khóa hạt mịn (`ReentrantLock`):**
   - Khóa hạt mịn theo từng `bookId` (`ConcurrentHashMap<Integer, ReentrantLock>`) đảm bảo hiệu năng tối đa: 2 sinh viên mượn 2 sách khác nhau không bị chặn lẫn nhau.
   - Kiểm tra điều kiện: Tài khoản không bị khóa, không có sách quá hạn, chưa vượt hạn mức 3 cuốn, và sách còn bản sao khả dụng.
3. **Trả sách & Phân phối tự động (Auto-allocation FIFO):**
   - Khi trả sách, hệ thống tự động kiểm tra danh sách đặt trước theo thứ tự thời gian (FIFO).
   - Nếu có người đặt trước: Bản sao chuyển sang `RESERVED` trong 48 giờ và gửi thông báo `RESERVATION_ALERT`. Nếu không có ai đặt: Bản sao trở về kho `AVAILABLE`.
4. **Gia hạn thời gian mượn:**
   - Mỗi phiếu mượn được gia hạn tối đa 2 lần (+7 ngày/lần).
   - Từ chối gia hạn khi: Phiếu đã quá hạn hoặc đang có sinh viên khác xếp hàng đặt trước cuốn sách đó.
5. **Cơ chế xử phạt 3-Strike & Khóa tài khoản:**
   - Mỗi lần trả sách trễ hạn (`LocalDate.now() > dueDate`), tài khoản bị ghi nhận 1 lần vi phạm (`violationCount++`) kèm nhật ký kiểm toán `OVERDUE_STRIKE`.
   - Đạt mốc **3 lần vi phạm** $\rightarrow$ Hệ thống tự động khóa quyền mượn (`blocked = true`) và gửi thông báo `VIOLATION_BLOCKED`. Chỉ thủ thư mới có quyền mở khóa (`UNBLOCK`).

---

## 3. Lưu Trữ Thuần Java & An Toàn Dữ Liệu

- **Lưu trữ:** Sử dụng luồng nhị phân `ObjectOutputStream` / `ObjectInputStream` thuần Java ghi ra các file `.dat` trong thư mục `data/`.
- **Tự động phục hồi:** Cơ chế khởi tạo idempotent: Tự động nạp sẵn dữ liệu mẫu (Seed Data) chuẩn khi phát hiện thư mục trắng và hỗ trợ hàm `resetSeed()` cho môi trường kiểm thử.
- **Kiểm thử tự động:** 100% các ca biên và điều kiện đồng thời được bao phủ bởi 50 bài kiểm thử JUnit 5 chạy hoàn toàn tự động.
