package com.example.chronox.common;

/**
 * TimeUtils — Centralized time formatting utilities.
 *
 * All time formatting in ChronoX goes through here.
 * Never duplicate formatting logic in individual fragments.
 *
 * These methods are pure functions — no Android context needed.
 * Uses java.time APIs available from API 26+; minSdk=29 so no desugaring needed.
 */
public final class TimeUtils {

    private TimeUtils() {}

    /**
     * Format milliseconds as HH:MM:SS.
     * Example: 3723000 ms → "01:02:03"
     */
    public static String formatHMS(long millis) {
        if (millis < 0) millis = 0;
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * Format stopwatch time compactly, expanding only when needed:
     * MM:SS.cc normally, HH:MM:SS.cc after an hour, and Dd HH:MM:SS after a day.
     */
    public static String formatMSSc(long millis) {
        if (millis < 0) millis = 0;
        long totalCentiseconds = millis / 10;
        long totalSeconds = totalCentiseconds / 100;
        long days = totalSeconds / 86_400;
        long hours = (totalSeconds % 86_400) / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;
        long centis = totalCentiseconds % 100;

        if (days > 0) {
            return String.format("%dd %02d:%02d:%02d", days, hours, minutes, seconds);
        }
        if (totalSeconds >= 3_600) {
            long totalHours = totalSeconds / 3_600;
            return String.format("%02d:%02d:%02d.%02d", totalHours, minutes, seconds, centis);
        }
        return String.format("%02d:%02d.%02d", totalSeconds / 60, seconds, centis);
    }

    /**
     * Format milliseconds as MM:SS for compact display.
     * Example: 305000 ms → "05:05"
     */
    public static String formatMS(long millis) {
        if (millis < 0) millis = 0;
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * Format as HH:MM:SS or MM:SS depending on whether hours > 0.
     * Useful for countdown timer display — hides hours when not needed.
     */
    public static String formatSmart(long millis) {
        if (millis < 0) millis = 0;
        long totalSeconds = millis / 1000;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;
        if (totalSeconds >= 3_600) {
            // Show total hours rather than a separate day label.
            return String.format("%02d:%02d:%02d", totalSeconds / 3_600, minutes, seconds);
        }
        return String.format("%02d:%02d", totalSeconds / 60, seconds);
    }

    /**
     * Format for Chess Timer — always MM:SS.
     * Clamps to zero if negative (timeout safety).
     */
    public static String formatChessTime(long millis) {
        if (millis <= 0) return "00:00";
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes > 99) minutes = 99;
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * Convert hours/minutes/seconds components to total milliseconds.
     */
    public static long toMillis(int hours, int minutes, int seconds) {
        return ((long) hours * 3600 + (long) minutes * 60 + (long) seconds) * 1000L;
    }

    /** Convert day/hour/minute/second components to milliseconds (supports up to 1000 days). */
    public static long toMillis(int days, int hours, int minutes, int seconds) {
        return (((long) days * 86_400L) + ((long) hours * 3_600L)
                + ((long) minutes * 60L) + seconds) * 1000L;
    }

    /** Extract hours component from total milliseconds. */
    public static int hoursFromMillis(long millis) {
        return (int) ((millis / 1000) / 3600);
    }

    /** Extract minutes component (0–59) from total milliseconds. */
    public static int minutesFromMillis(long millis) {
        return (int) (((millis / 1000) % 3600) / 60);
    }

    /** Extract seconds component (0–59) from total milliseconds. */
    public static int secondsFromMillis(long millis) {
        return (int) ((millis / 1000) % 60);
    }
}
