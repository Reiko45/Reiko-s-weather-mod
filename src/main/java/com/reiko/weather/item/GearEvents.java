package com.reiko.weather.item;

import com.reiko.weather.registry.ModItems;
import com.reiko.weather.weather.WeatherAdvancements;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import com.reiko.weather.ReikosWeather;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Umbrella gliding. The client caps the fall speed (players move client-side). The server, which
 * doesn't simulate player velocity, watches the height and lowers the player's gravity attribute while
 * they drift: that is what vanilla's anti-fly check scales with, so a slow, legitimate fall isn't kicked.
 */
public final class GearEvents {
    private static final String GLIDE_START = "reikos_weather.glide_start";
    private static final String LAST_Y = "reikos_weather.last_y";
    private static final ResourceLocation UMBRELLA_GRAVITY = ReikosWeather.id("umbrella_glide");
    private static final double MAX_FALL_SPEED = -0.16;

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            serverTick(serverPlayer);
            com.reiko.weather.temperature.TemperatureEffects.tick(serverPlayer);
        } else if (player.level().isClientSide && canHoldOpen(player) && player.getDeltaMovement().y < -0.08) {
            Vec3 motion = player.getDeltaMovement();
            if (motion.y < MAX_FALL_SPEED) {
                player.setDeltaMovement(motion.x, MAX_FALL_SPEED, motion.z);
            }
            player.resetFallDistance();
        }
    }

    private static void serverTick(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        // movement packets arrive between ticks, so compare with our own record of last tick's height
        double lastY = data.contains(LAST_Y) ? data.getDouble(LAST_Y) : player.getY();
        data.putDouble(LAST_Y, player.getY());
        boolean falling = player.getY() < lastY - 0.01;
        boolean gliding = data.contains(GLIDE_START);
        if (!canHoldOpen(player) || !falling && !gliding || player.onGround()) {
            data.remove(GLIDE_START);
            AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
            if (gravity != null) {
                gravity.removeModifier(UMBRELLA_GRAVITY);
            }
            return;
        }
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        if (gravity != null && !gravity.hasModifier(UMBRELLA_GRAVITY)) {
            gravity.addTransientModifier(new AttributeModifier(UMBRELLA_GRAVITY, -0.9375, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        player.resetFallDistance();
        if (!data.contains(GLIDE_START)) {
            data.putDouble(GLIDE_START, player.getY());
        } else if (data.getDouble(GLIDE_START) - player.getY() >= 20.0) {
            WeatherAdvancements.award(player, "mary_poppins");
        }
    }

    private static boolean canHoldOpen(Player player) {
        return (player.getMainHandItem().is(ModItems.UMBRELLA.get()) || player.getOffhandItem().is(ModItems.UMBRELLA.get()))
                && !player.onGround() && !player.isFallFlying() && !player.getAbilities().flying
                && !player.isShiftKeyDown() && !player.isInWater() && !player.onClimbable();
    }

    private GearEvents() {}
}
