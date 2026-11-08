package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The bed of a pond that dried up in a drought: mud baked hard and split into plates. Rain soaks it back into
 * mud.
 */
public class CrackedMudBlock extends Block {
    public static final MapCodec<CrackedMudBlock> CODEC = simpleCodec(CrackedMudBlock::new);

    public CrackedMudBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.isRainingAt(pos.above()) && random.nextInt(5) == 0) {
            level.setBlockAndUpdate(pos, Blocks.MUD.defaultBlockState());
        }
    }
}
