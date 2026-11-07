package com.reiko.weather.weather;

import com.reiko.weather.block.LeafPileBlock;
import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** The acid drizzle under volcanic smog: copper weathers faster and leaves wilt and fall. */
final class AcidRain {
    static void tick(ServerLevel level, ServerPlayer player, RandomSource random, @Nullable BlockPos top) {
        if (top == null) {
            return;
        }
        BlockPos pos = top.below();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof WeatheringCopper copper) {
            if (random.nextFloat() < 0.5F) {
                copper.getNext(state).ifPresent(next -> level.setBlockAndUpdate(pos, next));
            }
        } else if (state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT)
                && random.nextFloat() < 0.3F) {
            // the leaf withers and drops: it ends up on the ground below the tree
            level.removeBlock(pos, false);
            BlockPos.MutableBlockPos ground = pos.mutable();
            for (int i = 0; i < 24 && ground.getY() > level.getMinBuildHeight(); i++) {
                ground.move(0, -1, 0);
                BlockState s = level.getBlockState(ground);
                if (!s.isAir() && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS)) {
                    LeafPileBlock.addLayer(level, ground.above(), ModBlocks.LEAF_PILE.get(), 2);
                    break;
                }
            }
        }
    }

    private AcidRain() {}
}
