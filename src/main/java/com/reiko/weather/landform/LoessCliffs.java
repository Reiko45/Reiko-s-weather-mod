package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Loess cliffs: the edge of a loess plateau, a tableland built of fine, yellow, wind-blown silt. Through the ice ages
 * dust storms off the deserts and the bare outwash plains of the glaciers laid it down a little at a time, and in the
 * warmer spells between, soils formed on it before the dust buried them again, so its walls are banded with thin,
 * dark brown fossil soils, most with a pale line of lime nodules washed down beneath them. Dry loess holds together so
 * well that it stands in sheer, vertical cliffs, but once water gets into it, it slumps and washes away, so the
 * plateau's edge is cut into a maze of deep, narrow, flat-floored gullies, their heads eating back into the tableland a
 * little further with every downpour.
 *
 * @param base    the level of the plain round about
 * @param top     the level of the tableland's flat top
 * @param radius  the tableland's rough radius
 * @param gullies {x0, z0, x1, z1, half width x 10 at the start, at the end} for each stretch of gully, mouth first
 */
public record LoessCliffs(int cx, int cz, int base, int top, int radius, int[] gullies, long seed) implements Landform {
    private static final int F = 6;
    private static final BlockState PALE = Blocks.SMOOTH_SANDSTONE.defaultBlockState(), LOESS = Blocks.SANDSTONE.defaultBlockState(),
            DARK = Blocks.YELLOW_TERRACOTTA.defaultBlockState(), LIME = Blocks.WHITE_TERRACOTTA.defaultBlockState(),
            SOIL = Blocks.BROWN_TERRACOTTA.defaultBlockState(), RED_SOIL = Blocks.TERRACOTTA.defaultBlockState();

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 2 || !c.fits(ground, 14, 40)) {
            return null;
        }
        int radius = 26 + c.random().nextInt(16);
        // fairly level ground under the tableland and round its foot
        int min = ground, max = ground;
        for (int i = 0; i < 20; i++) {
            float a = i * Mth.TWO_PI / 10.0F + (i >= 10 ? 0.31F : 0.0F);
            int r = i < 10 ? Math.round(radius * 0.55F) : radius + 6;
            int g = c.groundAt(c.x() + Math.round(Mth.cos(a) * r), c.z() + Math.round(Mth.sin(a) * r));
            min = Math.min(min, g);
            max = Math.max(max, g);
        }
        if (max - min > 12) {
            return null;
        }
        int top = Math.min(Math.max(max + 9, ground + 12 + c.random().nextInt(11)), min + 25);
        long seed = c.random().nextLong();
        // the gullies: each a trunk from just outside the cliff, bending in towards the middle, with a side branch or two
        List<int[]> stretches = new ArrayList<>();
        int n = 5 + c.random().nextInt(5);
        float start = c.random().nextFloat() * Mth.TWO_PI;
        for (int g = 0; g < n; g++) {
            float a = start + g * Mth.TWO_PI / n + (c.random().nextFloat() - 0.5F) * 0.5F;
            float r0 = edge(seed, radius, a) + 5.0F;
            float x0 = c.x() + Mth.cos(a) * r0, z0 = c.z() + Mth.sin(a) * r0;
            float into = radius * (0.3F + 0.45F * c.random().nextFloat()), total = r0 - edge(seed, radius, a) + into;
            float dir = a + Mth.PI + (c.random().nextFloat() - 0.5F) * 0.6F;
            float w0 = 2.2F + c.random().nextFloat() * 1.6F, w1 = 1.2F + c.random().nextFloat() * 0.8F, wm = (w0 + w1) / 2.0F;
            float x1 = x0 + Mth.cos(dir) * total * 0.5F, z1 = z0 + Mth.sin(dir) * total * 0.5F;
            float bend = dir + (c.random().nextFloat() - 0.5F) * 0.8F;
            float x2 = x1 + Mth.cos(bend) * total * 0.5F, z2 = z1 + Mth.sin(bend) * total * 0.5F;
            stretches.add(stretch(x0, z0, x1, z1, w0, wm));
            stretches.add(stretch(x1, z1, x2, z2, wm, w1));
            int branches = 1 + c.random().nextInt(2);
            for (int k = 0; k < branches; k++) {
                // a side gully, forking off the trunk's inner half
                float t = 0.25F + 0.65F * c.random().nextFloat();
                float bx = Mth.lerp(t, x1, x2), bz = Mth.lerp(t, z1, z2);
                float side = (c.random().nextBoolean() ? 1.0F : -1.0F) * (0.6F + 0.5F * c.random().nextFloat());
                float len = into * (0.3F + 0.35F * c.random().nextFloat());
                float wb = Mth.lerp(t, wm, w1);
                stretches.add(stretch(bx, bz, bx + Mth.cos(bend + side) * len, bz + Mth.sin(bend + side) * len, wb, 1.0F + c.random().nextFloat() * 0.5F));
            }
        }
        int[] gullies = new int[stretches.size() * F];
        for (int i = 0; i < stretches.size(); i++) System.arraycopy(stretches.get(i), 0, gullies, i * F, F);
        return new LoessCliffs(c.x(), c.z(), min, top, radius, gullies, seed);
    }

    private static int[] stretch(float x0, float z0, float x1, float z1, float w0, float w1) {
        return new int[]{Math.round(x0), Math.round(z0), Math.round(x1), Math.round(z1), Math.round(w0 * 10.0F), Math.round(w1 * 10.0F)};
    }

    /** How far the cliff stands from the middle at a bearing: a ragged, lobed outline. */
    private static float edge(long seed, int radius, float a) {
        return radius * (1.0F + 0.13F * Mth.sin(2.0F * a + Terrain.hash(seed, 1) * 6.0F) + 0.08F * Mth.sin(3.0F * a + Terrain.hash(seed, 2) * 6.0F)
                + 0.04F * Mth.sin(7.0F * a + Terrain.hash(seed, 3) * 6.0F));
    }

    static Landform load(CompoundTag tag) {
        return new LoessCliffs(tag.getInt("x"), tag.getInt("z"), tag.getInt("base"), tag.getInt("top"), tag.getInt("r"), tag.getIntArray("gullies"),
                tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("base", base);
        tag.putInt("top", top);
        tag.putInt("r", radius);
        tag.put("gullies", new IntArrayTag(gullies));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.LOESS_CLIFFS;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * 1.25F) + 7;
        return new BoundingBox(cx - r, base - 12, cz - r, cx + r, top + 20, cz + r);
    }

    /** Whether a column is on the tableland: inside the cliffs and not in a gully. */
    private boolean plateau(int x, int z) {
        float dx = x + 0.5F - cx, dz = z + 0.5F - cz;
        return Mth.sqrt(dx * dx + dz * dz) <= edge(seed, radius, (float) Mth.atan2(dz, dx)) + 1.8F * (Terrain.field(seed + 1, x, z, 4.0) - 0.5F)
                && !gully(x, z);
    }

    /** Whether a column is in one of the gullies. */
    private boolean gully(int x, int z) {
        float px = x + 0.5F, pz = z + 0.5F;
        float wobble = 0.85F + 0.3F * Terrain.field(seed + 2, x, z, 3.0);
        for (int i = 0; i < gullies.length; i += F) {
            float ax = gullies[i] + 0.5F, az = gullies[i + 1] + 0.5F, bx = gullies[i + 2] + 0.5F, bz = gullies[i + 3] + 0.5F;
            float dx = bx - ax, dz = bz - az, len2 = dx * dx + dz * dz;
            float t = len2 < 0.01F ? 0.0F : Mth.clamp(((px - ax) * dx + (pz - az) * dz) / len2, 0.0F, 1.0F);
            float d = Mth.sqrt(Mth.square(px - ax - dx * t) + Mth.square(pz - az - dz * t));
            if (d < Mth.lerp(t, gullies[i + 4], gullies[i + 5]) / 10.0F * wobble) return true;
        }
        return false;
    }

    /**
     * The beds of the plateau from {@code base - 24} up: loess, paler and darker by turns, each bed capped by a fossil
     * soil with a line of lime nodules under it. The same at the same height everywhere, so the bands line up round
     * every cliff and down every gully.
     */
    private BlockState[] beds() {
        int len = top - base + 28;
        BlockState[] beds = new BlockState[len];
        int h = 0;
        for (int k = 0; h < len; k++) {
            int thick = 4 + Math.round(Terrain.hash(seed, 300 + k) * 5.0F);
            for (int i = 0; i < thick && h < len; i++, h++) {
                boolean pale = (i + k) / 2 % 2 == 0;
                beds[h] = pale ? PALE : Terrain.hash(seed, 400 + h) < 0.35F ? DARK : LOESS;
            }
            if (Terrain.hash(seed, 500 + k) < 0.55F && h < len) beds[h++] = LIME;
            if (h < len) beds[h++] = SOIL;
            if (Terrain.hash(seed, 600 + k) < 0.25F && h < len) beds[h++] = RED_SOIL; // a thicker soil, from a longer, warmer spell
        }
        return beds;
    }

    private BlockState bed(BlockState[] beds, int x, int h, int z) {
        // the beds dip and swell a little across the plateau
        int i = Mth.clamp(h - (base - 24) + Math.round(2.4F * (Terrain.field(seed + 3, x, z, 26.0) - 0.5F)), 0, beds.length - 1);
        BlockState s = beds[i];
        if (s == PALE || s == LOESS || s == DARK) {
            float n = Terrain.noise(seed + 4, x, h, z);
            // vertical joints, where the cliffs split off in slabs, streak the faces darker
            if (Terrain.noise(seed + 5, x, 0, z) < 0.12F && s == PALE) return LOESS;
            if (n < 0.06F) return s == PALE ? LOESS : PALE;
        }
        return s;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState[] beds = beds();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float dx = x + 0.5F - cx, dz = z + 0.5F - cz;
                float dist = Mth.sqrt(dx * dx + dz * dz);
                float edge = edge(seed, radius, (float) Mth.atan2(dz, dx)) + 1.8F * (Terrain.field(seed + 1, x, z, 4.0) - 0.5F);
                if (dist > edge + 4.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 4 || ground > top + 2) continue;
                int surface = Terrain.surface(level, pos, x, ground, z);
                boolean gully = gully(x, z);
                if (!gully && dist <= edge) {
                    // a sliver of tableland only a block wide between two gullies would never stand: it's long gone
                    boolean alongX = plateau(x - 1, z) || plateau(x + 1, z), alongZ = plateau(x, z - 1) || plateau(x, z + 1);
                    if (!alongX || !alongZ) gully = true;
                }
                float n = Terrain.noise(seed, x, ground, z);
                if (dist <= edge && !gully) {
                    // the tableland: loess from the old ground up to the flat top, under a skin of turf
                    int t = Math.max(top, ground + 3);
                    Terrain.clearAbove(level, pos, x, Math.max(ground, t), z, Math.min(14, b.maxY() - Math.max(ground, t)));
                    for (int h = ground; h <= t - 2; h++) Terrain.set(level, pos, x, h, z, bed(beds, x, h, z));
                    Terrain.set(level, pos, x, t - 1, z, Blocks.DIRT.defaultBlockState());
                    Terrain.set(level, pos, x, t, z, Blocks.GRASS_BLOCK.defaultBlockState());
                    if (n > 0.93F) {
                        BlockState grass = Blocks.TALL_GRASS.defaultBlockState();
                        Terrain.set(level, pos, x, t + 1, z, grass.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
                        Terrain.set(level, pos, x, t + 2, z, grass.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
                    } else if (n > 0.5F) {
                        Terrain.set(level, pos, x, t + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                    } else if (n < 0.015F) {
                        Terrain.set(level, pos, x, t + 1, z, n < 0.007F ? Blocks.POPPY.defaultBlockState() : Blocks.DANDELION.defaultBlockState());
                    }
                } else if (dist <= edge) {
                    // a gully floor: the old ground, built up a step or two towards the gully's head by loess washed off the walls
                    int floor = ground + Math.max(0, Math.min(Math.round((edge - dist) / 6.0F), Math.min(4, top - ground - 6)));
                    boolean wet = surface > floor;
                    Terrain.clearAbove(level, pos, x, Math.max(ground, floor), z, top - Math.max(ground, floor) + 8);
                    for (int h = ground; h <= floor; h++) {
                        BlockState s = h < floor ? Blocks.DIRT.defaultBlockState() : wet ? Blocks.DIRT.defaultBlockState()
                                : n < 0.2F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                        Terrain.set(level, pos, x, h, z, s);
                    }
                    if (wet) continue;
                    if (n > 0.95F) {
                        Terrain.set(level, pos, x, floor + 1, z, n > 0.98F ? PALE : LOESS); // a slab fallen off the wall
                    } else if (n > 0.5F) {
                        Terrain.set(level, pos, x, floor + 1, z, n > 0.88F ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
                    }
                } else if (!gully && surface == ground) {
                    // the foot of the cliff: a low apron of loess crumbled off the face
                    float t = (dist - edge) / 4.0F;
                    int apron = Math.round(2.4F * (1.0F - t) * Terrain.field(seed + 6, x, z, 5.0));
                    if (apron <= 0) continue;
                    Terrain.clearAbove(level, pos, x, ground, z, Math.min(14, b.maxY() - ground));
                    for (int h = ground + 1; h <= ground + apron; h++) {
                        Terrain.set(level, pos, x, h, z, Terrain.noise(seed + 7, x, h, z) < 0.4F ? Blocks.COARSE_DIRT.defaultBlockState() : bed(beds, x, h, z));
                    }
                    if (n < 0.5F) {
                        Terrain.set(level, pos, x, ground + apron, z, n < 0.3F ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
                        if (n < 0.12F) Terrain.set(level, pos, x, ground + apron + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                    }
                }
            }
        }
    }
}
