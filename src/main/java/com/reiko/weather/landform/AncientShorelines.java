package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
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
import java.util.function.BiPredicate;

/**
 * Ancient shorelines (like those of Lake Bonneville in Utah, or Lake Lahontan in Nevada): the basin of a great lake that
 * dried up thousands of years ago. In the wetter times of the ice ages it filled deep, and wherever its level held steady
 * for long enough the waves cut a flat bench into the slopes round it and heaped gravel, sand and pebbles along its edge.
 * As the climate dried and the water fell it stood for a while at lower levels, cutting more benches each time, so the
 * hills round the basin are ringed with them one above another, like the rings round a drained bath. Where springs fed
 * the old lake, lime came out of the water round them as mounds of tufa at the waterline. On the floor, where the last
 * of the water dried away, lies a flat, pale lakebed of cracked mud and white marl.
 *
 * @param floor  the level of the dry lakebed
 * @param levels the height of each old shoreline, lowest first
 * @param reach  how far out the basin reaches in each of eight directions (east first, turning south)
 * @param mounds {x, z, radius in tenths, height} for each tufa mound
 */
public record AncientShorelines(int cx, int cz, int floor, int[] levels, int[] reach, int[] mounds, long seed) implements Landform {
    private static final int NEAR = 26, FAR = 50;
    /** How wide the band round the edge is where the benches fade into the land outside. */
    private static final float FADE = 8.0F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 3 || !c.fits(ground, 16, 40)) {
            return null;
        }
        // a hollow: the rim (the highest ground out along each way) well above the low ground inside it. The old lake
        // may have spilled out through a gap or two (as Bonneville did through Red Rock Pass), so the two lowest ways
        // out don't count, and its highest shoreline lies just under the lowest of the rest
        int[] near = new int[8], far = new int[8], reach = new int[8], top = new int[8];
        int low = ground;
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.PI / 4.0F;
            near[i] = c.groundAt(c.x() + Math.round(Mth.cos(a) * NEAR), c.z() + Math.round(Mth.sin(a) * NEAR));
            if (near[i] < c.seaLevel() + 2) return null;
            low = Math.min(low, near[i]);
            far[i] = c.groundAt(c.x() + Math.round(Mth.cos(a) * FAR), c.z() + Math.round(Mth.sin(a) * FAR));
            top[i] = Math.max(near[i], far[i]);
            reach[i] = (near[i] >= far[i] ? NEAR : FAR) + 10;
        }
        int[] tops = top.clone();
        Arrays.sort(tops);
        int rim = tops[2];
        if (rim - low < 6) return null;
        // the floor is lowered a little to make it flat
        int floor = low - 1, span = rim - 1 - floor;
        if (floor < c.seaLevel() + 2) {
            return null;
        }
        // the highest shoreline is where the lake spilled out over the lowest point of its rim; the others lie between
        int n = Mth.clamp(span / 4, 3, 5);
        float step = span / (float) n;
        int[] levels = new int[n];
        for (int k = 0; k < n; k++) {
            float jitter = k < n - 1 && step >= 5.0F ? c.random().nextFloat() * step * 0.35F : 0.0F;
            levels[k] = Math.max(floor + Math.round(step * (k + 1) - jitter), (k == 0 ? floor + 3 : levels[k - 1] + 2));
        }
        // tufa mounds where springs once welled up in the lake, at the foot of one of its old shores
        int m = 1 + c.random().nextInt(3);
        int[] mounds = new int[m * 4];
        int kept = 0;
        for (int j = 0; j < m; j++) {
            int i = c.random().nextInt(8), line = levels[c.random().nextInt(n)];
            float d = cross(line, ground, near[i], 0, NEAR);
            if (Float.isNaN(d)) d = cross(line, near[i], far[i], NEAR, FAR);
            float a = i * Mth.PI / 4.0F + (c.random().nextFloat() - 0.5F) * 0.4F;
            int r = 22 + c.random().nextInt(18), h = 3 + c.random().nextInt(5);
            if (Float.isNaN(d) || d < 6.0F || d - 2.0F > reach[i] - FADE - 4) continue;
            mounds[kept * 4] = c.x() + Math.round(Mth.cos(a) * (d - 2.0F));
            mounds[kept * 4 + 1] = c.z() + Math.round(Mth.sin(a) * (d - 2.0F));
            mounds[kept * 4 + 2] = r;
            mounds[kept * 4 + 3] = h;
            kept++;
        }
        return new AncientShorelines(c.x(), c.z(), floor, levels, reach, Arrays.copyOf(mounds, kept * 4), c.random().nextLong());
    }

    /** How far along a straight stretch of ground (from {@code a} at {@code da} to {@code b} at {@code db}) it rises past {@code y}. */
    private static float cross(int y, int a, int b, int da, int db) {
        return a < y && b >= y ? da + (db - da) * (y - a) / (float) (b - a) : Float.NaN;
    }

    static Landform load(CompoundTag tag) {
        return new AncientShorelines(tag.getInt("x"), tag.getInt("z"), tag.getInt("floor"), tag.getIntArray("levels"), tag.getIntArray("reach"),
                tag.getIntArray("mounds"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("floor", floor);
        tag.put("levels", new IntArrayTag(levels));
        tag.put("reach", new IntArrayTag(reach));
        tag.put("mounds", new IntArrayTag(mounds));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.ANCIENT_SHORELINES;
    }

    @Override
    public BoundingBox bounds() {
        int r = 0;
        for (int d : reach) r = Math.max(r, d);
        r += 5;
        return new BoundingBox(cx - r, floor - 12, cz - r, cx + r, levels[levels.length - 1] + 30, cz + r);
    }

    /** How far out the basin reaches the way {@code phi} points, its outline wavering a little. */
    private float radius(float phi) {
        float t = phi / (Mth.PI / 4.0F);
        int i = Mth.floor(t);
        float r = Mth.lerp(t - i, reach[Math.floorMod(i, 8)], reach[Math.floorMod(i + 1, 8)]);
        return r + 6.0F * (Terrain.field(seed + 1, Mth.cos(phi) * 12.0F, Mth.sin(phi) * 12.0F, 5.0) - 0.5F);
    }

    /** 1 well inside the basin, falling to 0 at its edge (and beyond). */
    private float fade(int x, int z) {
        float dx = x - cx, dz = z - cz;
        return Terrain.smooth((radius((float) Mth.atan2(dz, dx)) - Mth.sqrt(dx * dx + dz * dz)) / FADE);
    }

    private int gap(int k) {
        return levels[k] - (k == 0 ? floor : levels[k - 1]);
    }

    /** How far below shoreline {@code k} its beach was built out, and how far above it the waves cut back into the slope. */
    private int fill(int k) {
        return gap(k) >= 5 ? 2 : 1;
    }

    private int cut(int k) {
        return k == levels.length - 1 ? 3 : Mth.clamp(gap(k + 1) - fill(k + 1) - 1, 1, 2);
    }

    /** Which shoreline ground at height {@code g} was shaped by: -1 for the lakebed, or {@code levels.length} for none. */
    private int zone(int g) {
        for (int k = levels.length - 1; k >= 0; k--) {
            if (g >= levels[k] + cut(k)) return levels.length;
            if (g >= levels[k] - fill(k)) return k;
        }
        return -1;
    }

    /**
     * What ground at height {@code g} is cut or built to: a bench (with a beach ridge on its edge), or left alone. Inside
     * the lowest shoreline the old lake floor slopes gently down to the lakebed, flattened (and its hollows filled) where
     * the last of the water lay.
     */
    private int tread(int g, boolean ridge) {
        int k = zone(g);
        if (k < 0) return g <= floor + 2 ? floor : g;
        if (k >= levels.length) return g;
        return ridge && fill(k) == 2 && g == levels[k] - 2 ? levels[k] + 1 : levels[k];
    }

    /** How high any tufa mound stands at a column. */
    private int mound(int x, int z) {
        int best = 0;
        for (int j = 0; j < mounds.length / 4; j++) {
            float d = Mth.sqrt(Mth.square(x - mounds[j * 4]) + Mth.square(z - mounds[j * 4 + 1])) / (mounds[j * 4 + 2] / 10.0F);
            if (d >= 1.0F) continue;
            float lumps = 0.7F + 0.6F * Terrain.noise(seed + 6 + j, x, 0, z);
            best = Math.max(best, Math.round(mounds[j * 4 + 3] * (float) Math.pow(1.0F - d * d, 0.6) * lumps));
        }
        return best;
    }

    /** Turf, sand or snow lying on the ground before, to put back on a cut bench (or null to leave the rock bare). */
    @Nullable
    private static BlockState soil(BlockState was) {
        if (was.is(Blocks.GRASS_BLOCK)) return Blocks.GRASS_BLOCK.defaultBlockState();
        if (was.is(BlockTags.DIRT) || was.is(BlockTags.SAND) || was.is(Blocks.SNOW_BLOCK)) return was;
        return null;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BiPredicate<Integer, Integer> outside = (nx, nz) -> fade(nx, nz) <= 0.0F;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float f = fade(x, z);
                if (f <= 0.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 4 || ground > b.maxY() - 10) continue;
                // no water lies in the basin now (the weather may fill it again)
                int surface = Terrain.surface(level, pos, x, ground, z);
                float n = Terrain.noise(seed, x, 5, z);
                int target = tread(ground, Terrain.field(seed + 2, x, z, 6.0) > 0.4F);
                int newTop = Math.round(Mth.lerp(f, ground, target));
                int mound = mound(x, z);
                boolean bed = zone(ground) < 0 && target == floor && f >= 0.6F;
                if (newTop == ground && surface == ground && mound == 0 && !bed) continue;
                pos.set(x, ground, z);
                BlockState was = level.getBlockState(pos);
                boolean red = was.is(Blocks.RED_SAND) || level.getBiome(pos).is(BiomeTags.IS_BADLANDS);
                int highest = Math.max(ground, newTop);
                Terrain.clearAbove(level, pos, x, highest, z, Math.min(12, b.maxY() - highest));
                // cutting down (and any pond goes; one just outside keeps a bank of earth)
                for (int h = newTop + 1; h <= Math.max(ground, surface); h++) {
                    Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, outside) ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
                BlockState top;
                if (bed) {
                    // the lakebed: fine white marl and clay under a crust of cracked mud, with patches of salty white
                    top = Terrain.field(seed + 3, x, z, 7.0) > 0.7F ? Blocks.WHITE_TERRACOTTA.defaultBlockState()
                            : Terrain.field(seed + 4, x, z, 4.0) > 0.78F ? Blocks.CALCITE.defaultBlockState() : ModBlocks.CRACKED_MUD.get().defaultBlockState();
                    for (int h = Math.min(ground, newTop) - 2; h < newTop; h++) {
                        Terrain.set(level, pos, x, h, z, h == newTop - 1 ? Blocks.CLAY.defaultBlockState() : Blocks.WHITE_TERRACOTTA.defaultBlockState());
                    }
                    Terrain.set(level, pos, x, newTop, z, top);
                } else if (newTop > ground) {
                    // built out: the beach along the edge of a bench, of gravel, sand and pebbles
                    for (int h = ground + 1; h <= newTop; h++) {
                        float m = Terrain.noise(seed + 7, x, h, z);
                        BlockState s = m < 0.4F ? Blocks.GRAVEL.defaultBlockState() : m < 0.78F ? (red ? Blocks.RED_SAND : Blocks.SAND).defaultBlockState()
                                : m < 0.92F ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
                        Terrain.set(level, pos, x, h, z, s);
                    }
                    top = level.getBlockState(pos.set(x, newTop, z));
                } else {
                    // cut back: the bench's tread, with the turf back on it, strewn with old beach pebbles
                    BlockState soil = soil(was);
                    top = level.getBlockState(pos.set(x, newTop, z));
                    if (newTop < ground) {
                        if (f > 0.8F && Terrain.field(seed + 5, x, z, 4.0) > 0.66F && level.getBlockState(pos.set(x, newTop - 1, z)).blocksMotion()) {
                            top = Blocks.GRAVEL.defaultBlockState();
                        } else if (soil != null && (!soil.is(BlockTags.SAND) || level.getBlockState(pos.set(x, newTop - 1, z)).blocksMotion())) {
                            top = soil;
                        }
                        Terrain.set(level, pos, x, newTop, z, top);
                    }
                }
                if (mound > 0) {
                    // a knobbly mound of tufa, white lime crystallised round an old spring
                    for (int h = newTop + 1; h <= newTop + mound; h++) {
                        float m = Terrain.noise(seed + 8, x, h, z);
                        Terrain.set(level, pos, x, h, z, m < 0.8F ? Blocks.CALCITE.defaultBlockState() : Blocks.DIORITE.defaultBlockState());
                    }
                    continue;
                }
                if (!level.getBlockState(pos.set(x, newTop + 1, z)).isAir()) continue;
                if (top.is(Blocks.GRASS_BLOCK) && n > 0.7F) {
                    Terrain.set(level, pos, x, newTop + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                } else if ((top.is(BlockTags.SAND) || top.is(Blocks.WHITE_TERRACOTTA)) && n > (bed ? 0.985F : 0.95F)) {
                    Terrain.set(level, pos, x, newTop + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                }
            }
        }
    }
}
