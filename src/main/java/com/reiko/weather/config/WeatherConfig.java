package com.reiko.weather.config;

import com.reiko.weather.weather.WeatherType;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.Map;

/** Server-side (per world) configuration: weather frequency and hazards. Synced to clients. */
public final class WeatherConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.DoubleValue DURATION_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue SLEEP_CLEARS_WEATHER;
    public static final Map<WeatherType, ModConfigSpec.IntValue> WEIGHTS = new EnumMap<>(WeatherType.class);

    public static final ModConfigSpec.DoubleValue HAIL_DAMAGE;
    public static final ModConfigSpec.BooleanValue HAIL_DAMAGES_CROPS;
    public static final ModConfigSpec.BooleanValue FREEZING;
    public static final ModConfigSpec.DoubleValue HEAT_EXHAUSTION;
    public static final ModConfigSpec.IntValue MAX_SNOW_LAYERS;
    public static final ModConfigSpec.BooleanValue GLAZE_WATER;
    public static final ModConfigSpec.BooleanValue WIND_PUSHES_ENTITIES;
    public static final ModConfigSpec.BooleanValue RAIN_BOOSTS_CROPS;
    public static final ModConfigSpec.BooleanValue HEAT_SLOWS_CROPS;
    public static final ModConfigSpec.BooleanValue EXTRA_LIGHTNING;

    public static final ModConfigSpec.BooleanValue TORNADOES;
    public static final ModConfigSpec.DoubleValue TORNADO_CHANCE;
    public static final ModConfigSpec.IntValue MAX_TORNADOES;
    public static final ModConfigSpec.BooleanValue TORNADO_LIFTS_BLOCKS;
    public static final ModConfigSpec.BooleanValue TORNADO_WARNINGS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("General weather cycle settings").push("general");
        ENABLED = b.comment("Enable the Reiko's Weather cycle. When disabled, vanilla weather runs untouched.")
                .define("enabled", true);
        DURATION_MULTIPLIER = b.comment("Multiplier applied to how long each weather lasts.")
                .defineInRange("durationMultiplier", 1.0, 0.05, 20.0);
        SLEEP_CLEARS_WEATHER = b.comment("Sleeping through the night clears any weather, not just rain.")
                .define("sleepClearsWeather", true);
        b.pop();

        b.comment("Relative chance of each weather being chosen next (0 disables it).",
                "Chances are further biased toward the climates players are standing in.").push("weights");
        for (WeatherType type : WeatherType.values()) {
            WEIGHTS.put(type, b.defineInRange(type.id(), type.defaultWeight, 0, 1000));
        }
        b.pop();

        b.comment("Gameplay hazards caused by weather").push("hazards");
        HAIL_DAMAGE = b.comment("Damage dealt by hailstones to unsheltered creatures without a helmet (0 disables).")
                .defineInRange("hailDamage", 1.0, 0.0, 20.0);
        HAIL_DAMAGES_CROPS = b.comment("Hail can knock back the growth of exposed crops.")
                .define("hailDamagesCrops", true);
        FREEZING = b.comment("Blizzards and sleet chill unsheltered creatures. Leather armor and nearby heat sources protect.")
                .define("freezing", true);
        HEAT_EXHAUSTION = b.comment("Extra hunger exhaustion per second for players in direct sun during a heatwave (0 disables).")
                .defineInRange("heatExhaustion", 0.1, 0.0, 4.0);
        MAX_SNOW_LAYERS = b.comment("Maximum snow layers heavy snow and blizzards can pile up (0 disables extra accumulation).")
                .defineInRange("maxSnowLayers", 4, 0, 8);
        GLAZE_WATER = b.comment("Sleet glazes exposed still water with a thin crust of ice that melts in sunlight.")
                .define("glazeWater", true);
        WIND_PUSHES_ENTITIES = b.comment("Strong winds push players, mobs, items and projectiles.")
                .define("windPushesEntities", true);
        RAIN_BOOSTS_CROPS = b.comment("Crops open to the sky grow faster in rain.")
                .define("rainBoostsCrops", true);
        HEAT_SLOWS_CROPS = b.comment("Crops grow slower during heatwaves.")
                .define("heatSlowsCrops", true);
        EXTRA_LIGHTNING = b.comment("Supercells and hurricanes produce frequent extra lightning near players.")
                .define("extraLightning", true);
        b.pop();

        b.comment("Tornadoes spawned by supercell storms").push("tornadoes");
        TORNADOES = b.comment("Allow supercells to spawn tornadoes.")
                .define("enabled", true);
        TORNADO_CHANCE = b.comment("Chance per second, during a supercell, that a tornado touches down near a player.")
                .defineInRange("chancePerSecond", 0.012, 0.0, 1.0);
        MAX_TORNADOES = b.comment("Maximum tornadoes alive at once.")
                .defineInRange("maxTornadoes", 2, 0, 16);
        TORNADO_LIFTS_BLOCKS = b.comment("Tornadoes rip up plants, leaves, crops and snow (see tag reikos_weather:tornado_liftable).",
                        "Also requires the mobGriefing gamerule.")
                .define("liftBlocks", true);
        TORNADO_WARNINGS = b.comment("Warn nearby players when a tornado touches down.")
                .define("warnings", true);
        b.pop();

        SPEC = b.build();
    }

    public static int weight(WeatherType type) {
        return WEIGHTS.get(type).get();
    }

    private WeatherConfig() {}
}
