package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BaseCoralWallFanBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A blue hole: a round, sheer-sided shaft dropping far into a shallow limestone bank, so deep that from above it shows
 * as a disc of dark blue in the pale turquoise of the shallows round it. It was a dry cave when the ice ages locked up
 * so much water that the sea stood far lower; its roof fell in, and when the sea rose again it drowned it. Down in the
 * dark, under the overhanging walls, hang the stalactites that grew while it was still dry, and round its lip the coral
 * has built a reef.
 *
 * @param floor the sea bed at the middle before the hole was there
 */
public record BlueHole(int cx, int cz, int sea, int floor, int radius, int depth, long seed) implements Landform {
    private static final int RING = 9, SLOPE = 10;
    private static final BlockState[] CORAL_BLOCKS = {Blocks.TUBE_CORAL_BLOCK.defaultBlockState(), Blocks.BRAIN_CORAL_BLOCK.defaultBlockState(),
            Blocks.BUBBLE_CORAL_BLOCK.defaultBlockState(), Blocks.FIRE_CORAL_BLOCK.defaultBlockState(), Blocks.HORN_CORAL_BLOCK.defaultBlockState()};
    private static final BlockState[] CORALS = {Blocks.TUBE_CORAL.defaultBlockState(), Blocks.BRAIN_CORAL.defaultBlockState(),
            Blocks.BUBBLE_CORAL.defaultBlockState(), Blocks.FIRE_CORAL.defaultBlockState(), Blocks.HORN_CORAL.defaultBlockState(),
            Blocks.TUBE_CORAL_FAN.defaultBlockState(), Blocks.BRAIN_CORAL_FAN.defaultBlockState(), Blocks.FIRE_CORAL_FAN.defaultBlockState(),
            Blocks.HORN_CORAL_FAN.defaultBlockState()};
    private static final BlockState[] WALL_FANS = {Blocks.TUBE_CORAL_WALL_FAN.defaultBlockState(), Blocks.BRAIN_CORAL_WALL_FAN.defaultBlockState(),
            Blocks.BUBBLE_CORAL_WALL_FAN.defaultBlockState(), Blocks.FIRE_CORAL_WALL_FAN.defaultBlockState(), Blocks.HORN_CORAL_WALL_FAN.defaultBlockState()};

    @Nullable
    static Landform plan(PlanContext c) {
        // shallow banks are patchy, so look about a little for one
        return c.search(16, 16, BlueHole::planAt);
    }

    @Nullable
    private static Landform planAt(PlanContext c) {
        int floor = c.groundAt(c.x(), c.z());
        if (floor > c.seaLevel() - 4 || floor < c.seaLevel() - 16) {
            return null;
        }
        int radius = 7 + c.random().nextInt(7);
        int depth = 30 + c.random().nextInt(26);
        if (!c.fits(floor, depth + 6, 8)) {
            return null;
        }
        // on a broad, shallow bank: open water all round, with no land and no sudden deeps
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F;
            int h = c.groundAt(c.x() + Math.round(Mth.cos(a) * (radius + RING + 4)), c.z() + Math.round(Mth.sin(a) * (radius + RING + 4)));
            if (h > c.seaLevel() - 3 || h < c.seaLevel() - 24) {
                return null;
            }
        }
        return new BlueHole(c.x(), c.z(), c.seaLevel(), floor, radius, depth, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new BlueHole(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getInt("floor"), tag.getInt("r"), tag.getInt("depth"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("sea", sea);
        tag.putInt("floor", floor);
        tag.putInt("r", radius);
        tag.putInt("depth", depth);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.BLUE_HOLE;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * 1.15F) + RING + SLOPE + 3;
        return new BoundingBox(cx - r, floor - depth - 4, cz - r, cx + r, sea + 1, cz + r);
    }

    private float wobble(float theta) {
        return 1.0F + 0.08F * Mth.sin(2.0F * theta + Terrain.hash(seed, 1) * Mth.TWO_PI) + 0.05F * Mth.sin(5.0F * theta + Terrain.hash(seed, 2) * Mth.TWO_PI);
    }

    private static float field3(long seed, double x, double h, double z, double scale) {
        double g = h / scale;
        int k = Mth.floor(g);
        float t = Terrain.smooth((float) (g - k));
        return Mth.lerp(t, Terrain.field(seed + k * 7919L, x, z, scale), Terrain.field(seed + (k + 1) * 7919L, x, z, scale));
    }

    /** The radius of the shaft at a height: sheer under the lip, belling out into the old cave lower down, and narrowing again to the bottom. */
    private float hole(int x, int h, int z, float theta) {
        float t = Mth.clamp((floor - 6 - h) / (float) (depth - 6), 0.0F, 1.0F);
        float bell = 0.18F * Mth.sin(t * Mth.PI);
        return radius * wobble(theta) * (1.0F + bell) + (field3(seed + 7, x, h, z, 4.0) - 0.5F) * 2.0F;
    }

    /** The top of the shallow limestone bank round the hole: a flat of sand a couple of blocks under the surface, sloping off to the sea bed. */
    private int bank(int x, int z, float d, float edge, int ground) {
        int reef = sea - 3 - (Terrain.field(seed + 2, x, z, 5.0) > 0.7F ? 1 : 0);
        if (d < edge + RING) return Math.max(ground, reef);
        float t = Terrain.smooth((d - edge - RING) / SLOPE);
        return Math.max(ground, Math.round(Mth.lerp(t, reef, ground)));
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState limestone = ModBlocks.LIMESTONE.get().defaultBlockState(), water = Blocks.WATER.defaultBlockState();
        int bottom = floor - depth;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float dx = x + 0.5F - cx, dz = z + 0.5F - cz;
                float d = Mth.sqrt(dx * dx + dz * dz), theta = (float) Mth.atan2(dz, dx);
                float edge = radius * wobble(theta);
                if (d > edge + RING + SLOPE) continue;
                int ground = Terrain.ground(level, pos, x, z);
                pos.set(x, ground + 1, z);
                if (level.getFluidState(pos).isEmpty() || ground >= sea - 1) continue; // dry land, a sandbar or an islet: left as it is
                int top = Math.min(sea - 2, bank(x, z, d, edge, ground));
                boolean shaft = d < radius * 1.4F + 5.0F;
                int silt = bottom + Math.round(2.5F * Math.max(0.0F, 1.0F - Mth.square(d / radius)));
                for (int h = Math.max(b.minY(), shaft ? bottom - 2 : ground + 1); h <= sea - 1; h++) {
                    float r = shaft ? hole(x, h, z, theta) : 0.0F;
                    float n = Terrain.noise(seed, x, h, z);
                    BlockState s;
                    if (d < r && h <= top) {
                        // down the hole: sediment settled on the bottom, and the dark water over it
                        if (h > silt) s = water;
                        else if (h == bottom - 2) s = limestone;
                        else s = n < 0.4F ? Blocks.GRAVEL.defaultBlockState() : n < 0.75F ? Blocks.SAND.defaultBlockState() : n < 0.98F ? Blocks.CLAY.defaultBlockState()
                                : Blocks.BONE_BLOCK.defaultBlockState(); // the odd bone of something that fell in
                    } else if (h > top) {
                        pos.set(x, h, z);
                        BlockState was = level.getBlockState(pos);
                        // the old weed (cut off by the new sea bed) goes; the sea stays
                        if (!was.is(Blocks.KELP) && !was.is(Blocks.KELP_PLANT) && !was.is(Blocks.SEAGRASS) && !was.is(Blocks.TALL_SEAGRASS) && !(shaft && d < r)) continue;
                        s = water;
                    } else if (shaft && d < r + 3.5F) {
                        // the walls: reef and sand round the lip, limestone (and old flowstone) below
                        if (h >= top - 1) {
                            boolean reef = h == top ? d < r + 2.0F && n < 0.6F : d < r + 0.8F && n < 0.5F;
                            s = reef ? CORAL_BLOCKS[(int) (n * 97) % CORAL_BLOCKS.length] : Blocks.SAND.defaultBlockState();
                        } else if (h < floor - 10 && h > bottom + 4 && n < 0.18F) {
                            s = Blocks.DRIPSTONE_BLOCK.defaultBlockState();
                        } else {
                            s = limestone;
                        }
                    } else if (h > ground) {
                        // the bank, built up from the sea bed
                        s = h >= top - 1 ? Blocks.SAND.defaultBlockState() : n < 0.7F ? limestone : Blocks.SANDSTONE.defaultBlockState();
                    } else {
                        continue;
                    }
                    Terrain.set(level, pos, x, h, z, s);
                }
                decorate(level, pos, x, z, d, theta, top, shaft);
            }
        }
    }

    /** Coral and sea grass on the bank, coral fans on the upper walls, and stalactites under the overhangs down in the dark. */
    private void decorate(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, float d, float theta, int top, boolean shaft) {
        float m = Terrain.noise(seed, x, 5, z);
        boolean open = shaft && d < hole(x, top, z, theta);
        if (!open && top < sea - 2) {
            pos.set(x, top, z);
            BlockState below = level.getBlockState(pos);
            if (below.is(BlockTags.CORAL_BLOCKS) && m < 0.6F) {
                Terrain.set(level, pos, x, top + 1, z, CORALS[(int) (m * 113) % CORALS.length]);
            } else if (below.is(Blocks.SAND) && m < 0.3F) {
                Terrain.set(level, pos, x, top + 1, z, Blocks.SEAGRASS.defaultBlockState());
            } else if (below.is(Blocks.SAND) && m < 0.36F && top + 2 <= sea - 1) {
                Terrain.set(level, pos, x, top + 1, z, Blocks.TALL_SEAGRASS.defaultBlockState());
                Terrain.set(level, pos, x, top + 2, z, Blocks.TALL_SEAGRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
            } else if (m > 0.97F) {
                Terrain.set(level, pos, x, top + 1, z, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + (int) (m * 400) % 4));
            }
        }
        if (!shaft) return;
        int bottom = floor - depth;
        for (int h = top; h > bottom + 1; h--) {
            pos.set(x, h, z);
            if (!level.getBlockState(pos).is(Blocks.WATER)) continue;
            float n = Terrain.noise(seed + 3, x, h, z);
            BlockState above = level.getBlockState(pos.set(x, h + 1, z));
            if (h < floor - 4 && n > 0.8F && (above.is(ModBlocks.LIMESTONE.get()) || above.is(Blocks.DRIPSTONE_BLOCK))) {
                // stalactites from the old dry cave, drowned
                int length = 1 + (int) (Terrain.noise(seed + 4, x, h, z) * 3.99F);
                for (int k = 0; k < length; k++) {
                    pos.set(x, h - k, z);
                    if (!level.getBlockState(pos).is(Blocks.WATER) || !level.getBlockState(pos.below()).is(Blocks.WATER)) {
                        length = k;
                        break;
                    }
                }
                for (int k = 0; k < length; k++) {
                    int fromTip = length - 1 - k;
                    DripstoneThickness t = fromTip == 0 ? DripstoneThickness.TIP : fromTip == 1 ? DripstoneThickness.FRUSTUM
                            : k == 0 ? DripstoneThickness.BASE : DripstoneThickness.MIDDLE;
                    Terrain.set(level, pos, x, h - k, z, Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN)
                            .setValue(PointedDripstoneBlock.THICKNESS, t).setValue(PointedDripstoneBlock.WATERLOGGED, true));
                }
                continue;
            }
            // coral fans growing out from the walls near the top, where the light still reaches
            if (h < top - 7 || h > top - 2 || n > 0.14F) continue;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                int nx = x + dir.getStepX(), nz = z + dir.getStepZ();
                float ndx = nx + 0.5F - cx, ndz = nz + 0.5F - cz;
                if (Mth.sqrt(ndx * ndx + ndz * ndz) >= hole(nx, h, nz, (float) Mth.atan2(ndz, ndx))) {
                    Terrain.set(level, pos, x, h, z, WALL_FANS[(int) (n * 300) % WALL_FANS.length].setValue(BaseCoralWallFanBlock.FACING, dir.getOpposite()));
                    break;
                }
            }
        }
    }
}
