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

    /**
     * The sun's path through the seasons: the sun, moon and stars are tilted toward the south together, just before
     * vanilla turns them to the time of day (see SunPath).
     */
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "renderSky", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V", ordinal = 3))
    private void reikos_weather$sunPath(com.mojang.blaze3d.vertex.PoseStack pose, org.joml.Quaternionf rotation,
                                        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        float tilt = com.reiko.weather.client.SunPath.tilt();
        reikos_weather$tilt = tilt;
        if (tilt != 0.0F) pose.mulPose(com.mojang.math.Axis.XP.rotation(tilt));
        original.call(pose, rotation);
    }

    @org.spongepowered.asm.mixin.Unique
    private static float reikos_weather$tilt, reikos_weather$roll;

    /**
     * ...then, once vanilla has turned the sky to the time of day, the sun's and moon's square discs are turned about
     * their own centres to sit square to the horizon (the stars are drawn without that turn, below).
     */
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "renderSky", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V", ordinal = 4))
    private void reikos_weather$levelDiscs(com.mojang.blaze3d.vertex.PoseStack pose, org.joml.Quaternionf rotation,
                                           com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        original.call(pose, rotation);
        // (the time of day's turn about X, read back from the rotation vanilla made)
        float t = 2.0F * (float) Math.atan2(rotation.x(), rotation.w());
        reikos_weather$roll = com.reiko.weather.client.SunPath.discRoll(reikos_weather$tilt, t);
        if (reikos_weather$roll != 0.0F) pose.mulPose(com.mojang.math.Axis.YP.rotation(reikos_weather$roll));
    }

    @org.spongepowered.asm.mixin.injection.ModifyArg(method = "renderSky", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V",
            ordinal = 1), index = 0)
    private org.joml.Matrix4f reikos_weather$starsUnrolled(org.joml.Matrix4f pose) {
        return reikos_weather$roll == 0.0F ? pose : new org.joml.Matrix4f(pose).rotateY(-reikos_weather$roll);
    }

    /** A supermoon looks noticeably bigger than an ordinary full moon. */
    @org.spongepowered.asm.mixin.injection.ModifyConstant(method = "renderSky", constant = @org.spongepowered.asm.mixin.injection.Constant(floatValue = 20.0F))
    private float reikos_weather$supermoon(float size) {
        return size * (1.0F + 0.22F * com.reiko.weather.client.SkyTints.supermoon());
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
