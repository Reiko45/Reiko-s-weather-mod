package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Small helpers shared by the landform builders. */
public final class Terrain {
    public static final int FLAGS = 2;

    /** Finished chunks keep the final heightmaps; chunks still being generated keep the "_WG" ones. */
    public static Heightmap.Types floor(WorldGenLevel level) {
        return level instanceof ServerLevel ? Heightmap.Types.OCEAN_FLOOR : Heightmap.Types.OCEAN_FLOOR_WG;
    }

    /** Whether a block is something growing (or lying) on the ground rather than the ground itself. */
    public static boolean cover(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.REPLACEABLE) || state.isAir()
                || state.is(BlockTags.FLOWERS) || state.is(Blocks.CACTUS) || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BAMBOO)
                || state.is(Blocks.SNOW) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON);
    }

    /** The y of the top block of real ground at a column: below any water, trees and plants. */
    public static int ground(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
        int y = level.getHeight(floor(level), x, z) - 1;
        int min = level.getMinBuildHeight();
        while (y > min) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            // through plants and trees, and through any water under them (as the heightmap does where there's no canopy)
            if (cover(state) || state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
                y--;
            } else {
                break;
            }
        }
        return y;
    }

    /** The top of any water standing on the ground in a column (the ground itself when there's none), up to 8 deep. */
    public static int surface(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int ground, int z) {
        int surface = ground;
        for (pos.set(x, ground + 1, z); surface < ground + 8 && !level.getFluidState(pos).isEmpty(); pos.move(net.minecraft.core.Direction.UP)) surface++;
        return surface;
    }

    /**
     * Whether water stands at height {@code h} in a column beside (x, z) that the landform never touches, as
     * {@code outside} says: lowering this column there would let that water out, so it should stay solid.
     */
    public static boolean pondBeside(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int h, int z,
                                     java.util.function.BiPredicate<Integer, Integer> outside) {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            int nx = x + d.getStepX(), nz = z + d.getStepZ();
            if (outside.test(nx, nz) && !level.getFluidState(pos.set(nx, h, nz)).isEmpty()) return true;
        }
        return false;
    }

    /** Clears trees, plants and snow from a column above {@code y}, up to {@code height} blocks (leaves water alone). */
    public static void clearAbove(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, int height) {
        int max = Math.min(level.getMaxBuildHeight() - 1, y + height);
        for (int h = y + 1; h <= max; h++) {
            pos.set(x, h, z);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && cover(state) && state.getFluidState().isEmpty()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            }
        }
    }

    public static void set(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState state) {
        pos.set(x, y, z);
        level.setBlock(pos, state, FLAGS);
    }

    /** Sets a block only if it's inside {@code area} (the chunk being built): for things that reach across chunks, like trees. */
    public static void setIn(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState state) {
        if (area.isInside(x, y, z)) {
            set(level, pos, x, y, z, state);
        }
    }

    /** Whether a column is inside the area being built. */
    public static boolean column(BoundingBox area, int x, int z) {
        return x >= area.minX() && x <= area.maxX() && z >= area.minZ() && z <= area.maxZ();
    }

    /** A smooth 0-1 value-noise field (bilinear between a grid of {@code scale}-block cells). */
    public static float field(long seed, double x, double z, double scale) {
        double gx = x / scale, gz = z / scale;
        int x0 = Mth.floor(gx), z0 = Mth.floor(gz);
        float fx = smooth((float) (gx - x0)), fz = smooth((float) (gz - z0));
        float a = noise(seed, x0, 0, z0), b = noise(seed, x0 + 1, 0, z0), c = noise(seed, x0, 0, z0 + 1), d = noise(seed, x0 + 1, 0, z0 + 1);
        return Mth.lerp(fz, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d));
    }

    /** A point's position along ({@code u}) and across ({@code v}) a direction from a centre. */
    public static float[] uv(double x, double z, double cx, double cz, float angle) {
        double dx = x - cx, dz = z - cz, c = Math.cos(angle), s = Math.sin(angle);
        return new float[]{(float) (dx * c + dz * s), (float) (-dx * s + dz * c)};
    }

    /** The world position {@code u} along and {@code v} across a direction from a centre. */
    public static double[] xz(double u, double v, double cx, double cz, float angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        return new double[]{cx + u * c - v * s, cz + u * s + v * c};
    }

    /** A repeatable 0-1 value for a position. */
    public static float noise(long seed, int x, int y, int z) {
        long h = Mth.getSeed(x, y, z) ^ seed;
        h = h * 6364136223846793005L + 1442695040888963407L;
        return ((h >>> 40) & 0xFFFF) / 65535.0F;
    }

    public static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** This mod's own biome for glacial lakes: it only exists to colour their water turquoise with rock flour. */
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> GLACIAL_LAKE_BIOME =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, com.reiko.weather.ReikosWeather.id("glacial_lake"));

    /** Biomes that colour lake water: pink salt lakes, milky turquoise acid crater lakes and yellow-green acid pools. */
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> PINK_LAKE_BIOME =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, com.reiko.weather.ReikosWeather.id("pink_lake"));
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> ACID_LAKE_BIOME =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, com.reiko.weather.ReikosWeather.id("acid_lake"));
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> ACID_POOL_BIOME =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, com.reiko.weather.ReikosWeather.id("acid_pool"));
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> BRINE_POOL_BIOME =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, com.reiko.weather.ReikosWeather.id("brine_pool"));
    /** Crystal-clear blue water (rift fissures, fairy pools). */
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> CLEAR_WATER_BIOME =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, com.reiko.weather.ReikosWeather.id("clear_water"));
    /** A cloud forest: cool, dripping jungle wrapped in mist (the client draws the mist here). */
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> CLOUD_FOREST_BIOME =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, com.reiko.weather.ReikosWeather.id("cloud_forest"));
    /** The deep, vivid azure of a blue spring. */
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> BLUE_SPRING_BIOME =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, com.reiko.weather.ReikosWeather.id("blue_spring"));
    /** A boiling river: milky green-blue water, steaming hot (the client draws the steam, and the water scalds). */
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> BOILING_RIVER_BIOME =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, com.reiko.weather.ReikosWeather.id("boiling_river"));

    /**
     * Repaints the biome over part of the chunk being built: every 4x4x4 biome cell between {@code minY} and
     * {@code maxY} whose middle column passes {@code inside}. Biomes set the colour of water and grass, so this is how
     * a landform gives a lake its own colour.
     */
    public static void setBiome(WorldGenLevel level, BoundingBox area, int minY, int maxY, java.util.function.BiPredicate<Integer, Integer> inside,
                                net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> key) {
        var holder = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME).getHolder(key).orElse(null);
        if (holder == null) return;
        net.minecraft.world.level.chunk.ChunkAccess chunk = level.getChunk(area.minX() >> 4, area.minZ() >> 4);
        chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> {
            int x = net.minecraft.core.QuartPos.toBlock(qx) + 2, y = net.minecraft.core.QuartPos.toBlock(qy) + 2, z = net.minecraft.core.QuartPos.toBlock(qz) + 2;
            return y >= minY && y <= maxY && column(area, x, z) && inside.test(x, z) ? holder : chunk.getNoiseBiome(qx, qy, qz);
        }, null);
    }

    /** Whether landforms may lay down quicksand (they do while the config is still loading, during world creation). */
    public static boolean quicksand() {
        return !com.reiko.weather.config.WeatherConfig.SPEC.isLoaded() || com.reiko.weather.config.WeatherConfig.QUICKSAND.get();
    }

    /** A repeatable 0-1 value for the i-th thing a landform places. */
    public static float hash(long seed, int i) {
        int h = Mth.murmurHash3Mixer((int) (seed ^ seed >>> 32) + i * 0x9E3779B9);
        return (h >>> 8) / (float) (1 << 24);
    }

    private Terrain() {}
}
