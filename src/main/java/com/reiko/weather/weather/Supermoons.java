package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import net.minecraft.world.level.Level;

/**
 * Supermoons. The moon's orbit is an ellipse, so it swings nearer the Earth and further away each month; when a full
 * moon falls near its closest approach, it looks noticeably bigger and brighter than usual, and its pull raises
 * higher spring tides (king tides). The two cycles drift in and out of step, so it's only now and then: here, every
 * fifth full moon, worked out from the day count alone so the server and every client agree.
 */
public final class Supermoons {
    /** How many full moons apart supermoons come round. */
    private static final int EVERY = 5;

    /** How super tonight's moon is, 0 to 1: 1 on a supermoon's night, a little either side, 0 otherwise. */
    public static float strength(Level level) {
        if (level == null || level.dimension() != Level.OVERWORLD) return 0.0F;
        return strength(level.getDayTime());
    }

    public static float strength(long dayTime) {
        // (the moon's phase goes by the day count, as in vanilla: phase 0, the full moon, every eighth day)
        long day = Math.floorDiv(dayTime, 24000L);
        if (Math.floorMod(day, 8L) != 0) return 0.0F;
        return Math.floorMod(Math.floorDiv(day, 8L), (long) EVERY) == 2 ? 1.0F : 0.0F;
    }

    /** Whether the higher supermoon tides are on (the setting, and a supermoon tonight). */
    public static boolean kingTide(Level level) {
        return WeatherConfig.SPEC.isLoaded() && WeatherConfig.SUPERMOONS.get() && strength(level) > 0.5F;
    }

    private Supermoons() {}
}
