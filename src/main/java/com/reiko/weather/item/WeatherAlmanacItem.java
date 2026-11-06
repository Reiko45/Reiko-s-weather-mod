package com.reiko.weather.item;

import com.reiko.weather.ClientActions;
import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;

/** The mod's guidebook: everything about the weather, the seasons and the gear, with recipes. */
public class WeatherAlmanacItem extends Item {
    private static final String GOT_ONE = "reikos_weather.got_almanac";

    public WeatherAlmanacItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            ClientActions.Holder.instance.openGuide();
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.reikos_weather.weather_almanac.tooltip").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    /** New players get a copy the first time they join (configurable). */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !WeatherConfig.GUIDE_ON_JOIN.get()) {
            return;
        }
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GOT_ONE)) {
            return;
        }
        persisted.putBoolean(GOT_ONE, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        ItemStack book = new ItemStack(ModItems.WEATHER_ALMANAC.get());
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
    }
}
