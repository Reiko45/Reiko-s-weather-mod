package com.reiko.weather.weather;

import com.reiko.weather.block.MeltwaterRillBlock;
import com.reiko.weather.block.RippledSandBlock;
import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.temperature.Temperature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;

/**
 * Small marks the weather leaves on open ground: snow lingering on shady slopes in a thaw, meltwater trickling down
 * the hillsides below the melting snow, and the wind combing dry sand into ripples.
 */
public final class ThawAndWind {
    private static final int AREA = 40;

    static void tickPlayer(ServerLevel level, ServerPlayer player, LocalWeather local, Climate climate) {
        RandomSource random = level.random;
        WeatherProfile p = local.profile();
        if (WeatherConfig.SAND_RIPPLES.get() && p.wind > 0.3F && p.rain < 0.05F) {
            for (int i = 0; i < 2; i++) ripple(level, column(level, player, random), local);
        }
        if (WeatherConfig.MELTWATER_RILLS.get() && random.nextInt(30) == 0) {
            BlockPos top = column(level, player, random);
            if (top != null && Temperature.ambient(level, top, local) > 2.0F) rill(level, top);
        }
        WindowFrost.tickPlayer(level, player, local);
    }

    @Nullable
    private static BlockPos column(ServerLevel level, ServerPlayer player, RandomSource random) {
        int x = player.getBlockX() + random.nextInt(AREA * 2 + 1) - AREA, z = player.getBlockZ() + random.nextInt(AREA * 2 + 1) - AREA;
        BlockPos c = new BlockPos(x, 0, z);
        return level.isLoaded(c) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, c) : null;
    }

    /** Dry, open sand in a steady wind ripples, the crests running across the wind. */
    private static void ripple(ServerLevel level, @Nullable BlockPos top, LocalWeather local) {
        if (top == null || !level.getBlockState(top).isAir()) return;
        BlockPos sand = top.below();
        BlockState s = level.getBlockState(sand);
        boolean red = s.is(Blocks.RED_SAND);
        if (!s.is(Blocks.SAND) && !red) return;
        // wet sand by the water doesn't blow
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (!level.getFluidState(sand.relative(d)).isEmpty() || !level.getFluidState(top.relative(d)).isEmpty()) return;
        }
        if (GroundEvents.built(level, sand, 2)) return;
        WeatherState state = WeatherManager.state(level);
        if (state == null) return;
        var block = red ? ModBlocks.RIPPLED_RED_SAND.get() : ModBlocks.RIPPLED_SAND.get();
        level.setBlockAndUpdate(sand, block.defaultBlockState().setValue(RippledSandBlock.AXIS, RippledSandBlock.crests(state.windAngle)));
    }

    /**
     * Starts a rill from melting snow on a slope: the meltwater runs off down the lie of the land over the bare
     * ground below the snow, for a dozen blocks or so.
     */
    private static void rill(ServerLevel level, BlockPos top) {
        if (!snowy(level, top) || level.getBiome(top).value().coldEnoughToSnow(top)) return;
        BlockPos at = top;
        Direction last = null;
        int placed = 0, flat = 0;
        for (int step = 0; step < 32 && placed < 14; step++) {
            // the steepest way down from here (keeping on the same way when it's as steep)
            Direction down = null;
            int best = Integer.MAX_VALUE;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int h = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, at.relative(d)).getY();
                if (h > at.getY() || h < at.getY() - 3) continue;
                if (h < best || h == best && d == last) {
                    best = h;
                    down = d;
                }
            }
            if (down == null) return;
            flat = best == at.getY() ? flat + 1 : 0;
            if (flat >= 3) return; // out on the level it soaks away
            last = down;
            at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, at.relative(down));
            if (snowy(level, at)) continue; // still running under the snow
            BlockPos floor = at.below();
            BlockState ground = level.getBlockState(floor), here = level.getBlockState(at);
            if (here.is(ModBlocks.MELTWATER_RILL.get()) || !level.getFluidState(floor).isEmpty() || !level.getFluidState(at).isEmpty()) return;
            if (!ground.isFaceSturdy(level, floor, Direction.UP) || !natural(ground)) return;
            if (!here.isAir() && !here.canBeReplaced()) return;
            if (placed == 0 && GroundEvents.built(level, floor, 3)) return;
            level.setBlockAndUpdate(at, ModBlocks.MELTWATER_RILL.get().defaultBlockState().setValue(MeltwaterRillBlock.FACING, down));
            placed++;
        }
    }

    private static boolean snowy(ServerLevel level, BlockPos top) {
        BlockState s = level.getBlockState(top), under = level.getBlockState(top.below());
        return s.is(Blocks.SNOW) || under.is(Blocks.SNOW) || under.is(Blocks.SNOW_BLOCK) || s.is(ModBlocks.DUSTY_SNOW.get());
    }

    private static boolean natural(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.GRAVEL) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.SAND);
    }

    /**
     * How fast the sun melts snow here, against snow on open, level ground: up to nearly twice as fast on a slope
     * facing the sun (south), much slower on a slope facing away, and slowest of all in the shadow of trees, cliffs
     * or walls to the south. At night, or under thick cloud, the sun makes no difference.
     */
    public static float sun(ServerLevel level, BlockPos top) {
        if (!WeatherConfig.SHADY_SNOW.get()) return 1.0F;
        long time = Math.floorMod(level.getDayTime(), 24000L);
        if (time < 1000L || time > 12000L) return 1.0F;
        float sunshine = Mth.clamp(1.0F - WeatherManager.local(level, top).profile().overcast * 1.3F, 0.0F, 1.0F);
        if (sunshine < 0.05F) return 1.0F;
        int x = top.getX(), z = top.getZ(), y = top.getY();
        int north = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z - 4);
        int south = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z + 4);
        // ground rising to the north faces south, into the sun
        float aspect = Mth.clamp((north - south) / 5.0F, -1.0F, 1.0F);
        float f = aspect >= 0 ? 1.0F + 0.8F * aspect : 1.0F + 0.75F * aspect;
        // a tree, cliff or wall just to the south throws its shadow over the snow (the low winter sun is about 30° up)
        for (int d = 1; d <= 6; d++) {
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z + d) > y + 1 + d * 0.6F) {
                f = Math.min(f, 0.2F);
                break;
            }
        }
        return Mth.lerp(sunshine, 1.0F, f);
    }

    private ThawAndWind() {}
}
