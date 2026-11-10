package com.reiko.weather.landform;

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
 * A pink lake (like Lake Hillier or Hutt Lagoon in Western Australia): a shallow salt lake the colour of bubblegum.
 * Its water is many times saltier than the sea, too salty for almost anything but a single-celled alga that turns
 * itself red with carotene to shield it from the fierce sun, and salt-loving bacteria that are pink as well. Round the
 * shore the water is evaporating all the time, leaving a crust of salt crystals stained pink at the waterline and a
 * beach of dazzling white sand.
 *
 * @param water the lake's surface
 * @param depth how deep it is in the middle
 */
public record PinkLake(int cx, int cz, int water, int radius, int depth, long seed) implements Landform {
    /** How far out the shore reaches, and the ring beyond it where standing water is sealed in. */
    private static final float SHORE = 1.5F, SEAL = 1.62F;
    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        int radius = 15 + c.random().nextInt(11);
        if (ground < c.seaLevel() || !c.fits(ground, 10, 20)) {
            return null;
        }
        // the water stands a block under the lowest point of the land round it
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int i = 0; i < 12; i++) {
            float a = i * Mth.TWO_PI / 12.0F;
            int h = c.groundAt(c.x() + Math.round(Mth.cos(a) * (radius + 2)), c.z() + Math.round(Mth.sin(a) * (radius + 2)));
            low = Math.min(low, h);
            high = Math.max(high, h);
        }
        // never open to the sea, nor perched on a slope
        if (low < c.seaLevel() || high - low > 12) {
            return null;
        }
        return new PinkLake(c.x(), c.z(), low - 1, radius, 1 + c.random().nextInt(3), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new PinkLake(tag.getInt("x"), tag.getInt("z"), tag.getInt("water"), tag.getInt("r"), tag.getInt("depth"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("water", water);
        tag.putInt("r", radius);
        tag.putInt("depth", depth);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.PINK_LAKE;
    }

    @Override
    public BoundingBox bounds() {
        int r = Mth.ceil(radius * SEAL / 0.88F) + 2;
        return new BoundingBox(cx - r, water - 12, cz - r, cx + r, water + 46, cz + r);
    }

    /** How far out from the middle a point is: under 1 in the water. */
    private float lake(double x, double z) {
        return (float) Mth.length(x - cx, z - cz) / (radius * (0.88F + 0.24F * Terrain.field(seed, x, z, 9.0)));
    }

    /** The top of a column {@code d} out from the middle (the water's surface in the lake) once it's built, given its ground. */
    private int shore(float d, int ground) {
        if (d < 1.0F) return water;
        if (d < 1.2F) return water + 1;
        return d < SHORE ? Math.round(Mth.lerp(Terrain.smooth((d - 1.2F) / (SHORE - 1.2F)), water + 1, ground)) : ground;
    }

    /**
     * Turns to sandstone any water left standing on a column the lake leaves alone, as high as the shore beside it is cut
     * down below it, so none of it can pour out over the new ground.
     */
    private void seal(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, int ground) {
        int top = ground;
        while (top < b.maxY() && !level.getFluidState(pos.set(x, top + 1, z)).isEmpty()) top++;
        int low = top;
        for (int[] s : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) low = Math.min(low, shore(lake(x + s[0] + 0.5, z + s[1] + 0.5), top));
        for (int h = Math.max(ground, low) + 1; h <= top; h++) Terrain.set(level, pos, x, h, z, Blocks.SANDSTONE.defaultBlockState());
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState crust = ModBlocks.SALT_CRUST.get().defaultBlockState();
        BlockState pink = Blocks.PINK_TERRACOTTA.defaultBlockState();
        BlockState white = ModBlocks.GYPSUM_SAND.get().defaultBlockState();
        boolean wet = false;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float d = lake(x + 0.5, z + 0.5);
                if (d >= SEAL) continue;
                int ground = Terrain.ground(level, pos, x, z);
                // the lake and the bank round it are built however low the ground under them, so the water can't get out
                boolean bank = d < 1.2F;
                if (ground > b.maxY() - 14) continue;
                if (d >= SHORE) {
                    seal(level, pos, b, x, z, ground);
                    continue;
                }
                if (ground < b.minY() + 4 && !bank) continue;
                // water in the way is filled in first: any under the lake or its bank, a puddle further out (deeper water there is sealed in)
                int pond = 0;
                while (pond < (bank ? 24 : 4) && !level.getFluidState(pos.set(x, ground + 1 + pond, z)).isEmpty()) pond++;
                if (pond >= 4 && !bank || ground + pond > b.maxY() - 14) {
                    seal(level, pos, b, x, z, ground);
                    continue;
                }
                for (int h = Math.max(b.minY(), ground + 1); h <= ground + pond; h++) Terrain.set(level, pos, x, h, z, Blocks.SANDSTONE.defaultBlockState());
                ground += pond;
                float n = Terrain.noise(seed, x, water, z);
                // the beach thins out into the land round about in patches
                if (d >= 1.3F && n > 1.0F - Terrain.smooth((SHORE - d) / 0.2F) && shore(d, ground) == ground) continue;
                Terrain.clearAbove(level, pos, x, Math.max(ground, water), z, Math.min(12, b.maxY() - Math.max(ground, water)));
                if (d < 1.0F) {
                    // the lake: shallow, over a bed of salt crystals, pinkest where it laps at the shore
                    wet = true;
                    int bed = water - Math.max(1, Math.round(depth * Mth.sqrt(1.0F - d * d)));
                    for (int h = water + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    for (int h = Math.max(b.minY(), Math.min(ground + 1, bed - 2)); h < bed; h++) Terrain.set(level, pos, x, h, z, h == bed - 1 ? crust : Blocks.SANDSTONE.defaultBlockState());
                    BlockState floor = d > 0.85F || n < 0.2F ? pink : n < 0.85F ? crust : ModBlocks.ROCK_SALT.get().defaultBlockState();
                    Terrain.set(level, pos, x, bed, z, floor);
                    for (int h = bed + 1; h <= water; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                    continue;
                }
                // the shore, never lower than a block over the water next to it, and easing back into the land beyond
                int top = shore(d, ground);
                for (int h = top + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                // banked up with sand where the land dips, on a footing of sandstone so none of it can fall
                int bottom = Math.max(b.minY(), Math.min(ground, top) - 1);
                Terrain.set(level, pos, x, bottom, z, Blocks.SANDSTONE.defaultBlockState());
                for (int h = bottom + 1; h < top; h++) Terrain.set(level, pos, x, h, z, white);
                BlockState surface;
                if (d < 1.07F) {
                    surface = n < 0.75F ? pink : crust; // salt stained pink right at the waterline
                } else if (d < 1.3F) {
                    surface = Terrain.field(seed + 1, x, z, 4.0) < 0.55F ? crust : n < 0.3F ? ModBlocks.SHELL_SAND.get().defaultBlockState() : white;
                } else {
                    surface = n < 0.2F ? ModBlocks.SHELL_SAND.get().defaultBlockState() : white;
                }
                Terrain.set(level, pos, x, top, z, surface);
            }
        }
        // the algae and bacteria in the brine turn it pink
        if (wet) Terrain.setBiome(level, area, water - depth - 3, water + 3, (x, z) -> lake(x + 0.5, z + 0.5) < 1.08F, Terrain.PINK_LAKE_BIOME);
    }
}
