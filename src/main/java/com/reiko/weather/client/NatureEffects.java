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
import com.reiko.weather.registry.ModParticles;
import com.reiko.weather.weather.Climate;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.SkyPhenomena;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Seasonal life around the player: fireflies on summer evenings, blossom petals in spring, earthworms
 * on the wet grass after rain, and flocks of birds flying over in a V in spring and autumn.
 */
public final class NatureEffects {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final ResourceLocation BIRD = ReikosWeather.id("textures/environment/bird.png");
    private static final List<Flock> FLOCKS = new ArrayList<>();
    private static int nextFlock = 1200;
    private static Season lastSeason;

    private static final class Flock {
        final Vec3 start;
        final Vec3 heading;
        final int size;
        final float speed;
        int age;
        final int life;
        boolean honked;

        Flock(Vec3 start, Vec3 heading, int size, float speed, int life) {
            this.start = start;
            this.heading = heading;
            this.size = size;
            this.speed = speed;
            this.life = life;
        }

        Vec3 lead(float partialTick) {
            return start.add(heading.scale((age + partialTick) * speed));
        }
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        FLOCKS.removeIf(f -> ++f.age > f.life);
        if (level == null || mc.player == null || level.dimension() != Level.OVERWORLD || mc.isPaused() || !SyncedWeather.managed()) {
            return;
        }
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        for (Flock flock : FLOCKS) {
            Vec3 lead = flock.lead(0.0F);
            // honking as they pass overhead
            if (!flock.honked && lead.subtract(eye).horizontalDistance() < 80 && ClientConfig.WILDLIFE_SOUNDS.get() && ClientConfig.SOUNDS.get()) {
                flock.honked = true;
                level.playLocalSound(lead.x, lead.y, lead.z, com.reiko.weather.registry.ModSounds.GEESE.get(), net.minecraft.sounds.SoundSource.AMBIENT,
                        6.0F, 0.9F + RANDOM.nextFloat() * 0.2F, false);
            }
        }
        ParticleStatus status = mc.options.particles().get();
        float density = ClientConfig.PARTICLE_DENSITY.get().floatValue() * (status == ParticleStatus.MINIMAL ? 0.0F : status == ParticleStatus.DECREASED ? 0.5F : 1.0F);
        Season season = SyncedWeather.season();
        if (season != lastSeason) {
            lastSeason = season;
            // the first flock of the migration comes over soon after it starts
            nextFlock = 600 + RANDOM.nextInt(1200);
        }
        float sun = SkyPhenomena.sunHeight(level, 1.0F);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        boolean calm = ClientWeather.rain() < 0.05F && ClientWeather.wind() < 0.55F;
        Climate climate = ClientWeather.climate();
        if (density > 0) {
            if (ClientConfig.FIREFLIES.get() && calm && climate != Climate.COLD && (season == Season.SUMMER || season == null)
                    && sun < 0.12F && sun > -0.85F && ClientWeather.airTemperature() > 12.0F) {
                fireflies(level, cam, density);
            }
            if (ClientConfig.BLOSSOMS.get() && season == Season.SPRING && sun > 0.0F && climate == Climate.TEMPERATE && ClientWeather.rain() < 0.3F) {
                blossoms(level, cam, density * (1.0F + ClientWeather.wind()));
            }
            if (com.reiko.weather.config.WeatherConfig.SPEC.isLoaded() && com.reiko.weather.config.WeatherConfig.EARTHWORMS.get()
                    && ClientWeather.wetness() > 0.25F && ClientWeather.rain() < 0.15F && climate != Climate.ARID) {
                worms(level, cam, density * ClientWeather.wetness());
            }
        }
        if (ClientConfig.BIRD_FLOCKS.get() && (season == Season.SPRING || season == Season.AUTUMN) && sun > 0.1F
                && ClientWeather.rain() < 0.3F && --nextFlock <= 0) {
            nextFlock = 2400 + RANDOM.nextInt(4800);
            startFlock(cam, season == Season.SPRING);
        }
    }

    private static void fireflies(ClientLevel level, Vec3 cam, float density) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int attempts = (int) (6 * density) + (RANDOM.nextFloat() < 6 * density % 1 ? 1 : 0);
        for (int i = 0; i < attempts; i++) {
            pos.set(cam.x + RANDOM.nextInt(33) - 16, cam.y + RANDOM.nextInt(9) - 4, cam.z + RANDOM.nextInt(33) - 16);
            if (!level.getBlockState(pos).isAir() || RANDOM.nextInt(3) != 0) continue;
            BlockPos below = pos.below();
            var ground = level.getBlockState(below);
            boolean plants = ground.is(Blocks.GRASS_BLOCK) || ground.is(BlockTags.REPLACEABLE) || ground.is(BlockTags.FLOWERS)
                    || level.getFluidState(below).isSource();
            if (plants && level.canSeeSky(pos)) {
                level.addParticle(ModParticles.FIREFLY.get(), pos.getX() + RANDOM.nextDouble(), pos.getY() + 0.3 + RANDOM.nextDouble() * 1.8,
                        pos.getZ() + RANDOM.nextDouble(), 0.0, 0.0, 0.0);
            }
        }
    }

    private static void blossoms(ClientLevel level, Vec3 cam, float density) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int attempts = (int) (48 * density);
        for (int i = 0; i < attempts; i++) {
            pos.set(cam.x + RANDOM.nextInt(25) - 12, cam.y + RANDOM.nextInt(16) - 4, cam.z + RANDOM.nextInt(25) - 12);
            if (!level.getBlockState(pos).is(BlockTags.LEAVES) || RANDOM.nextInt(3) != 0) continue;
            pos.move(0, -1, 0);
            if (!level.getBlockState(pos).isAir()) continue;
            level.addParticle(ParticleTypes.CHERRY_LEAVES, pos.getX() + RANDOM.nextDouble(), pos.getY() + 0.9, pos.getZ() + RANDOM.nextDouble(), 0.0, 0.0, 0.0);
        }
    }

    private static void worms(ClientLevel level, Vec3 cam, float density) {
        if (RANDOM.nextFloat() > 0.15F * density) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 6; i++) {
            pos.set(cam.x + RANDOM.nextInt(17) - 8, cam.y + RANDOM.nextInt(5) - 3, cam.z + RANDOM.nextInt(17) - 8);
            if (level.getBlockState(pos).is(Blocks.GRASS_BLOCK) && level.getBlockState(pos.above()).isAir() && level.canSeeSky(pos.above())) {
                level.addParticle(ModParticles.WORM.get(), pos.getX() + 0.2 + RANDOM.nextDouble() * 0.6, pos.getY() + 1.01,
                        pos.getZ() + 0.2 + RANDOM.nextDouble() * 0.6, 0.0, 0.0, 0.0);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ bird flocks

    private static void startFlock(Vec3 cam, boolean north) {
        // north in spring, south in autumn, with a little sideways drift
        float angle = (north ? Mth.PI : 0.0F) + (RANDOM.nextFloat() - 0.5F) * 0.8F;
        Vec3 heading = new Vec3(-Mth.sin(angle), 0.0, Mth.cos(angle));
        Vec3 side = new Vec3(heading.z, 0.0, -heading.x);
        double offset = (RANDOM.nextDouble() - 0.5) * 120;
        Vec3 start = new Vec3(cam.x, Math.max(cam.y + 28 + RANDOM.nextInt(16), 100 + RANDOM.nextInt(25)), cam.z)
                .add(heading.scale(-220)).add(side.scale(offset));
        FLOCKS.add(new Flock(start, heading, 7 + RANDOM.nextInt(10), 0.55F + RANDOM.nextFloat() * 0.2F, 800));
    }

    public static void renderFlocks(Camera camera, float partialTick) {
        if (FLOCKS.isEmpty()) {
            return;
        }
        Vec3 cam = camera.getPosition();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, BIRD);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Quaternionf facing = camera.rotation();
        float daylight = Mth.clamp(0.35F + 0.65F * SkyPhenomena.sunHeight(Minecraft.getInstance().level, partialTick), 0.2F, 1.0F);
        for (Flock flock : FLOCKS) {
            Vec3 lead = flock.lead(partialTick);
            Vec3 side = new Vec3(flock.heading.z, 0.0, -flock.heading.x);
            float fade = Mth.clamp(Math.min(flock.age, flock.life - flock.age) / 60.0F, 0.0F, 1.0F);
            for (int i = 0; i < flock.size; i++) {
                // a V: alternate sides, each bird a little further back
                int rank = (i + 1) / 2;
                int sideSign = i == 0 ? 0 : (i % 2 == 0 ? 1 : -1);
                double bob = Mth.sin((flock.age + partialTick) * 0.08F + i) * 0.4;
                Vec3 p = lead.subtract(flock.heading.scale(rank * 2.6)).add(side.scale(sideSign * rank * 2.4)).add(0, bob, 0);
                boolean wingsUp = ((flock.age + i * 3) / 5) % 2 == 0;
                quad(buffer, facing, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z), 1.3F, wingsUp, 0.12F * daylight, fade);
            }
        }
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void quad(BufferBuilder buffer, Quaternionf facing, float x, float y, float z, float size, boolean wingsUp, float shade, float alpha) {
        float u0 = wingsUp ? 0.0F : 0.5F, u1 = u0 + 0.5F;
        Vector3f[] corners = {new Vector3f(-1, -0.5F, 0), new Vector3f(-1, 0.5F, 0), new Vector3f(1, 0.5F, 0), new Vector3f(1, -0.5F, 0)};
        float[][] uv = {{u1, 1}, {u1, 0}, {u0, 0}, {u0, 1}};
        Matrix4f none = new Matrix4f();
        for (int k = 0; k < 4; k++) {
            Vector3f c = facing.transform(corners[k].mul(size)).add(x, y, z);
            buffer.addVertex(none, c.x, c.y, c.z).setUv(uv[k][0], uv[k][1]).setColor(shade, shade, shade, alpha);
        }
    }

    public static void reset() {
        FLOCKS.clear();
        nextFlock = 1200;
        lastSeason = null;
    }

    private NatureEffects() {}
}
