package com.reiko.weather.item;

import com.reiko.weather.entity.CloudSeedingRocketEntity;
import com.reiko.weather.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

/** Fire it into the open sky to seed a rain cloud overhead. */
public class CloudSeedingRocketItem extends Item {
    public CloudSeedingRocketItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            CloudSeedingRocketEntity rocket = new CloudSeedingRocketEntity(ModEntities.CLOUD_SEEDING_ROCKET.get(), level);
            rocket.owner = player.getUUID();
            rocket.moveTo(player.getX(), player.getEyeY() - 0.2, player.getZ(), 0.0F, 0.0F);
            rocket.setDeltaMovement(player.getLookAngle().x * 0.15, 0.6, player.getLookAngle().z * 0.15);
            level.addFreshEntity(rocket);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 3.0F, 0.8F);
        }
        stack.consume(1, player);
        player.getCooldowns().addCooldown(this, 40);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.reikos_weather.cloud_seeding_rocket.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
