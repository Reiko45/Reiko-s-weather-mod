package com.reiko.weather.landform;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.weather.LocalWeather;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.WeatherAdvancements;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.WeatherType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;

/**
 * Vernal pools: shallow pools in hollows on flat ground, full in spring and dry by midsummer. With no stream in or
 * out, they fill from the autumn rains and the melting snow and slowly evaporate as the weather warms, and because they
 * dry out every year no fish can live in them. That makes them a safe nursery for frogs, salamanders and fairy shrimp,
 * which time their lives to the water: they breed in the spring pools, and are grown and gone (or buried in the mud as
 * eggs) before the hollow is a patch of cracked mud again.
 *
 * @param y     the ground in the middle
 * @param pools {x, z, radius, depth, water y} for each pool
 */
public record VernalPools(int cx, int cz, int y, int[] pools, long seed) implements Landform {
    private static final int F = 5, SPREAD = 26;
    /** How far out (as a fraction of a pool's radius) its banks reach. */
    private static final float BANK = 1.8F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 2 || !c.fits(ground, 12, 14) || c.relief(24, 10) > 8) {
            return null;
        }
        int n = 3 + c.random().nextInt(5);
        int[] pools = new int[n * F];
        int placed = 0;
        for (int attempt = 0; attempt < 30 && placed < n; attempt++) {
            float a = c.random().nextFloat() * Mth.TWO_PI, d = Mth.sqrt(c.random().nextFloat()) * SPREAD;
            int x = c.x() + Math.round(Mth.cos(a) * d), z = c.z() + Math.round(Mth.sin(a) * d);
            int r = 2 + c.random().nextInt(5);
            // far enough apart that each has banks of its own all the way round
            boolean clear = true;
            for (int k = 0; k < placed && clear; k++) {
                float dd = Mth.sqrt(Mth.square(x - pools[k * F]) + Mth.square(z - pools[k * F + 1]));
                if (dd < (r + pools[k * F + 2]) * 1.2F * BANK + 2.0F) clear = false;
            }
            if (!clear) continue;
            int g = c.groundAt(x, z);
            if (g < c.seaLevel() + 1 || Math.abs(g - ground) > 8) continue;
            pools[placed * F] = x;
            pools[placed * F + 1] = z;
            pools[placed * F + 2] = r;
            pools[placed * F + 3] = 1 + c.random().nextInt(2);
            pools[placed * F + 4] = g - 1; // sunk a little into the ground
            placed++;
        }
        if (placed < 3) {
            return null;
        }
        return new VernalPools(c.x(), c.z(), ground, java.util.Arrays.copyOf(pools, placed * F), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new VernalPools(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getIntArray("pools"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.put("pools", new IntArrayTag(pools));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.VERNAL_POOLS;
    }

    @Override
    public BoundingBox bounds() {
        int r = SPREAD + Math.round(6 * 1.1F * BANK) + 3;
        return new BoundingBox(cx - r, y - 20, cz - r, cx + r, y + 22, cz + r);
    }

    private int count() {
        return pools.length / F;
    }

    /** How far into pool {@code i} a column is, as a fraction of its (wavering) radius. */
    private float into(int i, int x, int z) {
        float d = Mth.sqrt(Mth.square(x + 0.5F - pools[i * F] - 0.5F) + Mth.square(z + 0.5F - pools[i * F + 1] - 0.5F));
        return d / (pools[i * F + 2] * (0.9F + 0.2F * Terrain.field(seed + i, x, z, 4.0)));
    }

    /** The floor of pool {@code i} at a column inside it: deepest in the middle, a block down at the edge. */
    private int floor(int i, int x, int z) {
        float pd = into(i, x, z);
        int depth = pools[i * F + 3] > 1 && pd < 0.6F ? 2 : 1;
        return pools[i * F + 4] - depth;
    }

    /** Whether a column is in pool {@code i}'s hollow (where the water stands when it's full). */
    private boolean inPool(int i, int x, int z) {
        return into(i, x, z) < 1.0F;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                int pool = -1;
                float pd = Float.MAX_VALUE;
                for (int i = 0; i < count(); i++) {
                    float d = into(i, x, z);
                    if (d < pd) {
                        pd = d;
                        pool = i;
                    }
                }
                if (pool < 0 || pd >= BANK) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 3 || ground > b.maxY() - 12) continue;
                // any water standing here already is taken away (the pool keeps to its own level)
                int surface = ground;
                for (pos.set(x, ground + 1, z); surface < ground + 6 && !level.getFluidState(pos).isEmpty(); pos.move(Direction.UP)) surface++;
                int water = pools[pool * F + 4];
                float n = Terrain.noise(seed, x, water, z), m = Terrain.noise(seed + 1, x, water, z);
                if (pd < 1.0F) {
                    int f = floor(pool, x, z);
                    Terrain.clearAbove(level, pos, x, Math.max(surface, water), z, Math.min(12, b.maxY() - Math.max(surface, water)));
                    for (int h = water + 1; h <= surface; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    for (int h = Math.min(ground + 1, f - 1); h < f; h++) Terrain.set(level, pos, x, h, z, Blocks.DIRT.defaultBlockState());
                    // a bed of soft, dark mud: the dry-season floor that the eggs wait out the summer in
                    Terrain.set(level, pos, x, f, z, Blocks.MUD.defaultBlockState());
                    for (int h = f + 1; h <= water; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                    if (lily(x, z, pd)) Terrain.set(level, pos, x, water + 1, z, Blocks.LILY_PAD.defaultBlockState());
                    continue;
                }
                // the banks, never lower than a block over the water, easing back into the ground round about
                float t = (pd - 1.0F) / (BANK - 1.0F);
                int bank = Math.max(water + 1, Math.round(Mth.lerp(t, water + 1, ground)));
                if (bank == ground && surface == ground && pd > 1.5F) continue;
                Terrain.clearAbove(level, pos, x, Math.max(surface, bank), z, Math.min(12, b.maxY() - Math.max(surface, bank)));
                for (int h = bank + 1; h <= surface; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                for (int h = ground + 1; h < bank; h++) Terrain.set(level, pos, x, h, z, Blocks.DIRT.defaultBlockState());
                if (bank != ground) Terrain.set(level, pos, x, bank - 1, z, Blocks.DIRT.defaultBlockState());
                // a soft edge of moss, ferns and grass, lushest by the water
                boolean moss = n < (pd < 1.3F ? 0.65F : 0.25F);
                Terrain.set(level, pos, x, bank, z, moss ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
                if (m < 0.35F) {
                    Terrain.set(level, pos, x, bank + 1, z, Blocks.FERN.defaultBlockState());
                } else if (m < 0.6F) {
                    Terrain.set(level, pos, x, bank + 1, z, moss && m < 0.45F ? Blocks.MOSS_CARPET.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
                }
            }
        }
    }

    private boolean lily(int x, int z, float pd) {
        return pd > 0.3F && Terrain.noise(seed + 2, x, 0, z) < 0.12F;
    }

    // ------------------------------------------------------------------ the seasons

    /**
     * About once a second while a player is near: in the summer heat the pools dry out, a strip at a time, leaving
     * cracked mud; with the autumn rains, the winter wet and the spring thaw they fill again to their old level.
     */
    @Override
    public void tick(ServerLevel level, ServerPlayer player) {
        Season season = WeatherManager.seasonInfo().season();
        if (season == Season.SPRING) {
            for (int i = 0; i < count(); i++) {
                if (into(i, player.getBlockX(), player.getBlockZ()) < BANK + 0.4F && Math.abs(player.getBlockY() - pools[i * F + 4]) < 4 && full(level, i)) {
                    WeatherAdvancements.award(player, "vernal_pool");
                }
            }
        }
        if (!WeatherConfig.CHANGING_LANDFORMS.get() || count() == 0) {
            return;
        }
        BlockPos centre = new BlockPos(cx, y + 1, cz);
        if (!level.isLoaded(centre)) {
            return;
        }
        LocalWeather local = WeatherManager.local(level, centre);
        boolean wet = local != null && local.type().vanillaRain && local.profile().rain > 0.3F;
        boolean hot = local != null && (local.profile().heat || local.type() == WeatherType.HEATWAVE);
        boolean drying, filling;
        if (season == null) {
            // no seasons: they just follow the weather, filling in the rain and drying out in a heatwave
            drying = hot && !wet;
            filling = wet;
        } else {
            drying = season == Season.SUMMER && !wet;
            filling = season == Season.AUTUMN && wet || season == Season.WINTER || season == Season.SPRING;
        }
        RandomSource random = level.random;
        if (drying) {
            int chance = hot ? 2 : level.isDay() ? 6 : 15;
            if (random.nextInt(chance) == 0) dry(level, random.nextInt(count()));
        } else if (filling) {
            int chance = wet ? 3 : 10;
            if (random.nextInt(chance) == 0) fill(level, random.nextInt(count()));
        }
    }

    /** The square round pool {@code i} that holds its hollow: {min x, min z, max x, max z}. */
    private int[] square(int i) {
        int reach = Mth.ceil(pools[i * F + 2] * 1.1F) + 1;
        return new int[]{pools[i * F] - reach, pools[i * F + 1] - reach, pools[i * F] + reach, pools[i * F + 1] + reach};
    }

    private boolean loaded(ServerLevel level, int[] sq) {
        for (int x = sq[0]; x <= sq[2] + 15; x += 16) {
            for (int z = sq[1]; z <= sq[3] + 15; z += 16) {
                if (!level.isLoaded(new BlockPos(Math.min(x, sq[2]), y, Math.min(z, sq[3])))) return false;
            }
        }
        return true;
    }

    private static boolean source(ServerLevel level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        return fluid.isSourceOfType(Fluids.WATER) && level.getBlockState(pos).is(Blocks.WATER);
    }

    /** Whether every block of pool {@code i}'s surface is water. */
    private boolean full(ServerLevel level, int i) {
        int[] sq = square(i);
        if (!loaded(level, sq)) return false;
        int water = pools[i * F + 4];
        for (int x = sq[0]; x <= sq[2]; x++) {
            for (int z = sq[1]; z <= sq[3]; z++) {
                if (inPool(i, x, z) && !source(level, new BlockPos(x, water, z))) return false;
            }
        }
        return true;
    }

    /**
     * Dries out the next strip of pool {@code i} (a row across it, swept from one side): its water, its lily pads, and
     * its mud baked hard and cracked. A whole row goes at once, so no gap is left between two sources for the water to
     * pour back into.
     */
    private void dry(ServerLevel level, int i) {
        int[] sq = square(i);
        if (!loaded(level, sq)) return;
        int water = pools[i * F + 4];
        for (int x = sq[0]; x <= sq[2]; x++) {
            boolean any = false;
            for (int z = sq[1]; z <= sq[3] && !any; z++) {
                if (!inPool(i, x, z)) continue;
                // (only sources count: water running into a strip already dried comes from the next one along)
                for (int h = floor(i, x, z) + 1; h <= water && !any; h++) any = source(level, new BlockPos(x, h, z));
            }
            if (!any) continue;
            for (int z = sq[1]; z <= sq[3]; z++) {
                if (!inPool(i, x, z)) continue;
                int f = floor(i, x, z);
                BlockPos lily = new BlockPos(x, water + 1, z);
                if (level.getBlockState(lily).is(Blocks.LILY_PAD)) level.setBlock(lily, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                for (int h = water; h > f; h--) {
                    BlockPos at = new BlockPos(x, h, z);
                    if (level.getBlockState(at).is(Blocks.WATER)) level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
                BlockPos bed = new BlockPos(x, f, z);
                if (level.getBlockState(bed).is(Blocks.MUD)) level.setBlock(bed, ModBlocks.CRACKED_MUD.get().defaultBlockState(), Block.UPDATE_ALL);
            }
            return;
        }
    }

    /** Whether water at a block of pool {@code i}'s hollow could only go where it should: every side of it is walled or in the pool too. */
    private boolean walled(ServerLevel level, int i, int x, int h, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int nx = x + d.getStepX(), nz = z + d.getStepZ();
            if (inPool(i, nx, nz) && floor(i, nx, nz) < h) continue;
            BlockPos at = new BlockPos(nx, h, nz);
            BlockState state = level.getBlockState(at);
            if (!state.getFluidState().isEmpty()) continue;
            if (!state.blocksMotion() || state.getBlock() instanceof LiquidBlockContainer) return false;
        }
        return true;
    }

    /**
     * Fills the next strip of pool {@code i} back up to its old level (never higher), but only if the whole hollow is
     * still as it was built: its floor and banks unbroken, so the water can't run off anywhere.
     */
    private void fill(ServerLevel level, int i) {
        int[] sq = square(i);
        if (!loaded(level, sq)) return;
        int water = pools[i * F + 4];
        for (int x = sq[0]; x <= sq[2]; x++) {
            for (int z = sq[1]; z <= sq[3]; z++) {
                if (!inPool(i, x, z)) continue;
                int f = floor(i, x, z);
                BlockState bed = level.getBlockState(new BlockPos(x, f, z));
                if (!bed.blocksMotion()) return;
                for (int h = f + 1; h <= water; h++) {
                    BlockState state = level.getBlockState(new BlockPos(x, h, z));
                    // something's been put in the hollow (a dusting of snow, or a plant, doesn't count)
                    if (!state.isAir() && !state.is(Blocks.WATER) && !(state.canBeReplaced() && state.getFluidState().isEmpty())) return;
                    if (!walled(level, i, x, h, z)) return;
                }
            }
        }
        for (int x = sq[0]; x <= sq[2]; x++) {
            boolean missing = false;
            for (int z = sq[1]; z <= sq[3] && !missing; z++) {
                if (inPool(i, x, z)) missing = !source(level, new BlockPos(x, water, z)) || !source(level, new BlockPos(x, floor(i, x, z) + 1, z));
            }
            if (!missing) continue;
            for (int z = sq[1]; z <= sq[3]; z++) {
                if (!inPool(i, x, z)) continue;
                int f = floor(i, x, z);
                BlockPos bed = new BlockPos(x, f, z);
                if (level.getBlockState(bed).is(ModBlocks.CRACKED_MUD.get())) level.setBlock(bed, Blocks.MUD.defaultBlockState(), Block.UPDATE_ALL);
                for (int h = f + 1; h <= water; h++) {
                    BlockPos at = new BlockPos(x, h, z);
                    if (!source(level, at)) level.setBlock(at, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                }
                BlockPos above = new BlockPos(x, water + 1, z);
                if (lily(x, z, into(i, x, z)) && level.getBlockState(above).isAir()) level.setBlock(above, Blocks.LILY_PAD.defaultBlockState(), Block.UPDATE_ALL);
            }
            return;
        }
    }
}
