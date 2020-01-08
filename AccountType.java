package com.wizzard.model;

import org.json.simple.JSONObject;
public enum AccountType {


    SAVINGS("SAVINGS", 1),
    CURRENT("CURRENT" ,2);

    private final String key;
    private int value;

    AccountType(final String key) {
        this.key = key;
    }
    AccountType(final String key, int value) {
        this.key = key;
        this.value = value;
    }
    @Override
    public String toString() {
        return this.key;
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("key", this.key);
        json.put("value", this.value);
        return json;
    }
}
