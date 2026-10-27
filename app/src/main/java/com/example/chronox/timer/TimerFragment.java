package com.example.chronox.timer;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.chronox.R;
import com.example.chronox.common.TimeUtils;
import com.example.chronox.common.DialogStyler;
import com.example.chronox.databinding.FragmentTimerBinding;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Durable countdown timer. The stored wall-clock deadline keeps time accurate while backgrounded. */
public class TimerFragment extends Fragment {

    private FragmentTimerBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private enum State { IDLE, RUNNING, PAUSED }
    private State state = State.IDLE;

    private long endTimestamp = 0L;
    private long remainingOnPause = 0L;

    private int inputHours = 0;
    private int inputMinutes = 0;
    private int inputSeconds = 0;
    private boolean updatingInputFields = false;
    private AlertDialog countdownEndedDialog;
    private boolean alertStoppedReceiverRegistered;

    private final BroadcastReceiver alertStoppedReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (countdownEndedDialog != null && countdownEndedDialog.isShowing()) {
                countdownEndedDialog.dismiss();
            }
            countdownEndedDialog = null;
        }
    };

    private static final long TICK_MS = 100L;
    private static final int TYPE_HOURS = 0;
    private static final int TYPE_MINUTES = 1;
    private static final int TYPE_SECONDS = 2;

    private static final long[] PRESETS_MS = {
            60_000L, 180_000L, 300_000L, 600_000L, 900_000L, 1_800_000L
    };
    private static final String[] PRESETS_LABEL = {"1m", "3m", "5m", "10m", "15m", "30m"};

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (binding == null || state != State.RUNNING) return;
            long remaining = endTimestamp - System.currentTimeMillis();
            if (remaining <= 0L) {
                completeCountdown();
                return;
            }
            updateDisplay(remaining);
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTimerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupDurationButtons();
        setupEditableInputs();
        setupPresetChips();
        binding.btnTimerStartPause.setOnClickListener(v -> onStartPauseClicked());
        binding.btnTimerReset.setOnClickListener(v -> onResetClicked());

        restoreSavedState();
        syncInputLabels();
        renderCurrentState();
        maybeShowCountdownEndedDialog();
    }

    @Override public void onResume() {
        super.onResume();
        // The AlarmManager receiver may have completed the timer while this page was away.
        if (binding != null) {
            restoreSavedState();
            renderCurrentState();
            maybeShowCountdownEndedDialog();
        }
    }

    @Override public void onStart() {
        super.onStart();
        if (!alertStoppedReceiverRegistered && getContext() != null) {
            IntentFilter filter = new IntentFilter(TimerAlertService.ACTION_ALERT_STOPPED);
            ContextCompat.registerReceiver(requireContext(), alertStoppedReceiver, filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            alertStoppedReceiverRegistered = true;
        }
    }

    @Override public void onStop() {
        if (alertStoppedReceiverRegistered && getContext() != null) {
            try { requireContext().unregisterReceiver(alertStoppedReceiver); }
            catch (IllegalArgumentException ignored) { }
            alertStoppedReceiverRegistered = false;
        }
        dismissCountdownEndedDialog();
        super.onStop();
    }

    @Override public void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(ticker);
        dismissCountdownEndedDialog();
        binding = null;
    }

    private void restoreSavedState() {
        Context context = getContext();
        if (context == null) return;
        SharedPreferences prefs = CountdownStateStore.preferences(context);

        inputHours = clamp(prefs.getInt(CountdownStateStore.KEY_HOURS, 0), 0, 23);
        inputMinutes = clamp(prefs.getInt(CountdownStateStore.KEY_MINUTES, 0), 0, 59);
        inputSeconds = clamp(prefs.getInt(CountdownStateStore.KEY_SECONDS, 0), 0, 59);
        endTimestamp = prefs.getLong(CountdownStateStore.KEY_END_TIMESTAMP, 0L);
        remainingOnPause = Math.max(0L, prefs.getLong(CountdownStateStore.KEY_REMAINING_PAUSED, 0L));

        String savedState = prefs.getString(CountdownStateStore.KEY_STATE, CountdownStateStore.STATE_IDLE);
        try {
            state = State.valueOf(savedState);
        } catch (Exception ignored) {
            state = State.IDLE;
        }

        if (state == State.RUNNING) {
            if (endTimestamp > System.currentTimeMillis()) {
                TimerCountdownScheduler.schedule(context, endTimestamp);
            } else if (endTimestamp > 0L) {
                completeCountdown();
            } else {
                state = State.IDLE;
                remainingOnPause = 0L;
                persistState();
            }
        } else if (state == State.PAUSED) {
            TimerCountdownScheduler.cancel(context);
        } else {
            state = State.IDLE;
            remainingOnPause = 0L;
        }
    }

    private void renderCurrentState() {
        if (binding == null) return;
        if (state == State.RUNNING) {
            showInputRow(false);
            long remaining = endTimestamp - System.currentTimeMillis();
            if (remaining <= 0L) {
                completeCountdown();
                return;
            }
            updateDisplay(remaining);
            updateButtonStates();
            startTicker();
        } else if (state == State.PAUSED) {
            showInputRow(false);
            updateDisplay(remainingOnPause);
            updateButtonStates();
            handler.removeCallbacks(ticker);
        } else {
            showInputRow(true);
            updateDisplay(0L);
            updateButtonStates();
            handler.removeCallbacks(ticker);
        }
    }

    private void onStartPauseClicked() {
        Context context = requireContext();
        switch (state) {
            case IDLE: {
                long totalMs = TimeUtils.toMillis(inputHours, inputMinutes, inputSeconds);
                if (totalMs <= 0L) return;
                endTimestamp = System.currentTimeMillis() + totalMs;
                remainingOnPause = 0L;
                state = State.RUNNING;
                showInputRow(false);
                persistStateDurably();
                TimerCountdownScheduler.schedule(context, endTimestamp);
                startTicker();
                break;
            }
            case RUNNING:
                remainingOnPause = Math.max(0L, endTimestamp - System.currentTimeMillis());
                state = State.PAUSED;
                handler.removeCallbacks(ticker);
                TimerCountdownScheduler.cancel(context);
                persistStateDurably();
                break;
            case PAUSED:
                endTimestamp = System.currentTimeMillis() + remainingOnPause;
                state = State.RUNNING;
                persistStateDurably();
                TimerCountdownScheduler.schedule(context, endTimestamp);
                startTicker();
                break;
        }
        updateButtonStates();
    }

    private void onResetClicked() {
        if (state == State.IDLE && inputHours == 0
                && inputMinutes == 0 && inputSeconds == 0) return;

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(
                requireContext(), R.style.ThemeOverlay_ChronoX_AlertDialog)
                .setTitle("Reset countdown?")
                .setMessage("This will stop the countdown and clear the selected time.")
                .setPositiveButton("Reset", (ignored, which) -> resetCountdownNow())
                .setNegativeButton("Cancel", null)
                .create();
        DialogStyler.show(dialog, requireContext());
    }

    private void resetCountdownNow() {
        handler.removeCallbacks(ticker);
        if (TimerAlertService.isAlerting(requireContext())) {
            TimerAlertService.stop(requireContext());
        }
        TimerCountdownScheduler.cancel(requireContext());
        state = State.IDLE;
        remainingOnPause = 0L;
        endTimestamp = 0L;
        inputHours = inputMinutes = inputSeconds = 0;
        persistStateDurably();
        syncInputLabels();
        updateDisplay(0L);
        showInputRow(true);
        updateButtonStates();
    }

    private void completeCountdown() {
        long completedEnd = endTimestamp;
        boolean shouldNotify = CountdownStateStore.markCompletedIfDue(getContext(), completedEnd);
        handler.removeCallbacks(ticker);
        TimerCountdownScheduler.cancel(getContext());
        state = State.IDLE;
        remainingOnPause = 0L;
        // Keep the selected input values so they can be reused, but display 00:00 on completion.
        updateDisplay(0L);
        showInputRow(true);
        updateButtonStates();
        if (shouldNotify && getContext() != null) {
            TimerCompletionNotifier.notifyFinished(requireContext());
            maybeShowCountdownEndedDialog();
        }
    }

    private void maybeShowCountdownEndedDialog() {
        if (binding == null || !isAdded() || !isResumed()
                || !TimerAlertService.isAlerting(requireContext())) return;
        if (countdownEndedDialog != null && countdownEndedDialog.isShowing()) return;

        countdownEndedDialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                requireContext(), R.style.ThemeOverlay_ChronoX_AlertDialog)
                .setTitle(R.string.timer_done)
                .setMessage(R.string.timer_finished_message)
                .setPositiveButton(R.string.timer_stop_sound, (dialog, which) ->
                        TimerAlertService.stop(requireContext()))
                .setCancelable(false)
                .create();
        DialogStyler.show(countdownEndedDialog, requireContext());
    }

    private void dismissCountdownEndedDialog() {
        if (countdownEndedDialog != null) {
            if (countdownEndedDialog.isShowing()) countdownEndedDialog.dismiss();
            countdownEndedDialog = null;
        }
    }

    private void setupDurationButtons() {
        binding.btnHoursUp.setOnClickListener(v -> {
            if (inputHours < 23) { inputHours++; syncInputLabels(); }
        });
        binding.btnHoursDown.setOnClickListener(v -> {
            if (inputHours > 0) { inputHours--; syncInputLabels(); }
        });
        binding.btnMinutesUp.setOnClickListener(v -> {
            if (inputMinutes < 59) { inputMinutes++; syncInputLabels(); }
        });
        binding.btnMinutesDown.setOnClickListener(v -> {
            if (inputMinutes > 0) { inputMinutes--; syncInputLabels(); }
        });
        binding.btnSecondsUp.setOnClickListener(v -> {
            if (inputSeconds < 59) { inputSeconds++; syncInputLabels(); }
        });
        binding.btnSecondsDown.setOnClickListener(v -> {
            if (inputSeconds > 0) { inputSeconds--; syncInputLabels(); }
        });
    }

    private void setupEditableInputs() {
        addInputWatcher(binding.tvHours, TYPE_HOURS, 23);
        addInputWatcher(binding.tvMinutes, TYPE_MINUTES, 59);
        addInputWatcher(binding.tvSeconds, TYPE_SECONDS, 59);
    }

    private void addInputWatcher(EditText field, int type, int max) {
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        field.setOnEditorActionListener((textView, actionId, event) -> {
            boolean enterPressed = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_UP;
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_GO || enterPressed) {
                InputMethodManager imm = (InputMethodManager) requireContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
                field.clearFocus();
                return true;
            }
            return false;
        });
        field.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) field.selectAll();
        });
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable editable) {
                if (updatingInputFields) return;
                String value = editable.toString().trim();
                int parsed;
                try {
                    parsed = value.isEmpty() ? 0 : Integer.parseInt(value);
                } catch (NumberFormatException ex) {
                    parsed = max;
                }
                if (parsed > max) {
                    parsed = max;
                    updatingInputFields = true;
                    field.setText(String.valueOf(parsed));
                    field.setSelection(field.length());
                    updatingInputFields = false;
                }
                if (parsed < 0) parsed = 0;
                switch (type) {
                    case TYPE_HOURS: inputHours = parsed; break;
                    case TYPE_MINUTES: inputMinutes = parsed; break;
                    case TYPE_SECONDS: inputSeconds = parsed; break;
                }
                persistState();
                updateButtonStates();
            }
        });
    }

    private void syncInputLabels() {
        if (binding == null) return;
        updatingInputFields = true;
        binding.tvHours.setText(String.format("%02d", inputHours));
        binding.tvMinutes.setText(String.format("%02d", inputMinutes));
        binding.tvSeconds.setText(String.format("%02d", inputSeconds));
        binding.tvHours.setSelection(binding.tvHours.length());
        binding.tvMinutes.setSelection(binding.tvMinutes.length());
        binding.tvSeconds.setSelection(binding.tvSeconds.length());
        updatingInputFields = false;
        persistState();
        updateButtonStates();
    }

    private void setupPresetChips() {
        android.util.TypedValue value = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(R.attr.colorAppSurfaceSecondary, value, true);
        int chipBgColor = value.data;
        requireContext().getTheme().resolveAttribute(R.attr.colorAppTextPrimary, value, true);
        int textColor = value.data;

        for (int i = 0; i < PRESETS_MS.length; i++) {
            final long durationMs = PRESETS_MS[i];
            Chip chip = new Chip(requireContext(), null, R.style.Widget_ChronoX_Chip);
            chip.setText(PRESETS_LABEL[i]);
            chip.setCheckable(false);
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(chipBgColor));
            chip.setTextColor(textColor);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(8, 0, 8, 0);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> applyPreset(durationMs));
            binding.llPresets.addView(chip);
        }
    }

    private void applyPreset(long durationMs) {
        if (state != State.IDLE) resetCountdownNow();
        inputHours = TimeUtils.hoursFromMillis(durationMs);
        inputMinutes = TimeUtils.minutesFromMillis(durationMs);
        inputSeconds = TimeUtils.secondsFromMillis(durationMs);
        syncInputLabels();
        updateButtonStates();
    }

    private void updateDisplay(long remainingMs) {
        if (binding == null) return;
        String text = TimeUtils.formatSmart(remainingMs);
        binding.tvTimerDisplay.setText(text);
        int length = text.length();
        float sizeSp;
        if (length >= 13) sizeSp = 22f;
        else if (length >= 12) sizeSp = 26f;
        else if (length >= 10) sizeSp = 32f;
        else if (length >= 8) sizeSp = 56f;
        else sizeSp = 112f;
        binding.tvTimerDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        if (remainingMs > 0L && remainingMs < 10_000L) {
            binding.tvTimerDisplay.setTextColor(getColorFromAttr(R.attr.colorTimeout));
        } else {
            binding.tvTimerDisplay.setTextColor(getColorFromAttr(R.attr.colorAppTextPrimary));
        }
    }

    private int getColorFromAttr(int attrRes) {
        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(attrRes, typedValue, true);
        return typedValue.data;
    }

    private void updateButtonStates() {
        if (binding == null) return;
        boolean hasInput = inputHours > 0 || inputMinutes > 0 || inputSeconds > 0;
        switch (state) {
            case IDLE:
                binding.btnTimerStartPause.setText(R.string.timer_start);
                binding.btnTimerReset.setEnabled(hasInput);
                break;
            case RUNNING:
                binding.btnTimerStartPause.setText(R.string.timer_pause);
                binding.btnTimerReset.setEnabled(true);
                break;
            case PAUSED:
                binding.btnTimerStartPause.setText(R.string.timer_resume);
                binding.btnTimerReset.setEnabled(true);
                break;
        }
    }

    private void showInputRow(boolean show) {
        if (binding == null) return;
        binding.llDurationInput.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.tvTimerDisplay.setVisibility(show ? View.GONE : View.VISIBLE);
        binding.hsvPresets.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void startTicker() {
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private void persistState() {
        Context context = getContext();
        if (context == null) return;
        CountdownStateStore.save(context, state.name(), endTimestamp, remainingOnPause,
                0, inputHours, inputMinutes, inputSeconds);
    }

    private void persistStateDurably() {
        Context context = getContext();
        if (context == null) return;
        CountdownStateStore.saveDurably(context, state.name(), endTimestamp, remainingOnPause,
                0, inputHours, inputMinutes, inputSeconds);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
