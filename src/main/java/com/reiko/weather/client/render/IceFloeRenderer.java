package com.reiko.weather.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reiko.weather.entity.IceFloeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** A rough slab of grey-white river ice with a crust of old snow on top, and a lump or two broken against it. */
public class IceFloeRenderer extends EntityRenderer<IceFloeEntity> {
    private static final BlockState ICE = Blocks.PACKED_ICE.defaultBlockState();
    private static final BlockState SNOW = Blocks.SNOW_BLOCK.defaultBlockState();
    private final BlockRenderDispatcher blocks;

    public IceFloeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(IceFloeEntity floe, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float w = floe.size() * 2.0F;
        int id = floe.getId();
        // a slow bob and wobble on the water
        float t = floe.tickCount + partialTick;
        poseStack.pushPose();
        poseStack.translate(0.0, 0.02 * Mth.sin(t * 0.07F + id), 0.0);
        poseStack.mulPose(Axis.XP.rotationDegrees(1.2F * Mth.sin(t * 0.05F + id * 3)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(1.2F * Mth.cos(t * 0.04F + id * 7)));
        slab(poseStack, buffers, light, ICE, 0, 0, 0, w, 0.36F, w);
        slab(poseStack, buffers, light, SNOW, -w * 0.08F, 0.36F, w * 0.05F, w * 0.78F, 0.08F, w * 0.72F);
        // broken-off chunks frozen onto the edges
        for (int i = 0; i < 2; i++) {
            float a = (Mth.murmurHash3Mixer(id * 31 + i) & 1023) / 1023.0F * Mth.TWO_PI;
            float r = w * 0.42F;
            slab(poseStack, buffers, light, ICE, Mth.cos(a) * r, 0.05F, Mth.sin(a) * r, w * 0.32F, 0.3F, w * 0.28F);
        }
        poseStack.popPose();
        super.render(floe, yaw, partialTick, poseStack, buffers, light);
    }

    /** A block model squashed into a box centred at (x, z), sitting on y. */
    private void slab(PoseStack poseStack, MultiBufferSource buffers, int light, BlockState state, float x, float y, float z, float sx, float sy, float sz) {
        poseStack.pushPose();
        poseStack.translate(x - sx / 2.0F, y, z - sz / 2.0F);
        poseStack.scale(sx, sy, sz);
        blocks.renderSingleBlock(state, poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(IceFloeEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
