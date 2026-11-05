package com.reiko.weather.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Darkens rain-soaked ground: a faint film over the exposed top faces around the player, which builds up
 * while it rains and slowly dries afterwards.
 */
public final class WetGroundRenderer {
    private static final int RADIUS = 22;
    private static final IntArrayList TOPS = new IntArrayList();
    private static BlockPos lastCenter = BlockPos.ZERO;
    private static long lastBuild = -100;

    public static void render(Vec3 cam) {
        float wetness = ClientWeather.wetness();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (wetness < 0.01F || level == null) {
            return;
        }
        BlockPos center = BlockPos.containing(cam);
        if (level.getGameTime() - lastBuild > 10 || center.distManhattan(lastCenter) > 3) {
            rebuild(level, center);
        }
        if (TOPS.isEmpty()) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1.0F, -10.0F);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < TOPS.size(); i += 3) {
            int x = TOPS.getInt(i), y = TOPS.getInt(i + 1), z = TOPS.getInt(i + 2);
            double dx = x + 0.5 - cam.x, dz = z + 0.5 - cam.z;
            float fade = 1.0F - (float) Math.min(1.0, Math.sqrt(dx * dx + dz * dz) / RADIUS);
            float a = wetness * 0.3F * Math.min(1.0F, fade * 2.5F);
            float fy = (float) (y - cam.y) + 0.001F;
            float fx = (float) (x - cam.x), fz = (float) (z - cam.z);
            buffer.addVertex(fx, fy, fz).setColor(0.04F, 0.06F, 0.1F, a);
            buffer.addVertex(fx, fy, fz + 1).setColor(0.04F, 0.06F, 0.1F, a);
            buffer.addVertex(fx + 1, fy, fz + 1).setColor(0.04F, 0.06F, 0.1F, a);
            buffer.addVertex(fx + 1, fy, fz).setColor(0.04F, 0.06F, 0.1F, a);
        }
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        RenderSystem.polygonOffset(0.0F, 0.0F);
        RenderSystem.disablePolygonOffset();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /** Finds the exposed, flat, solid top faces in rainy columns around the camera. */
    private static void rebuild(ClientLevel level, BlockPos center) {
        TOPS.clear();
        lastCenter = center;
        lastBuild = level.getGameTime();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int x = center.getX() + dx, z = center.getZ() + dz;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                if (Math.abs(top - center.getY()) > 24) {
                    continue;
                }
                pos.set(x, top - 1, z);
                BlockState state = level.getBlockState(pos);
                if (!state.getFluidState().isEmpty() || state.is(BlockTags.LEAVES) || !state.isFaceSturdy(level, pos, Direction.UP)
                        || state.getShape(level, pos).max(Direction.Axis.Y) < 1.0) {
                    continue;
                }
                pos.set(x, top, z);
                if (level.getBiome(pos).value().getPrecipitationAt(pos) != Biome.Precipitation.RAIN) {
                    continue;
                }
                TOPS.add(x);
                TOPS.add(top);
                TOPS.add(z);
            }
        }
    }

    private WetGroundRenderer() {}
}
