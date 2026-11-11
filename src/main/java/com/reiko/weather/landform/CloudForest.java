package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SporeBlossomBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * A tropical montane cloud forest, like those on the slopes of the Andes, at Monteverde in Costa Rica or on Mount
 * Kinabalu: a jungle mountainside high enough that the clouds sit on it most days. Warm, wet air from the lowlands is
 * pushed up the mountain, cools as it rises and condenses into a bank of cloud with a fairly steady base, and above that
 * line the forest is wrapped in mist for much of the year. Much of its water never falls as rain at all: the trees comb
 * it straight out of the passing cloud, and it drips off every leaf and branch. In that constant damp everything that
 * can grow on something else does, so the trees are shorter and more gnarled than the jungle below but furred all over
 * with moss and hung with vines, tree ferns crowd the gaps between them, and the ground is a spongy carpet of moss and
 * ferns round mossy boulders.
 *
 * @param base   the cloud base: the forest grows on ground above it (give or take a few blocks), and the jungle below is left alone
 * @param top    the highest ground it covers
 * @param radius its rough radius
 */
public record CloudForest(int cx, int cz, int base, int top, int radius, long seed) implements Landform {
    /** The trees stand one to a cell this size; how far a tree's crown, branches, roots and vines reach from its trunk. */
    private static final int CELL = 6, REACH = 6;
    /** The mossy boulders lie one to a cell this size (where there's one at all). */
    private static final int ROCKS = 14;
    /** How far the forest's biome (and its mist) reaches below the ground and above it. */
    private static final int BIOME_BELOW = 5, BIOME_ABOVE = 24;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 25 || !c.fits(ground, 20, 60)) {
            return null;
        }
        // a real mountainside, not a level upland
        if (c.relief(24, 8) < 8) {
            return null;
        }
        // the lie of the land round about: the ring just looked at, and a wider one
        int[] h = new int[21];
        h[0] = ground;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI * 2.0 / 8;
            h[1 + i] = c.groundAt(c.x() + (int) Math.round(Math.cos(a) * 24), c.z() + (int) Math.round(Math.sin(a) * 24));
        }
        for (int i = 0; i < 12; i++) {
            double a = (i + 0.5) * Math.PI * 2.0 / 12;
            h[9 + i] = c.groundAt(c.x() + (int) Math.round(Math.cos(a) * 44), c.z() + (int) Math.round(Math.sin(a) * 44));
        }
        Arrays.sort(h);
        // the cloud base lies across the lower slopes, so most of the mountainside here is above it
        int base = Math.max(c.seaLevel() + 18, Math.min(h[5], ground - 4)) + c.random().nextInt(3) - 1;
        int top = Math.min(Math.max(h[20] + 8, base + 24), base + 90);
        if (!c.fits(top, 0, BIOME_ABOVE + 10)) {
            return null;
        }
        return new CloudForest(c.x(), c.z(), base, top, 38 + c.random().nextInt(15), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new CloudForest(tag.getInt("x"), tag.getInt("z"), tag.getInt("base"), tag.getInt("top"), tag.getInt("radius"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("base", base);
        tag.putInt("top", top);
        tag.putInt("radius", radius);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.CLOUD_FOREST;
    }

    @Override
    public BoundingBox bounds() {
        int r = Mth.ceil(radius * 1.15F) + REACH + 2;
        return new BoundingBox(cx - r, base - 10, cz - r, cx + r, top + BIOME_ABOVE, cz + r);
    }

    /** How far out a column is, as a share of the forest's ragged radius (under 1 inside it). */
    private float edge(int x, int z) {
        float dx = x + 0.5F - cx, dz = z + 0.5F - cz;
        return Mth.sqrt(dx * dx + dz * dz) / (radius * (0.85F + 0.3F * Terrain.field(seed + 1, x, z, 20.0)));
    }

    /** The cloud base at a column: it wavers a little round the mountain. */
    private int cloudLine(int x, int z) {
        return base + Math.round((Terrain.field(seed + 2, x, z, 11.0) - 0.5F) * 6.0F);
    }

    /** How thick the forest is at a column: 1 in the heart of it, thinning out to its edges and down to the cloud base (0 outside it). */
    private float thick(int x, int z, int ground) {
        float e = edge(x, z);
        int line = cloudLine(x, z);
        if (e >= 1.0F || ground < line || ground > top) return 0.0F;
        return Math.min(Terrain.smooth((1.0F - e) / 0.25F), Terrain.smooth((ground - line + 1) / 6.0F));
    }

    /**
     * The ground in a column, under any trees, plants, moss and vines. It sees through everything the forest grows on the
     * ground, whether the chunk it's in has been built yet or not, so a tree standing in one chunk and reaching into the
     * next is always the same tree in both.
     */
    private static int soil(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
        int y = level.getHeight(Terrain.floor(level), x, z) - 1;
        int min = level.getMinBuildHeight();
        while (y > min) {
            BlockState s = level.getBlockState(pos.set(x, y, z));
            if (growth(s) || s.getBlock() instanceof LiquidBlock) {
                y--;
            } else {
                break;
            }
        }
        return y;
    }

    private static boolean growth(BlockState s) {
        Block b = s.getBlock();
        return Terrain.cover(s) || b instanceof LeavesBlock || s.is(BlockTags.LOGS) || s.is(Blocks.JUNGLE_LOG) || s.is(Blocks.OAK_LOG) || s.is(Blocks.JUNGLE_WOOD)
                || b instanceof BushBlock || b instanceof VineBlock || b instanceof MultifaceBlock || b instanceof CarpetBlock || b instanceof SporeBlossomBlock
                || b instanceof CocoaBlock;
    }

    /** Whether a block is the jungle's own leaves (this forest's own are always persistent). */
    private static boolean wild(BlockState s) {
        return s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT);
    }

    /** Whether a block is something a log can grow through: open air or a small plant. */
    private static boolean soft(BlockState s) {
        return s.isAir() || growth(s) && !(s.getBlock() instanceof LeavesBlock) && !s.is(BlockTags.LOGS) && !s.is(Blocks.JUNGLE_LOG) && !s.is(Blocks.OAK_LOG)
                && !(s.getBlock() instanceof DoublePlantBlock) && s.getFluidState().isEmpty();
    }

    // ---- where things stand: all of it a function of position alone, so every chunk agrees on it ----

    /** The tree that might stand in a cell: {kind (0 none, 1 a canopy tree, 2 a small mossy tree), trunk x, trunk z}. */
    private int[] site(int i, int j) {
        float k = Terrain.noise(seed + 10, i, 0, j);
        int x = i * CELL + 1 + (int) (Terrain.noise(seed + 11, i, 0, j) * (CELL - 2));
        int z = j * CELL + 1 + (int) (Terrain.noise(seed + 12, i, 0, j) * (CELL - 2));
        return new int[]{k < 0.55F ? 1 : k < 0.8F ? 2 : 0, x, z};
    }

    /** Whether a column is where a tree's trunk might stand. */
    private boolean trunkColumn(int x, int z) {
        int[] s = site(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
        return s[0] != 0 && s[1] == x && s[2] == z;
    }

    /** Whether a tree might stand within {@code d} blocks of a column. */
    private boolean nearTree(int x, int z, float d) {
        int i0 = Math.floorDiv(x, CELL), j0 = Math.floorDiv(z, CELL);
        for (int i = i0 - 1; i <= i0 + 1; i++) {
            for (int j = j0 - 1; j <= j0 + 1; j++) {
                int[] s = site(i, j);
                if (s[0] != 0 && Mth.square(s[1] - x) + Mth.square(s[2] - z) <= d * d) return true;
            }
        }
        return false;
    }

    /** The boulder that might lie in a cell: {x, z, radius in tenths}, or null. */
    @Nullable
    private int[] rock(int i, int j) {
        if (Terrain.noise(seed + 20, i, 0, j) > 0.45F) return null;
        int x = i * ROCKS + 3 + (int) (Terrain.noise(seed + 21, i, 0, j) * (ROCKS - 6));
        int z = j * ROCKS + 3 + (int) (Terrain.noise(seed + 22, i, 0, j) * (ROCKS - 6));
        return new int[]{x, z, 13 + (int) (Terrain.noise(seed + 23, i, 0, j) * 14)};
    }

    /** How high a mossy boulder stands at a column (0 where there's none). */
    private int boulder(int x, int z) {
        int i0 = Math.floorDiv(x, ROCKS), j0 = Math.floorDiv(z, ROCKS);
        for (int i = i0 - 1; i <= i0 + 1; i++) {
            for (int j = j0 - 1; j <= j0 + 1; j++) {
                int[] r = rock(i, j);
                if (r == null) continue;
                float rad = r[2] / 10.0F;
                float d = Mth.sqrt(Mth.square(r[0] - x) + Mth.square(r[1] - z)) / (rad * (0.85F + 0.3F * Terrain.noise(seed + 24, x, 0, z)));
                if (d < 1.0F) return 1 + Math.round(rad * 0.8F * Mth.sqrt(1.0F - d * d));
            }
        }
        return 0;
    }

    /** Whether a boulder might lie within a couple of blocks of a column (so no tree grows there). */
    private boolean nearBoulder(int x, int z) {
        int i0 = Math.floorDiv(x, ROCKS), j0 = Math.floorDiv(z, ROCKS);
        for (int i = i0 - 1; i <= i0 + 1; i++) {
            for (int j = j0 - 1; j <= j0 + 1; j++) {
                int[] r = rock(i, j);
                if (r != null && Mth.sqrt(Mth.square(r[0] - x) + Mth.square(r[1] - z)) < r[2] / 10.0F * 1.15F + 2.0F) return true;
            }
        }
        return false;
    }

    /** Whether a tree fern might grow in a column: now and then, clear of the trees and the boulders. */
    private boolean treeFern(int x, int z) {
        return Terrain.noise(seed + 5, x, 0, z) < 0.014F && boulder(x, z) == 0 && !nearTree(x, z, 1.5F);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        int x0 = Math.max(area.minX(), b.minX()), x1 = Math.min(area.maxX(), b.maxX());
        int z0 = Math.max(area.minZ(), b.minZ()), z1 = Math.min(area.maxZ(), b.maxZ());
        if (x0 > x1 || z0 > z1) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int w = x1 - x0 + 1, d = z1 - z0 + 1;
        // each column's ground before anything grows on it, where it's in the forest (and MIN_VALUE where it isn't)
        int[] soils = new int[w * d];
        Arrays.fill(soils, Integer.MIN_VALUE);
        boolean any = false;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (edge(x, z) >= 1.0F) continue;
                int g = soil(level, pos, x, z);
                if (g >= b.minY() + 2 && thick(x, z, g) > 0.0F) {
                    soils[(x - x0) * d + (z - z0)] = g;
                    any = true;
                }
            }
        }
        if (!any) return;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int g = soils[(x - x0) * d + (z - z0)];
                if (g != Integer.MIN_VALUE) floor(level, pos, x, g, z, thick(x, z, g), b);
            }
        }
        // the jungle's own trees, where any stand here already (before this forest's own go in, so only theirs are touched)
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int g = soils[(x - x0) * d + (z - z0)];
                if (g != Integer.MIN_VALUE) drape(level, area, pos, x, g, z, b);
            }
        }
        // the forest's own trees, from every cell near enough to reach into this chunk
        int i0 = Math.floorDiv(x0 - REACH, CELL), i1 = Math.floorDiv(x1 + REACH, CELL);
        int j0 = Math.floorDiv(z0 - REACH, CELL), j1 = Math.floorDiv(z1 + REACH, CELL);
        java.util.List<Tree> trees = new java.util.ArrayList<>();
        for (int i = i0; i <= i1; i++) {
            for (int j = j0; j <= j1; j++) {
                Tree t = tree(level, pos, b, i, j);
                if (t != null) trees.add(t);
            }
        }
        // (all the wood first, then all the leaves, then what grows on them, so where trees meet it's always the same way)
        for (Tree t : trees) t.wood(level, area, b, pos);
        for (Tree t : trees) t.leaves(level, area, b, pos);
        for (Tree t : trees) t.hangings(level, area, b, pos);
        // the cool, misty biome over the whole forest, from just under its ground to above its treetops
        paint(level, area, b, soils, x0, z0, w, d);
    }

    /** The forest floor in a column: moss over the soil and the rocks, ferns, mossy boulders and tree ferns. */
    private void floor(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int g, int z, float t, BoundingBox b) {
        if (Terrain.surface(level, pos, x, g, z) > g) return; // (a pond: left as it is)
        BlockState was = level.getBlockState(pos.set(x, g, z));
        BlockState surface = was;
        float m = Terrain.field(seed + 3, x, z, 6.0) + (Terrain.noise(seed + 3, x, g, z) - 0.5F) * 0.2F;
        if ((was.is(Blocks.GRASS_BLOCK) || was.is(Blocks.PODZOL) || was.is(Blocks.COARSE_DIRT) || was.is(Blocks.DIRT)) && m < 0.25F + 0.4F * t) {
            surface = Blocks.MOSS_BLOCK.defaultBlockState();
        } else if ((was.is(Blocks.STONE) || was.is(Blocks.ANDESITE) || was.is(Blocks.COBBLESTONE) || was.is(Blocks.TUFF)) && m < 0.3F + 0.3F * t) {
            // bare rock furred with moss (but never where a tree might stand: whether a tree can take root somewhere mustn't change)
            surface = m < 0.15F + 0.3F * t && !trunkColumn(x, z) ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        }
        if (surface != was) Terrain.set(level, pos, x, g, z, surface);
        BlockState above = level.getBlockState(pos.set(x, g + 1, z));
        if (!(above.isAir() || above.is(Blocks.SHORT_GRASS) || above.is(Blocks.FERN)) || g + 8 > b.maxY()) return;
        int rock = boulder(x, z);
        if (rock > 0 && t > 0.15F) {
            // a mossy boulder, fallen from the crags above long ago
            for (int h = 1; h <= rock; h++) {
                BlockState here = level.getBlockState(pos.set(x, g + h, z));
                if (!here.isAir() && !(here.is(Blocks.SHORT_GRASS) || here.is(Blocks.FERN))) break;
                boolean cap = h == rock && Terrain.noise(seed + 6, x, g + h, z) < 0.45F;
                Terrain.set(level, pos, x, g + h, z, cap ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                if (cap && level.getBlockState(pos.set(x, g + h + 1, z)).isAir() && Terrain.noise(seed + 7, x, g, z) < 0.6F) {
                    Terrain.set(level, pos, x, g + h + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                }
            }
            return;
        }
        if (!surface.is(BlockTags.DIRT) || trunkColumn(x, z)) return;
        int stalk = 1 + Math.round(Terrain.noise(seed + 8, x, g, z) * 2.0F);
        if (t > 0.3F && treeFern(x, z) && clear(level, pos, x, g + 1, z, stalk + 3)) {
            // a tree fern: a fibrous stalk, a crown of moss and a great spray of fronds
            for (int h = 1; h <= stalk; h++) Terrain.set(level, pos, x, g + h, z, Blocks.JUNGLE_WOOD.defaultBlockState());
            Terrain.set(level, pos, x, g + stalk + 1, z, Blocks.MOSS_BLOCK.defaultBlockState());
            largeFern(level, pos, x, g + stalk + 2, z);
            return;
        }
        float n = Terrain.noise(seed + 4, x, g, z);
        if (n < 0.02F + 0.08F * t && clear(level, pos, x, g + 1, z, 2)) {
            largeFern(level, pos, x, g + 1, z);
        } else if (n < 0.26F) {
            Terrain.set(level, pos, x, g + 1, z, Blocks.FERN.defaultBlockState());
        } else if (n < 0.38F) {
            Terrain.set(level, pos, x, g + 1, z, surface.is(Blocks.MOSS_BLOCK) ? Blocks.MOSS_CARPET.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
        } else if (n > 0.994F) {
            Terrain.set(level, pos, x, g + 1, z, Blocks.BLUE_ORCHID.defaultBlockState());
        }
    }

    /** Whether the {@code height} blocks from y up are open (or only short grass or a fern, at the bottom). */
    private static boolean clear(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, int height) {
        for (int h = 0; h < height; h++) {
            BlockState s = level.getBlockState(pos.set(x, y + h, z));
            if (!s.isAir() && !(h == 0 && (s.is(Blocks.SHORT_GRASS) || s.is(Blocks.FERN)))) return false;
        }
        return true;
    }

    private static void largeFern(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z) {
        Terrain.set(level, pos, x, y, z, Blocks.LARGE_FERN.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
        Terrain.set(level, pos, x, y + 1, z, Blocks.LARGE_FERN.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /**
     * Drapes whatever of the jungle's own trees stand in a column: moss on top of their leaves, vines hanging off the
     * edges of their crowns and up their trunks, glow lichen here and there, and spore blossoms under their branches.
     */
    private void drape(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos, int x, int g, int z, BoundingBox b) {
        // (as high as anything stands here or beside: whatever's been built round about, the jungle's own leaves are under that)
        int high = level.getHeight(Terrain.floor(level), x, z);
        for (Direction dir : Direction.Plane.HORIZONTAL) high = Math.max(high, level.getHeight(Terrain.floor(level), x + dir.getStepX(), z + dir.getStepZ()));
        high = Math.min(Math.min(high, g + 36), b.maxY() - 2);
        for (int y = g + 1; y <= high; y++) {
            BlockState s = level.getBlockState(pos.set(x, y, z));
            if (wild(s)) {
                if (Terrain.noise(seed + 30, x, y, z) < 0.4F && level.getBlockState(pos.set(x, y + 1, z)).isAir()) {
                    Terrain.set(level, pos, x, y + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                    y++;
                }
                continue;
            }
            if (!s.isAir()) continue;
            float k = Terrain.noise(seed + 31, x, y, z);
            BlockState up = level.getBlockState(pos.set(x, y + 1, z));
            if (k < 0.12F && up.is(BlockTags.LOGS) && level.getBlockState(pos.set(x, y - 1, z)).isAir()) {
                Terrain.set(level, pos, x, y, z, Blocks.SPORE_BLOSSOM.defaultBlockState());
                continue;
            }
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                int nx = x + dir.getStepX(), nz = z + dir.getStepZ();
                BlockState n = level.getBlockState(pos.set(nx, y, nz));
                if (wild(n) && k < 0.3F && !(up.getBlock() instanceof VineBlock)) {
                    // a curtain of vines off the edge of a crown
                    int length = 1 + Math.round(Terrain.noise(seed + 32, x, y, z) * 6.0F);
                    BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(dir), true);
                    for (int h = y; h > y - length && h > g; h--) {
                        if (!level.getBlockState(pos.set(x, h, z)).isAir()) break;
                        Terrain.set(level, pos, x, h, z, vine);
                    }
                    break;
                }
                // (only a trunk in this chunk, where every log is still the jungle's own)
                if (Terrain.column(area, nx, nz) && n.is(BlockTags.LOGS) && k < 0.34F) {
                    Terrain.set(level, pos, x, y, z, k < 0.03F ? Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(dir), true)
                            : Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(dir), true));
                    break;
                }
            }
        }
    }

    /** The tree that stands in a cell, if one does: it needs a footing of soil in the forest, clear of the jungle's own trees. */
    @Nullable
    private Tree tree(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int i, int j) {
        int[] s = site(i, j);
        if (s[0] == 0 || edge(s[1], s[2]) >= 1.0F || nearBoulder(s[1], s[2])) return null;
        int x = s[1], z = s[2];
        int g = soil(level, pos, x, z);
        float t = thick(x, z, g);
        if (t <= 0.0F || Terrain.noise(seed + 13, i, 0, j) > 0.2F + 0.8F * t || g + 22 > b.maxY() || g < b.minY() + 2) return null;
        if (!level.getBlockState(pos.set(x, g, z)).is(BlockTags.DIRT) || Terrain.surface(level, pos, x, g, z) > g) return null;
        for (int h = g + 1; h <= g + 32; h++) {
            if (wild(level.getBlockState(pos.set(x, h, z)))) return null; // (the jungle has a tree here already)
        }
        return new Tree(x, g, z, s[0] == 1, i, j);
    }

    /**
     * One of the forest's own trees: a gnarled canopy tree, or a small mossy tree of the understorey. Every block of it
     * follows from its cell and the ground at its foot, so each chunk builds its own share of it and the shares fit.
     */
    private final class Tree {
        final int x, g, z, height;
        final boolean canopy;
        final BlockState log, leaves;
        /** {x, y, z, radius (tenths), vertical radius (tenths)} for each blob of leaves. */
        final int[] blobs;
        /** {x, y, z, axis (0 along x, 1 along z)} for each block of its branches. */
        final int[] branches;
        final long tseed;

        Tree(int x, int g, int z, boolean canopy, int i, int j) {
            this.x = x;
            this.g = g;
            this.z = z;
            this.canopy = canopy;
            this.tseed = seed + 1000L * i + 7919L * j;
            float a = Terrain.hash(tseed, 1), c = Terrain.hash(tseed, 2), e = Terrain.hash(tseed, 3);
            boolean oak = !canopy && Terrain.hash(tseed, 4) < 0.5F;
            this.log = oak ? Blocks.OAK_LOG.defaultBlockState() : Blocks.JUNGLE_LOG.defaultBlockState();
            this.leaves = (oak ? Blocks.OAK_LEAVES : Blocks.JUNGLE_LEAVES).defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            if (canopy) {
                height = 5 + (int) (a * 5.99F);
                int top = g + height, count = (int) (c * 2.99F);
                int[] bl = new int[(1 + count) * 5];
                int[] br = new int[0];
                bl[0] = x;
                bl[1] = top + 1;
                bl[2] = z;
                bl[3] = 20 + (int) (e * 15);
                bl[4] = 16 + (int) (Terrain.hash(tseed, 5) * 6);
                for (int k = 0; k < count; k++) {
                    // a branch reaching out from high on the trunk, with a smaller crown of its own
                    float ang = Terrain.hash(tseed, 10 + k) * Mth.TWO_PI, ca = Mth.cos(ang), sa = Mth.sin(ang);
                    int len = 2 + (int) (Terrain.hash(tseed, 20 + k) * 1.99F), from = top - 1 - (int) (Terrain.hash(tseed, 30 + k) * 2.99F);
                    int axis = Math.abs(ca) > Math.abs(sa) ? 0 : 1;
                    int[] more = Arrays.copyOf(br, br.length + len * 4);
                    int bx = x, by = from, bz = z;
                    for (int s = 1; s <= len; s++) {
                        bx = x + Math.round(ca * s);
                        bz = z + Math.round(sa * s);
                        by = from + (s >= 2 ? 1 : 0);
                        int o = br.length + (s - 1) * 4;
                        more[o] = bx;
                        more[o + 1] = by;
                        more[o + 2] = bz;
                        more[o + 3] = axis;
                    }
                    br = more;
                    int o = (1 + k) * 5;
                    bl[o] = bx;
                    bl[o + 1] = by + 1;
                    bl[o + 2] = bz;
                    bl[o + 3] = 14 + (int) (Terrain.hash(tseed, 40 + k) * 8);
                    bl[o + 4] = 11 + (int) (Terrain.hash(tseed, 50 + k) * 5);
                }
                blobs = bl;
                branches = br;
            } else {
                height = 2 + (int) (a * 2.99F);
                blobs = new int[]{x, g + height + 1, z, 15 + (int) (e * 7), 12 + (int) (c * 4)};
                branches = new int[0];
            }
        }

        /** Whether a spot is among this tree's leaves. */
        boolean leafy(int px, int py, int pz) {
            for (int k = 0; k < blobs.length / 5; k++) {
                float r = blobs[k * 5 + 3] / 10.0F, rv = blobs[k * 5 + 4] / 10.0F;
                float dx = px - blobs[k * 5], dy = py - blobs[k * 5 + 1], dz = pz - blobs[k * 5 + 2];
                // (flatter underneath than on top, and ragged all over)
                float vy = dy < 0 ? dy / (rv * 0.6F) : dy / rv;
                if ((dx * dx + dz * dz) / (r * r) + vy * vy < 0.8F + 0.35F * Terrain.noise(tseed + 1, px, py, pz)) return true;
            }
            return false;
        }

        private void put(WorldGenLevel level, BoundingBox area, BoundingBox b, BlockPos.MutableBlockPos pos, int px, int py, int pz, BlockState s) {
            if (area.isInside(px, py, pz) && b.isInside(px, py, pz)) Terrain.set(level, pos, px, py, pz, s);
        }

        private static boolean air(WorldGenLevel level, BlockPos.MutableBlockPos pos, int px, int py, int pz) {
            return level.getBlockState(pos.set(px, py, pz)).isAir();
        }

        /** The trunk, its roots and its branches. */
        void wood(WorldGenLevel level, BoundingBox area, BoundingBox b, BlockPos.MutableBlockPos pos) {
            for (int h = g + 1; h <= g + height; h++) {
                if (!area.isInside(x, h, z)) continue;
                if (!soft(level.getBlockState(pos.set(x, h, z)))) break;
                put(level, area, b, pos, x, h, z, log);
            }
            if (canopy) {
                // buttress roots, spreading out over the moss
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    int rx = x + dir.getStepX(), rz = z + dir.getStepZ();
                    if (Terrain.hash(tseed, 60 + dir.ordinal()) > 0.45F || !Terrain.column(area, rx, rz)) continue;
                    int rg = soil(level, pos, rx, rz);
                    if (rg < g - 2 || rg > g) continue;
                    for (int h = rg + 1; h <= g + 1; h++) {
                        if (!air(level, pos, rx, h, rz)) break;
                        put(level, area, b, pos, rx, h, rz, log);
                    }
                }
            }
            for (int k = 0; k < branches.length / 4; k++) {
                int bx = branches[k * 4], by = branches[k * 4 + 1], bz = branches[k * 4 + 2];
                if (!area.isInside(bx, by, bz) || !soft(level.getBlockState(pos.set(bx, by, bz)))) continue;
                put(level, area, b, pos, bx, by, bz, Blocks.JUNGLE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,
                        branches[k * 4 + 3] == 0 ? Direction.Axis.X : Direction.Axis.Z));
            }
        }

        /** The crown: a few heavy, ragged blobs of leaves. */
        void leaves(WorldGenLevel level, BoundingBox area, BoundingBox b, BlockPos.MutableBlockPos pos) {
            for (int k = 0; k < blobs.length / 5; k++) {
                int r = Mth.ceil(blobs[k * 5 + 3] / 10.0F), rv = Mth.ceil(blobs[k * 5 + 4] / 10.0F);
                int bx = blobs[k * 5], by = blobs[k * 5 + 1], bz = blobs[k * 5 + 2];
                for (int px = Math.max(bx - r, area.minX()); px <= Math.min(bx + r, area.maxX()); px++) {
                    for (int pz = Math.max(bz - r, area.minZ()); pz <= Math.min(bz + r, area.maxZ()); pz++) {
                        for (int py = by - rv; py <= by + rv; py++) {
                            if (leafy(px, py, pz) && air(level, pos, px, py, pz)) put(level, area, b, pos, px, py, pz, leaves);
                        }
                    }
                }
            }
        }

        /** What grows on the tree: moss on its crown and branches, vines down its trunk and off its crown, lichen, and spore blossoms. */
        void hangings(WorldGenLevel level, BoundingBox area, BoundingBox b, BlockPos.MutableBlockPos pos) {
            float vines = canopy ? 0.4F : 0.7F;
            for (int h = g + 1; h < g + height; h++) {
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    int px = x + dir.getStepX(), pz = z + dir.getStepZ();
                    if (!area.isInside(px, h, pz) || !air(level, pos, px, h, pz)) continue;
                    float k = Terrain.noise(tseed + 2, px, h, pz);
                    if (k < 0.05F) {
                        put(level, area, b, pos, px, h, pz, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(dir.getOpposite()), true));
                    } else if (k < vines) {
                        put(level, area, b, pos, px, h, pz, Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(dir.getOpposite()), true));
                    }
                }
            }
            for (int k = 0; k < branches.length / 4; k++) {
                int bx = branches[k * 4], by = branches[k * 4 + 1], bz = branches[k * 4 + 2];
                if (!area.isInside(bx, by, bz) || !level.getBlockState(pos.set(bx, by, bz)).is(Blocks.JUNGLE_LOG)) continue;
                float n = Terrain.noise(tseed + 3, bx, by, bz);
                if (n < 0.55F && air(level, pos, bx, by + 1, bz)) {
                    put(level, area, b, pos, bx, by + 1, bz, Blocks.MOSS_CARPET.defaultBlockState());
                } else if (n > 0.65F && air(level, pos, bx, by - 1, bz)) {
                    put(level, area, b, pos, bx, by - 1, bz, Blocks.SPORE_BLOSSOM.defaultBlockState()); // (hanging from the branch, which holds it up)
                }
            }
            float edgeVines = canopy ? 0.2F : 0.32F;
            for (int k = 0; k < blobs.length / 5; k++) {
                int r = Mth.ceil(blobs[k * 5 + 3] / 10.0F) + 1, rv = Mth.ceil(blobs[k * 5 + 4] / 10.0F);
                int bx = blobs[k * 5], by = blobs[k * 5 + 1], bz = blobs[k * 5 + 2];
                for (int px = Math.max(bx - r, area.minX()); px <= Math.min(bx + r, area.maxX()); px++) {
                    for (int pz = Math.max(bz - r, area.minZ()); pz <= Math.min(bz + r, area.maxZ()); pz++) {
                        for (int py = by - rv - 1; py <= by + rv + 1; py++) {
                            if (!air(level, pos, px, py, pz)) continue;
                            float n = Terrain.noise(tseed + 4, px, py, pz);
                            if (leafy(px, py - 1, pz)) {
                                // moss lying thick on top of the crown
                                if (n < 0.5F && level.getBlockState(pos.set(px, py - 1, pz)).getBlock() instanceof LeavesBlock) put(level, area, b, pos, px, py, pz, Blocks.MOSS_CARPET.defaultBlockState());
                                continue;
                            }
                            if (n < 0.03F && level.getBlockState(pos.set(px, py + 1, pz)).getBlock() instanceof LeavesBlock) {
                                put(level, area, b, pos, px, py, pz, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.UP), true));
                                continue;
                            }
                            if (leafy(px, py, pz) || leafy(px, py + 1, pz) || n >= edgeVines) continue;
                            for (Direction dir : Direction.Plane.HORIZONTAL) {
                                if (!leafy(px - dir.getStepX(), py, pz - dir.getStepZ())) continue;
                                // a curtain of vines off the edge of the crown, as far down as the air below it goes
                                int length = 1 + (int) (Terrain.noise(tseed + 5, px, py, pz) * (canopy ? 6.99F : 3.99F));
                                BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(dir.getOpposite()), true);
                                for (int h = py; h > py - length && h > g; h--) {
                                    if (!air(level, pos, px, h, pz)) break;
                                    put(level, area, b, pos, px, h, pz, vine);
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    /** Paints the forest's biome over every 4x4x4 cell whose middle column is in the forest, from just under its ground to above its trees. */
    private static void paint(WorldGenLevel level, BoundingBox area, BoundingBox b, int[] soils, int x0, int z0, int w, int d) {
        var holder = level.registryAccess().registryOrThrow(Registries.BIOME).getHolder(Terrain.CLOUD_FOREST_BIOME).orElse(null);
        if (holder == null) return;
        ChunkAccess chunk = level.getChunk(area.minX() >> 4, area.minZ() >> 4);
        chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> {
            int x = QuartPos.toBlock(qx) + 2, y = QuartPos.toBlock(qy) + 2, z = QuartPos.toBlock(qz) + 2;
            if (x >= x0 && x < x0 + w && z >= z0 && z < z0 + d) {
                int g = soils[(x - x0) * d + (z - z0)];
                if (g != Integer.MIN_VALUE && y >= g - BIOME_BELOW && y <= Math.min(b.maxY(), g + BIOME_ABOVE)) return holder;
            }
            return chunk.getNoiseBiome(qx, qy, qz);
        }, null);
    }
}
