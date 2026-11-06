package com.reiko.weather.sky;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.weather.SkyPhenomena;
import com.reiko.weather.weather.WeatherAdvancements;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Some full moons rise blood red. Monsters swarm (extra ones spawn around every player out in the
 * open) and nobody can sleep the night away.
 */
public final class BloodMoon {
    private static final int MAX_NEARBY_MONSTERS = 40;
    private static long announcedDay = -1;

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD
                || level.getGameTime() % 20L != 0L || !WeatherConfig.SPEC.isLoaded()) {
            return;
        }
        if (!SkyPhenomena.bloodMoonNow(level)) {
            return;
        }
        long day = level.getDayTime() / 24000L;
        if (announcedDay != day) {
            announcedDay = day;
            for (ServerPlayer player : level.players()) {
                player.sendSystemMessage(Component.translatable("message.reikos_weather.blood_moon").withStyle(ChatFormatting.DARK_RED));
                level.playSound(null, player.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.AMBIENT, 0.25F, 0.5F);
            }
        }
        boolean spawn = level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) && level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            if (level.canSeeSky(BlockPos.containing(player.getEyePosition()))) {
                WeatherAdvancements.award(player, "blood_moon");
            }
            if (spawn && level.getGameTime() % 100L == 0L) {
                swarm(level, player);
            }
        }
    }

    /** A few extra monsters out in the dark around a player. */
    private static void swarm(ServerLevel level, ServerPlayer player) {
        int nearby = level.getEntitiesOfClass(Monster.class, new AABB(player.blockPosition()).inflate(64)).size();
        if (nearby >= MAX_NEARBY_MONSTERS) {
            return;
        }
        RandomSource random = level.random;
        for (int attempt = 0; attempt < 4; attempt++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            double distance = 24 + random.nextDouble() * 24;
            int x = Mth.floor(player.getX() + Mth.cos(angle) * distance), z = Mth.floor(player.getZ() + Mth.sin(angle) * distance);
            BlockPos column = new BlockPos(x, 0, z);
            if (!level.isLoaded(column)) {
                continue;
            }
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            EntityType<? extends Monster> type = pick(random);
            if (!SpawnPlacements.isSpawnPositionOk(type, level, pos)
                    || !SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, pos, random)
                    || level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 20, false) != null) {
                continue;
            }
            Mob mob = type.create(level);
            if (mob == null) {
                continue;
            }
            mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
            if (!level.noCollision(mob)) {
                continue;
            }
            EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
            level.addFreshEntityWithPassengers(mob);
            return;
        }
    }

    private static EntityType<? extends Monster> pick(RandomSource random) {
        int roll = random.nextInt(100);
        if (roll < 40) return EntityType.ZOMBIE;
        if (roll < 65) return EntityType.SKELETON;
        if (roll < 85) return EntityType.SPIDER;
        return EntityType.CREEPER;
    }

    public static void onCanSleep(CanPlayerSleepEvent event) {
        if (event.getProblem() == null && WeatherConfig.SPEC.isLoaded() && WeatherConfig.BLOOD_MOON_NO_SLEEP.get()
                && SkyPhenomena.bloodMoonNow(event.getLevel())) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
            event.getEntity().displayClientMessage(Component.translatable("message.reikos_weather.blood_moon.no_sleep"), true);
        }
    }

    private BloodMoon() {}
}
