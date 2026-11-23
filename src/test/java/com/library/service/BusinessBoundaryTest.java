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

/**
 * D8 / C8: Kiểm thử toàn diện các ca biên nghiệp vụ:
 * 1. Hạn mức mượn tối đa 3 cuốn
 * 2. Khóa mượn khi tài khoản đang bị blocked
 * 3. Ngày mượn days = 0 và days = 99 bị kẹp trong giới hạn hợp lệ
 * 4. Gia hạn lần 3 bị chặn
 * 5. Đặt trước khi sách còn trong kho bị chặn
 * 6. Hủy đặt trước nhả bản sao cho người kế tiếp hoặc về AVAILABLE
 * 7. Quá hạn 3 lần -> tự động khóa tài khoản (3-strike)
 * 8. Mở khóa reset violationCount = 0 và blocked = false
 */
public class BusinessBoundaryTest {

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
    void testBoundaryMaxBorrowQuota() throws LibraryException {
        service.borrowBook("sv001", 1, 14);
        service.borrowBook("sv001", 2, 14);
        service.borrowBook("sv001", 3, 14);

        LibraryException ex = assertThrows(LibraryException.class, () -> service.borrowBook("sv001", 4, 14));
        assertEquals(ResponseStatus.QUOTA_EXCEEDED, ex.getStatus());
    }

    @Test
    void testBoundaryBlockedUserCannotBorrow() throws LibraryException {
        List<User> users = storage.loadUsers();
        User u = users.stream().filter(x -> x.getUsername().equals("sv001")).findFirst().orElseThrow();
        u.setBlocked(true);
        storage.saveUsers(users);

        LibraryException ex = assertThrows(LibraryException.class, () -> service.borrowBook("sv001", 1, 14));
        assertEquals(ResponseStatus.BLOCKED, ex.getStatus());
    }

    @Test
    void testBoundaryClampedDays() throws LibraryException {
        BorrowRecord r0 = service.borrowBook("sv001", 1, 0);
        assertEquals(14, java.time.temporal.ChronoUnit.DAYS.between(r0.getBorrowDate(), r0.getDueDate()));

        BorrowRecord r99 = service.borrowBook("sv001", 2, 99);
        assertEquals(14, java.time.temporal.ChronoUnit.DAYS.between(r99.getBorrowDate(), r99.getDueDate()));
    }

    @Test
    void testBoundaryRenewThirdTimeRejected() throws LibraryException {
        BorrowRecord r = service.borrowBook("sv001", 1, 14);
        service.renewBook("sv001", r.getId());
        service.renewBook("sv001", r.getId());

        LibraryException ex = assertThrows(LibraryException.class, () -> service.renewBook("sv001", r.getId()));
        assertEquals(ResponseStatus.ERROR, ex.getStatus());
    }

    @Test
    void testBoundaryReserveAvailableBookRejected() {
        LibraryException ex = assertThrows(LibraryException.class, () -> service.reserveBook("sv001", 1));
        assertEquals(ResponseStatus.ERROR, ex.getStatus());
    }

    @Test
    void testBoundaryCancelReservationReleasesCopy() throws LibraryException {
        BorrowRecord r1 = service.borrowBook("sv001", 1, 14);
        service.borrowBook("sv002", 1, 14);
        service.borrowBook("sv003", 1, 14);

        service.addUser("sv004", "123", "SV 4", "sv4@test.com", Role.STUDENT);
        ReservationRecord res = service.reserveBook("sv004", 1);

        service.returnBook("sv001", r1.getCopyId());
        // sv004 được gán bản sao này (status = RESERVED)
        BookCopy copy = service.getCopies(1).stream().filter(c -> c.getCopyId().equals(r1.getCopyId())).findFirst().orElseThrow();
        assertEquals(CopyStatus.RESERVED, copy.getStatus());

        // Hủy đặt trước -> bản sao phải trở lại AVAILABLE
        service.cancelReservation("sv004", res.getId());
        BookCopy updatedCopy = service.getCopies(1).stream().filter(c -> c.getCopyId().equals(r1.getCopyId())).findFirst().orElseThrow();
        assertEquals(CopyStatus.AVAILABLE, updatedCopy.getStatus());
    }

    @Test
    void testBoundaryThreeStrikesAndUnblock() throws LibraryException {
        User u = service.authenticate("sv001", "123");
        assertEquals(0, u.getViolationCount());

        // Vi phạm 1
        BorrowRecord r1 = service.borrowBook("sv001", 1, 14);
        setOverdue(r1.getId(), 2);
        service.returnBook("sv001", r1.getCopyId());
        assertEquals(1, service.authenticate("sv001", "123").getViolationCount());

        // Vi phạm 2
        BorrowRecord r2 = service.borrowBook("sv001", 1, 14);
        setOverdue(r2.getId(), 3);
        service.returnBook("sv001", r2.getCopyId());
        assertEquals(2, service.authenticate("sv001", "123").getViolationCount());

        // Vi phạm 3 -> blocked
        BorrowRecord r3 = service.borrowBook("sv001", 1, 14);
        setOverdue(r3.getId(), 4);
        service.returnBook("sv001", r3.getCopyId());
        User blocked = service.authenticate("sv001", "123");
        assertEquals(3, blocked.getViolationCount());
        assertTrue(blocked.isBlocked());

        // Mở khóa -> reset về 0 và false
        User unblocked = service.unblockUser("sv001");
        assertEquals(0, unblocked.getViolationCount());
        assertFalse(unblocked.isBlocked());
    }

    private void setOverdue(long borrowId, int daysLate) {
        List<BorrowRecord> borrows = storage.loadBorrows();
        BorrowRecord b = borrows.stream().filter(x -> x.getId() == borrowId).findFirst().orElseThrow();
        b.setDueDate(LocalDate.now().minusDays(daysLate));
        storage.saveBorrows(borrows);
    }
}
