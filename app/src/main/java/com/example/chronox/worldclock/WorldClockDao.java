package com.example.chronox.worldclock;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * WorldClockDao — Room Data Access Object for world clock cities.
 *
 * LiveData<List<WorldClock>> is returned for the main list query so the
 * RecyclerView automatically updates whenever the database changes —
 * no manual refresh needed.
 */
@Dao
public interface WorldClockDao {

    /** Get all saved cities ordered by their sort position. */
    @Query("SELECT * FROM world_clock ORDER BY sortOrder ASC")
    LiveData<List<WorldClock>> getAllCities();

    /** One-shot query (no LiveData) — used for checking duplicates on background thread. */
    @Query("SELECT * FROM world_clock ORDER BY sortOrder ASC")
    List<WorldClock> getAllCitiesSync();

    /** Check if a city with this zoneId already exists (duplicate prevention). */
    @Query("SELECT COUNT(*) FROM world_clock WHERE zoneId = :zoneId")
    int countByZoneId(String zoneId);

    /** Get the current max sort order (for appending new cities at end). */
    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM world_clock")
    int getMaxSortOrder();

    /** Insert a new city. Ignores conflicts (shouldn't happen with unique IDs). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(WorldClock city);

    /** Delete a specific city. */
    @Delete
    void delete(WorldClock city);

    /** Delete all cities (used during testing / reset). */
    @Query("DELETE FROM world_clock")
    void deleteAll();
}
