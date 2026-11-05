package com.reiko.weather.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Draws rainbows and auroras onto the sky dome, right after vanilla's sky. */
public final class SkyEffectsRenderer {
    private static final float DOME = 100.0F;

    /** Rainbow colors from the inside (violet) to the outside (red). */
    private static final float[][] RAINBOW = {
            {0.55F, 0.20F, 0.85F},
            {0.25F, 0.30F, 1.00F},
            {0.15F, 0.75F, 1.00F},
            {0.20F, 0.95F, 0.30F},
            {1.00F, 0.95F, 0.20F},
            {1.00F, 0.55F, 0.10F},
            {1.00F, 0.15F, 0.15F},
    };

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL
                || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        float rainbow = ClientWeather.rainbow();
        float aurora = ClientWeather.aurora();
        if (rainbow <= 0.005F && aurora <= 0.005F) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float theta = level.getTimeOfDay(partialTick) * Mth.TWO_PI;
        Vector3f sun = new Vector3f(-Mth.sin(theta), Mth.cos(theta), 0.0F);
        float clearSky = (1.0F - level.getRainLevel(partialTick)) * (1.0F - Math.min(1.0F, ClientWeather.fogDensity() * 1.5F));

        Matrix4f pose = new Matrix4f(event.getModelViewMatrix());
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (rainbow > 0.005F) {
            // the rainbow sits 42 degrees from the anti-solar point, so it needs a low sun
            float alpha = rainbow * clearSky * 0.55F
                    * Mth.clamp((0.68F - sun.y) * 4.0F, 0.0F, 1.0F)
                    * Mth.clamp((sun.y + 0.02F) * 10.0F, 0.0F, 1.0F);
            if (alpha > 0.005F) {
                drawRainbow(pose, sun, alpha);
            }
        }
        if (aurora > 0.005F) {
            float night = Mth.clamp((-sun.y - 0.05F) * 4.0F, 0.0F, 1.0F);
            float alpha = aurora * night * clearSky;
            if (alpha > 0.005F) {
                drawAurora(pose, level.getGameTime() + partialTick, alpha);
            }
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void drawRainbow(Matrix4f pose, Vector3f sun, float alpha) {
        Vector3f anti = new Vector3f(sun).negate().normalize();
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f u = new Vector3f(up).sub(new Vector3f(anti).mul(anti.dot(up)));
        if (u.lengthSquared() < 1.0E-5F) {
            u.set(1.0F, 0.0F, 0.0F);
        }
        u.normalize();
        Vector3f v = new Vector3f(anti).cross(u).normalize();

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int segments = 96;
        float inner = 39.5F * Mth.DEG_TO_RAD;
        float outer = 42.5F * Mth.DEG_TO_RAD;
        int bands = RAINBOW.length + 1;
        for (int s = 0; s < segments; s++) {
            float phi0 = s / (float) segments * Mth.TWO_PI;
            float phi1 = (s + 1) / (float) segments * Mth.TWO_PI;
            for (int b = 0; b < bands; b++) {
                float rho0 = Mth.lerp(b / (float) bands, inner, outer);
                float rho1 = Mth.lerp((b + 1) / (float) bands, inner, outer);
                Vector3f p00 = ringPoint(anti, u, v, phi0, rho0);
                Vector3f p10 = ringPoint(anti, u, v, phi1, rho0);
                Vector3f p11 = ringPoint(anti, u, v, phi1, rho1);
                Vector3f p01 = ringPoint(anti, u, v, phi0, rho1);
                if (p00.y < -0.03F && p10.y < -0.03F) {
                    continue; // below the horizon
                }
                float[] c0 = bandColor(b - 1);
                float[] c1 = bandColor(b);
                float a0 = b == 0 ? 0.0F : alpha;
                float a1 = b == bands - 1 ? 0.0F : alpha;
                vertex(buffer, pose, p00, c0, a0 * horizonFade(p00));
                vertex(buffer, pose, p10, c0, a0 * horizonFade(p10));
                vertex(buffer, pose, p11, c1, a1 * horizonFade(p11));
                vertex(buffer, pose, p01, c1, a1 * horizonFade(p01));
            }
        }
        draw(buffer);
    }

    private static float[] bandColor(int index) {
        return RAINBOW[Mth.clamp(index, 0, RAINBOW.length - 1)];
    }

    private static float horizonFade(Vector3f p) {
        return Mth.clamp((p.y / DOME + 0.03F) * 8.0F, 0.0F, 1.0F);
    }

    private static Vector3f ringPoint(Vector3f center, Vector3f u, Vector3f v, float phi, float rho) {
        float s = Mth.sin(rho);
        return new Vector3f(center).mul(Mth.cos(rho))
                .add(new Vector3f(u).mul(Mth.cos(phi) * s))
                .add(new Vector3f(v).mul(Mth.sin(phi) * s))
                .mul(DOME);
    }

    /** Three shimmering curtains hanging over the northern sky. */
    private static void drawAurora(Matrix4f pose, float time, float alpha) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int steps = 64;
        for (int curtain = 0; curtain < 3; curtain++) {
            float offset = curtain * 2.1F;
            float centre = (curtain - 1) * 0.55F;
            float span = 0.9F + curtain * 0.15F;
            for (int i = 0; i < steps; i++) {
                float t0 = i / (float) steps, t1 = (i + 1) / (float) steps;
                float[] a = curtainPoint(t0, time, offset, centre, span);
                float[] b = curtainPoint(t1, time, offset, centre, span);
                float edge0 = Mth.sin(t0 * Mth.PI), edge1 = Mth.sin(t1 * Mth.PI);
                float glow0 = alpha * edge0 * a[4], glow1 = alpha * edge1 * b[4];
                // lower band: bright green
                quad(buffer, pose, a, b, a[2], b[2], a[3], b[3], 0.25F, 1.0F, 0.55F, glow0 * 0.9F, glow1 * 0.9F, 0.35F, 0.9F, 0.75F, glow0 * 0.45F, glow1 * 0.45F);
                // upper band: fading into violet
                float topA = a[3] + (a[3] - a[2]) * 0.8F, topB = b[3] + (b[3] - b[2]) * 0.8F;
                quad(buffer, pose, a, b, a[3], b[3], topA, topB, 0.35F, 0.9F, 0.75F, glow0 * 0.45F, glow1 * 0.45F, 0.6F, 0.25F, 0.9F, 0.0F, 0.0F);
            }
        }
        draw(buffer);
    }

    /** Returns {azimuth, unused, bottomElevation, topElevation, brightness} for a point along a curtain. */
    private static float[] curtainPoint(float t, float time, float offset, float centre, float span) {
        float azimuth = centre + (t - 0.5F) * span * 2.2F + 0.06F * Mth.sin(t * 9.0F + time * 0.011F + offset);
        float bottom = (16.0F + 5.0F * Mth.sin(t * 5.0F + time * 0.007F + offset)) * Mth.DEG_TO_RAD;
        float height = (14.0F + 9.0F * (0.5F + 0.5F * Mth.sin(t * 7.0F - time * 0.013F + offset * 1.7F))) * Mth.DEG_TO_RAD;
        float brightness = 0.55F + 0.45F * Mth.sin(t * 23.0F + time * 0.05F + offset * 3.0F) * Mth.sin(t * 3.0F + time * 0.004F);
        return new float[]{azimuth, 0.0F, bottom, bottom + height, Mth.clamp(brightness, 0.15F, 1.0F)};
    }

    private static void quad(BufferBuilder buffer, Matrix4f pose, float[] a, float[] b,
                             float aLow, float bLow, float aHigh, float bHigh,
                             float r0, float g0, float b0, float alphaA0, float alphaB0,
                             float r1, float g1, float b1, float alphaA1, float alphaB1) {
        Vector3f p0 = sky(a[0], aLow), p1 = sky(b[0], bLow), p2 = sky(b[0], bHigh), p3 = sky(a[0], aHigh);
        buffer.addVertex(pose, p0.x, p0.y, p0.z).setColor(r0, g0, b0, alphaA0);
        buffer.addVertex(pose, p1.x, p1.y, p1.z).setColor(r0, g0, b0, alphaB0);
        buffer.addVertex(pose, p2.x, p2.y, p2.z).setColor(r1, g1, b1, alphaB1);
        buffer.addVertex(pose, p3.x, p3.y, p3.z).setColor(r1, g1, b1, alphaA1);
    }

    /** A point on the sky dome; azimuth 0 is due north (-Z). */
    private static Vector3f sky(float azimuth, float elevation) {
        float c = Mth.cos(elevation);
        return new Vector3f(Mth.sin(azimuth) * c, Mth.sin(elevation), -Mth.cos(azimuth) * c).mul(DOME);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f pose, Vector3f p, float[] color, float alpha) {
        buffer.addVertex(pose, p.x, p.y, p.z).setColor(color[0], color[1], color[2], alpha);
    }

    private static void draw(BufferBuilder buffer) {
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    private SkyEffectsRenderer() {}
}
