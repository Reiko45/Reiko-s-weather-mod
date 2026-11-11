package com.reiko.weather.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reiko.weather.entity.TumbleweedEntity;
import com.reiko.weather.registry.ModBlocks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** A rolling tumbleweed: the tumbleweed block's tangle of twigs, turning over as it goes. */
public class TumbleweedRenderer extends EntityRenderer<TumbleweedEntity> {
    private final BlockRenderDispatcher blocks;

    public TumbleweedRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.3F;
    }

    @Override
    public void render(TumbleweedEntity weed, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float s = weed.size();
        poseStack.pushPose();
        poseStack.translate(0.0, s / 2.0, 0.0);
        // face the way it's going, then roll over forwards
        poseStack.mulPose(Axis.YP.rotation(-weed.heading));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-Mth.lerp(partialTick, weed.prevRoll, weed.roll)));
        poseStack.mulPose(Axis.XP.rotationDegrees((weed.getId() * 37) % 360));
        poseStack.scale(s * 1.3F, s * 1.3F, s * 1.3F);
        poseStack.translate(-0.5, -0.45, -0.5);
        blocks.renderSingleBlock(ModBlocks.TUMBLEWEED.get().defaultBlockState(), poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(weed, yaw, partialTick, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(TumbleweedEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
