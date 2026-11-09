package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Penitentes: a forest of thin, sharp blades of hardened snow and ice on a high snowfield, packed close together
 * like a crowd of kneeling penitents. High up, where the air is cold and very dry, strong sunshine turns the snow
 * straight to vapour rather than melting it, and any hollow deepens faster than the ridges round it. The blades
 * line up in rows running east and west, leaning toward the noon sun.
 */
public record Penitentes(int cx, int cz, int y, int radius, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        int radius = 16 + c.random().nextInt(13);
        if (ground < c.seaLevel() + 35 || !c.fits(ground, 18, 24) || c.relief(radius, 8) > 22) {
            return null;
        }
        return new Penitentes(c.x(), c.z(), ground, radius, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Penitentes(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.PENITENTES;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * 1.2F) + 2;
        return new BoundingBox(cx - r, y - 18, cz - r, cx + r, y + 24, cz + r);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int phase = Math.round(Terrain.hash(seed, 1));
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float dx = x + 0.5F - cx, dz = z + 0.5F - cz;
                float d = Mth.sqrt(dx * dx + dz * dz) / (radius * (0.85F + 0.3F * Terrain.field(seed, x, z, 10.0)));
                if (d > 1.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 2 || ground > b.maxY() - 8) continue;
                // the snowfield they stand on
                Terrain.clearAbove(level, pos, x, ground, z, 8);
                Terrain.set(level, pos, x, ground, z, Blocks.SNOW_BLOCK.defaultBlockState());
                Terrain.set(level, pos, x, ground - 1, z, Terrain.noise(seed, x, ground - 1, z) < 0.5F ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.SNOW_BLOCK.defaultBlockState());
                // the blades, in rows running east-west, shortest at the edges of the field
                if (Math.floorMod(z + phase, 2) != 0) continue;
                float edge = Terrain.smooth((1.0F - d) / 0.4F);
                if (edge < 0.15F || Terrain.noise(seed, x, 1, z) < 0.12F) continue;
                float tall = 0.55F * Terrain.field(seed + 1, x, z, 5.0) + 0.45F * Terrain.noise(seed, x, 2, z);
                int height = Math.max(1, Math.round((2.0F + 3.0F * tall) * edge));
                for (int k = 1; k <= height; k++) {
                    float n = Terrain.noise(seed, x, ground + k, z);
                    // hard, glassy ice at the core, firn snow on the faces and at the tips
                    BlockState s = k == height ? Blocks.SNOW_BLOCK.defaultBlockState() : n < 0.55F ? Blocks.PACKED_ICE.defaultBlockState()
                            : n < 0.65F ? Blocks.ICE.defaultBlockState() : Blocks.SNOW_BLOCK.defaultBlockState();
                    Terrain.set(level, pos, x, ground + k, z, s);
                }
            }
        }
    }
}
