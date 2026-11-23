package com.library.common.net;

import java.io.Serializable;

public class RealtimeEvent implements Serializable {
    private static final long serialVersionUID = 1L;

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

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
        this.type = type;
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
