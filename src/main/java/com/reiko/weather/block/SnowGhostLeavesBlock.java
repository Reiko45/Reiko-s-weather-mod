package com.reiko.weather.block;

import com.reiko.weather.temperature.Temperature;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A snow ghost's needles: on a high, cold ridge, freezing fog blowing through the trees leaves rime on every needle,
 * snow sticks to the rime, and more rime grows on the snow, until a conifer is buried in a white, lumpy shape (the
 * "snow monsters" of Mount Zaō). In the thaw the plaster drops off and the tree is green again.
 */
public class SnowGhostLeavesBlock extends LeavesBlock {
    public SnowGhostLeavesBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (Temperature.ambient(level, pos, WeatherManager.local(level, pos)) > 1.0F) {
            level.setBlock(pos, thawed(state), Block.UPDATE_CLIENTS);
            return;
        }
        super.randomTick(state, level, pos, random);
    }

    /** The plain spruce needles under the plaster. */
    public static BlockState thawed(BlockState state) {
        return Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(DISTANCE, state.getValue(DISTANCE))
                .setValue(PERSISTENT, state.getValue(PERSISTENT)).setValue(WATERLOGGED, state.getValue(WATERLOGGED));
    }

    /** The plastered needles over spruce leaves. */
    public static BlockState plastered(Block ghost, BlockState spruce) {
        return ghost.defaultBlockState().setValue(DISTANCE, spruce.getValue(DISTANCE))
                .setValue(PERSISTENT, spruce.getValue(PERSISTENT)).setValue(WATERLOGGED, spruce.getValue(WATERLOGGED));
    }
}
