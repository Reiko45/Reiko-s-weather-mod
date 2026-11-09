package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Talus cones (scree cones): steep heaps of broken rock leaning against the foot of a cliff, each one fanning out
 * below a gully cut into the cliff face. Frost splits the rock all over the cliff, but the gullies funnel the
 * falling stones together, and they pile up at the steepest slope loose rock can stand at (the angle of
 * repose, about 35 degrees). Big blocks roll the furthest, so the cones are fine gravel near their tops and
 * coarse boulders round their toes.
 *
 * @param angle the way the cliff faces (downhill)
 * @param cones {apex x, apex z, apex y, foot y, top y, gully half width x 10} for each cone
 */
public record TalusCones(int cx, int cz, float angle, int[] cones, long seed) implements Landform {
    /** How much the cone's surface falls per block out from its apex. */
    private static final float REPOSE = 0.72F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 8 || !c.fits(ground, 30, 10)) {
            return null;
        }
        // a big drop over a short way: a cliff
        float[] down = c.downhill(10);
        if (down[1] < 8) {
            return null;
        }
        float a = down[0], ca = Mth.cos(a), sa = Mth.sin(a);
        int n = 2 + c.random().nextInt(3);
        float spacing = 11.0F + c.random().nextFloat() * 6.0F;
        int[] cones = new int[n * 6];
        int count = 0;
        for (int j = 0; j < n; j++) {
            float v = (j - (n - 1) / 2.0F) * spacing + (c.random().nextFloat() - 0.5F) * 4.0F;
            // the cliff's profile along this line: find its face (the steepest step down) and its foot
            int[] g = new int[4];
            for (int k = 0; k < 4; k++) {
                float u = -2.0F + k * 7.0F;
                g[k] = c.groundAt(c.x() + Math.round(ca * u - sa * v), c.z() + Math.round(sa * u + ca * v));
            }
            int step = 0;
            for (int k = 1; k < 3; k++) if (g[k] - g[k + 1] > g[step] - g[step + 1]) step = k;
            int top = g[step], foot = Math.min(g[step + 1], g[Math.min(3, step + 2)]);
            if (top - foot < 8 || top - g[step + 1] < 5) continue;
            // the cone's apex sits at the mouth of its gully, a little over halfway up the cliff, just behind the face
            float face = -2.0F + step * 7.0F + 2.5F;
            float u = face - 1.0F;
            cones[count * 6] = c.x() + Math.round(ca * u - sa * v);
            cones[count * 6 + 1] = c.z() + Math.round(sa * u + ca * v);
            cones[count * 6 + 2] = foot + Math.round((top - foot) * (0.55F + 0.15F * c.random().nextFloat()));
            cones[count * 6 + 3] = foot;
            cones[count * 6 + 4] = top;
            cones[count * 6 + 5] = Math.round((1.3F + c.random().nextFloat() * 1.0F) * 10.0F);
            count++;
        }
        if (count < 2) {
            return null;
        }
        return new TalusCones(c.x(), c.z(), a, java.util.Arrays.copyOf(cones, count * 6), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new TalusCones(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getIntArray("cones"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.put("cones", new IntArrayTag(cones));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.TALUS_CONES;
    }

    private int reach(int j) {
        return Math.round((cones[j * 6 + 2] - cones[j * 6 + 3]) / REPOSE) + 3;
    }

    @Override
    public BoundingBox bounds() {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int j = 0; j < cones.length / 6; j++) {
            int r = Math.max(reach(j), 12);
            minX = Math.min(minX, cones[j * 6] - r);
            maxX = Math.max(maxX, cones[j * 6] + r);
            minZ = Math.min(minZ, cones[j * 6 + 1] - r);
            maxZ = Math.max(maxZ, cones[j * 6 + 1] + r);
            minY = Math.min(minY, cones[j * 6 + 3]);
            maxY = Math.max(maxY, cones[j * 6 + 4]);
        }
        return new BoundingBox(minX, minY - 8, minZ, maxX, maxY + 10, maxZ);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int count = cones.length / 6;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                int cone = Integer.MIN_VALUE, gully = Integer.MAX_VALUE;
                float sort = 0.0F;
                for (int j = 0; j < count; j++) {
                    float[] l = Terrain.uv(x + 0.5, z + 0.5, cones[j * 6] + 0.5, cones[j * 6 + 1] + 0.5, angle);
                    float u = l[0], v = l[1];
                    int apex = cones[j * 6 + 2], foot = cones[j * 6 + 3];
                    float r = Mth.sqrt(u * u + v * v);
                    // the cone spreads out in front of the face (and wraps a little round the sides of its apex)
                    if (u > -1.5F && r < reach(j)) {
                        float h = apex - r * REPOSE + 0.8F * (Terrain.field(seed + j, x, z, 3.0) - 0.5F);
                        if (h > foot && Math.round(h) > cone) {
                            cone = Math.round(h);
                            sort = r / reach(j);
                        }
                    }
                    // the gully above it: a steep notch cut back into the cliff, down to the apex
                    float half = cones[j * 6 + 5] / 10.0F * (1.0F + 0.25F * Math.max(0.0F, -u) / 4.0F);
                    if (u < 1.0F && u > -9.0F && Math.abs(v) < half + 1.0F) {
                        float floor = apex + Math.max(0.0F, -u) * 1.7F + Math.max(0.0F, Math.abs(v) - half) * 3.0F;
                        gully = Math.min(gully, Math.round(floor));
                    }
                }
                if (cone == Integer.MIN_VALUE && gully == Integer.MAX_VALUE) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 3 || ground > b.maxY() - 3) continue;
                float n = Terrain.noise(seed, x, ground, z);
                if (gully < ground && cone <= ground) {
                    // cut the notch, floored with rock and loose stones
                    Terrain.clearAbove(level, pos, x, ground, z, Math.min(8, b.maxY() - ground));
                    for (int h = gully + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    Terrain.set(level, pos, x, gully, z, n < 0.45F ? Blocks.GRAVEL.defaultBlockState() : n < 0.75F ? Blocks.STONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState());
                    Terrain.set(level, pos, x, gully - 1, z, Blocks.STONE.defaultBlockState());
                    continue;
                }
                if (cone <= ground) continue;
                Terrain.clearAbove(level, pos, x, ground, z, Math.min(cone - ground + 6, b.maxY() - ground));
                for (int h = ground + 1; h <= cone; h++) {
                    // gravel near the top of the cone, the big blocks rolled out to its toe
                    float m = Terrain.noise(seed, x, h, z) - (h == cone ? 0.0F : 0.2F);
                    float coarse = sort * 0.8F;
                    BlockState s = m < 0.55F - coarse * 0.4F ? Blocks.GRAVEL.defaultBlockState()
                            : m < 0.75F ? Blocks.COBBLESTONE.defaultBlockState()
                            : m < 0.88F ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    Terrain.set(level, pos, x, h, z, s);
                }
                if (sort > 0.75F && n > 0.9F && cone + 1 <= b.maxY()) {
                    Terrain.set(level, pos, x, cone + 1, z, n > 0.97F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                }
            }
        }
    }
}
