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
                        .precip(Precip.HAIL, 0.6F).precip(Precip.DOWNPOUR, 0.5F).hail().lightning(6.0F).tornadoes().waterspouts().floods().debris().build(),
                builder().rain(1.0F).thunder(1.0F).overcast(0.85F).tint(STORM_GREEN, 0.3F).fog(0.35F).wind(0.75F)
                        .precip(Precip.DUST, 1.0F).lightning(4.0F).tornadoes().build());

        put(WeatherType.HURRICANE,
                builder().rain(1.0F).thunder(1.0F).overcast(0.85F).tint(SNOW_WHITE, 0.4F).fog(0.85F, SNOW_WHITE).wind(1.0F)
                        .precip(Precip.BLIZZARD, 1.2F).freeze(0.6F).snowLayers(3).lightning(2.0F).build(),
                builder().rain(1.0F).thunder(1.0F).overcast(0.9F).fog(0.65F).wind(1.0F)
                        .precip(Precip.DOWNPOUR, 1.2F).precip(Precip.LEAVES, 0.6F).lightning(3.0F).floods().waterspouts().debris().build(),
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

        // a derecho: a fast-moving line of violent wind and thunder that breaks branches off trees
        put(WeatherType.DERECHO,
                builder().rain(0.8F).thunder(1.0F).overcast(0.8F).fog(0.5F, SNOW_WHITE).wind(1.0F)
                        .precip(Precip.BLIZZARD, 0.8F).freeze(0.3F).snowLayers(1).lightning(3.0F).debris().build(),
                builder().rain(1.0F).thunder(1.0F).overcast(0.85F).tint(0x6E7A8A, 0.25F).fog(0.45F).wind(1.0F)
                        .precip(Precip.DOWNPOUR, 0.7F).precip(Precip.LEAVES, 1.2F).lightning(5.0F).debris().build(),
                builder().rain(0.6F).thunder(1.0F).overcast(0.75F).tint(SAND, 0.35F).fog(0.6F, SAND_FOG).wind(1.0F)
                        .precip(Precip.DUST, 1.2F).precip(Precip.SAND, 0.6F).lightning(3.0F).build());

        // the ash cloud of an erupting volcano: a dark, choking sky raining grey ash (its lightning stays over the volcano)
        WeatherProfile ash = builder().overcast(0.9F).tint(0x4E4945, 0.55F).fog(0.62F, 0x6A645F).wind(0.25F)
                .precip(Precip.ASH, 1.0F).precip(Precip.DUST, 0.6F).ashLayers(3).build();
        put(WeatherType.VOLCANIC_ASH, ash, ash, ash);

        // a cold front: a fast squall line of wind, downpours and thunder (a snow squall in the cold, a dust front in deserts)
        put(WeatherType.COLD_FRONT,
                builder().rain(1.0F).thunder(0.6F).overcast(0.9F).fog(0.6F, SNOW_WHITE).wind(0.9F)
                        .precip(Precip.BLIZZARD, 1.0F).freeze(0.4F).snowLayers(1).lightning(1.0F).build(),
                builder().rain(1.0F).thunder(1.0F).overcast(0.9F).tint(0x5C6878, 0.25F).fog(0.45F).wind(0.9F)
                        .precip(Precip.DOWNPOUR, 0.9F).precip(Precip.LEAVES, 0.6F).lightning(3.0F).build(),
                builder().rain(0.5F).thunder(1.0F).overcast(0.7F).tint(SAND, 0.3F).fog(0.55F, SAND_FOG).wind(0.95F)
                        .precip(Precip.DUST, 1.2F).precip(Precip.SAND, 0.4F).lightning(2.0F).build());

        // a warm front: a wide, slow band of low cloud and drizzle (light snow in the cold)
        put(WeatherType.WARM_FRONT,
                builder().rain(0.6F).overcast(0.85F).fog(0.35F).wind(0.2F).precip(Precip.SNOW, 0.4F).build(),
                builder().rain(0.4F).overcast(0.85F).fog(0.35F).wind(0.2F).cropBoost().build(),
                builder().overcast(0.75F).fog(0.15F, SAND_FOG).wind(0.25F).build());

        // a bank of sea fog: thick and white, and very still
        WeatherProfile seaFog = builder().overcast(0.45F).fog(0.85F, 0xCDD3D8).wind(0.04F).clouds(0.0F).build();
        put(WeatherType.SEA_FOG, seaFog, seaFog, seaFog);

        // wildfire smoke: an orange-brown haze that turns the sun red
        WeatherProfile smoke = builder().overcast(0.45F).tint(0xC0703A, 0.5F).fog(0.5F, 0x9C7B62).wind(0.2F)
                .precip(Precip.DUST, 0.35F).build();
        put(WeatherType.WILDFIRE_SMOKE, smoke, smoke, smoke);

        // volcanic smog ("vog"): a sulfurous yellow-grey haze with a stinging acid drizzle
        WeatherProfile vog = builder().rain(0.3F).overcast(0.7F).tint(0xB0A672, 0.35F).fog(0.42F, 0xB4AC86).wind(0.15F).acid().build();
        put(WeatherType.VOLCANIC_SMOG, vog, vog, vog);

        // a cold snap: a hard, clear frost, with only a breath of wind (a sharp nip even in the desert)
        put(WeatherType.COLD_SNAP,
                builder().fog(0.05F, 0xDCE6F0).wind(0.12F).clouds(0.0F).freeze(0.3F).tempOffset(-12.0F).build(),
                builder().fog(0.04F, 0xD8E0E8).wind(0.12F).clouds(0.0F).freeze(0.25F).tempOffset(-11.0F).build(),
                builder().wind(0.15F).clouds(0.0F).tempOffset(-8.0F).build());

        // dust carried far downwind of a sandstorm: a tawny haze that dims the sun, and dust settling on everything
        WeatherProfile haze = builder().overcast(0.3F).tint(0xD0884A, 0.5F).fog(0.36F, 0xC4925E).wind(0.3F).clouds(0.0F)
                .precip(Precip.DUST, 0.3F).dustLayers(1).build();
        put(WeatherType.DUST_HAZE, haze, haze, haze);

        // a haboob: a wall of dust a few hundred metres high rolling across the land, then gone in an hour
        WeatherProfile haboob = builder().overcast(0.6F).tint(0xB06A32, 0.8F).fog(0.9F, 0xA8743F).wind(0.85F).clouds(0.0F)
                .precip(Precip.SAND, 1.6F).precip(Precip.DUST, 1.4F).build();
        put(WeatherType.GRAUPEL,
                builder().rain(0.5F).overcast(0.55F).fog(0.15F).wind(0.3F).precip(Precip.GRAUPEL, 1.0F).freeze(0.15F).build(),
                builder().rain(0.5F).overcast(0.55F).fog(0.15F).wind(0.3F).precip(Precip.GRAUPEL, 1.0F).build(),
                builder().rain(0.3F).overcast(0.45F).wind(0.3F).build());

        // thunder and lightning in a heavy snowstorm (in milder places it falls as sleet)
        put(WeatherType.THUNDERSNOW,
                builder().rain(1.0F).thunder(1.0F).overcast(0.85F).tint(SNOW_WHITE, 0.3F).fog(0.6F, SNOW_WHITE).wind(0.6F)
                        .precip(Precip.SNOW, 1.2F).freeze(0.5F).snowLayers(3).lightning(1.5F).build(),
                builder().rain(1.0F).thunder(1.0F).overcast(0.8F).fog(0.45F).wind(0.55F).precip(Precip.SLEET, 0.8F).freeze(0.3F).lightning(1.0F).build(),
                builder().rain(0.6F).thunder(1.0F).overcast(0.6F).wind(0.5F).precip(Precip.DUST, 0.4F).build());

        put(WeatherType.FREEZING_DRIZZLE,
                builder().rain(0.35F).overcast(0.65F).tint(0xC8D4E0, 0.15F).fog(0.35F, 0xC8D2DC).wind(0.1F).precip(Precip.SLEET, 0.25F)
                        .freeze(0.25F).glazeWater().glazeGround().tempOffset(-2.0F).build(),
                builder().rain(0.35F).overcast(0.65F).tint(0xC8D4E0, 0.15F).fog(0.3F, 0xC8D2DC).wind(0.1F).precip(Precip.SLEET, 0.25F)
                        .freeze(0.25F).glazeWater().glazeGround().tempOffset(-2.0F).build(),
                builder().rain(0.3F).overcast(0.5F).wind(0.15F).build());

        put(WeatherType.HABOOB, haboob, haboob, haboob);

        // an atmospheric river: warm, steady, soaking rain that goes on and on (deep snow on the cold mountains)
        put(WeatherType.ATMOSPHERIC_RIVER,
                builder().rain(1.0F).overcast(0.85F).fog(0.55F).wind(0.45F).precip(Precip.SNOW, 1.0F).snowLayers(3).build(),
                builder().rain(1.0F).overcast(0.85F).fog(0.5F).wind(0.45F).tempOffset(3.0F).precip(Precip.DOWNPOUR, 0.9F).cropBoost().floods().build(),
                builder().rain(1.0F).overcast(0.8F).fog(0.35F).wind(0.4F).tempOffset(3.0F).precip(Precip.DOWNPOUR, 0.6F).floods().build());

        // a heat burst: hot, bone-dry gusts out of a clear night sky
        WeatherProfile burst = builder().overcast(0.15F).wind(0.75F).clouds(0.2F).tempOffset(13.0F).precip(Precip.DUST, 0.4F).build();
        put(WeatherType.HEAT_BURST, burst, burst, burst);

        // a fire cloud: a smoky thunderhead over a huge wildfire, with dry lightning and hardly any rain
        WeatherProfile pyro = builder().rain(0.1F).thunder(0.6F).overcast(0.7F).tint(0xA0603A, 0.45F).fog(0.45F, 0x8C705C).wind(0.55F)
                .precip(Precip.ASH, 0.3F).precip(Precip.DUST, 0.4F).lightning(3.0F).build();
        put(WeatherType.PYROCUMULUS, pyro, pyro, pyro);

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
            if (p.hail || p.freeze > 0 || p.heat || p.wind > Wind.PUSH_THRESHOLD || p.acid || p.ashLayers > 0) {
                return true;
            }
        }
        return false;
    }

    private WeatherProfiles() {}
}
