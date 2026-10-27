package com.example.chronox.alarm;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Alarm — Room entity for a saved alarm.
 *
 * DESIGN NOTES:
 *
 * repeatDays: Bitmask integer where each bit represents a day of the week.
 *   Bit 0 (1)  = Monday
 *   Bit 1 (2)  = Tuesday
 *   Bit 2 (4)  = Wednesday
 *   Bit 3 (8)  = Thursday
 *   Bit 4 (16) = Friday
 *   Bit 5 (32) = Saturday
 *   Bit 6 (64) = Sunday
 *   0 = non-repeating (one-time alarm)
 *
 * The alarm's stable int ID is used as both:
 *   - The Room primary key
 *   - The AlarmManager PendingIntent request code (unique per alarm)
 *   - The notification ID (so edit/cancel affects the right notification)
 */
@Entity(tableName = "alarms")
public class Alarm {

    /** Auto-generated stable ID — used as PendingIntent request code */
    @PrimaryKey(autoGenerate = true)
    public int id;

    /** Hour of alarm (0–23, always stored in 24h format) */
    public int hour;

    /** Minute of alarm (0–59) */
    public int minute;

    /** Local start-of-day epoch millis for a one-time alarm; 0 keeps legacy time-only behavior. */
    @ColumnInfo(defaultValue = "0")
    public long scheduledDateMillis;

    /** User-provided label; empty string if not set */
    public String label;

    /** Whether this alarm is currently enabled */
    public boolean isEnabled;

    /**
     * Which days this alarm repeats, encoded as a bitmask.
     * 0 = one-time alarm (fires once then auto-disables).
     * See Constants.DAY_MON through DAY_SUN for bit values.
     */
    public int repeatDays;

    /** Whether to play the alarm sound when it fires */
    public boolean soundEnabled;

    /** Whether to vibrate when the alarm fires */
    public boolean vibrationEnabled;

    // Room requires a no-arg constructor
    public Alarm() {}

    /**
     * Create a new alarm with default settings (sound on, vibration on, one-time).
     */
    public static Alarm createDefault(int hour, int minute) {
        Alarm alarm = new Alarm();
        alarm.hour = hour;
        alarm.minute = minute;
        alarm.label = "";
        alarm.isEnabled = true;
        alarm.repeatDays = 0; // one-time
        alarm.soundEnabled = true;
        alarm.vibrationEnabled = true;
        return alarm;
    }

    /**
     * Check if this alarm repeats on a given day.
     * @param dayBit One of Constants.DAY_MON .. Constants.DAY_SUN
     */
    public boolean repeatsOn(int dayBit) {
        return (repeatDays & dayBit) != 0;
    }

    /** Returns true if this is a one-time alarm (no repeat days set). */
    public boolean isOneTime() {
        return repeatDays == 0;
    }
}
