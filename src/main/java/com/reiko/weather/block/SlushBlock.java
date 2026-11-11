package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.temperature.Temperature;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Slush: snow that rain has soaked through, grey and heavy and wet, that drags at your feet. It melts away as the thaw
 * goes on, but if the night turns cold again it sets hard into an icy crust.
 */
public class SlushBlock extends SnowLayerBlock {
    public static final MapCodec<SnowLayerBlock> CODEC = simpleCodec(SlushBlock::new);

    public SlushBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SnowLayerBlock> codec() {
        return CODEC;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (state.getValue(LAYERS) >= 2 && entity.onGround()) {
            entity.makeStuckInBlock(state, new Vec3(0.8, 1.0, 0.8));
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        float air = Temperature.ambient(level, pos, WeatherManager.local(level, pos));
        int layers = state.getValue(LAYERS);
        if (air < -1.0F) {
            // the meltwater in it freezes: a hard, glassy crust
            level.setBlockAndUpdate(pos, ModBlocks.SNOW_CRUST.get().defaultBlockState().setValue(LAYERS, layers));
        } else if ((air > 1.0F || level.getBrightness(LightLayer.BLOCK, pos) > 11) && random.nextInt(3) == 0) {
            if (layers > 1) level.setBlockAndUpdate(pos, state.setValue(LAYERS, layers - 1));
            else level.removeBlock(pos, false);
        }
    }
}
