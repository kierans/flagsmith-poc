package com.poc.flagsmith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Generates the 50 test users used in the POC.
 *
 * Each user gets:
 *   - A human-readable name
 *   - A UUID-based deviceId (simulates a mobile/browser device token)
 *
 * The first 20 users are additionally assigned a userId
 * (simulates users who have created an account / logged in).
 */
public class UserFactory {

    // Deterministic seed names so the POC is reproducible across runs
    private static final String[] FIRST_NAMES = {
        "Alice", "Bob", "Carol", "David", "Eve",
        "Frank", "Grace", "Heidi", "Ivan", "Judy",
        "Karl", "Laura", "Mallory", "Nathan", "Olivia",
        "Peter", "Quinn", "Rachel", "Steve", "Tina",
        "Uma", "Victor", "Wendy", "Xavier", "Yvonne",
        "Zach", "Amber", "Brian", "Chloe", "Derek",
        "Elena", "Felix", "Gloria", "Henry", "Iris",
        "James", "Kate", "Liam", "Mia", "Noah",
        "Olivia", "Paul", "Queenie", "Ryan", "Sophia",
        "Tom", "Ursula", "Vince", "Willow", "Xander"
    };

    /**
     * Build and return 50 users.
     * Users 0-19 (the first 20) will also have a userId assigned.
     */
    public static List<User> createUsers() {
        List<User> users = new ArrayList<>(50);

        for (int i = 0; i < 50; i++) {
            String name     = FIRST_NAMES[i] + "_" + (i + 1);           // e.g. "Alice_1"
            String deviceId = "device-" + stableUuid(name + "-device");  // deterministic UUID

            User user = new User(name, deviceId);

            // Place ALL 50 users in the carousel cohort.
            // To limit exposure (e.g. only 30 users), change the condition:
            //   e.g.  if (i < 30) user.setInCarouselCohort(true);
            user.setInCarouselCohort(true);

            // Assign a userId to the first 20 users
            if (i < 20) {
                String userId = "user-" + stableUuid(name + "-user");
                user.setUserId(userId);
            }

            users.add(user);
        }

        return Collections.unmodifiableList(users);
    }

    /**
     * Generates a name-based (deterministic) UUID so device/user IDs are
     * stable between runs without needing a database.
     */
    private static String stableUuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes()).toString();
    }
}
