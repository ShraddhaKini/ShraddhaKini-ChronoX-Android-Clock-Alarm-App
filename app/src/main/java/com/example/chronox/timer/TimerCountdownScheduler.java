package com.example.chronox.timer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Schedules a durable completion callback so countdowns notify even while ChronoX is backgrounded. */
public final class TimerCountdownScheduler {
    private static final int REQUEST_CODE = 53001;
    private static final String ACTION_FINISH = "com.example.chronox.COUNTDOWN_FINISHED";

    private TimerCountdownScheduler() {}

    public static void schedule(Context context, long endTimestamp) {
        if (context == null || endTimestamp <= 0L) return;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;

        PendingIntent pendingIntent = buildPendingIntent(context, endTimestamp, PendingIntent.FLAG_UPDATE_CURRENT);
        manager.cancel(pendingIntent);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (manager.canScheduleExactAlarms()) {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTimestamp, pendingIntent);
                } else {
                    // Preserve completion delivery without the special exact-alarm permission.
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTimestamp, pendingIntent);
                }
            } else {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTimestamp, pendingIntent);
            }
        } catch (SecurityException ex) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTimestamp, pendingIntent);
        }
    }

    public static void cancel(Context context) {
        if (context == null) return;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        PendingIntent pendingIntent = buildPendingIntent(context, 0L, PendingIntent.FLAG_NO_CREATE);
        if (pendingIntent != null) {
            manager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    /**
     * Called from BOOT_COMPLETED. Always re-register the saved deadline, including a deadline
     * in the past, so the actual alarm receiver performs completion from an alarm delivery rather
     * than trying to launch an audio foreground service directly from BOOT_COMPLETED.
     */
    public static void restorePendingCountdownAfterBoot(Context context) {
        if (context == null) return;
        android.content.SharedPreferences prefs = CountdownStateStore.preferences(context);
        String state = prefs.getString(CountdownStateStore.KEY_STATE, CountdownStateStore.STATE_IDLE);
        long endTimestamp = prefs.getLong(CountdownStateStore.KEY_END_TIMESTAMP, 0L);
        if (CountdownStateStore.STATE_RUNNING.equals(state) && endTimestamp > 0L) {
            schedule(context, endTimestamp);
        }
    }

    /** Call on app launch to restore or reconcile a saved countdown. */
    public static void restorePendingCountdown(Context context) {
        if (context == null) return;
        android.content.SharedPreferences prefs = CountdownStateStore.preferences(context);
        String state = prefs.getString(CountdownStateStore.KEY_STATE, CountdownStateStore.STATE_IDLE);
        long endTimestamp = prefs.getLong(CountdownStateStore.KEY_END_TIMESTAMP, 0L);
        if (!CountdownStateStore.STATE_RUNNING.equals(state) || endTimestamp <= 0L) return;
        handleDeadline(context, endTimestamp);
    }

    static void handleDeadline(Context context, long expectedEndTimestamp) {
        if (context == null || expectedEndTimestamp <= 0L) return;
        android.content.SharedPreferences prefs = CountdownStateStore.preferences(context);
        boolean isCurrentRun = CountdownStateStore.STATE_RUNNING.equals(
                prefs.getString(CountdownStateStore.KEY_STATE, CountdownStateStore.STATE_IDLE))
                && prefs.getLong(CountdownStateStore.KEY_END_TIMESTAMP, 0L) == expectedEndTimestamp;
        if (!isCurrentRun) {
            // A stale broadcast must not cancel a newer countdown's pending alarm.
            if (!CountdownStateStore.STATE_RUNNING.equals(
                    prefs.getString(CountdownStateStore.KEY_STATE, CountdownStateStore.STATE_IDLE))) {
                cancel(context);
            }
            return;
        }

        if (System.currentTimeMillis() < expectedEndTimestamp) {
            schedule(context, expectedEndTimestamp);
            return;
        }

        boolean shouldNotify = CountdownStateStore.markCompletedIfDue(context, expectedEndTimestamp);
        cancel(context);
        if (shouldNotify) TimerCompletionNotifier.notifyFinished(context);
    }

    private static PendingIntent buildPendingIntent(Context context, long endTimestamp, int flag) {
        Intent intent = new Intent(context, TimerFinishedReceiver.class);
        intent.setAction(ACTION_FINISH);
        if (endTimestamp > 0L) {
            intent.putExtra(CountdownStateStore.EXTRA_END_TIMESTAMP, endTimestamp);
        }
        int flags = flag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags);
    }
}
