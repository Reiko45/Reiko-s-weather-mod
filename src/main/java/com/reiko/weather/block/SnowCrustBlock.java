package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.temperature.Temperature;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Crusted snow: rain-soaked snow frozen hard overnight into a glassy, slippery crust. It thaws back to slush when it
 * warms up, and fresh snow falling on it buries it again.
 */
public class SnowCrustBlock extends SnowLayerBlock {
    public static final MapCodec<SnowLayerBlock> CODEC = simpleCodec(SnowCrustBlock::new);

    public SnowCrustBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SnowLayerBlock> codec() {
        return CODEC;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int layers = state.getValue(LAYERS);
        if (level.isRainingAt(pos.above()) || level.isRaining() && level.canSeeSky(pos.above())
                && level.getBiome(pos).value().getPrecipitationAt(pos) == Biome.Precipitation.SNOW) {
            // fresh snow buries the crust (and rain soaks it back to slush)
            boolean snow = level.getBiome(pos).value().getPrecipitationAt(pos) == Biome.Precipitation.SNOW;
            level.setBlockAndUpdate(pos, (snow ? Blocks.SNOW : ModBlocks.SLUSH.get()).defaultBlockState().setValue(LAYERS, layers));
            return;
        }
        float air = Temperature.ambient(level, pos, WeatherManager.local(level, pos));
        if ((air > 1.5F || level.getBrightness(LightLayer.BLOCK, pos) > 11) && random.nextInt(2) == 0) {
            level.setBlockAndUpdate(pos, ModBlocks.SLUSH.get().defaultBlockState().setValue(LAYERS, layers));
        }
    }
}
