package com.reiko.weather.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.reiko.weather.weather.RegionalWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Lets storm cells fill cauldrons and pile up snow the way global rain does. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @ModifyExpressionValue(method = "tickPrecipitation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;isRaining()Z"))
    private boolean reikos_weather$stormCells(boolean raining, @Local(argsOnly = true) BlockPos pos) {
        return raining || RegionalWeather.cellRainsAt((ServerLevel) (Object) this, pos);
    }
}
