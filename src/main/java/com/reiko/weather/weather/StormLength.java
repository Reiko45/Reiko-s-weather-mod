package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * How long a storm lasts. Real storms come in every length: a shower that's over in a couple of minutes, an
 * ordinary afternoon of rain, a long wet evening, and now and then a grey, soaking day when it simply doesn't
 * stop. Each storm rolls one of four lengths, with odds that suit the kind of weather (a steady rain or snow
 * can set in all day; a thunderstorm only rarely rumbles on that long; hail and sun showers are always brief).
 *
 * <p>A long storm isn't the same all the way through either: it eases off into lulls and comes back harder.
 * {@link #pulse} gives that rhythm from the storm's elapsed time and total length, both of which the client
 * is sent, so the server's gameplay and the client's rain agree.
 */
public final class StormLength {
    public enum Kind {
        BRIEF, NORMAL, LONG, ALL_DAY;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** "All day": most of a Minecraft day. */
    public static final int ALL_DAY_MIN = 18000;
    public static final int ALL_DAY_MAX = 26000;

    private enum Habit {
        /** Steady rain and snow: anything from a passing shower to an all-day soaking. */
        PERSISTENT(0.32F, 0.44F, 0.17F, 0.07F),
        /** Thunderstorms: often brief, sometimes long, rarely rumbling on all day. */
        CONVECTIVE(0.38F, 0.42F, 0.16F, 0.04F),
        /** Big, violent systems: they come and go, but never sit all day. */
        VIOLENT(0.25F, 0.55F, 0.20F, 0.0F),
        /** Hail, graupel, sun showers and the like: over quickly. */
        FLEETING(0.45F, 0.55F, 0.0F, 0.0F),
        /** Not a storm: its usual time. */
        NONE(0.0F, 1.0F, 0.0F, 0.0F);

        final float brief, normal, longer, allDay;

        Habit(float brief, float normal, float longer, float allDay) {
            this.brief = brief;
            this.normal = normal;
            this.longer = longer;
            this.allDay = allDay;
        }
    }

    private static Habit habit(WeatherType type) {
        return switch (type) {
            case DRIZZLE, RAIN, HEAVY_RAIN, SNOW, SLEET, FREEZING_DRIZZLE, ICE_STORM, BLIZZARD, WARM_FRONT, THUNDERSNOW -> Habit.PERSISTENT;
            case THUNDERSTORM -> Habit.CONVECTIVE;
            case SUPERCELL, DERECHO, HURRICANE, COLD_FRONT, POLAR_LOW -> Habit.VIOLENT;
            case HAIL, GRAUPEL, SUN_SHOWER, HABOOB, HEAT_BURST -> Habit.FLEETING;
            default -> Habit.NONE;
        };
    }

    public static boolean varied() {
        return WeatherConfig.VARIED_STORM_LENGTHS.get();
    }

    /** Whether this weather's length varies at all (only storms do). */
    public static boolean applies(WeatherType type) {
        return habit(type) != Habit.NONE;
    }

    public static Kind roll(WeatherType type, RandomSource random) {
        Habit h = habit(type);
        if (h == Habit.NONE || !varied()) return Kind.NORMAL;
        float r = random.nextFloat();
        if ((r -= h.brief) < 0) return Kind.BRIEF;
        if ((r -= h.normal) < 0) return Kind.NORMAL;
        if ((r -= h.longer) < 0) return Kind.LONG;
        return h.allDay > 0 ? Kind.ALL_DAY : Kind.NORMAL;
    }

    /** A length for this weather, before the config's duration multiplier. */
    public static int duration(WeatherType type, RandomSource random) {
        return duration(type, roll(type, random), random);
    }

    public static int duration(WeatherType type, Kind kind, RandomSource random) {
        int d = switch (kind) {
            case BRIEF -> Math.round(Mth.lerp(random.nextFloat(), type.minDuration * 0.25F, type.minDuration * 0.6F));
            case NORMAL -> type.randomDuration(random);
            case LONG -> Math.round(Mth.lerp(random.nextFloat(), Math.min(type.maxDuration * 1.3F, 14000.0F), Math.min(type.maxDuration * 2.2F, 16000.0F)));
            case ALL_DAY -> ALL_DAY_MIN + random.nextInt(ALL_DAY_MAX - ALL_DAY_MIN + 1);
        };
        return Math.max(600, d);
    }

    /** Which length a storm of this many ticks (after the duration multiplier) counts as. */
    public static Kind classify(WeatherType type, int ticks) {
        if (habit(type) == Habit.NONE) return Kind.NORMAL;
        float d = ticks / (float) Math.max(0.05, WeatherConfig.DURATION_MULTIPLIER.get());
        if (d >= ALL_DAY_MIN * 0.95F) return Kind.ALL_DAY;
        if (d >= type.maxDuration * 1.25F) return Kind.LONG;
        if (d <= type.minDuration * 0.65F) return Kind.BRIEF;
        return Kind.NORMAL;
    }

    /**
     * How hard a long storm is coming down right now: 1 at full strength, easing to about 0.45 in a lull. Ordinary
     * and brief storms are steady (1). Worked out from the storm's own elapsed time and total length, so the server
     * and every client get the same answer.
     */
    public static float pulse(WeatherType type, int elapsed, int remaining) {
        Habit h = habit(type);
        if (h == Habit.NONE || h == Habit.FLEETING || !varied()) return 1.0F;
        int total = elapsed + remaining;
        Kind kind = classify(type, total);
        if (kind != Kind.LONG && kind != Kind.ALL_DAY) return 1.0F;
        // each storm gets its own rhythm, picked from its length
        int seed = total * 0x9E3779B1;
        float p1 = (seed >>> 8 & 1023) / 1023.0F * Mth.TWO_PI;
        float p2 = (seed >>> 18 & 1023) / 1023.0F * Mth.TWO_PI;
        float p3 = (seed & 255) / 255.0F * Mth.TWO_PI;
        float t = elapsed;
        float v = 0.5F * Mth.sin(t * Mth.TWO_PI / 4100.0F + p1) + 0.3F * Mth.sin(t * Mth.TWO_PI / 2300.0F + p2)
                + 0.2F * Mth.sin(t * Mth.TWO_PI / 7300.0F + p3);
        float strength = Mth.clamp(0.74F + 0.30F * v, 0.45F, 1.0F);
        // full strength while it sets in, and while it ends
        float edge = Mth.clamp(Math.min(elapsed, remaining) / 1500.0F, 0.0F, 1.0F);
        return Mth.lerp(edge, 1.0F, strength);
    }

    /** A storm's profile eased off by a lull: lighter rain or snow, less thunder, a little less wind. */
    public static WeatherProfile lulled(WeatherProfile p, float strength) {
        if (strength >= 0.999F) return p;
        WeatherProfile.Builder b = WeatherProfile.Builder.from(p);
        b.rain *= strength;
        b.thunder *= strength;
        b.lightning *= strength;
        b.wind *= 0.6F + 0.4F * strength;
        b.overcast *= 0.85F + 0.15F * strength;
        for (int i = 0; i < b.precip.length; i++) {
            b.precip[i] *= strength;
        }
        return b.build();
    }

    private StormLength() {}
}
