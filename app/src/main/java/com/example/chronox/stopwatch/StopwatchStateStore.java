package com.example.chronox.stopwatch;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Keeps stopwatch state available while pages/views are recreated in the same app process.
 * A new process gets a new session ID, intentionally resetting the stopwatch after force-stop
 * or process death while leaving the countdown's separate durable state untouched.
 */
public final class StopwatchStateStore {
    private static final String PREFS_NAME = "chronox_stopwatch_session";
    private static final String KEY_SESSION = "process_session";
    private static final String KEY_STATE = "state";
    private static final String KEY_START = "start_timestamp";
    private static final String KEY_ACCUMULATOR = "paused_accumulator";
    private static final String KEY_LAP_COUNT = "lap_count";
    private static final String KEY_LAST_LAP_TOTAL = "last_lap_total";
    private static final String KEY_LAPS = "laps_json";
    private static final String PROCESS_SESSION_ID = UUID.randomUUID().toString();

    private StopwatchStateStore() {}

    public static final class Snapshot {
        public final String state;
        public final long startTimestamp;
        public final long pausedAccumulator;
        public final int lapCount;
        public final long lastLapTotalMs;
        public final List<Lap> laps;

        Snapshot(String state, long startTimestamp, long pausedAccumulator,
                 int lapCount, long lastLapTotalMs, List<Lap> laps) {
            this.state = state;
            this.startTimestamp = startTimestamp;
            this.pausedAccumulator = pausedAccumulator;
            this.lapCount = lapCount;
            this.lastLapTotalMs = lastLapTotalMs;
            this.laps = laps;
        }
    }

    public static boolean hasCurrentProcessSession(Context context) {
        return PROCESS_SESSION_ID.equals(prefs(context).getString(KEY_SESSION, null));
    }

    public static Snapshot load(Context context) {
        SharedPreferences prefs = prefs(context);
        if (!PROCESS_SESSION_ID.equals(prefs.getString(KEY_SESSION, null))) {
            return null;
        }
        List<Lap> laps = decodeLaps(prefs.getString(KEY_LAPS, "[]"));
        return new Snapshot(
                prefs.getString(KEY_STATE, "IDLE"),
                prefs.getLong(KEY_START, 0L),
                prefs.getLong(KEY_ACCUMULATOR, 0L),
                prefs.getInt(KEY_LAP_COUNT, 0),
                prefs.getLong(KEY_LAST_LAP_TOTAL, 0L),
                laps);
    }

    public static void save(Context context, String state, long startTimestamp,
                            long pausedAccumulator, int lapCount, long lastLapTotalMs,
                            List<Lap> laps) {
        JSONArray json = new JSONArray();
        if (laps != null) {
            for (Lap lap : laps) {
                JSONObject item = new JSONObject();
                try {
                    item.put("number", lap.lapNumber);
                    item.put("lapMs", lap.lapTimeMs);
                    item.put("totalMs", lap.totalTimeMs);
                    json.put(item);
                } catch (Exception ignored) {
                    // One malformed entry should not prevent the stopwatch from being saved.
                }
            }
        }
        prefs(context).edit()
                .putString(KEY_SESSION, PROCESS_SESSION_ID)
                .putString(KEY_STATE, state)
                .putLong(KEY_START, startTimestamp)
                .putLong(KEY_ACCUMULATOR, pausedAccumulator)
                .putInt(KEY_LAP_COUNT, lapCount)
                .putLong(KEY_LAST_LAP_TOTAL, lastLapTotalMs)
                .putString(KEY_LAPS, json.toString())
                .commit();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static List<Lap> decodeLaps(String encoded) {
        List<Lap> laps = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(encoded);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                laps.add(new Lap(item.getInt("number"), item.getLong("lapMs"), item.getLong("totalMs")));
            }
        } catch (Exception ignored) {
            // Corrupt lap data is non-fatal; the stopwatch state still restores.
        }
        return laps;
    }
}
