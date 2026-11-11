package com.reiko.weather.weather;

import javax.annotation.Nullable;

/**
 * When the insects come out over the water. Mayflies hatch on calm, dry evenings in late spring and early summer:
 * they rise from the river in their thousands to dance and mate, and the fish rise to them. Midges swarm over water and
 * damp ground on still, warm evenings and mornings all summer long.
 */
public final class InsectHatches {
    /** Whether the mayflies are hatching (late spring and early summer evenings, calm and dry). */
    public static boolean mayflies(@Nullable Season season, int dayOfSeason, int daysPerSeason, long dayTime, float wind, boolean raining) {
        if (raining || wind > 0.35F) return false;
        long t = Math.floorMod(dayTime, 24000L);
        if (t < 11000L || t > 13600L) return false;
        if (season == Season.SPRING) return daysPerSeason <= 0 || dayOfSeason >= daysPerSeason / 2;
        if (season == Season.SUMMER) return daysPerSeason <= 0 || dayOfSeason < daysPerSeason / 2;
        return false;
    }

    /** Whether the midges are out (still, warm evenings and early mornings from late spring to early autumn). */
    public static boolean midges(@Nullable Season season, long dayTime, float wind, boolean raining, float celsius) {
        if (raining || wind > 0.3F || celsius < 11.0F || season == Season.WINTER) return false;
        long t = Math.floorMod(dayTime, 24000L);
        return t > 10800L && t < 13800L || t > 23000L || t < 1500L;
    }

    private InsectHatches() {}
}
