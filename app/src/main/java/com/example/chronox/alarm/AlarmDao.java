package com.example.chronox.alarm;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * AlarmDao — Room DAO for alarm CRUD operations.
 *
 * LiveData<List<Alarm>> for the main list query so AlarmFragment's
 * RecyclerView auto-updates when alarms are added/deleted/toggled.
 */
@Dao
public interface AlarmDao {

    /** All alarms, ordered by time (hour, then minute). */
    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC")
    LiveData<List<Alarm>> getAllAlarms();

    /** Synchronous query — used in BootReceiver (runs on background thread). */
    @Query("SELECT * FROM alarms WHERE isEnabled = 1")
    List<Alarm> getEnabledAlarmsSync();

    /** Get a single alarm by ID — used when rescheduling after snooze. */
    @Query("SELECT * FROM alarms WHERE id = :alarmId LIMIT 1")
    Alarm getAlarmByIdSync(int alarmId);

    /**
     * Get the next upcoming alarm for display on the Home screen.
     * Returns the enabled alarm with the smallest time value from now.
     * Note: For full next-alarm logic (respecting day of week), the
     * repository layer does the calculation after fetching all enabled alarms.
     */
    @Query("SELECT * FROM alarms WHERE isEnabled = 1 ORDER BY hour ASC, minute ASC LIMIT 1")
    Alarm getNextAlarmSync();

    /** Insert a new alarm; returns the generated ID. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Alarm alarm);

    /** Update an existing alarm (after editing). */
    @Update
    void update(Alarm alarm);

    /** Delete an alarm (also need to cancel its AlarmManager entry). */
    @Delete
    void delete(Alarm alarm);

    /** Toggle alarm enabled state by ID — used by the list switch. */
    @Query("UPDATE alarms SET isEnabled = :isEnabled WHERE id = :alarmId")
    void setEnabled(int alarmId, boolean isEnabled);
}
