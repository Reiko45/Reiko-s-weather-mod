package com.reiko.weather.mixin;

import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets fulgurite skip the purely visual lightning (e.g. from the weather altar). */
@Mixin(LightningBolt.class)
public interface LightningBoltAccessor {
    @Accessor("visualOnly")
    boolean reikos_weather$isVisualOnly();
}
