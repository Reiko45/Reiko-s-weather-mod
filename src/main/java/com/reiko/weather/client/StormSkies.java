package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.weather.CustomWeathers;
import com.reiko.weather.weather.Microclimate;
import com.reiko.weather.weather.StormCell;
import com.reiko.weather.weather.WeatherType;
import com.reiko.weather.weather.Wind;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Two skies the wind makes over storms and mountains.
 * <p>
 * Asperitas: around some thunderstorms, a dark cloud base heaves and rolls overhead like the surface of a rough sea
 * seen from underneath, its waves stirred up by the wind shear at the top of the cold air the storm has spread out.
 * <p>
 * The Chinook arch: when a föhn wind pours down the lee of a range, the air sinking off the mountains clears a band
 * of blue sky over them, under a long, sharp-edged deck of cloud that arches across the sky like a bow.
 */
public final class StormSkies {
    private static float asperitas, chinook;
    private static float chinookAngle;
    private static long lastFoehnCheck = -100;
    private static float foehnTarget;

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD || !SyncedWeather.managed()) {
            asperitas = chinook = 0.0F;
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        // asperitas: only some storms grow it, and you see it near the storm's edge, not deep in the rain
        float target = 0.0F;
        if (ClientConfig.ASPERITAS_CLOUDS.get() && ClientWeather.rain() < 0.35F) {
            for (StormCell cell : SyncedWeather.cells()) {
                WeatherType t = cell.type;
                if (t != WeatherType.THUNDERSTORM && t != WeatherType.SUPERCELL && t != WeatherType.HAIL && t != WeatherType.DERECHO
                        && t != WeatherType.COLD_FRONT) continue;
                if (PuffRenderer.hash(cell.id, 77) > 0.4F) continue;
                float edge = (float) (cell.distanceTo(cam.x, cam.z) - cell.radius);
                float near = Mth.clamp(1.0F - Math.abs(edge - 30.0F) / 140.0F, 0.0F, 1.0F) * cell.strength();
                if (near > target) {
                    target = near;
                }
            }
        }
        asperitas = Mth.lerp(0.01F, asperitas, target);
        // the Chinook arch: a föhn here, blowing down off the mountains upwind
        if (ClientConfig.CHINOOK_ARCH.get()) {
            long now = level.getGameTime();
            if (now - lastFoehnCheck >= 40 || now < lastFoehnCheck) {
                lastFoehnCheck = now;
                float windAngle = SyncedWeather.windAngle();
                var info = Microclimate.at(level, Mth.floor(cam.x), Mth.floor(cam.z), windAngle);
                var profile = CustomWeathers.backgroundProfile(SyncedWeather.type(), CustomWeathers.client(SyncedWeather.custom()), ClientWeather.climate());
                foehnTarget = info == Microclimate.Info.NONE ? 0.0F : Microclimate.foehn(info, profile);
                // the arch lies over the mountains, upwind
                chinookAngle = (float) Math.atan2(-Mth.cos(windAngle), Mth.sin(windAngle));
            }
            chinook = Mth.lerp(0.005F, chinook, ClientWeather.rain() > 0.3F ? 0.0F : foehnTarget);
        } else {
            chinook = 0.0F;
        }
    }

    /** How strongly the Chinook arch shows, 0 to 1. */
    public static float chinook() {
        return chinook;
    }

    /** The sky azimuth (measured from north, as the sky renderer does) of the mountains the arch lies over. */
    public static float chinookAzimuth() {
        return chinookAngle;
    }

    /** Adds this frame's asperitas cloud base to the puff renderer. */
    public static void collect(Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || asperitas < 0.02F) return;
        Vec3 cam = camera.getPosition();
        float maxDistance = mc.gameRenderer.getDepthFar() * 0.9F;
        float daylight = level.getSkyDarken(partialTick);
        float time = level.getGameTime() + partialTick;
        float base = level.getSeaLevel() + 72.0F;
        Vec3 wind = Wind.direction(SyncedWeather.windAngle());
        // the waves run along the wind, the longer swell across it
        float step = 16.0F, reach = 168.0F;
        double gx = Math.floor(cam.x / step) * step, gz = Math.floor(cam.z / step) * step;
        int n = (int) (reach / step);
        for (int i = -n; i <= n; i++) {
            for (int j = -n; j <= n; j++) {
                double x = gx + i * step, z = gz + j * step;
                double dx = x - cam.x, dz = z - cam.z;
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > reach) continue;
                double along = x * wind.x + z * wind.z, across = -x * wind.z + z * wind.x;
                float wave = Mth.sin((float) (along * 0.045 - time * 0.012)) + 0.6F * Mth.sin((float) (across * 0.028 + along * 0.01 + time * 0.007))
                        + 0.3F * Mth.sin((float) (along * 0.11 + across * 0.07 - time * 0.02));
                float y = base + 5.5F * wave;
                // the troughs hang down dark; the crests catch a little more light
                float shade = (0.2F + 0.15F * (wave + 1.9F)) * (0.3F + 0.7F * daylight);
                float alpha = 0.8F * asperitas * (float) Mth.clamp((reach - dist) / 60.0, 0.0, 1.0);
                float size = step * 1.05F + 2.0F * PuffRenderer.hash(i * 977 + j, 31);
                PuffRenderer.add(x, y, z, size, shade, shade * 1.02F, shade * 1.08F, alpha, false, cam, maxDistance);
            }
        }
    }

    public static void reset() {
        asperitas = chinook = foehnTarget = 0.0F;
        lastFoehnCheck = -100;
    }

    private StormSkies() {}
}
