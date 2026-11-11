package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.farming.Greenhouse;
import net.minecraft.tags.BiomeTags;
import com.reiko.weather.entity.TornadoEntity;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.registry.ModDamageTypes;
import com.reiko.weather.registry.ModEntities;
import com.reiko.weather.registry.ModItems;
import com.reiko.weather.temperature.TemperatureEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FrostedIceBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import javax.annotation.Nullable;
import java.util.List;

/** Server-side gameplay consequences of the weather. */
public final class WeatherHazards {
    private static final int AREA = 48;

    // ------------------------------------------------------------------ world effects, per player area

    /** How much meltwater rain falling on deep snow has sent into the rivers lately (it drains away over a few minutes). */
    static float rainOnSnow;

    static void tickLevel(ServerLevel level, WeatherState state) {
        RandomSource random = level.random;
        rainOnSnow *= 0.9995F;
        if (level.getGameTime() % 20L == 0L) Seas.tickWorld(level, state);
        boolean everySecond = level.getGameTime() % 20L == 0L;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            Climate climate = Climate.at(level, player.blockPosition());
            LocalWeather local = Microclimate.apply(level, climate, player.getBlockX(), player.getBlockZ(), state.windAngle,
                    LocalWeather.resolve(state.type, CustomWeathers.of(state), state.intensity(), state.cells, climate, player.getX(), player.getZ()));
            WeatherProfile profile = local.profile();
            float intensity = local.intensity();

            // desert dust in the air: snow falling for a day or so after comes down dusty
            if (profile.dustLayers > 0 || local.type() == WeatherType.HABOOB || local.type() == WeatherType.SANDSTORM) {
                DUST_IN_AIR.put(player.getUUID(), level.getGameTime());
            }
            Long dust = DUST_IN_AIR.get(player.getUUID());
            boolean dusty = dust != null && level.getGameTime() - dust < 24000L && WeatherConfig.DUSTY_SNOWFALL.get() && climate != Climate.ARID;
            int maxLayers = Math.min(profile.snowLayers, WeatherConfig.MAX_SNOW_LAYERS.get());
            if (maxLayers > 0 && intensity > 0) {
                int attempts = Mth.ceil(6 * intensity);
                for (int i = 0; i < attempts; i++) {
                    accumulateSnow(level, randomColumn(level, player, random), maxLayers, dusty);
                }
            }
            if (profile.ashLayers > 0 && intensity > 0 && WeatherConfig.ASH_FALL.get()) {
                int attempts = Mth.ceil(2 * intensity);
                for (int i = 0; i < attempts; i++) {
                    settleAsh(level, randomColumn(level, player, random), profile.ashLayers);
                }
            }
            if (WeatherConfig.SNOW_MELT.get()) {
                melt(level, randomColumn(level, player, random));
                melt(level, randomColumn(level, player, random));
                if (profile.tempOffset > 4.0F) {
                    // a föhn wind: warm, dry air pouring down off the mountains eats the snow away
                    for (int i = 0; i < 3; i++) foehnMelt(level, randomColumn(level, player, random));
                }
            }
            if (local.type() == WeatherType.COLD_SNAP && climate != Climate.ARID && intensity > 0.5F && WeatherConfig.FREEZE_RIVERS.get()) {
                for (int i = 0; i < 3; i++) snapFreeze(level, state, randomColumn(level, player, random), local);
            }
            if (profile.dustLayers > 0 && intensity > 0.5F && climate != Climate.ARID && WeatherConfig.DESERT_DUST.get() && random.nextFloat() < 0.4F) {
                settleDust(level, randomColumn(level, player, random));
            }
            Drought.tickPlayer(level, state, player, local, climate, randomColumn(level, player, random));
            Downdrafts.tickPlayer(level, player, local);
            Frostwork.tickPlayer(level, state, player, local, climate);
            Coasts.tickPlayer(level, player, local);
            WinterGround.tickPlayer(level, state, player, local, climate);
            StormGround.tickPlayer(level, state, player, local, climate);
            Highlands.tickPlayer(level, player, local, climate);
            Seas.tickPlayer(level, state, player, local, climate);
            StormWake.tickPlayer(level, player, local, climate);
            IcyWaters.tickPlayer(level, player, local, climate);
            LocalTouches.tickPlayer(level, player, local, climate);
            if (profile.glazeWater && WeatherConfig.GLAZE_WATER.get() && random.nextFloat() < intensity) {
                glazeWater(level, randomColumn(level, player, random));
            }
            if (profile.glazeGround && WeatherConfig.ICE_GLAZE.get()) {
                for (int i = 0; i < 3; i++) {
                    if (random.nextFloat() < intensity) glazeGround(level, randomColumn(level, player, random));
                }
            }
            if (profile.puddles && WeatherConfig.PUDDLES.get() && random.nextFloat() < 0.5F * intensity) {
                placePuddle(level, randomColumn(level, player, random));
            }
            if (profile.floods && WeatherConfig.FLASH_FLOODS.get() && intensity > 0.8F && random.nextFloat() < 0.25F) {
                flood(level, state, randomColumn(level, player, random));
            }
            if (local.type() == WeatherType.HURRICANE && WeatherConfig.STORM_SURGE.get() && intensity > 0.8F) {
                for (int i = 0; i < 3; i++) {
                    surge(level, state, randomColumn(level, player, random), false);
                }
            }
            if ((Tides.flooding(level) || Tides.floodingFlats(level)) && random.nextFloat() < 0.5F) {
                for (int i = 0; i < 2; i++) {
                    surge(level, state, randomColumn(level, player, random), true);
                }
            }
            if ((snowmeltActive() || rainOnSnow > 0.1F) && climate != Climate.ARID && random.nextFloat() < 0.35F) {
                BlockPos top = randomColumn(level, player, random);
                if (top != null && level.getBiome(top).is(BiomeTags.IS_RIVER)) {
                    flood(level, state, top, false);
                }
            }
            if (profile.debris && WeatherConfig.STORM_DEBRIS.get() && intensity > 0.6F && random.nextFloat() < 0.12F * intensity
                    && level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)) {
                StormDebris.drop(level, player, random);
            }
            if (profile.hail && WeatherConfig.HAIL_DAMAGES_CROPS.get() && random.nextFloat() < 0.5F * intensity) {
                damageCrop(level, randomColumn(level, player, random), random);
            }
            if (profile.hail && intensity > 0.6F && WeatherConfig.HAIL_BREAKS_GLASS.get() && random.nextFloat() < 0.35F * intensity
                    && level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)) {
                HailGlass.hit(level, randomColumn(level, player, random));
            }
            if (profile.acid && intensity > 0.4F && WeatherConfig.ACID_RAIN.get()) {
                AcidRain.tick(level, player, random, randomColumn(level, player, random));
            }
            float lightning = profile.lightning;
            if (local.cell() != null && local.type().vanillaThunder && local.cellWeight() >= 0.5F) {
                lightning += 1.5F; // vanilla only strikes during global thunderstorms
            }
            if (lightning > 0 && WeatherConfig.EXTRA_LIGHTNING.get() && random.nextFloat() < lightning * intensity / 1200.0F) {
                strikeLightning(level, randomColumn(level, player, random));
            }
            if (everySecond) {
                trySpawnWhirlwinds(level, player, local, climate);
                WeatherAdvancements.check(level, state, player, local, climate);
                com.reiko.weather.mob.WeatherCreatures.everySecond(level, player, local, climate);
            }
            com.reiko.weather.farming.Frost.tick(level, player, local);
            com.reiko.weather.nature.FallenLeaves.tick(level, player, climate, profile.wind);
            com.reiko.weather.sky.Meteors.tick(level, player, local);
            LightningEvents.maybeSpawnBallLightning(level, player, local);
            ExtremeWeather.tick(level, player, local, climate);
        }
        recedeFloods(level, state);
        Drought.refill(level, state);
        thawSnapIce(level, state);
        com.reiko.weather.landform.Arroyo.drain(level, state);
        LightningWarnings.tick(level);
        HailGlass.tick(level);
    }

    /** The surface position of a random column near the player, or null if that chunk isn't loaded. */
    @Nullable
    private static BlockPos randomColumn(ServerLevel level, ServerPlayer player, RandomSource random) {
        int x = player.getBlockX() + random.nextInt(AREA * 2 + 1) - AREA;
        int z = player.getBlockZ() + random.nextInt(AREA * 2 + 1) - AREA;
        BlockPos column = new BlockPos(x, 0, z);
        // check first: reading the heightmap of an unloaded chunk would force it to load
        return level.isLoaded(column) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column) : null;
    }

    /** When each player last had desert dust in the air round them. */
    private static final java.util.Map<java.util.UUID, Long> DUST_IN_AIR = new java.util.HashMap<>();

    private static void accumulateSnow(ServerLevel level, @Nullable BlockPos pos, int maxLayers, boolean dusty) {
        if (pos == null || !level.isLoaded(pos)) {
            return;
        }
        Biome biome = level.getBiome(pos).value();
        if (!biome.coldEnoughToSnow(pos) || level.getBrightness(LightLayer.BLOCK, pos) >= 10) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (dusty && (state.is(ModBlocks.DUSTY_SNOW.get()) || state.is(Blocks.SNOW))) {
            // snow falling through the dust brings it down with it
            int layers = state.getValue(SnowLayerBlock.LAYERS);
            if (layers < maxLayers || state.is(Blocks.SNOW)) {
                BlockState grown = ModBlocks.DUSTY_SNOW.get().defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers < maxLayers ? Math.min(8, layers + 1) : layers);
                Block.pushEntitiesUp(state, grown, level, pos);
                level.setBlockAndUpdate(pos, grown);
            }
        } else if (dusty && state.isAir() && ModBlocks.DUSTY_SNOW.get().defaultBlockState().canSurvive(level, pos)) {
            level.setBlockAndUpdate(pos, ModBlocks.DUSTY_SNOW.get().defaultBlockState());
        } else if (state.is(ModBlocks.DUSTY_SNOW.get())) {
            // fresh snow buries the dust
            int layers = Math.min(8, state.getValue(SnowLayerBlock.LAYERS) + 1);
            level.setBlockAndUpdate(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers));
        } else if (state.is(ModBlocks.SNOW_CRUST.get()) || state.is(ModBlocks.SLUSH.get())) {
            // fresh snow on a crust (or on slush, freezing again)
            int layers = Math.min(8, state.getValue(SnowLayerBlock.LAYERS) + 1);
            level.setBlockAndUpdate(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers));
        } else if (state.is(Blocks.SNOW)) {
            int layers = state.getValue(SnowLayerBlock.LAYERS);
            if (layers < maxLayers) {
                BlockState grown = state.setValue(SnowLayerBlock.LAYERS, layers + 1);
                Block.pushEntitiesUp(state, grown, level, pos);
                level.setBlockAndUpdate(pos, grown);
            }
        } else if (state.isAir() && Blocks.SNOW.defaultBlockState().canSurvive(level, pos)) {
            level.setBlockAndUpdate(pos, Blocks.SNOW.defaultBlockState());
        }
    }

    /** Volcanic ash drifting down out of an eruption cloud, burying the ground (and the grass) layer by layer. */
    private static void settleAsh(ServerLevel level, @Nullable BlockPos pos, int maxLayers) {
        if (pos == null || !level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        BlockState ash = ModBlocks.VOLCANIC_ASH.get().defaultBlockState();
        if (state.is(ModBlocks.VOLCANIC_ASH.get())) {
            int layers = state.getValue(com.reiko.weather.block.VolcanicAshBlock.LAYERS);
            if (layers < maxLayers) {
                BlockState grown = state.setValue(com.reiko.weather.block.VolcanicAshBlock.LAYERS, layers + 1);
                Block.pushEntitiesUp(state, grown, level, pos);
                level.setBlockAndUpdate(pos, grown);
            }
        } else if (state.is(Blocks.SNOW) && state.getValue(SnowLayerBlock.LAYERS) <= 2 && ash.canSurvive(level, pos)) {
            level.setBlockAndUpdate(pos, ash); // grey ash over a thin dusting of snow
        } else if ((state.isAir() || state.is(BlockTags.REPLACEABLE) && state.getFluidState().isEmpty() && !state.is(Blocks.SNOW))
                && ash.canSurvive(level, pos)) {
            level.setBlockAndUpdate(pos, ash);
        }
    }

    /** Melts snow layers and frozen lake surfaces wherever it is no longer cold (e.g. after winter). */
    private static void melt(ServerLevel level, @Nullable BlockPos top) {
        if (top == null || level.getBiome(top).value().coldEnoughToSnow(top)) {
            return;
        }
        BlockState state = level.getBlockState(top);
        if (state.is(Blocks.SNOW) || state.is(ModBlocks.DUSTY_SNOW.get())) {
            int layers = state.getValue(SnowLayerBlock.LAYERS);
            if (layers > 1) {
                level.setBlockAndUpdate(top, state.setValue(SnowLayerBlock.LAYERS, layers - 1));
            } else {
                level.removeBlock(top, false);
            }
            return;
        }
        BlockPos below = top.below();
        if (level.getBlockState(below).is(Blocks.ICE) && level.getFluidState(below.below()).is(FluidTags.WATER)) {
            level.setBlockAndUpdate(below, Blocks.WATER.defaultBlockState());
            com.reiko.weather.entity.IceFloeEntity.breakUp(level, below);
        }
    }

    /** A föhn wind melts the snow fast, even where it would usually lie all winter. */
    private static void foehnMelt(ServerLevel level, @Nullable BlockPos top) {
        if (top == null) {
            return;
        }
        BlockState state = level.getBlockState(top);
        if (state.is(Blocks.SNOW) || state.is(ModBlocks.DUSTY_SNOW.get())) {
            int layers = state.getValue(SnowLayerBlock.LAYERS);
            if (layers > 1) {
                level.setBlockAndUpdate(top, state.setValue(SnowLayerBlock.LAYERS, layers - 1));
            } else {
                level.removeBlock(top, false);
            }
        }
    }

    /**
     * A cold snap freezes lakes and rivers even where they don't usually freeze, from the shore inward. That ice
     * is remembered, so it can thaw again afterwards.
     */
    private static void snapFreeze(ServerLevel level, WeatherState state, @Nullable BlockPos top, LocalWeather local) {
        if (top == null || state.snapIce.size() >= 6000) {
            return;
        }
        BlockPos pos = top.below();
        if (!level.getFluidState(pos).isSourceOfType(Fluids.WATER) || !level.getBlockState(top).isAir() || !level.canSeeSky(top)
                || com.reiko.weather.temperature.Temperature.ambient(level, top, local) > -3.0F) {
            return;
        }
        boolean shore = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(pos.relative(d)).getFluidState().isEmpty()) {
                shore = true; // land, or ice that's already formed
                break;
            }
        }
        if (!shore) {
            return;
        }
        level.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
        if (!level.getBiome(pos).value().coldEnoughToSnow(pos)) {
            state.snapIce.add(pos.asLong());
            state.setDirty();
        }
    }

    /** Ice left by a cold snap thaws once the air is above freezing again. */
    private static void thawSnapIce(ServerLevel level, WeatherState state) {
        if (state.snapIce.isEmpty() || level.getGameTime() % 5L != 3L) {
            return;
        }
        for (int i = 0; i < 4 && !state.snapIce.isEmpty(); i++) {
            int index = level.random.nextInt(state.snapIce.size());
            BlockPos pos = BlockPos.of(state.snapIce.getLong(index));
            if (!level.isLoaded(pos)) continue;
            LocalWeather local = WeatherManager.local(level, pos);
            if (local != null && local.type() == WeatherType.COLD_SNAP
                    || com.reiko.weather.temperature.Temperature.ambient(level, pos.above(), local) < 1.0F) {
                continue;
            }
            if (level.getBlockState(pos).is(Blocks.ICE)) {
                level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
                com.reiko.weather.entity.IceFloeEntity.breakUp(level, pos);
            }
            state.snapIce.removeLong(index);
            state.setDirty();
        }
    }

    /** Dust settling out of a haze: it reddens the snow, and leaves a thin film on hard ground (rain washes it off). */
    private static void settleDust(ServerLevel level, @Nullable BlockPos top) {
        if (top == null || !level.isLoaded(top)) {
            return;
        }
        BlockState at = level.getBlockState(top);
        if (at.is(Blocks.SNOW)) {
            level.setBlockAndUpdate(top, ModBlocks.DUSTY_SNOW.get().defaultBlockState().setValue(SnowLayerBlock.LAYERS, at.getValue(SnowLayerBlock.LAYERS)));
            return;
        }
        if (!at.isAir()) {
            return;
        }
        BlockPos below = top.below();
        BlockState ground = level.getBlockState(below);
        if (ground.is(Blocks.SNOW_BLOCK) || ground.is(Blocks.POWDER_SNOW)) {
            if (ModBlocks.DUSTY_SNOW.get().defaultBlockState().canSurvive(level, top)) {
                level.setBlockAndUpdate(top, ModBlocks.DUSTY_SNOW.get().defaultBlockState());
            }
            return;
        }
        // a film of dust on rock, roofs and paths (it just disappears into grass, sand and soil)
        if (ground.is(BlockTags.DIRT) || ground.is(BlockTags.SAND) || ground.is(BlockTags.LEAVES) || ground.is(Blocks.FARMLAND)
                || ground.is(ModBlocks.DESERT_DUST.get()) || !ground.getFluidState().isEmpty() || level.random.nextFloat() > 0.3F) {
            return;
        }
        BlockState dust = ModBlocks.DESERT_DUST.get().defaultBlockState();
        if (dust.canSurvive(level, top)) {
            level.setBlockAndUpdate(top, dust);
        }
    }

    /** Thin, temporary ice: frosted ice melts on its own once the sun is out. */
    private static void glazeWater(ServerLevel level, @Nullable BlockPos top) {
        if (top == null) {
            return;
        }
        BlockPos pos = top.below();
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.WATER) && level.getFluidState(pos).isSourceOfType(Fluids.WATER)) {
            level.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState().setValue(FrostedIceBlock.AGE, 1));
            level.scheduleTick(pos, Blocks.FROSTED_ICE, Mth.nextInt(level.random, 60, 120));
        }
    }

    private static void glazeGround(ServerLevel level, @Nullable BlockPos top) {
        if (top == null || !level.getBlockState(top).isAir() || level.getBrightness(LightLayer.BLOCK, top) > 11) {
            return;
        }
        BlockPos below = top.below();
        BlockState ground = level.getBlockState(below);
        if (ground.getFluidState().isEmpty() && ground.isFaceSturdy(level, below, Direction.UP)) {
            level.setBlockAndUpdate(top, ModBlocks.ICE_GLAZE.get().defaultBlockState());
        }
    }

    private static void placePuddle(ServerLevel level, @Nullable BlockPos top) {
        if (top == null || !level.getBlockState(top).isAir() || !level.isRainingAt(top)) {
            return;
        }
        BlockPos below = top.below();
        if (!isFlatGround(level, below)) {
            return;
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = below.relative(d);
            if (!isFlatGround(level, n) || !level.getBlockState(n.above()).isAir() && !level.getBlockState(n.above()).is(ModBlocks.PUDDLE)) {
                return;
            }
        }
        level.setBlockAndUpdate(top, ModBlocks.PUDDLE.get().defaultBlockState());
    }

    private static boolean isFlatGround(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isFaceSturdy(level, pos, Direction.UP) && state.getFluidState().isEmpty() && !state.is(BlockTags.LEAVES);
    }

    /**
     * Raises low-lying water by placing temporary sources on its surface where it can spill onto the bank.
     * Sources are kept at least 3 blocks from other water at that level so they can't merge into new,
     * permanent sources, and every one is tracked so it can be removed after the storm.
     */
    private static void flood(ServerLevel level, WeatherState state, @Nullable BlockPos top) {
        flood(level, state, top, true);
    }

    /** With {@code needBank} false the water also rises where the banks are high (a swollen river), not just where it can spill. */
    private static void flood(ServerLevel level, WeatherState state, @Nullable BlockPos top, boolean needBank) {
        if (top == null || state.floodBlocks.size() >= WeatherConfig.MAX_FLOOD_BLOCKS.get()) {
            return;
        }
        BlockPos waterPos = top.below();
        if (!level.getFluidState(waterPos).isSourceOfType(Fluids.WATER) || waterPos.getY() > level.getSeaLevel() + 3
                || !level.getBlockState(top).isAir() || !level.canSeeSky(top)) {
            return;
        }
        boolean bank = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = top.relative(d);
            if (level.getBlockState(n).isAir() && level.getFluidState(n.below()).isEmpty()) {
                bank = true;
            }
        }
        if (!bank && needBank) {
            return;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (!level.getFluidState(top.offset(dx, 0, dz)).isEmpty()) {
                    return;
                }
            }
        }
        if (protectedBySandbags(level, top)) {
            return;
        }
        level.setBlockAndUpdate(top, Blocks.WATER.defaultBlockState());
        state.floodBlocks.add(top.asLong());
        state.setDirty();
    }

    private static long lastJamMessage = -10000L;

    /** An ice jam: the river backs up behind the floes and spills over its banks. */
    public static void jamFlood(ServerLevel level, BlockPos at) {
        WeatherState state = WeatherManager.activeState(level);
        if (state == null || !WeatherConfig.ICE_BREAKUP.get()) {
            return;
        }
        int before = state.floodBlocks.size();
        RandomSource random = level.random;
        for (int i = 0; i < 8; i++) {
            BlockPos column = new BlockPos(at.getX() + random.nextInt(17) - 8, 0, at.getZ() + random.nextInt(17) - 8);
            if (level.isLoaded(column)) {
                flood(level, state, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column), true); // spilling over the banks
            }
        }
        if (state.floodBlocks.size() > before && level.getGameTime() - lastJamMessage > 1200L) {
            lastJamMessage = level.getGameTime();
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(at.getX(), at.getY(), at.getZ()) < 64 * 64) {
                    player.displayClientMessage(Component.translatable("message.reikos_weather.ice_jam").withStyle(s -> s.withColor(0x9FD4FF)), true);
                }
            }
        }
    }

    /** Sandbags stop flood water from being placed within two blocks of them. */
    public static boolean protectedBySandbags(Level level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 1, 2))) {
            if (level.getBlockState(p).is(ModBlocks.SANDBAG.get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hurricanes push the sea one block up onto low-lying coasts. Like flash floods, surge sources are
     * kept at least three blocks apart (flowing water fills the gaps), so vanilla can never join them into
     * permanent new sources, and every one is tracked and removed after the storm.
     */
    private static void surge(ServerLevel level, WeatherState state, @Nullable BlockPos top, boolean tide) {
        if (top == null || state.floodBlocks.size() >= WeatherConfig.MAX_FLOOD_BLOCKS.get()) {
            return;
        }
        int sea = level.getSeaLevel();
        BlockPos pos;
        // (a supermoon's king tide washes a block higher up the beach)
        boolean king = tide && Supermoons.kingTide(level) && top.getY() == sea + 1 && level.getBiome(top).is(BiomeTags.IS_BEACH);
        if ((top.getY() == sea || king) && level.getBlockState(top).isAir()) {
            pos = top;
            boolean flat = level.getBlockState(pos.below()).is(Blocks.MUD);
            if (tide && !(Tides.flooding(level) && level.getBiome(pos).is(BiomeTags.IS_BEACH) || flat && Tides.floodingFlats(level))) {
                return; // high tide only washes over beaches (at spring tides) and tidal flats (every high tide)
            }
        } else if (!tide && top.getY() == sea + 1 && level.getFluidState(top.below()).is(FluidTags.WATER) && !level.getFluidState(top.below()).isSource()) {
            pos = top.below(); // already awash with surge water: deepen it
        } else {
            return;
        }
        BlockPos below = pos.below();
        // only over land: water on top of the open sea would just float on it (and sink any boat under it)
        boolean onLand = level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && level.getFluidState(below).isEmpty();
        if (!level.canSeeSky(pos) || !onLand) {
            return;
        }
        boolean nearWater = false;
        for (int dx = -3; dx <= 3 && !nearWater; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                BlockPos n = pos.offset(dx, 0, dz);
                if ((dx != 0 || dz != 0) && (level.getFluidState(n).is(FluidTags.WATER) || level.getFluidState(n.below()).is(FluidTags.WATER))) {
                    nearWater = true;
                    break;
                }
            }
        }
        boolean coastal = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            var biome = level.getBiome(pos.relative(d, 6));
            if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_BEACH)) {
                coastal = true;
            }
        }
        if (!nearWater || !coastal || protectedBySandbags(level, pos)) {
            return;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (level.getFluidState(pos.offset(dx, 0, dz)).isSourceOfType(Fluids.WATER)) {
                    return;
                }
            }
        }
        level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        state.floodBlocks.add(pos.asLong());
        state.setDirty();
    }

    /** The first few days of spring, when melting snow swells the rivers. */
    public static boolean snowmeltActive() {
        if (!WeatherConfig.SNOWMELT_FLOODS.get()) {
            return false;
        }
        Seasons.Info info = WeatherManager.seasonInfo();
        return info.season() == Season.SPRING && info.dayOfSeason() >= 0 && info.dayOfSeason() < Math.max(1, info.daysPerSeason() * 3 / 7);
    }

    private static void recedeFloods(ServerLevel level, WeatherState state) {
        if (state.floodBlocks.isEmpty() || level.getGameTime() % 5L != 0L) {
            return;
        }
        boolean floodsOn = WeatherConfig.FLASH_FLOODS.get();
        boolean surgeOn = WeatherConfig.STORM_SURGE.get();
        for (int i = 0; i < 4 && !state.floodBlocks.isEmpty(); i++) {
            int index = level.random.nextInt(state.floodBlocks.size());
            BlockPos pos = BlockPos.of(state.floodBlocks.getLong(index));
            if (!level.isLoaded(pos)) {
                continue;
            }
            LocalWeather local = WeatherManager.local(level, pos);
            if (local != null && (floodsOn && local.profile().floods || surgeOn && local.type() == WeatherType.HURRICANE)) {
                continue; // still pouring
            }
            if (!level.getEntitiesOfClass(com.reiko.weather.entity.IceFloeEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(12),
                    com.reiko.weather.entity.IceFloeEntity::jammed).isEmpty()) {
                continue; // the ice jam is still holding the water back
            }
            if (Tides.flooding(level) && level.getBiome(pos).is(BiomeTags.IS_BEACH)
                    || Tides.floodingFlats(level) && level.getBlockState(pos.below()).is(Blocks.MUD)
                    || (snowmeltActive() || rainOnSnow > 0.1F) && level.getBiome(pos).is(BiomeTags.IS_RIVER)) {
                continue; // high tide, or the spring thaw is still running off
            }
            if (level.getFluidState(pos).isSourceOfType(Fluids.WATER)) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                Coasts.leaveSilt(level, state, pos);
                if (pos.getY() == level.getSeaLevel()) {
                    // a surge can leave stray sources where vanilla joined two of ours: drain those too
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        BlockPos n = pos.relative(d);
                        // nothing natural sits on top of the sea, so a source here was left by the surge
                        if (level.getFluidState(n).isSourceOfType(Fluids.WATER) && !state.floodBlocks.contains(n.asLong())) {
                            level.setBlockAndUpdate(n, Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }
            state.floodBlocks.removeLong(index);
            state.setDirty();
        }
    }

    private static void damageCrop(ServerLevel level, @Nullable BlockPos pos, RandomSource random) {
        if (pos == null || !level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CropBlock crop && random.nextFloat() < 0.35F && !Greenhouse.underGlass(level, pos)) {
            int age = crop.getAge(state);
            if (age > 0) {
                level.setBlockAndUpdate(pos, crop.getStateForAge(age - 1));
            }
        }
    }

    private static void strikeLightning(ServerLevel level, @Nullable BlockPos pos) {
        if (pos == null || !level.isLoaded(pos)) {
            return;
        }
        if (WeatherConfig.LIGHTNING_WARNINGS.get()) {
            LightningWarnings.schedule(level, pos); // sparks crackle around the spot for a few seconds first
            return;
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(Vec3.atBottomCenterOf(pos));
            level.addFreshEntity(bolt);
        }
    }

    // ------------------------------------------------------------------ tornadoes, waterspouts, dust devils

    private static void trySpawnWhirlwinds(ServerLevel level, ServerPlayer player, LocalWeather local, Climate climate) {
        WeatherProfile profile = local.profile();
        RandomSource random = level.random;
        if (profile.tornadoes && local.intensity() >= 0.8F && WeatherConfig.TORNADOES.get()
                && random.nextDouble() < WeatherConfig.TORNADO_CHANCE.get()) {
            BlockPos ground = randomGroundNear(level, player, 48, 96, false);
            if (ground != null) spawnHeadingTo(level, player, ground, TornadoEntity.Kind.TORNADO);
        } else if (profile.waterspouts && local.intensity() >= 0.8F && WeatherConfig.WATERSPOUTS.get()
                && random.nextDouble() < WeatherConfig.TORNADO_CHANCE.get() * 1.5) {
            BlockPos water = randomGroundNear(level, player, 40, 90, true);
            if (water != null) spawnHeadingTo(level, player, water, TornadoEntity.Kind.WATERSPOUT);
        } else if (WeatherConfig.WHIRLWINDS.get() && trySpawnMinorWhirls(level, player, local, climate)) {
            return;
        } else if (climate == Climate.ARID && WeatherConfig.DUST_DEVILS.get() && level.isDay() && random.nextFloat() < 0.005F
                && (local.type() == WeatherType.CLEAR || local.type() == WeatherType.HEATWAVE || local.type() == WeatherType.WINDY)) {
            BlockPos ground = randomGroundNear(level, player, 16, 48, false);
            if (ground != null && Climate.at(level, ground) == Climate.ARID) spawnHeadingTo(level, player, ground, TornadoEntity.Kind.DUST_DEVIL);
        }
    }

    /**
     * Landspouts under growing storms on open plains, gustnadoes along a storm's gust front, steam devils over open
     * water in bitter cold and snow devils on sunny, windy snowfields.
     */
    private static boolean trySpawnMinorWhirls(ServerLevel level, ServerPlayer player, LocalWeather local, Climate climate) {
        RandomSource random = level.random;
        WeatherType type = local.type();
        float wind = local.profile().wind * local.intensity();
        if (type == WeatherType.THUNDERSTORM && climate != Climate.COLD && local.intensity() > 0.5F && random.nextFloat() < 0.0012F) {
            BlockPos ground = randomGroundNear(level, player, 40, 90, false);
            if (ground != null && level.getBiome(ground).is(net.minecraft.tags.BiomeTags.IS_FOREST) == false) {
                spawnHeadingTo(level, player, ground, TornadoEntity.Kind.LANDSPOUT);
                return true;
            }
        }
        if ((type == WeatherType.COLD_FRONT || type == WeatherType.DERECHO || type == WeatherType.THUNDERSTORM || type == WeatherType.HABOOB)
                && wind > 0.5F && random.nextFloat() < 0.004F) {
            BlockPos ground = randomGroundNear(level, player, 16, 48, false);
            if (ground != null) {
                spawnHeadingTo(level, player, ground, TornadoEntity.Kind.GUSTNADO);
                return true;
            }
        }
        if (random.nextFloat() < 0.004F && climate != Climate.ARID) {
            BlockPos at = BlockPos.containing(player.position());
            float t = com.reiko.weather.temperature.Temperature.ambient(level, at, local);
            if (t < -12.0F && wind < 0.45F && level.getDayTime() % 24000L < 4000L) {
                BlockPos water = randomGroundNear(level, player, 12, 40, true);
                if (water != null && !level.getBlockState(water.below()).is(net.minecraft.world.level.block.Blocks.ICE)) {
                    spawnHeadingTo(level, player, water, TornadoEntity.Kind.STEAM_DEVIL);
                    return true;
                }
            }
            if (climate == Climate.COLD && level.isDay() && (type == WeatherType.CLEAR || type == WeatherType.WINDY || type == WeatherType.COLD_SNAP)
                    && wind > 0.2F) {
                BlockPos ground = randomGroundNear(level, player, 16, 48, false);
                if (ground != null && (level.getBlockState(ground).is(net.minecraft.world.level.block.Blocks.SNOW)
                        || level.getBlockState(ground.below()).is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK))) {
                    spawnHeadingTo(level, player, ground, TornadoEntity.Kind.SNOW_DEVIL);
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    private static BlockPos randomGroundNear(ServerLevel level, ServerPlayer player, double min, double max, boolean water) {
        float angle = level.random.nextFloat() * Mth.TWO_PI;
        double distance = min + level.random.nextDouble() * (max - min);
        BlockPos column = BlockPos.containing(player.getX() + Mth.cos(angle) * distance, 0, player.getZ() + Mth.sin(angle) * distance);
        if (!level.isLoaded(column)) {
            return null;
        }
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        boolean isWater = level.getFluidState(top.below()).is(FluidTags.WATER);
        if (isWater != water) {
            return null;
        }
        if (water) {
            // needs open water, not a pond
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos far = top.below().relative(d, 6);
                if (!level.isLoaded(far) || !level.getFluidState(far).is(FluidTags.WATER)) return null;
            }
        }
        return top;
    }

    private static void spawnHeadingTo(ServerLevel level, ServerPlayer player, BlockPos at, TornadoEntity.Kind kind) {
        int limit = kind.small() ? 2 : WeatherConfig.MAX_TORNADOES.get();
        long existing = level.getEntities(ModEntities.TORNADO.get(), e -> e.kind() == kind).size();
        if (existing >= limit) {
            return;
        }
        // head roughly toward the player so it is actually witnessed
        float heading = (float) Mth.atan2(player.getZ() - at.getZ(), player.getX() - at.getX()) + (level.random.nextFloat() - 0.5F) * 1.2F;
        spawnTornado(level, Vec3.atBottomCenterOf(at), heading, kind);
    }

    public static TornadoEntity spawnTornado(ServerLevel level, Vec3 pos, float heading, TornadoEntity.Kind kind) {
        RandomSource r = level.random;
        TornadoEntity tornado = new TornadoEntity(ModEntities.TORNADO.get(), level);
        tornado.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        switch (kind) {
            case TORNADO -> tornado.configure(kind, heading, 1200 + r.nextInt(1200), 5.0F + r.nextFloat() * 4.0F, 70.0F);
            case WATERSPOUT -> tornado.configure(kind, heading, 900 + r.nextInt(900), 3.0F + r.nextFloat() * 2.0F, 55.0F);
            case DUST_DEVIL -> tornado.configure(kind, heading, 300 + r.nextInt(400), 1.2F + r.nextFloat() * 0.8F, 12.0F + r.nextFloat() * 10.0F);
            case FIRE_WHIRL -> tornado.configure(kind, heading, 300 + r.nextInt(500), 1.6F + r.nextFloat() * 1.2F, 18.0F + r.nextFloat() * 14.0F);
            case LANDSPOUT -> tornado.configure(kind, heading, 500 + r.nextInt(700), 1.4F + r.nextFloat() * 0.8F, 55.0F + r.nextFloat() * 15.0F);
            case GUSTNADO -> tornado.configure(kind, heading, 160 + r.nextInt(160), 2.0F + r.nextFloat() * 1.5F, 10.0F + r.nextFloat() * 8.0F);
            case STEAM_DEVIL -> tornado.configure(kind, heading, 200 + r.nextInt(300), 0.8F + r.nextFloat() * 0.6F, 6.0F + r.nextFloat() * 8.0F);
            case SNOW_DEVIL -> tornado.configure(kind, heading, 250 + r.nextInt(350), 1.0F + r.nextFloat() * 0.8F, 8.0F + r.nextFloat() * 8.0F);
        }
        level.addFreshEntity(tornado);
        if (!kind.small() && kind != TornadoEntity.Kind.FIRE_WHIRL && WeatherConfig.TORNADO_WARNINGS.get()) {
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(pos) < 200 * 200) {
                    player.displayClientMessage(Component.translatable("message.reikos_weather." + kind.id() + "_warning")
                            .withStyle(s -> s.withColor(0xFF5555).withBold(true)), true);
                }
            }
        }
        return tornado;
    }

    // ------------------------------------------------------------------ entity effects

    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide || entity instanceof Player && entity.isSpectator()
                || entity instanceof net.minecraft.world.entity.projectile.FishingHook) {
            return; // (a bobber blown away by the wind would make storm fishing impossible)
        }
        WeatherState state = WeatherManager.activeState(level);
        if (state == null || state.cells.isEmpty() && !WeatherProfiles.affectsEntities(state.type)) {
            return;
        }
        BlockPos eyes = BlockPos.containing(entity.getEyePosition());
        if (!Wind.isExposed(level, eyes) || entity instanceof LivingEntity && com.reiko.weather.block.TentBlock.isInside(entity)) {
            return;
        }
        Climate entityClimate = Climate.at(level, eyes);
        LocalWeather local = Microclimate.apply(level, entityClimate, eyes.getX(), eyes.getZ(), state.windAngle,
                LocalWeather.resolve(state.type, CustomWeathers.of(state), state.intensity(), state.cells, entityClimate, entity.getX(), entity.getZ()));
        WeatherProfile profile = local.profile();
        float intensity = local.intensity();

        // the local player is pushed client-side for smooth movement
        if (!(entity instanceof Player) && WeatherConfig.WIND_PUSHES_ENTITIES.get()) {
            float[] breeze = Breezes.adjust(level, eyes.getX(), eyes.getZ(), state.windAngle, profile.wind * intensity);
            Wind.push(entity, breeze[0], breeze[1], level.getGameTime());
        }
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            return;
        }
        if (profile.hail && (entity.tickCount + entity.getId()) % 20 == 0 && level.random.nextFloat() < 0.25F * intensity) {
            hailHit(living);
        }
        // body temperature (ours, or Tough As Nails') replaces the simple weather chill and heat exhaustion for players
        boolean temperatureRules = living instanceof Player && (TemperatureEffects.enabled() || com.reiko.weather.compat.Compat.toughAsNailsHandlesTemperature());
        if (profile.freeze > 0.0F && WeatherConfig.FREEZING.get() && !temperatureRules) {
            chill(living, profile.freeze * intensity, eyes);
        }
        if (profile.acid && intensity > 0.4F && living instanceof Player player && entity.tickCount % 120 == 0 && WeatherConfig.ACID_RAIN.get()
                && !player.getAbilities().invulnerable && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && !player.isUnderWater()) {
            player.hurt(level.damageSources().source(ModDamageTypes.ACID_RAIN), 1.0F); // the acid drizzle stings
        }
        if (profile.ashLayers > 0 && intensity > 0.4F && living instanceof Player player && entity.tickCount % 40 == 0
                && !player.getAbilities().invulnerable && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            // breathing in volcanic ash; anything over your face helps
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.WEAKNESS, 120, 0, false, false, true));
        }
        if (profile.heat && !temperatureRules && living instanceof Player player && level.isDay() && entity.tickCount % 20 == 0
                && !player.getAbilities().invulnerable && !player.hasEffect(MobEffects.FIRE_RESISTANCE) && !player.isInWaterOrRain()) {
            float exhaustion = (float) (WeatherConfig.HEAT_EXHAUSTION.get() * intensity);
            if (player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.WINTER_COAT.get())) {
                exhaustion *= 2.0F; // dressed for the wrong weather
            }
            player.causeFoodExhaustion(exhaustion);
        }
    }

    private static void hailHit(LivingEntity living) {
        double damage = WeatherConfig.HAIL_DAMAGE.get();
        if (damage <= 0.0) {
            return;
        }
        // an umbrella in either hand shields you completely
        for (ItemStack held : List.of(living.getMainHandItem(), living.getOffhandItem())) {
            if (held.is(ModItems.UMBRELLA.get())) {
                if (living.getRandom().nextInt(3) == 0) {
                    held.hurtAndBreak(1, living, held == living.getMainHandItem() ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                }
                if (living instanceof ServerPlayer player) {
                    WeatherAdvancements.award(player, "under_my_umbrella");
                }
                return;
            }
        }
        ItemStack helmet = living.getItemBySlot(EquipmentSlot.HEAD);
        if (!helmet.isEmpty() && helmet.getItem() instanceof ArmorItem) {
            if (living.getRandom().nextInt(4) == 0) {
                helmet.hurtAndBreak(1, living, EquipmentSlot.HEAD);
            }
            return;
        }
        if (!(living instanceof Player) && !(living instanceof Enemy) && living.getHealth() <= damage + 1.0) {
            return; // livestock gets bruised, but hail alone won't wipe out a farm
        }
        living.hurt(living.level().damageSources().source(ModDamageTypes.HAIL), (float) damage);
    }

    /**
     * Works with vanilla's powder-snow freezing: vanilla thaws 2 ticks/tick, so adding slightly more
     * makes creatures freeze gradually (a blizzard takes ~18 s to freeze an unprotected player solid).
     * Only blizzard-strength cold can freeze players to death; mobs and weaker cold are just slowed.
     * Leather armor and the winter coat make you immune (vanilla's freeze-immune wearables tag).
     */
    private static void chill(LivingEntity living, float rate, BlockPos eyes) {
        if (!living.canFreeze() || living instanceof Player p && p.getAbilities().invulnerable) {
            return;
        }
        if (living.level().getBrightness(LightLayer.BLOCK, eyes) >= 11 || living.isOnFire()) {
            return; // warming up by a fire, torch or lava
        }
        int required = living.getTicksRequiredToFreeze();
        boolean lethal = rate >= 0.8F && living instanceof Player;
        int cap = lethal ? required + 2 : (int) (required * Mth.clamp(rate, 0.2F, 0.9F));
        int add = 2 + (living.getRandom().nextFloat() < rate * 0.4F ? 1 : 0);
        int current = living.getTicksFrozen();
        if (current < cap) {
            living.setTicksFrozen(Math.min(cap, current + add));
        }
    }

    // ------------------------------------------------------------------ crops

    /** Rice needs water: rain or the monsoon make it shoot up, standing water next to it keeps it going, otherwise it stalls. */
    private static void riceGrowth(CropGrowEvent.Pre event, ServerLevel level, BlockPos pos) {
        RandomSource random = level.random;
        boolean rained = level.isRainingAt(pos.above()) || Monsoon.monsoonAt(level.getBiome(pos));
        boolean paddy = false;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, -1, 1))) {
            if (level.getFluidState(p).is(FluidTags.WATER)) {
                paddy = true;
                break;
            }
        }
        if (rained) {
            if (random.nextFloat() < 0.4F) event.setResult(CropGrowEvent.Pre.Result.GROW);
        } else if (!paddy && random.nextFloat() < 0.8F) {
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        } else if (WeatherManager.seasonInfo().season() == Season.WINTER && random.nextFloat() < 0.7F) {
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        }
    }

    /** Kale likes it cool: it grows on through the winter (with no frost damage) but slows right down in the heat. */
    private static void kaleGrowth(CropGrowEvent.Pre event, ServerLevel level, BlockPos pos, LocalWeather local) {
        float celsius = com.reiko.weather.temperature.Temperature.ambient(level, pos, local);
        if (celsius > 26.0F || local.profile().heat) {
            if (level.random.nextFloat() < 0.6F) event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        } else if (celsius < 10.0F && level.random.nextFloat() < 0.2F) {
            event.setResult(CropGrowEvent.Pre.Result.GROW);
        }
    }

    public static void onCropGrow(CropGrowEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getResult() != CropGrowEvent.Pre.Result.DEFAULT) {
            return;
        }
        BlockPos pos = event.getPos();
        LocalWeather local = WeatherManager.local(level, pos);
        if (local == null) {
            return;
        }
        if (event.getState().getBlock() instanceof com.reiko.weather.block.RiceCropBlock) {
            riceGrowth(event, level, pos);
            return;
        }
        if (event.getState().getBlock() instanceof com.reiko.weather.block.KaleCropBlock) {
            kaleGrowth(event, level, pos, local);
            return;
        }
        if (!level.canSeeSky(pos)) {
            return;
        }
        WeatherProfile profile = local.profile();
        WeatherState weather = WeatherManager.state(level);
        if (!weather.silt.isEmpty() && weather.silt.contains(pos.below().asLong()) && level.random.nextFloat() < 0.3F) {
            // fresh silt left by a flood: rich soil
            event.setResult(CropGrowEvent.Pre.Result.GROW);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.0);
            return;
        }
        if (Greenhouse.underGlass(level, pos)) {
            // no rain gets in, but the warmth keeps crops growing through winter
            if (WeatherManager.seasonInfo().season() == Season.WINTER) {
                level.players().stream().filter(p -> p.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 32 * 32)
                        .forEach(p -> WeatherAdvancements.award(p, "green_thumb"));
            }
            if (profile.heat && WeatherConfig.HEAT_SLOWS_CROPS.get() && level.random.nextFloat() < 0.5F) {
                event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
            }
            return;
        }
        if (profile.ashLayers > 0 && level.random.nextFloat() < 0.4F) {
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW); // the ash cloud blots out the sun
        } else if (profile.acid && level.random.nextFloat() < 0.4F) {
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW); // acid rain burns the leaves
        } else if (profile.cropBoost && WeatherConfig.RAIN_BOOSTS_CROPS.get() && level.random.nextFloat() < 0.25F) {
            event.setResult(CropGrowEvent.Pre.Result.GROW);
        } else if (profile.heat && WeatherConfig.HEAT_SLOWS_CROPS.get() && level.random.nextFloat() < 0.5F) {
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        } else if (WeatherManager.seasonInfo().season() == Season.WINTER && WeatherConfig.SEASONAL_CROPS.get()
                && level.random.nextFloat() < 0.5F) {
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        }
    }

    private WeatherHazards() {}
}
