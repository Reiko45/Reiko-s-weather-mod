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
import com.reiko.weather.registry.ModParticles;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Iterator;

/**
 * Water and cold air: steam fog rising off unfrozen lakes on bitterly cold mornings, and spray billowing up
 * at the foot of waterfalls, with a little rainbow in it when the sun is behind you.
 */
public final class WaterMist {
    private static final RandomSource RANDOM = RandomSource.create();
    /** The feet of waterfalls seen lately: block position to the game time last seen. */
    private static final Long2LongOpenHashMap FALLS = new Long2LongOpenHashMap();
    /** And how far the water drops there. */
    private static final Long2IntOpenHashMap HEIGHTS = new Long2IntOpenHashMap();
    private static final float[][] BANDS = {
            {0.55F, 0.20F, 0.85F}, {0.25F, 0.30F, 1.00F}, {0.15F, 0.75F, 1.00F}, {0.20F, 0.95F, 0.30F},
            {1.00F, 0.95F, 0.20F}, {1.00F, 0.55F, 0.10F}, {1.00F, 0.15F, 0.15F},
    };

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            FALLS.clear();
            HEIGHTS.clear();
            return;
        }
        ParticleStatus status = mc.options.particles().get();
        float density = ClientConfig.PARTICLE_DENSITY.get().floatValue() * (status == ParticleStatus.DECREASED ? 0.5F : 1.0F);
        if (status == ParticleStatus.MINIMAL) density = 0.0F;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        if (ClientConfig.STEAM_FOG.get() && density > 0.0F && level.dimensionType().hasSkyLight()) {
            steamFog(level, cam, density);
        }
        if (ClientConfig.WATERFALL_SPRAY.get()) {
            findFalls(level, cam);
            if (density > 0.0F) spray(level, cam, density);
        } else {
            FALLS.clear();
        }
    }

    /** Sea smoke: when the air is far colder than the water, the water steams. */
    private static void steamFog(ClientLevel level, Vec3 cam, float density) {
        float air = ClientWeather.airTemperature();
        if (air > 1.0F || ClientWeather.wind() > 0.45F || ClientWeather.rain() > 0.1F) {
            return;
        }
        float strength = Mth.clamp((1.0F - air) / 9.0F, 0.0F, 1.0F);
        int attempts = Math.round(14 * strength * density);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < attempts; i++) {
            int x = Mth.floor(cam.x) + RANDOM.nextInt(49) - 24, z = Mth.floor(cam.z) + RANDOM.nextInt(49) - 24;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (Math.abs(top - cam.y) > 24) continue;
            pos.set(x, top - 1, z);
            if (!level.getFluidState(pos).is(FluidTags.WATER)) continue;
            level.addParticle(ModParticles.STEAM_FOG.get(), x + RANDOM.nextDouble(), top + 0.05, z + RANDOM.nextDouble(), 0.0, 0.02, 0.0);
        }
    }

    /** Looks around for falling water and follows it down to where it lands. */
    private static void findFalls(ClientLevel level, Vec3 cam) {
        long now = level.getGameTime();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 60; i++) {
            int x = Mth.floor(cam.x) + RANDOM.nextInt(81) - 40, z = Mth.floor(cam.z) + RANDOM.nextInt(81) - 40;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            pos.set(x, top, z);
            if (!falling(level.getFluidState(pos))) continue;
            int y = top;
            while (y > level.getMinBuildHeight() && top - y < 64) {
                pos.set(x, y - 1, z);
                if (!falling(level.getFluidState(pos))) break;
                y--;
            }
            int drop = top - y;
            if (drop >= 5) {
                long key = BlockPos.asLong(x, y, z);
                FALLS.put(key, now);
                HEIGHTS.put(key, drop);
            }
        }
        for (Iterator<Long2LongMap.Entry> it = FALLS.long2LongEntrySet().iterator(); it.hasNext(); ) {
            Long2LongMap.Entry e = it.next();
            BlockPos p = BlockPos.of(e.getLongKey());
            if (now - e.getLongValue() > 1200 || p.distToCenterSqr(cam) > 96 * 96 || !falling(level.getFluidState(p))) {
                HEIGHTS.remove(e.getLongKey());
                it.remove();
            }
        }
    }

    private static boolean falling(FluidState fluid) {
        return fluid.is(FluidTags.WATER) && !fluid.isSource() && fluid.hasProperty(FlowingFluid.FALLING) && fluid.getValue(FlowingFluid.FALLING);
    }

    private static void spray(ClientLevel level, Vec3 cam, float density) {
        for (Long2LongMap.Entry e : FALLS.long2LongEntrySet()) {
            BlockPos foot = BlockPos.of(e.getLongKey());
            int drop = HEIGHTS.getOrDefault(e.getLongKey(), 5);
            float amount = Math.min(3.0F, drop / 6.0F) * density;
            int count = (int) amount + (RANDOM.nextFloat() < amount - (int) amount ? 1 : 0);
            for (int i = 0; i < count; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2.0, speed = 0.05 + RANDOM.nextDouble() * 0.08;
                level.addParticle(ModParticles.SPRAY.get(), foot.getX() + 0.5 + Math.cos(a) * 0.6, foot.getY() + 0.3 + RANDOM.nextDouble(),
                        foot.getZ() + 0.5 + Math.sin(a) * 0.6, Math.cos(a) * speed, 0.03, Math.sin(a) * speed);
            }
        }
    }

    /**
     * A rainbow in the spray: part of the same 42-degree circle round the point opposite the sun as a sky
     * rainbow, but only where it passes through the cloud of spray, so it hangs in the mist a few blocks away.
     */
    public static void renderRainbows(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || FALLS.isEmpty() || !ClientConfig.WATERFALL_SPRAY.get() || !level.dimensionType().hasSkyLight()) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float theta = level.getTimeOfDay(partialTick) * Mth.TWO_PI;
        Vector3f sun = new Vector3f(-Mth.sin(theta), Mth.cos(theta), 0.0F);
        float sunUp = Mth.clamp(sun.y * 5.0F, 0.0F, 1.0F) * (1.0F - level.getRainLevel(partialTick)) * (1.0F - ClientWeather.overcast());
        if (sunUp < 0.02F) {
            return;
        }
        Vector3f anti = new Vector3f(sun).negate().normalize();
        Vector3f ref = Math.abs(anti.y) < 0.95F ? new Vector3f(0.0F, 1.0F, 0.0F) : new Vector3f(1.0F, 0.0F, 0.0F);
        Vector3f u = new Vector3f(ref).sub(new Vector3f(anti).mul(anti.dot(ref))).normalize();
        Vector3f v = new Vector3f(anti).cross(u).normalize();
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f pose = new Matrix4f(); // the view rotation is already applied at the particle stage
        BufferBuilder buffer = null;
        for (Long2LongMap.Entry e : FALLS.long2LongEntrySet()) {
            BlockPos foot = BlockPos.of(e.getLongKey());
            int drop = HEIGHTS.getOrDefault(e.getLongKey(), 5);
            float radius = Math.min(6.0F, 2.0F + drop * 0.15F);
            Vector3f d = new Vector3f((float) (foot.getX() + 0.5 - cam.x), (float) (foot.getY() + radius * 0.6 - cam.y), (float) (foot.getZ() + 0.5 - cam.z));
            float dist = d.length();
            if (dist < radius + 1.0F || dist > 64.0F) continue;
            d.div(dist);
            float angle = (float) Math.acos(Mth.clamp(d.dot(anti), -1.0F, 1.0F)) * Mth.RAD_TO_DEG;
            float fit = Mth.clamp(1.0F - Math.abs(angle - 41.5F) / (radius / dist * Mth.RAD_TO_DEG + 2.0F), 0.0F, 1.0F);
            if (fit <= 0.0F) continue;
            if (buffer == null) {
                buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            }
            float phi0 = (float) Math.atan2(d.dot(v), d.dot(u));
            float span = radius / (dist * Mth.sin(42.0F * Mth.DEG_TO_RAD));
            float alpha = 0.32F * fit * sunUp * Math.min(1.0F, drop / 10.0F);
            int segments = 12;
            for (int s = 0; s < segments; s++) {
                float f0 = s / (float) segments, f1 = (s + 1) / (float) segments;
                float p0 = phi0 + (f0 - 0.5F) * 2.0F * span, p1 = phi0 + (f1 - 0.5F) * 2.0F * span;
                float e0 = Mth.sin(f0 * Mth.PI), e1 = Mth.sin(f1 * Mth.PI);
                for (int b = 0; b <= BANDS.length; b++) {
                    float r0 = Mth.lerp(b / (float) (BANDS.length + 1), 40.0F, 42.5F) * Mth.DEG_TO_RAD;
                    float r1 = Mth.lerp((b + 1) / (float) (BANDS.length + 1), 40.0F, 42.5F) * Mth.DEG_TO_RAD;
                    float[] c0 = BANDS[Mth.clamp(b - 1, 0, BANDS.length - 1)], c1 = BANDS[Mth.clamp(b, 0, BANDS.length - 1)];
                    float a0 = b == 0 ? 0.0F : alpha, a1 = b == BANDS.length ? 0.0F : alpha;
                    vertex(buffer, pose, ring(anti, u, v, p0, r0, dist), c0, a0 * e0);
                    vertex(buffer, pose, ring(anti, u, v, p1, r0, dist), c0, a0 * e1);
                    vertex(buffer, pose, ring(anti, u, v, p1, r1, dist), c1, a1 * e1);
                    vertex(buffer, pose, ring(anti, u, v, p0, r1, dist), c1, a1 * e0);
                }
            }
        }
        if (buffer == null) {
            return;
        }
        MeshData mesh = buffer.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static Vector3f ring(Vector3f center, Vector3f u, Vector3f v, float phi, float rho, float dist) {
        float s = Mth.sin(rho);
        return new Vector3f(center).mul(Mth.cos(rho)).add(new Vector3f(u).mul(Mth.cos(phi) * s)).add(new Vector3f(v).mul(Mth.sin(phi) * s)).mul(dist);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f pose, Vector3f p, float[] c, float a) {
        buffer.addVertex(pose, p.x, p.y, p.z).setColor(c[0], c[1], c[2], a);
    }

    private WaterMist() {}
}
