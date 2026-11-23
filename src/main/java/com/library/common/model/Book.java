package com.library.common.model;

import java.io.Serializable;

public class Book implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String isbn;
    private String title;
    private String author;
    private String category;
    private int publishYear;
    private int totalCopies;
    private int availableCopies;
    private boolean hasEbook;
    private String ebookFileName;
    /** Số bản đang giữ chỗ cho người đặt trước; chỉ dùng để hiển thị trên bảng sách. */
    private int heldCopies;

    public Book() {
    }

    public Book(int id, String isbn, String title, String author, String category, int publishYear, int totalCopies, int availableCopies, boolean hasEbook, String ebookFileName) {
        this.id = id;
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.category = category;
        this.publishYear = publishYear;
        this.totalCopies = totalCopies;
        this.availableCopies = availableCopies;
        this.hasEbook = hasEbook;
        this.ebookFileName = ebookFileName;
    }

    public Book(int id, String title, String author, String category, int publishYear, int totalCopies) {
        this(id, "ISBN-" + id, title, author, category, publishYear, totalCopies, totalCopies, false, null);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getPublishYear() {
        return publishYear;
    }

    public void setPublishYear(int publishYear) {
        this.publishYear = publishYear;
    }

    public int getTotalCopies() {
        return totalCopies;
    }

    public void setTotalCopies(int totalCopies) {
        this.totalCopies = totalCopies;
    }

    public int getAvailableCopies() {
        return availableCopies;
    }

    public void setAvailableCopies(int availableCopies) {
        this.availableCopies = availableCopies;
    }

    public boolean isHasEbook() {
        return hasEbook;
    }

    public boolean getHasEbook() {
        return hasEbook;
    }

    public void setHasEbook(boolean hasEbook) {
        this.hasEbook = hasEbook;
    }

    public String getEbookFileName() {
        return ebookFileName;
    }

    public void setEbookFileName(String ebookFileName) {
        this.ebookFileName = ebookFileName;
    }

    public int getHeldCopies() {
        return heldCopies;
    }

    public void setHeldCopies(int heldCopies) {
        this.heldCopies = heldCopies;
    }

    @Override
    public String toString() {
        return title;
    }
}
