package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.weather.Climate;
import com.reiko.weather.weather.SkyPhenomena;
import com.reiko.weather.weather.Wind;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The everyday clouds, drawn in place of Minecraft's blocky ones: soft, flat-bottomed patches of stratocumulus and
 * altocumulus drifting along on the wind aloft. How much of the sky they cover wanders through the day, from nearly
 * clear to well clouded, and closes in as the weather turns overcast; they're white by day, warm at sunrise and
 * sunset, and dim grey under the moon. (Storms bring their own low, dark deck, and fair afternoons their own cumulus.)
 */
public final class DriftingClouds {
    /** How far apart the cloud slots are, and how far out the clouds are drawn, in blocks. */
    private static final float SPACING = 92.0F, REACH = 520.0F;
    private static float cover;
    private static float drift, step;
    private static double windX = 1.0, windZ = 0.0;

    /** Whether the mod's clouds stand in for Minecraft's here (and so Minecraft's aren't drawn). */
    public static boolean replacing() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension() == Level.OVERWORLD && SyncedWeather.managed()
                && ClientConfig.SPEC.isLoaded() && ClientConfig.REPLACE_VANILLA_CLOUDS.get();
    }

    /** Whether the player wants clouds drawn at all (Minecraft's own "Clouds" video setting). */
    static boolean cloudsWanted(Minecraft mc) {
        return mc.options.getCloudsType() != CloudStatus.OFF;
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || !replacing()) {
            cover = 0.0F;
            return;
        }
        long time = level.getGameTime();
        // the sky clouds over and clears again through the day, and closes in as the weather turns
        float wander = 0.5F + 0.28F * Mth.sin(time / 9000.0F) + 0.22F * Mth.sin(time / 23000.0F + 1.7F);
        float target = 0.18F + 0.42F * wander + 0.6F * ClientWeather.overcast();
        if (ClientWeather.climate() == Climate.ARID) target *= 0.5F;
        // a föhn clears the sky downwind of the mountains (the Chinook arch's deck stands in for them)
        target *= 1.0F - StormSkies.chinook();
        cover = Mth.lerp(0.002F, cover, Mth.clamp(target, 0.0F, 0.92F));
        Vec3 wind = Wind.direction(SyncedWeather.windAngle());
        windX = wind.x;
        windZ = wind.z;
        step = 0.06F + 0.25F * ClientWeather.wind();
        drift += step;
        if (drift > 1.0E6F) drift = 0.0F;
    }

    private static float base(ClientLevel level) {
        return level.getSeaLevel() + 124.0F;
    }

    /** One cloud's slot: {along, across, length, width}, or null where the sky is clear. */
    private static float[] cloud(int i, int j) {
        int seed = i * 92821 + j * 68917;
        if (PuffRenderer.hash(seed, 21) > cover) return null;
        float along = (i + 0.5F) * SPACING + (PuffRenderer.hash(seed, 22) - 0.5F) * SPACING * 0.7F;
        float across = (j + 0.5F) * SPACING + (PuffRenderer.hash(seed, 23) - 0.5F) * SPACING * 0.7F;
        // stretched out along the wind, and bigger where the sky is more clouded
        float length = (22.0F + 30.0F * PuffRenderer.hash(seed, 24)) * (0.8F + 0.5F * cover);
        float width = length * (0.45F + 0.3F * PuffRenderer.hash(seed, 25));
        return new float[]{along, across, length, width};
    }

    /** How much of the sun's disc a spot on the cloud layer hides, 0 to 1. */
    static float coverage(ClientLevel level, double camX, double camY, double camZ, double sunX, double sunY) {
        if (cover < 0.02F || sunY < 0.05) return 0.0F;
        double t = (base(level) + 2.0 - camY) / Math.max(0.08, sunY);
        if (t <= 0.0) return 0.0F;
        double x = camX + sunX * t, z = camZ;
        double pa = x * windX + z * windZ - drift, pc = -x * windZ + z * windX;
        int i0 = Mth.floor(pa / SPACING), j0 = Mth.floor(pc / SPACING);
        float most = 0.0F;
        for (int i = i0 - 1; i <= i0 + 1; i++) {
            for (int j = j0 - 1; j <= j0 + 1; j++) {
                float[] c = cloud(i, j);
                if (c == null) continue;
                double du = (pa - c[0]) / (c[2] * 0.6), dv = (pc - c[1]) / (c[3] * 0.6);
                most = Math.max(most, (float) Mth.clamp((1.0 - Math.sqrt(du * du + dv * dv)) * 2.5, 0.0, 1.0));
            }
        }
        return most;
    }

    /** Adds this frame's clouds to the puff renderer. */
    public static void collect(Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || cover < 0.02F || !replacing() || !cloudsWanted(mc)) return;
        Vec3 cam = camera.getPosition();
        float maxDistance = mc.gameRenderer.getDepthFar() * 0.9F;
        float daylight = level.getSkyDarken(partialTick);
        float fade = 1.0F - Math.min(1.0F, ClientWeather.fogDensity() * 1.3F);
        if (fade <= 0.02F) return;
        float d = drift + step * partialTick;
        double ca = cam.x * windX + cam.z * windZ - d, cc = -cam.x * windZ + cam.z * windX;
        float base = base(level);
        float[] tint = sunlit(level, partialTick, daylight);
        int i0 = Mth.floor((ca - REACH) / SPACING), i1 = Mth.floor((ca + REACH) / SPACING);
        int j0 = Mth.floor((cc - REACH) / SPACING), j1 = Mth.floor((cc + REACH) / SPACING);
        for (int i = i0; i <= i1; i++) {
            for (int j = j0; j <= j1; j++) {
                float[] c = cloud(i, j);
                if (c == null) continue;
                double along = c[0] + d, across = c[1];
                double x = along * windX - across * windZ, z = along * windZ + across * windX;
                double dist = Math.sqrt((x - cam.x) * (x - cam.x) + (z - cam.z) * (z - cam.z));
                if (dist > REACH) continue;
                // (the storm deck lies below these, so where it covers the sky they're hidden behind it)
                float deck = ClientConfig.STORM_CLOUDS.get() ? ClientWeather.cloudsAt(x, z) : 0.0F;
                float alpha = 0.78F * fade * (1.0F - deck) * (float) Mth.clamp((REACH - dist) / 140.0, 0.0, 1.0) * Mth.clamp(cover * 4.0F, 0.0F, 1.0F);
                if (alpha < 0.01F) continue;
                patch(i * 92821 + j * 68917, x, base, z, c[2], c[3], tint, alpha, cam, maxDistance);
            }
        }
    }

    /** One cloud: a flat-bottomed patch of soft puffs, greyer underneath and brighter on top. */
    private static void patch(int seed, double x, float y, double z, float length, float width, float[] tint, float alpha, Vec3 cam, float maxDistance) {
        int n = 4 + (int) (length / 9.0F);
        for (int k = 0; k < n; k++) {
            float u = (PuffRenderer.hash(seed + k, 31) - 0.5F) * length, v = (PuffRenderer.hash(seed + k, 32) - 0.5F) * width;
            // round off the ends
            float edge = 1.0F - Math.abs(u) / (length * 0.5F);
            v *= 0.4F + 0.6F * edge;
            boolean top = PuffRenderer.hash(seed + k, 33) < 0.4F;
            float size = (9.0F + 6.0F * PuffRenderer.hash(seed + k, 34)) * (0.6F + 0.4F * edge) * (top ? 0.85F : 1.0F);
            float shade = top ? 1.0F : 0.86F;
            double px = x + windX * u - windZ * v, pz = z + windZ * u + windX * v;
            PuffRenderer.add(px, y + (top ? 3.5F : 0.0F) + PuffRenderer.hash(seed + k, 35) * 1.5F, pz, size,
                    tint[0] * shade, tint[1] * shade, tint[2] * shade, alpha, false, cam, maxDistance);
        }
    }

    /** The clouds' colour: white by day, warm gold and pink near sunrise and sunset, dim grey by night. */
    static float[] sunlit(ClientLevel level, float partialTick, float daylight) {
        // (and greyer as the weather clouds over)
        float lit = (0.22F + 0.78F * daylight) * (1.0F - 0.4F * ClientWeather.overcast());
        float sun = SkyPhenomena.sunHeight(level, partialTick);
        float glow = Mth.clamp(1.0F - Math.abs(sun - 0.04F) / 0.18F, 0.0F, 1.0F) * (1.0F - ClientWeather.overcast());
        return new float[]{Math.min(1.0F, lit * (1.0F + 0.3F * glow)), lit * (1.0F - 0.02F * glow), lit * (1.0F - 0.18F * glow) * 1.02F};
    }

    public static void reset() {
        cover = 0.0F;
    }

    private DriftingClouds() {}
}
