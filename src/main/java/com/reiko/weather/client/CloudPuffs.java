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
        // (the volumetric clouds build the storms' towers themselves; the sprite towers clashed with them)
        boolean towers = ClientConfig.TOWERING_CLOUDS.get() && !VolumetricClouds.active(), banks = ClientConfig.FOG_BANKS.get(), shapes = ClientConfig.STORM_CLOUD_SHAPES.get();
        sunset = sunsetGlow(level, partialTick);
        Vec3 cam = camera.getPosition();
        float maxDistance = mc.gameRenderer.getDepthFar() * 0.9F;
        float daylight = level.getSkyDarken(partialTick);
        float fog = 1.0F - 0.6F * ClientWeather.fogDensity();
        float time = level.getGameTime() + partialTick;
        Vec3 wind = Wind.direction(com.reiko.weather.network.SyncedWeather.windAngle()); // clouds aloft follow the prevailing wind, not the local breezes
        for (StormCell cell : SyncedWeather.cells()) {
            float strength = cell.strength();
            if (strength < 0.02F) continue;
            switch (cell.type) {
                case HABOOB -> {
                    if (towers) haboob(level, cell, cam, maxDistance, daylight, fog, strength);
                }
                case THUNDERSTORM, SUPERCELL, HAIL, HURRICANE, DERECHO, COLD_FRONT, PYROCUMULUS, THUNDERSNOW, GRAUPEL, POLAR_LOW -> {
                    if (towers) towers(level, cell, cam, maxDistance, daylight, fog, time, wind, strength);
                    if (shapes && (cell.type == WeatherType.COLD_FRONT || cell.type == WeatherType.DERECHO)) {
                        shelfCloud(level, cell, cam, maxDistance, daylight, fog, strength);
                    }
                }
                case SEA_FOG -> {
                    if (banks) fogBank(level, cell, cam, maxDistance, daylight, fog, strength);
                }
                default -> {}
            }
            // in dry country, rain streaks hang under the clouds and dry up before they reach the ground
            if (ClientConfig.VIRGA.get() && ClientWeather.climate() == com.reiko.weather.weather.Climate.ARID && cell.type.vanillaRain
                    && cell.type != WeatherType.ATMOSPHERIC_RIVER) {
                virga(level, cell, cam, maxDistance, daylight, fog, strength);
            }
        }
    }

    /** Grey streaks of rain trailing down from the cloud base and fading out halfway to the ground. */
    private static void virga(ClientLevel level, StormCell cell, Vec3 cam, float maxDistance, float daylight, float fog, float strength) {
        if (cell.distanceTo(cam.x, cam.z) < cell.radius * 0.8F) return;
        float base = level.getSeaLevel() + 78.0F;
        int salt = cell.id * 6151;
        float shade = 0.45F * (0.3F + 0.7F * daylight);
        for (int column = 0; column < 7; column++) {
            float a = PuffRenderer.hash(column, salt) * Mth.TWO_PI, r = Mth.sqrt(PuffRenderer.hash(column, salt + 1)) * cell.radius * 0.5F;
            double x = cell.x + Mth.cos(a) * r, z = cell.z + Mth.sin(a) * r;
            float length = 18.0F + 22.0F * PuffRenderer.hash(column, salt + 2);
            for (int k = 0; k < 8; k++) {
                float t = k / 7.0F;
                // the streaks bend back with the wind as they fall
                PuffRenderer.add(x + t * 6.0, base - t * length, z, 9.0F - 4.0F * t, shade, shade, shade * 1.05F,
                        0.35F * strength * fog * (1.0F - t), false, cam, maxDistance);
            }
        }
    }

    /** A haboob: a wall of dust, hundreds of blocks long and taller than a mountain, rolling forward along the ground. */
    private static void haboob(ClientLevel level, StormCell cell, Vec3 cam, float maxDistance, float daylight, float fog, float strength) {
        double speed = Math.sqrt(cell.vx * cell.vx + cell.vz * cell.vz);
        if (speed < 1.0E-6) return;
        double ux = cell.vx / speed, uz = cell.vz / speed;
        if (cell.distanceTo(cam.x, cam.z) < cell.radius * 0.7F) return; // inside it: the fog does the work
        float ground = level.getSeaLevel() + 2.0F;
        float halfLength = cell.radius * cell.elongation() * 0.85F;
        int salt = cell.id * 2741;
        float light = 0.25F + 0.75F * daylight;
        int n = 30;
        for (int layer = 0; layer < 4; layer++) {
            for (int i = 0; i < n; i++) {
                float s = (i + PuffRenderer.hash(i, salt + layer)) / n * 2.0F - 1.0F;
                float ends = Mth.clamp((1.0F - Math.abs(s)) * 3.0F, 0.0F, 1.0F);
                // billowing, taller in the middle, a little behind the front at the top
                float h = (25.0F + 55.0F * ends * (0.7F + 0.3F * PuffRenderer.hash(i, salt + 9))) * layer / 3.0F;
                double along = s * halfLength, front = cell.radius * 0.6 - layer * 8.0;
                double x = cell.x + ux * front - uz * along, z = cell.z + uz * front + ux * along;
                float size = 24.0F + 12.0F * PuffRenderer.hash(i, salt + layer + 5);
                float shade = (0.55F + 0.1F * layer) * light;
                PuffRenderer.add(x, ground + h, z, size, shade * 1.0F, shade * 0.72F, shade * 0.48F, 0.85F * strength * ends * fog, false, cam, maxDistance);
            }
        }
    }

    private static int peakX, peakY = Integer.MIN_VALUE, peakZ;
    private static long peakScan = -1000;

    /**
     * Lenticular clouds: when a strong, steady wind blows over a mountain it sets up standing waves in the air
     * downwind, and smooth, lens-shaped clouds sit in the wave crests, stacked like plates, hardly moving.
     */
    public static void lenticular(Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD || !ClientConfig.LENTICULAR_CLOUDS.get() || !SyncedWeather.managed()) return;
        float prevailing = com.reiko.weather.weather.CustomWeathers.backgroundProfile(SyncedWeather.type(),
                com.reiko.weather.weather.CustomWeathers.client(SyncedWeather.custom()), ClientWeather.climate()).wind;
        if (prevailing < 0.45F || ClientWeather.rain() > 0.2F || ClientWeather.overcast() > 0.6F) return;
        Vec3 cam = camera.getPosition();
        scanPeak(level, cam);
        if (peakY < level.getSeaLevel() + 55) return;
        float maxDistance = mc.gameRenderer.getDepthFar() * 0.9F;
        float daylight = level.getSkyDarken(1.0F);
        Vec3 wind = Wind.direction(SyncedWeather.windAngle());
        float strength = Mth.clamp((prevailing - 0.45F) * 4.0F, 0.0F, 1.0F) * (1.0F - ClientWeather.overcast());
        for (int layer = 0; layer < 3; layer++) {
            double cx = peakX + wind.x * (30 + layer * 6), cz = peakZ + wind.z * (30 + layer * 6);
            float y = peakY + 28.0F + layer * 9.0F;
            float length = 70.0F - layer * 14.0F;
            int n = 11;
            for (int i = 0; i < n; i++) {
                float t = i / (float) (n - 1);
                float size = 6.0F + 15.0F * Mth.sin(t * Mth.PI) * (1.0F - layer * 0.15F);
                double along = (t - 0.5F) * length;
                // the lens lies across the wind, so it's long side-on to the flow
                double x = cx - wind.z * along, z = cz + wind.x * along;
                float shade = (0.85F + 0.1F * layer) * (0.25F + 0.75F * daylight);
                PuffRenderer.add(x, y, z, size, shade, shade, shade * 1.02F, 0.7F * strength, false, cam, maxDistance);
            }
        }
    }

    /** Finds the highest ground round about (every ten seconds or so): the peak lenticular and banner clouds form over. */
    private static void scanPeak(ClientLevel level, Vec3 cam) {
        long now = level.getGameTime();
        if (now - peakScan <= 200 && now >= peakScan) return;
        peakScan = now;
        peakY = Integer.MIN_VALUE;
        for (int i = 0; i < 24; i++) {
            for (int r = 80; r <= 240; r += 80) {
                float a = i * Mth.TWO_PI / 24.0F;
                int x = Mth.floor(cam.x + Mth.cos(a) * r), z = Mth.floor(cam.z + Mth.sin(a) * r);
                if (!SkySights.loaded(level, new net.minecraft.core.BlockPos(x, 0, z))) continue;
                int h = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
                if (h > peakY) {
                    peakY = h;
                    peakX = x;
                    peakZ = z;
                }
            }
        }
        if (peakY == Integer.MIN_VALUE) return;
        // climb to the very summit from the best sample
        for (int step = 0; step < 6; step++) {
            int bestX = peakX, bestZ = peakZ, best = peakY;
            for (int dx = -8; dx <= 8; dx += 4) {
                for (int dz = -8; dz <= 8; dz += 4) {
                    if (!SkySights.loaded(level, new net.minecraft.core.BlockPos(peakX + dx, 0, peakZ + dz))) continue;
                    int h = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, peakX + dx, peakZ + dz);
                    if (h > best) {
                        best = h;
                        bestX = peakX + dx;
                        bestZ = peakZ + dz;
                    }
                }
            }
            if (best == peakY) break;
            peakX = bestX;
            peakZ = bestZ;
            peakY = best;
        }
    }

    /**
     * A banner cloud: on a windy day, air forced up the windward face of a sharp peak swirls up again in the lee,
     * cools and condenses, so a plume of cloud streams off the downwind side of the summit like a flag from a pole
     * (as off the Matterhorn), while the windward side stays clear.
     */
    public static void banner(Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD || !ClientConfig.BANNER_CLOUDS.get() || !SyncedWeather.managed()) return;
        float prevailing = com.reiko.weather.weather.CustomWeathers.backgroundProfile(SyncedWeather.type(),
                com.reiko.weather.weather.CustomWeathers.client(SyncedWeather.custom()), ClientWeather.climate()).wind;
        if (prevailing < 0.35F || ClientWeather.rain() > 0.3F) return;
        Vec3 cam = camera.getPosition();
        scanPeak(level, cam);
        if (peakY < level.getSeaLevel() + 55) return;
        // only a peak that stands well clear of the ground round it flies a banner
        int shoulder = Integer.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F;
            int x = Mth.floor(peakX + Mth.cos(a) * 24), z = Mth.floor(peakZ + Mth.sin(a) * 24);
            if (!SkySights.loaded(level, new net.minecraft.core.BlockPos(x, 0, z))) continue;
            shoulder = Math.min(shoulder, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z));
        }
        if (shoulder == Integer.MAX_VALUE || peakY - shoulder < 18) return;
        float maxDistance = mc.gameRenderer.getDepthFar() * 0.9F;
        float daylight = level.getSkyDarken(1.0F);
        float time = level.getGameTime();
        Vec3 wind = Wind.direction(SyncedWeather.windAngle());
        float strength = Mth.clamp((prevailing - 0.35F) * 4.0F, 0.0F, 1.0F) * (1.0F - 0.5F * ClientWeather.overcast());
        int n = 18;
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            // the plume billows out and sinks a little as it streams away downwind, fluttering
            double along = 3.0 + t * 75.0;
            double flutter = Mth.sin(time * 0.03F + i * 0.9F) * (1.5 + t * 6.0);
            double x = peakX + 0.5 + wind.x * along - wind.z * flutter, z = peakZ + 0.5 + wind.z * along + wind.x * flutter;
            float y = peakY - 3.0F - t * 9.0F + Mth.sin(time * 0.021F + i) * 1.5F;
            float size = 4.0F + t * 15.0F + 2.0F * PuffRenderer.hash(i, 931);
            float shade = (0.88F + 0.08F * PuffRenderer.hash(i, 932)) * (0.25F + 0.75F * daylight);
            PuffRenderer.add(x, y, z, size, shade, shade, shade * 1.02F, 0.75F * strength * (1.0F - 0.65F * t), false, cam, maxDistance);
        }
    }

    /** Cold air trapped in a valley under a lid of warmer air, with the morning's smoke and haze lying in a flat layer at the top of it. */
    public static void inversion(Camera camera) {
        float strength = SkySights.inversion();
        if (strength < 0.02F) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = camera.getPosition();
        float lid = SkySights.inversionLid();
        float maxDistance = mc.gameRenderer.getDepthFar() * 0.9F;
        float daylight = mc.level == null ? 1.0F : mc.level.getSkyDarken(1.0F);
        float shade = 0.5F + 0.4F * daylight;
        for (int i = 0; i < 60; i++) {
            float a = PuffRenderer.hash(i, 911) * Mth.TWO_PI, r = 24.0F + Mth.sqrt(PuffRenderer.hash(i, 912)) * 110.0F;
            double x = Math.floor(cam.x / 64.0) * 64.0 + Mth.cos(a) * r, z = Math.floor(cam.z / 64.0) * 64.0 + Mth.sin(a) * r;
            PuffRenderer.add(x, lid + 1.5F * PuffRenderer.hash(i, 913), z, 16.0F + 8.0F * PuffRenderer.hash(i, 914),
                    shade * 0.95F, shade * 0.9F, shade * 0.86F, 0.22F * strength, false, cam, maxDistance);
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
                // a fire cloud is brown with smoke low down, with a white cap of cloud on top
                float smoky = cell.type == WeatherType.PYROCUMULUS ? Mth.clamp(1.0F - s * 1.4F, 0.0F, 1.0F) : 0.0F;
                PuffRenderer.add(x, y, z, w * (0.8F + 0.4F * PuffRenderer.hash(i, salt + c + 202)), shade * (1.0F - 0.1F * smoky),
                        shade * (1.0F - 0.3F * smoky), shade * (1.03F - 0.5F * smoky), alpha, false, cam, maxDistance);
            }
            if (ClientConfig.CLOUD_FORMS.get() && strength > 0.25F && strength < 0.7F && cell.age < cell.maxAge / 3) {
                // a pileus: a smooth cap of cloud draped over the top of a fast-growing tower
                for (int i = 0; i < 9; i++) {
                    float t = (i / 8.0F - 0.5F) * 2.0F;
                    double x = cx + Mth.cos(salt * 0.1F) * t * width * 0.9, z = cz + Mth.sin(salt * 0.1F) * t * width * 0.9;
                    double y = base + height + 10.0 - 6.0 * t * t;
                    float shade = 0.97F * (0.25F + 0.75F * daylight);
                    PuffRenderer.add(x, y, z, width * 0.45F, shade, shade, shade, alpha * 0.45F * (1.0F - t * t * 0.5F), false, cam, maxDistance);
                }
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
                if (strength > 0.75F && !line && ClientConfig.STORM_CLOUD_SHAPES.get()) {
                    mammatus(cell, cx, cz, base + height * 0.9F, width, wind, alpha * (strength - 0.75F) * 4.0F, daylight, cam, maxDistance, salt + c);
                }
            }
        }
    }

    /** How orange the low sun is making the undersides of the clouds (0 by day and night). */
    private static float sunset;

    private static float sunsetGlow(ClientLevel level, float partialTick) {
        float sun = com.reiko.weather.weather.SkyPhenomena.sunHeight(level, partialTick);
        return Mth.clamp(1.0F - Math.abs(sun - 0.05F) / 0.15F, 0.0F, 1.0F);
    }

    /**
     * Mammatus: pouches of cloud hanging in rows from the underside of the anvil, best seen as the storm moves off
     * (and glowing orange when the sun is low).
     */
    private static void mammatus(StormCell cell, double cx, double cz, float y, float width, Vec3 wind, float alpha, float daylight,
                                 Vec3 cam, float maxDistance, int salt) {
        if (alpha < 0.02F) return;
        for (int i = 0; i < 16; i++) {
            float t = 0.3F + 0.7F * PuffRenderer.hash(i, salt + 505);
            float across = (PuffRenderer.hash(i, salt + 606) - 0.5F) * 1.6F;
            double x = cx + wind.x * t * width * 2.0 - wind.z * across * width;
            double z = cz + wind.z * t * width * 2.0 + wind.x * across * width;
            float size = width * (0.18F + 0.1F * PuffRenderer.hash(i, salt + 707));
            float shade = 0.55F * (0.25F + 0.75F * daylight);
            float r = Mth.lerp(sunset, shade, 0.95F), g = Mth.lerp(sunset, shade, 0.55F), b = Mth.lerp(sunset, shade * 1.03F, 0.4F);
            PuffRenderer.add(x, y - size * 0.6F, z, size, r, g, b, alpha * 0.8F, false, cam, maxDistance);
        }
    }

    /**
     * A shelf cloud: a long, low, dark wedge of cloud along the leading edge of a squall line, with a lighter
     * underside and tiers stepping up behind it.
     */
    private static void shelfCloud(ClientLevel level, StormCell cell, Vec3 cam, float maxDistance, float daylight, float fog, float strength) {
        double speed = Math.sqrt(cell.vx * cell.vx + cell.vz * cell.vz);
        if (speed < 1.0E-6 || strength < 0.3F) return;
        double ux = cell.vx / speed, uz = cell.vz / speed;
        // only from ahead of the front (from underneath it you're in the downpour)
        double ahead = (cam.x - cell.x) * ux + (cam.z - cell.z) * uz;
        if (ahead < cell.radius * 0.5) return;
        float base = level.getSeaLevel() + 38.0F;
        float halfLength = cell.radius * cell.elongation() * 0.8F;
        int salt = cell.id * 15485863;
        int n = 26;
        for (int tier = 0; tier < 3; tier++) {
            double front = cell.radius * (0.85 - tier * 0.18);
            for (int i = 0; i < n; i++) {
                float s = (i + PuffRenderer.hash(i, salt + tier)) / n * 2.0F - 1.0F;
                double along = s * halfLength;
                double x = cell.x + ux * front - uz * along, z = cell.z + uz * front + ux * along;
                double dx = x - cam.x, dz = z - cam.z;
                if (dx * dx + dz * dz < 30 * 30) continue;
                float ends = Mth.clamp((1.0F - Math.abs(s)) * 4.0F, 0.0F, 1.0F);
                float size = 26.0F + 10.0F * PuffRenderer.hash(i, salt + tier + 11);
                float shade = (0.22F + 0.12F * tier) * (0.25F + 0.75F * daylight);
                PuffRenderer.add(x, base + tier * 16.0F + 4.0F * PuffRenderer.hash(i, salt + 3), z, size,
                        shade, shade * 1.02F, shade * 1.08F, 0.85F * strength * ends * fog, false, cam, maxDistance);
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
