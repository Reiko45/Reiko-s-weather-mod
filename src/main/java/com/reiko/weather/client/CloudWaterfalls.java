package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.weather.Wind;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Cloud waterfalls: when the wind pushes a layer of fog or low cloud against a ridge, it piles up on the windward side,
 * spills over the crest and tumbles down the far slope like a slow white waterfall, melting away as it sinks into the
 * warmer, drier air below.
 */
public final class CloudWaterfalls {
    private record Crest(double x, double z, int top, float[] slope) {}
    private static final List<Crest> CRESTS = new ArrayList<>();
    private static final float LENGTH = 44.0F;
    private static float strength;
    private static long lastScan = -1000;
    private static double windX = 1.0, windZ = 0.0;

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD || !SyncedWeather.managed() || !ClientConfig.CLOUD_WATERFALLS.get()) {
            strength = 0.0F;
            CRESTS.clear();
            return;
        }
        float wind = ClientWeather.wind();
        float moisture = Math.max(Math.max(ClientWeather.fogDensity() * 1.5F, ClientWeather.mist()), Math.max(SkySights.inversion(), ClientWeather.overcast() * 0.7F));
        float target = Mth.clamp(moisture, 0.0F, 1.0F) * Mth.clamp((wind - 0.08F) * 5.0F, 0.0F, 1.0F) * Mth.clamp(1.0F - ClientWeather.rain() * 2.0F, 0.0F, 1.0F);
        strength = Mth.lerp(0.01F, strength, target);
        Vec3 w = Wind.direction(SyncedWeather.windAngle());
        windX = w.x;
        windZ = w.z;
        long now = level.getGameTime();
        if (strength > 0.03F && (now - lastScan >= 60 || now < lastScan)) {
            lastScan = now;
            scan(level, mc.gameRenderer.getMainCamera().getPosition());
        }
    }

    /** Finds the ridges round about that the cloud is spilling over: high ground falling away steeply downwind. */
    private static void scan(ClientLevel level, Vec3 cam) {
        CRESTS.clear();
        List<float[]> found = new ArrayList<>();
        int sea = level.getSeaLevel();
        for (int r = 48; r <= 160; r += 28) {
            for (int i = 0; i < 24; i++) {
                float a = i * Mth.TWO_PI / 24.0F + r * 0.01F;
                double x = cam.x + Mth.cos(a) * r, z = cam.z + Mth.sin(a) * r;
                int h = height(level, x, z), up = height(level, x - windX * 20, z - windZ * 20), down = height(level, x + windX * 24, z + windZ * 24);
                if (h == Integer.MIN_VALUE || up == Integer.MIN_VALUE || down == Integer.MIN_VALUE) continue;
                if (h < sea + 25 || h < up - 2 || h - down < 12) continue;
                found.add(new float[]{h - down, (float) x, (float) z, h});
            }
        }
        found.sort((p, q) -> Float.compare(q[0], p[0]));
        for (float[] f : found) {
            if (CRESTS.size() >= 3) break;
            if (CRESTS.stream().anyMatch(c -> (c.x() - f[1]) * (c.x() - f[1]) + (c.z() - f[2]) * (c.z() - f[2]) < 40 * 40)) continue;
            float[] slope = new float[9];
            boolean ok = true;
            for (int k = 0; k < 9 && ok; k++) {
                int g = height(level, f[1] + windX * LENGTH * k / 8.0, f[2] + windZ * LENGTH * k / 8.0);
                ok = g != Integer.MIN_VALUE;
                slope[k] = g;
            }
            if (ok) CRESTS.add(new Crest(f[1], f[2], (int) f[3], slope));
        }
    }

    private static int height(ClientLevel level, double x, double z) {
        BlockPos p = BlockPos.containing(x, 0, z);
        if (!SkySights.loaded(level, p)) return Integer.MIN_VALUE;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ());
    }

    /** Adds this frame's spilling cloud to the puff renderer. */
    public static void collect(Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || strength < 0.03F || CRESTS.isEmpty()) return;
        Vec3 cam = camera.getPosition();
        float maxDistance = mc.gameRenderer.getDepthFar() * 0.9F;
        float lit = 0.35F + 0.65F * level.getSkyDarken(partialTick);
        float shade = Math.min(1.0F, 1.05F * lit);
        // (it's thick cloud, so even a middling spell of it is a solid white mass)
        float dense = Mth.clamp(strength * 1.8F, 0.0F, 1.0F);
        float time = level.getGameTime() + partialTick;
        double px = -windZ, pz = windX;
        int id = 0;
        for (Crest c : CRESTS) {
            id++;
            // the cloud piled up against the windward side of the ridge
            for (int i = 0; i < 8; i++) {
                double back = 2 + i * 3.5, side = (PuffRenderer.hash(id * 31 + i, 51) - 0.5) * 26;
                PuffRenderer.add(c.x() - windX * back + px * side, c.top() + 2.0F + 4.0F * PuffRenderer.hash(id * 31 + i, 52), c.z() - windZ * back + pz * side,
                        13.0F + 6.0F * PuffRenderer.hash(id * 31 + i, 53), shade, shade, shade * 1.02F, 0.75F * dense, false, cam, maxDistance);
            }
            // and the stream of it pouring over the crest and down the slope, thinning out as it sinks
            int n = 28;
            for (int i = 0; i < n; i++) {
                float t = (time * 0.0025F + i / (float) n + PuffRenderer.hash(id * 97 + i, 54) * 0.05F) % 1.0F;
                float along = t * LENGTH;
                int k = Math.min(7, (int) (t * 8.0F));
                float ground = Mth.lerp(t * 8.0F - k, c.slope()[k], c.slope()[k + 1]);
                double side = (PuffRenderer.hash(id * 97 + i, 55) - 0.5) * (14.0 + 10.0 * t);
                double x = c.x() + windX * along + px * side, z = c.z() + windZ * along + pz * side;
                float y = Math.max(ground, c.top() - along * 1.2F) + 2.0F + (1.0F - t) * 2.5F;
                float alpha = 0.85F * dense * (float) Math.pow(1.0F - t, 1.1F) * Mth.clamp(t * 8.0F, 0.0F, 1.0F);
                PuffRenderer.add(x, y, z, 8.0F + 9.0F * t, shade, shade, shade * 1.02F, alpha, false, cam, maxDistance);
            }
        }
    }

    /** For the F3 screen: how strongly the cloud is pouring, and over which ridges. */
    static String debug() {
        return String.format(java.util.Locale.ROOT, "%.2f (%d ridges)", strength, CRESTS.size());
    }

    public static void reset() {
        strength = 0.0F;
        CRESTS.clear();
        lastScan = -1000;
    }

    private CloudWaterfalls() {}
}
