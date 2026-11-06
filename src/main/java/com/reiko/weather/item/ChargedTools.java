package com.reiko.weather.item;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModDataComponents;
import com.reiko.weather.registry.ModTags;
import com.reiko.weather.weather.WeatherAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Leave a tool or weapon on an anvil or a copper block in a thunderstorm (a lightning rod helps) and a
 * strike charges it: for five minutes it mines faster and hits harder, with an arc of electricity.
 */
public final class ChargedTools {
    public static final int CHARGE_TICKS = 6000;

    public static void onStruckByLightning(EntityStruckByLightningEvent event) {
        if (!(event.getEntity() instanceof ItemEntity item) || !(item.level() instanceof ServerLevel level) || !WeatherConfig.CHARGED_TOOLS.get()) {
            return;
        }
        ItemStack stack = item.getItem();
        BlockPos on = item.blockPosition();
        boolean conductor = level.getBlockState(on).is(ModTags.CONDUCTS_CHARGE) || level.getBlockState(on.below()).is(ModTags.CONDUCTS_CHARGE);
        if (!stack.isDamageableItem() || !conductor) {
            return;
        }
        event.setCanceled(true);
        charge(stack, level.getGameTime());
        item.setItem(stack);
        // the strike leaves fire around; keep the tool safe from it for a few seconds
        item.setInvulnerable(true);
        level.getServer().tell(new TickTask(level.getServer().getTickCount() + 100, () -> item.setInvulnerable(false)));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, item.getX(), item.getY() + 0.3, item.getZ(), 40, 0.3, 0.3, 0.3, 0.3);
        level.playSound(null, on, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.BLOCKS, 0.6F, 1.6F);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(on).inflate(24))) {
            WeatherAdvancements.award(player, "supercharged");
        }
    }

    public static void charge(ItemStack stack, long gameTime) {
        stack.set(ModDataComponents.CHARGED_UNTIL.get(), gameTime + CHARGE_TICKS);
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
    }

    public static boolean isCharged(ItemStack stack, Level level) {
        Long until = stack.get(ModDataComponents.CHARGED_UNTIL.get());
        return until != null && until > level.getGameTime();
    }

    /** Ticks of charge left, or 0. */
    public static long remaining(ItemStack stack, Level level) {
        Long until = stack.get(ModDataComponents.CHARGED_UNTIL.get());
        return until == null ? 0 : Math.max(0, until - level.getGameTime());
    }

    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (isCharged(event.getEntity().getMainHandItem(), event.getEntity().level())) {
            event.setNewSpeed(event.getNewSpeed() * 1.6F);
        }
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getDirectEntity();
        if (!(attacker instanceof Player player) || !(player.level() instanceof ServerLevel level)
                || !isCharged(player.getMainHandItem(), level)) {
            return;
        }
        LivingEntity target = event.getEntity();
        event.setAmount(event.getAmount() + 3.0F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.5), target.getZ(), 12, 0.3, 0.4, 0.3, 0.2);
        level.playSound(null, target.blockPosition(), SoundEvents.COPPER_BULB_TURN_ON, SoundSource.PLAYERS, 0.8F, 1.8F);
    }

    /** Charges run out: strip the charge (and its shine) once it's spent. */
    public static void tickPlayer(ServerPlayer player) {
        if (player.tickCount % 20 != 0) {
            return;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.has(ModDataComponents.CHARGED_UNTIL.get()) && !isCharged(stack, player.level())) {
                stack.remove(ModDataComponents.CHARGED_UNTIL.get());
                stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            }
        }
    }

    private ChargedTools() {}
}
