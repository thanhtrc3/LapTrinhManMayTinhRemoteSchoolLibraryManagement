package com.library.common.model;

import java.io.Serializable;

public class BookCopy implements Serializable {
    private static final long serialVersionUID = 1L;

    private String copyId;
    private int bookId;
    private String barcode;
    private String qrData;
    private CopyStatus status;

    public BookCopy() {
    }

    public BookCopy(String copyId, int bookId, String barcode, String qrData, CopyStatus status) {
        this.copyId = copyId;
        this.bookId = bookId;
        this.barcode = barcode;
        this.qrData = qrData;
        this.status = status;
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

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getQrData() {
        return qrData;
    }

    public void setQrData(String qrData) {
        this.qrData = qrData;
    }

    public CopyStatus getStatus() {
        return status;
    }

    public void setStatus(CopyStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return copyId + " [" + status + "]";
    }
}
