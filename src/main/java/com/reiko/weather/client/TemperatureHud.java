package com.reiko.weather.client;

import com.reiko.weather.ReikosWeather;
import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.registry.ModItems;
import com.reiko.weather.temperature.Temperature;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** A thermometer readout under the forecast panel while a thermometer is held. */
public final class TemperatureHud {
    public static final ResourceLocation ID = ReikosWeather.id("temperature");
    private static float air = Float.NaN;
    private static float felt = Float.NaN;

    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !holding(player)) {
            air = felt = Float.NaN;
            return;
        }
        if (player.tickCount % 10 != 0 && !Float.isNaN(felt)) {
            return;
        }
        float a = Temperature.ambient(mc.level, BlockPos.containing(player.getEyePosition()), ClientWeather.local());
        float f = Temperature.felt(mc.level, player, ClientWeather.local());
        air = Float.isNaN(air) ? a : Mth.lerp(0.5F, air, a);
        felt = Float.isNaN(felt) ? f : Mth.lerp(0.5F, felt, f);
    }

    private static boolean holding(LocalPlayer player) {
        return player.getMainHandItem().is(ModItems.THERMOMETER.get()) || player.getOffhandItem().is(ModItems.THERMOMETER.get());
    }

    /** Air temperature at the camera, or NaN when no thermometer is held. */
    public static float air() {
        return air;
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || Float.isNaN(felt) || mc.getDebugOverlay().showDebugScreen()
                || ClientConfig.HUD.get() == ClientConfig.HudMode.NEVER) {
            return;
        }
        Font font = mc.font;
        boolean fahrenheit = ClientConfig.FAHRENHEIT.get();
        Temperature.Feel feel = Temperature.Feel.of(felt);
        Component main = Component.literal(Temperature.format(felt, fahrenheit)).withStyle(feel.color, ChatFormatting.BOLD);
        Component feelText = feel.displayName();
        Component airText = Component.translatable("hud.reikos_weather.air", Temperature.format(air, fahrenheit)).withStyle(ChatFormatting.GRAY);
        int textWidth = Math.max(font.width(main) + 4 + font.width(feelText), font.width(airText));
        int width = 16 + 6 + textWidth + 8;
        int height = 26;
        int x = graphics.guiWidth() - width - 4;
        int y = ForecastHud.bottom() + 4;
        graphics.fill(x, y, x + width, y + height, 0x90000000);
        graphics.renderOutline(x, y, width, height, 0x40FFFFFF);
        graphics.renderItem(new ItemStack(ModItems.THERMOMETER.get()), x + 4, y + 5);
        int tx = x + 4 + 16 + 6;
        graphics.drawString(font, main, tx, y + 4, 0xFFFFFF, false);
        graphics.drawString(font, feelText, tx + font.width(main) + 4, y + 4, 0xFFFFFF, false);
        graphics.drawString(font, airText, tx, y + 15, 0xFFFFFF, false);
        // a tiny mercury bar along the left edge, -20 to 50 °C
        float fill = Mth.clamp((felt + 20.0F) / 70.0F, 0.0F, 1.0F);
        int barTop = y + 3, barBottom = y + height - 3;
        int level = barBottom - Math.round((barBottom - barTop) * fill);
        graphics.fill(x + 1, barTop, x + 3, barBottom, 0x60FFFFFF);
        Integer color = feel.color.getColor();
        graphics.fill(x + 1, level, x + 3, barBottom, 0xFF000000 | (color != null ? color : 0xFFFFFF));
    }

    private TemperatureHud() {}
}
