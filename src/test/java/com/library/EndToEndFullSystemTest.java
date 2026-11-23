package com.library;

import com.library.common.model.*;
import com.library.server.service.LibraryException;
import com.library.server.service.LibraryService;
import com.library.server.storage.DataStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D10 / C10: Kiểm thử tích hợp toàn diện hệ thống (End-To-End Full System Test).
 * Mô phỏng toàn bộ chu trình nghiệp vụ thực tế của sinh viên và thủ thư:
 * 1. Đăng nhập hệ thống (Xác thực)
 * 2. Tìm kiếm sách mờ (Fuzzy Search)
 * 3. Mượn sách và giảm số lượng khả dụng
 * 4. Gia hạn thời gian mượn (+7 ngày)
 * 5. Tải tài liệu E-Book (Full & Preview)
 * 6. Trả sách và tự động cập nhật kho
 * 7. Đặt trước sách khi hết hàng và tự động phân phối (Auto-allocation FIFO)
 */
public class EndToEndFullSystemTest {

    @TempDir
    Path tempDir;

    private LibraryService service;
    private DataStorage storage;

    @BeforeEach
    void setUp() {
        storage = new DataStorage(tempDir);
        service = new LibraryService(storage);
    }

    @Test
    void testFullWorkflowLifecycle() throws LibraryException {
        // Bước 1: Đăng nhập
        User student = service.authenticate("sv001", "123");
        assertNotNull(student);
        assertEquals(Role.STUDENT, student.getRole());

        User librarian = service.authenticate("admin", "admin123");
        assertNotNull(librarian);
        assertEquals(Role.LIBRARIAN, librarian.getRole());

        // Bước 2: Tìm kiếm sách
        List<Book> searchResults = service.searchBooks("lap trinh", null, false);
        assertFalse(searchResults.isEmpty());
        Book book = searchResults.get(0);
        int bookId = book.getId();
        int initialAvailable = book.getAvailableCopies();

        // Bước 3: Sinh viên mượn sách
        BorrowRecord borrow = service.borrowBook(student.getUsername(), bookId, 14);
        assertNotNull(borrow);
        assertEquals(BorrowStatus.BORROWING, borrow.getStatus());

        List<Book> updatedBooks = service.searchBooks(book.getTitle(), null, false);
        assertEquals(initialAvailable - 1, updatedBooks.get(0).getAvailableCopies());

        // Bước 4: Gia hạn sách
        BorrowRecord renewed = service.renewBook(student.getUsername(), borrow.getId());
        assertEquals(1, renewed.getRenewCount());
        assertEquals(borrow.getDueDate().plusDays(7), renewed.getDueDate());

        // Bước 5: Tải tài liệu E-Book
        byte[] fullEbook = service.getEbook(bookId, true);
        assertNotNull(fullEbook);
        assertTrue(fullEbook.length > 0);

        byte[] previewEbook = service.getEbook(bookId, false);
        assertNotNull(previewEbook);
        assertTrue(previewEbook.length > 0);
        assertFalse(java.util.Arrays.equals(fullEbook, previewEbook));

        // Bước 6: Trả sách
        BorrowRecord returned = service.returnBook(student.getUsername(), borrow.getCopyId());
        assertEquals(BorrowStatus.RETURNED, returned.getStatus());

        List<Book> booksAfterReturn = service.searchBooks(book.getTitle(), null, false);
        assertEquals(initialAvailable, booksAfterReturn.get(0).getAvailableCopies());

        // Bước 7: Thử nghiệm đặt trước FIFO khi hết kho
        // Mượn hết toàn bộ bản sao
        BorrowRecord b1 = service.borrowBook("sv001", bookId, 14);
        BorrowRecord b2 = service.borrowBook("sv002", bookId, 14);
        BorrowRecord b3 = service.borrowBook("sv003", bookId, 14);

        // Sinh viên sv004 đặt trước sách khi hết hàng
        service.addUser("sv004", "123", "Sinh viên 4", "sv4@test.com", Role.STUDENT);
        ReservationRecord reservation = service.reserveBook("sv004", bookId);
        assertNotNull(reservation);
        assertEquals(ReservationStatus.PENDING, reservation.getStatus());

        // sv001 trả sách -> bản sao tự động gán cho sv004
        service.returnBook("sv001", b1.getCopyId());

        var reservations = service.getReservationsByStudent("sv004");
        assertEquals(1, reservations.size());
        assertEquals(b1.getCopyId(), reservations.get(0).getAssignedCopyId());

        // sv004 hoàn tất mượn cuốn sách đã đặt trước
        BorrowRecord b4 = service.borrowBook("sv004", bookId, 14);
        assertNotNull(b4);
        assertEquals(b1.getCopyId(), b4.getCopyId());
    }
}
