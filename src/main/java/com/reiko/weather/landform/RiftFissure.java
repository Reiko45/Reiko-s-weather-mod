package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.function.BiPredicate;

/**
 * Rift fissures: long, straight cracks where the crust is being pulled apart, as at Þingvellir in Iceland, where the
 * North American and Eurasian plates drift away from each other a couple of centimetres a year. The brittle lava flows
 * on top can't stretch, so they split along parallel cracks, staggered one beside the next like the treads of a stair,
 * with sheer walls of dark basalt and pale tuff. Some, like Silfra, reach down below the water table and fill with
 * groundwater that has spent decades seeping through the porous lava from the glaciers, filtered so clean that you can
 * see a hundred metres through it. Where the ground between two fissures has sagged down between them, one lip stands
 * higher than the other, as along Almannagjá. Moss and grass grow thick on the lips, and here and there a block that
 * broke off the edge has fallen in and jammed between the walls.
 *
 * @param angle     the way the fissures run
 * @param fissures  {centre along, centre across, half length, width (tenths), depth, water level} for each fissure
 * @param graben    the fissure the ground beside (on the side of the next one) has dropped down from, or -1
 * @param drop      how far that ground has dropped
 * @param low       the lowest ground sampled
 * @param high      the highest ground sampled
 */
public record RiftFissure(int cx, int cz, float angle, int[] fissures, int graben, int drop, int low, int high, long seed) implements Landform {
    private static final int F = 6;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 3 || !c.fits(ground, 34, 16) || c.relief(36, 10) > 10) {
            return null;
        }
        float angle = c.random().nextFloat() * Mth.PI;
        int n = 2 + c.random().nextInt(3);
        int spacing = 12 + c.random().nextInt(6), step = (6 + c.random().nextInt(12)) * (c.random().nextBoolean() ? 1 : -1);
        int[] fissures = new int[n * F];
        for (int i = 0; i < n; i++) {
            // staggered en echelon: each a little to one side of the last, and further along
            float k = i - (n - 1) / 2.0F;
            int u = Math.round(k * step), v = Math.round(k * spacing) + c.random().nextInt(3) - 1;
            fissures[i * F] = u;
            fissures[i * F + 1] = v;
            fissures[i * F + 2] = Math.min(25 + c.random().nextInt(61), 94 - Math.abs(u));
            fissures[i * F + 3] = 10 + c.random().nextInt(31);
            fissures[i * F + 4] = 10 + c.random().nextInt(16);
        }
        // the ground between two of them may have dropped, where they run side by side for long enough
        int graben = -1, drop = 0;
        if (c.random().nextFloat() < 0.65F) {
            int a = c.random().nextInt(n - 1);
            int from = Math.max(fissures[a * F] - fissures[a * F + 2], fissures[(a + 1) * F] - fissures[(a + 1) * F + 2]);
            int to = Math.min(fissures[a * F] + fissures[a * F + 2], fissures[(a + 1) * F] + fissures[(a + 1) * F + 2]);
            if (to - from >= 24) {
                graben = a;
                drop = 1 + c.random().nextInt(2);
            }
        }
        // each fills with water to a few blocks under the lowest point of its lips
        int lowest = Integer.MAX_VALUE, highest = ground;
        for (int i = 0; i < n; i++) {
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            int samples = n == 4 ? 4 : 5;
            for (int s = 0; s < samples; s++) {
                float t = samples == 4 ? -0.75F + s * 0.5F : -0.8F + s * 0.4F;
                double[] p = Terrain.xz(fissures[i * F] + fissures[i * F + 2] * t, fissures[i * F + 1], c.x(), c.z(), angle);
                int h = c.groundAt(Mth.floor(p[0]), Mth.floor(p[1]));
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
            if (min < c.seaLevel() + 2 || max - min > 10) {
                return null;
            }
            int lip = graben >= 0 && (i == graben || i == graben + 1) ? min - drop : min;
            fissures[i * F + 5] = lip - 2 - c.random().nextInt(3);
            lowest = Math.min(lowest, fissures[i * F + 5] - fissures[i * F + 4]);
            highest = Math.max(highest, max);
        }
        return new RiftFissure(c.x(), c.z(), angle, fissures, graben, drop, lowest, highest, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new RiftFissure(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getIntArray("fissures"), tag.getInt("graben"), tag.getInt("drop"),
                tag.getInt("low"), tag.getInt("high"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.put("fissures", new IntArrayTag(fissures));
        tag.putInt("graben", graben);
        tag.putInt("drop", drop);
        tag.putInt("low", low);
        tag.putInt("high", high);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.RIFT_FISSURE;
    }

    @Override
    public BoundingBox bounds() {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < fissures.length / F; i++) {
            float reach = fissures[i * F + 3] / 20.0F * 1.25F + 1.0F + 5.0F;
            for (float u : new float[]{fissures[i * F] - fissures[i * F + 2] - 5.0F, fissures[i * F] + fissures[i * F + 2] + 5.0F}) {
                for (float v : new float[]{fissures[i * F + 1] - reach, fissures[i * F + 1] + reach}) {
                    double[] p = Terrain.xz(u, v, cx, cz, angle);
                    minX = Math.min(minX, Mth.floor(p[0]));
                    maxX = Math.max(maxX, Mth.ceil(p[0]));
                    minZ = Math.min(minZ, Mth.floor(p[1]));
                    maxZ = Math.max(maxZ, Mth.ceil(p[1]));
                }
            }
        }
        return new BoundingBox(minX, low - 4, minZ, maxX, high + 14, maxZ);
    }

    /** How far fissure {@code i} strays to one side of a straight line, {@code s} along it. */
    private float wobble(int i, float s) {
        return 0.6F * Mth.sin(s * 0.05F + Terrain.hash(seed, 10 + i) * 6.0F) + 0.25F * Mth.sin(s * 0.13F + Terrain.hash(seed, 20 + i) * 6.0F);
    }

    /** Half fissure {@code i}'s width, {@code s} along it: opening and closing a little, and pinching shut at the ends. */
    private float halfWidth(int i, float s) {
        float half = fissures[i * F + 2];
        float ends = Mth.sqrt(Mth.clamp((half - Math.abs(s)) / 14.0F, 0.0F, 1.0F));
        return fissures[i * F + 3] / 20.0F * (0.75F + 0.5F * Terrain.field(seed + i, s, i * 100.0, 9.0)) * ends;
    }

    /** A column's place relative to fissure {@code i}: {along, across from its line, half its width there}. */
    private float[] local(int i, float u, float v) {
        float s = u - fissures[i * F];
        return new float[]{s, v - fissures[i * F + 1] - wobble(i, s), halfWidth(i, s)};
    }

    /** How far the ground has dropped at a point between the two fissures bounding the graben. */
    private int dropAt(float u, float v) {
        if (graben < 0) return 0;
        int a = graben, b = graben + 1;
        float[] la = local(a, u, v), lb = local(b, u, v);
        // between the two lines, on the far side of each from the other
        boolean between = fissures[a * F + 1] < fissures[b * F + 1] ? la[1] > 0.0F && lb[1] < 0.0F : la[1] < 0.0F && lb[1] > 0.0F;
        if (!between) return 0;
        float from = Math.max(fissures[a * F] - fissures[a * F + 2], fissures[b * F] - fissures[b * F + 2]);
        float to = Math.min(fissures[a * F] + fissures[a * F + 2], fissures[b * F] + fissures[b * F + 2]);
        return Math.round(drop * Terrain.smooth((u - from) / 8.0F) * Terrain.smooth((to - u) / 8.0F));
    }

    /**
     * Which fissure a column is in or beside: {index, 0 in it, 1 in its walls (a column next to the water), 2 on its
     * lips}, or null if it's well away from them all.
     */
    @Nullable
    private int[] where(float u, float v) {
        int[] best = null;
        for (int i = 0; i < fissures.length / F; i++) {
            float[] l = local(i, u, v);
            float half = fissures[i * F + 2], s = Math.abs(l[0]), t = Math.abs(l[1]);
            int place = s < half && t < l[2] ? 0 : s < half + 2.0F && t < l[2] + 2.0F ? 1 : s < half + 4.0F && t < l[2] + 4.5F ? 2 : 3;
            if (place < 3 && (best == null || place < best[1])) best = new int[]{i, place};
        }
        return best;
    }

    /** Whether a column is one the fissures never change the water in (for ponds beside lowered ground). */
    private boolean untouched(int x, int z) {
        float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
        int[] w = where(l[0], l[1]);
        return (w == null || w[1] == 2) && dropAt(l[0], l[1]) == 0;
    }

    /** The fissures' walls: lava flows stacked one on another, dark basalt between paler beds of tuff. */
    private BlockState rock(int x, int y, int z) {
        int bed = Math.floorMod(y + Math.round(3.0F * Terrain.field(seed + 1, x, z, 16.0)), 8);
        float n = Terrain.noise(seed + 2, x, y, z);
        if (bed < 2) return n < 0.8F ? Blocks.TUFF.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
        if (bed == 7) return n < 0.5F ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
        return n < 0.85F ? Blocks.BASALT.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BiPredicate<Integer, Integer> outside = this::untouched;
        int top = Integer.MIN_VALUE;
        for (int i = 0; i < fissures.length / F; i++) top = Math.max(top, fissures[i * F + 5]);
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                int[] w = where(l[0], l[1]);
                int dropped = dropAt(l[0], l[1]);
                if (w == null && dropped == 0) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground > b.maxY() - 4 || ground < b.minY() + 2) continue;
                int surface = Terrain.surface(level, pos, x, ground, z);
                if (surface > b.maxY() - 4) continue;
                float n = Terrain.noise(seed, x, ground, z);
                if (w != null && w[1] == 0) {
                    fissure(level, pos, x, z, w[0], local(w[0], l[0], l[1]), ground, surface, n);
                    continue;
                }
                BlockState was = level.getBlockState(pos.set(x, ground, z));
                boolean wall = w != null && w[1] == 1;
                // the lips stand at least a block over the water, and drop with the ground between the fissures
                int lip = wall ? Math.max(ground - dropped, fissures[w[0] * F + 5] + 1) : ground - dropped;
                if (lip < Math.max(ground, surface)) {
                    Terrain.clearAbove(level, pos, x, ground, z, Math.min(12, b.maxY() - ground));
                    for (int h = lip + 1; h <= Math.max(ground, surface); h++) {
                        // (a pond just outside keeps a bank of earth between it and the lowered ground)
                        Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, outside) ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    }
                } else if (lip > ground) {
                    Terrain.clearAbove(level, pos, x, ground, z, Math.min(12, b.maxY() - ground));
                }
                if (wall) {
                    // sheer rock right down past the bottom (sealing off any cave behind it), under a skin of soil
                    int i = w[0];
                    for (int h = fissures[i * F + 5] - fissures[i * F + 4] - 1; h < lip - 1; h++) Terrain.set(level, pos, x, h, z, rock(x, h, z));
                    Terrain.set(level, pos, x, lip - 1, z, Blocks.DIRT.defaultBlockState());
                    float m = Terrain.field(seed + 3, x, z, 3.0);
                    Terrain.set(level, pos, x, lip, z, m < 0.45F ? Blocks.MOSS_BLOCK.defaultBlockState() : m < 0.85F ? Blocks.GRASS_BLOCK.defaultBlockState()
                            : Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                    if (m < 0.85F) plant(level, pos, x, lip, z, n);
                    continue;
                }
                if (lip != ground) Terrain.set(level, pos, x, lip, z, was);
                if (surface > ground && lip >= ground) continue; // a pond: left as it is
                // moss spreading back from the lips, and blocks broken off the edges lying in it
                float m = Terrain.field(seed + 3, x, z, 3.0);
                if (w != null && m < 0.4F && (was.is(BlockTags.DIRT) || was.is(Blocks.STONE))) {
                    Terrain.clearAbove(level, pos, x, lip, z, 2);
                    Terrain.set(level, pos, x, lip, z, Blocks.MOSS_BLOCK.defaultBlockState());
                    if (Terrain.field(seed + 4, x, z, 2.0) > 0.82F) {
                        Terrain.set(level, pos, x, lip + 1, z, n < 0.5F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : rock(x, lip + 1, z));
                    } else {
                        plant(level, pos, x, lip, z, n);
                    }
                }
            }
        }
        Terrain.setBiome(level, area, b.minY(), top + 2, (x, z) -> {
            float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
            for (int i = 0; i < fissures.length / F; i++) {
                float[] f = local(i, l[0], l[1]);
                if (Math.abs(f[0]) < fissures[i * F + 2] && Math.abs(f[1]) < f[2] + 3.0F) return true;
            }
            return false;
        }, Terrain.CLEAR_WATER_BIOME);
    }

    private void plant(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, float n) {
        if (n > 0.3F || !level.getBlockState(pos.set(x, y + 1, z)).isAir()) return;
        Terrain.set(level, pos, x, y + 1, z, n < 0.1F ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
    }

    /** A column in a fissure: sheer-sided, flooded to the planned level with clear water over a floor of fallen rock. */
    private void fissure(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int i, float[] l, int ground, int surface, float n) {
        int water = fissures[i * F + 5], depth = fissures[i * F + 4];
        float half = fissures[i * F + 2], across = Math.abs(l[1]) / Math.max(0.01F, l[2]);
        // deepest in the middle and away from the ends, where the walls have pulled furthest apart
        float profile = (0.55F + 0.45F * Mth.sqrt(Math.max(0.0F, 1.0F - across * across))) * (0.35F + 0.65F * Terrain.smooth((half - Math.abs(l[0])) / 12.0F));
        int floor = water - 1 - Math.round((depth - 3) * profile);
        int high = Math.max(ground, surface);
        Terrain.clearAbove(level, pos, x, Math.min(ground, water), z, Math.min(high + 10, bounds().maxY()) - Math.min(ground, water));
        // solid under the floor too, as deep as the water goes anywhere beside it (sealing off any cave)
        for (int h = Math.min(water - depth + 2, floor - 1); h < floor; h++) {
            BlockState was = level.getBlockState(pos.set(x, h, z));
            if (h > ground || h == floor - 1 || !was.blocksMotion() || !was.getFluidState().isEmpty()) Terrain.set(level, pos, x, h, z, rock(x, h, z));
        }
        // blocks fallen from the walls, lying on the bottom
        if (Terrain.field(seed + 5, x, z, 2.5) > 0.78F && floor < water - 2) {
            Terrain.set(level, pos, x, floor, z, rock(x, floor, z));
            floor++;
        }
        Terrain.set(level, pos, x, floor, z, n < 0.4F ? Blocks.GRAVEL.defaultBlockState() : rock(x, floor, z));
        for (int h = floor + 1; h <= water; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
        if (water - floor >= 3 && n > 0.86F) Terrain.set(level, pos, x, floor + 1, z, Blocks.SEAGRASS.defaultBlockState());
        for (int h = water + 1; h <= high; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
        // a block broken off a lip and jammed between the walls on its way down, where the crack is narrow
        int count = 2 + Math.round(Terrain.hash(seed, 30 + i) * 4.0F);
        for (int k = 0; k < count; k++) {
            float at = (Terrain.hash(seed, 40 + i * 8 + k) - 0.5F) * half * 1.6F;
            if (Math.abs(l[0] - at) > 0.6F + Terrain.hash(seed, 50 + i * 8 + k) || l[2] > 1.6F) continue;
            int h = water + 1 + Math.round(Terrain.hash(seed, 60 + i * 8 + k) * 3.0F);
            if (h < ground) Terrain.set(level, pos, x, h, z, rock(x, h, z));
        }
    }
}
