package com.example.chronox.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.example.chronox.common.Constants;

import java.util.Calendar;

/** Schedules exact one-time/repeating alarms plus independent snooze/auto-snooze alarms. */
public class AlarmScheduler {
    private static final String TAG = "AlarmScheduler";
    private static final int SNOOZE_REQUEST_OFFSET = 200000;
    private static final int AUTO_SNOOZE_REQUEST_OFFSET = 300000;

    public static boolean scheduleAlarm(Context context, Alarm alarm) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return false;
        long triggerAt = computeNextTriggerTime(alarm);
        PendingIntent pi = buildPendingIntent(context, alarm);
        return setAlarmCompat(am, triggerAt, pi, alarm.id);
    }

    /** Schedule a single 5-minute snooze without replacing a repeating alarm's next occurrence. */
    public static void scheduleSnoozeAlarm(Context context, int alarmId, String label) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = buildSnoozePendingIntent(context, alarmId, label);
        long triggerAt = System.currentTimeMillis() + Constants.SNOOZE_DURATION_MS;
        setAlarmCompat(am, triggerAt, pi, alarmId);
        Log.d(TAG, "Snooze scheduled: id=" + alarmId);
    }

    /** If nobody interacts with the ringing screen, trigger an automatic 5-minute snooze in 60s. */
    public static void scheduleAutoSnooze(Context context, int alarmId, String label) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = buildAutoSnoozePendingIntent(context, alarmId, label);
        long triggerAt = System.currentTimeMillis() + Constants.AUTO_SNOOZE_DELAY_MS;
        setAlarmCompat(am, triggerAt, pi, alarmId);
    }

    public static void cancelAutoSnooze(Context context, int alarmId) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(buildAutoSnoozePendingIntent(context, alarmId, null));
    }

    /** Cancel a scheduled alarm and its per-alarm snooze timers. */
    public static void cancelAlarm(Context context, Alarm alarm) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(buildPendingIntent(context, alarm));
        am.cancel(buildSnoozePendingIntent(context, alarm.id, null));
        am.cancel(buildAutoSnoozePendingIntent(context, alarm.id, null));
        Log.d(TAG, "Alarm and pending snoozes cancelled: id=" + alarm.id);
    }

    private static boolean setAlarmCompat(AlarmManager am, long triggerAt, PendingIntent pi, int alarmId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                Log.d(TAG, "Exact alarm scheduled: id=" + alarmId);
                return true;
            }
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            Log.w(TAG, "Exact alarm permission missing; inexact alarm scheduled: id=" + alarmId);
            return false;
        }
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        return true;
    }

    /** Compute the next alarm wall-clock time in local time. */
    public static long computeNextTriggerTime(Alarm alarm) {
        Calendar cal = Calendar.getInstance();

        if (alarm.repeatDays == 0) {
            if (alarm.scheduledDateMillis > 0L) {
                cal.setTimeInMillis(alarm.scheduledDateMillis);
            }
            cal.set(Calendar.HOUR_OF_DAY, alarm.hour);
            cal.set(Calendar.MINUTE, alarm.minute);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            // Legacy time-only alarms (scheduledDateMillis=0) retain the original
            // today-or-tomorrow behavior. A stale saved date safely moves forward.
            if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
            return cal.getTimeInMillis();
        }

        cal.set(Calendar.HOUR_OF_DAY, alarm.hour);
        cal.set(Calendar.MINUTE, alarm.minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // Calendar.DAY_OF_WEEK (1=Sun…7=Sat) to the app's repeat bitmask.
        int[] calDayToBit = {0, Constants.DAY_SUN, Constants.DAY_MON, Constants.DAY_TUE,
                Constants.DAY_WED, Constants.DAY_THU, Constants.DAY_FRI, Constants.DAY_SAT};
        for (int daysAhead = 0; daysAhead < 7; daysAhead++) {
            Calendar candidate = (Calendar) cal.clone();
            candidate.add(Calendar.DAY_OF_MONTH, daysAhead);
            int calDay = candidate.get(Calendar.DAY_OF_WEEK);
            int bit = (calDay >= 1 && calDay <= 7) ? calDayToBit[calDay] : 0;
            if ((alarm.repeatDays & bit) != 0
                    && candidate.getTimeInMillis() > System.currentTimeMillis()) {
                return candidate.getTimeInMillis();
            }
        }
        cal.add(Calendar.DAY_OF_MONTH, 1);
        return cal.getTimeInMillis();
    }

    private static PendingIntent buildPendingIntent(Context context, Alarm alarm) {
        return buildAlarmPendingIntent(context, alarm.id, alarm.label, alarm.id);
    }

    private static PendingIntent buildSnoozePendingIntent(Context context, int alarmId, String label) {
        return buildAlarmPendingIntent(context, alarmId, label, alarmId + SNOOZE_REQUEST_OFFSET);
    }

    private static PendingIntent buildAlarmPendingIntent(Context context, int alarmId, String label, int requestCode) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction(Constants.ACTION_ALARM_FIRED);
        intent.putExtra(Constants.EXTRA_ALARM_ID, alarmId);
        intent.putExtra(Constants.EXTRA_ALARM_LABEL, label);
        return PendingIntent.getBroadcast(context, requestCode, intent, pendingIntentFlags());
    }

    private static PendingIntent buildAutoSnoozePendingIntent(Context context, int alarmId, String label) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction(Constants.ACTION_ALARM_AUTO_SNOOZE);
        intent.putExtra(Constants.EXTRA_ALARM_ID, alarmId);
        intent.putExtra(Constants.EXTRA_ALARM_LABEL, label);
        return PendingIntent.getBroadcast(context, alarmId + AUTO_SNOOZE_REQUEST_OFFSET,
                intent, pendingIntentFlags());
    }

    private static int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return flags;
    }
}
