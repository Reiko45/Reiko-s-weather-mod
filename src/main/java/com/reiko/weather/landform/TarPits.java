package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * Tar pits, like Rancho La Brea in Los Angeles, Pitch Lake in Trinidad or the bitumen seeps of the Middle East: natural
 * asphalt oozing up through cracks from oil-bearing rock below and collecting at the surface in sticky black pools. As
 * the oil creeps up along faults its lighter parts evaporate, leaving thick, sticky bitumen that wells up into hollows in
 * the ground and spreads; round the edges it hardens in the sun into a cracked black crust, and it stains the ground
 * round about. Rainwater lies on some of the pools in a thin skin that hides the tar under it, and for tens of thousands
 * of years animals that came down to drink waded in, stuck fast and died there, and the wolves, big cats and vultures
 * that came to feed on them were caught in their turn. The pools are full of their bones.
 *
 * @param lo    the lowest and highest ground sampled (for the bounds)
 * @param pools {x, z, radius x 10, the height of the crust at its edge, depth of tar, rainwater on it (1) or not (0)} for each pool
 * @param bones {x, z, which way it lies (0 east-west, 1 north-south), length of its spine, extras} for each skeleton,
 *              the extras being a rib on either side (1, 2), a skull (4) and a bone sticking up (8)
 * @param seeps {x, z} for each little seep of tar between the pools
 */
public record TarPits(int cx, int cz, int lo, int hi, int[] pools, int[] bones, int[] seeps, long seed) implements Landform {
    /** Numbers stored for each pool and each skeleton. */
    private static final int F = 6, B = 5;
    /** How far from the middle the pools are scattered, and how far beyond each one's crust the stain on the ground reaches. */
    private static final int SPREAD = 26, STAIN = 3;
    /** The survey of the ground round about: every {@value STEP} blocks, {@value GRID} points each way. */
    private static final int STEP = 8, GRID = 9;
    /** The most relief allowed over the level stretch the pools are scattered round, and under any one pool. */
    private static final int RELIEF = 10, LOCAL = 4;
    /** How far in and out a pool's wavering edge strays from its radius, as a fraction of it. */
    private static final float NARROW = 0.75F, WIDE = 1.25F;
    /** How far up a tree or plant is cleared from a column that's dug out or built up. */
    private static final int CLEAR = 16;

    @Nullable
    static Landform plan(PlanContext c) {
        int sea = c.seaLevel(), ground = c.groundAt(c.x(), c.z());
        if (ground < sea + 1 || !c.fits(ground, 16, 40)) {
            return null;
        }
        // the lie of the land round about, every 8 blocks out to 32 each way
        int mid = GRID / 2;
        int[][] g = new int[GRID][GRID];
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) g[i][j] = c.groundAt(c.x() + STEP * (i - mid), c.z() + STEP * (j - mid));
        }
        // the most level stretch of dry ground nearby: of the squares 32 blocks across round each of the middle 25 points of
        // the survey, the one with the least relief (the nearest the middle, if two tie). A lone knoll or hollow doesn't
        // count against it, as the pools each find their own level patch of ground.
        int best = Integer.MAX_VALUE, bi = mid, bj = mid, lo = ground, hi = ground;
        int[] square = new int[25];
        for (int i = 2; i < GRID - 2; i++) {
            for (int j = 2; j < GRID - 2; j++) {
                for (int a = -2; a <= 2; a++) {
                    for (int b = -2; b <= 2; b++) square[(a + 2) * 5 + b + 2] = g[i + a][j + b];
                }
                Arrays.sort(square);
                int relief = square[23] - square[1], score = relief * 16 + Math.abs(i - mid) + Math.abs(j - mid);
                if (square[0] >= sea + 1 && relief <= RELIEF && score < best) {
                    best = score;
                    bi = i;
                    bj = j;
                    lo = square[0];
                    hi = square[24];
                }
            }
        }
        if (best == Integer.MAX_VALUE) {
            return null;
        }
        int ox = c.x() + STEP * (bi - mid), oz = c.z() + STEP * (bj - mid);
        // the pools, each on a patch of ground level enough to give it a flat floor
        int want = 4 + c.random().nextInt(5), placed = 0, spent = 0;
        int[] pools = new int[want * F];
        for (int attempt = 0; attempt < 40 && placed < want && spent < 100; attempt++) {
            // mostly small and middling pools, with the odd big one
            float r = 1.2F + 4.8F * (float) Math.pow(c.random().nextFloat(), 1.6);
            float a = c.random().nextFloat() * Mth.TWO_PI, d = Mth.sqrt(c.random().nextFloat()) * SPREAD;
            int x = ox + Math.round(Mth.cos(a) * d), z = oz + Math.round(Mth.sin(a) * d), r10 = Math.round(r * 10.0F);
            // far enough from the others that each has a crust of its own all the way round
            boolean clear = true;
            for (int k = 0; k < placed && clear; k++) {
                float gap = Mth.sqrt(Mth.square(x - pools[k * F]) + Mth.square(z - pools[k * F + 1]));
                if (gap < reach(r10) + reach(pools[k * F + 2]) + 1.0F) clear = false;
            }
            if (!clear) continue;
            int centre = c.groundAt(x, z), min = centre, max = centre;
            int[] ring = new int[5];
            float turn = c.random().nextFloat();
            for (int q = 0; q < 5; q++) {
                float b = (q + turn) * Mth.TWO_PI / 5.0F, out = WIDE * r + 1.0F;
                ring[q] = c.groundAt(x + Math.round(Mth.cos(b) * out), z + Math.round(Mth.sin(b) * out));
                min = Math.min(min, ring[q]);
                max = Math.max(max, ring[q]);
            }
            spent += 6;
            if (max - min > LOCAL || min < sea + 1) continue;
            // the crust round it stands at the second lowest point of its rim (a single dip is simply built up), the tar a block lower
            Arrays.sort(ring);
            pools[placed * F] = x;
            pools[placed * F + 1] = z;
            pools[placed * F + 2] = r10;
            pools[placed * F + 3] = ring[1];
            pools[placed * F + 4] = r < 2.0F ? 1 + c.random().nextInt(2) : 2 + c.random().nextInt(2);
            pools[placed * F + 5] = r >= 3.0F && c.random().nextFloat() < 0.6F ? 1 : 0;
            lo = Math.min(lo, min);
            hi = Math.max(hi, max);
            placed++;
        }
        if (placed < 3) {
            return null;
        }
        // (if any pool is big enough to hold a skin of rainwater, at least one does)
        int biggest = -1;
        boolean wet = false;
        for (int i = 0; i < placed; i++) {
            wet |= pools[i * F + 5] == 1;
            if (pools[i * F + 2] >= 30 && (biggest < 0 || pools[i * F + 2] > pools[biggest * F + 2])) biggest = i;
        }
        if (!wet && biggest >= 0) pools[biggest * F + 5] = 1;
        // the skeletons of animals caught long ago: none in most small pools, a couple at most in a big one
        int[] bones = new int[placed * 2 * B];
        int nb = 0;
        for (int i = 0; i < placed; i++) {
            float r = pools[i * F + 2] / 10.0F;
            float roll = c.random().nextFloat();
            int groups = r < 2.5F ? (roll < 0.4F ? 1 : 0) : r < 4.5F ? (roll < 0.75F ? 1 : 0) : roll < 0.65F ? 1 : 2;
            for (int k = 0; k < groups; k++) {
                for (int attempt = 0; attempt < 4; attempt++) {
                    float a = c.random().nextFloat() * Mth.TWO_PI, d = r * (0.2F + 0.9F * c.random().nextFloat());
                    int x = pools[i * F] + Math.round(Mth.cos(a) * d), z = pools[i * F + 1] + Math.round(Mth.sin(a) * d);
                    int axis = c.random().nextInt(2), len = 2 + c.random().nextInt(2);
                    int extras = (c.random().nextFloat() < 0.4F ? 1 : 0) | (c.random().nextFloat() < 0.4F ? 2 : 0)
                            | (c.random().nextFloat() < 0.4F ? 4 : 0) | (c.random().nextFloat() < 0.3F ? 8 : 0);
                    bones[nb * B] = x;
                    bones[nb * B + 1] = z;
                    bones[nb * B + 2] = axis;
                    bones[nb * B + 3] = len;
                    bones[nb * B + 4] = extras;
                    if (lies(bones, nb, pools, i)) {
                        nb++;
                        break;
                    }
                }
            }
        }
        // and little seeps of tar out of cracks in the ground between the pools
        int ns = 3 + c.random().nextInt(5), s = 0;
        int[] seeps = new int[ns * 2];
        for (int attempt = 0; attempt < 30 && s < ns; attempt++) {
            float a = c.random().nextFloat() * Mth.TWO_PI, d = Mth.sqrt(c.random().nextFloat()) * (SPREAD + 6);
            int x = ox + Math.round(Mth.cos(a) * d), z = oz + Math.round(Mth.sin(a) * d);
            boolean clear = true;
            for (int k = 0; k < placed && clear; k++) {
                if (Mth.sqrt(Mth.square(x - pools[k * F]) + Mth.square(z - pools[k * F + 1])) < reach(pools[k * F + 2]) + 2.5F) clear = false;
            }
            for (int k = 0; k < s && clear; k++) {
                if (Mth.square(x - seeps[k * 2]) + Mth.square(z - seeps[k * 2 + 1]) < 25) clear = false;
            }
            if (!clear) continue;
            seeps[s * 2] = x;
            seeps[s * 2 + 1] = z;
            s++;
        }
        return new TarPits(ox, oz, lo, hi, Arrays.copyOf(pools, placed * F), Arrays.copyOf(bones, nb * B), Arrays.copyOf(seeps, s * 2),
                c.random().nextLong());
    }

    /**
     * Whether skeleton {@code k} lies well within pool {@code i} and its crust (however its edge wavers), clear of every
     * skeleton before it.
     */
    private static boolean lies(int[] bones, int k, int[] pools, int i) {
        float r = pools[i * F + 2] / 10.0F;
        for (int u = -1; u <= 3; u++) {
            for (int v = -1; v <= 1; v++) {
                int x = bones[k * B + 2] == 0 ? bones[k * B] + u : bones[k * B] + v, z = bones[k * B + 2] == 0 ? bones[k * B + 1] + v : bones[k * B + 1] + u;
                if (part(bones, k, x, z) == 0) continue;
                if (Mth.sqrt(Mth.square(x - pools[i * F]) + Mth.square(z - pools[i * F + 1])) - NARROW * r >= crustWidth(pools[i * F + 2]) - 1) return false;
                for (int q = 0; q < k; q++) {
                    if (part(bones, q, x, z) != 0) return false;
                }
            }
        }
        return true;
    }

    static Landform load(CompoundTag tag) {
        return new TarPits(tag.getInt("x"), tag.getInt("z"), tag.getInt("lo"), tag.getInt("hi"), tag.getIntArray("pools"), tag.getIntArray("bones"),
                tag.getIntArray("seeps"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("lo", lo);
        tag.putInt("hi", hi);
        tag.put("pools", new IntArrayTag(pools));
        tag.put("bones", new IntArrayTag(bones));
        tag.put("seeps", new IntArrayTag(seeps));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.TAR_PITS;
    }

    @Override
    public BoundingBox bounds() {
        int x0 = cx, z0 = cz, x1 = cx, z1 = cz;
        for (int i = 0; i < pools.length / F; i++) {
            int r = Mth.ceil(reach(pools[i * F + 2])) + STAIN + 1;
            x0 = Math.min(x0, pools[i * F] - r);
            z0 = Math.min(z0, pools[i * F + 1] - r);
            x1 = Math.max(x1, pools[i * F] + r);
            z1 = Math.max(z1, pools[i * F + 1] + r);
        }
        for (int k = 0; k < seeps.length / 2; k++) {
            x0 = Math.min(x0, seeps[k * 2] - 2);
            z0 = Math.min(z0, seeps[k * 2 + 1] - 2);
            x1 = Math.max(x1, seeps[k * 2] + 2);
            z1 = Math.max(z1, seeps[k * 2 + 1] + 2);
        }
        return new BoundingBox(x0, lo - 10, z0, x1, hi + 28, z1);
    }

    // ------------------------------------------------------------------ the shape of the pools

    /** How far out from the edge of a pool of a given radius (x 10) its crust reaches. */
    private static int crustWidth(int r10) {
        return 2 + r10 / 20;
    }

    /** How far out from its middle a pool's crust can reach. */
    private static float reach(int r10) {
        return WIDE * r10 / 10.0F + crustWidth(r10);
    }

    private float radius(int i) {
        return pools[i * F + 2] / 10.0F;
    }

    private int width(int i) {
        return crustWidth(pools[i * F + 2]);
    }

    private float distance(int i, int x, int z) {
        return Mth.sqrt(Mth.square(x - pools[i * F]) + Mth.square(z - pools[i * F + 1]));
    }

    /** Pool {@code i}'s radius at a column: round, but with a lumpy, irregular edge. */
    private float edgeRadius(int i, int x, int z) {
        float r = radius(i);
        return r * (NARROW + 0.35F * Terrain.field(seed + 31L * i, x, z, Math.max(2.5F, r * 0.9F)) + 0.15F * Terrain.field(seed + 31L * i + 1, x, z, 1.7));
    }

    /** How far a column is outside pool {@code i}'s edge, in blocks (below 0 inside it). */
    private float edge(int i, int x, int z) {
        float d = distance(i, x, z), r = radius(i);
        // (too far out for the wavering of the edge to matter)
        if (d > WIDE * r + width(i) + STAIN + 2) return d - WIDE * r;
        return d - edgeRadius(i, x, z);
    }

    /** How far into pool {@code i} a column is, as a fraction of its radius there. */
    private float into(int i, int x, int z) {
        return distance(i, x, z) / edgeRadius(i, x, z);
    }

    /** Whether a column is clear of every pool and its crust (so it's never dug out or built up). */
    private boolean outside(int x, int z) {
        for (int i = 0; i < pools.length / F; i++) {
            if (edge(i, x, z) < width(i)) return false;
        }
        return true;
    }

    /**
     * Whether rainwater lies on pool {@code i} at a column: on the pools that hold it, well in from the edge, and only
     * with more of the pool on every side, so the water is held in by tar (or more water) all round.
     */
    private boolean wet(int i, int x, int z) {
        if (pools[i * F + 5] == 0 || into(i, x, z) >= 1.0F - 1.3F / radius(i)) return false;
        return edge(i, x + 1, z) < 0.0F && edge(i, x - 1, z) < 0.0F && edge(i, x, z + 1) < 0.0F && edge(i, x, z - 1) < 0.0F;
    }

    /** Whether a column is a seep (0), the ground just round one (1), or neither (-1). */
    private int seepAt(int x, int z) {
        int found = -1;
        for (int k = 0; k < seeps.length / 2; k++) {
            int dx = x - seeps[k * 2], dz = z - seeps[k * 2 + 1];
            if (dx == 0 && dz == 0) return 0;
            if (dx * dx + dz * dz <= 2) found = 1;
        }
        return found;
    }

    /**
     * What skeleton {@code k} has at a column: 0 nothing, 1 a bone of its spine, 2 a rib, 3 the end of its spine with the
     * skull lying on it, 4 the other end with a bone sticking up out of it.
     */
    private static int part(int[] bones, int k, int x, int z) {
        int axis = bones[k * B + 2], len = bones[k * B + 3], extras = bones[k * B + 4];
        int u = axis == 0 ? x - bones[k * B] : z - bones[k * B + 1], v = axis == 0 ? z - bones[k * B + 1] : x - bones[k * B];
        if (v == 0 && u >= 0 && u < len) return u == len - 1 && (extras & 4) != 0 ? 3 : u == 0 && (extras & 8) != 0 ? 4 : 1;
        if (u == len / 2 && (v == -1 && (extras & 1) != 0 || v == 1 && (extras & 2) != 0)) return 2;
        return 0;
    }

    /** The bone at a column's surface and what lies on top of it (or null), or null where there's no skeleton. */
    @Nullable
    private BlockState[] skeleton(int x, int z) {
        for (int k = 0; k < bones.length / B; k++) {
            int p = part(bones, k, x, z);
            if (p == 0) continue;
            Direction.Axis along = bones[k * B + 2] == 0 ? Direction.Axis.X : Direction.Axis.Z;
            Direction.Axis across = along == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            BlockState bone = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, p == 2 ? across : along);
            BlockState on = p == 3 ? Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, Mth.floor(Terrain.hash(seed, 100 + k) * 16.0F) & 15)
                    : p == 4 ? Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y) : null;
            return new BlockState[]{bone, on};
        }
        return null;
    }

    // ------------------------------------------------------------------ building

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                // the pool whose ground this is (no two pools' crusts ever meet), and how far out from its edge
                int k = -1;
                float e = Float.MAX_VALUE, out = Float.MAX_VALUE;
                for (int i = 0; i < pools.length / F; i++) {
                    float ei = edge(i, x, z);
                    if (ei - width(i) < out) {
                        out = ei - width(i);
                        e = ei;
                        k = i;
                    }
                }
                int seep = seepAt(x, z);
                if (k < 0 || out >= STAIN && seep < 0) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (e < 0.0F) {
                    // (every column of a pool is built, however odd the ground under it, so no water can find a way out)
                    pool(level, pos, b, k, x, z, ground);
                    continue;
                }
                if (ground < b.minY() + 4 || ground > b.maxY() - 20) continue;
                if (out < 0.0F) {
                    crust(level, pos, b, k, x, z, ground, e);
                } else if (seep >= 0) {
                    seep(level, pos, x, z, ground, seep == 0);
                } else {
                    stain(level, pos, k, x, z, ground, e);
                }
            }
        }
    }

    /**
     * A column of pool {@code i}: tar a block to three deep on a solid floor, a block below the crust round it, with a
     * skin of rainwater lying on it in the middle of the pools that hold one.
     */
    private void pool(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int i, int x, int z, int ground) {
        int top = pools[i * F + 3] - 1, depth = pools[i * F + 4];
        boolean water = pools[i * F + 5] == 1, wet = wet(i, x, z);
        float pd = into(i, x, z);
        // deepest in the middle; on a pool with water on it, the tar under the water is a block lower than round its edge
        int thick = Mth.clamp(Math.round(depth * Mth.sqrt(Math.max(0.0F, 1.0F - pd * pd)) + 0.3F), 1, depth);
        int bottom = (water ? top - 1 : top) - thick + 1;
        int surface = Terrain.surface(level, pos, x, ground, z), old = Math.min(surface, b.maxY() - 1);
        BlockState fill = fill(level.getBlockState(pos.set(x, ground, z)));
        Terrain.clearAbove(level, pos, x, Math.max(old, top), z, Math.min(CLEAR, b.maxY() - Math.max(old, top)));
        // the floor of the hollow, built up where the ground dips, and never air or water under the tar
        for (int h = Math.max(b.minY(), ground); h < bottom; h++) Terrain.set(level, pos, x, h, z, fill);
        if (bottom <= ground) {
            BlockState under = level.getBlockState(pos.set(x, bottom - 1, z));
            if (!under.blocksMotion() || !under.getFluidState().isEmpty()) Terrain.set(level, pos, x, bottom - 1, z, fill);
        }
        for (int h = bottom; h <= top; h++) {
            Terrain.set(level, pos, x, h, z, h == top && wet ? Blocks.WATER.defaultBlockState() : ModBlocks.TAR.get().defaultBlockState());
        }
        for (int h = top + 1; h <= old; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
        // the bones of something caught here, sunk in the tar with only their tops showing
        BlockState[] bone = skeleton(x, z);
        if (bone != null) {
            Terrain.set(level, pos, x, top, z, bone[0]);
            if (bone[1] != null) Terrain.set(level, pos, x, top + 1, z, bone[1]);
        }
    }

    /**
     * A column of the ground round pool {@code i}, {@code e} blocks out from its edge: a block above the tar at the
     * edge, easing back into the lie of the land, with a rim of hard asphalt crust and tar-stained ground in patches
     * further out.
     */
    private void crust(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int i, int x, int z, int ground, float e) {
        int w = width(i), surface = Terrain.surface(level, pos, x, ground, z);
        // (a puddle standing here is filled in, so the ground eases out to the top of it)
        int bank = Mth.clamp(Math.round(Mth.lerp(Terrain.smooth(e / w), pools[i * F + 3], surface)), b.minY() + 2, b.maxY() - 18);
        boolean stained = stained(i, x, z, e);
        BlockState[] bone = skeleton(x, z);
        if (bank == ground && surface == ground && !stained && bone == null) return;
        // where it's cut down, never below water standing just outside
        for (int h = surface; h > bank; h--) {
            if (Terrain.pondBeside(level, pos, x, h, z, this::outside)) {
                bank = h;
                break;
            }
        }
        BlockState natural = level.getBlockState(pos.set(x, ground, z)), fill = fill(natural);
        Terrain.clearAbove(level, pos, x, Math.max(surface, bank), z, Math.min(CLEAR, b.maxY() - Math.max(surface, bank)));
        for (int h = ground; h < bank; h++) Terrain.set(level, pos, x, h, z, fill);
        for (int h = bank + 1; h <= surface; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
        BlockState top = stained ? crustOf(i, x, z, e) : natural.getFluidState().isEmpty() && !natural.isAir() ? natural : fill;
        if (top.getBlock() instanceof FallingBlock && !level.getBlockState(pos.set(x, bank - 1, z)).blocksMotion()) top = fill;
        if (bone != null) {
            // bones poking out of the crust
            Terrain.set(level, pos, x, bank, z, bone[0]);
            if (bone[1] != null) Terrain.set(level, pos, x, bank + 1, z, bone[1]);
            return;
        }
        Terrain.set(level, pos, x, bank, z, top);
        if (bush(top, x, bank, z)) Terrain.set(level, pos, x, bank + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
    }

    /** A column beyond pool {@code i}'s crust, where the tar has only stained the ground here and there. */
    private void stain(WorldGenLevel level, BlockPos.MutableBlockPos pos, int i, int x, int z, int ground, float e) {
        if (!stained(i, x, z, e)) return;
        BlockState natural = level.getBlockState(pos.set(x, ground, z)), above = level.getBlockState(pos.set(x, ground + 1, z));
        // only soil, sand or clay takes the stain, in the open (not under water, nor where a cactus needs its sand)
        if (!soft(natural) || !above.getFluidState().isEmpty() || above.is(Blocks.CACTUS)) return;
        BlockState top = Blocks.COARSE_DIRT.defaultBlockState();
        Terrain.set(level, pos, x, ground, z, top);
        if (above.isAir() && bush(top, x, ground, z)) Terrain.set(level, pos, x, ground + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
    }

    /** A seep ({@code crack}), a single block of tar oozing up through a crack in the ground, or the ground stained round one. */
    private void seep(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, boolean crack) {
        BlockState natural = level.getBlockState(pos.set(x, ground, z)), above = level.getBlockState(pos.set(x, ground + 1, z));
        if (!above.getFluidState().isEmpty() || above.is(BlockTags.LOGS) || above.is(Blocks.CACTUS) || above.is(Blocks.BAMBOO) || above.is(Blocks.SUGAR_CANE)) {
            return;
        }
        if (crack) {
            Terrain.clearAbove(level, pos, x, ground, z, 2);
            BlockState under = level.getBlockState(pos.set(x, ground - 1, z));
            if (!under.blocksMotion() || !under.getFluidState().isEmpty()) Terrain.set(level, pos, x, ground - 1, z, fill(natural));
            Terrain.set(level, pos, x, ground, z, ModBlocks.TAR.get().defaultBlockState());
            return;
        }
        if (!soft(natural) || Terrain.noise(seed + 10, x, ground, z) > 0.7F) return;
        boolean hard = Terrain.noise(seed + 11, x, ground, z) < 0.4F;
        if (hard) Terrain.clearAbove(level, pos, x, ground, z, 2);
        Terrain.set(level, pos, x, ground, z, hard ? ModBlocks.ASPHALT.get().defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
    }

    /** Whether a column out round pool {@code i} is crusted or stained with tar: all of it by the edge, patches further out. */
    private boolean stained(int i, int x, int z, float e) {
        if (e < 1.5F) return true;
        float fade = e / (width(i) + STAIN);
        return 0.75F * Terrain.field(seed + 5, x, z, 3.0) + 0.25F * Terrain.noise(seed + 6, x, 0, z) < 0.95F - 1.05F * fade;
    }

    /** What the ground round pool {@code i} is crusted with: mostly hard asphalt by the tar, tar-soaked earth further out. */
    private BlockState crustOf(int i, int x, int z, float e) {
        float n = 0.6F * Terrain.field(seed + 7, x, z, 2.0) + 0.4F * Terrain.noise(seed + 8, x, 1, z);
        float hard = e < 1.5F ? 0.85F : 0.6F - 0.6F * e / (width(i) + STAIN);
        return n < hard ? ModBlocks.ASPHALT.get().defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
    }

    /** Whether a dead bush grows on a block of the stained ground (only ever on earth, sand or clay). */
    private boolean bush(BlockState top, int x, int y, int z) {
        return (top.is(Blocks.COARSE_DIRT) || top.is(BlockTags.SAND) || top.is(BlockTags.TERRACOTTA)) && Terrain.noise(seed + 9, x, y, z) < 0.05F;
    }

    /** Soft ground that soaks up a stain of tar: soil, sand and clay. */
    private static boolean soft(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.TERRACOTTA);
    }

    /** What to build up the ground with under a column whose top was {@code natural} (never anything that falls). */
    private static BlockState fill(BlockState natural) {
        if (natural.is(Blocks.RED_SAND)) return Blocks.RED_SANDSTONE.defaultBlockState();
        if (natural.is(BlockTags.SAND)) return Blocks.SANDSTONE.defaultBlockState();
        if (natural.is(BlockTags.TERRACOTTA) || natural.is(BlockTags.BASE_STONE_OVERWORLD) || natural.is(Blocks.SANDSTONE) || natural.is(Blocks.RED_SANDSTONE)) {
            return natural;
        }
        return Blocks.DIRT.defaultBlockState();
    }
}
