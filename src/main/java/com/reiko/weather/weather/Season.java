package com.reiko.weather.weather;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/** The four seasons. Each one reshapes the forecast and (optionally) shifts biome temperatures. */
public enum Season {
    SPRING(-0.1F),
    SUMMER(0.1F),
    AUTUMN(-0.2F),
    WINTER(-0.7F);

    private static final Season[] VALUES = values();

    /** Fraction of the winter temperature drop applied in this season (winter = 1). */
    private final float temperatureFactor;

    Season(float temperatureShift) {
        this.temperatureFactor = temperatureShift / 0.7F;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public MutableComponent displayName() {
        return Component.translatable("season.reikos_weather." + id());
    }

    public Season next() {
        return VALUES[(ordinal() + 1) % 4];
    }

    public Season previous() {
        return VALUES[(ordinal() + 3) % 4];
    }

    /** Biome temperature shift for this season, given the configured winter drop. */
    public float temperatureShift(float winterDrop) {
        return temperatureFactor * winterDrop;
    }

    /** How much more (or less) likely a weather is in this season. */
    public float weatherMultiplier(WeatherType type) {
        return switch (this) {
            case SPRING -> switch (type) {
                case DRIZZLE, RAIN -> 1.6F;
                case HEAVY_RAIN -> 1.4F;
                case THUNDERSTORM -> 1.5F;
                case SUPERCELL -> 2.0F;
                case HAIL -> 1.8F;
                case FOG -> 1.2F;
                case SNOW, SLEET -> 0.5F;
                case BLIZZARD, ICE_STORM -> 0.2F;
                case HEATWAVE, HURRICANE -> 0.3F;
                default -> 1.0F;
            };
            case SUMMER -> switch (type) {
                case CLEAR -> 1.3F;
                case HEATWAVE -> 3.0F;
                case THUNDERSTORM, SUPERCELL -> 1.6F;
                case HURRICANE -> 2.0F;
                case SANDSTORM -> 1.5F;
                case HAIL -> 1.2F;
                case FOG -> 0.6F;
                case DRIZZLE -> 0.8F;
                case SNOW, SLEET -> 0.15F;
                case BLIZZARD, ICE_STORM -> 0.05F;
                default -> 1.0F;
            };
            case AUTUMN -> switch (type) {
                case FOG -> 2.0F;
                case OVERCAST, DRIZZLE -> 1.5F;
                case WINDY -> 1.8F;
                case RAIN, HURRICANE, SLEET -> 1.3F;
                case HEATWAVE -> 0.3F;
                case SUPERCELL -> 0.5F;
                case SNOW, ICE_STORM -> 0.7F;
                default -> 1.0F;
            };
            case WINTER -> switch (type) {
                case SNOW -> 3.0F;
                case BLIZZARD -> 2.5F;
                case SLEET, ICE_STORM -> 2.0F;
                case FOG -> 1.4F;
                case OVERCAST -> 1.3F;
                case DRIZZLE -> 0.7F;
                case RAIN -> 0.6F;
                case SANDSTORM -> 0.5F;
                case THUNDERSTORM, HAIL -> 0.3F;
                case HURRICANE -> 0.2F;
                case SUPERCELL -> 0.1F;
                case HEATWAVE -> 0.0F;
                default -> 1.0F;
            };
        };
    }

    public static Season byId(String id) {
        for (Season s : VALUES) {
            if (s.id().equalsIgnoreCase(id)) {
                return s;
            }
        }
        return null;
    }

    public static Season byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < 4 ? VALUES[ordinal] : null;
    }
}
