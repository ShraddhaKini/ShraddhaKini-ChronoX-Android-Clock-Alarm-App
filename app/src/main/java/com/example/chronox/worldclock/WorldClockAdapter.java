package com.example.chronox.worldclock;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chronox.R;
import com.example.chronox.common.DateUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * WorldClockAdapter — RecyclerView adapter for the world clock city list.
 * Uses ListAdapter + DiffUtil for efficient, animated updates.
 * Long-press triggers the delete callback.
 */
public class WorldClockAdapter extends ListAdapter<WorldClock, WorldClockAdapter.ViewHolder> {

    public interface OnDeleteListener {
        void onDelete(WorldClock city);
    }

    private OnDeleteListener deleteListener;

    public WorldClockAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnDeleteListener(OnDeleteListener listener) {
        this.deleteListener = listener;
    }

    private static final DiffUtil.ItemCallback<WorldClock> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<WorldClock>() {
                @Override
                public boolean areItemsTheSame(@NonNull WorldClock a, @NonNull WorldClock b) {
                    return a.id == b.id;
                }
                @Override
                public boolean areContentsTheSame(@NonNull WorldClock a, @NonNull WorldClock b) {
                    return a.cityName.equals(b.cityName) && a.zoneId.equals(b.zoneId);
                }
            };

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_world_clock, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WorldClock city = getItem(position);
        holder.bind(city);
        holder.itemView.setOnLongClickListener(v -> {
            if (deleteListener != null) deleteListener.onDelete(city);
            return true;
        });
    }

    /** Refresh all time displays — called every 60s by the Fragment. */
    public void refreshTimes() {
        notifyItemRangeChanged(0, getItemCount(), "time_update");
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCityName, tvCityDate, tvTimeDiff, tvCityTime;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCityName = itemView.findViewById(R.id.tv_city_name);
            tvCityDate = itemView.findViewById(R.id.tv_city_date);
            tvTimeDiff = itemView.findViewById(R.id.tv_time_diff);
            tvCityTime = itemView.findViewById(R.id.tv_city_time);
        }

        void bind(WorldClock city) {
            tvCityName.setText(city.cityName);
            try {
                ZoneId cityZone = ZoneId.of(city.zoneId);
                ZonedDateTime cityNow = ZonedDateTime.now(cityZone);
                ZoneId localZone = ZoneId.systemDefault();
                tvCityTime.setText(DateUtils.formatWorldClockTime(cityNow));
                tvCityDate.setText(DateUtils.formatWorldClockDate(cityNow));
                tvTimeDiff.setText(DateUtils.formatTimeDifference(localZone, cityZone));
            } catch (Exception e) {
                tvCityTime.setText("--:--");
                tvCityDate.setText("");
                tvTimeDiff.setText("");
            }
        }
    }
}
