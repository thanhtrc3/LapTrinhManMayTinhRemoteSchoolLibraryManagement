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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LibraryServiceTest {

    @TempDir
    Path tempDir;

    private LibraryService service;

    @BeforeEach
    void setUp() {
        DataStorage storage = new DataStorage(tempDir);
        service = new LibraryService(storage);
    }

    @Test
    void testAuthenticateSuccess() throws LibraryException {
        User user = service.authenticate("sv001", "123");
        assertNotNull(user);
        assertEquals("sv001", user.getUsername());
        assertEquals("Nguyễn Văn An", user.getFullName());
    }

    @Test
    void testAuthenticateFailure() {
        LibraryException ex = assertThrows(LibraryException.class, () -> service.authenticate("sv001", "wrongpass"));
        assertEquals(ResponseStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void testSearchBooksFuzzy() {
        // Tìm không dấu, sai một vài ký tự
        List<Book> result = service.searchBooks("lap trinh mag", null, false);
        assertFalse(result.isEmpty());
        assertTrue(result.stream().anyMatch(b -> b.getTitle().equals("Lập trình mạng máy tính")));

        // Tìm theo thể loại
        List<Book> mathBooks = service.searchBooks(null, "Toán học", false);
        assertEquals(1, mathBooks.size());
        assertEquals("Toán rời rạc ứng dụng", mathBooks.get(0).getTitle());
    }

    @Test
    void testRecordSearchAndRecommendations() {
        service.recordSearch("sv001", "toán học", "Toán học");
        service.recordSearch("sv001", "đại số", "Toán học");
        service.recordSearch("sv001", "rời rạc", "Toán học");

        List<Book> recs = service.getRecommendations("sv001");
        assertFalse(recs.isEmpty());
        // Sách toán học phải xuất hiện đầu tiên vì tìm nhiều nhất
        assertEquals("Toán học", recs.get(0).getCategory());
    }

    @Test
    void testClampedBorrowDays() throws LibraryException {
        // days = 0 hoặc 99 đều bị kẹp trong dải 1..30 hoặc mặc định 14
        BorrowRecord r1 = service.borrowBook("sv001", 1, 0);
        assertEquals(14, java.time.temporal.ChronoUnit.DAYS.between(r1.getBorrowDate(), r1.getDueDate()));

        BorrowRecord r2 = service.borrowBook("sv001", 2, 99);
        assertEquals(14, java.time.temporal.ChronoUnit.DAYS.between(r2.getBorrowDate(), r2.getDueDate()));
    }
}
