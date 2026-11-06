package com.reiko.weather.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.weather.SkyPhenomena;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Shooting stars streaking across clear night skies; dozens of them on meteor shower nights. */
public final class ShootingStars {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final float DOME = 99.0F;
    private static final List<Star> STARS = new ArrayList<>();

    private static final class Star {
        final Vector3f start;
        final Vector3f axis;
        final float speed;
        final int life;
        final float brightness;
        int age;

        Star(Vector3f start, Vector3f axis, float speed, int life, float brightness) {
            this.start = start;
            this.axis = axis;
            this.speed = speed;
            this.life = life;
            this.brightness = brightness;
        }

        Vector3f at(float t) {
            return new Quaternionf().rotateAxis(speed * t, axis).transform(new Vector3f(start));
        }
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        STARS.removeIf(s -> ++s.age > s.life);
        if (level == null || level.dimension() != Level.OVERWORLD || !ClientConfig.SHOOTING_STARS.get() || mc.isPaused()) {
            return;
        }
        float sun = SkyPhenomena.sunHeight(level, 1.0F);
        if (sun > -0.15F || ClientWeather.sunOcclusion() > 0.4F || level.getRainLevel(1.0F) > 0.2F) {
            return;
        }
        boolean shower = SkyPhenomena.isMeteorShowerNight(level.getDayTime());
        if (RANDOM.nextInt(shower ? 18 : 700) != 0) {
            return;
        }
        // shower meteors all appear to come from one point in the sky (the radiant)
        float azimuth = shower ? 0.8F + (RANDOM.nextFloat() - 0.5F) * 1.2F : RANDOM.nextFloat() * Mth.TWO_PI;
        float elevation = (25.0F + RANDOM.nextFloat() * 45.0F) * Mth.DEG_TO_RAD;
        Vector3f start = new Vector3f(Mth.sin(azimuth) * Mth.cos(elevation), Mth.sin(elevation), -Mth.cos(azimuth) * Mth.cos(elevation));
        Vector3f heading = shower ? new Vector3f(start).cross(0, 1, 0).add(0, -0.6F, 0)
                : new Vector3f(RANDOM.nextFloat() - 0.5F, -0.3F - RANDOM.nextFloat() * 0.4F, RANDOM.nextFloat() - 0.5F);
        Vector3f axis = new Vector3f(start).cross(heading);
        if (axis.lengthSquared() < 1.0E-4F) {
            return;
        }
        axis.normalize();
        STARS.add(new Star(start, axis, 0.012F + RANDOM.nextFloat() * 0.012F, 12 + RANDOM.nextInt(14), 0.6F + RANDOM.nextFloat() * 0.4F));
    }

    public static boolean any() {
        return !STARS.isEmpty();
    }

    /** Called from the sky renderer with additive blending already set up. */
    public static void render(Matrix4f pose, float partialTick, float alpha) {
        if (STARS.isEmpty() || alpha <= 0.01F) {
            return;
        }
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Star star : STARS) {
            float t = star.age + partialTick;
            float life = t / star.life;
            float fade = Mth.clamp(Math.min(life * 5.0F, (1.0F - life) * 3.0F), 0.0F, 1.0F) * star.brightness * alpha;
            int segments = 8;
            for (int i = 0; i < segments; i++) {
                float t0 = t - i * 0.9F, t1 = t - (i + 1) * 0.9F;
                if (t1 < 0) break;
                Vector3f p0 = star.at(t0).mul(DOME), p1 = star.at(t1).mul(DOME);
                float w0 = 0.45F * (1.0F - i / (float) segments), w1 = 0.45F * (1.0F - (i + 1) / (float) segments);
                float a0 = fade * (1.0F - i / (float) segments), a1 = fade * (1.0F - (i + 1) / (float) segments);
                Vector3f side0 = new Vector3f(star.axis).mul(w0), side1 = new Vector3f(star.axis).mul(w1);
                buffer.addVertex(pose, p0.x + side0.x, p0.y + side0.y, p0.z + side0.z).setColor(1.0F, 0.97F, 0.9F, a0);
                buffer.addVertex(pose, p0.x - side0.x, p0.y - side0.y, p0.z - side0.z).setColor(1.0F, 0.97F, 0.9F, a0);
                buffer.addVertex(pose, p1.x - side1.x, p1.y - side1.y, p1.z - side1.z).setColor(0.8F, 0.85F, 1.0F, a1);
                buffer.addVertex(pose, p1.x + side1.x, p1.y + side1.y, p1.z + side1.z).setColor(0.8F, 0.85F, 1.0F, a1);
            }
        }
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    public static void reset() {
        STARS.clear();
    }

    private ShootingStars() {}
}
