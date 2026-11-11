package com.reiko.weather.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Driftwood: a log stripped of its bark and bleached silver-grey by sun, salt and sea, washed up on a beach. */
public class DriftwoodBlock extends RotatedPillarBlock {
    public DriftwoodBlock(Properties properties) {
        super(properties);
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 5;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 5;
    }
}
