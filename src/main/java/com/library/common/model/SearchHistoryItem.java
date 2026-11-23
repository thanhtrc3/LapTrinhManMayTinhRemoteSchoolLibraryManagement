package com.library.common.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class SearchHistoryItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private String studentId;
    private String searchKeyword;
    private String searchCategory;
    private LocalDateTime timestamp;

    public SearchHistoryItem() {
    }

    public SearchHistoryItem(String studentId, String searchKeyword, String searchCategory, LocalDateTime timestamp) {
        this.studentId = studentId;
        this.searchKeyword = searchKeyword;
        this.searchCategory = searchCategory;
        this.timestamp = timestamp;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getSearchKeyword() {
        return searchKeyword;
    }

    public void setSearchKeyword(String searchKeyword) {
        this.searchKeyword = searchKeyword;
    }

    public String getSearchCategory() {
        return searchCategory;
    }

    public void setSearchCategory(String searchCategory) {
        this.searchCategory = searchCategory;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
