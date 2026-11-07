package com.reiko.weather.mixin;

import com.reiko.weather.client.StaticElectricity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fires burning out in the open send up tall columns of smoke. */
@Mixin(BaseFireBlock.class)
public abstract class BaseFireBlockMixin {
    @Inject(method = "animateTick", at = @At("TAIL"))
    private void reikos_weather$smokeColumn(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        StaticElectricity.fireSmoke(state, level, pos, random);
    }
}
