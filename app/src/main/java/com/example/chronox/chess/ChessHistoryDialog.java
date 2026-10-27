package com.example.chronox.chess;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chronox.R;
import com.example.chronox.common.DialogStyler;
import com.example.chronox.data.AppDatabase;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChessHistoryDialog extends DialogFragment {

    private RecyclerView recyclerView;
    private HistoryAdapter adapter;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_chess_history, null);
        recyclerView = view.findViewById(R.id.rv_chess_history);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new HistoryAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        AppDatabase.getInstance(requireContext()).chessDao().getAllGames().observe(this, games -> {
            adapter.setGames(games);
        });

        Dialog dialog = new MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_ChronoX_AlertDialog)
                .setTitle("Chess Game History")
                .setView(view)
                .setPositiveButton("Close", null)
                .create();
        DialogStyler.prepareWindow(dialog, requireContext());
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        DialogStyler.enlargeText(getDialog());
    }

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private List<ChessGame> games;

        public HistoryAdapter(List<ChessGame> games) {
            this.games = games;
        }

        public void setGames(List<ChessGame> games) {
            this.games = games;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chess_history, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChessGame game = games.get(position);
            holder.tvTitle.setText(game.p1Name + " vs " + game.p2Name);
            holder.tvResult.setText(game.result != null ? game.result : "Unknown");
            holder.tvMoves.setText(game.p1Moves + " moves | " + game.p2Moves + " moves");
            
            SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            String start = sdf.format(new Date(game.startTimeMs));
            String end = sdf.format(new Date(game.endTimeMs));
            long durationMins = (game.endTimeMs - game.startTimeMs) / 60000;
            holder.tvTime.setText(start + " → " + end + " (" + durationMins + " min)");
            
            holder.tvControl.setText((game.timeControlMs/60000) + " min + " + (game.incrementMs/1000) + " sec");

            holder.itemView.setOnClickListener(v -> {
                androidx.appcompat.app.AlertDialog detailsDialog = new MaterialAlertDialogBuilder(
                        requireContext(), R.style.ThemeOverlay_ChronoX_AlertDialog)
                    .setTitle("Game Details")
                    .setMessage(
                        "START TIME: " + start + "\n" +
                        "END TIME: " + end + "\n" +
                        "GAME DURATION: " + durationMins + " min\n" +
                        "TIME CONTROL: " + holder.tvControl.getText()
                    )
                    .setPositiveButton("Close", null)
                    .create();
                DialogStyler.show(detailsDialog, requireContext());
            });
        }

        @Override
        public int getItemCount() {
            return games.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvResult, tvMoves, tvTime, tvControl;
            ViewHolder(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tv_history_title);
                tvResult = v.findViewById(R.id.tv_history_result);
                tvMoves = v.findViewById(R.id.tv_history_moves);
                tvTime = v.findViewById(R.id.tv_history_time);
                tvControl = v.findViewById(R.id.tv_history_control);
            }
        }
    }
}
