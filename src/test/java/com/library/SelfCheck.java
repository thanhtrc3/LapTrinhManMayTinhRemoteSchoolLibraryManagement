package com.library;

import com.library.common.model.Book;
import com.library.common.model.BorrowRecord;
import com.library.common.model.ReservationRecord;
import com.library.common.model.ReservationStatus;
import com.library.common.model.Role;
import com.library.server.service.LibraryService;
import com.library.server.service.ServiceException;
import com.library.server.storage.DataStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Kiểm tra nhanh nghiệp vụ, không cần server và không cần JUnit.
 * Chạy: test.bat
 */
public class SelfCheck {
    private static int passed;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("library-check");
        LibraryService service = new LibraryService(new DataStorage(dir.toString()));

        check(service.getBooks().size() == 5, "nạp 5 tựa sách mẫu");
        check(service.login("admin", "admin123").getRole() == Role.LIBRARIAN, "đăng nhập thủ thư");
        check(service.login("sv003", "123").getFullName().equals("Lê Minh Châu"), "có tài khoản sinh viên sv003");
        expectFail(() -> service.login("admin", "sai"), "sai mật khẩu bị chặn");

        int available = book(service, 1).getAvailableCopies();
        BorrowRecord record = service.borrow("sv001", 1, 14);
        check(book(service, 1).getAvailableCopies() == available - 1, "mượn làm giảm số bản khả dụng");
        check(record.getDueDate().equals(LocalDate.now().plusDays(14)), "hạn trả = hôm nay + 14 ngày");
        expectFail(() -> service.borrow("sv001", 1, 14), "không cho mượn trùng một cuốn");

        service.borrow("sv001", 2, 14);
        service.borrow("sv001", 3, 14);
        expectFail(() -> service.borrow("sv001", 4, 14), "chặn khi vượt hạn mức 3 cuốn");

        service.returnBook(record.getId(), "sv001");
        check(book(service, 1).getAvailableCopies() == available, "trả sách hoàn lại bản sao");
        expectFail(() -> service.returnBook(record.getId(), "sv001"), "không cho trả hai lần");

        expectFail(() -> service.deleteBook(2), "không xóa sách đang được mượn");

        Book rare = service.addBook("Sách hiếm", "Tác giả X", "Test", 2020, 1);
        check(book(service, rare.getId()).getTotalCopies() == 1, "thêm sách mới");
        service.borrow("sv001", rare.getId(), 7);
        expectFail(() -> service.borrow("sv002", rare.getId(), 7), "báo hết khi không còn bản sao");
        expectFail(() -> service.updateBook(rare.getId(), "Sách hiếm", "Tác giả X", "Test", 2020, 0),
                "không giảm bản sao xuống dưới số đang mượn");

        service.updateBook(rare.getId(), "Sách hiếm 2", "Tác giả Y", "Test", 2021, 3);
        check(book(service, rare.getId()).getAvailableCopies() == 2, "tăng bản sao thì số khả dụng tăng theo (3 bản - 1 đang mượn)");
        check(book(service, rare.getId()).getTitle().equals("Sách hiếm 2"), "sửa được thông tin sách");

        service.deleteBook(4);
        check(service.getBooks().size() == 5, "xóa được sách không bị mượn");

        // --- Đặt trước (FIFO) ---
        expectFail(() -> service.reserve("sv001", 5), "không đặt trước khi sách còn bản sao");

        Book hot = service.addBook("Sách hot", "Tác giả Z", "Test", 2022, 1);
        service.borrow("sv003", hot.getId(), 7);
        check(book(service, hot.getId()).getAvailableCopies() == 0, "sách hot đã hết bản sao");

        ReservationRecord first = service.reserve("sv002", hot.getId());
        ReservationRecord second = service.reserve("sv001", hot.getId());
        expectFail(() -> service.reserve("sv002", hot.getId()), "không cho đặt trước trùng một cuốn");

        BorrowRecord hotBorrow = service.myBorrows("sv003").stream()
                .filter(b -> b.getBookId() == hot.getId())
                .findFirst()
                .orElseThrow();
        service.returnBook(hotBorrow.getId(), "sv003");
        ReservationRecord firstAfterReturn = service.myReservations("sv002").stream()
                .filter(r -> r.getId() == first.getId())
                .findFirst()
                .orElseThrow();
        check(firstAfterReturn.getStatus() == ReservationStatus.READY,
                "người đặt đầu tiên được giữ chỗ khi sách về");
        check(firstAfterReturn.getExpiryDate().equals(LocalDate.now().plusDays(LibraryService.HOLD_DAYS)),
                "hạn nhận = hôm nay + " + LibraryService.HOLD_DAYS + " ngày");
        check(book(service, hot.getId()).getAvailableCopies() == 0,
                "bản giữ chỗ không bị trả về kho");
        check(book(service, hot.getId()).getHeldCopies() == 1,
                "bảng sách hiển thị đang giữ 1 bản cho người đặt trước");

        service.borrow("sv002", hot.getId(), 7);
        check(service.myReservations("sv002").stream()
                        .filter(r -> r.getId() == first.getId()).findFirst().orElseThrow()
                        .getStatus() == ReservationStatus.FULFILLED,
                "người đặt trước nhận được sách dù kho đang 0");

        BorrowRecord hotBorrow2 = service.myBorrows("sv002").stream()
                .filter(b -> b.getBookId() == hot.getId())
                .findFirst()
                .orElseThrow();
        service.returnBook(hotBorrow2.getId(), "sv002");
        check(service.myReservations("sv001").stream()
                        .filter(r -> r.getId() == second.getId()).findFirst().orElseThrow()
                        .getStatus() == ReservationStatus.READY,
                "người thứ hai trong hàng đợi được giữ chỗ tiếp theo");

        service.cancelReservation(second.getId(), "sv001");
        check(book(service, hot.getId()).getAvailableCopies() == 1,
                "hủy giữ chỗ thì bản sao về kho");

        System.out.println("SELF-CHECK OK (" + passed + " kiểm tra)");
    }

    private static Book book(LibraryService service, int id) {
        return service.getBooks().stream()
                .filter(b -> b.getId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Thiếu sách id=" + id));
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new IllegalStateException("FAIL: " + label);
        }
        passed++;
        System.out.println("  ok - " + label);
    }

    private static void expectFail(Runnable action, String label) {
        try {
            action.run();
        } catch (ServiceException e) {
            check(true, label + " (" + e.getMessage() + ")");
            return;
        }
        throw new IllegalStateException("FAIL (không báo lỗi): " + label);
    }
}
