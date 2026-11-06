package com.reiko.weather.compat.toughasnails;

import com.reiko.weather.block.TentBlock;
import com.reiko.weather.compat.Compat;
import com.reiko.weather.registry.ModEffects;
import com.reiko.weather.registry.ModItems;
import com.reiko.weather.weather.LocalWeather;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.WeatherProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import toughasnails.api.temperature.TemperatureHelper;
import toughasnails.api.temperature.TemperatureLevel;

/**
 * Feeds this mod's weather into Tough As Nails' temperature: blizzards and freezing storms chill a spot,
 * heatwaves warm it, and our drinks, sun hat and tent work on TaN's scale too. Only loaded with TaN.
 */
public final class TanIntegration {
    public static void register() {
        TemperatureHelper.registerPositionalTemperatureModifier(TanIntegration::weather);
        TemperatureHelper.registerPlayerTemperatureModifier(TanIntegration::player);
    }

    public static boolean temperatureEnabled() {
        return TemperatureHelper.isTemperatureEnabled();
    }

    private static TemperatureLevel weather(Level level, BlockPos pos, TemperatureLevel current) {
        if (!Compat.toughAsNailsHandlesTemperature()) {
            return current;
        }
        LocalWeather local = WeatherManager.local(level, pos);
        if (local == null || !level.canSeeSky(pos.above())) {
            return current;
        }
        WeatherProfile profile = local.profile();
        if (profile.freeze >= 0.9F) {
            return current.decrement(2);
        }
        if (profile.freeze > 0.2F || profile.wind > 0.8F && profile.rain > 0.5F) {
            return current.decrement(1);
        }
        if (profile.heat) {
            return current.increment(1);
        }
        return current;
    }

    private static TemperatureLevel player(Player player, TemperatureLevel current) {
        if (!Compat.toughAsNailsHandlesTemperature()) {
            return current;
        }
        TemperatureLevel level = current;
        if (player.hasEffect(ModEffects.WARMTH)) level = level.increment(1);
        if (player.hasEffect(ModEffects.REFRESHED)) level = level.decrement(1);
        if (Compat.wearing(player, EquipmentSlot.HEAD, ModItems.SUN_HAT.get()) && player.level().isDay()
                && player.level().canSeeSky(player.blockPosition().above()) && level.ordinal() > TemperatureLevel.NEUTRAL.ordinal()) {
            level = level.decrement(1);
        }
        if (TentBlock.isInside(player)) {
            // a tent takes the edge off either way
            if (level.ordinal() < TemperatureLevel.NEUTRAL.ordinal()) level = level.increment(1);
            else if (level.ordinal() > TemperatureLevel.NEUTRAL.ordinal()) level = level.decrement(1);
        }
        return level;
    }

    private TanIntegration() {}
}
