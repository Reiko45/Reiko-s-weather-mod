package com.reiko.weather.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.reiko.weather.ReikosWeather;
import com.reiko.weather.entity.VolcanicBombEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import org.joml.Matrix4f;

/** A tumbling, glowing lump of molten rock with a hot halo around it. */
public class VolcanicBombRenderer extends EntityRenderer<VolcanicBombEntity> {
    private static final ResourceLocation GLOW = ReikosWeather.id("textures/entity/ball_lightning.png");
    private final BlockRenderDispatcher blocks;

    public VolcanicBombRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(VolcanicBombEntity bomb, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float spin = (bomb.tickCount + partialTick) * 9.0F + bomb.getId() * 37.0F;
        poseStack.pushPose();
        poseStack.translate(0.0, 0.35, 0.0);
        poseStack.mulPose(Axis.XP.rotationDegrees(spin));
        poseStack.mulPose(Axis.ZP.rotationDegrees(spin * 0.7F));
        poseStack.scale(0.7F, 0.7F, 0.7F);
        poseStack.translate(-0.5, -0.5, -0.5);
        blocks.renderSingleBlock(Blocks.MAGMA_BLOCK.defaultBlockState(), poseStack, buffers, 0xF000F0, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        VertexConsumer consumer = buffers.getBuffer(RenderType.eyes(GLOW));
        poseStack.pushPose();
        poseStack.translate(0.0, 0.35, 0.0);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        PoseStack.Pose pose = poseStack.last();
        Matrix4f m = pose.pose();
        float size = 1.3F;
        v(consumer, pose, m, -size, -size, 0, 1);
        v(consumer, pose, m, size, -size, 1, 1);
        v(consumer, pose, m, size, size, 1, 0);
        v(consumer, pose, m, -size, size, 0, 0);
        poseStack.popPose();
        super.render(bomb, yaw, partialTick, poseStack, buffers, light);
    }

    private static void v(VertexConsumer c, PoseStack.Pose pose, Matrix4f m, float x, float y, float u, float v) {
        c.addVertex(m, x, y, 0.0F).setColor(255, 96, 20, 200).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    protected int getBlockLightLevel(VolcanicBombEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    public ResourceLocation getTextureLocation(VolcanicBombEntity entity) {
        return GLOW;
    }
}
