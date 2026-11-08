package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Desert dust carried far: a sandstorm lifts dust high enough for the wind to carry it hundreds of blocks
 * beyond the desert, as a tawny haze that dims the sun, settles on the snow and turns the rain reddish.
 */
public final class DustHaze {
    static void tick(ServerLevel level, WeatherState state) {
        if (!WeatherConfig.DESERT_DUST.get()) {
            return;
        }
        RandomSource random = level.random;
        Vec3 wind = Wind.direction(state.windAngle);
        // downwind of each established sandstorm cell
        List<StormCell> storms = new ArrayList<>();
        for (StormCell cell : state.cells) {
            if (cell.type == WeatherType.SANDSTORM && cell.age > 1200 && cell.strength() > 0.6F) storms.add(cell);
        }
        for (StormCell storm : storms) {
            double x = storm.x + wind.x * (storm.radius + 380.0), z = storm.z + wind.z * (storm.radius + 380.0);
            if (covered(state, x, z)) continue;
            double speed = Math.max(0.08, Math.sqrt(storm.vx * storm.vx + storm.vz * storm.vz) * 0.9);
            spawn(level, state, x, z, speed, random);
        }
        // a desert-wide sandstorm: dust drifts out over whoever is downwind of a desert
        if (state.type == WeatherType.SANDSTORM && state.elapsed >= 1200 && state.elapsed < 1300) {
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || Climate.at(level, player.blockPosition()) == Climate.ARID) continue;
                for (int d = 300; d <= 900; d += 150) {
                    double x = player.getX() - wind.x * d, z = player.getZ() - wind.z * d;
                    if (Climate.at(level, BlockPos.containing(x, level.getSeaLevel(), z)) == Climate.ARID) {
                        double hx = player.getX() - wind.x * 380, hz = player.getZ() - wind.z * 380;
                        if (!covered(state, hx, hz)) spawn(level, state, hx, hz, 0.1 + random.nextDouble() * 0.04, random);
                        break;
                    }
                }
            }
        }
    }

    private static boolean covered(WeatherState state, double x, double z) {
        for (StormCell cell : state.cells) {
            if (cell.type == WeatherType.DUST_HAZE && cell.distanceTo(x, z) < cell.radius + 150) return true;
        }
        return false;
    }

    private static void spawn(ServerLevel level, WeatherState state, double x, double z, double speed, RandomSource random) {
        if (state.cells.stream().filter(c -> c.type == WeatherType.DUST_HAZE).count() >= 3) return;
        Vec3 wind = Wind.direction(state.windAngle);
        float radius = 260.0F + random.nextFloat() * 120.0F;
        WeatherManager.spawnCell(level, state, WeatherType.DUST_HAZE, x, z, radius, wind.x * speed, wind.z * speed,
                12000 + random.nextInt(6000), 2400, true);
    }

    private DustHaze() {}
}
