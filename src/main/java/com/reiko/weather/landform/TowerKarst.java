package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Tower karst: where hot, wet weather has dissolved a thick bed of limestone for millions of years, all that's
 * left are steep-sided towers standing out of the jungle (or the sea), their tops tufted with trees and their
 * sheer grey walls hung with vines.
 *
 * @param towers {x, z, radius, height, base} for each tower
 */
public record TowerKarst(int cx, int cz, int[] towers, long seed) implements Landform {
    private static final int SPREAD = 44;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() - 6 || !c.fits(ground, 10, 70)) {
            return null;
        }
        int n = 5 + c.random().nextInt(5);
        int[] towers = new int[n * 5];
        for (int i = 0; i < n; i++) {
            float a = c.random().nextFloat() * Mth.TWO_PI, d = Mth.sqrt(c.random().nextFloat()) * SPREAD;
            int x = c.x() + Math.round(Mth.cos(a) * d), z = c.z() + Math.round(Mth.sin(a) * d);
            int r = 4 + c.random().nextInt(5);
            // the towers stand on the lowest ground round their feet, and some reach a long way up
            int base = Math.min(c.groundAt(x, z), Math.min(c.groundAt(x + r, z), c.groundAt(x - r, z)));
            base = Math.min(base, Math.min(c.groundAt(x, z + r), c.groundAt(x, z - r)));
            int h = Math.min(20 + c.random().nextInt(28), r * 7);
            towers[i * 5] = x;
            towers[i * 5 + 1] = z;
            towers[i * 5 + 2] = r;
            towers[i * 5 + 3] = h;
            towers[i * 5 + 4] = base;
        }
        return new TowerKarst(c.x(), c.z(), towers, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new TowerKarst(tag.getInt("x"), tag.getInt("z"), tag.getIntArray("towers"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.put("towers", new IntArrayTag(towers));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.TOWER_KARST;
    }

    @Override
    public BoundingBox bounds() {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int i = 0; i < towers.length; i += 5) {
            min = Math.min(min, towers[i + 4]);
            max = Math.max(max, towers[i + 4] + towers[i + 3]);
        }
        int r = SPREAD + 14;
        return new BoundingBox(cx - r, min - 4, cz - r, cx + r, max + 8, cz + r);
    }

    /** How high tower {@code i} stands above its base at a column (0 if outside it). */
    private float height(int i, float x, float z) {
        float dx = x - towers[i * 5], dz = z - towers[i * 5 + 1];
        float r = towers[i * 5 + 2], h = towers[i * 5 + 3];
        float theta = (float) Mth.atan2(dz, dx);
        float wobble = 1.0F + 0.16F * Mth.sin(3.0F * theta + Terrain.hash(seed, i) * 6.0F) + 0.08F * Mth.sin(5.0F * theta + i);
        float dd = Mth.sqrt(dx * dx + dz * dz) / (r * wobble);
        if (dd >= 1.1F) return 0.0F;
        if (dd > 0.95F) return h * 0.85F * (1.1F - dd) / 0.15F; // the sheer walls, flaring a touch at the foot
        if (dd < 0.45F) return h;
        float t = (dd - 0.45F) / 0.5F;
        return h * (0.85F + 0.15F * Mth.sqrt(Math.max(0.0F, 1.0F - t * t))); // the rounded, wooded top
    }

    /** The tallest tower at a column: {tower index, top y}, or null. */
    @Nullable
    private int[] tower(int x, int z) {
        int best = -1, top = Integer.MIN_VALUE;
        for (int i = 0; i < towers.length / 5; i++) {
            float h = height(i, x + 0.5F, z + 0.5F);
            if (h <= 0.5F) continue;
            int t = towers[i * 5 + 4] + Math.round(h);
            if (t > top) {
                top = t;
                best = i;
            }
        }
        return best < 0 ? null : new int[]{best, top};
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState limestone = ModBlocks.LIMESTONE.get().defaultBlockState();
        BlockState leaves = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        int minX = Math.max(area.minX(), b.minX()), maxX = Math.min(area.maxX(), b.maxX());
        int minZ = Math.max(area.minZ(), b.minZ()), maxZ = Math.min(area.maxZ(), b.maxZ());
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int[] t = tower(x, z);
                if (t == null) continue;
                int i = t[0], top = t[1], base = towers[i * 5 + 4], h = towers[i * 5 + 3];
                int ground = Terrain.ground(level, pos, x, z);
                Terrain.clearAbove(level, pos, x, Math.max(ground, base), z, top - Math.max(ground, base) + 8);
                float wall = height(i, x + 0.5F, z + 0.5F) < h * 0.85F ? 1.0F : 0.0F;
                for (int y = Math.min(ground, base) - 2; y <= top; y++) {
                    // streaks of darker, wetter rock running down the walls
                    float streak = Terrain.field(seed, x * 3.0 + z * 3.0, y * 0.25, 4.0);
                    BlockState s = streak > 0.78F ? Blocks.STONE.defaultBlockState()
                            : streak < 0.12F && wall > 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : limestone;
                    Terrain.set(level, pos, x, y, z, s);
                }
                if (top - base >= h * 0.85F - 1) {
                    // a skin of soil on top, with jungle growing out of it
                    Terrain.set(level, pos, x, top - 1, z, Blocks.DIRT.defaultBlockState());
                    Terrain.set(level, pos, x, top, z, Blocks.GRASS_BLOCK.defaultBlockState());
                    float n = Terrain.noise(seed, x, top, z);
                    if (n > 0.975F) {
                        tree(level, area, pos, x, top + 1, z, leaves);
                    } else if (n > 0.45F) {
                        Terrain.set(level, pos, x, top + 1, z, leaves);
                        if (n > 0.8F) Terrain.set(level, pos, x, top + 2, z, leaves);
                    } else if (n < 0.15F) {
                        Terrain.set(level, pos, x, top + 1, z, Blocks.FERN.defaultBlockState());
                    }
                }
            }
        }
        // vines hanging down the walls (once the towers are all in, so nothing clears them away again)
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int[] t = tower(x, z);
                if (t == null) continue;
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    if (Terrain.noise(seed + d.ordinal(), x, 5, z) > 0.45F) continue;
                    int nx = x + d.getStepX(), nz = z + d.getStepZ();
                    int[] n = tower(nx, nz);
                    int outside = n == null ? Integer.MIN_VALUE : n[1];
                    int start = t[1] - 1 - Math.round(Terrain.noise(seed, x, 6, z) * 6.0F);
                    int end = start - 3 - Math.round(Terrain.noise(seed, x, 7, z) * 16.0F);
                    BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(d.getOpposite()), true);
                    for (int y = start; y > end && y > outside; y--) {
                        if (!area.isInside(nx, y, nz)) continue;
                        pos.set(nx, y, nz);
                        if (!level.getBlockState(pos).isAir()) break;
                        level.setBlock(pos, vine, Terrain.FLAGS);
                    }
                }
            }
        }
    }

    private static void tree(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState leaves) {
        int height = 3 + Math.floorMod(x * 31 + z * 17, 3);
        for (int k = 0; k < height; k++) Terrain.set(level, pos, x, y + k, z, Blocks.JUNGLE_LOG.defaultBlockState());
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (Math.abs(dx) + Math.abs(dz) + Math.max(0, dy) * 2 > 3 || dx == 0 && dz == 0 && dy < 1) continue;
                    if (!area.isInside(x + dx, y + height + dy, z + dz)) continue;
                    pos.set(x + dx, y + height + dy, z + dz);
                    if (level.getBlockState(pos).isAir()) level.setBlock(pos, leaves, Terrain.FLAGS);
                }
            }
        }
    }
}
