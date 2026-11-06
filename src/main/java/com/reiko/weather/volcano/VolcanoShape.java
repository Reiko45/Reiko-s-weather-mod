package com.reiko.weather.volcano;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * The geometry of one volcano: a concave cone of rock with a bowl-shaped crater at the top and a lava
 * lake in the bottom of the crater. Pure maths, so world generation, the eruption logic and the tests
 * all agree on where everything is.
 *
 * @param baseY  height of the land around the volcano (the foot of the cone)
 * @param height how far the crater rim rises above the base
 * @param radius horizontal radius of the cone's foot
 * @param seed   varies the outline, ridges and old lava flows
 */
public record VolcanoShape(int cx, int cz, int baseY, int height, int radius, int craterRadius, int craterDepth, long seed) {
    /** Not part of the volcano. */
    public static final int OUTSIDE = Integer.MIN_VALUE;

    public int peakY() {
        return baseY + height;
    }

    public int craterFloorY() {
        return peakY() - craterDepth;
    }

    /** Top of the lava lake in the crater. */
    public int lavaY() {
        return craterFloorY() + 3;
    }

    /** The middle of the lava lake. */
    public BlockPos crater() {
        return new BlockPos(cx, lavaY(), cz);
    }

    private float phase(int k) {
        return (float) ((seed >>> (k * 8)) & 255) / 255.0F * Mth.TWO_PI;
    }

    /** The crater's edge wobbles a little around the circle. */
    public float rimRadius(float angle) {
        return craterRadius + 1.5F * Mth.sin(3.0F * angle + phase(3));
    }

    /** Is (x, z) inside the crater? */
    public boolean inCrater(int x, int z) {
        float dx = x - cx, dz = z - cz;
        return Mth.sqrt(dx * dx + dz * dz) < rimRadius((float) Math.atan2(dz, dx));
    }

    /**
     * The height of the volcano's surface at a column: the top of the cone outside the crater, the crater
     * floor inside it, or {@link #OUTSIDE}.
     */
    public int surfaceY(int x, int z) {
        float dx = x - cx, dz = z - cz;
        float r = Mth.sqrt(dx * dx + dz * dz);
        if (r > radius) {
            return OUTSIDE;
        }
        float angle = (float) Math.atan2(dz, dx);
        float rim = rimRadius(angle);
        if (r < rim) {
            // a bowl: deepest in the middle, rising toward the crater wall
            float t = r / rim;
            return Math.round(craterFloorY() + craterDepth * 0.8F * t * t);
        }
        float t = Mth.clamp((radius - r) / (radius - rim), 0.0F, 1.0F);
        // concave slopes, steep near the top; ridges and gullies run down the flanks
        float cone = height * (float) Math.pow(t, 1.5);
        float ridges = (2.2F * Mth.sin(5.0F * angle + phase(0)) + 1.4F * Mth.sin(11.0F * angle + phase(1))
                + 0.8F * Mth.sin(23.0F * angle + r * 0.15F + phase(2))) * 4.0F * t * (1.0F - t);
        return Math.round(baseY + cone + ridges);
    }

    /** How high up the cone a column is: 0 at the foot, 1 at the rim. */
    public float heightFraction(int x, int z) {
        int y = surfaceY(x, z);
        return y == OUTSIDE ? 0.0F : Mth.clamp((y - baseY) / (float) height, 0.0F, 1.0F);
    }

    /** Old, solidified lava flows: a few dark streaks running down from the rim. */
    public boolean onOldLavaFlow(int x, int z) {
        float dx = x - cx, dz = z - cz;
        float r = Mth.sqrt(dx * dx + dz * dz);
        if (r < craterRadius || r > radius * 0.85F) {
            return false;
        }
        float angle = (float) Math.atan2(dz, dx);
        for (int k = 0; k < 3; k++) {
            float flow = phase(4 + k) + 0.25F * Mth.sin(r * 0.06F + k);
            float diff = Math.abs(Mth.wrapDegrees((angle - flow) * Mth.RAD_TO_DEG)) * Mth.DEG_TO_RAD;
            if (diff * r < 1.5F + r * 0.03F) {
                return true;
            }
        }
        return false;
    }

    /** Distance from the volcano's centre line, horizontally. */
    public double horizontalDistance(double x, double z) {
        double dx = x - cx, dz = z - cz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Volcanoes are keyed by the chunk their centre is in (the structure's start chunk). */
    public long key() {
        return net.minecraft.world.level.ChunkPos.asLong(cx >> 4, cz >> 4);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("cx", cx);
        tag.putInt("cz", cz);
        tag.putInt("base", baseY);
        tag.putInt("height", height);
        tag.putInt("radius", radius);
        tag.putInt("crater", craterRadius);
        tag.putInt("depth", craterDepth);
        tag.putLong("seed", seed);
        return tag;
    }

    public static VolcanoShape load(CompoundTag tag) {
        return new VolcanoShape(tag.getInt("cx"), tag.getInt("cz"), tag.getInt("base"), tag.getInt("height"), tag.getInt("radius"),
                tag.getInt("crater"), tag.getInt("depth"), tag.getLong("seed"));
    }
}
