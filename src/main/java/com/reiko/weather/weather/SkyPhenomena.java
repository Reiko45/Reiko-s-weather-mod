package com.reiko.weather.weather;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/** Shared rules for sky phenomena, so the server's advancements agree with what clients render. */
public final class SkyPhenomena {
    /** About half of all nights can show an aurora (in cold, clear places). */
    public static boolean isAuroraNight(long dayTime) {
        long day = dayTime / 24000L;
        return Math.floorMod(Mth.murmurHash3Mixer((int) day * 31 + 7), 100) < 55;
    }

    /** About one night in seven brings a meteor shower. */
    public static boolean isMeteorShowerNight(long dayTime) {
        long day = dayTime / 24000L;
        return Math.floorMod(Mth.murmurHash3Mixer((int) day * 17 + 3), 100) < 15;
    }

    /**
     * On some aurora nights a geomagnetic storm hits: auroras reach further south, compasses spin and
     * radars crackle. Only at night.
     */
    public static boolean isGeomagneticStormNight(long dayTime) {
        long day = dayTime / 24000L;
        return isAuroraNight(dayTime) && Math.floorMod(Mth.murmurHash3Mixer((int) day * 53 + 11), 100) < 35;
    }

    public static boolean geomagneticStormNow(Level level) {
        return com.reiko.weather.config.WeatherConfig.SPEC.isLoaded() && com.reiko.weather.config.WeatherConfig.GEOMAGNETIC_STORMS.get()
                && level.dimension() == Level.OVERWORLD && isGeomagneticStormNight(level.getDayTime()) && sunHeight(level, 1.0F) < -0.15F;
    }

    /** The sun's height: 1 at noon, 0 on the horizon, -1 at midnight. */
    public static float sunHeight(Level level, float partialTick) {
        return Mth.cos(level.getTimeOfDay(partialTick) * Mth.TWO_PI);
    }

    /** A rainbow needs a low sun (it sits 42 degrees from the point opposite the sun). */
    public static boolean rainbowSunAngle(float sunHeight) {
        return sunHeight > -0.02F && sunHeight < 0.62F;
    }

    private SkyPhenomena() {}
}
