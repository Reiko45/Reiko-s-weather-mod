package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Steaming ground: a patch of hillside warmed from below by hot rock, where steam and volcanic gases seep up through
 * the soil. The sour gases rot the soil and the rock under it to pale clay, bleached white and yellow and crusted here
 * and there with sulphur, and the warm ground itself is stained a rusty orange and steams on cold mornings. Snow never
 * lies on it for long. In a few places the gas has opened a fumarole, a vent hissing out a plume of steam. Nothing
 * grows on the hot, acid ground in the middle, and round its edges stand the bleached trunks of the trees it killed as
 * it spread.
 *
 * @param y      the ground in the middle
 * @param radius the patch's rough radius
 * @param vents  {x, z, built up into a little chimney (1) or not (0)} for each fumarole
 * @param trees  {x, z, height} for each dead tree
 */
public record SteamingGround(int cx, int cz, int y, int radius, int[] vents, int[] trees, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 1 || !c.fits(ground, 26, 44)) {
            return null;
        }
        int radius = 10 + c.random().nextInt(11);
        if (c.relief(radius, 10) > 18) {
            return null;
        }
        int nv = 2 + c.random().nextInt(4);
        int[] vents = new int[nv * 3];
        for (int i = 0; i < nv; i++) {
            float a = c.random().nextFloat() * Mth.TWO_PI, d = Mth.sqrt(c.random().nextFloat()) * radius * 0.55F;
            vents[i * 3] = c.x() + Math.round(Mth.cos(a) * d);
            vents[i * 3 + 1] = c.z() + Math.round(Mth.sin(a) * d);
            vents[i * 3 + 2] = c.random().nextFloat() < 0.4F ? 1 : 0;
        }
        int nt = 3 + c.random().nextInt(5);
        int[] trees = new int[nt * 3];
        for (int i = 0; i < nt; i++) {
            float a = c.random().nextFloat() * Mth.TWO_PI, d = radius * (0.85F + 0.3F * c.random().nextFloat());
            trees[i * 3] = c.x() + Math.round(Mth.cos(a) * d);
            trees[i * 3 + 1] = c.z() + Math.round(Mth.sin(a) * d);
            trees[i * 3 + 2] = 3 + c.random().nextInt(4);
        }
        return new SteamingGround(c.x(), c.z(), ground, radius, vents, trees, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new SteamingGround(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getIntArray("vents"), tag.getIntArray("trees"),
                tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.put("vents", new IntArrayTag(vents));
        tag.put("trees", new IntArrayTag(trees));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.STEAMING_GROUND;
    }

    @Override
    public BoundingBox bounds() {
        int r = Mth.ceil(radius * 1.55F) + 4;
        return new BoundingBox(cx - r, y - 26, cz - r, cx + r, y + 44, cz + r);
    }

    /** How hot the ground is at a column: above 0 inside the patch, highest in the middle. */
    private float heat(int x, int z) {
        float dx = x + 0.5F - cx, dz = z + 0.5F - cz;
        float a = (float) Mth.atan2(dz, dx);
        float lobes = 1.0F + 0.22F * Mth.sin(3.0F * a + Terrain.hash(seed, 1) * 6.0F) + 0.12F * Mth.sin(5.0F * a + Terrain.hash(seed, 2) * 6.0F);
        return 1.0F - Mth.sqrt(dx * dx + dz * dz) / (radius * lobes) - 0.3F * (Terrain.field(seed, x, z, 4.0) - 0.5F);
    }

    private static BlockState wood(Holder<Biome> biome) {
        if (biome.is(BiomeTags.IS_SAVANNA)) return Blocks.STRIPPED_ACACIA_LOG.defaultBlockState();
        if (biome.is(BiomeTags.IS_TAIGA) || biome.value().getBaseTemperature() < 0.4F) return Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
        return Blocks.STRIPPED_OAK_LOG.defaultBlockState();
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Block steaming = ModBlocks.STEAMING_GROUND.get(), sulphur = ModBlocks.SULFUR_CRUST.get();
        // where each dead tree in this chunk stands, for its branches once the ground's done
        int[] bases = new int[trees.length / 3];
        java.util.Arrays.fill(bases, Integer.MIN_VALUE);
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float heat = heat(x, z);
                int tree = -1;
                for (int i = 0; i < trees.length / 3; i++) {
                    if (trees[i * 3] == x && trees[i * 3 + 1] == z) tree = i;
                }
                if (heat <= 0.0F && tree < 0) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 4 || ground > b.maxY() - 14) continue;
                // a pond on it stays as it is
                if (!level.getFluidState(pos.set(x, ground + 1, z)).isEmpty()) continue;
                BlockState was = level.getBlockState(pos.set(x, ground, z));
                if (was.isAir() || was.is(BlockTags.LOGS) || !was.getFluidState().isEmpty()) continue;
                float n = Terrain.noise(seed, x, ground, z), p = Terrain.field(seed + 1, x, z, 2.5);
                if (heat > 0.15F) {
                    // the trees here died long ago and are gone, and snow melts as it lands
                    Terrain.clearAbove(level, pos, x, ground, z, Math.min(24, b.maxY() - ground));
                } else {
                    // the fringe: only the snow and the plants go, not the trees standing round it
                    for (int h = ground + 1; h <= ground + 2; h++) {
                        BlockState s = level.getBlockState(pos.set(x, h, z));
                        if (!s.isAir() && Terrain.cover(s) && !s.is(BlockTags.LOGS) && !s.is(BlockTags.LEAVES) && s.getFluidState().isEmpty()) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Terrain.FLAGS);
                        }
                    }
                }
                // the nearest fumarole
                int vent = -1;
                float near = Float.MAX_VALUE;
                for (int i = 0; i < vents.length / 3; i++) {
                    float d = Mth.sqrt(Mth.square(x - vents[i * 3]) + Mth.square(z - vents[i * 3 + 1]));
                    if (d < near) {
                        near = d;
                        vent = i;
                    }
                }
                boolean snowy = was.is(Blocks.SNOW_BLOCK) || was.is(Blocks.POWDER_SNOW) || was.is(BlockTags.ICE);
                BlockState top = null, under = null;
                if (heat > 0.0F) {
                    if (near < 1.7F && n < 0.65F || near < 2.6F && n < 0.25F) {
                        // sulphur crusted round the vents, and the clay yellowed by it
                        top = n < 0.4F ? sulphur.defaultBlockState() : Blocks.YELLOW_TERRACOTTA.defaultBlockState();
                    } else if (heat > 0.15F) {
                        // the warm, orange-stained ground in blotches, and between them the clay the gas has bleached
                        float q = Terrain.field(seed + 3, x, z, 3.5);
                        if (p < (heat > 0.45F ? 0.5F : 0.38F)) {
                            top = steaming.defaultBlockState();
                        } else if (q < 0.42F) {
                            top = Blocks.WHITE_TERRACOTTA.defaultBlockState();
                        } else if (q < 0.6F) {
                            top = n < 0.45F ? Blocks.CALCITE.defaultBlockState() : n < 0.75F ? Blocks.YELLOW_TERRACOTTA.defaultBlockState()
                                    : Blocks.WHITE_TERRACOTTA.defaultBlockState();
                        } else {
                            top = heat < 0.3F && q > 0.75F ? null : Blocks.COARSE_DIRT.defaultBlockState();
                        }
                        if (heat > 0.45F && n > 0.97F) top = sulphur.defaultBlockState();
                    } else {
                        // the fringe, browned and bleached in patches
                        top = p < 0.4F || n < 0.25F ? Blocks.COARSE_DIRT.defaultBlockState() : p < 0.5F ? steaming.defaultBlockState() : null;
                    }
                    if (top == null && snowy) top = Blocks.COARSE_DIRT.defaultBlockState();
                    // turf left as it was has no snow on it any more
                    if (top == null && was.hasProperty(BlockStateProperties.SNOWY)) top = was.setValue(BlockStateProperties.SNOWY, false);
                    // a block or two down, the ground is rotted to pale clay too
                    if (heat > 0.3F && top != null) {
                        under = Terrain.noise(seed + 2, x, ground - 1, z) < 0.5F ? Blocks.WHITE_TERRACOTTA.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
                    }
                }
                if (top != null) Terrain.set(level, pos, x, ground, z, top);
                if (under != null) {
                    BlockState below = level.getBlockState(pos.set(x, ground - 1, z));
                    if (!below.isAir() && below.getFluidState().isEmpty()) Terrain.set(level, pos, x, ground - 1, z, under);
                }
                boolean clear = level.getBlockState(pos.set(x, ground + 1, z)).isAir();
                if (vent >= 0 && near < 0.5F && heat > 0.0F && clear) {
                    // a fumarole: flush with the ground, or on a little chimney of sulphur it has built round itself
                    if (vents[vent * 3 + 2] == 1 && level.getBlockState(pos.set(x, ground + 2, z)).isAir()) {
                        Terrain.set(level, pos, x, ground, z, sulphur.defaultBlockState());
                        Terrain.set(level, pos, x, ground + 1, z, ModBlocks.VOLCANIC_VENT.get().defaultBlockState());
                    } else {
                        Terrain.set(level, pos, x, ground, z, ModBlocks.VOLCANIC_VENT.get().defaultBlockState());
                    }
                    continue;
                }
                if (tree >= 0 && clear) {
                    // a dead tree, bleached and stripped of its bark
                    Terrain.clearAbove(level, pos, x, ground, z, trees[tree * 3 + 2] + 2);
                    BlockState log = wood(level.getBiome(pos.set(x, ground, z)));
                    for (int h = ground + 1; h <= ground + trees[tree * 3 + 2]; h++) Terrain.set(level, pos, x, h, z, log);
                    bases[tree] = ground;
                    continue;
                }
                BlockState now = level.getBlockState(pos.set(x, ground, z));
                if (clear && heat > 0.0F && heat < 0.45F && n > 0.95F && (now.is(BlockTags.DIRT) || now.is(BlockTags.TERRACOTTA))) {
                    Terrain.set(level, pos, x, ground + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                }
            }
        }
        // a stub of a branch or two on the bigger dead trees, where they reach no further than this chunk
        for (int i = 0; i < bases.length; i++) {
            if (bases[i] == Integer.MIN_VALUE || trees[i * 3 + 2] < 4) continue;
            int tx = trees[i * 3], tz = trees[i * 3 + 1], height = trees[i * 3 + 2];
            BlockState log = wood(level.getBiome(pos.set(tx, bases[i], tz)));
            for (int k = 0; k < 2; k++) {
                if (Terrain.hash(seed, 100 + i * 2 + k) < 0.35F) continue;
                Direction dir = Direction.from2DDataValue(Math.floorMod((int) (Terrain.hash(seed, 200 + i * 2 + k) * 4.0F), 4));
                int h = bases[i] + 2 + Math.round(Terrain.hash(seed, 300 + i * 2 + k) * (height - 3));
                int bx = tx + dir.getStepX(), bz = tz + dir.getStepZ();
                if (!area.isInside(bx, h, bz)) continue;
                pos.set(bx, h, bz);
                if (level.getBlockState(pos).isAir()) level.setBlock(pos, log.setValue(RotatedPillarBlock.AXIS, dir.getAxis()), Terrain.FLAGS);
            }
        }
    }
}
