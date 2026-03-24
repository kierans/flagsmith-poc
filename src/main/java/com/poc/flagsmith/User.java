package com.poc.flagsmith;

import java.util.Optional;

/**
 * Represents a user in the POC.
 * All users have a name and deviceId. Some users are additionally
 * assigned a userId once they log in or register.
 */
public class User {

    private final String name;
    private final String deviceId;
    private String userId; // nullable — assigned to 20 of the 50 users

    public User(String name, String deviceId) {
        this.name = name;
        this.deviceId = deviceId;
    }

    public User(String name, String deviceId, String userId) {
        this.name = name;
        this.deviceId = deviceId;
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Optional<String> getUserId() {
        return Optional.ofNullable(userId);
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public boolean isIdentified() {
        return userId != null;
    }

    @Override
    public String toString() {
        return String.format("User{name='%s', deviceId='%s', userId=%s}",
                name, deviceId, userId != null ? "'" + userId + "'" : "null");
    }
}
