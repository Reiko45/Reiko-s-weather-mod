package com.reiko.weather.compat;

import com.reiko.weather.block.IceboxBlock;
import com.reiko.weather.block.LeafPileBlock;
import com.reiko.weather.block.LightningBatteryBlock;
import com.reiko.weather.block.RainBarrelBlock;
import com.reiko.weather.block.StormProbeBlock;
import com.reiko.weather.block.TornadoSirenBlock;
import com.reiko.weather.block.WeatherRadarBlock;
import com.reiko.weather.block.WeatherStationBlock;
import com.reiko.weather.block.WeatherVaneBlock;
import com.reiko.weather.block.WindmillBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * What to show about our blocks in Jade or WTHIT tooltips. Everything comes from the block state, so it
 * works on any server without extra syncing.
 */
public final class BlockInfo {
    /** Every block class that gets a tooltip line. */
    public static final List<Class<? extends Block>> BLOCKS = List.of(LightningBatteryBlock.class, StormProbeBlock.class, RainBarrelBlock.class,
            WeatherStationBlock.class, WeatherRadarBlock.class, WeatherVaneBlock.class, WindmillBlock.class, TornadoSirenBlock.class,
            LeafPileBlock.class, IceboxBlock.class);

    private static final String[] DIRECTIONS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    public static List<Component> lines(BlockState state) {
        List<Component> lines = new ArrayList<>();
        Block block = state.getBlock();
        if (block instanceof LightningBatteryBlock) {
            lines.add(line("charge", state.getValue(LightningBatteryBlock.CHARGE), LightningBatteryBlock.MAX_CHARGE));
        } else if (block instanceof StormProbeBlock) {
            lines.add(Component.translatable("tooltip.reikos_weather.info.readings." + state.getValue(StormProbeBlock.READINGS)).withStyle(ChatFormatting.GRAY));
        } else if (block instanceof RainBarrelBlock) {
            lines.add(line("water", state.getValue(RainBarrelBlock.LEVEL), 3));
        } else if (block instanceof WeatherStationBlock) {
            lines.add(Component.translatable("tooltip.reikos_weather.info.mode",
                    Component.translatable("block.reikos_weather.weather_station.mode." + state.getValue(WeatherStationBlock.MODE).getSerializedName()))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(line("signal", state.getValue(WeatherStationBlock.POWER), 15));
        } else if (block instanceof WeatherRadarBlock) {
            lines.add(line("signal", state.getValue(WeatherRadarBlock.SIGNAL), 15));
        } else if (block instanceof WeatherVaneBlock) {
            int d = state.getValue(WeatherVaneBlock.DIRECTION);
            lines.add(d == 0 ? Component.translatable("tooltip.reikos_weather.info.calm").withStyle(ChatFormatting.GRAY)
                    : Component.translatable("tooltip.reikos_weather.info.wind_from",
                    Component.translatable("direction.reikos_weather." + DIRECTIONS[(d - 1) % 8])).withStyle(ChatFormatting.GRAY));
        } else if (block instanceof WindmillBlock) {
            lines.add(line("signal", state.getValue(WindmillBlock.POWER), 15));
        } else if (block instanceof TornadoSirenBlock) {
            if (state.getValue(TornadoSirenBlock.POWERED)) {
                lines.add(Component.translatable("tooltip.reikos_weather.info.siren").withStyle(ChatFormatting.RED));
            }
        } else if (block instanceof LeafPileBlock) {
            lines.add(line("layers", state.getValue(LeafPileBlock.LAYERS), LeafPileBlock.MAX_LAYERS));
        } else if (block instanceof IceboxBlock) {
            lines.add(Component.translatable("tooltip.reikos_weather.info.icebox").withStyle(ChatFormatting.AQUA));
        }
        return lines;
    }

    private static Component line(String key, int value, int max) {
        return Component.translatable("tooltip.reikos_weather.info." + key, value, max).withStyle(ChatFormatting.GRAY);
    }

    private BlockInfo() {}
}
