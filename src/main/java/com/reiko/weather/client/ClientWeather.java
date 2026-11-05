package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.registry.ModItems;
import com.reiko.weather.weather.Climate;
import com.reiko.weather.weather.LocalWeather;
import com.reiko.weather.weather.Precip;
import com.reiko.weather.weather.SkyPhenomena;
import com.reiko.weather.weather.StormCell;
import com.reiko.weather.weather.WeatherProfile;
import com.reiko.weather.weather.WeatherProfiles;
import com.reiko.weather.weather.WeatherType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.Vec3;

/**
 * The client's view of the weather where the camera is. Every visual channel is smoothed so weather
 * fades in and out, and so walking out of a storm cell or from a desert into a forest blends smoothly.
 */
public final class ClientWeather {
    private static final RandomSource RANDOM = RandomSource.create();

    private static boolean active;
    private static WeatherType type = WeatherType.CLEAR;
    private static WeatherType previousType = WeatherType.CLEAR;
    private static Climate climate = Climate.TEMPERATE;

    private static float rain, thunder, overcast, fog, wind, exposure = 1.0F;
    private static float tintR = 1, tintG = 1, tintB = 1, tintStrength;
    private static float fogR = 0.75F, fogG = 0.78F, fogB = 0.8F, fogColorWeight;
    private static boolean fogAutoGray = true;
    private static final float[] precipRates = new float[Precip.values().length];
    private static float windAngle;
    private static float rainbow, aurora, mist, wetness, backgroundClouds;
    private static int localRainbowTicks;

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            reset();
            return;
        }
        boolean managed = SyncedWeather.managed() && level.dimension() == Level.OVERWORLD;
        if (!managed && !active) {
            return;
        }
        if (managed) {
            SyncedWeather.tick();
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        BlockPos camPos = BlockPos.containing(cam);
        climate = Climate.at(level, camPos);
        LocalWeather local = managed
                ? LocalWeather.resolve(SyncedWeather.type(), 1.0F, SyncedWeather.cells(), climate, cam.x, cam.z)
                : null;
        previousType = type;
        type = local != null ? local.type() : WeatherType.CLEAR;
        WeatherProfile target = local != null ? local.profile() : WeatherProfile.NONE;

        rain = approach(rain, target.rain, 0.004F);
        thunder = approach(thunder, target.thunder, 0.004F);
        overcast = approach(overcast, target.overcast, 0.004F);
        fog = approach(fog, target.fog, 0.004F);
        wind = approach(wind, target.wind, 0.005F);
        float skyLight = level.getBrightness(LightLayer.SKY, camPos) / 15.0F;
        exposure = approach(exposure, skyLight, 0.02F);
        float bgClouds = managed ? WeatherProfiles.get(SyncedWeather.type(), climate).clouds : 0.0F;
        backgroundClouds = approach(backgroundClouds, bgClouds, 0.003F);

        float tr = (target.tint >> 16 & 255) / 255.0F, tg = (target.tint >> 8 & 255) / 255.0F, tb = (target.tint & 255) / 255.0F;
        tintR = Mth.lerp(0.02F, tintR, tr);
        tintG = Mth.lerp(0.02F, tintG, tg);
        tintB = Mth.lerp(0.02F, tintB, tb);
        tintStrength = approach(tintStrength, target.tintStrength, 0.004F);

        if (target.fogColor >= 0) {
            fogAutoGray = false;
            fogR = Mth.lerp(0.02F, fogR, (target.fogColor >> 16 & 255) / 255.0F);
            fogG = Mth.lerp(0.02F, fogG, (target.fogColor >> 8 & 255) / 255.0F);
            fogB = Mth.lerp(0.02F, fogB, (target.fogColor & 255) / 255.0F);
            fogColorWeight = approach(fogColorWeight, Math.min(1.0F, target.fog * 1.1F), 0.004F);
        } else {
            fogColorWeight = approach(fogColorWeight, Math.min(0.8F, target.fog), 0.004F);
            if (fogColorWeight < 0.05F) {
                fogAutoGray = true;
            }
        }

        for (Precip p : Precip.values()) {
            precipRates[p.ordinal()] = approach(precipRates[p.ordinal()], target.rateOf(p), 0.006F);
        }

        float syncedAngle = SyncedWeather.windAngle();
        windAngle += Mth.wrapDegrees((syncedAngle - windAngle) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * 0.02F;

        float sun = SkyPhenomena.sunHeight(level, 1.0F);
        // a rainbow after the rain: either the whole sky cleared, or a storm cell just moved off
        if (managed && previousType.vanillaRain && previousType != WeatherType.SNOW && type.isCalm() && sun > 0) {
            localRainbowTicks = 2400;
        }
        if (localRainbowTicks > 0) localRainbowTicks--;
        int rainbowTicks = Math.max(SyncedWeather.rainbowTicks(), localRainbowTicks);
        boolean rainbowWanted = managed && rainbowTicks > 0 && type.isCalm() && ClientConfig.RAINBOWS.get() && climate != Climate.ARID;
        rainbow = approach(rainbow, rainbowWanted ? Math.min(1.0F, rainbowTicks / 600.0F) : 0.0F, 0.004F);

        boolean auroraWanted = managed && ClientConfig.AURORAS.get() && SkyPhenomena.isAuroraNight(level.getDayTime())
                && climate == Climate.COLD && (type == WeatherType.CLEAR || type == WeatherType.WINDY);
        aurora = approach(aurora, auroraWanted ? 1.0F : 0.0F, 0.003F);

        mist = approach(mist, managed ? mistTarget(level, camPos, cam.y) : 0.0F, 0.003F);

        boolean wetHere = rain > 0.3F && level.getBiome(camPos).value().getPrecipitationAt(camPos) == Biome.Precipitation.RAIN;
        wetness = wetHere ? approach(wetness, Math.min(1.0F, rain), 0.002F) : approach(wetness, 0.0F, 0.0006F);

        if (managed && ClientConfig.HEAT_LIGHTNING.get() && sun < -0.1F && level.getSkyFlashTime() <= 0) {
            boolean hotNight = target.heatLightning;
            boolean distantStorm = distantThunderCell(cam.x, cam.z);
            if ((hotNight || distantStorm) && RANDOM.nextInt(hotNight ? 160 : 260) == 0) {
                level.setSkyFlashTime(2);
            }
        }

        active = managed || rain > 0.001F || fog > 0.001F || overcast > 0.001F || wind > 0.001F || mist > 0.001F;

        // drive vanilla's rain/thunder rendering with our intensities (drizzle, sleet, storm cells...)
        if (managed) {
            level.setRainLevel(rain);
            level.setThunderLevel(thunder);
        }
    }

    /** Morning mist over rivers, swamps and beaches, hugging the ground on calm mornings. */
    private static float mistTarget(ClientLevel level, BlockPos pos, double camY) {
        if (!ClientConfig.MIST.get() || !(type.isCalm() || type == WeatherType.FOG || type == WeatherType.DRIZZLE)) {
            return 0.0F;
        }
        long time = Math.floorMod(level.getDayTime(), 24000L);
        // strongest around sunrise (time 0), fading out by mid-morning
        float dawn = time >= 22500 ? (time - 22500) / 1500.0F : time < 2500 ? 1.0F - Math.max(0, time - 500) / 2000.0F : 0.0F;
        if (dawn <= 0.0F) {
            return 0.0F;
        }
        Holder<Biome> biome = level.getBiome(pos);
        boolean damp = biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH) || biome.is(Biomes.SWAMP)
                || biome.is(Biomes.MANGROVE_SWAMP) || biome.is(BiomeTags.IS_OCEAN) || nearOpenWater(level, pos);
        if (!damp) {
            return 0.0F;
        }
        float altitude = 1.0F - Mth.clamp((float) (camY - level.getSeaLevel() - 6) / 14.0F, 0.0F, 1.0F);
        return dawn * altitude;
    }

    /** True when several of the surrounding columns end in water (lakes, rivers, the sea). */
    private static boolean nearOpenWater(ClientLevel level, BlockPos pos) {
        int wet = 0;
        for (int i = 0; i < 12; i++) {
            float angle = i * Mth.TWO_PI / 12.0F;
            int x = pos.getX() + Math.round(Mth.cos(angle) * 12), z = pos.getZ() + Math.round(Mth.sin(angle) * 12);
            int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
            if (level.getFluidState(new BlockPos(x, top - 1, z)).is(net.minecraft.tags.FluidTags.WATER)) {
                wet++;
            }
        }
        return wet >= 4;
    }

    private static boolean distantThunderCell(double x, double z) {
        for (StormCell cell : SyncedWeather.cells()) {
            double edge = cell.distanceTo(x, z) - cell.radius;
            if (cell.type.vanillaThunder && edge > 0 && edge < 1500) {
                return true;
            }
        }
        return false;
    }

    public static void reset() {
        active = false;
        type = previousType = WeatherType.CLEAR;
        rain = thunder = overcast = fog = wind = tintStrength = fogColorWeight = rainbow = aurora = mist = wetness = backgroundClouds = 0.0F;
        localRainbowTicks = 0;
        exposure = 1.0F;
        java.util.Arrays.fill(precipRates, 0.0F);
    }

    private static float approach(float current, float target, float step) {
        if (current < target) {
            return Math.min(target, current + step);
        }
        return Math.max(target, current - step);
    }

    // ------------------------------------------------------------------ accessors

    public static boolean active() { return active; }
    public static WeatherType type() { return type; }
    public static Climate climate() { return climate; }
    public static float wind() { return wind; }
    public static float rain() { return rain; }
    public static float windAngle() { return windAngle; }
    public static float exposure() { return exposure; }
    public static float precipRate(Precip p) { return precipRates[p.ordinal()]; }
    public static float rainbow() { return rainbow; }
    public static float aurora() { return aurora; }
    public static float mist() { return mist; }
    public static float wetness() { return ClientConfig.WET_GROUND.get() ? wetness : 0.0F; }

    /** Storm-cloud deck coverage at a world position: the background weather plus any storm cell there. */
    public static float cloudsAt(double x, double z) {
        float coverage = backgroundClouds;
        for (StormCell cell : SyncedWeather.cells()) {
            float w = cell.weightAt(x, z);
            if (w > 0) {
                coverage = Math.max(coverage, w * WeatherProfiles.get(cell.type, Climate.TEMPERATE).clouds);
            }
        }
        return coverage;
    }

    public static boolean hasStormClouds() {
        return backgroundClouds > 0.01F || !SyncedWeather.cells().isEmpty();
    }

    private static boolean wearingGoggles() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.GOGGLES.get());
    }

    /** Effective fog density at the camera; fades out underground and indoors, and goggles cut through storms. */
    public static float fogDensity() {
        if (!ClientConfig.FOG.get()) {
            return 0.0F;
        }
        float weatherFog = fog;
        if (type != WeatherType.FOG && wearingGoggles()) {
            weatherFog *= 0.45F;
        }
        return Math.max(weatherFog, mist * 0.72F) * (0.25F + 0.75F * exposure);
    }

    public static float fogColorWeight() {
        return ClientConfig.FOG.get() ? Math.max(fogColorWeight, mist * 0.85F) * (0.25F + 0.75F * exposure) : 0.0F;
    }

    /** When morning mist dominates, fog is soft white rather than the weather's own color. */
    public static boolean mistDominates() {
        return mist * 0.72F > fog;
    }

    public static boolean fogAutoGray() { return fogAutoGray; }
    public static float fogR() { return fogR; }
    public static float fogG() { return fogG; }
    public static float fogB() { return fogB; }

    /** How much the sun and stars are hidden by cloud cover. */
    public static float sunOcclusion() {
        if (!active || !ClientConfig.SKY_EFFECTS.get()) {
            return 0.0F;
        }
        return Math.max(overcast * 0.9F, Math.min(1.0F, fog * 1.05F));
    }

    // ------------------------------------------------------------------ sky hooks (called from mixins)

    public static Vec3 modifySkyColor(Vec3 color) {
        if (!active || !ClientConfig.SKY_EFFECTS.get()) {
            return color;
        }
        double r = color.x, g = color.y, b = color.z;
        double lum = r * 0.3 + g * 0.59 + b * 0.11;
        if (overcast > 0) {
            double gray = lum * 0.85;
            double k = overcast * 0.85;
            r = Mth.lerp(k, r, gray);
            g = Mth.lerp(k, g, gray);
            b = Mth.lerp(k, b, gray * 1.03);
        }
        if (tintStrength > 0) {
            double brightness = Mth.clamp(lum * 1.7, 0.0, 1.0);
            r = Mth.lerp(tintStrength, r, tintR * brightness);
            g = Mth.lerp(tintStrength, g, tintG * brightness);
            b = Mth.lerp(tintStrength, b, tintB * brightness);
        }
        return new Vec3(r, g, b);
    }

    public static Vec3 modifyCloudColor(Vec3 color) {
        if (!active || !ClientConfig.SKY_EFFECTS.get()) {
            return color;
        }
        double darken = 1.0 - overcast * 0.4;
        double r = color.x * darken, g = color.y * darken, b = color.z * darken;
        if (tintStrength > 0) {
            double lum = r * 0.3 + g * 0.59 + b * 0.11;
            double k = tintStrength * 0.6;
            r = Mth.lerp(k, r, tintR * lum);
            g = Mth.lerp(k, g, tintG * lum);
            b = Mth.lerp(k, b, tintB * lum);
        }
        return new Vec3(r, g, b);
    }

    /** Extra darkening of world lighting under heavy cloud (vanilla already handles rain). */
    public static float modifySkyDarken(float darken) {
        if (!active || !ClientConfig.SKY_EFFECTS.get()) {
            return darken;
        }
        float extra = Math.max(0.0F, overcast - rain * 0.5F) * 0.18F + tintStrength * 0.1F;
        return darken * (1.0F - extra);
    }

    private ClientWeather() {}
}
