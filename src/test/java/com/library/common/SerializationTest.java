package com.library.common;

import com.library.common.model.*;
import com.library.common.net.*;
import com.library.server.service.LibraryException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SerializationTest {

    @SuppressWarnings("unchecked")
    private static <T> T serializeAndDeserialize(T obj) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
            oos.flush();
        }
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (T) ois.readObject();
        }
    }

    @Test
    @DisplayName("Kiểm tra tuần tự hóa Request và Response")
    void testRequestAndResponseSerialization() throws Exception {
        Request req = new Request(CommandType.LOGIN, Map.of("username", "sv001", "password", "123"));
        req.setRequestId(7L);
        Request reqResult = serializeAndDeserialize(req);
        assertNotNull(reqResult);
        assertEquals(7L, reqResult.getRequestId());
        assertEquals(CommandType.LOGIN, reqResult.getCommand());
        assertEquals("sv001", reqResult.getPayload().get("username"));

        Request pingReq = new Request(CommandType.PING, null);
        Request pingResult = serializeAndDeserialize(pingReq);
        assertNotNull(pingResult.getPayload());
        assertTrue(pingResult.getPayload().isEmpty());

        Response res = new Response(7L, ResponseStatus.OK, "Thành công", "testData");
        Response resResult = serializeAndDeserialize(res);
        assertNotNull(resResult);
        assertEquals(7L, resResult.getRequestId());
        assertEquals(ResponseStatus.OK, resResult.getStatus());
        assertTrue(resResult.isOk());
        assertEquals("Thành công", resResult.getMessage());
        assertEquals("testData", resResult.getData());
    }

    @Test
    @DisplayName("Kiểm tra tuần tự hóa MulticastMessage, RealtimeEvent, ServerStats và LibraryException")
    void testProtocolMessagesAndException() throws Exception {
        MulticastMessage msg = new MulticastMessage("Thông báo", "Thư viện đóng cửa sau 15 phút", 1700000000000L, "admin");
        MulticastMessage msgResult = serializeAndDeserialize(msg);
        assertNotNull(msgResult);
        assertEquals("Thông báo", msgResult.getTitle());
        assertEquals("Thư viện đóng cửa sau 15 phút", msgResult.getContent());
        assertEquals(1700000000000L, msgResult.getSentAt());
        assertEquals("admin", msgResult.getSender());

        RealtimeEvent event = new RealtimeEvent(EventType.BOOKS_CHANGED, "sv001", "Cập nhật sách", 42);
        RealtimeEvent eventResult = serializeAndDeserialize(event);
        assertNotNull(eventResult);
        assertEquals(EventType.BOOKS_CHANGED, eventResult.getType());
        assertEquals("sv001", eventResult.getTargetUsername());
        assertEquals("Cập nhật sách", eventResult.getMessage());
        assertEquals(42, eventResult.getData());

        ServerStats stats = new ServerStats(2, List.of("127.0.0.1:50001", "127.0.0.1:50002"), 12L, 4096L);
        ServerStats statsResult = serializeAndDeserialize(stats);
        assertNotNull(statsResult);
        assertEquals(2, statsResult.getOnlineClients());
        assertEquals(List.of("127.0.0.1:50001", "127.0.0.1:50002"), statsResult.getClientAddresses());
        assertEquals(12L, statsResult.getAvgPingMs());
        assertEquals(4096L, statsResult.getTotalBytesSent());

        ServerStats emptyStats = serializeAndDeserialize(new ServerStats(0, null, 0L, 0L));
        assertNotNull(emptyStats.getClientAddresses());
        assertTrue(emptyStats.getClientAddresses().isEmpty());

        LibraryException ex = new LibraryException(ResponseStatus.OUT_OF_STOCK, "Sách đã hết");
        assertEquals(ResponseStatus.OUT_OF_STOCK, ex.getStatus());
        assertEquals("Sách đã hết", ex.getMessage());
    }

    @Test
    @DisplayName("Kiểm tra tuần tự hóa User và Book")
    void testUserAndBookSerialization() throws Exception {
        User user = new User("sv001", "123", "Nguyễn Văn An", Role.STUDENT, "sv001@school.edu.vn", 3, 0, false);
        User userResult = serializeAndDeserialize(user);
        assertNotNull(userResult);
        assertEquals("sv001", userResult.getUsername());
        assertEquals("Nguyễn Văn An", userResult.getFullName());
        assertEquals(Role.STUDENT, userResult.getRole());
        assertEquals("sv001@school.edu.vn", userResult.getEmail());
        assertEquals(3, userResult.getMaxBorrowQuota());
        assertEquals(0, userResult.getViolationCount());
        assertFalse(userResult.isBlocked());

        Book book = new Book(1, "978-604-0-12345-6", "Lập trình mạng máy tính", "Nguyễn Gia Hiếu", "Mạng máy tính", 2021, 4, 3, true, "1_full.pdf");
        Book bookResult = serializeAndDeserialize(book);
        assertNotNull(bookResult);
        assertEquals(1, bookResult.getId());
        assertEquals("978-604-0-12345-6", bookResult.getIsbn());
        assertEquals("Lập trình mạng máy tính", bookResult.getTitle());
        assertEquals("Nguyễn Gia Hiếu", bookResult.getAuthor());
        assertEquals("Mạng máy tính", bookResult.getCategory());
        assertEquals(2021, bookResult.getPublishYear());
        assertEquals(4, bookResult.getTotalCopies());
        assertEquals(3, bookResult.getAvailableCopies());
        assertTrue(bookResult.isHasEbook());
        assertEquals("1_full.pdf", bookResult.getEbookFileName());
    }

    @Test
    @DisplayName("Kiểm tra tuần tự hóa BookCopy, BorrowRecord và ReservationRecord")
    void testCopyAndRecordsSerialization() throws Exception {
        BookCopy copy = new BookCopy("BK01-C01", 1, "BC-BK01-C01", "LIB|BK01-C01|BC-BK01-C01", CopyStatus.AVAILABLE);
        BookCopy copyResult = serializeAndDeserialize(copy);
        assertNotNull(copyResult);
        assertEquals("BK01-C01", copyResult.getCopyId());
        assertEquals(1, copyResult.getBookId());
        assertEquals("BC-BK01-C01", copyResult.getBarcode());
        assertEquals("LIB|BK01-C01|BC-BK01-C01", copyResult.getQrData());
        assertEquals(CopyStatus.AVAILABLE, copyResult.getStatus());

        LocalDate borrowDate = LocalDate.of(2026, 11, 23);
        LocalDate dueDate = LocalDate.of(2026, 12, 7);
        BorrowRecord borrow = new BorrowRecord(100L, "BK01-C01", 1, "Lập trình mạng máy tính", "sv001",
                borrowDate, dueDate, null, BorrowStatus.BORROWING, 1);
        BorrowRecord borrowResult = serializeAndDeserialize(borrow);
        assertNotNull(borrowResult);
        assertEquals(100L, borrowResult.getId());
        assertEquals("BK01-C01", borrowResult.getCopyId());
        assertEquals(1, borrowResult.getBookId());
        assertEquals("Lập trình mạng máy tính", borrowResult.getBookTitle());
        assertEquals("sv001", borrowResult.getStudentId());
        assertEquals(borrowDate, borrowResult.getBorrowDate());
        assertEquals(dueDate, borrowResult.getDueDate());
        assertNull(borrowResult.getReturnDate());
        assertEquals(BorrowStatus.BORROWING, borrowResult.getStatus());
        assertEquals(1, borrowResult.getRenewCount());

        LocalDateTime resDate = LocalDateTime.of(2026, 11, 23, 10, 0);
        LocalDateTime expDate = LocalDateTime.of(2026, 11, 25, 10, 0);
        ReservationRecord reservation = new ReservationRecord(200L, 1, "Lập trình mạng máy tính", "sv002",
                resDate, expDate, ReservationStatus.PENDING, "BK01-C02");
        ReservationRecord resResult = serializeAndDeserialize(reservation);
        assertNotNull(resResult);
        assertEquals(200L, resResult.getId());
        assertEquals(1, resResult.getBookId());
        assertEquals("Lập trình mạng máy tính", resResult.getBookTitle());
        assertEquals("sv002", resResult.getStudentId());
        assertEquals(resDate, resResult.getReservationDate());
        assertEquals(expDate, resResult.getExpiryDate());
        assertEquals(ReservationStatus.PENDING, resResult.getStatus());
        assertEquals("BK01-C02", resResult.getAssignedCopyId());
    }

    @Test
    @DisplayName("Kiểm tra tuần tự hóa AuditLog và SearchHistoryItem")
    void testAuditLogAndSearchHistorySerialization() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        AuditLog audit = new AuditLog(1L, now, AuditAction.BORROW, "sv001", "Mượn sách BK01-C01");
        AuditLog auditResult = serializeAndDeserialize(audit);
        assertNotNull(auditResult);
        assertEquals(1L, auditResult.getId());
        assertEquals(now, auditResult.getTimestamp());
        assertEquals(AuditAction.BORROW, auditResult.getAction());
        assertEquals("sv001", auditResult.getActorUsername());
        assertEquals("Mượn sách BK01-C01", auditResult.getDetails());

        SearchHistoryItem item = new SearchHistoryItem("sv001", "lap trinh mang", "Mạng máy tính", now);
        SearchHistoryItem itemResult = serializeAndDeserialize(item);
        assertNotNull(itemResult);
        assertEquals("sv001", itemResult.getStudentId());
        assertEquals("lap trinh mang", itemResult.getSearchKeyword());
        assertEquals("Mạng máy tính", itemResult.getSearchCategory());
        assertEquals(now, itemResult.getTimestamp());
    }
}
