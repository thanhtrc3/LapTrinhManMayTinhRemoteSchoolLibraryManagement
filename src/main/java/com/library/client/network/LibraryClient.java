package com.library.client.network;

import com.library.common.net.CommandType;
import com.library.common.net.RealtimeEvent;
import com.library.common.net.Request;
import com.library.common.net.Response;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Kết nối TCP tới server. Một luồng nền đọc mọi gói tin: Response được chuyển cho
 * lệnh đang chờ, RealtimeEvent được đẩy cho listener (listener chạy trên luồng nền,
 * không được gọi send() trực tiếp ở đó).
 */
public class LibraryClient implements AutoCloseable {
    private static final int RESPONSE_TIMEOUT_SECONDS = 15;

    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;
    private final BlockingQueue<Response> responses = new LinkedBlockingQueue<>();
    private volatile Consumer<RealtimeEvent> eventListener;

    public LibraryClient(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.out.flush();
        this.in = new ObjectInputStream(socket.getInputStream());
        Thread reader = new Thread(this::readLoop, "library-client-reader");
        reader.setDaemon(true);
        reader.start();
    }

    public void setEventListener(Consumer<RealtimeEvent> listener) {
        this.eventListener = listener;
    }

    private void readLoop() {
        try {
            while (!socket.isClosed()) {
                Object message = in.readObject();
                if (message instanceof Response) {
                    responses.add((Response) message);
                } else if (message instanceof RealtimeEvent) {
                    Consumer<RealtimeEvent> listener = eventListener;
                    if (listener != null) {
                        try {
                            listener.accept((RealtimeEvent) message);
                        } catch (RuntimeException ignored) {
                            // lỗi phía giao diện không được làm chết luồng đọc
                        }
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            // server đóng kết nối hoặc socket bị close()
        }
    }

    public synchronized Response send(CommandType type, Map<String, Object> data) throws IOException {
        out.reset();
        out.writeObject(Request.of(type, data));
        out.flush();
        try {
            Response response = responses.poll(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (response == null) {
                throw new IOException("Server không phản hồi trong " + RESPONSE_TIMEOUT_SECONDS + " giây.");
            }
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Bị ngắt khi chờ phản hồi", e);
        }
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // đóng kết nối hỏng cũng không cần xử lý
        }
    }
}
