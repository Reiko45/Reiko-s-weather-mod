package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * An oxbow lake: the loop of an old river meander, cut off when the river broke through the narrow neck and
 * took the short way round. What's left is a still, crescent-shaped lake, silting up at its ends into reed
 * beds, with the old point bar inside the curve.
 */
public record OxbowLake(int cx, int cz, int y, int radius, float angle, long seed) implements Landform {
    /** Half the arc the crescent sweeps round. */
    private static final float SWEEP = Mth.PI * 0.68F;

    @Nullable
    static Landform plan(PlanContext c) {
        int radius = 12 + c.random().nextInt(9);
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() || ground > c.seaLevel() + 12 || c.relief(radius + 4, 12) > 6 || !c.fits(ground, 8, 8)) {
            return null;
        }
        // the water sits a block below the lowest part of the floodplain round it
        int low = ground;
        for (int i = 0; i < 12; i++) {
            float a = i * Mth.TWO_PI / 12.0F;
            low = Math.min(low, c.groundAt(c.x() + Math.round(Mth.cos(a) * radius), c.z() + Math.round(Mth.sin(a) * radius)));
        }
        return new OxbowLake(c.x(), c.z(), low - 1, radius, c.random().nextFloat() * Mth.TWO_PI, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new OxbowLake(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getFloat("angle"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.putFloat("angle", angle);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.OXBOW_LAKE;
    }

    @Override
    public BoundingBox bounds() {
        int r = radius + 10;
        return new BoundingBox(cx - r, y - 6, cz - r, cx + r, y + 12, cz + r);
    }

    /** {how far from the lake's middle line, how far along the arc (0 middle, 1 tips), the lake's half-width there}, or null. */
    @Nullable
    private float[] lake(float x, float z) {
        float dx = x - cx, dz = z - cz;
        float theta = Mth.wrapDegrees((float) Math.toDegrees(Mth.atan2(dz, dx) - angle)) * Mth.DEG_TO_RAD;
        float along = Math.abs(theta) / SWEEP;
        if (along > 1.25F) return null;
        float r = Mth.sqrt(dx * dx + dz * dz);
        // the outer bank of the old bend wobbles; the lake narrows to nothing at the silted-up tips
        float mid = radius * (1.0F + 0.1F * Mth.sin(theta * 3.0F + Terrain.hash(seed, 1) * 6.0F));
        float half = (3.0F + 1.5F * Terrain.hash(seed, 2)) * Mth.sqrt(Math.max(0.0F, 1.0F - along * along));
        return new float[]{Math.abs(r - mid), along, half};
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = lake(x + 0.5F, z + 0.5F);
                if (l == null) continue;
                // the banks: up to three blocks out from the water
                float shore = l[0];
                if (shore > l[2] + 3.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                float n = Terrain.noise(seed, x, y, z);
                if (shore < l[2] && l[2] > 0.8F) {
                    float across = shore / l[2];
                    int depth = 1 + Math.round(2.5F * (1.0F - across * across) * (1.0F - l[1] * 0.6F));
                    Terrain.clearAbove(level, pos, x, Math.max(ground, y), z, 10);
                    for (int h = y + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    for (int h = Math.min(ground, y - depth) - 1; h <= y - depth; h++) {
                        Terrain.set(level, pos, x, h, z, n < 0.6F ? Blocks.MUD.defaultBlockState() : Blocks.CLAY.defaultBlockState());
                    }
                    for (int h = y - depth + 1; h <= y; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                    if (across > 0.6F && n > 0.55F || l[1] > 0.75F && n > 0.3F) {
                        // reeds standing in the shallows and the silted-up tips
                        if (depth <= 1) {
                            Terrain.set(level, pos, x, y, z, Blocks.MUD.defaultBlockState());
                            reed(level, pos, x, y + 1, z, n);
                        }
                    } else if (n < 0.12F) {
                        Terrain.set(level, pos, x, y + 1, z, Blocks.LILY_PAD.defaultBlockState());
                    } else if (n > 0.9F && depth >= 2) {
                        Terrain.set(level, pos, x, y - depth + 1, z, Blocks.SEAGRASS.defaultBlockState());
                    }
                    continue;
                }
                // the banks come down to just above the water (and are built up where the floodplain dips below it)
                float t = Mth.clamp((shore - l[2]) / 3.0F, 0.0F, 1.0F);
                int bank = Math.round(Mth.lerp(t, y + 1, Math.max(ground, y + 1)));
                if (bank == ground) continue;
                Terrain.clearAbove(level, pos, x, Math.max(ground, bank), z, 10);
                for (int h = bank + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                for (int h = ground; h < bank; h++) Terrain.set(level, pos, x, h, z, Blocks.DIRT.defaultBlockState());
                Terrain.set(level, pos, x, bank, z, Blocks.GRASS_BLOCK.defaultBlockState());
                if (n > 0.7F) reed(level, pos, x, bank + 1, z, 0.5F); // (no sugar cane: it needs water right beside it)
            }
        }
    }

    private static void reed(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, float n) {
        if (n > 0.85F) {
            for (int k = 0; k < 2 + Math.round(n * 10) % 2; k++) Terrain.set(level, pos, x, y + k, z, Blocks.SUGAR_CANE.defaultBlockState());
        } else {
            BlockState grass = Blocks.TALL_GRASS.defaultBlockState();
            Terrain.set(level, pos, x, y, z, grass.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
            Terrain.set(level, pos, x, y + 1, z, grass.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
        }
    }
}
