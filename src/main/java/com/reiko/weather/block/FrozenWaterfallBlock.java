package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.temperature.Temperature;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A frozen waterfall: in a hard freeze, falling water turns to a hanging curtain of blue-white ice, fluted where
 * the water ran. When it thaws the ice melts away block by block (to air, not water: the stream above is still
 * there and soon pours down again).
 */
public class FrozenWaterfallBlock extends Block {
    public static final MapCodec<FrozenWaterfallBlock> CODEC = simpleCodec(FrozenWaterfallBlock::new);
    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);

    public FrozenWaterfallBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBrightness(LightLayer.BLOCK, pos) > 11 || Temperature.ambient(level, pos, WeatherManager.local(level, pos)) > 1.0F) {
            level.removeBlock(pos, false);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // a drip off the bottom of the curtain now and then
        if (random.nextInt(20) == 0 && level.getBlockState(pos.below()).isAir()) {
            level.addParticle(ParticleTypes.DRIPPING_WATER, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() - 0.05,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.0, 0.0);
        }
    }
}
