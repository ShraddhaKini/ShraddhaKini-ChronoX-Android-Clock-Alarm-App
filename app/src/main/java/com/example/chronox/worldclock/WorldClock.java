package com.example.chronox.worldclock;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * WorldClock — Room entity for a saved World Clock city.
 *
 * Each entry stores a city name and its IANA timezone ID.
 * The timezone ID is used with ZoneId to get the current time — no
 * manual UTC offsets needed. Java handles DST automatically.
 *
 * Example:
 *   cityName = "Tokyo"
 *   zoneId   = "Asia/Tokyo"
 */
@Entity(tableName = "world_clock")
public class WorldClock {

    /** Auto-generated primary key */
    @PrimaryKey(autoGenerate = true)
    public int id;

    /** Display name for the city (e.g. "New York") */
    public String cityName;

    /**
     * IANA timezone ID (e.g. "America/New_York").
     * Used with ZoneId.of() — never store a raw UTC offset,
     * as that would break when DST changes.
     */
    public String zoneId;

    /** Display order in the list (lower = shown first) */
    public int sortOrder;

    // Room requires a no-arg constructor
    public WorldClock() {}

    @androidx.room.Ignore
    public WorldClock(String cityName, String zoneId, int sortOrder) {
        this.cityName = cityName;
        this.zoneId = zoneId;
        this.sortOrder = sortOrder;
    }
}
