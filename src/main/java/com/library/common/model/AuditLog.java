package com.library.common.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class AuditLog implements Serializable {
    private static final long serialVersionUID = 1L;

    private long id;
    private LocalDateTime timestamp;
    private AuditAction action;
    private String actorUsername;
    private String details;

    public AuditLog() {
    }

    public AuditLog(long id, LocalDateTime timestamp, AuditAction action, String actorUsername, String details) {
        this.id = id;
        this.timestamp = timestamp;
        this.action = action;
        this.actorUsername = actorUsername;
        this.details = details;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public AuditAction getAction() {
        return action;
    }

    public void setAction(AuditAction action) {
        this.action = action;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public void setActorUsername(String actorUsername) {
        this.actorUsername = actorUsername;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
