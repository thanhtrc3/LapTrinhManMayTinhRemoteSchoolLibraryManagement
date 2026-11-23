package com.library.common.model;

import java.io.Serializable;

public class Book implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int id;
    private String title;
    private String author;
    private String category;
    private int publishYear;
    private int totalCopies;
    private int availableCopies;
    /** Số bản đang giữ chỗ cho người đặt trước; chỉ dùng để hiển thị, server tính lại mỗi lần trả danh sách. */
    private int heldCopies;

    public Book(int id, String title, String author, String category, int publishYear, int totalCopies) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.category = category;
        this.publishYear = publishYear;
        this.totalCopies = totalCopies;
        this.availableCopies = totalCopies;
    }

    public int getId() {
        return id;
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
