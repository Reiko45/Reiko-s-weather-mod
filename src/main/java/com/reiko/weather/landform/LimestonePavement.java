package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A limestone pavement: a sheet of bare grey rock scraped clean by the ice and then dissolved along its joints
 * by thousands of years of rain, into flat slabs (clints) split by deep, narrow cracks (grikes). Out of the wind
 * and the sheep, ferns and moss grow down in the cracks.
 */
public record LimestonePavement(int cx, int cz, int y, int radius, float joints, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        int radius = 16 + c.random().nextInt(12);
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 3 || c.relief(radius, 12) > 7 || !c.fits(ground, 8, 8)) {
            return null;
        }
        return new LimestonePavement(c.x(), c.z(), ground, radius, c.random().nextFloat() * Mth.HALF_PI, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new LimestonePavement(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getFloat("joints"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.putFloat("joints", joints);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.LIMESTONE_PAVEMENT;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * 1.3F) + 2;
        return new BoundingBox(cx - r, y - 10, cz - r, cx + r, y + 14, cz + r);
    }

    /** Whether a point lies on a joint: two sets of cracks, roughly square to each other, a few blocks apart. */
    private boolean grike(int x, int z) {
        float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, joints);
        // the main joints run long and straight; the cross joints are shorter and staggered
        float u = l[0] + 0.8F * Mth.sin(l[1] * 0.11F + seed % 7);
        float v = l[1] + 0.8F * Mth.sin(l[0] * 0.13F);
        int row = Mth.floor(v / 4.0F);
        float along = Math.floorMod((int) Math.floor(u + row * 2.3F), 5);
        boolean main = Math.floorMod(Mth.floor(v), 4) == 0;
        boolean cross = along == 0 && Terrain.noise(seed, row, 1, Mth.floor((u + row * 2.3F) / 5.0F)) < 0.7F;
        return main || cross;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState limestone = ModBlocks.LIMESTONE.get().defaultBlockState();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float dx = x + 0.5F - cx, dz = z + 0.5F - cz;
                float d = Mth.sqrt(dx * dx + dz * dz) / (radius * (0.75F + 0.5F * Terrain.field(seed, x, z, 11.0)));
                if (d > 1.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                // flattened toward the middle, where the ice scraped it bare
                int top = Math.round(Mth.lerp(Terrain.smooth((1.0F - d) * 2.5F), ground, y));
                if (d > 0.85F && Terrain.noise(seed, x, 3, z) < (d - 0.85F) * 6.0F) continue; // ragged edges, turf creeping over
                Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, 12);
                for (int h = top + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                for (int h = Math.min(top, ground) - 3; h <= top; h++) Terrain.set(level, pos, x, h, z, limestone);
                float n = Terrain.noise(seed, x, top, z);
                if (grike(x, z)) {
                    int depth = 2 + Math.round(Terrain.field(seed + 1, x, z, 4.0) * 2.0F);
                    for (int h = top - depth + 1; h <= top; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    int bottom = top - depth;
                    // the cracks hold a little soil, and damp shade
                    Terrain.set(level, pos, x, bottom, z, n < 0.6F ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState());
                    if (n < 0.45F) {
                        Terrain.set(level, pos, x, bottom + 1, z, Blocks.FERN.defaultBlockState());
                    } else if (n < 0.6F) {
                        Terrain.set(level, pos, x, bottom + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                    } else if (n > 0.97F && depth >= 3) {
                        // the odd stunted hawthorn (an azalea here) poking out of a crack
                        Terrain.set(level, pos, x, bottom + 1, z, Blocks.AZALEA.defaultBlockState());
                    }
                } else if (n > 0.93F) {
                    // a rain-dissolved runnel or a patch of lichen on the clint
                    Terrain.set(level, pos, x, top, z, n > 0.97F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState());
                } else if (n < 0.03F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                }
            }
        }
    }
}
