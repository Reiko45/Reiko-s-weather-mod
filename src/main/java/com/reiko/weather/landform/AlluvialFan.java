package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * An alluvial fan: a broad, low cone of gravel and sand spread out over the plain where a steep valley opens out
 * at the foot of the mountains. Floods come racing down the valley loaded with rock, and as soon as they burst
 * out of its mouth they spread, slow and drop their load, coarsest first, so the fan is bouldery at its apex and
 * sandy at its edges. The water wanders over it in shallow, braided channels that split and rejoin, shifting
 * from one side of the fan to the other from flood to flood.
 *
 * @param ax     the apex, at the valley mouth
 * @param y      the level of the ground at the apex
 * @param angle  the way the fan spreads (straight out of the valley)
 */
public record AlluvialFan(int ax, int az, int y, float angle, int radius, long seed) implements Landform {
    private static final float SPREAD = 1.05F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 4 || !c.fits(ground, 20, 16)) {
            return null;
        }
        // a steep slope here...
        float[] down = c.downhill(14);
        if (down[1] < 6) {
            return null;
        }
        float a = down[0], ca = Mth.cos(a), sa = Mth.sin(a);
        // ...running down onto flat ground: find where it levels off
        int[] heights = new int[4];
        heights[0] = ground - Math.round(down[1]);
        for (int k = 1; k < 4; k++) heights[k] = c.groundAt(c.x() + Math.round(ca * 14 * (k + 1)), c.z() + Math.round(sa * 14 * (k + 1)));
        int foot = -1;
        for (int k = 0; k < 3 && foot < 0; k++) {
            int fall = heights[k] - heights[k + 1];
            if (fall <= 3 && fall >= -2) foot = k;
            else if (fall < 2) return null; // climbing again: a hollow in the hillside, not the foot of it
        }
        if (foot < 0 || heights[foot] < c.seaLevel() + 2) {
            return null;
        }
        // the apex sits just inside the mouth of the valley
        int d = 14 * (foot + 1) - 4;
        return new AlluvialFan(c.x() + Math.round(ca * d), c.z() + Math.round(sa * d), heights[foot], a, 28 + c.random().nextInt(14), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new AlluvialFan(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getFloat("angle"), tag.getInt("r"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", ax);
        tag.putInt("z", az);
        tag.putInt("y", y);
        tag.putFloat("angle", angle);
        tag.putInt("r", radius);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.ALLUVIAL_FAN;
    }

    @Override
    public BoundingBox bounds() {
        // the sector the fan spreads over, and a little round the apex
        int minX = ax - 6, maxX = ax + 6, minZ = az - 6, maxZ = az + 6;
        float reach = radius * 1.12F + 2.0F;
        for (int i = 0; i <= 8; i++) {
            float phi = angle + (i / 4.0F - 1.0F) * (SPREAD + 0.4F);
            minX = Math.min(minX, Mth.floor(ax + Mth.cos(phi) * reach));
            maxX = Math.max(maxX, Mth.ceil(ax + Mth.cos(phi) * reach));
            minZ = Math.min(minZ, Mth.floor(az + Mth.sin(phi) * reach));
            maxZ = Math.max(maxZ, Mth.ceil(az + Mth.sin(phi) * reach));
        }
        return new BoundingBox(minX, y - 16, minZ, maxX, y + 14, maxZ);
    }

    /** The braided channels: each fans out from the apex, wandering a little, and some split in two partway down. */
    private boolean channel(float r, float phi) {
        if (r < 2.5F) return false;
        int n = 5 + Math.round(Terrain.hash(seed, 1) * 3.0F);
        float width = 0.7F + r * 0.025F;
        for (int k = 0; k < n; k++) {
            float base = (k / (n - 1.0F) * 2.0F - 1.0F) * SPREAD * 0.8F + (Terrain.hash(seed, 10 + k) - 0.5F) * 0.15F;
            float wander = 0.1F * Mth.sin(r * 0.17F + Terrain.hash(seed, 20 + k) * 6.0F) + 0.05F * Mth.sin(r * 0.41F + Terrain.hash(seed, 30 + k) * 6.0F);
            float at = base + wander;
            if (Math.abs(phi - at) * r < width) return true;
            // a braid splitting off and drifting away to one side
            float split = radius * (0.25F + 0.4F * Terrain.hash(seed, 40 + k));
            if (r > split && Terrain.hash(seed, 50 + k) < 0.6F) {
                float side = (Terrain.hash(seed, 60 + k) < 0.5F ? -1.0F : 1.0F) * 0.18F * Mth.sqrt((r - split) / radius);
                if (Math.abs(phi - at - side) * r < width * 0.8F) return true;
            }
        }
        return false;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, ax, az, angle);
                float u = l[0], v = l[1];
                if (u < -4.0F) continue;
                float r = Mth.sqrt(u * u + v * v);
                float phi = (float) Mth.atan2(v, u);
                float edge = radius * (0.9F + 0.22F * Terrain.field(seed, x, z, 14.0));
                float t = r / edge;
                if (t >= 1.0F) continue;
                // a cone, thickest at the apex and thinning out towards its edges; it doesn't climb back up the valley
                float side = r < 3.0F ? 1.0F : Terrain.smooth((SPREAD * (0.9F + 0.2F * Terrain.field(seed + 1, x, z, 10.0)) - Math.abs(phi)) / 0.35F);
                float thick = 5.0F * (float) Math.pow(1.0F - t, 1.5) * side * Terrain.smooth((u + 4.0F) / 4.0F);
                if (thick < 0.25F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 3 || ground > b.maxY() - 8 || !level.getFluidState(pos.set(x, ground + 1, z)).isEmpty()) continue;
                boolean wet = channel(r, phi) && thick > 0.6F;
                int top = ground + Math.round(thick) - (wet ? 1 : 0);
                pos.set(x, ground, z);
                BlockState was = level.getBlockState(pos);
                boolean grassy = was.is(Blocks.GRASS_BLOCK) || was.is(Blocks.PODZOL) || was.is(Blocks.DIRT);
                Holder<Biome> biome = level.getBiome(pos);
                BlockState sand = biome.is(BiomeTags.IS_BADLANDS) ? Blocks.RED_SAND.defaultBlockState() : Blocks.SAND.defaultBlockState();
                Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, Math.min(12, b.maxY() - Math.max(ground, top)));
                for (int h = top + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                for (int h = ground; h < top; h++) {
                    Terrain.set(level, pos, x, h, z, Terrain.noise(seed, x, h, z) < 0.55F ? Blocks.GRAVEL.defaultBlockState() : sand);
                }
                // coarse near the apex, fine at the edges; the channels' beds are freshly washed gravel and sand
                float n = Terrain.noise(seed, x, top, z);
                BlockState s;
                if (wet) {
                    s = n < 0.6F ? Blocks.GRAVEL.defaultBlockState() : sand;
                } else if (t < 0.35F) {
                    s = n < 0.55F ? Blocks.GRAVEL.defaultBlockState() : n < 0.7F ? Blocks.COBBLESTONE.defaultBlockState() : n < 0.8F ? Blocks.STONE.defaultBlockState() : sand;
                } else if (grassy) {
                    // the parts of the fan the floods have left alone a while are grassing over
                    s = n < 0.5F ? Blocks.GRASS_BLOCK.defaultBlockState() : n < 0.75F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                } else if (t < 0.7F) {
                    s = n < 0.45F ? Blocks.GRAVEL.defaultBlockState() : n < 0.9F ? sand : Blocks.COARSE_DIRT.defaultBlockState();
                } else {
                    s = n < 0.2F ? Blocks.GRAVEL.defaultBlockState() : n < 0.85F ? sand : Blocks.COARSE_DIRT.defaultBlockState();
                }
                if (s.is(Blocks.GRASS_BLOCK)) Terrain.set(level, pos, x, top - 1, z, Blocks.DIRT.defaultBlockState());
                Terrain.set(level, pos, x, top, z, s);
                float p = Terrain.noise(seed + 1, x, top, z);
                if (!wet && t < 0.45F && p > 0.96F) {
                    // a boulder the last big flood rolled out of the valley
                    Terrain.set(level, pos, x, top + 1, z, p > 0.985F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                } else if (s.is(Blocks.GRASS_BLOCK) && p < 0.35F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                } else if ((s.is(BlockTags.SAND) || s.is(Blocks.COARSE_DIRT)) && p < 0.03F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                }
            }
        }
    }
}
