package com.reiko.weather.weather;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;

import java.util.Locale;

/**
 * Every weather the overworld can experience.
 *
 * <p>{@code vanillaRain}/{@code vanillaThunder} describe how the weather is mirrored onto vanilla's own
 * weather flags, so vanilla systems (lightning, cauldrons filling, fishing, mob spawning, riptide...)
 * keep working.</p>
 */
public enum WeatherType {
    //           vanilla rain/thunder, default weight, min/max duration (ticks), climate affinity {cold, temperate, arid}
    CLEAR       (false, false, 30, 6000, 18000, 1.0F, 1.0F, 1.5F),
    OVERCAST    (false, false, 12, 3600, 9600, 1.0F, 1.0F, 0.5F),
    FOG         (false, false, 6, 2400, 6000, 0.7F, 1.0F, 0.3F),
    DRIZZLE     (true, false, 8, 3600, 8400, 0.3F, 1.0F, 0.2F),
    RAIN        (true, false, 12, 4800, 12000, 0.4F, 1.0F, 0.2F),
    HEAVY_RAIN  (true, false, 5, 3600, 7200, 0.2F, 1.0F, 0.1F),
    THUNDERSTORM(true, true, 6, 3600, 8400, 0.3F, 1.0F, 0.3F),
    SUPERCELL   (true, true, 2, 3600, 6000, 0.05F, 1.0F, 0.5F),
    HURRICANE   (true, true, 1, 3600, 6000, 0.1F, 1.0F, 0.2F),
    HAIL        (true, false, 3, 1800, 3600, 0.5F, 1.0F, 0.3F),
    SLEET       (true, false, 3, 2400, 6000, 1.0F, 0.6F, 0.0F),
    ICE_STORM   (true, false, 2, 2400, 6000, 0.8F, 0.6F, 0.0F),
    SNOW        (true, false, 6, 4800, 12000, 2.0F, 0.3F, 0.0F),
    BLIZZARD    (true, false, 3, 3600, 7200, 1.5F, 0.1F, 0.0F),
    WINDY       (false, false, 8, 3600, 8400, 1.0F, 1.0F, 1.0F),
    SANDSTORM   (false, false, 4, 2400, 6000, 0.0F, 0.1F, 3.0F),
    HEATWAVE    (false, false, 4, 6000, 12000, 0.0F, 0.6F, 2.0F),
    DERECHO     (true, true, 1, 2400, 4800, 0.2F, 1.0F, 0.3F),
    /** Only from volcanic eruptions (the ash cloud drifting downwind), never in the forecast. */
    VOLCANIC_ASH(false, false, 0, 2400, 4800, 1.0F, 1.0F, 1.0F),
    /** A cold front: a fast squall line, with cooler air and a wind shift behind it. Only ever a storm cell. */
    COLD_FRONT  (true, true, 2, 2400, 4800, 0.6F, 1.0F, 0.5F),
    /** A warm front: a broad band of cloud and drizzle, with milder air behind it. Only ever a storm cell. */
    WARM_FRONT  (true, false, 2, 3600, 6000, 0.6F, 1.0F, 0.3F),
    /** A fog bank rolling in off the sea on cool mornings. */
    SEA_FOG     (false, false, 0, 2400, 4800, 1.0F, 1.0F, 1.0F),
    /** Smoke drifting downwind from a big wildfire. */
    WILDFIRE_SMOKE(false, false, 0, 2400, 4800, 1.0F, 1.0F, 1.0F),
    /** Volcanic smog after an eruption: a yellowish haze with stinging acid drizzle. */
    VOLCANIC_SMOG(false, false, 0, 2400, 4800, 1.0F, 1.0F, 1.0F),
    /** A cold snap: bitterly cold, clear, still air pouring down from the north for a day or two. */
    COLD_SNAP   (false, false, 3, 12000, 30000, 1.5F, 0.8F, 0.1F),
    /** Desert dust carried far downwind of a sandstorm: a tawny haze, and reddish dust in the rain and on the snow. */
    DUST_HAZE   (false, false, 0, 2400, 4800, 1.0F, 1.0F, 1.0F),
    /** A haboob: a towering wall of dust driven out ahead of a desert thunderstorm by its downdraft. */
    HABOOB      (false, false, 0, 1200, 2400, 1.0F, 1.0F, 1.0F),
    /** An atmospheric river: a long, narrow plume of tropical moisture that pours rain onto a coast for days. */
    ATMOSPHERIC_RIVER(true, false, 0, 24000, 48000, 1.0F, 1.0F, 1.0F),
    /** A heat burst: a sudden blast of hot, dry, gusty wind at night as a dying thunderstorm collapses. */
    HEAT_BURST  (false, false, 0, 1200, 2400, 1.0F, 1.0F, 1.0F),
    /** A fire cloud (pyrocumulonimbus): a thundercloud built by a huge wildfire, throwing dry lightning downwind. */
    PYROCUMULUS (false, true, 0, 2400, 4800, 1.0F, 1.0F, 1.0F),
    /** Graupel: showers of soft, white snow pellets that bounce and roll, mostly in chilly spring and autumn weather. */
    GRAUPEL     (true, false, 3, 1800, 4200, 1.0F, 0.8F, 0.0F),
    /** Thundersnow: a snowstorm strong enough to throw lightning, the thunder muffled by the falling snow. */
    THUNDERSNOW (true, true, 1, 2400, 4800, 1.2F, 0.3F, 0.0F),
    /** Freezing drizzle: fine drizzle falling through freezing air, glazing everything it lands on. */
    FREEZING_DRIZZLE(true, false, 2, 3600, 7200, 0.8F, 0.6F, 0.0F),
    /** A sun shower: a brief, light shower falling out of a mostly clear sky while the sun shines, often with a rainbow. */
    SUN_SHOWER  (true, false, 3, 1200, 3000, 0.4F, 1.0F, 0.5F),
    /**
     * A polar low: a small, fierce winter storm that spins up over cold seas when Arctic air pours out over open
     * water, with gales, blinding snow and sometimes a clear eye like a hurricane's. Only ever a storm cell.
     */
    POLAR_LOW   (true, false, 1, 2400, 4800, 1.5F, 0.3F, 0.0F);

    private static final WeatherType[] VALUES = values();

    public final boolean vanillaRain;
    public final boolean vanillaThunder;
    public final int defaultWeight;
    public final int minDuration;
    public final int maxDuration;
    private final float[] affinity;

    WeatherType(boolean vanillaRain, boolean vanillaThunder, int defaultWeight, int minDuration, int maxDuration, float cold, float temperate, float arid) {
        this.vanillaRain = vanillaRain;
        this.vanillaThunder = vanillaThunder;
        this.defaultWeight = defaultWeight;
        this.minDuration = minDuration;
        this.maxDuration = maxDuration;
        this.affinity = new float[]{cold, temperate, arid};
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public MutableComponent displayName() {
        return Component.translatable("weather.reikos_weather." + id());
    }

    /** How suitable this weather is for a climate; used to bias the forecast toward where players are. */
    public float affinity(Climate climate) {
        return affinity[climate.ordinal()];
    }

    /** Whether the weather cycle can pick this weather by itself (volcanic ash only comes from eruptions). */
    public boolean natural() {
        return this != VOLCANIC_ASH && this != SEA_FOG && this != WILDFIRE_SMOKE && this != VOLCANIC_SMOG && this != DUST_HAZE
                && this != HABOOB && this != ATMOSPHERIC_RIVER && this != HEAT_BURST && this != PYROCUMULUS;
    }

    /** Weathers that only ever come as a storm cell (a front is a line, not the whole world's weather). */
    public boolean cellOnly() {
        return !natural() || this == COLD_FRONT || this == WARM_FRONT || this == POLAR_LOW;
    }

    public boolean isFront() {
        return this == COLD_FRONT || this == WARM_FRONT;
    }

    /** Calm weathers are the "breaks" between more eventful weather. */
    public boolean isCalm() {
        return this == CLEAR || this == OVERCAST;
    }

    public boolean isSevere() {
        return this == SUPERCELL || this == HURRICANE || this == BLIZZARD || this == SANDSTORM || this == ICE_STORM || this == DERECHO
                || this == HABOOB || this == ATMOSPHERIC_RIVER || this == POLAR_LOW;
    }

    /** Weathers that can also arrive as a regional storm cell drifting across the map. */
    public boolean isStorm() {
        return switch (this) {
            case RAIN, HEAVY_RAIN, THUNDERSTORM, SUPERCELL, HURRICANE, HAIL, SLEET, ICE_STORM, SNOW, BLIZZARD, SANDSTORM, DERECHO,
                 COLD_FRONT, WARM_FRONT, GRAUPEL, THUNDERSNOW, SUN_SHOWER, POLAR_LOW -> true;
            default -> false;
        };
    }

    /**
     * Fixed 0-15 redstone code for the weather station. There are more weathers than signal strengths, so
     * the newer ones share the code of the weather they're most like (a cold front reads as a thunderstorm,
     * sea fog and smoke as fog, a cold snap as clear skies, and so on).
     */
    public int signalCode() {
        return switch (this) {
            case CLEAR, COLD_SNAP -> 0; case OVERCAST -> 1; case FOG, SEA_FOG, WILDFIRE_SMOKE -> 2; case DRIZZLE, WARM_FRONT, VOLCANIC_SMOG, SUN_SHOWER -> 3; case RAIN -> 4;
            case HEAVY_RAIN, ATMOSPHERIC_RIVER -> 5; case THUNDERSTORM, COLD_FRONT, PYROCUMULUS -> 6; case SUPERCELL -> 7; case HURRICANE -> 8; case HAIL, GRAUPEL -> 9;
            case SLEET, ICE_STORM, FREEZING_DRIZZLE -> 10; case SNOW, THUNDERSNOW -> 11; case BLIZZARD, POLAR_LOW -> 12; case WINDY, DERECHO -> 13; case SANDSTORM, VOLCANIC_ASH, DUST_HAZE, HABOOB -> 14;
            case HEATWAVE, HEAT_BURST -> 15;
        };
    }

    public int randomDuration(RandomSource random) {
        return minDuration + random.nextInt(Math.max(1, maxDuration - minDuration + 1));
    }

    public static WeatherType byId(String id) {
        for (WeatherType type : VALUES) {
            if (type.id().equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }

    public static WeatherType byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : CLEAR;
    }
}
