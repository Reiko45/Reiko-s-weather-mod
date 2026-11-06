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
        WILDLIFE_SOUNDS = b.comment("Birdsong on spring and summer days, crickets on summer nights.")
                .define("wildlifeSounds", true);
        SPEC = b.build();
    }

    private ClientConfig() {}
}
