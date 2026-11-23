package com.library.common.net;

import java.io.Serializable;

public class Response implements Serializable {
    private static final long serialVersionUID = 1L;

    private long requestId;
    private ResponseStatus status;
    private String message;
    private Object data;

    public Response() {
    }

    public Response(long requestId, ResponseStatus status, String message, Object data) {
        this.requestId = requestId;
        this.status = status;
        this.message = message;
        this.data = data;
    }

    public Response(ResponseStatus status, String message, Object data) {
        this(0L, status, message, data);
    }

    public static Response ok(String message, Object data) {
        return new Response(0L, ResponseStatus.OK, message, data);
    }

    public static Response error(String message) {
        return new Response(0L, ResponseStatus.ERROR, message, null);
    }

    public boolean isOk() {
        return status == ResponseStatus.OK;
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public ResponseStatus getStatus() {
        return status;
    }

    public void setStatus(ResponseStatus status) {
        this.status = status;
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
