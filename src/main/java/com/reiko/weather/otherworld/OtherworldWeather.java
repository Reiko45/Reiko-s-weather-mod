package com.reiko.weather.otherworld;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.network.OtherworldPayload;
import com.reiko.weather.weather.Wind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;

/** Runs the weather cycle in the Nether and the End. */
public final class OtherworldWeather {
    public static final class State extends SavedData {
        public static final Factory<State> FACTORY = new Factory<>(State::new, State::load);
        public OtherworldType type = OtherworldType.CALM;
        public int remaining = 2400;
        public int elapsed;

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putString("type", type.id());
            tag.putInt("remaining", remaining);
            tag.putInt("elapsed", elapsed);
            return tag;
        }

        static State load(CompoundTag tag, HolderLookup.Provider registries) {
            State s = new State();
            OtherworldType t = OtherworldType.byId(tag.getString("type"));
            s.type = t != null ? t : OtherworldType.CALM;
            s.remaining = tag.getInt("remaining");
            s.elapsed = tag.getInt("elapsed");
            return s;
        }
    }

    public static boolean handles(Level level) {
        return level.dimension() == Level.NETHER || level.dimension() == Level.END;
    }

    private static boolean enabled() {
        return WeatherConfig.SPEC.isLoaded() && WeatherConfig.OTHERWORLD_WEATHER.get();
    }

    public static State state(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(State.FACTORY, "reikos_weather_otherworld");
    }

    @Nullable
    public static OtherworldType active(Level level) {
        if (!(level instanceof ServerLevel serverLevel) || !handles(level) || !enabled()) {
            return null;
        }
        return state(serverLevel).type;
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !handles(level) || !enabled()) {
            return;
        }
        State state = state(level);
        state.elapsed++;
        if (level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE) && --state.remaining <= 0) {
            set(level, state, OtherworldType.pick(level, state.type, level.random), -1);
        }
        if (level.getGameTime() % 200L == 0L) {
            sync(level, state);
            state.setDirty();
        }
        if (level.getGameTime() % 20L == 0L && state.elapsed > 300 && (state.type == OtherworldType.ASH_STORM || state.type == OtherworldType.VOID_STORM)) {
            for (ServerPlayer player : level.players()) {
                boolean outside = level.dimension() == Level.NETHER ? openAbove(level, player.blockPosition().above())
                        : com.reiko.weather.weather.Wind.isExposed(level, player.blockPosition().above());
                if (!player.isSpectator() && outside) {
                    com.reiko.weather.weather.WeatherAdvancements.award(player, state.type == OtherworldType.ASH_STORM ? "ash_storm" : "void_storm");
                }
            }
        }
        if (state.type == OtherworldType.FIRE_RAIN && state.elapsed > 200) {
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator() && level.random.nextFloat() < 0.04F) {
                    igniteNear(level, player);
                }
            }
        }
    }

    public static void set(ServerLevel level, State state, OtherworldType type, int duration) {
        state.type = type;
        state.elapsed = 0;
        state.remaining = duration > 0 ? duration : type.minDuration + level.random.nextInt(type.maxDuration - type.minDuration + 1);
        state.setDirty();
        sync(level, state);
    }

    /** Nothing overhead for at least 10 blocks (the Nether has no sky to check against). */
    public static boolean openAbove(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int i = 1; i <= 10; i++) {
            p.move(Direction.UP);
            if (!level.getBlockState(p).isAir()) return false;
        }
        return true;
    }

    private static void igniteNear(ServerLevel level, ServerPlayer player) {
        if (!WeatherConfig.FIRE_RAIN_IGNITES.get() || !level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        int x = player.getBlockX() + level.random.nextInt(49) - 24;
        int z = player.getBlockZ() + level.random.nextInt(49) - 24;
        if (!level.isLoaded(new BlockPos(x, 0, z))) return;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, player.getBlockY() + 8, z);
        for (int i = 0; i < 18; i++, p.move(Direction.DOWN)) {
            BlockState state = level.getBlockState(p);
            if (state.isAir()) continue;
            BlockPos above = p.above();
            if (state.isFlammable(level, p, Direction.UP) && openAbove(level, p)) {
                level.setBlockAndUpdate(above, BaseFireBlock.getState(level, above));
            }
            return;
        }
    }

    /** Fire rain sets exposed creatures alight; void storm gusts shove mobs and items (players move client-side). */
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide || !handles(level) || entity.tickCount % 10 != 0) {
            return;
        }
        OtherworldType type = active(level);
        if (type == OtherworldType.FIRE_RAIN && entity instanceof LivingEntity living && !living.fireImmune()
                && !(living instanceof Player p && (p.isSpectator() || p.isCreative()))
                && openAbove(level, BlockPos.containing(living.getEyePosition())) && level.random.nextFloat() < 0.08F) {
            living.igniteForSeconds(2.0F);
        }
        if (type == OtherworldType.VOID_STORM && !(entity instanceof Player) && WeatherConfig.VOID_STORM_GUSTS.get()
                && Wind.isExposed(level, BlockPos.containing(entity.getEyePosition()))) {
            float gust = voidGust(level.getGameTime());
            for (int i = 0; i < 10; i++) {
                Wind.push(entity, outward(entity.getX(), entity.getZ()), gust, level.getGameTime());
            }
        }
    }

    /** Direction pointing away from the End's main island (toward the void). */
    public static float outward(double x, double z) {
        return (float) Mth.atan2(z, x);
    }

    /**
     * Gusty pulses: a breeze most of the time, then every 15 seconds a 3-second shove toward the void.
     * Each gust has its own strength.
     */
    public static float voidGust(long time) {
        long cycle = Math.floorDiv(time, 300L);
        float phase = Math.floorMod(time, 300L) / 300.0F;
        if (phase < 0.8F) {
            return 0.3F;
        }
        float strength = 0.75F + 0.45F * ((Mth.murmurHash3Mixer((int) cycle) >>> 8 & 255) / 255.0F);
        return 0.3F + Mth.sin((phase - 0.8F) / 0.2F * Mth.PI) * (strength - 0.3F) * 1.15F;
    }

    public static void sync(ServerLevel level, State state) {
        PacketDistributor.sendToPlayersInDimension(level, new OtherworldPayload(state.type.ordinal(), state.elapsed));
    }

    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        syncTo(event.getEntity());
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        syncTo(event.getEntity());
    }

    private static void syncTo(Player player) {
        if (player instanceof ServerPlayer serverPlayer && handles(serverPlayer.level()) && enabled()) {
            State state = state(serverPlayer.serverLevel());
            PacketDistributor.sendToPlayer(serverPlayer, new OtherworldPayload(state.type.ordinal(), state.elapsed));
        } else if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new OtherworldPayload(OtherworldType.CALM.ordinal(), 0));
        }
    }

    private OtherworldWeather() {}
}
