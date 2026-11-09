package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Nunataks: rocky peaks standing up out of an ice sheet. The ice has spread out over the land in a broad, smooth lobe,
 * burying the hills and valleys alike, and only the highest summits still poke through it, bare and frost-shattered,
 * with a little snow lodged on their ledges. Where the ice flows on round them, or bulges over the land toward its
 * edge, it cracks open in crevasses. (The word is Inuit, for a lonely peak.)
 *
 * @param y     the level of the land under the middle of the ice
 * @param angle the way the ice spreads (toward the lowest ground)
 * @param peaks {x, z, radius, summit y, foot y (where it meets the ice)} for each peak
 */
public record Nunataks(int cx, int cz, int y, float angle, int radius, int thick, int[] peaks, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 1) {
            return null;
        }
        int radius = 30 + c.random().nextInt(14);
        int sum = ground, low = ground, high = ground, lowest = Integer.MAX_VALUE;
        float spread = 0.0F;
        for (int i = 0; i < 10; i++) {
            float a = i * Mth.TWO_PI / 10.0F;
            int h = c.groundAt(c.x() + Math.round(Mth.cos(a) * radius * 0.75F), c.z() + Math.round(Mth.sin(a) * radius * 0.75F));
            sum += h;
            low = Math.min(low, h);
            high = Math.max(high, h);
            if (h < lowest) {
                lowest = h;
                spread = a;
            }
        }
        if (high - low > 20 || low < c.seaLevel() - 2 || !c.fits(ground, 28, 50)) {
            return null;
        }
        long seed = c.random().nextLong();
        int thick = 8 + c.random().nextInt(7);
        Nunataks sheet = new Nunataks(c.x(), c.z(), Math.round(sum / 11.0F), spread + (c.random().nextFloat() - 0.5F) * 0.5F, radius, thick, new int[0], seed);
        int n = 2 + c.random().nextInt(4);
        int[] peaks = new int[n * 5];
        for (int i = 0; i < n; i++) {
            float a = c.random().nextFloat() * Mth.TWO_PI, d = (0.12F + 0.5F * c.random().nextFloat()) * radius;
            int px = c.x() + Math.round(Mth.cos(a) * d), pz = c.z() + Math.round(Mth.sin(a) * d);
            int foot = Math.round(sheet.iceTop(px + 0.5F, pz + 0.5F));
            peaks[i * 5] = px;
            peaks[i * 5 + 1] = pz;
            peaks[i * 5 + 2] = 4 + c.random().nextInt(5);
            peaks[i * 5 + 3] = Math.max(foot, c.groundAt(px, pz)) + 6 + c.random().nextInt(10);
            peaks[i * 5 + 4] = foot;
        }
        return new Nunataks(sheet.cx, sheet.cz, sheet.y, sheet.angle, radius, thick, peaks, seed);
    }

    static Landform load(CompoundTag tag) {
        return new Nunataks(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getFloat("angle"), tag.getInt("r"), tag.getInt("thick"),
                tag.getIntArray("peaks"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putFloat("angle", angle);
        tag.putInt("r", radius);
        tag.putInt("thick", thick);
        tag.put("peaks", new IntArrayTag(peaks));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.NUNATAKS;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * 1.45F) + 4;
        int top = y + thick + 6;
        for (int i = 0; i < peaks.length / 5; i++) top = Math.max(top, peaks[i * 5 + 3] + 4);
        return new BoundingBox(cx - r, y - 26, cz - r, cx + r, top, cz + r);
    }

    /** How far out toward the edge of the lobe a point is (0 in the middle, 1 at the edge): longer the way the ice spreads. */
    private float spread(float px, float pz) {
        float[] l = Terrain.uv(px, pz, cx, cz, angle);
        float a = radius * (l[0] > 0.0F ? 1.25F : 0.95F), b = radius * 0.8F;
        float wobble = 0.9F + 0.2F * Terrain.field(seed, px, pz, 16.0);
        return (Mth.square(l[0] / a) + Mth.square(l[1] / b)) / (wobble * wobble);
    }

    /** The ice sheet's surface: a broad, smooth dome, steep at its margin and sloping off the way it spreads. */
    private float iceTop(float px, float pz) {
        float e = spread(px, pz);
        if (e >= 1.0F) return Float.NEGATIVE_INFINITY;
        float u = Terrain.uv(px, pz, cx, cz, angle)[0];
        return y + thick * Mth.sqrt(1.0F - e) - u * 0.04F + (Terrain.field(seed + 1, px, pz, 9.0) - 0.5F) * 1.6F;
    }

    /** The highest rock of any peak at a column: craggy pyramids, ridged by arêtes, rising steeply from the ice. */
    private float peakTop(float px, float pz) {
        float best = Float.NEGATIVE_INFINITY;
        for (int k = 0; k < peaks.length / 5; k++) {
            float dx = px - peaks[k * 5] - 0.5F, dz = pz - peaks[k * 5 + 1] - 0.5F;
            float d = Mth.sqrt(dx * dx + dz * dz), r = peaks[k * 5 + 2];
            if (d > r * 2.5F) continue;
            float theta = (float) Mth.atan2(dz, dx);
            float crag = 1.0F + 0.3F * Mth.sin(3.0F * theta + k * 2.1F) + 0.12F * Mth.sin(7.0F * theta + k);
            float slope = (peaks[k * 5 + 3] - peaks[k * 5 + 4] + 1.0F) / r;
            float h = peaks[k * 5 + 3] - slope * d / crag + (Terrain.field(seed + 2 + k, px, pz, 2.5) - 0.5F) * 3.0F;
            best = Math.max(best, h);
        }
        return best;
    }

    /** Whether (u, v) lies on one of the crevasses that split the ice, across the way it flows and round its edge. */
    private boolean crevasse(float u, float v, int x, int z, float e) {
        if (e < 0.3F || Terrain.field(seed + 3, x, z, 10.0) < 0.5F) return false;
        float spacing = 7.0F + 3.0F * Terrain.hash(seed, 4);
        return Math.abs(Mth.sin(Mth.PI * (u + 0.03F * v * v) / spacing + Terrain.field(seed + 5, x, z, 6.0) * 2.0F)) < 0.1F;
    }

    private BlockState rock(int x, int h, int z) {
        float band = Terrain.field(seed + 6, x * 0.5 + h * 1.5, z * 0.5 - h, 3.0);
        float n = Terrain.noise(seed, x, h, z);
        return n < 0.07F ? Blocks.COBBLESTONE.defaultBlockState() : band > 0.6F ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float px = x + 0.5F, pz = z + 0.5F;
                float e = spread(px, pz);
                float peak = peakTop(px, pz);
                if (e >= 1.0F && peak == Float.NEGATIVE_INFINITY) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 1 || ground > b.maxY() - 2) continue;
                int rockTop = Math.max(ground, Math.min(b.maxY() - 2, Math.round(peak)));
                int ice = e < 1.0F ? Math.min(b.maxY() - 2, Math.round(iceTop(px, pz))) : Integer.MIN_VALUE;
                // the ice is never more than so thick over a hollow: over deep ground it follows the land down
                ice = Math.min(ice, ground + thick + 6);
                int top = Math.max(rockTop, ice);
                if (top <= ground) continue;
                Terrain.clearAbove(level, pos, x, top, z, Math.min(16, b.maxY() - top));
                // the peak's bare rock, under the ice and out of it
                for (int h = ground + 1; h <= rockTop; h++) Terrain.set(level, pos, x, h, z, rock(x, h, z));
                if (ice > rockTop) {
                    float[] l = Terrain.uv(px, pz, cx, cz, angle);
                    boolean crack = ice - rockTop >= 4 && crevasse(l[0], l[1], x, z, e);
                    int depth = crack ? Math.min(6, ice - rockTop - 1) : 0;
                    for (int h = rockTop + 1; h <= ice; h++) {
                        float n = Terrain.noise(seed, x, h, z);
                        BlockState s;
                        if (h > ice - depth) s = Blocks.AIR.defaultBlockState();
                        else if (h == ice - depth && crack) s = Blocks.SNOW_BLOCK.defaultBlockState(); // drifted snow in the bottom of the crack
                        else if (h >= ice - 1 && !crack) s = Blocks.SNOW_BLOCK.defaultBlockState(); // snow and firn on the surface
                        else if (h <= rockTop + 2 && n < 0.55F || n > 0.9F || crack && h > ice - depth - 2) s = Blocks.BLUE_ICE.defaultBlockState();
                        else s = Blocks.PACKED_ICE.defaultBlockState();
                        Terrain.set(level, pos, x, h, z, s);
                    }
                    continue;
                }
                // snow lodged on the peaks' ledges, where the rock is flat enough to hold it
                if (rockTop > ground && rockTop + 1 <= b.maxY()) {
                    float steep = 0.0F;
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) steep = Math.max(steep, Math.abs(peakTop(px + d[0], pz + d[1]) - peakTop(px, pz)));
                    float n = Terrain.noise(seed, x, 3, z);
                    if (steep < 1.4F && n < 0.75F) {
                        Terrain.set(level, pos, x, rockTop + 1, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + (int) (n * 4.0F)));
                    } else if (steep < 2.2F && n < 0.25F) {
                        Terrain.set(level, pos, x, rockTop + 1, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1));
                    }
                }
            }
        }
    }
}
