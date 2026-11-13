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
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * On meteor shower nights, now and then a meteor makes it all the way down and leaves a meteorite. Like real ones
 * they come in all sizes, and the smaller the commoner: most are pebbles that leave a lump of rock in a scorch mark,
 * now and then a bright fireball blasts a crater, and very rarely a huge one lights up the whole sky and shakes the
 * ground for a long way around.
 */
public final class Meteors {
    /** Size classes; {@code min}–{@code max} is the meteor's size (1 = the classic meteor), {@code odds} how often it falls. */
    public enum Size {
        SMALL("small", 0.45F, 0.7F, 0.55F),
        MEDIUM("medium", 0.85F, 1.15F, 0.30F),
        LARGE("large", 1.5F, 1.9F, 0.12F),
        HUGE("huge", 2.4F, 3.0F, 0.03F);

        public final String id;
        public final float min, max, odds;

        Size(String id, float min, float max, float odds) {
            this.id = id;
            this.min = min;
            this.max = max;
            this.odds = odds;
        }

        public static Size roll(RandomSource random) {
            float r = random.nextFloat();
            for (Size size : values()) {
                if ((r -= size.odds) < 0) return size;
            }
            return SMALL;
        }

        /** The class a size falls in. */
        public static Size of(float size) {
            return size < 0.8F ? SMALL : size < 1.35F ? MEDIUM : size < 2.2F ? LARGE : HUGE;
        }

        public static Size byId(String id) {
            for (Size size : values()) {
                if (size.id.equals(id)) return size;
            }
            return null;
        }

        public float pick(RandomSource random) {
            return min + random.nextFloat() * (max - min);
        }
    }

    public static void tick(ServerLevel level, ServerPlayer player, LocalWeather local) {
        if (!WeatherConfig.METEORS.get() || !local.type().isCalm() || level.random.nextInt(9000) != 0
                || !SkyPhenomena.isMeteorShowerNight(level.getDayTime()) || SkyPhenomena.sunHeight(level, 1.0F) > -0.2F) {
            return;
        }
        Size size = Size.roll(level.random);
        float angle = level.random.nextFloat() * Mth.TWO_PI;
        // the big ones come down further off (still close enough to stay in ticking chunks on the way)
        double distance = size == Size.HUGE ? 56 + level.random.nextDouble() * 32 : 32 + level.random.nextDouble() * 48;
        spawn(level, new Vec3(player.getX() + Mth.cos(angle) * distance, player.getY(), player.getZ() + Mth.sin(angle) * distance), size);
    }

    public static MeteorEntity spawn(ServerLevel level, Vec3 target) {
        return spawn(level, target, Size.roll(level.random));
    }

    /**
     * Sends a meteor down toward a spot on the ground. It starts high up but only a short way off
     * sideways, so it stays inside the area where entities are ticking (simulation distance).
     */
    public static MeteorEntity spawn(ServerLevel level, Vec3 target, Size size) {
        float heading = level.random.nextFloat() * Mth.TWO_PI;
        double run = 40 + level.random.nextDouble() * 30;
        double top = Math.min(level.getMaxBuildHeight() + 60, target.y + 140);
        Vec3 start = new Vec3(target.x - Mth.cos(heading) * run, top, target.z - Mth.sin(heading) * run);
        Vec3 velocity = target.subtract(start).normalize().scale(2.6);
        MeteorEntity meteor = new MeteorEntity(ModEntities.METEOR.get(), level);
        meteor.setSize(size.pick(level.random));
        meteor.moveTo(start.x, start.y, start.z, 0.0F, 0.0F);
        meteor.setDeltaMovement(velocity);
        level.addFreshEntity(meteor);
        double heard = size == Size.HUGE ? 512 : size == Size.LARGE ? 320 : 256;
        float volume = switch (size) { case SMALL -> 0.6F; case MEDIUM -> 1.0F; case LARGE -> 1.6F; case HUGE -> 3.0F; };
        float pitch = switch (size) { case SMALL -> 1.35F; case MEDIUM -> 1.0F; case LARGE -> 0.8F; case HUGE -> 0.6F; };
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(target) < heard * heard) {
                String key = size == Size.MEDIUM ? "message.reikos_weather.meteor" : "message.reikos_weather.meteor." + size.id;
                p.displayClientMessage(Component.translatable(key).withStyle(s -> s.withColor(size == Size.HUGE ? 0xFF7A2E : 0xFFB347)
                        .withBold(size == Size.HUGE)), true);
                p.playNotifySound(ModSounds.METEOR.get(), SoundSource.WEATHER, volume, pitch * (0.9F + level.random.nextFloat() * 0.2F));
            }
        }
        return meteor;
    }

    private Meteors() {}
}
