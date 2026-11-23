package com.library.storage;

import com.library.common.model.*;
import com.library.server.storage.DataStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DataStorageTest {

    @TempDir
    Path tempDir;

    private DataStorage storage;

    @BeforeEach
    void setUp() {
        storage = new DataStorage(tempDir);
    }

    @Test
    void testInitialSeed() throws IOException {
        List<User> users = storage.loadUsers();
        assertEquals(4, users.size(), "Phải có đúng 4 tài khoản seed ban đầu (admin, sv001, sv002, sv003)");
        assertTrue(users.stream().anyMatch(u -> u.getUsername().equals("admin") && u.getRole() == Role.LIBRARIAN));
        assertTrue(users.stream().anyMatch(u -> u.getUsername().equals("sv001") && u.getRole() == Role.STUDENT));

        List<Book> books = storage.loadBooks();
        assertEquals(5, books.size(), "Phải có đúng 5 tựa sách mẫu");
        assertTrue(books.stream().anyMatch(b -> b.getTitle().equals("Lập trình mạng máy tính")));

        List<BookCopy> copies = storage.loadCopies();
        assertEquals(15, copies.size(), "Phải có đúng 15 bản sao vật lý BK01-C01...BK05-C03");
        assertEquals("BK01-C01", copies.get(0).getCopyId());
        assertEquals(CopyStatus.AVAILABLE, copies.get(0).getStatus());

        try (var pdfs = Files.list(storage.getEbooksDir())) {
            assertEquals(15, pdfs.count(), "Phải có đúng 15 file PDF (5 full, 5 preview, 5 standard)");
        }
    }

    @Test
    void testNoReSeedOnSecondLoad() {
        List<User> users = storage.loadUsers();
        users.add(new User("sv999", "123", "Sinh viên mới", Role.STUDENT, "sv999@test.edu.vn", 3, 0, false));
        storage.saveUsers(users);

        DataStorage storage2 = new DataStorage(tempDir);
        List<User> reloaded = storage2.loadUsers();
        assertEquals(5, reloaded.size(), "Lần khởi động thứ hai không được ghi đè seed làm mất sinh viên mới");
        assertTrue(reloaded.stream().anyMatch(u -> u.getUsername().equals("sv999")));
    }

    @Test
    void testResetSeed() {
        List<User> users = storage.loadUsers();
        users.clear();
        storage.saveUsers(users);
        assertEquals(0, storage.loadUsers().size());

        storage.resetSeed();
        assertEquals(4, storage.loadUsers().size(), "Sau resetSeed phải phục hồi lại 4 tài khoản mẫu ban đầu");
        assertEquals(5, storage.loadBooks().size());
        assertEquals(15, storage.loadCopies().size());
    }

    @Test
    void testEbookDownloadDistinction() {
        byte[] fullPdf = storage.getEbookBytes(1, true);
        byte[] previewPdf = storage.getEbookBytes(1, false);

        assertNotNull(fullPdf);
        assertNotNull(previewPdf);
        assertTrue(fullPdf.length > 0);
        assertTrue(previewPdf.length > 0);
        assertFalse(java.util.Arrays.equals(fullPdf, previewPdf), "Nội dung bản full và bản preview phải khác nhau");
    }
}
