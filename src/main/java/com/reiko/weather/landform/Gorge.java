package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A gorge: a deep, narrow cleft with near-vertical rock walls, sawn through a ridge by a stream that kept cutting
 * down as the land rose round it. Softer beds of rock weather back faster than the hard ones, so the walls are
 * stepped with narrow ledges where ferns and moss find a hold in the damp shade, and far below, the stream that
 * made it runs over a bed of stones.
 *
 * @param angle the way the gorge runs
 * @param water the stream's surface
 * @param wet   how far either side of the middle the stream runs before the floor rises out of it
 * @param rim   the highest ground along it (for the bounds)
 */
public record Gorge(int cx, int cz, float angle, int length, int water, int wet, int rim, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        // it has to start on a ridge, which a chunk's middle often misses
        return c.search(12, 12, Gorge::planAt);
    }

    @Nullable
    private static Landform planAt(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 24 || !c.fits(ground, 40, 40)) {
            return null;
        }
        int length = 50 + c.random().nextInt(31), half = length / 2;
        // across the ridge: of a few ways, the one where the land falls furthest at both ends
        float start = c.random().nextFloat() * Mth.PI, best = start;
        int bestEnds = Integer.MAX_VALUE, low = 0;
        for (int k = 0; k < 4; k++) {
            float a = start + k * Mth.PI / 4.0F;
            int h1 = c.groundAt(c.x() + Math.round(Mth.cos(a) * half), c.z() + Math.round(Mth.sin(a) * half));
            int h2 = c.groundAt(c.x() - Math.round(Mth.cos(a) * half), c.z() - Math.round(Mth.sin(a) * half));
            if (Math.max(h1, h2) < bestEnds) {
                bestEnds = Math.max(h1, h2);
                low = Math.min(h1, h2);
                best = a;
            }
        }
        // 15-35 deep in the middle
        int water = Math.max(c.seaLevel() + 2, Mth.clamp(low - 1, ground - 34, ground - 16));
        if (ground - water < 15) {
            return null;
        }
        // the stream runs as far as the rock either side stands well above it
        int wet = 0, rim = Math.max(ground, bestEnds);
        for (int i = 1; i <= 2; i++) {
            int d = half * i / 3;
            int h1 = c.groundAt(c.x() + Math.round(Mth.cos(best) * d), c.z() + Math.round(Mth.sin(best) * d));
            int h2 = c.groundAt(c.x() - Math.round(Mth.cos(best) * d), c.z() - Math.round(Mth.sin(best) * d));
            rim = Math.max(rim, Math.max(h1, h2));
            if (Math.min(h1, h2) < water + 8) break;
            wet = d;
        }
        if (wet == 0) {
            return null;
        }
        return new Gorge(c.x(), c.z(), best, length, water, Math.max(6, wet - 4), rim, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Gorge(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("water"), tag.getInt("wet"),
                tag.getInt("rim"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putInt("water", water);
        tag.putInt("wet", wet);
        tag.putInt("rim", rim);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.GORGE;
    }

    @Override
    public BoundingBox bounds() {
        float half = length / 2.0F;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (float u : new float[]{-half, half}) {
            for (float v : new float[]{-16.0F, 16.0F}) {
                double[] w = Terrain.xz(u, v, cx, cz, angle);
                minX = Math.min(minX, Mth.floor(w[0]));
                maxX = Math.max(maxX, Mth.floor(w[0]));
                minZ = Math.min(minZ, Mth.floor(w[1]));
                maxZ = Math.max(maxZ, Mth.floor(w[1]));
            }
        }
        return new BoundingBox(minX, water - 4, minZ, maxX, rim + 30, maxZ);
    }

    private float centre(float u) {
        return 2.8F * Mth.sin(u * 0.07F + Terrain.hash(seed, 1) * 6.0F) + 1.2F * Mth.sin(u * 0.19F + Terrain.hash(seed, 2) * 6.0F);
    }

    /** Half the gorge's width at height {@code h}: rough, near-vertical walls stepped into ledges, flaring at the rim. */
    private float halfWidth(float u, int h, int rimHere) {
        float w = 1.5F + 2.5F * Terrain.field(seed, u, 0.0, 16.0) + 0.9F * (Terrain.field(seed + 1, u * 0.6, h * 0.5, 3.0) - 0.5F);
        // bands of softer rock weathered back, with ledges of harder rock under them
        if (Math.floorMod(h - water + Math.round(3.0F * Mth.sin(u * 0.05F + Terrain.hash(seed, 3) * 6.0F)), 7) >= 5) w += 1.0F;
        return w + Math.max(0, h - (rimHere - 5)) * 0.5F;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float half = length / 2.0F;
        int[][] rims = new int[area.getXSpan()][area.getZSpan()];
        int[][] beds = new int[area.getXSpan()][area.getZSpan()];
        boolean any = false;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float u = l[0], v = Math.abs(l[1] - centre(u));
                if (Math.abs(u) > half || v > 13.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                boolean stream = Math.abs(u) <= wet;
                // keep the stream in: where the rock beside it is lower than the water, build it up
                if (Math.abs(u) <= wet + 2 && ground <= water) {
                    for (int h = ground + 1; h <= water + 1; h++) Terrain.set(level, pos, x, h, z, Blocks.STONE.defaultBlockState());
                    ground = water + 1;
                }
                // it starts as a notch and deepens into the ridge; beyond the stream the floor rises a block clear of it
                float ends = Terrain.smooth((half - Math.abs(u)) / 10.0F);
                int floor = stream ? water - (Terrain.field(seed + 2, u, 0.0, 9.0) > 0.5F ? 2 : 1) : water + 1;
                int bed = Math.round(Mth.lerp(ends, ground - 1, floor));
                int top = Math.min(ground, b.maxY());
                if (bed >= top - 1) continue;
                boolean open = false, solid = true, cut = false;
                for (int h = bed + 1; h <= top; h++) {
                    if (v < halfWidth(u, h, ground)) {
                        if (!cut) Terrain.clearAbove(level, pos, x, ground, z, Math.max(0, Math.min(12, b.maxY() - ground)));
                        cut = true;
                        Terrain.set(level, pos, x, h, z, stream && h <= water ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                        if (h == bed + 1) open = true;
                        if (solid && h > bed + 1 && h > water + 1 && h < ground - 1) ledge(level, pos, x, h, z);
                        solid = false;
                    } else {
                        solid = true;
                        // the walls are bare rock, whatever the ground was
                        pos.set(x, h, z);
                        BlockState was = level.getBlockState(pos);
                        if (h < ground - 3 && v < halfWidth(u, h, ground) + 2.0F && (was.is(BlockTags.DIRT) || was.is(BlockTags.SAND) || was.is(Blocks.GRAVEL))) {
                            level.setBlock(pos, Blocks.STONE.defaultBlockState(), Terrain.FLAGS);
                        }
                    }
                }
                if (!cut) continue;
                any = true;
                rims[x - area.minX()][z - area.minZ()] = ground;
                beds[x - area.minX()][z - area.minZ()] = bed;
                if (open) {
                    float n = Terrain.noise(seed, x, bed, z);
                    BlockState s = stream ? (n < 0.6F ? Blocks.GRAVEL.defaultBlockState() : n < 0.85F ? Blocks.STONE.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState())
                            : n < 0.5F ? Blocks.GRAVEL.defaultBlockState() : n < 0.75F ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                    Terrain.set(level, pos, x, bed, z, s);
                    if (!stream && s.is(Blocks.MOSS_BLOCK) && n < 0.6F) Terrain.set(level, pos, x, bed + 1, z, Blocks.FERN.defaultBlockState());
                }
            }
        }
        if (!any || !level.getBiome(pos.set(area.getCenter().getX(), rim, area.getCenter().getZ())).is(BiomeTags.IS_JUNGLE)) {
            return;
        }
        // in the jungle, curtains of vines hang down the walls from the rim (once the gorge is all cut)
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                int top = rims[x - area.minX()][z - area.minZ()], bed = beds[x - area.minX()][z - area.minZ()];
                if (top == 0) continue;
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    if (Terrain.noise(seed + d.ordinal(), x, 5, z) > 0.5F) continue;
                    int nx = x + d.getStepX(), nz = z + d.getStepZ();
                    if (!Terrain.column(area, nx, nz)) continue;
                    int length = 4 + Math.round(Terrain.noise(seed, x, 6, z) * 14.0F), placed = 0;
                    BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(d), true);
                    for (int h = top; h > Math.max(bed + 1, water + 1) && placed < length; h--) {
                        pos.set(x, h, z);
                        boolean air = level.getBlockState(pos).isAir();
                        BlockState wall = level.getBlockState(pos.set(nx, h, nz));
                        if (air && wall.isSolidRender(level, pos)) {
                            Terrain.set(level, pos, x, h, z, vine);
                            placed++;
                        } else if (placed > 0) {
                            break;
                        }
                    }
                }
            }
        }
    }

    /** A ledge on the wall: moss, and ferns and moss carpet growing on it. */
    private void ledge(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int h, int z) {
        float n = Terrain.noise(seed, x, h, z);
        if (n > 0.65F) return;
        Terrain.set(level, pos, x, h - 1, z, Blocks.MOSS_BLOCK.defaultBlockState());
        if (n < 0.3F) Terrain.set(level, pos, x, h, z, Blocks.FERN.defaultBlockState());
        else if (n < 0.5F) Terrain.set(level, pos, x, h, z, Blocks.MOSS_CARPET.defaultBlockState());
    }
}
