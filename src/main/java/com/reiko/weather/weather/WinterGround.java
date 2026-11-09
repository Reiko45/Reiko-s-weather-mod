package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.registry.ModSounds;
import com.reiko.weather.temperature.Temperature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;

/**
 * What winter does to the ground: snow building up over the season into a deep snowpack (deepest high up and in
 * the coldest places) and melting away again in spring, rain on deep snow running straight off into the rivers,
 * needle ice pushing up out of frozen soil on frosty mornings, frost flowers on new ice, cornices overhanging
 * windswept ridges, and wind carving open snowfields into ridges and furrows (sastrugi).
 */
public final class WinterGround {
    private static final int AREA = 40;

    static void tickPlayer(ServerLevel level, WeatherState state, ServerPlayer player, LocalWeather local, Climate climate) {
        if (climate == Climate.ARID) return;
        RandomSource random = level.random;
        WeatherProfile p = local.profile();
        float intensity = local.intensity();
        Season season = WeatherManager.seasonInfo().season();
        boolean winter = season == Season.WINTER || season == null && climate == Climate.COLD;
        boolean snowing = p.snowLayers > 0 && intensity > 0.2F;
        boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);

        if (WeatherConfig.SNOWPACK.get()) {
            if (snowing && winter) {
                for (int i = 0; i < 3; i++) deepen(level, column(level, player, random), climate);
            } else if (season == Season.SPRING || !winter && random.nextInt(3) == 0) {
                thaw(level, column(level, player, random), local);
            }
            // rain on deep snow: it melts fast and the water runs straight off
            if (local.type().vanillaRain && !snowing && p.rain > 0.5F && intensity > 0.6F) {
                for (int i = 0; i < 3; i++) rainOnSnow(level, column(level, player, random));
            }
        }
        long time = Math.floorMod(level.getDayTime(), 24000L);
        boolean frostyMorning = (time > 22500L || time < 1500L) && p.rain < 0.1F && p.wind * intensity < 0.4F;
        if (WeatherConfig.FROST_HEAVE.get() && frostyMorning && random.nextInt(3) == 0) {
            float t = Temperature.ambient(level, player.blockPosition(), local);
            if (t < 1.0F && t > -9.0F) needleIce(level, column(level, player, random), random);
            if (t < -6.0F) frostFlowers(level, column(level, player, random));
        }
        float wind = p.wind * intensity;
        if (WeatherConfig.CORNICES.get() && griefing && snowing && wind > 0.5F && random.nextInt(4) == 0) {
            buildCornice(level, state, column(level, player, random));
        }
        if (WeatherConfig.CORNICES.get() && griefing) {
            checkCornice(level, player, local);
        }
        if (WeatherConfig.SASTRUGI.get() && wind > 0.55F && p.rain < 0.6F && climate == Climate.COLD) {
            for (int i = 0; i < 4; i++) sastrugi(level, state, column(level, player, random));
        }
    }

    @Nullable
    private static BlockPos column(ServerLevel level, ServerPlayer player, RandomSource random) {
        int x = player.getBlockX() + random.nextInt(AREA * 2 + 1) - AREA, z = player.getBlockZ() + random.nextInt(AREA * 2 + 1) - AREA;
        BlockPos c = new BlockPos(x, 0, z);
        return level.isLoaded(c) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, c) : null;
    }

    /** How deep (in snow layers) the snowpack grows here by midwinter: deeper up high and in the cold. */
    public static int targetDepth(ServerLevel level, BlockPos pos, Climate climate) {
        int altitude = Mth.clamp((pos.getY() - level.getSeaLevel() - 20) / 12, 0, 6);
        int depth = (climate == Climate.COLD ? 5 : 2) + altitude;
        // sheltered among trees the snow lies deeper and drifts less
        if (level.getBlockState(pos.above(3)).is(BlockTags.LEAVES) || level.getBlockState(pos.above(4)).is(BlockTags.LEAVES)) depth += 2;
        return Mth.clamp(depth, 1, 16);
    }

    /** Snow builds up through the winter beyond what one snowfall leaves, up to the snowpack's depth. */
    private static void deepen(ServerLevel level, @Nullable BlockPos top, Climate climate) {
        if (top == null || !level.getBiome(top).value().coldEnoughToSnow(top) || level.getBrightness(LightLayer.BLOCK, top) >= 10) return;
        BlockState s = level.getBlockState(top);
        if (!s.is(Blocks.SNOW)) return;
        int layers = s.getValue(SnowLayerBlock.LAYERS);
        int depth = layers + (level.getBlockState(top.below()).is(Blocks.SNOW_BLOCK) ? 8 : 0);
        if (depth >= targetDepth(level, top, climate)) return;
        if (layers < 8) {
            BlockState grown = s.setValue(SnowLayerBlock.LAYERS, layers + 1);
            net.minecraft.world.level.block.Block.pushEntitiesUp(s, grown, level, top);
            level.setBlockAndUpdate(top, grown);
        } else if (!level.getBlockState(top.below()).is(Blocks.SNOW_BLOCK) && level.getBlockState(top.above()).isAir()) {
            // a full drift packs down into a snow block, with fresh snow on top
            level.setBlockAndUpdate(top, Blocks.SNOW_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(top.above(), Blocks.SNOW.defaultBlockState());
        }
    }

    /** The snowpack settles and melts away in spring and on mild days. */
    private static void thaw(ServerLevel level, @Nullable BlockPos top, LocalWeather local) {
        if (top == null || Temperature.ambient(level, top, local) < 3.0F) return;
        BlockState s = level.getBlockState(top);
        BlockState below = level.getBlockState(top.below());
        if (s.is(Blocks.SNOW) && below.is(Blocks.SNOW_BLOCK)) {
            int layers = s.getValue(SnowLayerBlock.LAYERS);
            if (layers > 1) level.setBlockAndUpdate(top, s.setValue(SnowLayerBlock.LAYERS, layers - 1));
            else {
                level.removeBlock(top, false);
                level.setBlockAndUpdate(top.below(), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 8));
            }
        }
    }

    /** Rain falling on deep snow melts it fast, and the meltwater swells the rivers (as in a spring flood). */
    private static void rainOnSnow(ServerLevel level, @Nullable BlockPos top) {
        if (top == null) return;
        BlockState s = level.getBlockState(top);
        if (!s.is(Blocks.SNOW) || s.getValue(SnowLayerBlock.LAYERS) < 3 || level.getBiome(top).value().coldEnoughToSnow(top)) return;
        int layers = s.getValue(SnowLayerBlock.LAYERS);
        level.setBlockAndUpdate(top, s.setValue(SnowLayerBlock.LAYERS, Math.max(1, layers - 2)));
        WeatherHazards.rainOnSnow = Math.min(WeatherHazards.rainOnSnow + 0.02F, 1.0F);
    }

    /** Needle ice: columns of ice crystals pushing up out of bare, damp soil on a frosty morning. */
    private static void needleIce(ServerLevel level, @Nullable BlockPos top, RandomSource random) {
        if (top == null || !level.canSeeSky(top)) return;
        BlockState at = level.getBlockState(top);
        BlockState below = level.getBlockState(top.below());
        if (at.isAir() && (below.is(Blocks.DIRT) || below.is(Blocks.COARSE_DIRT) || below.is(Blocks.FARMLAND) || below.is(Blocks.MUD)
                || below.is(Blocks.ROOTED_DIRT))) {
            level.setBlockAndUpdate(top, ModBlocks.NEEDLE_ICE.get().defaultBlockState());
        } else if (at.getBlock() instanceof CropBlock crop && below.is(Blocks.FARMLAND) && crop.getAge(at) <= 1 && random.nextInt(6) == 0
                && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            // frost heave lifts a seedling clean out of the soil
            level.destroyBlock(top, true);
            level.setBlockAndUpdate(top, ModBlocks.NEEDLE_ICE.get().defaultBlockState());
        }
    }

    /** Frost flowers: feathery clusters of ice crystals that grow on new ice in bitter, still cold. */
    private static void frostFlowers(ServerLevel level, @Nullable BlockPos top) {
        if (top == null || !level.canSeeSky(top)) return;
        if (level.getBlockState(top).isAir() && level.getBlockState(top.below()).is(Blocks.ICE)) {
            level.setBlockAndUpdate(top, ModBlocks.FROST_FLOWERS.get().defaultBlockState());
        }
    }

    private static Direction downwind(WeatherState state) {
        return Direction.fromYRot(Math.toDegrees(state.windAngle) - 90.0);
    }

    /**
     * A cornice: wind-blown snow building out over the lee side of a ridge, until it overhangs the drop below. It
     * only grows where the snow at the top of a slope meets a steep drop downwind.
     */
    private static void buildCornice(ServerLevel level, WeatherState state, @Nullable BlockPos top) {
        if (top == null || !level.getBiome(top).value().coldEnoughToSnow(top)) return;
        BlockState s = level.getBlockState(top);
        if (!(s.is(Blocks.SNOW) && s.getValue(SnowLayerBlock.LAYERS) >= 4 || s.is(Blocks.SNOW_BLOCK))) return;
        Direction lee = downwind(state);
        BlockPos out = top.relative(lee);
        // reaching out at most a couple of blocks over a drop of at least five
        for (int k = 0; k < 2; k++) {
            BlockState here = level.getBlockState(out);
            if (here.is(Blocks.SNOW_BLOCK) && level.getBlockState(out.below()).isAir()) {
                out = out.relative(lee);
                continue;
            }
            if (!here.isAir()) return;
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, out.getX(), out.getZ());
            if (top.getY() - ground < 5) return;
            level.setBlockAndUpdate(out, Blocks.SNOW_BLOCK.defaultBlockState());
            return;
        }
    }

    /** A cornice gives way under anyone standing on it (or when it thaws), and brings the snow below down with it. */
    private static void checkCornice(ServerLevel level, ServerPlayer player, LocalWeather local) {
        if (!player.onGround() || player.tickCount % 5 != 0) return;
        BlockPos under = player.blockPosition().below();
        if (!level.getBlockState(under).is(Blocks.SNOW_BLOCK) || !level.getBlockState(under.below()).isAir()
                || !level.getBlockState(under.below(2)).isAir()) return;
        collapse(level, under);
    }

    private static void collapse(ServerLevel level, BlockPos start) {
        RandomSource random = level.random;
        int fallen = 0;
        for (BlockPos p : BlockPos.betweenClosed(start.offset(-2, -1, -2), start.offset(2, 1, 2))) {
            BlockState s = level.getBlockState(p);
            if ((s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.SNOW)) && (level.getBlockState(p.below()).isAir() || random.nextInt(3) == 0)) {
                FallingBlockEntity snow = FallingBlockEntity.fall(level, p.immutable(), s);
                snow.setHurtsEntities(1.0F, 6);
                fallen++;
            }
        }
        if (fallen == 0) return;
        level.playSound(null, start, ModSounds.AVALANCHE.get(), SoundSource.WEATHER, 3.0F, 1.1F);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(start.getX(), start.getY(), start.getZ()) < 48 * 48) {
                p.displayClientMessage(Component.translatable("message.reikos_weather.cornice").withStyle(s -> s.withColor(0xBBDDFF).withBold(true)), true);
                WeatherAdvancements.award(p, "avalanche");
            }
        }
    }

    /** Sastrugi: the wind scours open snowfields into sharp ridges and furrows lying across its path. */
    private static void sastrugi(ServerLevel level, WeatherState state, @Nullable BlockPos top) {
        if (top == null || !level.canSeeSky(top.above())) return;
        BlockState s = level.getBlockState(top);
        if (!s.is(Blocks.SNOW) || s.getValue(SnowLayerBlock.LAYERS) > 5) return;
        // only out in the open, not among trees
        if (level.getBlockState(top.above(3)).is(BlockTags.LEAVES)) return;
        double along = top.getX() * Math.cos(state.windAngle) + top.getZ() * Math.sin(state.windAngle);
        double across = -top.getX() * Math.sin(state.windAngle) + top.getZ() * Math.cos(state.windAngle);
        float wave = Mth.sin((float) (along * 1.1 + Math.sin(across * 0.35) * 1.5));
        int layers = Mth.clamp(2 + Math.round(wave * 1.6F), 1, 4);
        if (layers != s.getValue(SnowLayerBlock.LAYERS)) level.setBlockAndUpdate(top, s.setValue(SnowLayerBlock.LAYERS, layers));
    }

    private WinterGround() {}
}
