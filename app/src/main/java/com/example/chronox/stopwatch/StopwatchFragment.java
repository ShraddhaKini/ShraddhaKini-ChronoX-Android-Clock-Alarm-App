package com.example.chronox.stopwatch;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.chronox.R;
import com.example.chronox.common.TimeUtils;
import com.example.chronox.common.DialogStyler;
import com.example.chronox.common.NotificationHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.example.chronox.databinding.FragmentStopwatchBinding;

/** Stopwatch continues logically while ChronoX is backgrounded and resets on a new app process. */
public class StopwatchFragment extends Fragment {

    private FragmentStopwatchBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private enum State { IDLE, RUNNING, PAUSED }
    private State state = State.IDLE;

    private long startTimestamp = 0L;
    private long pausedAccumulator = 0L;
    private int lapCount = 0;
    private long lastLapTotalMs = 0L;
    private LapAdapter lapAdapter;

    private static final long TICK_MS = 50L;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (binding == null || state != State.RUNNING) return;
            updateDisplay(getElapsedMs());
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStopwatchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        lapAdapter = new LapAdapter();
        binding.rvLaps.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvLaps.setAdapter(lapAdapter);
        binding.rvLaps.setHasFixedSize(false);

        // Auto-size long values (hours/days) but retain the familiar large MM:SS.cc display.
        binding.tvStopwatchTime.setSingleLine(true);
        binding.tvStopwatchTime.setAutoSizeTextTypeUniformWithConfiguration(
                22, 48, 1, TypedValue.COMPLEX_UNIT_SP);

        binding.btnStartPause.setOnClickListener(v -> onStartPauseClicked());
        binding.btnLapReset.setOnClickListener(v -> onLapResetClicked());

        restoreCurrentProcessState();
        updateDisplay(getElapsedMs());
        updateButtonStates();
        if (state == State.RUNNING) startTicker();
        if (state == State.RUNNING || state == State.PAUSED) {
            NotificationHelper.showStopwatchNotification(requireContext(), getElapsedMs(),
                    state == State.RUNNING);
        } else {
            NotificationHelper.cancelStopwatchNotification(requireContext());
        }
    }

    @Override public void onResume() {
        super.onResume();
        if (state == State.RUNNING) startTicker();
        if (state == State.RUNNING || state == State.PAUSED) {
            NotificationHelper.showStopwatchNotification(requireContext(), getElapsedMs(),
                    state == State.RUNNING);
        }
    }

    @Override public void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(ticker);
        binding = null;
    }

    private void restoreCurrentProcessState() {
        StopwatchStateStore.Snapshot snapshot = StopwatchStateStore.load(requireContext());
        if (snapshot == null) {
            state = State.IDLE;
            startTimestamp = 0L;
            pausedAccumulator = 0L;
            lapCount = 0;
            lastLapTotalMs = 0L;
            lapAdapter.clearLaps();
            saveState(); // Overwrite any prior-process state with a fresh session.
            return;
        }
        try {
            state = State.valueOf(snapshot.state);
        } catch (Exception ignored) {
            state = State.IDLE;
        }
        startTimestamp = snapshot.startTimestamp;
        pausedAccumulator = Math.max(0L, snapshot.pausedAccumulator);
        lapCount = Math.max(0, snapshot.lapCount);
        lastLapTotalMs = Math.max(0L, snapshot.lastLapTotalMs);
        lapAdapter.restoreLaps(snapshot.laps);
    }

    private void onStartPauseClicked() {
        switch (state) {
            case IDLE:
                startTimestamp = System.currentTimeMillis();
                lastLapTotalMs = 0L;
                pausedAccumulator = 0L;
                state = State.RUNNING;
                break;
            case PAUSED:
                startTimestamp = System.currentTimeMillis();
                state = State.RUNNING;
                break;
            case RUNNING:
                pausedAccumulator += System.currentTimeMillis() - startTimestamp;
                state = State.PAUSED;
                handler.removeCallbacks(ticker);
                updateDisplay(pausedAccumulator);
                break;
        }
        saveState();
        if (state == State.RUNNING) startTicker();
        if (state == State.RUNNING || state == State.PAUSED) {
            NotificationHelper.showStopwatchNotification(requireContext(), getElapsedMs(),
                    state == State.RUNNING);
        } else {
            NotificationHelper.cancelStopwatchNotification(requireContext());
        }
        updateButtonStates();
    }

    private void onLapResetClicked() {
        switch (state) {
            case RUNNING: {
                lapCount++;
                long totalElapsed = getElapsedMs();
                long lapTime = totalElapsed - lastLapTotalMs;
                lastLapTotalMs = totalElapsed;
                lapAdapter.addLap(new Lap(lapCount, lapTime, totalElapsed));
                binding.rvLaps.scrollToPosition(0);
                saveState();
                NotificationHelper.showStopwatchNotification(requireContext(), totalElapsed, true);
                break;
            }
            case PAUSED:
                showResetConfirmation();
                return;
            case IDLE:
                return;
        }
        updateButtonStates();
    }

    private void showResetConfirmation() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(
                requireContext(), R.style.ThemeOverlay_ChronoX_AlertDialog)
                .setTitle(R.string.stopwatch_reset_confirm_title)
                .setMessage(R.string.stopwatch_reset_confirm_message)
                .setPositiveButton(R.string.timer_reset, (ignored, which) -> resetStopwatchNow())
                .setNegativeButton(R.string.cancel, null)
                .create();
        // Prepare dim and blur before the confirmation dialog's first visible frame.
        DialogStyler.show(dialog, requireContext());
    }

    private void resetStopwatchNow() {
        handler.removeCallbacks(ticker);
        state = State.IDLE;
        startTimestamp = 0L;
        pausedAccumulator = 0L;
        lapCount = 0;
        lastLapTotalMs = 0L;
        lapAdapter.clearLaps();
        updateDisplay(0L);
        saveState();
        NotificationHelper.cancelStopwatchNotification(requireContext());
        updateButtonStates();
    }

    private long getElapsedMs() {
        if (state == State.RUNNING) {
            return pausedAccumulator + Math.max(0L, System.currentTimeMillis() - startTimestamp);
        }
        return pausedAccumulator;
    }

    private void startTicker() {
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private void updateDisplay(long elapsedMs) {
        if (binding == null) return;
        binding.tvStopwatchTime.setText(TimeUtils.formatMSSc(elapsedMs));
    }

    private void updateButtonStates() {
        if (binding == null) return;
        switch (state) {
            case IDLE:
                binding.btnStartPause.setText(R.string.stopwatch_start);
                binding.btnLapReset.setText(R.string.stopwatch_lap);
                binding.btnLapReset.setEnabled(false);
                break;
            case RUNNING:
                binding.btnStartPause.setText(R.string.stopwatch_pause);
                binding.btnLapReset.setText(R.string.stopwatch_lap);
                binding.btnLapReset.setEnabled(true);
                break;
            case PAUSED:
                binding.btnStartPause.setText(R.string.stopwatch_resume);
                binding.btnLapReset.setText(R.string.stopwatch_reset);
                binding.btnLapReset.setEnabled(true);
                break;
        }
    }

    private void saveState() {
        if (getContext() == null) return;
        StopwatchStateStore.save(requireContext(), state.name(), startTimestamp,
                pausedAccumulator, lapCount, lastLapTotalMs,
                lapAdapter == null ? null : lapAdapter.getLapsSnapshot());
    }
}
