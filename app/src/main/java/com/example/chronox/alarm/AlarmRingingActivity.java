package com.example.chronox.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.chronox.R;
import com.example.chronox.common.Constants;
import com.example.chronox.settings.ThemeHelper;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Full-screen alarm UI shown over the lock screen while an alarm is ringing. */
public class AlarmRingingActivity extends AppCompatActivity {
    private int alarmId = -1;
    private boolean receiverRegistered;
    private BroadcastReceiver ringClosedReceiver;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.hide(WindowInsetsCompat.Type.statusBars()
                    | WindowInsetsCompat.Type.navigationBars());
            insetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
        setContentView(R.layout.activity_alarm_ringing);

        Intent launchIntent = getIntent();
        alarmId = launchIntent != null ? launchIntent.getIntExtra(Constants.EXTRA_ALARM_ID, -1) : -1;
        String label = launchIntent != null ? launchIntent.getStringExtra(Constants.EXTRA_ALARM_LABEL) : null;
        if (label == null || label.trim().isEmpty()) label = getString(R.string.alarm_label_default);

        TextView tvLabel = findViewById(R.id.tv_ringing_alarm_label);
        TextView tvTime = findViewById(R.id.tv_ringing_alarm_time);
        TextView tvCaption = findViewById(R.id.tv_ringing_alarm_caption);
        MaterialButton btnSnooze = findViewById(R.id.btn_ringing_snooze);
        MaterialButton btnStop = findViewById(R.id.btn_ringing_stop);

        tvLabel.setText(label.trim());
        boolean use24h = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                .getBoolean(Constants.PREF_CLOCK_FORMAT_24H, false);
        String timePattern = use24h ? "HH:mm" : "h:mm a";
        tvTime.setText(new SimpleDateFormat(timePattern, Locale.getDefault()).format(new Date()));
        tvCaption.setText("Alarm is ringing");
        btnSnooze.setOnClickListener(v -> sendAlarmAction(Constants.ACTION_ALARM_SNOOZE));
        btnStop.setOnClickListener(v -> sendAlarmAction(Constants.ACTION_ALARM_DISMISS));

        ringClosedReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (intent != null && Constants.ACTION_ALARM_RING_CLOSED.equals(intent.getAction())
                        && intent.getIntExtra(Constants.EXTRA_ALARM_ID, -1) == alarmId) {
                    finish();
                }
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (ringClosedReceiver != null && !receiverRegistered) {
            IntentFilter filter = new IntentFilter(Constants.ACTION_ALARM_RING_CLOSED);
            ContextCompat.registerReceiver(this, ringClosedReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
            receiverRegistered = true;
        }
    }

    @Override
    protected void onStop() {
        if (receiverRegistered && ringClosedReceiver != null) {
            unregisterReceiver(ringClosedReceiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    private void sendAlarmAction(String action) {
        if (alarmId >= 0) {
            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.setAction(action);
            intent.putExtra(Constants.EXTRA_ALARM_ID, alarmId);
            String label = getIntent() != null ? getIntent().getStringExtra(Constants.EXTRA_ALARM_LABEL) : null;
            intent.putExtra(Constants.EXTRA_ALARM_LABEL, label);
            sendBroadcast(intent);
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        // Back is treated as snooze so the alarm isn't dismissed accidentally.
        sendAlarmAction(Constants.ACTION_ALARM_SNOOZE);
    }
}
