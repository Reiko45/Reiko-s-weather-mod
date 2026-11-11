package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A lava dome, like the one that grew in the crater of Mount St Helens after 1980, or Novarupta in Alaska: lava so
 * stiff and sticky with silica that it couldn't run away downhill, squeezed up out of its vent like paste from a tube
 * and heaped up over it into a steep-sided, bulging mound. Its skin cooled and set while the dome went on swelling
 * underneath, so the crust split into great blocks and slabs that were shoved apart and tipped up on edge, and here and
 * there a spine of lava that had already gone solid was pushed straight up out of it like a broken tooth. Blocks keep
 * breaking off its steep flanks and tumbling down into a broad skirt of rubble round its foot. Its heart is still hot:
 * the cracks on top steam and are crusted yellow with sulphur, and deep down in one or two the rock still glows.
 *
 * @param base the level its height is measured from: the lie of the ground round its foot
 * @param low  the lowest ground sampled round it, and {@code top} the highest (for the bounds)
 */
public record LavaDome(int cx, int cz, int base, int low, int top, int radius, int height, long seed) implements Landform {
    /** How far out the skirt of fallen blocks runs, as a fraction of the dome's own radius. */
    private static final float APRON = 1.42F;
    /** The size of the blocks its crust has broken into. */
    private static final float CELL = 5.5F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 2) {
            return null;
        }
        int radius = 15 + c.random().nextInt(16);
        int height = Mth.clamp(Math.round(radius * (0.6F + 0.35F * c.random().nextFloat())), 15, 30);
        float turn = c.random().nextFloat() * Mth.TWO_PI;
        long seed = c.random().nextLong();
        // a fairly level base (a gentle slope will do): here, or a little way off
        for (int k = 0; k < 9; k++) {
            int ox = c.x(), oz = c.z();
            if (k > 0) {
                float a = turn + k * Mth.TWO_PI / 8.0F;
                ox += Math.round(Mth.cos(a) * 16.0F);
                oz += Math.round(Mth.sin(a) * 16.0F);
            }
            Landform dome = at(c, ox, oz, radius, height, seed);
            if (dome != null) return dome;
        }
        return null;
    }

    @Nullable
    private static Landform at(PlanContext c, int ox, int oz, int radius, int height, long seed) {
        int here = c.groundAt(ox, oz);
        if (here < c.seaLevel() + 2) {
            return null;
        }
        int low = here, high = here, sum = here;
        for (int i = 0; i < 12; i++) {
            // the four quarters first, so most spots that won't do are given up after a few samples
            float a = ((i % 4) * 3 + i / 4) * Mth.TWO_PI / 12.0F;
            int h = c.groundAt(ox + Math.round(Mth.cos(a) * radius), oz + Math.round(Mth.sin(a) * radius));
            low = Math.min(low, h);
            high = Math.max(high, h);
            sum += h;
            if (high - low > 15) return null;
        }
        int base = Math.round(sum / 13.0F);
        if (low < c.seaLevel() - 3 || !c.fits(base, 16, height + 20)) {
            return null;
        }
        return new LavaDome(ox, oz, base, low, high, radius, height, seed);
    }

    static Landform load(CompoundTag tag) {
        return new LavaDome(tag.getInt("x"), tag.getInt("z"), tag.getInt("base"), tag.getInt("low"), tag.getInt("top"), tag.getInt("r"), tag.getInt("height"),
                tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("base", base);
        tag.putInt("low", low);
        tag.putInt("top", top);
        tag.putInt("r", radius);
        tag.putInt("height", height);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.LAVA_DOME;
    }

    @Override
    public BoundingBox bounds() {
        int r = Mth.ceil(radius * 1.17F * APRON) + 2;
        return new BoundingBox(cx - r, low - 8, cz - r, cx + r, Math.max(top + 16, base + height + 18), cz + r);
    }

    private float wobble(float theta) {
        return 1.0F + 0.08F * Mth.sin(2.0F * theta + Terrain.hash(seed, 1) * Mth.TWO_PI) + 0.06F * Mth.sin(3.0F * theta + Terrain.hash(seed, 2) * Mth.TWO_PI)
                + 0.03F * Mth.sin(5.0F * theta + Terrain.hash(seed, 3) * Mth.TWO_PI);
    }

    /** The dome's shape, from 1 at the crown to 0 at its foot: broad and rounded on top, with steep, bulging flanks. */
    private static float profile(float d) {
        return d >= 1.0F ? 0.0F : (float) Math.pow(1.0 - Math.pow(d, 2.6), 0.55);
    }

    /** How far out from the middle a column is, as a fraction of the dome's radius that way. */
    private float out(double x, double z) {
        double dx = x - cx, dz = z - cz;
        return (float) Math.sqrt(dx * dx + dz * dz) / (radius * wobble((float) Mth.atan2(dz, dx)));
    }

    /**
     * Which block of the broken crust a column is in: {distance to its middle, distance to the next block's middle,
     * the block's grid cell x and z, its middle x and z}.
     */
    private float[] crust(int x, int z) {
        float px = (x + 0.5F) / CELL, pz = (z + 0.5F) / CELL;
        int gx = Mth.floor(px), gz = Mth.floor(pz);
        float f1 = Float.MAX_VALUE, f2 = Float.MAX_VALUE, bx = 0, bz = 0, mx = 0, mz = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int kx = gx + i, kz = gz + j;
                float jx = kx + 0.15F + 0.7F * Terrain.noise(seed + 30, kx, 0, kz), jz = kz + 0.15F + 0.7F * Terrain.noise(seed + 31, kx, 0, kz);
                float d = Mth.sqrt(Mth.square(px - jx) + Mth.square(pz - jz)) * CELL;
                if (d < f1) {
                    f2 = f1;
                    f1 = d;
                    bx = kx;
                    bz = kz;
                    mx = jx * CELL;
                    mz = jz * CELL;
                } else if (d < f2) {
                    f2 = d;
                }
            }
        }
        return new float[]{f1, f2, bx, bz, mx, mz};
    }

    /** How far a block of crust stands above the dome's smooth surface at a column: raised, and tipped up on one edge. */
    private float lift(float[] k, int x, int z) {
        int bx = (int) k[2], bz = (int) k[3];
        float rise = 2.2F * Terrain.noise(seed + 32, bx, 1, bz);
        float t = Terrain.noise(seed + 33, bx, 2, bz), tilt = t * t * 0.9F, way = Terrain.noise(seed + 34, bx, 3, bz) * Mth.TWO_PI;
        return rise + tilt * ((x + 0.5F - k[4]) * Mth.cos(way) + (z + 0.5F - k[5]) * Mth.sin(way));
    }

    private int spines() {
        return 3 + Math.round(Terrain.hash(seed, 40) * 4.0F);
    }

    /** The top of any spine standing over a column, or MIN_VALUE: {top, the lowest height it's solid at}. */
    private int[] spine(int x, int z) {
        int topY = Integer.MIN_VALUE, from = Integer.MAX_VALUE;
        for (int i = 0; i < spines(); i++) {
            float a = Terrain.hash(seed, 41 + i * 7) * Mth.TWO_PI, r = Mth.sqrt(Terrain.hash(seed, 42 + i * 7)) * 0.55F * radius;
            float sx = cx + Mth.cos(a) * r, sz = cz + Mth.sin(a) * r;
            if (Math.abs(x + 0.5F - sx) > 9.0F || Math.abs(z + 0.5F - sz) > 9.0F) continue;
            int sb = Math.round(base + height * profile(out(sx, sz))) - 2;
            int hs = 6 + Math.round(Terrain.hash(seed, 43 + i * 7) * 9.0F);
            float r0 = 1.6F + Terrain.hash(seed, 44 + i * 7) * 1.3F, lean = Terrain.hash(seed, 45 + i * 7) * 0.4F, way = Terrain.hash(seed, 46 + i * 7) * Mth.TWO_PI;
            for (int h = sb; h <= sb + hs; h++) {
                float frac = (h - sb) / (float) hs;
                float ox = sx + Mth.cos(way) * lean * (h - sb), oz = sz + Mth.sin(way) * lean * (h - sb);
                // jagged: it narrows and swells as it goes up, and breaks off short at the top
                float rr = r0 * (1.0F - 0.8F * (float) Math.pow(frac, 1.4)) * (0.8F + 0.4F * Terrain.noise(seed + 47, i, h, 0));
                if (Mth.square(x + 0.5F - ox) + Mth.square(z + 0.5F - oz) < rr * rr) {
                    topY = Math.max(topY, h);
                    from = Math.min(from, h);
                }
            }
        }
        return topY == Integer.MIN_VALUE ? null : new int[]{topY, from};
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float d = out(x + 0.5, z + 0.5);
                if (d > APRON) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground > b.maxY() - 4) continue;
                // the dome: its smooth swell, broken into blocks of crust with cracks between them, deepest on top
                float[] k = crust(x, z);
                float edge = k[1] - k[0];
                int dome = Integer.MIN_VALUE, crack = 0;
                if (d < 1.0F) {
                    float p = profile(d);
                    if (edge < 1.0F) {
                        crack = d < 0.45F ? 3 + Math.round(2.0F * Terrain.noise(seed + 35, (int) k[2], 4, (int) k[3])) : 1 + Math.round(Terrain.noise(seed + 36, x, 5, z));
                    }
                    dome = Math.round(base + height * p + lift(k, x, z) * Math.min(1.0F, p * 3.0F)) - crack;
                }
                // the skirt of blocks fallen from its flanks, with the odd big one
                int talus = ground;
                if (d > 0.8F) {
                    float t = 4.5F * Terrain.smooth((APRON - d) / 0.5F) * (0.5F + Terrain.field(seed + 40, x, z, 4.0));
                    if (d > 0.9F && Terrain.field(seed + 41, x, z, 2.5) > 0.75F) t += 1.5F;
                    talus = ground + Math.round(t);
                }
                int topY = Math.min(Math.max(dome, talus), b.maxY() - 1);
                int[] spine = spine(x, z);
                if (topY <= ground && spine == null) continue;
                int flood = Terrain.surface(level, pos, x, ground, z);
                int high = Math.max(ground, flood);
                if (high >= b.minY()) Terrain.clearAbove(level, pos, x, high, z, Math.min(14, b.maxY() - high));
                boolean onDome = dome > ground;
                // under the dome itself the old ground is buried in lava a little way down, so none of it shows at the foot
                int from = onDome && d < 0.97F ? ground - 2 : ground + 1;
                int start = Math.max(b.minY(), from);
                for (int h = start; h <= topY; h++) {
                    BlockState s = h <= dome ? lava(x, h, z, dome - h) : talus(x, h, z);
                    if (h == start && s.is(Blocks.GRAVEL)) s = Blocks.BLACKSTONE.defaultBlockState(); // (never loose over a hollow)
                    Terrain.set(level, pos, x, h, z, s);
                }
                if (spine != null) {
                    // (rooted in the crust, unless it's the overhang of a leaning spine)
                    for (int h = spine[1] - topY <= 4 ? topY + 1 : spine[1]; h <= Math.min(spine[0], b.maxY()); h++) {
                        float n = Terrain.noise(seed + 48, x, h, z);
                        Terrain.set(level, pos, x, h, z, n < 0.55F ? Blocks.POLISHED_BASALT.defaultBlockState() : n < 0.8F ? Blocks.BASALT.defaultBlockState()
                                : Blocks.BLACKSTONE.defaultBlockState());
                    }
                }
                if (onDome && d < 0.45F && dome == topY && (spine == null || spine[0] <= topY)) summit(level, pos, x, z, k, edge, crack, dome, d);
            }
        }
    }

    /** The crown: steaming cracks crusted with sulphur, vents breathing out of them, and the rock still glowing deep in one or two. */
    private void summit(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, float[] k, float edge, int crack, int dome, float d) {
        float n = Terrain.noise(seed + 50, x, dome, z);
        // the heat comes up in patches, not everywhere
        float heat = Terrain.field(seed + 52, x, z, 7.0);
        BlockState s = null;
        if (crack > 0) {
            boolean hot = crack >= 4 && d < 0.32F && Terrain.noise(seed + 51, (int) k[2], 6, (int) k[3]) < 0.2F;
            if (hot && n < 0.5F) s = Blocks.MAGMA_BLOCK.defaultBlockState();
            else if (heat > 0.45F) s = n < 0.22F ? ModBlocks.VOLCANIC_VENT.get().defaultBlockState() : n < 0.7F ? ModBlocks.STEAMING_GROUND.get().defaultBlockState()
                    : ModBlocks.SULFUR_CRUST.get().defaultBlockState();
            else if (n < 0.3F) s = ModBlocks.STEAMING_GROUND.get().defaultBlockState();
        } else if (edge < 2.0F && heat > 0.5F) {
            if (n < 0.35F) s = ModBlocks.SULFUR_CRUST.get().defaultBlockState();
            else if (n < 0.45F) s = ModBlocks.STEAMING_GROUND.get().defaultBlockState();
        } else if (heat > 0.7F && n < 0.3F) {
            s = ModBlocks.STEAMING_GROUND.get().defaultBlockState();
        }
        if (s != null) Terrain.set(level, pos, x, dome, z, s);
    }

    /** The dome's lava: a glassy black crust of blackstone and basalt, patched with obsidian; inside, the same rock with ashy tuff. */
    private BlockState lava(int x, int h, int z, int under) {
        float n = Terrain.noise(seed + 10, x, h, z);
        if (under <= 1) {
            if (Terrain.field(seed + 9, x, z, 5.0) > 0.85F && n < 0.7F) return Blocks.OBSIDIAN.defaultBlockState();
            return n < 0.32F ? Blocks.BLACKSTONE.defaultBlockState() : n < 0.55F ? Blocks.BASALT.defaultBlockState() : n < 0.7F ? Blocks.SMOOTH_BASALT.defaultBlockState()
                    : n < 0.82F ? Blocks.POLISHED_BASALT.defaultBlockState() : n < 0.92F ? Blocks.TUFF.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
        }
        return n < 0.45F ? Blocks.BLACKSTONE.defaultBlockState() : n < 0.75F ? Blocks.BASALT.defaultBlockState() : n < 0.9F ? Blocks.TUFF.defaultBlockState()
                : Blocks.SMOOTH_BASALT.defaultBlockState();
    }

    /** The rubble round its foot: broken blocks of the crust, with gravel ground out of them. */
    private BlockState talus(int x, int h, int z) {
        float n = Terrain.noise(seed + 20, x, h, z);
        return n < 0.28F ? Blocks.BLACKSTONE.defaultBlockState() : n < 0.46F ? Blocks.BASALT.defaultBlockState() : n < 0.6F ? Blocks.COBBLED_DEEPSLATE.defaultBlockState()
                : n < 0.72F ? Blocks.TUFF.defaultBlockState() : n < 0.84F ? Blocks.GRAVEL.defaultBlockState() : n < 0.95F ? Blocks.SMOOTH_BASALT.defaultBlockState()
                : Blocks.OBSIDIAN.defaultBlockState();
    }
}
