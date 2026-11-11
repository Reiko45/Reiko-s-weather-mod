package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.temperature.Temperature;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Snow reddened by desert dust, settled out of a haze or brought down with the snowfall itself. The dust darkens it,
 * so it soaks up the sunshine that clean snow throws back, and melts away days or weeks earlier.
 */
public class DustySnowBlock extends SnowLayerBlock {
    public static final MapCodec<SnowLayerBlock> CODEC = simpleCodec(DustySnowBlock::new);

    public DustySnowBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SnowLayerBlock> codec() {
        return CODEC;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);
        if (!level.getBlockState(pos).is(this) || !com.reiko.weather.config.WeatherConfig.DUSTY_SNOWFALL.get()) return;
        // in sunshine it melts even when the air is a little below freezing
        if (level.isDay() && !level.isRaining() && level.canSeeSky(pos.above()) && random.nextInt(4) == 0
                && Temperature.ambient(level, pos, WeatherManager.local(level, pos)) > -4.0F) {
            int layers = state.getValue(LAYERS);
            if (layers > 1) level.setBlockAndUpdate(pos, state.setValue(LAYERS, layers - 1));
            else level.removeBlock(pos, false);
        }
    }

    /**
     * Only a thin single layer gets swept aside by a block landing on it. (This has no item of its own, and vanilla's
     * snow check, which compares the item in hand with this block's item, would call itself forever when a falling
     * block landed on it.)
     */
    @Override
    protected boolean canBeReplaced(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.item.context.BlockPlaceContext context) {
        return state.getValue(LAYERS) == 1;
    }
}
