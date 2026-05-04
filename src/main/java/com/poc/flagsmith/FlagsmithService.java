package com.poc.flagsmith;

import com.flagsmith.FlagsmithClient;
import com.flagsmith.exceptions.FlagsmithClientError;
import com.flagsmith.models.Flags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Wraps the Flagsmith Java SDK, providing methods to:
 *  - Evaluate the carousel feature flag for anonymous (device-only) identities
 *  - Evaluate the flag for identified (userId) identities
 *  - Set identity overrides so a userId inherits the same bucket as a deviceId
 */
public class FlagsmithService {

    private static final Logger log = LoggerFactory.getLogger(FlagsmithService.class);

    // -----------------------------------------------------------------------
    // Flag / variant constants — must match what you create in the Flagsmith UI
    // -----------------------------------------------------------------------
    public static final String FLAG_KEY = "carousel_ab_test";

    // Multivariate option values (must match the Flagsmith UI exactly)
    public static final String VARIANT_CONTROL = "control";
    public static final String VARIANT_TEST    = "test";

    // -----------------------------------------------------------------------
    // Trait keys stored against an identity in Flagsmith
    // -----------------------------------------------------------------------
    private static final String TRAIT_DEVICE_ID = "device_id";
    private static final String TRAIT_USER_ID = "user_id";
    private static final String TRAIT_NAME = "name";
    // Segment membership trait — set to true to place an identity in the
    // carousel_ab_test segment (which carries the 90/10 multivariate split).
    // Set to false (or omit) to exclude the identity from the experiment entirely.
    public static final String TRAIT_CAROUSEL_COHORT = "member_carousel_ab_test_cohort";

    private final FlagsmithClient client;

    public FlagsmithService(String environmentApiKey) {
        this.client = FlagsmithClient.newBuilder()
            .setApiKey(environmentApiKey)
            .build();

        log.info("FlagsmithService initialised (env key: {}...)",
            environmentApiKey.substring(0, Math.min(8, environmentApiKey.length())));
    }

    // -----------------------------------------------------------------------
    // Read flag for an anonymous user identified only by their device ID
    // -----------------------------------------------------------------------

    /**
     * Evaluate the carousel flag for a user whose identity is their deviceId.
     * Traits are stored so the identity can be re-used later for overrides.
     */
    public FlagVariant getVariantForDevice(User user) {
        try {
            Map<String, Object> traits = new HashMap<>();
            traits.put(TRAIT_DEVICE_ID,      user.getDeviceId());
            traits.put(TRAIT_NAME,            user.getName());
            // member_carousel_ab_test_cohort controls segment membership in Flagsmith.
            // Only identities with member_carousel_ab_test_cohort=true enter the experiment.
            traits.put(TRAIT_CAROUSEL_COHORT, user.isInCarouselCohort());

            Flags flags = client.getIdentityFlags(user.getDeviceId(), traits);
            return extractVariant(flags);
        } catch (FlagsmithClientError e) {
            log.error("Error fetching flag for device {}: {}", user.getDeviceId(), e.getMessage());
            return FlagVariant.DISABLED;
        }
    }

    // -----------------------------------------------------------------------
    // Read flag for an identified user (has both deviceId AND userId)
    // -----------------------------------------------------------------------

    /**
     * Evaluate the carousel flag for a fully-identified user.
     * The identity key used here is the userId.
     */
    public FlagVariant getVariantForIdentifiedUser(User user) {
        if (!user.isIdentified()) {
            throw new IllegalArgumentException("User " + user.getName() + " has no userId");
        }
        try {
            Map<String, Object> traits = new HashMap<>();
            traits.put(TRAIT_USER_ID,        user.getUserId().get());
            traits.put(TRAIT_DEVICE_ID,       user.getDeviceId());
            traits.put(TRAIT_NAME,            user.getName());
            traits.put(TRAIT_CAROUSEL_COHORT, user.isInCarouselCohort());

            Flags flags = client.getIdentityFlags(user.getUserId().get(), traits);
            return extractVariant(flags);
        } catch (FlagsmithClientError e) {
            log.error("Error fetching flag for user {}: {}", user.getUserId(), e.getMessage());
            return FlagVariant.DISABLED;
        }
    }

    // -----------------------------------------------------------------------
    // Identity override — tie userId bucket to the deviceId bucket
    // -----------------------------------------------------------------------

    /**
     * Applies an identity override so that a user identified by their userId
     * receives the SAME variant as they received when identified by deviceId.
     *
     * Strategy:
     *   1. Read the variant for the deviceId identity.
     *   2. Upsert the userId identity with an explicit trait that locks them
     *      to the same variant.  In the Flagsmith SDK this is done by calling
     *      getIdentityFlags with all traits — the platform persists those
     *      traits and the identity is subsequently evaluated consistently.
     *
     * NOTE: True "identity overrides" (pinning a specific flag value per
     * identity) require the Flagsmith Management API (available on paid plans).
     * This method demonstrates the approach via traits + SDK.  The companion
     * README explains how to apply hard overrides via the REST Management API.
     */
    public FlagVariant applyUserIdOverride(User user) {
        if (!user.isIdentified()) {
            throw new IllegalArgumentException("User must have a userId to apply an override");
        }

        // Step 1 – determine the variant the device identity received
        FlagVariant deviceVariant = getVariantForDevice(user);
        log.info("  [override] Device '{}' is in variant '{}'", user.getDeviceId(), deviceVariant);

        // Step 2 – register the userId identity with matching traits
        //          The "carousel_ab_test_cohort_override" trait signals which variant to use
        //          if custom rules / segments are configured in Flagsmith.
        try {
            Map<String, Object> traits = new HashMap<>();
            traits.put(TRAIT_USER_ID,        user.getUserId().get());
            traits.put(TRAIT_DEVICE_ID,      user.getDeviceId());
            traits.put(TRAIT_NAME,           user.getName());
            // Preserve cohort membership on the userId identity
            traits.put(TRAIT_CAROUSEL_COHORT, user.isInCarouselCohort());
            // carousel_ab_test_cohort_override is matched by the higher-priority segment overrides
            // (carousel_ab_test_cohort_test_override / carousel_ab_test_cohort_control_override) to pin the variant
            traits.put("carousel_ab_test_cohort_override",   deviceVariant.getValue());

            Flags flags = client.getIdentityFlags(user.getUserId().get(), traits);
            FlagVariant userVariant = extractVariant(flags);
            log.info("  [override] User '{}' (userId={}) is now in variant '{}'",
                user.getName(), user.getUserId().get(), userVariant);

            return userVariant;
        } catch (FlagsmithClientError e) {
            log.error("Error applying override for {}: {}", user.getUserId(), e.getMessage());
            return FlagVariant.DISABLED;
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private FlagVariant extractVariant(Flags flags) throws FlagsmithClientError {
        // Use the documented Flags API:
        //   isFeatureEnabled(key) — returns true if the flag is on
        //   getFeatureValue(key)  — returns the string/multivariate value
        boolean enabled = flags.isFeatureEnabled(FLAG_KEY);

        if (!enabled) {
            return FlagVariant.DISABLED;
        }

        Object value = flags.getFeatureValue(FLAG_KEY);
        String strValue = (value != null) ? value.toString() : null;
        return FlagVariant.fromValue(strValue);
    }

    public void close() {
        client.close();
    }
}
