package com.reiko.weather.client;

import com.reiko.weather.ReikosWeather;
import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.registry.ModItems;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.StormCell;
import com.reiko.weather.weather.WeatherReport;
import com.reiko.weather.weather.WeatherType;
import com.reiko.weather.weather.Wind;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** A small forecast panel in the top-right corner, shown while holding a barometer (or always). */
public final class ForecastHud {
    public static final ResourceLocation ID = ReikosWeather.id("forecast");
    private static final ResourceLocation ICONS = ReikosWeather.id("textures/gui/weather_icons.png");
    private static final int ICON = 16;
    private static final int ICON_COUNT = WeatherType.values().length;
    /** Bottom edge of the panel drawn this frame (0 when hidden), so other panels can stack under it. */
    private static int bottom;

    public static int bottom() {
        return bottom;
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        bottom = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null || mc.level.dimension() != Level.OVERWORLD
                || !SyncedWeather.managed() || mc.getDebugOverlay().showDebugScreen() || menuOpen(mc)) {
            return;
        }
        ClientConfig.HudMode mode = ClientConfig.HUD.get();
        boolean holding = mc.player.getMainHandItem().is(ModItems.BAROMETER.get()) || mc.player.getOffhandItem().is(ModItems.BAROMETER.get());
        if (mode == ClientConfig.HudMode.NEVER || mode == ClientConfig.HudMode.BAROMETER && !holding) {
            return;
        }
        Font font = mc.font;
        WeatherType local = ClientWeather.type();
        WeatherType background = SyncedWeather.type();
        List<Component> lines = new ArrayList<>();
        com.reiko.weather.weather.CustomWeather custom = com.reiko.weather.weather.CustomWeathers.client(SyncedWeather.custom());
        lines.add((local == background && custom != null ? custom.displayName() : local.displayName()).withStyle(ChatFormatting.WHITE));
        lines.add(local == background
                ? WeatherReport.duration(SyncedWeather.remaining()).withStyle(ChatFormatting.GRAY)
                : Component.translatable("hud.reikos_weather.storm_overhead").withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("hud.reikos_weather.wind", Wind.kmh(ClientWeather.wind()),
                Component.translatable("direction.reikos_weather." + Wind.compassFrom(ClientWeather.windAngle()))).withStyle(ChatFormatting.GRAY));
        Season season = SyncedWeather.season();
        if (season != null) {
            lines.add(SyncedWeather.dayOfSeason() >= 0
                    ? Component.translatable("hud.reikos_weather.season_day", season.displayName(), SyncedWeather.dayOfSeason() + 1,
                    SyncedWeather.daysPerSeason()).withStyle(ChatFormatting.GREEN)
                    : season.displayName().withStyle(ChatFormatting.GREEN));
        }
        Vec3 pos = mc.player.position();
        StormCell nearest = null;
        double best = 1500;
        for (StormCell cell : SyncedWeather.cells()) {
            double edge = cell.distanceTo(pos.x, pos.z) - cell.radius;
            if (edge > 0 && edge < best) {
                best = edge;
                nearest = cell;
            }
        }
        if (nearest != null) {
            lines.add(Component.translatable("hud.reikos_weather.storm", nearest.type.displayName(), (int) best,
                    Component.translatable("direction.reikos_weather." + WeatherReport.compass(nearest.x - pos.x, nearest.z - pos.z)))
                    .withStyle(ChatFormatting.GOLD));
        }

        WeatherType[] forecast = SyncedWeather.forecast();
        int textWidth = 0;
        for (Component line : lines) {
            textWidth = Math.max(textWidth, font.width(line));
        }
        int width = Math.max(ICON + 6 + textWidth, forecast.length * (ICON + 2) + font.width(Component.translatable("hud.reikos_weather.next")) + 4) + 8;
        int height = 6 + Math.max(ICON, lines.size() * 10) + 4 + ICON + 4;
        int x = graphics.guiWidth() - width - 4;
        int y = 4;
        graphics.fill(x, y, x + width, y + height, 0x90000000);
        graphics.renderOutline(x, y, width, height, 0x40FFFFFF);
        bottom = y + height;

        icon(graphics, local, x + 4, y + 5);
        int ty = y + 5;
        for (Component line : lines) {
            graphics.drawString(font, line, x + 4 + ICON + 6, ty, 0xFFFFFF, false);
            ty += 10;
        }
        int fy = y + height - ICON - 4;
        Component next = Component.translatable("hud.reikos_weather.next").withStyle(ChatFormatting.GRAY);
        graphics.drawString(font, next, x + 4, fy + 4, 0xFFFFFF, false);
        int fx = x + 6 + font.width(next);
        for (WeatherType type : forecast) {
            icon(graphics, type, fx, fy);
            fx += ICON + 2;
        }
    }

    /** True while a menu (other than chat) is open, so the panels don't sit on top of inventories and JEI. */
    public static boolean menuOpen(Minecraft mc) {
        return mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen);
    }

    private static void icon(GuiGraphics graphics, WeatherType type, int x, int y) {
        graphics.blit(ICONS, x, y, type.ordinal() * ICON, 0, ICON, ICON, ICON * ICON_COUNT, ICON);
    }

    private ForecastHud() {}
}
