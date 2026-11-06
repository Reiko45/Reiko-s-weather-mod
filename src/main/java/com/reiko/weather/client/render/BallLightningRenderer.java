package com.reiko.weather.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.reiko.weather.ReikosWeather;
import com.reiko.weather.entity.BallLightningEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** A glowing, flickering orb drawn as two camera-facing billboards. */
public class BallLightningRenderer extends EntityRenderer<BallLightningEntity> {
    private static final ResourceLocation TEXTURE = ReikosWeather.id("textures/entity/ball_lightning.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    public BallLightningRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(BallLightningEntity ball, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float brightness = ball.brightness(partialTick);
        if (brightness <= 0.01F) {
            return;
        }
        float time = ball.tickCount + partialTick;
        float flicker = 0.85F + 0.15F * Mth.sin(time * 1.7F) * Mth.sin(time * 0.53F + 1.0F);
        VertexConsumer consumer = buffers.getBuffer(RenderType.eyes(TEXTURE));
        poseStack.pushPose();
        poseStack.translate(0.0, 0.3, 0.0);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        // outer glow, slowly turning
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(time * 3.0F));
        quad(poseStack, consumer, 0.9F * flicker, brightness * 0.75F, 0.55F, 0.75F, 1.0F);
        poseStack.popPose();
        // bright core, turning the other way
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(-time * 7.0F));
        quad(poseStack, consumer, 0.45F * flicker, brightness, 0.9F, 0.95F, 1.0F);
        poseStack.popPose();
        poseStack.popPose();
        super.render(ball, yaw, partialTick, poseStack, buffers, light);
    }

    private static void quad(PoseStack poseStack, VertexConsumer consumer, float size, float alpha, float r, float g, float b) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        int ri = (int) (r * alpha * 255), gi = (int) (g * alpha * 255), bi = (int) (b * alpha * 255);
        vertex(consumer, pose, matrix, -size, -size, 0, 1, ri, gi, bi);
        vertex(consumer, pose, matrix, size, -size, 1, 1, ri, gi, bi);
        vertex(consumer, pose, matrix, size, size, 1, 0, ri, gi, bi);
        vertex(consumer, pose, matrix, -size, size, 0, 0, ri, gi, bi);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Matrix4f matrix, float x, float y, float u, float v, int r, int g, int b) {
        consumer.addVertex(matrix, x, y, 0.0F).setColor(r, g, b, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    protected int getBlockLightLevel(BallLightningEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    public ResourceLocation getTextureLocation(BallLightningEntity entity) {
        return TEXTURE;
    }
}
