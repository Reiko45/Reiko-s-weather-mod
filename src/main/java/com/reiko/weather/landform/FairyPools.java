package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.util.function.BiPredicate;

/**
 * Fairy pools, like those on the Isle of Skye: a mountain stream stepping down a hillside through a chain of small, deep,
 * astonishingly clear pools. The stream runs off bare, hard rock with hardly any soil or peat above it to stain it, so
 * its water is as clear as glass and shows the stones on the bottom in shades of blue and green. Where it crosses a
 * tougher band of rock it drops over a little fall, and below each fall the churning water and the stones it carries have
 * scoured out a round pool; the rock steps between the pools are worn smooth and furred with moss. Here and there the
 * stream has worn a channel right under a band of rock, leaving a natural arch over a pool.
 *
 * @param angle the way the stream runs (down the slope)
 * @param pools {u (in tenths of a block, along the stream from the middle), radius (in tenths), depth, water} for each
 *              pool, from the top of the stream down
 * @param top   the highest ground about (for the bounds)
 * @param arch  which pool has a rock arch over it (-1 for none)
 */
public record FairyPools(int cx, int cz, float angle, int[] pools, int top, int arch, long seed) implements Landform {
    /** What each column is: untouched, a bank, a lip of rock between basins, a little basin in the channel, or a pool. */
    private static final int NONE = 0, BANK = 1, LIP = 2, BASIN = 3, POOL = 4;
    /** Half the width of the stream's channel between the pools, and how far the banks reach beyond the water. */
    private static final float CHANNEL = 1.2F, BANKS = 2.5F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 4 || !c.fits(ground, 20, 30)) {
            return null;
        }
        float[] down = c.downhill(20);
        if (down[1] < 2) {
            return null;
        }
        float a = down[0];
        int half = 20 + c.random().nextInt(9);
        // a steady slope down the line of the stream: falling all the way, never rising, and never dropping off a cliff
        int[] p = new int[5];
        for (int k = 0; k < 5; k++) p[k] = k == 2 ? ground : at(c, a, -half + k * half / 2.0F, 0.0F);
        int drop = p[0] - p[4];
        if (drop < 8 || drop > 25 || p[4] < c.seaLevel() + 3) {
            return null;
        }
        for (int k = 0; k < 4; k++) {
            if (p[k] - p[k + 1] < -1 || p[k] - p[k + 1] > drop * 0.5F + 3) return null;
        }
        // a stream runs down a hollow in the slope, not along the crest of a spur
        int left = at(c, a, 0.0F, 10.0F), right = at(c, a, 0.0F, -10.0F);
        if (Math.min(left, right) < ground - 2) {
            return null;
        }
        // the pools, evenly along the stream, each a little below the ground where it lies and below the one above it
        int n = Math.min(4 + c.random().nextInt(5), 2 * half / 6);
        float spacing = 2.0F * half / n;
        int[] pools = new int[n * 4];
        int prev = Integer.MAX_VALUE, arch = -1;
        float widest = 0.0F;
        for (int i = 0; i < n; i++) {
            float u = -half + spacing * (i + 0.5F) + (c.random().nextFloat() - 0.5F);
            float r = Math.min(1.5F + c.random().nextFloat() * 2.0F, spacing / 2.0F - 1.5F);
            float t = (u + half) / (half / 2.0F);
            int k = Mth.clamp((int) t, 0, 3);
            float g = Mth.lerp(t - k, p[k], p[k + 1]);
            // (and never more than a few blocks below the one above: where the ground drops away it's built up instead)
            int water = Math.max(Math.min(Math.round(g) - 1 - (r > 2.5F ? 1 : 0), prev - 1), prev == Integer.MAX_VALUE ? Integer.MIN_VALUE : prev - 4);
            pools[i * 4] = Math.round(u * 10.0F);
            pools[i * 4 + 1] = Math.round(r * 10.0F);
            pools[i * 4 + 2] = Math.min(2 + c.random().nextInt(4), Math.round(r) + 2);
            pools[i * 4 + 3] = water;
            prev = water;
            if (r >= 2.5F && r > widest) {
                widest = r;
                arch = i;
            }
        }
        if (prev < c.seaLevel() + 1) {
            return null;
        }
        if (c.random().nextFloat() < 0.4F) arch = -1;
        int top = Math.max(Math.max(p[0], left), right);
        return new FairyPools(c.x(), c.z(), a, pools, top, arch, c.random().nextLong());
    }

    /** The ground {@code u} blocks down the slope and {@code v} across it from here. */
    private static int at(PlanContext c, float angle, float u, float v) {
        double[] p = Terrain.xz(u, v, c.x() + 0.5, c.z() + 0.5, angle);
        return c.groundAt(Mth.floor(p[0]), Mth.floor(p[1]));
    }

    static Landform load(CompoundTag tag) {
        return new FairyPools(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getIntArray("pools"), tag.getInt("top"), tag.getInt("arch"),
                tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.put("pools", new IntArrayTag(pools));
        tag.putInt("top", top);
        tag.putInt("arch", arch);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.FAIRY_POOLS;
    }

    private int count() {
        return pools.length / 4;
    }

    private float pu(int i) {
        return pools[i * 4] / 10.0F;
    }

    private float radius(int i) {
        return pools[i * 4 + 1] / 10.0F;
    }

    private int water(int i) {
        return pools[i * 4 + 3];
    }

    /** How far along the stream a pool can reach either side of its middle (with its edge at its most ragged). */
    private float reach(int i) {
        return radius(i) * 1.12F;
    }

    @Override
    public BoundingBox bounds() {
        int n = count(), low = Integer.MAX_VALUE;
        for (int i = 0; i < n; i++) low = Math.min(low, water(i) - pools[i * 4 + 2]);
        float u0 = pu(0) - reach(0) - BANKS - 2.0F, u1 = pu(n - 1) + reach(n - 1) + BANKS + 2.0F, w = amplitude() + 3.5F * 1.12F + BANKS + 2.0F;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (float u : new float[]{u0, u1}) {
            for (float v : new float[]{-w, w}) {
                double[] p = Terrain.xz(u, v, cx + 0.5, cz + 0.5, angle);
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minZ = Math.min(minZ, p[1]);
                maxZ = Math.max(maxZ, p[1]);
            }
        }
        return new BoundingBox(Mth.floor(minX) - 1, low - 8, Mth.floor(minZ) - 1, Mth.floor(maxX) + 1, top + 18, Mth.floor(maxZ) + 1);
    }

    private float amplitude() {
        return 1.0F + 2.0F * Terrain.hash(seed, 1);
    }

    /** Where the stream's line wanders across the slope, {@code u} blocks along it. */
    private float meander(float u) {
        return amplitude() * Mth.sin(u * (0.12F + 0.08F * Terrain.hash(seed, 2)) + Terrain.hash(seed, 3) * Mth.TWO_PI);
    }

    /** How far into pool {@code i} a spot is (under 1 inside it). */
    private float inPool(int i, int x, int z, float u, float v) {
        float du = u - pu(i), dv = v - meander(pu(i));
        return Mth.sqrt(du * du + dv * dv) / (radius(i) * (0.88F + 0.24F * Terrain.field(seed + 5 + i, x, z, 2.5)));
    }

    /** How far the water drops from the pool above into pool {@code i}. */
    private int fall(int i) {
        return i <= 0 || i >= count() ? 0 : water(i - 1) - water(i);
    }

    /**
     * The stream's level at a point along it: {level, what it is (0 a lip, 1 a basin, 2 within a pool's reach), the pool}.
     * Where the next pool lies a block below, the stream steps down to it over a lip of rock; where it lies further below,
     * the stream runs on level from the pool above in a little channel and pours over the end of it, a small waterfall
     * into the next. It never rises going downstream.
     */
    private int[] step(float u) {
        int n = count();
        for (int i = 0; i < n; i++) {
            if (u > pu(i) + reach(i)) continue;
            if (u >= pu(i) - reach(i)) return new int[]{water(i), 2, i};
            if (i == 0) return new int[]{water(i), 0, -1};
            int hi = water(i - 1);
            return fall(i) >= 2 ? new int[]{hi, 1, -1} : new int[]{hi, 0, -1};
        }
        return new int[]{water(n - 1), 0, -1};
    }

    /** The highest water that can be beside a column of the banks, {@code u} along the stream (or far below, if none can). */
    private int waterNear(float u) {
        int most = Integer.MIN_VALUE / 2;
        for (float t = u - 1.25F; t <= u + 1.25F; t += 0.25F) {
            int[] s = step(t);
            if (s[1] > 0) most = Math.max(most, s[0]);
        }
        return most;
    }

    /** Whether a point along the stream is between its first pool and its last (where it has a channel at all). */
    private boolean onStream(float u, float margin) {
        return u > pu(0) - reach(0) - margin && u < pu(count() - 1) + reach(count() - 1) + margin;
    }

    /**
     * What a column is: {kind, level, pool}. For a pool or basin the level is its water; for a lip, the top of its rock
     * (level with the water above it); for a bank, the least height it's built up to, a block over any water beside it.
     */
    private int[] classify(int x, int z, float u, float v) {
        int n = count();
        float near = Float.MAX_VALUE;
        int nearest = -1;
        for (int i = 0; i < n; i++) {
            if (Math.abs(u - pu(i)) > reach(i) + BANKS + 1.0F) continue;
            float d = inPool(i, x, z, u, v);
            if (d < 1.0F) return new int[]{POOL, water(i), i};
            if (d * radius(i) - radius(i) < near) {
                near = d * radius(i) - radius(i);
                nearest = i;
            }
        }
        float off = Math.abs(v - meander(u));
        if (off < CHANNEL && onStream(u, 0.0F)) {
            int[] s = step(u);
            if (s[1] == 2) {
                // in a pool's reach but outside the pool itself: where a fall comes down into it, the head of the pool
                // reaches up the channel to meet it; where the stream runs on level to a fall, the channel leads out of it
                int i = s[2];
                if (u < pu(i) && fall(i) >= 2) return new int[]{POOL, s[0], i};
                if (u >= pu(i) && fall(i + 1) >= 2) return new int[]{BASIN, s[0], -1};
                return new int[]{LIP, s[0], -1};
            }
            return new int[]{s[1] == 1 ? BASIN : LIP, s[0], -1};
        }
        if (nearest >= 0 && near < BANKS || off < CHANNEL + BANKS && onStream(u, BANKS)) {
            return new int[]{BANK, waterNear(u) + 1, -1};
        }
        return new int[]{NONE, 0, -1};
    }

    private int[] classify(int x, int z) {
        float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
        return classify(x, z, l[0], l[1]);
    }

    /**
     * Whether water standing in a column that can't hold it pours over the edge instead: where every column beside it is
     * solid or water at its level, except for at least one that is a pool's water two or more blocks below, which the
     * water spilling over will fall straight down into.
     */
    private boolean spout(int x, int z, int level) {
        boolean pours = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int nx = x + d.getStepX(), nz = z + d.getStepZ();
            int[] k = classify(nx, nz);
            if (k[0] == NONE) return false;
            if (k[1] >= level) continue;
            if (k[0] != POOL || k[1] > level - 2 || !held(nx, nz, k[1])) return false;
            pours = true;
        }
        return pours;
    }

    /**
     * Whether a pool or basin's water can stand in a column: only where every column beside it is certain to be solid or
     * water at its level (a bank or lip at least as high, or water at least as high, which, if higher, will itself be a lip).
     * Where it can't, the column is a lip of rock instead (or, at the head of a fall, it pours over).
     */
    private boolean held(int x, int z, int level) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int[] k = classify(x + d.getStepX(), z + d.getStepZ());
            if (k[0] == NONE || k[1] < level) return false;
        }
        return true;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // (a bank left alone, far above or below the stream, keeps any pond in it)
        BiPredicate<Integer, Integer> outside = (nx, nz) -> {
            int[] k = classify(nx, nz);
            return k[0] == NONE || k[0] == BANK && skipped(Math.max(Goosenecks.ground(level, new BlockPos.MutableBlockPos(), nx, nz), k[1]), b);
        };
        boolean wetAny = false;
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int i = 0; i < count(); i++) {
            low = Math.min(low, water(i) - pools[i * 4 + 2]);
            high = Math.max(high, water(i));
        }
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
                float u = l[0], v = l[1];
                int[] k = classify(x, z, u, v);
                if (k[0] == NONE) continue;
                int ground = Goosenecks.ground(level, pos, x, z);
                int standing = Math.min(b.maxY(), Goosenecks.standing(level, pos, x, ground, z));
                pos.set(x, ground, z);
                BlockState was = level.getBlockState(pos);
                float n = Terrain.noise(seed, x, k[1], z);
                int lvl = k[1];
                // sound rock under it all, with no hollow in it for the water to find
                for (int h = Math.max(b.minY(), lvl - 8); h <= Math.min(ground, lvl); h++) {
                    BlockState here = level.getBlockState(pos.set(x, h, z));
                    if (here.isAir() || !here.getFluidState().isEmpty()) Terrain.set(level, pos, x, h, z, rock(x, h, z));
                }
                int clear = Math.max(0, Math.min(16, b.maxY() - Math.max(ground, standing)));
                if (k[0] == BANK) {
                    bank(level, pos, b, x, z, ground, standing, lvl, clear, was, n, outside);
                    arch(level, pos, x, z, u, v, Math.max(ground, lvl));
                    continue;
                }
                boolean wet = k[0] >= BASIN && held(x, z, lvl), pour = k[0] >= BASIN && !wet && spout(x, z, lvl);
                int depth = 0;
                if (pour) {
                    wet = true;
                    depth = 1;
                } else if (wet) {
                    depth = 1;
                    if (k[0] == POOL) {
                        // (the head of a pool, where a fall comes down into it, is scoured out a little deeper)
                        float d = inPool(k[2], x, z, u, v);
                        depth = d >= 1.0F ? 2 : 1 + Math.round((pools[k[2] * 4 + 2] - 1) * Mth.sqrt(Math.max(0.0F, 1.0F - d * d)));
                    }
                }
                int bed = lvl - depth, open = lvl;
                Terrain.clearAbove(level, pos, x, Math.max(ground, standing), z, clear);
                for (int h = open + 1; h <= Math.min(b.maxY(), Math.max(ground, standing)); h++) {
                    Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, outside) ? rock(x, h, z) : Blocks.AIR.defaultBlockState());
                }
                // solid rock up to the bed (or the lip's top), worn smooth and mossy where the water runs over it
                for (int h = Math.max(b.minY(), Math.min(ground + 1, bed - 2)); h <= bed; h++) {
                    Terrain.set(level, pos, x, h, z, h == bed ? wet ? bed(n) : lip(x, h, z) : rock(x, h, z));
                }
                if (wet) {
                    wetAny = true;
                    for (int h = bed + 1; h <= lvl; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                    if (pour) level.scheduleTick(pos.set(x, lvl, z).immutable(), Fluids.WATER, 1); // set it pouring over the edge
                }
                arch(level, pos, x, z, u, v, lvl);
            }
        }
        // the water's crystal-clear blue
        if (wetAny) {
            Terrain.setBiome(level, area, low - 4, high + 3, (x, z) -> classify(x, z)[0] >= LIP, Terrain.CLEAR_WATER_BIOME);
        }
    }

    /** The banks: bare rock where the stream has cut down through the turf, built up a block over the water where the slope falls away. */
    private void bank(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, int ground, int standing, int least, int clear,
                      BlockState was, float n, BiPredicate<Integer, Integer> outside) {
        int top = Math.max(ground, least);
        if (skipped(top, b)) return; // (high above the stream, or far below it: nothing to do)
        Terrain.clearAbove(level, pos, x, Math.max(ground, standing), z, clear);
        for (int h = top + 1; h <= standing; h++) {
            Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, outside) ? rock(x, h, z) : Blocks.AIR.defaultBlockState());
        }
        boolean turf = was.is(BlockTags.DIRT) && n > 0.3F;
        // (rock all down the face the water shows, as deep as a pool beside it goes)
        int from = least > Integer.MIN_VALUE / 4 ? Math.min(ground + 1, least - 7) : ground - 4;
        for (int h = Math.max(b.minY(), from); h < top; h++) Terrain.set(level, pos, x, h, z, rock(x, h, z));
        BlockState surface = turf ? top > ground ? Blocks.MOSS_BLOCK.defaultBlockState() : was
                : n < 0.15F ? Blocks.MOSS_BLOCK.defaultBlockState() : n < 0.3F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : rock(x, top, z);
        Terrain.set(level, pos, x, top, z, surface);
        if (!surface.is(BlockTags.DIRT)) return;
        float m = Terrain.noise(seed + 9, x, top, z);
        if (m < 0.22F) {
            Terrain.set(level, pos, x, top + 1, z, m < 0.12F ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
        } else if (surface.is(Blocks.MOSS_BLOCK) && m < 0.4F) {
            Terrain.set(level, pos, x, top + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
        }
    }

    private static boolean skipped(int top, BoundingBox b) {
        return top > b.maxY() - 2 || top < b.minY() + 1;
    }

    /** The rock of the hillside: grey stone with bands of andesite and diorite. */
    private BlockState rock(int x, int y, int z) {
        float band = Terrain.field(seed + 7, x * 0.6 + y * 1.3, z * 0.6 - y * 0.4, 3.0);
        return band < 0.25F ? Blocks.ANDESITE.defaultBlockState() : band > 0.8F ? Blocks.DIORITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    /** The top of a lip of rock the water runs over: smooth stone, furred with moss. */
    private BlockState lip(int x, int y, int z) {
        float n = Terrain.noise(seed + 8, x, y, z);
        return n < 0.4F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : n < 0.55F ? Blocks.MOSS_BLOCK.defaultBlockState() : rock(x, y, z);
    }

    /** The bottom of a pool: clean stones and gravel, seen through the clear water. */
    private static BlockState bed(float n) {
        return n < 0.3F ? Blocks.GRAVEL.defaultBlockState() : n < 0.5F ? Blocks.STONE.defaultBlockState() : n < 0.65F ? Blocks.DIORITE.defaultBlockState()
                : n < 0.8F ? Blocks.ANDESITE.defaultBlockState() : n < 0.92F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
    }

    /** The natural arch over one of the pools: a band of rock the stream has worn its way under. */
    private void arch(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, float u, float v, int top) {
        if (arch < 0) return;
        float r = radius(arch), au = pu(arch) + (Terrain.hash(seed, 4) - 0.5F) * r * 0.6F, span = r + 2.5F;
        float across = v - meander(pu(arch));
        if (Math.abs(u - au) >= 1.0F || Math.abs(across) >= span) return;
        int w = water(arch);
        int under = w + 1 + Math.round(2.5F * Mth.sqrt(1.0F - Mth.square(across / span)));
        // over the water the arch is a band of rock; beyond it, it stands on the banks
        int from = Math.abs(across) > r + 0.5F ? top + 1 : under;
        for (int h = from; h <= under + 1; h++) {
            Terrain.set(level, pos, x, h, z, h == under + 1 && Terrain.noise(seed + 10, x, h, z) < 0.4F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : rock(x, h, z));
        }
    }
}
