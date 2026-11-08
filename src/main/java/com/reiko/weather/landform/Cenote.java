package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A cenote: a sinkhole where the roof of a limestone cave fell in long ago, leaving a round, sheer-sided shaft
 * open to the sky with deep, clear water at the bottom. The walls are undercut, hung with vines and roots, and
 * the overhangs drip with stalactites.
 */
public record Cenote(int cx, int cz, int y, int radius, int depth, int water, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        int radius = 6 + c.random().nextInt(5);
        int depth = 18 + c.random().nextInt(9);
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 2 || c.relief(radius + 3, 8) > 8 || !c.fits(ground, depth + 4, 10)) {
            return null;
        }
        int rim = ground, low = ground;
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F;
            rim = Math.min(rim, c.groundAt(c.x() + Math.round(Mth.cos(a) * radius), c.z() + Math.round(Mth.sin(a) * radius)));
            low = Math.min(low, c.groundAt(c.x() + Math.round(Mth.cos(a) * (radius + 14)), c.z() + Math.round(Mth.sin(a) * (radius + 14))));
        }
        // the water is the water table, so it never stands above the lowest land round about
        int water = Math.max(6 + c.random().nextInt(4), rim - low + 3);
        if (water > depth - 4) {
            return null;
        }
        return new Cenote(c.x(), c.z(), rim, radius, depth, water, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Cenote(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getInt("depth"), tag.getInt("water"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.putInt("depth", depth);
        tag.putInt("water", water);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.CENOTE;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * 1.45F) + 4;
        return new BoundingBox(cx - r, y - depth - 3, cz - r, cx + r, y + 12, cz + r);
    }

    /** The radius of the shaft at a height: the lip, then the walls belling out below (the old cave). */
    private float radiusAt(int h, float wobble) {
        float down = y - h;
        float t = Mth.clamp((down - 3.0F) / (depth * 0.6F), 0.0F, 1.0F);
        return radius * (1.0F + 0.38F * Terrain.smooth(t)) * wobble;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int bottom = y - depth, surface = y - water;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
                float d = (float) Math.sqrt(dx * dx + dz * dz);
                float wobble = 1.0F + 0.12F * (Terrain.field(seed, Math.atan2(dz, dx) * 6.0 + 50.0, 0.0, 1.0) - 0.5F) * 2.0F;
                if (d > radius * 1.45F * wobble + 3.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (d < radius * wobble * 0.8F) {
                    Terrain.clearAbove(level, pos, x, ground, z, 30); // nothing growing across the mouth of the shaft
                }
                for (int h = bottom - 1; h <= Math.max(ground, y); h++) {
                    float r = radiusAt(h, wobble);
                    if (h == bottom && d < r) {
                        Terrain.set(level, pos, x, h, z, Terrain.noise(seed, x, 1, z) < 0.6F ? Blocks.SAND.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
                    } else if (d < r && h > bottom) {
                        Terrain.set(level, pos, x, h, z, h <= surface ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    } else if (d < r + 2.0F && h <= ground && h < y - 1) {
                        // pale limestone walls, stained brown with dripstone in places
                        float n = Terrain.noise(seed, x, h, z);
                        Terrain.set(level, pos, x, h, z, n < 0.65F ? Blocks.CALCITE.defaultBlockState()
                                : n < 0.85F ? Blocks.DRIPSTONE_BLOCK.defaultBlockState() : Blocks.SMOOTH_SANDSTONE.defaultBlockState());
                    }
                }
                // a lip of exposed rock round the rim
                if (d >= radius * wobble && d < radius * wobble + 1.2F) {
                    pos.set(x, ground, z);
                    if (ground <= y + 1 && Terrain.noise(seed, x, 2, z) < 0.5F) Terrain.set(level, pos, x, ground, z, Blocks.CALCITE.defaultBlockState());
                }
            }
        }
        decorate(level, area, pos, bottom, surface);
    }

    /** Vines and roots hanging down the walls, and stalactites under the overhangs. */
    private void decorate(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos, int bottom, int surface) {
        BoundingBox b = bounds();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                for (int h = y - 1; h > surface; h--) {
                    pos.set(x, h, z);
                    if (!level.getBlockState(pos).isAir()) continue;
                    float n = Terrain.noise(seed, x, h, z);
                    BlockState above = level.getBlockState(pos.above());
                    // stalactites hanging from the overhanging roof
                    if (above.is(Blocks.CALCITE) || above.is(Blocks.DRIPSTONE_BLOCK)) {
                        if (n > 0.86F) {
                            Terrain.set(level, pos, x, h, z, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                                    .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN).setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP));
                        } else if (n < 0.08F) {
                            Terrain.set(level, pos, x, h, z, Blocks.HANGING_ROOTS.defaultBlockState());
                        }
                        continue;
                    }
                    // vines trailing down from the rim
                    if (h < y - 6 - (int) (n * 6)) continue;
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        BlockPos wall = pos.relative(d);
                        BlockState ws = level.getBlockState(wall);
                        if ((ws.is(Blocks.CALCITE) || ws.is(Blocks.DRIPSTONE_BLOCK) || ws.is(Blocks.SMOOTH_SANDSTONE)) && n < 0.45F) {
                            Terrain.set(level, pos, x, h, z, Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(d), true));
                            break;
                        }
                    }
                }
            }
        }
    }
}
