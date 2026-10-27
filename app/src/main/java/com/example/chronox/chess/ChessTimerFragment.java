package com.example.chronox.chess;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.text.InputFilter;
import android.text.InputType;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.chronox.R;
import com.example.chronox.common.TimeUtils;
import com.example.chronox.common.DialogStyler;
import com.example.chronox.data.AppDatabase;
import com.example.chronox.databinding.FragmentChessTimerBinding;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChessTimerFragment extends Fragment {

    private FragmentChessTimerBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ToneGenerator clockTapTone;

    private enum GameState { IDLE, P1_ACTIVE, P2_ACTIVE, PAUSED, GAME_OVER }
    private GameState state = GameState.IDLE;
    private GameState stateBeforePause = GameState.P1_ACTIVE;

    private long p1RemainingMs;
    private long p2RemainingMs;
    private long activePlayerStartMs;
    
    private long gameStartTimestampMs;

    // Last-used time control. Static so it survives tab switches / fragment
    // recreation for as long as the app process lives; a force-stop or full
    // app close kills the process, which resets it to the 10 minute default.
    private static long sessionStartTimeMs = 10 * 60 * 1000L;
    private static long sessionIncrementMs = 0L;

    // Time control of the game currently on the board (saved with it).
    private long gameTimeControlMs = sessionStartTimeMs;
    private long gameIncrementMs = sessionIncrementMs;
    private int timeoutLoser = 0;

    private long startTimeMs = sessionStartTimeMs;
    private long incrementMs = sessionIncrementMs;

    // True only when the player typed a real name (otherwise the box shows
    // the "Enter your name" hint until the game starts).
    private boolean p1NameCustom = false;
    private boolean p2NameCustom = false;
    
    private int p1Moves = 0;
    private int p2Moves = 0;
    private StringBuilder p1History = new StringBuilder();
    private StringBuilder p2History = new StringBuilder();

    private static final long TICK_MS = 100L;
    private static final String[] PRESET_LABELS = {"1m","3m","5m","10m","15m","30m"};
    private static final int[] PRESET_MINUTES = {1, 3, 5, 10, 15, 30};

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (binding == null) return;
            if (state != GameState.P1_ACTIVE && state != GameState.P2_ACTIVE) return;

            long elapsed = System.currentTimeMillis() - activePlayerStartMs;

            if (state == GameState.P1_ACTIVE) {
                long remaining = p1RemainingMs - elapsed;
                if (remaining <= 0) { onTimeout(1); return; }
                binding.tvPlayer1Time.setText(TimeUtils.formatChessTime(remaining));
                updateWarningColor(binding.tvPlayer1Time, remaining);
            } else {
                long remaining = p2RemainingMs - elapsed;
                if (remaining <= 0) { onTimeout(2); return; }
                binding.tvPlayer2Time.setText(TimeUtils.formatChessTime(remaining));
                updateWarningColor(binding.tvPlayer2Time, remaining);
            }

            handler.postDelayed(this, TICK_MS);
        }
    };

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentChessTimerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            // A short, soft clock-only tone. Control buttons intentionally do not use it.
            clockTapTone = new ToneGenerator(AudioManager.STREAM_MUSIC, 55);
        } catch (RuntimeException ignored) {
            clockTapTone = null;
        }
        resetGame();
        restoreSavedGame();

        binding.panelPlayer1.setOnClickListener(v -> onPlayerTapped(1));
        binding.panelPlayer2.setOnClickListener(v -> onPlayerTapped(2));
        // Before the game starts, names are editable. Once active, tapping a
        // name is treated exactly like tapping that player's clock panel.
        binding.tvPlayer1Name.setOnClickListener(v -> {
            if (state == GameState.IDLE) showPlayerNameDialog(1);
            else onPlayerTapped(1);
        });
        binding.tvPlayer2Name.setOnClickListener(v -> {
            if (state == GameState.IDLE) showPlayerNameDialog(2);
            else onPlayerTapped(2);
        });
        binding.btnChessPlayPause.setOnClickListener(v -> togglePause());
        binding.btnChessEnd.setOnClickListener(v -> {
            if (state != GameState.IDLE) showEndGameOptions();
        });
        // Settings: the time-control popup is now opened only from this button.
        binding.btnChessSettings.setOnClickListener(v -> {
            if (state == GameState.IDLE) showSetupDialog();
        });
        
        binding.btnChessRecord.setOnClickListener(v -> {
            new ChessHistoryDialog().show(getChildFragmentManager(), "history");
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateBottomNavVisibility();
        if (state == GameState.P1_ACTIVE || state == GameState.P2_ACTIVE) {
            activePlayerStartMs = System.currentTimeMillis();
            startTicker();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (state == GameState.P1_ACTIVE || state == GameState.P2_ACTIVE) {
            long elapsed = System.currentTimeMillis() - activePlayerStartMs;
            if (state == GameState.P1_ACTIVE) p1RemainingMs = Math.max(0, p1RemainingMs - elapsed);
            else p2RemainingMs = Math.max(0, p2RemainingMs - elapsed);
        }
        handler.removeCallbacks(ticker);
        saveGameState();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(ticker);
        // Never leave the nav bar hidden when this screen goes away.
        if (getActivity() instanceof com.example.chronox.MainActivity) {
            ((com.example.chronox.MainActivity) getActivity()).setBottomNavVisible(true);
        }
        if (clockTapTone != null) {
            clockTapTone.release();
            clockTapTone = null;
        }
        binding = null;
    }

    private void showSetupDialog() {
        handler.removeCallbacks(ticker);

        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_chess_setup, null);
        ChipGroup cg = view.findViewById(R.id.cg_time_presets);
        View customLayout = view.findViewById(R.id.ll_custom_time);
        EditText etMin = view.findViewById(R.id.et_custom_minutes);
        EditText etInc = view.findViewById(R.id.et_custom_increment);

        cg.setSingleSelection(true);
        // Pre-select whatever the last game used (10m on a fresh app start).
        int defaultIdx = -1;
        if (incrementMs == 0L) {
            for (int i = 0; i < PRESET_MINUTES.length; i++) {
                if (startTimeMs == PRESET_MINUTES[i] * 60000L) { defaultIdx = i; break; }
            }
        }
        for (int i = 0; i < PRESET_LABELS.length; i++) {
            Chip chip = new Chip(requireContext());
            chip.setText(PRESET_LABELS[i]);
            chip.setCheckable(true);
            chip.setId(i + 1);
            cg.addView(chip);
        }
        Chip customChip = new Chip(requireContext());
        customChip.setText(R.string.chess_custom);
        customChip.setCheckable(true);
        customChip.setId(PRESET_LABELS.length + 1);
        cg.addView(customChip);
        
        if (defaultIdx >= 0) {
            cg.check(defaultIdx + 1);
        } else {
            cg.check(PRESET_LABELS.length + 1);
            customLayout.setVisibility(View.VISIBLE);
            etMin.setText(String.valueOf(startTimeMs / 60000L));
            etInc.setText(String.valueOf(incrementMs / 1000L));
        }

        cg.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == PRESET_LABELS.length + 1) {
                customLayout.setVisibility(View.VISIBLE);
            } else {
                customLayout.setVisibility(View.GONE);
            }
        });

        AlertDialog setupDialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_ChronoX_AlertDialog)
                .setView(view)
                .setPositiveButton("Apply", (d, w) -> {
                    int checkedId = cg.getCheckedChipId();
                    if (checkedId == PRESET_LABELS.length + 1) {
                        try {
                            String mStr = etMin.getText().toString().trim();
                            String incStr = etInc.getText().toString().trim();
                            if (mStr.isEmpty()) throw new NumberFormatException();
                            long m = Long.parseLong(mStr);
                            long inc = incStr.isEmpty() ? 0L : Long.parseLong(incStr);
                            
                            if (m <= 0) throw new NumberFormatException();
                            startTimeMs = m * 60000L;
                            incrementMs = inc * 1000L;
                        } catch (Exception e) {
                            Toast.makeText(requireContext(), "Invalid time", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } else {
                        int idx = checkedId - 1;
                        if (idx >= 0 && idx < PRESET_MINUTES.length) {
                            startTimeMs = PRESET_MINUTES[idx] * 60000L;
                            incrementMs = 0L;
                        }
                    }
                    // Remember for the next games until the app process ends.
                    sessionStartTimeMs = startTimeMs;
                    sessionIncrementMs = incrementMs;
                    resetGame();
                })
                .setNegativeButton(R.string.cancel, null)
                .setCancelable(false)
                .create();

        // The setup dialog is intentionally modal. If the user taps outside it,
        // give a gentle visual nudge rather than dismissing the dialog.
        setupDialog.setCanceledOnTouchOutside(false);
        setupDialog.setOnShowListener(dialog -> {
            DialogStyler.enlargeText(setupDialog);
            installOutsideTouchHint(setupDialog, view);
        });
        // Configure blur/dim before show() so the first visible frame is already softened.
        styleChessDialog(setupDialog);
        setupDialog.show();
    }

    private void installOutsideTouchHint(AlertDialog dialog, View content) {
        if (dialog.getWindow() == null) return;
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
        dialog.getWindow().getDecorView().setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                hintSetupDialog(content);
                return true;
            }
            return false;
        });
    }

    private void hintSetupDialog(View content) {
        content.animate().cancel();
        content.setScaleX(1f);
        content.setScaleY(1f);
        content.setAlpha(1f);
        content.animate()
                .scaleX(1.012f)
                .scaleY(1.012f)
                .alpha(0.96f)
                .setDuration(90)
                .withEndAction(() -> content.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(130)
                        .start())
                .start();
    }

    private void showEndGameOptions() {
        boolean wasRunning = (state == GameState.P1_ACTIVE || state == GameState.P2_ACTIVE);
        if (wasRunning) togglePause();

        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_ChronoX_AlertDialog)
                .setTitle("End Game")
                .setItems(new CharSequence[]{"Save Result", "Restart"}, (d, which) -> {
                    if (which == 0) {
                        showResultDialog();
                    } else {
                        // Same time settings as the game just played.
                        resetGame();
                    }
                })
                .setNegativeButton("Cancel", (d, w) -> {
                    if (wasRunning) togglePause();
                })
                .create();
        dialog.setOnShowListener(ignored -> DialogStyler.enlargeText(dialog));
        styleChessDialog(dialog);
        dialog.show();
    }
    
    private void showResultDialog() {
        String p1Name = getPlayerName(1);
        String p2Name = getPlayerName(2);
        CharSequence[] resultOptions = {p1Name + " Wins", p2Name + " Wins", "Draw"};

        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_ChronoX_AlertDialog)
            .setTitle("Record Final Result")
            .setItems(resultOptions, (d, which) -> {
                String result = which == 0 ? "P1" : (which == 1 ? "P2" : "DRAW");
                // Once the database confirms the save, reset and show the same
                // setup dialog used when Chess is first opened.
                saveGameRecord(result, () -> {
                    if (binding != null && isAdded()) {
                        // Next game keeps the settings of the one just saved.
                        resetGame();
                    }
                });
            })
            .setNegativeButton("Cancel", (d, w) -> {
                if (state == GameState.GAME_OVER && timeoutLoser != 0) showTimeoutDialog(timeoutLoser);
            })
            .create();
        dialog.setOnShowListener(ignored -> DialogStyler.enlargeText(dialog));
        styleChessDialog(dialog);
        dialog.show();
    }

    private String getPlayerName(int player) {
        if (binding == null) {
            return getString(player == 1 ? R.string.chess_player1_default : R.string.chess_player2_default);
        }
        String name = (player == 1 ? binding.tvPlayer1Name : binding.tvPlayer2Name)
                .getText().toString().trim();
        if (name.isEmpty()) {
            name = getString(player == 1 ? R.string.chess_player1_default : R.string.chess_player2_default);
        }
        return name.length() > 25 ? name.substring(0, 25) : name;
    }

    private int resolveThemeColor(int attrRes) {
        TypedValue value = new TypedValue();
        requireContext().getTheme().resolveAttribute(attrRes, value, true);
        return value.data;
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    /** Apply blur and sizing before a dialog first appears. */
    private void styleChessDialog(AlertDialog dialog) {
        DialogStyler.prepareWindow(dialog, requireContext());
    }

    private void playClockTapSound() {
        if (clockTapTone != null) {
            clockTapTone.startTone(ToneGenerator.TONE_PROP_BEEP, 65);
        }
    }

    private void showPlayerNameDialog(int player) {
        if (binding == null || state != GameState.IDLE) return;

        TextView nameView = player == 1 ? binding.tvPlayer1Name : binding.tvPlayer2Name;
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(25)});
        boolean hasCustom = player == 1 ? p1NameCustom : p2NameCustom;
        if (hasCustom) {
            input.setText(nameView.getText().toString().trim());
            input.selectAll();
        } else {
            input.setText("");
        }
        // No placeholder text inside the popup.
        input.setTextColor(resolveThemeColor(R.attr.colorAppTextPrimary));
        input.setBackgroundResource(R.drawable.bg_chess_editable_name);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setContentDescription("Player " + player + " name, maximum 25 characters");

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setPadding(dp(8), dp(4), dp(8), 0);
        container.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                requireContext(), R.style.ThemeOverlay_ChronoX_AlertDialog)
                .setTitle(player == 1 ? "Player 1 Name" : "Player 2 Name")
                .setView(container)
                .setPositiveButton("Save", (d, which) -> {
                    String enteredName = input.getText().toString().trim();
                    if (enteredName.length() > 25) enteredName = enteredName.substring(0, 25);
                    // Empty -> box goes back to "Enter your name"; default is applied at game start.
                    nameView.setText(enteredName);
                    if (player == 1) p1NameCustom = !enteredName.isEmpty();
                    else p2NameCustom = !enteredName.isEmpty();
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            DialogStyler.enlargeText(dialog);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            input.requestFocus();
            input.selectAll();
            input.post(() -> {
                InputMethodManager imm = (InputMethodManager) requireContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                input.selectAll();
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            });
        });
        styleChessDialog(dialog);
        dialog.show();
    }

    private void saveGameRecord(String result, Runnable afterSaved) {
        ChessGame game = new ChessGame();
        game.startTimeMs = gameStartTimestampMs;
        game.endTimeMs = System.currentTimeMillis();
        game.p1Moves = p1Moves;
        game.p2Moves = p2Moves;
        game.timeControlMs = gameTimeControlMs;
        game.incrementMs = gameIncrementMs;
        game.p1Name = getPlayerName(1);
        game.p2Name = getPlayerName(2);
        // Preserve the editable player names in the saved result and move history.
        if ("P1".equals(result)) {
            game.result = game.p1Name + " Wins";
        } else if ("P2".equals(result)) {
            game.result = game.p2Name + " Wins";
        } else {
            game.result = "Draw";
        }

        game.historyJson = null; // move history is no longer shown or stored

        Context appContext = requireContext().getApplicationContext();
        executor.execute(() -> {
            try {
                AppDatabase.getInstance(appContext).chessDao().insert(game);
                handler.post(() -> {
                    Toast.makeText(appContext, "Game recorded", Toast.LENGTH_SHORT).show();
                    if (afterSaved != null) afterSaved.run();
                });
            } catch (Exception exception) {
                handler.post(() -> Toast.makeText(appContext,
                        "Could not save game result", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void setPlayerNameEditingEnabled(boolean enabled) {
        if (binding == null) return;

        // Keep the labels enabled/clickable so taps are consumed by the name
        // labels even after game start; otherwise they could bubble to the
        // player panel and accidentally count as a chess move. The dialog
        // itself only opens while state == IDLE.
        binding.tvPlayer1Name.setEnabled(true);
        binding.tvPlayer2Name.setEnabled(true);
        binding.tvPlayer1Name.setClickable(true);
        binding.tvPlayer2Name.setClickable(true);

        // Make the editable state obvious before the game starts.
        binding.tvPlayer1Name.setBackgroundResource(
                enabled ? R.drawable.bg_chess_editable_name : android.R.color.transparent);
        binding.tvPlayer2Name.setBackgroundResource(
                enabled ? R.drawable.bg_chess_editable_name : android.R.color.transparent);
        binding.tvPlayer1Name.setTextColor(resolveThemeColor(R.attr.colorAppTextPrimary));
        binding.tvPlayer2Name.setTextColor(resolveThemeColor(R.attr.colorAppTextPrimary));
        binding.tvPlayer1Name.setAlpha(1f);
        binding.tvPlayer2Name.setAlpha(1f);
    }

    private void resetGame() {
        state = GameState.IDLE;
        gameTimeControlMs = startTimeMs;
        gameIncrementMs = incrementMs;
        p1RemainingMs = startTimeMs;
        p2RemainingMs = startTimeMs;
        p1Moves = 0;
        p2Moves = 0;
        p1History.setLength(0);
        p2History.setLength(0);
        // Names the player never typed go back to the "Enter your name" hint.
        if (!p1NameCustom) binding.tvPlayer1Name.setText("");
        if (!p2NameCustom) binding.tvPlayer2Name.setText("");
        
        binding.tvPlayer1Time.setText(TimeUtils.formatChessTime(p1RemainingMs));
        binding.tvPlayer2Time.setText(TimeUtils.formatChessTime(p2RemainingMs));
        
        updateWarningColor(binding.tvPlayer1Time, p1RemainingMs);
        updateWarningColor(binding.tvPlayer2Time, p2RemainingMs);
        
        binding.tvPlayer1MovesCount.setText("Moves: 0");
        binding.tvPlayer2MovesCount.setText("Moves: 0");

        setPlayerNameEditingEnabled(true);
        updateUIForState();
    }

    /** Players who typed nothing become "Player 1" / "Player 2" when the game starts. */
    private void applyDefaultNamesIfEmpty() {
        if (binding == null) return;
        if (binding.tvPlayer1Name.getText().toString().trim().isEmpty()) {
            binding.tvPlayer1Name.setText(R.string.chess_player1_default);
        }
        if (binding.tvPlayer2Name.getText().toString().trim().isEmpty()) {
            binding.tvPlayer2Name.setText(R.string.chess_player2_default);
        }
    }

    private void onPlayerTapped(int player) {
        if (state == GameState.GAME_OVER || state == GameState.PAUSED) return;

        if (state == GameState.IDLE) {
            playClockTapSound();
            applyDefaultNamesIfEmpty();
            gameStartTimestampMs = System.currentTimeMillis();
            state = (player == 1) ? GameState.P2_ACTIVE : GameState.P1_ACTIVE;
            activePlayerStartMs = System.currentTimeMillis();
            startTicker();
            updateUIForState();
            return;
        }

        if ((state == GameState.P1_ACTIVE && player == 1) ||
            (state == GameState.P2_ACTIVE && player == 2)) {
            playClockTapSound();
            
            long now = System.currentTimeMillis();
            long elapsed = now - activePlayerStartMs;
            
            SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss a", Locale.getDefault());
            String timeStr = sdf.format(new Date(now));

            if (player == 1) {
                p1RemainingMs = Math.max(0, p1RemainingMs - elapsed) + gameIncrementMs;
                p1Moves++;
                binding.tvPlayer1MovesCount.setText("Moves: " + p1Moves);
                p1History.append("Move ").append(p1Moves).append(" - ").append(timeStr).append(" (").append(TimeUtils.formatChessTime(p1RemainingMs)).append(" left)\n");
                
                
                state = GameState.P2_ACTIVE;
            } else {
                p2RemainingMs = Math.max(0, p2RemainingMs - elapsed) + gameIncrementMs;
                p2Moves++;
                binding.tvPlayer2MovesCount.setText("Moves: " + p2Moves);
                p2History.append("Move ").append(p2Moves).append(" - ").append(timeStr).append(" (").append(TimeUtils.formatChessTime(p2RemainingMs)).append(" left)\n");
                
                
                state = GameState.P1_ACTIVE;
            }

            activePlayerStartMs = now;
            updateUIForState();
        }
    }

    private void togglePause() {
        if (state == GameState.GAME_OVER) return;

        if (state == GameState.IDLE) {
            applyDefaultNamesIfEmpty();
            gameStartTimestampMs = System.currentTimeMillis();
            state = GameState.P1_ACTIVE;
            activePlayerStartMs = System.currentTimeMillis();
            startTicker();
        } else if (state == GameState.PAUSED) {
            state = stateBeforePause;
            activePlayerStartMs = System.currentTimeMillis();
            startTicker();
        } else {
            long elapsed = System.currentTimeMillis() - activePlayerStartMs;
            if (state == GameState.P1_ACTIVE) {
                p1RemainingMs = Math.max(0, p1RemainingMs - elapsed);
            } else {
                p2RemainingMs = Math.max(0, p2RemainingMs - elapsed);
            }
            stateBeforePause = state;
            state = GameState.PAUSED;
            handler.removeCallbacks(ticker);
        }
        updateUIForState();
    }

    private void onTimeout(int losingPlayer) {
        state = GameState.GAME_OVER;
        handler.removeCallbacks(ticker);

        if (losingPlayer == 1) {
            p1RemainingMs = 0;
            binding.tvPlayer1Time.setText("00:00");
        } else {
            p2RemainingMs = 0;
            binding.tvPlayer2Time.setText("00:00");
        }
        updateUIForState();

        timeoutLoser = losingPlayer;
        showTimeoutDialog(losingPlayer);
    }

    private void showTimeoutDialog(int losingPlayer) {
        if (binding == null || !isAdded()) return;
        String p1Name = getPlayerName(1);
        String p2Name = getPlayerName(2);

        String loser = losingPlayer == 1 ? p1Name : p2Name;
        String winner = losingPlayer == 1 ? p2Name : p1Name;
        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_ChronoX_AlertDialog)
                .setTitle(winner + " Wins!")
                .setMessage(loser + " ran out of time.\nTotal Moves: " + (p1Moves + p2Moves))
                .setPositiveButton("Record Result", (d, w) -> showResultDialog())
                .setNegativeButton("Restart", (d, w) -> resetGame())
                .setCancelable(false)
                .create();
        dialog.setOnShowListener(ignored -> DialogStyler.enlargeText(dialog));
        styleChessDialog(dialog);
        dialog.show();
    }

    // ---- Persistence: keeps a game across tab switches and app close ----
    private static final String K = "chess_";

    private void saveGameState() {
        if (binding == null) return;
        android.content.SharedPreferences.Editor e = requireContext()
                .getSharedPreferences(com.example.chronox.common.Constants.PREFS_NAME, Context.MODE_PRIVATE).edit();
        boolean inGame = state != GameState.IDLE;
        e.putBoolean(K + "inGame", inGame);
        e.putString(K + "p1Name", p1NameCustom ? binding.tvPlayer1Name.getText().toString() : "");
        e.putString(K + "p2Name", p2NameCustom ? binding.tvPlayer2Name.getText().toString() : "");
        if (inGame) {
            boolean running = state == GameState.P1_ACTIVE || state == GameState.P2_ACTIVE;
            e.putString(K + "state", state.name());
            e.putString(K + "before", (state == GameState.PAUSED ? stateBeforePause : state).name());
            e.putLong(K + "p1Ms", p1RemainingMs);
            e.putLong(K + "p2Ms", p2RemainingMs);
            e.putInt(K + "p1Moves", p1Moves);
            e.putInt(K + "p2Moves", p2Moves);
            e.putLong(K + "startTs", gameStartTimestampMs);
            e.putLong(K + "tc", gameTimeControlMs);
            e.putLong(K + "inc", gameIncrementMs);
            e.putInt(K + "loser", timeoutLoser);
            e.putString(K + "p1Hist", p1History.toString());
            e.putString(K + "p2Hist", p2History.toString());
        }
        e.apply();
    }

    private void restoreSavedGame() {
        if (binding == null) return;
        android.content.SharedPreferences sp = requireContext()
                .getSharedPreferences(com.example.chronox.common.Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String n1 = sp.getString(K + "p1Name", "");
        String n2 = sp.getString(K + "p2Name", "");
        p1NameCustom = !n1.isEmpty();
        p2NameCustom = !n2.isEmpty();
        if (!sp.getBoolean(K + "inGame", false)) {
            binding.tvPlayer1Name.setText(n1);
            binding.tvPlayer2Name.setText(n2);
            return;
        }
        // A game was in progress: bring it back exactly where it stopped.
        p1RemainingMs = sp.getLong(K + "p1Ms", startTimeMs);
        p2RemainingMs = sp.getLong(K + "p2Ms", startTimeMs);
        p1Moves = sp.getInt(K + "p1Moves", 0);
        p2Moves = sp.getInt(K + "p2Moves", 0);
        gameStartTimestampMs = sp.getLong(K + "startTs", System.currentTimeMillis());
        gameTimeControlMs = sp.getLong(K + "tc", startTimeMs);
        gameIncrementMs = sp.getLong(K + "inc", 0L);
        timeoutLoser = sp.getInt(K + "loser", 0);
        p1History.setLength(0);
        p1History.append(sp.getString(K + "p1Hist", ""));
        p2History.setLength(0);
        p2History.append(sp.getString(K + "p2Hist", ""));
        binding.tvPlayer1Name.setText(n1.isEmpty() ? getString(R.string.chess_player1_default) : n1);
        binding.tvPlayer2Name.setText(n2.isEmpty() ? getString(R.string.chess_player2_default) : n2);
        binding.tvPlayer1MovesCount.setText("Moves: " + p1Moves);
        binding.tvPlayer2MovesCount.setText("Moves: " + p2Moves);

        String saved = sp.getString(K + "state", "PAUSED");
        if ("GAME_OVER".equals(saved)) {
            state = GameState.GAME_OVER;
            updateUIForState();
            if (timeoutLoser != 0) handler.post(() -> showTimeoutDialog(timeoutLoser));
        } else {
            // Clocks were running or paused: come back paused so nobody loses time.
            try { stateBeforePause = GameState.valueOf(sp.getString(K + "before", "P1_ACTIVE")); }
            catch (IllegalArgumentException ex) { stateBeforePause = GameState.P1_ACTIVE; }
            if (stateBeforePause != GameState.P1_ACTIVE && stateBeforePause != GameState.P2_ACTIVE) {
                stateBeforePause = GameState.P1_ACTIVE;
            }
            state = GameState.PAUSED;
            updateUIForState();
        }
    }

    private void startTicker() {
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private void updateUIForState() {
        binding.tvPlayer1Time.setText(TimeUtils.formatChessTime(p1RemainingMs));
        binding.tvPlayer2Time.setText(TimeUtils.formatChessTime(p2RemainingMs));
        updateWarningColor(binding.tvPlayer1Time, p1RemainingMs);
        updateWarningColor(binding.tvPlayer2Time, p2RemainingMs);

        switch (state) {
            case IDLE:
                setPlayerNameEditingEnabled(true);
                binding.tvPlayer1Status.setText("TAP TO START");
                binding.tvPlayer2Status.setText("TAP TO START");
                binding.tvPlayer1Status.setVisibility(View.VISIBLE);
                binding.tvPlayer2Status.setVisibility(View.VISIBLE);
                binding.panelPlayer1.setBackgroundResource(R.drawable.bg_chess_panel_inactive);
                binding.panelPlayer2.setBackgroundResource(R.drawable.bg_chess_panel_inactive);
                binding.panelPlayer1.setAlpha(1.0f);
                binding.panelPlayer2.setAlpha(1.0f);
                binding.panelPlayer1.setElevation(0f);
                binding.panelPlayer2.setElevation(0f);
                binding.btnChessPlayPause.setEnabled(true);
                binding.btnChessEnd.setEnabled(false);
                binding.btnChessSettings.setEnabled(true);
                binding.btnChessPlayPause.setIconResource(R.drawable.ic_play);
                binding.btnChessPlayPause.setText(""); // clear text
                binding.btnChessPlayPause.setContentDescription("Start chess timer");
                break;
            case P1_ACTIVE:
                setPlayerNameEditingEnabled(false);
                binding.tvPlayer1Status.setText(R.string.chess_your_turn);
                binding.tvPlayer2Status.setText(R.string.chess_waiting);
                binding.tvPlayer1Status.setVisibility(View.VISIBLE);
                binding.tvPlayer2Status.setVisibility(View.INVISIBLE);
                binding.panelPlayer1.setBackgroundResource(R.drawable.bg_chess_panel_active_p1);
                binding.panelPlayer2.setBackgroundResource(R.drawable.bg_chess_panel_inactive);
                binding.panelPlayer1.setAlpha(1.0f);
                binding.panelPlayer1.setElevation(12f);
                binding.panelPlayer2.setAlpha(0.6f);
                binding.panelPlayer2.setElevation(0f);
                binding.btnChessPlayPause.setEnabled(true);
                binding.btnChessEnd.setEnabled(true);
                binding.btnChessSettings.setEnabled(false);
                binding.btnChessPlayPause.setIconResource(R.drawable.ic_pause);
                binding.btnChessPlayPause.setText(""); // clear text
                binding.btnChessPlayPause.setContentDescription("Pause chess timer");
                break;
            case P2_ACTIVE:
                setPlayerNameEditingEnabled(false);
                binding.tvPlayer1Status.setText(R.string.chess_waiting);
                binding.tvPlayer2Status.setText(R.string.chess_your_turn);
                binding.tvPlayer1Status.setVisibility(View.INVISIBLE);
                binding.tvPlayer2Status.setVisibility(View.VISIBLE);
                binding.panelPlayer1.setBackgroundResource(R.drawable.bg_chess_panel_inactive);
                binding.panelPlayer2.setBackgroundResource(R.drawable.bg_chess_panel_active_p2);
                binding.panelPlayer1.setAlpha(0.6f);
                binding.panelPlayer1.setElevation(0f);
                binding.panelPlayer2.setAlpha(1.0f);
                binding.panelPlayer2.setElevation(12f);
                binding.btnChessPlayPause.setEnabled(true);
                binding.btnChessEnd.setEnabled(true);
                binding.btnChessSettings.setEnabled(false);
                binding.btnChessPlayPause.setIconResource(R.drawable.ic_pause);
                binding.btnChessPlayPause.setText(""); // clear text
                binding.btnChessPlayPause.setContentDescription("Pause chess timer");
                break;
            case PAUSED:
                setPlayerNameEditingEnabled(false);
                binding.tvPlayer1Status.setText(R.string.chess_pause);
                binding.tvPlayer2Status.setText(R.string.chess_pause);
                binding.tvPlayer1Status.setVisibility(View.VISIBLE);
                binding.tvPlayer2Status.setVisibility(View.VISIBLE);
                binding.panelPlayer1.setBackgroundResource(R.drawable.bg_chess_panel_inactive);
                binding.panelPlayer2.setBackgroundResource(R.drawable.bg_chess_panel_inactive);
                binding.panelPlayer1.setAlpha(0.8f);
                binding.panelPlayer2.setAlpha(0.8f);
                binding.panelPlayer1.setElevation(0f);
                binding.panelPlayer2.setElevation(0f);
                binding.btnChessPlayPause.setEnabled(true);
                binding.btnChessEnd.setEnabled(true);
                binding.btnChessSettings.setEnabled(false);
                binding.btnChessPlayPause.setIconResource(R.drawable.ic_play);
                binding.btnChessPlayPause.setText(""); // clear text
                binding.btnChessPlayPause.setContentDescription("Resume chess timer");
                break;
            case GAME_OVER:
                setPlayerNameEditingEnabled(false);
                binding.tvPlayer1Status.setText("GAME OVER");
                binding.tvPlayer2Status.setText("GAME OVER");
                binding.tvPlayer1Status.setVisibility(View.VISIBLE);
                binding.tvPlayer2Status.setVisibility(View.VISIBLE);
                binding.panelPlayer1.setBackgroundResource(R.drawable.bg_chess_panel_inactive);
                binding.panelPlayer2.setBackgroundResource(R.drawable.bg_chess_panel_inactive);
                binding.panelPlayer1.setAlpha(0.8f);
                binding.panelPlayer2.setAlpha(0.8f);
                binding.panelPlayer1.setElevation(0f);
                binding.panelPlayer2.setElevation(0f);
                binding.btnChessPlayPause.setEnabled(false);
                binding.btnChessEnd.setEnabled(false);
                binding.btnChessSettings.setEnabled(false);
                break;
        }
        applyChessPanelTextColors();
        // Full-screen clock while a game is running; nav bar is back when
        // idle, paused, or finished.
        updateBottomNavVisibility();
    }

    private void updateBottomNavVisibility() {
        if (!isAdded()) return;
        boolean running = state == GameState.P1_ACTIVE || state == GameState.P2_ACTIVE;
        if (requireActivity() instanceof com.example.chronox.MainActivity) {
            ((com.example.chronox.MainActivity) requireActivity()).setBottomNavVisible(!running);
        }
    }

    private void applyChessPanelTextColors() {
        if (binding == null) return;
        int activePlayer = state == GameState.P1_ACTIVE ? 1
                : state == GameState.P2_ACTIVE ? 2 : 0;
        int activeText = resolveThemeColor(R.attr.colorAppBackground);
        int regularText = resolveThemeColor(R.attr.colorAppTextPrimary);
        int secondaryText = resolveThemeColor(R.attr.colorAppTextSecondary);

        binding.tvPlayer1Name.setTextColor(activePlayer == 1 ? activeText : regularText);
        binding.tvPlayer2Name.setTextColor(activePlayer == 2 ? activeText : regularText);
        binding.tvPlayer1MovesCount.setTextColor(activePlayer == 1 ? activeText : secondaryText);
        binding.tvPlayer2MovesCount.setTextColor(activePlayer == 2 ? activeText : secondaryText);
        binding.tvPlayer1Status.setTextColor(activePlayer == 1 ? activeText : secondaryText);
        binding.tvPlayer2Status.setTextColor(activePlayer == 2 ? activeText : secondaryText);
    }

    private void updateWarningColor(TextView tv, long remainingMs) {
        boolean isActiveClock = (tv == binding.tvPlayer1Time && state == GameState.P1_ACTIVE)
                || (tv == binding.tvPlayer2Time && state == GameState.P2_ACTIVE);
        if (remainingMs <= 10000 && remainingMs > 0) {
            tv.setTextColor(getColorFromAttr(R.attr.colorTimeoutWarning));
        } else if (isActiveClock) {
            // The active panel is filled with a theme-specific player color,
            // so use the theme's inverse/background color for readable digits.
            tv.setTextColor(getColorFromAttr(R.attr.colorAppBackground));
        } else {
            tv.setTextColor(tv == binding.tvPlayer1Time ? 
                getColorFromAttr(R.attr.colorPlayer1) : getColorFromAttr(R.attr.colorPlayer2));
        }
    }

    private int getColorFromAttr(int attrId) {
        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(attrId, typedValue, true);
        return typedValue.data;
    }
}




