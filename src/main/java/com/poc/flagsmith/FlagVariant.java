package com.poc.flagsmith;

/**
 * Represents the possible variants for the carousel A/B test flag.
 *
 * CONTROL  = carousel hidden  (90% of traffic)
 * TEST     = carousel visible (10% of traffic)
 * DISABLED = flag is off entirely
 */
public enum FlagVariant {
    CONTROL("control"),
    TEST("test"),
    DISABLED("disabled");

    private final String value;

    FlagVariant(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse the string value returned by Flagsmith into a FlagVariant.
     */
    public static FlagVariant fromValue(String value) {
        if (value == null) return DISABLED;
        for (FlagVariant v : values()) {
            if (v.value.equalsIgnoreCase(value.trim())) return v;
        }
        return DISABLED;
    }

    @Override
    public String toString() {
        return value;
    }
}
