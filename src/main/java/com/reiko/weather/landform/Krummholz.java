package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * Krummholz ("crooked wood"): the last stunted spruces at the treeline, where the wind is too strong and the winters
 * too long for a tree to stand up straight. Any shoot that pokes up above the winter snow is scoured by wind-blown
 * ice crystals, dries out and dies, so most of the trees grow as low, dense mats that creep away downwind on
 * crooked, half-lying stems, dying back on the windward side as they grow on the other, with the snow drifting deep
 * in their lee. Here and there one has managed to grow taller, but only as a "flag tree": a bare trunk with all its
 * branches streaming out on the sheltered side, every one of them pointing the way the wind blows, and a skirt of
 * low growth round its foot where the snow kept it safe.
 *
 * @param angle the way the wind blows (radians)
 * @param trees {x, z, base y, height} for each flag tree
 */
public record Krummholz(int cx, int cz, int y, int radius, float angle, int[] trees, long seed) implements Landform {
    /** The grid the mats are spread over, along and across the wind. */
    private static final float CU = 8.0F, CV = 5.0F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        int radius = 20 + c.random().nextInt(11);
        if (ground < c.seaLevel() + 30 || !c.fits(ground, radius + 10, radius + 22)) {
            return null;
        }
        // up on a mountainside (it follows the slope, however steep), but not a cliff
        if (c.relief(radius, 10) > radius + 6) {
            return null;
        }
        float angle = c.random().nextFloat() * Mth.TWO_PI;
        int n = 4 + c.random().nextInt(5);
        int[] trees = new int[n * 4];
        int k = 0;
        for (int attempt = 0; attempt < n * 3 && k < n; attempt++) {
            float a = c.random().nextFloat() * Mth.TWO_PI, d = Mth.sqrt(c.random().nextFloat()) * radius * 0.8F;
            int x = c.x() + Math.round(Mth.cos(a) * d), z = c.z() + Math.round(Mth.sin(a) * d);
            boolean clear = true;
            for (int j = 0; j < k && clear; j++) clear = Mth.square(x - trees[j * 4]) + Mth.square(z - trees[j * 4 + 1]) >= 30;
            if (!clear) continue;
            trees[k * 4] = x;
            trees[k * 4 + 1] = z;
            trees[k * 4 + 2] = c.groundAt(x, z);
            trees[k * 4 + 3] = 4 + c.random().nextInt(4);
            k++;
        }
        return new Krummholz(c.x(), c.z(), ground, radius, angle, Arrays.copyOf(trees, k * 4), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Krummholz(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getFloat("angle"), tag.getIntArray("trees"),
                tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.putFloat("angle", angle);
        tag.put("trees", new IntArrayTag(trees));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.KRUMMHOLZ;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * 1.3F) + 10;
        int low = y - radius - 8, high = y + radius + 12;
        for (int i = 0; i < trees.length; i += 4) {
            low = Math.min(low, trees[i + 2] - 2);
            high = Math.max(high, trees[i + 2] + trees[i + 3] + 3);
        }
        return new BoundingBox(cx - r, low, cz - r, cx + r, high, cz + r);
    }

    /** How far out into the patch a point is: 1 at its ragged edge, where the last mats give out. */
    private float spread(double x, double z) {
        return (float) Math.sqrt(Mth.square(x - cx - 0.5) + Mth.square(z - cz - 0.5)) / (radius * (0.85F + 0.3F * Terrain.field(seed, x, z, 12.0)));
    }

    /** What grows on (or drifts over) a column: how high the mat stands, its stems, and how deep the snow lies. */
    private record Cover(int mat, boolean stem, int stub, boolean dead, int snow) {}

    private static final Cover NOTHING = new Cover(0, false, 0, false, 0);

    private Cover cover(float px, float pz) {
        float[] l = Terrain.uv(px, pz, cx + 0.5, cz + 0.5, angle);
        float u = l[0], v = l[1];
        int mat = 0, stub = 0, snow = 0;
        boolean stem = false, dead = false;
        // the mats: each a wedge, low and dying back to windward, thickest just short of its lee end, on a crooked stem
        int gi = Mth.floor(u / CU), gj = Mth.floor(v / CV);
        for (int i = gi - 2; i <= gi + 1; i++) {
            for (int j = gj - 1; j <= gj + 1; j++) {
                float ou = (i + 0.2F + 0.6F * Terrain.noise(seed + 2, i, 0, j)) * CU, ov = (j + 0.2F + 0.6F * Terrain.noise(seed + 3, i, 0, j)) * CV;
                double[] w = Terrain.xz(ou, ov, cx + 0.5, cz + 0.5, angle);
                // fewer and smaller mats out toward the edge of the patch
                float e = spread(w[0], w[1]);
                float keep = 0.9F * Terrain.smooth((1.1F - e) / 0.5F);
                if (Terrain.noise(seed + 1, i, 0, j) >= keep) continue;
                float len = (3.0F + 3.5F * Terrain.noise(seed + 4, i, 0, j)) * (0.6F + 0.4F * keep / 0.9F), wid = 1.3F + 1.2F * Terrain.noise(seed + 5, i, 0, j);
                float s = (u - ou) / len;
                if (s < -1.0F || s > 1.0F + (3.0F + len) / len) continue;
                float bend = 0.9F * Mth.sin(s * 1.8F + 6.0F * Terrain.noise(seed + 6, i, 0, j));
                if (s > 1.0F) {
                    // the drift banked up in its lee
                    float t = (v - ov - bend) / wid, k = (s - 1.0F) * len / (3.0F + len);
                    if (Math.abs(t) < 1.0F) snow = Math.max(snow, Math.round(1.0F + 5.0F * (1.0F - k) * (1.0F - t * t)));
                    continue;
                }
                float across = v - ov - bend;
                float t = across / (wid * (0.55F + 0.45F * (s + 1.0F) / 2.0F));
                if (t * t + Mth.square(Math.max(0.0F, Math.abs(s) - 0.6F) / 0.4F) >= 1.0F) continue;
                int h = 1 + Math.round(1.7F * Terrain.smooth((s + 1.0F) / 1.5F) * (1.0F - t * t));
                if (h > mat) {
                    mat = h;
                    // the stem runs the length of it, rooted at the windward end
                    stem = Math.abs(across) < 0.5F && s > -0.9F && s < 0.7F;
                    stub = stem && s < -0.6F ? 1 + Mth.floor(Terrain.noise(seed + 7, i, 0, j) * 2.99F) : 0;
                    dead = s < -0.7F;
                }
            }
        }
        // the skirts round the flag trees' feet, and the snow drifted behind them
        for (int i = 0; i < trees.length / 4; i++) {
            float[] t = Terrain.uv(px, pz, trees[i * 4] + 0.5, trees[i * 4 + 1] + 0.5, angle);
            if (Math.abs(t[0]) > 9.0F || Math.abs(t[1]) > 4.0F) continue;
            float q = Mth.square((t[0] - 0.6F) / 2.4F) + Mth.square(t[1] / 1.7F);
            if (q < 1.0F) {
                int h = q < 0.35F && t[0] > 0.0F ? 2 : 1;
                if (h > mat) {
                    mat = h;
                    stem = false;
                    stub = 0;
                    dead = false;
                }
            } else if (t[0] > 1.5F && t[0] < 8.0F) {
                float half = 1.5F + 0.2F * t[0];
                if (Math.abs(t[1]) < half) snow = Math.max(snow, Math.round(1.0F + 4.0F * (1.0F - (t[0] - 1.5F) / 6.5F) * (1.0F - Mth.square(t[1] / half))));
            }
        }
        if (mat == 0 && snow == 0) return NOTHING;
        return new Cover(mat, stem, stub, dead, snow);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        // the wood lies along the wind
        Direction.Axis along = Math.abs(Mth.cos(angle)) > Math.abs(Mth.sin(angle)) ? Direction.Axis.X : Direction.Axis.Z;
        BlockState lying = Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, along);
        int minX = Math.max(area.minX(), b.minX()), maxX = Math.min(area.maxX(), b.maxX());
        int minZ = Math.max(area.minZ(), b.minZ()), maxZ = Math.min(area.maxZ(), b.maxZ());
        if (minX > maxX || minZ > maxZ) return;
        // each column's own ground, before anything is built on it, for the flag trees to stand on
        int[] grounds = new int[(maxX - minX + 1) * (maxZ - minZ + 1)];
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int ground = Terrain.ground(level, pos, x, z);
                grounds[(x - minX) * (maxZ - minZ + 1) + (z - minZ)] = ground;
                if (ground < b.minY() + 2 || ground > b.maxY() - 8) continue;
                Cover c = cover(x + 0.5F, z + 0.5F);
                if (c == NOTHING) continue;
                // nothing grows in a pond or a stream
                if (!level.getFluidState(pos.set(x, ground + 1, z)).isEmpty()) continue;
                pos.set(x, ground, z);
                BlockState under = level.getBlockState(pos);
                boolean cold = level.getBiome(pos.set(x, ground + 1, z)).value().coldEnoughToSnow(pos);
                if (c.mat() == 0 && !cold) continue;
                Terrain.clearAbove(level, pos, x, ground, z, c.mat() + c.stub() + 4);
                for (int h = 1; h <= c.mat(); h++) Terrain.set(level, pos, x, ground + h, z, leaves);
                if (c.stub() > 0) {
                    // the stem's root end, crooked up out of the mat
                    for (int h = 1; h <= c.stub(); h++) Terrain.set(level, pos, x, ground + h, z, Blocks.SPRUCE_LOG.defaultBlockState());
                } else if (c.stem()) {
                    Terrain.set(level, pos, x, ground + 1, z, lying);
                } else if (c.dead() && Terrain.noise(seed + 8, x, ground, z) < 0.45F) {
                    Terrain.set(level, pos, x, ground + 1, z, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, along));
                }
                if (!cold) continue;
                int top = ground + Math.max(c.mat(), c.stub());
                int layers = c.mat() > 0 ? (c.snow() > 0 ? Math.min(3, c.snow()) : 0) : c.snow();
                if (c.mat() == 0 && !under.isFaceSturdy(level, pos.set(x, ground, z), Direction.UP)) continue; // (never on powder snow, nor ice)
                if (layers > 0 && !under.is(BlockTags.ICE)) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, Math.min(7, layers)));
                    if (c.mat() == 0 && under.hasProperty(SnowyDirtBlock.SNOWY)) Terrain.set(level, pos, x, ground, z, under.setValue(SnowyDirtBlock.SNOWY, true));
                }
            }
        }
        for (int i = 0; i < trees.length / 4; i++) tree(level, area, pos, i, grounds, minX, maxX, minZ, maxZ, leaves);
    }

    /** Flag tree {@code i}: a bare trunk on the windward side, with all its branches streaming away downwind. */
    private void tree(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos, int i, int[] grounds, int minX, int maxX, int minZ, int maxZ,
                      BlockState leaves) {
        int tx = trees[i * 4], tz = trees[i * 4 + 1], base = trees[i * 4 + 2], height = trees[i * 4 + 3];
        if (tx + 5 < minX || tx - 5 > maxX || tz + 5 < minZ || tz - 5 > maxZ) return;
        float dx = Mth.cos(angle), dz = Mth.sin(angle);
        if (tx >= minX && tx <= maxX && tz >= minZ && tz <= maxZ) {
            int g = grounds[(tx - minX) * (maxZ - minZ + 1) + (tz - minZ)];
            // rooted in the ground where it is, however that differs a little from the lie of the land round it
            if (g < base + height - 1) {
                for (int h = Math.min(g, base) + 1; h <= base + height; h++) Terrain.set(level, pos, tx, h, tz, Blocks.SPRUCE_LOG.defaultBlockState());
            }
        }
        for (int k = 2; k <= height; k++) {
            // the flag comes in ragged tiers
            if (k < height && Terrain.hash(seed, 300 + i * 16 + k) < 0.15F) continue;
            float frac = (k - 2) / (float) Math.max(1, height - 2);
            int length = k == height ? 1 : Math.max(1, Math.round(3.3F * (1.0F - frac) + Terrain.hash(seed, 400 + i * 16 + k) * 0.8F - 0.3F));
            for (int j = 1; j <= length; j++) {
                int bx = tx + Math.round(dx * j), bz = tz + Math.round(dz * j);
                // the longest branches droop a little at their tips
                int by = base + k - (j == length && length >= 3 ? 1 : 0);
                boolean limb = j == 1 && k <= 3 && height >= 6;
                branch(level, area, pos, bx, by, bz, limb ? Blocks.SPRUCE_LOG.defaultBlockState()
                        .setValue(RotatedPillarBlock.AXIS, Math.abs(dx) > Math.abs(dz) ? Direction.Axis.X : Direction.Axis.Z) : leaves);
                // and spread out a little to the sides, more so lower down
                if (j < length || k <= height / 2 + 1) {
                    for (int side = -1; side <= 1; side += 2) {
                        if (Terrain.hash(seed, 500 + i * 64 + k * 8 + j * 2 + (side + 1) / 2) < (k <= height / 2 + 1 ? 0.6F : 0.35F)) {
                            branch(level, area, pos, bx + Math.round(-dz * side), by, bz + Math.round(dx * side), leaves);
                        }
                    }
                }
            }
        }
    }

    /** One block of a branch, wherever it's open air (or snow, or something growing). */
    private static void branch(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState state) {
        if (!area.isInside(x, y, z)) return;
        pos.set(x, y, z);
        BlockState was = level.getBlockState(pos);
        if (was.isAir() || was.is(Blocks.SNOW) || Terrain.cover(was) && was.getFluidState().isEmpty() && !was.is(BlockTags.LOGS)) {
            level.setBlock(pos, state, Terrain.FLAGS);
        }
    }
}
