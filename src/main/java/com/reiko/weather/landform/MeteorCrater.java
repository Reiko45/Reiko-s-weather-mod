package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * An old impact crater: a round, weathered bowl with a raised rim, grassed over by now (sometimes with a
 * pond in the bottom), and the meteorite itself buried a few blocks under the middle.
 */
public record MeteorCrater(int cx, int cz, int y, int radius, int depth, boolean pond, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        int radius = 10 + c.random().nextInt(10);
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 1 || c.relief(Math.round(radius * 1.5F), 8) > 10 || !c.fits(ground, 20, 20)) {
            return null;
        }
        int sum = ground;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            sum += c.groundAt(c.x() + (int) Math.round(Math.cos(a) * radius), c.z() + (int) Math.round(Math.sin(a) * radius));
        }
        return new MeteorCrater(c.x(), c.z(), Math.round(sum / 9.0F), radius, Math.max(4, Math.round(radius * 0.38F)),
                c.random().nextFloat() < 0.35F, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new MeteorCrater(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getInt("depth"),
                tag.getBoolean("pond"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.putInt("depth", depth);
        tag.putBoolean("pond", pond);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.METEOR_CRATER;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * 1.7F) + 1;
        return new BoundingBox(cx - r, y - depth - 12, cz - r, cx + r, y + 24, cz + r);
    }

    /** The crater floor's height at a distance from the middle (and the rim's, outside it). */
    public float profile(float r) {
        if (r < radius) {
            float t = r / radius;
            return y - depth * (1.0F - t * t);
        }
        float rim = (r - radius) / (radius * 0.3F);
        return y + depth * 0.35F * (float) Math.exp(-rim * rim);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int floor = y - depth;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float r = (float) Mth.length(x - cx, z - cz);
                if (r > radius * 1.7F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                BlockState natural = level.getBlockState(pos.set(x, ground, z));
                if (!natural.is(BlockTags.DIRT) && !natural.is(BlockTags.SAND) && !natural.is(Blocks.SNOW_BLOCK) && !natural.is(Blocks.GRAVEL)) {
                    natural = Blocks.GRASS_BLOCK.defaultBlockState();
                }
                float edge = r < radius ? 1.0F : 1.0F - Terrain.smooth((r - radius) / (radius * 0.7F));
                float bump = (Terrain.noise(seed, x, 1, z) - 0.5F) * 1.2F; // weathered, not perfectly smooth
                int target = Math.round(Mth.lerp(edge, ground, profile(r) + bump));
                Terrain.clearAbove(level, pos, x, Math.min(ground, target), z, 20);
                for (int h = target + 1; h <= ground; h++) {
                    Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                }
                for (int h = Math.min(ground, target) - 1; h <= target; h++) {
                    BlockState s = h == target ? natural : target - h <= 3 ? (natural.is(BlockTags.SAND) ? natural : Blocks.DIRT.defaultBlockState())
                            : Blocks.STONE.defaultBlockState();
                    Terrain.set(level, pos, x, h, z, s);
                }
                if (pond && r < radius * 0.33F && target < floor + 2) {
                    Terrain.set(level, pos, x, target, z, Blocks.CLAY.defaultBlockState());
                    for (int h = target + 1; h <= floor + 2; h++) {
                        Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                    }
                }
                // shattered, melted rock under the crater, and the meteorite in the middle of it
                if (r < 4.0F) {
                    for (int h = floor - 6; h < Math.min(target, floor - 1); h++) {
                        float n = Terrain.noise(seed, x, h, z);
                        Terrain.set(level, pos, x, h, z, n < 0.4F ? Blocks.GRAVEL.defaultBlockState() : n < 0.75F ? Blocks.TUFF.defaultBlockState()
                                : Blocks.BLACKSTONE.defaultBlockState());
                    }
                }
                int mx = x - cx, mz = z - cz;
                if (Math.abs(mx) + Math.abs(mz) <= 1) {
                    for (int dy = 0; dy <= (mx == 0 && mz == 0 ? 2 : 1); dy++) {
                        Terrain.set(level, pos, x, floor - 4 + dy, z, ModBlocks.METEORITE.get().defaultBlockState());
                    }
                } else if (r < radius && Terrain.noise(seed, x, 2, z) < 0.006F) {
                    Terrain.set(level, pos, x, target - 1, z, ModBlocks.METEORITE.get().defaultBlockState()); // a stray fragment
                }
            }
        }
    }
}
