package com.reiko.weather.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.reiko.weather.config.ClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Optics close to the ground, drawn into the world rather than on the sky: a glory and your own shadow (a
 * Brocken spectre) cast onto fog below you when the sun is at your back, and light pillars standing over
 * lanterns and fires on frigid, clear nights when the air is full of ice crystals.
 */
public final class GroundOptics {
    private static final List<BlockPos> LIGHTS = new ArrayList<>();
    private static long lastScan;

    public static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD || !com.reiko.weather.network.SyncedWeather.managed()) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        if (ClientConfig.GLORIES.get()) glory(level, camera, partialTick);
        if (ClientConfig.LIGHT_PILLARS.get()) pillars(level, camera);
        if (ClientConfig.DEWBOWS.get()) dew(level, camera, partialTick);
    }

    // ------------------------------------------------------------------ dew optics

    /**
     * On a dewy morning with the sun low behind you, the drops on the grass do two things. They gather sunlight
     * and send it straight back, so a bright halo rings the shadow of your head (the heiligenschein). And, like
     * raindrops, they bend it into a bow: a dewbow, a pale ring lying on the grass in front of you, curving away
     * into the distance.
     */
    private static void dew(ClientLevel level, Camera camera, float partialTick) {
        long time = Math.floorMod(level.getDayTime(), 24000L);
        if (time < 100L || time > 2600L) return;
        float theta = level.getTimeOfDay(partialTick) * Mth.TWO_PI;
        Vector3f sun = new Vector3f(-Mth.sin(theta), Mth.cos(theta), 0.0F);
        if (sun.y < 0.03F || ClientWeather.rain() > 0.05F || ClientWeather.overcast() > 0.4F || ClientWeather.wind() > 0.3F
                || ClientWeather.airTemperature() < 1.0F || ClientWeather.airTemperature() > 22.0F) return;
        // the dew burns off as the sun climbs
        float wet = time < 1500L ? 1.0F : 1.0F - (time - 1500L) / 1100.0F;
        Vec3 cam = camera.getPosition();
        int under = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, Mth.floor(cam.x), Mth.floor(cam.z));
        float eye = (float) (cam.y - under);
        if (eye < 0.8F || eye > 6.0F || !dewy(level, Mth.floor(cam.x), Mth.floor(cam.z))) return;
        Vector3f anti = new Vector3f(sun).negate().normalize();
        Matrix4f pose = new Matrix4f();
        begin();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        // the heiligenschein, round the shadow of your head on the grass
        float t = eye / -anti.y;
        if (t < 40.0F) {
            int hx = Mth.floor(cam.x + anti.x * t), hz = Mth.floor(cam.z + anti.z * t);
            if (dewy(level, hx, hz)) {
                float gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, hx, hz) - (float) cam.y + 0.06F;
                Vector3f c = new Vector3f(anti.x * t, gy, anti.z * t);
                Vector3f u = new Vector3f(1.0F, 0.0F, 0.0F), v = new Vector3f(0.0F, 0.0F, 1.0F);
                float[] white = {1.0F, 0.98F, 0.9F};
                float radius = 0.6F + t * 0.08F;
                for (int sgm = 0; sgm < 24; sgm++) {
                    float p0 = sgm / 24.0F * Mth.TWO_PI, p1 = (sgm + 1) / 24.0F * Mth.TWO_PI;
                    vertex(buffer, pose, c, white, 0.32F * wet);
                    vertex(buffer, pose, c, white, 0.32F * wet);
                    vertex(buffer, pose, ring(c, u, v, p1, radius), white, 0.0F);
                    vertex(buffer, pose, ring(c, u, v, p0, radius), white, 0.0F);
                }
                any = true;
            }
        }
        // the dewbow: the 42-degree circle round the point opposite the sun, where it meets the ground
        Vector3f ref = Math.abs(anti.y) < 0.95F ? new Vector3f(0.0F, 1.0F, 0.0F) : new Vector3f(1.0F, 0.0F, 0.0F);
        Vector3f bu = new Vector3f(ref).sub(new Vector3f(anti).mul(anti.dot(ref))).normalize();
        Vector3f bv = new Vector3f(anti).cross(bu).normalize();
        float[][] bands = {{0.75F, 0.8F, 1.0F}, {0.95F, 1.0F, 0.95F}, {1.0F, 0.88F, 0.8F}};
        int segments = 120;
        for (int sgm = 0; sgm < segments; sgm++) {
            float p0 = sgm / (float) segments * Mth.TWO_PI, p1 = (sgm + 1) / (float) segments * Mth.TWO_PI;
            for (int b = 0; b < bands.length; b++) {
                float r0 = (40.5F + b * 0.7F) * Mth.DEG_TO_RAD, r1 = r0 + 0.7F * Mth.DEG_TO_RAD;
                Vector3f a0 = onGround(level, cam, eye, bowRay(anti, bu, bv, p0, r0)), a1 = onGround(level, cam, eye, bowRay(anti, bu, bv, p1, r0));
                Vector3f b1 = onGround(level, cam, eye, bowRay(anti, bu, bv, p1, r1)), b0 = onGround(level, cam, eye, bowRay(anti, bu, bv, p0, r1));
                if (a0 == null || a1 == null || b0 == null || b1 == null) continue;
                // fainter far off, where the drops are seen edge-on through the grass
                float alpha = 0.2F * wet * Mth.clamp(1.0F - a0.length() / 36.0F, 0.0F, 1.0F);
                if (alpha < 0.01F) continue;
                vertex(buffer, pose, a0, bands[b], alpha);
                vertex(buffer, pose, a1, bands[b], alpha);
                vertex(buffer, pose, b1, bands[b], alpha);
                vertex(buffer, pose, b0, bands[b], alpha);
                any = true;
            }
        }
        if (any) draw(buffer);
        else buffer.build();
        end();
    }

    private static Vector3f bowRay(Vector3f anti, Vector3f u, Vector3f v, float phi, float rho) {
        return new Vector3f(anti).mul(Mth.cos(rho)).add(new Vector3f(u).mul(Mth.sin(rho) * Mth.cos(phi))).add(new Vector3f(v).mul(Mth.sin(rho) * Mth.sin(phi)));
    }

    /** Where a ray from the eye meets dewy grass (relative to the camera), or null if it doesn't, or it's too far. */
    @org.jetbrains.annotations.Nullable
    private static Vector3f onGround(ClientLevel level, Vec3 cam, float eye, Vector3f ray) {
        if (ray.y > -0.03F) return null;
        float t = eye / -ray.y;
        if (t > 36.0F) return null;
        int x = Mth.floor(cam.x + ray.x * t), z = Mth.floor(cam.z + ray.z * t);
        if (!dewy(level, x, z)) return null;
        float gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z) - (float) cam.y + 0.06F;
        return new Vector3f(ray.x * t, gy, ray.z * t);
    }

    /** Grass (or a lawn of short grass) open to the sky, where dew settles. */
    private static boolean dewy(ClientLevel level, int x, int z) {
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos ground = new BlockPos(x, y - 1, z);
        BlockState s = level.getBlockState(ground);
        return s.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) || s.is(net.minecraft.world.level.block.Blocks.MOSS_BLOCK);
    }

    // ------------------------------------------------------------------ glories

    /** How much fog or mist is lying below the camera to cast a shadow on. */
    private static float fogBelow(ClientLevel level, Vec3 cam) {
        // you have to be up above it: on a peak, or above the lid of a valley inversion
        var info = com.reiko.weather.weather.Microclimate.at(level, Mth.floor(cam.x), Mth.floor(cam.z), com.reiko.weather.network.SyncedWeather.windAngle());
        boolean above = info.peak() > 0.15F || SkySights.inversion() > 0.2F && cam.y > SkySights.inversionLid() + 2.0F;
        if (!above) return 0.0F;
        return Math.max(Math.max(ClientWeather.mist(), SkySights.inversion()), ClientWeather.fogDensity() > 0.5F ? 0.6F : 0.0F);
    }

    private static void glory(ClientLevel level, Camera camera, float partialTick) {
        float theta = level.getTimeOfDay(partialTick) * Mth.TWO_PI;
        Vector3f sun = new Vector3f(-Mth.sin(theta), Mth.cos(theta), 0.0F);
        if (sun.y < 0.02F || sun.y > 0.45F) return;
        Vec3 cam = camera.getPosition();
        float fog = fogBelow(level, cam);
        if (fog < 0.2F) return;
        // the shadow falls straight away from the sun: down and out onto the fog
        Vector3f anti = new Vector3f(sun).negate().normalize();
        float dist = 22.0F;
        for (float d = 2.0F; d <= dist; d += 2.0F) {
            BlockPos p = BlockPos.containing(cam.x + anti.x * d, cam.y + anti.y * d, cam.z + anti.z * d);
            if (!level.getBlockState(p).isAir() || !level.getFluidState(p).isEmpty()) return; // the ground's in the way
        }
        float alpha = Mth.clamp((fog - 0.2F) * 2.0F, 0.0F, 1.0F) * Mth.clamp((0.45F - sun.y) * 6.0F, 0.0F, 1.0F) * (1.0F - ClientWeather.rain());
        if (alpha < 0.02F) return;
        Vector3f center = new Vector3f(anti).mul(dist);
        Vector3f u = new Vector3f(anti).cross(new Vector3f(0.0F, 1.0F, 0.0F));
        if (u.lengthSquared() < 1.0E-5F) u.set(1.0F, 0.0F, 0.0F);
        u.normalize();
        Vector3f v = new Vector3f(u).cross(anti).normalize();
        Matrix4f pose = new Matrix4f();
        begin();
        // the spectre: a dark, foreshortened figure (your own shadow), with normal blending
        RenderSystem.defaultBlendFunc();
        BufferBuilder shade = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] dark = {0.32F, 0.34F, 0.4F};
        float a = 0.45F * alpha;
        quad(shade, pose, center, u, v, -0.35F, 0.0F, 0.35F, 1.3F, dark, a);          // body
        quad(shade, pose, center, u, v, -0.75F, 0.55F, 0.75F, 0.9F, dark, a * 0.7F);  // arms
        quad(shade, pose, center, u, v, -0.22F, 1.35F, 0.22F, 1.8F, dark, a);         // head
        quad(shade, pose, center, u, v, -0.3F, -1.6F, -0.05F, 0.0F, dark, a * 0.85F); // legs
        quad(shade, pose, center, u, v, 0.05F, -1.6F, 0.3F, 0.0F, dark, a * 0.85F);
        draw(shade);
        // the glory: rings of colour round the shadow's head
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferBuilder rings = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Vector3f head = new Vector3f(center).add(new Vector3f(v).mul(1.6F));
        float[][] colours = {{0.4F, 0.5F, 1.0F}, {0.4F, 1.0F, 0.6F}, {1.0F, 0.9F, 0.4F}, {1.0F, 0.4F, 0.35F}};
        for (int ring = 0; ring < 2; ring++) {
            for (int band = 0; band < colours.length; band++) {
                float r0 = 1.2F + ring * 1.4F + band * 0.3F, r1 = r0 + 0.3F;
                float ra = alpha * (ring == 0 ? 0.35F : 0.18F);
                for (int s = 0; s < 36; s++) {
                    float p0 = s / 36.0F * Mth.TWO_PI, p1 = (s + 1) / 36.0F * Mth.TWO_PI;
                    vertex(rings, pose, ring(head, u, v, p0, r0), colours[band], ra);
                    vertex(rings, pose, ring(head, u, v, p1, r0), colours[band], ra);
                    vertex(rings, pose, ring(head, u, v, p1, r1), colours[band], ra);
                    vertex(rings, pose, ring(head, u, v, p0, r1), colours[band], ra);
                }
            }
        }
        draw(rings);
        end();
    }

    private static Vector3f ring(Vector3f c, Vector3f u, Vector3f v, float phi, float r) {
        return new Vector3f(c).add(new Vector3f(u).mul(Mth.cos(phi) * r)).add(new Vector3f(v).mul(Mth.sin(phi) * r));
    }

    private static void quad(BufferBuilder b, Matrix4f pose, Vector3f c, Vector3f u, Vector3f v, float x0, float y0, float x1, float y1, float[] col, float a) {
        vertex(b, pose, new Vector3f(c).add(new Vector3f(u).mul(x0)).add(new Vector3f(v).mul(y0)), col, a);
        vertex(b, pose, new Vector3f(c).add(new Vector3f(u).mul(x1)).add(new Vector3f(v).mul(y0)), col, a);
        vertex(b, pose, new Vector3f(c).add(new Vector3f(u).mul(x1)).add(new Vector3f(v).mul(y1)), col, a);
        vertex(b, pose, new Vector3f(c).add(new Vector3f(u).mul(x0)).add(new Vector3f(v).mul(y1)), col, a);
    }

    // ------------------------------------------------------------------ light pillars

    private static boolean bright(BlockState state) {
        return state.getLightEmission() >= 13;
    }

    /** Looks for bright lights out under the open sky nearby (only in sections whose palette holds one). */
    private static void scan(ClientLevel level, Vec3 cam) {
        LIGHTS.clear();
        int cx = SectionPos.blockToSectionCoord(cam.x), cz = SectionPos.blockToSectionCoord(cam.z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -6; dx <= 6 && LIGHTS.size() < 48; dx++) {
            for (int dz = -6; dz <= 6 && LIGHTS.size() < 48; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx + dx, cz + dz, false);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length && LIGHTS.size() < 48; i++) {
                    LevelChunkSection section = sections[i];
                    if (section.hasOnlyAir() || !section.maybeHas(GroundOptics::bright)) continue;
                    int baseY = SectionPos.sectionToBlockCoord(chunk.getMinSection() + i);
                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                if (!bright(section.getBlockState(x, y, z))) continue;
                                pos.set(chunk.getPos().getBlockX(x), baseY + y, chunk.getPos().getBlockZ(z));
                                if (level.canSeeSky(pos.above())) LIGHTS.add(pos.immutable());
                            }
                        }
                    }
                }
            }
        }
    }

    private static void pillars(ClientLevel level, Camera camera) {
        float sun = com.reiko.weather.weather.SkyPhenomena.sunHeight(level, 1.0F);
        boolean frigid = sun < -0.1F && ClientWeather.airTemperature() < -10.0F && ClientWeather.overcast() < 0.35F
                && ClientWeather.wind() < 0.3F && ClientWeather.rain() < 0.05F;
        if (!frigid) {
            LIGHTS.clear();
            return;
        }
        Vec3 cam = camera.getPosition();
        long now = level.getGameTime();
        if (now - lastScan > 40L || now < lastScan) {
            lastScan = now;
            scan(level, cam);
        }
        if (LIGHTS.isEmpty()) return;
        float strength = Mth.clamp((-10.0F - ClientWeather.airTemperature()) / 8.0F, 0.0F, 1.0F);
        Matrix4f pose = new Matrix4f();
        begin();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] warm = {1.0F, 0.82F, 0.5F};
        for (BlockPos p : LIGHTS) {
            double dx = p.getX() + 0.5 - cam.x, dz = p.getZ() + 0.5 - cam.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            // the pillar is the light caught by the crystals between you and it: only from a way off
            float fade = Mth.clamp((float) (d - 10.0) / 20.0F, 0.0F, 1.0F);
            if (fade <= 0.0F) continue;
            float sx = (float) (-dz / d) * 0.6F, sz = (float) (dx / d) * 0.6F;
            float x = (float) dx, z = (float) dz, y0 = (float) (p.getY() + 1.0 - cam.y), height = 28.0F + (p.hashCode() & 15);
            float a = 0.35F * strength * fade;
            buffer.addVertex(pose, x - sx, y0, z - sz).setColor(warm[0], warm[1], warm[2], a);
            buffer.addVertex(pose, x + sx, y0, z + sz).setColor(warm[0], warm[1], warm[2], a);
            buffer.addVertex(pose, x + sx, y0 + height, z + sz).setColor(warm[0], warm[1], warm[2], 0.0F);
            buffer.addVertex(pose, x - sx, y0 + height, z - sz).setColor(warm[0], warm[1], warm[2], 0.0F);
        }
        draw(buffer);
        end();
    }

    // ------------------------------------------------------------------ drawing

    private static void begin() {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
    }

    private static void end() {
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void vertex(BufferBuilder buffer, Matrix4f pose, Vector3f p, float[] c, float a) {
        buffer.addVertex(pose, p.x, p.y, p.z).setColor(c[0], c[1], c[2], a);
    }

    private static void draw(BufferBuilder buffer) {
        MeshData mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    private GroundOptics() {}
}
