package com.example.chronox.worldclock;

import android.content.Context;
import androidx.lifecycle.LiveData;
import com.example.chronox.data.AppDatabase;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WorldClockRepository — mediates between WorldClockFragment and the Room database.
 * All write operations run on a single background thread to avoid race conditions.
 */
public class WorldClockRepository {
    private final WorldClockDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final LiveData<List<WorldClock>> allCities;

    public WorldClockRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        dao = db.worldClockDao();
        allCities = dao.getAllCities();
    }

    /** Observe all cities — updates automatically when DB changes. */
    public LiveData<List<WorldClock>> getAllCities() { return allCities; }

    /** Insert a city on background thread. Returns false if zoneId already exists. */
    public void insert(WorldClock city, InsertCallback callback) {
        executor.execute(() -> {
            boolean isDuplicate = dao.countByZoneId(city.zoneId) > 0;
            if (!isDuplicate) {
                int maxOrder = dao.getMaxSortOrder();
                city.sortOrder = maxOrder + 1;
                dao.insert(city);
            }
            if (callback != null) callback.onResult(!isDuplicate);
        });
    }

    /** Delete a city on the background thread and invoke the callback after the write completes. */
    public void delete(WorldClock city, Runnable onDeleted) {
        executor.execute(() -> {
            dao.delete(city);
            if (onDeleted != null) onDeleted.run();
        });
    }

    public interface InsertCallback {
        void onResult(boolean wasInserted);
    }
}
