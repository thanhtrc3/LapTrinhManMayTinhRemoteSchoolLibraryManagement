package com.library.common.net;

import java.io.Serializable;

public class Response implements Serializable {
    private static final long serialVersionUID = 1L;

    private final ResponseStatus status;
    private final String message;
    private final Object data;

    public Response(ResponseStatus status, String message, Object data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }

    public static Response ok(String message, Object data) {
        return new Response(ResponseStatus.OK, message, data);
    }

    public static Response error(String message) {
        return new Response(ResponseStatus.ERROR, message, null);
    }

    public boolean isOk() {
        return status == ResponseStatus.OK;
    }

    public ResponseStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }
}
