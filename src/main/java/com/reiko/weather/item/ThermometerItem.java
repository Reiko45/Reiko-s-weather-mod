package com.reiko.weather.item;

import com.reiko.weather.temperature.Temperature;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Shows the air temperature and how warm you feel while held; right-click for a reading in chat. */
public class ThermometerItem extends Item {
    public ThermometerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            var local = WeatherManager.local(level, player.blockPosition());
            float air = Temperature.ambient(level, player.blockPosition().above(), local);
            float felt = Temperature.felt(level, player, local);
            serverPlayer.sendSystemMessage(Component.translatable("item.reikos_weather.thermometer.reading",
                    Component.literal(Temperature.format(air, false) + " / " + Temperature.format(air, true)).withStyle(ChatFormatting.WHITE),
                    Component.literal(Temperature.format(felt, false)).withStyle(Temperature.Feel.of(felt).color),
                    Temperature.Feel.of(felt).displayName()));
        }
        player.getCooldowns().addCooldown(this, 20);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.reikos_weather.thermometer.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
