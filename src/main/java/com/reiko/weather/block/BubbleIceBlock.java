package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.temperature.Temperature;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lake ice with bubbles frozen into it: marsh gas rising from rotting plants on the lake bed is caught by the ice as it
 * thickens, and frozen in as stacks of flat white discs, one under another. It melts like any other lake ice.
 */
public class BubbleIceBlock extends IceBlock {
    public static final MapCodec<IceBlock> CODEC = simpleCodec(BubbleIceBlock::new);

    public BubbleIceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends IceBlock> codec() {
        return CODEC;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);
        if (level.getBlockState(pos).is(this) && random.nextInt(3) == 0 && Temperature.ambient(level, pos, WeatherManager.local(level, pos)) > 3.0F) {
            melt(state, level, pos);
        }
    }
}
