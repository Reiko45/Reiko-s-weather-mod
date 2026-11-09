package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
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
 * Rarer cloud shapes: roll clouds (long, detached tubes of cloud rolling ahead of some storms), breaking-wave
 * clouds (Kelvin-Helmholtz billows) where a fast wind slides over slower air, hole-punch clouds (a round gap in a
 * sheet of high cloud with a streak of falling ice in the middle), and spider lightning crawling along the
 * underside of storm clouds at night.
 */
public final class CloudForms {
    public static void collect(Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD || !SyncedWeather.managed()) return;
        Vec3 cam = camera.getPosition();
        float maxDistance = mc.gameRenderer.getDepthFar() * 0.9F;
        float daylight = level.getSkyDarken(partialTick);
        float time = level.getGameTime() + partialTick;
        if (ClientConfig.CLOUD_FORMS.get()) {
            for (StormCell cell : SyncedWeather.cells()) {
                if ((cell.type == WeatherType.THUNDERSTORM || cell.type == WeatherType.COLD_FRONT) && PuffRenderer.hash(cell.id, 77) < 0.4F
                        && cell.strength() > 0.3F) {
                    rollCloud(level, cell, cam, maxDistance, daylight);
                }
            }
            long day = level.getDayTime() / 24000L;
            float overcast = ClientWeather.overcast(), rain = ClientWeather.rain();
            if (rain < 0.05F && ClientWeather.wind() > 0.45F && overcast > 0.15F && overcast < 0.6F && PuffRenderer.hash((int) day, 501) < 0.3F) {
                waveClouds(level, cam, maxDistance, daylight, time);
            }
            if (rain < 0.05F && overcast > 0.2F && overcast < 0.55F && level.isDay() && PuffRenderer.hash((int) day, 911) < 0.2F) {
                holePunch(level, cam, maxDistance, daylight, (int) day);
            }
        }
        if (ClientConfig.SPIDER_LIGHTNING.get() && !Accessibility.reduceFlashing()) {
            spiderLightning(level, cam, maxDistance, time);
        }
    }

    /** A roll cloud: a long, smooth tube of cloud lying across the storm's path, out ahead of it and low down. */
    private static void rollCloud(ClientLevel level, StormCell cell, Vec3 cam, float maxDistance, float daylight) {
        double speed = Math.sqrt(cell.vx * cell.vx + cell.vz * cell.vz);
        if (speed < 1.0E-6) return;
        double fx = cell.vx / speed, fz = cell.vz / speed;
        double cx = cell.x + fx * cell.radius * 1.15, cz = cell.z + fz * cell.radius * 1.15;
        float y = level.getSeaLevel() + 42.0F;
        float length = cell.radius * Math.max(1.0F, cell.elongation()) * 1.6F;
        float shade = 0.62F * (0.25F + 0.75F * daylight);
        int n = Math.min(60, Math.max(12, Math.round(length / 9.0F)));
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1) - 0.5F;
            double x = cx - fz * t * length, z = cz + fx * t * length;
            float taper = Mth.sqrt(Math.max(0.0F, 1.0F - 4.0F * t * t));
            float wobble = 2.0F * Mth.sin(t * 9.0F + cell.id);
            PuffRenderer.add(x, y + wobble, z, 10.0F + 6.0F * taper, shade, shade, shade * 1.04F, 0.7F * cell.strength() * taper, false, cam, maxDistance);
        }
    }

    /** Breaking-wave clouds: a row of curling crests, like waves breaking, drifting along with the wind aloft. */
    private static void waveClouds(ClientLevel level, Vec3 cam, float maxDistance, float daylight, float time) {
        Vec3 wind = Wind.direction(SyncedWeather.windAngle());
        float y = level.getSeaLevel() + 96.0F;
        double drift = (time * 0.08) % 400.0;
        double baseX = Math.floor(cam.x / 400.0) * 400.0 + wind.x * drift, baseZ = Math.floor(cam.z / 400.0) * 400.0 + wind.z * drift;
        float shade = 0.95F * (0.25F + 0.75F * daylight);
        for (int w = 0; w < 7; w++) {
            double ox = baseX + wind.x * (w - 3) * 28, oz = baseZ + wind.z * (w - 3) * 28;
            // each crest: a curl rising, rolling over downwind and breaking
            for (int k = 0; k < 9; k++) {
                float a = k / 8.0F * Mth.PI * 1.3F;
                double along = Mth.sin(a) * 10.0, up = (1.0F - Mth.cos(a)) * 7.0;
                for (int s = -2; s <= 2; s++) {
                    double x = ox + wind.x * along - wind.z * s * 14, z = oz + wind.z * along + wind.x * s * 14;
                    PuffRenderer.add(x, y + up, z, 6.0F, shade, shade, shade, 0.45F * (1.0F - Math.abs(s) * 0.15F), false, cam, maxDistance);
                }
            }
        }
    }

    /** A hole-punch cloud: a sheet of high cloud with a round hole in it, and a wisp of falling ice in the middle. */
    private static void holePunch(ClientLevel level, Vec3 cam, float maxDistance, float daylight, int day) {
        float a = PuffRenderer.hash(day, 3) * Mth.TWO_PI;
        double cx = cam.x + Mth.cos(a) * 160, cz = cam.z + Mth.sin(a) * 160;
        cx = Math.floor(cx / 64.0) * 64.0;
        cz = Math.floor(cz / 64.0) * 64.0;
        float y = level.getSeaLevel() + 120.0F;
        float shade = 0.9F * (0.25F + 0.75F * daylight);
        for (int i = 0; i < 90; i++) {
            float r = 30.0F + PuffRenderer.hash(i, day + 13) * 70.0F, t = PuffRenderer.hash(i, day + 29) * Mth.TWO_PI;
            PuffRenderer.add(cx + Mth.cos(t) * r, y + PuffRenderer.hash(i, day + 41) * 3.0F, cz + Mth.sin(t) * r, 12.0F, shade, shade, shade * 1.02F,
                    0.5F * Mth.clamp((100.0F - r) / 25.0F, 0.0F, 1.0F), false, cam, maxDistance);
        }
        // the fallstreak: ice crystals drifting down out of the middle of the hole
        for (int k = 0; k < 8; k++) {
            float f = k / 7.0F;
            PuffRenderer.add(cx + f * 6.0, y - f * 26.0F, cz, 9.0F - 4.0F * f, shade, shade, shade * 1.05F, 0.35F * (1.0F - f), false, cam, maxDistance);
        }
    }

    /**
     * Spider lightning: at night, under a big storm, long branching discharges flicker out across the underside of
     * the cloud for a moment.
     */
    private static void spiderLightning(ClientLevel level, Vec3 cam, float maxDistance, float time) {
        if (level.isDay()) return;
        for (StormCell cell : SyncedWeather.cells()) {
            if (!cell.type.vanillaThunder || cell.strength() < 0.5F || cell.distanceTo(cam.x, cam.z) > cell.radius + 260) continue;
            // each storm gets a crawler every few seconds, lasting a fraction of a second
            int slot = (int) (time / 70.0F);
            float into = time / 70.0F - slot;
            if (into > 0.12F || PuffRenderer.hash(slot, cell.id) > 0.6F) continue;
            float flicker = into < 0.04F ? 1.0F : into < 0.07F ? 0.3F : 0.9F;
            float base = level.getSeaLevel() + 76.0F;
            int seed = slot * 31 + cell.id;
            double x = cell.x + (PuffRenderer.hash(seed, 1) - 0.5F) * cell.radius, z = cell.z + (PuffRenderer.hash(seed, 2) - 0.5F) * cell.radius;
            float heading = PuffRenderer.hash(seed, 3) * Mth.TWO_PI;
            for (int branch = 0; branch < 4; branch++) {
                double bx = x, bz = z;
                float h = heading + (branch - 1.5F) * 0.7F;
                for (int k = 0; k < 18; k++) {
                    h += (PuffRenderer.hash(seed + branch * 97, k) - 0.5F) * 0.9F;
                    bx += Mth.cos(h) * 6.0;
                    bz += Mth.sin(h) * 6.0;
                    PuffRenderer.add(bx, base - PuffRenderer.hash(seed, k + 50) * 3.0F, bz, 2.4F - k * 0.08F, 0.85F, 0.9F, 1.0F,
                            0.9F * flicker * (1.0F - k / 22.0F), true, cam, maxDistance);
                }
            }
        }
    }

    private CloudForms() {}
}
