package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A spotted lake (like Kliluk in British Columbia): a lake so rich in minerals (Epsom salt, calcium and sodium
 * sulphates and a dozen more) that in summer, as the water evaporates, it shrinks into scores of separate pools
 * walled off by pale ridges of the minerals crystallising out of it. Each pool is a little different in what it
 * holds, so each is its own colour: milky blue, turquoise, jade, yellow-green, even pink.
 *
 * @param y     the level of the dried lake bed, and the water in every pool
 * @param pools {x, z, radius x 10, colour, depth} for each pool
 */
public record SpottedLake(int cx, int cz, int y, int radius, int[] pools, long seed) implements Landform {
    /** How far out the shore reaches, and the ring beyond it where standing water is sealed in. */
    private static final float SHORE = 1.45F, SEAL = 1.56F;
    @SuppressWarnings("unchecked")
    private static final ResourceKey<Biome>[] COLOURS = new ResourceKey[]{Terrain.GLACIAL_LAKE_BIOME, Terrain.ACID_LAKE_BIOME,
            Terrain.ACID_POOL_BIOME, Terrain.PINK_LAKE_BIOME};
    /** What lines the bottom of a pool of each colour. */
    private static final BlockState[] BEDS = {Blocks.CALCITE.defaultBlockState(), Blocks.CYAN_TERRACOTTA.defaultBlockState(),
            Blocks.YELLOW_TERRACOTTA.defaultBlockState(), Blocks.PINK_TERRACOTTA.defaultBlockState()};

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        int radius = 20 + c.random().nextInt(8);
        if (ground < c.seaLevel() + 1 || !c.fits(ground, 10, 20)) {
            return null;
        }
        // the bed lies a block under the lowest point of its rim
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int i = 0; i < 12; i++) {
            float a = i * Mth.TWO_PI / 12.0F;
            int h = c.groundAt(c.x() + Math.round(Mth.cos(a) * (radius + 1)), c.z() + Math.round(Mth.sin(a) * (radius + 1)));
            low = Math.min(low, h);
            high = Math.max(high, h);
        }
        if (low < c.seaLevel() || high - low > 10) {
            return null;
        }
        // as many pools as will fit, each kept well inside the bed, with a walkway of crust round every one
        int want = 30 + c.random().nextInt(21);
        int[] pools = new int[want * 5];
        int placed = 0;
        float room = radius * 0.86F - 2.0F;
        for (int attempt = 0; attempt < 900 && placed < want; attempt++) {
            // mostly small pools, with the odd big one
            float r = 1.0F + 3.0F * (float) Math.pow(c.random().nextFloat(), 2.5);
            float a = c.random().nextFloat() * Mth.TWO_PI, d = Mth.sqrt(c.random().nextFloat()) * (room - r * 1.1F);
            if (d < 0.0F) continue;
            int x = c.x() + Math.round(Mth.cos(a) * d), z = c.z() + Math.round(Mth.sin(a) * d);
            boolean clear = (float) Mth.length(x - c.x(), z - c.z()) + r * 1.1F <= room;
            for (int k = 0; k < placed && clear; k++) {
                float gap = (float) Mth.length(x - pools[k * 5], z - pools[k * 5 + 1]);
                if (gap < 1.1F * (r + pools[k * 5 + 2] / 10.0F) + 1.3F) clear = false;
            }
            if (!clear) continue;
            float pick = c.random().nextFloat();
            pools[placed * 5] = x;
            pools[placed * 5 + 1] = z;
            pools[placed * 5 + 2] = Math.round(r * 10.0F);
            pools[placed * 5 + 3] = pick < 0.25F ? 0 : pick < 0.55F ? 1 : pick < 0.85F ? 2 : 3;
            pools[placed * 5 + 4] = r > 2.0F && c.random().nextBoolean() ? 2 : 1;
            placed++;
        }
        if (placed < 12) {
            return null;
        }
        return new SpottedLake(c.x(), c.z(), low - 1, radius, java.util.Arrays.copyOf(pools, placed * 5), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new SpottedLake(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getIntArray("pools"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.put("pools", new IntArrayTag(pools));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.SPOTTED_LAKE;
    }

    @Override
    public BoundingBox bounds() {
        int r = Mth.ceil(radius * SEAL / 0.9F) + 2;
        return new BoundingBox(cx - r, y - 10, cz - r, cx + r, y + 26, cz + r);
    }

    /** How far out from the middle a point is: under 1 on the dried lake bed. */
    private float lake(double x, double z) {
        return (float) Mth.length(x - cx, z - cz) / (radius * (0.9F + 0.2F * Terrain.field(seed, x, z, 10.0)));
    }

    /** The top of a column {@code d} out from the middle once it's built, given its ground. */
    private int shore(float d, int ground) {
        if (d < 1.0F) return y;
        return d < SHORE ? Math.round(Mth.lerp(Terrain.smooth((d - 1.0F) / (SHORE - 1.0F)), y, ground)) : ground;
    }

    /**
     * Turns to dirt any water left standing on a column the lake leaves alone, as high as the shore beside it is cut
     * down below it, so none of it can pour out over the new ground.
     */
    private void seal(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, int ground) {
        int top = ground;
        while (top < b.maxY() && !level.getFluidState(pos.set(x, top + 1, z)).isEmpty()) top++;
        int low = top;
        for (int[] s : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) low = Math.min(low, shore(lake(x + s[0] + 0.5, z + s[1] + 0.5), top));
        for (int h = Math.max(ground, low) + 1; h <= top; h++) Terrain.set(level, pos, x, h, z, Blocks.DIRT.defaultBlockState());
    }

    /** The pool nearest a point (by how far it is from its edge) and that distance, as {index, distance}. */
    private float[] nearest(double x, double z) {
        int best = -1;
        float edge = Float.MAX_VALUE;
        for (int i = 0; i < pools.length / 5; i++) {
            float r = pools[i * 5 + 2] / 10.0F * (0.9F + 0.2F * Terrain.field(seed + i, x, z, 3.0));
            float d = (float) Mth.length(x - pools[i * 5] - 0.5, z - pools[i * 5 + 1] - 0.5) - r;
            if (d < edge) {
                edge = d;
                best = i;
            }
        }
        return new float[]{best, edge};
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean wet = false;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float d = lake(x + 0.5, z + 0.5);
                if (d >= SEAL) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground > b.maxY() - 14) continue;
                if (d >= SHORE) {
                    seal(level, pos, b, x, z, ground);
                    continue;
                }
                // (every column of the bed is built, however deep the ground under it, so no pool can find a way out)
                if (ground < b.minY() + 4 && d >= 1.0F) continue;
                // water in the way is filled in first: any on the bed, a puddle on the shore (deeper water there is sealed in)
                int pond = 0;
                while (pond < (d < 1.0F ? 24 : 4) && !level.getFluidState(pos.set(x, ground + 1 + pond, z)).isEmpty()) pond++;
                if (pond >= 4 && d >= 1.0F || ground + pond > b.maxY() - 14) {
                    seal(level, pos, b, x, z, ground);
                    continue;
                }
                for (int h = Math.max(b.minY(), ground + 1); h <= ground + pond; h++) Terrain.set(level, pos, x, h, z, Blocks.DIRT.defaultBlockState());
                pos.set(x, ground, z);
                BlockState natural = level.getBlockState(pos);
                ground += pond;
                float n = Terrain.noise(seed, x, y, z);
                if (d < 1.0F) {
                    // the dried lake bed: dead flat, with a pool or a pale mineral walkway on every block of it
                    Terrain.clearAbove(level, pos, x, ground, z, Math.min(12, b.maxY() - ground));
                    for (int h = y + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    for (int h = Math.max(b.minY(), Math.min(ground + 1, y - 3)); h < y; h++) Terrain.set(level, pos, x, h, z, Blocks.CALCITE.defaultBlockState());
                    float[] p = nearest(x + 0.5, z + 0.5);
                    int i = (int) p[0];
                    if (i >= 0 && p[1] < 0.0F) {
                        wet = true;
                        int depth = pools[i * 5 + 4];
                        Terrain.set(level, pos, x, y - depth, z, BEDS[pools[i * 5 + 3]]);
                        for (int h = y - depth + 1; h <= y; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                        continue;
                    }
                    float crust = Terrain.field(seed + 1, x, z, 3.0);
                    BlockState walk = crust < 0.4F ? Blocks.CALCITE.defaultBlockState() : crust < 0.7F ? ModBlocks.SALT_CRUST.get().defaultBlockState()
                            : Blocks.WHITE_TERRACOTTA.defaultBlockState();
                    Terrain.set(level, pos, x, y, z, walk);
                    continue;
                }
                // the shore, easing up (or down) from the lake bed into the land round about
                int top = shore(d, ground);
                if (top == ground) continue;
                Terrain.clearAbove(level, pos, x, ground, z, Math.min(12, b.maxY() - ground));
                for (int h = top + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                boolean soil = natural.is(BlockTags.DIRT) || natural.isAir() || !natural.getFluidState().isEmpty();
                BlockState fill = soil ? Blocks.DIRT.defaultBlockState() : natural.is(BlockTags.SAND) ? Blocks.SANDSTONE.defaultBlockState() : natural;
                for (int h = Math.min(ground, top) - 1; h < top; h++) Terrain.set(level, pos, x, h, z, fill);
                BlockState surface = soil ? natural.is(Blocks.GRASS_BLOCK) || natural.isAir() ? Blocks.GRASS_BLOCK.defaultBlockState() : natural : natural;
                if (d < 1.12F && n < 0.5F) surface = Blocks.WHITE_TERRACOTTA.defaultBlockState(); // a rime of minerals round the old shoreline
                Terrain.set(level, pos, x, top, z, surface);
            }
        }
        if (!wet) return;
        // every pool holds its own brew of minerals, and so has its own colour
        for (int k = 0; k < COLOURS.length; k++) {
            int colour = k;
            Terrain.setBiome(level, area, y - 4, y + 3, (x, z) -> {
                if (lake(x + 0.5, z + 0.5) >= 1.0F) return false;
                int i = (int) nearest(x + 0.5, z + 0.5)[0];
                return i >= 0 && pools[i * 5 + 3] == colour;
            }, COLOURS[k]);
        }
    }
}
