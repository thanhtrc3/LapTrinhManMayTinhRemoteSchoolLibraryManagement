package com.library.common.net;

import java.io.Serializable;

public class MulticastMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String title;
    private String content;
    private long sentAt;
    private String sender;

    public MulticastMessage() {
    }

    public MulticastMessage(String title, String content, long sentAt, String sender) {
        this.title = title;
        this.content = content;
        this.sentAt = sentAt;
        this.sender = sender;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getSentAt() {
        return sentAt;
    }

    public void setSentAt(long sentAt) {
        this.sentAt = sentAt;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }
}
