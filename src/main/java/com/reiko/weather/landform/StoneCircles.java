package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Sorted patterned ground: a field of stone circles that nobody laid out. Year after year of freezing and thawing
 * churns the ground over, heaving the fine soil up in the middle of each cell and shoving the stones out to its edges,
 * until the ground is netted with rings of stones round patches of bare earth, packed edge to edge like a honeycomb.
 * Where the ground slopes, the churned soil creeps downhill as it goes, and the circles are drawn out into stripes of
 * stones and soil running straight down the slope.
 *
 * @param angle the way the ground slopes down, across the field as a whole
 * @param grid  the ground's height at a 3 by 3 grid of points over the field, to tell how steep it is anywhere in it
 */
public record StoneCircles(int cx, int cz, int radius, float angle, int[] grid, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 1 || !c.fits(ground, 20, 20)) {
            return null;
        }
        int radius = 22 + c.random().nextInt(10);
        int g = spacing(radius);
        int[] grid = new int[9];
        int min = ground, max = ground;
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 3; i++) {
                int h = c.groundAt(c.x() + (i - 1) * g, c.z() + (j - 1) * g);
                grid[i + j * 3] = h;
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        }
        // a hillside will do, but not a cliff
        if (max - min > 22 || min < c.seaLevel()) {
            return null;
        }
        float gx = ((grid[2] + grid[5] + grid[8]) - (grid[0] + grid[3] + grid[6])) / (6.0F * g);
        float gz = ((grid[6] + grid[7] + grid[8]) - (grid[0] + grid[1] + grid[2])) / (6.0F * g);
        float angle = gx * gx + gz * gz < 1.0E-4F ? c.random().nextFloat() * Mth.TWO_PI : (float) Mth.atan2(-gz, -gx);
        return new StoneCircles(c.x(), c.z(), radius, angle, grid, c.random().nextLong());
    }

    private static int spacing(int radius) {
        return Math.round(radius * 0.7F);
    }

    static Landform load(CompoundTag tag) {
        return new StoneCircles(tag.getInt("x"), tag.getInt("z"), tag.getInt("r"), tag.getFloat("angle"), tag.getIntArray("grid"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("r", radius);
        tag.putFloat("angle", angle);
        tag.put("grid", new IntArrayTag(grid));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.STONE_CIRCLES;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * 1.15F) + 2;
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int h : grid) {
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        return new BoundingBox(cx - r, min - 16, cz - r, cx + r, max + 16, cz + r);
    }

    /** The lie of the land, smoothed: the grid's heights blended between (and carried on past its edge). */
    private float model(float px, float pz) {
        float g = spacing(radius);
        float gx = (px - cx) / g + 1.0F, gz = (pz - cz) / g + 1.0F;
        int i = Mth.clamp(Mth.floor(gx), 0, 1), j = Mth.clamp(Mth.floor(gz), 0, 1);
        float fx = gx - i, fz = gz - j;
        return Mth.lerp(fz, Mth.lerp(fx, grid[i + j * 3], grid[i + 1 + j * 3]), Mth.lerp(fx, grid[i + (j + 1) * 3], grid[i + 1 + (j + 1) * 3]));
    }

    /** How steep the ground is here (blocks of fall per block). */
    private float slope(float px, float pz) {
        float dx = (model(px + 5.0F, pz) - model(px - 5.0F, pz)) / 10.0F, dz = (model(px, pz + 5.0F) - model(px, pz - 5.0F)) / 10.0F;
        return Mth.sqrt(dx * dx + dz * dz);
    }

    /** How far a point is from the nearest edge between circles, on a honeycomb of jittered centres in rows along the slope. */
    private float circles(float u, float v, float a) {
        float rowH = a * 0.866F, d1 = Float.MAX_VALUE, d2 = Float.MAX_VALUE;
        int row = Mth.floor(v / rowH);
        for (int j = row - 1; j <= row + 1; j++) {
            float shift = (j & 1) == 0 ? 0.0F : 0.5F * a;
            int col = Mth.floor((u - shift) / a);
            for (int i = col - 1; i <= col + 1; i++) {
                float cu = (i + 0.5F + 0.5F * (Terrain.noise(seed, i, 1, j) - 0.5F)) * a + shift;
                float cv = (j + 0.5F + 0.4F * (Terrain.noise(seed, i, 2, j) - 0.5F)) * rowH;
                float d = Mth.sqrt(Mth.square(u - cu) + Mth.square(v - cv));
                if (d < d1) {
                    d2 = d1;
                    d1 = d;
                } else if (d < d2) {
                    d2 = d;
                }
            }
        }
        return (d2 - d1) * 0.5F;
    }

    /** How far a point is from the nearest line of stones, where the circles have been drawn out downhill into stripes. */
    private float stripes(float u, float v, float a) {
        float rowH = a * 0.866F;
        float f = (v - 0.5F * Mth.sin(u * 0.21F + Terrain.hash(seed, 3) * Mth.TWO_PI)) / rowH;
        f -= Mth.floor(f);
        return rowH * Math.min(f, 1.0F - f);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float a = 4.2F + 1.4F * Terrain.hash(seed, 1);
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float px = x + 0.5F, pz = z + 0.5F;
                float inner = 1.0F - Mth.sqrt(Mth.square(px - cx) + Mth.square(pz - cz)) / (radius * (0.85F + 0.3F * Terrain.field(seed + 2, px, pz, 10.0)));
                if (inner <= 0.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 2 || ground > b.maxY() - 4) continue;
                pos.set(x, ground + 1, z);
                if (!level.getFluidState(pos).isEmpty()) continue;
                pos.set(x, ground, z);
                BlockState was = level.getBlockState(pos);
                if (!was.is(BlockTags.DIRT) && !was.is(BlockTags.BASE_STONE_OVERWORLD) && !was.is(Blocks.SNOW_BLOCK) && !was.is(Blocks.POWDER_SNOW)
                        && !was.is(Blocks.GRAVEL)) continue;
                float[] l = Terrain.uv(px, pz, cx, cz, angle);
                // circles on the level, stripes down the slopes, and drawn-out nets in between
                float t = Terrain.smooth((slope(px, pz) - 0.06F) / 0.14F);
                float edge = Mth.lerp(t, circles(l[0], l[1], a), stripes(l[0], l[1], a));
                float n = Terrain.noise(seed, x, ground, z);
                // the pattern fades out raggedly at the field's edge
                if (inner < 0.15F && n > inner / 0.15F) continue;
                // grass and snow on top go (but a tree is left standing)
                pos.set(x, ground + 1, z);
                BlockState above = level.getBlockState(pos);
                if (above.is(BlockTags.LOGS)) continue;
                if (!above.isAir() && Terrain.cover(above) && !above.is(BlockTags.LEAVES)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Terrain.FLAGS);
                    if (level.getBlockState(pos.move(0, 1, 0)).getBlock() instanceof DoublePlantBlock) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Terrain.FLAGS);
                }
                BlockState s;
                if (edge < 0.75F) {
                    s = n < 0.35F ? Blocks.COBBLESTONE.defaultBlockState() : n < 0.6F ? Blocks.GRAVEL.defaultBlockState() : n < 0.8F ? Blocks.ANDESITE.defaultBlockState()
                            : n < 0.93F ? Blocks.STONE.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                    // the odd big stone, shoved up on edge
                    if (edge < 0.35F && Terrain.noise(seed, x, 1, z) > 0.84F) {
                        Terrain.set(level, pos, x, ground + 1, z, Terrain.noise(seed, x, 2, z) < 0.5F ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState());
                    }
                } else if (edge < 1.15F) {
                    s = n < 0.5F ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
                } else {
                    s = n < 0.4F ? Blocks.COARSE_DIRT.defaultBlockState() : n < 0.65F ? Blocks.DIRT.defaultBlockState() : n < 0.92F ? Blocks.MOSS_BLOCK.defaultBlockState()
                            : Blocks.ROOTED_DIRT.defaultBlockState();
                }
                Terrain.set(level, pos, x, ground, z, s);
                // under the stones, more of the same (so the gravel never sits over a hole)
                pos.set(x, ground - 1, z);
                if (s.is(Blocks.GRAVEL) && level.getBlockState(pos).isAir()) Terrain.set(level, pos, x, ground - 1, z, Blocks.COBBLESTONE.defaultBlockState());
                if (s.is(Blocks.MOSS_BLOCK) && n > 0.85F) {
                    Terrain.set(level, pos, x, ground + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                } else if (s.is(Blocks.DIRT) && n > 0.6F) {
                    Terrain.set(level, pos, x, ground + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                }
            }
        }
    }
}
