package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.weather.Climate;
import com.reiko.weather.weather.Convection;
import com.reiko.weather.weather.Monsoon;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.WeatherType;
import com.reiko.weather.weather.Wind;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Altocumulus castellanus, "castles in the sky": on a summer morning, little turrets sprouting from the tops of a
 * layer of mid-level cloud show that the air up there is already unstable. By afternoon the sun's heating from below
 * sets it off, and the day turns thundery. An old and reliable sign.
 */
public final class Castellanus {
    private static final float SLOT = 110.0F, REACH = 480.0F;
    private static float strength;
    private static float drift;
    private static double windX = 1.0, windZ = 0.0;

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD || !SyncedWeather.managed() || !ClientConfig.CASTELLANUS.get()) {
            strength = 0.0F;
            return;
        }
        long dayTime = level.getDayTime(), time = Math.floorMod(dayTime, 24000L);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        boolean tropical = Monsoon.isTropical(level.getBiome(BlockPos.containing(cam)));
        Climate climate = ClientWeather.climate();
        Season season = SyncedWeather.season();
        WeatherType type = SyncedWeather.type();
        boolean fit = (season == Season.SUMMER || tropical) && climate != Climate.COLD && (climate != Climate.ARID || tropical)
                && type != null && (type.isCalm() || type == WeatherType.HEATWAVE) && Convection.thunderyDay(Math.floorDiv(dayTime, 24000L), tropical);
        // through the morning, until the afternoon's storms take over
        float morning = Mth.clamp((time - 600.0F) / 1200.0F, 0.0F, 1.0F) * Mth.clamp((6400.0F - time) / 1200.0F, 0.0F, 1.0F);
        strength = Mth.lerp(0.01F, strength, fit ? morning * (1.0F - ClientWeather.overcast()) : 0.0F);
        Vec3 w = Wind.direction(SyncedWeather.windAngle());
        windX = w.x;
        windZ = w.z;
        drift += 0.1F + 0.2F * ClientWeather.wind();
        if (drift > 1.0E6F) drift = 0.0F;
    }

    /** Adds this frame's turreted clouds to the puff renderer. */
    public static void collect(Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || strength < 0.02F || !DriftingClouds.cloudsWanted(mc)) return;
        Vec3 cam = camera.getPosition();
        float maxDistance = mc.gameRenderer.getDepthFar() * 0.9F;
        float[] tint = DriftingClouds.sunlit(level, partialTick, level.getSkyDarken(partialTick));
        float base = level.getSeaLevel() + 112.0F;
        double ca = cam.x * windX + cam.z * windZ - drift, cc = -cam.x * windZ + cam.z * windX;
        int i0 = Mth.floor((ca - REACH) / SLOT), i1 = Mth.floor((ca + REACH) / SLOT);
        int j0 = Mth.floor((cc - REACH) / SLOT), j1 = Mth.floor((cc + REACH) / SLOT);
        for (int i = i0; i <= i1; i++) {
            for (int j = j0; j <= j1; j++) {
                int seed = i * 52711 + j * 91199;
                if (PuffRenderer.hash(seed, 81) > 0.55F) continue;
                double along = (i + PuffRenderer.hash(seed, 82)) * SLOT + drift, across = (j + PuffRenderer.hash(seed, 83)) * SLOT;
                double x = along * windX - across * windZ, z = along * windZ + across * windX;
                double dist = Math.sqrt((x - cam.x) * (x - cam.x) + (z - cam.z) * (z - cam.z));
                if (dist > REACH) continue;
                float alpha = 0.8F * strength * (float) Mth.clamp((REACH - dist) / 120.0, 0.0, 1.0);
                castle(seed, x, base + 6.0F * PuffRenderer.hash(seed, 84), z, tint, alpha, cam, maxDistance);
            }
        }
    }

    /** One cloud: a long, flat, greyish base along the wind, with turrets rising from its top. */
    private static void castle(int seed, double x, float y, double z, float[] tint, float alpha, Vec3 cam, float maxDistance) {
        float length = 44.0F + 40.0F * PuffRenderer.hash(seed, 85);
        int n = 5 + (int) (length / 9.0F);
        // the flat, slightly greyer base of the layer, strung out along the wind
        for (int k = 0; k < n; k++) {
            float u = (k / (float) (n - 1) - 0.5F) * length, v = (PuffRenderer.hash(seed + k, 90) - 0.5F) * 6.0F;
            float s = 8.0F + 3.0F * PuffRenderer.hash(seed + k, 86);
            PuffRenderer.add(x + windX * u - windZ * v, y, z + windZ * u + windX * v, s, tint[0] * 0.84F, tint[1] * 0.84F, tint[2] * 0.87F,
                    alpha, false, cam, maxDistance);
        }
        // and a row of turrets standing up from its top, narrow and taller than they are wide, like battlements
        int towers = 3 + (int) (PuffRenderer.hash(seed, 87) * 4.0F);
        for (int t = 0; t < towers; t++) {
            float u = ((t + 0.5F) / towers - 0.5F) * length * 0.85F + (PuffRenderer.hash(seed + t, 88) - 0.5F) * 6.0F;
            int stack = 3 + (int) (PuffRenderer.hash(seed + t, 89) * 3.0F);
            for (int k = 0; k < stack; k++) {
                float s = 7.5F - k * 0.8F;
                PuffRenderer.add(x + windX * u, y + 6.0F + k * 4.0F, z + windZ * u, s, tint[0], tint[1], tint[2], alpha * (1.0F - 0.06F * k), false, cam, maxDistance);
            }
        }
    }

    static float strength() {
        return strength;
    }

    public static void reset() {
        strength = 0.0F;
    }

    private Castellanus() {}
}
