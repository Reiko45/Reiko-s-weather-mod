package com.reiko.weather.mixin;

import com.reiko.weather.client.StaticElectricity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** St. Elmo's fire on lightning rods under the mod's storm cells (vanilla only does it in global thunderstorms). */
@Mixin(LightningRodBlock.class)
public abstract class LightningRodBlockMixin {
    @Inject(method = "animateTick", at = @At("TAIL"))
    private void reikos_weather$stElmo(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        StaticElectricity.rodSparks(state, level, pos, random);
    }
}
