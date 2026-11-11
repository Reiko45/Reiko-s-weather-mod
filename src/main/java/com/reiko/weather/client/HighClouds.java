package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.weather.WeatherType;
import com.reiko.weather.weather.Wind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The high clouds, made of ice crystals five to ten kilometres up. On settled days a few wisps of cirrus drift over;
 * ahead of a warm front or a spell of rain they come in thicker, streaming out in hooked "mares' tails", then the
 * rippled rows of a mackerel sky, and finally a thin milky veil (cirrostratus) that puts a halo round the sun a few
 * hours before the rain. "Mackerel sky and mares' tails make tall ships carry low sails."
 */
public final class HighClouds {
    /** How long before the rain the high cloud starts to spread in, in ticks (about a Minecraft day). */
    private static final float LEAD = 20000.0F;
    private static float cirrus, mackerel, veil;
    private static float windAzimuth;

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD || !SyncedWeather.managed() || !ClientConfig.HIGH_CLOUDS.get()) {
            cirrus = mackerel = veil = 0.0F;
            return;
        }
        long time = level.getGameTime();
        // the rain on its way: the closer it is, the more the high cloud has spread in
        float front = 0.0F;
        WeatherType[] forecast = SyncedWeather.forecast();
        WeatherType now = SyncedWeather.type();
        if (forecast.length > 0 && wet(forecast[0]) && !wet(now)) {
            front = Mth.clamp(1.0F - SyncedWeather.remaining() / LEAD, 0.0F, 1.0F);
        }
        if (now == WeatherType.WARM_FRONT) front = Math.max(front, 0.8F);
        // and on some fine days, a few wisps for no reason at all
        float fair = Mth.clamp(0.5F * Mth.sin(time / 27000.0F + 1.0F) + 0.45F * Mth.sin(time / 61000.0F + 4.0F) - 0.2F, 0.0F, 1.0F);
        // (a lower cloud deck hides them)
        float seen = Mth.clamp(1.0F - ClientWeather.overcast() * 1.4F, 0.0F, 1.0F) * (1.0F - Math.min(1.0F, ClientWeather.rain() * 3.0F));
        float cirrusTarget = Mth.clamp(Math.max(fair * 0.7F, front * 1.3F) * (front > 0.85F ? (1.0F - front) / 0.15F * 0.6F + 0.4F : 1.0F), 0.0F, 1.0F);
        float mackerelTarget = Mth.clamp(1.0F - Math.abs(front - 0.5F) / 0.28F, 0.0F, 1.0F) * 0.9F
                + Mth.clamp(0.5F * Mth.sin(time / 41000.0F + 2.3F) - 0.3F, 0.0F, 0.35F);
        float veilTarget = Mth.clamp((front - 0.6F) / 0.3F, 0.0F, 1.0F);
        cirrus = Mth.lerp(0.002F, cirrus, cirrusTarget * seen);
        mackerel = Mth.lerp(0.002F, mackerel, Math.min(1.0F, mackerelTarget) * seen);
        veil = Mth.lerp(0.002F, veil, veilTarget * seen);
        // they stream along with the wind aloft (the same way as the weather, give or take)
        Vec3 w = Wind.direction(SyncedWeather.windAngle());
        windAzimuth = (float) Mth.atan2(w.x, -w.z);
    }

    private static boolean wet(WeatherType type) {
        return type != null && (type.vanillaRain || type == WeatherType.WARM_FRONT || type == WeatherType.ATMOSPHERIC_RIVER);
    }

    public static float cirrus() {
        return cirrus;
    }

    public static float mackerel() {
        return mackerel;
    }

    /** The thin milky veil of cirrostratus ahead of the rain (it makes halos). */
    public static float veil() {
        return veil;
    }

    /** Which way the high cloud streams across the sky, as an azimuth (radians, 0 north, clockwise). */
    public static float windAzimuth() {
        return windAzimuth;
    }

    public static void reset() {
        cirrus = mackerel = veil = 0.0F;
    }

    private HighClouds() {}
}
