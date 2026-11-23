package com.library.common.model;

import java.io.Serializable;
import java.time.LocalDate;

public class BorrowRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private long id;
    private String copyId;
    private int bookId;
    private String bookTitle;
    private String studentId;
    private LocalDate borrowDate;
    private LocalDate dueDate;
    private LocalDate returnDate;
    private BorrowStatus status;
    private int renewCount;

    public BorrowRecord() {
    }

    public BorrowRecord(long id, String copyId, int bookId, String bookTitle, String studentId, LocalDate borrowDate, LocalDate dueDate, LocalDate returnDate, BorrowStatus status, int renewCount) {
        this.id = id;
        this.copyId = copyId;
        this.bookId = bookId;
        this.bookTitle = bookTitle;
        this.studentId = studentId;
        this.borrowDate = borrowDate;
        this.dueDate = dueDate;
        this.returnDate = returnDate;
        this.status = status;
        this.renewCount = renewCount;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getCopyId() {
        return copyId;
    }

    public void setCopyId(String copyId) {
        this.copyId = copyId;
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

    public LocalDate getBorrowDate() {
        return borrowDate;
    }

    public void setBorrowDate(LocalDate borrowDate) {
        this.borrowDate = borrowDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(LocalDate returnDate) {
        this.returnDate = returnDate;
    }

    public BorrowStatus getStatus() {
        return status;
    }

    public void setStatus(BorrowStatus status) {
        this.status = status;
    }

    public int getRenewCount() {
        return renewCount;
    }

    public void setRenewCount(int renewCount) {
        this.renewCount = renewCount;
    }
}
