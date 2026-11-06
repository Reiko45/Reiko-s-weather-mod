package com.reiko.weather.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.reiko.weather.ReikosWeather;
import com.reiko.weather.block.ForecastBoardBlockEntity;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.weather.CustomWeather;
import com.reiko.weather.weather.CustomWeathers;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.WeatherType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import org.joml.Matrix4f;

/** Writes the weather, the season and the next three forecast icons onto the board. */
public class ForecastBoardRenderer implements BlockEntityRenderer<ForecastBoardBlockEntity> {
    private static final ResourceLocation ICONS = ReikosWeather.id("textures/gui/weather_icons.png");
    private static final int ICON_COUNT = WeatherType.values().length;
    private static final int INK = 0xFF2B2420;
    private final Font font;

    public ForecastBoardRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(ForecastBoardBlockEntity board, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = board.getLevel();
        if (level == null) {
            return;
        }
        Direction facing = board.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(0.0, 0.0, -7.0 / 16.0 + 0.006);

        boolean signal = level.dimension() == Level.OVERWORLD && SyncedWeather.managed();
        text(poseStack, buffers, Component.translatable("block.reikos_weather.forecast_board.title"), 0.0F, 0.215F, 0.0080F, light, true);
        if (!signal) {
            text(poseStack, buffers, Component.translatable("block.reikos_weather.forecast_board.no_signal"), 0.0F, 0.02F, 0.0075F, light, true);
            poseStack.popPose();
            return;
        }
        WeatherType now = SyncedWeather.type();
        CustomWeather custom = CustomWeathers.client(SyncedWeather.custom());
        MutableComponent name = custom != null ? custom.displayName() : now.displayName();
        icon(poseStack, buffers, now, -0.42F, -0.03F, 0.17F, light);
        text(poseStack, buffers, name, -0.2F, 0.115F, 0.0074F, light, false);
        Season season = SyncedWeather.season();
        if (season != null) {
            MutableComponent seasonText = SyncedWeather.dayOfSeason() >= 0
                    ? Component.translatable("hud.reikos_weather.season_day", season.displayName(), SyncedWeather.dayOfSeason() + 1, SyncedWeather.daysPerSeason())
                    : season.displayName();
            text(poseStack, buffers, seasonText, -0.2F, 0.045F, 0.0060F, light, false);
        }
        text(poseStack, buffers, Component.translatable("hud.reikos_weather.next"), -0.42F, -0.115F, 0.0064F, light, false);
        float x = -0.2F;
        for (WeatherType next : SyncedWeather.forecast()) {
            icon(poseStack, buffers, next, x, -0.225F, 0.15F, light);
            x += 0.2F;
        }
        poseStack.popPose();
    }

    private void text(PoseStack poseStack, MultiBufferSource buffers, Component text, float x, float y, float scale, int light, boolean centred) {
        poseStack.pushPose();
        poseStack.translate(x, y, 0.0F);
        poseStack.scale(scale, -scale, scale);
        float width = font.width(text);
        float maxWidth = (0.43F - x) / scale;
        if (!centred && width > maxWidth) {
            float squeeze = maxWidth / width;
            poseStack.scale(squeeze, squeeze, 1.0F);
        }
        font.drawInBatch(text, centred ? -width / 2.0F : 0.0F, 0.0F, INK, false, poseStack.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, light);
        poseStack.popPose();
    }

    private static void icon(PoseStack poseStack, MultiBufferSource buffers, WeatherType type, float x, float y, float size, int light) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.text(ICONS));
        Matrix4f m = poseStack.last().pose();
        float u0 = type.ordinal() / (float) ICON_COUNT, u1 = (type.ordinal() + 1) / (float) ICON_COUNT;
        float z = 0.002F;
        consumer.addVertex(m, x, y, z).setColor(255, 255, 255, 255).setUv(u0, 1.0F).setLight(light);
        consumer.addVertex(m, x + size, y, z).setColor(255, 255, 255, 255).setUv(u1, 1.0F).setLight(light);
        consumer.addVertex(m, x + size, y + size, z).setColor(255, 255, 255, 255).setUv(u1, 0.0F).setLight(light);
        consumer.addVertex(m, x, y + size, z).setColor(255, 255, 255, 255).setUv(u0, 0.0F).setLight(light);
    }
}
