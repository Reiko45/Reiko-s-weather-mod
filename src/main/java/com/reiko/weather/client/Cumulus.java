package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.weather.Climate;
import com.reiko.weather.weather.CustomWeathers;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.SkyPhenomena;
import com.reiko.weather.weather.Wind;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Fair-weather cumulus: on warm, sunny days, the sun heats the ground until bubbles of warm air rise and their
 * moisture condenses into flat-bottomed, cauliflower-topped clouds. In a breeze the rising air is rolled into long
 * spirals lying along the wind, and the clouds line up over them in rows: cloud streets. When one drifts across the
 * sun, the light dims for a moment and brightens again.
 */
public final class Cumulus {
    /** How far apart the rows (across the wind) and the clouds in a row are, in blocks. */
    private static final float ROW = 96.0F, SLOT = 64.0F;
    private static float strength, streets, shade;
    private static float drift, step;
    private static double windX = 1.0, windZ = 0.0;

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD || !SyncedWeather.managed()) {
            strength = streets = shade = 0.0F;
            return;
        }
        float sun = SkyPhenomena.sunHeight(level, 1.0F);
        Season season = SyncedWeather.season();
        float seasonal = season == Season.SUMMER ? 1.0F : season == Season.SPRING ? 0.8F : season == Season.AUTUMN ? 0.5F : season == Season.WINTER ? 0.15F : 0.8F;
        // the ground has to warm up first: they grow through the morning, peak in the afternoon and die away at dusk
        float day = Mth.clamp((sun - 0.15F) * 4.0F, 0.0F, 1.0F);
        float fair = Mth.clamp(1.0F - ClientWeather.overcast() * 2.2F, 0.0F, 1.0F) * (ClientWeather.rain() > 0.05F ? 0.0F : 1.0F)
                * (1.0F - Math.min(1.0F, ClientWeather.fogDensity() * 2.0F));
        // (the sinking air of a föhn kills them off)
        float target = day * fair * seasonal * (ClientWeather.climate() == Climate.ARID ? 0.3F : 1.0F) * (1.0F - StormSkies.chinook());
        strength = Mth.lerp(0.01F, strength, target);
        float prevailing = CustomWeathers.backgroundProfile(SyncedWeather.type(), CustomWeathers.client(SyncedWeather.custom()), ClientWeather.climate()).wind;
        // a steady breeze lines them up in streets; in calm air they're scattered anyhow, and a gale tears them apart
        float breeze = Math.max(prevailing, ClientWeather.wind());
        streets = Mth.lerp(0.005F, streets, Mth.clamp((breeze - 0.15F) / 0.2F, 0.0F, 1.0F) * Mth.clamp((0.95F - breeze) / 0.2F, 0.0F, 1.0F));
        Vec3 wind = Wind.direction(SyncedWeather.windAngle());
        windX = wind.x;
        windZ = wind.z;
        step = 0.12F + 0.3F * breeze;
        drift += step;
        if (drift > 1.0E6F) drift = 0.0F;
        // is a cloud between us and the sun?
        float covered = 0.0F;
        if (strength > 0.02F && sun > 0.05F && ClientConfig.CLOUD_SHADE.get()) {
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            float theta = level.getTimeOfDay(1.0F) * Mth.TWO_PI;
            double sx = -Mth.sin(theta), sy = Mth.cos(theta);
            double t = (base(level) + 6.0 - cam.y) / Math.max(0.08, sy);
            if (t > 0.0) covered = coverage(cam.x + sx * t, cam.z) * strength;
        }
        shade = Mth.lerp(0.06F, shade, covered);
    }

    /** How much the passing cloud shades the sun, 0 to 1. */
    public static float shade() {
        return ClientConfig.SPEC.isLoaded() && ClientConfig.CLOUD_SHADE.get() ? shade : 0.0F;
    }

    private static float base(ClientLevel level) {
        return level.getSeaLevel() + 100.0F;
    }

    /** One cloud's slot in the field: {along, across, size}, or null where the slot is empty. */
    private static float[] cloud(int row, int slot) {
        int seed = row * 7919 + slot * 104729;
        if (PuffRenderer.hash(seed, 1) > 0.55F + 0.2F * streets) return null;
        // in streets they sit close to the line of their row; scattered, they wander right across the gap
        float across = (row + 0.5F) * ROW + (PuffRenderer.hash(seed, 2) - 0.5F) * ROW * Mth.lerp(streets, 0.85F, 0.22F);
        float along = (slot + 0.5F) * SLOT + (PuffRenderer.hash(seed, 3) - 0.5F) * SLOT * 0.6F;
        float size = 15.0F + 17.0F * PuffRenderer.hash(seed, 4);
        return new float[]{along, across, size};
    }

    /** How much cloud there is overhead at a spot, 0 to 1. */
    private static float coverage(double x, double z) {
        double pa = x * windX + z * windZ - drift, pc = -x * windZ + z * windX;
        int r0 = Mth.floor(pc / ROW), k0 = Mth.floor(pa / SLOT);
        float most = 0.0F;
        for (int r = r0 - 1; r <= r0 + 1; r++) {
            for (int k = k0 - 1; k <= k0 + 1; k++) {
                float[] c = cloud(r, k);
                if (c == null) continue;
                double d = Math.sqrt((pa - c[0]) * (pa - c[0]) + (pc - c[1]) * (pc - c[1])) / (c[2] * 1.1);
                most = Math.max(most, (float) Mth.clamp((1.0 - d) * 2.5, 0.0, 1.0));
            }
        }
        return most;
    }

    /** Adds this frame's clouds to the puff renderer. */
    public static void collect(Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || strength < 0.02F || !ClientConfig.CLOUD_STREETS.get()) return;
        Vec3 cam = camera.getPosition();
        float maxDistance = mc.gameRenderer.getDepthFar() * 0.9F;
        float daylight = level.getSkyDarken(partialTick);
        float fade = 1.0F - 0.6F * ClientWeather.fogDensity();
        float d = drift + step * partialTick;
        double ca = cam.x * windX + cam.z * windZ - d, cc = -cam.x * windZ + cam.z * windX;
        float base = base(level);
        float reach = 430.0F;
        int r0 = Mth.floor((cc - reach) / ROW), r1 = Mth.floor((cc + reach) / ROW);
        int k0 = Mth.floor((ca - reach) / SLOT), k1 = Mth.floor((ca + reach) / SLOT);
        for (int r = r0; r <= r1; r++) {
            for (int k = k0; k <= k1; k++) {
                float[] c = cloud(r, k);
                if (c == null) continue;
                double along = c[0] + d, across = c[1];
                double x = along * windX - across * windZ, z = along * windZ + across * windX;
                double dist = Math.sqrt((x - cam.x) * (x - cam.x) + (z - cam.z) * (z - cam.z));
                if (dist > reach) continue;
                float alpha = 0.85F * strength * fade * (float) Mth.clamp((reach - dist) / 120.0, 0.0, 1.0);
                puffs(x, base + 4.0F * PuffRenderer.hash(r * 7919 + k, 5), z, c[2], daylight, alpha, cam, maxDistance);
            }
        }
    }

    /** One cumulus: a flat, greyer base and a bright, bulging top. */
    private static void puffs(double x, float y, double z, float size, float daylight, float alpha, Vec3 cam, float maxDistance) {
        float lit = 0.25F + 0.75F * daylight;
        for (int i = 0; i < 3; i++) {
            double off = (i - 1) * size * 0.45;
            float shade = 0.9F * lit;
            PuffRenderer.add(x + windX * off, y, z + windZ * off, size * 0.7F, shade, shade, shade * 1.03F, alpha, false, cam, maxDistance);
        }
        float top = 1.0F * lit;
        PuffRenderer.add(x + windZ * size * 0.1, y + size * 0.55F, z - windX * size * 0.1, size * 0.62F, top, top, top, alpha, false, cam, maxDistance);
        if (size > 20.0F) {
            PuffRenderer.add(x, y + size * 0.95F, z, size * 0.45F, top, top, top, alpha * 0.9F, false, cam, maxDistance);
        }
    }

    public static void reset() {
        strength = streets = shade = 0.0F;
    }

    private Cumulus() {}
}
