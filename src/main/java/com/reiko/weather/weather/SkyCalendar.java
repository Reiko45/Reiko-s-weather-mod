package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

import java.util.Locale;

/**
 * The sky's calendar: how long the days are through the year, and when eclipses and comets come. Everything here
 * is worked out from the day number alone, so the server's warnings and what every client draws always agree.
 */
public final class SkyCalendar {
    /** How far the share of the day the sun is up swings either side of a half (60% at midsummer, 40% at midwinter). */
    private static final float DAY_LENGTH_SWING = 0.1F;

    // ------------------------------------------------------------------ the length of the day

    /** The share of the day the sun is up (0.5 at the equinoxes, more in summer, less in winter). */
    public static float daylightFraction(Seasons.Info info, long dayTime) {
        if (info.season() == null || info.daysPerSeason() <= 0) {
            return 0.5F;
        }
        float year = (info.season().ordinal() + (info.dayOfSeason() + Math.floorMod(dayTime, 24000L) / 24000.0F) / info.daysPerSeason()) / 4.0F;
        // longest at midsummer (the middle of the second season), shortest at midwinter
        return 0.5F + DAY_LENGTH_SWING * Mth.cos((year - 0.375F) * Mth.TWO_PI);
    }

    private static double timeDebt;

    /**
     * Every tick: in summer the clock runs a little slow while the sun is up and fast at night, so the days are
     * long and the nights short (the other way round in winter). A whole day still takes the usual twenty minutes,
     * so noon and midnight stay where they are and the moon keeps to its phases.
     */
    static void tickDayLength(ServerLevel level, Seasons.Info info) {
        if (!WeatherConfig.SEASONAL_DAY_LENGTH.get() || info.season() == null || !level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
            timeDebt = 0.0;
            return;
        }
        long time = level.getDayTime();
        float sunUp = daylightFraction(info, time);
        boolean day = Math.floorMod(time, 24000L) < 12000L;
        double rate = day ? 0.5 / sunUp : 0.5 / (1.0 - sunUp);
        timeDebt += rate - 1.0;
        int step = (int) timeDebt;
        if (step != 0) {
            level.setDayTime(time + step);
            timeDebt -= step;
        }
    }

    /** How many real minutes the sun is up today. */
    public static int daylightMinutes(Seasons.Info info, long dayTime) {
        return Math.round(daylightFraction(info, dayTime) * 20.0F);
    }

    // ------------------------------------------------------------------ eclipses

    public enum Eclipse {
        NONE, TOTAL, ANNULAR, PARTIAL, LUNAR, PARTIAL_LUNAR;

        public boolean solar() {
            return this == TOTAL || this == ANNULAR || this == PARTIAL;
        }

        public boolean lunar() {
            return this == LUNAR || this == PARTIAL_LUNAR;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Ticks from the middle of an eclipse to either end of it. */
    public static final float SOLAR_HALF = 1500.0F, LUNAR_HALF = 2000.0F;
    /** The middle part of a total eclipse when the sun is wholly hidden, as a share of the half-length. */
    public static final float TOTALITY = 0.14F;

    /**
     * Solar eclipses fall at new moon (moon phase 4) and lunar ones at full moon (phase 0), as they must: the moon
     * has to be between us and the sun, or in the Earth's shadow. About one new moon in four brings one somewhere
     * in the sky, and a lunar eclipse never lands on a blood moon night.
     */
    public static Eclipse eclipseOn(long day) {
        if (day < 8) return Eclipse.NONE;
        int phase = (int) Math.floorMod(day, 8L);
        int h = Math.floorMod(Mth.murmurHash3Mixer((int) day * 97 + 13), 1000);
        if (phase == 4) {
            return h < 110 ? Eclipse.TOTAL : h < 190 ? Eclipse.ANNULAR : h < 290 ? Eclipse.PARTIAL : Eclipse.NONE;
        }
        if (phase == 0 && !SkyPhenomena.isBloodMoonNight(day * 24000L)) {
            return h < 150 ? Eclipse.LUNAR : h < 270 ? Eclipse.PARTIAL_LUNAR : Eclipse.NONE;
        }
        return Eclipse.NONE;
    }

    public static boolean eclipsesOn() {
        return WeatherConfig.SPEC.isLoaded() && WeatherConfig.ECLIPSES.get();
    }

    /** Today's eclipse in this level (only ever in the overworld). */
    public static Eclipse eclipseToday(Level level) {
        if (!eclipsesOn() || level.dimension() != Level.OVERWORLD) return Eclipse.NONE;
        return eclipseOn(level.getDayTime() / 24000L);
    }

    /** -1 at first contact, 0 at greatest eclipse, 1 at last contact; outside that range when there's no eclipse on. */
    public static float eclipseProgress(Eclipse eclipse, float timeOfDay) {
        if (eclipse.solar()) return (timeOfDay - 6000.0F) / SOLAR_HALF;
        if (eclipse.lunar()) return (timeOfDay - 18000.0F) / LUNAR_HALF;
        return 9.0F;
    }

    /** The first day after {@code from} (up to 40 days ahead) with an eclipse, or -1. */
    public static long nextEclipse(long from) {
        for (long day = from; day < from + 40; day++) {
            if (eclipseOn(day) != Eclipse.NONE) return day;
        }
        return -1;
    }

    /** Whether the sun is wholly hidden right now (for the "totality" advancement). */
    public static boolean totalityNow(Level level) {
        Eclipse e = eclipseToday(level);
        return e == Eclipse.TOTAL && Math.abs(eclipseProgress(e, Math.floorMod(level.getDayTime(), 24000L))) < TOTALITY;
    }

    // ------------------------------------------------------------------ comets

    /** Comet nights last this long, brightest in the middle. */
    public static final int COMET_NIGHTS = 7;
    private static final int COMET_WINDOW = 48;

    /**
     * Once in a while (in about a third of 48-day stretches) a bright comet hangs low in the twilight for a week,
     * after sunset or before dawn depending on the comet. Returns 0-1, how bright it is on this day's night.
     */
    public static float cometBrightness(long day) {
        if (!WeatherConfig.SPEC.isLoaded() || !WeatherConfig.COMETS.get() || day < 0) return 0.0F;
        long window = Math.floorDiv(day, COMET_WINDOW);
        int h = Mth.murmurHash3Mixer((int) window * 131 + 29);
        if (Math.floorMod(h, 100) >= 34) return 0.0F;
        long start = window * COMET_WINDOW + Math.floorMod(h >>> 8, COMET_WINDOW - COMET_NIGHTS);
        long night = day - start;
        if (night < 0 || night >= COMET_NIGHTS) return 0.0F;
        return Mth.sin((night + 0.5F) / COMET_NIGHTS * Mth.PI);
    }

    /** A comet's look, the same for every night it's up: {evening (1) or morning (0), elongation, north offset, tail length, curve}. */
    public static float[] cometShape(long day) {
        long window = Math.floorDiv(day, COMET_WINDOW);
        int h = Mth.murmurHash3Mixer((int) window * 733 + 3);
        float a = (h & 255) / 255.0F, b = (h >>> 8 & 255) / 255.0F, c = (h >>> 16 & 255) / 255.0F, d = (h >>> 24 & 255) / 255.0F;
        return new float[]{a < 0.6F ? 1.0F : 0.0F, 34.0F + 18.0F * b, -14.0F + 28.0F * c, 16.0F + 16.0F * d, (b - 0.5F) * 0.5F};
    }

    private SkyCalendar() {}
}
