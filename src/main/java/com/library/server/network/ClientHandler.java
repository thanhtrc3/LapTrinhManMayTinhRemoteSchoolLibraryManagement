package com.library.server.network;

import com.library.common.net.CommandType;
import com.library.common.net.RealtimeEvent;
import com.library.common.net.Request;
import com.library.common.net.Response;
import com.library.server.service.LibraryService;
import com.library.server.service.ServiceException;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Map;

/** Phục vụ một kết nối TCP: đọc Request, trả Response, và nhận RealtimeEvent từ server. */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final LibraryService service;
    private final LibraryServer server;
    private ObjectOutputStream out;

    public ClientHandler(Socket socket, LibraryService service, LibraryServer server) {
        this.socket = socket;
        this.service = service;
        this.server = server;
    }

    @Override
    public void run() {
        server.register(this);
        System.out.println("[TCP] Client kết nối: " + socket.getRemoteSocketAddress());
        try (Socket s = socket) {
            out = new ObjectOutputStream(s.getOutputStream());
            // ObjectOutputStream phải được tạo và flush trước ObjectInputStream để tránh deadlock.
            out.flush();
            ObjectInputStream in = new ObjectInputStream(s.getInputStream());
            while (true) {
                Object incoming = in.readObject();
                if (!(incoming instanceof Request)) {
                    break;
                }
                Request request = (Request) incoming;
                Response response = dispatch(request);
                write(response);
                if (response.isOk() && changesData(request.getType())) {
                    server.broadcast(new RealtimeEvent(RealtimeEvent.Type.DATA_CHANGED,
                            "Dữ liệu thư viện vừa thay đổi"));
                }
            }
        } catch (IOException e) {
            System.out.println("[TCP] Client ngắt kết nối: " + socket.getRemoteSocketAddress());
        } catch (ClassNotFoundException e) {
            System.out.println("[TCP] Gói tin không hợp lệ từ " + socket.getRemoteSocketAddress());
        } finally {
            server.unregister(this);
        }
    }

    /** Server gọi từ luồng khác để đẩy sự kiện xuống client này. */
    public void sendEvent(RealtimeEvent event) {
        try {
            write(event);
        } catch (IOException ignored) {
            // client đã ngắt, vòng lặp run() sẽ tự dọn dẹp
        }
    }

    private synchronized void write(Object message) throws IOException {
        if (out == null) {
            return;
        }
        out.reset();
        out.writeObject(message);
        out.flush();
    }

    private static boolean changesData(CommandType type) {
        switch (type) {
            case BORROW:
            case RETURN_BOOK:
            case ADD_BOOK:
            case UPDATE_BOOK:
            case DELETE_BOOK:
            case RESERVE:
            case CANCEL_RESERVATION:
                return true;
            default:
                return false;
        }
    }

    private Response dispatch(Request request) {
        try {
            Map<String, Object> d = request.getData() == null ? Map.of() : request.getData();
            switch (request.getType()) {
                case LOGIN:
                    return Response.ok("Đăng nhập thành công",
                            service.login(text(d, "username"), text(d, "password")));
                case GET_BOOKS:
                    return Response.ok("", service.getBooks());
                case BORROW:
                    return Response.ok("Mượn sách thành công",
                            service.borrow(text(d, "username"), number(d, "bookId"), number(d, "days")));
                case RETURN_BOOK:
                    return Response.ok("Trả sách thành công",
                            service.returnBook(number(d, "recordId"), text(d, "username")));
                case GET_MY_BORROWS:
                    return Response.ok("", service.myBorrows(text(d, "username")));
                case GET_ALL_BORROWS:
                    service.requireLibrarian(text(d, "username"));
                    return Response.ok("", service.allBorrows());
                case RESERVE:
                    return Response.ok("Đã đặt trước sách",
                            service.reserve(text(d, "username"), number(d, "bookId")));
                case CANCEL_RESERVATION:
                    service.cancelReservation(number(d, "reservationId"), text(d, "username"));
                    return Response.ok("Đã hủy đặt trước", null);
                case GET_MY_RESERVATIONS:
                    return Response.ok("", service.myReservations(text(d, "username")));
                case ADD_BOOK:
                    service.requireLibrarian(text(d, "username"));
                    return Response.ok("Đã thêm sách", service.addBook(
                            text(d, "title"), text(d, "author"), text(d, "category"),
                            number(d, "year"), number(d, "copies")));
                case UPDATE_BOOK:
                    service.requireLibrarian(text(d, "username"));
                    return Response.ok("Đã cập nhật sách", service.updateBook(
                            number(d, "bookId"), text(d, "title"), text(d, "author"),
                            text(d, "category"), number(d, "year"), number(d, "copies")));
                case DELETE_BOOK:
                    service.requireLibrarian(text(d, "username"));
                    service.deleteBook(number(d, "bookId"));
                    return Response.ok("Đã xóa sách", null);
                default:
                    return Response.error("Lệnh không được hỗ trợ: " + request.getType());
            }
        } catch (ServiceException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            e.printStackTrace();
            return Response.error("Lỗi máy chủ: " + e.getMessage());
        }
    }

    private static String text(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value == null ? "" : value.toString();
    }

    private static int number(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
