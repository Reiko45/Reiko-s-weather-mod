package com.reiko.weather.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.reiko.weather.ReikosWeather;
import com.reiko.weather.entity.TornadoEntity;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.registry.ModEntities;
import com.reiko.weather.weather.StormCell;
import com.reiko.weather.weather.WeatherReport;
import com.reiko.weather.weather.WeatherType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** A green radar scope showing storm cells (and tornadoes) moving across the map. Scroll to zoom. */
public class RadarScreen extends Screen {
    private static final ResourceLocation BACK = ReikosWeather.id("textures/gui/radar_back.png");
    private static final ResourceLocation BEZEL = ReikosWeather.id("textures/gui/radar_bezel.png");
    private static final ResourceLocation SWEEP = ReikosWeather.id("textures/gui/radar_sweep.png");
    private static final ResourceLocation BLOB = ReikosWeather.id("textures/gui/radar_blob.png");
    private static final int[] RANGES = {400, 800, 1600, 3200};

    private final Vec3 fixedCenter;
    private final boolean followPlayer;
    private int range = 2;

    public RadarScreen(Vec3 center, boolean followPlayer) {
        super(Component.translatable("screen.reikos_weather.radar"));
        this.fixedCenter = center;
        this.followPlayer = followPlayer;
    }

    public static int colorOf(WeatherType type) {
        return switch (type) {
            case RAIN, DRIZZLE -> 0x3C78FF;
            case HEAVY_RAIN -> 0x2850D0;
            case THUNDERSTORM -> 0xFFD23C;
            case SUPERCELL -> 0xFF3C3C;
            case HURRICANE -> 0xC03CFF;
            case HAIL -> 0xF0F0FF;
            case SLEET -> 0x8CC8FF;
            case ICE_STORM -> 0xB4E6FF;
            case SNOW, BLIZZARD -> 0xE6F4FF;
            case SANDSTORM -> 0xE6B464;
            case DERECHO -> 0xFF8C28;
            case VOLCANIC_ASH -> 0x8A7F78;
            case COLD_FRONT -> 0x4F8DFF;
            case WARM_FRONT -> 0xFF6F6F;
            case SEA_FOG -> 0xD6DCE2;
            case WILDFIRE_SMOKE -> 0xB0703A;
            case VOLCANIC_SMOG -> 0xB8B07A;
            case DUST_HAZE -> 0xC89A64;
            case COLD_SNAP -> 0xA8D8FF;
            case HABOOB -> 0xB06A32;
            case ATMOSPHERIC_RIVER -> 0x2E6BFF;
            case HEAT_BURST -> 0xFF7A3C;
            case PYROCUMULUS -> 0x9A5A3A;
            case GRAUPEL -> 0xE4EEF6;
            case THUNDERSNOW -> 0xB8C8FF;
            case FREEZING_DRIZZLE -> 0x9EC4D8;
            default -> 0x9AA0A6;
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        range = Mth.clamp(range - (int) Math.signum(scrollY), 0, RANGES.length - 1);
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        Vec3 center = followPlayer ? mc.player.position() : fixedCenter;
        boolean overworld = mc.level.dimension() == Level.OVERWORLD && SyncedWeather.managed();
        int size = Math.min((int) (width * 0.55), height - 50) & ~1;
        int cx = 20 + size / 2, cy = height / 2 + 6;
        int radius = size / 2;
        double blocks = RANGES[range];
        double scale = radius / blocks;
        float time = mc.level.getGameTime() + partialTick;

        int textX = cx + radius + 30;
        g.drawString(font, title.copy().withStyle(ChatFormatting.GREEN), textX, cy - radius, 0xFFFFFF, true);
        g.drawString(font, Component.translatable("screen.reikos_weather.radar.range", RANGES[range]).withStyle(ChatFormatting.GRAY),
                textX, cy - radius + 11, 0xFFFFFF, false);
        blend();
        g.blit(BACK, cx - radius, cy - radius, size, size, 0, 0, 256, 256, 256, 256);

        List<StormCell> cells = new ArrayList<>(overworld ? SyncedWeather.cells() : List.of());
        boolean interference = overworld && Geomagnetic.active();
        java.util.Random jitter = new java.util.Random((long) (time / 3));
        for (StormCell cell : cells) {
            double px = cx + (cell.x - center.x) * scale, py = cy + (cell.z - center.z) * scale;
            if (interference) {
                px += (jitter.nextFloat() - 0.5F) * 14;
                py += (jitter.nextFloat() - 0.5F) * 14;
            }
            double r = Math.max(3, cell.radius * scale);
            if (Math.hypot(px - cx, py - cy) > radius + r * cell.elongation()) continue;
            int c = colorOf(cell.type);
            blend(); // GUI fills switch blending off again after drawing
            RenderSystem.setShaderColor((c >> 16 & 255) / 255.0F, (c >> 8 & 255) / 255.0F, (c & 255) / 255.0F, 0.5F + 0.45F * cell.strength());
            if (cell.elongation() != 1.0F && Math.hypot(cell.vx, cell.vz) > 1.0E-4) {
                // a derecho is a long band across its direction of travel
                g.pose().pushPose();
                g.pose().translate(px, py, 0);
                g.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(cell.vz, cell.vx)));
                int across = (int) (r * cell.elongation());
                g.blit(BLOB, (int) -r, -across, (int) (r * 2), across * 2, 0, 0, 64, 64, 64, 64);
                g.pose().popPose();
            } else {
                g.blit(BLOB, (int) (px - r), (int) (py - r), (int) (r * 2), (int) (r * 2), 0, 0, 64, 64, 64, 64);
            }
            RenderSystem.setShaderColor(1, 1, 1, 1);
            // heading arrow
            double speed = Math.hypot(cell.vx, cell.vz);
            if (speed > 1.0E-4) {
                g.pose().pushPose();
                g.pose().translate(px, py, 0);
                g.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(cell.vz, cell.vx)));
                g.fill(0, -1, 9, 1, 0xDDFFFFFF);
                for (int k = 0; k < 4; k++) {
                    g.fill(9 + k, -3 + k, 10 + k, 3 - k, 0xDDFFFFFF);
                }
                g.pose().popPose();
            }
        }
        // tornadoes the client knows about
        AABB area = new AABB(center, center).inflate(blocks, 256, blocks);
        for (TornadoEntity tornado : mc.level.getEntities(ModEntities.TORNADO.get(), area, e -> true)) {
            int px = (int) (cx + (tornado.getX() - center.x) * scale), py = (int) (cy + (tornado.getZ() - center.z) * scale);
            int color = ((int) (time / 6) % 2 == 0) ? 0xFFFF2020 : 0xFFFFA020;
            g.fill(px - 2, py - 2, px + 3, py + 3, color);
        }
        // volcanoes: a small triangle, red and flashing while one erupts
        if (overworld) {
            for (com.reiko.weather.network.VolcanoPayload.Entry v : com.reiko.weather.network.VolcanoPayload.client) {
                double dx = v.x() - center.x, dz = v.z() - center.z;
                if (dx * dx + dz * dz > blocks * blocks) continue;
                int px = (int) (cx + dx * scale), py = (int) (cy + dz * scale);
                boolean erupting = v.phase() == com.reiko.weather.volcano.Volcano.Phase.ERUPTING.ordinal();
                int color = erupting ? ((int) (time / 6) % 2 == 0 ? 0xFFFF3010 : 0xFFFFB040)
                        : v.phase() == com.reiko.weather.volcano.Volcano.Phase.RUMBLING.ordinal() ? 0xFFE08A3A : 0xFF8A7F78;
                for (int row = 0; row < 4; row++) {
                    g.fill(px - row, py - 2 + row, px + row + 1, py - 1 + row, color);
                }
            }
        }
        if (interference) {
            // static from the geomagnetic storm
            for (int i = 0; i < 260; i++) {
                double a = jitter.nextDouble() * Math.PI * 2, d = Math.sqrt(jitter.nextDouble()) * radius;
                int sx = (int) (cx + Math.cos(a) * d), sy = (int) (cy + Math.sin(a) * d);
                int shade = 120 + jitter.nextInt(135);
                g.fill(sx, sy, sx + 1 + jitter.nextInt(2), sy + 1, (0x50 + jitter.nextInt(0x60)) << 24 | shade << 16 | shade << 8 | shade);
            }
            blend();
        }
        // the sweep
        blend();
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees((time * 3.0F) % 360.0F));
        g.blit(SWEEP, -radius, -radius, size, size, 0, 0, 256, 256, 256, 256);
        g.pose().popPose();
        blend();
        g.blit(BEZEL, cx - radius - 6, cy - radius - 6, size + 12, size + 12, 0, 0, 256, 256, 256, 256);
        RenderSystem.disableBlend();

        // you are here, plus compass letters
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(mc.player.getYRot() + 180.0F));
        // a little arrowhead pointing the way you face
        for (int row = 0; row < 4; row++) {
            g.fill(-row, -5 + row, row + 1, -4 + row, 0xFFFFFFFF);
        }
        g.fill(0, -1, 1, 4, 0xFFFFFFFF);
        g.pose().popPose();
        g.drawCenteredString(font, "N", cx, cy - radius + 8, 0xA0FFA0);
        g.drawCenteredString(font, "S", cx, cy + radius - 8 - font.lineHeight, 0xA0FFA0);
        g.drawString(font, "W", cx - radius + 8, cy - 4, 0xA0FFA0);
        g.drawString(font, "E", cx + radius - 8 - font.width("E"), cy - 4, 0xA0FFA0);

        drawList(g, mc, center, cells, textX, cy - radius + 30, overworld);
    }

    private static void blend() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private void drawList(GuiGraphics g, Minecraft mc, Vec3 center, List<StormCell> cells, int x, int y, boolean overworld) {
        int maxWidth = width - x - 10;
        if (!overworld) {
            g.drawWordWrap(font, Component.translatable("screen.reikos_weather.radar.no_signal"), x, y, maxWidth, 0xA0A0A0);
            return;
        }
        Component here = Component.translatable("screen.reikos_weather.radar.here", ClientWeather.type().displayName());
        g.drawString(font, here, x, y, 0xFFFFFF, false);
        y += 14;
        if (Geomagnetic.active()) {
            g.drawWordWrap(font, Component.translatable("screen.reikos_weather.radar.interference").withStyle(ChatFormatting.GOLD), x, y, maxWidth, 0xFFAA00);
            y += 22;
        }
        cells.sort(Comparator.comparingDouble(c -> c.distanceTo(center.x, center.z) - c.radius));
        if (cells.isEmpty()) {
            g.drawString(font, Component.translatable("screen.reikos_weather.radar.clear"), x, y, 0x80FF80, false);
            return;
        }
        for (StormCell cell : cells) {
            if (y > height - 30) break;
            double edge = cell.distanceTo(center.x, center.z) - cell.radius;
            Component status;
            if (edge <= 0) {
                status = Component.translatable("screen.reikos_weather.radar.overhead").withStyle(ChatFormatting.GOLD);
            } else {
                double dx = center.x - cell.x, dz = center.z - cell.z, len = Math.max(1, Math.hypot(dx, dz));
                double closing = (cell.vx * dx + cell.vz * dz) / len; // blocks per tick toward us
                status = closing > 0.01
                        ? Component.translatable("screen.reikos_weather.radar.eta", Math.max(1, Math.round(edge / closing / 1200.0)))
                        : Component.translatable("screen.reikos_weather.radar.away").withStyle(ChatFormatting.GRAY);
            }
            int c = colorOf(cell.type);
            g.fill(x, y + 1, x + 7, y + 8, 0xFF000000 | c);
            g.drawString(font, cell.type.displayName(), x + 11, y, 0xFFFFFF, false);
            y += 10;
            String where = edge <= 0 ? "" : (int) edge + "m " + WeatherReport.compass(cell.x - center.x, cell.z - center.z).toUpperCase() + "  ";
            g.drawString(font, Component.literal(where).withStyle(ChatFormatting.GRAY).append(status), x + 11, y, 0xFFFFFF, false);
            y += 14;
        }
    }
}
