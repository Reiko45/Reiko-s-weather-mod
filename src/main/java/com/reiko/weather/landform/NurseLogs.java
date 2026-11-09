package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Nurse logs and windthrow: in an old forest, trees blown down by storms lie where they fell and slowly rot. Moss
 * creeps over them, mushrooms feed on the soft wood, and seedlings take root along their backs, so in time a row of
 * young trees grows out of each fallen one. Where a tree was torn up whole, its plate of roots stands on edge at the
 * end of the trunk, still clutching its soil, beside the pit it was ripped out of.
 *
 * @param logs {x, z, direction (0-3), length, root plate radius (0 if it snapped instead)} for each fallen tree
 */
public record NurseLogs(int cx, int cz, int y, int[] logs, long seed) implements Landform {
    private static final int SPREAD = 36;
    private static final int[] DX = {1, 0, -1, 0}, DZ = {0, 1, 0, -1};

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 1 || c.relief(SPREAD - 4, 8) > 14 || !c.fits(ground, 20, 24)) {
            return null;
        }
        int n = 6 + c.random().nextInt(5);
        int[] logs = new int[n * 5];
        int placed = 0;
        for (int i = 0; i < n * 3 && placed < n; i++) {
            float a = c.random().nextFloat() * Mth.TWO_PI, d = Mth.sqrt(c.random().nextFloat()) * SPREAD;
            int x = c.x() + Math.round(Mth.cos(a) * d), z = c.z() + Math.round(Mth.sin(a) * d);
            int dir = c.random().nextInt(4), length = 6 + c.random().nextInt(7), plate = c.random().nextFloat() < 0.55F ? 2 + c.random().nextInt(2) : 0;
            // don't let one fall across another
            boolean clear = true;
            for (int k = 0; k < placed && clear; k++) {
                clear = !overlaps(logs, k, x, z, dir, length, plate);
            }
            if (!clear) continue;
            logs[placed * 5] = x;
            logs[placed * 5 + 1] = z;
            logs[placed * 5 + 2] = dir;
            logs[placed * 5 + 3] = length;
            logs[placed * 5 + 4] = plate;
            placed++;
        }
        return new NurseLogs(c.x(), c.z(), ground, java.util.Arrays.copyOf(logs, placed * 5), c.random().nextLong());
    }

    /** The box a fallen tree takes up, root plate and pit included: {minX, minZ, maxX, maxZ}. */
    private static int[] footprint(int x, int z, int dir, int length, int plate) {
        int back = 2 * plate + 2, across = Math.max(plate, 1) + 1;
        int x1 = x - DX[dir] * back, z1 = z - DZ[dir] * back, x2 = x + DX[dir] * (length + 1), z2 = z + DZ[dir] * (length + 1);
        int px = Math.abs(DZ[dir]) * across, pz = Math.abs(DX[dir]) * across;
        return new int[]{Math.min(x1, x2) - px, Math.min(z1, z2) - pz, Math.max(x1, x2) + px, Math.max(z1, z2) + pz};
    }

    private static boolean overlaps(int[] logs, int k, int x, int z, int dir, int length, int plate) {
        int[] a = footprint(logs[k * 5], logs[k * 5 + 1], logs[k * 5 + 2], logs[k * 5 + 3], logs[k * 5 + 4]);
        int[] b = footprint(x, z, dir, length, plate);
        return a[0] <= b[2] + 1 && b[0] <= a[2] + 1 && a[1] <= b[3] + 1 && b[1] <= a[3] + 1;
    }

    static Landform load(CompoundTag tag) {
        return new NurseLogs(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getIntArray("logs"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.put("logs", new IntArrayTag(logs));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.NURSE_LOGS;
    }

    @Override
    public BoundingBox bounds() {
        int r = SPREAD + 16;
        return new BoundingBox(cx - r, y - 20, cz - r, cx + r, y + 26, cz + r);
    }

    /** A fallen tree's wood, the seedlings that root in it, and how thick its trunk is. */
    private record Wood(BlockState log, BlockState sapling, int thick) {}

    /** The forest's own wood, from the biome in the middle of the fallen tree (always in reach of the chunk being built). */
    private Wood wood(WorldGenLevel level, BlockPos.MutableBlockPos pos, int k) {
        int dir = logs[k * 5 + 2], mid = (logs[k * 5 + 3] - 2 * logs[k * 5 + 4] - 1) / 2;
        Holder<Biome> biome = level.getBiome(pos.set(logs[k * 5] + DX[dir] * mid, y, logs[k * 5 + 1] + DZ[dir] * mid));
        float pick = Terrain.hash(seed, 10 + k);
        Block log, sapling;
        int thick = 1;
        if (biome.is(Biomes.DARK_FOREST)) {
            log = Blocks.DARK_OAK_LOG;
            sapling = Blocks.DARK_OAK_SAPLING;
            thick = pick < 0.6F ? 2 : 1;
        } else if (biome.is(BiomeTags.IS_TAIGA)) {
            log = Blocks.SPRUCE_LOG;
            sapling = Blocks.SPRUCE_SAPLING;
            // the giants of the old growth come down two blocks thick
            thick = pick < 0.5F && (biome.is(Biomes.OLD_GROWTH_PINE_TAIGA) || biome.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA)) ? 2 : 1;
        } else if (biome.is(Biomes.BIRCH_FOREST) || biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST) || pick > 0.75F) {
            log = Blocks.BIRCH_LOG;
            sapling = Blocks.BIRCH_SAPLING;
        } else {
            log = Blocks.OAK_LOG;
            sapling = Blocks.OAK_SAPLING;
        }
        Direction.Axis axis = DX[dir] != 0 ? Direction.Axis.X : Direction.Axis.Z;
        return new Wood(log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis), sapling.defaultBlockState(), thick);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int count = logs.length / 5;
        for (int k = 0; k < count; k++) {
            int bx = logs[k * 5], bz = logs[k * 5 + 1], dir = logs[k * 5 + 2], length = logs[k * 5 + 3], plate = logs[k * 5 + 4];
            int[] box = footprint(bx, bz, dir, length, plate);
            if (box[2] < area.minX() || box[0] > area.maxX() || box[3] < area.minZ() || box[1] > area.maxZ()) continue;
            Wood wood = wood(level, pos, k);
            int thick = wood.thick();
            int dx = DX[dir], dz = DZ[dir], px = -dz, pz = dx;
            for (int x = Math.max(Math.max(area.minX(), b.minX()), box[0]); x <= Math.min(Math.min(area.maxX(), b.maxX()), box[2]); x++) {
                for (int z = Math.max(Math.max(area.minZ(), b.minZ()), box[1]); z <= Math.min(Math.min(area.maxZ(), b.maxZ()), box[3]); z++) {
                    int a = (x - bx) * dx + (z - bz) * dz, w = (x - bx) * px + (z - bz) * pz;
                    float wc = w - (thick - 1) / 2.0F;
                    int ground = Terrain.ground(level, pos, x, z);
                    if (ground < b.minY() + 4 || ground > b.maxY() - 12) continue;
                    // nothing lies in a pond or a stream
                    if (!level.getFluidState(pos.set(x, ground + 1, z)).isEmpty()) continue;
                    if (a >= 1 && a <= length && w >= 0 && w < thick) {
                        trunk(level, pos, x, ground, z, a, length, wood, k);
                    } else if (a == 0 && plate > 0 && Math.abs(wc) <= plate + 0.5F) {
                        plate(level, pos, x, ground, z, wc, plate, thick);
                    } else if (a < 0 && plate > 0) {
                        pit(level, pos, x, ground, z, a, wc, plate);
                    }
                }
            }
        }
    }

    /** One block of a fallen trunk, with whatever is growing on its back. */
    private void trunk(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int ground, int z, int a, int length, Wood wood, int k) {
        Terrain.clearAbove(level, pos, x, ground, z, wood.thick() + 3);
        for (int h = 1; h <= wood.thick(); h++) Terrain.set(level, pos, x, ground + h, z, wood.log());
        int top = ground + wood.thick();
        float n = Terrain.noise(seed + k, x, top, z);
        if (a == length && n < 0.5F) return; // the broken end, bare wood
        if (n < 0.1F) {
            // a soft, rotten stretch with a seedling rooted in it
            Terrain.set(level, pos, x, top, z, Blocks.MOSS_BLOCK.defaultBlockState());
            Terrain.set(level, pos, x, top + 1, z, wood.sapling());
        } else if (n < 0.2F) {
            Terrain.set(level, pos, x, top, z, Blocks.MOSS_BLOCK.defaultBlockState());
            Terrain.set(level, pos, x, top + 1, z, Blocks.FERN.defaultBlockState());
        } else if (n < 0.3F) {
            Terrain.set(level, pos, x, top + 1, z, n < 0.27F ? Blocks.BROWN_MUSHROOM.defaultBlockState() : Blocks.RED_MUSHROOM.defaultBlockState());
        } else if (n < 0.88F) {
            Terrain.set(level, pos, x, top + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
        }
    }

    /** One column of the root plate: a disc of roots and soil standing on edge, roots dangling from its underside. */
    private void plate(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int ground, int z, float wc, int radius, int thick) {
        float r = radius + 0.45F, centre = ground + radius + (thick - 1) * 0.5F;
        float span = Mth.sqrt(Math.max(0.0F, r * r - wc * wc));
        int low = Math.max(ground + 1, Mth.ceil(centre - span)), high = Mth.floor(centre + span);
        if (high < low) return;
        Terrain.clearAbove(level, pos, x, ground, z, high - ground + 2);
        for (int h = low; h <= high; h++) {
            float n = Terrain.noise(seed, x, h, z);
            Terrain.set(level, pos, x, h, z, n < 0.5F ? Blocks.ROOTED_DIRT.defaultBlockState() : n < 0.85F ? Blocks.DIRT.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
        }
        if (low - 1 > ground) Terrain.set(level, pos, x, low - 1, z, Blocks.HANGING_ROOTS.defaultBlockState());
        if (Terrain.noise(seed, x, high, z) < 0.3F) Terrain.set(level, pos, x, high + 1, z, Blocks.FERN.defaultBlockState());
        if (Terrain.noise(seed, x, high, z) < 0.5F) Terrain.set(level, pos, x, high, z, Blocks.ROOTED_DIRT.defaultBlockState());
    }

    /** The hollow left where the roots were torn out of the ground. */
    private void pit(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int ground, int z, int a, float wc, int radius) {
        float da = (a + radius + 1.0F) / (radius + 0.5F), dw = wc / (radius + 0.5F);
        float d = da * da + dw * dw;
        if (d >= 1.0F) return;
        int depth = 1 + Math.round(1.2F * (1.0F - d));
        Terrain.clearAbove(level, pos, x, ground, z, 6);
        for (int h = ground - depth + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
        float n = Terrain.noise(seed, x, ground, z);
        Terrain.set(level, pos, x, ground - depth, z, n < 0.4F ? Blocks.COARSE_DIRT.defaultBlockState() : n < 0.75F ? Blocks.PODZOL.defaultBlockState()
                : Blocks.ROOTED_DIRT.defaultBlockState());
        if (n > 0.85F) Terrain.set(level, pos, x, ground - depth + 1, z, Blocks.FERN.defaultBlockState());
    }
}
