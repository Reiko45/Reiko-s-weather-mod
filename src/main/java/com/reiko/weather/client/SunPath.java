package com.reiko.weather.client;

import com.reiko.weather.compat.Shaders;
import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.weather.Season;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * The sun's path through the seasons. In vanilla Minecraft the sun passes straight overhead every day of the year; here
 * the whole sky is tilted toward the south as it would be some way north of the equator, so the noon sun stands high
 * in summer and low in winter, with the moon and stars wheeling round a pole star up in the northern sky. It still
 * rises due east and sets due west on the same schedule, so daylight and darkness come and go just as before.
 */
public final class SunPath {
    /** How far north and south of the equator the sun swings over the year, in degrees (the tilt of the Earth's axis). */
    private static final float OBLIQUITY = 23.44F;

    /**
     * How far the sky is tilted toward the south, in radians: 0 is vanilla (the noon sun overhead). The noon sun stands
     * 90 degrees less than this over the southern horizon, and the pole star this high over the northern one.
     */
    public static float tilt() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level.dimension() != Level.OVERWORLD || !ClientConfig.SUN_PATH.get() || Shaders.packInUse()) return 0.0F;
        return (ClientConfig.SUN_LATITUDE.get() - declination(level)) * Mth.DEG_TO_RAD;
    }

    /** The sun's declination in degrees: about +23 at midsummer, -23 at midwinter, 0 at the equinoxes (or with no seasons). */
    public static float declination(ClientLevel level) {
        Season season = SyncedWeather.season();
        int days = SyncedWeather.daysPerSeason(), day = SyncedWeather.dayOfSeason();
        if (season == null || days <= 0 || day < 0) return 0.0F;
        float year = (season.ordinal() * days + day + Math.floorMod(level.getDayTime(), 24000L) / 24000.0F) / (4.0F * days);
        // midsummer is halfway through summer, the second of the four seasons
        return OBLIQUITY * Mth.cos(Mth.TWO_PI * (year - 0.375F));
    }

    /** Turns a direction on vanilla's sky (the sun's path through the zenith) onto the tilted sky, in place. */
    public static Vector3f tiltSky(Vector3f v, float tilt) {
        if (tilt == 0.0F) return v;
        float c = Mth.cos(tilt), s = Mth.sin(tilt);
        return v.set(v.x, v.y * c - v.z * s, v.y * s + v.z * c);
    }

    /** Which way the sun is (a unit vector), on its tilted path. */
    public static Vector3f sun(ClientLevel level, float partialTick) {
        float theta = level.getTimeOfDay(partialTick) * Mth.TWO_PI;
        return tiltSky(new Vector3f(-Mth.sin(theta), Mth.cos(theta), 0.0F), tilt());
    }

    private SunPath() {}
}
