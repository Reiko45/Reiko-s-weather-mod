package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
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
    /** A fire this size (about eighty blocks burning at once) spins up fire whirls; bigger still and it builds its own thundercloud. */
    private static final int HUGE_FIRE = 240, FIRESTORM = 330;
    /** Fire ticks per area in five seconds that make a "big" fire: about thirty blocks burning at once. */
    private static final int BIG_FIRE = 90;
    /** How far below the surface (the tops of the trees) a fire can be and still count as burning out in the open. */
    private static final int OPEN_AIR = 14;

    /** Called (from a mixin) every time a fire block ticks. */
    public static void onFireTick(Level level, BlockPos pos) {
        // a wildfire leaves the ground charred (it grows back over the seasons)
        if (level instanceof net.minecraft.server.level.ServerLevel server && level.random.nextInt(3) == 0
                && WeatherConfig.FIRE_SUCCESSION.get() && server.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)) {
            BlockPos below = pos.below();
            var ground = level.getBlockState(below);
            if (ground.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) || ground.is(net.minecraft.world.level.block.Blocks.PODZOL)
                    || ground.is(net.minecraft.world.level.block.Blocks.DIRT) || ground.is(net.minecraft.world.level.block.Blocks.COARSE_DIRT)
                    || ground.is(net.minecraft.world.level.block.Blocks.ROOTED_DIRT) || ground.is(net.minecraft.world.level.block.Blocks.MYCELIUM)) {
                level.setBlock(below, com.reiko.weather.registry.ModBlocks.CHARRED_SOIL.get().defaultBlockState(), 2);
            }
        }
        if (level.dimension() == Level.OVERWORLD && wild(level, pos)) {
            long key = ChunkPos.asLong(pos.getX() >> 5, pos.getZ() >> 5);
            COUNTS.addTo(key, 1);
            BURNING.put(key, pos.asLong());
        }
    }

    /**
     * Whether a fire is burning out in the open, where its smoke rises into the sky: not deep in a cave or an old
     * mineshaft lit by lava (common near spawn in a new world), and not a tame fire that burns for ever on netherrack
     * or over a gas crater's vents.
     */
    private static boolean wild(Level level, BlockPos pos) {
        if (pos.getY() < level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) - OPEN_AIR) return false;
        BlockState below = level.getBlockState(pos.below());
        return !below.is(level.dimensionType().infiniburn()) && !below.is(com.reiko.weather.registry.ModBlocks.GAS_VENT.get());
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
                if (!whirlNear && random.nextInt(5) == 0 && level.isLoaded(fire)) {
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
        if (WeatherConfig.EMBER_SPOTTING.get() && level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOFIRETICK)
                && level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)) {
            for (Long2IntMap.Entry e : COUNTS.long2IntEntrySet()) {
                if (e.getIntValue() >= BIG_FIRE && BURNING.containsKey(e.getLongKey())) spot(level, state, BlockPos.of(BURNING.get(e.getLongKey())));
            }
        }
        COUNTS.clear();
        BURNING.clear();
    }

    /**
     * Spotting: a big fire's updraft lifts burning embers and the wind carries them on ahead, sometimes hundreds of
     * metres, so the fire jumps rivers and firebreaks and new fires spring up downwind of the main one.
     */
    private static void spot(ServerLevel level, WeatherState state, BlockPos fire) {
        RandomSource random = level.random;
        LocalWeather local = WeatherManager.local(level, fire);
        float wind = local == null ? 0.3F : local.profile().wind * local.intensity();
        if (wind < 0.25F || local != null && local.profile().rain > 0.2F || random.nextFloat() > 0.5F) return;
        Vec3 dir = Wind.direction(state.windAngle);
        double distance = 10 + random.nextDouble() * (20 + 50 * wind);
        double spread = (random.nextDouble() - 0.5) * 0.6;
        double x = fire.getX() + (dir.x - dir.z * spread) * distance, z = fire.getZ() + (dir.z + dir.x * spread) * distance;
        BlockPos column = BlockPos.containing(x, 0, z);
        if (!level.isLoaded(column)) return;
        BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, column);
        // a trail of embers in the air between them
        for (int k = 1; k <= 6; k++) {
            double t = k / 7.0;
            level.sendParticles(com.reiko.weather.registry.ModParticles.EMBER.get(), Mth.lerp(t, fire.getX(), x), Mth.lerp(t, fire.getY() + 6, top.getY() + 2) + 6 * Math.sin(t * Math.PI),
                    Mth.lerp(t, fire.getZ(), z), 3, 1.0, 1.0, 1.0, 0.02);
        }
        // it only catches on something dry that burns
        BlockState at = level.getBlockState(top);
        BlockState under = level.getBlockState(top.below());
        if (at.isAir() && under.isFlammable(level, top.below(), net.minecraft.core.Direction.UP)
                || at.canBeReplaced() && at.isFlammable(level, top, net.minecraft.core.Direction.UP)) {
            if (net.minecraft.world.level.block.BaseFireBlock.canBePlacedAt(level, top, net.minecraft.core.Direction.UP)) {
                level.setBlockAndUpdate(top, net.minecraft.world.level.block.BaseFireBlock.getState(level, top));
            }
        }
    }

    public static void reset() {
        COUNTS.clear();
        BURNING.clear();
    }

    private FireTracker() {}
}
