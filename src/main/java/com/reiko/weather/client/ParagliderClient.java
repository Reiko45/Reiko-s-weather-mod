package com.reiko.weather.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reiko.weather.ReikosWeather;
import com.reiko.weather.item.ParagliderItem;
import com.reiko.weather.item.Paragliding;
import com.reiko.weather.weather.Microclimate;
import com.reiko.weather.weather.SkyPhenomena;
import com.reiko.weather.weather.Wind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Matrix4f;

/** Steers the local player's paraglider and draws the canopy over gliding players. */
public final class ParagliderClient {
    private static final ResourceLocation CANOPY = ReikosWeather.id("textures/entity/paraglider_canopy.png");
    private static float thermal;

    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !ParagliderItem.gliding(player)) {
            thermal = 0.0F;
            if (player != null) {
                Paragliding.clientTick(player, false, Vec3.ZERO, 0.0F, 0.0F);
            }
            return;
        }
        if (player.tickCount % 5 == 0) {
            float sun = SkyPhenomena.sunHeight(mc.level, 1.0F);
            boolean sunny = sun > 0.2F && ClientWeather.sunOcclusion() < 0.5F && ClientWeather.rain() < 0.1F;
            float peak = Microclimate.at(mc.level, player.getBlockX(), player.getBlockZ(), ClientWeather.windAngle()).peak();
            float ridge = Mth.clamp(peak, 0.0F, 1.0F) * Mth.clamp(ClientWeather.wind() * 1.5F, 0.0F, 1.0F) * 0.8F;
            float target = Paragliding.thermalAt(mc.level, player.position(), ClientWeather.airTemperature(), sunny, ridge);
            thermal = Mth.lerp(0.5F, thermal, target);
        }
        float wind = ClientWeather.active() ? ClientWeather.wind() : 0.0F;
        Paragliding.clientTick(player, true, Wind.direction(ClientWeather.windAngle()), wind, thermal);
        if (thermal > 0.3F && player.tickCount % 4 == 0) {
            mc.level.addParticle(ParticleTypes.WHITE_ASH, player.getX() + (player.getRandom().nextDouble() - 0.5) * 3, player.getY() - 1,
                    player.getZ() + (player.getRandom().nextDouble() - 0.5) * 3, 0.0, 0.1, 0.0);
        }
    }

    public static float thermal() {
        return thermal;
    }

    /** A curved canopy above the player, on lines down to their shoulders. */
    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        if (!ParagliderItem.gliding(player)) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        float yaw = Mth.rotLerp(event.getPartialTick(), player.yBodyRotO, player.yBodyRot);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yaw));
        VertexConsumer buffer = event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(CANOPY));
        Matrix4f m = pose.last().pose();
        int light = event.getPackedLight();
        int segments = 8;
        float span = 2.2F, depth = 0.9F, height = 3.3F;
        for (int i = 0; i < segments; i++) {
            float a0 = (i / (float) segments - 0.5F) * 2.4F, a1 = ((i + 1) / (float) segments - 0.5F) * 2.4F;
            float x0 = Mth.sin(a0) * span, y0 = height + Mth.cos(a0) * 0.7F;
            float x1 = Mth.sin(a1) * span, y1 = height + Mth.cos(a1) * 0.7F;
            float u0 = i / (float) segments, u1 = (i + 1) / (float) segments;
            vertex(buffer, m, x0, y0, -depth, u0, 0.0F, light);
            vertex(buffer, m, x1, y1, -depth, u1, 0.0F, light);
            vertex(buffer, m, x1, y1, depth * 0.3F, u1, 0.5F, light);
            vertex(buffer, m, x0, y0, depth * 0.3F, u0, 0.5F, light);
        }
        // the lines: thin quads from the canopy's ends to the shoulders
        for (int side = -1; side <= 1; side += 2) {
            float tx = side * span * Mth.sin(1.2F), ty = height + Mth.cos(1.2F) * 0.7F;
            vertex(buffer, m, tx, ty, -0.3F, 0.0F, 0.9F, light);
            vertex(buffer, m, tx + 0.04F, ty, -0.3F, 0.05F, 0.9F, light);
            vertex(buffer, m, side * 0.3F + 0.04F, 1.4F, 0.0F, 0.05F, 1.0F, light);
            vertex(buffer, m, side * 0.3F, 1.4F, 0.0F, 0.0F, 1.0F, light);
        }
        pose.popPose();
    }

    private static void vertex(VertexConsumer buffer, Matrix4f m, float x, float y, float z, float u, float v, int light) {
        buffer.addVertex(m, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(0.0F, 1.0F, 0.0F);
    }

    private ParagliderClient() {}
}
