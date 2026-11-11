package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.weather.Wind;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Scud: ragged shreds of low cloud racing along under a storm's cloud base, much lower and much faster than the deck
 * above them, torn off by the gusty, rain-cooled air. Ominous, but harmless.
 */
public final class Scud {
    private static final float SPACING = 46.0F, REACH = 300.0F;
    private static float strength;
    private static float drift, step;
    private static double windX = 1.0, windZ = 0.0;

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD || !SyncedWeather.managed() || !ClientConfig.SCUD.get()) {
            strength = 0.0F;
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        // under a storm's dark base, in a strong, gusty wind
        float deck = Math.max(ClientWeather.cloudsAt(cam.x, cam.z), ClientWeather.overcast() * 0.8F);
        // (it forms in the rain-cooled, gusty air flowing out under the storm: the more wind and rain, the more of it)
        float target = Mth.clamp((deck - 0.45F) * 2.5F, 0.0F, 1.0F) * Mth.clamp(0.3F + ClientWeather.wind() * 1.5F + ClientWeather.rain() * 0.5F, 0.0F, 1.0F);
        strength = Mth.lerp(0.01F, strength, target);
        Vec3 w = Wind.direction(SyncedWeather.windAngle());
        windX = w.x;
        windZ = w.z;
        step = 0.35F + 0.9F * ClientWeather.wind();
        drift += step;
        if (drift > 1.0E6F) drift = 0.0F;
    }

    /** Adds this frame's scud to the puff renderer. */
    public static void collect(Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || strength < 0.02F || !DriftingClouds.cloudsWanted(mc)) return;
        Vec3 cam = camera.getPosition();
        float maxDistance = mc.gameRenderer.getDepthFar() * 0.9F;
        float d = drift + step * partialTick;
        double ca = cam.x * windX + cam.z * windZ - d, cc = -cam.x * windZ + cam.z * windX;
        float lit = 0.25F + 0.4F * level.getSkyDarken(partialTick);
        float base = level.getSeaLevel() + 64.0F;
        int i0 = Mth.floor((ca - REACH) / SPACING), i1 = Mth.floor((ca + REACH) / SPACING);
        int j0 = Mth.floor((cc - REACH) / SPACING), j1 = Mth.floor((cc + REACH) / SPACING);
        for (int i = i0; i <= i1; i++) {
            for (int j = j0; j <= j1; j++) {
                int seed = i * 73471 + j * 19937;
                if (PuffRenderer.hash(seed, 61) > strength * 0.75F) continue;
                double along = (i + PuffRenderer.hash(seed, 62)) * SPACING + d, across = (j + PuffRenderer.hash(seed, 63)) * SPACING;
                double x = along * windX - across * windZ, z = along * windZ + across * windX;
                double dist = Math.sqrt((x - cam.x) * (x - cam.x) + (z - cam.z) * (z - cam.z));
                if (dist > REACH) continue;
                float alpha = 0.55F * strength * (float) Mth.clamp((REACH - dist) / 80.0, 0.0, 1.0);
                float y = base + 14.0F * PuffRenderer.hash(seed, 64);
                // a torn fragment: a few small puffs strung out along the wind, darker underneath
                int n = 6 + (int) (PuffRenderer.hash(seed, 65) * 5.0F);
                float len = 14.0F + 18.0F * PuffRenderer.hash(seed, 66);
                for (int k = 0; k < n; k++) {
                    float u = (k / (float) Math.max(1, n - 1) - 0.5F) * len, v = (PuffRenderer.hash(seed + k, 67) - 0.5F) * 3.0F;
                    float size = (2.5F + 3.0F * PuffRenderer.hash(seed + k, 68)) * (1.0F - 0.5F * Math.abs(u) / len);
                    float shade = lit * (0.62F + 0.15F * PuffRenderer.hash(seed + k, 69));
                    PuffRenderer.add(x + windX * u - windZ * v, y + 2.0F * PuffRenderer.hash(seed + k, 70), z + windZ * u + windX * v, size,
                            shade, shade, shade * 1.03F, alpha, false, cam, maxDistance);
                }
            }
        }
    }

    static float strength() {
        return strength;
    }

    public static void reset() {
        strength = 0.0F;
    }

    private Scud() {}
}
