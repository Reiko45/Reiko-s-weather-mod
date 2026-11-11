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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Footprints pressed into fresh snow (or spring mud) by players and animals, fading after a minute or so. */
public final class Footprints {
    private static final ResourceLocation TEXTURE = ReikosWeather.id("textures/environment/footprint.png");
    private static final int MAX_PRINTS = 600;
    private static final int LIFETIME = 1600;
    private static final double STRIDE = 0.6;

    private record Print(double x, double y, double z, float yaw, boolean left, float size, long born, boolean mud) {}

    private record Track(Vec3 last, boolean left) {}

    private static final Deque<Print> PRINTS = new ArrayDeque<>();
    private static final Map<Integer, Track> TRACKS = new HashMap<>();

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || !ClientConfig.FOOTPRINTS.get()) {
            PRINTS.clear();
            TRACKS.clear();
            return;
        }
        long now = level.getGameTime();
        while (!PRINTS.isEmpty() && (now - PRINTS.peekFirst().born() > LIFETIME || PRINTS.size() > MAX_PRINTS)) {
            PRINTS.removeFirst();
        }
        AABB area = mc.player.getBoundingBox().inflate(32);
        for (Entity entity : level.getEntities((Entity) null, area, e -> e instanceof LivingEntity)) {
            LivingEntity living = (LivingEntity) entity;
            if (!living.onGround() || living.isSpectator() || living.getBbWidth() > 2.5F || living.isInWater()) continue;
            Vec3 pos = living.position();
            Track track = TRACKS.get(entity.getId());
            if (track != null && track.last().distanceToSqr(pos) < STRIDE * STRIDE * Math.max(0.5, living.getBbWidth())) continue;
            boolean mud = !onSnow(level, pos) && inMud(level, pos);
            if (!onSnow(level, pos) && !mud) {
                TRACKS.put(entity.getId(), new Track(pos, track != null && track.left()));
                continue;
            }
            boolean left = track == null || !track.left();
            float yaw = track != null ? (float) Mth.atan2(pos.z - track.last().z, pos.x - track.last().x) : living.getYRot() * Mth.DEG_TO_RAD + Mth.HALF_PI;
            double side = (left ? 1 : -1) * 0.13 * Math.max(0.6, living.getBbWidth() / 0.6);
            double px = pos.x + Mth.sin(yaw) * side, pz = pos.z - Mth.cos(yaw) * side;
            PRINTS.addLast(new Print(px, surface(level, pos) + 0.006, pz, yaw, left, Math.min(1.6F, Math.max(0.6F, living.getBbWidth() / 0.6F)), now, mud));
            TRACKS.put(entity.getId(), new Track(pos, left));
        }
        if (TRACKS.size() > 256) TRACKS.clear();
        // prints vanish when the snow under them melts or is dug up
        if (now % 20 == 0) {
            Iterator<Print> it = PRINTS.iterator();
            while (it.hasNext()) {
                Print p = it.next();
                Vec3 under = new Vec3(p.x(), p.y() - 0.01, p.z());
                if (p.mud() ? !inMud(level, new Vec3(p.x(), p.y() - 0.13, p.z())) : !onSnow(level, new Vec3(p.x(), p.y() - 0.13, p.z())) && !onSnow(level, under)) it.remove();
            }
        }
    }

    /** The visible top of the snow you're standing in (a thin snow layer is drawn above your feet). */
    private static double surface(ClientLevel level, Vec3 feet) {
        BlockPos at = BlockPos.containing(feet.x, feet.y + 0.01, feet.z);
        BlockState state = level.getBlockState(at);
        if (state.is(Blocks.SNOW)) {
            return at.getY() + state.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) / 8.0;
        }
        // you sink into mud a little: the prints lie on its top
        if (mud(state)) return at.getY() + 1.0;
        return feet.y;
    }

    private static boolean mud(BlockState state) {
        return state.is(com.reiko.weather.registry.ModBlocks.SOFT_MUD.get()) || state.is(com.reiko.weather.registry.ModBlocks.MUDDY_PATH.get()) || state.is(Blocks.MUD);
    }

    private static boolean inMud(ClientLevel level, Vec3 feet) {
        return mud(level.getBlockState(BlockPos.containing(feet.x, feet.y + 0.01, feet.z))) || mud(level.getBlockState(BlockPos.containing(feet.x, feet.y - 0.01, feet.z)));
    }

    private static boolean onSnow(ClientLevel level, Vec3 feet) {
        BlockPos at = BlockPos.containing(feet.x, feet.y - 0.01, feet.z);
        BlockState state = level.getBlockState(at);
        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) return true;
        BlockState inside = level.getBlockState(BlockPos.containing(feet));
        return inside.is(Blocks.SNOW);
    }

    public static void render(Vec3 cam, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (PRINTS.isEmpty() || mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1.0F, -10.0F);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (Print p : PRINTS) {
            double dx = p.x() - cam.x, dz = p.z() - cam.z;
            if (dx * dx + dz * dz > 48 * 48) continue;
            float age = (now - p.born()) / (float) LIFETIME;
            float alpha = 0.55F * Mth.clamp((1.0F - age) / 0.35F, 0.0F, 1.0F);
            float c = Mth.cos(p.yaw()), s = Mth.sin(p.yaw());
            // blue-grey hollows in snow, dark wet prints in mud
            float r = p.mud() ? 0.16F : 0.55F, g = p.mud() ? 0.11F : 0.62F, b = p.mud() ? 0.07F : 0.75F;
            float len = 0.17F * p.size(), wid = 0.08F * p.size();
            float x = (float) dx, y = (float) (p.y() - cam.y), z = (float) dz;
            // forward (c, s) along the stride, side (-s, c)
            float u0 = p.left() ? 0.0F : 1.0F, u1 = p.left() ? 1.0F : 0.0F;
            buffer.addVertex(x - c * len - s * -wid, y, z - s * len - c * wid).setUv(u0, 1).setColor(r, g, b, alpha);
            buffer.addVertex(x - c * len + s * -wid, y, z - s * len + c * wid).setUv(u1, 1).setColor(r, g, b, alpha);
            buffer.addVertex(x + c * len + s * -wid, y, z + s * len + c * wid).setUv(u1, 0).setColor(r, g, b, alpha);
            buffer.addVertex(x + c * len - s * -wid, y, z + s * len - c * wid).setUv(u0, 0).setColor(r, g, b, alpha);
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

    public static void reset() {
        PRINTS.clear();
        TRACKS.clear();
    }

    private Footprints() {}
}
