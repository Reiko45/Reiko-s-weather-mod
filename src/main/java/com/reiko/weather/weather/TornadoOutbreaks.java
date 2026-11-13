package com.reiko.weather.weather;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

/**
 * Tornado outbreaks. Now and then in spring and early summer the ingredients all line up across a whole region at
 * once: warm, moist air near the ground, cold dry air aloft and strong winds turning with height. On a day like that,
 * several supercells build through the afternoon, and each can drop tornado after tornado.
 */
public final class TornadoOutbreaks {
    /** How many spring and summer days bring an outbreak. */
    private static final float CHANCE = 0.08F;

    /** Whether a day is an outbreak day: worked out from the day alone, so the forecast can warn of it that morning. */
    public static boolean outbreakDay(long day) {
        Season season = WeatherManager.seasonInfo().season();
        if (season != null && season != Season.SPRING && season != Season.SUMMER) return false;
        int h = Mth.murmurHash3Mixer((int) day * 0x632BE5AB + 0x1B873593 + (int) (day >>> 32));
        return (h >>> 8) / (float) (1 << 24) < CHANCE;
    }

    /** Whether today is one. */
    public static boolean today(ServerLevel level) {
        return outbreakDay(level.getDayTime() / 24000L);
    }

    private TornadoOutbreaks() {}
}
