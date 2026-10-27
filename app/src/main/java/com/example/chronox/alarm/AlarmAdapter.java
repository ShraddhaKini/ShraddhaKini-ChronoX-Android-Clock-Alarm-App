package com.example.chronox.alarm;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chronox.R;
import com.example.chronox.common.Constants;
import com.example.chronox.common.DateUtils;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.List;

/**
 * AlarmAdapter — RecyclerView adapter for the alarm list.
 * Uses ListAdapter with DiffUtil for efficient, animated updates.
 */
public class AlarmAdapter extends ListAdapter<Alarm, AlarmAdapter.ViewHolder> {

    public interface OnAlarmToggleListener {
        void onToggle(Alarm alarm, boolean isEnabled);
    }

    public interface OnAlarmClickListener {
        void onClick(Alarm alarm);
    }

    public interface OnAlarmDeleteListener {
        void onDelete(Alarm alarm);
    }

    private OnAlarmToggleListener toggleListener;
    private OnAlarmClickListener  clickListener;
    private OnAlarmDeleteListener deleteListener;
    private boolean use24h = false;

    public AlarmAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnToggleListener(OnAlarmToggleListener l)  { this.toggleListener = l; }
    public void setOnClickListener(OnAlarmClickListener l)    { this.clickListener  = l; }
    public void setOnDeleteListener(OnAlarmDeleteListener l)  { this.deleteListener = l; }
    public void setUse24h(boolean use24h)                     { this.use24h = use24h; notifyDataSetChanged(); }

    private static final DiffUtil.ItemCallback<Alarm> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Alarm>() {
                @Override
                public boolean areItemsTheSame(@NonNull Alarm a, @NonNull Alarm b) {
                    return a.id == b.id;
                }
                @Override
                public boolean areContentsTheSame(@NonNull Alarm a, @NonNull Alarm b) {
                    return a.hour == b.hour && a.minute == b.minute
                            && a.isEnabled == b.isEnabled && a.repeatDays == b.repeatDays
                            && a.scheduledDateMillis == b.scheduledDateMillis
                            && java.util.Objects.equals(a.label, b.label);
                }
            };

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_alarm, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Alarm alarm = getItem(position);
        holder.bind(alarm, use24h);

        // Toggle switch — use listener to avoid switch triggering during bind
        holder.switchEnabled.setOnCheckedChangeListener(null);
        holder.switchEnabled.setChecked(alarm.isEnabled);
        holder.switchEnabled.setOnCheckedChangeListener((btn, checked) -> {
            if (toggleListener != null) toggleListener.onToggle(alarm, checked);
        });

        // Tap → edit alarm
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(alarm);
        });

        // Long press → delete alarm
        holder.itemView.setOnLongClickListener(v -> {
            if (deleteListener != null) deleteListener.onDelete(alarm);
            return true;
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAlarmTime, tvAlarmLabel, tvAlarmDays;
        SwitchMaterial switchEnabled;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAlarmTime    = itemView.findViewById(R.id.tv_alarm_time);
            tvAlarmLabel   = itemView.findViewById(R.id.tv_alarm_label);
            tvAlarmDays    = itemView.findViewById(R.id.tv_alarm_days);
            switchEnabled  = itemView.findViewById(R.id.switch_alarm_enabled);
        }

        void bind(Alarm alarm, boolean use24h) {
            tvAlarmTime.setText(DateUtils.formatAlarmTime(alarm.hour, alarm.minute, use24h));

            String daysText = buildDaySummary(alarm);
            if (daysText.isEmpty()) daysText = "Once";

            String subText;
            if (alarm.label != null && !alarm.label.trim().isEmpty()) {
                subText = daysText + " | " + alarm.label.trim();
            } else {
                subText = daysText + " | No label";
            }

            tvAlarmDays.setText(subText);
            tvAlarmDays.setVisibility(View.VISIBLE);
            tvAlarmLabel.setVisibility(View.GONE);

            // Dim the time text if disabled
            tvAlarmTime.setAlpha(alarm.isEnabled ? 1.0f : 0.4f);
        }

        private String buildDaySummary(Alarm alarm) {
            int repeatDays = alarm.repeatDays;
            if (repeatDays == 0) {
                if (alarm.scheduledDateMillis <= 0L) return "Once";
                String date = new java.text.SimpleDateFormat("EEE, d MMM", java.util.Locale.getDefault())
                        .format(new java.util.Date(alarm.scheduledDateMillis));
                return "Once • " + date;
            }
            if (repeatDays == Constants.ALL_DAYS) return "Every day";
            if (repeatDays == Constants.WEEKDAYS)  return "Weekdays";
            if (repeatDays == Constants.WEEKENDS)  return "Weekends";

            String[] dayNames = {"Mon","Tue","Wed","Thu","Fri","Sat","Sun"};
            int[] dayBits = {Constants.DAY_MON, Constants.DAY_TUE, Constants.DAY_WED,
                             Constants.DAY_THU, Constants.DAY_FRI, Constants.DAY_SAT, Constants.DAY_SUN};
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < dayBits.length; i++) {
                if ((repeatDays & dayBits[i]) != 0) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(dayNames[i]);
                }
            }
            return sb.toString();
        }
    }
}
