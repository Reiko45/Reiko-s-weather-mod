package com.reiko.weather.item;

import com.reiko.weather.mixin.ServerGamePacketListenerImplAccessor;
import com.reiko.weather.weather.WeatherAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** The physics of paragliding. Movement runs on the client (players move themselves); the server keeps score. */
public final class Paragliding {
    private static final double CRUISE = 0.34;
    private static final double SINK = -0.055;
    private static final String LOW_POINT = "reikos_weather.glide_low";

    /** The glider's own velocity, kept apart from the player's so vanilla gravity and drag don't drag it down. */
    private static Vec3 glide;

    /** Called every client tick for the local player while gliding ({@code gliding} false resets). */
    public static void clientTick(Player player, boolean gliding, Vec3 windDirection, float windStrength, float thermal) {
        if (!gliding) {
            glide = null;
            return;
        }
        if (glide == null) {
            Vec3 m = player.getDeltaMovement();
            glide = new Vec3(m.x, Math.max(m.y, -0.6), m.z);
        }
        Vec3 look = player.getLookAngle();
        Vec3 heading = new Vec3(look.x, 0.0, look.z);
        if (heading.lengthSqr() < 1.0E-4) {
            heading = Vec3.directionFromRotation(0.0F, player.getYRot());
        }
        heading = heading.normalize();
        // looking down dives faster, looking up flares and slows you
        double pitch = Mth.clamp(look.y, -0.8, 0.5);
        double speed = CRUISE * (1.0 - pitch * 0.6);
        Vec3 wanted = heading.scale(speed).add(windDirection.scale(0.12 * windStrength));
        Vec3 horizontal = new Vec3(glide.x, 0.0, glide.z).lerp(wanted, 0.1);
        double targetY = Mth.clamp(SINK + pitch * 0.06 + 0.2 * thermal, -0.2, 0.18);
        double y = Mth.lerp(0.12, glide.y, targetY);
        glide = new Vec3(horizontal.x, y, horizontal.z);
        player.setDeltaMovement(glide);
        player.resetFallDistance();
    }

    /**
     * How strongly warm air is rising under a player: hot spots like lava, fire and lit campfires; sun-baked
     * sand on hot days; and wind blowing up mountain slopes. 0 to 1.
     */
    public static float thermalAt(Level level, Vec3 pos, float airTemperature, boolean sunny, float ridgeLift) {
        BlockPos.MutableBlockPos p = BlockPos.containing(pos).mutable();
        for (int depth = 0; depth < 64; depth++) {
            p.move(0, -1, 0);
            if (p.getY() < level.getMinBuildHeight()) {
                break;
            }
            BlockState state = level.getBlockState(p);
            if (state.isAir()) {
                continue;
            }
            float fade = 1.0F - depth / 64.0F;
            if (state.getBlock() instanceof com.reiko.weather.block.GeyserBlock) {
                // an erupting geyser is the strongest updraft there is; even a resting one steams a little
                return Math.max(ridgeLift, state.getValue(com.reiko.weather.block.GeyserBlock.ERUPTING) ? 1.0F : 0.3F * fade);
            }
            if (state.is(com.reiko.weather.registry.ModBlocks.VOLCANIC_VENT.get())) {
                return Math.max(ridgeLift, 0.6F * fade);
            }
            if (state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK) || state.is(BlockTags.FIRE)
                    || state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
                return Math.max(ridgeLift, fade);
            }
            float ground = 0.0F;
            if (sunny && airTemperature > 22.0F && (state.is(BlockTags.SAND) || state.is(BlockTags.TERRACOTTA)
                    || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.STONE))) {
                ground = Mth.clamp((airTemperature - 22.0F) / 12.0F, 0.0F, 1.0F) * 0.7F;
            }
            return Math.max(ridgeLift, ground * fade);
        }
        return ridgeLift;
    }

    /** Server side: no fall damage, no "flying is not enabled" kick, wear on the glider, and the thermal advancement. */
    public static void serverTick(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!ParagliderItem.gliding(player)) {
            data.remove(LOW_POINT);
            return;
        }
        player.resetFallDistance();
        ((ServerGamePacketListenerImplAccessor) player.connection).reikos_weather$setAboveGroundTickCount(0);
        double low = data.contains(LOW_POINT) ? Math.min(data.getDouble(LOW_POINT), player.getY()) : player.getY();
        data.putDouble(LOW_POINT, low);
        if (player.getY() - low > 15.0) {
            WeatherAdvancements.award(player, "riding_thermals");
        }
        if (player.tickCount % 40 == 0 && !player.getAbilities().instabuild) {
            EquipmentSlot slot = player.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            player.getUseItem().hurtAndBreak(1, player, slot);
        }
    }

    private Paragliding() {}
}
