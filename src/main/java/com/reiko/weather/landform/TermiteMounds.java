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
 * Termite mounds: towers of soil cemented with the termites' spit, dotted across the savanna, some low and
 * domed, some steep spires taller than a person with a smaller pinnacle or two at their side. Each is built
 * round a chimney that keeps the nest below cool, and lasts for decades.
 */
public record TermiteMounds(int cx, int cz, int y, int count, long seed) implements Landform {
    private static final int SPREAD = 40;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 1 || !c.fits(ground, 4, 12)) {
            return null;
        }
        return new TermiteMounds(c.x(), c.z(), ground, 7 + c.random().nextInt(8), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new TermiteMounds(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("count"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("count", count);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.TERMITE_MOUNDS;
    }

    @Override
    public BoundingBox bounds() {
        int r = SPREAD + 8;
        return new BoundingBox(cx - r, y - 24, cz - r, cx + r, y + 32, cz + r);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // each mound: a main spire and maybe a pinnacle beside it
        float[][] spires = new float[count * 2][];
        for (int i = 0; i < count; i++) {
            float a = Terrain.hash(seed, i) * Mth.TWO_PI, d = Mth.sqrt(Terrain.hash(seed, 100 + i)) * SPREAD;
            float x = cx + 0.5F + Math.round(Mth.cos(a) * d), z = cz + 0.5F + Math.round(Mth.sin(a) * d);
            boolean spire = Terrain.hash(seed, 200 + i) < 0.65F;
            float r = spire ? 1.8F + Terrain.hash(seed, 300 + i) * 1.2F : 2.0F + Terrain.hash(seed, 300 + i) * 1.5F;
            float h = spire ? 4.0F + Terrain.hash(seed, 400 + i) * 4.0F : 1.5F + Terrain.hash(seed, 400 + i) * 1.5F;
            spires[i * 2] = new float[]{x, z, r, h, spire ? 0.7F : 2.0F};
            if (spire && Terrain.hash(seed, 500 + i) < 0.6F) {
                float side = Terrain.hash(seed, 600 + i) * Mth.TWO_PI;
                spires[i * 2 + 1] = new float[]{x + Math.round(Mth.cos(side) * 1.6F), z + Math.round(Mth.sin(side) * 1.6F), r * 0.6F, h * 0.6F, 0.7F};
            }
        }
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float rise = 0.0F;
                for (float[] s : spires) {
                    if (s == null) continue;
                    float d = Mth.sqrt(Mth.square(x + 0.5F - s[0]) + Mth.square(z + 0.5F - s[1])) / s[2];
                    // a spire's sides steepen toward the top (a dome's stay rounded)
                    if (d < 1.0F) rise = Math.max(rise, s[3] * (1.0F - (float) Math.pow(d, s[4])));
                }
                int add = Math.round(rise);
                if (add < 1) continue;
                int ground = Terrain.ground(level, pos, x, z);
                pos.set(x, ground + 1, z);
                if (!level.getFluidState(pos).isEmpty()) continue;
                Terrain.clearAbove(level, pos, x, ground, z, add + 4);
                for (int k = ground + 1; k <= ground + add; k++) {
                    float n = Terrain.noise(seed, x, k, z);
                    BlockState s = n < 0.45F ? Blocks.PACKED_MUD.defaultBlockState() : n < 0.75F ? Blocks.TERRACOTTA.defaultBlockState()
                            : n < 0.9F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.BROWN_TERRACOTTA.defaultBlockState();
                    Terrain.set(level, pos, x, k, z, s);
                }
            }
        }
    }
}
