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

/**
 * Hogbacks and flatirons (like the Dakota Hogback along the Colorado Front Range, and the Flatirons above Boulder).
 * Where a range of mountains was pushed up, the flat beds of sandstone and shale lying over it were bent up and tilted
 * along its flanks. Since then the soft shales have worn away and the hard sandstones have been left standing as long,
 * sharp ridges running along the mountain front: each has a gentle dip slope down one side, which is the top of the hard
 * bed itself, and a steep scarp on the other, where the broken edges of the beds face the mountains. Gullies running down
 * the dip slopes notch the crests into sawteeth, and streams older than the ridges have kept cutting down through them in
 * narrow notches, water gaps. Nearest the mountains, where the beds stand steepest, the gullies have cut a hard bed right
 * through into triangular slabs, flatirons, leaning against the higher ground.
 *
 * @param lo     the lowest ground seen round it
 * @param hi     the highest ground seen round it
 * @param angle  the strike, the way the ridges run (the beds dip to its left, down away from the high ground)
 * @param dip    how steeply the beds dip, in hundredths of a block per block (the slope of the dip slopes)
 * @param scarp  how steep the scarps are, in hundredths
 * @param ridges {crest (across), height, middle (along), half length, facet spacing} for each ridge
 * @param gaps   {along, half width} for each water gap
 * @param slabs  {along, foot (across), half width, depth, height} for each flatiron
 */
public record Hogbacks(int cx, int cz, int lo, int hi, float angle, int dip, int scarp, int[] ridges, int[] gaps, int[] slabs, long seed)
        implements Landform {
    /** How thick the hard bed capping each ridge is. */
    private static final int CAP = 3;
    /** The beds under it in order, a block each (so a colour repeated is a thicker bed). */
    private static final BlockState[] BEDS = {
            Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState(),
            Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(), Blocks.STONE.defaultBlockState(),
            Blocks.STONE.defaultBlockState(), Blocks.WHITE_TERRACOTTA.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(),
            Blocks.SANDSTONE.defaultBlockState(), Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.RED_TERRACOTTA.defaultBlockState(),
            Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState(), Blocks.BROWN_TERRACOTTA.defaultBlockState(), Blocks.STONE.defaultBlockState(),
            Blocks.YELLOW_TERRACOTTA.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState(),
            Blocks.TERRACOTTA.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState()};

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 3 || !c.fits(ground, 14, 64)) {
            return null;
        }
        int relief = c.relief(32, 8);
        if (relief > 18) {
            return null;
        }
        // the beds dip away from the high ground (where the mountains rose), so the ridges run across the slope
        float[] down = c.downhill(32);
        float dipAngle = down[1] >= 4 ? down[0] : c.random().nextFloat() * Mth.TWO_PI;
        float strike = dipAngle - Mth.HALF_PI;
        float dip = 0.42F + c.random().nextFloat() * 0.28F, scarp = 1.7F + c.random().nextFloat() * 0.8F;
        int n = 2 + c.random().nextInt(3), m = 3 + c.random().nextInt(4), depth = 14 + c.random().nextInt(9);
        int[] heights = new int[n], spaces = new int[n];
        for (int i = 0; i < n; i++) {
            heights[i] = 12 + c.random().nextInt(24);
            spaces[i] = 2 + c.random().nextInt(5);
        }
        // the whole set, flatirons and all, has to fit in about 150 blocks across: lower the ridges (or drop one) until it does
        float[] crest = new float[n];
        float near = 0.0F, far = 0.0F;
        for (int tries = 0; tries < 14; tries++) {
            for (int i = 1; i < n; i++) crest[i] = crest[i - 1] + heights[i - 1] / dip * 0.7F + heights[i] / scarp + spaces[i];
            near = -heights[0] / scarp - spaces[0] - depth - 14.0F;
            far = crest[n - 1] + heights[n - 1] * 1.15F / dip + 2.0F;
            if (far - near <= 150.0F) break;
            if (tries >= 6 && n > 2) n--;
            else for (int i = 0; i < n; i++) heights[i] = Math.max(12, Math.round(heights[i] * 0.85F));
        }
        if (far - near > 150.0F) {
            return null;
        }
        float shift = (near + far) / 2.0F, front = -heights[0] / scarp - spaces[0] - shift;
        // as long as it can be while staying within reach of the middle
        int length = 80 + c.random().nextInt(111);
        float across = (far - near) / 2.0F + 2.0F, cos = Math.abs(Mth.cos(strike)), sin = Math.abs(Mth.sin(strike));
        while (length > 80 && Math.max(cos * (length / 2.0F + 2.0F) + sin * across, sin * (length / 2.0F + 2.0F) + cos * across) > 106.0F) {
            length -= 6;
        }
        // never out into the sea or a lake: it only runs as far as dry land
        int lo = ground, hi = ground + relief;
        for (float v : new float[]{near - shift + 6.0F, far - shift - 2.0F}) {
            double[] w = Terrain.xz(0.0, v, c.x(), c.z(), strike);
            int g = c.groundAt(Mth.floor(w[0]), Mth.floor(w[1]));
            if (g < c.seaLevel() + 1) return null;
            lo = Math.min(lo, g);
            hi = Math.max(hi, g);
        }
        for (int s = -1; s <= 1; s += 2) {
            double[] w = Terrain.xz(s * length * 0.5, 0.0, c.x(), c.z(), strike);
            int g = c.groundAt(Mth.floor(w[0]), Mth.floor(w[1]));
            if (g < c.seaLevel() + 1) length = length * 2 / 3;
            lo = Math.min(lo, g);
            hi = Math.max(hi, g);
        }
        int half = length / 2;
        int[] ridges = new int[n * 5];
        for (int i = 0; i < n; i++) {
            int h = i == 0 ? half : Math.round(half * (0.65F + 0.35F * c.random().nextFloat()));
            ridges[i * 5] = Math.round(crest[i] - shift);
            ridges[i * 5 + 1] = heights[i];
            ridges[i * 5 + 2] = Math.round((c.random().nextFloat() - 0.5F) * (half - h));
            ridges[i * 5 + 3] = h;
            ridges[i * 5 + 4] = 12 + c.random().nextInt(9);
        }
        // a stream or a few cutting through them all, well apart
        int g = 1 + c.random().nextInt(length > 140 ? 3 : 2);
        int[] gaps = new int[g * 2];
        float spacing = Math.max(8.0F, length - 40.0F) / g;
        for (int i = 0; i < g; i++) {
            gaps[i * 2] = Math.round(-half + 20.0F + spacing * (i + 0.25F + 0.5F * c.random().nextFloat()));
            gaps[i * 2 + 1] = 1 + c.random().nextInt(3);
        }
        // the flatirons, in a row along the flank nearest the high ground
        int[] slabs = new int[m * 5];
        float row = Math.min(half * 0.8F, m * 13.0F);
        for (int j = 0; j < m; j++) {
            int d = depth - 3 + c.random().nextInt(6);
            slabs[j * 5] = Math.round(-row / 2.0F + row * (j + 0.5F) / m + (c.random().nextFloat() - 0.5F) * 4.0F);
            slabs[j * 5 + 1] = Math.round(front);
            slabs[j * 5 + 2] = Math.max(2, Math.min(Math.round(row / m / 2.0F) - 1, 4 + c.random().nextInt(4)));
            slabs[j * 5 + 3] = d;
            slabs[j * 5 + 4] = Mth.clamp(Math.round(d * (0.9F + 0.5F * c.random().nextFloat())), 12, 30);
        }
        return new Hogbacks(c.x(), c.z(), lo, hi, strike, Math.round(dip * 100.0F), Math.round(scarp * 100.0F), ridges, gaps, slabs,
                c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Hogbacks(tag.getInt("x"), tag.getInt("z"), tag.getInt("lo"), tag.getInt("hi"), tag.getFloat("angle"), tag.getInt("dip"),
                tag.getInt("scarp"), tag.getIntArray("ridges"), tag.getIntArray("gaps"), tag.getIntArray("slabs"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("lo", lo);
        tag.putInt("hi", hi);
        tag.putFloat("angle", angle);
        tag.putInt("dip", dip);
        tag.putInt("scarp", scarp);
        tag.put("ridges", new IntArrayTag(ridges));
        tag.put("gaps", new IntArrayTag(gaps));
        tag.put("slabs", new IntArrayTag(slabs));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.HOGBACKS;
    }

    /** The box round everything in the strike's own frame: {min along, max along, min across, max across}. */
    private float[] extent() {
        float u0 = Float.MAX_VALUE, u1 = -Float.MAX_VALUE, v0 = Float.MAX_VALUE, v1 = -Float.MAX_VALUE;
        float d = dip / 100.0F, s = scarp / 100.0F;
        for (int i = 0; i < ridges.length / 5; i++) {
            u0 = Math.min(u0, ridges[i * 5 + 2] - ridges[i * 5 + 3]);
            u1 = Math.max(u1, ridges[i * 5 + 2] + ridges[i * 5 + 3]);
            // (the crest wanders a block or so, and rubble lies a few blocks out from the foot of the scarp)
            v0 = Math.min(v0, ridges[i * 5] - ridges[i * 5 + 1] * 1.15F / s - 7.0F);
            v1 = Math.max(v1, ridges[i * 5] + ridges[i * 5 + 1] * 1.15F / d + 2.0F);
        }
        for (int j = 0; j < slabs.length / 5; j++) {
            float sides = slabs[j * 5 + 4] / 3.0F + 1.0F;
            u0 = Math.min(u0, slabs[j * 5] - slabs[j * 5 + 2] - sides);
            u1 = Math.max(u1, slabs[j * 5] + slabs[j * 5 + 2] + sides);
            v0 = Math.min(v0, slabs[j * 5 + 1] - slabs[j * 5 + 3] - sides);
            v1 = Math.max(v1, slabs[j * 5 + 1] + 1.0F);
        }
        return new float[]{u0 - 1.0F, u1 + 1.0F, v0, v1};
    }

    @Override
    public BoundingBox bounds() {
        float[] e = extent();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (float u : new float[]{e[0], e[1]}) {
            for (float v : new float[]{e[2], e[3]}) {
                double[] w = Terrain.xz(u, v, cx + 0.5, cz + 0.5, angle);
                minX = Math.min(minX, Mth.floor(w[0]));
                maxX = Math.max(maxX, Mth.ceil(w[0]));
                minZ = Math.min(minZ, Mth.floor(w[1]));
                maxZ = Math.max(maxZ, Mth.ceil(w[1]));
            }
        }
        int tallest = 0;
        for (int i = 0; i < ridges.length / 5; i++) tallest = Math.max(tallest, ridges[i * 5 + 1]);
        for (int j = 0; j < slabs.length / 5; j++) tallest = Math.max(tallest, slabs[j * 5 + 4]);
        return new BoundingBox(minX, lo - 16, minZ, maxX, hi + tallest + 40, maxZ);
    }

    /**
     * How high ridge {@code i} stands over the ground at a point, or 0 off it. {@code out[0]} is set to the height of the
     * top of its hard bed there (carried on up into the air over the scarp), which the beds below lie parallel to.
     */
    private float ridge(int i, float u, float v, float[] out) {
        int middle = ridges[i * 5 + 2], half = ridges[i * 5 + 3], period = ridges[i * 5 + 4];
        float along = Math.abs(u - middle);
        if (along >= half) return 0.0F;
        // lower at the ends, where it plunges into the ground, and rising and falling a little along its length
        float top = ridges[i * 5 + 1] * Terrain.smooth((half - along) / 22.0F) * (0.82F + 0.3F * Terrain.field(seed + 7 * i, u, i * 41.0, 26.0));
        if (top < 0.5F) return 0.0F;
        float d = dip / 100.0F, s = scarp / 100.0F;
        float w = v - ridges[i * 5] - 2.5F * (Terrain.field(seed + 7 * i + 1, u, i * 41.0, 30.0) - 0.5F);
        float scarpWidth = top / s, dipWidth = top / d;
        if (w <= -scarpWidth || w >= dipWidth) return 0.0F;
        out[0] = top - w * d;
        float h, open;
        if (w <= 0.0F) {
            // the scarp: sheer near the top, easing out over its own rubble at the foot
            float t = 1.0F + w / scarpWidth;
            h = top * (0.3F * t + 0.7F * t * t * t);
            open = 0.3F;
        } else {
            float t = w / dipWidth;
            h = top * (1.0F - t);
            open = 0.3F + 0.7F * t;
        }
        // gullies down the dip slope cut it into triangular facets, points up, so the crest is notched into sawteeth
        float phase = Terrain.hash(seed, 100 + i) * period;
        int k = Mth.floor((u - phase) / period + 0.5F);
        float off = Math.abs(u - phase - k * period);
        float facet = period * 0.5F * open * (0.8F + 0.4F * Terrain.hash(seed, 200 + i * 64 + (k & 63)));
        return h - Math.min(Math.max(0.0F, off - facet) * 1.3F, 0.45F * h);
    }

    /** How high flatiron {@code j} stands at a point, or 0 off it; {@code out[0]} is set to the height of its face, as for a ridge. */
    private float slab(int j, float u, float v, float[] out) {
        int foot = slabs[j * 5 + 1], halfWidth = slabs[j * 5 + 2], depth = slabs[j * 5 + 3], height = slabs[j * 5 + 4];
        float t = (foot - v) / depth;
        if (t < 0.0F || t > 1.0F + height / (3.0F * depth)) return 0.0F;
        float off = Math.abs(u - slabs[j * 5]);
        if (off > halfWidth + height / 3.0F + 1.0F) return 0.0F;
        // its face rises from the ground to a point at the back, where it leans on the high ground
        float face = height * t + 1.2F * (Terrain.field(seed + 300 + j, u, v, 5.0) - 0.5F);
        out[0] = face;
        float top = t <= 1.0F ? face : height - (t - 1.0F) * depth * 3.0F;
        float side = halfWidth * (1.0F - Math.min(t, 1.0F)) + 0.6F;
        // and its sides fall away steeply into the gullies between
        return top - Math.max(0.0F, off - side) * 3.0F;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float[] e = extent(), out = new float[1];
        int count = ridges.length / 5;
        float s = scarp / 100.0F;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
                float u = l[0], v = l[1];
                if (u < e[0] || u > e[1] || v < e[2] || v > e[3]) continue;
                // the tallest ridge (or slab) here, the top of its hard bed, and the rubble off any scarp close by
                float height = 0.0F, plane = 0.0F, rubble = 0.0F;
                boolean flatiron = false;
                for (int i = 0; i < count; i++) {
                    float h = ridge(i, u, v, out);
                    if (h > height) {
                        height = h;
                        plane = out[0];
                    }
                    float foot = ridges[i * 5] - ridges[i * 5 + 1] / s;
                    if (h < 0.5F && v > foot - 5.0F && v < foot + 1.0F && Math.abs(u - ridges[i * 5 + 2]) < ridges[i * 5 + 3] - 10) {
                        rubble = Math.max(rubble, 0.2F * (1.0F - (foot - v) / 5.0F));
                    }
                }
                for (int j = 0; j < slabs.length / 5; j++) {
                    float h = slab(j, u, v, out);
                    if (h > height) {
                        height = h;
                        plane = out[0];
                        flatiron = true;
                    }
                }
                // the water gaps, notched down through everything in their way
                boolean gap = false;
                for (int k = 0; k < gaps.length / 2; k++) {
                    float skew = (Terrain.hash(seed, 400 + k) - 0.5F) * 0.3F;
                    float off = Math.abs(u - gaps[k * 2] - v * skew - 2.0F * (Terrain.field(seed + 500 + k, v, 0.0, 9.0) - 0.5F)) - gaps[k * 2 + 1];
                    float cut = Math.max(0.0F, off * 2.8F);
                    if (cut < height) {
                        if (off < 0.5F && height > 1.5F) gap = true;
                        height = cut;
                    }
                }
                if (height < 0.5F && rubble <= 0.0F && !gap) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 2 || ground > b.maxY() - 44) continue;
                int surface = Terrain.surface(level, pos, x, ground, z);
                int top = ground + Math.round(height);
                // where a stream or a pond lies, the ridge is breached (unless it stands well clear of the water)
                if (surface > ground && top <= surface) continue;
                pos.set(x, ground, z);
                BlockState was = level.getBlockState(pos);
                float n = Terrain.noise(seed, x, ground, z);
                if (top <= ground) {
                    if (gap) {
                        // the stream's dry bed of gravel and cobbles, washed down through the gap
                        BlockState bed = n < 0.7F ? Blocks.GRAVEL.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
                        if (level.getBlockState(pos.set(x, ground - 1, z)).blocksMotion()) {
                            Terrain.clearAbove(level, pos, x, ground, z, 6);
                            Terrain.set(level, pos, x, ground, z, bed);
                        }
                    } else if (rubble > 0.0F && Terrain.noise(seed + 1, x, 0, z) < rubble) {
                        // blocks broken off the scarp above, lying at its foot
                        Terrain.clearAbove(level, pos, x, ground, z, 4);
                        float m = Terrain.noise(seed + 2, x, 0, z);
                        BlockState rock = m < 0.35F ? Blocks.SANDSTONE.defaultBlockState() : m < 0.6F ? Blocks.COBBLESTONE.defaultBlockState()
                                : m < 0.8F ? Blocks.RED_SANDSTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                        Terrain.set(level, pos, x, ground + 1, z, rock);
                    }
                    continue;
                }
                Terrain.clearAbove(level, pos, x, ground, z, Math.min(top - ground + 10, b.maxY() - ground));
                int offset = Math.floorMod(Math.round(Terrain.hash(seed, flatiron ? 1 : 2) * BEDS.length), BEDS.length);
                for (int h = ground; h <= top; h++) {
                    // how far under the top of the hard bed this is: the beds all lie parallel to it
                    int under = Mth.floor(ground + plane - h);
                    BlockState rock;
                    if (under < CAP) {
                        float m = Terrain.noise(seed + 3, x, h, z);
                        rock = flatiron ? (m < 0.8F ? Blocks.RED_SANDSTONE.defaultBlockState() : Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState())
                                : m < 0.85F ? Blocks.SANDSTONE.defaultBlockState() : Blocks.SMOOTH_SANDSTONE.defaultBlockState();
                    } else {
                        rock = BEDS[Math.floorMod(under - CAP + offset, BEDS.length)];
                    }
                    Terrain.set(level, pos, x, h, z, rock);
                }
                // soil and scrub gathering on the gentler dip slopes, or windblown sand where it's dry
                boolean onBed = ground + plane - top < 1.5F && !flatiron;
                if (onBed && Terrain.field(seed + 4, x, z, 6.0) > 0.58F) {
                    if (was.is(BlockTags.DIRT)) {
                        Terrain.set(level, pos, x, top - 1, z, Blocks.DIRT.defaultBlockState());
                        Terrain.set(level, pos, x, top, z, Blocks.GRASS_BLOCK.defaultBlockState());
                        if (n > 0.55F) Terrain.set(level, pos, x, top + 1, z, n > 0.93F ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
                    } else if (was.is(BlockTags.SAND)) {
                        boolean red = was.is(Blocks.RED_SAND) || level.getBiome(pos.set(x, ground, z)).is(BiomeTags.IS_BADLANDS);
                        Terrain.set(level, pos, x, top, z, red ? Blocks.RED_SAND.defaultBlockState() : Blocks.SAND.defaultBlockState());
                        if (n > 0.9F) Terrain.set(level, pos, x, top + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                    }
                }
            }
        }
    }
}
