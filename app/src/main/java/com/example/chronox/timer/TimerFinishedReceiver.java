package com.example.chronox.timer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** AlarmManager receiver for a countdown deadline. */
public class TimerFinishedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        long endTimestamp = intent.getLongExtra(CountdownStateStore.EXTRA_END_TIMESTAMP, 0L);
        TimerCountdownScheduler.handleDeadline(context.getApplicationContext(), endTimestamp);
    }
}
