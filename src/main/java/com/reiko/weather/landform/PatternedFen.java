package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.function.BiPredicate;

/**
 * A patterned fen (a string bog, or aapa mire): a broad, gently sloping peatland in the cold north, combed into long,
 * narrow ridges of moss and peat, the strings, lying across the slope like contour lines. Between them lie long
 * shallow pools, the flarks, each dammed by the string below it, so the fen comes down the slope in a staircase of
 * still water a hand's breadth at a time. How the pattern forms isn't fully known: water seeping slowly down through
 * the peat, frost heaving it each winter and the mosses growing fastest where it's driest all seem to play a part.
 * Dwarf shrubs and berries grow along the drier strings.
 *
 * @param angle  the way the slope falls
 * @param length how far the fen runs down the slope
 * @param width  how far it reaches either side of its middle
 * @param period how far apart the strings are
 * @param levels the water in the flark of each band, from the top of the slope down
 */
public record PatternedFen(int cx, int cz, float angle, int length, int width, int period, int[] levels, long seed) implements Landform {
    private static final int BLEND = 8;
    /** How much the strings bow down the slope in the middle. */
    private static final float BOW = 0.004F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 1 || !c.fits(ground, 12, 20)) {
            return null;
        }
        // a gentle slope: falling, but only a little
        float[] down = c.downhill(30);
        if (down[1] < 1 || down[1] > 10) {
            return null;
        }
        float angle = down[0];
        int length = 60 + c.random().nextInt(21), width = 24 + c.random().nextInt(9), period = 6 + c.random().nextInt(3);
        // and level across it
        if (Math.abs(at(c, angle, 0, -width * 0.8F) - ground) > 5 || Math.abs(at(c, angle, 0, width * 0.8F) - ground) > 5) {
            return null;
        }
        // the slope down the middle, never climbing again (the fen fills any hollow in it with peat)
        int[] profile = new int[7];
        for (int i = 0; i <= 6; i++) {
            int h = at(c, angle, -length / 2.0F + i * length / 6.0F, 0);
            if (h < c.seaLevel() + 1) return null;
            profile[i] = i == 0 ? h : Math.min(profile[i - 1], h);
        }
        if (profile[0] - profile[6] > 14) {
            return null;
        }
        int bands = Mth.ceil((length + BOW * width * width + 4.0F) / period) + 1;
        int[] levels = new int[bands];
        for (int k = 0; k < bands; k++) {
            float u = (k + 0.5F) * period - 2.0F - length / 2.0F;
            float t = Mth.clamp((u + length / 2.0F) / length * 6.0F, 0.0F, 6.0F);
            int i = Math.min(5, (int) t);
            levels[k] = Math.round(Mth.lerp(t - i, profile[i], profile[i + 1]));
            if (k > 0) levels[k] = Math.min(levels[k], levels[k - 1]);
        }
        return new PatternedFen(c.x(), c.z(), angle, length, width, period, levels, c.random().nextLong());
    }

    /** The ground {@code u} blocks down the slope and {@code v} across it from the middle. */
    private static int at(PlanContext c, float angle, float u, float v) {
        double[] p = Terrain.xz(u, v, c.x() + 0.5, c.z() + 0.5, angle);
        return c.groundAt(Mth.floor(p[0]), Mth.floor(p[1]));
    }

    static Landform load(CompoundTag tag) {
        return new PatternedFen(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("width"), tag.getInt("period"),
                tag.getIntArray("levels"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putInt("width", width);
        tag.putInt("period", period);
        tag.put("levels", new IntArrayTag(levels));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.PATTERNED_FEN;
    }

    @Override
    public BoundingBox bounds() {
        float half = length / 2.0F + 1.0F, out = width + 1.0F;
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
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int l : levels) {
            low = Math.min(low, l);
            high = Math.max(high, l);
        }
        return new BoundingBox(Mth.floor(minX) - 1, low - 9, Mth.floor(minZ) - 1, Mth.floor(maxX) + 1, high + 20, Mth.floor(maxZ) + 1);
    }

    private float[] uv(int x, int z) {
        return Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
    }

    /**
     * How far out a point lies against the fen's ragged, rounded edge: below 1 inside it, 1 on the edge. The outline is
     * a rounded oblong with a few broad lobes, never reaching past the oblong itself.
     */
    private float out(float u, float v) {
        float a = Math.abs(u) / (length / 2.0F), b = Math.abs(v) / width;
        float r = (float) Math.pow(Math.pow(a, 2.6) + Math.pow(b, 2.6), 1.0 / 2.6);
        float phi = (float) Math.atan2(v / width, u / (length / 2.0F));
        float edge = 0.86F + 0.08F * Mth.sin(3.0F * phi + Mth.TWO_PI * Terrain.hash(seed, 7)) + 0.06F * Mth.sin(5.0F * phi + Mth.TWO_PI * Terrain.hash(seed, 8));
        return r / edge;
    }

    private boolean inside(float u, float v) {
        return out(u, v) <= 1.0F;
    }

    /** 0 over the fen proper, rising to 1 at its edge, where it eases into the land round it. */
    private float blend(float u, float v) {
        float soft = (float) BLEND / width;
        return Mth.clamp((out(u, v) - (1.0F - soft)) / soft, 0.0F, 1.0F);
    }

    /** How far down the pattern a column is: the strings lie along lines of this, bowed and wavering a little. */
    private float band(int x, int z, float u, float v) {
        return u + length / 2.0F + 2.0F + BOW * v * v + 3.0F * (Terrain.field(seed, x, z, 12.0) - 0.5F);
    }

    /** The water level of band {@code k}'s flark. */
    private int level(int k) {
        return levels[Mth.clamp(k, 0, levels.length - 1)];
    }

    /** How wide the string at the foot of band {@code k} is. */
    private int string(int k) {
        return Terrain.hash(seed, 50 + k) < 0.4F ? 3 : 2;
    }

    /**
     * The plan for a column of the fen proper: {band, its top, 1 if it's in a flark's pool}. The strings stand a block
     * over the flark above them, and everything else in the band lies level with its water: the sedge lawns round the
     * pools, and the pools themselves (which only fill where {@link #wet} says).
     */
    private int[] shape(int x, int z, float u, float v) {
        float t = band(x, z, u, v);
        int k = Mth.floor(t / period);
        float tau = t - k * period;
        int sw = string(k), w = level(k);
        if (tau >= period - sw) return new int[]{k, w + 1, 0};
        // the flarks: long, narrow pools across the slope, broken here and there by a bridge of moss
        float reach = (0.55F + 0.4F * Terrain.hash(seed, 80 + k)) * (width - BLEND);
        boolean pool = tau > 0.7F && tau < period - sw - 0.5F && Math.abs(v) < reach && Terrain.field(seed + 3, x, z, 9.0) > 0.24F;
        return new int[]{k, w, pool ? 1 : 0};
    }

    /** The least height a column's ground (or water) stands at, if it's part of the fen proper (MIN_VALUE if it isn't). */
    private int lowest(int x, int z) {
        float[] l = uv(x, z);
        if (!inside(l[0], l[1]) || blend(l[0], l[1]) > 0.0F) return Integer.MIN_VALUE;
        return shape(x, z, l[0], l[1])[1];
    }

    /** Whether a flark's pool fills a column: only where nothing beside it is lower, so each pool is held in by its string. */
    private boolean wet(int x, int z, int[] s) {
        if (s[2] == 0) return false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (lowest(x + d.getStepX(), z + d.getStepZ()) < s[1]) return false;
        }
        return true;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState peat = ModBlocks.PEAT.get().defaultBlockState();
        BiPredicate<Integer, Integer> outside = (nx, nz) -> {
            float[] l = uv(nx, nz);
            return !inside(l[0], l[1]);
        };
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = uv(x, z);
                float u = l[0], v = l[1];
                if (!inside(u, v)) continue;
                int ground = Goosenecks.ground(level, pos, x, z);
                if (ground > b.maxY() - 3) continue;
                int standing = Math.min(b.maxY(), Goosenecks.standing(level, pos, x, ground, z));
                float t = blend(u, v);
                int[] s = shape(x, z, u, v);
                boolean string = s[1] > level(s[0]);
                // out at the edge the fen eases into the land round about (never with standing water)
                int top = t > 0.0F ? Math.round(Mth.lerp(t, s[1], ground)) : s[1];
                boolean wet = t == 0.0F && wet(x, z, s);
                int bed = wet ? top - 1 - (Terrain.field(seed + 4, x, z, 4.0) > 0.6F ? 1 : 0) : top;
                if (t >= 1.0F && top == ground && standing == ground) continue; // (the edge, where it meets the land unchanged)
                if (top != ground || wet || standing > ground || t < 1.0F) {
                    Terrain.clearAbove(level, pos, x, ground, z, Math.min(12, b.maxY() - ground));
                    for (int h = top + 1; h <= standing; h++) {
                        // (a pond just outside keeps a bank of peat between it and the fen)
                        Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, outside) ? peat : Blocks.AIR.defaultBlockState());
                    }
                }
                // peat under it all, filling any hollow down to below the pools
                for (int h = Math.max(b.minY(), Math.min(bed - 4, levels[levels.length - 1] - 4)); h < bed; h++) {
                    BlockState here = level.getBlockState(pos.set(x, h, z));
                    if (h >= bed - 4 || h > ground || here.isAir() || !here.getFluidState().isEmpty() || Terrain.cover(here)) {
                        Terrain.set(level, pos, x, h, z, h == bed - 4 && h <= ground ? Blocks.CLAY.defaultBlockState() : peat);
                    }
                }
                float n = Terrain.noise(seed, x, top, z), m = Terrain.noise(seed + 1, x, top, z);
                if (wet) {
                    Terrain.set(level, pos, x, bed, z, n < 0.6F ? Blocks.MUD.defaultBlockState() : peat);
                    for (int h = bed + 1; h <= top; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                    if (n > 0.96F) Terrain.set(level, pos, x, top + 1, z, Blocks.LILY_PAD.defaultBlockState());
                    continue;
                }
                float patch = Terrain.field(seed + 2, x, z, 5.0);
                if (string && t < 1.0F) {
                    // the strings: hummocks of moss and peat, dry enough for shrubs and berries
                    Terrain.set(level, pos, x, top, z, patch > 0.55F ? Blocks.MOSS_BLOCK.defaultBlockState() : patch > 0.3F ? Blocks.GRASS_BLOCK.defaultBlockState()
                            : Blocks.PODZOL.defaultBlockState());
                    if (m < 0.1F) {
                        Terrain.set(level, pos, x, top + 1, z, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 1 + Math.round(n * 2.0F)));
                    } else if (m < 0.3F) {
                        Terrain.set(level, pos, x, top + 1, z, Blocks.FERN.defaultBlockState());
                    } else if (m < 0.65F) {
                        Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                    }
                } else if (t < 1.0F) {
                    // the wet lawns of sedge and moss round the pools
                    Terrain.set(level, pos, x, top, z, patch > 0.6F ? Blocks.MUD.defaultBlockState() : patch > 0.35F ? Blocks.MOSS_BLOCK.defaultBlockState() : peat);
                    if (m < 0.18F) Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                    else if (m > 0.97F && patch > 0.35F && patch <= 0.6F) Terrain.set(level, pos, x, top + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                } else if (top != ground) {
                    Terrain.set(level, pos, x, top, z, Blocks.GRASS_BLOCK.defaultBlockState());
                }
            }
        }
    }
}
