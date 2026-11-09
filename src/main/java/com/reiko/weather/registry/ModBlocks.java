package com.reiko.weather.registry;

import com.reiko.weather.ReikosWeather;
import com.reiko.weather.block.IceGlazeBlock;
import com.reiko.weather.block.IcicleBlock;
import com.reiko.weather.block.WeatherAltarBlock;
import com.reiko.weather.block.WeatherRadarBlock;
import com.reiko.weather.block.WeatherVaneBlock;
import com.reiko.weather.block.WindmillBlock;
import com.reiko.weather.block.PuddleBlock;
import com.reiko.weather.block.RainBarrelBlock;
import com.reiko.weather.block.TornadoSirenBlock;
import com.reiko.weather.block.WeatherStationBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks REGISTER = DeferredRegister.createBlocks(ReikosWeather.MOD_ID);

    public static final DeferredBlock<IceGlazeBlock> ICE_GLAZE = REGISTER.register("ice_glaze", () -> new IceGlazeBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.ICE).friction(0.98F).strength(0.1F).sound(SoundType.GLASS)
                    .noOcclusion().randomTicks().replaceable().pushReaction(PushReaction.DESTROY).noLootTable()
                    .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)));

    public static final DeferredBlock<PuddleBlock> PUDDLE = REGISTER.register("puddle", () -> new PuddleBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WATER).noCollission().instabreak().sound(SoundType.EMPTY).noOcclusion().randomTicks()
                    .replaceable().pushReaction(PushReaction.DESTROY).noLootTable()));

    public static final DeferredBlock<WeatherStationBlock> WEATHER_STATION = REGISTER.register("weather_station", () -> new WeatherStationBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2.0F, 6.0F).sound(SoundType.COPPER)));

    public static final DeferredBlock<TornadoSirenBlock> TORNADO_SIREN = REGISTER.register("tornado_siren", () -> new TornadoSirenBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL)));

    public static final DeferredBlock<RainBarrelBlock> RAIN_BARREL = REGISTER.register("rain_barrel", () -> new RainBarrelBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion()));

    public static final DeferredBlock<IcicleBlock> ICICLE = REGISTER.register("icicle", () -> new IcicleBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(0.2F).sound(SoundType.GLASS).noCollission()
                    .noOcclusion().randomTicks().pushReaction(PushReaction.DESTROY).noLootTable().dynamicShape()));

    public static final DeferredBlock<WeatherAltarBlock> WEATHER_ALTAR = REGISTER.register("weather_altar", () -> new WeatherAltarBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(50.0F, 1200.0F).sound(SoundType.AMETHYST)
                    .lightLevel(s -> 10).requiresCorrectToolForDrops()));

    public static final DeferredBlock<WeatherRadarBlock> WEATHER_RADAR = REGISTER.register("weather_radar", () -> new WeatherRadarBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).lightLevel(s -> 4)));

    public static final DeferredBlock<WeatherVaneBlock> WEATHER_VANE = REGISTER.register("weather_vane", () -> new WeatherVaneBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2.0F).sound(SoundType.COPPER).noOcclusion()));

    public static final DeferredBlock<WindmillBlock> WINDMILL = REGISTER.register("windmill", () -> new WindmillBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion()));

    public static final DeferredBlock<com.reiko.weather.block.TentBlock> TENT = REGISTER.register("tent", () -> new com.reiko.weather.block.TentBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.4F).sound(SoundType.WOOL).noOcclusion()
                    .ignitedByLava().pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<net.minecraft.world.level.block.Block> SANDBAG = REGISTER.register("sandbag", () -> new net.minecraft.world.level.block.Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(1.0F, 12.0F).sound(SoundType.SAND)));

    public static final DeferredBlock<com.reiko.weather.block.LightningBatteryBlock> LIGHTNING_BATTERY = REGISTER.register("lightning_battery",
            () -> new com.reiko.weather.block.LightningBatteryBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(3.0F, 6.0F)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()
                    .lightLevel(s -> s.getValue(com.reiko.weather.block.LightningBatteryBlock.CHARGE) * 3)));

    public static final DeferredBlock<com.reiko.weather.block.ForecastBoardBlock> FORECAST_BOARD = REGISTER.register("forecast_board",
            () -> new com.reiko.weather.block.ForecastBoardBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F)
                    .sound(SoundType.WOOD).noCollission().noOcclusion().ignitedByLava()));

    public static final DeferredBlock<com.reiko.weather.block.StormProbeBlock> STORM_PROBE = REGISTER.register("storm_probe",
            () -> new com.reiko.weather.block.StormProbeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5F)
                    .sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<net.minecraft.world.level.block.Block> METEORITE = REGISTER.register("meteorite", () -> new net.minecraft.world.level.block.Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(5.0F, 9.0F).sound(SoundType.ANCIENT_DEBRIS)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<com.reiko.weather.block.FallenBranchBlock> FALLEN_BRANCH = REGISTER.register("fallen_branch",
            () -> new com.reiko.weather.block.FallenBranchBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.5F)
                    .sound(SoundType.WOOD).noOcclusion().randomTicks().ignitedByLava().pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<com.reiko.weather.block.LeafPileBlock> LEAF_PILE = REGISTER.register("leaf_pile",
            () -> new com.reiko.weather.block.LeafPileBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.1F)
                    .sound(SoundType.AZALEA_LEAVES).noOcclusion().replaceable().ignitedByLava().pushReaction(PushReaction.DESTROY)
                    .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)));

    public static final DeferredBlock<com.reiko.weather.block.RiceCropBlock> RICE = REGISTER.register("rice",
            () -> new com.reiko.weather.block.RiceCropBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission()
                    .randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<com.reiko.weather.block.KaleCropBlock> KALE = REGISTER.register("kale",
            () -> new com.reiko.weather.block.KaleCropBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission()
                    .randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<com.reiko.weather.block.IceboxBlock> ICEBOX = REGISTER.register("icebox",
            () -> new com.reiko.weather.block.IceboxBlock(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(2.5F)
                    .sound(SoundType.WOOD).ignitedByLava()));

    public static final DeferredBlock<com.reiko.weather.block.VolcanicAshBlock> VOLCANIC_ASH = REGISTER.register("volcanic_ash",
            () -> new com.reiko.weather.block.VolcanicAshBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).replaceable()
                    .forceSolidOff().strength(0.2F).sound(SoundType.SAND).pushReaction(PushReaction.DESTROY)
                    .isViewBlocking((s, l, p) -> s.getValue(com.reiko.weather.block.VolcanicAshBlock.LAYERS) >= 8)));

    public static final DeferredBlock<com.reiko.weather.block.VolcanicVentBlock> VOLCANIC_VENT = REGISTER.register("volcanic_vent",
            () -> new com.reiko.weather.block.VolcanicVentBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5F, 6.0F)
                    .sound(SoundType.BASALT).requiresCorrectToolForDrops().lightLevel(s -> 3)
                    .isValidSpawn((s, l, p, type) -> type.fireImmune())));

    public static final DeferredBlock<com.reiko.weather.block.SulfurCrustBlock> SULFUR_CRUST = REGISTER.register("sulfur_crust",
            () -> new com.reiko.weather.block.SulfurCrustBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(1.2F, 3.0F)
                    .sound(SoundType.TUFF).requiresCorrectToolForDrops()));

    public static final DeferredBlock<com.reiko.weather.block.SeismographBlock> SEISMOGRAPH = REGISTER.register("seismograph",
            () -> new com.reiko.weather.block.SeismographBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2.0F, 6.0F)
                    .sound(SoundType.COPPER)));

    public static final DeferredBlock<com.reiko.weather.block.GeyserBlock> GEYSER = REGISTER.register("geyser",
            () -> new com.reiko.weather.block.GeyserBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(1.5F, 6.0F)
                    .sound(SoundType.CALCITE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<com.reiko.weather.block.SaltCrustBlock> SALT_CRUST = REGISTER.register("salt_crust",
            () -> new com.reiko.weather.block.SaltCrustBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.8F)
                    .sound(SoundType.CALCITE)));

    public static final DeferredBlock<net.minecraft.world.level.block.ColoredFallingBlock> BLACK_SAND = REGISTER.register("black_sand",
            () -> new net.minecraft.world.level.block.ColoredFallingBlock(new net.minecraft.util.ColorRGBA(0x2B2826),
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5F).sound(SoundType.SAND)));

    public static final DeferredBlock<com.reiko.weather.block.CrackedMudBlock> CRACKED_MUD = REGISTER.register("cracked_mud",
            () -> new com.reiko.weather.block.CrackedMudBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_LIGHT_GRAY).strength(0.8F, 2.0F)
                    .sound(SoundType.PACKED_MUD).randomTicks()));

    /** Desert dust blown far from a sandstorm and settled out of the haze (washes away in the rain, like ash). */
    public static final DeferredBlock<com.reiko.weather.block.VolcanicAshBlock> DESERT_DUST = REGISTER.register("desert_dust",
            () -> new com.reiko.weather.block.VolcanicAshBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).replaceable()
                    .forceSolidOff().strength(0.1F).sound(SoundType.SAND).pushReaction(PushReaction.DESTROY)
                    .isViewBlocking((s, l, p) -> s.getValue(com.reiko.weather.block.VolcanicAshBlock.LAYERS) >= 8)));

    /** Snow reddened by desert dust settling out of a haze (fresh snow buries it again). */
    public static final DeferredBlock<net.minecraft.world.level.block.SnowLayerBlock> DUSTY_SNOW = REGISTER.register("dusty_snow",
            () -> new net.minecraft.world.level.block.SnowLayerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_PINK).replaceable()
                    .forceSolidOff().randomTicks().strength(0.1F).requiresCorrectToolForDrops().sound(SoundType.SNOW)
                    .isViewBlocking((s, l, p) -> s.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) >= 8)
                    .pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<com.reiko.weather.block.PeatBlock> PEAT = REGISTER.register("peat",
            () -> new com.reiko.weather.block.PeatBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(0.6F)
                    .sound(SoundType.ROOTED_DIRT).lightLevel(s -> s.getValue(com.reiko.weather.block.PeatBlock.SMOULDERING) ? 3 : 0)));

    public static final DeferredBlock<com.reiko.weather.block.RimeBlock> RIME = REGISTER.register("rime",
            () -> new com.reiko.weather.block.RimeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).replaceable().noCollission()
                    .strength(0.1F).sound(SoundType.GLASS).noOcclusion().randomTicks().pushReaction(PushReaction.DESTROY).noLootTable()));

    public static final DeferredBlock<com.reiko.weather.block.SnowRollerBlock> SNOW_ROLLER = REGISTER.register("snow_roller",
            () -> new com.reiko.weather.block.SnowRollerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.2F)
                    .sound(SoundType.SNOW).noOcclusion().randomTicks().pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<com.reiko.weather.block.QuicksandBlock> QUICKSAND = REGISTER.register("quicksand",
            () -> new com.reiko.weather.block.QuicksandBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.5F)
                    .sound(SoundType.MUD).dynamicShape().isRedstoneConductor((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> true)));

    public static final DeferredBlock<com.reiko.weather.block.BlowholeBlock> BLOWHOLE = REGISTER.register("blowhole",
            () -> new com.reiko.weather.block.BlowholeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5F, 6.0F)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<net.minecraft.world.level.block.Block> LIMESTONE = REGISTER.register("limestone",
            () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(1.2F, 5.0F)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops()));

    private ModBlocks() {}
}
