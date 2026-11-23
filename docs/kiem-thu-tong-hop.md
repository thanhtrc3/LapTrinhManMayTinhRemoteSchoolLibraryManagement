# Biên Bản Kiểm Thử Tổng Hợp Hệ Thống (3 Client & Server)

- **Thời gian:** Tuần 3 (Chuẩn bị nghiệm thu đồ án)
- **Người tham gia:** 
  - Người A: Server & Mạng
  - Người B: Client & Giao diện
  - Người C: Dữ liệu, Nghiệp vụ, Kiểm thử
- **Môi trường thử nghiệm:** 1 Server TCP/UDP/Multicast (port 8888, 8889, 9999) + 3 Client hoạt động đồng thời (1 Thủ thư `admin`, 2 Sinh viên `sv001`, `sv002`).

---

## Kết Quả Kiểm Tra 18 Kịch Bản Mục 6

| STT | Kịch bản kiểm thử | Mô tả thực hiện | Kết quả thực tế | Đánh giá |
|---|---|---|---|---|
| **6.1** | Dò tìm Server trong LAN | Bật server, client bấm "Tự tìm Server" | Nhận diện đúng IP Server nội bộ qua UDP Broadcast | **Đạt** |
| **6.2** | Ngắt kết nối Server | Tắt server, client kiểm tra kết nối | Báo trạng thái mất kết nối không treo UI | **Đạt** |
| **6.3** | Thêm sách mới & bản sao | Thủ thư thêm sách 2 bản sao | Sinh mã `BK06-C01`, `BK06-C02` khả dụng đúng | **Đạt** |
| **6.4** | Đăng nhập tài khoản | Sinh viên `sv001` và `sv002` đăng nhập | Vào đúng Dashboard sinh viên, tải dữ liệu đầy đủ | **Đạt** |
| **6.5** | Tra cứu & Tìm kiếm mờ | Tìm "lap trinh mag", "toan roi rac" | Khớp không dấu và Levenshtein <= 2 chính xác | **Đạt** |
| **6.6** | Mượn sách trực tuyến | `sv001` mượn sách ID 1 | Số lượng giảm ngay trên cả 2 màn hình sinh viên | **Đạt** |
| **6.7** | Giới hạn hạn mức 3 cuốn | `sv001` mượn cuốn thứ 4 | Bị từ chối với thông báo `QUOTA_EXCEEDED` | **Đạt** |
| **6.8** | Gia hạn thời gian mượn | `sv001` bấm gia hạn (+7 ngày) | Hạn trả tăng 7 ngày, tăng `renewCount` lên 1 | **Đạt** |
| **6.9** | Giới hạn 2 lần gia hạn | Gia hạn lần 3 | Server chặn với thông báo đã đạt giới hạn tối đa | **Đạt** |
| **6.10** | Đặt trước khi hết kho (FIFO) | 3 sinh viên mượn hết bản sao, sv thứ 4 đặt trước | Phiếu chuyển sang `PENDING` thành công | **Đạt** |
| **6.11** | Tự động phân phối bản sao | Người mượn trả sách | Bản sao tự động gán `RESERVED` 48h cho người chờ | **Đạt** |
| **6.12** | Quét mã vạch / QR | Nhập mã `BAR-BK01-C01` | Tự động định vị và chọn đúng bản sao trên bảng | **Đạt** |
| **6.13** | Tải tài liệu E-Book | Tải PDF qua Socket | Client nhận đầy đủ byte stream và mở đọc được | **Đạt** |
| **6.14** | Phân quyền mạng E-Book | Kiểm tra qua IP nội bộ và Tunnel | Nội bộ nhận bản Full, Tunnel nhận bản Preview | **Đạt** |
| **6.15** | Phát thông báo Multicast | Thủ thư phát thông báo toàn trường | Toàn bộ client sinh viên nhận popup tức thì | **Đạt** |
| **6.16** | Giám sát hiệu năng server | Thủ thư mở tab giám sát | Hiển thị chính xác số client online và dung lượng | **Đạt** |
| **6.17** | Phạt vi phạm trả quá hạn | Trả sách trễ hạn | Tăng `violationCount` và ghi log kiểm toán | **Đạt** |
| **6.18** | Khóa tài khoản 3-strike | Vi phạm quá hạn 3 lần | Tự động khóa mượn, mở khóa bằng quyền thủ thư | **Đạt** |

---
**Kết luận:** Hệ thống vượt qua 18/18 kịch bản kiểm thử, đáp ứng 100% yêu cầu kỹ thuật và nghiệp vụ.
