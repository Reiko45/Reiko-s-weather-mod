package com.reiko.weather.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Hold use in mid-air to open the paraglider. You glide forward where you look, the wind carries you,
 * and warm air rising off lava, fires, hot sand and sunny slopes lifts you up (see {@link Paragliding}).
 */
public class ParagliderItem extends Item {
    public ParagliderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.onGround() || player.isInWater() || player.getAbilities().flying || player.isPassenger() || player.isFallFlying()) {
            return InteractionResultHolder.pass(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    public static boolean gliding(LivingEntity entity) {
        return entity.isUsingItem() && entity.getUseItem().getItem() instanceof ParagliderItem
                && !entity.onGround() && !entity.isInWater() && !entity.isPassenger()
                && !(entity instanceof Player p && p.getAbilities().flying);
    }
}
