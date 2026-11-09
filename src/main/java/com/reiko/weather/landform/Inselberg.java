package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * An inselberg ("island mountain"), like Uluru: a single great dome of rock rising sheer out of a dead-flat plain.
 * It's the last of a thick bed of hard sandstone, tilted on end, that has outlasted all the softer rock round it.
 * Its smooth, rust-red sides are scored by vertical gullies where rain sheets off it, honeycombed at the foot by
 * hollows the weather has eaten out (tafoni), and the runoff collects in waterholes round its base that stay
 * full long after the plain has dried out.
 *
 * @param base   the level of the plain at its foot
 * @param pools  {x, z, radius, water y} for each waterhole
 */
public record Inselberg(int cx, int cz, int base, float angle, int rx, int rz, int height, int[] pools, long seed) implements Landform {
    private static final BlockState[] SKIN = {Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState(),
            Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(),
            Blocks.CUT_RED_SANDSTONE.defaultBlockState()};

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        int rx = 15 + c.random().nextInt(10), rz = Math.round(rx * (0.6F + 0.3F * c.random().nextFloat()));
        int height = 25 + c.random().nextInt(16);
        if (ground < c.seaLevel() + 2 || !c.fits(ground, 12, height + 10)) {
            return null;
        }
        // flat ground all round (it's the rock that stands up, not the land under it)
        int sum = 0, min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int i = 0; i < 10; i++) {
            float a = i * Mth.TWO_PI / 10.0F;
            int g = c.groundAt(c.x() + Math.round(Mth.cos(a) * (rx + 8)), c.z() + Math.round(Mth.sin(a) * (rx + 8)));
            sum += g;
            min = Math.min(min, g);
            max = Math.max(max, g);
        }
        if (max - min > 12) {
            return null;
        }
        int base = Math.round(sum / 10.0F);
        float angle = c.random().nextFloat() * Mth.PI;
        long seed = c.random().nextLong();
        Inselberg probe = new Inselberg(c.x(), c.z(), base, angle, rx, rz, height, new int[0], seed);
        // waterholes at the feet of a couple of the gullies, where the runoff pours off the rock
        int n = 1 + c.random().nextInt(2);
        int[] pools = new int[n * 4];
        int count = 0;
        for (int k = 0; k < n; k++) {
            int g = Math.floorMod(probe.gullies() / 2 * k + Math.round(Terrain.hash(seed, 7) * 5.0F), probe.gullies());
            float theta = probe.gullyAngle(g);
            int r = 2 + c.random().nextInt(2);
            float out = probe.edge(theta) + r + 1.5F;
            double[] p = Terrain.xz(Mth.cos(theta) * out, Mth.sin(theta) * out, c.x(), c.z(), angle);
            int px = Mth.floor(p[0]), pz = Mth.floor(p[1]);
            // the water stands a block under the lowest point round its rim
            int low = c.groundAt(px, pz);
            for (int i = 0; i < 4; i++) {
                float b = i * Mth.HALF_PI;
                low = Math.min(low, c.groundAt(px + Math.round(Mth.cos(b) * (r + 1)), pz + Math.round(Mth.sin(b) * (r + 1))));
            }
            if (low > base + 3 || low < base - 6 || low <= c.seaLevel()) continue;
            pools[count * 4] = px;
            pools[count * 4 + 1] = pz;
            pools[count * 4 + 2] = r;
            pools[count * 4 + 3] = low - 1;
            count++;
        }
        return new Inselberg(c.x(), c.z(), base, angle, rx, rz, height, java.util.Arrays.copyOf(pools, count * 4), seed);
    }

    static Landform load(CompoundTag tag) {
        return new Inselberg(tag.getInt("x"), tag.getInt("z"), tag.getInt("base"), tag.getFloat("angle"), tag.getInt("rx"), tag.getInt("rz"),
                tag.getInt("height"), tag.getIntArray("pools"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("base", base);
        tag.putFloat("angle", angle);
        tag.putInt("rx", rx);
        tag.putInt("rz", rz);
        tag.putInt("height", height);
        tag.put("pools", new IntArrayTag(pools));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.INSELBERG;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(Math.max(rx, rz) * 1.25F) + 12;
        int low = base;
        for (int i = 0; i < pools.length; i += 4) low = Math.min(low, pools[i + 3] - 4);
        return new BoundingBox(cx - r, low - 8, cz - r, cx + r, base + height + 4, cz + r);
    }

    private int gullies() {
        return 9 + Math.round(Terrain.hash(seed, 1) * 6.0F);
    }

    private float gullyAngle(int g) {
        return (g + 0.6F * Terrain.hash(seed, 100 + g)) * Mth.TWO_PI / gullies();
    }

    /** How far the foot of the rock lies from the middle, in a direction (local to its long axis). */
    private float edge(float theta) {
        float wobble = 1.0F + 0.06F * Mth.sin(2.0F * theta + Terrain.hash(seed, 2) * 6.0F) + 0.04F * Mth.sin(3.0F * theta + Terrain.hash(seed, 3) * 6.0F)
                + 0.025F * Mth.sin(5.0F * theta + Terrain.hash(seed, 4) * 6.0F);
        float c = Mth.cos(theta) / rx, s = Mth.sin(theta) / rz;
        return wobble / Mth.sqrt(c * c + s * s);
    }

    /** How far the gullies have cut back into the rock in a direction, in blocks (the edge is that far nearer the middle). */
    private float gully(float theta, float edge) {
        float cut = 0.0F;
        int n = gullies();
        for (int g = 0; g < n; g++) {
            float d = Math.abs(Mth.wrapDegrees((theta - gullyAngle(g)) * Mth.RAD_TO_DEG)) * Mth.DEG_TO_RAD * edge;
            float width = 1.2F + Terrain.hash(seed, 120 + g) * 1.2F;
            if (d < width) cut = Math.max(cut, (2.0F + 2.0F * Terrain.hash(seed, 140 + g)) * Terrain.smooth(1.0F - d / width));
        }
        return cut;
    }

    /** The top of the rock at a column (or {@code Integer.MIN_VALUE} outside it), and how far in from its foot that is. */
    private float[] dome(double x, double z) {
        float[] l = Terrain.uv(x, z, cx, cz, angle);
        float r = Mth.sqrt(l[0] * l[0] + l[1] * l[1]);
        float theta = (float) Mth.atan2(l[1], l[0]);
        float edge = edge(theta);
        float cut = gully(theta, edge);
        float d = r / (edge - cut);
        if (d >= 1.0F) return new float[]{Integer.MIN_VALUE, d, cut, edge - cut - r};
        // near-vertical sides rounding over onto a broad, gently domed top
        float h = height * (float) Math.pow(1.0F - Math.pow(d, 5.0F), 0.32F);
        h += 1.5F * (Terrain.field(seed, x, z, 9.0) - 0.5F) * Terrain.smooth((0.9F - d) / 0.3F);
        return new float[]{base + h, d, cut, edge - cut - r};
    }

    private int top(int x, int z) {
        float t = dome(x + 0.5, z + 0.5)[0];
        return t == Integer.MIN_VALUE ? Integer.MIN_VALUE : Math.round(t);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int caves = 5 + Math.round(Terrain.hash(seed, 5) * 3.0F);
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] dome = dome(x + 0.5, z + 0.5);
                float d = dome[1];
                // the waterholes, and the banks that hold them
                int pool = -1;
                float pd = Float.MAX_VALUE;
                for (int i = 0; i < pools.length / 4; i++) {
                    float dd = Mth.sqrt(Mth.square(x + 0.5F - pools[i * 4]) + Mth.square(z + 0.5F - pools[i * 4 + 1])) - pools[i * 4 + 2];
                    if (dd < pd) {
                        pd = dd;
                        pool = i;
                    }
                }
                if (d > 1.14F && pd > 1.8F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 3 || ground > b.maxY() - 2) continue;
                float n = Terrain.noise(seed, x, ground, z);
                if (dome[0] == Integer.MIN_VALUE) {
                    if (pool >= 0 && pd < 1.8F) {
                        waterhole(level, pos, x, z, ground, pool, pd, n);
                    } else if (d < 1.14F) {
                        // an apron of sand and fallen flakes of rock round the foot
                        int apron = base + Math.round(2.0F * (1.14F - d) / 0.14F) - 1;
                        if (apron <= ground) continue;
                        Terrain.clearAbove(level, pos, x, apron, z, 6);
                        for (int h = ground + 1; h <= apron; h++) {
                            Terrain.set(level, pos, x, h, z, n < 0.2F ? Blocks.RED_SANDSTONE.defaultBlockState() : Blocks.RED_SAND.defaultBlockState());
                        }
                        if (n > 0.95F) Terrain.set(level, pos, x, apron + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                    }
                    continue;
                }
                int top = Math.round(dome[0]);
                // the faces showing on the outside: anything above the lowest of the neighbouring tops
                int open = top;
                open = Math.min(open, top(x + 1, z));
                open = Math.min(open, top(x - 1, z));
                open = Math.min(open, top(x, z + 1));
                open = Math.min(open, top(x, z - 1));
                Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, Math.min(8, b.maxY() - Math.max(ground, top)));
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float theta = (float) Mth.atan2(l[1], l[0]);
                boolean streak = dome[2] > 0.8F;
                // (sand drifted up against the foot is left lying on the rock)
                for (int h = Math.min(ground, base) - 2; h <= top; h++) {
                    BlockState s = Blocks.RED_SANDSTONE.defaultBlockState();
                    if (h > open - 1 || h >= top - 1) {
                        // the beds stand on end, so they show as stripes running up and over the rock
                        int bed = Math.floorMod(Math.round((l[1] + 3.0F * Terrain.field(seed + 1, x, z, 12.0)) / 2.0F), SKIN.length);
                        s = SKIN[bed];
                        float w = Terrain.noise(seed, x, h, z);
                        if (streak && w < 0.55F) {
                            s = w < 0.25F ? Blocks.BROWN_TERRACOTTA.defaultBlockState() : Blocks.TERRACOTTA.defaultBlockState(); // dark runoff stains
                        } else if (w > 0.9F) {
                            s = Blocks.ORANGE_TERRACOTTA.defaultBlockState();
                        }
                    }
                    Terrain.set(level, pos, x, h, z, s);
                }
                // tafoni: caves and honeycomb hollows weathered into the foot of the walls
                float in = dome[3];
                if (in > 6.0F || pd < 3.5F) continue;
                for (int k = 0; k < caves; k++) {
                    float ct = (k + 0.5F + 0.4F * (Terrain.hash(seed, 200 + k) - 0.5F)) * Mth.TWO_PI / caves + Mth.PI / gullies();
                    float along = Math.abs(Mth.wrapDegrees((theta - ct) * Mth.RAD_TO_DEG)) * Mth.DEG_TO_RAD * edge(theta);
                    float width = 1.2F + Terrain.hash(seed, 220 + k) * 1.4F;
                    if (along >= width) continue;
                    float f = Mth.sqrt(1.0F - Mth.square(along / width));
                    float deep = (2.0F + Terrain.hash(seed, 240 + k) * 3.0F) * f;
                    if (in >= deep) continue;
                    int floor = base + 1 + Math.round(Terrain.hash(seed, 260 + k) * 1.5F);
                    int roof = floor + Math.round((1.5F + Terrain.hash(seed, 280 + k) * 2.0F) * f * (1.0F - in / (deep + 1.0F)));
                    for (int h = floor; h <= Math.min(roof, top - 2); h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                }
                if (in < 1.6F) {
                    for (int h = base + 1; h <= Math.min(base + 9, top - 2); h++) {
                        if (Terrain.noise(seed + 7, x, h, z) > 0.84F) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    /** A waterhole at the foot of the rock: a flat pool dug into the plain, with its bank built up all round. */
    private void waterhole(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, int i, float pd, float n) {
        int water = pools[i * 4 + 3], r = pools[i * 4 + 2];
        if (pd < 0.0F) {
            float t = -pd / r;
            int depth = 1 + Math.round(2.0F * t);
            Terrain.clearAbove(level, pos, x, Math.max(ground, water), z, 8);
            for (int h = water + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
            for (int h = Math.min(ground + 1, water - depth - 1); h <= water - depth; h++) {
                Terrain.set(level, pos, x, h, z, n < 0.5F ? Blocks.CLAY.defaultBlockState() : Blocks.MUD.defaultBlockState());
            }
            for (int h = water - depth + 1; h <= water; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
            return;
        }
        // the bank: never lower than a block over the water
        int top = Math.max(ground, water + 1);
        Terrain.clearAbove(level, pos, x, top, z, 6);
        for (int h = ground + 1; h <= top; h++) Terrain.set(level, pos, x, h, z, Blocks.MUD.defaultBlockState());
        boolean grassy = n < 0.55F;
        Terrain.set(level, pos, x, top, z, grassy ? Blocks.GRASS_BLOCK.defaultBlockState() : n < 0.8F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.MUD.defaultBlockState());
        if (grassy && n < 0.3F) Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
    }
}
