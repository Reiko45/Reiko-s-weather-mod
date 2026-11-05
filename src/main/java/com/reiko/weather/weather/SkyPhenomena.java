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
