package com.example.chronox.timer;

import android.content.Context;
import android.content.SharedPreferences;

/** Small durable store for the countdown's target time and editable duration. */
public final class CountdownStateStore {
    public static final String PREFS_NAME = "chronox_countdown_state";
    public static final String KEY_STATE = "state";
    public static final String KEY_END_TIMESTAMP = "end_timestamp";
    public static final String KEY_REMAINING_PAUSED = "remaining_paused";
    public static final String KEY_DAYS = "input_days";
    public static final String KEY_HOURS = "input_hours";
    public static final String KEY_MINUTES = "input_minutes";
    public static final String KEY_SECONDS = "input_seconds";
    public static final String KEY_LAST_NOTIFIED_END = "last_notified_end";
    public static final String STATE_IDLE = "IDLE";
    public static final String STATE_RUNNING = "RUNNING";
    public static final String STATE_PAUSED = "PAUSED";
    public static final String EXTRA_END_TIMESTAMP = "countdown_end_timestamp";

    private static final Object LOCK = new Object();

    private CountdownStateStore() {}

    public static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void save(Context context, String state, long endTimestamp,
                            long remainingOnPause, int days, int hours, int minutes, int seconds) {
        write(context, state, endTimestamp, remainingOnPause, days, hours, minutes, seconds, false);
    }

    /** Synchronous write used for start/pause/resume/reset so force-stop cannot lose the transition. */
    public static void saveDurably(Context context, String state, long endTimestamp,
                                   long remainingOnPause, int days, int hours, int minutes, int seconds) {
        write(context, state, endTimestamp, remainingOnPause, days, hours, minutes, seconds, true);
    }

    private static void write(Context context, String state, long endTimestamp,
                              long remainingOnPause, int days, int hours, int minutes, int seconds,
                              boolean commit) {
        if (context == null) return;
        synchronized (LOCK) {
            SharedPreferences.Editor editor = preferences(context).edit()
                    .putString(KEY_STATE, state)
                    .putLong(KEY_END_TIMESTAMP, endTimestamp)
                    .putLong(KEY_REMAINING_PAUSED, remainingOnPause)
                    .putInt(KEY_DAYS, days)
                    .putInt(KEY_HOURS, hours)
                    .putInt(KEY_MINUTES, minutes)
                    .putInt(KEY_SECONDS, seconds);
            if (commit) editor.commit(); else editor.apply();
        }
    }

    /** Marks this run complete at most once and returns true only for the caller that should notify. */
    public static boolean markCompletedIfDue(Context context, long expectedEndTimestamp) {
        if (context == null || expectedEndTimestamp <= 0L) return false;
        synchronized (LOCK) {
            SharedPreferences prefs = preferences(context);
            String savedState = prefs.getString(KEY_STATE, STATE_IDLE);
            long savedEnd = prefs.getLong(KEY_END_TIMESTAMP, 0L);
            if (!STATE_RUNNING.equals(savedState)
                    || savedEnd != expectedEndTimestamp
                    || System.currentTimeMillis() < expectedEndTimestamp) {
                return false;
            }

            long lastNotifiedEnd = prefs.getLong(KEY_LAST_NOTIFIED_END, 0L);
            boolean shouldNotify = lastNotifiedEnd != expectedEndTimestamp;
            SharedPreferences.Editor editor = prefs.edit()
                    .putString(KEY_STATE, STATE_IDLE)
                    .putLong(KEY_REMAINING_PAUSED, 0L);
            if (shouldNotify) editor.putLong(KEY_LAST_NOTIFIED_END, expectedEndTimestamp);
            editor.commit();
            return shouldNotify;
        }
    }
}
