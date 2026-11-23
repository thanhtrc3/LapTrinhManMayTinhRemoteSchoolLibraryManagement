package com.library.common.net;

import java.io.Serializable;
import java.util.Map;

public class Request implements Serializable {
    private static final long serialVersionUID = 1L;

    private final CommandType type;
    private final Map<String, Object> data;

    public Request(CommandType type, Map<String, Object> data) {
        this.type = type;
        this.data = data;
    }

    public static Request of(CommandType type, Map<String, Object> data) {
        return new Request(type, data);
    }

    public CommandType getType() {
        return type;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
