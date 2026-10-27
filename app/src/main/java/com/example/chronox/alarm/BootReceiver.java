package com.example.chronox.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.chronox.data.AppDatabase;
import com.example.chronox.timer.TimerCountdownScheduler;

import java.util.List;
import java.util.concurrent.Executors;

/**
 * BootReceiver — reschedules all enabled alarms after device restart.
 *
 * Android clears ALL AlarmManager alarms on every device power-off/reboot.
 * This receiver listens for BOOT_COMPLETED and re-registers every enabled alarm.
 *
 * Manifest: exported=true, RECEIVE_BOOT_COMPLETED permission declared.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        boolean isBoot = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action);
        if (!isBoot) return;

        // Restore a running countdown's saved deadline after Android clears alarms on reboot.
        TimerCountdownScheduler.restorePendingCountdownAfterBoot(context.getApplicationContext());

        // goAsync() extends the BroadcastReceiver deadline so DB query can finish
        PendingResult result = goAsync();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                List<Alarm> enabled = db.alarmDao().getEnabledAlarmsSync();
                for (Alarm alarm : enabled) {
                    AlarmScheduler.scheduleAlarm(context, alarm);
                }
            } finally {
                result.finish();
            }
        });
    }
}
