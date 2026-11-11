package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.function.BiPredicate;

/**
 * A floodplain: the broad, dead-flat floor of a valley that a river has built for itself out of its own mud. Every
 * time it floods, the water spills over the banks and slows at once, dropping its sand right by the channel; so the
 * banks grow into natural levees, low ridges of sandy soil where willows and oaks take root. Farther out only the
 * finest silt settles, into rich meadow, and lowest of all, out behind the levees, lie the backswamps, where the
 * floodwater stands longest: wet, reedy ground and shallow pools. Here and there a crescent of reeds and still water
 * marks a channel the river has abandoned as it wandered across the plain.
 *
 * @param angle  the way the river runs
 * @param length how far it runs along the river
 * @param width  how far either side of the middle the floodplain reaches
 * @param floor  the height of the meadow (two blocks over the river)
 * @param start  how far aside of the middle line the river comes in at one end
 * @param end    and how far aside it leaves at the other
 * @param scars  {u, v, radius, facing in degrees} for each abandoned channel
 */
public record Floodplain(int cx, int cz, float angle, int length, int width, int floor, int start, int end, int[] scars, long seed) implements Landform {
    private static final int BLEND = 10, S = 4;
    private static final float SWEEP = Mth.PI * 0.55F;

    @Nullable
    static Landform plan(PlanContext c) {
        // a chunk's middle seldom lands in the river itself, so look round about for it (as PlanContext.search does),
        // but only plan from the first spot in a river: trying them all would take too many samples
        for (int r = 0; r <= 12; r += 12) {
            for (int dx = -r; dx <= r; dx += 12) {
                for (int dz = -r; dz <= r; dz += 12) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    PlanContext at = c.at(c.x() + dx, c.z() + dz);
                    int ground = at.groundAt(at.x(), at.z());
                    if (ground <= at.seaLevel() - 2 && ground >= at.seaLevel() - 15) return planAt(at);
                }
            }
        }
        return null;
    }

    /** Plans one around a column in a river. */
    @Nullable
    private static Landform planAt(PlanContext c) {
        int river = c.seaLevel() - 1;
        // the river's course: the way the ground stays low
        float first = c.random().nextFloat() * Mth.PI, best = first;
        int bestHigh = Integer.MAX_VALUE;
        for (int k = 0; k < 6; k++) {
            float a = first + k * Mth.PI / 6.0F;
            int high = Math.max(c.groundAt(c.x() + Math.round(Mth.cos(a) * 28), c.z() + Math.round(Mth.sin(a) * 28)),
                    c.groundAt(c.x() - Math.round(Mth.cos(a) * 28), c.z() - Math.round(Mth.sin(a) * 28)));
            if (high < bestHigh) {
                bestHigh = high;
                best = a;
            }
        }
        if (bestHigh > river) {
            return null;
        }
        int length = 100 + c.random().nextInt(31), width = 36 + c.random().nextInt(9);
        // low, level land either side: a floodplain, not a gorge (or the sea)
        int left = c.groundAt(c.x() - Math.round(Mth.sin(best) * width * 0.6F), c.z() + Math.round(Mth.cos(best) * width * 0.6F));
        int right = c.groundAt(c.x() + Math.round(Mth.sin(best) * width * 0.6F), c.z() - Math.round(Mth.cos(best) * width * 0.6F));
        if (Math.min(left, right) < c.seaLevel() - 1 || Math.max(left, right) > c.seaLevel() + 10) {
            return null;
        }
        // where the river runs in and out at the ends, so the new channel meets it
        int[] ends = new int[2];
        for (int k = 0; k < 2; k++) {
            float u = (k == 0 ? -0.5F : 0.5F) * length;
            int lowest = Integer.MAX_VALUE;
            for (int v = -8; v <= 8; v += 8) {
                double[] p = Terrain.xz(u, v, c.x() + 0.5, c.z() + 0.5, best);
                int h = c.groundAt(Mth.floor(p[0]), Mth.floor(p[1]));
                if (h < lowest) {
                    lowest = h;
                    ends[k] = lowest < river ? v : 0;
                }
            }
        }
        long seed = c.random().nextLong();
        Floodplain probe = new Floodplain(c.x(), c.z(), best, length, width, c.seaLevel() + 1, ends[0], ends[1], new int[0], seed);
        // a couple of old channels the river has left out on the plain, mostly on its wider side
        int[] scars = new int[3 * S];
        int n = 0;
        for (int attempt = 0; attempt < 40 && n < 3; attempt++) {
            int r = 5 + c.random().nextInt(5);
            float u = (c.random().nextFloat() - 0.5F) * (length - 2 * BLEND - 2 * r - 6);
            float side = probe.centre(u) > 3.0F ? -1.0F : probe.centre(u) < -3.0F ? 1.0F : c.random().nextBoolean() ? 1.0F : -1.0F;
            if (c.random().nextFloat() < 0.25F) side = -side;
            float v = probe.centre(u) + side * (13.0F + r * (0.4F + 0.6F * c.random().nextFloat()));
            // the crescent bowed away from the river, or toward it
            int facing = Math.round((side > 0 ? 90.0F : 270.0F) + (c.random().nextBoolean() ? 180.0F : 0.0F) + (c.random().nextFloat() - 0.5F) * 90.0F);
            int[] scar = {Math.round(u), Math.round(v), r, facing};
            if (probe.clear(scar, scars, n)) {
                System.arraycopy(scar, 0, scars, n * S, S);
                n++;
            }
        }
        return new Floodplain(c.x(), c.z(), best, length, width, c.seaLevel() + 1, ends[0], ends[1], java.util.Arrays.copyOf(scars, n * S), seed);
    }

    /** Whether an old channel would lie well out on the plain, clear of the river, the edges and the others. */
    private boolean clear(int[] scar, int[] others, int count) {
        for (int k = 0; k <= 12; k++) {
            float a = scar[3] * Mth.DEG_TO_RAD + (k / 6.0F - 1.0F) * SWEEP;
            float u = scar[0] + Mth.cos(a) * (scar[2] + 3.0F), v = scar[1] + Mth.sin(a) * (scar[2] + 3.0F);
            float u2 = scar[0] + Mth.cos(a) * (scar[2] - 3.0F), v2 = scar[1] + Mth.sin(a) * (scar[2] - 3.0F);
            for (float[] p : new float[][]{{u, v}, {u2, v2}}) {
                if (Math.abs(p[0]) > end(p[0], p[1]) - BLEND - 2 || Math.abs(p[1]) > reach(p[0]) - BLEND - 2 || edge(p[0], p[1]) < 8.5F) return false;
            }
        }
        for (int i = 0; i < count; i++) {
            if (Mth.sqrt(Mth.square(scar[0] - others[i * S]) + Mth.square(scar[1] - others[i * S + 1])) < scar[2] + others[i * S + 2] + 7) return false;
        }
        return true;
    }

    static Landform load(CompoundTag tag) {
        return new Floodplain(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("width"), tag.getInt("floor"),
                tag.getInt("start"), tag.getInt("end"), tag.getIntArray("scars"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putInt("width", width);
        tag.putInt("floor", floor);
        tag.putInt("start", start);
        tag.putInt("end", end);
        tag.put("scars", new IntArrayTag(scars));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.FLOODPLAIN;
    }

    @Override
    public BoundingBox bounds() {
        float half = length / 2.0F, out = width + 6.0F;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (float u : new float[]{-half, half}) {
            for (float v : new float[]{-out, out}) {
                double[] p = Terrain.xz(u, v, cx + 0.5, cz + 0.5, angle);
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minZ = Math.min(minZ, p[1]);
                maxZ = Math.max(maxZ, p[1]);
            }
        }
        return new BoundingBox(Mth.floor(minX) - 1, floor - 10, Mth.floor(minZ) - 1, Mth.floor(maxX) + 1, floor + 30, Mth.floor(maxZ) + 1);
    }

    private int river() {
        return floor - 2;
    }

    /** Where the river's middle is across the plain, {@code u} blocks along it: meandering, and meeting the old river at the ends. */
    private float centre(float u) {
        float half = length / 2.0F;
        float wave = Mth.TWO_PI / (55.0F + 20.0F * Terrain.hash(seed, 2)), amp = 4.0F + 4.0F * Terrain.hash(seed, 1);
        return Mth.lerp(Mth.clamp((u + half) / length, 0.0F, 1.0F), start, end)
                + amp * Terrain.smooth((half - Math.abs(u)) / 22.0F) * Mth.sin(u * wave + Terrain.hash(seed, 3) * Mth.TWO_PI);
    }

    /** How far a point is from the river's edge (negative in the river). */
    private float edge(float u, float v) {
        return Math.abs(v - centre(u)) - (3.4F + 1.4F * Terrain.field(seed + 1, u, 0.0, 20.0));
    }

    /**
     * How far the floodplain reaches either side of its middle, {@code u} blocks along it: lens-shaped, narrowing to
     * where the river runs in and out at the ends, its sides wandering in and out along the valley.
     */
    private float reach(float u) {
        float taper = 0.45F + 0.55F * Terrain.smooth((length / 2.0F - Math.abs(u)) / 28.0F);
        return width * taper + 8.0F * (Terrain.field(seed + 9, u, 0.0, 26.0) - 0.5F);
    }

    /** How far along the plain reaches at {@code v} across it: its ends round off away from the river, which runs right through. */
    private float end(float u, float v) {
        float across = Mth.clamp((v - centre(u)) / Math.max(1.0F, reach(u)), -1.0F, 1.0F);
        return length / 2.0F - 16.0F * across * across;
    }

    private boolean inside(float u, float v) {
        return Math.abs(u) <= end(u, v) && Math.abs(v) <= reach(u);
    }

    /** 0 over the floodplain proper, rising to 1 at its edge, where it eases into the land round it. */
    private float blend(float u, float v) {
        return Mth.clamp(Math.max((Math.abs(u) - (end(u, v) - BLEND)) / BLEND, (Math.abs(v) - (reach(u) - BLEND)) / BLEND), 0.0F, 1.0F);
    }

    /** How far into abandoned channel {@code i} a point is (below 1 inside it), or more than 1 if it's nowhere near. */
    private float scar(int i, float u, float v) {
        float du = u - scars[i * S], dv = v - scars[i * S + 1];
        float a = Mth.wrapDegrees((float) Math.toDegrees(Mth.atan2(dv, du)) - scars[i * S + 3]) * Mth.DEG_TO_RAD;
        float along = Math.abs(a) / SWEEP;
        if (along >= 1.0F) return 2.0F;
        // narrowing to nothing at its silted-up ends
        float half = 2.4F * Mth.sqrt(1.0F - along * along);
        return half < 0.6F ? 2.0F : Math.abs(Mth.sqrt(du * du + dv * dv) - scars[i * S + 2]) / half;
    }

    /**
     * The plan for a column of the floodplain proper (or anywhere along the river): {its top, the level of the water
     * standing on it, or MIN_VALUE if it's dry}. Pools only fill where {@link #wet} says.
     */
    private int[] shape(int x, int z, float u, float v, float e) {
        int w = river();
        if (e < 0.0F) {
            float across = Math.abs(v - centre(u)) / (Math.abs(v - centre(u)) - e);
            return new int[]{w - 1 - Math.round(2.0F * (1.0F - across * across)), w};
        }
        if (e < 1.0F) return new int[]{w, Integer.MIN_VALUE};
        if (e < 2.0F) return new int[]{w + 1, Integer.MIN_VALUE};
        if (e < 3.0F || e >= 6.0F && e < 8.0F) return new int[]{floor, Integer.MIN_VALUE};
        // the levee: a low, broad ridge of sandy soil all along the bank
        if (e < 6.0F) return new int[]{floor + 1 + (Terrain.field(seed + 4, x, z, 7.0) > 0.55F ? 1 : 0), Integer.MIN_VALUE};
        for (int i = 0; i < scars.length / S; i++) {
            float d = scar(i, u, v);
            if (d < 1.0F) return d < 0.6F ? new int[]{floor - 2 - (d < 0.3F ? 1 : 0), floor - 1} : new int[]{floor - 1, Integer.MIN_VALUE};
        }
        // the backswamps, low and wet, out behind the levees
        if (e > 11.0F && Terrain.field(seed + 7, x, z, 16.0) > 0.56F) {
            float pool = Terrain.field(seed + 8, x, z, 5.0);
            return pool > 0.55F ? new int[]{floor - 2 - (pool > 0.75F ? 1 : 0), floor - 1} : new int[]{floor - 1, Integer.MIN_VALUE};
        }
        return new int[]{floor, Integer.MIN_VALUE};
    }

    /** Whether a column is planned to the letter: the floodplain proper, or the river and its banks right to the ends. */
    private boolean planned(float u, float v, float e) {
        return inside(u, v) && (e < 2.0F || blend(u, v) == 0.0F);
    }

    /** The least height a column's ground or water stands at, if it's planned (MIN_VALUE if it's not). */
    private int lowest(int x, int z) {
        float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
        float e = edge(l[0], l[1]);
        if (!planned(l[0], l[1], e)) return Integer.MIN_VALUE;
        int[] s = shape(x, z, l[0], l[1], e);
        return s[1] != Integer.MIN_VALUE ? s[1] : s[0];
    }

    /**
     * Whether a pool out on the plain fills: only where every column beside it is planned and no lower than it (the
     * river itself always runs, between its banks).
     */
    private boolean wet(int x, int z, int[] s, float e) {
        if (s[1] == Integer.MIN_VALUE) return false;
        if (e < 0.0F) return true;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (lowest(x + d.getStepX(), z + d.getStepZ()) < s[1]) return false;
        }
        return true;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int w = river();
        BiPredicate<Integer, Integer> outside = (nx, nz) -> {
            float[] l = Terrain.uv(nx + 0.5, nz + 0.5, cx + 0.5, cz + 0.5, angle);
            return !inside(l[0], l[1]);
        };
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
                float u = l[0], v = l[1];
                if (!inside(u, v)) continue;
                float e = edge(u, v), t = blend(u, v);
                boolean planned = e < 2.0F || t == 0.0F;
                int ground = Goosenecks.ground(level, pos, x, z);
                if (!planned && ground > b.maxY() - 12) continue;
                int standing = Math.min(b.maxY(), Goosenecks.standing(level, pos, x, ground, z));
                // the old river (or a creek) running in across the edge is left as it is, to join the new channel
                if (!planned && ground < w && standing > ground && standing <= w) continue;
                int[] s = planned ? shape(x, z, u, v, e) : new int[]{Math.max(w + 1, Math.round(Mth.lerp(t, shape(x, z, u, v, e)[0], ground))), Integer.MIN_VALUE};
                boolean wet = planned && wet(x, z, s, e);
                int top = wet ? s[0] : s[1] != Integer.MIN_VALUE ? s[1] : s[0];
                int open = wet ? s[1] : top;
                if (!planned && top == ground && standing == ground) continue; // (the edge, where it meets the land unchanged)
                Terrain.clearAbove(level, pos, x, ground, z, Math.min(12, b.maxY() - ground));
                for (int h = open + 1; h <= standing; h++) {
                    // (a pond just outside keeps a bank of earth between it and the lowered ground)
                    Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, outside) ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
                // the ground is made sound as deep as any water goes, so nothing can drain out through a hollow
                for (int h = w - 4; h < top; h++) {
                    BlockState here = level.getBlockState(pos.set(x, h, z));
                    if (h > ground || here.isAir() || !here.getFluidState().isEmpty() || Terrain.cover(here)) {
                        Terrain.set(level, pos, x, h, z, h < w - 1 ? Blocks.CLAY.defaultBlockState() : Blocks.DIRT.defaultBlockState());
                    }
                }
                float n = Terrain.noise(seed, x, top, z), m = Terrain.noise(seed + 1, x, top, z);
                if (wet) {
                    water(level, pos, x, z, s, e, n, outside);
                } else if (e < 2.0F) {
                    bank(level, pos, x, z, top, e, n, m);
                } else if (top < floor) {
                    swamp(level, pos, x, z, top, n, m);
                } else if (e < 8.0F) {
                    levee(level, pos, x, z, top, n);
                } else {
                    meadow(level, pos, x, z, top, n, m);
                }
            }
        }
        trees(level, area, pos);
    }

    /** The river in its channel, or a still pool on the plain: water over sand and mud. */
    private void water(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int[] s, float e, float n, BiPredicate<Integer, Integer> outside) {
        boolean river = e < 0.0F;
        Terrain.set(level, pos, x, s[0] - 1, z, Blocks.CLAY.defaultBlockState());
        Terrain.set(level, pos, x, s[0], z, river ? n < 0.6F ? Blocks.SAND.defaultBlockState() : n < 0.85F ? Blocks.GRAVEL.defaultBlockState()
                : Blocks.CLAY.defaultBlockState() : n < 0.7F ? Blocks.MUD.defaultBlockState() : Blocks.CLAY.defaultBlockState());
        for (int h = s[0] + 1; h <= s[1]; h++) {
            // where the channel meets the land at the very edge (no river beyond it), the end is banked up
            boolean open = true;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int nx = x + d.getStepX(), nz = z + d.getStepZ();
                if (!outside.test(nx, nz)) continue;
                BlockState there = level.getBlockState(pos.set(nx, h, nz));
                if (there.getFluidState().isEmpty() && !there.blocksMotion()) open = false;
            }
            Terrain.set(level, pos, x, h, z, open ? Blocks.WATER.defaultBlockState() : Blocks.DIRT.defaultBlockState());
        }
        if (!river && n < 0.1F && level.getBlockState(pos.set(x, s[1], z)).is(Blocks.WATER)) {
            Terrain.set(level, pos, x, s[1] + 1, z, Blocks.LILY_PAD.defaultBlockState());
        }
    }

    /** The sandy banks of the river, with reeds of cane where they meet the water. */
    private void bank(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int top, float e, float n, float m) {
        Terrain.set(level, pos, x, top - 2, z, Blocks.DIRT.defaultBlockState());
        Terrain.set(level, pos, x, top - 1, z, Blocks.SAND.defaultBlockState());
        BlockState face = n < 0.55F ? Blocks.SAND.defaultBlockState() : n < 0.8F ? Blocks.MUD.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
        Terrain.set(level, pos, x, top, z, face);
        if (top == river() && byWater(x, z, river()) && m > 0.55F) {
            int tall = 1 + Math.round(Terrain.noise(seed + 2, x, top, z) * 2.0F);
            for (int h = 1; h <= tall; h++) Terrain.set(level, pos, x, top + h, z, Blocks.SUGAR_CANE.defaultBlockState());
        } else if (!face.is(Blocks.SAND) && m < 0.3F) {
            Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
        }
    }

    /** Whether water stands right beside a column at the level {@code h}, in the plan. */
    private boolean byWater(int x, int z, int h) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int nx = x + d.getStepX(), nz = z + d.getStepZ();
            float[] l = Terrain.uv(nx + 0.5, nz + 0.5, cx + 0.5, cz + 0.5, angle);
            float e = edge(l[0], l[1]);
            if (!planned(l[0], l[1], e) || blend(l[0], l[1]) > 0.0F) continue;
            int[] s = shape(nx, nz, l[0], l[1], e);
            if (s[1] == h && wet(nx, nz, s, e)) return true;
        }
        return false;
    }

    /** The backswamps (and the old channels' margins): soft mud and wet grass, with reeds round the pools. */
    private void swamp(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int top, float n, float m) {
        Terrain.set(level, pos, x, top - 2, z, Blocks.CLAY.defaultBlockState());
        Terrain.set(level, pos, x, top - 1, z, Blocks.MUD.defaultBlockState());
        BlockState face = Terrain.field(seed + 10, x, z, 4.0) > 0.5F ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.MUD.defaultBlockState();
        Terrain.set(level, pos, x, top, z, face);
        if (byWater(x, z, top) && m > 0.45F) {
            int tall = 1 + Math.round(Terrain.noise(seed + 2, x, top, z) * 2.0F);
            for (int h = 1; h <= tall; h++) Terrain.set(level, pos, x, top + h, z, Blocks.SUGAR_CANE.defaultBlockState());
        } else if (n < 0.12F) {
            tall(level, pos, x, top, z);
        } else if (n < 0.6F) {
            Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
        }
    }

    /** The levee: turf over the sand the floods dropped by the river. */
    private void levee(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int top, float n) {
        for (int h = top - 3; h < top; h++) Terrain.set(level, pos, x, h, z, h == top - 3 ? Blocks.DIRT.defaultBlockState() : Blocks.SAND.defaultBlockState());
        Terrain.set(level, pos, x, top, z, n < 0.08F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
        if (n > 0.6F) Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
        else if (n > 0.55F) tall(level, pos, x, top, z);
    }

    /** The meadow: deep, rich silt under thick grass and flowers. */
    private void meadow(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int top, float n, float m) {
        for (int h = top - 3; h < top; h++) Terrain.set(level, pos, x, h, z, Blocks.DIRT.defaultBlockState());
        Terrain.set(level, pos, x, top, z, Blocks.GRASS_BLOCK.defaultBlockState());
        if (n > 0.93F) {
            tall(level, pos, x, top, z);
        } else if (n > 0.5F) {
            Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
        } else if (n < 0.07F) {
            // drifts of flowers, a different kind in each part of the meadow
            Block[] flowers = {Blocks.DANDELION, Blocks.POPPY, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.AZURE_BLUET};
            int k = Math.min(flowers.length - 1, (int) (Terrain.field(seed + 12, x, z, 11.0) * flowers.length));
            Terrain.set(level, pos, x, top + 1, z, flowers[m < 0.2F ? Math.floorMod(k + 1, flowers.length) : k].defaultBlockState());
        }
    }

    private static void tall(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int top, int z) {
        BlockState grass = Blocks.TALL_GRASS.defaultBlockState();
        Terrain.set(level, pos, x, top + 1, z, grass.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
        Terrain.set(level, pos, x, top + 2, z, grass.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /**
     * Willows and oaks along the levees (and an oak now and then out in the meadow), one at most to each 7-block
     * square: once the ground is all in, and taking in any whose crown reaches over into this chunk. All the trunks go
     * in first, then all the crowns, then the vines (so every vine hangs from a leaf).
     */
    private void trees(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos) {
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int stage = 0; stage < 3; stage++) {
            trees(level, area, pos, leaves, stage);
        }
    }

    private void trees(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos, BlockState leaves, int stage) {
        for (int i = Math.floorDiv(area.minX() - 4, 7); i <= Math.floorDiv(area.maxX() + 4, 7); i++) {
            for (int j = Math.floorDiv(area.minZ() - 4, 7); j <= Math.floorDiv(area.maxZ() + 4, 7); j++) {
                int tx = i * 7 + (int) (Terrain.noise(seed + 20, i, 0, j) * 6.99F), tz = j * 7 + (int) (Terrain.noise(seed + 21, i, 0, j) * 6.99F);
                float roll = Terrain.noise(seed + 22, i, 0, j);
                float[] l = Terrain.uv(tx + 0.5, tz + 0.5, cx + 0.5, cz + 0.5, angle);
                if (!inside(l[0], l[1]) || blend(l[0], l[1]) > 0.0F) continue;
                float e = edge(l[0], l[1]);
                int[] s = shape(tx, tz, l[0], l[1], e);
                boolean levee = e >= 3.0F && e < 6.0F;
                if (s[1] != Integer.MIN_VALUE || !(levee ? roll < 0.6F : e > 9.0F && s[0] == floor && roll < 0.06F)) continue;
                int base = s[0] + 1, height = 4 + Math.round(Terrain.noise(seed + 23, i, 0, j) * 2.0F);
                if (stage == 0) {
                    for (int h = base; h < base + height; h++) Terrain.setIn(level, area, pos, tx, h, tz, Blocks.OAK_LOG.defaultBlockState());
                    continue;
                }
                int top = base + height - 1;
                if (levee && roll < 0.35F) {
                    willow(level, area, pos, tx, top, tz, leaves, stage == 2);
                } else if (stage == 1) {
                    // an oak: a round crown on a short trunk
                    for (int dy = -2; dy <= 1; dy++) {
                        int r = dy < 0 ? 2 : 1;
                        for (int dx = -r; dx <= r; dx++) {
                            for (int dz = -r; dz <= r; dz++) {
                                if (Math.abs(dx) == r && Math.abs(dz) == r && (dy == 1 || Terrain.noise(seed + 24, tx + dx, top + dy, tz + dz) < 0.5F)) continue;
                                leaf(level, area, pos, tx + dx, top + dy, tz + dz, leaves);
                            }
                        }
                    }
                }
            }
        }
    }

    /** A willow: a broad, low dome of leaves weeping down at the edges in long strands (or, later, the vines hung on it). */
    private void willow(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos, int tx, int top, int tz, BlockState leaves, boolean vines) {
        for (int dy = -1; dy <= 1 && !vines; dy++) {
            int r = dy == 1 ? 2 : 3;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > r * r + 1) continue;
                    leaf(level, area, pos, tx + dx, top + dy, tz + dz, leaves);
                }
            }
        }
        // the weeping strands round the rim, and vines among them
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int rr = dx * dx + dz * dz;
                if (rr < 8 || rr > 10) continue;
                float k = Terrain.noise(seed + 25, tx + dx, top, tz + dz);
                int x = tx + dx, z = tz + dz;
                if (k < 0.55F && !vines) {
                    int drop = 1 + Math.round(k * 4.0F);
                    for (int h = top - 2; h >= top - 1 - drop; h--) {
                        if (!leaf(level, area, pos, x, h, z, leaves)) break;
                    }
                } else if (k > 0.75F && vines) {
                    // a vine hanging from the outer face of the rim
                    Direction d = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
                    int vx = x - d.getStepX(), vz = z - d.getStepZ();
                    BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(d), true);
                    for (int h = top - 1; h >= top - 4; h--) {
                        if (!area.isInside(vx, h, vz)) break;
                        pos.set(vx, h, vz);
                        if (!level.getBlockState(pos).isAir()) break;
                        level.setBlock(pos, vine, Terrain.FLAGS);
                    }
                }
            }
        }
    }

    /** Puts a leaf in a block of air in this chunk; whether there was room for it. */
    private static boolean leaf(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState leaves) {
        if (!area.isInside(x, y, z)) return false;
        pos.set(x, y, z);
        if (!level.getBlockState(pos).isAir()) return false;
        level.setBlock(pos, leaves, Terrain.FLAGS);
        return true;
    }
}
