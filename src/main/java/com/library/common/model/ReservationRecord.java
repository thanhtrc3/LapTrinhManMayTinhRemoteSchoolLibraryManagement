package com.library.common.model;

import java.io.Serializable;
import java.time.LocalDate;

public class ReservationRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int id;
    private final int bookId;
    private final String bookTitle;
    private final String studentId;
    private final LocalDate reservationDate;
    private LocalDate expiryDate;
    private ReservationStatus status;

    public ReservationRecord(int id, int bookId, String bookTitle, String studentId, LocalDate reservationDate) {
        this.id = id;
        this.bookId = bookId;
        this.bookTitle = bookTitle;
        this.studentId = studentId;
        this.reservationDate = reservationDate;
        this.status = ReservationStatus.PENDING;
    }

    public int getId() {
        return id;
    }

    public int getBookId() {
        return bookId;
    }

    public String getBookTitle() {
        return bookTitle;
    }

    public String getStudentId() {
        return studentId;
    }

    public LocalDate getReservationDate() {
        return reservationDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }
}
