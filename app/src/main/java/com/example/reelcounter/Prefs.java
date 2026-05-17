package com.example.reelcounter;

public final class Prefs {
    public static final String NAME                    = "reel_counter_prefs";
    public static final String KEY_DEBOUNCE_MS         = "debounce_ms";
    public static final String KEY_DIAGNOSTIC          = "diagnostic_mode";
    public static final String KEY_SCREEN_LENGTH_CM    = "screen_length_cm";
    public static final String KEY_LIFETIME_COUNT      = "lifetime_count";
    public static final String KEY_LAST_WEEKLY_REPORT  = "last_weekly_report_ms";

    public static final long   DEFAULT_DEBOUNCE_MS     = 500L;  // Changed to 0.5s
    public static final float  DEFAULT_SCREEN_LENGTH   = 0f;    // User must set this

    public static final String KEY_WEEKLY_LIMIT = "weekly_limit";
    public static final int DEFAULT_WEEKLY_LIMIT = -1;   // -1 = no limit set

    public static final String KEY_DAILY_LIMIT  = "daily_limit";
    public static final int    DEFAULT_DAILY_LIMIT = -1;   // -1 = no limit set


    private Prefs() {}
}