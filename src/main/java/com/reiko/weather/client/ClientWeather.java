package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.weather.Climate;
import com.reiko.weather.weather.Precip;
import com.reiko.weather.weather.WeatherProfile;
import com.reiko.weather.weather.WeatherProfiles;
import com.reiko.weather.weather.WeatherType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * The client's view of the weather where the camera is. Every visual channel is smoothed so weather
 * fades in and out, and so walking from a desert into a forest blends the sandstorm into a dusty gale.
 */
public final class ClientWeather {
    private static boolean active;
    private static WeatherType type = WeatherType.CLEAR;
    private static Climate climate = Climate.TEMPERATE;

    private static float rain, thunder, overcast, fog, wind, exposure = 1.0F;
    private static float tintR = 1, tintG = 1, tintB = 1, tintStrength;
    private static float fogR = 0.75F, fogG = 0.78F, fogB = 0.8F, fogColorWeight;
    private static boolean fogAutoGray = true;
    private static final float[] precipRates = new float[Precip.values().length];
    private static float windAngle;
    private static float rainbow, aurora;

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
        type = managed ? SyncedWeather.type() : WeatherType.CLEAR;
        BlockPos camPos = mc.gameRenderer.getMainCamera().getBlockPosition();
        climate = Climate.at(level, camPos);
        WeatherProfile target = managed ? WeatherProfiles.get(type, climate) : WeatherProfile.NONE;

        rain = approach(rain, target.rain, 0.004F);
        thunder = approach(thunder, target.thunder, 0.004F);
        overcast = approach(overcast, target.overcast, 0.004F);
        fog = approach(fog, target.fog, 0.004F);
        wind = approach(wind, target.wind, 0.005F);
        float skyLight = level.getBrightness(LightLayer.SKY, camPos) / 15.0F;
        exposure = approach(exposure, skyLight, 0.02F);

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

        // rainbow after the rain, during daylight
        boolean rainbowWanted = managed && SyncedWeather.rainbowTicks() > 0 && type.isCalm()
                && ClientConfig.RAINBOWS.get() && climate != Climate.ARID;
        rainbow = approach(rainbow, rainbowWanted ? Math.min(1.0F, SyncedWeather.rainbowTicks() / 600.0F) : 0.0F, 0.004F);

        // auroras on some clear nights in cold places
        long day = level.getDayTime() / 24000L;
        boolean auroraNight = Math.floorMod(Mth.murmurHash3Mixer((int) day * 31 + 7), 100) < 55;
        boolean auroraWanted = managed && ClientConfig.AURORAS.get() && auroraNight && climate == Climate.COLD
                && (type == WeatherType.CLEAR || type == WeatherType.WINDY);
        aurora = approach(aurora, auroraWanted ? 1.0F : 0.0F, 0.003F);

        active = managed || rain > 0.001F || fog > 0.001F || overcast > 0.001F || wind > 0.001F;

        // drive vanilla's rain/thunder rendering with our intensities (drizzle, sleet, sandstorms...)
        if (managed) {
            level.setRainLevel(rain);
            level.setThunderLevel(thunder);
        }
    }

    public static void reset() {
        active = false;
        type = WeatherType.CLEAR;
        rain = thunder = overcast = fog = wind = tintStrength = fogColorWeight = rainbow = aurora = 0.0F;
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
    public static float windAngle() { return windAngle; }
    public static float exposure() { return exposure; }
    public static float precipRate(Precip p) { return precipRates[p.ordinal()]; }
    public static float rainbow() { return rainbow; }
    public static float aurora() { return aurora; }

    /** Effective fog density at the camera; fades out underground and indoors. */
    public static float fogDensity() {
        return ClientConfig.FOG.get() ? fog * (0.25F + 0.75F * exposure) : 0.0F;
    }

    public static float fogColorWeight() {
        return ClientConfig.FOG.get() ? fogColorWeight * (0.25F + 0.75F * exposure) : 0.0F;
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
