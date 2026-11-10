package com.reiko.weather.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.reiko.weather.client.ClientWeather;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Tints and darkens the sky and clouds for overcast, supercell, sandstorm and heatwave weather. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @ModifyReturnValue(method = "getSkyColor", at = @At("RETURN"))
    private Vec3 reikos_weather$skyColor(Vec3 original) {
        return ClientWeather.modifySkyColor(original);
    }

    @ModifyReturnValue(method = "getCloudColor", at = @At("RETURN"))
    private Vec3 reikos_weather$cloudColor(Vec3 original) {
        return ClientWeather.modifyCloudColor(original);
    }

    /** "Reduce flashing": no sky flashes from lightning at all. */
    @org.spongepowered.asm.mixin.injection.Inject(method = "setSkyFlashTime", at = @At("HEAD"), cancellable = true)
    private void reikos_weather$skyFlash(int time, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (time > 0 && com.reiko.weather.config.ClientConfig.SPEC.isLoaded() && com.reiko.weather.config.ClientConfig.REDUCE_FLASHING.get()) {
            ci.cancel();
        }
    }

    @ModifyReturnValue(method = "getStarBrightness", at = @At("RETURN"))
    private float reikos_weather$starBrightness(float original) {
        return ClientWeather.modifyStarBrightness(original);
    }

    @ModifyReturnValue(method = "getSkyDarken", at = @At("RETURN"))
    private float reikos_weather$skyDarken(float original) {
        return ClientWeather.modifySkyDarken(original);
    }
}
