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
 * A maar: a broad, round crater blasted into flat ground when rising magma met groundwater and flashed it to
 * steam. It's sunk below the land round it rather than built up, ringed by a low bank of tuff (the shattered
 * rock and ash the blasts threw out), and filled by a deep, still lake.
 */
public record Maar(int cx, int cz, int y, int radius, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        int radius = 18 + c.random().nextInt(11);
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 4 || c.relief(radius + 4, 12) > 10 || !c.fits(ground, 20, 12)) {
            return null;
        }
        int low = ground;
        for (int i = 0; i < 12; i++) {
            float a = i * Mth.TWO_PI / 12.0F;
            low = Math.min(low, c.groundAt(c.x() + Math.round(Mth.cos(a) * radius), c.z() + Math.round(Mth.sin(a) * radius)));
        }
        // the lake lies well below the land round about
        return new Maar(c.x(), c.z(), low - 4, radius, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Maar(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getLong("seed"));
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
        return LandformKind.MAAR;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * 1.6F) + 4;
        return new BoundingBox(cx - r, y - 16, cz - r, cx + r, y + 20, cz + r);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int ring = 3 + Math.round(Terrain.hash(seed, 1) * 3.0F);
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float dx = x + 0.5F - cx, dz = z + 0.5F - cz;
                float d = Mth.sqrt(dx * dx + dz * dz) / (radius * (0.95F + 0.1F * Terrain.field(seed, x, z, 10.0)));
                if (d > 1.6F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                float n = Terrain.noise(seed, x, y, z);
                // the tuff ring's crest, a little outside the crater's lip
                int bank = Math.round(ring * Terrain.smooth(1.0F - Math.abs(d - 1.05F) / 0.55F));
                int crest = Math.max(ground, y + 3) + bank;
                if (d < 0.82F) {
                    // the lake: deep, with a flat floor of fine silt
                    int depth = Math.round(4.0F + 8.0F * Terrain.smooth((0.82F - d) / 0.4F));
                    Terrain.clearAbove(level, pos, x, Math.max(ground, y), z, 12);
                    for (int h = y + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    for (int h = Math.min(ground + 1, y - depth - 1); h <= y - depth; h++) {
                        Terrain.set(level, pos, x, h, z, n < 0.6F ? Blocks.CLAY.defaultBlockState() : Blocks.TUFF.defaultBlockState());
                    }
                    for (int h = y - depth + 1; h <= y; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                    if (d > 0.7F && n > 0.9F) Terrain.set(level, pos, x, y + 1, z, Blocks.LILY_PAD.defaultBlockState());
                    continue;
                }
                int top;
                if (d < 1.05F) {
                    // the steep inner wall, from just above the water up to the crest
                    float t = (d - 0.82F) / 0.23F;
                    top = Math.round(Mth.lerp(t * t * (3.0F - 2.0F * t), y + 1, crest));
                } else {
                    top = ground + bank; // the gentle outer slope of the ring, fading into the land
                }
                if (top == ground) continue;
                Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, 12);
                for (int h = top + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                boolean steep = d < 1.0F && d > 0.86F;
                for (int h = Math.min(ground, top) - 1; h <= top; h++) {
                    BlockState s = Terrain.noise(seed, x, h, z) < 0.25F ? Blocks.ANDESITE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
                    if (h == top && !steep) s = Blocks.GRASS_BLOCK.defaultBlockState();
                    else if (h == top - 1 && !steep) s = Blocks.DIRT.defaultBlockState();
                    Terrain.set(level, pos, x, h, z, s);
                }
                if (!steep && n > 0.65F) Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
            }
        }
    }
}
