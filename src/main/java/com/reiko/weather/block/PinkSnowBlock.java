package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Watermelon snow: in summer, old snow high in the mountains turns pink where algae bloom in the meltwater
 * between the grains (it even smells faintly of watermelon). The first fresh snow of autumn buries it, and it's
 * white again.
 */
public class PinkSnowBlock extends SnowLayerBlock {
    public static final MapCodec<SnowLayerBlock> CODEC = simpleCodec(PinkSnowBlock::new);

    public PinkSnowBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SnowLayerBlock> codec() {
        return CODEC;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Season season = WeatherManager.seasonInfo().season();
        if (season != null && season != Season.SUMMER && random.nextInt(4) == 0) {
            level.setBlockAndUpdate(pos, Blocks.SNOW.defaultBlockState().setValue(LAYERS, state.getValue(LAYERS)));
            return;
        }
        super.randomTick(state, level, pos, random);
    }
}
