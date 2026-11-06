package com.reiko.weather.registry;

import com.reiko.weather.ReikosWeather;
import com.reiko.weather.item.BarometerItem;
import com.reiko.weather.item.CloudSeedingRocketItem;
import com.reiko.weather.item.HandheldRadarItem;
import com.reiko.weather.item.ThermometerItem;
import com.reiko.weather.item.UmbrellaItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(ReikosWeather.MOD_ID);

    public static final DeferredItem<BarometerItem> BAROMETER = REGISTER.register("barometer",
            () -> new BarometerItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<UmbrellaItem> UMBRELLA = REGISTER.register("umbrella",
            () -> new UmbrellaItem(new Item.Properties().stacksTo(1).durability(160)));

    public static final DeferredItem<ArmorItem> WINTER_COAT = REGISTER.register("winter_coat",
            () -> new ArmorItem(ModArmorMaterials.WINTER_COAT, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(12))));

    public static final DeferredItem<ArmorItem> GOGGLES = REGISTER.register("goggles",
            () -> new ArmorItem(ModArmorMaterials.GOGGLES, ArmorItem.Type.HELMET,
                    new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(10))));

    public static final DeferredItem<BlockItem> WEATHER_STATION = REGISTER.registerSimpleBlockItem(ModBlocks.WEATHER_STATION);
    public static final DeferredItem<BlockItem> TORNADO_SIREN = REGISTER.registerSimpleBlockItem(ModBlocks.TORNADO_SIREN);
    public static final DeferredItem<BlockItem> RAIN_BARREL = REGISTER.registerSimpleBlockItem(ModBlocks.RAIN_BARREL);

    public static final DeferredItem<ThermometerItem> THERMOMETER = REGISTER.register("thermometer",
            () -> new ThermometerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<HandheldRadarItem> HANDHELD_RADAR = REGISTER.register("handheld_radar",
            () -> new HandheldRadarItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<CloudSeedingRocketItem> CLOUD_SEEDING_ROCKET = REGISTER.register("cloud_seeding_rocket",
            () -> new CloudSeedingRocketItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<BlockItem> WEATHER_ALTAR = REGISTER.registerSimpleBlockItem(ModBlocks.WEATHER_ALTAR,
            new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC));
    public static final DeferredItem<BlockItem> WEATHER_RADAR = REGISTER.registerSimpleBlockItem(ModBlocks.WEATHER_RADAR);
    public static final DeferredItem<BlockItem> WEATHER_VANE = REGISTER.registerSimpleBlockItem(ModBlocks.WEATHER_VANE);
    public static final DeferredItem<BlockItem> WINDMILL = REGISTER.registerSimpleBlockItem(ModBlocks.WINDMILL);

    private ModItems() {}
}
