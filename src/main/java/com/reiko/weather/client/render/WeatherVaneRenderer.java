package com.reiko.weather.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reiko.weather.ReikosWeather;
import com.reiko.weather.block.WeatherVaneBlock;
import com.reiko.weather.block.WeatherVaneBlockEntity;
import com.reiko.weather.client.ClientWeather;
import com.reiko.weather.network.SyncedWeather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Turns the vane's arrow to point into the wind, wobbling a little in gusts. */
public class WeatherVaneRenderer implements BlockEntityRenderer<WeatherVaneBlockEntity> {
    public static final ModelResourceLocation ARROW = ModelResourceLocation.standalone(ReikosWeather.id("block/weather_vane_arrow"));
    private final BlockRenderDispatcher blocks;

    public WeatherVaneRenderer(BlockEntityRendererProvider.Context context) {
        this.blocks = context.getBlockRenderDispatcher();
    }

    /** Model yaw (radians) that makes a north-pointing (-Z) model point toward where the wind comes from. */
    public static float upwindYaw(float windAngle) {
        return Mth.HALF_PI - windAngle;
    }

    @Override
    public void render(WeatherVaneBlockEntity vane, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = vane.getLevel();
        if (level == null) {
            return;
        }
        float target;
        float wind;
        if (level.dimension() == Level.OVERWORLD && SyncedWeather.managed()) {
            target = upwindYaw(ClientWeather.windAngle());
            wind = ClientWeather.wind();
        } else {
            int direction = vane.getBlockState().getValue(WeatherVaneBlock.DIRECTION);
            // compass index 1-8: n, ne, e ... measured clockwise from north; the arrow points at the source
            target = direction == 0 ? (Float.isNaN(vane.angle) ? 0.0F : vane.angle) : -(direction - 1) * Mth.HALF_PI / 2.0F;
            wind = 0.2F;
        }
        float dt = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();
        if (Float.isNaN(vane.angle)) {
            vane.angle = target;
        } else {
            float k = 1.0F - (float) Math.pow(0.93, dt);
            vane.angle += Mth.wrapDegrees((target - vane.angle) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * k;
        }
        float time = level.getGameTime() + partialTick;
        float wobble = Mth.sin(time * 0.35F + vane.getBlockPos().hashCode()) * Mth.sin(time * 0.11F) * 0.18F * (0.2F + wind);

        BakedModel model = Minecraft.getInstance().getModelManager().getModel(ARROW);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotation(vane.angle + wobble));
        poseStack.translate(-0.5, 0.0, -0.5);
        blocks.getModelRenderer().renderModel(poseStack.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()), vane.getBlockState(),
                model, 1.0F, 1.0F, 1.0F, light, overlay);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(WeatherVaneBlockEntity vane) {
        return new AABB(vane.getBlockPos()).inflate(0.5, 0.5, 0.5);
    }
}
