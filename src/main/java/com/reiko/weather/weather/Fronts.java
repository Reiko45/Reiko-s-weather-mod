package com.reiko.weather.weather;

import com.reiko.weather.network.SyncedWeather;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Weather fronts are long storm cells that sweep across the map. A cold front is a fast squall line with
 * cooler air behind it; a warm front a broad band of cloud and drizzle with milder air behind it. As one
 * goes through, the wind swings round.
 */
public final class Fronts {
    /** Per front cell: whether it was still heading toward the players last time we looked. */
    private static final Map<Integer, Boolean> APPROACHING = new HashMap<>();

    static void tick(ServerLevel level, WeatherState state) {
        if (level.getGameTime() % 20L != 0L) {
            return;
        }
        double x = 0, z = 0;
        int n = 0;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()) {
                x += player.getX();
                z += player.getZ();
                n++;
            }
        }
        if (n > 0) {
            x /= n;
            z /= n;
        }
        for (StormCell cell : state.cells) {
            if (!cell.type.isFront() || n == 0) continue;
            boolean now = cell.approaching(x, z);
            Boolean before = APPROACHING.put(cell.id, now);
            if (Boolean.TRUE.equals(before) && !now && Math.abs(across(cell, x, z)) < cell.radius * cell.elongation()) {
                // the front just went through: the wind veers round behind it
                state.windDrift = cell.type == WeatherType.COLD_FRONT ? 0.004F : 0.0025F;
                state.setDirty();
            }
        }
        APPROACHING.keySet().removeIf(id -> state.cells.stream().noneMatch(c -> c.id == id));
    }

    /** Distance across the front's direction of travel (along its length). */
    private static double across(StormCell cell, double x, double z) {
        double speed = Math.sqrt(cell.vx * cell.vx + cell.vz * cell.vz);
        if (speed < 1.0E-6) return 0.0;
        double ux = cell.vx / speed, uz = cell.vz / speed;
        return -(x - cell.x) * uz + (z - cell.z) * ux;
    }

    /**
     * How much warmer (or cooler) the air is at a point because a front has already gone over it: about
     * 7 °C colder behind a cold front and 4 °C milder behind a warm front, fading out ~2.5 km back.
     */
    public static float temperatureOffset(List<StormCell> cells, double x, double z) {
        float offset = 0.0F;
        for (StormCell cell : cells) {
            if (!cell.type.isFront()) continue;
            double speed = Math.sqrt(cell.vx * cell.vx + cell.vz * cell.vz);
            if (speed < 1.0E-6) continue;
            double ux = cell.vx / speed, uz = cell.vz / speed;
            double along = (x - cell.x) * ux + (z - cell.z) * uz;
            double halfLength = cell.radius * cell.elongation();
            double side = Math.abs(across(cell, x, z));
            if (side > halfLength || along > -cell.radius * 0.5) continue;
            float behind = (float) (-along - cell.radius * 0.5);
            float fade = Mth.clamp(1.0F - behind / 2500.0F, 0.0F, 1.0F) * Mth.clamp((float) ((halfLength - side) / (halfLength * 0.2)), 0.0F, 1.0F);
            float ramp = Mth.clamp(behind / 150.0F, 0.0F, 1.0F); // it cools off over the first stretch behind the line
            offset += (cell.type == WeatherType.COLD_FRONT ? -7.0F : 4.0F) * fade * ramp * Math.max(0.3F, cell.strength());
        }
        return offset;
    }

    /** The storm cells a level knows about (the server's own, or the client's synced copy). */
    public static List<StormCell> cells(Level level) {
        if (level.dimension() != Level.OVERWORLD) {
            return List.of();
        }
        if (level.isClientSide) {
            return SyncedWeather.cells();
        }
        WeatherState state = WeatherManager.activeState(level);
        return state == null ? List.of() : state.cells;
    }

    public static void reset() {
        APPROACHING.clear();
    }

    private Fronts() {}
}
