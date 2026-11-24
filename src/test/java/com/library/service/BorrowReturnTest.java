package com.library.service;

import com.library.common.model.*;
import com.library.common.net.ResponseStatus;
import com.library.server.service.LibraryException;
import com.library.server.service.LibraryService;
import com.library.server.storage.DataStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BorrowReturnTest {

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
    void testBorrowNormal() throws LibraryException {
        BorrowRecord r = service.borrowBook("sv001", 1, 14);
        assertNotNull(r);
        assertEquals("sv001", r.getStudentId());
        assertEquals(1, r.getBookId());
        assertEquals(BorrowStatus.BORROWING, r.getStatus());

        List<BookCopy> copies = service.getCopies(1);
        long borrowedCount = copies.stream().filter(c -> c.getStatus() == CopyStatus.BORROWED).count();
        assertEquals(1, borrowedCount);
    }

    @Test
    void testBorrowOutOfStock() throws LibraryException {
        // Sách 1 có 3 bản sao -> mượn hết 3 cuốn
        service.borrowBook("sv001", 1, 14);
        service.borrowBook("sv002", 1, 14);
        service.borrowBook("sv003", 1, 14);

        // Sinh viên khác mượn tiếp -> OUT_OF_STOCK
        User sv4 = service.addUser("sv004", "123", "SV 4", "sv4@test.com", Role.STUDENT);
        LibraryException ex = assertThrows(LibraryException.class, () -> service.borrowBook(sv4.getUsername(), 1, 14));
        assertEquals(ResponseStatus.OUT_OF_STOCK, ex.getStatus());
    }

    @Test
    void testBorrowQuotaExceeded() throws LibraryException {
        // Hạn mức là 3 cuốn
        service.borrowBook("sv001", 1, 14);
        service.borrowBook("sv001", 2, 14);
        service.borrowBook("sv001", 3, 14);

        // Cuốn thứ 4 bị từ chối
        LibraryException ex = assertThrows(LibraryException.class, () -> service.borrowBook("sv001", 4, 14));
        assertEquals(ResponseStatus.QUOTA_EXCEEDED, ex.getStatus());
    }

    @Test
    void testBorrowOverdueBlocked() throws LibraryException {
        BorrowRecord r = service.borrowBook("sv001", 1, 14);
        // Cập nhật dueDate về quá khứ trong storage
        List<BorrowRecord> borrows = storage.loadBorrows();
        BorrowRecord b = borrows.stream().filter(x -> x.getId() == r.getId()).findFirst().orElseThrow();
        b.setDueDate(LocalDate.now().minusDays(2));
        b.setStatus(BorrowStatus.OVERDUE);
        storage.saveBorrows(borrows);

        LibraryException ex = assertThrows(LibraryException.class, () -> service.borrowBook("sv001", 2, 14));
        assertEquals(ResponseStatus.BLOCKED, ex.getStatus());
        assertTrue(ex.getMessage().contains("quá hạn"));
    }

    @Test
    void testReturnNormal() throws LibraryException {
        BorrowRecord r = service.borrowBook("sv001", 1, 14);
        BorrowRecord returned = service.returnBook("sv001", r.getCopyId());

        assertEquals(BorrowStatus.RETURNED, returned.getStatus());
        assertEquals(LocalDate.now(), returned.getReturnDate());

        List<BookCopy> copies = service.getCopies(1);
        BookCopy copy = copies.stream().filter(c -> c.getCopyId().equals(r.getCopyId())).findFirst().orElseThrow();
        assertEquals(CopyStatus.AVAILABLE, copy.getStatus());
    }

    @Test
    void testReturnOverdueStrike() throws LibraryException {
        BorrowRecord r = service.borrowBook("sv001", 1, 14);
        // Sửa hạn trả về quá khứ trong storage
        List<BorrowRecord> borrows = storage.loadBorrows();
        BorrowRecord b = borrows.stream().filter(x -> x.getId() == r.getId()).findFirst().orElseThrow();
        b.setDueDate(LocalDate.now().minusDays(5));
        storage.saveBorrows(borrows);

        service.returnBook("sv001", r.getCopyId());

        User user = service.authenticate("sv001", "123");
        assertEquals(1, user.getViolationCount(), "Phải tăng số lần vi phạm khi trả quá hạn");
        assertFalse(user.isBlocked(), "1 lần vi phạm chưa bị khóa");
    }

    @Test
    void testReturnThreeStrikesBlocked() throws LibraryException {
        List<User> users = storage.loadUsers();
        User u = users.stream().filter(x -> x.getUsername().equals("sv001")).findFirst().orElseThrow();
        u.setViolationCount(2);
        storage.saveUsers(users);

        BorrowRecord r = service.borrowBook("sv001", 1, 14);
        List<BorrowRecord> borrows = storage.loadBorrows();
        BorrowRecord b = borrows.stream().filter(x -> x.getId() == r.getId()).findFirst().orElseThrow();
        b.setDueDate(LocalDate.now().minusDays(1));
        storage.saveBorrows(borrows);

        service.returnBook("sv001", r.getCopyId());

        User updated = service.authenticate("sv001", "123");
        assertEquals(3, updated.getViolationCount());
        assertTrue(updated.isBlocked(), "Vi phạm lần thứ 3 phải bị khóa tài khoản");
    }

    @Test
    void testReturnNotFound() {
        LibraryException ex = assertThrows(LibraryException.class, () -> service.returnBook("sv001", "NON-EXISTENT-COPY"));
        assertEquals(ResponseStatus.NOT_FOUND, ex.getStatus());
    }
}
