package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Notices big fires (by counting fire blocks ticking in each 32-block area) and sends a pall of wildfire
 * smoke drifting downwind from them.
 */
public final class FireTracker {
    private static final Long2IntOpenHashMap COUNTS = new Long2IntOpenHashMap();
    /** Fire ticks per area in five seconds that make a "big" fire: about twenty blocks burning at once. */
    private static final int BIG_FIRE = 60;

    /** Called (from a mixin) every time a fire block ticks. */
    public static void onFireTick(Level level, BlockPos pos) {
        if (level.dimension() == Level.OVERWORLD) {
            COUNTS.addTo(ChunkPos.asLong(pos.getX() >> 5, pos.getZ() >> 5), 1);
        }
    }

    /** Smouldering peat ticks far less often than fire, so each tick counts for more (a big peat fire smokes too). */
    public static void onSmoulder(Level level, BlockPos pos) {
        if (level.dimension() == Level.OVERWORLD) {
            COUNTS.addTo(ChunkPos.asLong(pos.getX() >> 5, pos.getZ() >> 5), 20);
        }
    }

    static void tick(ServerLevel level, WeatherState state) {
        if (COUNTS.isEmpty()) {
            return;
        }
        if (WeatherConfig.WILDFIRE_SMOKE.get()) {
            RandomSource random = level.random;
            for (Long2IntMap.Entry e : COUNTS.long2IntEntrySet()) {
                if (e.getIntValue() < BIG_FIRE) continue;
                double x = ChunkPos.getX(e.getLongKey()) * 32 + 16, z = ChunkPos.getZ(e.getLongKey()) * 32 + 16;
                boolean covered = state.cells.stream().anyMatch(c -> c.type == WeatherType.WILDFIRE_SMOKE && c.distanceTo(x, z) < c.radius * 0.7F);
                if (covered) continue;
                Vec3 wind = Wind.direction(state.windAngle);
                float radius = 110.0F + random.nextFloat() * 60.0F;
                double speed = 0.06 + random.nextDouble() * 0.03;
                WeatherManager.spawnCell(level, state, WeatherType.WILDFIRE_SMOKE, x + wind.x * radius * 0.4, z + wind.z * radius * 0.4,
                        radius, wind.x * speed, wind.z * speed, 4000 + random.nextInt(2000), 600, true);
            }
        }
        COUNTS.clear();
    }

    public static void reset() {
        COUNTS.clear();
    }

    private FireTracker() {}
}
