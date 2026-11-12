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
    public static final ModConfigSpec.BooleanValue DROUGHTS;
    public static final ModConfigSpec.BooleanValue DRYING_PONDS;
    public static final ModConfigSpec.BooleanValue ICE_BREAKUP;
    public static final ModConfigSpec.BooleanValue DESERT_DUST;
    public static final ModConfigSpec.BooleanValue SINKHOLES;
    public static final ModConfigSpec.BooleanValue ROCKFALL;
    public static final ModConfigSpec.BooleanValue PEAT_FIRES;
    public static final ModConfigSpec.BooleanValue ARROYOS;
    public static final ModConfigSpec.BooleanValue CENOTES;
    public static final ModConfigSpec.BooleanValue OASES;
    public static final ModConfigSpec.BooleanValue WATERFALLS;
    public static final ModConfigSpec.BooleanValue BASALT_COLUMNS;
    public static final ModConfigSpec.BooleanValue SEA_STACKS;
    public static final ModConfigSpec.BooleanValue ATOLLS;
    public static final ModConfigSpec.BooleanValue FJORDS;
    public static final ModConfigSpec.BooleanValue ERRATICS;
    public static final ModConfigSpec.BooleanValue PEAT_BOGS;
    public static final ModConfigSpec.BooleanValue SEA_BREEZES;
    public static final ModConfigSpec.BooleanValue MICROBURSTS;
    public static final ModConfigSpec.BooleanValue HABOOBS;
    public static final ModConfigSpec.BooleanValue FIRE_WHIRLS;
    public static final ModConfigSpec.BooleanValue ATMOSPHERIC_RIVERS;
    public static final ModConfigSpec.BooleanValue HEAT_BURSTS;
    public static final ModConfigSpec.BooleanValue RIME_ICE;
    public static final ModConfigSpec.BooleanValue SNOW_ROLLERS;
    public static final ModConfigSpec.BooleanValue LAKE_ICE;
    public static final ModConfigSpec.BooleanValue FERTILE_FLOODS;
    public static final ModConfigSpec.BooleanValue EARTHQUAKES;
    public static final ModConfigSpec.BooleanValue TSUNAMIS;
    public static final ModConfigSpec.BooleanValue COASTAL_EROSION;
    public static final ModConfigSpec.BooleanValue ICEBERGS;
    public static final ModConfigSpec.BooleanValue QUICKSAND;
    public static final ModConfigSpec.BooleanValue TRAVERTINE;
    public static final ModConfigSpec.BooleanValue TUFA_LAKES;
    public static final ModConfigSpec.BooleanValue HOODOOS;
    public static final ModConfigSpec.BooleanValue ROCK_ARCHES;
    public static final ModConfigSpec.BooleanValue SLOT_CANYONS;
    public static final ModConfigSpec.BooleanValue LIMESTONE_PAVEMENT;
    public static final ModConfigSpec.BooleanValue TOWER_KARST;
    public static final ModConfigSpec.BooleanValue OXBOW_LAKES;
    public static final ModConfigSpec.BooleanValue BRAIDED_RIVERS;
    public static final ModConfigSpec.BooleanValue DELTAS;
    public static final ModConfigSpec.BooleanValue CIRQUES;
    public static final ModConfigSpec.BooleanValue KETTLE_LAKES;
    public static final ModConfigSpec.BooleanValue DRUMLINS;
    public static final ModConfigSpec.BooleanValue PERMAFROST;
    public static final ModConfigSpec.BooleanValue CHALK_CLIFFS;
    public static final ModConfigSpec.BooleanValue BARRIER_ISLANDS;
    public static final ModConfigSpec.BooleanValue SALT_MARSHES;
    public static final ModConfigSpec.BooleanValue CINDER_CONES;
    public static final ModConfigSpec.BooleanValue MAARS;
    public static final ModConfigSpec.BooleanValue MUD_VOLCANOES;
    public static final ModConfigSpec.BooleanValue TERMITE_MOUNDS;
    public static final ModConfigSpec.BooleanValue SNOWPACK;
    public static final ModConfigSpec.BooleanValue HUMIDITY;
    public static final ModConfigSpec.BooleanValue CLIMATE_SWINGS;
    public static final ModConfigSpec.BooleanValue WHIRLWINDS;
    public static final ModConfigSpec.BooleanValue FROST_HEAVE;
    public static final ModConfigSpec.BooleanValue CORNICES;
    public static final ModConfigSpec.BooleanValue SASTRUGI;
    public static final ModConfigSpec.BooleanValue RIP_CURRENTS;
    public static final ModConfigSpec.BooleanValue SWELL;
    public static final ModConfigSpec.BooleanValue TIDAL_BORES;
    public static final ModConfigSpec.BooleanValue ALGAL_BLOOMS;
    public static final ModConfigSpec.BooleanValue PUMICE_RAFTS;
    public static final ModConfigSpec.BooleanValue SEASONAL_BEACHES;
    public static final ModConfigSpec.BooleanValue GLACIAL_OUTBURSTS;
    public static final ModConfigSpec.BooleanValue TIANKENG;
    public static final ModConfigSpec.BooleanValue BLUE_HOLES;
    public static final ModConfigSpec.BooleanValue STONE_FORESTS;
    public static final ModConfigSpec.BooleanValue NATURAL_BRIDGES;
    public static final ModConfigSpec.BooleanValue SINKING_STREAMS;
    public static final ModConfigSpec.BooleanValue VOLCANIC_NECKS;
    public static final ModConfigSpec.BooleanValue SPATTER_CONES;
    public static final ModConfigSpec.BooleanValue PILLOW_LAVA;
    public static final ModConfigSpec.BooleanValue OBSIDIAN_FLOWS;
    public static final ModConfigSpec.BooleanValue NUNATAKS;
    public static final ModConfigSpec.BooleanValue ROCK_GLACIERS;
    public static final ModConfigSpec.BooleanValue PALSAS;
    public static final ModConfigSpec.BooleanValue STONE_CIRCLES;
    public static final ModConfigSpec.BooleanValue TORS;
    public static final ModConfigSpec.BooleanValue YARDANGS;
    public static final ModConfigSpec.BooleanValue INSELBERGS;
    public static final ModConfigSpec.BooleanValue MESAS;
    public static final ModConfigSpec.BooleanValue MUSHROOM_ROCKS;
    public static final ModConfigSpec.BooleanValue SAILING_STONES;
    public static final ModConfigSpec.BooleanValue FAIRY_CIRCLES;
    public static final ModConfigSpec.BooleanValue TINAJAS;
    public static final ModConfigSpec.BooleanValue ALLUVIAL_FANS;
    public static final ModConfigSpec.BooleanValue TALUS_CONES;
    public static final ModConfigSpec.BooleanValue HORNS;
    public static final ModConfigSpec.BooleanValue AVALANCHE_CHUTES;
    public static final ModConfigSpec.BooleanValue LANDSLIDE_SCARS;
    public static final ModConfigSpec.BooleanValue PETRIFIED_FORESTS;
    public static final ModConfigSpec.BooleanValue FOSSIL_BEDS;
    public static final ModConfigSpec.BooleanValue SPITS;
    public static final ModConfigSpec.BooleanValue TOMBOLOS;
    public static final ModConfigSpec.BooleanValue SHINGLE_BEACHES;
    public static final ModConfigSpec.BooleanValue RAISED_BEACHES;
    public static final ModConfigSpec.BooleanValue RIAS;
    public static final ModConfigSpec.BooleanValue RIVER_TERRACES;
    public static final ModConfigSpec.BooleanValue RAPIDS;
    public static final ModConfigSpec.BooleanValue GORGES;
    public static final ModConfigSpec.BooleanValue HANGING_VALLEYS;
    public static final ModConfigSpec.BooleanValue NURSE_LOGS;
    public static final ModConfigSpec.BooleanValue PENITENTES;
    public static final ModConfigSpec.BooleanValue GLACIAL_LAKES;
    public static final ModConfigSpec.BooleanValue WHIRLPOOLS;
    public static final ModConfigSpec.BooleanValue SEASONAL_DAY_LENGTH;
    public static final ModConfigSpec.BooleanValue ECLIPSES;
    public static final ModConfigSpec.BooleanValue COMETS;
    public static final ModConfigSpec.BooleanValue BOLTS_FROM_THE_BLUE;
    public static final ModConfigSpec.BooleanValue POLAR_LOWS;
    public static final ModConfigSpec.BooleanValue FROST_QUAKES;
    public static final ModConfigSpec.BooleanValue LAKE_ICE_SHOVES;
    public static final ModConfigSpec.BooleanValue ICE_DISCS;
    public static final ModConfigSpec.BooleanValue SNOW_LOAD;
    public static final ModConfigSpec.BooleanValue BLOWDOWNS;
    public static final ModConfigSpec.BooleanValue HAIL_DRIFTS;
    public static final ModConfigSpec.BooleanValue SOIL_EROSION;
    public static final ModConfigSpec.BooleanValue SEICHES;
    public static final ModConfigSpec.BooleanValue COASTAL_CURRENTS;
    public static final ModConfigSpec.BooleanValue UPWELLING;
    public static final ModConfigSpec.BooleanValue LAHARS;
    public static final ModConfigSpec.BooleanValue PYROCLASTIC_FLOWS;
    public static final ModConfigSpec.BooleanValue LIQUEFACTION;
    public static final ModConfigSpec.BooleanValue SNOW_CAPS;
    public static final ModConfigSpec.BooleanValue SNOW_ALGAE;
    public static final ModConfigSpec.BooleanValue GROUND_BLIZZARDS;
    public static final ModConfigSpec.BooleanValue BLACK_ICE;
    public static final ModConfigSpec.BooleanValue KATABATIC_WINDS;
    public static final ModConfigSpec.BooleanValue FROZEN_WATERFALLS;
    public static final ModConfigSpec.BooleanValue EMBER_SPOTTING;
    public static final ModConfigSpec.BooleanValue HEAT_ISLANDS;
    public static final ModConfigSpec.BooleanValue SALT_SPRAY;
    public static final ModConfigSpec.BooleanValue MARINE_HEATWAVES;
    public static final ModConfigSpec.BooleanValue BOG_BURSTS;
    public static final ModConfigSpec.BooleanValue PLAYA_LAKES;
    public static final ModConfigSpec.BooleanValue FIRE_SUCCESSION;
    public static final ModConfigSpec.BooleanValue FREEZING_SPRAY;
    public static final ModConfigSpec.BooleanValue BRINICLES;
    public static final ModConfigSpec.BooleanValue POLYNYAS;
    public static final ModConfigSpec.BooleanValue FRAZIL_ICE;
    public static final ModConfigSpec.BooleanValue WRACK_LINES;
    public static final ModConfigSpec.BooleanValue RAIN_ON_SNOW;
    public static final ModConfigSpec.BooleanValue DUSTY_SNOWFALL;
    public static final ModConfigSpec.BooleanValue HAIR_ICE;
    public static final ModConfigSpec.BooleanValue FOG_DRIP;
    public static final ModConfigSpec.BooleanValue CAVE_BREATH;
    public static final ModConfigSpec.BooleanValue SUPERBLOOMS;
    public static final ModConfigSpec.BooleanValue FROZEN_BUBBLES;
    public static final ModConfigSpec.BooleanValue MUD_SEASON;
    public static final ModConfigSpec.BooleanValue PUDDLE_ICE;
    public static final ModConfigSpec.BooleanValue INDIAN_SUMMER;
    public static final ModConfigSpec.BooleanValue FALSE_SPRING;
    public static final ModConfigSpec.BooleanValue INSECT_HATCHES;
    public static final ModConfigSpec.BooleanValue TUMBLEWEEDS;
    public static final ModConfigSpec.BooleanValue SNOW_BRIDGES;
    public static final ModConfigSpec.BooleanValue AUFEIS;
    public static final ModConfigSpec.BooleanValue DRIFTWOOD;
    public static final ModConfigSpec.BooleanValue SUPERMOONS;
    public static final ModConfigSpec.BooleanValue AFTERSHOCKS;
    public static final ModConfigSpec.BooleanValue STORM_SIGNS;
    public static final ModConfigSpec.BooleanValue FOXFIRE;
    public static final ModConfigSpec.BooleanValue SUN_CUPS;
    public static final ModConfigSpec.BooleanValue COASTAL_LICHEN;
    public static final ModConfigSpec.BooleanValue BORA;
    public static final ModConfigSpec.BooleanValue SNOW_GHOSTS;
    public static final ModConfigSpec.BooleanValue FROST_CRACKS;
    public static final ModConfigSpec.BooleanValue CANDLE_ICE;
    public static final ModConfigSpec.BooleanValue FAULT_SCARPS;
    public static final ModConfigSpec.BooleanValue SHADY_SNOW;
    public static final ModConfigSpec.BooleanValue MELTWATER_RILLS;
    public static final ModConfigSpec.BooleanValue SAND_RIPPLES;
    public static final ModConfigSpec.BooleanValue WINDOW_FROST;
    public static final ModConfigSpec.BooleanValue CALDERAS;
    public static final ModConfigSpec.BooleanValue ACID_LAKES;
    public static final ModConfigSpec.BooleanValue LAVA_FIELDS;
    public static final ModConfigSpec.BooleanValue DOLINE_FIELDS;
    public static final ModConfigSpec.BooleanValue COCKPIT_KARST;
    public static final ModConfigSpec.BooleanValue POLJES;
    public static final ModConfigSpec.BooleanValue PATERNOSTER_LAKES;
    public static final ModConfigSpec.BooleanValue ROCHES_MOUTONNEES;
    public static final ModConfigSpec.BooleanValue RIFT_VALLEYS;
    public static final ModConfigSpec.BooleanValue AIGUILLES;
    public static final ModConfigSpec.BooleanValue RAINBOW_MOUNTAINS;
    public static final ModConfigSpec.BooleanValue CANYONS;
    public static final ModConfigSpec.BooleanValue GYPSUM_DUNES;
    public static final ModConfigSpec.BooleanValue COPPICE_DUNES;
    public static final ModConfigSpec.BooleanValue SALT_DOMES;
    public static final ModConfigSpec.BooleanValue PINK_LAKES;
    public static final ModConfigSpec.BooleanValue SPOTTED_LAKES;
    public static final ModConfigSpec.BooleanValue ACID_POOLS;
    public static final ModConfigSpec.BooleanValue WAVE_ROCKS;
    public static final ModConfigSpec.BooleanValue CRUSTED_FLATS;
    public static final ModConfigSpec.BooleanValue PARABOLIC_DUNES;
    public static final ModConfigSpec.BooleanValue MOERAKI_BOULDERS;
    public static final ModConfigSpec.BooleanValue COLOURED_BEACHES;
    public static final ModConfigSpec.BooleanValue GHOST_FORESTS;
    public static final ModConfigSpec.BooleanValue STROMATOLITES;
    public static final ModConfigSpec.BooleanValue FAIRY_RINGS;
    public static final ModConfigSpec.BooleanValue VERNAL_POOLS;
    public static final ModConfigSpec.BooleanValue KIPUKAS;
    public static final ModConfigSpec.BooleanValue VOLCANIC_DIKES;
    public static final ModConfigSpec.BooleanValue STEAMING_GROUND;
    public static final ModConfigSpec.BooleanValue HYDROTHERMAL_VENTS;
    public static final ModConfigSpec.BooleanValue BRINE_POOLS;
    public static final ModConfigSpec.BooleanValue KRUMMHOLZ;
    public static final ModConfigSpec.BooleanValue THERMOKARST;
    public static final ModConfigSpec.BooleanValue GOOSENECKS;
    public static final ModConfigSpec.BooleanValue FLOODPLAINS;
    public static final ModConfigSpec.BooleanValue BEAVER_PONDS;
    public static final ModConfigSpec.BooleanValue PATTERNED_FENS;
    public static final ModConfigSpec.BooleanValue CYPRESS_SWAMPS;
    public static final ModConfigSpec.BooleanValue LOESS_CLIFFS;
    public static final ModConfigSpec.BooleanValue MIMA_MOUNDS;
    public static final ModConfigSpec.BooleanValue TAFONI;
    public static final ModConfigSpec.BooleanValue FRINGING_REEFS;
    public static final ModConfigSpec.BooleanValue SOLIFLUCTION;
    public static final ModConfigSpec.BooleanValue HOGBACKS;
    public static final ModConfigSpec.BooleanValue FOLDED_CLIFFS;
    public static final ModConfigSpec.BooleanValue TEPUIS;
    public static final ModConfigSpec.BooleanValue RIFT_FISSURES;
    public static final ModConfigSpec.BooleanValue ANCIENT_SHORELINES;
    public static final ModConfigSpec.BooleanValue HANGING_GARDENS;
    public static final ModConfigSpec.BooleanValue LANDSLIDE_LAKES;
    public static final ModConfigSpec.BooleanValue ICE_TOWERS;
    public static final ModConfigSpec.BooleanValue INVERTED_CHANNELS;
    public static final ModConfigSpec.BooleanValue FAIRY_POOLS;
    public static final ModConfigSpec.BooleanValue CLOUD_FORESTS;
    public static final ModConfigSpec.BooleanValue BLUE_SPRINGS;
    public static final ModConfigSpec.BooleanValue SEA_CLIFF_FALLS;
    public static final ModConfigSpec.BooleanValue COAL_SEAM_FIRES;
    public static final ModConfigSpec.BooleanValue DRY_FALLS;
    public static final ModConfigSpec.BooleanValue SKERRIES;
    public static final ModConfigSpec.BooleanValue DUNE_SLACKS;
    public static final ModConfigSpec.BooleanValue FROST_SHATTERED_SUMMITS;
    public static final ModConfigSpec.BooleanValue CRYSTAL_CAVES;
    public static final ModConfigSpec.BooleanValue GAS_CRATERS;
    public static final ModConfigSpec.BooleanValue BOILING_RIVERS;
    public static final ModConfigSpec.BooleanValue LAVA_DOMES;
    public static final ModConfigSpec.BooleanValue ICE_SHELVES;
    public static final ModConfigSpec.BooleanValue SEAMOUNTS;
    public static final ModConfigSpec.BooleanValue SUBMARINE_CANYONS;
    public static final ModConfigSpec.BooleanValue GLOWWORM_GROTTOES;
    public static final ModConfigSpec.BooleanValue RED_RIVERS;
    public static final ModConfigSpec.BooleanValue STRING_BOGS;
    public static final ModConfigSpec.BooleanValue EARTH_HUMMOCKS;
    public static final ModConfigSpec.BooleanValue QUAKING_BOGS;
    public static final ModConfigSpec.BooleanValue DESERT_PAVEMENTS;
    public static final ModConfigSpec.BooleanValue GLACIER_MICE;
    public static final ModConfigSpec.BooleanValue TERRACETTES;
    public static final ModConfigSpec.BooleanValue BEACH_CUSPS;
    public static final ModConfigSpec.BooleanValue GLACIER_GRIT;

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
        DROUGHTS = b.comment("Droughts: after a dry spell (mostly in summer) the rain stays away for days, grass browns, crops wilt",
                        "and wildfires break out more easily, until the drought breaks with a storm.")
                .define("droughts", true);
        DRYING_PONDS = b.comment("Shallow ponds dry up to cracked mud in a drought (and fill again when the rain returns). Needs mobGriefing.",
                        "Water next to farmland is left alone.")
                .define("dryingPonds", true);
        ICE_BREAKUP = b.comment("In the spring thaw, river ice breaks up into floes that drift downstream; floes that jam up flood the banks.")
                .define("iceBreakup", true);
        DESERT_DUST = b.comment("Sandstorms send a haze of dust far downwind, which settles on snow and colours the rain.")
                .define("desertDust", true);
        SINKHOLES = b.comment("After a long downpour the ground over a cave can give way into a sinkhole (needs mobGriefing).")
                .define("sinkholes", true);
        ROCKFALL = b.comment("Freeze-thaw: on mild spring days and thaws, rocks break off cliffs and pile up as scree below (needs mobGriefing).")
                .define("rockfall", true);
        PEAT_FIRES = b.comment("Lightning can set peat smouldering; peat fires creep slowly underground and send up smoke.")
                .define("peatFires", true);
        SEA_BREEZES = b.comment("Sea breezes blow in off the sea by day and land breezes out to sea at night; valley winds blow uphill by",
                        "day and mountain winds downhill at night (strongest off snow and ice).")
                .define("seaBreezes", true);
        MICROBURSTS = b.comment("Thunderstorms can drop a microburst: a violent downdraft that flattens crops and strips leaves in a",
                        "circle (block damage needs mobGriefing).")
                .define("microbursts", true);
        HABOOBS = b.comment("Desert thunderstorms drive a towering wall of dust out ahead of them.")
                .define("haboobs", true);
        FIRE_WHIRLS = b.comment("Huge wildfires spin up fire whirls and build fire clouds that throw dry lightning downwind.")
                .define("fireWhirls", true);
        ATMOSPHERIC_RIVERS = b.comment("Now and then, mostly in autumn and winter, an atmospheric river soaks a coast with days of heavy rain.")
                .define("atmosphericRivers", true);
        HEAT_BURSTS = b.comment("A dying thunderstorm at night can collapse into a heat burst: a sudden blast of hot, dry wind.")
                .define("heatBursts", true);
        RIME_ICE = b.comment("Freezing fog builds feathers of rime ice on the windward side of trees and rocks (it melts when it warms up).")
                .define("rimeIce", true);
        SNOW_ROLLERS = b.comment("Strong wind over fresh, wet snow rolls it up into snow rollers.")
                .define("snowRollers", true);
        LAKE_ICE = b.comment("Thick lake ice cracks and pushes up into pressure ridges in hard frosts.")
                .define("lakeIce", true);
        FERTILE_FLOODS = b.comment("Floods leave silt behind: crops on ground that was under the flood water grow faster for a few days.")
                .define("fertileFloods", true);
        EARTHQUAKES = b.comment("Rare earthquakes shake the ground, bring rocks down off cliffs and break the odd window (needs",
                        "mobGriefing for block damage).")
                .define("earthquakes", false);
        TSUNAMIS = b.comment("A big earthquake under the sea sends a tsunami onto nearby coasts a minute or two later (needs",
                        "earthquakes and mobGriefing).")
                .define("tsunamis", false);
        COASTAL_EROSION = b.comment("Storm waves slowly wear back natural cliffs and shift sand along beaches (natural blocks only; needs",
                        "mobGriefing).")
                .define("coastalErosion", true);
        ICEBERGS = b.comment("Glaciers that reach the sea break off icebergs, icebergs drift in cold seas, and sea ice breaks up into",
                        "floes in spring.")
                .define("icebergs", true);
        QUICKSAND = b.comment("Patches of quicksand on tidal flats, river deltas, salt marshes, oasis shores and dry washes.")
                .define("quicksand", true);
        SNOWPACK = b.comment("Snow builds up deeper over the winter, deepest in cold, sheltered spots, and melts away in spring;", "rain on deep snow sets off floods.")
                .define("snowpack", true);
        HUMIDITY = b.comment("Humid air makes hot days feel hotter, and muggy nights stay warm.")
                .define("humidity", true);
        CLIMATE_SWINGS = b.comment("Some years run wet and others dry, shifting the odds of droughts, monsoons and hurricanes for a few seasons.")
                .define("climateSwings", true);
        WHIRLWINDS = b.comment("Landspouts and gustnadoes along storm outflows, steam devils over unfrozen lakes in bitter cold, and snow", "devils on sunny, windy snowfields.")
                .define("whirlwinds", true);
        FROST_HEAVE = b.comment("Needle ice pushes up through bare soil on frosty mornings (and can heave young crops out of farmland), and", "frost flowers grow on new ice.")
                .define("frostHeave", true);
        CORNICES = b.comment("Wind-blown snow builds overhanging cornices on ridges, which can break off underfoot and set off avalanches", "(needs mobGriefing).")
                .define("cornices", true);
        SASTRUGI = b.comment("Wind carves open snowfields into ridges and furrows.")
                .define("sastrugi", true);
        RIP_CURRENTS = b.comment("Rip currents pull swimmers out from beaches. Swim along the shore to get out of one.")
                .define("ripCurrents", true);
        SWELL = b.comment("Swell runs ahead of hurricanes, rogue waves now and then swamp boats in rough seas, and storms pile sea foam", "on beaches.")
                .define("swell", true);
        TIDAL_BORES = b.comment("At spring tides, a tidal bore surges up rivers near the sea.")
                .define("tidalBores", true);
        ALGAL_BLOOMS = b.comment("Hot, still summers set off algal blooms (red tides) in warm seas: the water turns rusty and the fish stay away.")
                .define("algalBlooms", true);
        PUMICE_RAFTS = b.comment("Rafts of floating pumice drift on the sea after big undersea earthquakes.")
                .define("pumiceRafts", true);
        SEASONAL_BEACHES = b.comment("Winter storms strip sand off beaches and calm summer weather builds it back up (natural sand only; needs", "mobGriefing).")
                .define("seasonalBeaches", true);
        GLACIAL_OUTBURSTS = b.comment("Glacial lakes can burst through their moraine dams in summer, sending a flood down the valley.")
                .define("glacialOutbursts", true);
        SEASONAL_DAY_LENGTH = b.comment("Days are longer in summer and shorter in winter, with the nights the other way round (with this mod's own", "seasons on).")
                .define("seasonalDayLength", true);
        ECLIPSES = b.comment("Rare solar eclipses darken the midday sky, and total lunar eclipses turn the full moon copper-red. The", "weather radio warns of them the day before.")
                .define("eclipses", true);
        COMETS = b.comment("Some years a bright comet hangs low in the evening sky for a week or so.")
                .define("comets", true);
        BOLTS_FROM_THE_BLUE = b.comment("Lightning can strike well out from under a thunderstorm, from a clear sky, and splits the trees it hits.")
                .define("boltsFromTheBlue", true);
        POLAR_LOWS = b.comment("Small, fierce, hurricane-like snowstorms over cold seas in winter.")
                .define("polarLows", true);
        FROST_QUAKES = b.comment("Frost quakes: loud booms as wet ground freezes suddenly in a hard cold snap.")
                .define("frostQuakes", true);
        LAKE_ICE_SHOVES = b.comment("Waves spurting through shore ice build little ice volcanoes, and strong wind shoves lake ice up onto the", "shore (needs mobGriefing).")
                .define("lakeIceShoves", true);
        ICE_DISCS = b.comment("Ice discs spin slowly in cold rivers, and pancake ice forms on cold seas.")
                .define("iceDiscs", true);
        SNOW_LOAD = b.comment("Heavy snow piling up on trees breaks branches off (needs mobGriefing).")
                .define("snowLoad", true);
        BLOWDOWNS = b.comment("Hurricanes and derechos can uproot whole trees (needs mobGriefing).")
                .define("blowdowns", true);
        HAIL_DRIFTS = b.comment("Hail piles up in drifts before it melts, and on warm days fog rises off it.")
                .define("hailDrifts", true);
        SOIL_EROSION = b.comment("Downpours wash bare soil and farmland down steep slopes; terraced or planted slopes are safe (needs", "mobGriefing).")
                .define("soilErosion", true);
        SEICHES = b.comment("After a strong wind, the water in big lakes sloshes back and forth for a while.")
                .define("seiches", true);
        COASTAL_CURRENTS = b.comment("Currents along the coast carry boats, swimmers and floating things along the shore.")
                .define("coastalCurrents", true);
        UPWELLING = b.comment("Cold water wells up off some coasts: cooler air, more sea fog and better fishing.")
                .define("upwelling", true);
        LAHARS = b.comment("Heavy rain on a volcano's ash-covered slopes sends mudflows (lahars) down its valleys (needs mobGriefing).")
                .define("lahars", true);
        PYROCLASTIC_FLOWS = b.comment("Big eruptions send scalding avalanches of ash and gas pouring down the volcano's slopes.")
                .define("pyroclasticFlows", true);
        LIQUEFACTION = b.comment("Strong earthquakes turn wet sandy ground to quicksand for a while and throw up sand boils (needs mobGriefing).")
                .define("liquefaction", true);
        SNOW_CAPS = b.comment("The snowline on mountains creeps down in autumn and winter and retreats in spring and summer.")
                .define("seasonalSnowCaps", true);
        SNOW_ALGAE = b.comment("Old summer snowfields high in the mountains turn pink with snow algae (watermelon snow).")
                .define("snowAlgae", true);
        GROUND_BLIZZARDS = b.comment("Strong wind lifts loose snow off the ground into a blinding ground blizzard, even under a clear sky.")
                .define("groundBlizzards", true);
        BLACK_ICE = b.comment("After a thaw, a hard frost glazes stone and paths with slippery black ice by dawn.")
                .define("blackIce", true);
        KATABATIC_WINDS = b.comment("Bitter gales pour down off glaciers and ice sheets at night.")
                .define("katabaticWinds", true);
        FROZEN_WATERFALLS = b.comment("Waterfalls freeze into hanging curtains of ice in hard cold, and thaw again when it warms up.")
                .define("frozenWaterfalls", true);
        EMBER_SPOTTING = b.comment("Big wildfires throw embers ahead on the wind and start new fires downwind (respects doFireTick and mobGriefing).")
                .define("emberSpotting", true);
        HEAT_ISLANDS = b.comment("Villages are a few degrees warmer than the country round them, with less frost.")
                .define("heatIslands", true);
        SALT_SPRAY = b.comment("Storm spray scorches the leaves off trees along the coast (needs mobGriefing).")
                .define("saltSpray", true);
        MARINE_HEATWAVES = b.comment("In a long, hot summer the sea warms: fish move away and coral bleaches (needs mobGriefing).")
                .define("marineHeatwaves", true);
        BOG_BURSTS = b.comment("After long, heavy rain a waterlogged peat bog can burst and slide downhill as a river of peat (needs mobGriefing).")
                .define("bogBursts", true);
        PLAYA_LAKES = b.comment("Dry desert lakebeds flood into shallow lakes after heavy rain and slowly dry out again.")
                .define("playaLakes", true);
        FIRE_SUCCESSION = b.comment("Wildfires leave charred ground that recovers over the seasons: fireweed first, then grass and young trees.")
                .define("fireSuccession", true);
        FREEZING_SPRAY = b.comment("Gales over a freezing sea glaze the shore, docks and boats with ice.")
                .define("freezingSpray", true);
        BRINICLES = b.comment("Under sea ice in bitter cold, icy fingers (brinicles) grow down to the sea floor.")
                .define("brinicles", true);
        POLYNYAS = b.comment("Some patches of winter sea ice never freeze over: open water that steams in the cold.")
                .define("polynyas", true);
        FRAZIL_ICE = b.comment("In bitter cold, slushy frazil ice clogs fast rivers and anchor ice coats their beds.")
                .define("frazilIce", true);
        WRACK_LINES = b.comment("Storms wash kelp and seagrass up onto beaches along the high-tide line.")
                .define("wrackLines", true);
        RAIN_ON_SNOW = b.comment("Rain falling on snow soaks it into slush, which freezes into a slippery icy crust overnight.")
                .define("rainOnSnow", true);
        DUSTY_SNOWFALL = b.comment("Snow that falls while desert dust is still in the air comes down tinted, and dusty snow melts faster in the sun.")
                .define("dustySnowfall", true);
        HAIR_ICE = b.comment("On damp nights just below freezing, fine white hair ice grows on fallen logs, and melts away by mid-morning.")
                .define("hairIce", true);
        FOG_DRIP = b.comment("In fog, trees comb water out of the air: crops and farmland under them stay watered.")
                .define("fogDrip", true);
        CAVE_BREATH = b.comment("Cave mouths breathe: a cool draught blows out of them on hot days (and they steam in the cold).")
                .define("caveBreath", true);
        SUPERBLOOMS = b.comment("After an unusually wet winter, deserts burst into wildflowers in spring.")
                .define("superblooms", true);
        FROZEN_BUBBLES = b.comment("Bubbles of marsh gas freeze into the ice of boggy lakes in white stacks.")
                .define("frozenBubbles", true);
        MUD_SEASON = b.comment("In the spring thaw and long rains, bare ground and dirt paths turn to sticky mud that dries out again in summer.")
                .define("mudSeason", true);
        PUDDLE_ICE = b.comment("Puddles freeze over on frosty mornings, and the ice skin cracks underfoot.")
                .define("puddleIce", true);
        INDIAN_SUMMER = b.comment("Some autumns, after the first frosts, a spell of warm, still, hazy days: an Indian summer.")
                .define("indianSummer", true);
        FALSE_SPRING = b.comment("Some winters, a warm spell wakes everything early, and a hard frost follows: a false spring.")
                .define("falseSpring", true);
        INSECT_HATCHES = b.comment("Mayfly hatches on calm spring and summer evenings bring the fish rising (better fishing in rivers then).")
                .define("insectHatches", true);
        TUMBLEWEEDS = b.comment("Tumbleweeds break loose and bounce across deserts, badlands and dry plains on windy days.")
                .define("tumbleweeds", true);
        SNOW_BRIDGES = b.comment("Snow bridges build over narrow streams in winter (and hide crevasses on glaciers). They give way under you in a thaw,",
                        "or if you run or jump onto them.")
                .define("snowBridges", true);
        AUFEIS = b.comment("In hard winters, water seeping from springs and stream banks freezes layer on layer into thick sheets of ice (aufeis).")
                .define("aufeis", true);
        DRIFTWOOD = b.comment("Storms wash weathered driftwood up onto beaches.")
                .define("driftwood", true);
        SUPERMOONS = b.comment("A few full moons a year are supermoons, and their spring tides run higher than usual.")
                .define("supermoons", true);
        AFTERSHOCKS = b.comment("After a big earthquake, smaller aftershocks follow over the next few days.")
                .define("aftershocks", true);
        STORM_SIGNS = b.comment("Livestock bunch together as a storm comes on (and the swallows fly low).")
                .define("stormSigns", true);
        FOXFIRE = b.comment("Rotting logs in damp woods glow faintly green at night with foxfire.")
                .define("foxfire", true);
        SUN_CUPS = b.comment("In summer sun, high snowfields melt into a pitted surface of cup-shaped hollows.")
                .define("sunCups", true);
        COASTAL_LICHEN = b.comment("Rocky shores are banded with black and orange lichen above the waterline (new chunks only).")
                .define("coastalLichen", true);
        BORA = b.comment("In winter, on coasts below mountains, icy gusts pour down the slopes to the sea: a bora (the cold cousin of the föhn).")
                .define("bora", true);
        SNOW_GHOSTS = b.comment("On high, cold ridges in winter, rime and snow plaster the conifers into white shapes (snow ghosts) until the thaw.")
                .define("snowGhosts", true);
        FROST_CRACKS = b.comment("In a hard freeze at night, tree trunks split with a crack like a gunshot.")
                .define("frostCracks", true);
        CANDLE_ICE = b.comment("In the spring thaw, lake ice rots into long crystals that the wind piles ashore, tinkling.")
                .define("candleIce", true);
        FAULT_SCARPS = b.comment("A big earthquake can tear a fresh crack across the land, the ground heaved up along one side.",
                        "Only on natural ground, away from anything built; needs mobGriefing.")
                .define("faultScarps", true);
        SHADY_SNOW = b.comment("In a sunny thaw, snow melts first off slopes facing the sun (south) and lingers on shady north-facing",
                        "slopes and in the shadow of trees and cliffs.")
                .define("shadySnow", true);
        MELTWATER_RILLS = b.comment("In a fast thaw, little streams of meltwater trickle down the hillsides below the melting snow,",
                        "drying up once the snow above them has gone.")
                .define("meltwaterRills", true);
        SAND_RIPPLES = b.comment("The wind combs dry desert and beach sand into ripples running across it; rain smooths them away.")
                .define("sandRipples", true);
        WINDOW_FROST = b.comment("On bitter nights, ferns of frost grow over windows of plain glass, and clear when it warms up.",
                        "The glass always turns back into the same glass block or pane.")
                .define("windowFrost", true);
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
        ARROYOS = b.comment("Dry, sandy riverbeds winding across deserts and badlands, which flash-flood in a downpour.")
                .define("arroyos", true);
        CENOTES = b.comment("Cenotes: deep, round, water-filled sinkholes in jungles, hung with vines.")
                .define("cenotes", true);
        OASES = b.comment("Desert oases: a pool ringed with grass, reeds and palm trees.")
                .define("oases", true);
        WATERFALLS = b.comment("Waterfalls off cliffs and steep slopes, with a plunge pool at the foot.")
                .define("waterfalls", true);
        BASALT_COLUMNS = b.comment("Coasts of columnar basalt stepping down into the sea.")
                .define("basaltColumns", true);
        SEA_STACKS = b.comment("Sea stacks and natural arches standing off rocky coasts.")
                .define("seaStacks", true);
        ATOLLS = b.comment("Coral atolls in warm seas: a ring of sandy islets with palms around a shallow lagoon.")
                .define("atolls", true);
        FJORDS = b.comment("Fjords: long, deep, steep-walled inlets of the sea cut into cold coasts.")
                .define("fjords", true);
        ERRATICS = b.comment("Glacial leftovers: big erratic boulders and long moraine ridges (glaciers get a moraine at their foot too).")
                .define("erratics", true);
        PEAT_BOGS = b.comment("Peat bogs: wet, mossy flats over deep peat (a slow-burning fuel), dotted with dark pools.")
                .define("peatBogs", true);
        TRAVERTINE = b.comment("Travertine terraces: white, stepped pools of warm mineral water on hillsides.")
                .define("travertine", true);
        TUFA_LAKES = b.comment("Salty lakes with pale tufa towers standing in the shallows.")
                .define("tufaLakes", true);
        HOODOOS = b.comment("Hoodoos: banded rock spires with caprocks in the badlands and deserts.")
                .define("hoodoos", true);
        ROCK_ARCHES = b.comment("Natural rock arches standing in deserts and badlands.")
                .define("rockArches", true);
        SLOT_CANYONS = b.comment("Slot canyons: deep, narrow, twisting sandstone canyons (deadly in a flash flood).")
                .define("slotCanyons", true);
        LIMESTONE_PAVEMENT = b.comment("Limestone pavements: bare rock cracked into slabs, with ferns in the cracks.")
                .define("limestonePavement", true);
        TOWER_KARST = b.comment("Tower karst: steep limestone towers rising out of the jungle.")
                .define("towerKarst", true);
        OXBOW_LAKES = b.comment("Oxbow lakes: crescent-shaped lakes left by rivers changing course.")
                .define("oxbowLakes", true);
        BRAIDED_RIVERS = b.comment("Braided rivers: shallow channels weaving across wide gravel plains in cold lands.")
                .define("braidedRivers", true);
        DELTAS = b.comment("River deltas fanning out into marsh and channels where rivers meet the sea.")
                .define("deltas", true);
        CIRQUES = b.comment("Cirques: bowls carved into mountainsides by old glaciers, holding a small lake.")
                .define("cirques", true);
        KETTLE_LAKES = b.comment("Kettle lakes: deep, round ponds left where buried blocks of glacier ice melted.")
                .define("kettleLakes", true);
        DRUMLINS = b.comment("Drumlins and eskers: streamlined glacial hills and winding gravel ridges.")
                .define("drumlins", true);
        PERMAFROST = b.comment("Frozen ground: polygon-patterned tundra, ice-cored pingos and thaw lakes.")
                .define("permafrost", true);
        CHALK_CLIFFS = b.comment("White chalk cliffs with sea caves and a blowhole that spouts when the waves surge in.")
                .define("chalkCliffs", true);
        BARRIER_ISLANDS = b.comment("Barrier islands: long sandy islands with dunes, with a lagoon behind them.")
                .define("barrierIslands", true);
        SALT_MARSHES = b.comment("Salt marshes: flat coastal marsh threaded with tidal creeks, flooded at high tide.")
                .define("saltMarshes", true);
        CINDER_CONES = b.comment("Cinder cones: small, steep volcanic cones of black and red scoria.")
                .define("cinderCones", true);
        MAARS = b.comment("Maars: broad, flat-floored explosion craters with a lake, ringed by tuff.")
                .define("maars", true);
        MUD_VOLCANOES = b.comment("Mud volcanoes: little cones of grey mud with bubbling pools on top.")
                .define("mudVolcanoes", true);
        TERMITE_MOUNDS = b.comment("Termite mounds: tall earthen spires dotting savannas.")
                .define("termiteMounds", true);
        TIANKENG = b.comment("Tiankeng: giant, sheer-walled sinkholes with a forest growing on the floor.")
                .define("tiankeng", true);
        BLUE_HOLES = b.comment("Blue holes: deep, round sinkholes in shallow tropical seas.")
                .define("blueHoles", true);
        STONE_FORESTS = b.comment("Stone forests: fields of tall, sharp limestone pinnacles.")
                .define("stoneForests", true);
        NATURAL_BRIDGES = b.comment("Natural bridges: arches of rock spanning a stream.")
                .define("naturalBridges", true);
        SINKING_STREAMS = b.comment("Sinking streams that vanish into a cave mouth and come out again at a spring further down.")
                .define("sinkingStreams", true);
        VOLCANIC_NECKS = b.comment("Volcanic necks: towers of columnar basalt left standing when an old volcano wore away.")
                .define("volcanicNecks", true);
        SPATTER_CONES = b.comment("Fissure vents lined with a chain of small spatter cones, with a young lava field round them.")
                .define("spatterCones", true);
        PILLOW_LAVA = b.comment("Pillow lava: mounds of rounded basalt pillows on the sea bed.")
                .define("pillowLava", true);
        OBSIDIAN_FLOWS = b.comment("Obsidian flows: thick, steep-fronted tongues of volcanic glass.")
                .define("obsidianFlows", true);
        NUNATAKS = b.comment("Nunataks: rocky peaks poking up through an ice sheet.")
                .define("nunataks", true);
        ROCK_GLACIERS = b.comment("Rock glaciers: tongues of ice-cored rubble creeping down mountain valleys.")
                .define("rockGlaciers", true);
        PALSAS = b.comment("Palsas: frozen peat mounds rising out of northern bogs.")
                .define("palsas", true);
        STONE_CIRCLES = b.comment("Stone circles: frost-sorted rings of stones on cold ground.")
                .define("stoneCircles", true);
        TORS = b.comment("Tors: stacks of weathered granite on hilltops, with boulder fields round them.")
                .define("tors", true);
        YARDANGS = b.comment("Yardangs: wind-carved ridges like upturned boats in the desert, on a pavement of stones.")
                .define("yardangs", true);
        INSELBERGS = b.comment("Inselbergs: huge, isolated domes of rock rising out of the plains.")
                .define("inselbergs", true);
        MESAS = b.comment("Mesas and buttes: flat-topped hills with banded cliffs.")
                .define("mesas", true);
        MUSHROOM_ROCKS = b.comment("Mushroom rocks: wide caps of rock balanced on wind-worn stems.")
                .define("mushroomRocks", true);
        SAILING_STONES = b.comment("Sailing stones: boulders on dry lake beds, each at the end of a long track.")
                .define("sailingStones", true);
        FAIRY_CIRCLES = b.comment("Fairy circles: evenly spaced bare rings in dry grassland.")
                .define("fairyCircles", true);
        TINAJAS = b.comment("Tinajas: potholes in bare desert rock that fill with water after rain.")
                .define("tinajas", true);
        ALLUVIAL_FANS = b.comment("Alluvial fans: fans of gravel spreading out where a valley meets the plain.")
                .define("alluvialFans", true);
        TALUS_CONES = b.comment("Talus cones: cones of scree at the foot of cliffs.")
                .define("talusCones", true);
        HORNS = b.comment("Horns and aretes: pyramidal peaks with knife-edge ridges.")
                .define("horns", true);
        AVALANCHE_CHUTES = b.comment("Avalanche chutes: strips swept clear of trees down steep, snowy slopes.")
                .define("avalancheChutes", true);
        LANDSLIDE_SCARS = b.comment("Landslide scars: bare scoops in hillsides with a hummocky heap of debris below.")
                .define("landslideScars", true);
        PETRIFIED_FORESTS = b.comment("Petrified forests: fallen logs turned to stone in the badlands.")
                .define("petrifiedForests", true);
        FOSSIL_BEDS = b.comment("Fossil beds: eroding banks with great bones showing in them.")
                .define("fossilBeds", true);
        SPITS = b.comment("Spits: long, curving fingers of sand growing out from the shore.")
                .define("spits", true);
        TOMBOLOS = b.comment("Tombolos: sandbars tying a small island to the shore.")
                .define("tombolos", true);
        SHINGLE_BEACHES = b.comment("Shingle beaches: pebble beaches stepped with storm ridges.")
                .define("shingleBeaches", true);
        RAISED_BEACHES = b.comment("Raised beaches: old beaches left as flat terraces above today's shore.")
                .define("raisedBeaches", true);
        RIAS = b.comment("Rias: drowned river valleys winding inland as branching arms of the sea.")
                .define("rias", true);
        RIVER_TERRACES = b.comment("River terraces: flat benches stepping up from a river.")
                .define("riverTerraces", true);
        RAPIDS = b.comment("Rapids: rocky stretches of river with boulders and potholes.")
                .define("rapids", true);
        GORGES = b.comment("Gorges: deep, narrow, rock-walled river valleys.")
                .define("gorges", true);
        HANGING_VALLEYS = b.comment("Hanging valleys: glacial troughs with a waterfall spilling from a side valley high on the wall.")
                .define("hangingValleys", true);
        NURSE_LOGS = b.comment("Nurse logs and root mounds: fallen, mossy trees with saplings growing on them.")
                .define("nurseLogs", true);
        PENITENTES = b.comment("Penitentes: fields of tall snow spikes on high, dry peaks.")
                .define("penitentes", true);
        GLACIAL_LAKES = b.comment("Glacial lakes: turquoise mountain lakes dammed by moraines.")
                .define("glacialLakes", true);
        WHIRLPOOLS = b.comment("Whirlpools: tidal whirlpools in narrow sea straits.")
                .define("whirlpools", true);
        CALDERAS = b.comment("Calderas: huge collapsed volcanoes holding a lake round a new central dome.")
                .define("calderas", true);
        ACID_LAKES = b.comment("Acid crater lakes: milky turquoise lakes in steaming craters, with blue sulfur fire at night.")
                .define("acidLakes", true);
        LAVA_FIELDS = b.comment("Young lava fields with tumuli, hornitos, lava tube skylights and lava tree moulds.")
                .define("lavaFields", true);
        DOLINE_FIELDS = b.comment("Doline fields: ground pocked all over with small sinkholes.")
                .define("dolineFields", true);
        COCKPIT_KARST = b.comment("Cockpit karst: crowded cone-shaped hills with deep hollows between them.")
                .define("cockpitKarst", true);
        POLJES = b.comment("Poljes: big flat-floored karst basins that flood into seasonal lakes.")
                .define("poljes", true);
        PATERNOSTER_LAKES = b.comment("Paternoster lakes: a chain of lakes stepping down a glacial valley.")
                .define("paternosterLakes", true);
        ROCHES_MOUTONNEES = b.comment("Roches moutonnees: rock knobs smoothed by ice on one side and plucked on the other.")
                .define("rochesMoutonnees", true);
        RIFT_VALLEYS = b.comment("Rift valleys: long, straight, steep-walled valleys holding a long, deep lake.")
                .define("riftValleys", true);
        AIGUILLES = b.comment("Aiguilles: thin rock needles along high mountain ridges.")
                .define("aiguilles", true);
        RAINBOW_MOUNTAINS = b.comment("Rainbow mountains: hills striped in bands of brightly coloured rock.")
                .define("rainbowMountains", true);
        CANYONS = b.comment("Canyons: broad, layered canyons stepping down in rock terraces to a river.")
                .define("canyons", true);
        GYPSUM_DUNES = b.comment("Gypsum dune fields: dazzling white dunes beside a dry lake.")
                .define("gypsumDunes", true);
        COPPICE_DUNES = b.comment("Coppice dunes: mounds of sand heaped round desert shrubs.")
                .define("coppiceDunes", true);
        SALT_DOMES = b.comment("Salt domes: salt pushing up through desert rock and creeping out as salt glaciers.")
                .define("saltDomes", true);
        PINK_LAKES = b.comment("Pink lakes: salt lakes coloured pink by salt-loving algae.")
                .define("pinkLakes", true);
        SPOTTED_LAKES = b.comment("Spotted lakes: dry lake beds dotted with pools of different mineral colours.")
                .define("spottedLakes", true);
        ACID_POOLS = b.comment("Acid pools: hot, acid springs in yellow and green, crusted with salt and sulfur.")
                .define("acidPools", true);
        WAVE_ROCKS = b.comment("Wave rocks: swirling, striped sandstone like frozen waves.")
                .define("waveRocks", true);
        CRUSTED_FLATS = b.comment("Crusted desert flats with dark soil crust and varnished rock faces.")
                .define("crustedFlats", true);
        PARABOLIC_DUNES = b.comment("Parabolic dunes: coastal dunes with blowouts, their arms pointing upwind.")
                .define("parabolicDunes", true);
        MOERAKI_BOULDERS = b.comment("Moeraki boulders: great stone spheres lying on a beach.")
                .define("moerakiBoulders", true);
        COLOURED_BEACHES = b.comment("Shell beaches, green olivine sand beaches and pink sand beaches.")
                .define("colouredBeaches", true);
        GHOST_FORESTS = b.comment("Ghost forests: dead trees standing in the water where a coast has sunk.")
                .define("ghostForests", true);
        STROMATOLITES = b.comment("Stromatolites: living rock mounds in salty tropical shallows.")
                .define("stromatolites", true);
        FAIRY_RINGS = b.comment("Fairy rings: rings of mushrooms in grassland and woods.")
                .define("fairyRings", true);
        VERNAL_POOLS = b.comment("Vernal pools: woodland ponds that fill in spring and dry out by summer.")
                .define("vernalPools", true);
        KIPUKAS = b.comment("Kipukas: islands of old forest left standing in a sea of younger lava.")
                .define("kipukas", true);
        VOLCANIC_DIKES = b.comment("Volcanic dikes: long walls of dark rock radiating out across the land.")
                .define("volcanicDikes", true);
        STEAMING_GROUND = b.comment("Steaming ground: warm, bare geothermal patches where snow never settles.")
                .define("steamingGround", true);
        HYDROTHERMAL_VENTS = b.comment("Hydrothermal vents: smoking black chimneys on the deep sea floor.")
                .define("hydrothermalVents", true);
        BRINE_POOLS = b.comment("Brine pools: lakes of heavy, salty water lying on the sea floor.")
                .define("brinePools", true);
        KRUMMHOLZ = b.comment("Krummholz: stunted, wind-bent trees at the treeline.")
                .define("krummholz", true);
        THERMOKARST = b.comment("Thermokarst slumps: hillsides collapsing where the permafrost under them thaws.")
                .define("thermokarst", true);
        GOOSENECKS = b.comment("Goosenecks: a river winding in deep loops cut down into the rock.")
                .define("goosenecks", true);
        FLOODPLAINS = b.comment("Floodplains: flat river meadows with natural levees and swampy backwaters.")
                .define("floodplains", true);
        BEAVER_PONDS = b.comment("Beaver ponds: streams dammed with sticks and mud into ponds full of drowned trees.")
                .define("beaverPonds", true);
        PATTERNED_FENS = b.comment("Patterned fens: bogs striped with ridges and pools across the slope.")
                .define("patternedFens", true);
        CYPRESS_SWAMPS = b.comment("Cypress swamps: big trees standing in still water with their knees poking up round them.")
                .define("cypressSwamps", true);
        LOESS_CLIFFS = b.comment("Loess cliffs: plateaus of yellow wind-blown dust cut into sheer cliffs and deep gullies.")
                .define("loessCliffs", true);
        MIMA_MOUNDS = b.comment("Mima mounds: fields of low, evenly spaced grassy mounds.")
                .define("mimaMounds", true);
        TAFONI = b.comment("Tafoni: sea cliffs weathered into honeycombs of hollows.")
                .define("tafoni", true);
        FRINGING_REEFS = b.comment("Fringing reefs: coral reefs along warm coasts, cut by spurs and grooves.")
                .define("fringingReefs", true);
        SOLIFLUCTION = b.comment("Solifluction lobes: tundra slopes creeping downhill in tongues of turf.")
                .define("solifluctionLobes", true);
        HOGBACKS = b.comment("Hogbacks and flatirons: tilted rock layers standing up as sawtooth ridges and triangular slabs.")
                .define("hogbacks", true);
        FOLDED_CLIFFS = b.comment("Folded cliffs: rock layers bent into arches and troughs, laid bare in a cliff face.")
                .define("foldedCliffs", true);
        TEPUIS = b.comment("Tepuis: sheer-sided table mountains with long waterfalls pouring off them.")
                .define("tepuis", true);
        RIFT_FISSURES = b.comment("Rift fissures: long cracks in the rock, filled with crystal-clear water.")
                .define("riftFissures", true);
        ANCIENT_SHORELINES = b.comment("Ancient shorelines: steps cut by a long-gone lake, ringing its dry basin.")
                .define("ancientShorelines", true);
        HANGING_GARDENS = b.comment("Hanging gardens: seeps on canyon walls, green with ferns and moss.")
                .define("hangingGardens", true);
        LANDSLIDE_LAKES = b.comment("Landslide lakes: valleys dammed by an old landslide, with a lake behind it.")
                .define("landslideLakes", true);
        ICE_TOWERS = b.comment("Fumarole ice towers: hollow chimneys of ice built up over steam vents on snowy peaks.")
                .define("fumaroleIceTowers", true);
        INVERTED_CHANNELS = b.comment("Inverted channels: old riverbeds left standing as winding ridges in the desert.")
                .define("invertedChannels", true);
        FAIRY_POOLS = b.comment("Fairy pools: a chain of clear blue plunge pools and little falls down a mountain stream.")
                .define("fairyPools", true);
        CLOUD_FORESTS = b.comment("Cloud forests: misty jungle mountainsides with trees thick with moss, ferns and vines.")
                .define("cloudForests", true);
        BLUE_SPRINGS = b.comment("Blue springs: deep, vivid blue springs welling out of a cave at the foot of a cliff.")
                .define("blueSprings", true);
        SEA_CLIFF_FALLS = b.comment("Sea-cliff waterfalls: streams pouring straight off coastal cliffs onto the beach or into the sea.")
                .define("seaCliffFalls", true);
        COAL_SEAM_FIRES = b.comment("Burning coal seams: ground baked red over a coal seam that has smouldered for centuries, smoking from its cracks.")
                .define("coalSeamFires", true);
        DRY_FALLS = b.comment("Dry falls: the dry horseshoe cliff of a giant ancient waterfall, with lakes in its old plunge pools.")
                .define("dryFalls", true);
        SKERRIES = b.comment("Skerries: swarms of low, ice-smoothed rocky islets off cold coasts.")
                .define("skerries", true);
        DUNE_SLACKS = b.comment("Dune slacks: damp hollows between coastal dunes, with shallow pools, reeds and wildflowers.")
                .define("duneSlacks", true);
        FROST_SHATTERED_SUMMITS = b.comment("Frost-shattered summits: high, flat mountaintops strewn with boulders split by frost.")
                .define("frostShatteredSummits", true);
        CRYSTAL_CAVES = b.comment("Crystal caves: caverns full of giant, glassy selenite crystals.")
                .define("crystalCaves", true);
        GAS_CRATERS = b.comment("Gas craters: collapsed craters where natural gas has burned for decades (contained fire on bare rock).")
                .define("gasCraters", true);
        BOILING_RIVERS = b.comment("Boiling rivers: jungle rivers fed by hot springs, steaming and scalding hot.")
                .define("boilingRivers", true);
        LAVA_DOMES = b.comment("Lava domes: steep, blocky domes of dark lava in volcanic country, steaming from their cracks.")
                .define("lavaDomes", true);
        ICE_SHELVES = b.comment("Ice shelves: towering cliffs of ice along frozen coasts.")
                .define("iceShelves", true);
        SEAMOUNTS = b.comment("Seamounts: undersea mountains rising from the deep ocean almost to the surface.")
                .define("seamounts", true);
        SUBMARINE_CANYONS = b.comment("Submarine canyons: deep canyons cut into the sea floor off the coast.")
                .define("submarineCanyons", true);
        GLOWWORM_GROTTOES = b.comment("Glowworm grottoes: caves over underground streams, their ceilings speckled with blue points of light.")
                .define("glowwormGrottoes", true);
        RED_RIVERS = b.comment("Red rivers: acidic, iron-rich streams running blood-red over orange-stained rock.")
                .define("redRivers", true);
        STRING_BOGS = b.comment("String bogs: long curving ridges of peat with narrow pools between them, in flat northern country.")
                .define("stringBogs", true);
        EARTH_HUMMOCKS = b.comment("Earth hummocks: cold, wet grassland heaved by frost into countless little grassy mounds.")
                .define("earthHummocks", true);
        QUAKING_BOGS = b.comment("Quaking bogs: floating mats of moss at the edges of bog pools, which wobble and give way underfoot.")
                .define("quakingBogs", true);
        DESERT_PAVEMENTS = b.comment("Desert pavements: gibber plains of dark, wind-polished pebbles, with wind-carved rocks.")
                .define("desertPavements", true);
        GLACIER_MICE = b.comment("Glacier mice: balls of moss slowly rolling over the lower ice of glaciers.")
                .define("glacierMice", true);
        TERRACETTES = b.comment("Terracettes: narrow, step-like tracks running along steep grassy hillsides.")
                .define("terracettes", true);
        BEACH_CUSPS = b.comment("Beach cusps: evenly spaced crescent scallops along the edge of sandy beaches.")
                .define("beachCusps", true);
        GLACIER_GRIT = b.comment("Cryoconite holes and dirt cones: dark grit on glacier ice that melts little round holes into it,",
                        "or shields cones of ice from the sun.")
                .define("glacierGrit", true);
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
