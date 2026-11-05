package com.reiko.weather.item;

import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.WeatherReport;
import com.reiko.weather.weather.WeatherState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Right-click to read the current weather, the wind and the forecast. */
public class BarometerItem extends Item {
    public BarometerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            WeatherState state = WeatherManager.activeState(level);
            if (state == null) {
                serverPlayer.sendSystemMessage(Component.translatable("item.reikos_weather.barometer.no_reading").withStyle(ChatFormatting.GRAY));
            } else {
                ServerLevel serverLevel = (ServerLevel) level;
                serverPlayer.sendSystemMessage(WeatherReport.current(serverLevel, state, player.blockPosition()));
                serverPlayer.sendSystemMessage(WeatherReport.wind(serverLevel, state, player.blockPosition()));
                serverPlayer.sendSystemMessage(WeatherReport.forecast(state));
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.COPPER_BULB_TURN_ON, SoundSource.PLAYERS, 0.6F, 1.4F);
        }
        player.getCooldowns().addCooldown(this, 20);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.reikos_weather.barometer.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
