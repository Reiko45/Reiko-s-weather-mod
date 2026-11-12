package com.reiko.weather.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only visual and audio settings. */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue PARTICLE_DENSITY;
    public static final ModConfigSpec.BooleanValue FOG;
    public static final ModConfigSpec.BooleanValue SKY_EFFECTS;
    public static final ModConfigSpec.BooleanValue RAINBOWS;
    public static final ModConfigSpec.BooleanValue AURORAS;
    public static final ModConfigSpec.BooleanValue SOUNDS;
    public static final ModConfigSpec.BooleanValue STORM_CLOUDS;
    public static final ModConfigSpec.BooleanValue WET_GROUND;
    public static final ModConfigSpec.BooleanValue MIST;
    public static final ModConfigSpec.BooleanValue HEAT_LIGHTNING;
    public static final ModConfigSpec.EnumValue<HudMode> HUD;
    public static final ModConfigSpec.BooleanValue SEASONAL_COLORS;
    public static final ModConfigSpec.BooleanValue FALLING_LEAVES;
    public static final ModConfigSpec.BooleanValue THUNDER_DELAY;
    public static final ModConfigSpec.BooleanValue MUFFLE_INDOORS;
    public static final ModConfigSpec.BooleanValue AMBIENT_LOOPS;
    public static final ModConfigSpec.BooleanValue FAHRENHEIT;
    public static final ModConfigSpec.IntValue STORM_CLOUD_RANGE;
    public static final ModConfigSpec.BooleanValue BREATH;
    public static final ModConfigSpec.BooleanValue FOOTPRINTS;
    public static final ModConfigSpec.BooleanValue SCREEN_RAINDROPS;
    public static final ModConfigSpec.BooleanValue HEAT_SHIMMER;
    public static final ModConfigSpec.BooleanValue DRIPPING_LEAVES;
    public static final ModConfigSpec.BooleanValue SHOOTING_STARS;
    public static final ModConfigSpec.BooleanValue WILDLIFE_SOUNDS;
    public static final ModConfigSpec.BooleanValue SKY_OPTICS;
    public static final ModConfigSpec.BooleanValue HOARFROST;
    public static final ModConfigSpec.BooleanValue FIREFLIES;
    public static final ModConfigSpec.BooleanValue BLOSSOMS;
    public static final ModConfigSpec.BooleanValue BIRD_FLOCKS;
    public static final ModConfigSpec.BooleanValue WHITECAPS;
    public static final ModConfigSpec.BooleanValue TOWERING_CLOUDS;
    public static final ModConfigSpec.BooleanValue FOG_BANKS;
    public static final ModConfigSpec.BooleanValue SMOKE_COLUMNS;
    public static final ModConfigSpec.BooleanValue DIAMOND_DUST;
    public static final ModConfigSpec.BooleanValue STATIC_SPARKS;
    public static final ModConfigSpec.BooleanValue MIRAGES;
    public static final ModConfigSpec.BooleanValue STEAM_FOG;
    public static final ModConfigSpec.BooleanValue WATERFALL_SPRAY;
    public static final ModConfigSpec.BooleanValue DUSTY_RAIN;
    public static final ModConfigSpec.BooleanValue RED_SPRITES;
    public static final ModConfigSpec.BooleanValue NOCTILUCENT_CLOUDS;
    public static final ModConfigSpec.BooleanValue STORM_CLOUD_SHAPES;
    public static final ModConfigSpec.BooleanValue MOONBOWS;
    public static final ModConfigSpec.BooleanValue FOGBOWS;
    public static final ModConfigSpec.BooleanValue VIRGA;
    public static final ModConfigSpec.BooleanValue INVERSION_HAZE;
    public static final ModConfigSpec.BooleanValue SINGING_ICE;
    public static final ModConfigSpec.BooleanValue SEA_SPARKLE;
    public static final ModConfigSpec.BooleanValue DOUBLE_RAINBOWS;
    public static final ModConfigSpec.BooleanValue GLORIES;
    public static final ModConfigSpec.BooleanValue LENTICULAR_CLOUDS;
    public static final ModConfigSpec.BooleanValue GREEN_FLASH;
    public static final ModConfigSpec.BooleanValue TWILIGHT_BANDS;
    public static final ModConfigSpec.BooleanValue LIGHT_PILLARS;
    public static final ModConfigSpec.BooleanValue IRIDESCENT_CLOUDS;
    public static final ModConfigSpec.BooleanValue DEW;
    public static final ModConfigSpec.BooleanValue POLLEN;
    public static final ModConfigSpec.BooleanValue MILKY_WAY;
    public static final ModConfigSpec.BooleanValue ANTICREPUSCULAR_RAYS;
    public static final ModConfigSpec.BooleanValue SKY_ARCS;
    public static final ModConfigSpec.BooleanValue CLOUD_FORMS;
    public static final ModConfigSpec.BooleanValue NACREOUS_CLOUDS;
    public static final ModConfigSpec.BooleanValue SPIDER_LIGHTNING;
    public static final ModConfigSpec.BooleanValue RED_SKY;
    public static final ModConfigSpec.BooleanValue ALGAL_BLOOM_TINT;
    public static final ModConfigSpec.BooleanValue AIRGLOW;
    public static final ModConfigSpec.BooleanValue FUNNEL_CLOUDS;
    public static final ModConfigSpec.BooleanValue SNOW_GLARE;
    public static final ModConfigSpec.BooleanValue GROUND_STEAM;
    public static final ModConfigSpec.BooleanValue FATA_MORGANA;
    public static final ModConfigSpec.BooleanValue UPPER_LIGHTNING;
    public static final ModConfigSpec.BooleanValue STEVE;
    public static final ModConfigSpec.BooleanValue GEGENSCHEIN;
    public static final ModConfigSpec.BooleanValue PLANETS;
    public static final ModConfigSpec.BooleanValue EARTHSHINE;
    public static final ModConfigSpec.BooleanValue HARVEST_MOON;
    public static final ModConfigSpec.BooleanValue DEWBOWS;
    public static final ModConfigSpec.BooleanValue SPRAY_BOWS;
    public static final ModConfigSpec.BooleanValue BANNER_CLOUDS;
    public static final ModConfigSpec.BooleanValue BLUE_HAZE;
    public static final ModConfigSpec.BooleanValue WOODSMOKE;
    public static final ModConfigSpec.BooleanValue ALTITUDE_SEASONS;
    public static final ModConfigSpec.BooleanValue REPLACE_VANILLA_CLOUDS;
    public static final ModConfigSpec.BooleanValue CLOUD_STREETS;
    public static final ModConfigSpec.BooleanValue CLOUD_SHADE;
    public static final ModConfigSpec.BooleanValue ASPERITAS_CLOUDS;
    public static final ModConfigSpec.BooleanValue CHINOOK_ARCH;
    public static final ModConfigSpec.BooleanValue THUNDER_ECHOES;
    public static final ModConfigSpec.BooleanValue CAVE_BREATH;
    public static final ModConfigSpec.BooleanValue FOG_DRIP;
    public static final ModConfigSpec.BooleanValue CLOUD_FOREST_MIST;
    public static final ModConfigSpec.BooleanValue CLOUD_WATERFALLS;
    public static final ModConfigSpec.BooleanValue GREEN_STORM_SKIES;
    public static final ModConfigSpec.BooleanValue INSECT_SWARMS;
    public static final ModConfigSpec.BooleanValue SINGING_DUNES;
    public static final ModConfigSpec.BooleanValue GOLDEN_HAZE;
    public static final ModConfigSpec.BooleanValue HIGH_CLOUDS;
    public static final ModConfigSpec.BooleanValue SCUD;
    public static final ModConfigSpec.BooleanValue HAIL_FOG;
    public static final ModConfigSpec.BooleanValue SUPERMOON_LOOK;
    public static final ModConfigSpec.BooleanValue CASTELLANUS;
    public static final ModConfigSpec.BooleanValue VOLCANIC_TWILIGHT;
    public static final ModConfigSpec.BooleanValue SWALLOWS;
    public static final ModConfigSpec.BooleanValue RARE_HALOS;
    public static final ModConfigSpec.BooleanValue SNOW_PLUMES;
    public static final ModConfigSpec.BooleanValue LAKE_WHITINGS;
    public static final ModConfigSpec.BooleanValue CORONAS;
    public static final ModConfigSpec.BooleanValue SQUEAKY_SNOW;
    public static final ModConfigSpec.BooleanValue GOSSAMER;
    public static final ModConfigSpec.BooleanValue SUN_PATH;
    public static final ModConfigSpec.IntValue SUN_LATITUDE;
    public static final ModConfigSpec.BooleanValue REDUCE_FLASHING;
    public static final ModConfigSpec.DoubleValue SCREEN_EFFECTS;
    public static final ModConfigSpec.BooleanValue WEATHER_ALERT_SOUNDS;

    public enum HudMode { BAROMETER, ALWAYS, NEVER }

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        PARTICLE_DENSITY = b.comment("Multiplier for weather particles (hail, sand, snow, leaves...). 0 disables them.")
                .defineInRange("particleDensity", 1.0, 0.0, 3.0);
        FOG = b.comment("Render weather fog (fog banks, blizzard whiteouts, sandstorm haze).")
                .define("fog", true);
        SKY_EFFECTS = b.comment("Tint and darken the sky for overcast, supercell, sandstorm and heatwave weather.")
                .define("skyEffects", true);
        RAINBOWS = b.comment("Show rainbows when rain clears up during the day.")
                .define("rainbows", true);
        AURORAS = b.comment("Show auroras on clear nights in cold biomes.")
                .define("auroras", true);
        SOUNDS = b.comment("Play wind, hail and tornado sounds.")
                .define("sounds", true);
        STORM_CLOUDS = b.comment("Draw a low, dark cloud deck under storms (including distant storm cells on the horizon).")
                .define("stormClouds", true);
        WET_GROUND = b.comment("Ground looks darker and wet in the rain, and raindrops ripple on water.")
                .define("wetGround", true);
        MIST = b.comment("Morning mist over rivers, swamps and beaches.")
                .define("mist", true);
        HEAT_LIGHTNING = b.comment("Silent heat-lightning flashes on hot nights and from distant storms.")
                .define("heatLightning", true);
        HUD = b.comment("When to show the forecast panel: while holding a barometer, always, or never.")
                .defineEnum("forecastHud", HudMode.BAROMETER);
        SEASONAL_COLORS = b.comment("Grass and leaves change colour with the seasons (skipped when Serene Seasons is installed).")
                .define("seasonalColors", true);
        FALLING_LEAVES = b.comment("Leaves drift down from trees in autumn.")
                .define("fallingLeaves", true);
        THUNDER_DELAY = b.comment("Thunder arrives after the flash, later the further away the strike is.")
                .define("thunderDelay", true);
        MUFFLE_INDOORS = b.comment("Weather sounds are muffled when you're indoors.")
                .define("muffleIndoors", true);
        AMBIENT_LOOPS = b.comment("Rain drumming on the roof indoors, and wind whistling around buildings.")
                .define("ambientLoops", true);
        FAHRENHEIT = b.comment("Show temperatures in Fahrenheit instead of Celsius.")
                .define("fahrenheit", false);
        STORM_CLOUD_RANGE = b.comment("How far out the storm cloud deck is drawn, in blocks. 0 = automatic",
                        "(about 960, or matched to Distant Horizons' render distance when it is installed).")
                .defineInRange("stormCloudRange", 0, 0, 16384);
        BREATH = b.comment("Your breath (and other creatures') shows as vapour in the cold.")
                .define("breath", true);
        FOOTPRINTS = b.comment("Footprints in snow.")
                .define("footprints", true);
        SCREEN_RAINDROPS = b.comment("Raindrops on the screen when you look up into the rain.")
                .define("screenRaindrops", true);
        HEAT_SHIMMER = b.comment("Distant ground shimmers in the heat on hot, sunny days.")
                .define("heatShimmer", true);
        DRIPPING_LEAVES = b.comment("Leaves keep dripping for a while after the rain.")
                .define("drippingLeaves", true);
        SHOOTING_STARS = b.comment("Shooting stars on clear nights, and meteor showers on some nights.")
                .define("shootingStars", true);
        WILDLIFE_SOUNDS = b.comment("Birdsong on spring and summer days, crickets on summer nights, frogs after rain.")
                .define("wildlifeSounds", true);
        SKY_OPTICS = b.comment("Ice-crystal halos and sun dogs on icy days, and sunbeams through broken cloud.")
                .define("skyOptics", true);
        HOARFROST = b.comment("Freezing fog coats grass and leaves in white hoarfrost.")
                .define("hoarfrost", true);
        FIREFLIES = b.comment("Fireflies on warm summer evenings.")
                .define("fireflies", true);
        BLOSSOMS = b.comment("Blossom petals drifting down from trees in spring.")
                .define("blossoms", true);
        BIRD_FLOCKS = b.comment("Flocks of birds flying over in a V in spring and autumn.")
                .define("birdFlocks", true);
        WHITECAPS = b.comment("Whitecaps and spray on rough seas in storms.")
                .define("whitecaps", true);
        TOWERING_CLOUDS = b.comment("Thunderstorms tower up into tall clouds you can watch from far away.")
                .define("toweringClouds", true);
        FOG_BANKS = b.comment("Sea fog shows as a low bank of fog rolling in over the water.")
                .define("fogBanks", true);
        SMOKE_COLUMNS = b.comment("Fires burning in the open send up tall columns of smoke.")
                .define("smokeColumns", true);
        DIAMOND_DUST = b.comment("Glittering ice crystals in the air on bitterly cold, clear mornings, with a sun pillar.")
                .define("diamondDust", true);
        STATIC_SPARKS = b.comment("St. Elmo's fire: sparks on lightning rods and metal tools in thunderstorms.")
                .define("staticSparks", true);
        MIRAGES = b.comment("Shimmering mirages over hot desert flats and salt flats.")
                .define("mirages", true);
        STEAM_FOG = b.comment("Steam fog: wisps of mist rising off unfrozen lakes and rivers on bitterly cold mornings.")
                .define("steamFog", true);
        WATERFALL_SPRAY = b.comment("Mist billowing up at the foot of waterfalls, with a rainbow in the spray when the sun is behind you.")
                .define("waterfallSpray", true);
        DUSTY_RAIN = b.comment("Rain falling through a dust haze comes down reddish-brown.")
                .define("dustyRain", true);
        RED_SPRITES = b.comment("Rare red sprites flickering high above distant thunderstorms at night.")
                .define("redSprites", true);
        NOCTILUCENT_CLOUDS = b.comment("Glowing electric-blue noctilucent clouds low in the northern sky on some summer nights.")
                .define("noctilucentClouds", true);
        STORM_CLOUD_SHAPES = b.comment("Shelf clouds along the leading edge of cold fronts, and mammatus pouches under thunderstorm anvils.")
                .define("stormCloudShapes", true);
        MOONBOWS = b.comment("Faint, white moonbows after night rain when the moon is bright and low.")
                .define("moonbows", true);
        FOGBOWS = b.comment("Broad white fogbows opposite a low sun shining through thin fog.")
                .define("fogbows", true);
        VIRGA = b.comment("Virga: in dry climates, rain streaks hanging under storm clouds that dry up before reaching the ground.")
                .define("virga", true);
        INVERSION_HAZE = b.comment("On cold, calm mornings, smoke and haze pool in valleys under a lid of warm air.")
                .define("inversionHaze", true);
        SINGING_ICE = b.comment("Lake ice booms and pings as the temperature changes.")
                .define("singingIce", true);
        SEA_SPARKLE = b.comment("Waves glow blue at night on warm coasts.")
                .define("seaSparkle", true);
        DOUBLE_RAINBOWS = b.comment("A fainter second rainbow outside the first, with a darker band of sky between them.")
                .define("doubleRainbows", true);
        GLORIES = b.comment("Standing above fog with the sun behind you: your shadow on the fog, ringed by a rainbow glory.")
                .define("glories", true);
        LENTICULAR_CLOUDS = b.comment("Smooth, lens-shaped clouds over mountains in strong wind.")
                .define("lenticularClouds", true);
        GREEN_FLASH = b.comment("The green flash at the moment of sunset (or sunrise) over the sea.")
                .define("greenFlash", true);
        TWILIGHT_BANDS = b.comment("Earth's shadow and the pink belt above it, opposite the sun at twilight.")
                .define("twilightBands", true);
        LIGHT_PILLARS = b.comment("Light pillars standing over lanterns and fires on frigid, clear nights.")
                .define("lightPillars", true);
        IRIDESCENT_CLOUDS = b.comment("Pastel colours in thin clouds near the sun.")
                .define("iridescentClouds", true);
        DEW = b.comment("Dew sparkling on the grass on clear, calm mornings.")
                .define("dew", true);
        POLLEN = b.comment("Yellow pollen haze drifting out of the woods on dry spring days, and films of pollen on still water.")
                .define("pollen", true);
        MILKY_WAY = b.comment("The Milky Way and zodiacal light on dark, moonless nights (washed out by the moon and near villages).")
                .define("milkyWay", true);
        ANTICREPUSCULAR_RAYS = b.comment("Sunbeams converging again on the far side of the sky, opposite the sun.")
                .define("anticrepuscularRays", true);
        SKY_ARCS = b.comment("The circumhorizontal arc (fire rainbow) under a high sun and the circumzenithal arc over a low one.")
                .define("skyArcs", true);
        CLOUD_FORMS = b.comment("Roll clouds, breaking-wave clouds, pileus caps on growing thunderheads and hole-punch clouds.")
                .define("cloudForms", true);
        NACREOUS_CLOUDS = b.comment("Nacreous (mother-of-pearl) clouds in polar winter twilight.")
                .define("nacreousClouds", true);
        SPIDER_LIGHTNING = b.comment("Spider lightning crawling along the underside of storm anvils at night.")
                .define("spiderLightning", true);
        RED_SKY = b.comment("Red sky at night: a fiery sunset before fair weather, and a red sunrise before rain.")
                .define("redSky", true);
        ALGAL_BLOOM_TINT = b.comment("Rusty-red water under an algal bloom.")
                .define("algalBloomTint", true);
        AIRGLOW = b.comment("Faint green airglow low in the sky on dark nights.")
                .define("airglow", true);
        FUNNEL_CLOUDS = b.comment("Wall clouds and funnel clouds under supercells.")
                .define("funnelClouds", true);
        SNOW_GLARE = b.comment("Bright sun on snow dazzles you unless you wear storm goggles.")
                .define("snowGlare", true);
        GROUND_STEAM = b.comment("Steam rises off warm ground when the sun comes out after summer rain.")
                .define("groundSteam", true);
        FATA_MORGANA = b.comment("Towering, distorted mirages over cold seas.")
                .define("fataMorgana", true);
        UPPER_LIGHTNING = b.comment("Blue jets and elves above big thunderstorms at night.")
                .define("blueJetsAndElves", true);
        STEVE = b.comment("STEVE: a ribbon of purple light across the sky on some aurora nights.")
                .define("steve", true);
        GEGENSCHEIN = b.comment("Gegenschein: a faint glow in the sky opposite the sun on the darkest nights.")
                .define("gegenschein", true);
        PLANETS = b.comment("Venus, Jupiter, Mars and Saturn wander along the sun's path through the night sky.")
                .define("planets", true);
        EARTHSHINE = b.comment("The dark part of a crescent moon glows faintly with light reflected off the world.")
                .define("earthshine", true);
        HARVEST_MOON = b.comment("The moon rises big and orange, above all the full moon nearest the autumn equinox.")
                .define("harvestMoon", true);
        DEWBOWS = b.comment("On dewy mornings a white bow lies on the grass and a glow rings your shadow.")
                .define("dewbows", true);
        SPRAY_BOWS = b.comment("Rainbows in waterfall spray and breaking surf when the sun is behind you.")
                .define("sprayBows", true);
        BANNER_CLOUDS = b.comment("On windy days, a plume of cloud streams off the lee side of sharp peaks.")
                .define("bannerClouds", true);
        BLUE_HAZE = b.comment("A bluish haze hangs over big forests on hot summer days.")
                .define("blueHaze", true);
        WOODSMOKE = b.comment("On still, cold mornings, campfire and chimney smoke spreads out in a flat layer.")
                .define("woodsmokeInversions", true);
        ALTITUDE_SEASONS = b.comment("High ground greens up later in spring and turns earlier in autumn.")
                .define("altitudeSeasons", true);
        REPLACE_VANILLA_CLOUDS = b.comment("Draw soft, drifting clouds that follow the wind and the weather instead of Minecraft's blocky ones.",
                        "(Minecraft's own \"Clouds: Off\" video setting turns these off too.)")
                .define("replaceVanillaClouds", true);
        CLOUD_STREETS = b.comment("On breezy summer days, fair-weather clouds line up in rows along the wind.")
                .define("cloudStreets", true);
        CLOUD_SHADE = b.comment("On broken-cloud days the light dims and brightens as clouds cross the sun.")
                .define("cloudShade", true);
        ASPERITAS_CLOUDS = b.comment("A rough, wave-tossed cloud base rolling overhead around storms.")
                .define("asperitasClouds", true);
        CHINOOK_ARCH = b.comment("During a foehn wind, a clear band of sky arches over the mountains under the cloud deck.")
                .define("chinookArch", true);
        THUNDER_ECHOES = b.comment("Thunder echoes back off the mountains and valley sides round you.")
                .define("thunderEchoes", true);
        CAVE_BREATH = b.comment("Cave mouths steam in cold weather.")
                .define("caveBreath", true);
        FOG_DRIP = b.comment("Leaves drip in fog.")
                .define("fogDrip", true);
        CLOUD_FOREST_MIST = b.comment("Cloud forests are wrapped in drifting mist.")
                .define("cloudForestMist", true);
        CLOUD_WATERFALLS = b.comment("Fog and low cloud pour over mountain ridges and tumble down the far side.")
                .define("cloudWaterfalls", true);
        GREEN_STORM_SKIES = b.comment("The eerie green light under big hailstorms.")
                .define("greenStormSkies", true);
        INSECT_SWARMS = b.comment("Midges dance over water on calm summer evenings, and mayflies hatch over rivers.")
                .define("insectSwarms", true);
        SINGING_DUNES = b.comment("Sand sliding down a dune's steep face hums and booms in dry weather.")
                .define("singingDunes", true);
        GOLDEN_HAZE = b.comment("A soft golden haze over the still, warm days of an Indian summer.")
                .define("goldenHaze", true);
        HIGH_CLOUDS = b.comment("High cirrus streaks and rippled mackerel skies, which spread in and thicken ahead of fronts and storms.")
                .define("highClouds", true);
        SCUD = b.comment("Ragged shreds of low cloud racing under storm clouds and along gust fronts.")
                .define("scud", true);
        HAIL_FOG = b.comment("A low fog over the drifts of hail after a hailstorm.")
                .define("hailFog", true);
        SUPERMOON_LOOK = b.comment("Supermoons look bigger and brighter than other full moons.")
                .define("supermoonLook", true);
        CASTELLANUS = b.comment("Turreted clouds in the morning sky on days that turn thundery by the afternoon.")
                .define("castellanus", true);
        VOLCANIC_TWILIGHT = b.comment("After a big eruption, weeks of purple twilights and a pale ring round the sun.")
                .define("volcanicTwilight", true);
        SWALLOWS = b.comment("Swallows skim low over the fields before a storm.")
                .define("swallows", true);
        RARE_HALOS = b.comment("On the best halo days, the parhelic circle round the whole sky and the faint 46-degree halo.")
                .define("rareHalos", true);
        SNOW_PLUMES = b.comment("On windy winter days, snow streams off the mountain tops in long white plumes.")
                .define("snowPlumes", true);
        LAKE_WHITINGS = b.comment("In hot summers, some lakes turn milky turquoise for a few weeks (a whiting).")
                .define("lakeWhitings", true);
        CORONAS = b.comment("Soft pastel rings close round the moon (and the sun) shining through thin cloud.")
                .define("coronas", true);
        SQUEAKY_SNOW = b.comment("In a hard frost (below about -10°C), snow squeaks underfoot instead of crunching.")
                .define("squeakySnow", true);
        GOSSAMER = b.comment("On still autumn mornings, spider silk sheets the grass, glittering with dew, and threads drift on the air.")
                .define("gossamer", true);
        SUN_PATH = b.comment("The sun's daily path follows the seasons: low across the southern sky in winter and high in summer, with the",
                        "moon and stars tilted to match. (Turned off while a shader pack is in use, as shader packs place the sun themselves.)")
                .define("sunPath", true);
        SUN_LATITUDE = b.comment("How far north of the equator the world is, in degrees, for the sun's path: at 40 the noon sun stands about",
                        "73 degrees up at midsummer and 27 at midwinter; at 0 it passes overhead at the equinoxes.")
                .defineInRange("sunLatitude", 40, 0, 66);
        b.comment("Accessibility").push("accessibility");
        REDUCE_FLASHING = b.comment("Tone down lightning flashes, heat lightning and other bright flashes from this mod.",
                        "(Vanilla's 'Hide Lightning Flashes' accessibility option also turns these off.)")
                .define("reduceFlashing", false);
        SCREEN_EFFECTS = b.comment("Strength of screen effects: raindrops on the screen, heat shimmer, wind shake and sky flashes.",
                        "0 turns them off.")
                .defineInRange("screenEffects", 1.0, 0.0, 1.0);
        WEATHER_ALERT_SOUNDS = b.comment("Play an alert tone with weather radio warnings.")
                .define("weatherAlertSounds", true);
        b.pop();
        SPEC = b.build();
    }

    private ClientConfig() {}
}
