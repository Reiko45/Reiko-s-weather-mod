package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.network.SyncedWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Lets vanilla's rain checks see storm cells. Used by mixins on both logical sides, so it only touches
 * common code: the server's saved state, or the client's synced copy.
 */
public final class RegionalWeather {
    /** True if a rain-bearing storm cell is overhead (vanilla still checks sky access and biome after this). */
    public static boolean cellRainsAt(Level level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) {
            return false;
        }
        List<StormCell> cells;
        if (level.isClientSide) {
            if (!SyncedWeather.managed()) {
                return false;
            }
            cells = SyncedWeather.cells();
        } else {
            if (!WeatherConfig.SPEC.isLoaded()) {
                return false;
            }
            WeatherState state = WeatherManager.activeState(level);
            if (state == null) {
                return false;
            }
            cells = state.cells;
        }
        for (StormCell cell : cells) {
            if (cell.type.vanillaRain && cell.weightAt(pos.getX() + 0.5, pos.getZ() + 0.5) >= 0.5F) {
                return true;
            }
        }
        return false;
    }

    private RegionalWeather() {}
}
