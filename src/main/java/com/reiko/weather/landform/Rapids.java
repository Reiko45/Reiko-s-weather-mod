package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Rapids: a stretch where a river runs over a bar of hard rock it hasn't yet worn down. Boulders too big for the
 * current to roll stand up out of the water with the river churning white round them, more rocks lurk just under
 * the surface, and along one bank the bare rock is exposed as a smooth shelf, drilled with round potholes where
 * floods have spun pebbles round and round in one place until they ground a pit.
 *
 * @param angle the way the river runs
 * @param side  which bank has the rock shelf (+1 or -1 across the river)
 * @param bank  how far across that bank is
 * @param far   how far across the other bank is
 */
public record Rapids(int cx, int cz, int sea, float angle, int length, int side, int bank, int far, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        int water = c.seaLevel() - 1;
        if (ground > water - 1 || ground < water - 12) {
            return null; // it has to be planned from the river itself
        }
        // the river's course: the way the ground stays low
        float start = c.random().nextFloat() * Mth.PI, best = start;
        int bestHigh = Integer.MAX_VALUE;
        for (int k = 0; k < 6; k++) {
            float a = start + k * Mth.PI / 6.0F;
            int high = Math.max(c.groundAt(c.x() + Math.round(Mth.cos(a) * 24), c.z() + Math.round(Mth.sin(a) * 24)),
                    c.groundAt(c.x() - Math.round(Mth.cos(a) * 24), c.z() - Math.round(Mth.sin(a) * 24)));
            if (high < bestHigh) {
                bestHigh = high;
                best = a;
            }
        }
        if (bestHigh > water - 1) {
            return null;
        }
        // the banks either side
        int[] found = new int[2];
        for (int s = 0; s < 2; s++) {
            int sign = s == 0 ? 1 : -1;
            for (int d = 5; d <= 13; d += 4) {
                if (c.groundAt(c.x() - Math.round(Mth.sin(best) * d * sign), c.z() + Math.round(Mth.cos(best) * d * sign)) >= water) {
                    found[s] = d;
                    break;
                }
            }
        }
        if (found[0] == 0 && found[1] == 0) {
            return null; // a lake, or the sea, not a river
        }
        // the shelf goes along the nearer bank
        int s = found[1] == 0 || found[0] != 0 && found[0] <= found[1] ? 0 : 1;
        return new Rapids(c.x(), c.z(), c.seaLevel(), best, 40 + c.random().nextInt(21), s == 0 ? 1 : -1, found[s],
                found[1 - s] == 0 ? 14 : found[1 - s], c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Rapids(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("side"),
                tag.getInt("bank"), tag.getInt("far"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("sea", sea);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putInt("side", side);
        tag.putInt("bank", bank);
        tag.putInt("far", far);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.RAPIDS;
    }

    @Override
    public BoundingBox bounds() {
        float half = length / 2.0F + 2.0F;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (float u : new float[]{-half, half}) {
            for (float v : new float[]{-(far + 2.0F), bank + 6.0F}) {
                double[] w = Terrain.xz(u, side * v, cx, cz, angle);
                minX = Math.min(minX, Mth.floor(w[0]));
                maxX = Math.max(maxX, Mth.floor(w[0]));
                minZ = Math.min(minZ, Mth.floor(w[1]));
                maxZ = Math.max(maxZ, Mth.floor(w[1]));
            }
        }
        return new BoundingBox(minX, sea - 16, minZ, maxX, sea + 16, maxZ);
    }

    /** How far the edge of the rock shelf wanders in and out along the bank. */
    private float wobble(float u) {
        return 1.5F * Mth.sin(u * 0.09F + Terrain.hash(seed, 1) * 6.0F) + 0.8F * Mth.sin(u * 0.23F + Terrain.hash(seed, 2) * 6.0F);
    }

    /** {u, v, radius, depth} for each pothole, in the middle of the shelf. */
    private float[][] potholes() {
        int count = 3 + Math.round(Terrain.hash(seed, 3) * 2.0F);
        float[][] list = new float[count][];
        for (int i = 0; i < count; i++) {
            float u = (Terrain.hash(seed, 10 + i) - 0.5F) * (length - 18.0F);
            float v = bank - 0.5F + wobble(u) + (Terrain.hash(seed, 20 + i) - 0.5F) * 1.2F;
            list[i] = new float[]{u, v, 1.0F + 0.5F * Terrain.hash(seed, 30 + i), 2 + Math.round(Terrain.hash(seed, 40 + i))};
        }
        return list;
    }

    /** {u, v, radius, top} for each rock in the river: boulders standing out of it, and rocks just under it. */
    private float[][] rocks() {
        int count = 12 + Math.round(Terrain.hash(seed, 4) * 6.0F);
        float[][] list = new float[count][];
        for (int i = 0; i < count; i++) {
            float u = (Terrain.hash(seed, 100 + i) - 0.5F) * (length - 6.0F);
            float v = -(far - 2.0F) + Terrain.hash(seed, 200 + i) * (far + bank - 7.0F);
            boolean under = i % 3 == 2;
            float r = under ? 0.6F + 0.5F * Terrain.hash(seed, 300 + i) : 0.9F + 0.9F * Terrain.hash(seed, 300 + i);
            list[i] = new float[]{u, v, r, under ? sea - 2 : sea + Math.round(Terrain.hash(seed, 400 + i) * 2.0F)};
        }
        return list;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float half = length / 2.0F;
        int water = sea - 1, shelfTop = sea;
        float[][] holes = potholes(), rocks = rocks();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float u = l[0], v = side * l[1];
                if (Math.abs(u) > half + 2.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                float n = Terrain.noise(seed, x, sea, z);
                float ends = Terrain.smooth((half - Math.abs(u)) / 8.0F);
                float edge = bank + wobble(u);
                if (v > edge - 4.0F + (1.0F - ends) * 6.0F && v < edge + 3.0F && ground <= b.maxY() - 12) {
                    // the shelf: bare rock along the bank, worn smooth, a block above the water
                    Terrain.clearAbove(level, pos, x, ground, z, 10);
                    for (int h = shelfTop + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    for (int h = Math.min(ground + 1, sea - 3); h <= shelfTop; h++) {
                        Terrain.set(level, pos, x, h, z, Terrain.field(seed + 5, x + h * 0.7, z, 4.0) > 0.7F ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState());
                    }
                    for (float[] hole : holes) {
                        float d = Mth.sqrt(Mth.square(u - hole[0]) + Mth.square(v - hole[1]));
                        if (d >= hole[2]) continue;
                        // a pothole: a round pit holding water level with the river's, a pebble or two in the bottom
                        int depth = Math.round(hole[3]);
                        Terrain.set(level, pos, x, shelfTop, z, Blocks.AIR.defaultBlockState());
                        for (int h = shelfTop - depth + 1; h <= water; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                        Terrain.set(level, pos, x, shelfTop - depth, z, n < 0.5F ? Blocks.GRAVEL.defaultBlockState() : Blocks.STONE.defaultBlockState());
                        break;
                    }
                    continue;
                }
                if (v < -(far + 1.0F) || v > edge) continue;
                // in the river: boulders, rocks lurking just under the surface, and a bed of stones
                boolean wet = !level.getFluidState(pos.set(x, ground + 1, z)).isEmpty();
                int top = ground;
                for (float[] rock : rocks) {
                    float d = Mth.sqrt(Mth.square(u - rock[0]) + Mth.square(v - rock[1])) / rock[2];
                    if (d >= 1.0F) continue;
                    top = Math.max(top, Math.round(rock[3] - d * d * 1.4F));
                }
                if (top > ground) {
                    for (int h = ground + 1; h <= top; h++) {
                        float m = Terrain.noise(seed, x, h, z);
                        Terrain.set(level, pos, x, h, z, m < 0.45F ? Blocks.STONE.defaultBlockState() : m < 0.75F ? Blocks.ANDESITE.defaultBlockState()
                                : Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                    }
                    continue;
                }
                if (!wet || ground >= water) continue;
                Terrain.set(level, pos, x, ground, z, n < 0.4F ? Blocks.STONE.defaultBlockState() : n < 0.7F ? Blocks.GRAVEL.defaultBlockState()
                        : n < 0.9F ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState());
            }
        }
        // white water: the river boiling up in the deeper holes just past the bigger boulders
        for (int i = 0; i < rocks.length && i < 9; i++) {
            float[] rock = rocks[i];
            if (rock[3] < sea || Terrain.hash(seed, 500 + i) > 0.45F) continue;
            double[] p = Terrain.xz(rock[0] + rock[2] + 1.5F, side * rock[1], cx, cz, angle);
            int x = Mth.floor(p[0]), z = Mth.floor(p[1]);
            if (!Terrain.column(area, x, z)) continue;
            int ground = Terrain.ground(level, pos, x, z);
            if (ground > water - 3 || !level.getFluidState(pos.set(x, water, z)).isSource()) continue;
            Terrain.set(level, pos, x, ground, z, Blocks.SOUL_SAND.defaultBlockState());
            for (int h = ground + 1; h <= water; h++) {
                Terrain.set(level, pos, x, h, z, Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, false));
            }
        }
    }
}
