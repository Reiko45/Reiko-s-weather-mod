package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.landform.Terrain;
import com.reiko.weather.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Damp air: the mist that wraps a cloud forest, trees combing water out of fog so their leaves drip, and cave mouths
 * breathing out warm, damp air that steams in the cold.
 */
public final class DampAir {
    private static final RandomSource RANDOM = RandomSource.create();
    private static float cloudMist;
    private record Mouth(BlockPos pos, Direction out, long until) {}
    private static final List<Mouth> MOUTHS = new ArrayList<>();

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || level.dimension() != Level.OVERWORLD) {
            cloudMist = 0.0F;
            MOUTHS.clear();
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        BlockPos at = BlockPos.containing(cam);
        // the cloud forest's mist, thickest in the afternoon when the cloud settles on the mountain
        float inForest = 0.0F;
        if (ClientConfig.CLOUD_FOREST_MIST.get()) {
            int found = 0;
            for (int[] d : new int[][]{{0, 0}, {20, 0}, {-20, 0}, {0, 20}, {0, -20}}) {
                if (level.getBiome(at.offset(d[0], 0, d[1])).is(Terrain.CLOUD_FOREST_BIOME)) found++;
            }
            inForest = found / 5.0F;
        }
        cloudMist = Mth.lerp(0.01F, cloudMist, inForest);
        boolean particles = mc.options.particles().get() != ParticleStatus.MINIMAL;
        if (!particles) return;
        if (cloudMist > 0.05F) {
            for (int i = 0; i < Math.round(4 * cloudMist); i++) {
                double x = cam.x + RANDOM.nextGaussian() * 10, y = cam.y + RANDOM.nextDouble() * 8 - 2, z = cam.z + RANDOM.nextGaussian() * 10;
                if (level.getBlockState(BlockPos.containing(x, y, z)).isAir()) {
                    level.addParticle(ModParticles.MIST.get(), x, y, z, (RANDOM.nextDouble() - 0.5) * 0.02, 0.003, (RANDOM.nextDouble() - 0.5) * 0.02);
                }
            }
        }
        // leaves drip in fog (and all the time in a cloud forest)
        float fog = Math.max(ClientWeather.fogDensity(), cloudMist * 0.8F);
        if (ClientConfig.FOG_DRIP.get() && fog > 0.25F && ClientWeather.rain() < 0.05F) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int i = 0; i < (int) (24 * fog); i++) {
                pos.set(cam.x + RANDOM.nextInt(21) - 10, cam.y + RANDOM.nextInt(12) - 3, cam.z + RANDOM.nextInt(21) - 10);
                if (!level.getBlockState(pos).is(BlockTags.LEAVES)) continue;
                pos.move(0, -1, 0);
                if (!level.getBlockState(pos).isAir() || RANDOM.nextInt(3) != 0) continue;
                level.addParticle(ParticleTypes.DRIPPING_WATER, pos.getX() + RANDOM.nextDouble(), pos.getY() + 0.95, pos.getZ() + RANDOM.nextDouble(), 0, 0, 0);
            }
        }
        if (ClientConfig.CAVE_BREATH.get()) caveBreath(level, cam);
    }

    /** Cave mouths round about steam in the cold: the air inside stays at the year's average, so in winter it's the warm side. */
    private static void caveBreath(ClientLevel level, Vec3 cam) {
        long now = level.getGameTime();
        MOUTHS.removeIf(m -> m.until() < now || m.pos().distToCenterSqr(cam) > 40 * 40);
        if (now % 10 == 0 && MOUTHS.size() < 8) {
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int i = 0; i < 24; i++) {
                p.set(cam.x + RANDOM.nextInt(33) - 16, cam.y + RANDOM.nextInt(17) - 8, cam.z + RANDOM.nextInt(33) - 16);
                Direction out = mouth(level, p);
                if (out != null) {
                    BlockPos found = p.immutable();
                    if (MOUTHS.stream().noneMatch(m -> m.pos().distSqr(found) < 9)) MOUTHS.add(new Mouth(found, out, now + 200));
                }
            }
        }
        if (ClientWeather.airTemperature() > 3.0F) return;
        float cold = Mth.clamp((3.0F - ClientWeather.airTemperature()) / 8.0F, 0.3F, 1.0F);
        for (Mouth m : MOUTHS) {
            if (RANDOM.nextFloat() > 0.5F * cold) continue;
            BlockPos q = m.pos();
            double x = q.getX() + RANDOM.nextDouble(), y = q.getY() + 0.3 + RANDOM.nextDouble() * 0.7, z = q.getZ() + RANDOM.nextDouble();
            // the warm air spills out under the roof of the mouth and rises
            level.addParticle(ModParticles.STEAM_FOG.get(), x, y, z, m.out().getStepX() * 0.03, 0.02 + RANDOM.nextDouble() * 0.02, m.out().getStepZ() * 0.03);
        }
    }

    /**
     * Whether an open-air block is just inside a cave mouth: half-lit by the sky, with a dark passage running on into the
     * hill on one side. Returns the way out, or null.
     */
    private static Direction mouth(ClientLevel level, BlockPos p) {
        if (!SkySights.loaded(level, p) || !level.getBlockState(p).isAir()) return null;
        int sky = level.getBrightness(LightLayer.SKY, p);
        if (sky < 3 || sky > 12 || level.canSeeSky(p)) return null;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            boolean passage = true;
            for (int k = 1; k <= 4 && passage; k++) {
                BlockPos in = p.relative(d, k);
                passage = level.getBlockState(in).isAir() && level.getBrightness(LightLayer.SKY, in) <= (k == 1 ? 6 : 2);
            }
            if (!passage) continue;
            BlockPos outside = p.relative(d.getOpposite(), 2);
            if (level.getBlockState(outside).isAir() && level.getBrightness(LightLayer.SKY, outside) >= 13) return d.getOpposite();
        }
        return null;
    }

    /** How deep in a cloud forest's mist the camera is, 0 to 1. */
    public static float cloudMist() {
        return cloudMist;
    }

    public static void reset() {
        cloudMist = 0.0F;
        MOUTHS.clear();
    }

    private DampAir() {}
}
