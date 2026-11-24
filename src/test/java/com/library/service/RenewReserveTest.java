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
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class RenewReserveTest {

    @TempDir
    Path tempDir;

    private LibraryService service;

    @BeforeEach
    void setUp() {
        DataStorage storage = new DataStorage(tempDir);
        service = new LibraryService(storage);
    }

    @Test
    void testRenewNormal() throws LibraryException {
        BorrowRecord r = service.borrowBook("sv001", 1, 14);
        var originalDue = r.getDueDate();

        BorrowRecord renewed = service.renewBook("sv001", r.getId());
        assertEquals(1, renewed.getRenewCount());
        assertEquals(originalDue.plusDays(7), renewed.getDueDate());
    }

    @Test
    void testRenewMaxTwoTimes() throws LibraryException {
        BorrowRecord r = service.borrowBook("sv001", 1, 14);
        service.renewBook("sv001", r.getId());
        service.renewBook("sv001", r.getId());

        // Lần thứ 3 bị từ chối
        LibraryException ex = assertThrows(LibraryException.class, () -> service.renewBook("sv001", r.getId()));
        assertEquals(ResponseStatus.ERROR, ex.getStatus());
        assertTrue(ex.getMessage().contains("tối đa"));
    }

    @Test
    void testRenewBlockedByPendingReservation() throws LibraryException {
        BorrowRecord r1 = service.borrowBook("sv001", 1, 14);
        service.borrowBook("sv002", 1, 14);
        service.borrowBook("sv003", 1, 14);

        // Sinh viên khác đặt trước khi hết kho
        service.addUser("sv004", "123", "SV 4", "sv4@test.com", Role.STUDENT);
        service.reserveBook("sv004", 1);

        // sv001 gia hạn bị từ chối vì có người đang chờ
        LibraryException ex = assertThrows(LibraryException.class, () -> service.renewBook("sv001", r1.getId()));
        assertEquals(ResponseStatus.ERROR, ex.getStatus());
        assertTrue(ex.getMessage().contains("đặt trước"));
    }

    @Test
    void testReserveWhenStockAvailableRejected() {
        // Sách 1 còn 3 bản sao -> không được đặt trước
        LibraryException ex = assertThrows(LibraryException.class, () -> service.reserveBook("sv001", 1));
        assertEquals(ResponseStatus.ERROR, ex.getStatus());
        assertTrue(ex.getMessage().contains("còn trong kho"));
    }

    @Test
    void testReturnAutoAllocationFIFO() throws LibraryException {
        BorrowRecord r1 = service.borrowBook("sv001", 1, 14);
        service.borrowBook("sv002", 1, 14);
        service.borrowBook("sv003", 1, 14);

        service.addUser("sv004", "123", "SV 4", "sv4@test.com", Role.STUDENT);
        service.addUser("sv005", "123", "SV 5", "sv5@test.com", Role.STUDENT);

        ReservationRecord res1 = service.reserveBook("sv004", 1);
        ReservationRecord res2 = service.reserveBook("sv005", 1);

        // sv001 trả sách -> bản sao tự động gán cho sv004 (người đặt trước sớm nhất)
        service.returnBook("sv001", r1.getCopyId());

        var allRes = service.getAllReservations();
        ReservationRecord updatedRes1 = allRes.stream().filter(r -> r.getId() == res1.getId()).findFirst().orElseThrow();
        assertEquals(r1.getCopyId(), updatedRes1.getAssignedCopyId());
        assertNotNull(updatedRes1.getExpiryDate());

        // sv004 mượn sách này thành công
        BorrowRecord b4 = service.borrowBook("sv004", 1, 14);
        assertNotNull(b4);
        assertEquals(r1.getCopyId(), b4.getCopyId());
    }

    @Test
    void testExpireReservation() throws LibraryException {
        BorrowRecord r1 = service.borrowBook("sv001", 1, 14);
        service.borrowBook("sv002", 1, 14);
        service.borrowBook("sv003", 1, 14);

        service.addUser("sv004", "123", "SV 4", "sv4@test.com", Role.STUDENT);
        service.addUser("sv005", "123", "SV 5", "sv5@test.com", Role.STUDENT);

        ReservationRecord res1 = service.reserveBook("sv004", 1);
        ReservationRecord res2 = service.reserveBook("sv005", 1);

        service.returnBook("sv001", r1.getCopyId());

        // Giả lập sv004 quá 48h không đến nhận
        var allRes = service.getAllReservations();
        ReservationRecord rsv1 = allRes.stream().filter(r -> r.getId() == res1.getId()).findFirst().orElseThrow();
        rsv1.setExpiryDate(LocalDateTime.now().minusHours(1));
        service.getStorage().saveReservations(allRes);

        service.expireReservations();

        // Kiểm tra res1 thành EXPIRED và bản sao được chuyển cho sv005
        var reloadedRes = service.getAllReservations();
        ReservationRecord expired = reloadedRes.stream().filter(r -> r.getId() == res1.getId()).findFirst().orElseThrow();
        assertEquals(ReservationStatus.EXPIRED, expired.getStatus());

        ReservationRecord next = reloadedRes.stream().filter(r -> r.getId() == res2.getId()).findFirst().orElseThrow();
        assertEquals(r1.getCopyId(), next.getAssignedCopyId());
    }
}
