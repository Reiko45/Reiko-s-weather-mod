package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.weather.Climate;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.StormCell;
import com.reiko.weather.weather.WeatherType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The rarer sights in the sky: red sprites flickering high over distant thunderstorms at night, noctilucent
 * clouds glowing low in the north on summer nights, moonbows after night rain under a bright moon, and white
 * fogbows when a low sun shines through thin fog.
 */
public final class SkySights {
    private static final RandomSource RANDOM = RandomSource.create();

    /** A sprite: a brief red flash, high above a storm, with tendrils hanging down from it. */
    private record Sprite(double x, double y, double z, float size, int seed, int life, long born) {}

    private static final List<Sprite> SPRITES = new ArrayList<>();
    private static float noctilucent, moonbow, fogbow;

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        boolean on = level != null && level.dimension() == Level.OVERWORLD && SyncedWeather.managed();
        if (!on) {
            SPRITES.clear();
            noctilucent = moonbow = fogbow = 0.0F;
            return;
        }
        long now = level.getGameTime();
        SPRITES.removeIf(s -> now - s.born() > s.life());
        float sun = com.reiko.weather.weather.SkyPhenomena.sunHeight(level, 1.0F);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        boolean night = sun < -0.15F;

        // sprites: only seen from far off, over the top of a storm, on a dark night
        if (night && ClientConfig.RED_SPRITES.get() && !Accessibility.reduceFlashing()) {
            for (StormCell cell : SyncedWeather.cells()) {
                if (!cell.type.vanillaThunder || cell.strength() < 0.6F) continue;
                double edge = cell.distanceTo(cam.x, cam.z) - cell.radius;
                if (edge < 250 || edge > 1800 || RANDOM.nextInt(cell.type == WeatherType.SUPERCELL ? 260 : 420) != 0) continue;
                double x = cell.x + (RANDOM.nextDouble() - 0.5) * cell.radius * 0.6, z = cell.z + (RANDOM.nextDouble() - 0.5) * cell.radius * 0.6;
                double y = level.getSeaLevel() + 80.0 + 230.0 + RANDOM.nextDouble() * 60.0;
                SPRITES.add(new Sprite(x, y, z, 40.0F + RANDOM.nextFloat() * 30.0F, RANDOM.nextInt(), 3 + RANDOM.nextInt(4), now));
            }
        }

        float overcast = ClientWeather.overcast(), rain = ClientWeather.rain(), fog = ClientWeather.fogDensity();
        Climate climate = ClientWeather.climate();
        // noctilucent clouds: deep summer twilight, some nights only, away from the tropics
        long day = Math.floorDiv(level.getDayTime() + 6000L, 24000L);
        boolean summer = SyncedWeather.season() == null || SyncedWeather.season() == Season.SUMMER;
        boolean nlcNight = summer && climate != Climate.ARID && Math.floorMod(Mth.murmurHash3Mixer((int) day * 0x2545F491), 5) < 2;
        float twilight = Mth.clamp((sun + 0.38F) * 6.0F, 0.0F, 1.0F) * Mth.clamp((-0.08F - sun) * 10.0F, 0.0F, 1.0F);
        float nlcTarget = ClientConfig.NOCTILUCENT_CLOUDS.get() && nlcNight ? twilight * (1.0F - overcast) * (1.0F - rain) : 0.0F;
        noctilucent = approach(noctilucent, nlcTarget, 0.01F);

        // moonbows: after rain at night, with a bright, full-ish moon low in the sky
        int phase = level.getMoonPhase();
        float moonBright = phase == 0 ? 1.0F : phase == 1 || phase == 7 ? 0.6F : 0.0F;
        float moonbowTarget = ClientConfig.MOONBOWS.get() && ClientConfig.RAINBOWS.get() && night
                ? ClientWeather.rainbow() * moonBright : 0.0F;
        moonbow = approach(moonbow, moonbowTarget, 0.004F);

        // fogbows: a low sun shining through thin fog or mist
        boolean foggy = ClientWeather.type() == WeatherType.FOG || ClientWeather.type() == WeatherType.SEA_FOG || ClientWeather.mist() > 0.3F;
        float thin = Mth.clamp(1.0F - Math.abs(fog - 0.5F) / 0.5F, 0.0F, 1.0F) * 1.5F;
        float fogbowTarget = ClientConfig.FOGBOWS.get() && foggy && sun > 0.0F && sun < 0.6F && rain < 0.1F ? thin * (1.0F - overcast * 0.6F) : 0.0F;
        fogbow = approach(fogbow, Math.min(1.0F, fogbowTarget), 0.004F);
    }

    public static float noctilucent() {
        return noctilucent;
    }

    public static float moonbow() {
        return moonbow;
    }

    public static float fogbow() {
        return fogbow;
    }

    /** Adds the sprites flashing this frame to the puff renderer. */
    public static void collectSprites(Camera camera, float partialTick) {
        if (SPRITES.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = camera.getPosition();
        float maxDistance = mc.gameRenderer.getDepthFar() * 0.9F;
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        for (Sprite s : SPRITES) {
            float age = (now - s.born() + partialTick) / Math.max(1, s.life());
            float flash = Mth.clamp(1.0F - age, 0.0F, 1.0F) * (age < 0.2F ? age * 5.0F : 1.0F);
            if (flash <= 0.01F) continue;
            // the bright red head
            PuffRenderer.add(s.x(), s.y(), s.z(), s.size() * 0.55F, 1.0F, 0.25F, 0.3F, 0.7F * flash, true, cam, maxDistance);
            PuffRenderer.add(s.x(), s.y() + s.size() * 0.2, s.z(), s.size() * 0.9F, 0.9F, 0.15F, 0.25F, 0.35F * flash, true, cam, maxDistance);
            // tendrils trailing down, turning blue-violet toward the bottom
            int tendrils = 4 + Math.floorMod(s.seed(), 3);
            for (int t = 0; t < tendrils; t++) {
                float spread = (PuffRenderer.hash(t, s.seed()) - 0.5F) * s.size() * 0.9F;
                float spread2 = (PuffRenderer.hash(t, s.seed() + 7) - 0.5F) * s.size() * 0.9F;
                for (int k = 1; k <= 5; k++) {
                    float f = k / 5.0F;
                    double y = s.y() - f * s.size() * 1.8F;
                    PuffRenderer.add(s.x() + spread * f, y, s.z() + spread2 * f, s.size() * (0.22F - 0.025F * k),
                            Mth.lerp(f, 0.95F, 0.45F), Mth.lerp(f, 0.2F, 0.2F), Mth.lerp(f, 0.3F, 0.9F), 0.5F * flash * (1.0F - f * 0.6F), true, cam, maxDistance);
                }
            }
        }
    }

    private static float approach(float current, float target, float step) {
        return current < target ? Math.min(target, current + step) : Math.max(target, current - step);
    }

    private SkySights() {}
}
