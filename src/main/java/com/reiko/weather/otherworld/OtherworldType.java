package com.reiko.weather.otherworld;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.Locale;

/** Weather for the Nether and the End. */
public enum OtherworldType {
    CALM(true, true, 50, 2400, 9600),
    /** Choking grey ash swirling through the Nether's caverns. */
    ASH_STORM(true, false, 20, 2400, 6000),
    /** Glowing embers drifting down from the ceiling. Harmless and pretty. */
    EMBER_FALL(true, false, 20, 2400, 6000),
    /** Burning droplets that set exposed creatures and flammable blocks alight. */
    FIRE_RAIN(true, false, 10, 1800, 4200),
    /** Purple squalls in the End with gusts that shove you toward the void. */
    VOID_STORM(false, true, 30, 1800, 4800);

    private static final OtherworldType[] VALUES = values();

    public final boolean nether;
    public final boolean end;
    public final int weight;
    public final int minDuration;
    public final int maxDuration;

    OtherworldType(boolean nether, boolean end, int weight, int minDuration, int maxDuration) {
        this.nether = nether;
        this.end = end;
        this.weight = weight;
        this.minDuration = minDuration;
        this.maxDuration = maxDuration;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public MutableComponent displayName() {
        return Component.translatable("weather.reikos_weather." + id());
    }

    public boolean validIn(Level level) {
        return level.dimension() == Level.NETHER ? nether : level.dimension() == Level.END && end;
    }

    public static OtherworldType pick(Level level, OtherworldType after, RandomSource random) {
        int total = 0;
        for (OtherworldType t : VALUES) {
            if (t.validIn(level) && t != after) total += t.weight;
        }
        int roll = random.nextInt(Math.max(1, total));
        for (OtherworldType t : VALUES) {
            if (t.validIn(level) && t != after) {
                roll -= t.weight;
                if (roll < 0) return t;
            }
        }
        return CALM;
    }

    public static OtherworldType byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : CALM;
    }

    public static OtherworldType byId(String id) {
        for (OtherworldType t : VALUES) {
            if (t.id().equalsIgnoreCase(id)) return t;
        }
        return null;
    }
}
