package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.function.BiPredicate;

/**
 * A frost-shattered summit, a felsenmeer ("sea of rocks"): a high, broad mountaintop where the bedrock has been split
 * apart by frost into a blanket of angular boulders, like the summit plateaus of the Cairngorms, the tops of the
 * Glyderau in Snowdonia or the summit of Mount Washington in New Hampshire. Through thousands of cold winters,
 * meltwater seeped into every joint and crack of the rock, froze and swelled, and wedged the pieces a little further
 * apart each time, until the whole top of the mountain was heaved up into a jumble of loose blocks lying where they
 * broke. Nothing much grows on it: the soil has nowhere to gather but the hollows between the stones, where old snow
 * lingers into summer. Here and there frost has heaved a slab up onto its end, and where the summit starts to fall
 * away, freezing and thawing have sorted the stones from the finer grit into stripes running straight down the slope.
 *
 * @param angle  the way the summit's long axis runs (along the ridge, if it's on one)
 * @param radius half the summit's width across that
 * @param length half its length along it
 * @param crest  the ground in the middle of the summit
 * @param low    the lowest ground sampled (for the bounds)
 * @param high   the highest ground sampled (for the bounds)
 */
public record FrostShatteredSummit(int cx, int cz, float angle, int radius, int length, int crest, int low, int high, long seed) implements Landform {
    /** How far the scattered stones and the stripes run on beyond the boulder field, as a share of its size. */
    private static final float FRINGE = 1.18F;
    private static final int[][] AXES = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 30 || !c.fits(ground, 24, 40)) {
            return null;
        }
        // the lie of the land round about, every 12 blocks out to 36 each way
        int[][] g = new int[7][7];
        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 7; j++) g[i][j] = c.groundAt(c.x() + 12 * (i - 3), c.z() + 12 * (j - 3));
        }
        // the flattest high spot: high above the sea, fairly level for a dozen blocks round, and falling away no more
        // than a little further out (a shelf on a mountainside, with the peak towering over it, won't do either)
        float best = -1.0E9F;
        int bi = -1, bj = -1, lo = 0, hi = 0;
        for (int i = 1; i < 6; i++) {
            for (int j = 1; j < 6; j++) {
                int h = g[i][j];
                if (h < c.seaLevel() + 44 || !c.fits(h, 24, 40)) continue;
                int min = h, max = h, far = h, rise = h;
                for (int di = -2; di <= 2; di++) {
                    for (int dj = -2; dj <= 2; dj++) {
                        int a = i + di, b = j + dj;
                        if (a < 0 || b < 0 || a > 6 || b > 6) continue;
                        if (Math.abs(di) <= 1 && Math.abs(dj) <= 1) {
                            min = Math.min(min, g[a][b]);
                            max = Math.max(max, g[a][b]);
                        } else {
                            far = Math.min(far, g[a][b]);
                            rise = Math.max(rise, g[a][b]);
                        }
                    }
                }
                if (max - min > 12 || h - Math.min(far, min) > 22 || Math.max(rise, max) - h > 12) continue;
                float score = -(max - min) - 0.35F * (h - Math.min(far, min)) - 0.3F * Math.max(0, Math.max(rise, max) - h)
                        - 0.8F * (Math.abs(i - 3) + Math.abs(j - 3)) + 0.05F * (h - c.seaLevel());
                if (score > best) {
                    best = score;
                    bi = i;
                    bj = j;
                    lo = Math.min(far, min);
                    hi = Math.max(rise, max);
                }
            }
        }
        if (bi < 0) {
            return null;
        }
        // its long axis runs the way the ground stays highest (along a ridge), and it's longer the more of a ridge it is
        int along = 0, keep = Integer.MIN_VALUE, worst = Integer.MAX_VALUE;
        for (int k = 0; k < AXES.length; k++) {
            int ends = Math.min(g[bi + AXES[k][0]][bj + AXES[k][1]], g[bi - AXES[k][0]][bj - AXES[k][1]]);
            if (ends > keep) {
                keep = ends;
                along = k;
            }
            worst = Math.min(worst, ends);
        }
        float angle = (float) Mth.atan2(AXES[along][1], AXES[along][0]) + (c.random().nextFloat() - 0.5F) * 0.4F;
        int radius = 20 + c.random().nextInt(12);
        int length = Math.min(35, Math.round(radius * (1.0F + Mth.clamp((keep - worst) / 12.0F, 0.0F, 0.45F))));
        return new FrostShatteredSummit(c.x() + 12 * (bi - 3), c.z() + 12 * (bj - 3), angle, radius, length, g[bi][bj], lo, hi, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new FrostShatteredSummit(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("radius"), tag.getInt("length"),
                tag.getInt("crest"), tag.getInt("low"), tag.getInt("high"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("radius", radius);
        tag.putInt("length", length);
        tag.putInt("crest", crest);
        tag.putInt("low", low);
        tag.putInt("high", high);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.FROST_SHATTERED_SUMMIT;
    }

    @Override
    public BoundingBox bounds() {
        // (the ragged edge reaches out to 1.12 of the size, and the fringe beyond it)
        float a = length * FRINGE * 1.12F + 2.0F, r = radius * FRINGE * 1.12F + 2.0F;
        double c = Math.abs(Math.cos(angle)), s = Math.abs(Math.sin(angle));
        int ex = Mth.ceil(a * c + r * s), ez = Mth.ceil(a * s + r * c);
        return new BoundingBox(cx - ex, low - 12, cz - ez, cx + ex, high + 30, cz + ez);
    }

    /** How far out a column lies, as a share of the boulder field's ragged edge (under 1 inside it). */
    private float out(int x, int z) {
        float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
        return Mth.sqrt(Mth.square(l[0] / length) + Mth.square(l[1] / radius)) / (0.88F + 0.24F * Terrain.field(seed + 1, x, z, 10.0));
    }

    private static boolean skipped(int ground, BoundingBox b) {
        return ground >= b.maxY() - 8 || ground <= b.minY() + 7;
    }

    /** Whether a column is in the boulder field itself (where it fills and lowers the ground), not just its fringe. */
    private boolean field(int x, int z) {
        return out(x, z) < 1.0F;
    }

    /**
     * The boulder lying over a column: its height (0 for a gap between them) in the low bits, and which rock it is above
     * them. Each 3-block cell holds one angular block of rock, or none, set a little off its middle.
     */
    private int boulder(int x, int z) {
        int best = 0, kind = 0;
        for (int i = Math.floorDiv(x, 3) - 1; i <= Math.floorDiv(x, 3) + 1; i++) {
            for (int j = Math.floorDiv(z, 3) - 1; j <= Math.floorDiv(z, 3) + 1; j++) {
                if (Terrain.noise(seed + 30, i, 1, j) > 0.88F) continue;
                float bx = i * 3 + 1.5F + (Terrain.noise(seed + 31, i, 1, j) - 0.5F) * 1.8F, bz = j * 3 + 1.5F + (Terrain.noise(seed + 32, i, 1, j) - 0.5F) * 1.8F;
                float ax = 0.7F + 1.2F * Terrain.noise(seed + 33, i, 1, j), az = 0.7F + 1.2F * Terrain.noise(seed + 34, i, 1, j);
                float dx = Math.abs(x + 0.5F - bx), dz = Math.abs(z + 0.5F - bz);
                if (dx > ax || dz > az) continue;
                int h = Terrain.noise(seed + 35, i, 1, j) < 0.45F ? 2 : 1;
                // (the top of a big one broken off unevenly at its edges)
                if (h == 2 && (dx > ax - 0.7F || dz > az - 0.7F) && Terrain.noise(seed + 37, x, 2, z) < 0.6F) h = 1;
                if (h > best) {
                    best = h;
                    kind = (int) (Terrain.noise(seed + 36, i, 1, j) * 99.99F);
                }
            }
        }
        return best == 0 ? 0 : best | kind << 2;
    }

    private static BlockState stone(int kind) {
        if (kind < 30) return Blocks.STONE.defaultBlockState();
        if (kind < 58) return Blocks.ANDESITE.defaultBlockState();
        if (kind < 80) return Blocks.COBBLESTONE.defaultBlockState();
        if (kind < 90) return Blocks.TUFF.defaultBlockState();
        return Blocks.DIORITE.defaultBlockState();
    }

    /** Slabs the frost has heaved up onto their ends: {x, z, along x (1) or z (0), length, height, rock}. */
    private int[][] slabs() {
        int count = 3 + Math.round(Terrain.hash(seed, 2) * 5.0F);
        int[][] slabs = new int[count][];
        for (int i = 0; i < count; i++) {
            double[] p = Terrain.xz((Terrain.hash(seed, 3 + i) - 0.5F) * 1.3F * length, (Terrain.hash(seed, 13 + i) - 0.5F) * 1.3F * radius, cx + 0.5, cz + 0.5, angle);
            slabs[i] = new int[]{Mth.floor(p[0]), Mth.floor(p[1]), Terrain.hash(seed, 23 + i) < 0.5F ? 1 : 0, 2 + Math.round(Terrain.hash(seed, 33 + i)),
                    2 + Math.round(Terrain.hash(seed, 43 + i) * 2.0F), (int) (Terrain.hash(seed, 53 + i) * 89.99F)};
        }
        return slabs;
    }

    /** How high a heaved slab stands over a column (0 if there's none), and its rock above that. */
    private static int slab(int[][] slabs, int x, int z) {
        for (int[] s : slabs) {
            int k = s[2] == 1 ? x - s[0] : z - s[1];
            if ((s[2] == 1 ? z != s[1] : x != s[0]) || k < 0 || k >= s[3]) continue;
            // (tipped a little, so one end stands higher)
            return Math.max(1, s[4] - (k == s[3] - 1 ? 1 : 0)) | s[5] << 3;
        }
        return 0;
    }

    /** Whether a column is on one of the stony stripes (rather than the finer stripes between) where the summit starts to fall away. */
    private boolean stony(int x, int z) {
        float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
        float phi = (float) Mth.atan2(l[1] / radius, l[0] / length);
        int count = Math.max(10, Math.round(Mth.PI * (length + radius) / 5.0F));
        float s = (phi / Mth.TWO_PI + 0.5F) * count + 0.35F * (Terrain.field(seed + 50, x, z, 6.0) - 0.5F);
        return s - Mth.floor(s) < 0.42F;
    }

    /** Soil, snow and ice: what the frost has worked out of the ground between the stones, and what it's cleared of. */
    private static boolean soft(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW) || s.is(BlockTags.ICE) || s.is(Blocks.CLAY) || s.is(BlockTags.SAND);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(), probe = new BlockPos.MutableBlockPos();
        int minX = Math.max(area.minX(), b.minX()), maxX = Math.min(area.maxX(), b.maxX());
        int minZ = Math.max(area.minZ(), b.minZ()), maxZ = Math.min(area.maxZ(), b.maxZ());
        if (minX > maxX || minZ > maxZ) return;
        // (a column left alone keeps any pond in it, so where a hollow opens beside one, it stays solid)
        BiPredicate<Integer, Integer> outside = (nx, nz) -> !field(nx, nz) || skipped(Goosenecks.ground(level, probe, nx, nz), b);
        int[][] slabs = slabs();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                float o = out(x, z);
                if (o >= FRINGE) continue;
                int ground = Goosenecks.ground(level, pos, x, z);
                if (skipped(ground, b)) continue;
                int standing = Goosenecks.standing(level, pos, x, ground, z);
                pos.set(x, ground, z);
                boolean cold = level.getBiome(pos).value().getBaseTemperature() < 0.5F;
                // where the summit has started to fall away, the stones are sorted into stripes running down the slope
                boolean slope = o > 0.94F || o > 0.78F && ground <= crest - 4;
                if (o >= 1.0F) {
                    if (standing == ground) fringe(level, pos, x, ground, z, o, slope);
                    continue;
                }
                // the boulder field: everything growing on it gone, any pool in it choked with frost-shattered rubble
                int top = Math.max(ground, standing);
                Terrain.clearAbove(level, pos, x, top, z, b.maxY() - top);
                for (int h = ground + 1; h <= top; h++) Terrain.set(level, pos, x, h, z, Blocks.GRAVEL.defaultBlockState());
                for (int h = top - 6; h < top - 2; h++) {
                    if (soft(level.getBlockState(pos.set(x, h, z)))) Terrain.set(level, pos, x, h, z, Blocks.STONE.defaultBlockState());
                }
                float n = Terrain.noise(seed, x, top, z), m = Terrain.noise(seed + 4, x, top, z);
                // the broken rock the boulders sit on, with grit and gravel washed in between
                Terrain.set(level, pos, x, top - 2, z, m < 0.7F ? Blocks.STONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState());
                Terrain.set(level, pos, x, top - 1, z, m < 0.45F ? Blocks.GRAVEL.defaultBlockState() : m < 0.85F ? Blocks.STONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                if (slope) {
                    stripe(level, pos, x, top, z, n, stony(x, z), false);
                    continue;
                }
                Terrain.set(level, pos, x, top, z, n < 0.4F ? Blocks.GRAVEL.defaultBlockState() : n < 0.75F ? Blocks.STONE.defaultBlockState()
                        : n < 0.9F ? Blocks.ANDESITE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                int heaved = slab(slabs, x, z);
                if (heaved > 0) {
                    BlockState s = stone(heaved >> 3);
                    for (int h = 1; h <= (heaved & 7); h++) Terrain.set(level, pos, x, top + h, z, s);
                    continue;
                }
                int stone = boulder(x, z);
                if (stone > 0) {
                    BlockState s = stone(stone >> 2);
                    int tall = stone & 3;
                    for (int h = 1; h <= tall; h++) Terrain.set(level, pos, x, top + h, z, s);
                    if (cold && n > 0.93F) Terrain.set(level, pos, x, top + tall + 1, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1));
                    continue;
                }
                // the gaps between them, opening here and there into deeper holes, with old snow lying in the shade
                float hollow = Terrain.field(seed + 40, x, z, 2.2), snow = Terrain.field(seed + 41, x, z, 6.0);
                int sink = hollow < 0.15F ? 2 : hollow < 0.32F ? 1 : 0;
                if (sink > 0) {
                    boolean edge = false;
                    for (Direction d : Direction.Plane.HORIZONTAL) edge |= outside.test(x + d.getStepX(), z + d.getStepZ());
                    for (int h = top - sink + 1; h <= top; h++) {
                        Terrain.set(level, pos, x, h, z, edge && Terrain.pondBeside(level, pos, x, h, z, outside) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    }
                    if (!level.getBlockState(pos.set(x, top - sink + 1, z)).isAir()) continue;
                    if (snow > (cold ? 0.25F : 0.55F)) {
                        int bottom = top - sink + 1;
                        if (sink == 2 && cold && n < 0.4F) {
                            Terrain.set(level, pos, x, bottom, z, Blocks.SNOW_BLOCK.defaultBlockState());
                            bottom++;
                        }
                        Terrain.set(level, pos, x, bottom, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 2 + Math.round(m * 3.0F)));
                    }
                } else if (snow > (cold ? 0.5F : 0.78F)) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + Math.round(m)));
                }
            }
        }
    }

    /** One of the sorted stripes: stones on the stony ones, grit (or, beyond the boulder field, turf) on the finer ones. */
    private void stripe(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int top, int z, float n, boolean stony, boolean turf) {
        if (stony) {
            Terrain.set(level, pos, x, top, z, n < 0.4F ? Blocks.COBBLESTONE.defaultBlockState() : n < 0.75F ? Blocks.STONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState());
            float m = Terrain.noise(seed + 7, x, top, z);
            if (m < (turf ? 0.3F : 0.5F) && level.getBlockState(pos.set(x, top + 1, z)).isAir()) {
                Terrain.set(level, pos, x, top + 1, z, m < 0.2F ? Blocks.ANDESITE.defaultBlockState() : m < 0.35F ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState());
            }
        } else if (!turf) {
            Terrain.set(level, pos, x, top, z, n < 0.85F ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
        }
    }

    /**
     * Beyond the boulder field, where the summit gives way to softer slopes: a few stones strayed out onto the turf, and
     * the stony stripes running on down.
     */
    private void fringe(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int ground, int z, float o, boolean slope) {
        float n = Terrain.noise(seed + 5, x, ground, z), fade = (FRINGE - o) / (FRINGE - 1.0F);
        boolean stony = slope && stony(x, z) && n < 0.25F + 0.6F * fade;
        int stone = boulder(x, z);
        boolean stray = !stony && stone > 0 && Terrain.noise(seed + 6, x, 0, z) < 0.55F * fade;
        if (!stony && !stray) return;
        // (burying whatever grew there, though trees standing among them live on)
        for (int h = ground + 1; h <= ground + 2; h++) {
            BlockState above = level.getBlockState(pos.set(x, h, z));
            if (above.isAir() || above.is(BlockTags.LOGS) || above.is(BlockTags.LEAVES) || !Terrain.cover(above) || !above.getFluidState().isEmpty()) break;
            Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
        }
        if (stony) {
            stripe(level, pos, x, ground, z, n, true, true);
        } else if (level.getBlockState(pos.set(x, ground + 1, z)).isAir()) {
            Terrain.set(level, pos, x, ground + 1, z, stone(stone >> 2));
        }
    }
}
