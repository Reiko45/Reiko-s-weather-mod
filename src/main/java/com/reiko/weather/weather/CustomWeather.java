package com.reiko.weather.weather;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A weather type defined in a datapack, at {@code data/<namespace>/reikos_weather/weather/<name>.json}.
 * It is built on top of one of the built-in weathers (its {@code base}), which decides how vanilla sees
 * it (rain, thunder) and gives every value the file does not override. See the README for the format.
 *
 * @param json the source, kept so the definition can be sent to clients as-is
 */
public record CustomWeather(ResourceLocation id, MutableComponent name, WeatherType base, int weight, int minDuration, int maxDuration,
                            Set<Season> seasons, Set<Climate> climates, WeatherProfile[] profiles, String json) {

    public static CustomWeather parse(ResourceLocation id, JsonObject json) {
        String baseId = GsonHelper.getAsString(json, "base");
        WeatherType base = WeatherType.byId(baseId);
        if (base == null) {
            throw new JsonParseException("Unknown base weather '" + baseId + "'");
        }
        MutableComponent name;
        if (json.has("translate")) {
            name = Component.translatable(GsonHelper.getAsString(json, "translate"));
        } else if (json.has("name")) {
            name = Component.literal(GsonHelper.getAsString(json, "name"));
        } else {
            name = Component.literal(id.getPath().replace('_', ' '));
        }
        int weight = GsonHelper.getAsInt(json, "weight", 0);
        int min = base.minDuration, max = base.maxDuration;
        if (json.has("duration")) {
            JsonElement d = json.get("duration");
            if (d.isJsonArray()) {
                JsonArray a = d.getAsJsonArray();
                min = a.get(0).getAsInt() * 20;
                max = a.get(a.size() - 1).getAsInt() * 20;
            } else {
                min = max = d.getAsInt() * 20;
            }
        }
        if (min <= 0 || max < min) {
            throw new JsonParseException("Invalid duration");
        }
        Set<Season> seasons = EnumSet.noneOf(Season.class);
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "seasons", new JsonArray())) {
            Season s = Season.byId(e.getAsString());
            if (s == null) throw new JsonParseException("Unknown season '" + e.getAsString() + "'");
            seasons.add(s);
        }
        Set<Climate> climates = EnumSet.noneOf(Climate.class);
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "climates", new JsonArray())) {
            try {
                climates.add(Climate.valueOf(e.getAsString().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                throw new JsonParseException("Unknown climate '" + e.getAsString() + "'");
            }
        }
        JsonObject overrides = GsonHelper.getAsJsonObject(json, "profile", new JsonObject());
        JsonObject perClimate = GsonHelper.getAsJsonObject(json, "climate_profiles", new JsonObject());
        WeatherProfile[] profiles = new WeatherProfile[Climate.values().length];
        for (Climate c : Climate.values()) {
            WeatherProfile.Builder b = WeatherProfile.Builder.from(WeatherProfiles.get(base, c));
            apply(b, overrides);
            String key = c.name().toLowerCase(Locale.ROOT);
            if (perClimate.has(key)) {
                apply(b, GsonHelper.getAsJsonObject(perClimate, key));
            }
            profiles[c.ordinal()] = b.build();
        }
        return new CustomWeather(id, name, base, Math.max(0, weight), min, max, seasons, climates, profiles, json.toString());
    }

    private static void apply(WeatherProfile.Builder b, JsonObject o) {
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            JsonElement v = e.getValue();
            switch (e.getKey()) {
                case "rain" -> b.rain = unit(v);
                case "thunder" -> b.thunder = unit(v);
                case "overcast" -> b.overcast = unit(v);
                case "fog" -> b.fog = unit(v);
                case "fog_color" -> b.fogColor = color(v);
                case "tint" -> b.tint = color(v);
                case "tint_strength" -> b.tintStrength = unit(v);
                case "wind" -> b.wind = unit(v);
                case "clouds" -> b.clouds = unit(v);
                case "freeze" -> b.freeze = unit(v);
                case "lightning" -> b.lightning = Math.max(0.0F, v.getAsFloat());
                case "snow_layers" -> b.snowLayers = Math.max(0, Math.min(8, v.getAsInt()));
                case "hail" -> b.hail = v.getAsBoolean();
                case "heat" -> b.heat = v.getAsBoolean();
                case "crop_boost" -> b.cropBoost = v.getAsBoolean();
                case "puddles" -> b.puddles = v.getAsBoolean();
                case "glaze_water" -> b.glazeWater = v.getAsBoolean();
                case "glaze_ground" -> b.glazeGround = v.getAsBoolean();
                case "floods" -> b.floods = v.getAsBoolean();
                case "heat_lightning" -> b.heatLightning = v.getAsBoolean();
                case "tornadoes" -> b.tornadoes = v.getAsBoolean();
                case "waterspouts" -> b.waterspouts = v.getAsBoolean();
                case "debris" -> b.debris = v.getAsBoolean();
                case "ash_layers" -> b.ashLayers = Math.max(0, Math.min(8, v.getAsInt()));
                case "acid" -> b.acid = v.getAsBoolean();
                case "precipitation" -> {
                    java.util.Arrays.fill(b.precip, 0.0F);
                    for (Map.Entry<String, JsonElement> p : v.getAsJsonObject().entrySet()) {
                        Precip precip;
                        try {
                            precip = Precip.valueOf(p.getKey().toUpperCase(Locale.ROOT));
                        } catch (IllegalArgumentException ex) {
                            throw new JsonParseException("Unknown precipitation '" + p.getKey() + "'");
                        }
                        b.precip[precip.ordinal()] = unit(p.getValue());
                    }
                }
                default -> throw new JsonParseException("Unknown profile field '" + e.getKey() + "'");
            }
        }
    }

    private static float unit(JsonElement v) {
        return Math.max(0.0F, Math.min(1.0F, v.getAsFloat()));
    }

    private static int color(JsonElement v) {
        if (v.getAsJsonPrimitive().isNumber()) {
            return v.getAsInt() & 0xFFFFFF;
        }
        String s = v.getAsString().trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException ex) {
            throw new JsonParseException("Invalid color '" + v.getAsString() + "'");
        }
    }

    public WeatherProfile profile(Climate climate) {
        return profiles[climate.ordinal()];
    }

    /** How likely this weather is to come next, given the season and climate mix (fractions per climate). */
    public float weightFor(@javax.annotation.Nullable Season season, float[] climateMix) {
        if (weight <= 0 || season != null && !seasons.isEmpty() && !seasons.contains(season)) {
            return 0.0F;
        }
        float affinity = 0.0F;
        for (Climate c : Climate.values()) {
            float a = climates.isEmpty() ? base.affinity(c) : climates.contains(c) ? 1.0F : 0.0F;
            affinity += climateMix[c.ordinal()] * a;
        }
        return weight * affinity;
    }

    public int randomDuration(RandomSource random) {
        return minDuration + random.nextInt(Math.max(1, maxDuration - minDuration + 1));
    }

    public MutableComponent displayName() {
        return name.copy();
    }
}
