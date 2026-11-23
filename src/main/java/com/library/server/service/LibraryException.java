package com.library.server.service;

import com.library.common.net.ResponseStatus;

public class LibraryException extends Exception {
    private static final long serialVersionUID = 1L;

    private final ResponseStatus status;

    public LibraryException(ResponseStatus status, String message) {
        super(message);
        this.status = status;
    }

    public LibraryException(String message) {
        this(ResponseStatus.ERROR, message);
    }

    public ResponseStatus getStatus() {
        return status;
    }
}
