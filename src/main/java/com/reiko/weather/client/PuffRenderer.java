package com.reiko.weather.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.reiko.weather.ReikosWeather;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Big, soft, camera-facing cloud puffs in the world: volcano plumes, towering thunderstorm clouds, sea fog
 * banks. Effects add puffs during the frame and they're all drawn together, back to front. Puffs past the
 * far clip plane are slid in along the view ray (and shrunk to match), so they stay visible from far away.
 */
public final class PuffRenderer {
    private static final ResourceLocation PUFF = ReikosWeather.id("textures/environment/volcano_plume.png");

    private record Puff(float x, float y, float z, float size, float r, float g, float b, float a, boolean glow, float dist) {}

    private static final List<Puff> PUFFS = new ArrayList<>();

    /**
     * Adds a puff at a world position. {@code glow} puffs are drawn additively (lava light, sunlit tops).
     * Colours are clamped: anything past 1 would wrap around in the vertex buffer.
     */
    public static void add(double wx, double wy, double wz, float size, float r, float g, float b, float a, boolean glow, Vec3 cam, float maxDistance) {
        if (a < 0.01F) return;
        r = Mth.clamp(r, 0.0F, 1.0F);
        g = Mth.clamp(g, 0.0F, 1.0F);
        b = Mth.clamp(b, 0.0F, 1.0F);
        a = Math.min(a, 1.0F);
        float x = (float) (wx - cam.x), y = (float) (wy - cam.y), z = (float) (wz - cam.z);
        float d = Mth.sqrt(x * x + y * y + z * z);
        if (d < size * 0.6F) return; // we're inside it: the fog does the work
        if (d > maxDistance) {
            float k = maxDistance / d;
            x *= k;
            y *= k;
            z *= k;
            size *= k;
        }
        PUFFS.add(new Puff(x, y, z, size, r, g, b, a, glow, d));
    }

    /** Draws everything added this frame (at the particle stage, after the cloud deck). */
    public static void render(Camera camera) {
        if (PUFFS.isEmpty()) {
            return;
        }
        PUFFS.sort((p, q) -> Float.compare(q.dist(), p.dist()));
        Vector3f left = new Vector3f(camera.getLeftVector()), up = new Vector3f(camera.getUpVector());
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, PUFF);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        for (boolean glow : new boolean[]{false, true}) {
            if (glow) {
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            } else {
                RenderSystem.defaultBlendFunc();
            }
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            boolean any = false;
            for (Puff p : PUFFS) {
                if (p.glow() != glow) continue;
                float lx = left.x() * p.size(), ly = left.y() * p.size(), lz = left.z() * p.size();
                float ux = up.x() * p.size(), uy = up.y() * p.size(), uz = up.z() * p.size();
                buffer.addVertex(p.x() - lx - ux, p.y() - ly - uy, p.z() - lz - uz).setUv(0, 1).setColor(p.r(), p.g(), p.b(), p.a());
                buffer.addVertex(p.x() + lx - ux, p.y() + ly - uy, p.z() + lz - uz).setUv(1, 1).setColor(p.r(), p.g(), p.b(), p.a());
                buffer.addVertex(p.x() + lx + ux, p.y() + ly + uy, p.z() + lz + uz).setUv(1, 0).setColor(p.r(), p.g(), p.b(), p.a());
                buffer.addVertex(p.x() - lx + ux, p.y() - ly + uy, p.z() - lz + uz).setUv(0, 0).setColor(p.r(), p.g(), p.b(), p.a());
                any = true;
            }
            MeshData mesh = buffer.build();
            if (mesh != null && any) {
                BufferUploader.drawWithShader(mesh);
            } else if (mesh != null) {
                mesh.close();
            }
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        PUFFS.clear();
    }

    /** A repeatable pseudo-random number in [0, 1) for puff i of some object. */
    public static float hash(int i, int salt) {
        int h = Mth.murmurHash3Mixer(i * 0x9E3779B9 + salt * 0x85EBCA6B);
        return (h >>> 8) / (float) (1 << 24);
    }

    private PuffRenderer() {}
}
