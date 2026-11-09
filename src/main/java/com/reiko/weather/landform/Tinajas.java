package com.reiko.weather.landform;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.weather.LocalWeather;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.WeatherType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;

import javax.annotation.Nullable;

/**
 * Tinajas (potholes, or "water pockets"): round basins worn into bare slickrock, where the scant rain of the
 * desert runs off the stone and collects. Each began as a dimple where water lingered and dissolved the cement
 * holding the sand grains together; the wind blew the loose grains away, and every storm since has scoured it a
 * little deeper. After rain they brim with water, the only drink for miles, and in the heat they slowly dry out
 * again.
 *
 * @param low   the lowest ground sampled (for the bounds)
 * @param high  the highest ground sampled
 * @param pools {x, z, radius x 10, depth, rim y} for each pothole
 */
public record Tinajas(int cx, int cz, int low, int high, int[] pools, long seed) implements Landform {
    private static final float RADIUS = 26.0F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 3 || !c.fits(ground, 12, 12)) {
            return null;
        }
        int low = ground, high = ground;
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F;
            int g = c.groundAt(c.x() + Math.round(Mth.cos(a) * 22), c.z() + Math.round(Mth.sin(a) * 22));
            low = Math.min(low, g);
            high = Math.max(high, g);
        }
        if (high - low > 10 || low < c.seaLevel() + 1) {
            return null;
        }
        int n = 6 + c.random().nextInt(7);
        int[] pools = new int[n * 5];
        int placed = 0;
        for (int attempt = 0; attempt < 40 && placed < n; attempt++) {
            float a = c.random().nextFloat() * Mth.TWO_PI, d = Mth.sqrt(c.random().nextFloat()) * 21.0F;
            int x = c.x() + Math.round(Mth.cos(a) * d), z = c.z() + Math.round(Mth.sin(a) * d);
            float r = 1.0F + c.random().nextFloat() * 1.5F;
            boolean clear = true;
            // far enough apart that each has a flat rim of its own all the way round
            for (int k = 0; k < placed && clear; k++) {
                float dd = Mth.sqrt(Mth.square(x - pools[k * 5]) + Mth.square(z - pools[k * 5 + 1]));
                if (dd < r + pools[k * 5 + 2] / 10.0F + 5.5F) clear = false;
            }
            if (!clear) continue;
            int rim = c.groundAt(x, z);
            low = Math.min(low, rim);
            high = Math.max(high, rim);
            pools[placed * 5] = x;
            pools[placed * 5 + 1] = z;
            pools[placed * 5 + 2] = Math.round(r * 10.0F);
            pools[placed * 5 + 3] = Math.min(1 + c.random().nextInt(3), 1 + Math.round(r));
            pools[placed * 5 + 4] = rim;
            placed++;
        }
        if (placed < 5) {
            return null;
        }
        return new Tinajas(c.x(), c.z(), low, high, java.util.Arrays.copyOf(pools, placed * 5), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Tinajas(tag.getInt("x"), tag.getInt("z"), tag.getInt("low"), tag.getInt("high"), tag.getIntArray("pools"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("low", low);
        tag.putInt("high", high);
        tag.put("pools", new IntArrayTag(pools));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.TINAJAS;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(RADIUS * 1.15F) + 3;
        return new BoundingBox(cx - r, low - 10, cz - r, cx + r, high + 10, cz + r);
    }

    /** The floor of pothole {@code i} at a column: below its rim inside the basin, the rim itself outside it. */
    private int floor(int i, int x, int z) {
        float r = pools[i * 5 + 2] / 10.0F;
        float d = Mth.sqrt(Mth.square(x + 0.5F - pools[i * 5]) + Mth.square(z + 0.5F - pools[i * 5 + 1]));
        int rim = pools[i * 5 + 4];
        if (d >= r) return rim;
        // a rounded bowl, steepest round the edge
        return rim - Math.round(pools[i * 5 + 3] * Mth.sqrt(1.0F - Mth.square(d / r)));
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float e = (float) Math.sqrt(Mth.square(x + 0.5F - cx) + Mth.square(z + 0.5F - cz)) / (RADIUS * (0.85F + 0.3F * Terrain.field(seed, x, z, 10.0)));
                // the pothole whose rim this column is nearest
                int pool = -1;
                float pd = Float.MAX_VALUE;
                for (int i = 0; i < pools.length / 5; i++) {
                    float d = Mth.sqrt(Mth.square(x + 0.5F - pools[i * 5]) + Mth.square(z + 0.5F - pools[i * 5 + 1])) - pools[i * 5 + 2] / 10.0F;
                    if (d < pd) {
                        pd = d;
                        pool = i;
                    }
                }
                if (e > 1.0F && pd > 5.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 4 || ground > b.maxY() - 4 || !level.getFluidState(pos.set(x, ground + 1, z)).isEmpty()) continue;
                boolean red = level.getBiome(pos.set(x, ground, z)).is(BiomeTags.IS_BADLANDS);
                // the rock round each pothole is flat, so it brims full without spilling
                int top = ground;
                int rim = pools[pool * 5 + 4];
                if (pd < 2.5F) top = rim;
                else if (pd < 5.0F) top = Math.round(Mth.lerp(Terrain.smooth((pd - 2.5F) / 2.5F), rim, ground));
                Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, Math.min(8, b.maxY() - Math.max(ground, top)));
                for (int h = top + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                // bare slickrock: smooth, pale sandstone, streaked along its old cross-bedding
                for (int h = Math.min(Math.min(ground, top) - 2, top - 4); h <= top; h++) {
                    float bed = Terrain.field(seed + 1, x * 0.7 + h * 2.3, z * 0.7 - h * 1.1, 4.0);
                    BlockState s = bed < 0.42F ? red ? Blocks.RED_SANDSTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState()
                            : red ? Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState() : Blocks.SMOOTH_SANDSTONE.defaultBlockState();
                    Terrain.set(level, pos, x, h, z, s);
                }
                float n = Terrain.noise(seed, x, top, z);
                if (pd < 0.0F) {
                    // the pothole itself, dry when the world is made
                    int f = floor(pool, x, z);
                    if (f >= top) continue;
                    for (int h = f + 1; h <= top; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    if (n < 0.55F && pools[pool * 5 + 3] > 1) {
                        Terrain.set(level, pos, x, f, z, red ? Blocks.RED_SAND.defaultBlockState() : Blocks.SAND.defaultBlockState());
                    }
                    continue;
                }
                // drifts of sand in the hollows of the rock, with the odd dead bush
                if (pd > 2.5F && e < 0.95F && Terrain.field(seed + 5, x, z, 6.0) > 0.72F) {
                    Terrain.set(level, pos, x, top, z, red ? Blocks.RED_SAND.defaultBlockState() : Blocks.SAND.defaultBlockState());
                    if (n > 0.93F) Terrain.set(level, pos, x, top + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                }
            }
        }
    }

    /**
     * About once a second while a player is near: rain fills the potholes, a few blocks at a time, and in dry
     * weather they slowly evaporate (faster in a heatwave).
     */
    @Override
    public void tick(ServerLevel level, ServerPlayer player) {
        if (!WeatherConfig.CHANGING_LANDFORMS.get() || pools.length == 0) {
            return;
        }
        BlockPos centre = new BlockPos(cx, high + 1, cz);
        if (!level.isLoaded(centre)) {
            return;
        }
        LocalWeather local = WeatherManager.local(level, centre);
        RandomSource random = level.random;
        int count = pools.length / 5;
        boolean wet = local != null && local.type().vanillaRain && local.profile().rain > 0.3F;
        if (wet) {
            int fills = 3 + Math.round(local.intensity() * 4.0F);
            for (int k = 0; k < fills; k++) {
                int i = random.nextInt(count);
                float r = pools[i * 5 + 2] / 10.0F;
                double a = random.nextDouble() * Math.PI * 2.0, d = Math.sqrt(random.nextDouble()) * r;
                int x = Mth.floor(pools[i * 5] + Math.cos(a) * d), z = Mth.floor(pools[i * 5 + 1] + Math.sin(a) * d);
                fill(level, i, x, z);
            }
            return;
        }
        boolean hot = local != null && (local.profile().heat || local.type() == WeatherType.HEATWAVE);
        // drying out: slowly at night, faster by day, fastest in a heatwave
        int drains = hot ? 3 : level.isDay() ? 1 : random.nextInt(3) == 0 ? 1 : 0;
        for (int k = 0; k < drains; k++) {
            drain(level, random.nextInt(count));
        }
    }

    /**
     * Adds a water source to a pothole at the lowest level of a column that isn't already being fed. The sources
     * are kept apart, with flowing water between them, so they never merge into a permanent pool, and the whole
     * thing can dry out again.
     */
    private void fill(ServerLevel level, int i, int x, int z) {
        int f = floor(i, x, z), rim = pools[i * 5 + 4];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = f + 1; y <= rim; y++) {
            pos.set(x, y, z);
            if (!level.isLoaded(pos)) return;
            BlockState state = level.getBlockState(pos);
            FluidState fluid = state.getFluidState();
            if (!state.isAir() && !(state.is(Blocks.WATER) && !fluid.isSource())) {
                if (state.is(Blocks.WATER)) continue;
                return; // something's been put in the way
            }
            BlockPos at = pos.immutable();
            boolean fed = BlockPos.betweenClosedStream(at.offset(-2, 0, -2), at.offset(2, 0, 2)).anyMatch(p -> level.getFluidState(p).isSource());
            if (fed) continue;
            level.setBlock(at, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
    }

    /** Takes away the highest water source in a pothole: the water it fed drains off by itself. */
    private void drain(ServerLevel level, int i) {
        float r = pools[i * 5 + 2] / 10.0F;
        int px = pools[i * 5], pz = pools[i * 5 + 1], rim = pools[i * 5 + 4];
        int reach = Mth.ceil(r);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = rim; y > rim - pools[i * 5 + 3]; y--) {
            for (int x = px - reach; x <= px + reach; x++) {
                for (int z = pz - reach; z <= pz + reach; z++) {
                    if (floor(i, x, z) >= y) continue;
                    pos.set(x, y, z);
                    if (!level.isLoaded(pos)) return;
                    if (level.getFluidState(pos).isSource() && level.getBlockState(pos).is(Blocks.WATER)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        return;
                    }
                }
            }
        }
    }
}
