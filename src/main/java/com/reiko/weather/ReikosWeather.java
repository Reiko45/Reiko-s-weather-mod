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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
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
        com.reiko.weather.registry.ModCreativeTabs.REGISTER.register(modBus);
        com.reiko.weather.registry.ModFeatures.REGISTER.register(modBus);
        com.reiko.weather.registry.ModEffects.REGISTER.register(modBus);
        com.reiko.weather.registry.ModVillagers.POI_TYPES.register(modBus);
        com.reiko.weather.registry.ModVillagers.PROFESSIONS.register(modBus);
        com.reiko.weather.registry.ModDataComponents.REGISTER.register(modBus);
        com.reiko.weather.registry.ModStructures.TYPES.register(modBus);
        com.reiko.weather.registry.ModStructures.PIECES.register(modBus);

        modBus.addListener(ModNetwork::register);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(com.reiko.weather.compat.Compat::setup));
        // fireweed goes in a flower pot like any other flower
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(() ->
                ((net.minecraft.world.level.block.FlowerPotBlock) net.minecraft.world.level.block.Blocks.FLOWER_POT)
                        .addPlant(com.reiko.weather.registry.ModBlocks.FIREWEED.getId(), com.reiko.weather.registry.ModBlocks.POTTED_FIREWEED)));
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(() -> {
            net.minecraft.world.level.block.FlowerPotBlock pot = (net.minecraft.world.level.block.FlowerPotBlock) net.minecraft.world.level.block.Blocks.FLOWER_POT;
            pot.addPlant(com.reiko.weather.registry.ModBlocks.DESERT_GOLD.getId(), com.reiko.weather.registry.ModBlocks.POTTED_DESERT_GOLD);
            pot.addPlant(com.reiko.weather.registry.ModBlocks.SAND_VERBENA.getId(), com.reiko.weather.registry.ModBlocks.POTTED_SAND_VERBENA);
            pot.addPlant(com.reiko.weather.registry.ModBlocks.DESERT_POPPY.getId(), com.reiko.weather.registry.ModBlocks.POTTED_DESERT_POPPY);
        }));

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
}
