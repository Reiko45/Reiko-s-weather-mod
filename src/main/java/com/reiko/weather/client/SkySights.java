package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.weather.Climate;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.StormCell;
import com.reiko.weather.weather.WeatherType;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
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
    /** A blue jet (a narrow blue cone shooting up out of the top of a storm) or an elf (a vast red ring flashing out high above it). */
    private record Upper(boolean jet, double x, double y, double z, float size, int seed, int life, long born) {}
    private static final List<Upper> UPPER = new ArrayList<>();
    /** How much of the horizon in each of 16 directions (from north, clockwise) is open sea, for mirages. */
    private static final float[] SEA = new float[16];
    private static float fataMorgana;
    private static float noctilucent, moonbow, fogbow, inversion, inversionLid;
    /** Whether the horizon toward the low sun is open sea (for the green flash). */
    private static boolean seaHorizon;

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        boolean on = level != null && level.dimension() == Level.OVERWORLD && SyncedWeather.managed();
        if (!on) {
            SPRITES.clear();
            UPPER.clear();
            noctilucent = moonbow = fogbow = inversion = fataMorgana = 0.0F;
            return;
        }
        long now = level.getGameTime();
        SPRITES.removeIf(s -> now - s.born() > s.life());
        UPPER.removeIf(s -> now - s.born() > s.life());
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

        // blue jets and elves: rarer still, over the biggest storms
        if (night && ClientConfig.UPPER_LIGHTNING.get() && !Accessibility.reduceFlashing()) {
            for (StormCell cell : SyncedWeather.cells()) {
                if (!cell.type.vanillaThunder || cell.strength() < 0.7F) continue;
                double edge = cell.distanceTo(cam.x, cam.z) - cell.radius;
                if (edge < 200 || edge > 1600 || RANDOM.nextInt(cell.type == WeatherType.SUPERCELL ? 700 : 1400) != 0) continue;
                double x = cell.x + (RANDOM.nextDouble() - 0.5) * cell.radius * 0.5, z = cell.z + (RANDOM.nextDouble() - 0.5) * cell.radius * 0.5;
                boolean jet = RANDOM.nextBoolean();
                double y = level.getSeaLevel() + (jet ? 240.0 : 420.0);
                UPPER.add(new Upper(jet, x, y, z, jet ? 18.0F + RANDOM.nextFloat() * 10.0F : 160.0F + RANDOM.nextFloat() * 80.0F, RANDOM.nextInt(),
                        jet ? 8 + RANDOM.nextInt(6) : 2 + RANDOM.nextInt(2), now));
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

        // a temperature inversion: on cold, still, clear mornings the valley air is trapped under a lid of warmer air
        long time = Math.floorMod(level.getDayTime(), 24000L);
        boolean morning = time > 22000L || time < 3000L;
        float inversionTarget = 0.0F;
        if (ClientConfig.INVERSION_HAZE.get() && morning && ClientWeather.wind() < 0.25F && overcast < 0.5F && rain < 0.05F
                && ClientWeather.airTemperature() < 8.0F) {
            var info = com.reiko.weather.weather.Microclimate.at(level, Mth.floor(cam.x), Mth.floor(cam.z), SyncedWeather.windAngle());
            if (info.valley() > 0.25F) {
                inversionTarget = Mth.clamp((info.valley() - 0.25F) * 2.0F, 0.0F, 1.0F);
                int floor = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, Mth.floor(cam.x), Mth.floor(cam.z));
                inversionLid = Mth.lerp(0.05F, inversionLid == 0 ? floor + 12 : inversionLid, floor + 12.0F);
            }
        }
        inversion = approach(inversion, inversionTarget, 0.003F);

        if (level.getGameTime() % 40L == 0L) {
            // the sun rises and sets along the east-west line: is that horizon open sea?
            float theta = level.getTimeOfDay(1.0F) * Mth.TWO_PI;
            double dir = Math.signum(-Mth.sin(theta));
            boolean open = cam.y < level.getSeaLevel() + 48 && dir != 0;
            int seen = 0;
            for (int d = 50; d <= 450 && open; d += 50) {
                BlockPos p = BlockPos.containing(cam.x + dir * d, level.getSeaLevel(), cam.z);
                // only the chunks this client has: anything further off would read as plains
                if (!loaded(level, p)) break;
                open = level.getBiome(p).is(net.minecraft.tags.BiomeTags.IS_OCEAN);
                seen++;
            }
            seaHorizon = open && seen >= 2;
        }
        if (level.getGameTime() % 100L == 0L) {
            // which way the open sea lies, all round the horizon
            for (int i = 0; i < 16; i++) {
                float a = i * Mth.TWO_PI / 16.0F;
                int sea = 0, seen = 0;
                for (int d = 40; d <= 360; d += 40) {
                    BlockPos p = BlockPos.containing(cam.x + Mth.sin(a) * d, level.getSeaLevel(), cam.z - Mth.cos(a) * d);
                    // only the chunks this client has: anything further off would read as plains
                    if (!loaded(level, p)) break;
                    if (level.getBiome(p).is(net.minecraft.tags.BiomeTags.IS_OCEAN)) sea++;
                    seen++;
                }
                SEA[i] = seen >= 2 ? sea / (float) seen : 0.0F;
            }
        }
        // a Fata Morgana: calm, clear days over a cold sea, when warm air lies over the chilled surface layer
        var seaBiome = level.getBiome(BlockPos.containing(cam.x, level.getSeaLevel(), cam.z));
        boolean coldSea = climate == Climate.COLD || seaBiome.value().getBaseTemperature() < 0.4F
                || seaBiome.is(net.minecraft.world.level.biome.Biomes.COLD_OCEAN) || seaBiome.is(net.minecraft.world.level.biome.Biomes.DEEP_COLD_OCEAN)
                || seaBiome.is(net.minecraft.world.level.biome.Biomes.DEEP_FROZEN_OCEAN);
        float fataTarget = ClientConfig.FATA_MORGANA.get() && sun > 0.08F && coldSea && cam.y < level.getSeaLevel() + 30 && ClientWeather.wind() < 0.35F
                && overcast < 0.4F && rain < 0.02F ? 1.0F - fog : 0.0F;
        fataMorgana = approach(fataMorgana, fataTarget, 0.003F);
    }

    /**
     * Whether this client actually holds the chunk at a position. (A client's hasChunk says yes even for chunks it
     * was never sent: those come back as an empty stand-in whose biome reads as plains.)
     */
    public static boolean loaded(net.minecraft.world.level.Level level, BlockPos pos) {
        return !(level.getChunk(pos.getX() >> 4, pos.getZ() >> 4) instanceof net.minecraft.world.level.chunk.EmptyLevelChunk);
    }

    /** 0-1, how strongly a Fata Morgana is showing over the sea horizon. */
    public static float fataMorgana() {
        return fataMorgana;
    }

    /** How much of the horizon in a direction (0-15, clockwise from north) is open sea. */
    public static float sea(int sector) {
        return SEA[Math.floorMod(sector, 16)];
    }

    /** Adds this frame's blue jets and elves to the puff renderer. */
    public static void collectUpper(Camera camera, float partialTick) {
        if (UPPER.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = camera.getPosition();
        float maxDistance = mc.gameRenderer.getDepthFar() * 0.9F;
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        for (Upper u : UPPER) {
            float age = (now - u.born() + partialTick) / Math.max(1, u.life());
            float flash = Mth.clamp(1.0F - age, 0.0F, 1.0F);
            if (flash <= 0.01F) continue;
            if (u.jet()) {
                // a narrow blue cone climbing out of the cloud top, fanning out as it rises
                int n = 10;
                float reach = Math.min(1.0F, age * 2.5F);
                for (int k = 0; k < n; k++) {
                    float f = k / (float) (n - 1) * reach;
                    float wobble = (PuffRenderer.hash(k, u.seed()) - 0.5F) * u.size() * 0.3F * f;
                    PuffRenderer.add(u.x() + wobble, u.y() + f * u.size() * 5.0F, u.z() - wobble, u.size() * (0.25F + 0.6F * f),
                            0.35F, 0.55F, 1.0F, 0.55F * flash * (1.0F - f * 0.5F), true, cam, maxDistance);
                }
            } else {
                // an elf: a huge, faint red ring spreading out in a few thousandths of a second (here, a couple of ticks)
                float r = u.size() * (0.4F + 0.6F * age);
                for (int k = 0; k < 28; k++) {
                    float a = k / 28.0F * Mth.TWO_PI;
                    PuffRenderer.add(u.x() + Mth.cos(a) * r, u.y(), u.z() + Mth.sin(a) * r, u.size() * 0.18F, 1.0F, 0.3F, 0.25F, 0.4F * flash, true, cam, maxDistance);
                }
            }
        }
    }

    public static boolean seaHorizon() {
        return seaHorizon;
    }

    public static float inversion() {
        return inversion;
    }

    public static float inversionLid() {
        return inversionLid;
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
