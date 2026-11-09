package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

/**
 * Good years and bad years. The climate swings slowly between wet and dry spells that last a year or more (as the
 * real one does with El Niño and La Niña): a wet year brings more rain, storms and hurricanes and fewer droughts,
 * a dry year the other way round. Which way each year goes is worked out from the world seed, so it's the same
 * for everyone and needs nothing saved, and each year leans toward the one before.
 */
public final class ClimateYears {
    public enum Phase { WET, NEUTRAL, DRY }

    /** The current year's leaning, -1 (dry) to 1 (wet). */
    public static float leaning(ServerLevel level) {
        if (!WeatherConfig.SPEC.isLoaded() || !WeatherConfig.CLIMATE_SWINGS.get()) return 0.0F;
        long year = year(level);
        long seed = level.getSeed();
        // each year leans partly the way the last one did, so wet or dry spells run on
        return Mth.clamp(0.6F * raw(seed, year) + 0.4F * raw(seed, year - 1) + 0.15F * raw(seed, year - 2), -1.0F, 1.0F);
    }

    public static Phase phase(ServerLevel level) {
        float l = leaning(level);
        return l > 0.3F ? Phase.WET : l < -0.3F ? Phase.DRY : Phase.NEUTRAL;
    }

    private static float raw(long seed, long year) {
        int h = Mth.murmurHash3Mixer((int) (seed ^ seed >>> 32) + (int) year * 0x6C8E9CF5);
        return (h >>> 8) / (float) (1 << 23) - 1.0F;
    }

    /** Which year it is: four seasons' worth of days (or 96 days when seasons are off). */
    public static long year(ServerLevel level) {
        int perSeason = WeatherManager.seasonInfo().daysPerSeason();
        long days = level.getDayTime() / 24000L;
        return Math.floorDiv(days, Math.max(1, perSeason > 0 ? perSeason * 4L : 96L));
    }

    /** How much more (or less) likely a weather is this year. */
    public static float weatherMultiplier(@Nullable ServerLevel level, WeatherType type) {
        if (level == null) return 1.0F;
        float l = leaning(level);
        if (l == 0.0F) return 1.0F;
        float wet = Math.max(0.0F, l), dry = Math.max(0.0F, -l);
        return switch (type) {
            case DRIZZLE, RAIN, HEAVY_RAIN, SNOW, GRAUPEL -> 1.0F + 0.5F * wet - 0.4F * dry;
            case THUNDERSTORM, SUPERCELL, THUNDERSNOW, HAIL -> 1.0F + 0.35F * wet - 0.25F * dry;
            case HURRICANE -> 1.0F + 0.6F * wet - 0.5F * dry;
            case CLEAR -> 1.0F - 0.25F * wet + 0.35F * dry;
            case HEATWAVE, SANDSTORM -> 1.0F - 0.4F * wet + 0.7F * dry;
            default -> 1.0F;
        };
    }

    /** How much likelier (or less likely) droughts are this year. */
    public static float droughtMultiplier(ServerLevel level) {
        float l = leaning(level);
        return l >= 0.0F ? 1.0F - 0.6F * l : 1.0F - 1.5F * l;
    }

    private ClimateYears() {}
}
