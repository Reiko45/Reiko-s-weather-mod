package com.reiko.weather.landform;

import com.reiko.weather.ReikosWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;

import javax.annotation.Nullable;

/**
 * A wide, flat stretch of mud at the edge of the sea, level with the water. High tide washes over it; at low
 * tide it lies bare, dotted with tide pools of seagrass and sea pickles, with treasures buried in their sand.
 */
public record TidalFlat(int cx, int cz, int rx, int rz, float angle, int level, long seed) implements Landform {
    public static final ResourceKey<LootTable> TIDE_POOL_LOOT = ResourceKey.create(Registries.LOOT_TABLE, ReikosWeather.id("archaeology/tide_pool"));

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        int sea = c.seaLevel();
        if (ground < sea - 4 || ground > sea + 2) {
            return null;
        }
        boolean ocean = false;
        for (int i = 0; i < 8 && !ocean; i++) {
            double a = i * Math.PI / 4.0;
            ocean = c.groundAt(c.x() + (int) Math.round(Math.cos(a) * 50), c.z() + (int) Math.round(Math.sin(a) * 50)) < sea - 3;
        }
        if (!ocean) {
            return null;
        }
        return new TidalFlat(c.x(), c.z(), 30 + c.random().nextInt(23), 20 + c.random().nextInt(15), c.random().nextFloat() * Mth.TWO_PI,
                sea - 1, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new TidalFlat(tag.getInt("x"), tag.getInt("z"), tag.getInt("rx"), tag.getInt("rz"), tag.getFloat("angle"), tag.getInt("level"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("rx", rx);
        tag.putInt("rz", rz);
        tag.putFloat("angle", angle);
        tag.putInt("level", level);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.TIDAL_FLAT;
    }

    @Override
    public BoundingBox bounds() {
        int r = (int) Math.ceil(Math.max(rx, rz) * 1.25) + 1;
        return new BoundingBox(cx - r, level - 10, cz - r, cx + r, level + 24, cz + r);
    }

    private float ellipse(double x, double z) {
        double dx = x - cx, dz = z - cz, c = Math.cos(angle), s = Math.sin(angle);
        double u = dx * c + dz * s, v = -dx * s + dz * c;
        return (float) (u * u / (rx * rx) + v * v / (rz * rz));
    }

    /** Tide pools as {x, z, radius}. */
    private float[][] pools() {
        int count = 4 + (int) (Terrain.hash(seed, 0) * 4);
        float[][] pools = new float[count][];
        double c = Math.cos(angle), s = Math.sin(angle);
        for (int i = 0; i < count; i++) {
            float a = Terrain.hash(seed, 10 + i) * Mth.TWO_PI, r = Mth.sqrt(Terrain.hash(seed, 20 + i)) * 0.8F;
            double u = Mth.cos(a) * r * rx, v = Mth.sin(a) * r * rz;
            pools[i] = new float[]{(float) (cx + u * c - v * s), (float) (cz + u * s + v * c), 1.5F + 1.3F * Terrain.hash(seed, 30 + i)};
        }
        return pools;
    }

    @Override
    public void build(WorldGenLevel world, BoundingBox area) {
        BoundingBox b = bounds();
        float[][] pools = pools();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float e = ellipse(x + 0.5, z + 0.5);
                if (e > 1.5F) continue;
                int ground = Terrain.ground(world, pos, x, z);
                if (e > 1.0F) {
                    // blend the land down to the flats (the seaward side just drops away into the sea)
                    if (ground > level) {
                        int target = Math.round(Mth.lerp(Terrain.smooth((Mth.sqrt(e) - 1.0F) / 0.22F), level, ground));
                        Terrain.clearAbove(world, pos, x, target, z, 12);
                        for (int h = target + 1; h <= ground; h++) Terrain.set(world, pos, x, h, z, Blocks.AIR.defaultBlockState());
                        Terrain.set(world, pos, x, target, z, Blocks.SAND.defaultBlockState());
                    }
                    continue;
                }
                Terrain.clearAbove(world, pos, x, Math.min(ground, level), z, 16);
                for (int h = level + 1; h <= ground; h++) Terrain.set(world, pos, x, h, z, Blocks.AIR.defaultBlockState());
                float n = Terrain.noise(seed, x, 0, z);
                int surface = level;
                float[] pool = null;
                for (float[] p : pools) {
                    float d = (float) Mth.length(x + 0.5F - p[0], z + 0.5F - p[1]);
                    if (d < p[2]) {
                        pool = p;
                        surface = level - (d < p[2] * 0.5F ? 2 : 1);
                        break;
                    }
                }
                for (int h = Math.min(ground, surface) - 2; h < surface; h++) {
                    Terrain.set(world, pos, x, h, z, h < surface - 1 ? Blocks.SAND.defaultBlockState() : Blocks.MUD.defaultBlockState());
                }
                if (pool == null) {
                    // mostly mud, with sandbanks and the odd patch of clay (in patches, not speckles)
                    float patch = Terrain.noise(seed, x >> 2, 3, z >> 2);
                    Terrain.set(world, pos, x, surface, z, patch < 0.72F || n < 0.2F ? Blocks.MUD.defaultBlockState()
                            : patch < 0.93F ? Blocks.SAND.defaultBlockState() : Blocks.CLAY.defaultBlockState());
                    // soft spots in the wet sand that won't take your weight
                    if (Terrain.quicksand() && patch >= 0.72F && patch < 0.93F && Terrain.field(seed + 9, x, z, 3.0) > 0.78F) {
                        Terrain.set(world, pos, x, surface, z, com.reiko.weather.registry.ModBlocks.QUICKSAND.get().defaultBlockState());
                        Terrain.set(world, pos, x, surface - 1, z, com.reiko.weather.registry.ModBlocks.QUICKSAND.get().defaultBlockState());
                    }
                    continue;
                }
                // a tide pool: sand at the bottom (sometimes hiding something), seagrass and sea pickles in the water
                float d = (float) Mth.length(x + 0.5F - pool[0], z + 0.5F - pool[1]);
                boolean buried = d < 0.7F;
                Terrain.set(world, pos, x, surface, z, buried ? Blocks.SUSPICIOUS_SAND.defaultBlockState() : Blocks.SAND.defaultBlockState());
                if (buried && world.getBlockEntity(pos.set(x, surface, z)) instanceof BrushableBlockEntity brushable) {
                    brushable.setLootTable(TIDE_POOL_LOOT, seed ^ BlockPos.asLong(x, surface, z));
                }
                for (int h = surface + 1; h <= level; h++) {
                    if (h == surface + 1 && n < 0.35F) {
                        Terrain.set(world, pos, x, h, z, Blocks.SEAGRASS.defaultBlockState());
                    } else if (h == surface + 1 && n > 0.88F) {
                        Terrain.set(world, pos, x, h, z, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + (int) (n * 30) % 3));
                    } else {
                        Terrain.set(world, pos, x, h, z, Blocks.WATER.defaultBlockState());
                    }
                }
            }
        }
    }
}
