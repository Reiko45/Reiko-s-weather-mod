package com.reiko.weather.weather;

import com.reiko.weather.ReikosWeather;
import com.reiko.weather.config.WeatherConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Season tracking. Uses Serene Seasons when it is installed; otherwise runs its own calendar based on
 * the overworld day count. The current season also shifts biome temperatures (see BiomeMixin), which is
 * what turns rain into snow in a temperate winter and melts it again in spring.
 */
public final class Seasons {
    /** Read from the biome temperature mixin on any thread, so it is a plain volatile float. */
    private static volatile float temperatureShift;
    /**
     * Only the game threads see seasonal temperatures. World generation runs on worker threads and keeps
     * the biome's normal temperature, so a chunk first explored in winter doesn't get snow baked into it.
     */
    @Nullable
    private static volatile Thread serverThread;
    @Nullable
    private static volatile Thread clientThread;

    private static boolean sereneChecked;
    @Nullable
    private static Method sereneGetState;
    @Nullable
    private static Method sereneGetSeason;
    @Nullable
    private static Method sereneCycleTicks, sereneSeasonDuration, sereneDayDuration;

    public record Info(@Nullable Season season, int dayOfSeason, int daysPerSeason, float temperatureShift) {
        public static final Info NONE = new Info(null, 0, 0, 0.0F);
    }

    public static float temperatureShift() {
        return temperatureShift;
    }

    /** The shift to apply for the calling thread (zero for world generation). */
    public static float temperatureShiftForCurrentThread() {
        float shift = temperatureShift;
        if (shift == 0.0F) {
            return 0.0F;
        }
        Thread current = Thread.currentThread();
        return current == serverThread || current == clientThread ? shift : 0.0F;
    }

    /** Called on the client thread from the synced value; the server computes its own in {@link #compute}. */
    public static void setTemperatureShift(float shift) {
        clientThread = Thread.currentThread();
        temperatureShift = shift;
    }

    public static boolean usingSereneSeasons() {
        return ModList.get() != null && ModList.get().isLoaded("sereneseasons");
    }

    /** Works out the season on the server and updates the temperature shift. */
    public static Info compute(ServerLevel overworld, WeatherState state) {
        serverThread = Thread.currentThread();
        Info info = calculate(overworld, state);
        temperatureShift = info.temperatureShift();
        return info;
    }

    private static Info calculate(ServerLevel overworld, WeatherState state) {
        if (!WeatherConfig.SEASONS_ENABLED.get()) {
            return Info.NONE;
        }
        if (usingSereneSeasons()) {
            // Serene Seasons already adjusts biome temperatures itself
            Info info = sereneSeason(overworld);
            return info == null ? Info.NONE : info;
        }
        int daysPerSeason = WeatherConfig.DAYS_PER_SEASON.get();
        long day = overworld.getDayTime() / 24000L + state.seasonOffsetDays;
        long yearDay = Math.floorMod(day, daysPerSeason * 4L);
        Season season = Season.values()[(int) (yearDay / daysPerSeason)];
        int dayOfSeason = (int) (yearDay % daysPerSeason);
        float shift = 0.0F;
        if (WeatherConfig.SEASONAL_TEMPERATURE.get()) {
            float drop = WeatherConfig.WINTER_TEMPERATURE_DROP.get().floatValue();
            // ease from the previous season's temperature over the first quarter of the season
            float progress = (dayOfSeason + (overworld.getDayTime() % 24000L) / 24000.0F) / daysPerSeason;
            float blend = Mth.clamp(progress * 4.0F, 0.0F, 1.0F);
            blend = blend * blend * (3.0F - 2.0F * blend);
            shift = Mth.lerp(blend, season.previous().temperatureShift(drop), season.temperatureShift(drop));
        }
        return new Info(season, dayOfSeason, daysPerSeason, shift);
    }

    /** The season (and, when available, the day within it) from Serene Seasons' calendar. */
    @Nullable
    private static Info sereneSeason(Level level) {
        try {
            if (!sereneChecked) {
                sereneChecked = true;
                Class<?> helper = Class.forName("sereneseasons.api.season.SeasonHelper");
                sereneGetState = helper.getMethod("getSeasonState", Level.class);
                Class<?> stateType = sereneGetState.getReturnType();
                sereneGetSeason = stateType.getMethod("getSeason");
                try {
                    sereneCycleTicks = stateType.getMethod("getSeasonCycleTicks");
                    sereneSeasonDuration = stateType.getMethod("getSeasonDuration");
                    sereneDayDuration = stateType.getMethod("getDayDuration");
                } catch (NoSuchMethodException e) {
                    sereneCycleTicks = null; // older API: season only
                }
            }
            if (sereneGetState == null || sereneGetSeason == null) {
                return null;
            }
            Object state = sereneGetState.invoke(null, level);
            Object seasonObj = state == null ? null : sereneGetSeason.invoke(state);
            Season season = seasonObj instanceof Enum<?> e ? Season.byId(e.name()) : null;
            if (season == null) {
                return null;
            }
            int dayOfSeason = -1, daysPerSeason = -1;
            if (sereneCycleTicks != null && sereneSeasonDuration != null && sereneDayDuration != null) {
                int ticks = (Integer) sereneCycleTicks.invoke(state);
                int seasonTicks = (Integer) sereneSeasonDuration.invoke(state);
                int dayTicks = (Integer) sereneDayDuration.invoke(state);
                if (seasonTicks > 0 && dayTicks > 0) {
                    daysPerSeason = Math.max(1, seasonTicks / dayTicks);
                    dayOfSeason = Math.min(daysPerSeason - 1, Math.floorMod(ticks, seasonTicks) / dayTicks);
                }
            }
            return new Info(season, dayOfSeason, daysPerSeason, 0.0F);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (sereneGetState != null || !sereneChecked) {
                ReikosWeather.LOGGER.warn("Could not read the season from Serene Seasons; using no seasons", e);
            }
            sereneGetState = null;
            sereneGetSeason = null;
            return null;
        }
    }

    /**
     * Sets the season by shifting the calendar. Lands just past the season's opening transition, so the
     * new season's temperatures apply straight away.
     */
    public static void setSeason(ServerLevel overworld, WeatherState state, Season season) {
        int daysPerSeason = WeatherConfig.DAYS_PER_SEASON.get();
        long day = overworld.getDayTime() / 24000L;
        long wanted = (long) season.ordinal() * daysPerSeason + (daysPerSeason + 3) / 4;
        state.seasonOffsetDays = (int) Math.floorMod(wanted - day, daysPerSeason * 4L);
        state.setDirty();
    }

    private Seasons() {}
}
