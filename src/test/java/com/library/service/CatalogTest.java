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
        assertEquals("BK06-C02", copies.get(1).getCopyId());
    }

    @Test
    void testDeleteBookWithActiveBorrowBlocked() throws LibraryException {
        BorrowRecord r = service.borrowBook("sv001", 1, 14);

        LibraryException ex = assertThrows(LibraryException.class, () -> service.deleteBook(1));
        assertEquals(ResponseStatus.ERROR, ex.getStatus());
        assertTrue(ex.getMessage().contains("Không thể xóa sách đang có bản sao"));
    }

    @Test
    void testAddAndRemoveCopy() throws LibraryException {
        BookCopy copy4 = service.addCopy(1);
        assertEquals("BK01-C04", copy4.getCopyId());

        List<BookCopy> copies = service.getCopies(1);
        assertEquals(4, copies.size());

        service.removeCopy("BK01-C04");
        assertEquals(3, service.getCopies(1).size());
    }

    @Test
    void testAddUserDuplicateRejected() {
        LibraryException ex = assertThrows(LibraryException.class, () ->
                service.addUser("sv001", "456", "Trùng mã", "trung@test.com", Role.STUDENT));
        assertEquals(ResponseStatus.ERROR, ex.getStatus());
        assertTrue(ex.getMessage().contains("đã tồn tại"));
    }

    @Test
    void testUnblockUser() throws LibraryException {
        User user = service.authenticate("sv001", "123");
        user.setBlocked(true);
        user.setViolationCount(3);
        service.getStorage().saveUsers(service.getStorage().loadUsers());

        User unblocked = service.unblockUser("sv001");
        assertFalse(unblocked.isBlocked());
        assertEquals(0, unblocked.getViolationCount());
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
