package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Small helpers shared by the landform builders. */
public final class Terrain {
    public static final int FLAGS = 2;

    /** Finished chunks keep the final heightmaps; chunks still being generated keep the "_WG" ones. */
    public static Heightmap.Types floor(WorldGenLevel level) {
        return level instanceof ServerLevel ? Heightmap.Types.OCEAN_FLOOR : Heightmap.Types.OCEAN_FLOOR_WG;
    }

    /** Whether a block is something growing (or lying) on the ground rather than the ground itself. */
    public static boolean cover(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.REPLACEABLE) || state.isAir()
                || state.is(BlockTags.FLOWERS) || state.is(Blocks.CACTUS) || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BAMBOO)
                || state.is(Blocks.SNOW) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON);
    }

    /** The y of the top block of real ground at a column: below any water, trees and plants. */
    public static int ground(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
        int y = level.getHeight(floor(level), x, z) - 1;
        int min = level.getMinBuildHeight();
        while (y > min) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (cover(state) && state.getFluidState().isEmpty()) {
                y--;
            } else {
                break;
            }
        }
        return y;
    }

    /** Clears trees, plants and snow from a column above {@code y}, up to {@code height} blocks (leaves water alone). */
    public static void clearAbove(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, int height) {
        int max = Math.min(level.getMaxBuildHeight() - 1, y + height);
        for (int h = y + 1; h <= max; h++) {
            pos.set(x, h, z);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && cover(state) && state.getFluidState().isEmpty()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            }
        }
    }

    public static void set(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState state) {
        pos.set(x, y, z);
        level.setBlock(pos, state, FLAGS);
    }

    /** A repeatable 0-1 value for a position. */
    public static float noise(long seed, int x, int y, int z) {
        long h = Mth.getSeed(x, y, z) ^ seed;
        h = h * 6364136223846793005L + 1442695040888963407L;
        return ((h >>> 40) & 0xFFFF) / 65535.0F;
    }

    public static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** A repeatable 0-1 value for the i-th thing a landform places. */
    public static float hash(long seed, int i) {
        int h = Mth.murmurHash3Mixer((int) (seed ^ seed >>> 32) + i * 0x9E3779B9);
        return (h >>> 8) / (float) (1 << 24);
    }

    private Terrain() {}
}
