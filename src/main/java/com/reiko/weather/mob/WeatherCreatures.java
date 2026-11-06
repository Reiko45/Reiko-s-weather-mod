package com.reiko.weather.mob;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModItems;
import com.reiko.weather.registry.ModVillagers;
import com.reiko.weather.weather.Climate;
import com.reiko.weather.weather.LocalWeather;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.WeatherAdvancements;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.WeatherProfile;
import com.reiko.weather.weather.WeatherType;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.BasicItemListing;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

import java.util.List;

/** Villagers, sheep and polar bears reacting to the weather and seasons, and the meteorologist's trades. */
public final class WeatherCreatures {

    // ------------------------------------------------------------------ villagers head home in storms

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide || villager.tickCount % 80 != 0
                || !WeatherConfig.SPEC.isLoaded() || !WeatherConfig.VILLAGERS_SHELTER.get() || villager.isSleeping()) {
            return;
        }
        LocalWeather local = WeatherManager.local(villager.level(), villager.blockPosition());
        if (local != null && isStormy(local)) {
            // the same reaction as hearing the village bell: run home and stay inside for a while
            villager.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, villager.level().getGameTime());
        }
    }

    static boolean isStormy(LocalWeather local) {
        if (local.intensity() < 0.6F) return false;
        WeatherProfile p = local.profile();
        WeatherType t = local.type();
        return t.vanillaThunder || t.isSevere() || p.hail || p.wind >= 0.7F || p.freeze >= 0.8F || t == WeatherType.HEAVY_RAIN;
    }

    // ------------------------------------------------------------------ polar bears roam in blizzards

    public static void everySecond(ServerLevel level, ServerPlayer player, LocalWeather local, Climate climate) {
        if (local.type() != WeatherType.BLIZZARD || climate != Climate.COLD || !WeatherConfig.POLAR_BEARS.get()
                || local.intensity() < 0.8F || level.random.nextInt(90) != 0) {
            return;
        }
        if (level.getEntitiesOfClass(PolarBear.class, player.getBoundingBox().inflate(64)).size() >= 2) {
            return;
        }
        float angle = level.random.nextFloat() * Mth.TWO_PI;
        double distance = 24 + level.random.nextDouble() * 24;
        BlockPos column = BlockPos.containing(player.getX() + Mth.cos(angle) * distance, 0, player.getZ() + Mth.sin(angle) * distance);
        if (!level.isLoaded(column)) return;
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        var ground = level.getBlockState(top.below());
        if (ground.is(BlockTags.SNOW) || ground.is(Blocks.SNOW_BLOCK) || ground.is(BlockTags.ICE) || level.getBlockState(top).is(Blocks.SNOW)) {
            EntityType.POLAR_BEAR.spawn(level, top, MobSpawnType.EVENT);
        }
    }

    // ------------------------------------------------------------------ thicker wool in winter

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Sheep sheep) || !(event.getLevel() instanceof ServerLevel level)
                || !event.getItemStack().is(Items.SHEARS) || !sheep.readyForShearing() || !WeatherConfig.WINTER_WOOL.get()) {
            return;
        }
        boolean winter = WeatherManager.seasonInfo().season() == Season.WINTER;
        boolean cold = level.getBiome(sheep.blockPosition()).value().coldEnoughToSnow(sheep.blockPosition());
        if (!winter && !cold) {
            return;
        }
        // let vanilla shear first (next tick), then add the extra fleece
        var server = level.getServer();
        server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 1, () -> {
            if (sheep.isSheared()) {
                var wool = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(sheep.getColor().getName() + "_wool"));
                sheep.spawnAtLocation(new ItemStack(wool, 1 + level.random.nextInt(2)), 1.0F);
            }
        }));
    }

    // ------------------------------------------------------------------ the meteorologist

    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != ModVillagers.METEOROLOGIST.get()) {
            return;
        }
        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();
        trades.get(1).add(buy(Items.PAPER, 24, 1, 16, 2));
        trades.get(1).add(sell(ModItems.THERMOMETER.get().getDefaultInstance(), 2, 12, 2));
        trades.get(1).add(sell(ModItems.BAROMETER.get().getDefaultInstance(), 3, 12, 2));
        trades.get(2).add(buy(Items.COPPER_INGOT, 10, 1, 16, 10));
        trades.get(2).add(sell(new ItemStack(ModItems.CLOUD_SEEDING_ROCKET.get(), 3), 2, 12, 5));
        trades.get(2).add(sell(ModItems.UMBRELLA.get().getDefaultInstance(), 4, 12, 5));
        trades.get(3).add(buy(ModItems.STORM_DATA.get(), 1, 6, 12, 20));
        trades.get(3).add(sell(new ItemStack(ModItems.SALT.get(), 8), 1, 16, 10));
        trades.get(3).add(sell(new ItemStack(ModItems.SANDBAG.get(), 6), 1, 16, 10));
        trades.get(3).add(sell(ModItems.STORM_PROBE.get().getDefaultInstance(), 6, 8, 10));
        trades.get(4).add(sell(ModItems.GOGGLES.get().getDefaultInstance(), 7, 8, 15));
        trades.get(4).add(sell(ModItems.WINTER_COAT.get().getDefaultInstance(), 8, 8, 15));
        trades.get(4).add(sell(ModItems.HANDHELD_RADAR.get().getDefaultInstance(), 10, 8, 15));
        trades.get(5).add(buy(ModItems.STORM_DATA.get(), 1, 10, 12, 30));
        trades.get(5).add(sell(ModItems.LIGHTNING_BATTERY.get().getDefaultInstance(), 14, 4, 30));
        trades.get(5).add(sell(ModItems.WEATHER_RADAR.get().getDefaultInstance(), 20, 4, 30));
    }

    private static BasicItemListing buy(net.minecraft.world.level.ItemLike item, int count, int emeralds, int maxTrades, int xp) {
        return new BasicItemListing(new ItemStack(item, count), new ItemStack(Items.EMERALD, emeralds), maxTrades, xp, 0.05F);
    }

    private static BasicItemListing sell(ItemStack item, int emeralds, int maxTrades, int xp) {
        return new BasicItemListing(emeralds, item, maxTrades, xp, 0.05F);
    }

    public static void onTrade(TradeWithVillagerEvent event) {
        if (event.getAbstractVillager() instanceof Villager villager && villager.getVillagerData().getProfession() == ModVillagers.METEOROLOGIST.get()
                && event.getEntity() instanceof ServerPlayer player) {
            WeatherAdvancements.award(player, "weather_wise");
        }
    }

    private WeatherCreatures() {}
}
