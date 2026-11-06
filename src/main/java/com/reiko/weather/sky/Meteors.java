package com.reiko.weather.sky;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.entity.MeteorEntity;
import com.reiko.weather.registry.ModEntities;
import com.reiko.weather.registry.ModSounds;
import com.reiko.weather.weather.LocalWeather;
import com.reiko.weather.weather.SkyPhenomena;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** On meteor shower nights, now and then a meteor makes it all the way down and leaves a meteorite. */
public final class Meteors {
    public static void tick(ServerLevel level, ServerPlayer player, LocalWeather local) {
        if (!WeatherConfig.METEORS.get() || !local.type().isCalm() || level.random.nextInt(9000) != 0
                || !SkyPhenomena.isMeteorShowerNight(level.getDayTime()) || SkyPhenomena.sunHeight(level, 1.0F) > -0.2F) {
            return;
        }
        float angle = level.random.nextFloat() * Mth.TWO_PI;
        double distance = 32 + level.random.nextDouble() * 48;
        spawn(level, new Vec3(player.getX() + Mth.cos(angle) * distance, player.getY(), player.getZ() + Mth.sin(angle) * distance));
    }

    /**
     * Sends a meteor down toward a spot on the ground. It starts high up but only a short way off
     * sideways, so it stays inside the area where entities are ticking (simulation distance).
     */
    public static MeteorEntity spawn(ServerLevel level, Vec3 target) {
        float heading = level.random.nextFloat() * Mth.TWO_PI;
        double run = 40 + level.random.nextDouble() * 30;
        double top = Math.min(level.getMaxBuildHeight() + 60, target.y + 140);
        Vec3 start = new Vec3(target.x - Mth.cos(heading) * run, top, target.z - Mth.sin(heading) * run);
        Vec3 velocity = target.subtract(start).normalize().scale(2.6);
        MeteorEntity meteor = new MeteorEntity(ModEntities.METEOR.get(), level);
        meteor.moveTo(start.x, start.y, start.z, 0.0F, 0.0F);
        meteor.setDeltaMovement(velocity);
        level.addFreshEntity(meteor);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(target) < 256 * 256) {
                p.displayClientMessage(Component.translatable("message.reikos_weather.meteor").withStyle(s -> s.withColor(0xFFB347)), true);
                p.playNotifySound(ModSounds.METEOR.get(), SoundSource.WEATHER, 1.0F, 0.9F + level.random.nextFloat() * 0.2F);
            }
        }
        return meteor;
    }

    private Meteors() {}
}
