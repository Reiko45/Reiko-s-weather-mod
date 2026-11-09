package com.reiko.weather.registry;

import com.reiko.weather.ReikosWeather;
import com.reiko.weather.item.BarometerItem;
import com.reiko.weather.item.CloudSeedingRocketItem;
import com.reiko.weather.item.HandheldRadarItem;
import com.reiko.weather.item.ThermometerItem;
import com.reiko.weather.item.UmbrellaItem;
import com.reiko.weather.item.DrinkItem;
import com.reiko.weather.item.SaltItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
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

    public static final DeferredItem<DrinkItem> HOT_COCOA = REGISTER.register("hot_cocoa", () -> new DrinkItem(new Item.Properties().stacksTo(16)
            .food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.6F).alwaysEdible().usingConvertsTo(Items.BOWL)
                    .effect(() -> new MobEffectInstance(ModEffects.WARMTH, 3600), 1.0F).build()), "item.reikos_weather.hot_cocoa.tooltip"));
    public static final DeferredItem<DrinkItem> MELON_COOLER = REGISTER.register("melon_cooler", () -> new DrinkItem(new Item.Properties().stacksTo(16)
            .food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.4F).alwaysEdible().usingConvertsTo(Items.GLASS_BOTTLE)
                    .effect(() -> new MobEffectInstance(ModEffects.REFRESHED, 3600), 1.0F).build()), "item.reikos_weather.melon_cooler.tooltip"));
    public static final DeferredItem<ArmorItem> SUN_HAT = REGISTER.register("sun_hat",
            () -> new ArmorItem(ModArmorMaterials.STRAW, ArmorItem.Type.HELMET, new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(6))));
    public static final DeferredItem<BedItem> TENT = REGISTER.register("tent", () -> new BedItem(ModBlocks.TENT.get(), new Item.Properties().stacksTo(1)));
    public static final DeferredItem<SaltItem> SALT = REGISTER.register("salt", () -> new SaltItem(new Item.Properties()));
    public static final DeferredItem<BlockItem> SANDBAG = REGISTER.registerSimpleBlockItem(ModBlocks.SANDBAG);
    public static final DeferredItem<BlockItem> LIGHTNING_BATTERY = REGISTER.registerSimpleBlockItem(ModBlocks.LIGHTNING_BATTERY);
    public static final DeferredItem<BlockItem> FORECAST_BOARD = REGISTER.registerSimpleBlockItem(ModBlocks.FORECAST_BOARD);
    public static final DeferredItem<BlockItem> STORM_PROBE = REGISTER.registerSimpleBlockItem(ModBlocks.STORM_PROBE);
    public static final DeferredItem<Item> STORM_DATA = REGISTER.register("storm_data", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<BlockItem> METEORITE = REGISTER.registerSimpleBlockItem(ModBlocks.METEORITE);

    public static final DeferredItem<BlockItem> FALLEN_BRANCH = REGISTER.registerSimpleBlockItem(ModBlocks.FALLEN_BRANCH);
    public static final DeferredItem<BlockItem> LEAF_PILE = REGISTER.registerSimpleBlockItem(ModBlocks.LEAF_PILE);
    public static final DeferredItem<BlockItem> ICEBOX = REGISTER.registerSimpleBlockItem(ModBlocks.ICEBOX);
    public static final DeferredItem<ItemNameBlockItem> RICE = REGISTER.register("rice", () -> new ItemNameBlockItem(ModBlocks.RICE.get(), new Item.Properties()));
    public static final DeferredItem<ItemNameBlockItem> KALE = REGISTER.register("kale", () -> new ItemNameBlockItem(ModBlocks.KALE.get(),
            new Item.Properties().food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.6F).build())));
    public static final DeferredItem<Item> RICE_BOWL = REGISTER.register("rice_bowl", () -> new Item(new Item.Properties().stacksTo(1)
            .food(new FoodProperties.Builder().nutrition(7).saturationModifier(0.6F).usingConvertsTo(Items.BOWL).build())));
    public static final DeferredItem<Item> WINTER_STEW = REGISTER.register("winter_stew", () -> new Item(new Item.Properties().stacksTo(1)
            .food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.8F).usingConvertsTo(Items.BOWL)
                    .effect(() -> new MobEffectInstance(ModEffects.WARMTH, 1800), 1.0F).build())));
    public static final DeferredItem<Item> EARTHWORM = REGISTER.register("earthworm", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> STORMFISH = REGISTER.register("stormfish", () -> new Item(new Item.Properties()
            .food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.1F).build())));
    public static final DeferredItem<Item> COOKED_STORMFISH = REGISTER.register("cooked_stormfish", () -> new Item(new Item.Properties()
            .food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.8F)
                    .effect(() -> new MobEffectInstance(net.minecraft.world.effect.MobEffects.DIG_SPEED, 600), 1.0F).build())));
    public static final DeferredItem<Item> ICEFISH = REGISTER.register("icefish", () -> new Item(new Item.Properties()
            .food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.1F).build())));
    public static final DeferredItem<Item> COOKED_ICEFISH = REGISTER.register("cooked_icefish", () -> new Item(new Item.Properties()
            .food(new FoodProperties.Builder().nutrition(5).saturationModifier(0.6F)
                    .effect(() -> new MobEffectInstance(ModEffects.REFRESHED, 1200), 1.0F).build())));
    public static final DeferredItem<com.reiko.weather.item.RakeItem> RAKE = REGISTER.register("rake",
            () -> new com.reiko.weather.item.RakeItem(new Item.Properties().durability(160)));
    public static final DeferredItem<com.reiko.weather.item.IceSawItem> ICE_SAW = REGISTER.register("ice_saw",
            () -> new com.reiko.weather.item.IceSawItem(new Item.Properties().durability(220)));
    public static final DeferredItem<com.reiko.weather.item.ParagliderItem> PARAGLIDER = REGISTER.register("paraglider",
            () -> new com.reiko.weather.item.ParagliderItem(new Item.Properties().durability(320)));
    public static final DeferredItem<com.reiko.weather.item.WeatherRadioItem> WEATHER_RADIO = REGISTER.register("weather_radio",
            () -> new com.reiko.weather.item.WeatherRadioItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<com.reiko.weather.item.WeatherAlmanacItem> WEATHER_ALMANAC = REGISTER.register("weather_almanac",
            () -> new com.reiko.weather.item.WeatherAlmanacItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<com.reiko.weather.item.VolcanicAshItem> VOLCANIC_ASH = REGISTER.register("volcanic_ash",
            () -> new com.reiko.weather.item.VolcanicAshItem(ModBlocks.VOLCANIC_ASH.get(), new Item.Properties()));
    public static final DeferredItem<Item> SULFUR = REGISTER.register("sulfur", () -> new Item(new Item.Properties()));
    public static final DeferredItem<BlockItem> SULFUR_CRUST = REGISTER.registerSimpleBlockItem(ModBlocks.SULFUR_CRUST);
    public static final DeferredItem<BlockItem> VOLCANIC_VENT = REGISTER.registerSimpleBlockItem(ModBlocks.VOLCANIC_VENT);
    public static final DeferredItem<BlockItem> SEISMOGRAPH = REGISTER.registerSimpleBlockItem(ModBlocks.SEISMOGRAPH);

    public static final DeferredItem<BlockItem> GEYSER = REGISTER.registerSimpleBlockItem(ModBlocks.GEYSER);
    public static final DeferredItem<BlockItem> SALT_CRUST = REGISTER.registerSimpleBlockItem(ModBlocks.SALT_CRUST);
    public static final DeferredItem<BlockItem> BLACK_SAND = REGISTER.registerSimpleBlockItem(ModBlocks.BLACK_SAND);
    public static final DeferredItem<BlockItem> CRACKED_MUD = REGISTER.registerSimpleBlockItem(ModBlocks.CRACKED_MUD);
    public static final DeferredItem<com.reiko.weather.item.VolcanicAshItem> DESERT_DUST = REGISTER.register("desert_dust",
            () -> new com.reiko.weather.item.VolcanicAshItem(ModBlocks.DESERT_DUST.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> PEAT = REGISTER.registerSimpleBlockItem(ModBlocks.PEAT);
    public static final DeferredItem<BlockItem> QUICKSAND = REGISTER.registerSimpleBlockItem(ModBlocks.QUICKSAND);
    public static final DeferredItem<BlockItem> BLOWHOLE = REGISTER.registerSimpleBlockItem(ModBlocks.BLOWHOLE);
    public static final DeferredItem<BlockItem> LIMESTONE = REGISTER.registerSimpleBlockItem(ModBlocks.LIMESTONE);

    private ModItems() {}
}
