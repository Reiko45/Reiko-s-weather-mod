package com.reiko.weather;

import com.mojang.logging.LogUtils;
import com.reiko.weather.command.WeatherCommand;
import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.item.GearEvents;
import com.reiko.weather.mob.WeatherMobs;
import com.reiko.weather.network.ModNetwork;
import com.reiko.weather.registry.ModArmorMaterials;
import com.reiko.weather.registry.ModBlocks;
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
        ModArmorMaterials.REGISTER.register(modBus);
        ModParticles.REGISTER.register(modBus);
        ModSounds.REGISTER.register(modBus);
        ModEntities.REGISTER.register(modBus);
        ModItems.REGISTER.register(modBus);

        modBus.addListener(ModNetwork::register);
        modBus.addListener(ReikosWeather::addCreativeTabContents);

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
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.BAROMETER.get());
            event.accept(ModItems.UMBRELLA.get());
        } else if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.WINTER_COAT.get());
            event.accept(ModItems.GOGGLES.get());
        } else if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ModItems.WEATHER_STATION.get());
            event.accept(ModItems.TORNADO_SIREN.get());
        } else if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.WEATHER_STATION.get());
            event.accept(ModItems.TORNADO_SIREN.get());
            event.accept(ModItems.RAIN_BARREL.get());
        }
    }
}
