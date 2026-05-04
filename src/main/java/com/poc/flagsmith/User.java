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
    private String userId;          // nullable — assigned to 20 of the 50 users
    private boolean inCarouselCohort; // whether this identity carries member_carousel_ab_test_cohort=true

    public User(String name, String deviceId) {
        this.name = name;
        this.deviceId = deviceId;
    }

    public User(String name, String deviceId, String userId) {
        this.name = name;
        this.deviceId = deviceId;
        this.userId = userId;
    }

    public User(String name, String deviceId, String userId, boolean inCarouselCohort) {
        this.name = name;
        this.deviceId = deviceId;
        this.userId = userId;
        this.inCarouselCohort = inCarouselCohort;
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

    public boolean isInCarouselCohort() {
        return inCarouselCohort;
    }

    public void setInCarouselCohort(boolean inCarouselCohort) {
        this.inCarouselCohort = inCarouselCohort;
    }

    @Override
    public String toString() {
        return String.format("User{name='%s', deviceId='%s', userId=%s, carouselCohort=%s}",
            name, deviceId, userId != null ? "'" + userId + "'" : "null",
            inCarouselCohort);
    }
}
