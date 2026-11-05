package com.reiko.weather;

import com.mojang.logging.LogUtils;
import com.reiko.weather.command.WeatherCommand;
import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.network.ModNetwork;
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
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.BAROMETER.get());
        }
    }
}
