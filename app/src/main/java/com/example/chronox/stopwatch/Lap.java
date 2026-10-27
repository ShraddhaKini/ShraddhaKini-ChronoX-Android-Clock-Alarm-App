package com.example.chronox.stopwatch;

/** Lap — data class for a single stopwatch lap entry. */
public class Lap {
    public final int lapNumber;
    public final long lapTimeMs;   // Duration of this lap only
    public final long totalTimeMs; // Cumulative elapsed time at this lap

    public Lap(int lapNumber, long lapTimeMs, long totalTimeMs) {
        this.lapNumber  = lapNumber;
        this.lapTimeMs  = lapTimeMs;
        this.totalTimeMs = totalTimeMs;
    }
}
