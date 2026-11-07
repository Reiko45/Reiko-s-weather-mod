package com.reiko.weather.landform;

import com.reiko.weather.block.GeyserBlock;
import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A geothermal field: a flat patch of white sinter with steaming pools ringed in orange and yellow, hissing
 * fumaroles, bubbling mud, and a geyser or two on little mounds. The pools are warm (heat vents at the
 * bottom) and soaking in one heals you.
 */
public record HotSprings(int cx, int cz, int y, int radius, long seed) implements Landform {
    private static final int EDGE = 6;

    @Nullable
    static Landform plan(PlanContext c) {
        int radius = 14 + c.random().nextInt(9);
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 1 || c.relief(radius, 8) > 11 || !c.fits(ground, 10, 30)) {
            return null;
        }
        int sum = ground;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            sum += c.groundAt(c.x() + (int) Math.round(Math.cos(a) * radius * 0.7), c.z() + (int) Math.round(Math.sin(a) * radius * 0.7));
        }
        return new HotSprings(c.x(), c.z(), Math.round(sum / 9.0F), radius, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new HotSprings(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getLong("seed"));
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
        return LandformKind.HOT_SPRINGS;
    }

    @Override
    public BoundingBox bounds() {
        int r = radius + EDGE;
        return new BoundingBox(cx - r, y - 20, cz - r, cx + r, y + 30, cz + r);
    }

    /** Pools as {x, z, radius}. */
    private float[][] pools() {
        int count = 2 + (int) (Terrain.hash(seed, 0) * 3);
        float[][] pools = new float[count][];
        for (int i = 0; i < count; i++) {
            float angle = Terrain.hash(seed, 10 + i) * Mth.TWO_PI, dist = (0.3F + 0.45F * Terrain.hash(seed, 20 + i)) * radius;
            pools[i] = new float[]{cx + Mth.cos(angle) * dist, cz + Mth.sin(angle) * dist, 2.2F + 1.8F * Terrain.hash(seed, 30 + i)};
        }
        return pools;
    }

    /** Geysers as {x, z}: one near the middle, sometimes a second further out (unless it would land in a pool). */
    private float[][] geysers(float[][] pools) {
        float a0 = Terrain.hash(seed, 40) * Mth.TWO_PI, d0 = 0.12F * radius;
        float a1 = Terrain.hash(seed, 41) * Mth.TWO_PI, d1 = 0.65F * radius;
        float[][] candidates = Terrain.hash(seed, 42) < 0.5F
                ? new float[][]{{cx + Mth.cos(a0) * d0, cz + Mth.sin(a0) * d0}, {cx + Mth.cos(a1) * d1, cz + Mth.sin(a1) * d1}}
                : new float[][]{{cx + Mth.cos(a0) * d0, cz + Mth.sin(a0) * d0}};
        java.util.List<float[]> out = new java.util.ArrayList<>();
        for (float[] g : candidates) {
            boolean clear = true;
            for (float[] p : pools) {
                if ((float) Mth.length(g[0] - p[0], g[1] - p[1]) < p[2] + 3.0F) clear = false;
            }
            if (clear || out.isEmpty()) out.add(g);
        }
        return out.toArray(new float[0][]);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        float[][] pools = pools();
        float[][] geysers = geysers(pools);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float r = (float) Mth.length(x - cx, z - cz);
                if (r > radius + EDGE) continue;
                int ground = Terrain.ground(level, pos, x, z);
                BlockState natural = level.getBlockState(pos.set(x, ground, z));
                float inner = Terrain.smooth((radius + EDGE - r) / EDGE);
                int surface = Math.round(Mth.lerp(inner, ground, y));
                Terrain.clearAbove(level, pos, x, Math.min(ground, surface), z, 24);
                for (int h = surface + 1; h <= ground; h++) {
                    Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                }
                if (r > radius) {
                    // the edge: just bring the natural ground to the right height
                    for (int h = Math.min(ground, surface) - 1; h <= surface; h++) {
                        Terrain.set(level, pos, x, h, z, h == surface ? natural : Blocks.DIRT.defaultBlockState());
                    }
                    continue;
                }
                float n = Terrain.noise(seed, x, 0, z);
                for (int h = Math.min(ground, surface) - 2; h < surface; h++) {
                    Terrain.set(level, pos, x, h, z, n < 0.5F ? Blocks.CALCITE.defaultBlockState() : Blocks.TUFF.defaultBlockState());
                }
                BlockState top = n < 0.55F ? Blocks.CALCITE.defaultBlockState() : n < 0.85F ? Blocks.WHITE_TERRACOTTA.defaultBlockState()
                        : n < 0.97F ? Blocks.CALCITE.defaultBlockState() : Blocks.MUD.defaultBlockState();
                if (n < 0.01F) top = ModBlocks.VOLCANIC_VENT.get().defaultBlockState();
                else if (n < 0.03F) top = ModBlocks.SULFUR_CRUST.get().defaultBlockState();
                boolean pooled = false;
                for (float[] p : pools) {
                    float d = (float) Mth.length(x + 0.5F - p[0], z + 0.5F - p[1]);
                    if (d < p[2]) {
                        int depth = 1 + Math.round(2.0F * (1.0F - d / p[2]));
                        for (int h = surface - depth + 1; h <= surface; h++) {
                            Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                        }
                        Terrain.set(level, pos, x, surface - depth, z, d < 0.8F ? ModBlocks.VOLCANIC_VENT.get().defaultBlockState() : Blocks.CALCITE.defaultBlockState());
                        pooled = true;
                        break;
                    } else if (d < p[2] + 1.3F) {
                        top = Blocks.ORANGE_TERRACOTTA.defaultBlockState(); // bacterial mats in the warm runoff
                    } else if (d < p[2] + 2.6F && !top.is(Blocks.ORANGE_TERRACOTTA)) {
                        top = Blocks.YELLOW_TERRACOTTA.defaultBlockState();
                    }
                }
                if (pooled) continue;
                Terrain.set(level, pos, x, surface, z, top);
                for (float[] g : geysers) {
                    float d = (float) Mth.length(x + 0.5F - g[0], z + 0.5F - g[1]);
                    if (d < 2.6F) {
                        int mound = d < 0.75F ? 1 : d < 1.6F ? 2 : 1;
                        for (int h = 1; h <= mound; h++) {
                            Terrain.set(level, pos, x, surface + h, z, Blocks.CALCITE.defaultBlockState());
                        }
                        if (d < 0.75F) {
                            Terrain.set(level, pos, x, surface + 2, z, ModBlocks.GEYSER.get().defaultBlockState());
                            level.scheduleTick(pos.immutable(), ModBlocks.GEYSER.get(), 100 + (int) (Terrain.hash(seed, x * 31 + z) * GeyserBlock.restLength(level.getRandom())));
                        }
                    }
                }
            }
        }
    }
}
