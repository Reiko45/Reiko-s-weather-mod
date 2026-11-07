package com.reiko.weather.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.weather.Climate;
import com.reiko.weather.weather.SkyPhenomena;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Mirages: on hot, clear days out on flat sand or a salt flat, a shimmering band of sky seems to lie across
 * the ground in the distance, like water, always just out of reach.
 */
public final class Mirage {
    private static float strength;

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        float target = 0.0F;
        if (level != null && mc.player != null && level.dimension() == Level.OVERWORLD && ClientConfig.MIRAGES.get()) {
            BlockPos feet = mc.player.blockPosition();
            BlockState ground = level.getBlockState(feet.below());
            boolean flat = ground.is(BlockTags.SAND) || ground.is(ModBlocks.SALT_CRUST.get()) || ground.is(BlockTags.TERRACOTTA);
            float sun = SkyPhenomena.sunHeight(level, 1.0F);
            boolean hot = ClientWeather.climate() == Climate.ARID || ClientWeather.airTemperature() > 30.0F;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, feet.getX(), feet.getZ());
            if (flat && hot && sun > 0.3F && ClientWeather.overcast() < 0.3F && ClientWeather.rain() < 0.05F && mc.player.getY() - top < 3.0) {
                target = Mth.clamp((sun - 0.3F) * 3.0F, 0.0F, 1.0F);
            }
        }
        strength += Mth.clamp(target - strength, -0.01F, 0.01F);
    }

    public static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (strength < 0.02F || level == null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        Vec3 sky = level.getSkyColor(cam, partialTick);
        float time = level.getGameTime() + partialTick;
        // the band lies on the ground level we're standing on, far enough out that nearby dips and rises hide it
        float ground = (float) (mc.player.getY() - cam.y);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int segments = 64;
        float near = 24.0F, far = 75.0F;
        float r = (float) sky.x, g = (float) sky.y, b = (float) sky.z;
        for (int i = 0; i < segments; i++) {
            float a0 = i * Mth.TWO_PI / segments, a1 = (i + 1) * Mth.TWO_PI / segments;
            float s0 = shimmer(a0, time), s1 = shimmer(a1, time);
            float c0 = Mth.cos(a0), z0 = Mth.sin(a0), c1 = Mth.cos(a1), z1 = Mth.sin(a1);
            float y0 = ground - 0.1F, y1 = ground + 0.45F + 0.3F * s0, y2 = ground + 0.45F + 0.3F * s1;
            float alpha0 = 0.75F * strength * s0, alpha1 = 0.75F * strength * s1;
            // a flat sheet from near to far, brightest in the middle distance
            buffer.addVertex(c0 * near, y0, z0 * near).setColor(r, g, b, 0.0F);
            buffer.addVertex(c1 * near, y0, z1 * near).setColor(r, g, b, 0.0F);
            buffer.addVertex(c1 * far, y2, z1 * far).setColor(r, g, b, alpha1);
            buffer.addVertex(c0 * far, y1, z0 * far).setColor(r, g, b, alpha0);
        }
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static float shimmer(float angle, float time) {
        return 0.55F + 0.25F * Mth.sin(angle * 7.0F + time * 0.11F) + 0.2F * Mth.sin(angle * 13.0F - time * 0.17F);
    }

    public static void reset() {
        strength = 0.0F;
    }

    private Mirage() {}
}
