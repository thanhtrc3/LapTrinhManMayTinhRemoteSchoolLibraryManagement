package com.library.common.net;

import java.io.Serializable;

/** Sự kiện server đẩy xuống client qua kết nối TCP đang mở (không phải phản hồi cho một lệnh). */
public class RealtimeEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        DATA_CHANGED
    }

    private final Type type;
    private final String message;

    public RealtimeEvent(Type type, String message) {
        this.type = type;
        this.message = message;
    }

    public Type getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }
}
