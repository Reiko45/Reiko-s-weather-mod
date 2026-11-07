package com.reiko.weather.weather;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

import javax.annotation.Nullable;

/**
 * Big hail cracks glass that's open to the sky, or smashes it outright (a cracked pane goes on the next
 * hit). Tinted glass is tough enough to shrug it off. (Needs mobGriefing.)
 */
public final class HailGlass {
    private static final Long2IntOpenHashMap HITS = new Long2IntOpenHashMap();
    private static final Long2LongOpenHashMap LAST = new Long2LongOpenHashMap();
    private static final long FORGET_AFTER = 2400L;

    public static boolean breakable(BlockState state) {
        return (state.is(Tags.Blocks.GLASS_BLOCKS) || state.is(Tags.Blocks.GLASS_PANES)) && !state.is(Blocks.TINTED_GLASS);
    }

    static void hit(ServerLevel level, @Nullable BlockPos top) {
        if (top == null) {
            return;
        }
        BlockPos glass = top.below();
        if (!breakable(level.getBlockState(glass)) || !level.canSeeSky(top)) {
            return;
        }
        long key = glass.asLong(), now = level.getGameTime();
        int hits = now - LAST.getOrDefault(key, Long.MIN_VALUE / 2) > FORGET_AFTER ? 1 : HITS.get(key) + 1;
        int crackId = (int) (key ^ key >>> 32) | 0x40000000;
        if (hits >= 2 || level.random.nextFloat() < 0.25F) {
            level.destroyBlockProgress(crackId, glass, -1);
            level.destroyBlock(glass, false);
            HITS.remove(key);
            LAST.remove(key);
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(glass.getX(), glass.getY(), glass.getZ()) < 24 * 24) {
                    WeatherAdvancements.award(player, "glass_houses");
                }
            }
        } else {
            HITS.put(key, hits);
            LAST.put(key, now);
            level.destroyBlockProgress(crackId, glass, 5);
            level.playSound(null, glass, SoundEvents.GLASS_HIT, SoundSource.BLOCKS, 1.0F, 1.3F);
        }
    }

    static void tick(ServerLevel level) {
        if (level.getGameTime() % 1200L == 0L && !LAST.isEmpty()) {
            long now = level.getGameTime();
            LAST.long2LongEntrySet().removeIf(e -> {
                boolean old = now - e.getLongValue() > FORGET_AFTER;
                if (old) HITS.remove(e.getLongKey());
                return old;
            });
        }
    }

    public static void reset() {
        HITS.clear();
        LAST.clear();
    }

    private HailGlass() {}
}
