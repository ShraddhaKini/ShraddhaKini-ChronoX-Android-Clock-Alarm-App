package com.example.chronox.stopwatch;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chronox.R;
import com.example.chronox.common.TimeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * LapAdapter — RecyclerView adapter for the stopwatch lap list.
 * New laps are inserted at the top (newest first).
 */
public class LapAdapter extends RecyclerView.Adapter<LapAdapter.ViewHolder> {

    private final List<Lap> laps = new ArrayList<>();

    /** Insert a new lap at the top of the list. */
    public void addLap(Lap lap) {
        laps.add(0, lap);
        notifyItemInserted(0);
    }

    /** Remove all laps (on reset). */
    public void clearLaps() {
        int count = laps.size();
        laps.clear();
        notifyItemRangeRemoved(0, count);
    }

    /** Restore a saved snapshot in its existing newest-first order. */
    public void restoreLaps(List<Lap> savedLaps) {
        laps.clear();
        if (savedLaps != null) laps.addAll(savedLaps);
        notifyDataSetChanged();
    }

    /** Returns a defensive copy for lightweight session persistence. */
    public List<Lap> getLapsSnapshot() {
        return new ArrayList<>(laps);
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_lap, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Lap lap = laps.get(position);
        holder.tvLapNumber.setText(
                holder.itemView.getContext().getString(R.string.stopwatch_lap_number, lap.lapNumber));
        holder.tvLapTime.setText(TimeUtils.formatMSSc(lap.lapTimeMs));
        holder.tvTotalTime.setText(TimeUtils.formatMSSc(lap.totalTimeMs));
    }

    @Override
    public int getItemCount() { return laps.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvLapNumber, tvLapTime, tvTotalTime;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLapNumber = itemView.findViewById(R.id.tv_lap_number);
            tvLapTime   = itemView.findViewById(R.id.tv_lap_time);
            tvTotalTime = itemView.findViewById(R.id.tv_total_time);
        }
    }
}
