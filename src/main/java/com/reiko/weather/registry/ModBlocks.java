package com.reiko.weather.registry;

import com.reiko.weather.ReikosWeather;
import com.reiko.weather.block.IceGlazeBlock;
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

    private ModBlocks() {}
}
