package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
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
 * A river delta: where a river reaches the sea it slows and drops its load, building a fan of low, muddy land
 * out into the water and splitting into channels to cross it. The channels are lined with slightly higher
 * banks (levees), the land between is marsh that the high tide creeps over, and sand bars (with the odd patch
 * of quicksand) build up along the front.
 */
public record Delta(int cx, int cz, int sea, float angle, int radius, int channels, long seed) implements Landform {
    /** How far inland of the coast the delta's channels split. */
    private static final int APEX = 14;

    @Nullable
    static Landform plan(PlanContext c) {
        return c.search(12, 12, Delta::planAt);
    }

    @Nullable
    private static Landform planAt(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() - 3 || ground > c.seaLevel() + 3) {
            return null;
        }
        float seaward = c.seaward(30, 2);
        if (Float.isNaN(seaward)) {
            return null;
        }
        int ax = c.x() - Math.round(Mth.cos(seaward) * APEX), az = c.z() - Math.round(Mth.sin(seaward) * APEX);
        if (c.groundAt(ax, az) > c.seaLevel() + 10) {
            return null; // the land behind has to be low too
        }
        return new Delta(ax, az, c.seaLevel(), seaward, 44 + c.random().nextInt(14), 4 + c.random().nextInt(3), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Delta(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getFloat("angle"), tag.getInt("r"), tag.getInt("channels"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("sea", sea);
        tag.putFloat("angle", angle);
        tag.putInt("r", radius);
        tag.putInt("channels", channels);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.DELTA;
    }

    @Override
    public BoundingBox bounds() {
        int r = radius + 14;
        return new BoundingBox(cx - r, sea - 16, cz - r, cx + r, sea + 24, cz + r);
    }

    /** How far a point is from the nearest channel's edge (negative inside one), and whether it's a channel at all. */
    private float channel(float r, float dth, float u, float v) {
        if (u < 0.0F) {
            // the river coming in from inland, before it splits
            return Math.abs(v + 1.5F * Mth.sin(u * 0.12F)) - 2.5F;
        }
        float best = Float.MAX_VALUE;
        float spread = 1.7F;
        for (int j = 0; j < channels; j++) {
            float a = spread * (j / (float) (channels - 1) - 0.5F) + 0.15F * (Terrain.hash(seed, 40 + j) - 0.5F);
            float wiggle = (2.0F + 2.0F * Terrain.hash(seed, 50 + j)) * Mth.sin(r * (0.1F + 0.06F * Terrain.hash(seed, 60 + j)) + j * 2.1F);
            // near the apex the channels run together into the one river
            float across = Math.abs(r * Mth.sin(dth - a * Terrain.smooth(r / 16.0F)) - wiggle * Terrain.smooth(r / 10.0F));
            float width = 1.3F + r * 0.035F;
            best = Math.min(best, across - width);
        }
        return best;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean quicksand = Terrain.quicksand();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float dx = x + 0.5F - cx, dz = z + 0.5F - cz;
                float r = Mth.sqrt(dx * dx + dz * dz);
                float dth = Mth.wrapDegrees((float) Math.toDegrees(Mth.atan2(dz, dx) - angle)) * Mth.DEG_TO_RAD;
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                boolean feeder = l[0] < 0.0F && l[0] > -26.0F && Math.abs(l[1]) < 7.0F;
                float reach = radius * (0.8F + 0.3F * Terrain.field(seed, dth * 12.0, 0.0, 1.0)) * (1.0F - 0.3F * Math.abs(dth));
                if (!feeder && (Math.abs(dth) > 1.2F || r > reach)) continue;
                int ground = Terrain.ground(level, pos, x, z);
                float n = Terrain.noise(seed, x, sea, z);
                // the edge of the fan slopes away under the sea (and blends into the land at the sides)
                float front = Terrain.smooth((reach - r) / 7.0F) * Terrain.smooth((1.2F - Math.abs(dth)) / 0.25F);
                if (feeder) front = 1.0F;
                float gap = channel(r, dth, l[0], l[1]);
                int top;
                boolean reeds = gap < 0.0F && gap > -0.8F && r > 6.0F && n > 0.7F;
                if (reeds) {
                    top = sea - 1; // reed beds along the channel edges
                } else if (gap < 0.0F) {
                    top = sea - 3; // a channel
                } else if (gap < 1.6F && r > 4.0F) {
                    top = sea; // the levees along the banks, just above the water
                } else {
                    top = sea - 1;
                }
                if (feeder && gap >= 0.0F) {
                    // the land round the incoming river stays as it was, just trimmed to bank height
                    top = Math.max(sea, Math.min(ground, sea + 1 + Math.round(Math.abs(l[1]) - 2.5F)));
                }
                top = Math.round(Mth.lerp(front, ground, top));
                if (top == ground && gap >= 0.0F && !reeds) continue;
                Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, 10);
                for (int h = top + 1; h <= Math.max(ground, sea - 1); h++) {
                    Terrain.set(level, pos, x, h, z, h <= sea - 1 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
                boolean bar = r > reach - 9.0F;
                for (int h = Math.min(ground, top) - 1; h <= top; h++) {
                    BlockState s = h < top - 1 || bar ? Blocks.SAND.defaultBlockState() : Blocks.MUD.defaultBlockState();
                    if (h == top && top >= sea - 1 && !bar && Terrain.field(seed + 3, x, z, 7.0) > 0.55F) s = Blocks.GRASS_BLOCK.defaultBlockState();
                    if (h == top && top == sea) s = Blocks.GRASS_BLOCK.defaultBlockState();
                    Terrain.set(level, pos, x, h, z, s);
                }
                if (top < sea - 1) continue;
                // quicksand in the wet sand of the bars, and a little in the marsh
                if (quicksand && top == sea - 1 && Terrain.field(seed + 5, x, z, 4.0) > (bar ? 0.8F : 0.9F)) {
                    Terrain.set(level, pos, x, top, z, ModBlocks.QUICKSAND.get().defaultBlockState());
                    Terrain.set(level, pos, x, top - 1, z, ModBlocks.QUICKSAND.get().defaultBlockState());
                    continue;
                }
                if (bar) continue;
                if (reeds && top == sea - 1) {
                    Terrain.set(level, pos, x, top, z, Blocks.MUD.defaultBlockState());
                    for (int k = 1; k <= 2 + Math.round(n * 7) % 2; k++) Terrain.set(level, pos, x, top + k, z, Blocks.SUGAR_CANE.defaultBlockState());
                } else if (n > 0.5F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                } else if (n > 0.35F) {
                    BlockState grass = Blocks.TALL_GRASS.defaultBlockState();
                    Terrain.set(level, pos, x, top + 1, z, grass.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
                    Terrain.set(level, pos, x, top + 2, z, grass.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
                }
            }
        }
    }
}
