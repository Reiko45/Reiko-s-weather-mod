package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * An inverted channel (like the ones round Green River in Utah, and many seen from orbit on Mars): a winding ridge that
 * was once a riverbed. The river laid gravel and cobbles along its bed, and water seeping through them afterwards cemented
 * them with lime and silica into a hard conglomerate, far tougher than the soft sands and muds of the plain round it. As
 * wind and rain wore the plain down, the old channel was left standing higher and higher, until its whole course, bends,
 * tributaries and all, stood up as a ridge capped with pebbly rock. Its softer sides wear into slopes under the cap, and
 * its foot is littered with blocks fallen from it.
 *
 * @param angle    the way the old river ran (downstream)
 * @param width    how wide the main channel was
 * @param height   how high the main ridge stands
 * @param base     the ground along the main ridge, at five points from its upstream end to its downstream one
 * @param branches {junction (along the main ridge), angle (hundredths of a radian), length, ground at its far end} for each tributary
 */
public record InvertedChannel(int cx, int cz, float angle, int length, int width, int height, int[] base, int[] branches, long seed)
        implements Landform {
    private static final int CAP = 2;
    /** The soft beds the plain was cut in, a block each from the bottom up. */
    private static final BlockState[] SOFT = {
            Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), Blocks.SAND.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
            Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), Blocks.WHITE_TERRACOTTA.defaultBlockState(),
            Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.SAND.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState()};
    private static final BlockState[] RED = {
            Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.RED_SAND.defaultBlockState(),
            Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState(), Blocks.WHITE_TERRACOTTA.defaultBlockState(),
            Blocks.TERRACOTTA.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.RED_TERRACOTTA.defaultBlockState(),
            Blocks.RED_SAND.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState()};

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 2 || !c.fits(ground, 12, 40) || c.relief(28, 8) > 10) {
            return null;
        }
        long seed = c.random().nextLong();
        float angle = c.random().nextFloat() * Mth.TWO_PI;
        int length = 60 + c.random().nextInt(101), width = 3 + c.random().nextInt(6), height = 4 + c.random().nextInt(9);
        int k = 1 + c.random().nextInt(3);
        int[] branches = new int[k * 4];
        boolean first = c.random().nextBoolean();
        for (int i = 0; i < k; i++) {
            // tributaries come in from upstream, at a slant, from either side in turn
            float side = (i % 2 == 0) == first ? 1.0F : -1.0F;
            branches[i * 4] = Math.round(length * (-0.3F + 0.65F * (i + c.random().nextFloat()) / k));
            branches[i * 4 + 1] = Math.round((angle + Mth.PI + side * (0.55F + 0.5F * c.random().nextFloat())) * 100.0F);
            branches[i * 4 + 2] = 20 + c.random().nextInt(31);
        }
        // as long as it can be while staying within reach of the middle
        int[] flat = new int[5];
        Arrays.fill(flat, ground);
        for (int tries = 0; tries < 12; tries++) {
            int reach = reach(new InvertedChannel(c.x(), c.z(), angle, length, width, height, flat, branches, seed), c.x(), c.z());
            if (reach <= 108) break;
            for (int i = 0; i < k; i++) {
                branches[i * 4] = branches[i * 4] * (length - 10) / length;
                branches[i * 4 + 2] = Math.max(14, branches[i * 4 + 2] - 4);
            }
            length = Math.max(60, length - 10);
        }
        // along the main ridge (it has to be dry land all the way, and fairly level)
        int[] base = new int[5];
        int lo = ground, hi = ground;
        for (int i = 0; i < 5; i++) {
            float u = -length / 2.0F + i * length / 4.0F;
            double[] p = Terrain.xz(u, wander(seed, u), c.x() + 0.5, c.z() + 0.5, angle);
            base[i] = c.groundAt(Mth.floor(p[0]), Mth.floor(p[1]));
            lo = Math.min(lo, base[i]);
            hi = Math.max(hi, base[i]);
        }
        if (lo < c.seaLevel() + 1 || hi - lo > 12) {
            return null;
        }
        int kept = 0;
        for (int i = 0; i < k; i++) {
            InvertedChannel probe = new InvertedChannel(c.x(), c.z(), angle, length, width, height, base, branches, seed);
            double[] end = probe.branchPoint(i, branches[i * 4 + 2]);
            int g = c.groundAt(Mth.floor(end[0]), Mth.floor(end[1]));
            if (g < c.seaLevel() + 1 || Math.abs(g - ground) > 12) continue;
            System.arraycopy(branches, i * 4, branches, kept * 4, 3);
            branches[kept * 4 + 3] = g;
            kept++;
        }
        if (kept == 0) {
            return null;
        }
        return new InvertedChannel(c.x(), c.z(), angle, length, width, height, base, Arrays.copyOf(branches, kept * 4), seed);
    }

    private static int reach(InvertedChannel l, int x, int z) {
        BoundingBox b = l.bounds();
        return Math.max(Math.max(x - b.minX(), b.maxX() - x), Math.max(z - b.minZ(), b.maxZ() - z));
    }

    static Landform load(CompoundTag tag) {
        return new InvertedChannel(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("width"), tag.getInt("height"),
                tag.getIntArray("base"), tag.getIntArray("branches"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putInt("width", width);
        tag.putInt("height", height);
        tag.put("base", new IntArrayTag(base));
        tag.put("branches", new IntArrayTag(branches));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.INVERTED_CHANNEL;
    }

    /** How far out from the line of a channel anything is built: its cap, its sloping sides and the scree off them. */
    private float margin() {
        return width * 0.6F + height * 1.6F / 1.15F + 8.0F;
    }

    @Override
    public BoundingBox bounds() {
        float m = margin();
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (float u = -length / 2.0F; u <= length / 2.0F + 3.9F; u += 4.0F) {
            float a = Math.min(u, length / 2.0F);
            double[] p = Terrain.xz(a, wander(seed, a), cx + 0.5, cz + 0.5, angle);
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minZ = Math.min(minZ, p[1]);
            maxZ = Math.max(maxZ, p[1]);
        }
        int lo = base[0], hi = base[0];
        for (int b : base) {
            lo = Math.min(lo, b);
            hi = Math.max(hi, b);
        }
        for (int i = 0; i < branches.length / 4; i++) {
            for (float s = 0.0F; s <= branches[i * 4 + 2] + 3.9F; s += 4.0F) {
                double[] p = branchPoint(i, Math.min(s, branches[i * 4 + 2]));
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minZ = Math.min(minZ, p[1]);
                maxZ = Math.max(maxZ, p[1]);
            }
            if (branches[i * 4 + 3] != 0) {
                lo = Math.min(lo, branches[i * 4 + 3]);
                hi = Math.max(hi, branches[i * 4 + 3]);
            }
        }
        return new BoundingBox(Mth.floor(minX - m), lo - 16, Mth.floor(minZ - m), Mth.ceil(maxX + m), hi + height * 2 + 30, Mth.ceil(maxZ + m));
    }

    /** How far the old river swung to one side, {@code u} blocks along its course: big lazy bends, with smaller wiggles in them. */
    private static float wander(long seed, float u) {
        float big = 5.0F + 5.0F * Terrain.hash(seed, 1), wave = 55.0F + 30.0F * Terrain.hash(seed, 2);
        float small = 1.5F + 2.0F * Terrain.hash(seed, 3), ripple = 18.0F + 10.0F * Terrain.hash(seed, 4);
        return big * Mth.sin(u * Mth.TWO_PI / wave + Terrain.hash(seed, 5) * 6.0F) + small * Mth.sin(u * Mth.TWO_PI / ripple + Terrain.hash(seed, 6) * 6.0F);
    }

    /** How fast the main channel swings across, per block along it (for measuring distances square to it). */
    private static float slope(long seed, float u) {
        return (wander(seed, u + 0.5F) - wander(seed, u - 0.5F));
    }

    /** Tributary {@code i}'s own gentler wandering, {@code s} blocks up it from where it joins. */
    private float branchWander(int i, float s) {
        float big = 1.5F + 2.5F * Terrain.hash(seed, 20 + i), wave = 24.0F + 16.0F * Terrain.hash(seed, 30 + i);
        return big * Mth.sin(s * Mth.TWO_PI / wave + Terrain.hash(seed, 40 + i) * 6.0F) * Terrain.smooth(s / 10.0F);
    }

    /** Where tributary {@code i} joins the main channel. */
    private double[] junction(int i) {
        float u = branches[i * 4];
        return Terrain.xz(u, wander(seed, u), cx + 0.5, cz + 0.5, angle);
    }

    /** The point {@code s} blocks up tributary {@code i}'s course. */
    private double[] branchPoint(int i, float s) {
        double[] j = junction(i);
        return Terrain.xz(s, branchWander(i, s), j[0], j[1], branches[i * 4 + 1] / 100.0F);
    }

    /** The ground along the main ridge, {@code u} blocks along it, from the heights seen at plan time. */
    private float baseAt(float u) {
        float t = Mth.clamp((u + length / 2.0F) / length * 4.0F, 0.0F, 4.0F);
        int i = Math.min(3, Mth.floor(t));
        return Mth.lerp(t - i, base[i], base[i + 1]);
    }

    /**
     * How high a channel's cap stands over this column's ground: about its own height, but levelling out the bumps in the
     * ground a little, as the old riverbed ran smoothly down its valley whatever the plain has been worn into since.
     */
    private static float rise(float h, float bed, int ground) {
        return Mth.clamp(h + 0.6F * (bed - ground), 0.5F * h, 1.6F * h);
    }

    /** The height of a channel's ridge over the ground {@code d} blocks from its line: a flat cap, its edge, and soft slopes under it. */
    private static float profile(float d, float halfWidth, float rise) {
        if (d <= halfWidth) return rise;
        float e = d - halfWidth;
        return rise - (e < 1.0F ? e * 2.5F : 2.5F + (e - 1.0F) * 1.15F);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float half = length / 2.0F, m = margin();
        int count = branches.length / 4;
        double[][] joins = new double[count][];
        for (int i = 0; i < count; i++) joins[i] = junction(i);
        // (an eroded gap through the main ridge, now and then)
        float breach = Terrain.hash(seed, 7) < 0.5F ? (Terrain.hash(seed, 8) - 0.5F) * length * 0.6F : Float.NaN;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float px = x + 0.5F, pz = z + 0.5F;
                // each channel here: {distance from its line, half width, height, ground under it}
                float[][] near = new float[count + 1][];
                float[] l = Terrain.uv(px, pz, cx + 0.5, cz + 0.5, angle);
                if (l[0] > -half - m && l[0] < half + m) {
                    float u = Mth.clamp(l[0], -half, half);
                    float k = slope(seed, u);
                    float d = Math.abs(l[1] - wander(seed, u)) / Mth.sqrt(1.0F + k * k) + Math.abs(l[0] - u);
                    if (d < m) {
                        float ends = Terrain.smooth((half - Math.abs(u)) / 12.0F);
                        float h = height * (0.8F + 0.35F * Terrain.field(seed + 1, u, 0.0, 24.0)) * ends;
                        if (!Float.isNaN(breach)) h *= Terrain.smooth((Math.abs(u - breach) - 3.0F) / 4.0F);
                        float w = width * 0.5F * (0.8F + 0.4F * Terrain.field(seed + 2, u, 0.0, 18.0)) * (0.5F + 0.5F * ends);
                        near[0] = new float[]{d, w, h, baseAt(u)};
                    }
                }
                for (int i = 0; i < count; i++) {
                    int len = branches[i * 4 + 2];
                    float[] t = Terrain.uv(px, pz, joins[i][0], joins[i][1], branches[i * 4 + 1] / 100.0F);
                    if (t[0] < -2.0F || t[0] > len + m) continue;
                    float s = Mth.clamp(t[0], 0.0F, len);
                    float d = Math.abs(t[1] - branchWander(i, s)) + Math.abs(t[0] - s);
                    if (d >= m) continue;
                    float h = Math.max(3.0F, height * 0.7F) * (0.85F + 0.3F * Terrain.field(seed + 3 + i, s, 0.0, 16.0)) * Terrain.smooth((len - s) / 8.0F);
                    float w = Math.max(1.2F, width * 0.3F);
                    float bed = Mth.lerp(s / len, baseAt(branches[i * 4]), branches[i * 4 + 3]);
                    near[i + 1] = new float[]{d, w, h, bed};
                }
                boolean any = false;
                for (float[] c : near) any |= c != null;
                if (!any) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 4 || ground > b.maxY() - 30) continue;
                // the channel standing highest here, and the scree off any nearby
                float top = 0.0F, cap = 0.0F, scree = 0.0F;
                for (float[] c : near) {
                    if (c == null || c[2] < 0.5F) continue;
                    float rise = rise(c[2], c[3], ground);
                    float p = profile(c[0], c[1], rise);
                    if (p > top) {
                        top = p;
                        cap = rise;
                    }
                    float foot = c[1] + (rise <= 2.5F ? rise / 2.5F : 1.0F + (rise - 2.5F) / 1.15F);
                    if (c[0] > foot && c[0] < foot + 3.5F) scree = Math.max(scree, 0.4F * (1.0F - (c[0] - foot) / 3.5F) * Math.min(1.0F, c[2] / 4.0F));
                }
                int surface = Terrain.surface(level, pos, x, ground, z);
                int peak = ground + Math.round(top);
                // where a pond or a stream lies, the ridge has been worn through (unless it stands well clear of the water)
                if (surface > ground && peak <= surface) continue;
                pos.set(x, ground, z);
                BlockState was = level.getBlockState(pos);
                boolean red = was.is(Blocks.RED_SAND) || level.getBiome(pos).is(BiomeTags.IS_BADLANDS);
                float n = Terrain.noise(seed, x, ground, z);
                if (peak <= ground) {
                    if (surface > ground) continue;
                    if (scree > 0.0F && Terrain.noise(seed + 4, x, 0, z) < scree) {
                        // blocks of conglomerate fallen from the cap, and sand washed off the slopes
                        Terrain.clearAbove(level, pos, x, ground, z, 4);
                        Terrain.set(level, pos, x, ground + 1, z, conglomerate(x, ground + 1, z, true));
                    } else if (scree > 0.0F && n > 0.93F && (was.is(BlockTags.SAND) || was.is(BlockTags.TERRACOTTA) || was.is(BlockTags.DIRT))) {
                        Terrain.clearAbove(level, pos, x, ground, z, 4);
                        Terrain.set(level, pos, x, ground + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                    }
                    continue;
                }
                Terrain.clearAbove(level, pos, x, ground, z, Math.min(peak - ground + 8, b.maxY() - ground));
                BlockState[] soft = red ? RED : SOFT;
                int capFrom = ground + Math.round(cap) - CAP + 1;
                int offset = Math.round(Terrain.hash(seed, 9) * soft.length);
                for (int h = ground; h <= peak; h++) {
                    BlockState s = h >= capFrom ? conglomerate(x, h, z, h > ground || level.getBlockState(pos.set(x, h - 1, z)).blocksMotion())
                            : soft[Math.floorMod(h + offset, soft.length)];
                    if (h == ground && s.is(BlockTags.SAND) && !level.getBlockState(pos.set(x, h - 1, z)).blocksMotion()) s = Blocks.SANDSTONE.defaultBlockState();
                    Terrain.set(level, pos, x, h, z, s);
                }
                // sand blown up onto the slopes and drifted on the cap here and there
                if (Terrain.field(seed + 5, x, z, 5.0) > 0.72F) {
                    Terrain.set(level, pos, x, peak, z, red ? Blocks.RED_SAND.defaultBlockState() : Blocks.SAND.defaultBlockState());
                    if (n > 0.9F) Terrain.set(level, pos, x, peak + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                }
            }
        }
    }

    /** The cemented gravel of the old riverbed: pebbles and cobbles set in a hard, muddy matrix. */
    private BlockState conglomerate(int x, int y, int z, boolean held) {
        float m = Terrain.noise(seed + 6, x, y, z);
        if (m < 0.32F) return Blocks.COBBLESTONE.defaultBlockState();
        if (m < 0.52F) return held ? Blocks.GRAVEL.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
        if (m < 0.78F) return Blocks.PACKED_MUD.defaultBlockState();
        return Blocks.TUFF.defaultBlockState();
    }
}
