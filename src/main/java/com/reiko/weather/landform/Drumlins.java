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
 * A drumlin field with an esker: the ice sheet moulded the till beneath it into a swarm of smooth, whale-backed
 * hills, all lying the same way, blunt where the ice came from and tapering where it went. Winding through
 * them is an esker, the long gravel ridge left by a river that once ran in a tunnel under the ice.
 */
public record Drumlins(int cx, int cz, int y, float angle, int count, long seed) implements Landform {
    private static final int SPREAD = 56;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 2 || c.relief(SPREAD / 2, 10) > 10 || !c.fits(ground, 8, 20)) {
            return null;
        }
        return new Drumlins(c.x(), c.z(), ground, c.random().nextFloat() * Mth.TWO_PI, 6 + c.random().nextInt(5), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Drumlins(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getFloat("angle"), tag.getInt("count"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putFloat("angle", angle);
        tag.putInt("count", count);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.DRUMLINS;
    }

    @Override
    public BoundingBox bounds() {
        int r = SPREAD + 36;
        return new BoundingBox(cx - r, y - 30, cz - r, cx + r, y + 40, cz + r);
    }

    /** How high the drumlins and the esker raise the ground at a point, and whether it's the esker's gravel. */
    private float[] rise(float x, float z) {
        float[] l = Terrain.uv(x, z, cx, cz, angle);
        float u = l[0], v = l[1], best = 0.0F;
        for (int i = 0; i < count; i++) {
            float du = u - (Terrain.hash(seed, i) - 0.5F) * SPREAD * 1.8F, dv = v - (Terrain.hash(seed, 100 + i) - 0.5F) * SPREAD * 1.4F;
            float length = 16.0F + 10.0F * Terrain.hash(seed, 200 + i), width = 6.0F + 3.0F * Terrain.hash(seed, 300 + i);
            float height = 5.0F + 6.0F * Terrain.hash(seed, 400 + i);
            // blunt and steep on the side the ice came from (-u), long and tapering on the lee side
            float s = Mth.square(du / (length * (du < 0 ? 0.55F : 1.0F))) + Mth.square(dv / (width * (1.0F - 0.35F * Mth.clamp(du / length, 0.0F, 1.0F))));
            if (s < 1.0F) best = Math.max(best, height * Terrain.smooth(1.0F - s) * (0.85F + 0.15F * (1.0F - s)));
        }
        // the esker winds roughly along the way the ice flowed
        float esker = 0.0F;
        float half = SPREAD + 20.0F;
        if (Math.abs(u) < half) {
            float p1 = Terrain.hash(seed, 1) * Mth.TWO_PI, p2 = Terrain.hash(seed, 2) * Mth.TWO_PI;
            float centre = 12.0F * Mth.sin(u * 0.035F + p1) + 6.0F * Mth.sin(u * 0.09F + p2) + (Terrain.hash(seed, 3) - 0.5F) * 30.0F;
            float off = Math.abs(v - centre) / (3.2F + 0.8F * Mth.sin(u * 0.2F));
            float ends = Terrain.smooth((half - Math.abs(u)) / 20.0F);
            if (off < 1.0F) esker = (3.0F + 1.5F * Terrain.field(seed, u, 0.0, 18.0)) * (1.0F - off * off) * ends;
        }
        return new float[]{Math.max(best, esker), esker > best ? 1.0F : 0.0F};
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BoundingBox b = bounds();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] r = rise(x + 0.5F, z + 0.5F);
                int add = Math.round(r[0]);
                if (add < 1) continue;
                int ground = Terrain.ground(level, pos, x, z);
                pos.set(x, ground, z);
                if (!level.getFluidState(pos.above()).isEmpty()) continue; // not out of a lake or the sea
                BlockState was = level.getBlockState(pos);
                boolean snowy = was.is(Blocks.SNOW_BLOCK) || level.getBiome(pos).value().getBaseTemperature() < 0.15F;
                Terrain.clearAbove(level, pos, x, ground, z, add + 14);
                boolean esker = r[1] > 0.5F;
                float n = Terrain.noise(seed, x, ground, z);
                for (int h = ground; h < ground + add; h++) {
                    // till (stony clay) in the drumlins; washed sand and gravel in the esker
                    BlockState fill = esker ? (Terrain.noise(seed, x, h, z) < 0.6F ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState())
                            : h < ground + add - 1 ? (Terrain.noise(seed, x, h, z) < 0.2F ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.DIRT.defaultBlockState())
                            : Blocks.DIRT.defaultBlockState();
                    Terrain.set(level, pos, x, h, z, fill);
                }
                int top = ground + add;
                if (esker && n < 0.35F) {
                    Terrain.set(level, pos, x, top, z, Blocks.COARSE_DIRT.defaultBlockState()); // stony and dry along the crest
                } else {
                    Terrain.set(level, pos, x, top, z, snowy ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
                    if (!snowy && n > 0.7F) Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                }
            }
        }
    }
}
