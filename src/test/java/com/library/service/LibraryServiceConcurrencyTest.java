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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

public class LibraryServiceConcurrencyTest {

    @TempDir
    Path tempDir;

    private LibraryService service;

    @BeforeEach
    void setUp() {
        DataStorage storage = new DataStorage(tempDir);
        service = new LibraryService(storage);
    }

    @Test
    void testTenThreadsBorrowLastCopy() throws InterruptedException {
        // Sách ID 1 ban đầu có 3 bản sao. Mượn trước 2 bản sao -> còn đúng 1 bản sao khả dụng.
        try {
            service.borrowBook("sv001", 1, 14);
            service.borrowBook("sv002", 1, 14);
        } catch (LibraryException e) {
            fail("Setup mượn thất bại: " + e.getMessage());
        }

        // Tạo sẵn 10 user khác nhau
        for (int i = 10; i <= 20; i++) {
            try {
                service.addUser("user" + i, "123", "Sinh viên " + i, "u" + i + "@test.com", Role.STUDENT);
            } catch (LibraryException ignored) {
            }
        }

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger outOfStockCount = new AtomicInteger(0);

        for (int i = 10; i < 10 + threadCount; i++) {
            final String username = "user" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    service.borrowBook(username, 1, 14);
                    successCount.incrementAndGet();
                } catch (LibraryException e) {
                    if (e.getStatus() == ResponseStatus.OUT_OF_STOCK) {
                        outOfStockCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Bắt đầu 10 luồng cùng lúc
        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        // Khóa hạt mịn đảm bảo: đúng 1 thành công và 9 thất bại do OUT_OF_STOCK
        assertEquals(1, successCount.get(), "Đúng 1 luồng mượn thành công cuốn cuối cùng");
        assertEquals(9, outOfStockCount.get(), "9 luồng còn lại phải nhận OUT_OF_STOCK");
    }

    @Test
    void testTwoDifferentBooksNotBlockingEachOther() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch lock1Held = new CountDownLatch(1);
        CountDownLatch releaseLock1 = new CountDownLatch(1);

        // Luồng 1 giữ khóa ReentrantLock của sách 1
        Future<?> holder = executor.submit(() -> {
            ReentrantLock lock1 = service.getLock(1);
            lock1.lock();
            try {
                lock1Held.countDown();
                releaseLock1.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock1.unlock();
            }
        });

        assertTrue(lock1Held.await(1, TimeUnit.SECONDS));

        // Khi sách 1 đang bị giữ khóa, luồng 2 mượn sách 2 vẫn chạy xong ngay lập tức mà không bị chặn
        long startNs = System.nanoTime();
        Future<BorrowRecord> f2 = executor.submit(() -> service.borrowBook("sv002", 2, 14));
        BorrowRecord r2 = f2.get(500, TimeUnit.MILLISECONDS);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

        releaseLock1.countDown();
        holder.get(1, TimeUnit.SECONDS);
        executor.shutdown();

        assertNotNull(r2);
        assertEquals(2, r2.getBookId());
        assertTrue(elapsedMs < 500, "Mượn sách 2 không được bị chặn bởi khóa của sách 1");
    }
}
