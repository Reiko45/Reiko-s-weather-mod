package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * A young lava field: a sheet of black lava that welled out of the ground only a few hundred years ago and spread
 * across the country in broad lobes, burying it a few feet deep. Its skin is still bare, glassy ropes of pahoehoe and
 * rough clinker at the margins, and it's dotted with the things such flows leave behind: tumuli, low domes pushed up
 * by lava still moving underneath the crust, which split open along the top as they swelled; hornitos, little
 * chimneys of spatter built up round holes where gas and lava spurted through the crust; lava trees, hollow stone
 * moulds where the flow chilled round a tree trunk that then burned away; and a skylight or two, where the roof of a
 * drained lava tube has fallen in, opening a way down into the tunnel that fed the flow. At its far end a little
 * lava still glows.
 *
 * @param tumuli   {x, z, radius, height, angle in hundredths of a radian} for each tumulus
 * @param hornitos {x, z, height} for each hornito
 * @param trees    {x, z, height} for each lava tree
 * @param tubes    {x, z, angle in hundredths of a radian, length, floor y} for each lava tube
 */
public record LavaField(int cx, int cz, int y, int radius, int[] tumuli, int[] hornitos, int[] trees, int[] tubes, long seed) implements Landform {
    /** A lava tube's half-width inside. */
    private static final float TUBE = 2.5F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 2 || !c.fits(ground, 24, 40)) {
            return null;
        }
        int radius = 35 + c.random().nextInt(11);
        if (c.relief(radius, 12) > 12) {
            return null;
        }
        long seed = c.random().nextLong();
        LavaField probe = new LavaField(c.x(), c.z(), ground, radius, new int[0], new int[0], new int[0], new int[0], seed);
        int nt = 4 + c.random().nextInt(5), k = 0;
        int[] tumuli = new int[nt * 5];
        for (int i = 0; i < nt * 3 && k < nt; i++) {
            int[] p = probe.spot(c, 0.3F);
            if (p == null) continue;
            tumuli[k * 5] = p[0];
            tumuli[k * 5 + 1] = p[1];
            tumuli[k * 5 + 2] = 4 + c.random().nextInt(4);
            tumuli[k * 5 + 3] = 2 + c.random().nextInt(3);
            tumuli[k * 5 + 4] = Math.round(c.random().nextFloat() * Mth.PI * 100.0F);
            k++;
        }
        tumuli = Arrays.copyOf(tumuli, k * 5);
        int nh = 3 + c.random().nextInt(4);
        int[] hornitos = new int[nh * 3];
        k = 0;
        for (int i = 0; i < nh * 3 && k < nh; i++) {
            int[] p = probe.spot(c, 0.25F);
            if (p == null) continue;
            hornitos[k * 3] = p[0];
            hornitos[k * 3 + 1] = p[1];
            hornitos[k * 3 + 2] = 2 + c.random().nextInt(4);
            k++;
        }
        hornitos = Arrays.copyOf(hornitos, k * 3);
        int nl = 4 + c.random().nextInt(6);
        int[] trees = new int[nl * 3];
        k = 0;
        for (int i = 0; i < nl * 3 && k < nl; i++) {
            int[] p = probe.spot(c, 0.15F);
            if (p == null) continue;
            trees[k * 3] = p[0];
            trees[k * 3 + 1] = p[1];
            trees[k * 3 + 2] = 2 + c.random().nextInt(3);
            k++;
        }
        trees = Arrays.copyOf(trees, k * 3);
        // the lava tubes run a few blocks under the ground, level, so only where the ground over them is fairly even
        int nu = 1 + c.random().nextInt(2);
        int[] tubes = new int[nu * 5];
        k = 0;
        for (int i = 0; i < 4 && k < nu; i++) {
            int[] p = probe.spot(c, 0.45F);
            if (p == null || k > 0 && Mth.square(p[0] - tubes[0]) + Mth.square(p[1] - tubes[1]) < 400) continue;
            float a = c.random().nextFloat() * Mth.PI;
            int length = 14 + c.random().nextInt(7);
            double[] e1 = Terrain.xz(-length / 2.0, 0.0, p[0] + 0.5, p[1] + 0.5, a), e2 = Terrain.xz(length / 2.0, 0.0, p[0] + 0.5, p[1] + 0.5, a);
            if (probe.inside(e1[0], e1[1]) < 0.3F || probe.inside(e2[0], e2[1]) < 0.3F) continue;
            int g0 = c.groundAt(p[0], p[1]), g1 = c.groundAt(Mth.floor(e1[0]), Mth.floor(e1[1])), g2 = c.groundAt(Mth.floor(e2[0]), Mth.floor(e2[1]));
            int lo = Math.min(g0, Math.min(g1, g2)), hi = Math.max(g0, Math.max(g1, g2));
            if (hi - lo > 5) continue;
            tubes[k * 5] = p[0];
            tubes[k * 5 + 1] = p[1];
            tubes[k * 5 + 2] = Math.round(a * 100.0F);
            tubes[k * 5 + 3] = length;
            tubes[k * 5 + 4] = lo - 7;
            k++;
        }
        tubes = Arrays.copyOf(tubes, k * 5);
        return new LavaField(c.x(), c.z(), ground, radius, tumuli, hornitos, trees, tubes, seed);
    }

    /** A random spot at least {@code depth} into the flow, or null. */
    @Nullable
    private int[] spot(PlanContext c, float depth) {
        float a = c.random().nextFloat() * Mth.TWO_PI, d = Mth.sqrt(c.random().nextFloat()) * radius * 0.95F;
        int x = cx + Math.round(Mth.cos(a) * d), z = cz + Math.round(Mth.sin(a) * d);
        return inside(x + 0.5, z + 0.5) >= depth ? new int[]{x, z} : null;
    }

    static Landform load(CompoundTag tag) {
        return new LavaField(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getIntArray("tumuli"), tag.getIntArray("hornitos"),
                tag.getIntArray("trees"), tag.getIntArray("tubes"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.put("tumuli", new IntArrayTag(tumuli));
        tag.put("hornitos", new IntArrayTag(hornitos));
        tag.put("trees", new IntArrayTag(trees));
        tag.put("tubes", new IntArrayTag(tubes));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.LAVA_FIELD;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * 1.15F) + 3, low = y - 14;
        for (int i = 0; i < tubes.length; i += 5) low = Math.min(low, tubes[i + 4] - 2);
        return new BoundingBox(cx - r, low, cz - r, cx + r, y + 50, cz + r);
    }

    /** How far into the flow a point is: 1 in the middle, 0 at its lobed edge, below 0 outside it. */
    private float inside(double x, double z) {
        double dx = x - cx, dz = z - cz;
        float theta = (float) Math.atan2(dz, dx);
        float edge = radius * (0.82F + 0.12F * Mth.sin(3.0F * theta + Terrain.hash(seed, 1) * Mth.TWO_PI)
                + 0.07F * Mth.sin(5.0F * theta + Terrain.hash(seed, 2) * Mth.TWO_PI) + 0.22F * (Terrain.field(seed + 1, x, z, 14.0) - 0.5F));
        return 1.0F - (float) Math.sqrt(dx * dx + dz * dz) / edge;
    }

    /** Where tube {@code t}'s skylight is: near its upper end, well away from the lava at the other. */
    private double[] skylight(int t) {
        return Terrain.xz(-tubes[t * 5 + 3] / 2.0 + 2.5, 0.0, tubes[t * 5] + 0.5, tubes[t * 5 + 1] + 0.5, tubes[t * 5 + 2] / 100.0F);
    }

    /**
     * Whether tube {@code t}'s skylight can open here: never where water stands anywhere round it (a stream, a pond or
     * a flooded cave), which would pour down the hole. Nothing this landform builds adds or takes away water up there,
     * so every chunk the skylight touches comes to the same answer.
     */
    private boolean open(WorldGenLevel level, BoundingBox area, BoundingBox b, int t) {
        double[] s = skylight(t);
        int sx = Mth.floor(s[0]), sz = Mth.floor(s[1]);
        if (sx + 4 < area.minX() || sx - 4 > area.maxX() || sz + 4 < area.minZ() || sz - 4 > area.maxZ()) return false;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int h = tubes[t * 5 + 4] + 5; h <= b.maxY(); h++) {
                    if (!level.getFluidState(p.set(sx + dx, h, sz + dz)).isEmpty()) return false;
                }
            }
        }
        return true;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean[] open = new boolean[tubes.length / 5];
        for (int t = 0; t < open.length; t++) open[t] = open(level, area, b, t);
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float f = inside(x + 0.5, z + 0.5);
                if (f <= 0.0F && !nearTube(x, z)) continue;
                int ground = Terrain.ground(level, pos, x, z);
                // (and under any water, which leaves overhanging it can hide from Terrain.ground)
                while (ground > b.minY() && !level.getFluidState(pos.set(x, ground, z)).isEmpty()) ground--;
                // it doesn't flow into rivers or ponds (nor down ravines, nor up over hills); the tubes are always sealed in
                boolean wet = !level.getFluidState(pos.set(x, ground + 1, z)).isEmpty();
                boolean flows = f > 0.0F && !wet && ground >= b.minY() + 2 && ground <= b.maxY() - 26;
                int top = flows ? flow(level, pos, b, x, z, f, ground) : ground;
                for (int t = 0; t < open.length; t++) tube(level, pos, x, z, t, ground, top, open[t] && !wet);
            }
        }
    }

    private boolean nearTube(int x, int z) {
        for (int t = 0; t < tubes.length / 5; t++) {
            float[] l = Terrain.uv(x + 0.5, z + 0.5, tubes[t * 5] + 0.5, tubes[t * 5 + 1] + 0.5, tubes[t * 5 + 2] / 100.0F);
            if (Math.abs(l[0]) <= tubes[t * 5 + 3] / 2.0F + 1.5F && Math.abs(l[1]) <= TUBE + 1.5F) return true;
        }
        return false;
    }

    /** Lays the lava over a column, with whatever stands on it there; returns the top of it. */
    private int flow(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, float f, int ground) {
        // tumuli: domes split along their crests
        float dome = 0.0F;
        boolean cleft = false;
        for (int i = 0; i < tumuli.length; i += 5) {
            float[] l = Terrain.uv(x + 0.5, z + 0.5, tumuli[i] + 0.5, tumuli[i + 1] + 0.5, tumuli[i + 4] / 100.0F);
            float r = tumuli[i + 2];
            float e = Mth.sqrt(Mth.square(l[0] / r) + Mth.square(l[1] / (r * 0.65F)));
            if (e >= 1.0F) continue;
            float h = tumuli[i + 3] * (1.0F - e * e);
            if (h > dome) {
                dome = h;
                cleft = Math.abs(l[1]) < 0.75F && e < 0.7F;
            }
        }
        // hornitos and lava trees: a ring of rock round a hollow, standing up off the flow
        int rise = 0, hollow = Integer.MAX_VALUE;
        boolean spatter = false, mould = false, hornito = false;
        for (int i = 0; i < hornitos.length; i += 3) {
            int dx = x - hornitos[i], dz = z - hornitos[i + 1];
            if (Math.abs(dx) > 1 || Math.abs(dz) > 1) continue;
            spatter = true;
            if (dx == 0 && dz == 0) {
                hornito = true;
                rise = Math.max(rise, hornitos[i + 2]);
            } else {
                rise = Math.max(rise, hornitos[i + 2] - (dx != 0 && dz != 0 ? 1 : 0) - (Terrain.noise(seed, x, 4, z) < 0.3F ? 1 : 0));
            }
        }
        boolean trunk = false;
        for (int i = 0; i < trees.length; i += 3) {
            int dx = x - trees[i], dz = z - trees[i + 1];
            if (Math.abs(dx) > 1 || Math.abs(dz) > 1) continue;
            mould = true;
            if (dx == 0 && dz == 0) trunk = true;
            rise = Math.max(rise, trees[i + 2] - (dx != 0 && dz != 0 && Terrain.noise(seed, x, 5, z) < 0.5F ? 1 : 0));
        }
        // a few blocks thick in the middle, thinning to a low, lobed front
        int thick = 1 + Math.round(3.0F * Terrain.smooth(f / 0.4F) * (0.65F + 0.35F * Terrain.field(seed + 2, x, z, 6.0)));
        if (spatter) thick = Math.max(thick, 2);
        int base = ground + thick;
        int top = base + (rise > 0 ? rise : Math.max(0, Math.round(dome) - (cleft ? 2 : 0)));
        if (hornito) hollow = base - 1; // the vent, open down into the flow
        if (trunk) hollow = ground + 1; // the mould, open right down to where the tree stood
        Terrain.clearAbove(level, pos, x, ground, z, Math.min(28, b.maxY() - ground));
        // ropes of lava lie across the way it flowed, out from the middle
        float dx = x + 0.5F - cx, dz = z + 0.5F - cz;
        Direction.Axis across = Math.abs(dx) > Math.abs(dz) ? Direction.Axis.Z : Direction.Axis.X;
        boolean rope = f > 0.12F && dome < 0.5F && Mth.sin(Mth.sqrt(dx * dx + dz * dz) * 1.3F + 6.0F * Terrain.field(seed + 5, x, z, 7.0)) > 0.6F;
        float n = Terrain.noise(seed, x, 0, z);
        for (int h = ground; h <= top; h++) {
            BlockState s;
            if (h >= hollow) {
                s = Blocks.AIR.defaultBlockState();
            } else if (hornito && h == hollow - 1) {
                s = Blocks.MAGMA_BLOCK.defaultBlockState(); // still glowing at the bottom of the vent
            } else if (spatter && h > base) {
                float m = Terrain.noise(seed + 7, x, h, z);
                s = m < 0.25F ? Blocks.RED_TERRACOTTA.defaultBlockState() : m < 0.4F ? Blocks.BROWN_TERRACOTTA.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
            } else if (mould && h > base) {
                s = Terrain.noise(seed + 8, x, h, z) < 0.6F ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
            } else if (h == top && f < 0.08F) {
                // clinker: the rough, broken rubble along the flow front
                s = n < 0.4F ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : n < 0.75F ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.BASALT.defaultBlockState();
            } else if (h == top && rope) {
                s = Blocks.POLISHED_BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, across);
            } else if (h == top) {
                s = n < 0.5F ? Blocks.BASALT.defaultBlockState() : n < 0.8F ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState();
            } else {
                s = Terrain.noise(seed + 9, x, h, z) < 0.55F ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
            }
            Terrain.set(level, pos, x, h, z, s);
        }
        return top;
    }

    /**
     * The part of tube {@code t} under a column: a low tunnel with glazed walls, sealed all round in rock, and at its
     * lower end a little pool of lava sunk in the floor, which can never spread: rock holds it on every side and below,
     * and nothing that burns is anywhere near.
     */
    private void tube(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int t, int ground, int top, boolean open) {
        int o = t * 5, floor = tubes[o + 4];
        float half = tubes[o + 3] / 2.0F;
        float[] l = Terrain.uv(x + 0.5, z + 0.5, tubes[o] + 0.5, tubes[o + 1] + 0.5, tubes[o + 2] / 100.0F);
        float u = l[0], v = Math.abs(l[1]);
        if (Math.abs(u) > half + 1.5F || v > TUBE + 1.5F) return;
        // (where the ground over it dips too low, the tunnel is choked with rock instead)
        boolean hollow = Math.abs(u) <= half && v < TUBE && ground >= floor + 6;
        int ceil = hollow ? 1 + Math.round(2.0F * Mth.sqrt(Math.max(0.0F, 1.0F - Mth.square(v / TUBE)))) : 0;
        boolean pool = hollow && Math.abs(u - (half - 2.0F)) <= 1.2F && v <= 1.2F;
        double[] s = skylight(t);
        float sd = Mth.sqrt(Mth.square((float) (x + 0.5 - s[0])) + Mth.square((float) (z + 0.5 - s[1])));
        boolean sky = open && hollow && sd < 1.6F && top > ground;
        for (int h = floor - 1; h <= floor + 4; h++) {
            BlockState b;
            if (hollow && h > floor && h <= floor + ceil) b = Blocks.AIR.defaultBlockState();
            else if (pool && h == floor) b = Blocks.LAVA.defaultBlockState();
            else if (hollow && h == floor) b = Blocks.SMOOTH_BASALT.defaultBlockState(); // the floor, where the last lava drained away
            else b = Terrain.noise(seed + 11, x, h, z) < 0.5F ? Blocks.SMOOTH_BASALT.defaultBlockState() : Blocks.BASALT.defaultBlockState();
            Terrain.set(level, pos, x, h, z, b);
        }
        if (sky) {
            for (int h = floor + 1; h <= top; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
        } else if (open && sd < 2.6F && top > ground && hollow) {
            Terrain.set(level, pos, x, top, z, Blocks.COBBLED_DEEPSLATE.defaultBlockState()); // the broken edge of the hole
        }
    }
}
