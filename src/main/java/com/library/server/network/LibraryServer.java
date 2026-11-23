package com.library.server.network;

import com.library.common.net.RealtimeEvent;
import com.library.server.service.LibraryService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** TCP server đa luồng: mỗi kết nối được phục vụ bởi một luồng trong thread pool. */
public class LibraryServer implements AutoCloseable {
    private final LibraryService service;
    private final int port;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private ServerSocket serverSocket;
    private volatile boolean running;

    public LibraryServer(LibraryService service, int port) {
        this.service = service;
        this.port = port;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("[SERVER] Thư viện đang chạy tại cổng " + port + " ...");
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                pool.execute(new ClientHandler(socket, service, this));
            } catch (IOException e) {
                if (running) {
                    System.out.println("[SERVER] Lỗi nhận kết nối: " + e.getMessage());
                }
            }
        }
    }

    void register(ClientHandler client) {
        clients.add(client);
    }

    void unregister(ClientHandler client) {
        clients.remove(client);
    }

    /** Đẩy sự kiện tới mọi client đang kết nối. */
    public void broadcast(RealtimeEvent event) {
        for (ClientHandler client : clients) {
            client.sendEvent(event);
        }
    }

    public int onlineClients() {
        return clients.size();
    }

    @Override
    public void close() throws IOException {
        running = false;
        pool.shutdownNow();
        if (serverSocket != null) {
            serverSocket.close();
        }
    }
}
