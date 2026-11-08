package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A peat bog: a wet, level, spongy expanse of sphagnum moss and cotton grass over metres of peat (the half-rotted
 * remains of everything that has grown here for thousands of years), dotted with dark pools and the odd stunted,
 * dead spruce. Cut the peat and dry it, and it burns slowly in a furnace. Lightning can set the bog smouldering.
 */
public record PeatBog(int cx, int cz, int y, int rx, int rz, float angle, long seed) implements Landform {
    private static final float EDGE = 1.3F;

    @Nullable
    static Landform plan(PlanContext c) {
        int rx = 20 + c.random().nextInt(15), rz = 14 + c.random().nextInt(11);
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() || c.relief(rz, 8) > 7 || !c.fits(ground, 10, 20)) {
            return null;
        }
        int sum = ground;
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F;
            sum += c.groundAt(c.x() + Math.round(Mth.cos(a) * rz * 0.7F), c.z() + Math.round(Mth.sin(a) * rz * 0.7F));
        }
        return new PeatBog(c.x(), c.z(), Math.round(sum / 9.0F), rx, rz, c.random().nextFloat() * Mth.TWO_PI, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new PeatBog(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("rx"), tag.getInt("rz"), tag.getFloat("angle"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("rx", rx);
        tag.putInt("rz", rz);
        tag.putFloat("angle", angle);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.PEAT_BOG;
    }

    @Override
    public BoundingBox bounds() {
        int r = (int) Math.ceil(Math.max(rx, rz) * EDGE) + 2;
        return new BoundingBox(cx - r, y - 10, cz - r, cx + r, y + 20, cz + r);
    }

    private float ellipse(double x, double z) {
        double dx = x - cx, dz = z - cz, c = Math.cos(angle), s = Math.sin(angle);
        double u = dx * c + dz * s, v = -dx * s + dz * c;
        float wobble = 1.0F + 0.15F * (Terrain.field(seed, x, z, 11.0) - 0.5F) * 2.0F;
        return (float) (u * u / (rx * rx) + v * v / (rz * rz)) / wobble;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState peat = ModBlocks.PEAT.get().defaultBlockState();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float e = ellipse(x + 0.5, z + 0.5);
                if (e > EDGE) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (e > 1.0F) {
                    // the margin: the land eases down to the bog's level
                    float t = Terrain.smooth((e - 1.0F) / (EDGE - 1.0F));
                    int target = Math.round(Mth.lerp(t, y, ground));
                    if (target < ground) {
                        Terrain.clearAbove(level, pos, x, ground, z, 12);
                        for (int h = target + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                        Terrain.set(level, pos, x, target, z, Blocks.GRASS_BLOCK.defaultBlockState());
                    } else if (target > ground) {
                        for (int h = ground + 1; h <= target; h++) Terrain.set(level, pos, x, h, z, h == target ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState());
                    }
                    continue;
                }
                Terrain.clearAbove(level, pos, x, Math.max(ground, y), z, 20);
                for (int h = y + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                // deep peat, thickest in the middle, over clay
                int thickness = 3 + Math.round((1.0F - e) * 3.0F);
                for (int h = y - thickness - 1; h < y; h++) {
                    Terrain.set(level, pos, x, h, z, h == y - thickness - 1 ? Blocks.CLAY.defaultBlockState() : peat);
                }
                float pool = Terrain.field(seed + 1, x, z, 4.0);
                float n = Terrain.noise(seed, x, 0, z);
                if (pool > 0.74F && e < 0.85F) {
                    // a dark bog pool, peat-stained, over soft mud
                    Terrain.set(level, pos, x, y - 1, z, Blocks.MUD.defaultBlockState());
                    Terrain.set(level, pos, x, y, z, Blocks.WATER.defaultBlockState());
                    if (n > 0.9F) Terrain.set(level, pos, x, y + 1, z, Blocks.LILY_PAD.defaultBlockState());
                    continue;
                }
                float moss = Terrain.field(seed + 2, x, z, 3.0);
                Terrain.set(level, pos, x, y, z, moss > 0.42F ? Blocks.MOSS_BLOCK.defaultBlockState()
                        : moss > 0.3F ? Blocks.MUD.defaultBlockState() : n < 0.5F ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.PODZOL.defaultBlockState());
                // cotton grass, cranberries, moss and the odd dead tree
                if (n > 0.993F && e < 0.8F) {
                    int h = 2 + (int) (Terrain.noise(seed, x, 1, z) * 4);
                    for (int k = 1; k <= h; k++) {
                        Terrain.set(level, pos, x, y + k, z, Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
                    }
                } else if (n > 0.9F) {
                    Terrain.set(level, pos, x, y + 1, z, Blocks.LILY_OF_THE_VALLEY.defaultBlockState()); // cotton grass
                } else if (n > 0.86F) {
                    Terrain.set(level, pos, x, y + 1, z, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 2));
                } else if (n > 0.6F) {
                    Terrain.set(level, pos, x, y + 1, z, moss > 0.42F ? Blocks.MOSS_CARPET.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
                }
            }
        }
    }
}
