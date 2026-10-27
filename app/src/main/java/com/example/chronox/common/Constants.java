package com.example.chronox.common;

/**
 * Constants — App-wide constant values.
 * Centralizing these prevents typos and makes changes trivial.
 */
public final class Constants {

    // Private constructor — utility class, not instantiable
    private Constants() {}

    // =========================================================
    // NOTIFICATION CHANNELS
    // =========================================================
    public static final String CHANNEL_ALARM_ID = "chronox_alarm_channel";
    public static final String CHANNEL_TIMER_ID = "chronox_timer_channel";
    public static final String CHANNEL_STOPWATCH_ID = "chronox_stopwatch_channel";
    public static final String CHANNEL_TIMER_ALERT_ID = "chronox_timer_alert_channel";

    // =========================================================
    // NOTIFICATION IDs
    // =========================================================
    public static final int NOTIFICATION_TIMER_ID = 10001;
    public static final int NOTIFICATION_STOPWATCH_ID = 10002;
    public static final int NOTIFICATION_TIMER_ALERT_ID = 10003;
    // Alarm notifications use alarm DB ID as notification ID.

    // =========================================================
    // SHARED PREFERENCES
    // =========================================================
    public static final String PREFS_NAME = "chronox_prefs";
    public static final String PREF_CLOCK_FORMAT_24H = "clock_format_24h";
    public static final String PREF_ALARM_SOUND = "alarm_sound_enabled";
    public static final String PREF_ALARM_VIBRATION = "alarm_vibration_enabled";
    public static final String PREF_TIMER_SOUND = "timer_sound_enabled";
    public static final String PREF_TIMER_VIBRATION = "timer_vibration_enabled";

    // =========================================================
    // ALARM INTENT ACTIONS & EXTRAS
    // =========================================================
    public static final String ACTION_ALARM_FIRED = "com.example.chronox.ALARM_FIRED";
    public static final String ACTION_ALARM_DISMISS = "com.example.chronox.ALARM_DISMISS";
    public static final String ACTION_ALARM_SNOOZE = "com.example.chronox.ALARM_SNOOZE";
    public static final String ACTION_ALARM_AUTO_SNOOZE = "com.example.chronox.ALARM_AUTO_SNOOZE";
    public static final String ACTION_ALARM_RING_CLOSED = "com.example.chronox.ALARM_RING_CLOSED";
    public static final String EXTRA_ALARM_ID = "alarm_id";
    public static final String EXTRA_ALARM_LABEL = "alarm_label";
    public static final long SNOOZE_DURATION_MS = 5 * 60 * 1000L;
    // If an alarm is left ringing without input, automatically snooze it after one minute.
    public static final long AUTO_SNOOZE_DELAY_MS = 60 * 1000L;

    // =========================================================
    // WEEKDAY BIT MASKS
    // Stored as a single integer bitmask in the database.
    // Bit 0 = Monday, Bit 1 = Tuesday, ..., Bit 6 = Sunday.
    // =========================================================
    public static final int DAY_MON = 1;
    public static final int DAY_TUE = 1 << 1;
    public static final int DAY_WED = 1 << 2;
    public static final int DAY_THU = 1 << 3;
    public static final int DAY_FRI = 1 << 4;
    public static final int DAY_SAT = 1 << 5;
    public static final int DAY_SUN = 1 << 6;
    public static final int ALL_DAYS = DAY_MON | DAY_TUE | DAY_WED | DAY_THU | DAY_FRI | DAY_SAT | DAY_SUN;
    public static final int WEEKDAYS = DAY_MON | DAY_TUE | DAY_WED | DAY_THU | DAY_FRI;
    public static final int WEEKENDS = DAY_SAT | DAY_SUN;

    // =========================================================
    // CHESS TIMER
    // =========================================================
    /** Vibrate/warn when remaining time drops below this threshold. */
    public static final long CHESS_WARNING_THRESHOLD_MS = 30_000L;

    // =========================================================
    // DATABASE
    // =========================================================
    public static final String DATABASE_NAME = "chronox_database";
    public static final int DATABASE_VERSION = 3;
}
