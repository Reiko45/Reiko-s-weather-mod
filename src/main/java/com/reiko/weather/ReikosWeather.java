package com.reiko.weather;

import com.mojang.logging.LogUtils;
import com.reiko.weather.command.WeatherCommand;
import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.item.GearEvents;
import com.reiko.weather.mob.WeatherMobs;
import com.reiko.weather.network.ModNetwork;
import com.reiko.weather.registry.ModArmorMaterials;
import com.reiko.weather.registry.ModBlockEntities;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.otherworld.OtherworldWeather;
import com.reiko.weather.weather.LightningEvents;
import com.reiko.weather.registry.ModEntities;
import com.reiko.weather.registry.ModItems;
import com.reiko.weather.registry.ModParticles;
import com.reiko.weather.registry.ModSounds;
import com.reiko.weather.weather.WeatherHazards;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

/**
 * Reiko's Weather: a complete weather overhaul for the overworld.
 *
 * <p>The server owns a {@link com.reiko.weather.weather.WeatherState} that cycles through every
 * {@link com.reiko.weather.weather.WeatherType}. Each weather type manifests differently depending on the
 * local {@link com.reiko.weather.weather.Climate} (a blizzard in the tundra is a cold downpour in a forest).
 * The current state is synced to clients, which render fog, sky tints, particles, sounds, rainbows and auroras.</p>
 */
@Mod(ReikosWeather.MOD_ID)
public class ReikosWeather {
    public static final String MOD_ID = "reikos_weather";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ReikosWeather(IEventBus modBus, ModContainer container) {
        ModBlocks.REGISTER.register(modBus);
        ModBlockEntities.REGISTER.register(modBus);
        ModArmorMaterials.REGISTER.register(modBus);
        ModParticles.REGISTER.register(modBus);
        ModSounds.REGISTER.register(modBus);
        ModEntities.REGISTER.register(modBus);
        ModItems.REGISTER.register(modBus);
        com.reiko.weather.registry.ModEffects.REGISTER.register(modBus);
        com.reiko.weather.registry.ModVillagers.POI_TYPES.register(modBus);
        com.reiko.weather.registry.ModVillagers.PROFESSIONS.register(modBus);
        com.reiko.weather.registry.ModDataComponents.REGISTER.register(modBus);
        com.reiko.weather.registry.ModStructures.TYPES.register(modBus);
        com.reiko.weather.registry.ModStructures.PIECES.register(modBus);

        modBus.addListener(ModNetwork::register);
        modBus.addListener(ReikosWeather::addCreativeTabContents);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(com.reiko.weather.compat.Compat::setup));
        // fireweed goes in a flower pot like any other flower
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(() ->
                ((net.minecraft.world.level.block.FlowerPotBlock) net.minecraft.world.level.block.Blocks.FLOWER_POT)
                        .addPlant(com.reiko.weather.registry.ModBlocks.FIREWEED.getId(), com.reiko.weather.registry.ModBlocks.POTTED_FIREWEED)));

        container.registerConfig(ModConfig.Type.SERVER, WeatherConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        NeoForge.EVENT_BUS.addListener(WeatherManager::onLevelTick);
        NeoForge.EVENT_BUS.addListener(WeatherManager::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(WeatherManager::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(WeatherManager::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(WeatherManager::onSleepFinished);
        NeoForge.EVENT_BUS.addListener(WeatherManager::onServerStopped);
        NeoForge.EVENT_BUS.addListener(WeatherHazards::onEntityTick);
        NeoForge.EVENT_BUS.addListener(WeatherHazards::onCropGrow);
        NeoForge.EVENT_BUS.addListener(WeatherCommand::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(GearEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(WeatherMobs::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(WeatherMobs::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(LightningEvents::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(OtherworldWeather::onLevelTick);
        NeoForge.EVENT_BUS.addListener(OtherworldWeather::onEntityTick);
        NeoForge.EVENT_BUS.addListener(OtherworldWeather::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(OtherworldWeather::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(com.reiko.weather.weather.CustomWeathers::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(com.reiko.weather.weather.CustomWeathers::onDatapackSync);
        NeoForge.EVENT_BUS.addListener(com.reiko.weather.mob.WeatherCreatures::onEntityTick);
        NeoForge.EVENT_BUS.addListener(com.reiko.weather.mob.WeatherCreatures::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(com.reiko.weather.mob.WeatherCreatures::onVillagerTrades);
        NeoForge.EVENT_BUS.addListener(com.reiko.weather.mob.WeatherCreatures::onTrade);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent e) -> {
            // sleeping in a tent never moves your spawn point
            if (e.getNewSpawn() != null && e.getEntity().level().getBlockState(e.getNewSpawn()).getBlock() instanceof com.reiko.weather.block.TentBlock) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(com.reiko.weather.sky.BloodMoon::onLevelTick);
        NeoForge.EVENT_BUS.addListener(com.reiko.weather.sky.BloodMoon::onCanSleep);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
                com.reiko.weather.farming.Spoilage.tickLevel(level);
            }
        });
        NeoForge.EVENT_BUS.addListener(com.reiko.weather.nature.NatureEvents::onBlockDrops);
        NeoForge.EVENT_BUS.addListener(com.reiko.weather.nature.NatureEvents::onItemFished);
        NeoForge.EVENT_BUS.addListener(com.reiko.weather.nature.NatureEvents::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(com.reiko.weather.nature.NatureEvents::onLivingFall);
        NeoForge.EVENT_BUS.addListener(com.reiko.weather.item.ChargedTools::onStruckByLightning);
        NeoForge.EVENT_BUS.addListener(com.reiko.weather.item.ChargedTools::onBreakSpeed);
        NeoForge.EVENT_BUS.addListener(com.reiko.weather.item.ChargedTools::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(com.reiko.weather.item.WeatherAlmanacItem::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(com.reiko.weather.volcano.VolcanoManager::onLevelTick);
        NeoForge.EVENT_BUS.addListener(com.reiko.weather.volcano.VolcanoManager::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(com.reiko.weather.landform.LandformManager::onLevelTick);
        NeoForge.EVENT_BUS.addListener(com.reiko.weather.landform.LandformManager::onPlayerTick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartedEvent e) ->
                com.reiko.weather.weather.Monsoon.collect(e.getServer().registryAccess()));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.BAROMETER.get());
            event.accept(ModItems.THERMOMETER.get());
            event.accept(ModItems.HANDHELD_RADAR.get());
            event.accept(ModItems.UMBRELLA.get());
            event.accept(ModItems.CLOUD_SEEDING_ROCKET.get());
            event.accept(ModItems.SALT.get());
            event.accept(ModItems.STORM_DATA.get());
            event.accept(ModItems.WEATHER_ALMANAC.get());
            event.accept(ModItems.WEATHER_RADIO.get());
            event.accept(ModItems.PARAGLIDER.get());
            event.accept(ModItems.RAKE.get());
            event.accept(ModItems.ICE_SAW.get());
            event.accept(ModItems.EARTHWORM.get());
        } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.SULFUR.get());
            event.accept(ModItems.VOLCANIC_ASH.get());
        } else if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.WINTER_COAT.get());
            event.accept(ModItems.GOGGLES.get());
            event.accept(ModItems.SUN_HAT.get());
        } else if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(ModItems.HOT_COCOA.get());
            event.accept(ModItems.MELON_COOLER.get());
            event.accept(ModItems.KALE.get());
            event.accept(ModItems.RICE_BOWL.get());
            event.accept(ModItems.WINTER_STEW.get());
            event.accept(ModItems.STORMFISH.get());
            event.accept(ModItems.COOKED_STORMFISH.get());
            event.accept(ModItems.ICEFISH.get());
            event.accept(ModItems.COOKED_ICEFISH.get());
        } else if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(ModItems.METEORITE.get());
            event.accept(ModItems.LEAF_PILE.get());
            event.accept(ModItems.FALLEN_BRANCH.get());
            event.accept(ModItems.RICE.get());
            event.accept(ModItems.VOLCANIC_ASH.get());
            event.accept(ModItems.SULFUR_CRUST.get());
            event.accept(ModItems.VOLCANIC_VENT.get());
            event.accept(ModItems.GEYSER.get());
            event.accept(ModItems.SALT_CRUST.get());
            event.accept(ModItems.BLACK_SAND.get());
            event.accept(ModItems.CRACKED_MUD.get());
            event.accept(ModItems.DESERT_DUST.get());
            event.accept(ModItems.PEAT.get());
            event.accept(ModItems.QUICKSAND.get());
            event.accept(ModItems.BLOWHOLE.get());
            event.accept(ModItems.LIMESTONE.get());
            event.accept(ModItems.PETRIFIED_WOOD.get());
            event.accept(ModItems.PUMICE.get());
            event.accept(ModItems.GYPSUM_SAND.get());
            event.accept(ModItems.SHELL_SAND.get());
            event.accept(ModItems.OLIVINE_SAND.get());
            event.accept(ModItems.PINK_SAND.get());
            event.accept(ModItems.ROCK_SALT.get());
            event.accept(ModItems.STEAMING_GROUND.get());
            event.accept(ModItems.BEACH_WRACK.get());
            event.accept(ModItems.FIREWEED.get());
            event.accept(ModItems.CHARRED_SOIL.get());
        } else if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ModItems.SANDBAG.get());
        } else if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ModItems.WEATHER_RADAR.get());
            event.accept(ModItems.WEATHER_VANE.get());
            event.accept(ModItems.WINDMILL.get());
            event.accept(ModItems.WEATHER_STATION.get());
            event.accept(ModItems.TORNADO_SIREN.get());
            event.accept(ModItems.LIGHTNING_BATTERY.get());
            event.accept(ModItems.STORM_PROBE.get());
            event.accept(ModItems.SEISMOGRAPH.get());
        } else if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.WEATHER_RADAR.get());
            event.accept(ModItems.WEATHER_VANE.get());
            event.accept(ModItems.WINDMILL.get());
            event.accept(ModItems.WEATHER_ALTAR.get());
            event.accept(ModItems.WEATHER_STATION.get());
            event.accept(ModItems.TORNADO_SIREN.get());
            event.accept(ModItems.RAIN_BARREL.get());
            event.accept(ModItems.FORECAST_BOARD.get());
            event.accept(ModItems.TENT.get());
            event.accept(ModItems.LIGHTNING_BATTERY.get());
            event.accept(ModItems.STORM_PROBE.get());
            event.accept(ModItems.ICEBOX.get());
            event.accept(ModItems.SEISMOGRAPH.get());
        }
    }
}
