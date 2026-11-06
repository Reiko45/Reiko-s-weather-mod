package com.reiko.weather.weather;

import net.minecraft.util.Mth;

/**
 * How a weather type manifests in one climate. Client-side values drive rendering; the hazard values
 * drive server-side gameplay. All values are at full intensity. Profiles can be blended, which is how
 * the edge of a storm cell fades into the surrounding weather.
 */
public final class WeatherProfile {
    public static final WeatherProfile NONE = builder().build();
    private static final int NEUTRAL_FOG = 0xC2C6CA;

    // --- visuals (client) ---
    /** Vanilla rain level (0-1). Vanilla renders this as rain or snow depending on biome. */
    public final float rain;
    /** Vanilla thunder level (0-1), darkens the sky further. */
    public final float thunder;
    /** Graying of the sky, hides the sun and stars. */
    public final float overcast;
    /** Sky tint color and strength (e.g. green supercell skies, orange sandstorms). */
    public final int tint;
    public final float tintStrength;
    /** Fog density (0 = none, 1 = ~10 blocks visibility). */
    public final float fog;
    /** Fog color, or -1 to use a neutral gray derived from the vanilla fog color. */
    public final int fogColor;
    /** Wind strength 0-1 (1 = hurricane force). */
    public final float wind;
    /** Low, dark storm-cloud deck coverage (0-1). */
    public final float clouds;
    private final float[] precip;

    // --- hazards and effects (mostly server) ---
    public final boolean hail;
    /** Freezing rate; 1 freezes unprotected players solid, lower values only chill. */
    public final float freeze;
    public final boolean heat;
    /** Max snow layers this weather piles up (0 = vanilla behaviour). */
    public final int snowLayers;
    public final boolean cropBoost;
    public final boolean glazeWater;
    /** Freezing rain: coats the ground in slippery ice glaze. */
    public final boolean glazeGround;
    /** Puddles form on flat ground. */
    public final boolean puddles;
    /** Heavy enough to cause flash floods (when enabled in the config). */
    public final boolean floods;
    /** Silent flashes on the horizon at night. */
    public final boolean heatLightning;
    /** Extra lightning strikes per minute near each player. */
    public final float lightning;
    public final boolean tornadoes;
    public final boolean waterspouts;

    private WeatherProfile(Builder b) {
        this.rain = b.rain;
        this.thunder = b.thunder;
        this.overcast = b.overcast;
        this.tint = b.tint;
        this.tintStrength = b.tintStrength;
        this.fog = b.fog;
        this.fogColor = b.fogColor;
        this.wind = b.wind;
        this.clouds = b.clouds >= 0 ? b.clouds : Math.max(b.overcast * 0.9F, Math.max(b.rain * 0.75F, b.thunder));
        this.precip = b.precip.clone();
        this.hail = b.hail;
        this.freeze = b.freeze;
        this.heat = b.heat;
        this.snowLayers = b.snowLayers;
        this.cropBoost = b.cropBoost;
        this.glazeWater = b.glazeWater;
        this.glazeGround = b.glazeGround;
        this.puddles = b.puddles;
        this.floods = b.floods;
        this.heatLightning = b.heatLightning;
        this.lightning = b.lightning;
        this.tornadoes = b.tornadoes;
        this.waterspouts = b.waterspouts;
    }

    public static Builder builder() {
        return new Builder();
    }

    public float rateOf(Precip p) {
        return precip[p.ordinal()];
    }

    /** Blends from {@code a} (t = 0) to {@code b} (t = 1). Gameplay switches over at the halfway point. */
    public static WeatherProfile blend(WeatherProfile a, WeatherProfile b, float t) {
        if (t <= 0.0F) return a;
        if (t >= 1.0F) return b;
        WeatherProfile d = t >= 0.5F ? b : a;
        Builder r = new Builder();
        r.rain = Mth.lerp(t, a.rain, b.rain);
        r.thunder = Mth.lerp(t, a.thunder, b.thunder);
        r.overcast = Mth.lerp(t, a.overcast, b.overcast);
        r.fog = Mth.lerp(t, a.fog, b.fog);
        r.wind = Mth.lerp(t, a.wind, b.wind);
        r.clouds = Mth.lerp(t, a.clouds, b.clouds);
        float ta = a.tintStrength, tb = b.tintStrength;
        r.tintStrength = Mth.lerp(t, ta, tb);
        r.tint = ta + tb <= 0 ? 0xFFFFFF : lerpColor(a.tint, b.tint, tb * t / (ta * (1 - t) + tb * t));
        r.fogColor = a.fogColor < 0 && b.fogColor < 0 ? -1
                : lerpColor(a.fogColor < 0 ? NEUTRAL_FOG : a.fogColor, b.fogColor < 0 ? NEUTRAL_FOG : b.fogColor, t);
        for (int i = 0; i < r.precip.length; i++) {
            r.precip[i] = Mth.lerp(t, a.precip[i], b.precip[i]);
        }
        r.freeze = Mth.lerp(t, a.freeze, b.freeze);
        r.lightning = Mth.lerp(t, a.lightning, b.lightning);
        r.hail = d.hail;
        r.heat = d.heat;
        r.snowLayers = d.snowLayers;
        r.cropBoost = d.cropBoost;
        r.glazeWater = d.glazeWater;
        r.glazeGround = d.glazeGround;
        r.puddles = d.puddles;
        r.floods = d.floods;
        r.heatLightning = d.heatLightning;
        r.tornadoes = d.tornadoes;
        r.waterspouts = d.waterspouts;
        return r.build();
    }

    private static int lerpColor(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255);
        int g = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }

    public static final class Builder {
        // package-private so datapack weathers (CustomWeather) can override any field
        float rain, thunder, overcast, tintStrength, fog, wind, freeze, lightning, clouds = -1;
        int tint = 0xFFFFFF, fogColor = -1, snowLayers;
        final float[] precip = new float[Precip.values().length];
        boolean hail, heat, cropBoost, glazeWater, glazeGround, puddles, floods, heatLightning, tornadoes, waterspouts;

        /** A builder pre-filled with an existing profile's values. */
        public static Builder from(WeatherProfile p) {
            Builder b = new Builder();
            b.rain = p.rain; b.thunder = p.thunder; b.overcast = p.overcast; b.tint = p.tint; b.tintStrength = p.tintStrength;
            b.fog = p.fog; b.fogColor = p.fogColor; b.wind = p.wind; b.clouds = p.clouds;
            System.arraycopy(p.precip, 0, b.precip, 0, b.precip.length);
            b.hail = p.hail; b.freeze = p.freeze; b.heat = p.heat; b.snowLayers = p.snowLayers; b.cropBoost = p.cropBoost;
            b.glazeWater = p.glazeWater; b.glazeGround = p.glazeGround; b.puddles = p.puddles; b.floods = p.floods;
            b.heatLightning = p.heatLightning; b.lightning = p.lightning; b.tornadoes = p.tornadoes; b.waterspouts = p.waterspouts;
            return b;
        }

        public Builder rain(float v) { rain = v; return this; }
        public Builder thunder(float v) { thunder = v; return this; }
        public Builder overcast(float v) { overcast = v; return this; }
        public Builder tint(int rgb, float strength) { tint = rgb; tintStrength = strength; return this; }
        public Builder fog(float v) { fog = v; return this; }
        public Builder fog(float v, int rgb) { fog = v; fogColor = rgb; return this; }
        public Builder wind(float v) { wind = v; return this; }
        public Builder clouds(float v) { clouds = v; return this; }
        public Builder precip(Precip p, float rate) { precip[p.ordinal()] = rate; return this; }
        public Builder hail() { hail = true; return this; }
        public Builder freeze(float v) { freeze = v; return this; }
        public Builder heat() { heat = true; heatLightning = true; return this; }
        public Builder snowLayers(int v) { snowLayers = v; return this; }
        /** Ordinary rain: crops grow faster and puddles form. */
        public Builder cropBoost() { cropBoost = true; puddles = true; return this; }
        public Builder glazeWater() { glazeWater = true; return this; }
        public Builder glazeGround() { glazeGround = true; return this; }
        public Builder floods() { floods = true; return this; }
        public Builder lightning(float perMinute) { lightning = perMinute; return this; }
        public Builder tornadoes() { tornadoes = true; return this; }
        public Builder waterspouts() { waterspouts = true; return this; }
        public WeatherProfile build() { return new WeatherProfile(this); }
    }
}
