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
        SPEC = b.build();
    }

    private ClientConfig() {}
}
