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
        if (floe.berg()) {
            renderBerg(floe, partialTick, poseStack, buffers, light);
            super.render(floe, yaw, partialTick, poseStack, buffers, light);
            return;
        }
        float w = floe.size() * 2.0F;
        int id = floe.getId();
        // a slow bob and wobble on the water
        float t = floe.tickCount + partialTick;
        poseStack.pushPose();
        poseStack.translate(0.0, 0.02 * Mth.sin(t * 0.07F + id), 0.0);
        poseStack.mulPose(Axis.XP.rotationDegrees(1.2F * Mth.sin(t * 0.05F + id * 3)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(1.2F * Mth.cos(t * 0.04F + id * 7)));
        if (floe.pumice()) {
            // a raft of pale, frothy stone riding low in the water, in lumps
            BlockState pumice = com.reiko.weather.registry.ModBlocks.PUMICE.get().defaultBlockState();
            slab(poseStack, buffers, light, pumice, 0, 0, 0, w, 0.22F, w);
            for (int i = 0; i < 4; i++) {
                float a = (Mth.murmurHash3Mixer(id * 17 + i) & 1023) / 1023.0F * Mth.TWO_PI;
                float r = w * 0.3F;
                slab(poseStack, buffers, light, pumice, Mth.cos(a) * r, 0.12F, Mth.sin(a) * r, w * 0.35F, 0.16F, w * 0.3F);
            }
            poseStack.popPose();
            super.render(floe, yaw, partialTick, poseStack, buffers, light);
            return;
        }
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

    /**
     * An iceberg: a craggy mass of old, compressed ice, white and blue, with most of it hidden under the water
     * (you can see it going down a long way below the surface).
     */
    private void renderBerg(IceFloeEntity berg, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float w = berg.size() * 2.0F, h = berg.bergHeight();
        int id = berg.getId();
        float t = berg.tickCount + partialTick;
        poseStack.pushPose();
        poseStack.translate(0.0, 0.05 * Mth.sin(t * 0.03F + id), 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees((id * 47) % 360));
        BlockState blue = Blocks.BLUE_ICE.defaultBlockState();
        // the bulk under water, wider than what shows
        slab(poseStack, buffers, light, ICE, 0, -h * 2.2F, 0, w * 1.15F, h * 2.2F, w * 1.1F);
        slab(poseStack, buffers, light, ICE, 0, 0, 0, w, h * 0.55F, w * 0.92F);
        // crags and pinnacles standing up out of it
        for (int i = 0; i < 4; i++) {
            float hx = ((Mth.murmurHash3Mixer(id * 13 + i) & 1023) / 1023.0F - 0.5F) * w * 0.5F;
            float hz = ((Mth.murmurHash3Mixer(id * 29 + i) & 1023) / 1023.0F - 0.5F) * w * 0.5F;
            float ch = h * (0.6F + 0.4F * ((Mth.murmurHash3Mixer(id * 53 + i) & 1023) / 1023.0F));
            float cw = w * (0.25F + 0.2F * ((Mth.murmurHash3Mixer(id * 71 + i) & 1023) / 1023.0F));
            slab(poseStack, buffers, light, i == 1 ? blue : ICE, hx, 0, hz, cw, ch, cw * 0.9F);
            slab(poseStack, buffers, light, SNOW, hx, ch, hz, cw * 0.8F, 0.12F, cw * 0.7F);
        }
        poseStack.popPose();
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
