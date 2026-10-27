package com.example.chronox.home;

import android.content.SharedPreferences;
import android.graphics.Paint;
import android.util.TypedValue;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.chronox.R;
import com.example.chronox.common.Constants;
import com.example.chronox.common.DateUtils;
import com.example.chronox.databinding.FragmentHomeBinding;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * HomeFragment — Digital Clock screen.
 *
 * Displays the current time and date, updating every second.
 *
 * KEY DESIGN DECISIONS:
 *
 * 1. Timestamp-based time: We call ZonedDateTime.now() on every tick.
 *    We do NOT increment a counter ("seconds++") because Android Handler
 *    callbacks are not guaranteed to run at exactly 1-second intervals —
 *    especially under load, doze mode, or after app resume.
 *    By always reading the system clock, the display is always accurate.
 *
 * 2. Handler lifecycle management: The update Runnable is only posted
 *    in onResume() and cancelled in onPause(). This prevents:
 *    - Memory leaks (no reference to destroyed Fragment)
 *    - Duplicate tickers (only one runs at a time)
 *    - Battery waste (no ticking when screen is off)
 *
 * 3. 12/24h format stored in SharedPreferences so the preference
 *    persists across app restarts. Toggled by the top-right button.
 *
 * 4. Next alarm: Loaded from Room database via background thread.
 *    Shown below the clock as a convenience info card.
 */
public class HomeFragment extends Fragment {

    // ViewBinding for type-safe view access — released in onDestroyView()
    private FragmentHomeBinding binding;

    // Handler on the main thread for scheduling UI updates
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Whether 24-hour format is currently active
    private boolean is24HourFormat;

    // SharedPreferences for persisting user's format preference
    private SharedPreferences prefs;

    /**
     * Runnable that reads the current system time and updates the UI.
     * Re-posts itself every ~200ms so seconds always show on time.
     *
     * Why 200ms instead of 1000ms?
     * To stay accurate even if a callback fires 100ms late. The UI update
     * cost is negligible for a simple text set.
     */
    private final Runnable clockUpdater = new Runnable() {
        @Override
        public void run() {
            if (binding == null) return; // Fragment view already destroyed — stop

            updateClockDisplay();

            // Schedule next update at the start of the next second boundary
            // This keeps seconds ticking at exactly the right moment
            long now = System.currentTimeMillis();
            long delay = 1000 - (now % 1000); // ms until next full second
            handler.postDelayed(this, delay);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Load format preference
        prefs = requireContext().getSharedPreferences(Constants.PREFS_NAME, 0);
        is24HourFormat = prefs.getBoolean(Constants.PREF_CLOCK_FORMAT_24H, false);

        // Update button label to show current mode
        updateFormatButtonLabel();

        // Settings button — open the Settings destination from the Home screen.
        // This uses the Home view's NavController directly so it is independent
        // of the bottom-navigation listener.
        binding.btnSettings.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.nav_settings));

        // 12/24h toggle button
        binding.btnFormatToggle.setOnClickListener(v -> {
            is24HourFormat = !is24HourFormat;
            // Persist choice immediately
            prefs.edit().putBoolean(Constants.PREF_CLOCK_FORMAT_24H, is24HourFormat).apply();
            updateFormatButtonLabel();
            updateClockDisplay(); // Immediate refresh, no wait for next tick
        });

        // Only the blue time-zone label (e.g. "Asia/Calcutta") opens World Clock;
        // the day/date text next to it is intentionally not clickable.
        binding.tvTimezone.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.nav_world_clock));

        // Tapping the Next Alarm card opens the Alarm screen.
        binding.cardNextAlarm.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.nav_alarm));

        // Refit the main clock only when the available width requires it.
        // The original sizes remain unchanged on devices where the whole clock fits.
        binding.llMainClock.addOnLayoutChangeListener((v, left, top, right, bottom,
                                                       oldLeft, oldTop, oldRight, oldBottom) -> {
            if ((right - left) != (oldRight - oldLeft)) {
                updateClockFontScale();
            }
        });

        // Load next alarm info from DB on a background thread
        loadNextAlarm();
    }


    @Override
    public void onResume() {
        super.onResume();
        // Refresh the next-alarm card whenever Home becomes visible.
        loadNextAlarm();

        // Start the clock ticker — immediately show current time
        // then schedule the next update at the next second boundary
        updateClockDisplay();
        long now = System.currentTimeMillis();
        long delay = 1000 - (now % 1000);
        handler.postDelayed(clockUpdater, delay);
    }

    @Override
    public void onPause() {
        super.onPause();
        // CRITICAL: Remove all pending callbacks to prevent:
        // 1. Callbacks firing after Fragment is paused (memory leak)
        // 2. Multiple tickers if onResume() is called again
        handler.removeCallbacks(clockUpdater);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Release binding to avoid holding a reference to the destroyed view
        handler.removeCallbacks(clockUpdater);
        binding = null;
    }

    /**
     * Reads the current system time and updates all clock TextViews.
     *
     * Always calls ZonedDateTime.now() — never uses a counter.
     * This guarantees accuracy regardless of when the callback fires.
     */
    private void updateClockDisplay() {
        if (binding == null) return;

        // Get current time in the device's local timezone
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());

        if (is24HourFormat) {
            // 24h: show "HH:MM", hide AM/PM
            binding.tvTimeHm.setText(DateUtils.formatTime24H(now));
            binding.tvAmpm.setVisibility(View.GONE);
        } else {
            // 12h: show "hh:MM" + AM/PM label
            binding.tvTimeHm.setText(DateUtils.formatTime12H(now));
            binding.tvAmpm.setText(DateUtils.formatAmPm(now));
            binding.tvAmpm.setVisibility(View.VISIBLE);
        }

        // Seconds — always shown in secondary color below main time
        binding.tvSeconds.setText(DateUtils.formatSeconds(now));

        // Full day name (e.g. "Wednesday") — no comma, date has the rest
        binding.tvDay.setText(DateUtils.formatDayName(now) + ",");

        // Short date WITHOUT day abbrev (e.g. "Aug 19") — avoids "Wednesday, Wed Aug 19"
        binding.tvDate.setText(DateUtils.formatDateNoDay(now));

        // Time zone ID (e.g. "Asia/Calcutta")
        binding.tvTimezone.setText(now.getZone().getId());

        // Scale the whole time display proportionally only if it would otherwise
        // exceed the available width. On wider phones, the original font sizes
        // (104sp / 50sp / 36sp) are preserved exactly.
        updateClockFontScale();
    }

    /**
     * Keep hours/minutes, separator, and seconds visible on narrow screens.
     * This is a width-based fit, not a global font reduction: it returns to the
     * original sizes whenever sufficient horizontal space is available.
     */
    private void updateClockFontScale() {
        if (binding == null) return;

        int availableWidthPx = binding.llMainClock.getWidth();
        if (availableWidthPx <= 0) {
            // The first update can happen before measurement. Run again after layout.
            binding.llMainClock.post(this::updateClockFontScale);
            return;
        }

        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        String timeText = binding.tvTimeHm.getText() == null
                ? "00:00" : binding.tvTimeHm.getText().toString();
        String secondsText = binding.tvSeconds.getText() == null
                ? "00" : binding.tvSeconds.getText().toString();

        Paint timePaint = new Paint(binding.tvTimeHm.getPaint());
        timePaint.setTextSize(104f * scaledDensity);
        float timeWidth = timePaint.measureText(timeText);
        // TextView letterSpacing is 0.02em; include it in the width estimate.
        timeWidth += Math.max(0, timeText.length() - 1) * 104f * scaledDensity * 0.02f;

        Paint separatorPaint = new Paint(binding.tvTimeHm.getPaint());
        separatorPaint.setTextSize(50f * scaledDensity);
        float separatorWidth = separatorPaint.measureText(":");

        Paint secondsPaint = new Paint(binding.tvTimeHm.getPaint());
        secondsPaint.setTextSize(36f * scaledDensity);
        float secondsWidth = secondsPaint.measureText(secondsText);

        // A few pixels of safety space avoids edge clipping on devices with
        // different font rendering and display-density rounding.
        float safetyPaddingPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8f, getResources().getDisplayMetrics());
        float requiredWidthPx = timeWidth + separatorWidth + secondsWidth + safetyPaddingPx;
        if (requiredWidthPx <= 0f) return;

        float scale = Math.min(1f, availableWidthPx / requiredWidthPx);
        setTextSizeIfChanged(binding.tvTimeHm, 104f * scale);
        // The separator TextView is the only anonymous child in the clock row.
        android.view.View clockRow = (android.view.View) binding.tvTimeHm.getParent();
        if (clockRow instanceof android.view.ViewGroup) {
            android.view.ViewGroup row = (android.view.ViewGroup) clockRow;
            for (int i = 0; i < row.getChildCount(); i++) {
                android.view.View child = row.getChildAt(i);
                if (child instanceof android.widget.TextView && child.getId() == View.NO_ID
                        && ":".contentEquals(((android.widget.TextView) child).getText())) {
                    setTextSizeIfChanged((android.widget.TextView) child, 50f * scale);
                    break;
                }
            }
        }
        setTextSizeIfChanged(binding.tvSeconds, 36f * scale);
    }

    private void setTextSizeIfChanged(android.widget.TextView textView, float sizeSp) {
        float desiredPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, sizeSp, getResources().getDisplayMetrics());
        if (Math.abs(textView.getTextSize() - desiredPx) > 0.5f) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        }
    }

    /**
     * Update the format toggle button label to reflect the current state.
     * Shows "12H" when in 24h mode (meaning: tap to switch to 12h)
     * Shows "24H" when in 12h mode (meaning: tap to switch to 24h)
     */
    private void updateFormatButtonLabel() {
        if (binding == null) return;
        binding.btnFormatToggle.setText(is24HourFormat ? "12H" : "24H");
    }

    /**
     * Load the next upcoming alarm from the database.
     * Runs on a background thread to avoid blocking the UI.
     *
     * If no alarm exists or DB is empty, shows "No alarm set".
     */
    private void loadNextAlarm() {
        new Thread(() -> {
            try {
                com.example.chronox.data.AppDatabase db =
                        com.example.chronox.data.AppDatabase.getInstance(requireContext());
                java.util.List<com.example.chronox.alarm.Alarm> enabled =
                        db.alarmDao().getEnabledAlarmsSync();

                String nextAlarmText = getString(R.string.clock_no_alarm);

                if (!enabled.isEmpty()) {
                    // Find the alarm with the soonest next trigger time
                    long earliest = Long.MAX_VALUE;
                    com.example.chronox.alarm.Alarm next = null;
                    for (com.example.chronox.alarm.Alarm a : enabled) {
                        long t = com.example.chronox.alarm.AlarmScheduler.computeNextTriggerTime(a);
                        if (t < earliest) { earliest = t; next = a; }
                    }
                    if (next != null) {
                        boolean use24h = prefs.getBoolean(Constants.PREF_CLOCK_FORMAT_24H, false);
                        String timeStr = DateUtils.formatAlarmTime(next.hour, next.minute, use24h);
                        nextAlarmText = getString(R.string.clock_next_alarm, timeStr);
                    }
                }

                final String display = nextAlarmText;
                if (binding != null) {
                    handler.post(() -> {
                        if (binding != null) binding.tvNextAlarm.setText(display);
                    });
                }
            } catch (Exception e) {
                // Non-critical — silently ignore
            }
        }).start();
    }


    /**
     * Called from AlarmFragment/AlarmRepository when an alarm is saved or deleted,
     * to refresh the "next alarm" display on the home screen.
     * This will be wired up properly in Phase 6 when the Alarm module is complete.
     */
    public void refreshNextAlarm() {
        loadNextAlarm();
    }
}

