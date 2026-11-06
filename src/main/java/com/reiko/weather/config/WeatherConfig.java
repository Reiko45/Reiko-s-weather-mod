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

    public static final ModConfigSpec.BooleanValue ICE_GLAZE;
    public static final ModConfigSpec.BooleanValue PUDDLES;
    public static final ModConfigSpec.BooleanValue FLASH_FLOODS;
    public static final ModConfigSpec.IntValue MAX_FLOOD_BLOCKS;
    public static final ModConfigSpec.BooleanValue SNOW_MELT;

    public static final ModConfigSpec.BooleanValue SEASONS_ENABLED;
    public static final ModConfigSpec.IntValue DAYS_PER_SEASON;
    public static final ModConfigSpec.BooleanValue SEASONAL_TEMPERATURE;
    public static final ModConfigSpec.DoubleValue WINTER_TEMPERATURE_DROP;
    public static final ModConfigSpec.BooleanValue SEASONAL_CROPS;

    public static final ModConfigSpec.BooleanValue REGIONAL_STORMS;
    public static final ModConfigSpec.DoubleValue STORM_MINUTES;
    public static final ModConfigSpec.IntValue MAX_STORM_CELLS;

    public static final ModConfigSpec.BooleanValue WATERSPOUTS;
    public static final ModConfigSpec.BooleanValue DUST_DEVILS;
    public static final ModConfigSpec.BooleanValue WEATHER_MOBS;
    public static final ModConfigSpec.BooleanValue ANIMALS_SEEK_SHELTER;

    public static final ModConfigSpec.BooleanValue TEMPERATURE;
    public static final ModConfigSpec.BooleanValue WILDFIRES;
    public static final ModConfigSpec.BooleanValue CROP_WILTING;
    public static final ModConfigSpec.BooleanValue CAULDRON_EVAPORATION;
    public static final ModConfigSpec.BooleanValue FREEZE_RIVERS;
    public static final ModConfigSpec.BooleanValue ICICLES;
    public static final ModConfigSpec.BooleanValue AVALANCHES;
    public static final ModConfigSpec.BooleanValue FULGURITE;
    public static final ModConfigSpec.BooleanValue BALL_LIGHTNING;
    public static final ModConfigSpec.IntValue ALTAR_COOLDOWN;
    public static final ModConfigSpec.BooleanValue OTHERWORLD_WEATHER;
    public static final ModConfigSpec.BooleanValue FIRE_RAIN_IGNITES;
    public static final ModConfigSpec.BooleanValue VOID_STORM_GUSTS;
    public static final ModConfigSpec.BooleanValue CUSTOM_WEATHERS;

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
        ICE_GLAZE = b.comment("Ice storms coat exposed ground and treetops in slippery ice glaze that melts afterwards.")
                .define("iceGlaze", true);
        PUDDLES = b.comment("Puddles form on flat ground in the rain and dry up afterwards.")
                .define("puddles", true);
        FLASH_FLOODS = b.comment("Heavy rain raises rivers and lakes so they spill over their banks. The water recedes",
                        "after the storm, but flowing water can wash away crops and torches. Off by default.")
                .define("flashFloods", false);
        MAX_FLOOD_BLOCKS = b.comment("Maximum flood water sources that can exist at once.")
                .defineInRange("maxFloodBlocks", 400, 0, 10000);
        SNOW_MELT = b.comment("Snow layers and frozen lake surfaces melt in places that are no longer cold (e.g. in spring).")
                .define("snowMelt", true);
        b.pop();

        b.comment("Seasons (uses Serene Seasons instead when it is installed)").push("seasons");
        SEASONS_ENABLED = b.comment("Enable seasons, which change which weather is likely.")
                .define("enabled", true);
        DAYS_PER_SEASON = b.comment("Length of each season in Minecraft days.")
                .defineInRange("daysPerSeason", 7, 1, 365);
        SEASONAL_TEMPERATURE = b.comment("Seasons shift biome temperatures, so temperate biomes get snow in winter.")
                .define("seasonalTemperature", true);
        WINTER_TEMPERATURE_DROP = b.comment("How much colder biomes get in winter (plains are 0.8; snow falls below 0.15).")
                .defineInRange("winterTemperatureDrop", 0.7, 0.0, 2.0);
        SEASONAL_CROPS = b.comment("Crops open to the sky grow slower in winter.")
                .define("seasonalCrops", true);
        b.pop();

        b.comment("Regional storm cells that drift across the map with the wind").push("storms");
        REGIONAL_STORMS = b.comment("Spawn storm cells that roll in from the horizon, so players far apart can have different weather.")
                .define("enabled", true);
        STORM_MINUTES = b.comment("Average minutes between new storm cells forming near players.")
                .defineInRange("averageMinutesBetween", 10.0, 0.5, 240.0);
        MAX_STORM_CELLS = b.comment("Maximum storm cells at once.")
                .defineInRange("maxCells", 3, 0, 16);
        b.pop();

        b.comment("Smaller weather phenomena").push("phenomena");
        WATERSPOUTS = b.comment("Thunderstorms, supercells and hurricanes can form waterspouts over open water.")
                .define("waterspouts", true);
        DUST_DEVILS = b.comment("Small, mostly harmless whirlwinds form in arid biomes on hot, clear days.")
                .define("dustDevils", true);
        WEATHER_MOBS = b.comment("Weather changes which monsters spawn: strays in blizzards, husks in sandstorms,",
                        "drowned in heavy rain near water.")
                .define("weatherMobs", true);
        ANIMALS_SEEK_SHELTER = b.comment("Animals look for shelter under trees and roofs during storms.")
                .define("animalsSeekShelter", true);
        b.pop();

        b.comment("Body temperature: cold and heat affect players depending on biome, season, time, weather,",
                "clothing and nearby heat sources (campfires, fires, lava, furnaces...)").push("temperature");
        TEMPERATURE = b.comment("Players freeze when it is too cold and get exhausted (and eventually heatstroke) when it is too hot.")
                .define("enabled", true);
        b.pop();

        b.comment("Extreme weather").push("extremes");
        WILDFIRES = b.comment("Heatwaves can start wildfires in dry grass in arid biomes (needs doFireTick and mobGriefing).")
                .define("wildfires", true);
        CROP_WILTING = b.comment("Heatwaves wilt crops on dry (unwatered) farmland.")
                .define("cropWilting", true);
        CAULDRON_EVAPORATION = b.comment("Heatwaves slowly dry out water cauldrons left in the sun.")
                .define("cauldronEvaporation", true);
        FREEZE_RIVERS = b.comment("Rivers and lakes freeze over solidly in winter and cold places (and thaw again later).")
                .define("freezeRivers", true);
        ICICLES = b.comment("Icicles grow under roof edges in the cold and can fall on players below.")
                .define("icicles", true);
        AVALANCHES = b.comment("Blizzards can set off small avalanches on snowy slopes (needs mobGriefing).")
                .define("avalanches", true);
        FULGURITE = b.comment("Lightning fuses sand into glass where it strikes.")
                .define("fulgurite", true);
        BALL_LIGHTNING = b.comment("Rare ball lightning drifts around during thunderstorms.")
                .define("ballLightning", true);
        b.pop();

        b.comment("The Weather Altar (end-game weather control)").push("altar");
        ALTAR_COOLDOWN = b.comment("Seconds between uses of a weather altar.")
                .defineInRange("cooldownSeconds", 180, 0, 86400);
        b.pop();

        b.comment("Weather in the Nether and the End").push("otherworld");
        OTHERWORLD_WEATHER = b.comment("Ash storms, ember falls and fire rain in the Nether; void storms in the End.")
                .define("enabled", true);
        FIRE_RAIN_IGNITES = b.comment("Fire rain can set exposed flammable blocks alight (needs doFireTick and mobGriefing).")
                .define("fireRainIgnites", true);
        VOID_STORM_GUSTS = b.comment("Void storm gusts push players and mobs toward the edge of End islands.")
                .define("voidStormGusts", true);
        b.pop();

        b.comment("Data-driven weather").push("datapacks");
        CUSTOM_WEATHERS = b.comment("Load extra weather types from datapacks (data/<namespace>/reikos_weather/weather/*.json).")
                .define("customWeathers", true);
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
