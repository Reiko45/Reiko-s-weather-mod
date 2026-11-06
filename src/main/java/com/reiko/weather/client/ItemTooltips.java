package com.reiko.weather.client;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.item.ChargedTools;
import com.reiko.weather.registry.ModTags;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Tooltip lines for perishable food and lightning-charged tools, and sparks off a charged tool in hand. */
public final class ItemTooltips {
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Minecraft mc = Minecraft.getInstance();
        if (stack.is(ModTags.PERISHABLE) && WeatherConfig.SPEC.isLoaded() && WeatherConfig.FOOD_SPOILAGE.get()) {
            event.getToolTip().add(Component.translatable("tooltip.reikos_weather.perishable").withStyle(ChatFormatting.GOLD));
        }
        if (mc.level != null) {
            long left = ChargedTools.remaining(stack, mc.level);
            if (left > 0) {
                long seconds = left / 20;
                event.getToolTip().add(Component.translatable("tooltip.reikos_weather.charged", seconds / 60, String.format("%02d", seconds % 60))
                        .withStyle(ChatFormatting.AQUA));
            }
        }
    }

    public static void tick(Minecraft mc) {
        Player player = mc.player;
        if (player == null || mc.level == null || player.tickCount % 4 != 0 || !ChargedTools.isCharged(player.getMainHandItem(), mc.level)) {
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0, look.x).normalize().scale(0.35);
        Vec3 at = player.getEyePosition().add(look.scale(0.6)).add(side).add(0, -0.35, 0);
        mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, (player.getRandom().nextDouble() - 0.5) * 0.2, 0.05,
                (player.getRandom().nextDouble() - 0.5) * 0.2);
    }

    private ItemTooltips() {}
}
