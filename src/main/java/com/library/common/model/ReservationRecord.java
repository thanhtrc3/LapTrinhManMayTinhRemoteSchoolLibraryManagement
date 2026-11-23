package com.library.common.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ReservationRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private long id;
    private int bookId;
    private String bookTitle;
    private String studentId;
    private LocalDateTime reservationDate;
    private LocalDateTime expiryDate;
    private ReservationStatus status;
    private String assignedCopyId;

    public ReservationRecord() {
    }

    public ReservationRecord(long id, int bookId, String bookTitle, String studentId, LocalDateTime reservationDate, LocalDateTime expiryDate, ReservationStatus status, String assignedCopyId) {
        this.id = id;
        this.bookId = bookId;
        this.bookTitle = bookTitle;
        this.studentId = studentId;
        this.reservationDate = reservationDate;
        this.expiryDate = expiryDate;
        this.status = status;
        this.assignedCopyId = assignedCopyId;
    }

    public ReservationRecord(long id, int bookId, String bookTitle, String studentId, LocalDateTime reservationDate) {
        this(id, bookId, bookTitle, studentId, reservationDate, null, ReservationStatus.PENDING, null);
    }

    public ReservationRecord(long id, int bookId, String bookTitle, String studentId, LocalDate reservationDate) {
        this(id, bookId, bookTitle, studentId, reservationDate == null ? null : reservationDate.atStartOfDay(), null, ReservationStatus.PENDING, null);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getBookId() {
        return bookId;
    }

    public void setBookId(int bookId) {
        this.bookId = bookId;
    }

    public String getBookTitle() {
        return bookTitle;
    }

    public void setBookTitle(String bookTitle) {
        this.bookTitle = bookTitle;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public LocalDateTime getReservationDate() {
        return reservationDate;
    }

    public void setReservationDate(LocalDateTime reservationDate) {
        this.reservationDate = reservationDate;
    }

    public void setReservationDate(LocalDate reservationDate) {
        this.reservationDate = reservationDate == null ? null : reservationDate.atStartOfDay();
    }

    public LocalDateTime getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDateTime expiryDate) {
        this.expiryDate = expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate == null ? null : expiryDate.atStartOfDay();
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public String getAssignedCopyId() {
        return assignedCopyId;
    }

    public void setAssignedCopyId(String assignedCopyId) {
        this.assignedCopyId = assignedCopyId;
    }
}
