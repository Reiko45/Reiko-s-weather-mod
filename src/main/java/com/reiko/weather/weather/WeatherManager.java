package com.reiko.weather.weather;

import com.reiko.weather.ReikosWeather;
import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.network.WeatherSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Drives the weather on the server. There are two layers:
 * <ul>
 *   <li>the <b>background</b> weather, which covers the whole overworld, follows a forecast and is
 *   mirrored onto vanilla's rain/thunder flags (we adopt /weather and sleeping instead of fighting them);</li>
 *   <li><b>storm cells</b>, regional storms that form on the horizon and drift across the map with the wind.</li>
 * </ul>
 */
public final class WeatherManager {
    private static final int FORECAST_LENGTH = 3;
    private static final int VANILLA_TIMER_BUFFER = 200;

    @Nullable
    private static WeatherState cachedState;
    @Nullable
    private static ServerLevel cachedLevel;
    private static Seasons.Info seasonInfo = Seasons.Info.NONE;

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

    /** The weather experienced at a position (background plus any storm cell overhead). */
    @Nullable
    public static LocalWeather local(Level level, BlockPos pos) {
        WeatherState state = activeState(level);
        if (state == null) {
            return null;
        }
        Climate climate = Climate.at(level, pos);
        return Microclimate.apply(level, climate, pos.getX(), pos.getZ(), state.windAngle,
                LocalWeather.resolve(state.type, CustomWeathers.of(state), state.intensity(), state.cells, climate, pos.getX() + 0.5, pos.getZ() + 0.5));
    }

    public static Seasons.Info seasonInfo() {
        return seasonInfo;
    }

    /** Recomputes the season straight away (after /rweather season or /time) and tells the clients. */
    public static void refreshSeason(ServerLevel level, WeatherState state) {
        seasonInfo = Seasons.compute(level, state);
        sync(level, state);
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
        seasonInfo = Seasons.Info.NONE;
        Seasons.setTemperatureShift(0.0F);
        Fronts.reset();
        FireTracker.reset();
        LightningWarnings.reset();
        HailGlass.reset();
        com.reiko.weather.landform.Arroyo.reset();
        com.reiko.weather.volcano.MassFlows.reset();
        Highlands.reset();
        Seas.reset();
        Downdrafts.reset();
        Earthquakes.reset();
    }

    // ------------------------------------------------------------------ cycle

    private static void tick(ServerLevel level) {
        WeatherState state = state(level);
        if (!state.initialized) {
            initialize(level, state);
        }
        adoptVanillaChanges(level, state);
        if (level.getGameTime() % 20L == 0L) {
            seasonInfo = Seasons.compute(level, state);
            Monsoon.setServerActive(Monsoon.enabled() && seasonInfo.season() == Season.SUMMER);
        }
        SkyCalendar.tickDayLength(level, seasonInfo);

        state.elapsed++;
        boolean cycle = level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE);
        if (cycle) {
            state.remaining--;
            if (state.remaining <= 0) {
                advance(level, state);
            }
        }
        if (state.rainbowTicks > 0) {
            state.rainbowTicks--;
        }
        driftWind(level.random, state);
        tickCells(level, state, cycle);
        Drought.tick(level, state, cycle);
        Earthquakes.tick(level, state);
        if (cycle && level.getGameTime() % 100L == 0L) {
            Convection.tick(level, state);
            SeaFog.tick(level, state);
            FireTracker.tick(level, state);
            DustHaze.tick(level, state);
            Downdrafts.tick(level, state);
            AtmosphericRivers.tick(level, state);
        }

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
        // new worlds start in mid-spring rather than at the cold tail end of winter
        state.seasonOffsetDays = WeatherConfig.DAYS_PER_SEASON.get() / 2;
        state.forecast.clear();
        seasonInfo = Seasons.compute(level, state);
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
        setWeather(level, state, next.type(), next.duration(), CustomWeathers.get(next.custom()) != null ? next.custom() : null);
        refillForecast(level, state);
    }

    /** Skips straight to the next forecast weather. */
    public static void skip(ServerLevel level) {
        advance(level, state(level));
    }

    public static void setWeather(ServerLevel level, WeatherState state, WeatherType type, int duration) {
        setWeather(level, state, type, duration, null);
    }

    /** Sets the background weather; {@code custom} names a datapack weather built on {@code type}. */
    public static void setWeather(ServerLevel level, WeatherState state, WeatherType type, int duration, @Nullable String custom) {
        WeatherType previous = state.type;
        state.type = type;
        state.custom = custom;
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

    /** The overworld, while the server's running (for the year's climate leaning when picking weather). */
    @Nullable
    private static ServerLevel overworld;

    private static float seasonMultiplier(WeatherType type) {
        Season season = seasonInfo.season();
        return (season == null ? 1.0F : season.weatherMultiplier(type)) * ClimateYears.weatherMultiplier(overworld, type);
    }

    /** Weighted random choice of the weather following {@code after}, biased by climate and season. */
    private static WeatherState.Forecast pick(ServerLevel level, WeatherType after) {
        overworld = level;
        float[] mix = climateMix(level);
        boolean regional = WeatherConfig.REGIONAL_STORMS.get();
        float monsoon = Monsoon.enabled() && seasonInfo.season() == Season.SUMMER ? tropicalFraction(level) : 0.0F;
        float drought = Drought.severity(state(level));
        WeatherType[] types = WeatherType.values();
        float[] weights = new float[types.length];
        float total = 0.0F;
        for (int i = 0; i < types.length; i++) {
            WeatherType t = types[i];
            if (t.cellOnly() || regional && t == WeatherType.HURRICANE) {
                continue; // fronts and the like only ever come as storm cells (and so do hurricanes, off the sea)
            }
            float w = WeatherConfig.weight(t) * seasonMultiplier(t);
            float affinity = 0.0F;
            for (Climate c : Climate.values()) {
                affinity += mix[c.ordinal()] * t.affinity(c);
            }
            w *= affinity;
            if (t == after) w *= 0.15F;
            if (t.isCalm() && !after.isCalm()) w *= 2.0F;
            // with regional storms on, many storms arrive as passing cells instead
            if (regional && t.isStorm() && t != WeatherType.RAIN && t != WeatherType.SNOW) w *= 0.6F;
            // storms tend to build out of rain, blizzards out of snow
            if ((after == WeatherType.RAIN || after == WeatherType.HEAVY_RAIN)
                    && (t == WeatherType.THUNDERSTORM || t == WeatherType.SUPERCELL || t == WeatherType.HURRICANE || t == WeatherType.HEAVY_RAIN)) {
                w *= 2.0F;
            }
            if (after == WeatherType.SNOW && t == WeatherType.BLIZZARD) w *= 2.0F;
            if (after == WeatherType.SLEET && t == WeatherType.ICE_STORM) w *= 2.0F;
            if (after == WeatherType.OVERCAST && t.vanillaRain) w *= 1.5F;
            if (monsoon > 0) {
                // the rainy season: downpours and storms most days
                if (t == WeatherType.RAIN || t == WeatherType.HEAVY_RAIN || t == WeatherType.THUNDERSTORM || t == WeatherType.DRIZZLE) {
                    w *= 1.0F + 3.0F * monsoon;
                } else if (t == WeatherType.CLEAR || t == WeatherType.HEATWAVE || t == WeatherType.SANDSTORM) {
                    w *= 1.0F - 0.6F * monsoon;
                }
            }
            if (drought > 0) {
                // in a drought the rain just won't come
                if (t.vanillaRain && t != WeatherType.SNOW && t != WeatherType.BLIZZARD) w *= 1.0F - 0.85F * drought;
                else if (t == WeatherType.CLEAR || t == WeatherType.HEATWAVE) w *= 1.0F + drought;
            }
            weights[i] = w;
            total += w;
        }
        // datapack weathers join the same draw
        List<CustomWeather> customs = new ArrayList<>(CustomWeathers.all());
        float[] customWeights = new float[customs.size()];
        float customTotal = 0.0F;
        for (int i = 0; i < customs.size(); i++) {
            CustomWeather c = customs.get(i);
            float w = c.weightFor(seasonInfo.season(), mix);
            if (c.base() == after) w *= 0.15F;
            customWeights[i] = w;
            customTotal += w;
        }
        if (customTotal > 0.0F && level.random.nextFloat() * (total + customTotal) < customTotal) {
            float roll = level.random.nextFloat() * customTotal;
            for (int i = 0; i < customs.size(); i++) {
                roll -= customWeights[i];
                if (roll <= 0.0F && customWeights[i] > 0.0F) {
                    CustomWeather c = customs.get(i);
                    int duration = Math.max(200, Math.round(c.randomDuration(level.random) * WeatherConfig.DURATION_MULTIPLIER.get().floatValue()));
                    return new WeatherState.Forecast(c.base(), duration, c.id().toString());
                }
            }
        }
        WeatherType chosen = weightedPick(types, weights, total, level.random, WeatherType.CLEAR);
        return new WeatherState.Forecast(chosen, scaledDuration(chosen, level.random));
    }

    private static WeatherType weightedPick(WeatherType[] types, float[] weights, float total, RandomSource random, WeatherType fallback) {
        if (total <= 0.0F) {
            return fallback;
        }
        float roll = random.nextFloat() * total;
        for (int i = 0; i < types.length; i++) {
            roll -= weights[i];
            if (roll <= 0.0F && weights[i] > 0.0F) {
                return types[i];
            }
        }
        return fallback;
    }

    /** Fraction of players in each climate, or a temperate-leaning default when nobody is online. */
    /** Fraction of players in jungles and savannas. */
    private static float tropicalFraction(ServerLevel level) {
        int count = 0, tropical = 0;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()) {
                count++;
                if (Monsoon.isTropical(level.getBiome(player.blockPosition()))) tropical++;
            }
        }
        return count == 0 ? 0.0F : tropical / (float) count;
    }

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

    // ------------------------------------------------------------------ storm cells

    private static void tickCells(ServerLevel level, WeatherState state, boolean cycle) {
        boolean changed = false;
        Vec3 wind = Wind.direction(state.windAngle);
        for (StormCell cell : state.cells) {
            // storms steer with the wind
            if (cell.steer) {
                double speed = Math.sqrt(cell.vx * cell.vx + cell.vz * cell.vz);
                cell.vx = Mth.lerp(0.002, cell.vx, wind.x * speed);
                cell.vz = Mth.lerp(0.002, cell.vz, wind.z * speed);
            }
            cell.tick();
            if (cell.type == WeatherType.HURRICANE && level.getGameTime() % 20L == 0L && Hurricanes.develop(level, cell)) {
                changed = true;
            }
            if (cell.type == WeatherType.POLAR_LOW && level.getGameTime() % 20L == 0L && Hurricanes.developPolarLow(level, cell)) {
                changed = true;
            }
        }
        Fronts.tick(level, state);
        if (state.cells.removeIf(StormCell::expired)) {
            changed = true;
        }
        double minutes = WeatherConfig.STORM_MINUTES.get();
        // follow config changes straight away rather than waiting out a longer, older cooldown
        state.cellCooldown = Math.min(state.cellCooldown, (int) (minutes * 1200 * 1.5));
        if (cycle && WeatherConfig.REGIONAL_STORMS.get() && --state.cellCooldown <= 0) {
            state.cellCooldown = (int) (minutes * 1200 * (0.5 + level.random.nextDouble()));
            if (state.cells.size() < WeatherConfig.MAX_STORM_CELLS.get() && spawnNaturalCell(level, state) != null) {
                changed = true;
            }
        }
        if (changed) {
            state.setDirty();
            sync(level, state);
        }
    }

    @Nullable
    private static StormCell spawnNaturalCell(ServerLevel level, WeatherState state) {
        List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator()).toList();
        if (players.isEmpty()) {
            return null;
        }
        ServerPlayer target = players.get(level.random.nextInt(players.size()));
        Vec3 wind = Wind.direction(state.windAngle);
        double distance = 450 + level.random.nextDouble() * 350;
        double x = target.getX() - wind.x * distance;
        double z = target.getZ() - wind.z * distance;
        Climate climate = Climate.at(level, BlockPos.containing(x, level.getSeaLevel(), z));
        float drought = Drought.severity(state);
        WeatherType[] types = WeatherType.values();
        float[] weights = new float[types.length];
        float total = 0.0F;
        for (int i = 0; i < types.length; i++) {
            WeatherType t = types[i];
            if (!t.isStorm()) continue;
            weights[i] = WeatherConfig.weight(t) * t.affinity(climate) * seasonMultiplier(t);
            if (t.vanillaRain && t != WeatherType.SNOW && t != WeatherType.BLIZZARD) weights[i] *= 1.0F - 0.8F * drought;
            total += weights[i];
        }
        WeatherType type = weightedPick(types, weights, total, level.random, null);
        if (type == null) {
            return null;
        }
        if (type == WeatherType.HURRICANE) {
            return Hurricanes.spawnOffshore(level, state, target); // they only form over the sea
        }
        if (type == WeatherType.POLAR_LOW) {
            return WeatherConfig.POLAR_LOWS.get() ? Hurricanes.spawnPolarLow(level, state, target) : null;
        }
        return spawnCell(level, state, type, x, z, -1);
    }

    /** Spawns a storm cell centred at x/z. A radius below 0 picks one that suits the storm type. */
    public static StormCell spawnCell(ServerLevel level, WeatherState state, WeatherType type, double x, double z, float radius) {
        RandomSource random = level.random;
        if (radius <= 0) {
            radius = switch (type) {
                case SUPERCELL, HAIL -> 160 + random.nextFloat() * 120;
                case HURRICANE -> 500 + random.nextFloat() * 250;
                case POLAR_LOW -> 170 + random.nextFloat() * 90;
                case SUN_SHOWER -> 70 + random.nextFloat() * 60;
                case DERECHO -> 110 + random.nextFloat() * 60;
                case COLD_FRONT -> 70 + random.nextFloat() * 30;
                case WARM_FRONT -> 170 + random.nextFloat() * 60;
                default -> 220 + random.nextFloat() * 260;
            };
        }
        boolean derecho = type == WeatherType.DERECHO || type == WeatherType.COLD_FRONT;
        // derechos and cold fronts race across the map several times faster than other storms
        double speed = derecho ? (type == WeatherType.COLD_FRONT ? 0.25 : 0.35) + random.nextDouble() * 0.15 : 0.1 + random.nextDouble() * 0.1;
        // long enough to cross ~2 km of map (a derecho keeps going for ~3.5 km, a hurricane ~3 km)
        int maxAge = (int) (((derecho ? 3500 : type == WeatherType.HURRICANE ? 3000 : 1800) + radius * 2) / speed);
        return spawnCell(level, state, type, x, z, radius, speed, maxAge);
    }

    /** Spawns a storm cell drifting downwind at {@code speed} blocks a tick, lasting {@code maxAge} ticks. */
    public static StormCell spawnCell(ServerLevel level, WeatherState state, WeatherType type, double x, double z, float radius, double speed, int maxAge) {
        Vec3 wind = Wind.direction(state.windAngle);
        return spawnCell(level, state, type, x, z, radius, wind.x * speed, wind.z * speed, maxAge, -1, true);
    }

    /**
     * Spawns a cell with a given velocity. {@code grow} is how many ticks it takes to build up (below 0 for
     * the usual), and {@code steer} whether it turns with the wind.
     */
    public static StormCell spawnCell(ServerLevel level, WeatherState state, WeatherType type, double x, double z, float radius,
                                      double vx, double vz, int maxAge, int grow, boolean steer) {
        StormCell cell = new StormCell(state.nextCellId++, type, x, z, radius, vx, vz, maxAge);
        if (grow > 0) cell.grow = grow;
        cell.steer = steer;
        if (type == WeatherType.HURRICANE) {
            Hurricanes.setUp(cell, level.random);
        } else if (type == WeatherType.POLAR_LOW) {
            cell.power = 0.45F + level.random.nextFloat() * 0.25F;
        }
        state.cells.add(cell);
        state.setDirty();
        sync(level, state);
        ReikosWeather.LOGGER.debug("Storm cell {} ({}) formed at {}, {} with radius {}", cell.id, type.id(), (int) x, (int) z, (int) radius);
        return cell;
    }

    /** Spawns a storm cell upwind of a position so it drifts over it in a few minutes. */
    public static StormCell spawnCellUpwind(ServerLevel level, WeatherState state, WeatherType type, Vec3 pos, float radius) {
        Vec3 wind = Wind.direction(state.windAngle);
        float r = radius > 0 ? radius : 300;
        double distance = r + 250;
        return spawnCell(level, state, type, pos.x - wind.x * distance, pos.z - wind.z * distance, radius);
    }

    // ------------------------------------------------------------------ sync

    private static WeatherSyncPayload payload(@Nullable WeatherState state) {
        if (state == null || !state.initialized || !enabled()) {
            return WeatherSyncPayload.unmanaged();
        }
        int[] forecast = state.forecast.stream().mapToInt(f -> f.type().ordinal()).toArray();
        List<StormCell> cells = new ArrayList<>(state.cells.size());
        for (StormCell cell : state.cells) {
            cells.add(cell.copy());
        }
        Seasons.Info season = seasonInfo;
        return new WeatherSyncPayload(state.type.ordinal(), state.elapsed, state.remaining, state.windAngle, state.rainbowTicks,
                season.season() == null ? -1 : season.season().ordinal(), season.dayOfSeason(), season.daysPerSeason(),
                season.temperatureShift(), forecast, cells, state.custom != null && CustomWeathers.of(state) != null ? state.custom : "",
                Drought.severity(state), state.bloom);
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
