package com.example.chronox.timer;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import android.app.NotificationManager;

import com.example.chronox.MainActivity;
import com.example.chronox.R;
import com.example.chronox.common.Constants;
import com.example.chronox.common.NotificationHelper;

/** Keeps an audible countdown-complete alert active until the user explicitly stops it. */
public class TimerAlertService extends Service {
    public static final String ACTION_STOP_ALERT = "com.example.chronox.timer.STOP_COUNTDOWN_ALERT";
    public static final String ACTION_ALERT_STOPPED = "com.example.chronox.timer.COUNTDOWN_ALERT_STOPPED";
    private static final String PREFS = CountdownStateStore.PREFS_NAME;
    private static final String KEY_ALERT_ACTIVE = "countdown_alert_active";

    private Ringtone ringtone;

    public static void start(Context context) {
        Context app = context.getApplicationContext();
        setAlerting(app, true);
        NotificationHelper.createNotificationChannels(app);
        Intent intent = new Intent(app, TimerAlertService.class);
        try {
            ContextCompat.startForegroundService(app, intent);
        } catch (RuntimeException exception) {
            setAlerting(app, false);
            throw exception;
        }
    }

    public static void stop(Context context) {
        if (context == null) return;
        Intent intent = new Intent(context.getApplicationContext(), TimerAlertService.class);
        intent.setAction(ACTION_STOP_ALERT);
        try {
            context.getApplicationContext().startService(intent);
        } catch (RuntimeException ignored) {
            setAlerting(context.getApplicationContext(), false);
        }
    }

    public static boolean isAlerting(Context context) {
        if (context == null) return false;
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ALERT_ACTIVE, false);
    }

    private static void setAlerting(Context context, boolean active) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ALERT_ACTIVE, active).commit();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_ALERT.equals(intent.getAction())) {
            stopAlert();
            return START_NOT_STICKY;
        }

        setAlerting(this, true);
        NotificationHelper.createNotificationChannels(this);
        startForeground(Constants.NOTIFICATION_TIMER_ALERT_ID, buildAlertNotification());
        startAlarmTone();
        return START_NOT_STICKY;
    }

    private Notification buildAlertNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this,
                Constants.NOTIFICATION_TIMER_ALERT_ID,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | NotificationHelper.getPendingIntentImmutableFlag());

        Intent stopIntent = new Intent(this, TimerAlertService.class);
        stopIntent.setAction(ACTION_STOP_ALERT);
        PendingIntent stopPi = PendingIntent.getService(this,
                Constants.NOTIFICATION_TIMER_ALERT_ID + 1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | NotificationHelper.getPendingIntentImmutableFlag());

        return new NotificationCompat.Builder(this, Constants.CHANNEL_TIMER_ALERT_ID)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(getString(R.string.timer_notification_title))
                .setContentText("Countdown ended — tap Stop Sound to silence")
                .setContentIntent(openPi)
                .addAction(R.drawable.ic_timer, getString(R.string.timer_stop_sound), stopPi)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
    }

    private void startAlarmTone() {
        if (ringtone != null && ringtone.isPlaying()) return;
        Uri toneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (toneUri == null) toneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (toneUri == null) return;
        ringtone = RingtoneManager.getRingtone(getApplicationContext(), toneUri);
        if (ringtone != null) {
            ringtone.setLooping(true);
            try { ringtone.play(); } catch (RuntimeException ignored) { }
        }
    }

    private void stopTone() {
        if (ringtone != null) {
            try { ringtone.stop(); } catch (RuntimeException ignored) { }
            ringtone = null;
        }
    }

    private void stopAlert() {
        stopTone();
        setAlerting(this, false);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(Constants.NOTIFICATION_TIMER_ID);
        sendBroadcast(new Intent(ACTION_ALERT_STOPPED).setPackage(getPackageName()));
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopTone();
        setAlerting(this, false);
        sendBroadcast(new Intent(ACTION_ALERT_STOPPED).setPackage(getPackageName()));
        super.onDestroy();
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }
}
