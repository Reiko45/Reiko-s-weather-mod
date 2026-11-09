package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
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
    /** Somewhere that was burning in each area, for fire whirls to start from. */
    private static final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap BURNING = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    /** A fire this size spins up fire whirls; bigger still and it builds its own thundercloud. */
    private static final int HUGE_FIRE = 150, FIRESTORM = 220;
    /** Fire ticks per area in five seconds that make a "big" fire: about twenty blocks burning at once. */
    private static final int BIG_FIRE = 60;

    /** Called (from a mixin) every time a fire block ticks. */
    public static void onFireTick(Level level, BlockPos pos) {
        if (level.dimension() == Level.OVERWORLD) {
            long key = ChunkPos.asLong(pos.getX() >> 5, pos.getZ() >> 5);
            COUNTS.addTo(key, 1);
            BURNING.put(key, pos.asLong());
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
        if (WeatherConfig.FIRE_WHIRLS.get()) {
            RandomSource random = level.random;
            for (Long2IntMap.Entry e : COUNTS.long2IntEntrySet()) {
                if (e.getIntValue() < HUGE_FIRE || !BURNING.containsKey(e.getLongKey())) continue;
                BlockPos fire = BlockPos.of(BURNING.get(e.getLongKey()));
                // the heat sets the air spinning: a fire whirl
                boolean whirlNear = !level.getEntities(com.reiko.weather.registry.ModEntities.TORNADO.get(),
                        new net.minecraft.world.phys.AABB(fire).inflate(64), t -> t.kind() == com.reiko.weather.entity.TornadoEntity.Kind.FIRE_WHIRL).isEmpty();
                if (!whirlNear && random.nextInt(3) == 0 && level.isLoaded(fire)) {
                    WeatherHazards.spawnTornado(level, net.minecraft.world.phys.Vec3.atBottomCenterOf(fire), random.nextFloat() * Mth.TWO_PI,
                            com.reiko.weather.entity.TornadoEntity.Kind.FIRE_WHIRL);
                }
                // a firestorm: the smoke column builds into a thundercloud that throws dry lightning downwind
                if (e.getIntValue() >= FIRESTORM && state.cells.stream().noneMatch(c -> c.type == WeatherType.PYROCUMULUS
                        && c.distanceTo(fire.getX(), fire.getZ()) < c.radius + 200)) {
                    Vec3 wind = Wind.direction(state.windAngle);
                    WeatherManager.spawnCell(level, state, WeatherType.PYROCUMULUS, fire.getX() + wind.x * 60, fire.getZ() + wind.z * 60,
                            110.0F + random.nextFloat() * 40.0F, wind.x * 0.04, wind.z * 0.04, 4000 + random.nextInt(2400), 1200, true);
                }
            }
        }
        COUNTS.clear();
        BURNING.clear();
    }

    public static void reset() {
        COUNTS.clear();
        BURNING.clear();
    }

    private FireTracker() {}
}
