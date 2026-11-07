package com.reiko.weather.weather;

import com.reiko.weather.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * St. Elmo's fire: a few seconds before the storm's lightning strikes, blue sparks crackle around the spot
 * (and on anyone standing near it, whose hair stands on end). Time to move.
 */
public final class LightningWarnings {
    private record Pending(BlockPos pos, long at) {}

    private static final Map<ResourceKey<Level>, List<Pending>> PENDING = new HashMap<>();

    public static void schedule(ServerLevel level, BlockPos pos) {
        List<Pending> list = PENDING.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
        if (list.size() >= 16) {
            strike(level, pos);
            return;
        }
        list.add(new Pending(pos.immutable(), level.getGameTime() + 50 + level.random.nextInt(30)));
        level.playSound(null, pos, ModSounds.STATIC_CRACKLE.get(), SoundSource.WEATHER, 1.2F, 0.9F + level.random.nextFloat() * 0.2F);
    }

    static void tick(ServerLevel level) {
        List<Pending> list = PENDING.get(level.dimension());
        if (list == null || list.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (Iterator<Pending> it = list.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (!level.isLoaded(p.pos)) {
                it.remove();
                continue;
            }
            if (now >= p.at) {
                it.remove();
                strike(level, p.pos);
                continue;
            }
            if (now % 3L != 0L) continue;
            Vec3 at = Vec3.atBottomCenterOf(p.pos);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.3, at.z, 5, 0.8, 0.4, 0.8, 0.05);
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(at) < 7 * 7 && !player.isSpectator()) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getEyeY() + 0.3, player.getZ(), 3, 0.3, 0.2, 0.3, 0.02);
                    if (now % 30L == 0L) {
                        player.displayClientMessage(Component.translatable("message.reikos_weather.static").withStyle(ChatFormatting.AQUA), true);
                    }
                }
            }
        }
    }

    private static void strike(ServerLevel level, BlockPos pos) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return;
        }
        // the ground may have changed under the sparks (something built, or the tallest thing nearby)
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
        bolt.moveTo(Vec3.atBottomCenterOf(top));
        level.addFreshEntity(bolt);
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator() && player.distanceToSqr(Vec3.atCenterOf(top)) < 10 * 10) {
                WeatherAdvancements.award(player, "hair_raising");
            }
        }
    }

    public static void reset() {
        PENDING.clear();
    }

    private LightningWarnings() {}
}
