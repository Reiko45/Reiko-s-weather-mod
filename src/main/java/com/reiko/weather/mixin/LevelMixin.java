package com.reiko.weather.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.reiko.weather.weather.RegionalWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Makes isRainingAt aware of regional storm cells, so riptide, fire extinguishing, cauldrons, mobs
 * and anything else that asks "is it raining here?" works under a passing storm.
 */
@Mixin(Level.class)
public abstract class LevelMixin {
    @ModifyExpressionValue(method = "isRainingAt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;isRaining()Z"))
    private boolean reikos_weather$stormCells(boolean raining, @Local(argsOnly = true) BlockPos pos) {
        return raining || RegionalWeather.cellRainsAt((Level) (Object) this, pos);
    }
}
