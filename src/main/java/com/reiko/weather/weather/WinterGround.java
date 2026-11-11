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
import net.minecraft.world.level.block.Block;
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
 * windswept ridges, wind carving open snowfields into ridges and furrows (sastrugi), frost quakes booming as wet
 * ground freezes hard all at once, little ice volcanoes spouting along the edge of shore ice, and gales shoving
 * lake ice up onto the shore.
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
            // any rain on snow soaks it to slush (which freezes into a crust if the night turns cold)
            if (WeatherConfig.RAIN_ON_SNOW.get() && local.type().vanillaRain && !snowing && p.rain > 0.2F && intensity > 0.3F) {
                for (int i = 0; i < 4; i++) soak(level, column(level, player, random));
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
        if (WeatherConfig.FROST_QUAKES.get() && (winter || local.type() == WeatherType.COLD_SNAP) && random.nextInt(5000) == 0
                && (time > 16000L || time < 1000L) && Temperature.ambient(level, player.blockPosition(), local) < -12.0F) {
            frostQuake(level, player, random);
        }
        float air = Float.NaN;
        if (player.tickCount % 20 == 0) {
            air = Temperature.ambient(level, player.blockPosition(), local);
            // remember the last thaw: meltwater on the ground is what freezes into black ice
            if (air > 2.0F) LAST_THAW.put(player.getUUID(), level.getGameTime());
        }
        if (WeatherConfig.BLACK_ICE.get() && (time > 21000L || time < 1500L) && p.rain < 0.1F && random.nextInt(2) == 0) {
            Long thaw = LAST_THAW.get(player.getUUID());
            if (thaw != null && level.getGameTime() - thaw < 30000L) {
                if (Float.isNaN(air)) air = Temperature.ambient(level, player.blockPosition(), local);
                if (air < -1.0F) blackIce(level, column(level, player, random));
            }
        }
        if (WeatherConfig.FROZEN_WATERFALLS.get() && random.nextInt(8) == 0) {
            if (Float.isNaN(air)) air = Temperature.ambient(level, player.blockPosition(), local);
            if (air < -6.0F) freezeWaterfall(level, player, random);
        }
        if (WeatherConfig.ICE_DISCS.get() && climate == Climate.COLD && winter && random.nextInt(900) == 0) {
            BlockPos top = column(level, player, random);
            if (top != null) com.reiko.weather.entity.IceFloeEntity.formRoundIce(level, top.below());
        }
        if (WeatherConfig.LAKE_ICE_SHOVES.get() && griefing && (winter || climate == Climate.COLD)) {
            if (wind > 0.4F) {
                for (int i = 0; i < 3; i++) iceVolcano(level, state, column(level, player, random), random);
            }
            if (wind > 0.6F && random.nextInt(60) == 0) {
                iceShove(level, state, column(level, player, random), random);
            }
        }
    }

    /**
     * A frost quake (cryoseism): when a hard freeze follows a wet spell, the water in the ground freezes and swells
     * all at once and the soil cracks open with a bang like a gunshot, loud enough to rattle the windows.
     */
    private static void frostQuake(ServerLevel level, ServerPlayer player, RandomSource random) {
        float angle = random.nextFloat() * Mth.TWO_PI;
        double distance = 8 + random.nextDouble() * 32;
        BlockPos column = BlockPos.containing(player.getX() + Mth.cos(angle) * distance, 0, player.getZ() + Mth.sin(angle) * distance);
        if (!level.isLoaded(column)) return;
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        level.playSound(null, top, ModSounds.FROST_QUAKE.get(), SoundSource.WEATHER, 5.0F, 0.85F + random.nextFloat() * 0.3F);
        // a crack runs a few blocks across the ground, splitting the snow and turf
        Direction run = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        if (level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            BlockPos at = top;
            for (int i = 0; i < 3 + random.nextInt(4); i++) {
                BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
                BlockState s = level.getBlockState(surface);
                if (s.is(Blocks.SNOW)) level.removeBlock(surface, false);
                BlockState ground = level.getBlockState(surface.below());
                if (ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.DIRT) || ground.is(Blocks.PODZOL) || ground.is(Blocks.MUD)) {
                    level.setBlockAndUpdate(surface.below(), Blocks.COARSE_DIRT.defaultBlockState());
                }
                at = at.relative(random.nextInt(4) == 0 ? run.getClockWise() : run);
            }
        }
        for (ServerPlayer p : level.players()) {
            double d = Math.sqrt(p.distanceToSqr(top.getX(), p.getY(), top.getZ()));
            if (d > 48) continue;
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new com.reiko.weather.network.QuakePayload(0.35F * (float) (1.0 - d / 48.0), 20));
            p.displayClientMessage(Component.translatable("message.reikos_weather.frost_quake").withStyle(s -> s.withColor(0xBBDDFF)), true);
            WeatherAdvancements.award(p, "frost_quake");
        }
    }

    /**
     * An ice volcano: where waves run in under the edge of the shore ice, water and slush spout up through a hole
     * in it and freeze where they land, slowly building a little cone with a vent in the top.
     */
    private static void iceVolcano(ServerLevel level, WeatherState state, @Nullable BlockPos top, RandomSource random) {
        if (top == null) return;
        BlockPos ice = level.getBlockState(top).is(Blocks.ICE) ? top : top.below();
        if (!level.getBlockState(ice).is(Blocks.ICE) || !level.getBlockState(ice.below()).is(Blocks.WATER)) return;
        // the waves come from upwind, so open water must be there, with more ice (the shelf) on the other side
        Direction upwind = Direction.fromYRot(Math.toDegrees(state.windAngle) - 90.0).getOpposite();
        if (!level.getBlockState(ice.relative(upwind)).is(Blocks.WATER) || !level.getBlockState(ice.relative(upwind.getOpposite())).is(Blocks.ICE)) return;
        BlockPos vent = ice.relative(upwind.getOpposite());
        int height = 0;
        while (height < 3 && level.getBlockState(vent.above(height + 1)).is(Blocks.PACKED_ICE)) height++;
        if (height < 2 && level.getBlockState(vent.above(height + 1)).isAir()) {
            level.setBlockAndUpdate(vent.above(height + 1), Blocks.PACKED_ICE.defaultBlockState());
        }
        // slushy snow freezing round the foot of the cone
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos side = vent.relative(d).above();
            if (level.getBlockState(side).isAir() && level.getBlockState(side.below()).is(Blocks.ICE) && random.nextBoolean()) {
                level.setBlockAndUpdate(side, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + random.nextInt(3)));
            }
        }
        BlockPos spout = vent.above(height + 2);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, spout.getX() + 0.5, spout.getY(), spout.getZ() + 0.5, 24, 0.2, 0.6, 0.2, 0.3);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, spout.getX() + 0.5, spout.getY() + 0.5, spout.getZ() + 0.5, 4, 0.2, 0.4, 0.2, 0.02);
        level.playSound(null, spout, net.minecraft.sounds.SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.8F, 1.3F);
    }

    /**
     * An ice shove (ivu): a strong onshore wind drives a whole sheet of lake ice up the beach, where it buckles and
     * piles up in a jumble of slabs, sometimes metres high.
     */
    private static void iceShove(ServerLevel level, WeatherState state, @Nullable BlockPos top, RandomSource random) {
        if (top == null) return;
        BlockPos ice = level.getBlockState(top).is(Blocks.ICE) ? top : top.below();
        if (!level.getBlockState(ice).is(Blocks.ICE) || !level.getBlockState(ice.below()).is(Blocks.WATER)) return;
        Direction downwind = Direction.fromYRot(Math.toDegrees(state.windAngle) - 90.0);
        BlockPos shore = ice.relative(downwind);
        BlockState land = level.getBlockState(shore);
        BlockPos onto = land.isAir() || land.canBeReplaced() ? shore : shore.above();
        BlockState ground = level.getBlockState(onto.below());
        if (!ground.isFaceSturdy(level, onto.below(), Direction.UP) || ground.is(Blocks.ICE) || ground.is(Blocks.PACKED_ICE) && random.nextInt(3) != 0
                || onto.getY() > ice.getY() + 2 || GroundEvents.built(level, onto, 4)) return;
        BlockState here = level.getBlockState(onto);
        if (!(here.isAir() || here.canBeReplaced() && here.getFluidState().isEmpty())) return;
        if (!here.isAir()) level.destroyBlock(onto, true);
        level.setBlockAndUpdate(onto, random.nextBoolean() ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.ICE.defaultBlockState());
        // the slab came from the edge of the sheet, which is open water now
        level.setBlockAndUpdate(ice, Blocks.WATER.defaultBlockState());
        level.playSound(null, onto, ModSounds.ICE_SHOVE.get(), SoundSource.BLOCKS, 2.5F, 0.8F + random.nextFloat() * 0.3F);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(onto).inflate(32))) {
            WeatherAdvancements.award(p, "ice_shove");
        }
    }

    private static final java.util.Map<java.util.UUID, Long> LAST_THAW = new java.util.HashMap<>();

    /**
     * Black ice: after a thaw, meltwater and puddles left on stone and paths freeze overnight into a thin, clear
     * glaze you can hardly see, treacherous until the sun gets onto it.
     */
    private static void blackIce(ServerLevel level, @Nullable BlockPos top) {
        if (top == null || !level.canSeeSky(top) || !level.getBlockState(top).isAir()) return;
        BlockPos below = top.below();
        BlockState ground = level.getBlockState(below);
        boolean hard = ground.is(BlockTags.BASE_STONE_OVERWORLD) || ground.is(Blocks.COBBLESTONE) || ground.is(Blocks.STONE_BRICKS) || ground.is(Blocks.SMOOTH_STONE)
                || ground.is(Blocks.DIRT_PATH) || ground.is(Blocks.BRICKS) || ground.is(Blocks.POLISHED_ANDESITE) || ground.is(Blocks.POLISHED_DIORITE)
                || ground.is(Blocks.POLISHED_GRANITE) || ground.is(Blocks.MUD_BRICKS) || ground.is(Blocks.DEEPSLATE_TILES) || ground.is(Blocks.PACKED_MUD);
        if (!hard || !ground.isFaceSturdy(level, below, Direction.UP)) return;
        level.setBlockAndUpdate(top, ModBlocks.ICE_GLAZE.get().defaultBlockState());
    }

    /**
     * A frozen waterfall: in a long, hard freeze the spray and the edges of a waterfall freeze first, then the whole
     * fall, until it hangs from the cliff as a curtain of fluted ice that climbers go up.
     */
    private static void freezeWaterfall(ServerLevel level, ServerPlayer player, RandomSource random) {
        int x = player.getBlockX() + random.nextInt(65) - 32, z = player.getBlockZ() + random.nextInt(65) - 32;
        BlockPos c = new BlockPos(x, 0, z);
        if (!level.isLoaded(c)) return;
        BlockPos.MutableBlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, c).below().mutable();
        var fluid = level.getFluidState(pos);
        if (!fluid.is(net.minecraft.tags.FluidTags.WATER) || fluid.isSource()) return;
        // walk down the fall: it must be falling water for a good few blocks
        java.util.List<BlockPos> fall = new java.util.ArrayList<>();
        BlockPos lip = pos.immutable();
        while (fall.size() < 24) {
            var f = level.getFluidState(pos);
            if (!f.is(net.minecraft.tags.FluidTags.WATER) || f.isSource()) break;
            if (!f.getValue(net.minecraft.world.level.material.FlowingFluid.FALLING) && !fall.isEmpty()) break;
            fall.add(pos.immutable());
            pos.move(Direction.DOWN);
        }
        if (fall.size() < 4 || GroundEvents.built(level, lip, 3)) return;
        BlockState ice = ModBlocks.FROZEN_WATERFALL.get().defaultBlockState();
        for (BlockPos p : fall) level.setBlock(p, ice, Block.UPDATE_CLIENTS);
        level.playSound(null, lip, net.minecraft.sounds.SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 1.5F, 0.5F);
        if (player.distanceToSqr(lip.getX(), lip.getY(), lip.getZ()) < 48 * 48) WeatherAdvancements.award(player, "frozen_waterfall");
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

    /** Rain soaking into lying snow turns it to grey, heavy slush. */
    private static void soak(ServerLevel level, @Nullable BlockPos top) {
        if (top == null || level.getBiome(top).value().coldEnoughToSnow(top) || !level.isRainingAt(top)) return;
        BlockState s = level.getBlockState(top);
        if (s.is(Blocks.SNOW) || s.is(com.reiko.weather.registry.ModBlocks.DUSTY_SNOW.get()) || s.is(com.reiko.weather.registry.ModBlocks.SNOW_CRUST.get())) {
            int layers = s.getValue(SnowLayerBlock.LAYERS);
            level.setBlockAndUpdate(top, com.reiko.weather.registry.ModBlocks.SLUSH.get().defaultBlockState().setValue(SnowLayerBlock.LAYERS, Math.max(1, layers - 1)));
        }
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
