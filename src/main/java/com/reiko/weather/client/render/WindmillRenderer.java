package com.reiko.weather.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reiko.weather.ReikosWeather;
import com.reiko.weather.block.WindmillBlock;
import com.reiko.weather.block.WindmillBlockEntity;
import com.reiko.weather.client.ClientWeather;
import com.reiko.weather.network.SyncedWeather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Turns the windmill's head into the wind and spins its sails as fast as the wind blows. */
public class WindmillRenderer implements BlockEntityRenderer<WindmillBlockEntity> {
    public static final ModelResourceLocation HEAD = ModelResourceLocation.standalone(ReikosWeather.id("block/windmill_head"));
    public static final ModelResourceLocation BLADES = ModelResourceLocation.standalone(ReikosWeather.id("block/windmill_blades"));
    /** Hub centre in block units (matches the models). */
    private static final float HUB_Y = 13.0F / 16.0F;
    private static final float HUB_Z = 3.0F / 16.0F;
    private final BlockRenderDispatcher blocks;

    public WindmillRenderer(BlockEntityRendererProvider.Context context) {
        this.blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(WindmillBlockEntity mill, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = mill.getLevel();
        if (level == null) {
            return;
        }
        float dt = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();
        boolean weather = level.dimension() == Level.OVERWORLD && SyncedWeather.managed();
        float targetYaw = weather ? WeatherVaneRenderer.upwindYaw(ClientWeather.windAngle()) : (Float.isNaN(mill.yaw) ? 0.0F : mill.yaw);
        if (Float.isNaN(mill.yaw)) {
            mill.yaw = targetYaw;
        } else {
            float k = 1.0F - (float) Math.pow(0.97, dt);
            mill.yaw += Mth.wrapDegrees((targetYaw - mill.yaw) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * k;
        }
        int power = mill.getBlockState().getValue(WindmillBlock.POWER);
        float targetSpeed = power / 15.0F * 0.32F;
        mill.speed = Mth.lerp(1.0F - (float) Math.pow(0.98, dt), mill.speed, targetSpeed);
        mill.spin = (mill.spin + mill.speed * dt) % Mth.TWO_PI;

        Minecraft mc = Minecraft.getInstance();
        BakedModel head = mc.getModelManager().getModel(HEAD);
        BakedModel blades = mc.getModelManager().getModel(BLADES);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotation(mill.yaw));
        poseStack.translate(-0.5, 0.0, -0.5);
        blocks.getModelRenderer().renderModel(poseStack.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()), mill.getBlockState(),
                head, 1.0F, 1.0F, 1.0F, light, overlay);
        poseStack.translate(0.5, HUB_Y, HUB_Z);
        poseStack.mulPose(Axis.ZP.rotation(mill.spin));
        poseStack.translate(-0.5, -HUB_Y, -HUB_Z);
        blocks.getModelRenderer().renderModel(poseStack.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()), mill.getBlockState(),
                blades, 1.0F, 1.0F, 1.0F, light, overlay);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(WindmillBlockEntity mill) {
        return new AABB(mill.getBlockPos()).inflate(1.0);
    }
}
