package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A ghost forest: a stand of dead trees, bare and bleached silver-grey, standing in a salt marsh by the sea. It was
 * once a woodland on low ground behind the coast, but the land sank (in a great earthquake, or slowly as the sea rose)
 * and salt water crept in at every high tide. The salt killed the trees where they stood, and the marsh grasses moved
 * in round their roots; but the trunks are so steeped in salt that they hardly rot, and stand for a century or more,
 * stripped of their bark and most of their branches, with the tide pooling between them.
 *
 * @param trees {x, z, height, wood} for each dead tree
 */
public record GhostForest(int cx, int cz, int sea, int radius, int[] trees, long seed) implements Landform {
    private static final int F = 4;
    private static final Block[] WOODS = {Blocks.STRIPPED_OAK_LOG, Blocks.STRIPPED_SPRUCE_LOG, Blocks.STRIPPED_BIRCH_LOG};

    @Nullable
    static Landform plan(PlanContext c) {
        return c.search(12, 12, GhostForest::planAt);
    }

    @Nullable
    private static Landform planAt(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        int radius = 24 + c.random().nextInt(13);
        // low, flat ground right at the level of the sea
        if (ground < c.seaLevel() - 2 || ground > c.seaLevel() + 3 || c.relief(radius / 2, 8) > 8) {
            return null;
        }
        int n = 10 + c.random().nextInt(16);
        int[] trees = new int[n * F];
        int placed = 0;
        for (int attempt = 0; attempt < 60 && placed < n; attempt++) {
            float a = c.random().nextFloat() * Mth.TWO_PI, d = Mth.sqrt(c.random().nextFloat()) * radius * 0.65F;
            int x = c.x() + Math.round(Mth.cos(a) * d), z = c.z() + Math.round(Mth.sin(a) * d);
            boolean clear = true;
            for (int k = 0; k < placed && clear; k++) {
                if (Math.abs(x - trees[k * F]) + Math.abs(z - trees[k * F + 1]) < 4) clear = false;
            }
            if (!clear) continue;
            trees[placed * F] = x;
            trees[placed * F + 1] = z;
            trees[placed * F + 2] = 4 + c.random().nextInt(7);
            trees[placed * F + 3] = c.random().nextInt(WOODS.length);
            placed++;
        }
        if (placed < 10) {
            return null;
        }
        return new GhostForest(c.x(), c.z(), c.seaLevel(), radius, java.util.Arrays.copyOf(trees, placed * F), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new GhostForest(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getInt("r"), tag.getIntArray("trees"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("sea", sea);
        tag.putInt("r", radius);
        tag.put("trees", new IntArrayTag(trees));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.GHOST_FOREST;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * 1.25F) + 3;
        return new BoundingBox(cx - r, sea - 14, cz - r, cx + r, sea + 16, cz + r);
    }

    /** How far out in the marsh a column is (under 1 inside it). */
    private float extent(int x, int z) {
        return Mth.sqrt(Mth.square(x + 0.5F - cx) + Mth.square(z + 0.5F - cz)) / (radius * (0.8F + 0.4F * Terrain.field(seed, x, z, 16.0)));
    }

    /** Whether a column is well inside the marsh, where it's all built to plan (no blending into the land round it). */
    private boolean core(int x, int z) {
        return extent(x, z) < 0.8F;
    }

    /** Whether a column holds a pool of standing sea water: only where every side of it is marsh too, so it can't spill. */
    private boolean pool(int x, int z) {
        return Terrain.field(seed + 4, x, z, 7.0) < 0.32F && core(x, z) && core(x + 1, z) && core(x - 1, z) && core(x, z + 1) && core(x, z - 1);
    }

    /** The top of the marsh at a column inside it: tussocks a block above the tide line, bare mud at it, pools below. */
    private int marsh(int x, int z) {
        if (pool(x, z)) return Terrain.field(seed + 4, x, z, 7.0) < 0.2F ? sea - 3 : sea - 2;
        return Terrain.field(seed + 3, x, z, 9.0) > 0.48F ? sea : sea - 1;
    }

    /** The trees that have fallen and lie in the marsh: {x, z, length, along x}. */
    private int[] fallen(int k) {
        float a = Terrain.hash(seed, 60 + k) * Mth.TWO_PI, d = Terrain.hash(seed, 70 + k) * radius * 0.5F;
        return new int[]{cx + Math.round(Mth.cos(a) * d), cz + Math.round(Mth.sin(a) * d), 3 + Math.round(Terrain.hash(seed, 80 + k) * 3.0F),
                Terrain.hash(seed, 90 + k) < 0.5F ? 1 : 0};
    }

    private int fallenCount() {
        return 1 + Math.round(Terrain.hash(seed, 59) * 2.0F);
    }

    /** Whether a fallen trunk lies over a column. */
    private boolean underLog(int x, int z) {
        for (int k = 0; k < fallenCount(); k++) {
            int[] f = fallen(k);
            if (f[3] == 1 ? z == f[1] && x >= f[0] && x < f[0] + f[2] : x == f[0] && z >= f[1] && z < f[1] + f[2]) return true;
        }
        return false;
    }

    /** Whether water stands at this height in a column next to this one that the marsh doesn't reach (and so never changes). */
    private boolean pondBeside(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int h, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int nx = x + d.getStepX(), nz = z + d.getStepZ();
            if (extent(nx, nz) > 1.0F && !level.getFluidState(pos.set(nx, h, nz)).isEmpty()) return true;
        }
        return false;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float d = extent(x, z);
                if (d > 1.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 3 || ground > b.maxY() - 12) continue;
                boolean wet = !level.getFluidState(pos.set(x, ground + 1, z)).isEmpty();
                boolean pool = d < 0.8F && pool(x, z);
                // blending into the land (or the sea bed) at the edge
                float edge = Terrain.smooth((1.0F - d) / 0.2F);
                int top = d < 0.8F ? marsh(x, z) : Math.round(Mth.lerp(edge, ground, marsh(x, z)));
                Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, Math.min(12, b.maxY() - Math.max(ground, top)));
                // any pond standing here above the sea goes too (the marsh keeps its own pools)
                int surface = ground;
                for (pos.set(x, ground + 1, z); surface < ground + 8 && !level.getFluidState(pos).isEmpty(); pos.move(Direction.UP)) surface++;
                for (int h = top + 1; h <= Math.max(Math.max(ground, surface), sea - 1); h++) {
                    BlockState fill = h <= sea - 1 && (wet || pool) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    // a pond just outside the marsh keeps a bank of mud between it and the lowered ground
                    if (fill.isAir() && pondBeside(level, pos, x, h, z)) fill = Blocks.MUD.defaultBlockState();
                    Terrain.set(level, pos, x, h, z, fill);
                }
                float n = Terrain.noise(seed, x, sea, z), m = Terrain.noise(seed + 1, x, sea, z);
                for (int h = Math.max(b.minY(), Math.min(ground, top) - 2); h <= top; h++) {
                    BlockState s = h < top - 1 ? Blocks.CLAY.defaultBlockState() : Blocks.MUD.defaultBlockState();
                    if (h == top && top >= sea && !pool) {
                        // salt-scorched patches among the marsh grass
                        s = Terrain.field(seed + 5, x, z, 5.0) > 0.7F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                    }
                    Terrain.set(level, pos, x, h, z, s);
                }
                if (pool) {
                    if (top < sea - 2 && n > 0.8F) Terrain.set(level, pos, x, top + 1, z, Blocks.SEAGRASS.defaultBlockState());
                    continue;
                }
                if (top < sea - 1 || underLog(x, z)) continue;
                if (top >= sea) {
                    // the marsh grasses that took over from the trees
                    if (n > 0.82F && level.getBlockState(pos.set(x, top + 2, z)).isAir()) {
                        BlockState grass = Blocks.TALL_GRASS.defaultBlockState();
                        Terrain.set(level, pos, x, top + 1, z, grass.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
                        Terrain.set(level, pos, x, top + 2, z, grass.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
                    } else if (n > 0.3F) {
                        Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                    } else if (n < 0.04F) {
                        Terrain.set(level, pos, x, top + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                    }
                } else if (m > 0.85F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState()); // pioneers out on the mud
                }
            }
        }
        // the dead trees, once the marsh is in round them
        for (int i = 0; i < trees.length / F; i++) {
            int tx = trees[i * F], tz = trees[i * F + 1], height = trees[i * F + 2];
            if (tx < area.minX() - 3 || tx > area.maxX() + 3 || tz < area.minZ() - 3 || tz > area.maxZ() + 3 || !core(tx, tz)) continue;
            Block wood = WOODS[Math.floorMod(trees[i * F + 3], WOODS.length)];
            int base = marsh(tx, tz) + 1;
            for (int h = base; h < base + height; h++) Terrain.setIn(level, area, pos, tx, h, tz, wood.defaultBlockState());
            // a few broken-off stubs of branches
            int branches = height < 6 ? Math.round(Terrain.hash(seed, 1000 + i)) : 1 + Math.round(Terrain.hash(seed, 1000 + i) * 1.4F);
            for (int k = 0; k < branches; k++) {
                Direction dir = Direction.from2DDataValue(Math.floorMod((int) (Terrain.hash(seed, 1100 + i * 4 + k) * 4.0F), 4));
                int h = base + 2 + Math.round(Terrain.hash(seed, 1200 + i * 4 + k) * (height - 3));
                int len = height > 6 && Terrain.hash(seed, 1300 + i * 4 + k) < 0.5F ? 2 : 1;
                BlockState branch = wood.defaultBlockState().setValue(RotatedPillarBlock.AXIS, dir.getAxis());
                for (int s = 1; s <= len; s++) {
                    int bx = tx + dir.getStepX() * s, bz = tz + dir.getStepZ() * s;
                    if (!area.isInside(bx, h, bz)) continue;
                    pos.set(bx, h, bz);
                    if (level.getBlockState(pos).isAir()) level.setBlock(pos, branch, Terrain.FLAGS);
                }
            }
        }
        // and the ones that have fallen, lying in the mud and floating in the pools
        for (int k = 0; k < fallenCount(); k++) {
            int[] f = fallen(k);
            Block wood = WOODS[Math.floorMod(Math.round(Terrain.hash(seed, 95 + k) * 10.0F), WOODS.length)];
            BlockState log = wood.defaultBlockState().setValue(RotatedPillarBlock.AXIS, f[3] == 1 ? Direction.Axis.X : Direction.Axis.Z);
            for (int s = 0; s < f[2]; s++) {
                int x = f[0] + (f[3] == 1 ? s : 0), z = f[1] + (f[3] == 1 ? 0 : s);
                if (!area.isInside(x, sea, z) || !core(x, z)) continue;
                pos.set(x, sea, z);
                if (level.getBlockState(pos).is(BlockTags.LOGS)) continue;
                level.setBlock(pos, log, Terrain.FLAGS);
            }
        }
    }
}
