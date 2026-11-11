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
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Mima mounds: a stretch of prairie dimpled with low, round, grassy mounds, dozens of them, spaced so evenly they
 * look planned. Each is a heap of fine, stone-free soil a metre or two high, while the ground between them is thin
 * and gravelly. How they formed is still argued over: pocket gophers heaping soil up round their nests over
 * thousands of years is the favourite, but frost cracking, earthquakes shaking the soil into piles, and wind-blown
 * silt caught round clumps of plants all have their champions. In spring the wild flowers grow thickest on the
 * mounds, where the soil is deep.
 *
 * @param y       the level of the ground in the middle
 * @param spacing how far apart the mounds are
 * @param mounds  {x, z, radius x 10, height x 10} for each mound
 */
public record MimaMounds(int cx, int cz, int y, int spacing, int[] mounds, long seed) implements Landform {
    private static final int F = 4;
    private static final BlockState[] FLOWERS = {Blocks.DANDELION.defaultBlockState(), Blocks.POPPY.defaultBlockState(),
            Blocks.CORNFLOWER.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState(), Blocks.AZURE_BLUET.defaultBlockState(),
            Blocks.ALLIUM.defaultBlockState()};

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 1 || !c.fits(ground, 24, 36)) {
            return null;
        }
        int spacing = 9 + c.random().nextInt(7);
        int want = 25 + c.random().nextInt(36);
        float reach = spacing * Mth.sqrt(want / Mth.PI);
        if (c.relief(Math.min(Math.round(reach * 0.7F), 36), 12) > 12) {
            return null;
        }
        // a jittered grid, at no particular bearing, trimmed to a ragged, roughly round patch of prairie
        float turn = c.random().nextFloat() * Mth.HALF_PI;
        int cells = Mth.ceil(reach * 1.5F / spacing) + 1;
        List<float[]> spots = new ArrayList<>();
        for (int i = -cells; i <= cells; i++) {
            for (int j = -cells; j <= cells; j++) {
                float gu = (i + (c.random().nextFloat() - 0.5F) * 0.36F) * spacing, gv = (j + (c.random().nextFloat() - 0.5F) * 0.36F) * spacing;
                float d = Mth.sqrt(gu * gu + gv * gv) * (0.85F + 0.3F * c.random().nextFloat());
                double[] p = Terrain.xz(gu, gv, c.x(), c.z(), turn);
                spots.add(new float[]{d, (float) p[0], (float) p[1]});
            }
        }
        spots.sort((a, b) -> Float.compare(a[0], b[0]));
        int n = Math.min(want, spots.size());
        int[] mounds = new int[n * F];
        for (int k = 0; k < n; k++) {
            float r = Math.min(3.0F + c.random().nextFloat() * 3.0F, spacing * 0.42F);
            mounds[k * F] = Mth.floor(spots.get(k)[1]);
            mounds[k * F + 1] = Mth.floor(spots.get(k)[2]);
            mounds[k * F + 2] = Math.round(r * 10.0F);
            mounds[k * F + 3] = 10 + c.random().nextInt(16);
        }
        return new MimaMounds(c.x(), c.z(), ground, spacing, mounds, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new MimaMounds(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("spacing"), tag.getIntArray("mounds"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("spacing", spacing);
        tag.put("mounds", new IntArrayTag(mounds));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.MIMA_MOUNDS;
    }

    @Override
    public BoundingBox bounds() {
        int minX = cx, maxX = cx, minZ = cz, maxZ = cz;
        for (int i = 0; i < mounds.length; i += F) {
            int r = Math.max(mounds[i + 2] / 10 + 2, spacing);
            minX = Math.min(minX, mounds[i] - r);
            maxX = Math.max(maxX, mounds[i] + r);
            minZ = Math.min(minZ, mounds[i + 1] - r);
            maxZ = Math.max(maxZ, mounds[i + 1] + r);
        }
        return new BoundingBox(minX, y - 24, minZ, maxX, y + 36, maxZ);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                // the tallest mound over this column, and how near the nearest one's middle is
                float bump = 0.0F, near = Float.MAX_VALUE;
                int which = 0;
                for (int k = 0; k < mounds.length / F; k++) {
                    float dx = x - mounds[k * F], dz = z - mounds[k * F + 1];
                    float d = Mth.sqrt(dx * dx + dz * dz);
                    near = Math.min(near, d);
                    float r = mounds[k * F + 2] / 10.0F * (0.92F + 0.16F * Terrain.field(seed + k, x, z, 3.0));
                    if (d >= r) continue;
                    float t = d / r;
                    float h = mounds[k * F + 3] / 10.0F * (float) Math.pow(1.0F - t * t, 0.8);
                    if (h > bump) {
                        bump = h;
                        which = k;
                    }
                }
                // past the last mounds the prairie goes back to normal
                if (near > spacing * 0.9F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 3 || ground > b.maxY() - 6) continue;
                BlockState was = level.getBlockState(pos.set(x, ground, z));
                // only on soil, and never in a pond
                if (!was.is(BlockTags.DIRT) || !level.getFluidState(pos.set(x, ground + 1, z)).isEmpty()) continue;
                float n = Terrain.noise(seed, x, ground, z);
                int raise = (int) (bump + 0.4F);
                if (raise > 0) {
                    // a heap of deep, fine, stone-free soil under thick turf
                    Terrain.clearAbove(level, pos, x, ground, z, Math.min(14, b.maxY() - ground));
                    int top = ground + raise;
                    for (int h = ground; h < top; h++) Terrain.set(level, pos, x, h, z, Blocks.DIRT.defaultBlockState());
                    Terrain.set(level, pos, x, top, z, Blocks.GRASS_BLOCK.defaultBlockState());
                    lush(level, pos, x, top, z, n, which, true);
                } else if (bump > 0.0F) {
                    // the mound's skirt, still richer than the ground between
                    if (was.is(Blocks.GRASS_BLOCK) && level.getBlockState(pos.set(x, ground + 1, z)).isAir()) lush(level, pos, x, ground, z, n, which, false);
                } else {
                    // between the mounds the soil is thin, with gravel and stones showing through
                    float s = Terrain.field(seed + 7, x, z, 3.5);
                    boolean firm = level.getBlockState(pos.set(x, ground - 1, z)).isFaceSturdy(level, pos, net.minecraft.core.Direction.UP);
                    if (s > 0.73F && firm && was.is(Blocks.GRASS_BLOCK)) {
                        Terrain.clearAbove(level, pos, x, ground, z, 2);
                        Terrain.set(level, pos, x, ground, z, n > 0.93F ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
                    } else if (s > 0.6F || s > 0.52F && n < 0.25F) {
                        Terrain.set(level, pos, x, ground, z, Blocks.COARSE_DIRT.defaultBlockState());
                    }
                }
            }
        }
    }

    /** Flowers and tall grass on a mound (each mound has a flower of its own that does best on it). */
    private void lush(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int top, int z, float n, int which, boolean crown) {
        BlockState flower = FLOWERS[Math.floorMod(Math.round(Terrain.hash(seed, which) * 60.0F), FLOWERS.length)];
        float m = Terrain.noise(seed + 1, x, top, z);
        if (n < (crown ? 0.16F : 0.06F)) {
            Terrain.set(level, pos, x, top + 1, z, m < 0.7F ? flower : FLOWERS[Math.floorMod(Math.round(m * 50.0F), FLOWERS.length)]);
        } else if (crown && n < 0.34F && level.getBlockState(pos.set(x, top + 2, z)).isAir()) {
            BlockState grass = Blocks.TALL_GRASS.defaultBlockState();
            Terrain.set(level, pos, x, top + 1, z, grass.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
            Terrain.set(level, pos, x, top + 2, z, grass.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
        } else if (n < (crown ? 0.62F : 0.3F)) {
            Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
        }
    }
}
