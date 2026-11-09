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
 * A barrier island: a long, thin ridge of sand the waves have piled up just off a low coast, topped with dunes
 * and beach grass, with a calm, shallow lagoon behind it. The tide runs in and out through an inlet that cuts
 * the island in two.
 */
public record BarrierIsland(int cx, int cz, int sea, float angle, int length, long seed) implements Landform {
    /** How far offshore the island lies. */
    private static final float OFFSHORE = 26.0F;

    @Nullable
    static Landform plan(PlanContext c) {
        return c.search(12, 12, BarrierIsland::planAt);
    }

    @Nullable
    private static Landform planAt(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() - 2 || ground > c.seaLevel() + 4 || c.relief(20, 8) > 14) {
            return null;
        }
        float seaward = c.seaward(36, 1);
        if (Float.isNaN(seaward)) {
            return null;
        }
        // not off a cliff, and not over deep water
        int out = c.groundAt(c.x() + Math.round(Mth.cos(seaward) * OFFSHORE), c.z() + Math.round(Mth.sin(seaward) * OFFSHORE));
        if (out < c.seaLevel() - 16) {
            return null;
        }
        return new BarrierIsland(c.x(), c.z(), c.seaLevel(), seaward, 110 + c.random().nextInt(60), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new BarrierIsland(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getFloat("angle"), tag.getInt("length"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("sea", sea);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.BARRIER_ISLAND;
    }

    @Override
    public BoundingBox bounds() {
        int r = length / 2 + 40;
        return new BoundingBox(cx - r, sea - 20, cz - r, cx + r, sea + 12, cz + r);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float half = length / 2.0F;
        float inlet = (Terrain.hash(seed, 1) - 0.5F) * length * 0.5F;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float u = l[0], v = l[1];
                if (Math.abs(v) > half + 6.0F || u < 3.0F || u > OFFSHORE + 18.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                float centre = OFFSHORE + 3.0F * Mth.sin(v * 0.03F + Terrain.hash(seed, 2) * 6.0F);
                float width = (5.0F + 2.0F * Terrain.field(seed, v, 0.0, 30.0)) * Mth.sqrt(Math.max(0.0F, 1.0F - Mth.square(v / half)));
                // the inlet the tide runs through
                float gap = Math.abs(v - inlet);
                if (gap < 5.0F) width *= Terrain.smooth((gap - 3.0F) / 2.0F);
                float across = (u - centre) / Math.max(0.5F, width);
                float n = Terrain.noise(seed, x, sea, z);
                int top;
                BlockState surface = Blocks.SAND.defaultBlockState();
                if (Math.abs(across) < 1.0F && width > 1.0F) {
                    // the island: beach on the sea side, dunes along its back, a strip of marsh on the lagoon side
                    float dune = Terrain.field(seed + 1, x, z, 7.0);
                    float rise = (1.0F - across * across) * (1.5F + 3.0F * dune) * Math.min(1.0F, width / 4.0F);
                    top = sea - 1 + Math.round(rise);
                    // the older dunes at the back are grassed over
                    if (top > sea && across < 0.2F && dune > 0.55F) surface = Blocks.GRASS_BLOCK.defaultBlockState();
                    if (across < -0.7F) surface = n < 0.5F ? Blocks.MUD.defaultBlockState() : Blocks.SAND.defaultBlockState();
                } else if (u < centre && u > 3.0F) {
                    // the lagoon: shallow, sheltered, sandy and muddy-bottomed
                    float shore = Terrain.smooth((u - 3.0F) / 5.0F) * Mth.clamp((half + 6.0F - Math.abs(v)) / 6.0F, 0.0F, 1.0F);
                    top = Math.round(Mth.lerp(shore, ground, Math.min(ground, sea - 3 - Math.round(Terrain.field(seed + 2, x, z, 9.0) * 2.0F))));
                    surface = n < 0.4F ? Blocks.MUD.defaultBlockState() : Blocks.SAND.defaultBlockState();
                } else {
                    // the shoreface, shelving off into the sea
                    float out = Math.max(0.0F, across - 1.0F) * Math.max(1.0F, width);
                    float t = Terrain.smooth(out / 14.0F) * (1.0F - Mth.clamp((Math.abs(v) - half) / 6.0F, 0.0F, 1.0F)) + (Math.abs(v) > half ? 1.0F : 0.0F);
                    top = Math.round(Mth.lerp(Math.min(1.0F, t), sea - 2, ground));
                    if (gap < 5.0F && u > centre - 8.0F) top = Math.min(top, sea - 4); // scoured out through the inlet
                }
                if (top == ground) continue;
                Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, 10);
                for (int h = top + 1; h <= Math.max(ground, sea - 1); h++) {
                    Terrain.set(level, pos, x, h, z, h <= sea - 1 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
                for (int h = Math.min(ground, top) - 1; h <= top; h++) Terrain.set(level, pos, x, h, z, h < top ? Blocks.SAND.defaultBlockState() : surface);
                if (top >= sea) {
                    // grass holding the dunes together
                    if (n > 0.4F && surface.is(Blocks.GRASS_BLOCK)) Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                    else if (n < 0.04F) Terrain.set(level, pos, x, top + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                } else if (top < sea - 2 && n > 0.8F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SEAGRASS.defaultBlockState());
                }
            }
        }
    }
}
