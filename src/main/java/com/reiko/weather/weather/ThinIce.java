package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.temperature.Temperature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Ice over water is thin unless it is properly cold (below about -8 °C) or two blocks thick. Thin ice
 * cracks under a player who sprints across it or stands around on it, and eventually gives way.
 * Crouching spreads your weight and is much safer.
 */
public final class ThinIce {
    private static final String STRAIN = "reikos_weather.ice_strain";
    private static final String CRACKS = "reikos_weather.ice_cracks";
    private static final String COLD = "reikos_weather.ice_cold";
    private static final float BREAKS_AT = 200.0F;
    /** A fixed "breaker" id per player for the crack overlay, so it never clashes with real block breaking. */
    private static final int CRACK_ID_OFFSET = 0x3A1CE000;

    public static void tick(ServerPlayer player) {
        if (!WeatherConfig.THIN_ICE.get() || player.isSpectator() || player.getAbilities().flying || player.isPassenger()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        CompoundTag data = player.getPersistentData();
        float strain = data.getFloat(STRAIN);
        BlockPos under = player.getOnPos();
        boolean onThinIce = player.onGround() && level.dimension() == Level.OVERWORLD && isThin(level, under, coldEnough(player, level, under));
        if (!onThinIce) {
            if (strain > 0) {
                strain = Math.max(0.0F, strain - 4.0F);
                data.putFloat(STRAIN, strain);
                if (strain <= 0) {
                    clearCracks(level, player, data);
                }
            }
            return;
        }
        boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > 0.0004;
        float load;
        if (player.isCrouching()) {
            load = 0.25F;
        } else if (player.isSprinting()) {
            load = 3.2F;
        } else {
            load = moving ? 0.5F : 1.5F; // standing in one spot is the worst thing you can do (besides running)
        }
        if (player.fallDistance > 1.5F) {
            load += 40.0F;
        }
        strain += load;
        data.putFloat(STRAIN, strain);
        int stage = Math.min(9, (int) (strain / BREAKS_AT * 10.0F));
        int lastStage = data.getInt(CRACKS);
        if (stage != lastStage) {
            data.putInt(CRACKS, stage);
            level.destroyBlockProgress(CRACK_ID_OFFSET + player.getId(), under, stage);
            if (stage > lastStage) {
                level.playSound(null, under, SoundEvents.GLASS_HIT, SoundSource.BLOCKS, 0.6F + stage * 0.05F, 0.5F + level.random.nextFloat() * 0.3F);
            }
        }
        if (strain >= BREAKS_AT) {
            breakThrough(level, player, under);
            clearCracks(level, player, data);
            data.putFloat(STRAIN, 0.0F);
        }
    }

    private static boolean coldEnough(ServerPlayer player, ServerLevel level, BlockPos pos) {
        CompoundTag data = player.getPersistentData();
        // the air temperature only needs checking now and then
        if (player.tickCount % 40 == 0 || !data.contains(COLD)) {
            data.putBoolean(COLD, Temperature.ambient(level, pos, WeatherManager.local(level, pos)) < -8.0F);
        }
        return data.getBoolean(COLD);
    }

    /** Natural ice with open water right under it that hasn't frozen thick. */
    public static boolean isThin(BlockGetter level, BlockPos pos, boolean deepCold) {
        return !deepCold && level.getBlockState(pos).is(Blocks.ICE) && level.getFluidState(pos.below()).is(FluidTags.WATER);
    }

    private static void breakThrough(ServerLevel level, ServerPlayer player, BlockPos center) {
        level.playSound(null, center, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 0.7F);
        breakIce(level, center);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (level.random.nextFloat() < 0.6F && isThin(level, center.relative(d), false)) {
                breakIce(level, center.relative(d));
            }
        }
        WeatherAdvancements.award(player, "thin_ice");
    }

    private static void breakIce(ServerLevel level, BlockPos pos) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()),
                pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 30, 0.4, 0.1, 0.4, 0.1);
        level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
    }

    private static void clearCracks(ServerLevel level, ServerPlayer player, CompoundTag data) {
        if (data.getInt(CRACKS) > 0 || data.contains(CRACKS)) {
            level.destroyBlockProgress(CRACK_ID_OFFSET + player.getId(), player.getOnPos(), -1);
            data.remove(CRACKS);
        }
    }

    private ThinIce() {}
}
