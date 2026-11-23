package com.library.common.model;

public enum ReservationStatus {
    /** Đang xếp hàng chờ sách được trả. */
    PENDING,
    /** Sách đã về, đang giữ cho người đặt trong 48 giờ. */
    READY,
    FULFILLED,
    CANCELLED,
    EXPIRED
}
