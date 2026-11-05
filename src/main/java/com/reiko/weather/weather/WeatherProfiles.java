package com.reiko.weather.weather;

import java.util.EnumMap;
import java.util.Map;

import static com.reiko.weather.weather.WeatherProfile.builder;

/** The table of how every weather type looks and behaves in every climate. */
public final class WeatherProfiles {
    private static final Map<WeatherType, WeatherProfile[]> TABLE = new EnumMap<>(WeatherType.class);

    private static final int SAND = 0xC9A26B;
    private static final int SAND_FOG = 0xC4A26F;
    private static final int SNOW_WHITE = 0xDCE4EC;
    private static final int STORM_GREEN = 0x5E7D63;

    static {
        put(WeatherType.CLEAR, WeatherProfile.NONE, WeatherProfile.NONE, WeatherProfile.NONE);

        put(WeatherType.OVERCAST,
                builder().overcast(0.75F).fog(0.12F).wind(0.2F).build(),
                builder().overcast(0.75F).fog(0.12F).wind(0.2F).build(),
                builder().overcast(0.6F).fog(0.1F).wind(0.25F).build());

        put(WeatherType.FOG,
                builder().overcast(0.45F).fog(0.78F, 0xC8CDD2).wind(0.02F).build(),
                builder().overcast(0.45F).fog(0.78F, 0xC2C8CC).wind(0.02F).build(),
                builder().overcast(0.3F).fog(0.55F, 0xD9CDB4).wind(0.05F).build());

        put(WeatherType.DRIZZLE,
                builder().rain(0.35F).overcast(0.5F).fog(0.2F).wind(0.1F).build(),
                builder().rain(0.35F).overcast(0.55F).fog(0.18F).wind(0.12F).cropBoost().build(),
                builder().rain(0.35F).overcast(0.45F).wind(0.15F).build());

        put(WeatherType.RAIN,
                builder().rain(1.0F).overcast(0.25F).fog(0.3F).wind(0.25F).build(),
                builder().rain(1.0F).overcast(0.25F).fog(0.25F).wind(0.3F).cropBoost().build(),
                builder().rain(1.0F).overcast(0.5F).wind(0.3F).precip(Precip.DUST, 0.3F).build());

        put(WeatherType.HEAVY_RAIN,
                builder().rain(1.0F).overcast(0.6F).fog(0.6F).wind(0.4F).precip(Precip.SNOW, 1.0F).snowLayers(2).build(),
                builder().rain(1.0F).overcast(0.6F).fog(0.55F).wind(0.45F).precip(Precip.DOWNPOUR, 1.0F).cropBoost().floods().build(),
                builder().rain(1.0F).overcast(0.6F).wind(0.4F).precip(Precip.DUST, 0.5F).build());

        put(WeatherType.THUNDERSTORM,
                builder().rain(1.0F).thunder(1.0F).overcast(0.5F).fog(0.4F).wind(0.5F).precip(Precip.SNOW, 0.5F).build(),
                builder().rain(1.0F).thunder(1.0F).overcast(0.5F).fog(0.35F).wind(0.55F).precip(Precip.DOWNPOUR, 0.4F).cropBoost().floods().waterspouts().build(),
                builder().rain(1.0F).thunder(1.0F).overcast(0.5F).wind(0.6F).precip(Precip.DUST, 0.6F).build());

        put(WeatherType.SUPERCELL,
                builder().rain(1.0F).thunder(1.0F).overcast(0.85F).fog(0.5F).wind(0.7F)
                        .precip(Precip.SNOW, 1.0F).precip(Precip.HAIL, 0.4F).hail().lightning(4.0F).build(),
                builder().rain(1.0F).thunder(1.0F).overcast(0.9F).tint(STORM_GREEN, 0.35F).fog(0.4F).wind(0.7F)
                        .precip(Precip.HAIL, 0.6F).precip(Precip.DOWNPOUR, 0.5F).hail().lightning(6.0F).tornadoes().waterspouts().floods().build(),
                builder().rain(1.0F).thunder(1.0F).overcast(0.85F).tint(STORM_GREEN, 0.3F).fog(0.35F).wind(0.75F)
                        .precip(Precip.DUST, 1.0F).lightning(4.0F).tornadoes().build());

        put(WeatherType.HURRICANE,
                builder().rain(1.0F).thunder(1.0F).overcast(0.85F).tint(SNOW_WHITE, 0.4F).fog(0.85F, SNOW_WHITE).wind(1.0F)
                        .precip(Precip.BLIZZARD, 1.2F).freeze(0.6F).snowLayers(3).lightning(2.0F).build(),
                builder().rain(1.0F).thunder(1.0F).overcast(0.9F).fog(0.65F).wind(1.0F)
                        .precip(Precip.DOWNPOUR, 1.2F).precip(Precip.LEAVES, 0.6F).lightning(3.0F).floods().waterspouts().build(),
                builder().rain(1.0F).thunder(1.0F).overcast(0.8F).tint(SAND, 0.5F).fog(0.78F, SAND_FOG).wind(1.0F)
                        .precip(Precip.SAND, 1.2F).lightning(2.0F).build());

        put(WeatherType.HAIL,
                builder().rain(0.45F).overcast(0.65F).fog(0.2F).wind(0.35F).precip(Precip.HAIL, 1.0F).hail().build(),
                builder().rain(0.45F).overcast(0.65F).fog(0.2F).wind(0.35F).precip(Precip.HAIL, 1.0F).hail().build(),
                builder().rain(0.45F).overcast(0.6F).wind(0.35F).precip(Precip.DUST, 0.3F).build());

        put(WeatherType.SLEET,
                builder().rain(0.6F).overcast(0.6F).fog(0.35F).wind(0.35F).precip(Precip.SLEET, 1.0F).freeze(0.35F).glazeWater().build(),
                builder().rain(0.6F).overcast(0.6F).fog(0.3F).wind(0.35F).precip(Precip.SLEET, 1.0F).freeze(0.35F).glazeWater().build(),
                builder().rain(0.4F).overcast(0.5F).wind(0.3F).build());

        put(WeatherType.ICE_STORM,
                builder().rain(0.8F).overcast(0.7F).tint(0xC8D4E0, 0.2F).fog(0.35F, 0xC8D2DC).wind(0.25F)
                        .precip(Precip.SLEET, 0.8F).freeze(0.3F).glazeWater().glazeGround().build(),
                builder().rain(0.8F).overcast(0.7F).tint(0xC8D4E0, 0.2F).fog(0.3F, 0xC8D2DC).wind(0.25F)
                        .precip(Precip.DOWNPOUR, 0.5F).precip(Precip.SLEET, 0.5F).freeze(0.3F).glazeWater().glazeGround().build(),
                builder().rain(0.4F).overcast(0.5F).wind(0.3F).build());

        put(WeatherType.SNOW,
                builder().rain(1.0F).overcast(0.4F).fog(0.3F).wind(0.15F).precip(Precip.SNOW, 0.5F).snowLayers(1).build(),
                builder().rain(0.7F).overcast(0.5F).fog(0.3F).wind(0.2F).precip(Precip.SLEET, 0.3F).build(),
                builder().rain(0.4F).overcast(0.4F).wind(0.2F).build());

        put(WeatherType.BLIZZARD,
                builder().rain(1.0F).overcast(0.8F).tint(SNOW_WHITE, 0.5F).fog(0.85F, SNOW_WHITE).wind(0.95F)
                        .precip(Precip.BLIZZARD, 1.5F).freeze(1.0F).snowLayers(4).build(),
                builder().rain(1.0F).overcast(0.7F).fog(0.55F).wind(0.85F)
                        .precip(Precip.DOWNPOUR, 0.8F).precip(Precip.SLEET, 0.4F).freeze(0.2F).build(),
                builder().overcast(0.5F).fog(0.3F, SAND_FOG).wind(0.85F).precip(Precip.DUST, 1.0F).build());

        put(WeatherType.WINDY,
                builder().overcast(0.2F).fog(0.15F, SNOW_WHITE).wind(0.8F).precip(Precip.BLIZZARD, 0.5F).build(),
                builder().overcast(0.15F).fog(0.05F).wind(0.8F).precip(Precip.LEAVES, 1.0F).build(),
                builder().overcast(0.2F).fog(0.2F, SAND_FOG).wind(0.8F).precip(Precip.DUST, 1.0F).precip(Precip.SAND, 0.3F).build());

        put(WeatherType.SANDSTORM,
                builder().overcast(0.2F).wind(0.7F).precip(Precip.BLIZZARD, 0.3F).build(),
                builder().overcast(0.25F).fog(0.2F, 0xBFAE8E).wind(0.75F).precip(Precip.DUST, 0.8F).precip(Precip.LEAVES, 0.4F).build(),
                builder().overcast(0.5F).tint(SAND, 0.75F).fog(0.82F, SAND_FOG).wind(0.9F).clouds(0.0F).precip(Precip.SAND, 2.0F).precip(Precip.DUST, 1.0F).build());

        put(WeatherType.HEATWAVE,
                WeatherProfile.NONE,
                builder().tint(0xFFCC88, 0.5F).fog(0.25F, 0xF0D2A0).wind(0.05F).heat().build(),
                builder().tint(0xFFB866, 0.62F).fog(0.32F, 0xF2C98C).wind(0.05F).heat().build());

        for (WeatherType type : WeatherType.values()) {
            if (!TABLE.containsKey(type)) {
                throw new IllegalStateException("Missing weather profile for " + type);
            }
        }
    }

    private static void put(WeatherType type, WeatherProfile cold, WeatherProfile temperate, WeatherProfile arid) {
        TABLE.put(type, new WeatherProfile[]{cold, temperate, arid});
    }

    public static WeatherProfile get(WeatherType type, Climate climate) {
        return TABLE.get(type)[climate.ordinal()];
    }

    /** Whether this weather can produce any per-entity effect anywhere (used to skip work quickly). */
    public static boolean affectsEntities(WeatherType type) {
        for (WeatherProfile p : TABLE.get(type)) {
            if (p.hail || p.freeze > 0 || p.heat || p.wind > Wind.PUSH_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    private WeatherProfiles() {}
}
