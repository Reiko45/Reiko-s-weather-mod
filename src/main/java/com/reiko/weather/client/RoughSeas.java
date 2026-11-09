package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.weather.Wind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Storms make open water rough: boats get shoved and spun by the wind, the view rolls with the waves
 * (scaled by the screen-effects accessibility setting), and spray blows off whitecaps.
 */
public final class RoughSeas {
    private static final RandomSource RANDOM = RandomSource.create();
    private static float roughness, roll, prevRoll;

    public static void tick(Minecraft mc) {
        prevRoll = roll;
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || !ClientWeather.active()) {
            roughness = 0.0F;
            roll = Mth.lerp(0.1F, roll, 0.0F);
            return;
        }
        float stormy = Mth.clamp((ClientWeather.wind() - 0.45F) / 0.55F, 0.0F, 1.0F) * ClientWeather.exposure();
        // the long swell rolling in ahead of a hurricane, even under a clear sky
        if (WeatherConfig.SPEC.isLoaded() && WeatherConfig.SWELL.get()) {
            stormy = Math.max(stormy, AirAndWater.swell(player.getX(), player.getZ()) * 1.4F);
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        boolean open = openWater(level, BlockPos.containing(cam.x, level.getSeaLevel() - 1, cam.z));
        roughness = Mth.approach(roughness, open ? stormy : 0.0F, 0.01F);
        long time = level.getGameTime();

        float targetRoll = 0.0F;
        if (player.getVehicle() instanceof Boat boat && boat.isInWater() && roughness > 0.02F
                && WeatherConfig.SPEC.isLoaded() && WeatherConfig.ROUGH_SEAS.get()) {
            Vec3 wind = Wind.direction(ClientWeather.windAngle());
            float gust = Wind.gust(time, 0.0F);
            // shoved along by the wind (no vertical push: a boat's buoyancy is weak and it would founder)
            boat.setDeltaMovement(boat.getDeltaMovement().add(wind.x * 0.014 * roughness * gust, 0.0, wind.z * 0.014 * roughness * gust));
            if (RANDOM.nextFloat() < 0.025F * roughness) {
                boat.setYRot(boat.getYRot() + (RANDOM.nextFloat() - 0.5F) * 30.0F * roughness);
            }
            if (RANDOM.nextFloat() < 0.3F * roughness) {
                level.addParticle(ParticleTypes.SPLASH, boat.getX() + (RANDOM.nextDouble() - 0.5) * 1.6, boat.getY() + 0.6,
                        boat.getZ() + (RANDOM.nextDouble() - 0.5) * 1.6, wind.x * 0.2, 0.2, wind.z * 0.2);
            }
            targetRoll = (Mth.sin(time * 0.11F) * 6.0F + Mth.sin(time * 0.29F + 1.0F) * 2.0F) * roughness;
        }
        roll = Mth.lerp(0.15F, roll, targetRoll);

        if (roughness > 0.05F && ClientConfig.WHITECAPS.get()) {
            whitecaps(level, cam, time);
        }
    }

    private static void whitecaps(ClientLevel level, Vec3 cam, long time) {
        Vec3 wind = Wind.direction(ClientWeather.windAngle());
        int count = (int) (roughness * 6.0F * ClientConfig.PARTICLE_DENSITY.get().floatValue());
        for (int i = 0; i < count; i++) {
            int x = Mth.floor(cam.x) + RANDOM.nextInt(41) - 20, z = Mth.floor(cam.z) + RANDOM.nextInt(41) - 20;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos surface = new BlockPos(x, top - 1, z);
            if (!level.getFluidState(surface).is(FluidTags.WATER) || !level.canSeeSky(surface.above())) {
                continue;
            }
            double px = x + RANDOM.nextDouble(), pz = z + RANDOM.nextDouble(), py = top + 0.05;
            level.addParticle(ParticleTypes.SPLASH, px, py, pz, wind.x * 0.3, 0.15, wind.z * 0.3);
            if (RANDOM.nextFloat() < 0.35F * roughness) {
                level.addParticle(ParticleTypes.CLOUD, px, py + 0.1, pz, wind.x * 0.25, 0.02, wind.z * 0.25);
            }
        }
    }

    /** Most of the area around a point at sea level is water: a lake, a wide river or the sea. */
    private static boolean openWater(ClientLevel level, BlockPos center) {
        int wet = 0;
        for (int i = 0; i < 16; i++) {
            float angle = i * Mth.TWO_PI / 16.0F;
            int r = 6 + (i % 3) * 6;
            int x = center.getX() + Math.round(Mth.cos(angle) * r), z = center.getZ() + Math.round(Mth.sin(angle) * r);
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (level.getFluidState(new BlockPos(x, top - 1, z)).is(FluidTags.WATER)) {
                wet++;
            }
        }
        return wet >= 11;
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float r = Mth.lerp((float) event.getPartialTick(), prevRoll, roll) * Accessibility.screenEffects();
        if (Math.abs(r) > 0.01F) {
            event.setRoll(event.getRoll() + r);
        }
    }

    public static float roughness() {
        return roughness;
    }

    public static void reset() {
        roughness = roll = prevRoll = 0.0F;
    }

    private RoughSeas() {}
}
