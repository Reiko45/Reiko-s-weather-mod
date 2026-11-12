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
            () -> new com.reiko.weather.block.DustySnowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_PINK).replaceable()
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

    public static final DeferredBlock<net.minecraft.world.level.block.RotatedPillarBlock> PETRIFIED_WOOD = REGISTER.register("petrified_wood",
            () -> new net.minecraft.world.level.block.RotatedPillarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN)
                    .strength(2.0F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<net.minecraft.world.level.block.Block> PUMICE = REGISTER.register("pumice",
            () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(0.6F, 1.0F)
                    .sound(SoundType.TUFF)));

    public static final DeferredBlock<com.reiko.weather.block.WhirlpoolBlock> WHIRLPOOL = REGISTER.register("whirlpool",
            () -> new com.reiko.weather.block.WhirlpoolBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(3.0F, 6.0F)
                    .sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<com.reiko.weather.block.ThinLayerBlock> SEA_FOAM = REGISTER.register("sea_foam",
            () -> new com.reiko.weather.block.ThinLayerBlock(com.reiko.weather.block.ThinLayerBlock.Kind.SEA_FOAM, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW).strength(0.0F).sound(SoundType.WET_SPONGE).noOcclusion().randomTicks().replaceable()
                    .pushReaction(PushReaction.DESTROY).noLootTable()));

    public static final DeferredBlock<com.reiko.weather.block.ThinLayerBlock> NEEDLE_ICE = REGISTER.register("needle_ice",
            () -> new com.reiko.weather.block.ThinLayerBlock(com.reiko.weather.block.ThinLayerBlock.Kind.NEEDLE_ICE, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE).strength(0.0F).sound(SoundType.GLASS).noOcclusion().randomTicks().replaceable()
                    .pushReaction(PushReaction.DESTROY).noLootTable()));

    public static final DeferredBlock<com.reiko.weather.block.ThinLayerBlock> FROST_FLOWERS = REGISTER.register("frost_flowers",
            () -> new com.reiko.weather.block.ThinLayerBlock(com.reiko.weather.block.ThinLayerBlock.Kind.FROST_FLOWERS, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW).strength(0.0F).sound(SoundType.POWDER_SNOW).noOcclusion().randomTicks().replaceable()
                    .pushReaction(PushReaction.DESTROY).noLootTable()));

    public static final DeferredBlock<net.minecraft.world.level.block.ColoredFallingBlock> GYPSUM_SAND = REGISTER.register("gypsum_sand",
            () -> new net.minecraft.world.level.block.ColoredFallingBlock(new net.minecraft.util.ColorRGBA(0xF2EFE6),
                    BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.5F).sound(SoundType.SAND)));

    public static final DeferredBlock<net.minecraft.world.level.block.ColoredFallingBlock> SHELL_SAND = REGISTER.register("shell_sand",
            () -> new net.minecraft.world.level.block.ColoredFallingBlock(new net.minecraft.util.ColorRGBA(0xE6DCC8),
                    BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.5F).sound(SoundType.SAND)));

    public static final DeferredBlock<net.minecraft.world.level.block.ColoredFallingBlock> OLIVINE_SAND = REGISTER.register("olivine_sand",
            () -> new net.minecraft.world.level.block.ColoredFallingBlock(new net.minecraft.util.ColorRGBA(0x6E7F3A),
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.5F).sound(SoundType.SAND)));

    public static final DeferredBlock<net.minecraft.world.level.block.ColoredFallingBlock> PINK_SAND = REGISTER.register("pink_sand",
            () -> new net.minecraft.world.level.block.ColoredFallingBlock(new net.minecraft.util.ColorRGBA(0xE8B8B0),
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(0.5F).sound(SoundType.SAND)));

    public static final DeferredBlock<net.minecraft.world.level.block.Block> ROCK_SALT = REGISTER.register("rock_salt",
            () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(1.0F, 2.0F)
                    .sound(SoundType.CALCITE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<com.reiko.weather.block.HailstonesBlock> HAILSTONES = REGISTER.register("hailstones",
            () -> new com.reiko.weather.block.HailstonesBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).replaceable().forceSolidOff()
                    .strength(0.1F).sound(SoundType.POWDER_SNOW).randomTicks().pushReaction(PushReaction.DESTROY).noLootTable()
                    .isViewBlocking((s, l, pos) -> s.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) >= 8)));

    /** Watermelon snow: old summer snow stained pink by snow algae. */
    public static final DeferredBlock<com.reiko.weather.block.PinkSnowBlock> PINK_SNOW = REGISTER.register("pink_snow",
            () -> new com.reiko.weather.block.PinkSnowBlock(BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.SNOW)
                    .mapColor(MapColor.COLOR_PINK)));

    /** A waterfall frozen into a hanging curtain of ice: it melts away to nothing and the water runs again. */
    public static final DeferredBlock<com.reiko.weather.block.FrozenWaterfallBlock> FROZEN_WATERFALL = REGISTER.register("frozen_waterfall",
            () -> new com.reiko.weather.block.FrozenWaterfallBlock(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(0.5F)
                    .sound(SoundType.GLASS).noOcclusion().randomTicks().friction(0.98F).pushReaction(PushReaction.DESTROY).noLootTable()
                    .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    /** Warm geothermal ground: it steams, and snow melts off it. */
    public static final DeferredBlock<com.reiko.weather.block.SteamingGroundBlock> STEAMING_GROUND = REGISTER.register("steaming_ground",
            () -> new com.reiko.weather.block.SteamingGroundBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(0.6F)
                    .sound(SoundType.GRAVEL).randomTicks()));

    /** A brinicle: a hollow finger of ice growing down from sea ice to the sea floor. */
    public static final DeferredBlock<com.reiko.weather.block.BrinicleBlock> BRINICLE = REGISTER.register("brinicle",
            () -> new com.reiko.weather.block.BrinicleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(0.3F)
                    .sound(SoundType.GLASS).noOcclusion().randomTicks().pushReaction(PushReaction.DESTROY).noLootTable()
                    .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    /** Frazil ice: slush of loose ice crystals clogging a bitterly cold river (or coating its bed as anchor ice). */
    public static final DeferredBlock<com.reiko.weather.block.FrazilIceBlock> FRAZIL_ICE = REGISTER.register("frazil_ice",
            () -> new com.reiko.weather.block.FrazilIceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(0.2F)
                    .sound(SoundType.SNOW).noCollission().noOcclusion().randomTicks().pushReaction(PushReaction.DESTROY).noLootTable()
                    .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    /** Beach wrack: kelp and seagrass thrown up along the tideline by a storm. */
    public static final DeferredBlock<com.reiko.weather.block.BeachWrackBlock> BEACH_WRACK = REGISTER.register("beach_wrack",
            () -> new com.reiko.weather.block.BeachWrackBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(0.1F)
                    .sound(SoundType.WET_GRASS).noOcclusion().randomTicks().pushReaction(PushReaction.DESTROY)));

    /** Fireweed: tall magenta flowers, the first to come back on burnt ground. */
    public static final DeferredBlock<net.minecraft.world.level.block.FlowerBlock> FIREWEED = REGISTER.register("fireweed",
            () -> new net.minecraft.world.level.block.FlowerBlock(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 4.0F,
                    BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.ALLIUM).mapColor(MapColor.COLOR_MAGENTA)));
    public static final DeferredBlock<net.minecraft.world.level.block.FlowerPotBlock> POTTED_FIREWEED = REGISTER.register("potted_fireweed",
            () -> new net.minecraft.world.level.block.FlowerPotBlock(() -> (net.minecraft.world.level.block.FlowerPotBlock) net.minecraft.world.level.block.Blocks.FLOWER_POT,
                    FIREWEED, BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.POTTED_ALLIUM)));

    /** Charred soil left by a wildfire: it recovers over the seasons, through fireweed to grass and young trees. */
    public static final DeferredBlock<com.reiko.weather.block.CharredSoilBlock> CHARRED_SOIL = REGISTER.register("charred_soil",
            () -> new com.reiko.weather.block.CharredSoilBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5F)
                    .sound(SoundType.GRAVEL).randomTicks()));

    /** Hair ice: fine white hair combed out of a rotting log on a damp, frosty night. */
    public static final DeferredBlock<com.reiko.weather.block.ThinLayerBlock> HAIR_ICE = REGISTER.register("hair_ice",
            () -> new com.reiko.weather.block.ThinLayerBlock(com.reiko.weather.block.ThinLayerBlock.Kind.HAIR_ICE, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW).strength(0.0F).sound(SoundType.POWDER_SNOW).noOcclusion().randomTicks().replaceable()
                    .pushReaction(PushReaction.DESTROY).noLootTable()));

    /** Slush: snow soaked through by rain. */
    public static final DeferredBlock<com.reiko.weather.block.SlushBlock> SLUSH = REGISTER.register("slush",
            () -> new com.reiko.weather.block.SlushBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).replaceable()
                    .forceSolidOff().randomTicks().strength(0.1F).sound(SoundType.WET_GRASS).pushReaction(PushReaction.DESTROY).noLootTable()
                    .isViewBlocking((s, l, p) -> s.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) >= 8)));

    /** Crusted snow: slush frozen hard and glassy overnight. */
    public static final DeferredBlock<com.reiko.weather.block.SnowCrustBlock> SNOW_CRUST = REGISTER.register("snow_crust",
            () -> new com.reiko.weather.block.SnowCrustBlock(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).replaceable()
                    .forceSolidOff().randomTicks().strength(0.3F).friction(0.98F).sound(SoundType.GLASS).requiresCorrectToolForDrops()
                    .pushReaction(PushReaction.DESTROY)
                    .isViewBlocking((s, l, p) -> s.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) >= 8)));

    /** Lake ice with stacks of marsh-gas bubbles frozen into it. */
    public static final DeferredBlock<com.reiko.weather.block.BubbleIceBlock> BUBBLE_ICE = REGISTER.register("bubble_ice",
            () -> new com.reiko.weather.block.BubbleIceBlock(BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.ICE)));

    /** Clinker: shale and sandstone baked hard and brick-red by a coal seam burning underneath. */
    public static final DeferredBlock<net.minecraft.world.level.block.Block> CLINKER = REGISTER.register("clinker",
            () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_RED).strength(1.5F, 6.0F)
                    .sound(SoundType.DEEPSLATE_BRICKS).requiresCorrectToolForDrops()));

    /** Smouldering ground over a burning coal seam: hot, smoking and bare. */
    public static final DeferredBlock<com.reiko.weather.block.SmoulderingGroundBlock> SMOULDERING_GROUND = REGISTER.register("smouldering_ground",
            () -> new com.reiko.weather.block.SmoulderingGroundBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.8F)
                    .sound(SoundType.GRAVEL).randomTicks().lightLevel(s -> 3)));

    /** Desert wildflowers, which only flower after a wet winter. */
    public static final DeferredBlock<com.reiko.weather.block.DesertFlowerBlock> DESERT_GOLD = REGISTER.register("desert_gold",
            () -> new com.reiko.weather.block.DesertFlowerBlock(net.minecraft.world.effect.MobEffects.SATURATION, 0.35F,
                    BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DANDELION).mapColor(MapColor.COLOR_YELLOW).randomTicks()));
    public static final DeferredBlock<com.reiko.weather.block.DesertFlowerBlock> SAND_VERBENA = REGISTER.register("sand_verbena",
            () -> new com.reiko.weather.block.DesertFlowerBlock(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 5.0F,
                    BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.ALLIUM).mapColor(MapColor.COLOR_PURPLE).randomTicks()));
    public static final DeferredBlock<com.reiko.weather.block.DesertFlowerBlock> DESERT_POPPY = REGISTER.register("desert_poppy",
            () -> new com.reiko.weather.block.DesertFlowerBlock(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 4.0F,
                    BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.POPPY).mapColor(MapColor.COLOR_ORANGE).randomTicks()));
    public static final DeferredBlock<net.minecraft.world.level.block.FlowerPotBlock> POTTED_DESERT_GOLD = potted("potted_desert_gold", DESERT_GOLD);
    public static final DeferredBlock<net.minecraft.world.level.block.FlowerPotBlock> POTTED_SAND_VERBENA = potted("potted_sand_verbena", SAND_VERBENA);
    public static final DeferredBlock<net.minecraft.world.level.block.FlowerPotBlock> POTTED_DESERT_POPPY = potted("potted_desert_poppy", DESERT_POPPY);

    /** Mud season: spring mud on bare ground and on dirt paths (each dries back to what it was). */
    public static final DeferredBlock<com.reiko.weather.block.SoftMudBlock> SOFT_MUD = REGISTER.register("soft_mud",
            () -> new com.reiko.weather.block.SoftMudBlock(false, BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.5F)
                    .sound(SoundType.MUD).randomTicks()));
    public static final DeferredBlock<com.reiko.weather.block.SoftMudBlock> MUDDY_PATH = REGISTER.register("muddy_path",
            () -> new com.reiko.weather.block.SoftMudBlock(true, BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.5F)
                    .sound(SoundType.MUD).randomTicks()));

    /** A puddle frozen over on a frosty morning. */
    public static final DeferredBlock<com.reiko.weather.block.FrozenPuddleBlock> FROZEN_PUDDLE = REGISTER.register("frozen_puddle",
            () -> new com.reiko.weather.block.FrozenPuddleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(0.0F)
                    .sound(SoundType.GLASS).noOcclusion().randomTicks().replaceable().pushReaction(PushReaction.DESTROY).noLootTable()));

    /** Selenite: great glassy crystals of gypsum, grown slowly in hot, mineral-rich water deep underground. */
    public static final DeferredBlock<net.minecraft.world.level.block.RotatedPillarBlock> SELENITE = REGISTER.register("selenite",
            () -> new net.minecraft.world.level.block.RotatedPillarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(1.0F)
                    .sound(SoundType.AMETHYST).noOcclusion().requiresCorrectToolForDrops()
                    .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    /** A gas vent: scorched rock with natural gas seeping through it, which keeps a fire burning over it forever. */
    public static final DeferredBlock<com.reiko.weather.block.GasVentBlock> GAS_VENT = REGISTER.register("gas_vent",
            () -> new com.reiko.weather.block.GasVentBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5F, 6.0F)
                    .sound(SoundType.STONE).randomTicks().requiresCorrectToolForDrops().lightLevel(s -> 5)));

    /** A snow bridge over a stream or a crevasse: just snow to look at, but it gives way. */
    public static final DeferredBlock<com.reiko.weather.block.SnowBridgeBlock> SNOW_BRIDGE = REGISTER.register("snow_bridge",
            () -> new com.reiko.weather.block.SnowBridgeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.2F)
                    .sound(SoundType.SNOW).randomTicks()));

    /** Aufeis: layer on layer of ice frozen out of seeping winter water. */
    public static final DeferredBlock<com.reiko.weather.block.AufeisBlock> AUFEIS = REGISTER.register("aufeis",
            () -> new com.reiko.weather.block.AufeisBlock(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(0.5F)
                    .friction(0.98F).sound(SoundType.GLASS).noOcclusion().randomTicks()));

    /** Driftwood, washed up on the beach. */
    public static final DeferredBlock<com.reiko.weather.block.DriftwoodBlock> DRIFTWOOD = REGISTER.register("driftwood",
            () -> new com.reiko.weather.block.DriftwoodBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(1.5F)
                    .sound(SoundType.WOOD).ignitedByLava()));

    /** A floating mat of sphagnum over a bog pool. */
    public static final DeferredBlock<com.reiko.weather.block.QuakingMatBlock> QUAKING_MAT = REGISTER.register("quaking_mat",
            () -> new com.reiko.weather.block.QuakingMatBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).strength(0.4F)
                    .sound(SoundType.MOSS).speedFactor(0.6F).noOcclusion()));

    /** Glowworms on a cave roof. */
    public static final DeferredBlock<com.reiko.weather.block.GlowwormsBlock> GLOWWORMS = REGISTER.register("glowworms",
            () -> new com.reiko.weather.block.GlowwormsBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).instabreak()
                    .noCollission().sound(SoundType.COBWEB).lightLevel(s -> 5).noOcclusion().pushReaction(PushReaction.DESTROY)));

    /** Ochre: rock stained rust-red and orange by iron-rich water. */
    public static final DeferredBlock<net.minecraft.world.level.block.Block> OCHRE = REGISTER.register("ochre",
            () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(1.25F, 4.2F)
                    .sound(SoundType.TUFF).requiresCorrectToolForDrops()));

    /** A tumbleweed come to rest. */
    public static final DeferredBlock<com.reiko.weather.block.TumbleweedBlock> TUMBLEWEED = REGISTER.register("tumbleweed",
            () -> new com.reiko.weather.block.TumbleweedBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instabreak()
                    .noCollission().sound(SoundType.GRASS).noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY)));

    /** Gibber: desert pavement, a mosaic of dark, wind-polished, sun-varnished pebbles packed together on the ground. */
    public static final DeferredBlock<net.minecraft.world.level.block.Block> GIBBER = REGISTER.register("gibber",
            () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(0.8F)
                    .sound(SoundType.GRAVEL)));

    /** Foxfire glowing on a rotting log. */
    public static final DeferredBlock<com.reiko.weather.block.FoxfireBlock> FOXFIRE = REGISTER.register("foxfire",
            () -> new com.reiko.weather.block.FoxfireBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).instabreak()
                    .noCollission().sound(SoundType.FUNGUS).lightLevel(s -> 3).noOcclusion().randomTicks().replaceable().pushReaction(PushReaction.DESTROY)));

    /** Crust lichens on rocks by the sea: orange above, black tar lichen just over the waterline. */
    public static final DeferredBlock<com.reiko.weather.block.CrustLichenBlock> ORANGE_LICHEN = REGISTER.register("orange_lichen",
            () -> new com.reiko.weather.block.CrustLichenBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).replaceable()
                    .noCollission().strength(0.2F).sound(SoundType.GLOW_LICHEN).noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<com.reiko.weather.block.CrustLichenBlock> BLACK_LICHEN = REGISTER.register("black_lichen",
            () -> new com.reiko.weather.block.CrustLichenBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).replaceable()
                    .noCollission().strength(0.2F).sound(SoundType.GLOW_LICHEN).noOcclusion().pushReaction(PushReaction.DESTROY)));

    /** A glacier mouse: a ball of moss on the ice. */
    public static final DeferredBlock<com.reiko.weather.block.GlacierMouseBlock> GLACIER_MOUSE = REGISTER.register("glacier_mouse",
            () -> new com.reiko.weather.block.GlacierMouseBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.1F)
                    .sound(SoundType.MOSS).noOcclusion().randomTicks().pushReaction(PushReaction.DESTROY)));

    /** A snow ghost's needles: spruce plastered white with rime and snow. */
    public static final DeferredBlock<com.reiko.weather.block.SnowGhostLeavesBlock> SNOW_GHOST_LEAVES = REGISTER.register("snow_ghost_leaves",
            () -> new com.reiko.weather.block.SnowGhostLeavesBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.2F)
                    .randomTicks().sound(SoundType.SNOW).noOcclusion().isValidSpawn((s, l, p, e) -> false).isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false).ignitedByLava().pushReaction(PushReaction.DESTROY).isRedstoneConductor((s, l, p) -> false)));

    /** Candle ice washed up on a lake shore in the spring thaw. */
    public static final DeferredBlock<com.reiko.weather.block.CandleIceBlock> CANDLE_ICE = REGISTER.register("candle_ice",
            () -> new com.reiko.weather.block.CandleIceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(0.1F)
                    .sound(SoundType.GLASS).noOcclusion().noCollission().randomTicks().replaceable().pushReaction(PushReaction.DESTROY).noLootTable()));

    private static DeferredBlock<net.minecraft.world.level.block.FlowerPotBlock> potted(String name, DeferredBlock<? extends net.minecraft.world.level.block.Block> flower) {
        return REGISTER.register(name, () -> new net.minecraft.world.level.block.FlowerPotBlock(
                () -> (net.minecraft.world.level.block.FlowerPotBlock) net.minecraft.world.level.block.Blocks.FLOWER_POT, flower,
                BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.POTTED_ALLIUM)));
    }

    private ModBlocks() {}
}
