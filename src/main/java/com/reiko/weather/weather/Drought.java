package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Droughts. After a dry spell (mostly in summer) the weather can turn against rain for days on end: the
 * forecast stays dry, shallow ponds shrink to cracked mud, the grass browns, crops wilt and a stray spark
 * sets the countryside alight. In the end it breaks with a storm, and the rain fills the ponds again.
 */
public final class Drought {
    /** A day only counts as wet with a couple of hours of proper rain, not one passing shower. */
    private static final int WET_DAY = 4800;
    private static final int MAX_DRIED = 4096;
    private static final int MAX_POND = 48;

    /** 0 to 1: how bad the drought is (it worsens over its first three days). */
    public static float severity(WeatherState state) {
        if (state.droughtDaysLeft <= 0 || !WeatherConfig.SPEC.isLoaded() || !WeatherConfig.DROUGHTS.get()) {
            return 0.0F;
        }
        int into = state.droughtLength - state.droughtDaysLeft + 1;
        return Mth.clamp(into / 3.0F, 0.0F, 1.0F);
    }

    /** Counts the rain every tick, and takes stock at the end of each day. */
    static void tick(ServerLevel level, WeatherState state, boolean cycle) {
        if (state.type.vanillaRain && state.type != WeatherType.SNOW && state.type != WeatherType.BLIZZARD) {
            state.rainToday++;
        } else if (level.getGameTime() % 100L == 0L && rainingOnAnyone(level, state)) {
            state.rainToday += 100; // a storm cell passing over (it rains where the players are, which is what counts)
        }
        long day = level.getDayTime() / 24000L;
        if (state.droughtCheckDay < 0 || day < state.droughtCheckDay) {
            state.droughtCheckDay = day;
            return;
        }
        if (day == state.droughtCheckDay) {
            return;
        }
        state.droughtCheckDay = day;
        endOfDay(level, state, cycle);
        Coasts.weatherSilt(level, state);
        state.rainToday = 0;
        state.setDirty();
    }

    private static boolean rainingOnAnyone(ServerLevel level, WeatherState state) {
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            LocalWeather local = WeatherManager.local(level, player.blockPosition());
            if (local != null && local.profile().rain > 0.5F && local.type().vanillaRain) return true;
        }
        return false;
    }

    private static void endOfDay(ServerLevel level, WeatherState state, boolean cycle) {
        boolean wet = state.rainToday > WET_DAY;
        if (!WeatherConfig.DROUGHTS.get() || !cycle) {
            state.droughtDaysLeft = 0;
            state.dryDays = wet ? 0 : state.dryDays + 1;
            return;
        }
        if (state.droughtDaysLeft > 0) {
            state.droughtDaysLeft--;
            if (state.rainToday > WET_DAY * 2) {
                state.droughtDaysLeft = 0; // a real soaking ends it early
            }
            if (state.droughtDaysLeft == 0) {
                end(level, state);
            }
            WeatherManager.sync(level, state);
            return;
        }
        state.dryDays = wet ? 0 : state.dryDays + 1;
        Season season = WeatherManager.seasonInfo().season();
        float chance = season == null ? 0.05F : switch (season) {
            case SUMMER -> 0.1F;
            case SPRING, AUTUMN -> 0.03F;
            case WINTER -> 0.0F;
        };
        chance *= ClimateYears.droughtMultiplier(level); // dry years bring more droughts, wet years fewer
        if (state.dryDays >= 1 && level.random.nextFloat() < chance * Math.min(state.dryDays, 3)) {
            start(level, state, 4 + level.random.nextInt(5));
        }
    }

    /** Starts a drought lasting {@code days} days. */
    public static void start(ServerLevel level, WeatherState state, int days) {
        state.droughtLength = state.droughtDaysLeft = Math.max(1, days);
        state.rainToday = 0; // only rain from now on can break it
        state.setDirty();
        WeatherManager.sync(level, state);
    }

    /** The drought breaks: a storm is on its way. */
    public static void end(ServerLevel level, WeatherState state) {
        boolean wasOn = state.droughtDaysLeft > 0 || state.droughtLength > 0;
        state.droughtDaysLeft = 0;
        state.droughtLength = 0;
        state.dryDays = 0;
        if (wasOn && level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE) && !state.type.vanillaRain) {
            WeatherType storm = level.random.nextBoolean() ? WeatherType.THUNDERSTORM : WeatherType.RAIN;
            state.forecast.add(0, new WeatherState.Forecast(storm, Math.max(200, (int) (storm.randomDuration(level.random)
                    * WeatherConfig.DURATION_MULTIPLIER.get()))));
            while (state.forecast.size() > 3) state.forecast.remove(state.forecast.size() - 1);
            state.remaining = Math.min(state.remaining, 1800);
        }
        state.setDirty();
        WeatherManager.sync(level, state);
    }

    // ------------------------------------------------------------------ out in the countryside

    /** Per player tick (from the hazards): ponds drying up in the sun, and refilling once the rain comes back. */
    static void tickPlayer(ServerLevel level, WeatherState state, ServerPlayer player, LocalWeather local, Climate climate, @Nullable BlockPos column) {
        float severity = severity(state);
        if (player.tickCount % 20 == 0 && severity > 0.0F && level.getBlockState(player.blockPosition().below()).is(ModBlocks.CRACKED_MUD.get())) {
            WeatherAdvancements.award(player, "parched");
        }
        if (severity <= 0.3F || climate == Climate.COLD || !level.isDay() || local.profile().rain > 0.1F
                || !WeatherConfig.DRYING_PONDS.get() || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        if (column != null && level.random.nextFloat() < 0.2F * severity) {
            dryPond(level, state, column);
        }
    }

    /** Every few ticks: refill some of the dried-up ponds where it's raining again (and the drought is over). */
    static void refill(ServerLevel level, WeatherState state) {
        if (state.driedPonds.isEmpty() || level.getGameTime() % 5L != 2L || severity(state) > 0.0F) {
            return;
        }
        RandomSource random = level.random;
        for (int i = 0; i < 6 && !state.driedPonds.isEmpty(); i++) {
            int index = random.nextInt(state.driedPonds.size());
            BlockPos pos = BlockPos.of(state.driedPonds.getLong(index));
            if (!level.isLoaded(pos)) continue;
            LocalWeather local = WeatherManager.local(level, pos);
            boolean raining = local != null && local.type().vanillaRain && local.profile().rain > 0.3F;
            if (!raining && WeatherConfig.DROUGHTS.get()) continue;
            BlockPos bed = pos.below();
            BlockState bedState = level.getBlockState(bed);
            if (bedState.is(ModBlocks.CRACKED_MUD.get())) {
                level.setBlock(bed, Blocks.MUD.defaultBlockState(), Block.UPDATE_ALL);
            }
            BlockState at = level.getBlockState(pos);
            boolean flowing = at.getFluidState().is(FluidTags.WATER) && !at.getFluidState().isSource(); // water running in from the bit already refilled
            if ((at.isAir() || flowing || at.is(BlockTags.REPLACEABLE) && at.getFluidState().isEmpty()) && !level.getBlockState(bed).isAir()) {
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            }
            state.driedPonds.removeLong(index);
            state.setDirty();
        }
    }

    /**
     * Dries up a shallow pond: a small body of water only one block deep all over (the shallows of a bigger
     * lake don't count, and nor does water by farmland, which is someone's irrigation). The bed bakes into
     * cracked mud.
     */
    public static boolean dryPond(ServerLevel level, WeatherState state, BlockPos top) {
        BlockPos start = top.below();
        if (state.driedPonds.size() >= MAX_DRIED || !level.getFluidState(start).is(FluidTags.WATER) || !level.canSeeSky(top)) {
            return false;
        }
        Holder<Biome> biome = level.getBiome(start);
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)) {
            return false;
        }
        List<BlockPos> pond = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            if (!level.isLoaded(p)) return false;
            BlockPos below = p.below();
            BlockState under = level.getBlockState(below);
            if (!under.getFluidState().isEmpty() || !under.isFaceSturdy(level, below, Direction.UP) || !level.getBlockState(p.above()).isAir()) {
                return false; // deeper than a block, or iced over, or something odd
            }
            pond.add(p);
            if (pond.size() > MAX_POND) return false;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(d);
                if (seen.add(n) && level.getFluidState(n).is(FluidTags.WATER)) queue.add(n);
            }
        }
        for (BlockPos p : pond) {
            for (BlockPos f : BlockPos.betweenClosed(p.offset(-4, -1, -4), p.offset(4, 1, 4))) {
                if (level.getBlockState(f).is(Blocks.FARMLAND)) return false;
            }
        }
        for (BlockPos p : pond) {
            boolean source = level.getFluidState(p).isSourceOfType(Fluids.WATER);
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            BlockState bed = level.getBlockState(p.below());
            if (bed.is(BlockTags.DIRT) || bed.is(Blocks.MUD) || bed.is(Blocks.CLAY) || bed.is(Blocks.SAND) || bed.is(Blocks.GRAVEL)
                    || bed.is(Blocks.COARSE_DIRT)) {
                level.setBlock(p.below(), ModBlocks.CRACKED_MUD.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            if (source) state.driedPonds.add(p.asLong());
        }
        for (BlockPos p : pond) {
            level.blockUpdated(p, Blocks.AIR); // let the neighbours know only once it's all gone
        }
        state.setDirty();
        return true;
    }

    /** "Drought: day 3 of 6" for reports, or null. */
    @Nullable
    public static Component describe(WeatherState state) {
        if (severity(state) <= 0.0F) return null;
        return Component.translatable("report.reikos_weather.drought", state.droughtLength - state.droughtDaysLeft + 1, state.droughtLength);
    }

    private Drought() {}
}
