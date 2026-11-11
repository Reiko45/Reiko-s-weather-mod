package com.reiko.weather.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.systems.RenderSystem;
import com.reiko.weather.client.ClientWeather;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides the sun, moon and stars behind cloud cover, the same way vanilla does for rain; tints dusty rain; swaps in the mod's clouds. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @ModifyExpressionValue(method = "renderSky", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"))
    private float reikos_weather$cloudCover(float rainLevel) {
        // in a sun shower the sun shines on through the rain
        return Math.max(rainLevel * (1.0F - 0.9F * ClientWeather.sunShower()), ClientWeather.sunOcclusion());
    }

    /** Minecraft's blocky clouds give way to the mod's own (see DriftingClouds), when they're turned on. */
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void reikos_weather$replaceClouds(com.mojang.blaze3d.vertex.PoseStack poseStack, org.joml.Matrix4f frustum, org.joml.Matrix4f projection,
                                               float partialTick, double camX, double camY, double camZ, CallbackInfo ci) {
        if (com.reiko.weather.client.DriftingClouds.replacing()) ci.cancel();
    }

    /** Rain falling through a dust haze comes down reddish-brown. */
    @Inject(method = "renderSnowAndRain", at = @At("HEAD"))
    private void reikos_weather$dustyRainStart(LightTexture lightTexture, float partialTick, double camX, double camY, double camZ, CallbackInfo ci) {
        float dust = ClientWeather.dustyRain();
        if (dust > 0.01F) {
            RenderSystem.setShaderColor(1.0F - 0.15F * dust, 1.0F - 0.38F * dust, 1.0F - 0.55F * dust, 1.0F);
        }
    }

    @Inject(method = "renderSnowAndRain", at = @At("RETURN"))
    private void reikos_weather$dustyRainEnd(LightTexture lightTexture, float partialTick, double camX, double camY, double camZ, CallbackInfo ci) {
        if (ClientWeather.dustyRain() > 0.01F) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
