package com.example.chronox.timer;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import com.example.chronox.common.NotificationHelper;

/** Shared notification/vibration path for foreground and background countdown completion. */
public final class TimerCompletionNotifier {
    private TimerCompletionNotifier() {}

    public static void notifyFinished(Context context) {
        Context appContext = context.getApplicationContext();
        try {
            TimerAlertService.start(appContext);
        } catch (RuntimeException exception) {
            // Fallback notification if Android blocks foreground-service startup.
            NotificationHelper.showTimerFinishedNotification(appContext);
        }
        try {
            long[] pattern = {0, 500, 200, 500};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager manager = (VibratorManager) appContext
                        .getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (manager != null) {
                    manager.getDefaultVibrator().vibrate(VibrationEffect.createWaveform(pattern, -1));
                }
            } else {
                //noinspection deprecation
                Vibrator vibrator = (Vibrator) appContext.getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null) {
                    //noinspection deprecation
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                }
            }
        } catch (Exception ignored) {
            // Missing hardware/permission must not crash timer completion.
        }
    }
}
