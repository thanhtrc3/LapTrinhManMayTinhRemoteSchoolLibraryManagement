package com.library.server.service;

/** Lỗi nghiệp vụ, thông điệp được gửi thẳng về client để hiển thị cho người dùng. */
public class ServiceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ServiceException(String message) {
        super(message);
    }
}
