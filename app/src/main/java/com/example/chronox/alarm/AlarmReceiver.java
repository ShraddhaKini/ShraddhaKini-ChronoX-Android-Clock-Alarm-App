package com.example.chronox.alarm;

import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import com.example.chronox.common.Constants;
import com.example.chronox.common.NotificationHelper;
import com.example.chronox.data.AppDatabase;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Delivers alarms, controls their sound/vibration, and applies manual/automatic snooze actions. */
public class AlarmReceiver extends android.content.BroadcastReceiver {
    private static Ringtone activeRingtone;
    private static final Object RING_LOCK = new Object();
    private static final Set<Integer> stoppedRings = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        int alarmId = intent.getIntExtra(Constants.EXTRA_ALARM_ID, -1);
        if (alarmId < 0) return;
        String label = intent.getStringExtra(Constants.EXTRA_ALARM_LABEL);

        if (Constants.ACTION_ALARM_DISMISS.equals(action)) {
            closeCurrentRing(context, alarmId);
            return;
        }
        if (Constants.ACTION_ALARM_SNOOZE.equals(action)
                || Constants.ACTION_ALARM_AUTO_SNOOZE.equals(action)) {
            closeCurrentRing(context, alarmId);
            AlarmScheduler.scheduleSnoozeAlarm(context, alarmId, label);
            return;
        }
        if (!Constants.ACTION_ALARM_FIRED.equals(action)) return;

        // A fresh alarm firing clears any stop marker left by a previous ring/snooze cycle.
        stoppedRings.remove(alarmId);
        // Full-screen alarm activity + high-priority notification fallback.
        NotificationHelper.showAlarmNotification(context, alarmId, label);
        // If the user neither stops nor snoozes, auto-snooze after one minute.
        AlarmScheduler.scheduleAutoSnooze(context, alarmId, label);

        // Room access happens off the broadcast main thread.
        PendingResult result = goAsync();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        worker.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
                Alarm alarm = db.alarmDao().getAlarmByIdSync(alarmId);
                if (alarm == null) return;
                // A user may stop/snooze almost immediately after delivery. Serialize
                // startup with stopPlayback so a late DB read can't restart the ringtone.
                synchronized (RING_LOCK) {
                    if (!stoppedRings.contains(alarmId)) {
                        if (alarm.vibrationEnabled) vibrate(context);
                        if (alarm.soundEnabled) playDefaultRingtone(context);
                    }
                }

                if (alarm.isOneTime()) {
                    db.alarmDao().setEnabled(alarmId, false);
                } else {
                    AlarmScheduler.scheduleAlarm(context, alarm);
                }
            } finally {
                result.finish();
                worker.shutdown();
            }
        });
    }

    private static void closeCurrentRing(Context context, int alarmId) {
        synchronized (RING_LOCK) {
            stoppedRings.add(alarmId);
            stopPlayback(context);
        }
        AlarmScheduler.cancelAutoSnooze(context, alarmId);
        NotificationHelper.cancelAlarmNotification(context, alarmId);
        Intent closed = new Intent(Constants.ACTION_ALARM_RING_CLOSED)
                .setPackage(context.getPackageName())
                .putExtra(Constants.EXTRA_ALARM_ID, alarmId);
        context.sendBroadcast(closed);
    }

    /** Can also be used when a ringing notification is disabled or removed by the user. */
    public static synchronized void stopPlayback(Context context) {
        try {
            if (activeRingtone != null && activeRingtone.isPlaying()) activeRingtone.stop();
        } catch (Exception ignored) {}
        activeRingtone = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) vm.cancel();
            } else {
                Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null) vibrator.cancel();
            }
        } catch (Exception ignored) {}
    }

    private void vibrate(Context context) {
        try {
            long[] pattern = {0, 800, 400, 800, 400, 800};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) vm.getDefaultVibrator().vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null) vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            }
        } catch (Exception ignored) {}
    }

    private static synchronized void playDefaultRingtone(Context context) {
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (activeRingtone != null && activeRingtone.isPlaying()) activeRingtone.stop();
            activeRingtone = RingtoneManager.getRingtone(context.getApplicationContext(), uri);
            if (activeRingtone != null) {
                // Keep the alarm audible until the user stops/snoozes it or auto-snooze runs.
                activeRingtone.setLooping(true);
                activeRingtone.play();
            }
        } catch (Exception ignored) {}
    }
}
