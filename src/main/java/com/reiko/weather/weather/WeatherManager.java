package com.reiko.weather.weather;

import com.reiko.weather.ReikosWeather;
import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.network.WeatherSyncPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;

/**
 * Drives the weather cycle on the server. Our state is authoritative and mirrored onto vanilla's
 * rain/thunder flags; if something else changes vanilla weather (the /weather command, sleeping)
 * we adopt the change instead of fighting it.
 */
public final class WeatherManager {
    private static final int FORECAST_LENGTH = 3;
    private static final int VANILLA_TIMER_BUFFER = 200;

    @Nullable
    private static WeatherState cachedState;
    @Nullable
    private static ServerLevel cachedLevel;

    /** The weather system only runs in the overworld, which owns vanilla's weather. */
    public static boolean handles(Level level) {
        return level.dimension() == Level.OVERWORLD;
    }

    public static boolean enabled() {
        return WeatherConfig.ENABLED.get();
    }

    public static WeatherState state(ServerLevel level) {
        if (cachedLevel == level && cachedState != null) {
            return cachedState;
        }
        WeatherState state = level.getDataStorage().computeIfAbsent(WeatherState.FACTORY, WeatherState.FILE_ID);
        cachedLevel = level;
        cachedState = state;
        return state;
    }

    /** The active state for a level, or null if the system isn't running there. */
    @Nullable
    public static WeatherState activeState(Level level) {
        if (!(level instanceof ServerLevel serverLevel) || !handles(level) || !enabled()) {
            return null;
        }
        WeatherState state = state(serverLevel);
        return state.initialized ? state : null;
    }

    // ------------------------------------------------------------------ events

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && handles(level) && enabled()) {
            tick(level);
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncTo(player);
        }
    }

    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncTo(player);
        }
    }

    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncTo(player);
        }
    }

    public static void onSleepFinished(SleepFinishedTimeEvent event) {
        if (event.getLevel() instanceof ServerLevel level && handles(level) && enabled()
                && WeatherConfig.SLEEP_CLEARS_WEATHER.get()
                && level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)) {
            WeatherState state = state(level);
            if (state.type != WeatherType.CLEAR) {
                setWeather(level, state, WeatherType.CLEAR, scaledDuration(WeatherType.CLEAR, level.random));
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        cachedLevel = null;
        cachedState = null;
    }

    // ------------------------------------------------------------------ cycle

    private static void tick(ServerLevel level) {
        WeatherState state = state(level);
        if (!state.initialized) {
            initialize(level, state);
        }
        adoptVanillaChanges(level, state);

        state.elapsed++;
        if (level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)) {
            state.remaining--;
            if (state.remaining <= 0) {
                advance(level, state);
            }
        }
        if (state.rainbowTicks > 0) {
            state.rainbowTicks--;
        }
        driftWind(level.random, state);

        if (level.getGameTime() % 100L == 0L) {
            sync(level, state);
            state.setDirty();
        }
        WeatherHazards.tickLevel(level, state);
    }

    private static void initialize(ServerLevel level, WeatherState state) {
        ServerLevelData data = levelData(level);
        WeatherType type;
        int duration;
        if (data.isRaining()) {
            type = data.isThundering() ? WeatherType.THUNDERSTORM : WeatherType.RAIN;
            duration = data.getRainTime();
        } else {
            type = WeatherType.CLEAR;
            duration = data.getClearWeatherTime();
        }
        if (duration <= 0) {
            duration = scaledDuration(type, level.random);
        }
        state.initialized = true;
        state.windAngle = level.random.nextFloat() * Mth.TWO_PI;
        state.forecast.clear();
        setWeather(level, state, type, duration);
        refillForecast(level, state);
        ReikosWeather.LOGGER.info("Weather system initialized with {} for {} ticks", type.id(), duration);
    }

    /** Detects vanilla weather changes we didn't make (e.g. /weather) and adopts them. */
    private static void adoptVanillaChanges(ServerLevel level, WeatherState state) {
        ServerLevelData data = levelData(level);
        boolean raining = data.isRaining();
        boolean thundering = data.isThundering();
        if (raining == state.type.vanillaRain && thundering == state.type.vanillaThunder) {
            return;
        }
        WeatherType adopted;
        int duration;
        if (raining) {
            adopted = thundering ? WeatherType.THUNDERSTORM : WeatherType.RAIN;
            duration = data.getRainTime();
        } else {
            adopted = WeatherType.CLEAR;
            duration = data.getClearWeatherTime();
        }
        if (duration <= 0) {
            duration = scaledDuration(adopted, level.random);
        }
        setWeather(level, state, adopted, duration);
    }

    private static void advance(ServerLevel level, WeatherState state) {
        WeatherState.Forecast next = state.forecast.isEmpty() ? pick(level, state.type) : state.forecast.remove(0);
        setWeather(level, state, next.type(), next.duration());
        refillForecast(level, state);
    }

    /** Skips straight to the next forecast weather. */
    public static void skip(ServerLevel level) {
        advance(level, state(level));
    }

    public static void setWeather(ServerLevel level, WeatherState state, WeatherType type, int duration) {
        WeatherType previous = state.type;
        state.type = type;
        state.remaining = Math.max(1, duration);
        state.elapsed = 0;
        if (type.vanillaRain) {
            state.rainbowTicks = 0;
        } else if (previous.vanillaRain && previous != WeatherType.SNOW && previous != WeatherType.BLIZZARD) {
            state.rainbowTicks = 3600;
        }
        applyToVanilla(level, state);
        state.setDirty();
        sync(level, state);
    }

    /**
     * Mirrors our weather onto vanilla flags with timers longer than ours, so vanilla's own cycle never
     * flips the weather before we do.
     */
    private static void applyToVanilla(ServerLevel level, WeatherState state) {
        int timer = state.remaining + VANILLA_TIMER_BUFFER;
        if (state.type.vanillaRain) {
            level.setWeatherParameters(0, timer, true, state.type.vanillaThunder);
        } else {
            level.setWeatherParameters(timer, 0, false, false);
        }
    }

    private static void refillForecast(ServerLevel level, WeatherState state) {
        while (state.forecast.size() < FORECAST_LENGTH) {
            WeatherType last = state.forecast.isEmpty() ? state.type : state.forecast.get(state.forecast.size() - 1).type();
            state.forecast.add(pick(level, last));
        }
    }

    /** Weighted random choice of the weather following {@code after}, biased by the players' climates. */
    private static WeatherState.Forecast pick(ServerLevel level, WeatherType after) {
        float[] mix = climateMix(level);
        WeatherType[] types = WeatherType.values();
        float[] weights = new float[types.length];
        float total = 0.0F;
        for (int i = 0; i < types.length; i++) {
            WeatherType t = types[i];
            float w = WeatherConfig.weight(t);
            float affinity = 0.0F;
            for (Climate c : Climate.values()) {
                affinity += mix[c.ordinal()] * t.affinity(c);
            }
            w *= affinity;
            if (t == after) w *= 0.15F;
            if (t.isCalm() && !after.isCalm()) w *= 2.0F;
            // storms tend to build out of rain, blizzards out of snow
            if ((after == WeatherType.RAIN || after == WeatherType.HEAVY_RAIN)
                    && (t == WeatherType.THUNDERSTORM || t == WeatherType.SUPERCELL || t == WeatherType.HURRICANE || t == WeatherType.HEAVY_RAIN)) {
                w *= 2.0F;
            }
            if (after == WeatherType.SNOW && t == WeatherType.BLIZZARD) w *= 2.0F;
            if (after == WeatherType.OVERCAST && t.vanillaRain) w *= 1.5F;
            weights[i] = w;
            total += w;
        }
        WeatherType chosen = WeatherType.CLEAR;
        if (total > 0.0F) {
            float roll = level.random.nextFloat() * total;
            for (int i = 0; i < types.length; i++) {
                roll -= weights[i];
                if (roll <= 0.0F) {
                    chosen = types[i];
                    break;
                }
            }
        }
        return new WeatherState.Forecast(chosen, scaledDuration(chosen, level.random));
    }

    /** Fraction of players in each climate, or a temperate-leaning default when nobody is online. */
    private static float[] climateMix(ServerLevel level) {
        float[] mix = new float[Climate.values().length];
        int count = 0;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()) {
                mix[Climate.at(level, player.blockPosition()).ordinal()]++;
                count++;
            }
        }
        if (count == 0) {
            mix[Climate.COLD.ordinal()] = 0.2F;
            mix[Climate.TEMPERATE.ordinal()] = 0.6F;
            mix[Climate.ARID.ordinal()] = 0.2F;
            return mix;
        }
        for (int i = 0; i < mix.length; i++) {
            // keep some variety even if everyone stands in one biome
            mix[i] = 0.1F + 0.9F * (mix[i] / count);
        }
        return mix;
    }

    private static int scaledDuration(WeatherType type, RandomSource random) {
        return Math.max(200, (int) (type.randomDuration(random) * WeatherConfig.DURATION_MULTIPLIER.get()));
    }

    private static void driftWind(RandomSource random, WeatherState state) {
        state.windDrift = state.windDrift * 0.995F + (random.nextFloat() - 0.5F) * 0.00008F;
        state.windDrift = Mth.clamp(state.windDrift, -0.004F, 0.004F);
        state.windAngle = Mth.wrapDegrees(Mth.RAD_TO_DEG * (state.windAngle + state.windDrift)) * Mth.DEG_TO_RAD;
    }

    private static ServerLevelData levelData(ServerLevel level) {
        return level.getServer().getWorldData().overworldData();
    }

    // ------------------------------------------------------------------ sync

    private static WeatherSyncPayload payload(@Nullable WeatherState state) {
        if (state == null || !state.initialized || !enabled()) {
            return new WeatherSyncPayload(-1, 0, 0.0F, 0);
        }
        return new WeatherSyncPayload(state.type.ordinal(), state.elapsed, state.windAngle, state.rainbowTicks);
    }

    public static void sync(ServerLevel level, WeatherState state) {
        PacketDistributor.sendToPlayersInDimension(level, payload(state));
    }

    private static void syncTo(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        PacketDistributor.sendToPlayer(player, payload(enabled() ? state(overworld) : null));
    }

    private WeatherManager() {}
}
