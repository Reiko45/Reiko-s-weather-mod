package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Hail fog: after a big hailstorm the ground is left white with hailstones, and the warm, damp air passing over them
 * is chilled until it condenses into a low, ground-hugging fog over the drifts, which lasts until the hail melts.
 */
public final class HailFog {
    private static final RandomSource RANDOM = RandomSource.create();
    private static float fog, cover;
    private static final java.util.List<BlockPos> HAIL = new java.util.ArrayList<>();

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD || !SyncedWeather.managed() || !ClientConfig.HAIL_FOG.get()) {
            fog = cover = 0.0F;
            HAIL.clear();
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        if (level.getGameTime() % 10 == 0) {
            // how much of the ground round about is white with hail
            HAIL.clear();
            int found = 0, tried = 0;
            for (int i = 0; i < 32; i++) {
                BlockPos column = BlockPos.containing(cam.x + RANDOM.nextInt(33) - 16, 0, cam.z + RANDOM.nextInt(33) - 16);
                if (!SkySights.loaded(level, column)) continue;
                tried++;
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column); // (the one the server sends the client)
                for (BlockPos p : new BlockPos[]{top, top.below()}) {
                    if (level.getBlockState(p).is(ModBlocks.HAILSTONES.get())) {
                        found++;
                        HAIL.add(p.immutable());
                        break;
                    }
                }
            }
            cover = tried > 0 ? found / (float) tried : 0.0F;
        }
        // warm, moist air over the cold hail, once the storm itself has passed
        float warm = Mth.clamp((ClientWeather.airTemperature() - 2.0F) / 6.0F, 0.0F, 1.0F);
        float target = Mth.clamp(cover * 2.2F, 0.0F, 1.0F) * warm * Mth.clamp(1.0F - ClientWeather.rain() * 2.0F, 0.0F, 1.0F);
        fog = Mth.lerp(0.01F, fog, target);
        // wisps of it lying over the drifts
        if (fog > 0.05F && !HAIL.isEmpty() && mc.options.particles().get() != net.minecraft.client.ParticleStatus.MINIMAL) {
            for (int i = 0; i < 2; i++) {
                if (RANDOM.nextFloat() > fog) continue;
                BlockPos p = HAIL.get(RANDOM.nextInt(HAIL.size()));
                level.addParticle(ModParticles.MIST.get(), p.getX() + RANDOM.nextDouble(), p.getY() + 0.3 + RANDOM.nextDouble() * 0.6,
                        p.getZ() + RANDOM.nextDouble(), (RANDOM.nextDouble() - 0.5) * 0.01, 0.0, (RANDOM.nextDouble() - 0.5) * 0.01);
            }
        }
    }

    /** How thick the hail fog is round the camera, 0 to 1. */
    public static float fog() {
        return fog;
    }

    /** For the F3 screen. */
    static String debug() {
        return String.format(java.util.Locale.ROOT, "%.2f (hail %.2f)", fog, cover);
    }

    public static void reset() {
        fog = cover = 0.0F;
        HAIL.clear();
    }

    private HailFog() {}
}
