package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.weather.StormCell;
import com.reiko.weather.weather.WeatherType;
import com.reiko.weather.weather.Wind;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Clouds you can watch from a distance: thunderstorms tower up into tall, anvil-topped clouds (growing as the
 * storm builds), and sea fog rolls in as a low white bank over the water.
 */
public final class CloudPuffs {
    public static void collect(Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD || !SyncedWeather.managed() || SyncedWeather.cells().isEmpty()) {
            return;
        }
        boolean towers = ClientConfig.TOWERING_CLOUDS.get(), banks = ClientConfig.FOG_BANKS.get();
        Vec3 cam = camera.getPosition();
        float maxDistance = mc.gameRenderer.getDepthFar() * 0.9F;
        float daylight = level.getSkyDarken(partialTick);
        float fog = 1.0F - 0.6F * ClientWeather.fogDensity();
        float time = level.getGameTime() + partialTick;
        Vec3 wind = Wind.direction(ClientWeather.windAngle());
        for (StormCell cell : SyncedWeather.cells()) {
            float strength = cell.strength();
            if (strength < 0.02F) continue;
            switch (cell.type) {
                case THUNDERSTORM, SUPERCELL, HAIL, HURRICANE, DERECHO, COLD_FRONT -> {
                    if (towers) towers(level, cell, cam, maxDistance, daylight, fog, time, wind, strength);
                }
                case SEA_FOG -> {
                    if (banks) fogBank(level, cell, cam, maxDistance, daylight, fog, strength);
                }
                default -> {}
            }
        }
    }

    /** A cumulonimbus: a tall column of cloud over the storm, spreading into an anvil downwind at the top. */
    private static void towers(ClientLevel level, StormCell cell, Vec3 cam, float maxDistance, float daylight, float fog, float time,
                               Vec3 wind, float strength) {
        // from inside the storm the tower is lost in the rain and cloud overhead
        if (cell.distanceTo(cam.x, cam.z) < cell.radius * 0.6F) return;
        double far = Math.sqrt((cell.x - cam.x) * (cell.x - cam.x) + (cell.z - cam.z) * (cell.z - cam.z));
        float distanceFade = Mth.clamp((float) ((4000.0 - far) / 1500.0), 0.0F, 1.0F);
        if (distanceFade <= 0.0F) return;
        boolean line = cell.elongation() > 1.5F;
        int columns = line ? 5 : 1;
        double speed = Math.sqrt(cell.vx * cell.vx + cell.vz * cell.vz);
        double px = speed > 1.0E-6 ? -cell.vz / speed : 0.0, pz = speed > 1.0E-6 ? cell.vx / speed : 1.0;
        float base = level.getSeaLevel() + 80.0F;
        float height = (cell.type == WeatherType.SUPERCELL ? 210.0F : line ? 120.0F : 160.0F) * (0.3F + 0.7F * strength);
        // a tower, not a blob: keep it narrow however wide the rain area is
        float width = line ? Math.min(cell.radius * 0.6F, 45.0F) : Math.min(cell.radius * 0.38F, 60.0F);
        float alpha = 0.9F * Mth.clamp(strength * 1.5F, 0.0F, 1.0F) * fog * distanceFade;
        int salt = cell.id * 7919;
        for (int c = 0; c < columns; c++) {
            double offset = line ? (c - (columns - 1) / 2.0) * cell.radius * cell.elongation() / 2.6 : 0.0;
            double cx = cell.x + px * offset, cz = cell.z + pz * offset;
            int n = 28;
            for (int i = 0; i < n; i++) {
                float s = i / (float) (n - 1);
                float jitter = PuffRenderer.hash(i, salt + c) - 0.5F;
                float angle = PuffRenderer.hash(i, salt + c + 101) * Mth.TWO_PI;
                float w = width * (0.75F + 0.25F * Mth.sin(s * 3.0F + time * 0.002F));
                double x = cx + Mth.cos(angle) * jitter * w, z = cz + Mth.sin(angle) * jitter * w;
                double y = base + s * height;
                // sunlit, billowing white tops over a dark, rain-laden base
                float shade = Mth.lerp(s, 0.42F, 0.95F) * (0.25F + 0.75F * daylight);
                PuffRenderer.add(x, y, z, w * (0.8F + 0.4F * PuffRenderer.hash(i, salt + c + 202)), shade, shade, shade * 1.03F, alpha, false, cam, maxDistance);
            }
            if (strength > 0.6F && cell.type != WeatherType.COLD_FRONT) {
                // the anvil, sheared out downwind
                for (int i = 0; i < 9; i++) {
                    float t = PuffRenderer.hash(i, salt + c + 303);
                    float across = (PuffRenderer.hash(i, salt + c + 404) - 0.5F) * 2.0F;
                    double x = cx + wind.x * t * width * 2.2 - wind.z * across * width;
                    double z = cz + wind.z * t * width * 2.2 + wind.x * across * width;
                    float shade = 0.9F * (0.25F + 0.75F * daylight);
                    PuffRenderer.add(x, base + height * (0.95F + 0.05F * t), z, width * 0.9F, shade, shade, shade * 1.03F,
                            alpha * (strength - 0.6F) * 2.5F * (1.0F - 0.5F * t), false, cam, maxDistance);
                }
            }
        }
    }

    /** Sea fog: a low, white bank lying over the water and rolling in toward the coast. */
    private static void fogBank(ClientLevel level, StormCell cell, Vec3 cam, float maxDistance, float daylight, float fog, float strength) {
        float sea = level.getSeaLevel();
        float shade = 0.15F + 0.8F * daylight;
        int salt = cell.id * 104729;
        for (int i = 0; i < 70; i++) {
            float r = Mth.sqrt(PuffRenderer.hash(i, salt)) * cell.radius * 0.95F;
            float angle = PuffRenderer.hash(i, salt + 1) * Mth.TWO_PI;
            double x = cell.x + Mth.cos(angle) * r, z = cell.z + Mth.sin(angle) * r;
            double dx = x - cam.x, dz = z - cam.z;
            if (dx * dx + dz * dz < 40 * 40) continue;
            float edge = Mth.clamp((cell.radius - r) / (cell.radius * 0.35F), 0.0F, 1.0F);
            float size = 16.0F + 10.0F * PuffRenderer.hash(i, salt + 2);
            PuffRenderer.add(x, sea + 2.0F + 7.0F * PuffRenderer.hash(i, salt + 3), z, size, shade, shade, shade * 1.02F,
                    0.55F * strength * edge * fog, false, cam, maxDistance);
        }
    }

    private CloudPuffs() {}
}
