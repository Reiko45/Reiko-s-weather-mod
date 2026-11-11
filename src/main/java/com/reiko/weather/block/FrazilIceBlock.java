package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.temperature.Temperature;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * Frazil ice: in bitter cold, water too fast to freeze over is chilled a fraction below freezing and fills with
 * loose needles of ice. They clump into floating slush that clogs the river, and stick to the stones of the bed as
 * anchor ice. It's still water underneath (swim through it, slowly), and it all melts back when the cold eases.
 */
public class FrazilIceBlock extends Block {
    public static final MapCodec<FrazilIceBlock> CODEC = simpleCodec(FrazilIceBlock::new);

    public FrazilIceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Fluids.WATER.getSource(false);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        // wading through slush
        entity.makeStuckInBlock(state, new Vec3(0.6, 0.7, 0.6));
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (Temperature.ambient(level, pos, WeatherManager.local(level, pos)) > 0.5F) {
            level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        }
    }
}
