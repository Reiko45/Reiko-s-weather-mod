package com.reiko.weather.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reiko.weather.ReikosWeather;
import com.reiko.weather.entity.MeteorEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** A blazing fireball: bright, camera-facing glows, bigger and brighter the bigger the meteor. */
public class MeteorRenderer extends EntityRenderer<MeteorEntity> {
    private static final ResourceLocation TEXTURE = ReikosWeather.id("textures/entity/ball_lightning.png");

    public MeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(MeteorEntity meteor, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.eyes(TEXTURE));
        poseStack.pushPose();
        poseStack.translate(0.0, 0.5, 0.0);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        float size = meteor.size();
        if (size > 1.4F) {
            // the big ones wear a wide, dim halo of glowing air
            quad(poseStack, consumer, 5.5F * size, 110, 40, 10);
        }
        quad(poseStack, consumer, 3.2F * size, 255, 110, 20);
        quad(poseStack, consumer, 1.4F * size, 255, 230, 160);
        poseStack.popPose();
        super.render(meteor, yaw, partialTick, poseStack, buffers, light);
    }

    private static void quad(PoseStack poseStack, VertexConsumer consumer, float size, int r, int g, int b) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f m = pose.pose();
        v(consumer, pose, m, -size, -size, 0, 1, r, g, b);
        v(consumer, pose, m, size, -size, 1, 1, r, g, b);
        v(consumer, pose, m, size, size, 1, 0, r, g, b);
        v(consumer, pose, m, -size, size, 0, 0, r, g, b);
    }

    private static void v(VertexConsumer c, PoseStack.Pose pose, Matrix4f m, float x, float y, float u, float v, int r, int g, int b) {
        c.addVertex(m, x, y, 0.0F).setColor(r, g, b, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    protected int getBlockLightLevel(MeteorEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    public ResourceLocation getTextureLocation(MeteorEntity entity) {
        return TEXTURE;
    }
}
