package com.reiko.weather.weather;

import com.reiko.weather.block.FallenBranchBlock;
import com.reiko.weather.block.LeafPileBlock;
import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Violent wind snaps branches off trees and strips their leaves, leaving debris to clear up afterwards. */
public final class StormDebris {
    private static final int AREA = 40;

    public static void drop(ServerLevel level, ServerPlayer player, RandomSource random) {
        BlockPos ground = groundUnderTrees(level, player, random, AREA);
        if (ground == null) {
            return;
        }
        if (random.nextFloat() < 0.55F) {
            BlockState here = level.getBlockState(ground);
            if (here.isAir() || here.canBeReplaced() && here.getFluidState().isEmpty()) {
                BlockState branch = ModBlocks.FALLEN_BRANCH.get().defaultBlockState()
                        .setValue(FallenBranchBlock.AXIS, random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z);
                if (branch.canSurvive(level, ground)) {
                    level.setBlockAndUpdate(ground, branch);
                    level.playSound(null, ground, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.8F, 0.6F + random.nextFloat() * 0.3F);
                }
            }
        } else if (WeatherConfig.LEAF_PILES.get()) {
            LeafPileBlock.addLayer(level, ground, ModBlocks.LEAF_PILE.get(), 2);
        }
    }

    /**
     * The ground (first free block above solid ground, ignoring leaves) of a random column near the player
     * that has a tree canopy over it, or null.
     */
    public static BlockPos groundUnderTrees(ServerLevel level, ServerPlayer player, RandomSource random, int area) {
        int x = player.getBlockX() + random.nextInt(area * 2 + 1) - area;
        int z = player.getBlockZ() + random.nextInt(area * 2 + 1) - area;
        BlockPos column = new BlockPos(x, 0, z);
        if (!level.isLoaded(column)) {
            return null;
        }
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        int canopy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (canopy <= ground.getY() + 1 || canopy - ground.getY() > 14) {
            return null;
        }
        if (!level.getBlockState(new BlockPos(x, canopy - 1, z)).is(BlockTags.LEAVES)) {
            return null;
        }
        BlockPos below = ground.below();
        BlockState floor = level.getBlockState(below);
        if (!(floor.is(BlockTags.DIRT) || floor.is(BlockTags.SAND)) || !floor.isFaceSturdy(level, below, Direction.UP)) {
            return null;
        }
        return ground;
    }

    private StormDebris() {}
}
