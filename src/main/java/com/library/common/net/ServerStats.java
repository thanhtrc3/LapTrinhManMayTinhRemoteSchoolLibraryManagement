package com.library.common.net;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ServerStats implements Serializable {
    private static final long serialVersionUID = 1L;

    private int onlineClients;
    private List<String> clientAddresses;
    private long avgPingMs;
    private long totalBytesSent;

    public ServerStats() {
        this.clientAddresses = new ArrayList<>();
    }

    public ServerStats(int onlineClients, List<String> clientAddresses, long avgPingMs, long totalBytesSent) {
        this.onlineClients = onlineClients;
        setClientAddresses(clientAddresses);
        this.avgPingMs = avgPingMs;
        this.totalBytesSent = totalBytesSent;
    }

    public int getOnlineClients() {
        return onlineClients;
    }

    public void setOnlineClients(int onlineClients) {
        this.onlineClients = onlineClients;
    }

    public List<String> getClientAddresses() {
        return clientAddresses;
    }

    public void setClientAddresses(List<String> clientAddresses) {
        this.clientAddresses = clientAddresses == null ? new ArrayList<>() : clientAddresses;
    }

    public long getAvgPingMs() {
        return avgPingMs;
    }

    public void setAvgPingMs(long avgPingMs) {
        this.avgPingMs = avgPingMs;
    }

    public long getTotalBytesSent() {
        return totalBytesSent;
    }

    public void setTotalBytesSent(long totalBytesSent) {
        this.totalBytesSent = totalBytesSent;
    }
}
