package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.entity.TornadoEntity;
import com.reiko.weather.registry.ModDamageTypes;
import com.reiko.weather.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
        float intensity = state.intensity();
        if (intensity <= 0.0F) {
            return;
        }
        RandomSource random = level.random;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            WeatherProfile profile = WeatherProfiles.get(state.type, Climate.at(level, player.blockPosition()));

            int maxLayers = Math.min(profile.snowLayers, WeatherConfig.MAX_SNOW_LAYERS.get());
            if (maxLayers > 0) {
                int attempts = Mth.ceil(6 * intensity);
                for (int i = 0; i < attempts; i++) {
                    accumulateSnow(level, randomColumn(level, player, random), maxLayers);
                }
            }
            if (profile.glazeWater && WeatherConfig.GLAZE_WATER.get() && random.nextFloat() < intensity) {
                glazeWater(level, randomColumn(level, player, random));
            }
            if (profile.hail && WeatherConfig.HAIL_DAMAGES_CROPS.get() && random.nextFloat() < 0.5F * intensity) {
                damageCrop(level, randomColumn(level, player, random), random);
            }
            if (profile.lightning > 0 && WeatherConfig.EXTRA_LIGHTNING.get()
                    && random.nextFloat() < profile.lightning * intensity / 1200.0F) {
                strikeLightning(level, randomColumn(level, player, random));
            }
        }

        if (state.type == WeatherType.SUPERCELL && level.getGameTime() % 20L == 0L && intensity >= 1.0F) {
            trySpawnTornado(level, state);
        }
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

    private static void accumulateSnow(ServerLevel level, BlockPos pos, int maxLayers) {
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

    /** Thin, temporary ice: frosted ice melts on its own once the sun is out. */
    private static void glazeWater(ServerLevel level, BlockPos top) {
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

    private static void damageCrop(ServerLevel level, BlockPos pos, RandomSource random) {
        if (pos == null || !level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CropBlock crop && random.nextFloat() < 0.35F) {
            int age = crop.getAge(state);
            if (age > 0) {
                level.setBlockAndUpdate(pos, crop.getStateForAge(age - 1));
            }
        }
    }

    private static void strikeLightning(ServerLevel level, BlockPos pos) {
        if (pos == null || !level.isLoaded(pos)) {
            return;
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(Vec3.atBottomCenterOf(pos));
            level.addFreshEntity(bolt);
        }
    }

    private static void trySpawnTornado(ServerLevel level, WeatherState state) {
        if (!WeatherConfig.TORNADOES.get() || level.random.nextDouble() >= WeatherConfig.TORNADO_CHANCE.get()) {
            return;
        }
        List<? extends TornadoEntity> existing = level.getEntities(ModEntities.TORNADO.get(), e -> true);
        if (existing.size() >= WeatherConfig.MAX_TORNADOES.get()) {
            return;
        }
        List<ServerPlayer> candidates = level.players().stream()
                .filter(p -> !p.isSpectator())
                .filter(p -> WeatherProfiles.get(state.type, Climate.at(level, p.blockPosition())).tornadoes)
                .toList();
        if (candidates.isEmpty()) {
            return;
        }
        ServerPlayer target = candidates.get(level.random.nextInt(candidates.size()));
        float angle = level.random.nextFloat() * Mth.TWO_PI;
        double distance = 48 + level.random.nextDouble() * 48;
        BlockPos column = BlockPos.containing(target.getX() + Mth.cos(angle) * distance, 0, target.getZ() + Mth.sin(angle) * distance);
        if (!level.isLoaded(column)) {
            return;
        }
        BlockPos spawn = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        if (!level.getFluidState(spawn.below()).isEmpty()) {
            return;
        }
        // head roughly toward the player so the tornado is actually witnessed
        float heading = (float) Mth.atan2(target.getZ() - spawn.getZ(), target.getX() - spawn.getX())
                + (level.random.nextFloat() - 0.5F) * 1.2F;
        spawnTornado(level, Vec3.atBottomCenterOf(spawn), heading);
    }

    public static TornadoEntity spawnTornado(ServerLevel level, Vec3 pos, float heading) {
        TornadoEntity tornado = new TornadoEntity(ModEntities.TORNADO.get(), level);
        tornado.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        tornado.configure(heading, 1200 + level.random.nextInt(1200), 5.0F + level.random.nextFloat() * 4.0F);
        level.addFreshEntity(tornado);
        if (WeatherConfig.TORNADO_WARNINGS.get()) {
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(pos) < 200 * 200) {
                    player.displayClientMessage(Component.translatable("message.reikos_weather.tornado_warning")
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
        if (state == null || !WeatherProfiles.affectsEntities(state.type)) {
            return;
        }
        BlockPos eyes = BlockPos.containing(entity.getEyePosition());
        if (!Wind.isExposed(level, eyes)) {
            return;
        }
        WeatherProfile profile = WeatherProfiles.get(state.type, Climate.at(level, eyes));
        float intensity = state.intensity();

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
        if (profile.freeze > 0.0F && WeatherConfig.FREEZING.get()) {
            chill(living, profile.freeze * intensity, eyes);
        }
        if (profile.heat && living instanceof Player player && level.isDay() && entity.tickCount % 20 == 0
                && !player.getAbilities().invulnerable && !player.hasEffect(MobEffects.FIRE_RESISTANCE) && !player.isInWaterOrRain()) {
            player.causeFoodExhaustion((float) (WeatherConfig.HEAT_EXHAUSTION.get() * intensity));
        }
    }

    private static void hailHit(LivingEntity living) {
        double damage = WeatherConfig.HAIL_DAMAGE.get();
        if (damage <= 0.0) {
            return;
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
        WeatherState state = WeatherManager.activeState(level);
        if (state == null) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!level.canSeeSky(pos)) {
            return;
        }
        WeatherProfile profile = WeatherProfiles.get(state.type, Climate.at(level, pos));
        if (profile.cropBoost && WeatherConfig.RAIN_BOOSTS_CROPS.get() && level.random.nextFloat() < 0.25F) {
            event.setResult(CropGrowEvent.Pre.Result.GROW);
        } else if (profile.heat && WeatherConfig.HEAT_SLOWS_CROPS.get() && level.random.nextFloat() < 0.5F) {
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        }
    }

    private WeatherHazards() {}
}
