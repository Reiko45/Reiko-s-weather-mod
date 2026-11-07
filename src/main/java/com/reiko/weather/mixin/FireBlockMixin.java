package com.reiko.weather.mixin;

import com.reiko.weather.weather.FireTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts burning fire so big wildfires can send smoke downwind. */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void reikos_weather$countFire(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        FireTracker.onFireTick(level, pos);
    }
}
