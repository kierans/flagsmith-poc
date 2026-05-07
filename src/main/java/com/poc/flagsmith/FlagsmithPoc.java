package com.poc.flagsmith;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the Flagsmith A/B testing POC.
 * <br>
 * What this does:
 * ──────────────
 *  Phase 1  Evaluate the carousel_ab_test multivariate flag for all 50 users
 *           using their deviceId as the Flagsmith identity key.
 *           Users carry member_carousel_ab_test_cohort=true, placing them in the
 *           Carousel_Experiment segment.  The 90/10 split is applied only
 *           within that segment — users outside it get the default value.
 *
 *  Phase 2  Take the 20 users who have a userId, apply an identity override so
 *           that their userId identity is bucketed into the SAME variant as their
 *           deviceId identity.  Then verify that the variant is consistent.
 * <br>
 * Configuration:
 *   src/main/resources/config.properties → set flagsmith.api.key
 *   OR pass -Dflagsmith.api.key=<key> on the command line.
 */
public class FlagsmithPoc {

    private static final Logger log = LoggerFactory.getLogger(FlagsmithPoc.class);

    public static void main(String[] args) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║         FLAGSMITH A/B TESTING POC  —  CAROUSEL FLAG         ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        ConfigService configService = new ConfigService();
        String baseUrl = configService.resolveEdgeApiUrl();
        String apiKey = configService.resolveApiKey();

        log.info("Starting POC with Flagsmith API key: {}...", apiKey.substring(0, Math.min(8, apiKey.length())));

        FlagsmithService service = new FlagsmithService(baseUrl, apiKey);
        List<User> users = UserFactory.createUsers();
        ResultSummary summary = new ResultSummary();

        // ====================================================================
        // PHASE 1 — Evaluate all 50 users by deviceId
        // ====================================================================
        System.out.println("─".repeat(60));
        System.out.println("  Phase 1: Evaluating carousel flag for 50 users (deviceId)");
        System.out.println("─".repeat(60));

        for (User user : users) {
            FlagVariant variant = service.getVariantForDevice(user);
            summary.recordDeviceResult(user, variant);

            String cohortMarker = user.isInCarouselCohort() ? "[cohort]" : "[outside]";
            System.out.printf("  [%-18s] %-9s deviceId=%-38s → %s%n", user.getName(), cohortMarker, user.getDeviceId(),
                variant);
        }

        // ====================================================================
        // PHASE 2 — Apply userId identity overrides for the 20 identified users
        // ====================================================================
        System.out.println();
        System.out.println("─".repeat(60));
        System.out.println("  Phase 2: Applying userId overrides for 20 identified users");
        System.out.println("─".repeat(60));

        List<User> identifiedUsers = users.stream().filter(User::isIdentified).toList();

        System.out.printf("  Found %d identified users.%n%n", identifiedUsers.size());

        for (User user : identifiedUsers) {
            // Re-read the device variant (from Phase 1 cache or fresh call)
            FlagVariant deviceVariant = service.getVariantForDevice(user);

            System.out.printf("  [%-18s]%n", user.getName());
            System.out.printf("    deviceId : %s  → %s%n", user.getDeviceId(), deviceVariant);

            // Apply override: register the userId identity with carousel_ab_test_cohort_override trait
            FlagVariant userVariant = service.applyUserIdOverride(user);
            System.out.printf("    userId   : %s  → %s%n", user.getUserId().orElse("N/A"), userVariant);

            boolean preserved = deviceVariant == userVariant;
            System.out.printf("    Bucket preserved? %s%n%n", preserved ? "✅ YES" : "⚠️  NO");

            summary.recordDeviceVariantForUser(user.getUserId().orElseThrow(), deviceVariant);
            summary.recordUserIdResult(user, userVariant, deviceVariant);
        }

        // ====================================================================
        // Final report
        // ====================================================================
        summary.printReport();

        service.close();
    }
}
