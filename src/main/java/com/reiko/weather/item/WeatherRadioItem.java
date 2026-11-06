package com.reiko.weather.item;

import com.reiko.weather.registry.ModDataComponents;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.WeatherReport;
import com.reiko.weather.weather.WeatherState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

/** Right-click to switch it on or off (sneak + right-click for the current bulletin). */
public class WeatherRadioItem extends Item {
    public WeatherRadioItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                if (level instanceof ServerLevel server && WeatherManager.handles(server)) {
                    WeatherState state = WeatherManager.state(server);
                    player.sendSystemMessage(Component.translatable("radio.reikos_weather.prefix").withStyle(ChatFormatting.GOLD));
                    player.sendSystemMessage(WeatherReport.current(server, state, player.blockPosition()));
                    player.sendSystemMessage(WeatherReport.forecast(state));
                    Component storm = WeatherReport.approachingStorm(state, player.blockPosition());
                    if (storm != null) player.sendSystemMessage(storm);
                    Component volcano = WeatherReport.volcano(server, player.blockPosition());
                    if (volcano != null) player.sendSystemMessage(volcano);
                } else {
                    player.sendSystemMessage(Component.translatable("radio.reikos_weather.static").withStyle(ChatFormatting.GRAY));
                }
            } else {
                boolean on = !stack.getOrDefault(ModDataComponents.RADIO_ON.get(), true);
                stack.set(ModDataComponents.RADIO_ON.get(), on);
                player.displayClientMessage(Component.translatable(on ? "radio.reikos_weather.on" : "radio.reikos_weather.off"), true);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.4F, 1.6F);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean on = stack.getOrDefault(ModDataComponents.RADIO_ON.get(), true);
        tooltip.add(Component.translatable(on ? "item.reikos_weather.weather_radio.on" : "item.reikos_weather.weather_radio.off")
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.reikos_weather.weather_radio.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
