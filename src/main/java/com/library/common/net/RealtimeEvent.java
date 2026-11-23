package com.library.common.net;

import java.io.Serializable;

public class RealtimeEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        DATA_CHANGED
    }

    private EventType type;
    private String targetUsername;
    private String message;
    private Object data;

    public RealtimeEvent() {
    }

    public RealtimeEvent(EventType type, String targetUsername, String message, Object data) {
        this.type = type;
        this.targetUsername = targetUsername;
        this.message = message;
        this.data = data;
    }

    public RealtimeEvent(EventType type, String message) {
        this(type, null, message, null);
    }

    public RealtimeEvent(Type type, String message) {
        this(EventType.BOOKS_CHANGED, null, message, null);
    }

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
        this.type = type;
    }

    public void setType(Type type) {
        this.type = type == Type.DATA_CHANGED ? EventType.BOOKS_CHANGED : null;
    }

    public Type getLegacyType() {
        return type == null ? null : Type.DATA_CHANGED;
    }

    public String getTargetUsername() {
        return targetUsername;
    }

    public void setTargetUsername(String targetUsername) {
        this.targetUsername = targetUsername;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
