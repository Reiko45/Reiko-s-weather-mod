package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.registry.ModParticles;
import com.reiko.weather.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Two night-and-morning sounds and sights at the water's edge: plankton glowing blue where the waves break on
 * warm coasts (and round anything moving through the water), and lake ice that booms, pings and twangs as the
 * temperature changes around dawn and dusk.
 */
public final class ShoreAndIce {
    private static final RandomSource RANDOM = RandomSource.create();
    private static int iceCooldown;

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        Player player = mc.player;
        if (level == null || player == null || level.dimension() != Level.OVERWORLD) return;
        float sun = com.reiko.weather.weather.SkyPhenomena.sunHeight(level, 1.0F);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        if (ClientConfig.SEA_SPARKLE.get() && sun < -0.1F && mc.options.particles().get() != ParticleStatus.MINIMAL) {
            seaSparkle(level, player, cam);
        }
        if (ClientConfig.SINGING_ICE.get() && ClientConfig.SOUNDS.get()) {
            singingIce(mc, level, cam);
        }
    }

    private static boolean warmSea(ClientLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        return biome.is(Biomes.WARM_OCEAN) || biome.is(Biomes.LUKEWARM_OCEAN) || biome.is(Biomes.DEEP_LUKEWARM_OCEAN)
                || (biome.is(BiomeTags.IS_BEACH) || biome.is(Biomes.MANGROVE_SWAMP)) && biome.value().getBaseTemperature() >= 0.8F;
    }

    private static void seaSparkle(ClientLevel level, Player player, Vec3 cam) {
        float density = ClientConfig.PARTICLE_DENSITY.get().floatValue();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int sea = level.getSeaLevel();
        // breaking waves along the shore
        for (int i = 0; i < Math.round(40 * density); i++) {
            int x = Mth.floor(cam.x) + RANDOM.nextInt(65) - 32, z = Mth.floor(cam.z) + RANDOM.nextInt(65) - 32;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            if (top != sea - 1 && top != sea) continue;
            pos.set(x, top, z);
            if (!level.getFluidState(pos).is(FluidTags.WATER) || !warmSea(level, pos)) continue;
            boolean shore = false;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (level.getFluidState(pos.relative(d)).isEmpty() && !level.getBlockState(pos.relative(d)).isAir()) {
                    shore = true;
                    break;
                }
            }
            if (!shore && RANDOM.nextInt(12) != 0) continue;
            for (int k = 0; k < (shore ? 3 : 1); k++) {
                level.addParticle(ModParticles.SEA_SPARKLE.get(), x + RANDOM.nextDouble(), top + 0.9 + RANDOM.nextDouble() * 0.15, z + RANDOM.nextDouble(),
                        (RANDOM.nextDouble() - 0.5) * 0.02, 0.0, (RANDOM.nextDouble() - 0.5) * 0.02);
            }
        }
        // swirls of light round anything swimming
        if (player.isInWater() && warmSea(level, player.blockPosition()) && player.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4) {
            for (int k = 0; k < 6; k++) {
                level.addParticle(ModParticles.SEA_SPARKLE.get(), player.getX() + (RANDOM.nextDouble() - 0.5) * 1.6, player.getY() + RANDOM.nextDouble() * 0.8,
                        player.getZ() + (RANDOM.nextDouble() - 0.5) * 1.6, 0, 0, 0);
            }
        }
    }

    private static void singingIce(Minecraft mc, ClientLevel level, Vec3 cam) {
        if (iceCooldown > 0) {
            iceCooldown--;
            return;
        }
        long time = Math.floorMod(level.getDayTime(), 24000L);
        // the ice talks most as it warms in the morning and cools in the evening
        boolean changing = time > 22500L || time < 2500L || time > 11000L && time < 14000L;
        if (!changing || ClientWeather.airTemperature() > 0.0F || RANDOM.nextInt(changing ? 120 : 600) != 0) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 20; i++) {
            int x = Mth.floor(cam.x) + RANDOM.nextInt(61) - 30, z = Mth.floor(cam.z) + RANDOM.nextInt(61) - 30;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            pos.set(x, top, z);
            if (!level.getBlockState(pos).is(Blocks.ICE) || !level.getFluidState(pos.below()).is(FluidTags.WATER)) continue;
            mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.ICE_SING.get(), SoundSource.AMBIENT, 0.6F + RANDOM.nextFloat() * 0.6F,
                    0.8F + RANDOM.nextFloat() * 0.4F, RANDOM, x + 0.5, top + 0.5, z + 0.5));
            iceCooldown = 60 + RANDOM.nextInt(200);
            return;
        }
    }

    private ShoreAndIce() {}
}
