package com.library.common.model;

import java.io.Serializable;
import java.time.LocalDate;

public class BorrowRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int id;
    private final int bookId;
    private final String bookTitle;
    private final String studentId;
    private final LocalDate borrowDate;
    private LocalDate dueDate;
    private LocalDate returnDate;
    private BorrowStatus status;

    public BorrowRecord(int id, int bookId, String bookTitle, String studentId, LocalDate borrowDate, LocalDate dueDate) {
        this.id = id;
        this.bookId = bookId;
        this.bookTitle = bookTitle;
        this.studentId = studentId;
        this.borrowDate = borrowDate;
        this.dueDate = dueDate;
        this.status = BorrowStatus.BORROWING;
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

    public LocalDate getBorrowDate() {
        return borrowDate;
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
}
