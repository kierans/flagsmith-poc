package com.poc.flagsmith;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collects and prints a formatted summary of flag evaluation results.
 */
public class ResultSummary {

    // --- Phase 1: all 50 users by deviceId ---
    private final Map<String, FlagVariant> deviceResults = new LinkedHashMap<>();

    // --- Phase 2: identified users — variant under userId identity ---
    private final Map<String, FlagVariant> userIdResults = new LinkedHashMap<>();

    // --- Phase 2: variance check — did the bucket change after override? ---
    private final Map<String, Boolean> overridePreserved = new LinkedHashMap<>();

    public void recordDeviceResult(User user, FlagVariant variant) {
        deviceResults.put(user.getDeviceId(), variant);
    }

    public void recordUserIdResult(User user, FlagVariant variant, FlagVariant deviceVariant) {
        String uid = user.getUserId().orElseThrow();
        userIdResults.put(uid, variant);
        overridePreserved.put(uid, variant == deviceVariant);
    }

    public void printReport() {
        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("  FLAGSMITH A/B TEST POC — RESULTS REPORT");
        System.out.println("=".repeat(80));

        // ---- Phase 1 -------------------------------------------------------
        System.out.println("\n📱  PHASE 1 — All 50 users evaluated by deviceId\n");
        System.out.printf("  %-36s  %-24s  %s%n", "Device ID", "Name", "Variant");
        System.out.println("  " + "-".repeat(72));

        long controlCount = 0, testCount = 0, disabledCount = 0;

        for (FlagVariant v : deviceResults.values()) {
            switch (v) {
                case CONTROL  -> controlCount++;
                case TEST     -> testCount++;
                case DISABLED -> disabledCount++;
            }
        }

        // We need names — keep a parallel list
        for (Map.Entry<String, FlagVariant> e : deviceResults.entrySet()) {
            System.out.printf("  %-36s  %s%n", e.getKey(), variantLabel(e.getValue()));
        }

        System.out.println();
        System.out.printf("  Totals → control: %d | test: %d | disabled: %d%n",
                controlCount, testCount, disabledCount);
        System.out.printf("  Expected split  → control: ~45 (90%%) | test: ~5 (10%%)%n");

        // ---- Phase 2 -------------------------------------------------------
        System.out.println("\n👤  PHASE 2 — 20 identified users: override applied via userId\n");
        System.out.printf("  %-36s  %-14s  %-14s  %s%n",
                "User ID", "Device Var.", "UserId Var.", "Preserved?");
        System.out.println("  " + "-".repeat(72));

        long preserved = 0, drifted = 0;

        for (Map.Entry<String, Boolean> e : overridePreserved.entrySet()) {
            String uid          = e.getKey();
            FlagVariant uv      = userIdResults.get(uid);
            // Look up the device variant via the same ordering
            FlagVariant dv      = lookupDeviceVariantForUserId(uid);
            boolean ok          = e.getValue();
            if (ok) preserved++; else drifted++;

            System.out.printf("  %-36s  %-14s  %-14s  %s%n",
                    uid,
                    variantLabel(dv),
                    variantLabel(uv),
                    ok ? "✅ YES" : "⚠️  NO — mismatch");
        }

        System.out.println();
        System.out.printf("  Override fidelity: %d/%d preserved (%.0f%%)%n",
                preserved, preserved + drifted,
                (preserved + drifted) > 0 ? 100.0 * preserved / (preserved + drifted) : 0);

        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("  NOTE: If you are running against a REAL Flagsmith environment,");
        System.out.println("  configure your API key in src/main/resources/config.properties");
        System.out.println("  and set up the carousel_ab_test multivariate flag per the README.");
        System.out.println("=".repeat(80));
        System.out.println();
    }

    /** Stored as deviceId → variant in phase-1 map; look up by iterating. */
    private FlagVariant lookupDeviceVariantForUserId(String userId) {
        // The override map insertion order mirrors phase-2 user ordering.
        // We stored device results keyed by deviceId; we find the matching
        // entry by checking our overridePreserved iteration order.
        // Simple approach: store in parallel list during recording.
        return deviceVariantForUser.getOrDefault(userId, FlagVariant.DISABLED);
    }

    // extra map: userId → device variant (populated during recordUserIdResult)
    private final Map<String, FlagVariant> deviceVariantForUser = new LinkedHashMap<>();

    @Override
    public String toString() {
        return "ResultSummary{deviceResults=" + deviceResults.size()
                + ", userIdResults=" + userIdResults.size() + "}";
    }

    public void recordDeviceVariantForUser(String userId, FlagVariant deviceVariant) {
        deviceVariantForUser.put(userId, deviceVariant);
    }

    private String variantLabel(FlagVariant v) {
        return switch (v) {
            case CONTROL  -> "⬜ control";
            case TEST     -> "🔵 test";
            case DISABLED -> "🔴 disabled";
        };
    }
}
