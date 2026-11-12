package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.temperature.Temperature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;

/**
 * Small touches the weather leaves on the ground round players: hair ice on fallen logs, bubbles frozen into the ice
 * of boggy lakes, fog dripping off trees onto the crops below, and the desert flowering after a wet winter.
 */
public final class LocalTouches {
    private static final int AREA = 40;

    static void tickPlayer(ServerLevel level, ServerPlayer player, LocalWeather local, Climate climate) {
        RandomSource random = level.random;
        WeatherProfile p = local.profile();
        long time = Math.floorMod(level.getDayTime(), 24000L);
        Season season = WeatherManager.seasonInfo().season();
        boolean night = time > 13000L || time < 1500L;
        if (WeatherConfig.HAIR_ICE.get() && night && climate != Climate.ARID && random.nextInt(4) == 0) {
            float air = Temperature.ambient(level, player.blockPosition(), local);
            // hair ice needs damp air just below freezing: a few degrees colder and the water in the wood freezes solid
            if (air < 0.5F && air > -5.0F && (Temperature.humidity(level, player.blockPosition(), local) > 0.55F || p.fog > 0.3F)) {
                for (int i = 0; i < 3; i++) hairIce(level, column(level, player, random));
            }
        }
        if (WeatherConfig.FROZEN_BUBBLES.get() && climate != Climate.ARID && random.nextInt(8) == 0
                && (season == Season.WINTER || climate == Climate.COLD)) {
            float air = Temperature.ambient(level, player.blockPosition(), local);
            if (air < -3.0F) frozenBubbles(level, column(level, player, random), random);
        }
        if (WeatherConfig.FOG_DRIP.get() && p.fog > 0.35F && p.rain < 0.05F && random.nextInt(2) == 0) {
            fogDrip(level, column(level, player, random), random);
        }
        if (WeatherConfig.MUD_SEASON.get() && climate != Climate.ARID && local.type().vanillaRain && p.rain > 0.25F
                && (season == Season.SPRING || p.rain > 0.65F) && Temperature.ambient(level, player.blockPosition(), local) > 1.0F) {
            for (int i = 0; i < 4; i++) mud(level, column(level, player, random));
        }
        if (WeatherConfig.PUDDLE_ICE.get() && (night || time < 2500L) && random.nextInt(2) == 0
                && Temperature.ambient(level, player.blockPosition(), local) < -0.5F) {
            for (int i = 0; i < 6; i++) freezePuddle(level, column(level, player, random));
        }
        if (SeasonalSpells.falseSpringWarmth(level) > 0.5F) {
            // the false spring: the snow melts away and everything starts growing, weeks too early
            for (int i = 0; i < 3; i++) earlyThaw(level, column(level, player, random), random);
        }
        if ((WeatherConfig.SNOW_BRIDGES.get() || WeatherConfig.AUFEIS.get()) && climate != Climate.ARID && random.nextInt(3) == 0) {
            float air = Temperature.ambient(level, player.blockPosition(), local);
            // snow drifting into a narrow stream builds out from both banks until it meets in the middle
            if (WeatherConfig.SNOW_BRIDGES.get() && air < -3.0F && (p.snowLayers > 0 || random.nextInt(6) == 0)) {
                snowBridge(level, column(level, player, random), random);
            }
            // in a hard winter, water keeps seeping out over the stream ice and freezing, layer on layer
            if (WeatherConfig.AUFEIS.get() && air < -8.0F) aufeis(level, column(level, player, random));
        }
        com.reiko.weather.entity.TumbleweedEntity.trySpawn(level, player, local, climate);
        if (WeatherConfig.FOXFIRE.get() && climate != Climate.ARID && season != Season.WINTER && random.nextInt(4) == 0
                && (p.rain > 0.1F || p.fog > 0.3F || Temperature.humidity(level, player.blockPosition(), local) > 0.75F)
                && Temperature.ambient(level, player.blockPosition(), local) > 4.0F) {
            // honey fungus in the old logs of a wet wood
            foxfire(level, player, random);
        }
        if ((WeatherConfig.SNOW_GHOSTS.get() || WeatherConfig.FROST_CRACKS.get() || WeatherConfig.CANDLE_ICE.get()) && random.nextInt(2) == 0) {
            float air = Temperature.ambient(level, player.blockPosition(), local);
            // on a high, cold ridge in freezing fog or snow, rime and snow plaster the conifers white
            if (WeatherConfig.SNOW_GHOSTS.get() && air < -6.0F && (p.fog > 0.2F || p.snowLayers > 0 || p.rain > 0.2F)
                    && player.getBlockY() > level.getSeaLevel() + 40) {
                for (int i = 0; i < 3; i++) snowGhost(level, column(level, player, random), random);
            }
            // in a hard frost at night, the sap freezes and a trunk splits with a crack like a gunshot
            if (WeatherConfig.FROST_CRACKS.get() && air < -12.0F && night && random.nextInt(400) == 0) frostCrack(level, player, random);
            // in the spring thaw, rotten lake ice falls apart into candles, and the wind piles them ashore
            if (WeatherConfig.CANDLE_ICE.get() && season == Season.SPRING && air > 1.0F && p.wind > 0.15F) {
                for (int i = 0; i < 3; i++) candleIce(level, column(level, player, random));
            }
        }
        if (WeatherConfig.SUN_CUPS.get() && season == Season.SUMMER && level.isDay() && p.rain < 0.05F && random.nextInt(3) == 0
                && Temperature.ambient(level, player.blockPosition(), local) > -4.0F) {
            sunCups(level, column(level, player, random));
        }
        if (WeatherConfig.DRIFTWOOD.get() && random.nextInt(p.wind > 0.55F || p.rain > 0.6F ? 15 : 300) == 0) {
            // storms wash it up (and it lies about on the beach long after)
            driftwood(level, column(level, player, random), random);
        }
        if (WeatherConfig.SUPERBLOOMS.get() && climate == Climate.ARID && season == Season.SPRING && ClimateYears.leaning(level) > 0.3F) {
            for (int i = 0; i < 2; i++) bloom(level, column(level, player, random), random);
        }
    }

    @Nullable
    private static BlockPos column(ServerLevel level, ServerPlayer player, RandomSource random) {
        int x = player.getBlockX() + random.nextInt(AREA * 2 + 1) - AREA, z = player.getBlockZ() + random.nextInt(AREA * 2 + 1) - AREA;
        BlockPos c = new BlockPos(x, 0, z);
        return level.isLoaded(c) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, c) : null;
    }

    /**
     * Hair ice: on a damp night just below freezing, a fungus living in a rotting log pushes water out through the
     * pores of the wood, and it freezes into masses of hair-fine white threads that melt away once the sun is up.
     */
    private static void hairIce(ServerLevel level, @Nullable BlockPos top) {
        if (top == null || !level.getBlockState(top).isAir()) return;
        BlockState log = level.getBlockState(top.below());
        // a log lying on the ground, not a beam in a wall or a roof
        if (!log.is(BlockTags.LOGS) || !log.hasProperty(RotatedPillarBlock.AXIS) || log.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y) return;
        if (!level.getBlockState(top.below(2)).is(BlockTags.DIRT) && !level.getBlockState(top.below(2)).is(Blocks.MOSS_BLOCK)) return;
        if (net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(log.getBlock()).getPath().startsWith("stripped")) return;
        level.setBlockAndUpdate(top, ModBlocks.HAIR_ICE.get().defaultBlockState());
    }

    /**
     * Bubbles frozen into lake ice: rotting plants on the bed of a boggy lake give off marsh gas, and as the ice
     * thickens it catches the rising bubbles in stacks of flat white discs.
     */
    private static void frozenBubbles(ServerLevel level, @Nullable BlockPos top, RandomSource random) {
        if (top == null) return;
        BlockPos ice = top.below();
        if (!level.getBlockState(ice).is(Blocks.ICE) || !level.getFluidState(ice.below()).is(FluidTags.WATER)) return;
        boolean boggy = level.getBiome(ice).is(Biomes.SWAMP) || level.getBiome(ice).is(Biomes.MANGROVE_SWAMP);
        if (!boggy) {
            // a soft, muddy bed under the water
            BlockPos.MutableBlockPos p = ice.below().mutable();
            for (int i = 0; i < 10 && level.getFluidState(p).is(FluidTags.WATER); i++) p.move(Direction.DOWN);
            BlockState bed = level.getBlockState(p);
            boggy = bed.is(Blocks.MUD) || bed.is(Blocks.CLAY) || bed.is(ModBlocks.PEAT.get()) || bed.is(Blocks.DIRT) || bed.is(Blocks.GRASS_BLOCK)
                    || bed.is(Blocks.MOSS_BLOCK) || bed.is(Blocks.PODZOL);
            if (boggy && level.getBiome(ice).is(BiomeTags.IS_OCEAN)) boggy = false;
        }
        if (boggy && random.nextInt(2) == 0) level.setBlockAndUpdate(ice, ModBlocks.BUBBLE_ICE.get().defaultBlockState());
    }

    /** Fog drip: trees comb droplets out of fog that drifts through their leaves, enough to water what grows below. */
    private static void fogDrip(ServerLevel level, @Nullable BlockPos top, RandomSource random) {
        if (top == null || !level.getBlockState(top.below()).is(BlockTags.LEAVES)) return;
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, top);
        BlockState at = level.getBlockState(ground);
        BlockState below = level.getBlockState(ground.below());
        if (below.is(Blocks.FARMLAND) && below.getValue(FarmBlock.MOISTURE) < FarmBlock.MAX_MOISTURE) {
            level.setBlock(ground.below(), below.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), Block.UPDATE_CLIENTS);
        }
        if (at.getBlock() instanceof CropBlock && random.nextInt(3) == 0) {
            at.randomTick(level, ground, random);
        }
    }

    /**
     * A superbloom: after a wet winter, seeds that have lain in the desert sand for years all wake at once, and the
     * desert floor is carpeted with flowers for a few weeks in spring.
     */
    private static void bloom(ServerLevel level, @Nullable BlockPos top, RandomSource random) {
        if (top == null || !level.getBlockState(top).isAir() || !level.canSeeSky(top)) return;
        BlockState ground = level.getBlockState(top.below());
        if (!ground.is(BlockTags.SAND) && !ground.is(BlockTags.TERRACOTTA)) return;
        // they come up in drifts of one colour or another, not evenly everywhere
        float patch = com.reiko.weather.landform.Terrain.field(level.getSeed() ^ ClimateYears.year(level), top.getX(), top.getZ(), 14.0);
        if (patch < 0.38F || random.nextFloat() > (patch - 0.38F) * 2.2F) return;
        float kind = com.reiko.weather.landform.Terrain.field(level.getSeed() + 7, top.getX(), top.getZ(), 22.0);
        Block flower = kind < 0.4F ? ModBlocks.DESERT_GOLD.get() : kind < 0.7F ? ModBlocks.DESERT_POPPY.get() : ModBlocks.SAND_VERBENA.get();
        if (flower.defaultBlockState().canSurvive(level, top)) {
            level.setBlockAndUpdate(top, flower.defaultBlockState());
        }
    }

    /** Mud season: bare earth and dirt paths out in the rain churn into sticky mud. */
    private static void mud(ServerLevel level, @Nullable BlockPos top) {
        if (top == null || !level.isRainingAt(top)) return;
        BlockPos ground = top.below();
        BlockState s = level.getBlockState(ground);
        if (s.is(Blocks.DIRT_PATH)) {
            level.setBlockAndUpdate(ground, ModBlocks.MUDDY_PATH.get().defaultBlockState());
        } else if ((s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.ROOTED_DIRT)) && level.getBlockState(top).isAir()) {
            level.setBlockAndUpdate(ground, ModBlocks.SOFT_MUD.get().defaultBlockState());
        }
    }

    /** A puddle skinned over with ice on a frosty morning. */
    private static void freezePuddle(ServerLevel level, @Nullable BlockPos top) {
        if (top != null && level.getBlockState(top).is(ModBlocks.PUDDLE.get())) {
            level.setBlockAndUpdate(top, ModBlocks.FROZEN_PUDDLE.get().defaultBlockState());
        }
    }

    /** The warmth of a false spring: snow melting off, and crops and saplings putting on growth they'll lose to the frost. */
    private static void earlyThaw(ServerLevel level, @Nullable BlockPos top, RandomSource random) {
        if (top == null) return;
        BlockState s = level.getBlockState(top);
        if (s.is(Blocks.SNOW)) {
            int layers = s.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS);
            if (layers > 1) level.setBlockAndUpdate(top, s.setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, layers - 1));
            else level.removeBlock(top, false);
        } else if (s.getBlock() instanceof CropBlock || s.is(BlockTags.SAPLINGS)) {
            s.randomTick(level, top, random);
        }
    }

    /** A snow bridge: snow drifted over a narrow stream from bank to bank, hiding the water under it. */
    private static void snowBridge(ServerLevel level, @Nullable BlockPos top, RandomSource random) {
        if (top == null) return;
        BlockPos water = top.below();
        if (!level.getFluidState(water).is(FluidTags.WATER) || !level.getBlockState(top).isAir() || !level.canSeeSky(top)) return;
        Direction.Axis first = random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
        for (Direction.Axis axis : new Direction.Axis[]{first, first == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X}) {
            int lo = 0, hi = 0;
            while (lo < 4 && level.getFluidState(water.relative(axis, -(lo + 1))).is(FluidTags.WATER)) lo++;
            while (hi < 4 && level.getFluidState(water.relative(axis, hi + 1)).is(FluidTags.WATER)) hi++;
            // only a narrow stream: a wide river stays open
            if (lo + hi + 1 > 3 || !bank(level, water.relative(axis, -(lo + 1)), water.getY()) || !bank(level, water.relative(axis, hi + 1), water.getY())) continue;
            for (int k = -lo; k <= hi; k++) {
                BlockState over = level.getBlockState(water.relative(axis, k).above());
                if (!over.isAir() && !over.is(Blocks.SNOW)) return;
            }
            for (int k = -lo; k <= hi; k++) level.setBlockAndUpdate(water.relative(axis, k).above(), ModBlocks.SNOW_BRIDGE.get().defaultBlockState());
            return;
        }
    }

    /** Whether a column is a firm bank at the water's edge: ground level with the water, or a block above it. */
    private static boolean bank(ServerLevel level, BlockPos side, int waterY) {
        BlockPos at = new BlockPos(side.getX(), waterY, side.getZ());
        if (!level.getFluidState(at).isEmpty() || !level.getBlockState(at).isFaceSturdy(level, at, Direction.UP)) return false;
        BlockPos up = at.above();
        BlockState s = level.getBlockState(up);
        return s.isAir() || s.is(Blocks.SNOW) || s.isFaceSturdy(level, up, Direction.UP) && level.getBlockState(up.above()).isAir();
    }

    /** Aufeis: seeping winter water spreading over the stream ice from the banks and freezing, layer on layer. */
    private static void aufeis(ServerLevel level, @Nullable BlockPos top) {
        if (top == null) return;
        BlockPos at = top;
        if (level.getBlockState(at.below()).is(Blocks.SNOW)) at = at.below();
        BlockPos below = at.below();
        BlockState s = level.getBlockState(below);
        if (s.is(ModBlocks.AUFEIS.get())) {
            int layers = s.getValue(com.reiko.weather.block.AufeisBlock.LAYERS);
            if (layers < 8) {
                level.setBlockAndUpdate(below, s.setValue(com.reiko.weather.block.AufeisBlock.LAYERS, layers + 1));
            } else if (!level.getBlockState(below.below()).is(ModBlocks.AUFEIS.get()) && (level.getBlockState(at).isAir() || level.getBlockState(at).is(Blocks.SNOW))) {
                // up to two blocks thick at most
                level.setBlockAndUpdate(at, ModBlocks.AUFEIS.get().defaultBlockState());
            }
            return;
        }
        if (!s.is(Blocks.ICE) || !level.getFluidState(below.below()).is(FluidTags.WATER) && !level.getBlockState(below.below()).isSolid()) return;
        // it starts where the ground water comes out, at the banks, and spreads from there
        boolean source = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int k = 1; k <= 2 && !source; k++) {
                BlockPos n = below.relative(d, k);
                BlockState ns = level.getBlockState(n);
                source = ns.is(ModBlocks.AUFEIS.get()) || level.getBlockState(n.above()).is(ModBlocks.AUFEIS.get())
                        || ns.is(BlockTags.DIRT) || ns.is(BlockTags.BASE_STONE_OVERWORLD) || ns.is(Blocks.GRAVEL);
            }
        }
        if (source && (level.getBlockState(at).isAir() || level.getBlockState(at).is(Blocks.SNOW))) {
            level.setBlockAndUpdate(at, ModBlocks.AUFEIS.get().defaultBlockState());
        }
    }

    /** Driftwood: a bleached log washed up on the beach by a storm. */
    private static void driftwood(ServerLevel level, @Nullable BlockPos top, RandomSource random) {
        if (top == null) return;
        int sea = level.getSeaLevel();
        BlockPos ground = top.below();
        if (ground.getY() < sea - 1 || ground.getY() > sea + 1 || !level.getBlockState(top).isAir()) return;
        BlockState g = level.getBlockState(ground);
        if (!g.is(BlockTags.SAND) && !g.is(Blocks.GRAVEL) || !level.getBiome(top).is(BiomeTags.IS_BEACH)) return;
        // just above the waterline, and not every few blocks
        boolean shore = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int k = 1; k <= 5 && !shore; k++) shore = level.getFluidState(new BlockPos(ground.getX(), sea - 1, ground.getZ()).relative(d, k)).is(FluidTags.WATER);
        }
        if (!shore) return;
        for (BlockPos p : BlockPos.betweenClosed(top.offset(-6, -2, -6), top.offset(6, 2, 6))) {
            if (level.getBlockState(p).is(ModBlocks.DRIFTWOOD.get())) return;
        }
        Direction.Axis axis = random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
        int length = 1 + random.nextInt(3);
        for (int k = 0; k < length; k++) {
            BlockPos p = top.relative(axis, k);
            if (!level.getBlockState(p).isAir() || !level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) break;
            level.setBlockAndUpdate(p, ModBlocks.DRIFTWOOD.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis));
        }
    }

    /** Foxfire: a log lying on the woodland floor, rotting in the damp, starts to glow. */
    private static void foxfire(ServerLevel level, ServerPlayer player, RandomSource random) {
        int x = player.getBlockX() + random.nextInt(AREA * 2 + 1) - AREA, z = player.getBlockZ() + random.nextInt(AREA * 2 + 1) - AREA;
        BlockPos column = new BlockPos(x, 0, z);
        if (!level.isLoaded(column)) return;
        // (under the trees, not on top of them)
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        if (!level.getBlockState(top).isAir()) return;
        BlockState log = level.getBlockState(top.below());
        if (!log.is(BlockTags.LOGS) || !log.hasProperty(RotatedPillarBlock.AXIS) || log.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y) return;
        if (net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(log.getBlock()).getPath().startsWith("stripped")) return;
        BlockState under = level.getBlockState(top.below(2));
        if (!under.is(BlockTags.DIRT) && !under.is(Blocks.MOSS_BLOCK) && !under.is(BlockTags.LOGS)) return;
        var biome = level.getBiome(top);
        if (!biome.is(BiomeTags.IS_FOREST) && !biome.is(BiomeTags.IS_TAIGA) && !biome.is(BiomeTags.IS_JUNGLE) && !biome.is(Biomes.SWAMP)
                && !biome.is(Biomes.MANGROVE_SWAMP)) return;
        level.setBlockAndUpdate(top, ModBlocks.FOXFIRE.get().defaultBlockState());
    }

    /**
     * Sun cups: under a strong summer sun, a high snowfield melts unevenly, each hollow catching a little more sunlight
     * than the rim round it and so deepening, until the whole surface is pitted with shallow cups.
     */
    private static void sunCups(ServerLevel level, @Nullable BlockPos top) {
        if (top == null || top.getY() < level.getSeaLevel() + 60 || !level.canSeeSky(top)) return;
        BlockPos at = level.getBlockState(top).is(Blocks.SNOW) ? top : top.below();
        BlockState s = level.getBlockState(at);
        int layers = s.is(Blocks.SNOW_BLOCK) ? 8 : s.is(Blocks.SNOW) ? s.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) : 0;
        if (layers < 6) return;
        // the cups lie in a loose honeycomb about three blocks across
        int x = at.getX(), z = at.getZ();
        double nearest = 9.0;
        for (int i = Math.floorDiv(x, 3) - 1; i <= Math.floorDiv(x, 3) + 1; i++) {
            for (int j = Math.floorDiv(z, 3) - 1; j <= Math.floorDiv(z, 3) + 1; j++) {
                int h = Mth.murmurHash3Mixer(i * 73856093 ^ j * 19349663);
                double cx = i * 3 + 1.5 + ((h & 255) / 255.0 - 0.5), cz = j * 3 + 1.5 + (((h >>> 8) & 255) / 255.0 - 0.5);
                nearest = Math.min(nearest, Math.hypot(x + 0.5 - cx, z + 0.5 - cz));
            }
        }
        int depth = nearest < 0.75 ? 3 : nearest < 1.35 ? 2 : nearest < 1.8 ? 1 : 0;
        int target = 8 - 2 * depth;
        if (target >= layers) return;
        BlockPos below = at.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return;
        level.setBlockAndUpdate(at, Blocks.SNOW.defaultBlockState().setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, target));
    }

    /** A snow ghost: rime and snow building up over a conifer on a cold ridge until it's buried in white. */
    private static void snowGhost(ServerLevel level, @Nullable BlockPos top, RandomSource random) {
        if (top == null || top.getY() < level.getSeaLevel() + 40) return;
        BlockPos at = top.below();
        if (level.getBlockState(top).is(Blocks.SNOW)) at = top.below();
        else if (!level.getBlockState(at).is(Blocks.SPRUCE_LEAVES)) at = top;
        if (!level.getBlockState(at).is(Blocks.SPRUCE_LEAVES)) return;
        // the crown first, and the plaster spreads down and round the tree from there
        plaster(level, at);
        for (int i = 0; i < 6; i++) {
            BlockPos n = at.offset(random.nextInt(5) - 2, -random.nextInt(4), random.nextInt(5) - 2);
            plaster(level, n);
        }
    }

    private static void plaster(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.is(Blocks.SPRUCE_LEAVES)) {
            level.setBlock(pos, com.reiko.weather.block.SnowGhostLeavesBlock.plastered(ModBlocks.SNOW_GHOST_LEAVES.get(), s), Block.UPDATE_CLIENTS);
        }
    }

    /** A frost crack: a tree trunk round about splits in the cold, with a report like a rifle shot. */
    private static void frostCrack(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int i = 0; i < 32; i++) {
            int x = player.getBlockX() + random.nextInt(41) - 20, z = player.getBlockZ() + random.nextInt(41) - 20;
            BlockPos column = new BlockPos(x, 0, z);
            if (!level.isLoaded(column)) continue;
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            BlockPos trunk = ground.above(1 + random.nextInt(2));
            BlockState log = level.getBlockState(trunk);
            if (!log.is(BlockTags.LOGS) || !log.hasProperty(RotatedPillarBlock.AXIS) || log.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y) {
                // the heightmap stops on top of the trunk when there are no leaves over it: look down the column instead
                trunk = ground.below(1 + random.nextInt(2));
                log = level.getBlockState(trunk);
                if (!log.is(BlockTags.LOGS) || !log.hasProperty(RotatedPillarBlock.AXIS) || log.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y) continue;
            }
            level.playSound(null, trunk, com.reiko.weather.registry.ModSounds.FROST_CRACK.get(), net.minecraft.sounds.SoundSource.AMBIENT,
                    2.0F, 0.85F + random.nextFloat() * 0.3F);
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, log),
                    trunk.getX() + 0.5, trunk.getY() + 0.5, trunk.getZ() + 0.5, 10, 0.3, 0.6, 0.3, 0.05);
            return;
        }
    }

    /** Candle ice blown ashore from a thawing lake. */
    private static void candleIce(ServerLevel level, @Nullable BlockPos top) {
        if (top == null) return;
        BlockPos ice = top.below();
        if (!level.getBlockState(ice).is(Blocks.ICE) || !level.getFluidState(ice.below()).is(FluidTags.WATER)) return;
        var biome = level.getBiome(ice);
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN)) return;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos shore = ice.relative(d), on = shore.above();
            BlockState s = level.getBlockState(shore);
            if (!s.isFaceSturdy(level, shore, Direction.UP) || !level.getFluidState(shore).isEmpty() || s.is(Blocks.ICE)) continue;
            if (!heapable(level, on)) continue;
            // a heap a few blocks long along the shore
            level.setBlockAndUpdate(on, ModBlocks.CANDLE_ICE.get().defaultBlockState());
            for (Direction side : new Direction[]{d.getClockWise(), d.getCounterClockWise()}) {
                BlockPos n = on.relative(side);
                if (heapable(level, n) && level.getBlockState(n.below()).isFaceSturdy(level, n.below(), Direction.UP)
                        && level.getFluidState(n.below()).isEmpty()) {
                    level.setBlockAndUpdate(n, ModBlocks.CANDLE_ICE.get().defaultBlockState());
                }
            }
            return;
        }
    }

    /** Whether candle ice can heap up here: open air, snow, or the grass and such that grows along a shore. */
    private static boolean heapable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return (state.isAir() || state.is(Blocks.SNOW) || state.canBeReplaced()) && state.getFluidState().isEmpty()
                && !state.is(ModBlocks.CANDLE_ICE.get());
    }

    /** Whether a spot is just inside a cave mouth, where a cool draught blows out on a hot day. */
    public static boolean caveDraught(ServerLevel level, BlockPos eyes) {
        if (!WeatherConfig.CAVE_BREATH.get() || level.canSeeSky(eyes)) return false;
        int sky = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, eyes);
        if (sky < 2) return false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            boolean passage = true;
            for (int k = 2; k <= 5 && passage; k++) {
                BlockPos in = eyes.relative(d, k);
                passage = level.getBlockState(in).isAir() && level.getBrightness(net.minecraft.world.level.LightLayer.SKY, in) <= Math.max(1, 6 - k);
            }
            if (passage) return true;
        }
        return false;
    }

    private LocalTouches() {}
}
