package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.weather.Climate;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.Wind;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Snow plumes: on a windy winter day the gale strips the loose snow off the mountain tops and streams it away downwind
 * in long, glittering white banners, which you can see from miles off against the blue sky.
 */
public final class SnowPlumes {
    private record Peak(double x, double z, int top) {}
    private static final List<Peak> PEAKS = new ArrayList<>();
    private static float strength;
    private static long lastScan = -1000;
    private static double windX = 1.0, windZ = 0.0;

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD || !SyncedWeather.managed() || !ClientConfig.SNOW_PLUMES.get()) {
            strength = 0.0F;
            PEAKS.clear();
            return;
        }
        boolean wintry = SyncedWeather.season() == Season.WINTER || ClientWeather.climate() == Climate.COLD;
        // a strong wind, and a sky clear enough to see them (heavy snowfall hides everything)
        float target = wintry ? Mth.clamp((ClientWeather.wind() - 0.4F) * 4.0F, 0.0F, 1.0F) * Mth.clamp(1.0F - ClientWeather.rain() * 2.0F, 0.0F, 1.0F)
                * Mth.clamp(1.0F - ClientWeather.fogDensity() * 2.0F, 0.0F, 1.0F) : 0.0F;
        strength = Mth.lerp(0.01F, strength, target);
        Vec3 w = Wind.direction(SyncedWeather.windAngle());
        windX = w.x;
        windZ = w.z;
        long now = level.getGameTime();
        if (strength > 0.03F && (now - lastScan >= 100 || now < lastScan)) {
            lastScan = now;
            scan(level, mc.gameRenderer.getMainCamera().getPosition());
        }
    }

    /** Finds the snowy summits round about: high points, above the ground near them, with snow on top. */
    private static void scan(ClientLevel level, Vec3 cam) {
        PEAKS.clear();
        List<float[]> found = new ArrayList<>();
        int sea = level.getSeaLevel();
        for (int r = 40; r <= 230; r += 30) {
            for (int i = 0; i < 20; i++) {
                float a = i * Mth.TWO_PI / 20.0F + r * 0.013F;
                int x = Mth.floor(cam.x + Mth.cos(a) * r), z = Mth.floor(cam.z + Mth.sin(a) * r);
                int h = height(level, x, z);
                if (h == Integer.MIN_VALUE || h < sea + 60) continue;
                // climb to the local top
                for (int step = 0; step < 6; step++) {
                    int bx = x, bz = z, bh = h;
                    for (int dx = -6; dx <= 6; dx += 6) {
                        for (int dz = -6; dz <= 6; dz += 6) {
                            int hh = height(level, x + dx, z + dz);
                            if (hh > bh) { bh = hh; bx = x + dx; bz = z + dz; }
                        }
                    }
                    if (bh == h) break;
                    x = bx; z = bz; h = bh;
                }
                if (!snowy(level.getBlockState(new BlockPos(x, h - 1, z))) && !snowy(level.getBlockState(new BlockPos(x, h, z)))) continue;
                found.add(new float[]{h, x, z});
            }
        }
        found.sort((p, q) -> Float.compare(q[0], p[0]));
        for (float[] f : found) {
            if (PEAKS.size() >= 4) break;
            if (PEAKS.stream().anyMatch(p -> (p.x() - f[1]) * (p.x() - f[1]) + (p.z() - f[2]) * (p.z() - f[2]) < 50 * 50)) continue;
            PEAKS.add(new Peak(f[1] + 0.5, f[2] + 0.5, (int) f[0]));
        }
    }

    private static boolean snowy(BlockState s) {
        return s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.PACKED_ICE);
    }

    private static int height(ClientLevel level, int x, int z) {
        BlockPos p = new BlockPos(x, 0, z);
        if (!SkySights.loaded(level, p)) return Integer.MIN_VALUE;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    /** Adds this frame's plumes to the puff renderer. */
    public static void collect(Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || strength < 0.03F || PEAKS.isEmpty()) return;
        Vec3 cam = camera.getPosition();
        float maxDistance = mc.gameRenderer.getDepthFar() * 0.9F;
        float lit = 0.4F + 0.6F * level.getSkyDarken(partialTick);
        float time = level.getGameTime() + partialTick;
        double px = -windZ, pz = windX;
        int id = 0;
        for (Peak peak : PEAKS) {
            id++;
            int n = 26;
            float length = 50.0F + 30.0F * strength;
            for (int i = 0; i < n; i++) {
                // each puff streams away from the summit and thins out, then the next takes its place
                float t = (time * 0.006F + i / (float) n + PuffRenderer.hash(id * 131 + i, 91) * 0.04F) % 1.0F;
                float along = t * length;
                double side = (PuffRenderer.hash(id * 131 + i, 92) - 0.5) * (2.0 + 9.0 * t);
                double x = peak.x() + windX * along + px * side, z = peak.z() + windZ * along + pz * side;
                // lifted a little off the crest, then sinking slowly as it spreads
                float y = peak.top() + 1.5F + 4.0F * Mth.sin(t * Mth.PI * 0.8F) - 2.0F * t * t;
                float alpha = 0.65F * strength * (float) Math.pow(1.0F - t, 1.4F) * Mth.clamp(t * 10.0F, 0.0F, 1.0F);
                float shade = Math.min(1.0F, lit * 1.05F);
                PuffRenderer.add(x, y, z, 2.0F + 6.0F * t, shade, shade, shade * 1.03F, alpha, false, cam, maxDistance);
            }
        }
    }

    static int peaks() {
        return PEAKS.size();
    }

    /** For the F3 screen: where the highest plume is streaming from. */
    static String first() {
        return PEAKS.isEmpty() ? "" : String.format(java.util.Locale.ROOT, " @%d,%d,%d", (int) PEAKS.get(0).x(), PEAKS.get(0).top(), (int) PEAKS.get(0).z());
    }

    static float strength() {
        return strength;
    }

    public static void reset() {
        strength = 0.0F;
        PEAKS.clear();
        lastScan = -1000;
    }

    private SnowPlumes() {}
}
