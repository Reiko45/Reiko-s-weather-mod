package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A spit: a long, narrow tongue of sand built out from the coast where the shore turns away and the longshore drift
 * carries on in a straight line, dropping its sand in deeper water. Waves bending round its end curl the tip back
 * toward the land in a hook. Low dunes run along its spine, held together by grass, and behind it lies a sheltered,
 * shallow bay where the water is calm enough for mud to settle.
 *
 * @param cx    the root of the spit, on the coast
 * @param angle the way out to sea
 * @param side  which way along the coast it grows (+1 or -1)
 * @param swing how sharply it heads away from the shore at first (radians)
 */
public record Spit(int cx, int cz, int sea, float angle, int side, int length, float swing, long seed) implements Landform {
    private static final int STEPS = 24;

    @Nullable
    static Landform plan(PlanContext c) {
        return c.search(12, 12, Spit::planAt);
    }

    @Nullable
    private static Landform planAt(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() - 2 || ground > c.seaLevel() + 3) {
            return null;
        }
        float seaward = c.seaward(24, 2);
        if (Float.isNaN(seaward)) {
            return null;
        }
        // it grows along the coast into open (but not deep) water, the way the drift carries the sand
        int side = c.random().nextBoolean() ? 1 : -1;
        for (int k = 0; k < 2; k++) {
            double[] p = Terrain.xz(18.0, side * 36.0, c.x(), c.z(), seaward);
            int h = c.groundAt(Mth.floor(p[0]), Mth.floor(p[1]));
            if (h < c.seaLevel() - 1 && h > c.seaLevel() - 18) {
                return new Spit(c.x(), c.z(), c.seaLevel(), seaward, side, 55 + c.random().nextInt(21), 1.0F + c.random().nextFloat() * 0.3F,
                        c.random().nextLong());
            }
            side = -side;
        }
        return null;
    }

    static Landform load(CompoundTag tag) {
        return new Spit(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getFloat("angle"), tag.getInt("side"), tag.getInt("length"),
                tag.getFloat("swing"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("sea", sea);
        tag.putFloat("angle", angle);
        tag.putInt("side", side);
        tag.putInt("length", length);
        tag.putFloat("swing", swing);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.SPIT;
    }

    @Override
    public BoundingBox bounds() {
        int minX = cx, maxX = cx, minZ = cz, maxZ = cz;
        for (float[] p : line()) {
            double[] w = Terrain.xz(p[0], p[1], cx, cz, angle);
            minX = Math.min(minX, Mth.floor(w[0]));
            maxX = Math.max(maxX, Mth.floor(w[0]));
            minZ = Math.min(minZ, Mth.floor(w[1]));
            maxZ = Math.max(maxZ, Mth.floor(w[1]));
        }
        int m = 16;
        return new BoundingBox(minX - m, sea - 40, minZ - m, maxX + m, sea + 10, maxZ + m);
    }

    /** The spit's spine from root to tip, as {u, v, s}: u out to sea from the root, v along the coast, s how far along. */
    private float[][] line() {
        float[][] p = new float[STEPS + 1][];
        float ds = length / (float) STEPS;
        float hook = 1.3F + 0.5F * Terrain.hash(seed, 3), hookLength = length * (0.15F + 0.06F * Terrain.hash(seed, 4));
        float u = -4.0F, v = 0.0F;
        p[0] = new float[]{u, v, 0.0F};
        for (int i = 1; i <= STEPS; i++) {
            float s = (i - 0.5F) * ds;
            // heading out from the shore at first, then running on along it, then curling round landward at the tip
            float theta = -swing * (1.0F - Terrain.smooth(s / (length * 0.6F))) + 0.12F * Mth.sin(s * 0.06F + Terrain.hash(seed, 5) * 6.0F)
                    + hook * Terrain.smooth((s - length + hookLength) / hookLength);
            u -= Mth.sin(theta) * ds;
            v += side * Mth.cos(theta) * ds;
            p[i] = new float[]{u, v, i * ds};
        }
        return p;
    }

    /** {distance from the spine, how far along it, which side of it (positive: the bay side)}. */
    private float[] nearest(float[][] line, float u, float v) {
        float best = Float.MAX_VALUE, along = 0.0F, sign = 0.0F;
        for (int i = 0; i < line.length - 1; i++) {
            float[] a = line[i], b = line[i + 1];
            float du = b[0] - a[0], dv = b[1] - a[1];
            float t = Mth.clamp(((u - a[0]) * du + (v - a[1]) * dv) / Math.max(1.0E-4F, du * du + dv * dv), 0.0F, 1.0F);
            float pu = a[0] + du * t, pv = a[1] + dv * t;
            float d = Mth.square(u - pu) + Mth.square(v - pv);
            if (d < best) {
                best = d;
                along = Mth.lerp(t, a[2], b[2]);
                // the landward normal of this stretch of spine: the bay lies that way
                sign = side * ((u - pu) * -dv + (v - pv) * du);
            }
        }
        return new float[]{Mth.sqrt(best), along, sign};
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float[][] line = line();
        BlockState sand = Blocks.SAND.defaultBlockState();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float[] near = nearest(line, l[0], l[1]);
                float d = near[0], s = near[1];
                boolean bay = near[2] > 0.0F;
                // 4-8 blocks wide, narrowing toward the tip
                float hw = (2.2F + 1.8F * Terrain.field(seed, s, 0.0, 20.0)) * (0.6F + 0.4F * Terrain.smooth((length - s) / 14.0F));
                if (d > hw + 14.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                float n = Terrain.noise(seed, x, sea, z);
                int top;
                BlockState surface;
                boolean grass = false;
                if (d < hw) {
                    // the spit: a low ridge of sand, its spine heaped into dunes and held by grass
                    float across = d / hw, dune = Terrain.field(seed + 1, x, z, 6.0);
                    float rise = (1.0F - across * across) * (0.8F + 2.6F * dune) * (0.45F + 0.55F * Terrain.smooth((length - s) / 20.0F));
                    top = Math.max(ground, sea - 1 + Math.round(rise));
                    grass = top > sea && dune > 0.5F && across < 0.6F;
                    surface = grass ? Blocks.GRASS_BLOCK.defaultBlockState() : n < 0.08F ? Blocks.GRAVEL.defaultBlockState() : sand;
                } else {
                    // under the water either side: shelving off to the sea bed, or the shallow, sheltered floor of the bay
                    int floor = ground;
                    if (bay) {
                        int shallow = sea - 2 - Math.round(Terrain.field(seed + 2, x, z, 8.0) * 2.0F);
                        if (ground < shallow) floor = Math.round(Mth.lerp(Terrain.smooth((14.0F - d) / 6.0F), ground, shallow));
                    }
                    float t = Terrain.smooth((d - hw) / (bay ? 4.0F : 7.0F));
                    top = Math.max(floor, Math.round(Mth.lerp(t, sea - 2, floor)));
                    boolean mud = bay && d < 13.0F && Terrain.field(seed + 3, x, z, 5.0) < 0.5F;
                    surface = mud ? Blocks.MUD.defaultBlockState() : sand;
                    if (top == ground && mud && ground < sea - 1 && ground > sea - 6) {
                        // silt settling out of the still water behind the spit
                        Terrain.set(level, pos, x, ground, z, surface);
                        continue;
                    }
                }
                if (top <= ground) continue; // it only ever builds up
                Terrain.clearAbove(level, pos, x, ground, z, top - ground + 6);
                for (int h = Math.max(ground + 1, b.minY()); h < top; h++) Terrain.set(level, pos, x, h, z, sand);
                Terrain.set(level, pos, x, top, z, surface);
                float m = Terrain.noise(seed + 7, x, top, z);
                if (grass && m > 0.93F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
                    Terrain.set(level, pos, x, top + 2, z, Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
                } else if (grass && m > 0.45F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                } else if (surface.is(Blocks.SAND) && top >= sea && m < 0.03F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                } else if (top < sea - 2 && m > 0.85F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SEAGRASS.defaultBlockState());
                }
            }
        }
    }
}
