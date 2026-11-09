package com.reiko.weather.landform;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;

import javax.annotation.Nullable;
import java.util.function.IntBinaryOperator;

/**
 * What a landform needs to plan itself around a column: randomness, the height of the ground (under any
 * water) anywhere nearby, and the sea level.
 *
 * @param ground top solid block's y at a column (the sea bed under water)
 */
public record PlanContext(RandomSource random, int x, int z, IntBinaryOperator ground, int seaLevel, LevelHeightAccessor heights) {
    public int groundAt(int px, int pz) {
        return ground.applyAsInt(px, pz);
    }

    /** Height range of the ground over a ring of sample points (max minus min), to check how flat it is. */
    public int relief(int radius, int samples) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int i = 0; i < samples; i++) {
            double a = i * Math.PI * 2.0 / samples;
            int h = groundAt(x + (int) Math.round(Math.cos(a) * radius), z + (int) Math.round(Math.sin(a) * radius));
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        int c = groundAt(x, z);
        return Math.max(max, c) - Math.min(min, c);
    }

    /**
     * Which way the sea is from here: the direction (radians) where the ground {@code distance} blocks out is lowest,
     * or NaN unless it's under the sea that way and dry land the other way.
     */
    public float seaward(int distance, int landAbove) {
        float best = Float.NaN;
        int lowest = Integer.MAX_VALUE;
        for (int i = 0; i < 12; i++) {
            float a = i * (float) Math.PI * 2.0F / 12.0F;
            int h = groundAt(x + (int) Math.round(Math.cos(a) * distance), z + (int) Math.round(Math.sin(a) * distance));
            if (h < lowest) {
                lowest = h;
                best = a;
            }
        }
        if (Float.isNaN(best) || lowest > seaLevel - 4) return Float.NaN;
        int behind = groundAt(x - (int) Math.round(Math.cos(best) * distance), z - (int) Math.round(Math.sin(best) * distance));
        return behind >= seaLevel + landAbove ? best : Float.NaN;
    }

    /** The steepest way downhill from here, {angle, drop}: how far the ground falls {@code distance} blocks out that way. */
    public float[] downhill(int distance) {
        int here = groundAt(x, z), drop = 0;
        float angle = 0.0F;
        for (int i = 0; i < 12; i++) {
            float a = i * (float) Math.PI * 2.0F / 12.0F;
            int h = groundAt(x + (int) Math.round(Math.cos(a) * distance), z + (int) Math.round(Math.sin(a) * distance));
            if (here - h > drop) {
                drop = here - h;
                angle = a;
            }
        }
        return new float[]{angle, drop};
    }

    /** The same context, moved to another column. */
    public PlanContext at(int px, int pz) {
        return new PlanContext(random, px, pz, ground, seaLevel, heights);
    }

    /**
     * Tries a planner at this column and then at others round it ({@code step} apart, out to {@code reach}),
     * nearest first, for landforms that need a particular spot (a coastline, say) a chunk's middle seldom hits.
     */
    @Nullable
    public Landform search(int step, int reach, java.util.function.Function<PlanContext, Landform> planner) {
        for (int r = 0; r <= reach; r += step) {
            for (int dx = -r; dx <= r; dx += step) {
                for (int dz = -r; dz <= r; dz += step) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    Landform landform = planner.apply(at(x + dx, z + dz));
                    if (landform != null) return landform;
                }
            }
        }
        return null;
    }

    public boolean fits(int y, int below, int above) {
        return y - below > heights.getMinBuildHeight() + 2 && y + above < heights.getMaxBuildHeight() - 2;
    }
}
