package com.example.chronox.alarm;

import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.chronox.R;
import com.example.chronox.common.Constants;
import com.example.chronox.common.DialogStyler;
import com.example.chronox.data.AppDatabase;
import com.example.chronox.databinding.FragmentAlarmBinding;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AlarmFragment — displays the list of saved alarms and allows add/edit/delete/toggle.
 *
 * Architecture:
 * - Room LiveData → AlarmAdapter → RecyclerView (reactive, no manual refresh needed)
 * - DB writes (insert/delete/toggle) run on background thread via ExecutorService
 * - AlarmScheduler is called after each DB write to keep AlarmManager in sync
 *
 * Add alarm: FAB → time picker dialog → save to DB → schedule with AlarmManager
 * Toggle: switch → update isEnabled in DB → schedule or cancel with AlarmManager
 * Delete: long press → confirm dialog → delete from DB → cancel AlarmManager
 */
public class AlarmFragment extends Fragment {

    private FragmentAlarmBinding binding;
    private AlarmAdapter adapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAlarmBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = requireContext().getSharedPreferences(Constants.PREFS_NAME, 0);
        boolean use24h = prefs.getBoolean(Constants.PREF_CLOCK_FORMAT_24H, false);

        // Set up RecyclerView
        adapter = new AlarmAdapter();
        adapter.setUse24h(use24h);
        binding.rvAlarms.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvAlarms.setAdapter(adapter);
        binding.rvAlarms.setHasFixedSize(false);

        // Observe alarm list from DB — auto-updates RecyclerView
        AppDatabase.getInstance(requireContext())
                .alarmDao()
                .getAllAlarms()
                .observe(getViewLifecycleOwner(), alarms -> {
                    adapter.submitList(alarms);
                    binding.tvEmptyAlarms.setVisibility(
                            alarms == null || alarms.isEmpty() ? View.VISIBLE : View.GONE);
                });

        // Toggle alarm enabled/disabled
        adapter.setOnToggleListener((alarm, isEnabled) -> {
            final android.content.Context appContext = requireContext().getApplicationContext();
            final android.app.Activity ownerActivity = getActivity();
            final boolean use24hForToast = prefs.getBoolean(Constants.PREF_CLOCK_FORMAT_24H, false);
            executor.execute(() -> {
                AppDatabase db = AppDatabase.getInstance(appContext);
                db.alarmDao().setEnabled(alarm.id, isEnabled);
                alarm.isEnabled = isEnabled;
                if (isEnabled) {
                    // If an old one-time alarm date has passed, persist the next actual date
                    // that computeNextTriggerTime will use when the alarm is re-enabled.
                    if (alarm.repeatDays == 0 && alarm.scheduledDateMillis > 0L) {
                        long nextTrigger = AlarmScheduler.computeNextTriggerTime(alarm);
                        Calendar triggerDay = Calendar.getInstance();
                        triggerDay.setTimeInMillis(nextTrigger);
                        setStartOfDay(triggerDay);
                        Calendar storedDay = Calendar.getInstance();
                        storedDay.setTimeInMillis(alarm.scheduledDateMillis);
                        setStartOfDay(storedDay);
                        if (triggerDay.getTimeInMillis() != storedDay.getTimeInMillis()) {
                            alarm.scheduledDateMillis = triggerDay.getTimeInMillis();
                            db.alarmDao().update(alarm);
                        }
                    }
                    AlarmScheduler.scheduleAlarm(appContext, alarm);
                    long triggerAt = AlarmScheduler.computeNextTriggerTime(alarm);
                    long remaining = Math.max(0L, triggerAt - System.currentTimeMillis());
                    String remainingText = formatRemainingTime(remaining);
                    String alarmTime = com.example.chronox.common.DateUtils.formatAlarmTime(
                            alarm.hour, alarm.minute, use24hForToast);
                    String message = "Alarm set for " + alarmTime + " — in " + remainingText;
                    if (ownerActivity != null) ownerActivity.runOnUiThread(() ->
                            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show());
                } else {
                    AlarmScheduler.cancelAlarm(appContext, alarm);
                }
            });
        });

        // Tap -> edit alarm
        adapter.setOnClickListener(alarm -> showEditAlarmDialog(alarm));

        // Long press -> delete confirmation
        adapter.setOnDeleteListener(alarm -> showDeleteDialog(alarm));

        // FAB -> show time picker to add new alarm
        binding.fabAddAlarm.setOnClickListener(v -> showEditAlarmDialog(null));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void showEditAlarmDialog(@Nullable Alarm existingAlarm) {
        boolean use24h = prefs.getBoolean(Constants.PREF_CLOCK_FORMAT_24H, false);
        final Alarm alarm = (existingAlarm != null) ? existingAlarm : Alarm.createDefault(7, 0);
        final android.content.Context appContext = requireContext().getApplicationContext();

        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_alarm_edit, null);
        android.widget.TextView tvTime = view.findViewById(R.id.tv_alarm_time);
        android.widget.TextView tvDate = view.findViewById(R.id.tv_alarm_date);
        android.widget.TextView tvDateCaption = view.findViewById(R.id.tv_alarm_date_caption);
        com.google.android.material.textfield.TextInputEditText etLabel = view.findViewById(R.id.et_alarm_label);
        com.google.android.material.switchmaterial.SwitchMaterial swSound = view.findViewById(R.id.switch_ringtone);
        com.google.android.material.switchmaterial.SwitchMaterial swVib = view.findViewById(R.id.switch_vibration);

        android.widget.ToggleButton[] days = new android.widget.ToggleButton[] {
            view.findViewById(R.id.btn_day_mon), view.findViewById(R.id.btn_day_tue),
            view.findViewById(R.id.btn_day_wed), view.findViewById(R.id.btn_day_thu),
            view.findViewById(R.id.btn_day_fri), view.findViewById(R.id.btn_day_sat),
            view.findViewById(R.id.btn_day_sun)
        };
        int[] bits = { Constants.DAY_MON, Constants.DAY_TUE, Constants.DAY_WED,
                Constants.DAY_THU, Constants.DAY_FRI, Constants.DAY_SAT, Constants.DAY_SUN };

        Calendar today = Calendar.getInstance();
        setStartOfDay(today);
        long todayStart = today.getTimeInMillis();
        long initialDate;
        if (existingAlarm == null) {
            // New alarms start with today's date; if the default time has passed,
            // the user can choose a later time today or select a future date.
            initialDate = todayStart;
        } else if (alarm.scheduledDateMillis > 0L && alarm.repeatDays == 0) {
            Calendar storedTrigger = Calendar.getInstance();
            storedTrigger.setTimeInMillis(alarm.scheduledDateMillis);
            storedTrigger.set(Calendar.HOUR_OF_DAY, alarm.hour);
            storedTrigger.set(Calendar.MINUTE, alarm.minute);
            storedTrigger.set(Calendar.SECOND, 0);
            storedTrigger.set(Calendar.MILLISECOND, 0);
            if (storedTrigger.getTimeInMillis() <= System.currentTimeMillis()) {
                initialDate = AlarmScheduler.computeNextTriggerTime(alarm);
            } else {
                initialDate = alarm.scheduledDateMillis;
            }
        } else {
            initialDate = AlarmScheduler.computeNextTriggerTime(alarm);
        }
        Calendar initialCalendar = Calendar.getInstance();
        initialCalendar.setTimeInMillis(initialDate);
        setStartOfDay(initialCalendar);
        initialDate = initialCalendar.getTimeInMillis();
        if (initialDate < todayStart) initialDate = todayStart;
        final long[] selectedDateMillis = {initialDate};

        tvTime.setText(com.example.chronox.common.DateUtils.formatAlarmTime(alarm.hour, alarm.minute, use24h));
        tvDate.setText(formatAlarmDate(selectedDateMillis[0]));
        etLabel.setText(alarm.label);
        swSound.setChecked(alarm.soundEnabled);
        swVib.setChecked(alarm.vibrationEnabled);
        for (int i = 0; i < 7; i++) days[i].setChecked(alarm.repeatsOn(bits[i]));

        Runnable refreshDateVisibility = () -> {
            boolean repeating = false;
            for (android.widget.ToggleButton day : days) repeating |= day.isChecked();
            int visibility = repeating ? View.GONE : View.VISIBLE;
            tvDateCaption.setVisibility(visibility);
            tvDate.setVisibility(visibility);
        };
        for (android.widget.ToggleButton day : days) {
            day.setOnCheckedChangeListener((button, checked) -> refreshDateVisibility.run());
        }
        refreshDateVisibility.run();

        // Time picker
        final int[] curHour = {alarm.hour};
        final int[] curMin = {alarm.minute};
        tvTime.setOnClickListener(v -> {
            com.google.android.material.timepicker.MaterialTimePicker picker =
                new com.google.android.material.timepicker.MaterialTimePicker.Builder()
                        .setTimeFormat(use24h
                                ? com.google.android.material.timepicker.TimeFormat.CLOCK_24H
                                : com.google.android.material.timepicker.TimeFormat.CLOCK_12H)
                        .setHour(curHour[0])
                        .setMinute(curMin[0])
                        .build();
            picker.addOnPositiveButtonClickListener(btn -> {
                curHour[0] = picker.getHour();
                curMin[0] = picker.getMinute();
                tvTime.setText(com.example.chronox.common.DateUtils.formatAlarmTime(curHour[0], curMin[0], use24h));
            });
            picker.show(getChildFragmentManager(), "time_picker");
            // MaterialTimePicker is a DialogFragment; finish its transaction and scale its
            // text before the next frame is drawn so its text does not pop in later.
            getChildFragmentManager().executePendingTransactions();
            DialogStyler.enlargeText(picker.getDialog());
        });

        // Calendar date picker for one-time alarms. Past dates are intentionally unavailable.
        tvDate.setOnClickListener(v -> {
            Calendar currentSelection = Calendar.getInstance();
            currentSelection.setTimeInMillis(selectedDateMillis[0]);
            DatePickerDialog picker = new DatePickerDialog(requireContext(), (dateView, year, month, day) -> {
                Calendar picked = Calendar.getInstance();
                picked.clear();
                picked.set(year, month, day, 0, 0, 0);
                selectedDateMillis[0] = picked.getTimeInMillis();
                tvDate.setText(formatAlarmDate(selectedDateMillis[0]));
            }, currentSelection.get(Calendar.YEAR), currentSelection.get(Calendar.MONTH),
                    currentSelection.get(Calendar.DAY_OF_MONTH));
            Calendar minimum = Calendar.getInstance();
            setStartOfDay(minimum);
            picker.getDatePicker().setMinDate(minimum.getTimeInMillis());
            DialogStyler.prepareWindow(picker, requireContext());
            picker.setOnShowListener(ignored -> DialogStyler.enlargeText(picker));
            picker.show();
        });

        AlertDialog editDialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                requireContext(), R.style.ThemeOverlay_ChronoX_AlertDialog)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        editDialog.setOnShowListener(ignored -> {
            DialogStyler.enlargeText(editDialog);
            editDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            alarm.hour = curHour[0];
            alarm.minute = curMin[0];
            alarm.label = etLabel.getText() != null ? etLabel.getText().toString() : "";
            alarm.soundEnabled = swSound.isChecked();
            alarm.vibrationEnabled = swVib.isChecked();

            int newDays = 0;
            for (int i = 0; i < 7; i++) if (days[i].isChecked()) newDays |= bits[i];
            alarm.repeatDays = newDays;
            alarm.isEnabled = true;

            if (newDays == 0) {
                Calendar trigger = Calendar.getInstance();
                trigger.setTimeInMillis(selectedDateMillis[0]);
                trigger.set(Calendar.HOUR_OF_DAY, curHour[0]);
                trigger.set(Calendar.MINUTE, curMin[0]);
                trigger.set(Calendar.SECOND, 0);
                trigger.set(Calendar.MILLISECOND, 0);
                if (trigger.getTimeInMillis() <= System.currentTimeMillis()) {
                    Toast.makeText(requireContext(),
                            "Choose a future time or select a later date.", Toast.LENGTH_LONG).show();
                    return;
                }
                alarm.scheduledDateMillis = selectedDateMillis[0];
            } else {
                // Recurring alarms ignore the one-time date; their weekday rules determine the date.
                alarm.scheduledDateMillis = 0L;
            }

            editDialog.dismiss();
            executor.execute(() -> {
                AlarmDao dao = AppDatabase.getInstance(appContext).alarmDao();
                if (existingAlarm == null) alarm.id = (int) dao.insert(alarm);
                else dao.update(alarm);

                AlarmScheduler.scheduleAlarm(appContext, alarm);
                long triggerAt = AlarmScheduler.computeNextTriggerTime(alarm);
                long remaining = Math.max(0L, triggerAt - System.currentTimeMillis());
                String remainingText = formatRemainingTime(remaining);
                String alarmTime = com.example.chronox.common.DateUtils.formatAlarmTime(alarm.hour, alarm.minute, use24h);
                String message = "Alarm set for " + alarmTime + " — in " + remainingText;
                android.app.Activity activity = getActivity();
                if (activity != null) activity.runOnUiThread(() ->
                        Toast.makeText(appContext, message, Toast.LENGTH_LONG).show());
            });
            });
        });
        DialogStyler.prepareWindow(editDialog, requireContext());
        editDialog.show();
    }

    private static void setStartOfDay(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private String formatAlarmDate(long dateMillis) {
        return new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(new Date(dateMillis));
    }

    /** Format a duration such as 2h 15m 8s for the alarm confirmation toast. */
    private String formatRemainingTime(long millis) {
        long totalSeconds = millis / 1000L;
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    private void showDeleteDialog(Alarm alarm) {
        String time = com.example.chronox.common.DateUtils.formatAlarmTime(
                alarm.hour, alarm.minute,
                prefs.getBoolean(Constants.PREF_CLOCK_FORMAT_24H, false));

        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                requireContext(), R.style.ThemeOverlay_ChronoX_AlertDialog)
                .setTitle(time)
                .setMessage(R.string.alarm_delete_confirm)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    final android.content.Context appContext = requireContext().getApplicationContext();
                    executor.execute(() -> {
                        AlarmScheduler.cancelAlarm(appContext, alarm);
                        AppDatabase.getInstance(appContext).alarmDao().delete(alarm);
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        DialogStyler.show(dialog, requireContext());
    }
}
