package com.reiko.weather.compat.jei;

import com.reiko.weather.ReikosWeather;
import com.reiko.weather.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.List;

/** JEI information pages for every Reiko's Weather item. Only loaded when JEI is installed. */
@JeiPlugin
public class ReikosWeatherJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ReikosWeather.id("jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<DeferredItem<? extends Item>> items = List.of(ModItems.BAROMETER, ModItems.UMBRELLA, ModItems.WINTER_COAT, ModItems.GOGGLES,
                ModItems.THERMOMETER, ModItems.HANDHELD_RADAR, ModItems.CLOUD_SEEDING_ROCKET, ModItems.WEATHER_STATION,
                ModItems.TORNADO_SIREN, ModItems.RAIN_BARREL, ModItems.WEATHER_RADAR, ModItems.WEATHER_VANE, ModItems.WINDMILL,
                ModItems.WEATHER_ALTAR, ModItems.HOT_COCOA, ModItems.MELON_COOLER, ModItems.SUN_HAT, ModItems.TENT, ModItems.SALT,
                ModItems.SANDBAG, ModItems.LIGHTNING_BATTERY, ModItems.FORECAST_BOARD, ModItems.STORM_PROBE, ModItems.STORM_DATA,
                ModItems.METEORITE, ModItems.LEAF_PILE, ModItems.FALLEN_BRANCH, ModItems.ICEBOX, ModItems.RICE, ModItems.KALE,
                ModItems.RICE_BOWL, ModItems.WINTER_STEW, ModItems.EARTHWORM, ModItems.STORMFISH, ModItems.ICEFISH, ModItems.RAKE,
                ModItems.ICE_SAW, ModItems.PARAGLIDER, ModItems.WEATHER_RADIO, ModItems.WEATHER_ALMANAC, ModItems.VOLCANIC_ASH, ModItems.SULFUR,
                ModItems.SULFUR_CRUST, ModItems.VOLCANIC_VENT, ModItems.SEISMOGRAPH);
        for (DeferredItem<? extends Item> item : items) {
            String key = "jei.reikos_weather." + BuiltInRegistries.ITEM.getKey(item.get()).getPath();
            registration.addIngredientInfo(item.get(), Component.translatable(key));
        }
    }
}
