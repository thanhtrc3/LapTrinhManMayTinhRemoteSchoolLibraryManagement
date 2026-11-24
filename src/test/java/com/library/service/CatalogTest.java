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

public class CatalogTest {

    @TempDir
    Path tempDir;

    private LibraryService service;

    @BeforeEach
    void setUp() {
        DataStorage storage = new DataStorage(tempDir);
        service = new LibraryService(storage);
    }

    @Test
    void testAddBookAndSequentialCopies() {
        Book newBook = service.addBook("Mạng Nơ-ron và Deep Learning", "Lê Minh Tuấn", "Trí tuệ nhân tạo", 2023, 2);
        assertNotNull(newBook);
        assertEquals(6, newBook.getId());

        List<BookCopy> copies = service.getCopies(6);
        assertEquals(2, copies.size());
        assertEquals("BK06-C01", copies.get(0).getCopyId());
        assertEquals("BC-BK06-C01", copies.get(0).getBarcode());
        assertEquals("LIB|BK06-C01|BC-BK06-C01", copies.get(0).getQrData());
        assertEquals("BK06-C02", copies.get(1).getCopyId());
    }

    @Test
    void testDeleteBookWithActiveBorrowBlocked() throws LibraryException {
        service.borrowBook("sv001", 1, 14);

        LibraryException ex = assertThrows(LibraryException.class, () -> service.deleteBook(1));
        assertEquals(ResponseStatus.ERROR, ex.getStatus());
        assertTrue(ex.getMessage().contains("Không thể xóa sách đang có bản sao"));
    }

    @Test
    void testAddAndRemoveCopyWithoutDuplicateId() throws LibraryException {
        // Xóa bản sao ở giữa BK01-C02 (còn C01 và C03)
        service.removeCopy("BK01-C02");
        assertEquals(2, service.getCopies(1).size());

        // Thêm bản sao mới -> phải nối tiếp thành BK01-C04 chứ không trùng BK01-C03
        BookCopy copy4 = service.addCopy(1);
        assertEquals("BK01-C04", copy4.getCopyId());

        List<BookCopy> copies = service.getCopies(1);
        assertEquals(3, copies.size());
        assertEquals(3, copies.stream().map(BookCopy::getCopyId).distinct().count());
    }

    @Test
    void testUpdateBookPreservesCopiesCount() throws LibraryException {
        service.borrowBook("sv001", 1, 14);
        Book updated = service.updateBook(1, "Lập trình mạng nâng cao", "Nguyễn Gia Hiếu", "Mạng máy tính", 2024);
        assertEquals("Lập trình mạng nâng cao", updated.getTitle());
        assertEquals(2024, updated.getPublishYear());
        assertEquals(3, updated.getTotalCopies());
        assertEquals(2, updated.getAvailableCopies());
    }

    @Test
    void testAddUserFourArgsAndDuplicateRejected() throws LibraryException {
        User created = service.addUser("sv004", "123456", "Phạm Văn Dũng", "sv004@student.edu.vn");
        assertEquals(Role.STUDENT, created.getRole());

        LibraryException ex = assertThrows(LibraryException.class, () ->
                service.addUser("sv001", "456", "Trùng mã", "trung@test.com"));
        assertEquals(ResponseStatus.ERROR, ex.getStatus());
        assertTrue(ex.getMessage().contains("đã tồn tại"));
    }

    @Test
    void testUnblockUser() throws LibraryException {
        List<User> users = service.getStorage().loadUsers();
        User sv1 = users.stream().filter(u -> u.getUsername().equals("sv001")).findFirst().orElseThrow();
        sv1.setBlocked(true);
        sv1.setViolationCount(3);
        service.getStorage().saveUsers(users);

        assertEquals(1, service.getBlockedUsers().size());

        User unblocked = service.unblockUser("sv001");
        assertFalse(unblocked.isBlocked());
        assertEquals(0, unblocked.getViolationCount());
        assertTrue(service.getBlockedUsers().isEmpty());
    }

    @Test
    void testEbookDownloadDistinction() {
        byte[] full = service.getEbook(1, true);
        byte[] preview = service.getEbook(1, false);

        assertNotNull(full);
        assertNotNull(preview);
        assertFalse(java.util.Arrays.equals(full, preview));
    }
}
