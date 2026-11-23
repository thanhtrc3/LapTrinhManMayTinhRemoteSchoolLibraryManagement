package com.library.server;

import com.library.client.network.LibraryClient;
import com.library.common.model.BorrowRecord;
import com.library.common.model.User;
import com.library.common.net.CommandType;
import com.library.common.net.Response;
import com.library.server.network.LibraryServer;
import com.library.server.service.LibraryService;
import com.library.server.storage.DataStorage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kiểm tra đường truyền TCP thật: server + client + các lệnh chính + realtime. */
public class ServerNetworkTest {
    private static final int PORT = 18888;
    private final List<String> failures = new ArrayList<>();
    private int total;

    @Test
    void networkFlow() throws Exception {
        Path dir = Files.createTempDirectory("library-net-check");
        LibraryService service = new LibraryService(new DataStorage(dir.toString()));
        LibraryServer server = new LibraryServer(service, PORT);
        Thread serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (IOException ignored) {
                // server đóng thì accept ném IOException, bỏ qua
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        try (LibraryClient client = connectWithRetry(); LibraryClient watcher = connectWithRetry()) {
            Response login = client.send(CommandType.LOGIN,
                    Map.of("username", "sv001", "password", "123"));
            check(login.isOk() && ((User) login.getData()).getUsername().equals("sv001"), "đăng nhập qua TCP");

            Response books = client.send(CommandType.GET_BOOKS, Map.of());
            check(books.isOk() && ((List<?>) books.getData()).size() == 5, "lấy danh sách sách qua TCP");

            // Client thứ hai chỉ ngồi nghe, không gửi lệnh nào.
            CountDownLatch realtime = new CountDownLatch(1);
            watcher.setEventListener(event -> realtime.countDown());

            Response borrow = client.send(CommandType.BORROW,
                    Map.of("username", "sv001", "bookId", 1, "days", 7));
            check(borrow.isOk(), "mượn sách qua TCP");
            long recordId = ((BorrowRecord) borrow.getData()).getId();
            check(realtime.await(3, TimeUnit.SECONDS), "client khác nhận realtime khi có người mượn sách");

            Response returns = client.send(CommandType.RETURN_BOOK,
                    Map.of("username", "sv001", "recordId", recordId));
            check(returns.isOk(), "trả sách qua TCP");

            Response reserve = client.send(CommandType.RESERVE, Map.of("username", "sv002", "bookId", 1));
            check(!reserve.isOk(), "không đặt trước được khi sách còn bản sao");

            Response denied = client.send(CommandType.GET_ALL_BORROWS, Map.of("username", "sv001"));
            check(!denied.isOk(), "sinh viên không xem được phiếu mượn toàn trường");

            Response allowed = client.send(CommandType.GET_ALL_BORROWS, Map.of("username", "admin"));
            check(allowed.isOk(), "thủ thư xem được phiếu mượn toàn trường");
        } finally {
            server.close();
        }
    }

    private static LibraryClient connectWithRetry() throws Exception {
        IOException last = null;
        for (int i = 0; i < 50; i++) {
            try {
                return new LibraryClient("127.0.0.1", PORT);
            } catch (IOException e) {
                last = e;
                Thread.sleep(100);
            }
        }
        throw last;
    }

    private void check(boolean condition, String label) {
        total++;
        if (!condition) {
            failures.add(label);
        }
    }

    @AfterEach
    void reportFailures() {
        assertTrue(failures.isEmpty(),
                () -> "Hỏng " + failures.size() + "/" + total + ":\n - " + String.join("\n - ", failures));
    }
}
