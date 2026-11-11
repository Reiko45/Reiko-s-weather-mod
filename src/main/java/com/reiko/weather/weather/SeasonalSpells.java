package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.network.SyncedWeather;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Spells of out-of-season weather that come round some years.
 * <p>
 * An Indian summer: in mid-autumn, after the first frosts, a run of warm, still, hazy days, as if summer had come back
 * for a last look round. A false spring: late in winter, a warm spell wakes everything up early (the snow melts, buds
 * swell, crops start growing), and then a hard frost comes back and kills off the tender new growth.
 * <p>
 * Which years have them, and when, is worked out from the day count alone, so the server and every client agree
 * without anything being sent.
 */
public final class SeasonalSpells {
    /** How strong an Indian summer is right now, 0 to 1. */
    public static float indianSummer(Level level) {
        if (!enabled(WeatherConfig.INDIAN_SUMMER)) return 0.0F;
        Calendar c = calendar(level);
        if (c == null || c.season != Season.AUTUMN) return 0.0F;
        // not every year: about two years in three
        if (hash(c.year, 11) > 0.65F) return 0.0F;
        float start = c.per * (0.4F + 0.25F * hash(c.year, 12)), length = Math.max(2.0F, c.per * 0.2F);
        return window(c.t, start, length);
    }

    /** How warm the false spring's warm spell is right now, 0 to 1. */
    public static float falseSpringWarmth(Level level) {
        float[] f = falseSpring(level);
        return f[0];
    }

    /** How hard the frost after a false spring is biting right now, 0 to 1. */
    public static float falseSpringFrost(Level level) {
        float[] f = falseSpring(level);
        return f[1];
    }

    private static float[] falseSpring(Level level) {
        if (!enabled(WeatherConfig.FALSE_SPRING)) return new float[2];
        Calendar c = calendar(level);
        if (c == null || c.season != Season.WINTER || hash(c.year, 21) > 0.5F) return new float[2];
        float start = c.per * (0.55F + 0.15F * hash(c.year, 22)), warm = Math.max(2.0F, c.per * 0.13F), frost = Math.max(1.5F, c.per * 0.09F);
        return new float[]{window(c.t, start, warm), window(c.t, start + warm, frost)};
    }

    /** Degrees (°C) the spells put on (or take off) the usual temperature. */
    public static float temperatureOffset(Level level) {
        float[] f = falseSpring(level);
        return 7.0F * indianSummer(level) + 9.0F * f[0] - 9.0F * f[1];
    }

    /** How much likelier (or less likely) a weather is during a spell: an Indian summer is settled and dry. */
    public static float weatherMultiplier(Level level, WeatherType type) {
        float indian = indianSummer(level), still = Math.max(indian, Math.max(falseSpringWarmth(level), falseSpringFrost(level)) * 0.6F);
        if (still <= 0.0F) return 1.0F;
        return switch (type) {
            case CLEAR -> 1.0F + 2.0F * still;
            case FOG -> 1.0F + 0.5F * indian; // misty mornings
            case DRIZZLE, RAIN, HEAVY_RAIN, THUNDERSTORM, SUPERCELL, WINDY, SNOW, BLIZZARD -> 1.0F - 0.75F * still;
            default -> 1.0F;
        };
    }

    // ------------------------------------------------------------------ the calendar

    private record Calendar(Season season, long year, int per, float t) {}

    private static Calendar calendar(Level level) {
        if (level == null || level.dimension() != Level.OVERWORLD) return null;
        Season season;
        int day, per;
        if (level.isClientSide) {
            season = SyncedWeather.season();
            day = SyncedWeather.dayOfSeason();
            per = SyncedWeather.daysPerSeason();
        } else {
            Seasons.Info info = WeatherManager.seasonInfo();
            season = info.season();
            day = info.dayOfSeason();
            per = info.daysPerSeason();
        }
        if (season == null || per <= 0 || day < 0) return null;
        long days = level.getDayTime() / 24000L;
        long year = Math.floorDiv(days, per * 4L);
        float t = day + Math.floorMod(level.getDayTime(), 24000L) / 24000.0F;
        return new Calendar(season, year, per, t);
    }

    /** 0 to 1 inside [start, start + length], easing in and out over half a day at each end. */
    private static float window(float t, float start, float length) {
        return Mth.clamp(Math.min(t - start, start + length - t) / 0.5F, 0.0F, 1.0F);
    }

    private static float hash(long year, int salt) {
        int h = Mth.murmurHash3Mixer((int) year * 0x2545F491 + salt * 0x9E3779B9);
        return (h >>> 8) / (float) (1 << 24);
    }

    private static boolean enabled(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue flag) {
        return WeatherConfig.SPEC.isLoaded() && flag.get();
    }

    private SeasonalSpells() {}
}
