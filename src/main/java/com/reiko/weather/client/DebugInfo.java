package com.reiko.weather.client;

import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.network.TemperaturePayload;
import com.reiko.weather.weather.Microclimate;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

import java.util.Locale;

/** A line on the F3 screen: the weather where you are, the terrain's influence and your body temperature. */
public final class DebugInfo {
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        StringBuilder line = new StringBuilder("[Reiko's Weather] ");
        if (mc.level.dimension() == Level.OVERWORLD && SyncedWeather.managed()) {
            line.append(ClientWeather.type().id()).append(" (").append(ClientWeather.climate().name().toLowerCase(Locale.ROOT)).append(')');
            Microclimate.Info m = Microclimate.at(mc.level, mc.player.getBlockX(), mc.player.getBlockZ(), SyncedWeather.windAngle());
            line.append(String.format(Locale.ROOT, " valley %.2f peak %.2f shadow %.2f lake %.2f", m.valley(), m.peak(), m.rainShadow(), m.lakeEffect()));
        } else {
            line.append(com.reiko.weather.otherworld.OtherworldType.byOrdinal(com.reiko.weather.network.OtherworldPayload.clientWeather).id());
        }
        float body = TemperaturePayload.clientBody;
        if (!Float.isNaN(body)) {
            line.append(String.format(Locale.ROOT, " | body %.1f°C -> %.1f°C", body, TemperaturePayload.clientTarget));
        }
        event.getLeft().add(line.toString());
        if (mc.level.dimension() == Level.OVERWORLD && SyncedWeather.managed()) {
            event.getLeft().add(String.format(Locale.ROOT, "[Reiko's Weather] air %.1f°C halo %.2f beams %.2f blood %.2f frost %.2f seas %.2f",
                    ClientWeather.airTemperature(), ClientWeather.halo(), ClientWeather.sunbeams(), ClientWeather.bloodMoon(),
                    SeasonalColors.frost(), RoughSeas.roughness()));
        }
    }

    private DebugInfo() {}
}
