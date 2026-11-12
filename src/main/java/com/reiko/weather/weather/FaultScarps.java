package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A fault scarp: in a big earthquake the fault breaks right through to the surface, and the land on one side lurches up
 * past the other. What's left is a fresh, raw crack running across the country, the ground heaved up into a low step
 * along one side of it, slicing straight across fields and through hills alike.
 */
public final class FaultScarps {
    /** A big quake felt this hard tears the ground open somewhere near a player. */
    static void rupture(ServerLevel level, ServerPlayer player, float felt, RandomSource random) {
        if (!WeatherConfig.FAULT_SCARPS.get() || felt < 0.55F || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        float a = random.nextFloat() * Mth.TWO_PI;
        double d = 12 + random.nextDouble() * 18;
        BlockPos start = BlockPos.containing(player.getX() + Mth.cos(a) * d, 0, player.getZ() + Mth.sin(a) * d);
        int length = 24 + Math.round(30 * felt);
        // (it runs past the player rather than towards them)
        tear(level, start.offset(-Math.round(Mth.cos(a + Mth.HALF_PI) * length / 2.0F), 0, -Math.round(Mth.sin(a + Mth.HALF_PI) * length / 2.0F)),
                a + Mth.HALF_PI + (random.nextFloat() - 0.5F) * 0.6F, length, random);
    }

    /**
     * Tears a scarp {@code length} blocks long from {@code start} (only x and z are used) toward {@code angle}: an open
     * crack along the line, with the ground on its left heaved up a block. Returns how many columns it changed.
     */
    public static int tear(ServerLevel level, BlockPos start, float angle, int length, RandomSource random) {
        double dx = Mth.cos(angle), dz = Mth.sin(angle), px = -dz, pz = dx;
        int changed = 0;
        boolean blocked = false;
        float wobble = 0.0F;
        for (int t = 0; t <= length; t++) {
            wobble = Mth.clamp(wobble + (random.nextFloat() - 0.5F) * 0.5F, -1.5F, 1.5F);
            double x = start.getX() + 0.5 + dx * t + px * wobble, z = start.getZ() + 0.5 + dz * t + pz * wobble;
            // keep well clear of anything built (checked every few blocks)
            if (t % 4 == 0) {
                BlockPos here = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, 0, z));
                blocked = !level.isLoaded(here) || GroundEvents.built(level, here, 4);
            }
            if (blocked) continue;
            if (crack(level, BlockPos.containing(x, 0, z), random)) changed++;
            for (int o = 1; o <= 3; o++) {
                if (o == 3 && random.nextBoolean()) continue; // a ragged edge to the heaved-up strip
                if (heave(level, BlockPos.containing(x + px * o, 0, z + pz * o))) changed++;
            }
            if (t % 3 == 0) {
                BlockPos p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, 0, z));
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(p.below())), x, p.getY() + 0.2, z, 12, 0.6, 0.2, 0.6, 0.1);
                if (t % 9 == 0) level.playSound(null, p, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 1.5F, 0.5F);
            }
        }
        return changed;
    }

    /** Opens the crack in one column: the top two or three blocks of ground fall away into a raw gash. */
    private static boolean crack(ServerLevel level, BlockPos column, RandomSource random) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        BlockPos ground = top.below();
        if (!natural(level.getBlockState(ground)) || wet(level, ground)) return false;
        clearPlants(level, top);
        int depth = 2 + random.nextInt(2);
        for (int k = 0; k < depth; k++) {
            BlockPos p = ground.below(k);
            if (!natural(level.getBlockState(p)) || wet(level, p)) break;
            level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        }
        return true;
    }

    /** Heaves one column of ground up a block. */
    private static boolean heave(ServerLevel level, BlockPos column) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        BlockPos ground = top.below();
        BlockState s = level.getBlockState(ground);
        if (!natural(s) || wet(level, ground)) return false;
        clearPlants(level, top);
        if (!level.getBlockState(top).isAir()) return false;
        level.setBlockAndUpdate(top, s);
        if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.MYCELIUM) || s.is(Blocks.PODZOL)) level.setBlockAndUpdate(ground, Blocks.DIRT.defaultBlockState());
        return true;
    }

    private static void clearPlants(ServerLevel level, BlockPos top) {
        BlockState above = level.getBlockState(top);
        if (!above.isAir() && above.canBeReplaced() && above.getFluidState().isEmpty()) level.destroyBlock(top, false);
    }

    /** Natural ground: soil, sand, gravel, rock (not anything placed, nor trees). */
    private static boolean natural(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.TERRACOTTA)
                || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.CLAY) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.CALCITE);
    }

    /** Whether water lies right beside (a crack there would drain it). */
    private static boolean wet(ServerLevel level, BlockPos p) {
        for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, 0, -1), p.offset(1, 1, 1))) {
            if (level.getFluidState(n).is(FluidTags.WATER) || level.getFluidState(n).is(FluidTags.LAVA)) return true;
        }
        return false;
    }

    private FaultScarps() {}
}
