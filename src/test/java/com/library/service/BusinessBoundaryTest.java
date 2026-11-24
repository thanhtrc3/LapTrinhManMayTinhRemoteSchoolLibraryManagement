package com.library.service;

import com.library.common.model.*;
import com.library.common.net.EventType;
import com.library.common.net.RealtimeEvent;
import com.library.common.net.ResponseStatus;
import com.library.server.service.LibraryException;
import com.library.server.service.LibraryService;
import com.library.server.storage.DataStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
    void testBoundaryBlockedUserCannotBorrowRenewOrReserve() throws LibraryException {
        BorrowRecord r = service.borrowBook("sv001", 1, 14);
        service.borrowBook("sv002", 2, 14);
        service.borrowBook("sv003", 2, 14);
        service.addUser("sv004", "123", "SV 4", "sv4@test.com");
        service.borrowBook("sv004", 2, 14);

        List<User> users = storage.loadUsers();
        User u = users.stream().filter(x -> x.getUsername().equals("sv001")).findFirst().orElseThrow();
        u.setBlocked(true);
        storage.saveUsers(users);

        LibraryException exBorrow = assertThrows(LibraryException.class, () -> service.borrowBook("sv001", 3, 14));
        assertEquals(ResponseStatus.BLOCKED, exBorrow.getStatus());

        LibraryException exRenew = assertThrows(LibraryException.class, () -> service.renewBook("sv001", r.getId()));
        assertEquals(ResponseStatus.BLOCKED, exRenew.getStatus());

        LibraryException exReserve = assertThrows(LibraryException.class, () -> service.reserveBook("sv001", 2));
        assertEquals(ResponseStatus.BLOCKED, exReserve.getStatus());
    }

    @Test
    void testBoundaryClampedDays() throws LibraryException {
        BorrowRecord r0 = service.borrowBook("sv001", 1, 0);
        assertEquals(14, java.time.temporal.ChronoUnit.DAYS.between(r0.getBorrowDate(), r0.getDueDate()));

        BorrowRecord r99 = service.borrowBook("sv001", 2, 99);
        assertEquals(14, java.time.temporal.ChronoUnit.DAYS.between(r99.getBorrowDate(), r99.getDueDate()));
    }

    @Test
    void testBoundaryRenewThirdTimeAndReturnedBookRejected() throws LibraryException {
        BorrowRecord r = service.borrowBook("sv001", 1, 14);
        service.renewBook("sv001", r.getId());
        service.renewBook("sv001", r.getId());

        LibraryException ex = assertThrows(LibraryException.class, () -> service.renewBook("sv001", r.getId()));
        assertEquals(ResponseStatus.ERROR, ex.getStatus());

        // Sau khi trả sách thì không được phép gia hạn phiếu đã trả
        BorrowRecord r2 = service.borrowBook("sv002", 1, 14);
        service.returnBook("sv002", r2.getCopyId());
        LibraryException exReturned = assertThrows(LibraryException.class, () -> service.renewBook("sv002", r2.getId()));
        assertEquals(ResponseStatus.ERROR, exReturned.getStatus());
    }

    @Test
    void testBoundaryReserveAvailableBookRejected() {
        LibraryException ex = assertThrows(LibraryException.class, () -> service.reserveBook("sv001", 1));
        assertEquals(ResponseStatus.ERROR, ex.getStatus());
    }

    @Test
    void testBoundaryCancelReservationReleasesCopyAndRestoresAvailableCopies() throws LibraryException {
        BorrowRecord r1 = service.borrowBook("sv001", 1, 14);
        service.borrowBook("sv002", 1, 14);
        service.borrowBook("sv003", 1, 14);

        service.addUser("sv004", "123", "SV 4", "sv4@test.com", Role.STUDENT);
        ReservationRecord res = service.reserveBook("sv004", 1);

        service.returnBook("sv001", r1.getCopyId());
        // sv004 được gán bản sao này (status = RESERVED), availableCopies vẫn = 0
        BookCopy copy = service.getCopies(1).stream().filter(c -> c.getCopyId().equals(r1.getCopyId())).findFirst().orElseThrow();
        assertEquals(CopyStatus.RESERVED, copy.getStatus());
        assertEquals(0, service.searchBooks(null, null, false).stream().filter(b -> b.getId() == 1).findFirst().orElseThrow().getAvailableCopies());

        // Hủy đặt trước -> bản sao trở lại AVAILABLE và availableCopies tăng lên 1
        service.cancelReservation("sv004", res.getId());
        BookCopy updatedCopy = service.getCopies(1).stream().filter(c -> c.getCopyId().equals(r1.getCopyId())).findFirst().orElseThrow();
        assertEquals(CopyStatus.AVAILABLE, updatedCopy.getStatus());
        assertEquals(1, service.searchBooks(null, null, false).stream().filter(b -> b.getId() == 1).findFirst().orElseThrow().getAvailableCopies());
    }

    @Test
    void testHoldExpiringNotificationSentOnlyOnce() throws LibraryException {
        BorrowRecord r1 = service.borrowBook("sv001", 1, 14);
        service.borrowBook("sv002", 1, 14);
        service.borrowBook("sv003", 1, 14);

        service.addUser("sv004", "123", "SV 4", "sv4@test.com");
        ReservationRecord res = service.reserveBook("sv004", 1);
        service.returnBook("sv001", r1.getCopyId());

        // Chỉnh expiryDate còn 3 giờ (< 6 giờ)
        List<ReservationRecord> allRes = storage.loadReservations();
        ReservationRecord rsv = allRes.stream().filter(x -> x.getId() == res.getId()).findFirst().orElseThrow();
        rsv.setExpiryDate(LocalDateTime.now().plusHours(3));
        storage.saveReservations(allRes);

        List<RealtimeEvent> expiringEvents = new ArrayList<>();
        service.setPublisher(ev -> {
            if (ev.getType() == EventType.HOLD_EXPIRING) {
                expiringEvents.add(ev);
            }
        });

        service.expireReservations();
        service.expireReservations();
        service.expireReservations();

        assertEquals(1, expiringEvents.size(), "Cảnh báo HOLD_EXPIRING chỉ được gửi đúng 1 lần");
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
