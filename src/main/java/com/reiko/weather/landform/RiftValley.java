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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A rift valley: where the crust is being pulled apart, a long strip of it has dropped down between two parallel
 * faults, like a keystone slipping down out of an arch. Each side of the valley is a fault escarpment that comes down
 * in giant stairs, one slice of crust after another having slid down its own fault. The floor between is broad and
 * flat, with a long, thin lake gathered along it, and the stretched, thinned crust lets magma through: a young cinder
 * cone, or a line of steaming fissure vents, stands out on the floor.
 *
 * @param y        the plateau either side
 * @param floor    the valley floor (the lake stands a block below it)
 * @param half     the floor's half-width
 * @param steps    {bench width, scarp height} for each fault step from the floor outwards, the left side's then the right's
 * @param left     how many steps the left side has
 * @param features {lake u, half-length, v, half-width, depth; 0 for a cinder cone or 1 for fissure vents, its u, v,
 *                 radius or half-length, height}
 */
public record RiftValley(int cx, int cz, int y, int floor, float angle, int length, int half, int[] steps, int left, int[] features, long seed)
        implements Landform {
    /** How wide each fault's scarp is: steep, though not quite sheer. */
    private static final float SCARP = 2.0F;
    /** How far the rift takes to fade out at each end. */
    private static final float ENDS = 24.0F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 9 || !c.fits(ground, 30, 40)) {
            return null;
        }
        int length = 150 + c.random().nextInt(41), half = 15 + c.random().nextInt(8);
        float angle = c.random().nextFloat() * Mth.PI;
        int nl = 2 + c.random().nextInt(2), nr = 2 + c.random().nextInt(2);
        // the benches between the faults, three a side at most
        int[] benches = new int[6];
        for (int k = 0; k < 6; k++) benches[k] = 5 + c.random().nextInt(5);
        int widest = 0;
        for (int side = 0; side < 2; side++) {
            int n = side == 0 ? nl : nr, width = 0;
            for (int k = 0; k < n; k++) width += SCARP + (k < n - 1 ? benches[side * 3 + k] : 0);
            widest = Math.max(widest, width);
        }
        // fairly level ground all along it and either side of it
        int l2 = length / 2, outer = half + widest, lo = ground, hi = ground, sum = ground, count = 1;
        float[][] at = {{-l2, 0}, {-l2 / 2.0F, 0}, {l2 / 2.0F, 0}, {l2, 0},
                {-l2 / 2.0F, -outer}, {0, -outer}, {l2 / 2.0F, -outer}, {-l2 / 2.0F, outer}, {0, outer}, {l2 / 2.0F, outer}};
        for (float[] p : at) {
            double[] q = Terrain.xz(p[0], p[1], c.x() + 0.5, c.z() + 0.5, angle);
            int h = c.groundAt(Mth.floor(q[0]), Mth.floor(q[1]));
            lo = Math.min(lo, h);
            hi = Math.max(hi, h);
            sum += h;
            count++;
        }
        // its floor stays well above any river (which could otherwise pour into it), so on lower ground it drops less
        int y = sum / count, total = Math.min(12 + c.random().nextInt(10), y - c.seaLevel() - 3);
        if (hi - lo > 16 || total < 6) {
            return null;
        }
        int floor = y - total;
        nl = Math.min(nl, total / 4);
        nr = Math.min(nr, total / 4);
        int[] steps = new int[(nl + nr) * 2];
        for (int side = 0; side < 2; side++) {
            int from = side == 0 ? 0 : nl, n = side == 0 ? nl : nr;
            // each fault drops the ground a few blocks, then takes its share of the rest, at random
            for (int k = 0; k < n; k++) {
                steps[(from + k) * 2] = benches[side * 3 + k];
                steps[(from + k) * 2 + 1] = 4;
            }
            for (int r = total - 4 * n; r > 0; r--) steps[(from + c.random().nextInt(n)) * 2 + 1]++;
        }
        // the lake along one half of the floor, the cone or the vents along the other
        int s = c.random().nextBoolean() ? 1 : -1;
        int room = l2 - Math.round(ENDS) - 8;
        int ll = Math.min(22 + c.random().nextInt(20), (room - 4) / 2), lw = 3 + c.random().nextInt(4);
        int lu = s * Math.round(4 + ll + c.random().nextFloat() * Math.max(0, room - 4 - 2 * ll));
        int lv = Math.round((c.random().nextFloat() - 0.5F) * 2.0F * (half - lw - 4));
        int kind = c.random().nextInt(2);
        int fr = kind == 0 ? 6 + c.random().nextInt(4) : 12 + c.random().nextInt(9);
        int fu = -s * Math.round(6 + fr + c.random().nextFloat() * Math.max(0, room - 6 - 2 * fr));
        int fv = Math.round((c.random().nextFloat() - 0.5F) * 2.0F * (half - (kind == 0 ? fr : 3) - 3));
        int[] features = {lu, ll, lv, lw, 2 + c.random().nextInt(3), kind, fu, fv, fr, 6 + c.random().nextInt(5)};
        return new RiftValley(c.x(), c.z(), y, floor, angle, length, half, steps, nl, features, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new RiftValley(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("floor"), tag.getFloat("angle"), tag.getInt("length"),
                tag.getInt("half"), tag.getIntArray("steps"), tag.getInt("left"), tag.getIntArray("features"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("floor", floor);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putInt("half", half);
        tag.put("steps", new IntArrayTag(steps));
        tag.putInt("left", left);
        tag.put("features", new IntArrayTag(features));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.RIFT_VALLEY;
    }

    /** How far out from the axis the escarpments reach. */
    private float outer() {
        float widest = 0.0F;
        for (int side = 0; side < 2; side++) {
            int from = side == 0 ? 0 : left, to = side == 0 ? left : steps.length / 2;
            float width = 0.0F;
            for (int k = from; k < to; k++) width += SCARP + (k < to - 1 ? steps[k * 2] : 0);
            widest = Math.max(widest, width);
        }
        return half + widest + 2.0F; // (and the faults' wiggles)
    }

    @Override
    public BoundingBox bounds() {
        float o = outer() + 1.0F, l2 = length / 2.0F + 1.0F;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (float u : new float[]{-l2, l2}) {
            for (float v : new float[]{-o, o}) {
                double[] p = Terrain.xz(u, v, cx + 0.5, cz + 0.5, angle);
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minZ = Math.min(minZ, p[1]);
                maxZ = Math.max(maxZ, p[1]);
            }
        }
        return new BoundingBox(Mth.floor(minX), floor - features[4] - 5, Mth.floor(minZ), Mth.floor(maxX), y + 40, Mth.floor(maxZ));
    }

    /** The faults run straight, give or take a gentle waver. */
    private float wiggle(float u, int x, int z) {
        return 1.2F * Mth.sin(u * 0.045F + Terrain.hash(seed, 1) * Mth.TWO_PI) + 0.8F * (Terrain.field(seed + 1, x, z, 9.0) - 0.5F);
    }

    /**
     * How far the ground has dropped at a point {@code s} across the rift: {drop, 1 on a scarp, 1 at the foot of one}.
     * Out from the floor, each fault's scarp takes off its share of the drop, with a level bench before the next.
     */
    private float[] drop(float s) {
        int from = s < 0.0F ? 0 : left, to = s < 0.0F ? left : steps.length / 2;
        float p = Math.abs(s), e = half, level = y - floor;
        if (p < e) return new float[]{level, 0.0F, p > e - 1.5F ? 1.0F : 0.0F};
        for (int k = from; k < to; k++) {
            float h = steps[k * 2 + 1];
            if (p < e + SCARP) return new float[]{level - h * (p - e) / SCARP, 1.0F, 0.0F};
            level -= h;
            e += SCARP;
            if (k == to - 1) break;
            float w = steps[k * 2];
            if (p < e + w) return new float[]{level, 0.0F, p > e + w - 1.5F ? 1.0F : 0.0F};
            e += w;
        }
        return new float[]{0.0F, 0.0F, 0.0F};
    }

    /** How far out from the middle of the lake a point is: below 1 is the water. */
    private float lake(float u, float v, int x, int z) {
        return Mth.sqrt(Mth.square((u - features[0]) / features[1]) + Mth.square((v - features[2]) / features[3]))
                / (0.9F + 0.2F * Terrain.field(seed + 2, x, z, 8.0));
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float l2 = length / 2.0F;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                if (!inRift(x, z)) continue;
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
                float u = l[0], v = l[1];
                // the rift dies away towards its ends
                float fade = Terrain.smooth((l2 - Math.abs(u)) / ENDS);
                float[] dr = drop(v + wiggle(u, x, z));
                int target = Math.round(y - dr[0] * fade);
                boolean onFloor = Math.abs(v + wiggle(u, x, z)) < half && fade >= 1.0F;
                int ground = Terrain.ground(level, pos, x, z);
                // (and under any water, which leaves overhanging it can hide from Terrain.ground)
                while (ground > b.minY() && !level.getFluidState(pos.set(x, ground, z)).isEmpty()) ground--;
                if (ground > b.maxY() - 4) continue; // (low ground, a river bed say, is filled in up to the floor, so it's never skipped)
                pos.set(x, ground, z);
                BlockState was = level.getBlockState(pos);
                int surf = ground;
                for (pos.set(x, ground + 1, z); surf < ground + 12 && !level.getFluidState(pos).isEmpty(); pos.move(Direction.UP)) surf++;
                if (onFloor) {
                    floor(level, pos, b, x, z, u, v, ground, surf, was, dr[2] > 0.0F);
                    continue;
                }
                // everywhere else it's only ever cut down; a stream or pond in the way is filled in where the rift is shallower
                // than its water, and drained where it's deeper; and at the rift's edge, nothing is cut away from under
                // water standing just outside it, so none of it can pour in
                int top = Math.min(ground, target);
                if (surf > ground && target >= surf) top = surf;
                int high = Math.min(b.maxY(), Math.max(ground, surf));
                top = Math.max(top, hold(level, x, z, top, high));
                if (top == ground && surf == ground) continue;
                Terrain.clearAbove(level, pos, x, high, z, Math.min(16, b.maxY() - high));
                for (int h = top + 1; h <= high; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                boolean scarp = dr[1] > 0.0F, talus = dr[2] > 0.0F;
                BlockState[] soil = soil(was);
                for (int h = Math.max(b.minY(), ground + 1); h <= top; h++) Terrain.set(level, pos, x, h, z, soil[1].is(BlockTags.TERRACOTTA) ? soil[1] : Blocks.STONE.defaultBlockState());
                float n = Terrain.noise(seed, x, 0, z);
                if (scarp && !was.is(BlockTags.TERRACOTTA)) {
                    // the fault face: shattered rock (in badlands, the bands of terracotta show through as they are)
                    for (int h = Math.max(b.minY(), top - 5); h <= top; h++) {
                        float m = Terrain.noise(seed + 3, x, h, z);
                        Terrain.set(level, pos, x, h, z, (m < 0.5F ? Blocks.STONE : m < 0.75F ? Blocks.ANDESITE : m < 0.9F ? Blocks.TUFF : Blocks.COBBLESTONE).defaultBlockState());
                    }
                } else if (!scarp) {
                    // a bench: the old surface again, with scree fallen off the scarp above along its back
                    for (int h = Math.max(b.minY(), top - 2); h < top; h++) Terrain.set(level, pos, x, h, z, soil[1]);
                    Terrain.set(level, pos, x, top, z, talus && n < 0.55F ? (n < 0.3F ? Blocks.GRAVEL : Blocks.COBBLESTONE).defaultBlockState() : soil[0]);
                    if (!talus && soil[0].is(Blocks.GRASS_BLOCK) && n > 0.8F) Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                }
            }
        }
    }

    /** Whether a column is part of the rift (anywhere it lowers the ground). */
    private boolean inRift(int x, int z) {
        float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
        float l2 = length / 2.0F;
        if (Math.abs(l[0]) > l2 || Math.abs(l[1]) > outer()) return false;
        return Terrain.smooth((l2 - Math.abs(l[0])) / ENDS) > 0.0F && drop(l[1] + wiggle(l[0], x, z))[0] > 0.0F;
    }

    /**
     * The top of any water standing in a column just outside the rift beside this one, between {@code from} and
     * {@code to} (or MIN_VALUE): this column has to stay solid up to there. Nothing here ever changes a column outside
     * the rift, so every chunk sees the same water.
     */
    private int hold(WorldGenLevel level, int x, int z, int from, int to) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int hold = Integer.MIN_VALUE;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int nx = x + d.getStepX(), nz = z + d.getStepZ();
            if (inRift(nx, nz)) continue;
            for (int h = to; h > from && h > hold; h--) {
                if (!level.getFluidState(p.set(nx, h, nz)).isEmpty()) {
                    hold = h;
                    break;
                }
            }
        }
        return hold;
    }

    /** What to put back on top, and under it, for the surface the ground had: {top, under}. */
    private static BlockState[] soil(BlockState was) {
        if (was.is(Blocks.GRASS_BLOCK)) return new BlockState[]{was, Blocks.DIRT.defaultBlockState()};
        if (was.is(BlockTags.DIRT)) return new BlockState[]{was, Blocks.DIRT.defaultBlockState()};
        if (was.is(Blocks.RED_SAND)) return new BlockState[]{was, Blocks.RED_SANDSTONE.defaultBlockState()};
        if (was.is(BlockTags.SAND)) return new BlockState[]{was, Blocks.SANDSTONE.defaultBlockState()};
        if (was.is(BlockTags.TERRACOTTA)) return new BlockState[]{was, was};
        return new BlockState[]{Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState()};
    }

    /** The flat valley floor, with its lake and its cinder cone or vents. */
    private void floor(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, float u, float v, int ground, int surf, BlockState was, boolean talus) {
        int water = floor - 1, top = floor;
        float n = Terrain.noise(seed, x, 0, z);
        float dl = lake(u, v, x, z);
        boolean wet = dl < 1.0F;
        // the cinder cone, with a crater in its top; or the fissure, a crack along the floor
        float cone = 0.0F;
        boolean crack = false, lip = false;
        float du = u - features[6], dv = v - features[7];
        if (features[5] == 0) {
            float kd = Mth.sqrt(du * du + dv * dv) / (features[8] * (0.9F + 0.2F * Terrain.field(seed + 4, x, z, 4.0)));
            if (kd < 1.0F) {
                float rim = features[9] * (1.0F - (float) Math.pow(0.35F, 1.3));
                cone = kd < 0.35F ? rim - 3.0F * Terrain.smooth(1.0F - kd / 0.35F) : features[9] * (1.0F - (float) Math.pow(kd, 1.3));
            }
        } else if (Math.abs(du) < features[8] + 3.0F) {
            float off = Math.abs(dv - 0.7F * Mth.sin(du * 0.4F + Terrain.hash(seed, 5) * Mth.TWO_PI));
            crack = off < 0.7F && Math.abs(du) < features[8] - 1.0F;
            lip = off < 3.6F;
        }
        if (wet) top = water - 1 - Math.round(features[4] * Mth.sqrt(Math.max(0.0F, 1.0F - dl * dl)));
        else if (cone >= 1.0F) top = floor + Math.round(cone);
        else if (crack) top = floor - 2;
        int surface = Math.min(b.maxY(), Math.max(ground, surf));
        Terrain.clearAbove(level, pos, x, surface, z, Math.min(16, b.maxY() - surface));
        for (int h = top + 1; h <= surface; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
        BlockState[] soil = soil(was);
        for (int h = Math.max(b.minY(), Math.min(ground + 1, top - 3)); h <= top; h++) {
            BlockState s;
            float m = Terrain.noise(seed + 6, x, h, z);
            if (wet) {
                s = h < top ? soil[1].is(BlockTags.TERRACOTTA) ? soil[1] : Blocks.CLAY.defaultBlockState()
                        : m < 0.4F ? Blocks.CLAY.defaultBlockState() : m < 0.75F ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState();
                if (h < top - 1 && !soil[1].is(BlockTags.TERRACOTTA)) s = Blocks.STONE.defaultBlockState();
            } else if (cone >= 1.0F && h > floor) {
                // cinders, rusted red near the top where the hot gases oxidised them
                s = m < 0.3F && h > floor + 2 ? Blocks.RED_TERRACOTTA.defaultBlockState() : m < 0.45F ? Blocks.BROWN_TERRACOTTA.defaultBlockState()
                        : m < 0.8F ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.BASALT.defaultBlockState();
            } else if (crack && h == top) {
                s = m < 0.2F ? ModBlocks.VOLCANIC_VENT.get().defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
            } else if (crack || lip && h >= top - 1) {
                // brimstone and fresh black rock round the crack
                s = h == top && n < 0.45F ? ModBlocks.SULFUR_CRUST.get().defaultBlockState() : n < 0.75F ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.BASALT.defaultBlockState();
            } else if (h == top) {
                boolean shore = dl < 1.25F;
                s = shore && n < 0.5F ? Blocks.GRAVEL.defaultBlockState() : talus && n < 0.55F ? Blocks.GRAVEL.defaultBlockState() : soil[0];
            } else {
                s = soil[1];
            }
            Terrain.set(level, pos, x, h, z, s);
        }
        if (wet) {
            for (int h = top + 1; h <= water; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
        } else if (!crack && !lip && cone < 1.0F && soil[0].is(Blocks.GRASS_BLOCK) && dl >= 1.25F && n > 0.8F) {
            Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
        }
    }
}
