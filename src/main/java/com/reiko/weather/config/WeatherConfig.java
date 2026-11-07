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
    public static final ModConfigSpec.BooleanValue CROP_FROST;
    public static final ModConfigSpec.BooleanValue STORM_SURGE;
    public static final ModConfigSpec.BooleanValue DRY_LIGHTNING;
    public static final ModConfigSpec.BooleanValue MONSOON;
    public static final ModConfigSpec.BooleanValue MICROCLIMATES;
    public static final ModConfigSpec.BooleanValue METEORS;
    public static final ModConfigSpec.BooleanValue GEOMAGNETIC_STORMS;
    public static final ModConfigSpec.BooleanValue VILLAGERS_SHELTER;
    public static final ModConfigSpec.BooleanValue WINTER_WOOL;
    public static final ModConfigSpec.BooleanValue POLAR_BEARS;
    public static final ModConfigSpec.BooleanValue FIRE_RAIN_IGNITES;
    public static final ModConfigSpec.BooleanValue VOID_STORM_GUSTS;
    public static final ModConfigSpec.BooleanValue CUSTOM_WEATHERS;
    public static final ModConfigSpec.BooleanValue BLOOD_MOONS;
    public static final ModConfigSpec.BooleanValue BLOOD_MOON_NO_SLEEP;
    public static final ModConfigSpec.BooleanValue SNOWMELT_FLOODS;
    public static final ModConfigSpec.BooleanValue THIN_ICE;
    public static final ModConfigSpec.BooleanValue ROUGH_SEAS;
    public static final ModConfigSpec.BooleanValue TIDES;
    public static final ModConfigSpec.BooleanValue STORM_DEBRIS;
    public static final ModConfigSpec.BooleanValue LEAF_PILES;
    public static final ModConfigSpec.BooleanValue WILD_CROPS;
    public static final ModConfigSpec.BooleanValue WEATHER_FISHING;
    public static final ModConfigSpec.BooleanValue EARTHWORMS;
    public static final ModConfigSpec.BooleanValue FOOD_SPOILAGE;
    public static final ModConfigSpec.BooleanValue CHARGED_TOOLS;
    public static final ModConfigSpec.BooleanValue GUIDE_ON_JOIN;
    public static final ModConfigSpec.BooleanValue TOUGH_AS_NAILS;

    public static final ModConfigSpec.BooleanValue AFTERNOON_STORMS;
    public static final ModConfigSpec.BooleanValue SEA_FOG;
    public static final ModConfigSpec.BooleanValue WILDFIRE_SMOKE;
    public static final ModConfigSpec.BooleanValue LIGHTNING_WARNINGS;
    public static final ModConfigSpec.BooleanValue HAIL_BREAKS_GLASS;
    public static final ModConfigSpec.BooleanValue MUDSLIDES;
    public static final ModConfigSpec.BooleanValue ACID_RAIN;
    public static final ModConfigSpec.BooleanValue HOT_SPRINGS;
    public static final ModConfigSpec.BooleanValue CRATER_LAKES;
    public static final ModConfigSpec.BooleanValue LAVA_TUBES;
    public static final ModConfigSpec.BooleanValue METEOR_CRATERS;
    public static final ModConfigSpec.BooleanValue SALT_FLATS;
    public static final ModConfigSpec.BooleanValue GLACIERS;
    public static final ModConfigSpec.BooleanValue TIDAL_FLATS;
    public static final ModConfigSpec.BooleanValue DUNES;
    public static final ModConfigSpec.BooleanValue CHANGING_LANDFORMS;

    public static final ModConfigSpec.BooleanValue VOLCANO_GENERATION;
    public static final ModConfigSpec.BooleanValue ERUPTIONS;
    public static final ModConfigSpec.DoubleValue DAYS_BETWEEN_ERUPTIONS;
    public static final ModConfigSpec.BooleanValue LAVA_FLOWS;
    public static final ModConfigSpec.BooleanValue LAVA_BOMBS;
    public static final ModConfigSpec.BooleanValue ASH_FALL;

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
            if (type.natural()) {
                WEIGHTS.put(type, b.defineInRange(type.id(), type.defaultWeight, 0, 1000));
            }
        }
        b.pop();

        b.comment("Gameplay hazards caused by weather").push("hazards");
        HAIL_DAMAGE = b.comment("Damage dealt by hailstones to unsheltered creatures without a helmet (0 disables).")
                .defineInRange("hailDamage", 1.0, 0.0, 20.0);
        HAIL_DAMAGES_CROPS = b.comment("Hail can knock back the growth of exposed crops.")
                .define("hailDamagesCrops", true);
        HAIL_BREAKS_GLASS = b.comment("Big hail cracks and smashes glass open to the sky (tinted glass holds). Needs mobGriefing.")
                .define("hailBreaksGlass", true);
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

        b.comment("Farming").push("farming");
        CROP_FROST = b.comment("Frost damages exposed crops on freezing nights (glass greenhouses protect them).")
                .define("cropFrost", true);
        LEAF_PILES = b.comment("Fallen leaves pile up under trees in autumn (and are blown down in storms).")
                .define("leafPiles", true);
        WILD_CROPS = b.comment("Breaking grass sometimes drops rice (jungles, savannas, swamps) or kale (cold places).")
                .define("wildCrops", true);
        FOOD_SPOILAGE = b.comment("Raw meat and fish spoil in hot weather, in your inventory and in containers.",
                        "Iceboxes, and containers with ice next to them, keep food fresh.")
                .define("foodSpoilage", true);
        b.pop();

        b.comment("Fishing").push("fishing");
        WEATHER_FISHING = b.comment("Rain brings extra catches, and stormfish and icefish bite in storms and through ice.")
                .define("weatherFishing", true);
        EARTHWORMS = b.comment("Earthworms come up after rain; dig them out of grass and dirt to use as bait.")
                .define("earthworms", true);
        b.pop();

        b.comment("Local climates and regional weather").push("climate");
        MICROCLIMATES = b.comment("Terrain shapes the weather: calm, foggy valleys; windy peaks; drier land in the rain shadow",
                        "of mountains; lake-effect snow downwind of big lakes in the cold.")
                .define("microclimates", true);
        MONSOON = b.comment("Summer is the rainy season in jungles and savannas (savannas get rain at all).")
                .define("monsoon", true);
        STORM_SURGE = b.comment("Hurricanes push the sea up onto low-lying coasts for a while (sandbags hold it back).")
                .define("stormSurge", true);
        DRY_LIGHTNING = b.comment("Lightning that strikes where it isn't raining starts fires, and summer heatwaves bring the odd",
                        "dry thunderstorm strike (needs doFireTick and mobGriefing).")
                .define("dryLightning", true);
        SNOWMELT_FLOODS = b.comment("Rivers rise for the first days of spring as the snow melts (the water goes down again).")
                .define("snowmeltFloods", true);
        THIN_ICE = b.comment("Thin ice over water cracks and breaks under players who sprint on it or stand still too long.")
                .define("thinIce", true);
        ROUGH_SEAS = b.comment("Storms toss boats around on open water.")
                .define("roughSeas", true);
        TIDES = b.comment("High spring tides (around full and new moons) wash up onto low beaches and drain away again.")
                .define("tides", true);
        STORM_DEBRIS = b.comment("Violent storms (derechos, hurricanes, supercells) break branches off trees (needs mobGriefing).")
                .define("stormDebris", true);
        AFTERNOON_STORMS = b.comment("On summer days (any day in the tropics) clouds tower up and some break into thunderstorms",
                        "in the afternoon, dying away by dusk.")
                .define("afternoonStorms", true);
        SEA_FOG = b.comment("On cool, calm mornings a bank of fog rolls in off the sea, burning off by mid-morning.")
                .define("seaFog", true);
        b.pop();

        b.comment("Sky events").push("sky");
        METEORS = b.comment("Meteors fall on meteor shower nights, leaving a meteorite (needs mobGriefing for the crater).")
                .define("meteors", true);
        GEOMAGNETIC_STORMS = b.comment("On some aurora nights a geomagnetic storm makes compasses spin and radars crackle.")
                .define("geomagneticStorms", true);
        BLOOD_MOONS = b.comment("Some full moons rise blood red, and monsters swarm under them.")
                .define("bloodMoons", true);
        BLOOD_MOON_NO_SLEEP = b.comment("Nobody can sleep through a blood moon.")
                .define("bloodMoonNoSleep", true);
        b.pop();

        b.comment("Villagers and animals").push("creatures");
        VILLAGERS_SHELTER = b.comment("Villagers go indoors during storms.")
                .define("villagersShelter", true);
        WINTER_WOOL = b.comment("Sheep sheared in winter or in the cold give extra wool.")
                .define("winterWool", true);
        POLAR_BEARS = b.comment("Polar bears roam in blizzards.")
                .define("polarBears", true);
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
        LIGHTNING_WARNINGS = b.comment("St. Elmo's fire: sparks crackle around the spot (and on anyone near it) a few seconds",
                        "before the mod's storm lightning strikes there.")
                .define("lightningWarnings", true);
        WILDFIRE_SMOKE = b.comment("Big fires send a pall of smoke drifting downwind that turns the sun red.")
                .define("wildfireSmoke", true);
        MUDSLIDES = b.comment("Long downpours make soil on steep slopes slide downhill as mud (needs mobGriefing).")
                .define("mudslides", true);
        CHARGED_TOOLS = b.comment("Lightning striking a tool lying on an anvil or copper block charges it for a few minutes.")
                .define("chargedTools", true);
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

        b.comment("Other mods and the guidebook").push("misc");
        GUIDE_ON_JOIN = b.comment("Give players a Weather Almanac (the mod's guidebook) the first time they join.")
                .define("guideOnJoin", true);
        TOUGH_AS_NAILS = b.comment("When Tough As Nails is installed, let it handle body temperature (this mod's weather still",
                        "feeds into it) instead of running both temperature systems.")
                .define("toughAsNailsTemperature", true);
        b.pop();

        b.comment("Data-driven weather").push("datapacks");
        CUSTOM_WEATHERS = b.comment("Load extra weather types from datapacks (data/<namespace>/reikos_weather/weather/*.json).")
                .define("customWeathers", true);
        b.pop();

        b.comment("Volcanoes").push("volcanoes");
        VOLCANO_GENERATION = b.comment("Generate volcanoes (and volcanic islands) in new parts of the world.")
                .define("generate", true);
        ERUPTIONS = b.comment("Volcanoes wake up now and then: the ground rumbles for a few minutes, then they erupt.")
                .define("eruptions", true);
        DAYS_BETWEEN_ERUPTIONS = b.comment("Average Minecraft days a volcano sleeps between eruptions (only counted while a player is near it).")
                .defineInRange("averageDaysBetweenEruptions", 4.0, 0.1, 1000.0);
        LAVA_FLOWS = b.comment("Erupting volcanoes overflow, sending lava down their flanks; it cools into basalt afterwards (needs mobGriefing).")
                .define("lavaFlows", true);
        LAVA_BOMBS = b.comment("Erupting volcanoes hurl lava bombs that burst where they land.")
                .define("lavaBombs", true);
        ASH_FALL = b.comment("Eruptions send an ash cloud downwind that darkens the sky and buries the land in ash.")
                .define("ashFall", true);
        ACID_RAIN = b.comment("After an eruption, volcanic smog drifts downwind with an acid drizzle: copper weathers faster,",
                        "leaves wilt and fall, crops struggle and bare heads sting.")
                .define("acidRain", true);
        LAVA_TUBES = b.comment("Lava tube caves run down under the flanks of active volcanoes.")
                .define("lavaTubes", true);
        b.pop();

        b.comment("Landforms shaped by the weather (generated in new parts of the world)").push("landforms");
        HOT_SPRINGS = b.comment("Steaming hot springs (warm, and they heal you) and geysers that erupt every few minutes.")
                .define("hotSprings", true);
        CRATER_LAKES = b.comment("Extinct volcanoes with a lake in the crater and forest on the slopes.")
                .define("craterLakes", true);
        METEOR_CRATERS = b.comment("Old, weathered meteor craters with meteorite buried in the middle.")
                .define("meteorCraters", true);
        SALT_FLATS = b.comment("Salt-crusted dry lake beds in deserts that flood when a storm passes over and dry out again.")
                .define("saltFlats", true);
        GLACIERS = b.comment("Glaciers in cold mountains, with hidden crevasses under the snow.")
                .define("glaciers", true);
        TIDAL_FLATS = b.comment("Muddy tidal flats on the coast, under water at high tide, with tide pools to forage in.")
                .define("tidalFlats", true);
        DUNES = b.comment("Dune fields in deserts, shaped by the prevailing wind.")
                .define("dunes", true);
        CHANGING_LANDFORMS = b.comment("Landforms change with the weather: salt flats flood and dry, glaciers melt back in summer",
                        "and grow in winter, and dunes creep downwind in sandstorms.")
                .define("changingLandforms", true);
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
        ModConfigSpec.IntValue value = WEIGHTS.get(type);
        return value == null ? 0 : value.get();
    }

    private WeatherConfig() {}
}
