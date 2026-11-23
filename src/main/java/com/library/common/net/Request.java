package com.library.common.net;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Request implements Serializable {
    private static final long serialVersionUID = 1L;

    private long requestId;
    private CommandType command;
    private Map<String, Object> payload;

    public Request() {
        this.payload = new HashMap<>();
    }

    public Request(CommandType command, Map<String, Object> payload) {
        this.command = command;
        setPayload(payload);
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public CommandType getCommand() {
        return command;
    }

    public void setCommand(CommandType command) {
        this.command = command;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload == null ? new HashMap<>() : new HashMap<>(payload);
    }

    private Object readResolve() {
        if (payload == null) {
            payload = new HashMap<>();
        }
        return this;
    }
}
