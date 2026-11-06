package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.network.OtherworldPayload;
import com.reiko.weather.otherworld.OtherworldType;
import com.reiko.weather.otherworld.OtherworldWeather;
import com.reiko.weather.registry.ModParticles;
import com.reiko.weather.weather.Wind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Nether and End weather on the client: particles, fog, wind and the void storm's gusts. */
public final class OtherworldClient {
    private static final RandomSource RANDOM = RandomSource.create();
    private static OtherworldType shown = OtherworldType.CALM;
    private static float intensity;

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            shown = OtherworldType.CALM;
            intensity = 0.0F;
            return;
        }
        boolean other = level.dimension() == Level.NETHER || level.dimension() == Level.END;
        OtherworldType target = other ? OtherworldType.byOrdinal(OtherworldPayload.clientWeather) : OtherworldType.CALM;
        if (!target.validIn(level)) {
            target = OtherworldType.CALM;
        }
        if (target != shown) {
            intensity = Math.max(0.0F, intensity - 0.01F);
            if (intensity <= 0.0F) {
                shown = target;
            }
        } else {
            intensity = Math.min(shown == OtherworldType.CALM ? 0.0F : 1.0F, intensity + 0.005F);
        }
        if (shown == OtherworldType.CALM || intensity <= 0.01F || mc.isPaused()) {
            return;
        }
        float density = ClientConfig.PARTICLE_DENSITY.get().floatValue() * intensity;
        ParticleStatus status = mc.options.particles().get();
        if (status == ParticleStatus.DECREASED) density *= 0.6F;
        else if (status == ParticleStatus.MINIMAL) density *= 0.25F;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        long time = level.getGameTime();
        switch (shown) {
            case ASH_STORM -> ashStorm(level, cam, density, time);
            case EMBER_FALL -> embers(level, cam, density * 6.0F, false);
            case FIRE_RAIN -> fireRain(level, cam, density);
            case VOID_STORM -> voidStorm(level, player, cam, density, time);
            default -> {}
        }
    }

    /** The Nether's ash storms swirl in a slowly turning direction. */
    public static float netherWindAngle(long time) {
        return Mth.sin(time * 0.0007F) * Mth.PI + time * 0.0002F;
    }

    private static void ashStorm(ClientLevel level, Vec3 cam, float density, long time) {
        Vec3 dir = Wind.direction(netherWindAngle(time));
        float gust = Wind.gust(time, 0.0F);
        int count = count(density * 40.0F);
        for (int i = 0; i < count; i++) {
            double x = cam.x + (RANDOM.nextDouble() * 2 - 1) * 16 - dir.x * 6;
            double y = cam.y + (RANDOM.nextDouble() * 2 - 1) * 8;
            double z = cam.z + (RANDOM.nextDouble() * 2 - 1) * 16 - dir.z * 6;
            if (!level.getBlockState(BlockPos.containing(x, y, z)).isAir()) continue;
            double speed = 0.25 * gust;
            level.addParticle(RANDOM.nextInt(3) == 0 ? ParticleTypes.WHITE_ASH : ParticleTypes.ASH,
                    x, y, z, dir.x * speed, -0.02, dir.z * speed);
        }
        if (RANDOM.nextFloat() < density * 0.5F) {
            double x = cam.x + (RANDOM.nextDouble() * 2 - 1) * 12, z = cam.z + (RANDOM.nextDouble() * 2 - 1) * 12;
            level.addParticle(ParticleTypes.LARGE_SMOKE, x, cam.y + RANDOM.nextDouble() * 4 - 1, z, dir.x * 0.1, 0.0, dir.z * 0.1);
        }
    }

    private static void embers(ClientLevel level, Vec3 cam, float wanted, boolean fromAbove) {
        int count = count(wanted);
        for (int i = 0; i < count; i++) {
            double x = cam.x + (RANDOM.nextDouble() * 2 - 1) * 14;
            double y = cam.y + (fromAbove ? 4 + RANDOM.nextDouble() * 8 : (RANDOM.nextDouble() * 2 - 1) * 8 + 3);
            double z = cam.z + (RANDOM.nextDouble() * 2 - 1) * 14;
            if (level.getBlockState(BlockPos.containing(x, y, z)).isAir()) {
                level.addParticle(ModParticles.EMBER.get(), x, y, z, (RANDOM.nextDouble() - 0.5) * 0.02, -0.02, (RANDOM.nextDouble() - 0.5) * 0.02);
            }
        }
    }

    private static void fireRain(ClientLevel level, Vec3 cam, float density) {
        int count = count(density * 45.0F);
        for (int i = 0; i < count; i++) {
            double x = cam.x + (RANDOM.nextDouble() * 2 - 1) * 12;
            double z = cam.z + (RANDOM.nextDouble() * 2 - 1) * 12;
            double y = cam.y + 3 + RANDOM.nextDouble() * 9;
            BlockPos pos = BlockPos.containing(x, y, z);
            if (level.getBlockState(pos).isAir() && OtherworldWeather.openAbove(level, pos.below(4))) {
                level.addParticle(ParticleTypes.FALLING_LAVA, x, y, z, 0.0, 0.0, 0.0);
            }
        }
        embers(level, cam, density * 8.0F, true);
        if (RANDOM.nextFloat() < density * 0.8F) {
            double x = cam.x + (RANDOM.nextDouble() * 2 - 1) * 10, z = cam.z + (RANDOM.nextDouble() * 2 - 1) * 10;
            level.addParticle(ParticleTypes.LAVA, x, cam.y - 1 + RANDOM.nextDouble() * 2, z, 0.0, 0.0, 0.0);
        }
    }

    private static void voidStorm(ClientLevel level, LocalPlayer player, Vec3 cam, float density, long time) {
        float gust = OtherworldWeather.voidGust(time);
        int count = count(density * 22.0F * gust);
        for (int i = 0; i < count; i++) {
            double x = cam.x + (RANDOM.nextDouble() * 2 - 1) * 16;
            double y = cam.y + (RANDOM.nextDouble() * 2 - 1) * 8;
            double z = cam.z + (RANDOM.nextDouble() * 2 - 1) * 16;
            Vec3 out = Wind.direction(OtherworldWeather.outward(x, z));
            double speed = 0.15 + 0.35 * gust;
            level.addParticle(RANDOM.nextInt(4) == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.DRAGON_BREATH,
                    x, y, z, out.x * speed, (RANDOM.nextDouble() - 0.5) * 0.02, out.z * speed);
        }
        if (!player.isSpectator() && WeatherConfig.SPEC.isLoaded() && WeatherConfig.VOID_STORM_GUSTS.get()
                && Wind.isExposed(level, BlockPos.containing(player.getEyePosition()))) {
            Wind.push(player, OtherworldWeather.outward(player.getX(), player.getZ()), gust * intensity, time);
        }
    }

    private static int count(float wanted) {
        return (int) wanted + (RANDOM.nextFloat() < wanted - (int) wanted ? 1 : 0);
    }

    /** How loud the wind bed should be for storms in the Nether or End. */
    public static float windVolume() {
        if (shown == OtherworldType.ASH_STORM) return 0.45F * intensity;
        if (shown == OtherworldType.VOID_STORM) return 0.6F * intensity * OtherworldWeather.voidGust(Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0);
        return 0.0F;
    }

    /** Pulls fog in during ash storms, fire rain and void storms. Returns true if it changed the fog. */
    public static boolean applyFog(ViewportEvent.RenderFog event) {
        if (shown == OtherworldType.CALM || intensity <= 0.01F || !ClientConfig.FOG.get()) {
            return false;
        }
        float visibility = switch (shown) {
            case ASH_STORM -> 26.0F;
            case FIRE_RAIN -> 48.0F;
            case VOID_STORM -> 40.0F;
            default -> -1.0F;
        };
        if (visibility < 0) {
            return false;
        }
        float far = event.getFarPlaneDistance();
        float newFar = Mth.lerp(intensity, far, Math.min(far, visibility));
        if (newFar >= far - 0.5F) {
            return false;
        }
        event.setFarPlaneDistance(newFar);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), newFar * 0.1F));
        event.setCanceled(true);
        return true;
    }

    /** Tints the fog: grey ash, glowing red fire rain, violet void storms. */
    public static boolean applyFogColor(ViewportEvent.ComputeFogColor event) {
        if (shown == OtherworldType.CALM || intensity <= 0.01F) {
            return false;
        }
        int color = switch (shown) {
            case ASH_STORM -> 0x5A5552;
            case FIRE_RAIN -> 0x6E1E0A;
            case VOID_STORM -> 0x2E1440;
            case EMBER_FALL -> 0x4A1A0C;
            default -> -1;
        };
        if (color < 0) {
            return false;
        }
        float w = intensity * (shown == OtherworldType.EMBER_FALL ? 0.3F : 0.7F);
        event.setRed(Mth.lerp(w, event.getRed(), (color >> 16 & 255) / 255.0F));
        event.setGreen(Mth.lerp(w, event.getGreen(), (color >> 8 & 255) / 255.0F));
        event.setBlue(Mth.lerp(w, event.getBlue(), (color & 255) / 255.0F));
        return true;
    }

    public static OtherworldType shown() {
        return shown;
    }

    private OtherworldClient() {}
}
