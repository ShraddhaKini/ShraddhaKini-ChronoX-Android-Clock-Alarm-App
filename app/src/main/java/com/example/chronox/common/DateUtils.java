package com.example.chronox.common;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * DateUtils — Date and timezone formatting for ChronoX.
 *
 * Uses java.time APIs (API 26+; minSdk=29 so no desugaring needed).
 * ZoneId handles DST and fractional-hour offsets automatically —
 * we never manually calculate UTC offsets here.
 *
 * DateTimeFormatter instances are thread-safe in java.time,
 * unlike the old SimpleDateFormat.
 */
public final class DateUtils {

    private DateUtils() {}

    private static final DateTimeFormatter DATE_SHORT =
            DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault());
    // Date without day abbrev — used on Home screen to avoid "Wednesday, Wed Aug 19"
    private static final DateTimeFormatter DATE_NO_DAY =
            DateTimeFormatter.ofPattern("MMM d", Locale.getDefault());

    private static final DateTimeFormatter TIME_12H =
            DateTimeFormatter.ofPattern("hh:mm", Locale.getDefault());
    private static final DateTimeFormatter TIME_24H =
            DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());
    private static final DateTimeFormatter AMPM =
            DateTimeFormatter.ofPattern("a", Locale.getDefault());
    private static final DateTimeFormatter SECONDS =
            DateTimeFormatter.ofPattern("ss", Locale.getDefault());
    private static final DateTimeFormatter WORLD_TIME =
            DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault());
    private static final DateTimeFormatter WORLD_DATE =
            DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault());
    private static final DateTimeFormatter ALARM_TIME_12H =
            DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault());
    private static final DateTimeFormatter ALARM_TIME_24H =
            DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());

    /** Format time in 12-hour format (hours:minutes). Example: "09:45" */
    public static String formatTime12H(ZonedDateTime dt) {
        return dt.format(TIME_12H);
    }

    /** Format time in 24-hour format. Example: "21:45" */
    public static String formatTime24H(ZonedDateTime dt) {
        return dt.format(TIME_24H);
    }

    /** Get AM/PM string. Example: "AM" or "PM" */
    public static String formatAmPm(ZonedDateTime dt) {
        return dt.format(AMPM);
    }

    /** Get current seconds as two-digit string. Example: "07" */
    public static String formatSeconds(ZonedDateTime dt) {
        return dt.format(SECONDS);
    }

    /** Format short date with day abbreviation. Example: "Mon, Aug 18" */
    public static String formatDateShort(ZonedDateTime dt) {
        return dt.format(DATE_SHORT);
    }

    /** Format date without day abbreviation. Example: "Aug 19" — used on Home screen. */
    public static String formatDateNoDay(ZonedDateTime dt) {
        return dt.format(DATE_NO_DAY);
    }


    /** Full day name. Example: "Monday" */
    public static String formatDayName(ZonedDateTime dt) {
        return dt.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault());
    }

    /** Short timezone abbreviation. Example: "IST", "UTC", "EST" */
    public static String formatTimezoneShort(ZonedDateTime dt) {
        return dt.getZone().getDisplayName(TextStyle.SHORT, Locale.getDefault());
    }

    /**
     * Format a city's time for World Clock display.
     * Example: "09:45 AM"
     */
    public static String formatWorldClockTime(ZonedDateTime cityTime) {
        return cityTime.format(WORLD_TIME);
    }

    /**
     * Format a city's date for World Clock display.
     * Example: "Mon, Aug 18"
     */
    public static String formatWorldClockDate(ZonedDateTime cityTime) {
        return cityTime.format(WORLD_DATE);
    }

    /**
     * Format an alarm time for display in the alarm list.
     * @param use24h Whether to use 24-hour format
     */
    public static String formatAlarmTime(int hour, int minute, boolean use24h) {
        // Create a ZonedDateTime just for formatting purposes
        ZonedDateTime dt = ZonedDateTime.now().withHour(hour).withMinute(minute).withSecond(0);
        return use24h ? dt.format(ALARM_TIME_24H) : dt.format(ALARM_TIME_12H);
    }

    /**
     * Calculate the human-readable time difference between local timezone and a city.
     * Uses ZoneId — correctly handles DST and fractional-hour offsets.
     *
     * @param localZoneId  Device local timezone
     * @param cityZoneId   City timezone
     * @return E.g. "+5:30", "-3:00", or "Same time"
     */
    public static String formatTimeDifference(ZoneId localZoneId, ZoneId cityZoneId) {
        ZonedDateTime now = ZonedDateTime.now(localZoneId);
        ZonedDateTime cityNow = ZonedDateTime.now(cityZoneId);

        int localOffsetSec = now.getOffset().getTotalSeconds();
        int cityOffsetSec  = cityNow.getOffset().getTotalSeconds();
        int diffSec = cityOffsetSec - localOffsetSec;

        if (diffSec == 0) return "Same time";

        int absDiff = Math.abs(diffSec);
        int hours   = absDiff / 3600;
        int minutes = (absDiff % 3600) / 60;
        String sign = diffSec > 0 ? "+" : "-";

        if (minutes == 0) {
            return String.format("%s%d hr", sign, hours);
        } else {
            return String.format("%s%d:%02d hr", sign, hours, minutes);
        }
    }

    /**
     * Safely parse a ZoneId string.
     * Returns null instead of crashing on invalid input.
     * Always call this before using any user-supplied timezone string.
     */
    public static ZoneId safeParseZoneId(String zoneIdString) {
        try {
            return ZoneId.of(zoneIdString);
        } catch (Exception e) {
            return null; // caller handles null gracefully
        }
    }
}
