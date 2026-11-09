package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A ria: a river valley drowned by the sea as it rose at the end of the last ice age. The sea runs a long way inland
 * up the winding valley and into the side valleys of its old tributaries, deep at the mouth and shallowing to mud at
 * the heads, between steep wooded hillsides that come straight down to the water. Unlike a fjord, no glacier ever
 * shaped it: it is still a river's valley, just full of the sea.
 *
 * @param mx    where it meets the open sea
 * @param angle the way it runs inland
 * @param out   how far its channel runs back out to sea from the mouth
 * @param arms  {junction (blocks up the main inlet), side (+1/-1), angle off the main inlet (degrees), length} for each side arm
 */
public record Ria(int mx, int mz, int sea, float angle, int length, int width, int out, int[] arms, long seed) implements Landform {
    /** How far up the valley sides the cut reaches. */
    private static final float WALL = 12.0F;

    @Nullable
    static Landform plan(PlanContext c) {
        int here = c.groundAt(c.x(), c.z());
        if (here > c.seaLevel() - 2 || here < c.seaLevel() - 30) {
            return null; // it starts out in the sea
        }
        // which way are the hills?
        float bestAngle = 0.0F;
        int best = Integer.MIN_VALUE;
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F;
            int sum = c.groundAt(c.x() + Math.round(Mth.cos(a) * 36), c.z() + Math.round(Mth.sin(a) * 36))
                    + c.groundAt(c.x() + Math.round(Mth.cos(a) * 72), c.z() + Math.round(Mth.sin(a) * 72));
            if (sum > best) {
                best = sum;
                bestAngle = a;
            }
        }
        if (best / 2 < c.seaLevel() + 8) {
            return null;
        }
        // the coast: walk inland until out of the sea
        int mouth = -1;
        for (int d = 4; d <= 28; d += 4) {
            if (c.groundAt(c.x() + Math.round(Mth.cos(bestAngle) * d), c.z() + Math.round(Mth.sin(bestAngle) * d)) >= c.seaLevel()) {
                mouth = d;
                break;
            }
        }
        if (mouth < 0) {
            return null;
        }
        // keep its head (and the hillside round it) within reach of where it was planned from
        int length = Math.min(94 - mouth, 60 + c.random().nextInt(41));
        if (length < 60) {
            return null;
        }
        int mx = c.x() + Math.round(Mth.cos(bestAngle) * mouth), mz = c.z() + Math.round(Mth.sin(bestAngle) * mouth);
        int n = 2 + c.random().nextInt(2);
        int[] arms = new int[n * 4];
        int side = c.random().nextBoolean() ? 1 : -1;
        for (int i = 0; i < n; i++, side = -side) {
            int junction = Math.round(length * (0.22F + 0.5F * (i + c.random().nextFloat()) / n));
            int off = 40 + c.random().nextInt(31);
            int armLength = 22 + c.random().nextInt(20);
            // keep its head within reach of where the ria was planned from
            float a = bestAngle + side * off * Mth.DEG_TO_RAD;
            double jx = mx + Mth.cos(bestAngle) * junction, jz = mz + Mth.sin(bestAngle) * junction;
            while (armLength > 14 && Mth.length(jx + Mth.cos(a) * armLength - c.x(), jz + Mth.sin(a) * armLength - c.z()) > 88.0) armLength -= 2;
            arms[i * 4] = junction;
            arms[i * 4 + 1] = side;
            arms[i * 4 + 2] = off;
            arms[i * 4 + 3] = armLength > 14 ? armLength : 0;
        }
        return new Ria(mx, mz, c.seaLevel(), bestAngle, length, 6 + c.random().nextInt(3), mouth + 2, arms, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Ria(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("width"),
                tag.getInt("out"), tag.getIntArray("arms"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", mx);
        tag.putInt("z", mz);
        tag.putInt("sea", sea);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putInt("width", width);
        tag.putInt("out", out);
        tag.put("arms", new IntArrayTag(arms));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.RIA;
    }

    @Override
    public BoundingBox bounds() {
        // the water and the valley sides round it, out to the cut's edge (round the heads too)
        int[] box = {mx, mz, mx, mz};
        for (float u = -out; u < length; u += 4.0F) widen(box, Terrain.xz(u, wiggle(u), mx, mz, angle), mainWidth(u) + WALL + 2.0F);
        widen(box, Terrain.xz(length, wiggle(length), mx, mz, angle), WALL + 2.0F);
        for (int i = 0; i < arms.length / 4; i++) {
            int len = arms[i * 4 + 3];
            if (len == 0) continue;
            double[] j = junction(i);
            for (float u = 0.0F; u < len; u += 4.0F) widen(box, Terrain.xz(u, armWiggle(i, u), j[0], j[1], armAngle(i)), armWidth(i, u) + WALL + 2.0F);
            widen(box, Terrain.xz(len, armWiggle(i, len), j[0], j[1], armAngle(i)), WALL + 2.0F);
        }
        return new BoundingBox(box[0], sea - 16, box[1], box[2], sea + 72, box[3]);
    }

    private static void widen(int[] box, double[] p, float margin) {
        box[0] = Math.min(box[0], Mth.floor(p[0] - margin));
        box[1] = Math.min(box[1], Mth.floor(p[1] - margin));
        box[2] = Math.max(box[2], Mth.ceil(p[0] + margin));
        box[3] = Math.max(box[3], Mth.ceil(p[1] + margin));
    }

    /** How far the drowned valley's meanders swing it aside, {@code u} blocks inland. */
    private float wiggle(float u) {
        return (7.0F * Mth.sin(u * 0.045F + Terrain.hash(seed, 1) * 6.0F) + 2.5F * Mth.sin(u * 0.12F + Terrain.hash(seed, 2) * 6.0F))
                * Mth.clamp(u / 25.0F, 0.0F, 1.0F);
    }

    private float armWiggle(int i, float u) {
        return 3.0F * Mth.sin(u * 0.09F + Terrain.hash(seed, 10 + i) * 6.0F) * Mth.clamp(u / 12.0F, 0.0F, 1.0F);
    }

    private double[] junction(int i) {
        float u = arms[i * 4];
        return Terrain.xz(u, wiggle(u), mx, mz, angle);
    }

    private float armAngle(int i) {
        return angle + arms[i * 4 + 1] * arms[i * 4 + 2] * Mth.DEG_TO_RAD;
    }

    /** Half the main inlet's width: narrowing inland and rounding off at the head. */
    private float mainWidth(float u) {
        if (u <= 0.0F) return width;
        return width * (1.0F - 0.55F * u / length) * (1.0F + 0.15F * Mth.sin(u * 0.11F + Terrain.hash(seed, 3) * 6.0F))
                * Mth.sqrt(Mth.clamp((length - u) / 12.0F, 0.0F, 1.0F));
    }

    /** How deep the main inlet is in the middle: deep at the mouth, shallowing inland. */
    private float mainDepth(float u) {
        if (u <= 0.0F) return 2.0F + 10.0F * Terrain.smooth((u + out) / out); // fading into the sea bed off the mouth
        return 2.0F + 10.0F * (float) Math.pow(1.0F - Mth.clamp(u / length, 0.0F, 1.0F), 1.4);
    }

    private float armWidth(int i, float u) {
        float len = arms[i * 4 + 3];
        return (2.6F + 1.2F * Terrain.hash(seed, 30 + i)) * (1.0F - 0.5F * Mth.clamp(u / len, 0.0F, 1.0F)) * Mth.sqrt(Mth.clamp((len - u) / 8.0F, 0.0F, 1.0F));
    }

    /**
     * The drowned valleys at a column: {bed (NaN if not under water), how far up a valley side (0-1, or 2 if not on
     * one), and how far inland (0-1)}.
     */
    private float[] at(double x, double z) {
        int bed = Integer.MAX_VALUE;
        float side = 2.0F, inland = 0.0F;
        float[] l = Terrain.uv(x, z, mx, mz, angle);
        float u = l[0], v = Math.abs(l[1] - wiggle(u));
        if (u > -out && u < length + WALL) {
            float hw = mainWidth(u);
            if (u < length && v < hw) {
                bed = sea - 2 - Math.round((mainDepth(u) - 1.0F) * Mth.sqrt(1.0F - Mth.square(v / Math.max(1.0F, hw))));
                inland = Mth.clamp(u / length, 0.0F, 1.0F);
            } else if (u >= 0.0F) {
                side = u < length ? (v - hw) / WALL : Mth.sqrt(Mth.square(u - length) + v * v) / WALL;
            }
        }
        for (int i = 0; i < arms.length / 4; i++) {
            int len = arms[i * 4 + 3];
            if (len == 0) continue;
            double[] j = junction(i);
            float[] la = Terrain.uv(x, z, j[0], j[1], armAngle(i));
            float ua = la[0], va = Math.abs(la[1] - armWiggle(i, ua));
            if (ua < -2.0F || ua > len + WALL) continue;
            float hw = armWidth(i, Math.max(0.0F, ua));
            if (ua < len && va < hw) {
                float deep = 1.0F + (mainDepth(arms[i * 4]) * 0.6F - 1.0F) * (1.0F - Mth.clamp(ua / len, 0.0F, 1.0F));
                bed = Math.min(bed, sea - 2 - Math.round((deep - 1.0F) * Mth.sqrt(1.0F - Mth.square(va / Math.max(1.0F, hw)))));
                inland = Math.max(inland, 0.5F + 0.5F * ua / len);
            } else {
                side = Math.min(side, ua < len ? (va - hw) / WALL : Mth.sqrt(Mth.square(ua - len) + va * va) / WALL);
            }
        }
        return new float[]{bed == Integer.MAX_VALUE ? Float.NaN : bed, side, inland};
    }

    /** The cut valley side's height, {@code t} of the way up it. */
    private int cap(float t) {
        return sea + Math.round(28.0F * (float) Math.pow(t, 1.5));
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] here = at(x + 0.5, z + 0.5);
                boolean wet = !Float.isNaN(here[0]);
                int bed = wet ? (int) here[0] : 0;
                float side = here[1];
                if (!wet && side >= 1.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                float n = Terrain.noise(seed, x, sea, z);
                int clear = Math.max(0, Math.min(30, b.maxY() - Math.max(ground, sea - 1)));
                if (wet) {
                    // the drowned valley: sea water joined to the open sea, over mud toward the heads and sand and gravel lower down
                    int floor = Math.min(ground, bed);
                    Terrain.clearAbove(level, pos, x, Math.max(ground, sea - 1), z, clear);
                    for (int h = Math.max(b.minY(), floor + 1); h <= Math.min(Math.max(ground, sea - 1), b.maxY()); h++) {
                        pos.set(x, h, z);
                        BlockState was = level.getBlockState(pos);
                        BlockState s = h <= sea - 1 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
                        if (was != s) level.setBlock(pos, s, Terrain.FLAGS); // (leaving the sea that's already there alone)
                    }
                    if (bed <= ground) {
                        float silt = here[2] + 0.3F * (Terrain.field(seed + 1, x, z, 6.0) - 0.5F);
                        BlockState s = silt > 0.72F ? Blocks.MUD.defaultBlockState() : n < 0.2F ? Blocks.CLAY.defaultBlockState()
                                : silt > 0.4F || n < 0.6F ? Blocks.SAND.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                        Terrain.set(level, pos, x, floor, z, s);
                        if (floor < sea - 3 && n > 0.8F) Terrain.set(level, pos, x, floor + 1, z, Blocks.SEAGRASS.defaultBlockState());
                    }
                    continue;
                }
                // the valley sides: cut back steep where the hills stand high, banked up where the land is low
                int cap = cap(side);
                if (ground > cap) {
                    Terrain.clearAbove(level, pos, x, ground, z, clear);
                    for (int h = cap + 1; h <= Math.min(ground, b.maxY()); h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    Terrain.set(level, pos, x, cap, z, surface(level, pos, x, cap, z, side, n));
                    if (cap > sea && !steep(side)) Terrain.set(level, pos, x, cap - 1, z, Blocks.DIRT.defaultBlockState());
                    if (cap > sea + 1 && !steep(side) && n > 0.6F) {
                        Terrain.set(level, pos, x, cap + 1, z, n > 0.85F ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
                    }
                } else if (ground < sea - 1 && !level.getFluidState(pos.set(x, sea - 1, z)).isSourceOfType(net.minecraft.world.level.material.Fluids.WATER)) {
                    // a dip in the bank: build it up so the water stays in
                    for (int h = ground + 1; h <= sea; h++) Terrain.set(level, pos, x, h, z, h == sea ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState());
                }
            }
        }
        // trees clinging to the steep sides (once the ground is all in, so nothing clears them away again); a tree near the
        // edge of the chunk is grown from both sides, so each side works out the same trunk
        for (int x = area.minX() - 2; x <= area.maxX() + 2; x++) {
            for (int z = area.minZ() - 2; z <= area.maxZ() + 2; z++) {
                if (!b.isInside(x, b.minY(), z) || Terrain.noise(seed, x, 77, z) > 0.06F) continue;
                float[] here = at(x + 0.5, z + 0.5);
                float side = here[1];
                if (!Float.isNaN(here[0]) || side < 0.06F || side > 0.8F || steep(side)) continue;
                int cap = cap(side);
                if (cap < sea + 3 || Terrain.ground(level, pos, x, z) < cap) continue;
                float temperature = level.getBiome(pos.set(x, cap, z)).value().getBaseTemperature();
                tree(level, area, pos, x, cap + 1, z, 4 + Math.round(Terrain.noise(seed, x, 78, z) * 2.0F),
                        temperature < 0.3F ? 2 : Terrain.noise(seed, x, 79, z) < 0.3F ? 1 : 0);
            }
        }
    }

    /** Whether the valley side is too steep for soil to stay on, {@code t} of the way up. */
    private static boolean steep(float t) {
        // the cut's slope: 28 * 1.5 * sqrt(t) over WALL blocks
        return 42.0F * Mth.sqrt(t) / WALL > 2.6F;
    }

    private BlockState surface(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, float side, float n) {
        if (y <= sea) return n < 0.5F ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState();
        if (steep(side)) return n < 0.6F ? Blocks.STONE.defaultBlockState() : n < 0.8F ? Blocks.ANDESITE.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
        return n < 0.12F ? Blocks.PODZOL.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
    }

    /** A small tree (0 oak, 1 birch, 2 spruce), its leaves only placed into air and inside the chunk being built. */
    private static void tree(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos, int x, int y, int z, int height, int type) {
        BlockState log = (type == 2 ? Blocks.SPRUCE_LOG : type == 1 ? Blocks.BIRCH_LOG : Blocks.OAK_LOG).defaultBlockState();
        BlockState leaves = (type == 2 ? Blocks.SPRUCE_LEAVES : type == 1 ? Blocks.BIRCH_LEAVES : Blocks.OAK_LEAVES).defaultBlockState()
                .setValue(LeavesBlock.PERSISTENT, true);
        for (int k = 0; k < height; k++) Terrain.setIn(level, area, pos, x, y + k, z, log);
        int crown = y + height - 1;
        for (int dy = -3; dy <= 2; dy++) {
            int r = type == 2 ? (dy >= 1 ? 0 : dy == 0 || dy == -2 ? 1 : 2) : (dy <= -2 ? -1 : dy <= 0 ? 2 : dy == 1 ? 1 : 0);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (r >= 2 && Math.abs(dx) == r && Math.abs(dz) == r) continue;
                    if (dx == 0 && dz == 0 && dy <= 0) continue;
                    if (!area.isInside(x + dx, crown + dy, z + dz)) continue;
                    pos.set(x + dx, crown + dy, z + dz);
                    if (level.getBlockState(pos).isAir()) level.setBlock(pos, leaves, Terrain.FLAGS);
                }
            }
        }
    }
}
