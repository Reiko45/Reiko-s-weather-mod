package com.reiko.weather.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.reiko.weather.ReikosWeather;
import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.weather.StormCell;
import com.reiko.weather.weather.Wind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A low, dark deck of storm clouds under the vanilla clouds. Its coverage is worked out per position,
 * so a storm cell shows up as a wall of cloud on the horizon long before it arrives.
 */
public final class CloudDeckRenderer {
    private static final ResourceLocation TEXTURE = ReikosWeather.id("textures/environment/storm_clouds.png");
    private static final int GRID = 48;
    private static final float CELL = 40.0F;
    private static final float FADE_START = 560.0F;
    private static final float FADE_END = GRID * CELL / 2.0F - CELL;
    private static final float[][] LAYERS = {{0.0F, 256.0F, 1.0F}, {7.0F, 176.0F, 0.75F}};

    public static void render(Vec3 cam, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD || !ClientConfig.STORM_CLOUDS.get() || !ClientWeather.hasStormClouds()) {
            return;
        }
        float fogFade = Mth.clamp(1.0F - ClientWeather.fogDensity() * 1.2F, 0.0F, 1.0F);
        if (fogFade <= 0.0F) {
            return;
        }
        float baseY = level.getSeaLevel() + 88.0F;
        double gx0 = Math.floor(cam.x / CELL) * CELL - GRID / 2.0 * CELL;
        double gz0 = Math.floor(cam.z / CELL) * CELL - GRID / 2.0 * CELL;
        float[] alpha = new float[(GRID + 1) * (GRID + 1)];
        boolean any = false;
        for (int i = 0; i <= GRID; i++) {
            for (int j = 0; j <= GRID; j++) {
                double wx = gx0 + i * CELL, wz = gz0 + j * CELL;
                double dist = Math.sqrt((wx - cam.x) * (wx - cam.x) + (wz - cam.z) * (wz - cam.z));
                float fade = 1.0F - Mth.clamp((float) ((dist - FADE_START) / (FADE_END - FADE_START)), 0.0F, 1.0F);
                float a = ClientWeather.cloudsAt(wx, wz) * fade * fogFade;
                alpha[i * (GRID + 1) + j] = a;
                any |= a > 0.01F;
            }
        }
        if (!any) {
            return;
        }

        float time = level.getGameTime() + partialTick;
        Vec3 wind = Wind.direction(ClientWeather.windAngle());
        float drift = time * (0.03F + ClientWeather.wind() * 0.12F);
        float daylight = level.getSkyDarken(partialTick);
        float flash = level.getSkyFlashTime() > 0 ? 0.35F : 0.0F;
        float shade = Mth.clamp(0.22F + 0.26F * daylight + flash, 0.0F, 1.0F);

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (float[] layer : LAYERS) {
            float y = (float) (baseY + layer[0] - cam.y);
            float scale = layer[1];
            double offX = wind.x * drift * layer[2], offZ = wind.z * drift * layer[2];
            float layerShade = shade * (layer[0] > 0 ? 1.1F : 1.0F);
            for (int i = 0; i < GRID; i++) {
                for (int j = 0; j < GRID; j++) {
                    float a00 = alpha[i * (GRID + 1) + j], a10 = alpha[(i + 1) * (GRID + 1) + j];
                    float a11 = alpha[(i + 1) * (GRID + 1) + j + 1], a01 = alpha[i * (GRID + 1) + j + 1];
                    if (a00 + a10 + a11 + a01 < 0.02F) {
                        continue;
                    }
                    double x0 = gx0 + i * CELL, z0 = gz0 + j * CELL, x1 = x0 + CELL, z1 = z0 + CELL;
                    vertex(buffer, x0, y, z0, cam, offX, offZ, scale, layerShade, a00 * layer[2]);
                    vertex(buffer, x1, y, z0, cam, offX, offZ, scale, layerShade, a10 * layer[2]);
                    vertex(buffer, x1, y, z1, cam, offX, offZ, scale, layerShade, a11 * layer[2]);
                    vertex(buffer, x0, y, z1, cam, offX, offZ, scale, layerShade, a01 * layer[2]);
                }
            }
        }
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        drawRainCurtains(level, cam, baseY, shade * 0.85F, fogFade);

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /**
     * Gray shafts of rain hanging under distant storm cells, so you can watch a storm coming across the
     * landscape. Drawn as camera-facing curtains, pulled in to at most 900 blocks so they stay on screen.
     */
    private static void drawRainCurtains(ClientLevel level, Vec3 cam, float deckY, float shade, float fogFade) {
        BufferBuilder buffer = null;
        float ground = level.getSeaLevel() - 2.0F;
        for (StormCell cell : SyncedWeather.cells()) {
            if (!cell.type.vanillaRain) {
                continue;
            }
            double dx = cell.x - cam.x, dz = cell.z - cam.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance - cell.radius < 40 || distance > 2200) {
                continue;
            }
            double nx = dx / distance, nz = dz / distance;
            double shown = Math.min(distance, 900.0);
            double scale = shown / distance;
            float half = (float) (cell.radius * 0.75 * scale);
            float alpha = 0.45F * cell.strength() * fogFade * (float) Mth.clamp((2200 - distance) / 600, 0, 1);
            float cx = (float) (nx * shown), cz = (float) (nz * shown);
            float px = (float) -nz * half, pz = (float) nx * half;
            float top = (float) ((deckY - cam.y) * scale), bottom = (float) ((ground - cam.y) * scale);
            if (buffer == null) {
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            }
            // a few overlapping columns so the edges look ragged
            for (int k = -2; k <= 2; k++) {
                float off = k * 0.35F, w = 0.32F;
                float ax = cx + px * (off - w), az = cz + pz * (off - w), bx = cx + px * (off + w), bz = cz + pz * (off + w);
                float a = alpha * (1.0F - Math.abs(k) * 0.18F);
                buffer.addVertex(ax, top, az).setColor(shade, shade, shade * 1.05F, a);
                buffer.addVertex(bx, top, bz).setColor(shade, shade, shade * 1.05F, a);
                buffer.addVertex(bx, bottom, bz).setColor(shade, shade, shade * 1.05F, a * 0.35F);
                buffer.addVertex(ax, bottom, az).setColor(shade, shade, shade * 1.05F, a * 0.35F);
            }
        }
        if (buffer != null) {
            MeshData mesh = buffer.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        }
    }

    private static void vertex(BufferBuilder buffer, double wx, float y, double wz, Vec3 cam, double offX, double offZ,
                               float scale, float shade, float alpha) {
        buffer.addVertex((float) (wx - cam.x), y, (float) (wz - cam.z))
                .setUv((float) ((wx - offX) / scale), (float) ((wz - offZ) / scale))
                .setColor(shade, shade, shade * 1.04F, Math.min(1.0F, alpha * 0.95F));
    }

    private CloudDeckRenderer() {}
}
