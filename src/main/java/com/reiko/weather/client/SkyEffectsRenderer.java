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

/** Draws rainbows, auroras, halos, sun dogs, sunbeams and the blood moon onto the sky dome, right after vanilla's sky. */
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
        float halo = ClientWeather.halo();
        float blood = ClientWeather.bloodMoon();
        if (rainbow <= 0.005F && aurora <= 0.005F && halo <= 0.005F && blood <= 0.005F && !ShootingStars.any()) {
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

        float night = Mth.clamp((-sun.y - 0.1F) * 4.0F, 0.0F, 1.0F);
        ShootingStars.render(pose, partialTick, night * clearSky);

        if (halo > 0.005F) {
            // around the sun by day, the (fainter) moon by night
            Vector3f moon = new Vector3f(sun).negate();
            if (sun.y > -0.05F) {
                drawHalo(pose, sun, halo * 0.22F * Mth.clamp((sun.y + 0.05F) * 6.0F, 0.0F, 1.0F));
                if (sun.y < 0.6F) {
                    float dogs = halo * Mth.clamp((0.6F - sun.y) * 4.0F, 0.0F, 1.0F) * Mth.clamp((sun.y + 0.05F) * 8.0F, 0.0F, 1.0F);
                    drawSunDogs(pose, sun, dogs);
                }
            }
            if (moon.y > -0.05F) {
                drawHalo(pose, moon, halo * 0.12F * Mth.clamp((moon.y + 0.05F) * 6.0F, 0.0F, 1.0F) * night);
            }
        }
        if (blood > 0.005F) {
            drawBloodMoon(pose, new Vector3f(sun).negate(), blood);
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

    /** A 22-degree ring of light around the sun or moon, faintly red on the inside. */
    private static void drawHalo(Matrix4f pose, Vector3f center, float alpha) {
        if (alpha <= 0.003F) {
            return;
        }
        Vector3f[] basis = basis(center);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[][] bands = {{20.6F, 0.0F}, {21.6F, 1.0F}, {22.6F, 0.7F}, {24.5F, 0.0F}};
        float[][] colors = {{1.0F, 0.6F, 0.45F}, {1.0F, 0.85F, 0.75F}, {0.9F, 0.93F, 1.0F}, {0.85F, 0.9F, 1.0F}};
        int segments = 96;
        for (int i = 0; i < segments; i++) {
            float phi0 = i / (float) segments * Mth.TWO_PI, phi1 = (i + 1) / (float) segments * Mth.TWO_PI;
            for (int b = 0; b < bands.length - 1; b++) {
                float rho0 = bands[b][0] * Mth.DEG_TO_RAD, rho1 = bands[b + 1][0] * Mth.DEG_TO_RAD;
                Vector3f p00 = ringPoint(center, basis[0], basis[1], phi0, rho0), p10 = ringPoint(center, basis[0], basis[1], phi1, rho0);
                Vector3f p11 = ringPoint(center, basis[0], basis[1], phi1, rho1), p01 = ringPoint(center, basis[0], basis[1], phi0, rho1);
                if (p00.y < -3.0F && p10.y < -3.0F && p11.y < -3.0F) {
                    continue;
                }
                float a0 = alpha * bands[b][1], a1 = alpha * bands[b + 1][1];
                vertex(buffer, pose, p00, colors[b], a0 * horizonFade(p00));
                vertex(buffer, pose, p10, colors[b], a0 * horizonFade(p10));
                vertex(buffer, pose, p11, colors[b + 1], a1 * horizonFade(p11));
                vertex(buffer, pose, p01, colors[b + 1], a1 * horizonFade(p01));
            }
        }
        draw(buffer);
    }

    /** Two bright, rainbow-edged spots level with a low sun, one on each side of it. */
    private static void drawSunDogs(Matrix4f pose, Vector3f sun, float strength) {
        if (strength <= 0.005F) {
            return;
        }
        float elevation = (float) Math.asin(Mth.clamp(sun.y, -1.0F, 1.0F));
        float cosE = Mth.cos(elevation);
        // keep the dogs 22 degrees from the sun: they swing outward as the sun climbs
        float cosD = (Mth.cos(22.0F * Mth.DEG_TO_RAD) - sun.y * sun.y) / Math.max(0.05F, cosE * cosE);
        float delta = (float) Math.acos(Mth.clamp(cosD, -1.0F, 1.0F));
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int side = -1; side <= 1; side += 2) {
            Vector3f dog = new Vector3f(sun).rotateY(side * delta).normalize();
            // the horizontal axis that points away from the sun
            Vector3f out = new Vector3f(dog).sub(sun);
            out.sub(new Vector3f(dog).mul(out.dot(dog))).normalize();
            Vector3f up = new Vector3f(dog).cross(out).normalize();
            if (up.y < 0) up.negate();
            float w = 2.0F * Mth.DEG_TO_RAD, h = 2.8F * Mth.DEG_TO_RAD, tail = 9.0F * Mth.DEG_TO_RAD;
            float a = strength * 0.8F;
            // red inner edge, white core, bluish outer edge, then a faint tail away from the sun
            spot(buffer, pose, dog, out, up, -w, 0.0F, h, 1.0F, 0.45F, 0.3F, 0.0F, a);
            spot(buffer, pose, dog, out, up, 0.0F, w, h, 1.0F, 0.95F, 0.85F, a, a * 0.6F);
            spot(buffer, pose, dog, out, up, w, tail, h * 0.4F, 0.85F, 0.9F, 1.0F, a * 0.6F, 0.0F);
        }
        draw(buffer);
    }

    private static void spot(BufferBuilder buffer, Matrix4f pose, Vector3f center, Vector3f out, Vector3f up,
                             float from, float to, float halfHeight, float r, float g, float b, float alphaFrom, float alphaTo) {
        Vector3f c0 = new Vector3f(center).add(new Vector3f(out).mul(from)).normalize().mul(DOME);
        Vector3f c1 = new Vector3f(center).add(new Vector3f(out).mul(to)).normalize().mul(DOME);
        Vector3f dy = new Vector3f(up).mul(halfHeight * DOME);
        float[] color = {r, g, b};
        vertex(buffer, pose, new Vector3f(c0).sub(dy), color, 0.0F);
        vertex(buffer, pose, new Vector3f(c1).sub(dy), color, 0.0F);
        vertex(buffer, pose, c1, color, alphaTo);
        vertex(buffer, pose, c0, color, alphaFrom);
        vertex(buffer, pose, c0, color, alphaFrom);
        vertex(buffer, pose, c1, color, alphaTo);
        vertex(buffer, pose, new Vector3f(c1).add(dy), color, 0.0F);
        vertex(buffer, pose, new Vector3f(c0).add(dy), color, 0.0F);
    }

    /**
     * Sunbeams hang in the air below the clouds, so they're drawn after the storm cloud deck (from the
     * particle stage), still on a dome around the camera so nearby terrain hides them.
     */
    public static void renderSunbeams(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        float beams = ClientWeather.sunbeams();
        if (level == null || beams <= 0.005F || level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL
                || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float theta = level.getTimeOfDay(partialTick) * Mth.TWO_PI;
        Vector3f sun = new Vector3f(-Mth.sin(theta), Mth.cos(theta), 0.0F);
        if (sun.y <= 0.0F) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        drawSunbeams(new Matrix4f(), sun, level.getGameTime() + partialTick, beams * Mth.clamp(sun.y * 5.0F, 0.0F, 1.0F));
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** Crepuscular rays fanning out from the sun through gaps in the cloud. */
    private static void drawSunbeams(Matrix4f pose, Vector3f sun, float time, float strength) {
        Vector3f[] basis = basis(sun);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float[] warm = {1.0F, 0.94F, 0.78F};
        int rays = 14;
        for (int i = 0; i < rays; i++) {
            float seed = i * 12.9898F;
            float phi = (i + 0.5F * Mth.sin(seed)) / rays * Mth.TWO_PI + 0.05F * Mth.sin(time * 0.0007F + seed);
            float width = (1.2F + 1.4F * (0.5F + 0.5F * Mth.sin(seed * 3.1F))) * Mth.DEG_TO_RAD;
            float length = (45.0F + 30.0F * (0.5F + 0.5F * Mth.sin(seed * 1.7F))) * Mth.DEG_TO_RAD;
            // each ray brightens and fades as clouds drift across the sun
            float flicker = Mth.clamp(0.5F + 0.6F * Mth.sin(time * 0.004F + seed * 2.3F), 0.0F, 1.0F);
            float a = strength * 0.4F * flicker;
            if (a <= 0.002F) continue;
            Vector3f start = ringPoint(sun, basis[0], basis[1], phi, 3.0F * Mth.DEG_TO_RAD);
            Vector3f left = ringPoint(sun, basis[0], basis[1], phi - width / Mth.sin(length), length);
            Vector3f right = ringPoint(sun, basis[0], basis[1], phi + width / Mth.sin(length), length);
            vertex(buffer, pose, start, warm, a * horizonFade(start));
            vertex(buffer, pose, left, warm, 0.0F);
            vertex(buffer, pose, right, warm, 0.0F);
        }
        draw(buffer);
    }

    /** A blood moon: the moon dyed deep red, with a red glow around it. */
    private static void drawBloodMoon(Matrix4f pose, Vector3f moon, float strength) {
        if (moon.y < -0.1F) {
            return;
        }
        Vector3f[] basis = basis(moon);
        // multiply the moon (and the dark sky right behind it) toward red
        RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ZERO);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        // just the moon's disc (vanilla's moon quad is mostly empty texture around it)
        float half = 3.4F * Mth.DEG_TO_RAD;
        float[] tint = {1.0F, Mth.lerp(strength, 1.0F, 0.32F), Mth.lerp(strength, 1.0F, 0.22F)};
        for (int k = 0; k < 4; k++) {
            float phi = (k * 90 + 45) * Mth.DEG_TO_RAD;
            vertex(buffer, pose, ringPoint(moon, basis[0], basis[1], phi, half * Mth.SQRT_OF_TWO), tint, 1.0F);
        }
        draw(buffer);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float[] red = {0.9F, 0.12F, 0.08F};
        int segments = 32;
        Vector3f c = new Vector3f(moon).normalize().mul(DOME);
        for (int i = 0; i < segments; i++) {
            float phi0 = i / (float) segments * Mth.TWO_PI, phi1 = (i + 1) / (float) segments * Mth.TWO_PI;
            vertex(buffer, pose, c, red, 0.35F * strength);
            vertex(buffer, pose, ringPoint(moon, basis[0], basis[1], phi0, 26.0F * Mth.DEG_TO_RAD), red, 0.0F);
            vertex(buffer, pose, ringPoint(moon, basis[0], basis[1], phi1, 26.0F * Mth.DEG_TO_RAD), red, 0.0F);
        }
        draw(buffer);
    }

    /** Two unit vectors perpendicular to a direction (and to each other). */
    private static Vector3f[] basis(Vector3f dir) {
        Vector3f d = new Vector3f(dir).normalize();
        Vector3f ref = Math.abs(d.y) < 0.95F ? new Vector3f(0.0F, 1.0F, 0.0F) : new Vector3f(1.0F, 0.0F, 0.0F);
        Vector3f u = new Vector3f(ref).sub(new Vector3f(d).mul(d.dot(ref))).normalize();
        Vector3f v = new Vector3f(d).cross(u).normalize();
        return new Vector3f[]{u, v};
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
