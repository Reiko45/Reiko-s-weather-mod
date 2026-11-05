package com.reiko.weather.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Hold it to stay dry: it blocks hail, and opening it mid-fall lets you drift down gently. */
public class UmbrellaItem extends Item {
    public UmbrellaItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.reikos_weather.umbrella.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
