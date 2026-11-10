package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * An acid crater lake: a small, steep crater on a volcano that is quiet but not dead, holding a lake of hot, acid
 * water. Volcanic gases rising through the lake bed dissolve into it as sulphuric and hydrochloric acid, turning it a
 * vivid milky turquoise and eating at the rock round its shore, which they bleach white and crust with yellow
 * brimstone. Fumaroles hiss on the beach, nothing at all grows inside the crater, and its walls are pale, banded
 * pumice and ash from the eruption that blew it open.
 *
 * @param water the lake's surface
 * @param crest the crater's rim, before its rises and dips
 */
public record AcidLake(int cx, int cz, int water, int radius, int crest, long seed) implements Landform {
    /** How wide the beach is, then the crater wall, then the outer slope, in blocks out from the water's edge. */
    private static final int SHORE = 3, WALL = 7, OUTER = 16;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 8) {
            return null;
        }
        int radius = 14 + c.random().nextInt(9);
        int low = ground, high = ground;
        for (int i = 0; i < 12; i++) {
            float a = i * Mth.TWO_PI / 12.0F;
            int h = c.groundAt(c.x() + Math.round(Mth.cos(a) * (radius + SHORE + WALL)), c.z() + Math.round(Mth.sin(a) * (radius + SHORE + WALL)));
            low = Math.min(low, h);
            high = Math.max(high, h);
        }
        if (high - low > 14 || low < c.seaLevel() + 4) {
            return null;
        }
        // the water lies a little sunk into the ground, the rim built well up round it
        int water = Math.min(ground - 2, low + 2);
        int crest = water + 7 + c.random().nextInt(5);
        if (!c.fits(water, radius / 2 + 10, Math.max(crest, high) - water + 24)) {
            return null;
        }
        return new AcidLake(c.x(), c.z(), water, radius, crest, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new AcidLake(tag.getInt("x"), tag.getInt("z"), tag.getInt("water"), tag.getInt("r"), tag.getInt("crest"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("water", water);
        tag.putInt("r", radius);
        tag.putInt("crest", crest);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.ACID_LAKE;
    }

    private int depth() {
        return 3 + Math.round(radius * 0.45F);
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * 1.1F) + SHORE + WALL + OUTER + 2;
        return new BoundingBox(cx - r, water - depth() - 4, cz - r, cx + r, Math.max(crest, water + 14) + 24, cz + r);
    }

    private float wobble(double x, double z) {
        return 0.9F + 0.2F * Terrain.field(seed, x, z, 7.0);
    }

    /** How far a point is outside the water's edge, in blocks (negative in the lake). */
    private float edge(double x, double z) {
        double dx = x - cx, dz = z - cz;
        return (float) Math.sqrt(dx * dx + dz * dz) - radius * wobble(x, z);
    }

    private int crestAt(double x, double z) {
        return crest + Math.round((Terrain.field(seed + 1, x, z, 11.0) - 0.5F) * 4.0F);
    }

    /** Where the fumaroles are: {x, z} for each, on the beach a block or two up from the water. */
    private int[][] vents() {
        int n = 3 + Math.round(Terrain.hash(seed, 2) * 2.0F);
        int[][] vents = new int[n][];
        for (int i = 0; i < n; i++) {
            float a = (i + 0.6F * Terrain.hash(seed, 10 + i)) * Mth.TWO_PI / n + Terrain.hash(seed, 3) * Mth.TWO_PI;
            float r = radius;
            for (int k = 0; k < 2; k++) {
                r = radius * wobble(cx + Mth.cos(a) * r, cz + Mth.sin(a) * r) + 1.5F;
            }
            vents[i] = new int[]{Mth.floor(cx + Mth.cos(a) * r), Mth.floor(cz + Mth.sin(a) * r)};
        }
        return vents;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[][] vents = vents();
        int band = Math.round(Terrain.hash(seed, 1) * 5.0F);
        boolean wet = false;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float e = edge(x + 0.5, z + 0.5);
                if (e >= SHORE + WALL + OUTER) continue;
                int ground = Terrain.ground(level, pos, x, z);
                // (and under any water, which leaves overhanging it can hide from Terrain.ground)
                while (ground > b.minY() && !level.getFluidState(pos.set(x, ground, z)).isEmpty()) ground--;
                if (ground > b.maxY() - 4) continue; // left standing, it still holds the water in
                pos.set(x, ground, z);
                BlockState was = level.getBlockState(pos);
                int surf = ground;
                for (pos.set(x, ground + 1, z); surf < ground + 12 && !level.getFluidState(pos).isEmpty(); pos.move(Direction.UP)) surf++;
                float n = Terrain.noise(seed, x, 0, z);
                // brimstone crusted round the fumaroles
                float vent = Float.MAX_VALUE;
                for (int[] v : vents) vent = Math.min(vent, Mth.sqrt(Mth.square(x - v[0]) + Mth.square(z - v[1])));
                if (e >= SHORE + WALL) {
                    flank(level, pos, b, x, z, e, ground, was, band);
                    continue;
                }
                int surface = Math.min(b.maxY(), Math.max(ground, surf));
                if (e < 0.0F) {
                    // the lake: steep-sided and deep, on a bed of grey mud and brimstone
                    wet = true;
                    float dn = Math.max(0.0F, 1.0F + e / (radius * wobble(x + 0.5, z + 0.5)));
                    int bed = water - 1 - Math.round(depth() * Mth.sqrt(Math.max(0.0F, 1.0F - dn * dn)));
                    Terrain.clearAbove(level, pos, x, surface, z, Math.min(12, b.maxY() - surface));
                    for (int h = water + 1; h <= surface; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    for (int h = Math.max(b.minY(), Math.min(ground + 1, bed - 2)); h <= bed; h++) {
                        BlockState s = h < bed ? Blocks.TUFF.defaultBlockState()
                                : n < 0.35F ? Blocks.CLAY.defaultBlockState() : n < 0.6F ? Blocks.TUFF.defaultBlockState()
                                : n < 0.8F ? ModBlocks.SULFUR_CRUST.get().defaultBlockState() : Blocks.CALCITE.defaultBlockState();
                        Terrain.set(level, pos, x, h, z, s);
                    }
                    for (int h = bed + 1; h <= water; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                    continue;
                }
                // the beach, and above it the crater's steep inner wall, up to its rim (or to the hillside it was blown into)
                int top;
                if (e < SHORE) {
                    top = water + 1 + (e > 2.0F ? 1 : 0);
                } else {
                    float s = (e - SHORE) / WALL;
                    int peak = Math.max(crestAt(x + 0.5, z + 0.5), ground);
                    top = Math.round(water + 2 + (peak - water - 2) * (1.0F - (1.0F - s) * (1.0F - s)));
                }
                Terrain.clearAbove(level, pos, x, surface, z, Math.min(12, b.maxY() - surface));
                for (int h = top + 1; h <= surface; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                boolean isVent = e < SHORE && vents(x, z, vents);
                for (int h = Math.max(b.minY(), Math.min(ground + 1, top - 6)); h <= top; h++) {
                    BlockState s;
                    if (h == top && isVent) {
                        s = ModBlocks.VOLCANIC_VENT.get().defaultBlockState();
                    } else if (h >= top - 1 && (vent < 2.5F || e < SHORE && Terrain.field(seed + 4, x, z, 5.0) > 0.6F)) {
                        s = ModBlocks.SULFUR_CRUST.get().defaultBlockState();
                    } else if (e < SHORE || h <= water + 4) {
                        s = bleached(x, h, z);
                    } else {
                        s = ash(x, h, z, band);
                    }
                    Terrain.set(level, pos, x, h, z, s);
                }
            }
        }
        // the gases dissolved in the water turn it a vivid, milky turquoise-green
        if (wet) Terrain.setBiome(level, area, water - depth() - 2, water + 2, (x, z) -> edge(x + 0.5, z + 0.5) < 0.5F, Terrain.ACID_LAKE_BIOME);
    }

    private static boolean vents(int x, int z, int[][] vents) {
        for (int[] v : vents) {
            if (v[0] == x && v[1] == z) return true;
        }
        return false;
    }

    /** Outside the rim: the crater's outer slope, falling away into the ground round it. */
    private void flank(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, float e, int ground, BlockState was, int band) {
        float t = (e - SHORE - WALL) / OUTER;
        int top = Math.round(ground + (crestAt(x + 0.5, z + 0.5) - ground) * (1.0F - t) * (1.0F - t));
        if (top <= ground) return;
        Terrain.clearAbove(level, pos, x, ground, z, Math.min(top - ground + 12, b.maxY() - ground));
        // loose pumice and ash near the rim; lower down, the old soil has crept back over it
        boolean soil = t > 0.5F && (was.is(BlockTags.DIRT) || was.is(Blocks.SNOW_BLOCK));
        float n = Terrain.noise(seed, x, 1, z);
        for (int h = ground + 1; h <= top; h++) {
            BlockState s;
            if (h == top && soil) s = was;
            else if (h == top) s = n < 0.55F ? ModBlocks.PUMICE.get().defaultBlockState() : n < 0.8F ? Blocks.TUFF.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
            else if (soil && h >= top - 2) s = Blocks.DIRT.defaultBlockState();
            else s = ash(x, h, z, band);
            Terrain.set(level, pos, x, h, z, s);
        }
    }

    /** The crater walls: beds of pale pumice between greyer ash. */
    private BlockState ash(int x, int h, int z, int band) {
        int layer = Math.floorMod(h + band + Math.round(Terrain.field(seed + 2, x, z, 9.0) * 3.0F), 5);
        float n = Terrain.noise(seed + 3, x, h, z);
        if (layer < 2) return n < 0.85F ? Blocks.TUFF.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
        return n < 0.9F ? ModBlocks.PUMICE.get().defaultBlockState() : Blocks.TUFF.defaultBlockState();
    }

    /** Rock round the water, bleached white and stained yellow by the acid. */
    private BlockState bleached(int x, int h, int z) {
        float f = Terrain.field(seed + 5, x * 2.0 + h, z * 2.0 - h, 6.0), n = Terrain.noise(seed + 6, x, h, z);
        if (f > 0.62F) return n < 0.6F ? Blocks.YELLOW_TERRACOTTA.defaultBlockState() : ModBlocks.SULFUR_CRUST.get().defaultBlockState();
        if (f < 0.3F) return Blocks.CALCITE.defaultBlockState();
        return n < 0.7F ? Blocks.WHITE_TERRACOTTA.defaultBlockState() : Blocks.CALCITE.defaultBlockState();
    }
}
