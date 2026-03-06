package com.sudarshanchakra.alert.model;

public enum AlertStatus {
    NEW("new"),
    ACKNOWLEDGED("acknowledged"),
    RESOLVED("resolved"),
    FALSE_POSITIVE("false_positive");

    private final String value;

    AlertStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AlertStatus fromValue(String value) {
        for (AlertStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown alert status: " + value);
    }
}
