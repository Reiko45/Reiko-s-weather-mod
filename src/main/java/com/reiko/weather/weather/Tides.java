package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * The sea rises and falls twice a day, highest around noon and midnight. Tides are strongest (spring
 * tides) at full and new moon and weakest at the half moons; only the highest spring tides actually
 * wash up onto the beach.
 */
public final class Tides {
    /** -1 (low tide) to 1 (high tide). */
    public static float cycle(long dayTime) {
        return Mth.cos((float) ((dayTime - 6000L) / 12000.0 * Math.PI * 2.0));
    }

    /** 1 at full and new moon, 0.5 in between, 0 at the half moons. */
    public static float springness(Level level) {
        return switch (level.getMoonPhase()) {
            case 0, 4 -> 1.0F;
            case 2, 6 -> 0.0F;
            default -> 0.5F;
        };
    }

    /** Whether high water is washing up the beach right now. */
    public static boolean flooding(Level level) {
        return WeatherConfig.SPEC.isLoaded() && WeatherConfig.TIDES.get() && level.dimension() == Level.OVERWORLD
                && springness(level) >= 1.0F && cycle(level.getDayTime()) > (Supermoons.kingTide(level) ? 0.6F : 0.8F);
    }

    /** Whether the tide is in over the tidal flats (every high tide, not just the spring tides). */
    public static boolean floodingFlats(Level level) {
        return WeatherConfig.SPEC.isLoaded() && WeatherConfig.TIDES.get() && level.dimension() == Level.OVERWORLD && cycle(level.getDayTime()) > 0.45F;
    }

    /** "Tide: high (spring tide), falling" and so on. */
    public static Component describe(Level level) {
        long time = level.getDayTime();
        float now = cycle(time), soon = cycle(time + 200L);
        String height = now > 0.6F ? "high" : now < -0.6F ? "low" : soon > now ? "rising" : "falling";
        float spring = springness(level);
        String kind = Supermoons.kingTide(level) ? "king" : spring >= 1.0F ? "spring" : spring <= 0.0F ? "neap" : "normal";
        return Component.translatable("report.reikos_weather.tide", Component.translatable("report.reikos_weather.tide." + height),
                Component.translatable("report.reikos_weather.tide." + kind));
    }

    private Tides() {}
}
