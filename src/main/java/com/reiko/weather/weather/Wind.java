package com.reiko.weather.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Shared wind maths used by both the server (mobs, items, projectiles) and the client (the local player). */
public final class Wind {
    /** Below this strength wind is purely cosmetic. */
    public static final float PUSH_THRESHOLD = 0.35F;

    /** Unit vector the wind is blowing toward. */
    public static Vec3 direction(float angle) {
        return new Vec3(Mth.cos(angle), 0.0, Mth.sin(angle));
    }

    /** Slowly varying gust multiplier (0.55 - 1.15) so the wind feels alive. */
    public static float gust(long gameTime, float partialTick) {
        float t = gameTime + partialTick;
        float slow = Mth.sin(t * 0.013F) * 0.5F + 0.5F;
        float fast = Mth.sin(t * 0.071F + 1.7F) * Mth.sin(t * 0.029F + 0.4F);
        return 0.55F + slow * 0.45F + fast * 0.15F;
    }

    /** True when the position is open to the sky (not under a roof or tree canopy). */
    public static boolean isExposed(Level level, BlockPos pos) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) <= pos.getY();
    }

    /** Applies one tick of wind force to an entity. Strength is 0-1. */
    public static void push(Entity entity, float angle, float strength, long gameTime) {
        if (strength <= PUSH_THRESHOLD || entity.isInWater() || entity.isPassenger() || entity.noPhysics) {
            return;
        }
        float effective = (strength - PUSH_THRESHOLD) / (1.0F - PUSH_THRESHOLD);
        double force = effective * effective * gust(gameTime + entity.getId() * 7L, 0.0F);
        if (entity instanceof Player player) {
            if (player.getAbilities().flying) {
                return;
            }
            force *= player.isFallFlying() ? 0.06 : 0.035;
            if (player.isShiftKeyDown()) {
                force *= 0.4;
            }
        } else if (entity instanceof ItemEntity) {
            force *= 0.03;
        } else if (entity instanceof Projectile) {
            force *= 0.012;
        } else if (entity instanceof LivingEntity) {
            force *= 0.02;
        } else {
            return;
        }
        Vec3 dir = direction(angle);
        entity.setDeltaMovement(entity.getDeltaMovement().add(dir.x * force, 0.0, dir.z * force));
    }

    /** Compass name of the direction the wind blows FROM (meteorological convention). */
    public static String compassFrom(float angle) {
        Vec3 dir = direction(angle);
        // bearing of the source: opposite to where it blows, measured clockwise from north (-Z)
        double bearing = Math.toDegrees(Math.atan2(-dir.x, dir.z));
        bearing = (bearing + 360.0) % 360.0;
        String[] names = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        return names[(int) Math.round(bearing / 45.0) % 8];
    }

    /** A friendly km/h figure for the barometer. */
    public static int kmh(float strength) {
        return Math.round(strength * 130.0F);
    }

    private Wind() {}
}
