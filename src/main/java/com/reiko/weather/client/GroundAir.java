package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.registry.ModParticles;
import com.reiko.weather.weather.Precip;
import com.reiko.weather.weather.SkyPhenomena;
import com.reiko.weather.weather.Wind;
import com.reiko.weather.weather.WeatherType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The air just over the ground. A ground blizzard: a strong wind over loose, dry snow lifts it into a blinding,
 * hissing river a few feet deep, even with the sun shining overhead (stand on a rock and your head's out of it).
 * Ground steam: when the sun comes out after a summer shower, wisps of steam rise off the warm roads and fields.
 */
public final class GroundAir {
    private static final RandomSource RANDOM = RandomSource.create();
    private static float snowAround, drift, steam;
    private static int steamTicks;
    private static float cameraAbove = 10.0F;

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) return;
        boolean managed = SyncedWeather.managed() && level.dimension() == Level.OVERWORLD;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(cam.x), Mth.floor(cam.z));
        cameraAbove = (float) (cam.y - ground);

        // ground blizzard: how much loose snow is lying round about, and whether the wind is strong enough to lift it
        float target = 0.0F;
        if (managed && WeatherConfig.SPEC.isLoaded() && WeatherConfig.GROUND_BLIZZARDS.get()) {
            int snowy = 0;
            for (int i = 0; i < 4; i++) {
                int x = Mth.floor(cam.x) + RANDOM.nextInt(25) - 12, z = Mth.floor(cam.z) + RANDOM.nextInt(25) - 12;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                // a thin snow layer has nothing to stand on, so it sits just above the motion-blocking surface
                BlockState top = level.getBlockState(new BlockPos(x, y, z));
                BlockState s = top.is(Blocks.SNOW) ? top : level.getBlockState(new BlockPos(x, y - 1, z));
                if (s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW)) snowy++;
            }
            snowAround = Mth.lerp(0.05F, snowAround, snowy / 4.0F);
            float falling = ClientWeather.precipRate(Precip.SNOW) + ClientWeather.precipRate(Precip.BLIZZARD);
            float wind = ClientWeather.wind();
            target = Mth.clamp((wind - 0.45F) / 0.3F, 0.0F, 1.0F) * Mth.clamp((snowAround - 0.3F) * 2.0F, 0.0F, 1.0F) * (1.0F - 0.6F * Mth.clamp(falling, 0.0F, 1.0F))
                    * (ClientWeather.airTemperature() < 1.0F ? 1.0F : 0.0F);
        }
        drift = Mth.lerp(0.03F, drift, target);

        // ground steam: the first few minutes of sunshine after summer rain, while the ground's still wet and warm
        if (managed && ClientConfig.GROUND_STEAM.get()) {
            WeatherType type = ClientWeather.type();
            boolean showering = ClientWeather.rain() > 0.3F;
            float sun = SkyPhenomena.sunHeight(level, 1.0F);
            if (showering) steamTicks = 0;
            else if (ClientWeather.wetness() > 0.3F && sun > 0.25F && ClientWeather.airTemperature() > 15.0F && steamTicks == 0 && ClientWeather.overcast() < 0.5F) {
                steamTicks = 3600;
            }
            if (steamTicks > 0) steamTicks--;
            float wanted = steamTicks > 0 && sun > 0.1F && type != WeatherType.FOG ? Math.min(1.0F, steamTicks / 1200.0F) : 0.0F;
            steam = Mth.lerp(0.02F, steam, wanted);
        } else {
            steam = Mth.lerp(0.05F, steam, 0.0F);
        }
        spawn(level, cam);
    }

    private static void spawn(ClientLevel level, Vec3 cam) {
        float density = ClientConfig.PARTICLE_DENSITY.get().floatValue();
        if (drift > 0.02F) {
            Vec3 dir = Wind.direction(ClientWeather.windAngle());
            float speed = 0.5F + ClientWeather.wind() * 0.9F;
            int count = Math.round(drift * 40 * density);
            for (int i = 0; i < count; i++) {
                double x = cam.x + (RANDOM.nextDouble() * 2 - 1) * 16 - dir.x * 8, z = cam.z + (RANDOM.nextDouble() * 2 - 1) * 16 - dir.z * 8;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
                if (Math.abs(top - cam.y) > 10) continue;
                // hugging the ground: thickest at your feet, thinning out by head height
                double y = top + RANDOM.nextDouble() * RANDOM.nextDouble() * 2.6;
                level.addParticle(ModParticles.BLIZZARD.get(), x, y, z, dir.x * speed, 0.01 + RANDOM.nextDouble() * 0.04, dir.z * speed);
            }
        }
        if (steam > 0.02F) {
            int count = Math.round(steam * 5 * density);
            for (int i = 0; i < count; i++) {
                int x = Mth.floor(cam.x) + RANDOM.nextInt(33) - 16, z = Mth.floor(cam.z) + RANDOM.nextInt(33) - 16;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockPos ground = new BlockPos(x, top - 1, z);
                BlockState s = level.getBlockState(ground);
                // dark, sun-warmed surfaces steam the most: roads, rock, bare earth
                boolean warm = s.is(Blocks.DIRT_PATH) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.GRAVEL)
                        || s.is(BlockTags.DIRT) && RANDOM.nextInt(3) == 0;
                if (!warm || !level.canSeeSky(ground.above()) || Math.abs(top - cam.y) > 12) continue;
                level.addParticle(ModParticles.STEAM_FOG.get(), x + RANDOM.nextDouble(), top + 0.05, z + RANDOM.nextDouble(), 0.0, 0.025, 0.0);
            }
        }
    }

    /** Fog from a ground blizzard: thick near the ground, gone once you're a few blocks up. */
    public static float fog() {
        if (drift <= 0.01F) return 0.0F;
        return drift * 0.85F * Mth.clamp((5.0F - cameraAbove) / 3.5F, 0.0F, 1.0F);
    }

    public static void reset() {
        snowAround = drift = steam = 0.0F;
        steamTicks = 0;
    }

    private GroundAir() {}
}
