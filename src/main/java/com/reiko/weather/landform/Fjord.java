package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A fjord: a long, narrow arm of the sea reaching deep into the mountains, carved out by a glacier that has
 * long since melted away. Sheer rock walls drop straight into deep water, deepest partway in, with a shallow
 * sill across the mouth where the glacier dropped its rubble.
 *
 * @param mx     where the fjord meets the open sea
 * @param angle  the way it runs inland
 * @param length how far inland it reaches
 */
public record Fjord(int mx, int mz, float angle, int length, int width, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        int here = c.groundAt(c.x(), c.z());
        if (here > c.seaLevel() + 2 || here < c.seaLevel() - 30) {
            return null; // it starts at the sea
        }
        // which way are the mountains?
        float bestAngle = 0.0F;
        int best = Integer.MIN_VALUE;
        for (int i = 0; i < 12; i++) {
            float a = i * Mth.TWO_PI / 12.0F;
            int sum = 0;
            for (int d = 40; d <= 100; d += 30) {
                sum += c.groundAt(c.x() + Math.round(Mth.cos(a) * d), c.z() + Math.round(Mth.sin(a) * d));
            }
            if (sum > best) {
                best = sum;
                bestAngle = a;
            }
        }
        if (best / 3 < c.seaLevel() + 16) {
            return null;
        }
        // the coast: walk inland until we're out of the sea
        int mouth = -1;
        for (int d = 0; d <= 24; d += 3) {
            if (c.groundAt(c.x() + Math.round(Mth.cos(bestAngle) * d), c.z() + Math.round(Mth.sin(bestAngle) * d)) >= c.seaLevel()) {
                mouth = d;
                break;
            }
        }
        if (mouth < 0) {
            return null;
        }
        int mx = c.x() + Math.round(Mth.cos(bestAngle) * mouth), mz = c.z() + Math.round(Mth.sin(bestAngle) * mouth);
        // keep the whole thing within reach of where it started
        int reach = 118 - mouth;
        int length = Math.min(reach, 80 + c.random().nextInt(30));
        return new Fjord(mx, mz, bestAngle, length, 7 + c.random().nextInt(4), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Fjord(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("width"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", mx);
        tag.putInt("z", mz);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putInt("width", width);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.FJORD;
    }

    @Override
    public BoundingBox bounds() {
        int r = width + 16;
        int ex = mx + Math.round(Mth.cos(angle) * length), ez = mz + Math.round(Mth.sin(angle) * length);
        int sx = mx - Math.round(Mth.cos(angle) * 14), sz = mz - Math.round(Mth.sin(angle) * 14);
        return new BoundingBox(Math.min(sx, ex) - r, -64, Math.min(sz, ez) - r, Math.max(sx, ex) + r, 320, Math.max(sz, ez) + r);
    }

    private float wiggle(float u) {
        return 7.0F * Mth.sin(u * 0.035F + Terrain.hash(seed, 1) * 6.0F) * Mth.clamp(u / 30.0F, 0.0F, 1.0F);
    }

    /** Half the width of the water at a point along it: narrowing toward the head. */
    private float halfWidth(float u) {
        float t = Mth.clamp(u / length, 0.0F, 1.0F);
        return width * (1.0F - 0.45F * t) * (1.0F + 0.12F * Mth.sin(u * 0.13F + Terrain.hash(seed, 2) * 6.0F));
    }

    /** How deep the water is: a sill at the mouth, a deep basin, shallowing toward the head. */
    private int depth(float u) {
        float t = Mth.clamp(u / length, 0.0F, 1.0F);
        float d = u < 0.0F ? 6.0F : t < 0.3F ? Mth.lerp(t / 0.3F, 7.0F, 24.0F) : Mth.lerp((t - 0.3F) / 0.7F, 24.0F, 6.0F);
        return Math.round(d);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int sea = level.getSeaLevel();
        double c = Mth.cos(angle), s = Mth.sin(angle);
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                double dx = x + 0.5 - mx, dz = z + 0.5 - mz;
                float u = (float) (dx * c + dz * s);
                if (u < -14.0F || u > length) continue;
                float v = Math.abs((float) (-dx * s + dz * c) - wiggle(u));
                float hw = halfWidth(u);
                // the head of the fjord rounds off
                float head = Mth.clamp((length - u) / 10.0F, 0.0F, 1.0F);
                hw *= Mth.sqrt(head);
                float wall = 3.5F;
                if (v > hw + wall) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (v <= hw) {
                    int floor = sea - Math.round(depth(u) * Mth.sqrt(Math.max(0.0F, 1.0F - (v / Math.max(1.0F, hw)) * (v / Math.max(1.0F, hw)))) - 1);
                    Terrain.clearAbove(level, pos, x, ground, z, 40);
                    for (int h = floor + 1; h <= Math.max(ground, sea); h++) {
                        Terrain.set(level, pos, x, h, z, h <= sea ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    }
                    if (floor < ground || floor < sea) {
                        Terrain.set(level, pos, x, floor, z, Terrain.noise(seed, x, 0, z) < 0.6F ? Blocks.GRAVEL.defaultBlockState() : Blocks.STONE.defaultBlockState());
                    }
                } else if (ground > sea) {
                    // sheer walls: the rock rises almost straight up from the water
                    float t = (v - hw) / wall;
                    int cap = sea + Math.round(t * t * 40.0F);
                    if (cap >= ground) continue;
                    Terrain.clearAbove(level, pos, x, ground, z, 30);
                    for (int h = cap + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    Terrain.set(level, pos, x, cap, z, Terrain.noise(seed, x, 1, z) < 0.25F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState());
                }
            }
        }
    }
}
