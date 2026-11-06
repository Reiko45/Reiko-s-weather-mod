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

    static void tickLevel(ServerLevel level, WeatherState state) {
        RandomSource random = level.random;
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

            int maxLayers = Math.min(profile.snowLayers, WeatherConfig.MAX_SNOW_LAYERS.get());
            if (maxLayers > 0 && intensity > 0) {
                int attempts = Mth.ceil(6 * intensity);
                for (int i = 0; i < attempts; i++) {
                    accumulateSnow(level, randomColumn(level, player, random), maxLayers);
                }
            }
            if (WeatherConfig.SNOW_MELT.get()) {
                melt(level, randomColumn(level, player, random));
                melt(level, randomColumn(level, player, random));
            }
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
                    surge(level, state, randomColumn(level, player, random));
                }
            }
            if (profile.hail && WeatherConfig.HAIL_DAMAGES_CROPS.get() && random.nextFloat() < 0.5F * intensity) {
                damageCrop(level, randomColumn(level, player, random), random);
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
            com.reiko.weather.sky.Meteors.tick(level, player, local);
            LightningEvents.maybeSpawnBallLightning(level, player, local);
            ExtremeWeather.tick(level, player, local, climate);
        }
        recedeFloods(level, state);
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

    private static void accumulateSnow(ServerLevel level, @Nullable BlockPos pos, int maxLayers) {
        if (pos == null || !level.isLoaded(pos)) {
            return;
        }
        Biome biome = level.getBiome(pos).value();
        if (!biome.coldEnoughToSnow(pos) || level.getBrightness(LightLayer.BLOCK, pos) >= 10) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.SNOW)) {
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

    /** Melts snow layers and frozen lake surfaces wherever it is no longer cold (e.g. after winter). */
    private static void melt(ServerLevel level, @Nullable BlockPos top) {
        if (top == null || level.getBiome(top).value().coldEnoughToSnow(top)) {
            return;
        }
        BlockState state = level.getBlockState(top);
        if (state.is(Blocks.SNOW)) {
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
        if (!bank) {
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
    private static void surge(ServerLevel level, WeatherState state, @Nullable BlockPos top) {
        if (top == null || state.floodBlocks.size() >= WeatherConfig.MAX_FLOOD_BLOCKS.get()) {
            return;
        }
        int sea = level.getSeaLevel();
        BlockPos pos;
        if (top.getY() == sea && level.getBlockState(top).isAir()) {
            pos = top;
        } else if (top.getY() == sea + 1 && level.getFluidState(top.below()).is(FluidTags.WATER) && !level.getFluidState(top.below()).isSource()) {
            pos = top.below(); // already awash with surge water: deepen it
        } else {
            return;
        }
        BlockPos below = pos.below();
        if (!level.canSeeSky(pos) || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && !level.getFluidState(below).is(FluidTags.WATER)) {
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
            if (level.getFluidState(pos).isSourceOfType(Fluids.WATER)) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
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
        } else if (climate == Climate.ARID && WeatherConfig.DUST_DEVILS.get() && level.isDay() && random.nextFloat() < 0.005F
                && (local.type() == WeatherType.CLEAR || local.type() == WeatherType.HEATWAVE || local.type() == WeatherType.WINDY)) {
            BlockPos ground = randomGroundNear(level, player, 16, 48, false);
            if (ground != null && Climate.at(level, ground) == Climate.ARID) spawnHeadingTo(level, player, ground, TornadoEntity.Kind.DUST_DEVIL);
        }
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
        int limit = kind == TornadoEntity.Kind.DUST_DEVIL ? 2 : WeatherConfig.MAX_TORNADOES.get();
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
        }
        level.addFreshEntity(tornado);
        if (kind != TornadoEntity.Kind.DUST_DEVIL && WeatherConfig.TORNADO_WARNINGS.get()) {
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
        if (level.isClientSide || entity instanceof Player && entity.isSpectator()) {
            return;
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
            Wind.push(entity, state.windAngle, profile.wind * intensity, level.getGameTime());
        }
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            return;
        }
        if (profile.hail && (entity.tickCount + entity.getId()) % 20 == 0 && level.random.nextFloat() < 0.25F * intensity) {
            hailHit(living);
        }
        boolean temperatureRules = living instanceof Player && TemperatureEffects.enabled();
        if (profile.freeze > 0.0F && WeatherConfig.FREEZING.get() && !temperatureRules) {
            chill(living, profile.freeze * intensity, eyes);
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

    public static void onCropGrow(CropGrowEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getResult() != CropGrowEvent.Pre.Result.DEFAULT) {
            return;
        }
        BlockPos pos = event.getPos();
        LocalWeather local = WeatherManager.local(level, pos);
        if (local == null || !level.canSeeSky(pos)) {
            return;
        }
        WeatherProfile profile = local.profile();
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
        if (profile.cropBoost && WeatherConfig.RAIN_BOOSTS_CROPS.get() && level.random.nextFloat() < 0.25F) {
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
