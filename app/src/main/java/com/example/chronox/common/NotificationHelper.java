package com.example.chronox.common;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.chronox.MainActivity;
import com.example.chronox.alarm.AlarmReceiver;
import com.example.chronox.alarm.AlarmRingingActivity;
import com.example.chronox.R;

/**
 * NotificationHelper — Centralizes all notification creation for ChronoX.
 *
 * Why centralized:
 * - Prevents duplicate channel creation across features
 * - Ensures consistent notification style
 * - Single point to update if Android notification API behavior changes
 *
 * Notification channels MUST be created before any notification is posted.
 * Call createNotificationChannels() in MainActivity.onCreate().
 * Creating an existing channel is safe — Android ignores duplicate calls.
 *
 * If POST_NOTIFICATIONS is denied (API 33+), notifications won't appear,
 * but NO crash occurs. This class never throws on missing permission.
 */
public final class NotificationHelper {

    private NotificationHelper() {}

    /**
     * Create all notification channels used by the app.
     *
     * Required on API 26+ (our minSdk=29 so always needed).
     * Safe to call multiple times — Android ignores duplicates.
     */
    public static void createNotificationChannels(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        // Alarm channel — high priority for heads-up display
        NotificationChannel alarmChannel = new NotificationChannel(
                Constants.CHANNEL_ALARM_ID,
                context.getString(R.string.alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        alarmChannel.setDescription(context.getString(R.string.alarm_channel_desc));
        alarmChannel.enableVibration(true);
        alarmChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(alarmChannel);

        // Timer channel — high priority for "Timer finished" alerts
        NotificationChannel timerChannel = new NotificationChannel(
                Constants.CHANNEL_TIMER_ID,
                context.getString(R.string.timer_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        timerChannel.setDescription(context.getString(R.string.timer_channel_desc));
        timerChannel.enableVibration(true);
        manager.createNotificationChannel(timerChannel);

        NotificationChannel stopwatchChannel = new NotificationChannel(
                Constants.CHANNEL_STOPWATCH_ID,
                "Stopwatch",
                NotificationManager.IMPORTANCE_LOW);
        stopwatchChannel.setDescription("Ongoing stopwatch time");
        stopwatchChannel.setSound(null, null);
        stopwatchChannel.enableVibration(false);
        stopwatchChannel.setShowBadge(false);
        manager.createNotificationChannel(stopwatchChannel);

        NotificationChannel timerAlertChannel = new NotificationChannel(
                Constants.CHANNEL_TIMER_ALERT_ID,
                "Countdown completion",
                NotificationManager.IMPORTANCE_LOW);
        timerAlertChannel.setDescription("Persistent alert when a countdown finishes");
        // The alert service plays the alarm tone itself so the notification must stay silent.
        timerAlertChannel.setSound(null, null);
        timerAlertChannel.enableVibration(false);
        timerAlertChannel.setShowBadge(false);
        manager.createNotificationChannel(timerAlertChannel);
    }

    /**
     * Build and post an alarm notification.
     *
     * Uses alarmId as the notification ID so:
     * - Each alarm has its own notification
     * - Edit/reschedule updates the correct one
     * - Delete cancels the correct one
     *
     * @param context  Application context
     * @param alarmId  Unique alarm DB ID (used as notification ID)
     * @param label    Alarm label shown in notification title
     */
    public static void showAlarmNotification(Context context, int alarmId, String label) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        String title = (label != null && !label.trim().isEmpty())
                ? label.trim() : context.getString(R.string.alarm_label_default);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | getPendingIntentImmutableFlag();

        Intent ringingIntent = new Intent(context, AlarmRingingActivity.class);
        ringingIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        ringingIntent.putExtra(Constants.EXTRA_ALARM_ID, alarmId);
        ringingIntent.putExtra(Constants.EXTRA_ALARM_LABEL, title);
        PendingIntent ringingPi = PendingIntent.getActivity(
                context, alarmId + 400000, ringingIntent, piFlags);

        Intent snoozeIntent = new Intent(context, AlarmReceiver.class);
        snoozeIntent.setAction(Constants.ACTION_ALARM_SNOOZE);
        snoozeIntent.putExtra(Constants.EXTRA_ALARM_ID, alarmId);
        snoozeIntent.putExtra(Constants.EXTRA_ALARM_LABEL, title);
        PendingIntent snoozePi = PendingIntent.getBroadcast(
                context, alarmId + 500000, snoozeIntent, piFlags);

        Intent stopIntent = new Intent(context, AlarmReceiver.class);
        stopIntent.setAction(Constants.ACTION_ALARM_DISMISS);
        stopIntent.putExtra(Constants.EXTRA_ALARM_ID, alarmId);
        stopIntent.putExtra(Constants.EXTRA_ALARM_LABEL, title);
        PendingIntent stopPi = PendingIntent.getBroadcast(
                context, alarmId + 600000, stopIntent, piFlags);

        Notification notification = new NotificationCompat.Builder(context, Constants.CHANNEL_ALARM_ID)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle(title)
                .setContentText("Snooze for 5 minutes or stop the alarm")
                .setContentIntent(ringingPi)
                .setFullScreenIntent(ringingPi, true)
                .setAutoCancel(false)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(0)
                .addAction(R.drawable.ic_alarm, "Snooze 5 min", snoozePi)
                .addAction(R.drawable.ic_alarm, "Stop", stopPi)
                .build();

        manager.notify(alarmId, notification);
    }

    /**
     * Show an ongoing stopwatch notification. Android's built-in notification chronometer
     * updates live without a worker/service waking every second.
     */
    public static void showStopwatchNotification(Context context, long elapsedMs, boolean running) {
        if (context == null) return;
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        createNotificationChannels(context);

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | getPendingIntentImmutableFlag();
        PendingIntent openPi = PendingIntent.getActivity(context,
                Constants.NOTIFICATION_STOPWATCH_ID, openIntent, piFlags);

        long safeElapsedMs = Math.max(0L, elapsedMs);
        long chronometerBaseWallTime = System.currentTimeMillis() - safeElapsedMs;
        String content = running
                ? context.getString(R.string.stopwatch_notification_running)
                : context.getString(R.string.stopwatch_notification_paused,
                        com.example.chronox.common.TimeUtils.formatMSSc(safeElapsedMs));

        Notification notification = new NotificationCompat.Builder(context, Constants.CHANNEL_STOPWATCH_ID)
                .setSmallIcon(R.drawable.ic_stopwatch)
                .setContentTitle(context.getString(R.string.stopwatch_notification_title))
                .setContentText(content)
                .setContentIntent(openPi)
                .setWhen(running ? chronometerBaseWallTime : System.currentTimeMillis())
                .setShowWhen(running)
                .setUsesChronometer(running)
                .setChronometerCountDown(false)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
        manager.notify(Constants.NOTIFICATION_STOPWATCH_ID, notification);
    }

    public static void cancelStopwatchNotification(Context context) {
        if (context == null) return;
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(Constants.NOTIFICATION_STOPWATCH_ID);
    }

    /**
     * Post a timer completion notification.
     * Uses a fixed ID so repeated timer completions update the same notification.
     */
    public static void showTimerFinishedNotification(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | getPendingIntentImmutableFlag();
        PendingIntent openPi = PendingIntent.getActivity(
                context, Constants.NOTIFICATION_TIMER_ID, openIntent, piFlags);

        Notification notification = new NotificationCompat.Builder(context, Constants.CHANNEL_TIMER_ID)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(context.getString(R.string.timer_notification_title))
                .setContentText(context.getString(R.string.timer_notification_text))
                .setContentIntent(openPi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build();

        manager.notify(Constants.NOTIFICATION_TIMER_ID, notification);
    }

    /** Cancel a specific alarm notification by its alarm ID. */
    public static void cancelAlarmNotification(Context context, int alarmId) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(alarmId);
    }

    /**
     * Return FLAG_IMMUTABLE if API >= 23, else 0.
     *
     * PendingIntent.FLAG_IMMUTABLE was added in API 23.
     * On API 31+, either FLAG_IMMUTABLE or FLAG_MUTABLE MUST be specified.
     * We always use IMMUTABLE since we don't need the PendingIntent to be mutated.
     */
    public static int getPendingIntentImmutableFlag() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.FLAG_IMMUTABLE;
        }
        return 0;
    }
}
