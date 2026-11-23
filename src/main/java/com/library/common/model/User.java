package com.library.common.model;

import java.io.Serializable;

public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String username;
    private final String password;
    private final String fullName;
    private final Role role;

    public User(String username, String password, String fullName, Role role) {
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public String toString() {
        return fullName + " (" + username + ")";
    }
}
