package com.reiko.weather.block;

import com.reiko.weather.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds the windmill's animation state on the client (rotor yaw and blade spin). */
public class WindmillBlockEntity extends BlockEntity {
    public float yaw = Float.NaN;
    public float spin;
    public float previousSpin;
    public float speed;

    public WindmillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WINDMILL.get(), pos, state);
    }
}
