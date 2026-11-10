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

/**
 * Fairy rings: circles of mushrooms in a meadow or a woodland glade, each marking the living edge of a single fungus
 * spreading out through the soil. The mycelium starts from one spore and grows outward year after year, feeding as it
 * goes, so its edge is a ring that slowly widens (some are centuries old and many yards across). Just ahead of it the
 * fungus breaks down the dead matter in the soil and the grass grows lush and dark on what it frees; where it fruits,
 * a ring of mushrooms springs up; and behind it the spent soil, matted with old threads, sheds the rain, so the grass
 * inside withers in brown, bare patches.
 *
 * @param y     the level of the ground in the middle
 * @param rings {x, z, radius} for each ring
 */
public record FairyRings(int cx, int cz, int y, int[] rings, long seed) implements Landform {
    private static final int SPREAD = 26;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 1 || !c.fits(ground, 14, 16) || c.relief(SPREAD, 8) > 10) {
            return null;
        }
        int n = 2 + c.random().nextInt(4);
        int[] rings = new int[n * 3];
        int placed = 0;
        for (int attempt = 0; attempt < 30 && placed < n; attempt++) {
            float a = c.random().nextFloat() * Mth.TWO_PI, d = Mth.sqrt(c.random().nextFloat()) * SPREAD;
            int x = c.x() + Math.round(Mth.cos(a) * d), z = c.z() + Math.round(Mth.sin(a) * d);
            int r = 3 + c.random().nextInt(7);
            // each fungus keeps to its own patch of ground: where two meet, both stop growing
            boolean clear = true;
            for (int k = 0; k < placed && clear; k++) {
                float dd = Mth.sqrt(Mth.square(x - rings[k * 3]) + Mth.square(z - rings[k * 3 + 1]));
                if (dd < r + rings[k * 3 + 2] + 5.0F) clear = false;
            }
            if (!clear) continue;
            rings[placed * 3] = x;
            rings[placed * 3 + 1] = z;
            rings[placed * 3 + 2] = r;
            placed++;
        }
        if (placed < 2) {
            return null;
        }
        return new FairyRings(c.x(), c.z(), ground, java.util.Arrays.copyOf(rings, placed * 3), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new FairyRings(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getIntArray("rings"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.put("rings", new IntArrayTag(rings));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.FAIRY_RINGS;
    }

    @Override
    public BoundingBox bounds() {
        int r = SPREAD + 9 + 4;
        return new BoundingBox(cx - r, y - 16, cz - r, cx + r, y + 18, cz + r);
    }

    /** Clears a plant (both halves of a tall one) out of a block; false if there's something more than a plant there. */
    private static boolean open(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z) {
        pos.set(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        if (!state.is(BlockTags.REPLACEABLE) || !state.getFluidState().isEmpty()) return false;
        if (state.getBlock() instanceof DoublePlantBlock) {
            pos.set(x, y + 1, z);
            if (level.getBlockState(pos).getBlock() instanceof DoublePlantBlock) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Terrain.FLAGS);
        }
        Terrain.set(level, pos, x, y, z, Blocks.AIR.defaultBlockState());
        return true;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                // how far outside (or, below 0, inside) the nearest ring's edge this is
                int ring = -1;
                float best = Float.MAX_VALUE;
                for (int i = 0; i < rings.length / 3; i++) {
                    float d = Mth.sqrt(Mth.square(x + 0.5F - rings[i * 3]) + Mth.square(z + 0.5F - rings[i * 3 + 1]));
                    float e = d - rings[i * 3 + 2] * (0.92F + 0.16F * Terrain.field(seed + i, x, z, 5.0));
                    if (Math.abs(e) < Math.abs(best)) {
                        best = e;
                        ring = i;
                    }
                }
                if (ring < 0 || best > 2.6F || best < -rings[ring * 3 + 2]) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 2 || ground > b.maxY() - 3) continue;
                pos.set(x, ground, z);
                BlockState top = level.getBlockState(pos);
                // only on soil, and never under water
                if (!top.is(BlockTags.DIRT) || !level.getFluidState(pos.set(x, ground + 1, z)).isEmpty()) continue;
                float n = Terrain.noise(seed, x, ground, z), m = Terrain.noise(seed + 1, x, ground, z);
                if (Math.abs(best) < 0.75F) {
                    // the fruiting ring: dark, fungus-matted soil, with the mushrooms standing in it
                    boolean podzol = n > 0.22F;
                    Terrain.set(level, pos, x, ground, z, podzol ? Blocks.PODZOL.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState());
                    if (!podzol || m < 0.3F || !open(level, pos, x, ground + 1, z)) continue;
                    boolean red = Terrain.hash(seed, 10 + ring) < 0.35F;
                    if (m > 0.92F) red = !red; // the odd one of the other kind
                    Terrain.set(level, pos, x, ground + 1, z, red ? Blocks.RED_MUSHROOM.defaultBlockState() : Blocks.BROWN_MUSHROOM.defaultBlockState());
                } else if (best > 0.0F) {
                    // just outside: the grass grows thick and lush on what the fungus frees
                    pos.set(x, ground + 1, z);
                    if (!level.getBlockState(pos).isAir() || m < 0.3F) continue;
                    pos.set(x, ground + 2, z);
                    boolean room = level.getBlockState(pos).isAir();
                    if (m > 0.86F && room) {
                        BlockState grass = (n < 0.5F ? Blocks.TALL_GRASS : Blocks.LARGE_FERN).defaultBlockState();
                        Terrain.set(level, pos, x, ground + 1, z, grass.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
                        Terrain.set(level, pos, x, ground + 2, z, grass.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
                    } else {
                        Terrain.set(level, pos, x, ground + 1, z, m > 0.68F ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
                    }
                } else if (best < -1.2F && Terrain.field(seed + 7, x, z, 3.0) > 0.55F && top.is(Blocks.GRASS_BLOCK)) {
                    // inside: the spent soil sheds the rain, and the grass dies back in brown patches
                    if (!open(level, pos, x, ground + 1, z)) continue;
                    Terrain.set(level, pos, x, ground, z, n < 0.75F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState());
                    if (m > 0.97F) Terrain.set(level, pos, x, ground + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                }
            }
        }
    }
}
