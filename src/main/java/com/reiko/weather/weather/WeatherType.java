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
    SNOW        (true, false, 6, 4800, 12000, 2.0F, 0.3F, 0.0F),
    BLIZZARD    (true, false, 3, 3600, 7200, 1.5F, 0.1F, 0.0F),
    WINDY       (false, false, 8, 3600, 8400, 1.0F, 1.0F, 1.0F),
    SANDSTORM   (false, false, 4, 2400, 6000, 0.0F, 0.1F, 3.0F),
    HEATWAVE    (false, false, 4, 6000, 12000, 0.0F, 0.6F, 2.0F);

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

    /** Calm weathers are the "breaks" between more eventful weather. */
    public boolean isCalm() {
        return this == CLEAR || this == OVERCAST;
    }

    public boolean isSevere() {
        return this == SUPERCELL || this == HURRICANE || this == BLIZZARD || this == SANDSTORM;
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
