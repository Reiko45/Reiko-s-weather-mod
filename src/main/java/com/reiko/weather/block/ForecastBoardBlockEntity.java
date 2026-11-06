package com.reiko.weather.block;

import com.reiko.weather.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Exists so the board's renderer can draw the forecast on it. */
public class ForecastBoardBlockEntity extends BlockEntity {
    public ForecastBoardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FORECAST_BOARD.get(), pos, state);
    }
}
