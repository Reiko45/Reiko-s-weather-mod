package com.reiko.weather.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.reiko.weather.ReikosWeather;
import com.reiko.weather.compat.Shaders;
import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.weather.Wind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.io.IOException;

/**
 * Volumetric clouds: instead of flat sprites, every pixel of sky marches through a layer of cloud built from 3D
 * noise, gathering sunlight (shaded by the cloud between it and the sun) and skylight on the way. Flat-bottomed
 * heaps with bulging tops, thin fair-weather patches and thick, ragged storm decks all come out of the same layer,
 * set by the weather: the fair-weather cover through the day, the cumulus over warm ground, and the storm cells
 * around (passed in as a small map of cloud cover). It's drawn at half resolution or full, and stands in for the
 * flat storm deck and the sprite clouds; with a shader pack it steps aside and those come back.
 */
public final class VolumetricClouds {
    public enum Quality {
        OFF(0.0F, 0), LOW(0.5F, 24), MEDIUM(0.5F, 44), HIGH(1.0F, 64);

        final float scale;
        final int steps;

        Quality(float scale, int steps) {
            this.scale = scale;
            this.steps = steps;
        }
    }

    /** Every noise scale in the shader divides this, so the noise position can wrap around it without a seam. */
    private static final double PERIOD = 196608.0;
    /** The cover map's size in texels, and how far out the clouds are drawn. */
    private static final int COVER_N = 96;
    private static final float REACH = 2400.0F;
    /** Where the cloud layer starts, above sea level (the same as the old fair-weather clouds, give or take). */
    private static final float BASE = 96.0F;
    /** The lowest any cloud reaches below that base (a storm's ragged underside), less a margin. */
    private static final float LOWEST = 40.0F;
    /** The time the clouds slowly change shape by, which wraps (with the noise) every this many ticks. */
    private static final long EVOLVE = 262144L;

    @Nullable private static ShaderInstance march, composite;
    @Nullable private static TextureTarget target;
    @Nullable private static DynamicTexture noise, cover;
    private static boolean failed;
    private static double driftX, driftZ;
    private static float stepX, stepZ;
    private static double coverX, coverZ;
    private static float coverCell = REACH * 2.0F / COVER_N;
    private static float shade;
    /** The tallest storm tower around just now (0 to 1), so the march reaches up far enough for it. */
    private static float highest;
    /** How far a full-height storm tower rises above the storm deck's 150 blocks. */
    private static final float TOWER = 170.0F;
    /** The noise's red channel, for working out on the CPU how much cloud covers the sun. */
    private static final float[] NOISE = new float[256 * 256];
    /** The size of the Worley noise's cube; and its texture (made the first time it's needed), and the shapes' channel. */
    private static final int CELLS = 64;
    private static int worleyId = -1;
    @Nullable private static float[] shapes;
    /** The Worley noise's two channels, worked out off the render thread (it takes a moment). */
    private static final java.util.concurrent.CompletableFuture<float[][]> WORLEY = java.util.concurrent.CompletableFuture.supplyAsync(
            () -> new float[][]{worleyLayers(new int[]{4, 8, 16}, 11L), worleyLayers(new int[]{8, 16, 32}, 23L)});

    static {
        java.util.Random random = new java.util.Random(0x5EED_C10D);
        for (int i = 0; i < NOISE.length; i++) NOISE[i] = random.nextInt(256) / 255.0F;
    }

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), ReikosWeather.id("volumetric_clouds"), DefaultVertexFormat.POSITION),
                    s -> march = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), ReikosWeather.id("cloud_composite"), DefaultVertexFormat.POSITION),
                    s -> composite = s);
            failed = false;
        } catch (IOException | RuntimeException e) {
            failed = true;
            ReikosWeather.LOGGER.warn("Couldn't load the volumetric cloud shaders; using the flat clouds instead", e);
        }
    }

    public static Quality quality() {
        return ClientConfig.SPEC.isLoaded() ? ClientConfig.VOLUMETRIC_CLOUDS.get() : Quality.OFF;
    }

    /** Whether the volumetric clouds are drawn here (and so the flat deck and the sprite clouds aren't). */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return !failed && march != null && composite != null && quality() != Quality.OFF && DriftingClouds.replacing()
                && DriftingClouds.cloudsWanted(mc) && !Shaders.packInUse();
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || !active()) {
            shade = 0.0F;
            return;
        }
        // the clouds drift on the wind aloft
        Vec3 wind = Wind.direction(SyncedWeather.windAngle());
        float speed = 0.08F + 0.3F * ClientWeather.wind();
        stepX = (float) wind.x * speed;
        stepZ = (float) wind.z * speed;
        driftX = (driftX + stepX) % PERIOD;
        driftZ = (driftZ + stepZ) % PERIOD;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        updateCover(cam);
        // how much a cloud is shading the sun just now
        float covered = 0.0F;
        if (ClientConfig.CLOUD_SHADE.get()) {
            Vector3f sun = SunPath.sun(level, 1.0F);
            if (sun.y > 0.05F) covered = sunCover(level, cam, sun);
        }
        shade = Mth.lerp(0.08F, shade, covered);
    }

    /**
     * How tall a thunderstorm's tower stands over a spot, 0 to 1 (1: towering up {@link #TOWER} blocks over the storm
     * deck): rising over the heart of each storm, the tallest over supercells.
     */
    private static float tower(double x, double z) {
        float most = 0.0F;
        for (com.reiko.weather.weather.StormCell cell : SyncedWeather.cells()) {
            float height = switch (cell.type) {
                case SUPERCELL -> 1.0F;
                case THUNDERSTORM, HAIL, PYROCUMULUS -> 0.8F;
                case DERECHO, HURRICANE, THUNDERSNOW, GRAUPEL, POLAR_LOW -> 0.55F;
                default -> 0.0F;
            };
            if (height <= 0.0F) continue;
            float core = Math.min(cell.radius * 0.45F, 150.0F);
            double d = Math.sqrt((cell.x - x) * (cell.x - x) + (cell.z - z) * (cell.z - z));
            if (d >= core) continue;
            float k = 1.0F - (float) d / core;
            most = Math.max(most, height * cell.strength() * k * k * (3.0F - 2.0F * k));
        }
        return most;
    }

    /** How much the passing cloud shades the sun, 0 to 1. */
    public static float shade() {
        return shade;
    }

    /** The storm-cloud cover around the camera, worked out again each tick as the storm cells move. */
    private static void updateCover(Vec3 cam) {
        if (cover == null) return;
        NativeImage image = cover.getPixels();
        if (image == null) return;
        coverX = Math.floor(cam.x / coverCell) * coverCell - COVER_N / 2.0 * coverCell;
        coverZ = Math.floor(cam.z / coverCell) * coverCell - COVER_N / 2.0 * coverCell;
        boolean storms = ClientConfig.STORM_CLOUDS.get();
        highest = 0.0F;
        for (int i = 0; i < COVER_N; i++) {
            for (int j = 0; j < COVER_N; j++) {
                double x = coverX + (i + 0.5) * coverCell, z = coverZ + (j + 0.5) * coverCell;
                float c = storms ? ClientWeather.cloudsAt(x, z) : 0.0F;
                // (and the big, slow blotches the fair-weather clouds come and go in, drifting with them)
                float b = blotch(wrap(x - driftX), wrap(z - driftZ));
                // (and how high a thunderstorm's tower rises here)
                float t = storms && ClientConfig.TOWERING_CLOUDS.get() ? tower(x, z) : 0.0F;
                highest = Math.max(highest, t);
                int red = Mth.clamp(Math.round(c * 255.0F), 0, 255), green = Mth.clamp(Math.round(b * 255.0F), 0, 255);
                int blue = Mth.clamp(Math.round(t * 255.0F), 0, 255);
                image.setPixelRGBA(i, j, 0xFF000000 | blue << 16 | green << 8 | red);
            }
        }
        cover.bind();
        image.upload(0, 0, 0, 0, 0, COVER_N, COVER_N, true, true, false, false);
    }

    /** Makes the textures the first time they're needed; false while the Worley noise is still being worked out. */
    private static boolean ensureTextures() {
        if (noise == null) {
            NativeImage image = new NativeImage(256, 256, false);
            for (int z = 0; z < 256; z++) {
                for (int x = 0; x < 256; x++) {
                    int r = Math.round(NOISE[z * 256 + x] * 255.0F);
                    // green holds red shifted by (37, 17): one fetch then gives two neighbouring slices of 3D noise
                    int g = Math.round(NOISE[Math.floorMod(z - 17, 256) * 256 + Math.floorMod(x - 37, 256)] * 255.0F);
                    image.setPixelRGBA(x, z, 0xFF000000 | g << 8 | r);
                }
            }
            noise = new DynamicTexture(image);
            noise.bind();
            image.upload(0, 0, 0, 0, 0, 256, 256, true, false, false, false);
        }
        if (cover == null) {
            cover = new DynamicTexture(new NativeImage(COVER_N, COVER_N, true));
        }
        if (worleyId == -1) {
            if (!WORLEY.isDone()) return false;
            float[][] layers = WORLEY.join();
            float[] big = layers[0], fine = layers[1];
            java.nio.ByteBuffer pixels = org.lwjgl.system.MemoryUtil.memAlloc(CELLS * CELLS * CELLS * 4);
            for (int i = 0; i < big.length; i++) {
                pixels.put((byte) Math.round(big[i] * 255.0F)).put((byte) Math.round(fine[i] * 255.0F)).put((byte) 0).put((byte) 255);
            }
            pixels.flip();
            worleyId = com.mojang.blaze3d.platform.TextureUtil.generateTextureId();
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, worleyId);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL11.GL_REPEAT);
            GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
            GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL11.GL_RGBA8, CELLS, CELLS, CELLS, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
            org.lwjgl.system.MemoryUtil.memFree(pixels);
            shapes = big;
        }
        return true;
    }

    /** Tileable 3D Worley noise, 1 at each cell's centre and falling away from it: octaves of {@code cells} per side. */
    private static float[] worleyLayers(int[] cells, long seed) {
        float[] out = new float[CELLS * CELLS * CELLS];
        float[] weights = {0.625F, 0.25F, 0.125F};
        for (int o = 0; o < cells.length; o++) {
            int n = cells[o];
            java.util.Random random = new java.util.Random(seed + o);
            float[] points = new float[n * n * n * 3];
            for (int i = 0; i < points.length; i++) points[i] = random.nextFloat();
            float scale = n / (float) CELLS;
            for (int z = 0; z < CELLS; z++) {
                for (int y = 0; y < CELLS; y++) {
                    for (int x = 0; x < CELLS; x++) {
                        float px = x * scale, py = y * scale, pz = z * scale;
                        int cx = (int) px, cy = (int) py, cz = (int) pz;
                        float best = 9.0F;
                        for (int dz = -1; dz <= 1; dz++) {
                            for (int dy = -1; dy <= 1; dy++) {
                                for (int dx = -1; dx <= 1; dx++) {
                                    int ix = cx + dx, iy = cy + dy, iz = cz + dz;
                                    int k = ((Math.floorMod(iz, n) * n + Math.floorMod(iy, n)) * n + Math.floorMod(ix, n)) * 3;
                                    float ex = ix + points[k] - px, ey = iy + points[k + 1] - py, ez = iz + points[k + 2] - pz;
                                    best = Math.min(best, ex * ex + ey * ey + ez * ez);
                                }
                            }
                        }
                        out[(z * CELLS + y) * CELLS + x] += weights[o] * Math.max(0.0F, 1.0F - Mth.sqrt(best));
                    }
                }
            }
        }
        return out;
    }

    public static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !active()) return;
        float visible = 1.0F - Math.min(1.0F, ClientWeather.fogDensity() * 1.3F);
        if (visible <= 0.01F) return;
        Quality quality = quality();
        if (!ensureTextures()) return;
        int width = mc.getWindow().getWidth(), height = mc.getWindow().getHeight();
        int w = Math.max(1, Math.round(width * quality.scale)), h = Math.max(1, Math.round(height * quality.scale));
        int restoreFbo = GlStateManager.getBoundFramebuffer();
        int vx = GlStateManager.Viewport.x(), vy = GlStateManager.Viewport.y(), vw = GlStateManager.Viewport.width(), vh = GlStateManager.Viewport.height();
        if (target == null) {
            target = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_LINEAR);
        } else if (target.width != w || target.height != h) {
            target.resize(w, h, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_LINEAR);
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f viewProj = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        Matrix4f inverse = new Matrix4f(viewProj).invert();
        float base = level.getSeaLevel() + BASE;
        int depth = mc.getMainRenderTarget().getDepthTextureId();

        // 1: march the clouds into our own (smaller) target
        ShaderInstance shader = march;
        target.bindWrite(true);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, depth);
        RenderSystem.setShaderTexture(1, noise.getId());
        RenderSystem.setShaderTexture(2, cover.getId());
        double dx = driftX + stepX * partialTick, dz = driftZ + stepZ * partialTick;
        shader.safeGetUniform("InvViewProj").set(inverse);
        shader.safeGetUniform("CameraPos").set((float) wrap(cam.x - dx), (float) cam.y, (float) wrap(cam.z - dz));
        shader.safeGetUniform("CoverOrigin").set((float) (coverX - cam.x), (float) (coverZ - cam.z));
        shader.safeGetUniform("CoverSize").set(COVER_N * coverCell);
        shader.safeGetUniform("Cover").set(DriftingClouds.cover(), ClientConfig.CLOUD_STREETS.get() ? Cumulus.strength() : 0.0F,
                ClientWeather.overcast(), (float) (level.getGameTime() % EVOLVE) + partialTick);
        shader.safeGetUniform("Layer").set(base, visible, REACH, (float) quality.steps);
        lighting(shader, level, cam, partialTick);
        RenderSystem.setShaderTexture(3, 0);
        RenderSystem.activeTexture(GL13.GL_TEXTURE3);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, worleyId);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        fullscreen();
        RenderSystem.activeTexture(GL13.GL_TEXTURE3);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);

        // 2: lay them over the scene, behind anything nearer than the cloud layer
        ShaderInstance blend = composite;
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, restoreFbo);
        RenderSystem.viewport(vx, vy, vw, vh);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.setShader(() -> blend);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        blend.safeGetUniform("InvViewProj").set(inverse);
        blend.safeGetUniform("ViewProj").set(viewProj);
        blend.safeGetUniform("Below").set((float) (base - LOWEST - cam.y));
        fullscreen();

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    private static double wrap(double v) {
        double w = v % PERIOD;
        return w < 0.0 ? w + PERIOD : w;
    }

    /** The light on the clouds: sun or moon, the sky above and the ground below, and the haze they fade into. */
    private static void lighting(ShaderInstance shader, ClientLevel level, Vec3 cam, float partialTick) {
        Vector3f sun = SunPath.sun(level, partialTick);
        float height = sun.y;
        float overcast = ClientWeather.overcast();
        float rain = level.getRainLevel(partialTick);
        float thunder = level.getThunderLevel(partialTick);
        // by day the sun lights them, warm and low near sunrise and sunset; by night the moon, dimly
        // (the clouds stand high enough to catch the sun a little after it has set for anyone on the ground)
        float day = Mth.clamp((height + 0.1F) / 0.16F, 0.0F, 1.0F);
        float warm = (float) Math.exp(-Math.max(height, 0.0F) * 7.0F);
        float sunPower = 1.55F * day * (1.0F - 0.55F * overcast) * (1.0F - 0.35F * rain) * (1.0F - 0.9F * ClientWeather.eclipseLoss());
        float moonPower = 0.16F * (1.0F - day) * level.getMoonBrightness() * (1.0F - 0.6F * overcast);
        Vector3f light = height > -0.06F ? sun : new Vector3f(sun).negate();
        if (light.y < 0.02F) light = new Vector3f(light.x, 0.02F, light.z).normalize();
        float r = sunPower + moonPower * 0.75F, g = sunPower * (0.96F - 0.42F * warm) + moonPower * 0.85F, b = sunPower * (0.9F - 0.72F * warm) + moonPower;
        shader.safeGetUniform("LightDir").set(light.x, light.y, light.z);
        shader.safeGetUniform("LightColor").set(r, g, b);
        Vec3 sky = level.getSkyColor(cam, partialTick);
        float lit = level.getSkyDarken(partialTick);
        float grey = 0.3F * lit * (1.0F - 0.5F * thunder);
        float sr = (float) sky.x * 0.6F + grey, sg = (float) sky.y * 0.6F + grey, sb = (float) sky.z * 0.6F + grey;
        shader.safeGetUniform("SkyColor").set(sr, sg, sb);
        shader.safeGetUniform("GroundColor").set(sr * 0.45F, sg * 0.45F, sb * 0.42F);
        float[] fog = RenderSystem.getShaderFogColor();
        shader.safeGetUniform("HazeColor").set(fog[0], fog[1], fog[2]);
        float flash = level.getSkyFlashTime() > 0 ? 0.45F : 0.0F;
        shader.safeGetUniform("Extra").set(rain, flash, 1.0F, 150.0F + TOWER * highest);
    }

    private static void fullscreen() {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buffer.addVertex(0.0F, 0.0F, 0.0F);
        buffer.addVertex(1.0F, 0.0F, 0.0F);
        buffer.addVertex(1.0F, 1.0F, 0.0F);
        buffer.addVertex(0.0F, 1.0F, 0.0F);
        MeshData mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    // ------------------------------------------------------------------ the same clouds, on the CPU

    /** The shader's value noise: one channel of the texture, bilinear in x and z, with neighbouring slices in y. */
    private static float noise(double x, double y, double z) {
        int px = Mth.floor(x), py = Mth.floor(y), pz = Mth.floor(z);
        double fx = smooth(x - px), fy = smooth(y - py), fz = smooth(z - pz);
        int ux = px + 37 * py, uz = pz + 17 * py;
        // green (this slice) is red shifted by (37, 17); red is the next slice up
        float lower = bilinear(ux - 37, uz - 17, fx, fz), upper = bilinear(ux, uz, fx, fz);
        return (float) (lower + (upper - lower) * fy);
    }

    private static float noise2(double x, double z) {
        int px = Mth.floor(x), pz = Mth.floor(z);
        return bilinear(px, pz, smooth(x - px), smooth(z - pz));
    }

    /** The big, slow blotches the fair-weather clouds come and go in (at a spot in the drifting noise's frame). */
    private static float blotch(double nx, double nz) {
        return noise2(nx / 768.0, nz / 768.0) * 0.6F + noise2(nx / 384.0 + 7.0, nz / 384.0 + 7.0) * 0.4F;
    }

    private static double smooth(double f) {
        return f * f * (3.0 - 2.0 * f);
    }

    private static float bilinear(int x, int z, double fx, double fz) {
        float a = texel(x, z), b = texel(x + 1, z), c = texel(x, z + 1), d = texel(x + 1, z + 1);
        return (float) Mth.lerp(fz, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d));
    }

    private static float texel(int x, int z) {
        return NOISE[(z & 255) * 256 + (x & 255)];
    }

    /** The cloud's density at a world spot (the shader's, without the fine detail): 0 clear to 1 thick. */
    private static float density(ClientLevel level, double wx, double wy, double wz, double nx, double nz, float time) {
        float storm = ClientConfig.STORM_CLOUDS.get() ? ClientWeather.cloudsAt(wx, wz) : 0.0F;
        float fair = DriftingClouds.cover(), cumulus = ClientConfig.CLOUD_STREETS.get() ? Cumulus.strength() : 0.0F;
        float fairCover = fair < 0.02F ? 0.0F : Mth.clamp(fair * 1.1F + (blotch(nx, nz) - 0.5F) * 0.8F, 0.0F, 1.0F);
        float heaps = noise2(nx / 96.0 + 3.0, nz / 96.0 + 3.0) * 0.7F + noise2(nx / 48.0 + 5.0, nz / 48.0 + 5.0) * 0.3F;
        float cuCover = cumulus * Mth.clamp((heaps - 0.42F) * 3.5F, 0.0F, 1.0F);
        float tower = ClientConfig.STORM_CLOUDS.get() && ClientConfig.TOWERING_CLOUDS.get() ? tower(wx, wz) : 0.0F;
        float heaped = Math.max(cuCover > fairCover ? 1.0F - storm : 0.0F, tower);
        float coverage = Math.max(Math.max(fairCover, cuCover), storm);
        float depth = Mth.lerp(storm, Mth.lerp(heaped, 36.0F, 30.0F + 70.0F * cuCover), 150.0F + TOWER * tower);
        float base = level.getSeaLevel() + BASE - storm * (10.0F + 14.0F * noise2(nx / 96.0 + 11.0, nz / 96.0 + 11.0));
        float h = (float) ((wy - base) / depth);
        if (h <= 0.0F || h >= 1.0F || coverage < 0.01F) return 0.0F;
        float taper = smoothstep(0.0F, 0.05F, h) * Mth.lerp(tower, smoothstep(1.0F, Mth.lerp(heaped, 0.3F, 0.1F), h), 1.0F - smoothstep(0.6F, 1.0F, h));
        float c = coverage * taper;
        if (c < 0.01F) return 0.0F;
        double evolve = time / 1024.0;
        float cells = shapes == null ? 0.48F : worley(nx / 384.0, wy / 192.0 + evolve / 8.0, nz / 384.0);
        double qx = nx / 48.0, qy = wy / 30.0 + evolve, qz = nz / 48.0;
        float n = noise(qx, qy, qz) * 0.6F + noise(qx * 2.0 + 0.31, qy * 2.0 + 0.17, qz * 2.0 + 0.83) * 0.4F;
        float d = (cells * 0.6F + n * 0.4F - 0.8F + 0.62F * c) * 3.0F;
        return d <= 0.0F ? 0.0F : Math.min(d * Mth.lerp(storm, 2.6F, 1.8F), 1.0F);
    }

    /** The shapes' Worley noise at texture coordinates (wrapping), trilinear, as the GPU samples it. */
    private static float worley(double u, double v, double w) {
        double x = u * CELLS - 0.5, y = v * CELLS - 0.5, z = w * CELLS - 0.5;
        int x0 = Mth.floor(x), y0 = Mth.floor(y), z0 = Mth.floor(z);
        double fx = x - x0, fy = y - y0, fz = z - z0;
        double sum = 0.0;
        for (int k = 0; k < 8; k++) {
            int dx = k & 1, dy = k >> 1 & 1, dz = k >> 2 & 1;
            double weight = (dx == 1 ? fx : 1.0 - fx) * (dy == 1 ? fy : 1.0 - fy) * (dz == 1 ? fz : 1.0 - fz);
            sum += weight * shapes[((Math.floorMod(z0 + dz, CELLS) * CELLS) + Math.floorMod(y0 + dy, CELLS)) * CELLS + Math.floorMod(x0 + dx, CELLS)];
        }
        return (float) sum;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** How much of the sun's light the clouds between here and the sun hold back, 0 to 1. */
    private static float sunCover(ClientLevel level, Vec3 cam, Vector3f sun) {
        float base = level.getSeaLevel() + BASE;
        double t0 = Math.max(0.0, (base - LOWEST - cam.y) / sun.y), t1 = Math.max(0.0, (base + 160.0 + TOWER * highest - cam.y) / sun.y);
        if (t1 <= t0) return 0.0F;
        float time = (float) (level.getGameTime() % EVOLVE);
        double optical = 0.0;
        int steps = 16;
        double dt = (t1 - t0) / steps;
        for (int i = 0; i < steps; i++) {
            double t = t0 + (i + 0.5) * dt;
            double x = cam.x + sun.x * t, y = cam.y + sun.y * t, z = cam.z + sun.z * t;
            optical += density(level, x, y, z, wrap(x - driftX), wrap(z - driftZ), time) * dt;
        }
        return (float) (1.0 - Math.exp(-optical * 0.09));
    }

    public static void reset() {
        shade = 0.0F;
    }

    private VolumetricClouds() {}
}
