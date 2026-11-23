package com.library.common.model;

import java.io.Serializable;

public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private String password;
    private String fullName;
    private Role role;
    private String email;
    private int maxBorrowQuota = 3;
    private int violationCount;
    private boolean blocked;

    public User() {
        this.maxBorrowQuota = 3;
    }

    public User(String username, String password, String fullName, Role role, String email, int maxBorrowQuota, int violationCount, boolean blocked) {
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
        this.email = email;
        this.maxBorrowQuota = maxBorrowQuota;
        this.violationCount = violationCount;
        this.blocked = blocked;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public int getMaxBorrowQuota() {
        return maxBorrowQuota;
    }

    public void setMaxBorrowQuota(int maxBorrowQuota) {
        this.maxBorrowQuota = maxBorrowQuota;
    }

    public int getViolationCount() {
        return violationCount;
    }

    public void setViolationCount(int violationCount) {
        this.violationCount = violationCount;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    @Override
    public String toString() {
        return fullName + " (" + username + ")";
    }
}
