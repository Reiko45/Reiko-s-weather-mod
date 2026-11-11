package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.volcano.MassFlows;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;

/**
 * What heavy rain and storms leave behind: bog bursts on sodden hillsides, shallow lakes on dry lakebeds, and lines
 * of kelp and seagrass thrown up along the beaches.
 */
public final class StormWake {
    private static final int AREA = 40;
    private static long lastStorm = Long.MIN_VALUE / 2, lastBurst = Long.MIN_VALUE / 2;

    static void tickPlayer(ServerLevel level, ServerPlayer player, LocalWeather local, Climate climate) {
        RandomSource random = level.random;
        boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        boolean heavyRain = local.type().vanillaRain && local.profile().rain > 0.6F && local.intensity() > 0.5F;
        long now = level.getGameTime();
        if (WeatherConfig.BOG_BURSTS.get() && griefing && heavyRain && player.tickCount % 20 == 0 && now - lastBurst > 6000L) {
            bogBurst(level, player, random);
        }
        if (WeatherConfig.PLAYA_LAKES.get() && heavyRain) {
            for (int i = 0; i < 4; i++) flood(level, player, column(level, player, random), climate);
        }
        if (WeatherConfig.WRACK_LINES.get() && griefing) {
            if (local.type().isStorm() || local.profile().wind > 0.6F) lastStorm = now;
            // the beaches are strewn for a day or so after a storm
            if (now - lastStorm < 24000L && random.nextInt(3) == 0) wrack(level, column(level, player, random));
        }
    }

    @Nullable
    private static BlockPos column(ServerLevel level, ServerPlayer player, RandomSource random) {
        int x = player.getBlockX() + random.nextInt(AREA * 2 + 1) - AREA, z = player.getBlockZ() + random.nextInt(AREA * 2 + 1) - AREA;
        BlockPos c = new BlockPos(x, 0, z);
        return level.isLoaded(c) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c) : null;
    }

    /**
     * A bog burst: after days of rain a blanket bog on a hillside soaks up more water than it can hold, the peat
     * tears away from the ground underneath, and the whole sodden mass slides downhill as a slow black river,
     * burying the land in its path.
     */
    private static void bogBurst(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int i = 0; i < 6; i++) {
            BlockPos top = column(level, player, random);
            // (it takes rain: snow just piles up on the bog)
            if (top == null || !level.getBlockState(top.below()).is(ModBlocks.PEAT.get()) || level.getBiome(top).value().coldEnoughToSnow(top)) continue;
            // only on a slope: which way is steepest down, and how far
            int here = top.getY(), drop = 0;
            float angle = 0.0F;
            for (int k = 0; k < 8; k++) {
                float a = k * Mth.TWO_PI / 8.0F;
                BlockPos c = BlockPos.containing(top.getX() + Mth.cos(a) * 10, 0, top.getZ() + Mth.sin(a) * 10);
                if (!level.isLoaded(c)) continue;
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
                if (here - h > drop) {
                    drop = here - h;
                    angle = a;
                }
            }
            if (drop < 3 || random.nextInt(60) != 0) continue;
            lastBurst = level.getGameTime();
            MassFlows.start(level, MassFlows.Kind.BOG, top.getX() + 0.5, top.getZ() + 0.5, Mth.cos(angle), Mth.sin(angle));
            return;
        }
    }

    /** Ground a shallow lake can spread over after heavy rain: the flat, hard floor of a dry lakebed. */
    public static boolean lakebed(BlockState s) {
        // (cracked mud softens back to mud in the rain, and the lake still lies on it)
        return s.is(ModBlocks.CRACKED_MUD.get()) || s.is(Blocks.MUD) || s.is(Blocks.WHITE_TERRACOTTA) || s.is(Blocks.PACKED_MUD) || s.is(ModBlocks.ROCK_SALT.get());
    }

    /**
     * A playa lake: rain hardly ever falls on a desert lakebed, but when a big storm does come the water has nowhere
     * to soak away, and a sheet of water a hand deep spreads right across the flat floor, mirroring the sky, before
     * it slowly dries out again.
     */
    private static void flood(ServerLevel level, ServerPlayer player, @Nullable BlockPos top, Climate climate) {
        if (top == null) return;
        BlockState at = level.getBlockState(top);
        BlockPos floor = top.below();
        if (!lakebed(level.getBlockState(floor)) || !(at.isAir() || at.is(ModBlocks.SALT_CRUST.get())) || level.getBiome(top).value().coldEnoughToSnow(top)) return;
        // only on the flat: the water would just run off a slope
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos side = floor.relative(d);
            if (!level.getBlockState(side).isFaceSturdy(level, side, Direction.UP) || !level.getBlockState(side.above()).canBeReplaced()
                    && !level.getBlockState(side.above()).is(ModBlocks.PUDDLE.get())) return;
        }
        level.setBlockAndUpdate(top, ModBlocks.PUDDLE.get().defaultBlockState());
        if (climate == Climate.ARID) WeatherAdvancements.award(player, "playa_lake");
    }

    /** Kelp and seagrass ripped up by the storm and thrown along the top of the beach. */
    private static void wrack(ServerLevel level, @Nullable BlockPos top) {
        if (top == null || !level.getBlockState(top).isAir()) return;
        int sea = level.getSeaLevel();
        BlockPos ground = top.below();
        BlockState g = level.getBlockState(ground);
        if (ground.getY() < sea - 1 || ground.getY() > sea + 1 || !(g.is(Blocks.SAND) || g.is(Blocks.GRAVEL) || g.is(ModBlocks.SHELL_SAND.get())
                || g.is(ModBlocks.BLACK_SAND.get()))) return;
        // just above the water's edge, on a sea beach
        boolean shore = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int r = 1; r <= 3 && !shore; r++) {
                BlockPos w = new BlockPos(ground.getX() + d.getStepX() * r, sea - 1, ground.getZ() + d.getStepZ() * r);
                shore = level.getFluidState(w).is(FluidTags.WATER) && (level.getBiome(w).is(BiomeTags.IS_OCEAN) || level.getBiome(top).is(BiomeTags.IS_BEACH));
            }
        }
        if (!shore) return;
        level.setBlockAndUpdate(top, ModBlocks.BEACH_WRACK.get().defaultBlockState());
    }

    public static void reset() {
        lastStorm = lastBurst = Long.MIN_VALUE / 2;
    }

    private StormWake() {}
}
