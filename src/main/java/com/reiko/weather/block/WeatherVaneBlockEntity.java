package com.reiko.weather.block;

import com.reiko.weather.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds the vane's animation state on the client; the arrow itself is drawn by its renderer. */
public class WeatherVaneBlockEntity extends BlockEntity {
    public float angle = Float.NaN;
    public float previousAngle;

    public WeatherVaneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WEATHER_VANE.get(), pos, state);
    }
}
