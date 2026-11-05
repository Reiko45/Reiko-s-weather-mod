package com.reiko.weather.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.reiko.weather.client.ClientWeather;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Hides the sun, moon and stars behind cloud cover, the same way vanilla does for rain. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @ModifyExpressionValue(method = "renderSky", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"))
    private float reikos_weather$cloudCover(float rainLevel) {
        return Math.max(rainLevel, ClientWeather.sunOcclusion());
    }
}
