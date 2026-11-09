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
 * A natural arch: a fin of sandstone, left standing as the rock around it weathered away, with a great window
 * worn right through it by frost and wind-blown sand.
 */
public record RockArch(int cx, int cz, int y, float angle, int span, int height, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 2 || c.relief(16, 8) > 8 || !c.fits(ground, 10, 40)) {
            return null;
        }
        return new RockArch(c.x(), c.z(), ground, c.random().nextFloat() * Mth.TWO_PI, 12 + c.random().nextInt(11), 11 + c.random().nextInt(9),
                c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new RockArch(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getFloat("angle"), tag.getInt("span"), tag.getInt("h"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putFloat("angle", angle);
        tag.putInt("span", span);
        tag.putInt("h", height);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.ROCK_ARCH;
    }

    @Override
    public BoundingBox bounds() {
        int r = span / 2 + 10;
        return new BoundingBox(cx - r, y - 10, cz - r, cx + r, y + height + 6, cz + r);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean red = Terrain.hash(seed, 1) < 0.5F;
        BlockState[] bands = red
                ? new BlockState[]{Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState()}
                : new BlockState[]{Blocks.SANDSTONE.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState()};
        float half = span / 2.0F + 4.0F, holeW = span / 2.0F, holeH = height * 0.62F;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float v = l[1], u = l[0];
                if (Math.abs(v) > half) continue;
                // the fin is thicker at its feet and thinner over the top of the arch
                float end = Mth.sqrt(Math.max(0.0F, 1.0F - (v / half) * (v / half)));
                float thick = (2.0F + 1.6F * (1.0F - end)) * (0.85F + 0.3F * Terrain.noise(seed, x, 0, z));
                if (Math.abs(u) > thick) continue;
                int ground = Terrain.ground(level, pos, x, z);
                int top = y + Math.round(height * (0.7F + 0.3F * end));
                Terrain.clearAbove(level, pos, x, ground, z, top - ground + 2);
                for (int h = ground; h <= top; h++) {
                    float hole = (v / holeW) * (v / holeW) + ((h - y + 1.0F) / holeH) * ((h - y + 1.0F) / holeH);
                    if (hole < 1.0F && h > y - 2) continue;
                    Terrain.set(level, pos, x, h, z, bands[Math.floorMod(h / 2 + (int) (Terrain.hash(seed, 2) * 3), bands.length)]);
                }
            }
        }
    }
}
