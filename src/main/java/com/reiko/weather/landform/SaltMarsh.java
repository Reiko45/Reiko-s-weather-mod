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
 * A salt marsh: a flat, sheltered stretch of coast at the level of the high tides, grown over with tough grasses
 * and threaded by a maze of winding creeks that fill and drain twice a day. The low marsh is bare mud the tide
 * floods over; round pans of standing water dot the higher marsh, and the soft mud hides patches of quicksand.
 */
public record SaltMarsh(int cx, int cz, int sea, int radius, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        return c.search(12, 12, SaltMarsh::planAt);
    }

    @Nullable
    private static Landform planAt(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        int radius = 30 + c.random().nextInt(16);
        if (ground < c.seaLevel() - 2 || ground > c.seaLevel() + 3 || c.relief(8, 8) > 4) {
            return null;
        }
        // it has to be by the sea
        boolean sea = false;
        for (int i = 0; i < 12 && !sea; i++) {
            float a = i * Mth.TWO_PI / 12.0F;
            sea = c.groundAt(c.x() + Math.round(Mth.cos(a) * radius), c.z() + Math.round(Mth.sin(a) * radius)) < c.seaLevel() - 2;
        }
        return sea ? new SaltMarsh(c.x(), c.z(), c.seaLevel(), radius, c.random().nextLong()) : null;
    }

    static Landform load(CompoundTag tag) {
        return new SaltMarsh(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getInt("r"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("sea", sea);
        tag.putInt("r", radius);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.SALT_MARSH;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * 1.25F) + 2;
        return new BoundingBox(cx - r, sea - 14, cz - r, cx + r, sea + 14, cz + r);
    }

    /** How deep a creek runs here (0 if there isn't one): the big creeks follow one contour of a noise field, the little ones another. */
    private int creek(int x, int z) {
        float wx = x + 6.0F * (Terrain.field(seed + 11, x, z, 23.0) - 0.5F), wz = z + 6.0F * (Terrain.field(seed + 12, x, z, 23.0) - 0.5F);
        float big = Math.abs(Terrain.field(seed + 1, wx, wz, 26.0) - 0.5F);
        if (big < 0.035F) return big < 0.018F ? 3 : 2;
        float small = Math.abs(Terrain.field(seed + 2, wx, wz, 11.0) - 0.5F);
        return small < 0.03F ? 1 : 0;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean quicksand = Terrain.quicksand();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float dx = x + 0.5F - cx, dz = z + 0.5F - cz;
                float d = Mth.sqrt(dx * dx + dz * dz) / (radius * (0.8F + 0.4F * Terrain.field(seed, x, z, 16.0)));
                if (d > 1.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                float n = Terrain.noise(seed, x, sea, z);
                // high marsh a block over the tide line in the middle, low marsh (bare mud, awash at high tide) round it
                float rise = Terrain.field(seed + 3, x, z, 13.0);
                boolean high = rise > 0.55F && d < 0.85F;
                int marsh = high ? sea : sea - 1;
                int depth = creek(x, z);
                // (well inside the high marsh, so the pool can't spill out over the low marsh or into a creek)
                boolean pan = depth == 0 && rise > 0.63F && d < 0.8F && Terrain.field(seed + 4, x, z, 4.0) > 0.8F
                        && creek(x + 1, z) == 0 && creek(x - 1, z) == 0 && creek(x, z + 1) == 0 && creek(x, z - 1) == 0;
                int top = depth > 0 ? sea - 1 - depth : marsh;
                // blending into the land (or the sea bed) at the edge
                float edge = Terrain.smooth((1.0F - d) / 0.2F);
                top = Math.round(Mth.lerp(edge, ground, top));
                Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, 10);
                for (int h = top + 1; h <= Math.max(ground, sea - 1); h++) {
                    Terrain.set(level, pos, x, h, z, h <= sea - 1 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
                if (pan && edge > 0.99F) {
                    // a salt pan: a shallow pool sitting up on the high marsh
                    Terrain.set(level, pos, x, sea, z, Blocks.WATER.defaultBlockState());
                    Terrain.set(level, pos, x, sea - 1, z, Blocks.MUD.defaultBlockState());
                    continue;
                }
                for (int h = Math.min(ground, top) - 2; h <= top; h++) {
                    BlockState s = h < top - 1 ? Blocks.CLAY.defaultBlockState() : Blocks.MUD.defaultBlockState();
                    if (h == top && high && depth == 0 && top >= sea) s = Blocks.GRASS_BLOCK.defaultBlockState();
                    Terrain.set(level, pos, x, h, z, s);
                }
                if (depth > 0 || top < sea - 1) continue;
                if (quicksand && !high && Terrain.field(seed + 5, x, z, 5.0) > 0.86F) {
                    Terrain.set(level, pos, x, top, z, ModBlocks.QUICKSAND.get().defaultBlockState());
                    Terrain.set(level, pos, x, top - 1, z, ModBlocks.QUICKSAND.get().defaultBlockState());
                    continue;
                }
                if (high) {
                    // cordgrass and sea lavender (ish) on the high marsh
                    if (n > 0.55F) {
                        BlockState grass = Blocks.TALL_GRASS.defaultBlockState();
                        Terrain.set(level, pos, x, top + 1, z, grass.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
                        Terrain.set(level, pos, x, top + 2, z, grass.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
                    } else if (n > 0.15F) {
                        Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                    } else if (n < 0.03F) {
                        Terrain.set(level, pos, x, top + 1, z, Blocks.ALLIUM.defaultBlockState());
                    }
                } else if (n > 0.8F) {
                    // pioneer plants out on the mud
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                }
            }
        }
    }
}
