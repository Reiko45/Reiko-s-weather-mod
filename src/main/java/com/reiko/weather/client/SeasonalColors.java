package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.Seasons;
import com.reiko.weather.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Season-tinted grass and foliage: golden and red-orange leaves in autumn, dull dormant colours in
 * winter, slowly greening up again in spring. Colours change in steps, and only then are chunks re-meshed.
 */
public final class SeasonalColors {
    private static final int[] AUTUMN_LEAVES = {0xE07B26, 0xC23B22, 0xE8C33A, 0xB8652A, 0xD9A23A};
    private static final int AUTUMN_GRASS = 0xB59A4A;
    private static final int WINTER_LEAVES = 0x8A7F5A;
    private static final int WINTER_GRASS = 0x9B9563;

    /** Read on chunk-meshing threads. */
    private static volatile float autumn;
    private static volatile float winter;
    private static long lastRebuild;
    private static boolean rebuildPending;
    private static final RandomSource RANDOM = RandomSource.create();

    public static void tick(Minecraft mc) {
        float a = 0.0F, w = 0.0F;
        Season season = SyncedWeather.season();
        boolean on = mc.level != null && mc.level.dimension() == Level.OVERWORLD && season != null && SyncedWeather.dayOfSeason() >= 0
                && ClientConfig.SEASONAL_COLORS.get() && !Seasons.usingSereneSeasons();
        if (on) {
            float day = SyncedWeather.dayOfSeason() + Math.floorMod(mc.level.getDayTime(), 24000L) / 24000.0F;
            float progress = Mth.clamp(day / Math.max(1, SyncedWeather.daysPerSeason()), 0.0F, 1.0F);
            float early = smooth(progress * 4.0F);
            switch (season) {
                case AUTUMN -> a = smooth(progress * 2.0F);
                case WINTER -> { a = 1.0F - early; w = early; }
                case SPRING -> w = 1.0F - early;
                case SUMMER -> {}
            }
        }
        // change in visible steps so chunks only re-mesh a few times a season
        a = Math.round(a * 8.0F) / 8.0F;
        w = Math.round(w * 8.0F) / 8.0F;
        if ((a != autumn || w != winter) && mc.level != null) {
            autumn = a;
            winter = w;
            rebuildPending = true;
        }
        long now = System.currentTimeMillis();
        if (rebuildPending && mc.level != null && now - lastRebuild > 3000) {
            rebuildPending = false;
            lastRebuild = now;
            mc.levelRenderer.allChanged();
        }
        if (on && a > 0.05F && !mc.isPaused()) {
            spawnFallingLeaves(mc, a);
        }
    }

    /** Autumn leaves letting go of the trees around the player. */
    private static void spawnFallingLeaves(Minecraft mc, float amount) {
        if (!ClientConfig.FALLING_LEAVES.get() || mc.player == null) {
            return;
        }
        ClientLevel level = mc.level;
        float density = ClientConfig.PARTICLE_DENSITY.get().floatValue();
        ParticleStatus status = mc.options.particles().get();
        if (status == ParticleStatus.MINIMAL) return;
        if (status == ParticleStatus.DECREASED) density *= 0.5F;
        float wind = 1.0F + 2.0F * com.reiko.weather.client.ClientWeather.wind();
        int attempts = (int) (40 * amount * density * wind);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < attempts; i++) {
            pos.set(cam.x + RANDOM.nextInt(25) - 12, cam.y + RANDOM.nextInt(16) - 4, cam.z + RANDOM.nextInt(25) - 12);
            if (!level.getBlockState(pos).is(BlockTags.LEAVES)) continue;
            pos.move(0, -1, 0);
            if (!level.getBlockState(pos).isAir() || RANDOM.nextInt(4) != 0) continue;
            level.addParticle(ModParticles.LEAF.get(), pos.getX() + RANDOM.nextDouble(), pos.getY() + 0.9, pos.getZ() + RANDOM.nextDouble(),
                    0.0, -0.02, 0.0);
        }
    }

    public static float autumn() {
        return autumn;
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    public static int grass(int color, BlockPos pos) {
        float a = autumn, w = winter;
        if (a <= 0 && w <= 0) return color;
        color = mix(color, AUTUMN_GRASS, a * 0.3F);
        return mix(color, WINTER_GRASS, w * 0.5F);
    }

    public static int foliage(int color, BlockPos pos) {
        float a = autumn, w = winter;
        if (a <= 0 && w <= 0) return color;
        // patches of 4x4 blocks share a colour, so whole trees turn together
        int hash = Mth.murmurHash3Mixer((pos.getX() >> 2) * 73856093 ^ (pos.getZ() >> 2) * 19349663 ^ (pos.getY() >> 3) * 83492791);
        int autumnColor = AUTUMN_LEAVES[Math.floorMod(hash, AUTUMN_LEAVES.length)];
        color = mix(color, autumnColor, a * 0.85F);
        return mix(color, WINTER_LEAVES, w * 0.6F);
    }

    private static int mix(int a, int b, float t) {
        if (t <= 0) return a;
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255);
        int g = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }

    private SeasonalColors() {}
}
