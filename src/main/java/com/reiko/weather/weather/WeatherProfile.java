package com.reiko.weather.weather;

/**
 * How a weather type manifests in one climate. Client-side values drive rendering; the hazard values
 * drive server-side gameplay. All values are at full intensity.
 */
public final class WeatherProfile {
    public static final WeatherProfile NONE = builder().build();

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
    public final Precip precip;
    public final float precipRate;
    public final Precip precip2;
    public final float precip2Rate;

    // --- hazards (server) ---
    public final boolean hail;
    /** Freezing rate; 1 freezes unprotected entities solid, lower values only chill. */
    public final float freeze;
    public final boolean heat;
    /** Max snow layers this weather piles up (0 = vanilla behaviour). */
    public final int snowLayers;
    public final boolean cropBoost;
    public final boolean glazeWater;
    /** Extra lightning strikes per minute near each player. */
    public final float lightning;
    public final boolean tornadoes;

    private WeatherProfile(Builder b) {
        this.rain = b.rain;
        this.thunder = b.thunder;
        this.overcast = b.overcast;
        this.tint = b.tint;
        this.tintStrength = b.tintStrength;
        this.fog = b.fog;
        this.fogColor = b.fogColor;
        this.wind = b.wind;
        this.precip = b.precip;
        this.precipRate = b.precipRate;
        this.precip2 = b.precip2;
        this.precip2Rate = b.precip2Rate;
        this.hail = b.hail;
        this.freeze = b.freeze;
        this.heat = b.heat;
        this.snowLayers = b.snowLayers;
        this.cropBoost = b.cropBoost;
        this.glazeWater = b.glazeWater;
        this.lightning = b.lightning;
        this.tornadoes = b.tornadoes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public float rateOf(Precip p) {
        float r = 0.0F;
        if (precip == p) r += precipRate;
        if (precip2 == p) r += precip2Rate;
        return r;
    }

    public static final class Builder {
        private float rain, thunder, overcast, tintStrength, fog, wind, precipRate, precip2Rate, freeze, lightning;
        private int tint = 0xFFFFFF, fogColor = -1, snowLayers;
        private Precip precip = Precip.NONE, precip2 = Precip.NONE;
        private boolean hail, heat, cropBoost, glazeWater, tornadoes;

        public Builder rain(float v) { rain = v; return this; }
        public Builder thunder(float v) { thunder = v; return this; }
        public Builder overcast(float v) { overcast = v; return this; }
        public Builder tint(int rgb, float strength) { tint = rgb; tintStrength = strength; return this; }
        public Builder fog(float v) { fog = v; return this; }
        public Builder fog(float v, int rgb) { fog = v; fogColor = rgb; return this; }
        public Builder wind(float v) { wind = v; return this; }
        public Builder precip(Precip p, float rate) {
            if (precip == Precip.NONE) { precip = p; precipRate = rate; } else { precip2 = p; precip2Rate = rate; }
            return this;
        }
        public Builder hail() { hail = true; return this; }
        public Builder freeze(float v) { freeze = v; return this; }
        public Builder heat() { heat = true; return this; }
        public Builder snowLayers(int v) { snowLayers = v; return this; }
        public Builder cropBoost() { cropBoost = true; return this; }
        public Builder glazeWater() { glazeWater = true; return this; }
        public Builder lightning(float perMinute) { lightning = perMinute; return this; }
        public Builder tornadoes() { tornadoes = true; return this; }
        public WeatherProfile build() { return new WeatherProfile(this); }
    }
}
