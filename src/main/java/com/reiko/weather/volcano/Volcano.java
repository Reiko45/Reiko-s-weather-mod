package com.reiko.weather.volcano;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.util.Mth;

import java.util.Locale;

/** One known volcano and where it is in its eruption cycle. */
public final class Volcano {
    public enum Phase {
        /** Asleep: steaming vents, nothing more. */
        DORMANT,
        /** Waking up: earthquakes and a smoking crater, a few minutes' warning before it blows. */
        RUMBLING,
        /** Lava fountains, lava bombs, an ash column, volcanic lightning and lava pouring over the rim. */
        ERUPTING,
        /** Dying down: the lava crusts over into basalt and the smoke thins out. */
        COOLING;

        private static final Phase[] VALUES = values();

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Phase byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : DORMANT;
        }

        public static Phase byId(String id) {
            for (Phase p : VALUES) {
                if (p.id().equals(id)) return p;
            }
            return DORMANT;
        }
    }

    /** A tongue of lava making its way down the mountain. */
    public static final class Flow {
        public long head;
        public int steps;

        Flow(long head, int steps) {
            this.head = head;
            this.steps = steps;
        }
    }

    public final VolcanoShape shape;
    public Phase phase = Phase.DORMANT;
    /** Ticks left in the current phase. */
    public int timer;
    /** How long the current phase lasts in all. */
    public int length = 1;
    /** Storm cells of this volcano's ash cloud. */
    public final IntArrayList ashCells = new IntArrayList();
    /** Lava source blocks this volcano has spilled, to be cooled into rock afterwards. */
    public final LongArrayList spills = new LongArrayList();
    public final java.util.List<Flow> flows = new java.util.ArrayList<>();
    /** How many lava flows this eruption has started. */
    public int flowsStarted;

    /** Whether a player has been near enough recently for the dormant clock to run (not saved). */
    boolean watched;

    public Volcano(VolcanoShape shape) {
        this.shape = shape;
    }

    public void enter(Phase phase, int length) {
        this.phase = phase;
        this.length = Math.max(1, length);
        this.timer = this.length;
    }

    /** 0 to 1, how far through the current phase it is. */
    public float progress() {
        return Mth.clamp(1.0F - timer / (float) length, 0.0F, 1.0F);
    }

    /** How violent the volcano is right now, 0 (asleep) to 1 (the height of an eruption). */
    public float activity() {
        return activity(phase, progress());
    }

    public static float activity(Phase phase, float p) {
        return switch (phase) {
            case DORMANT -> 0.0F;
            case RUMBLING -> 0.08F + 0.22F * p;
            // builds up quickly, holds, then tails off toward the end
            case ERUPTING -> 0.35F + 0.65F * Mth.clamp(p * 5.0F, 0.0F, 1.0F) * (1.0F - 0.6F * Mth.clamp((p - 0.7F) / 0.3F, 0.0F, 1.0F));
            case COOLING -> 0.3F * (1.0F - p);
        };
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("shape", shape.save());
        tag.putString("phase", phase.id());
        tag.putInt("timer", timer);
        tag.putInt("length", length);
        tag.putIntArray("ash", ashCells.toIntArray());
        tag.put("spills", new LongArrayTag(spills.toLongArray()));
        long[] heads = new long[flows.size()];
        int[] steps = new int[flows.size()];
        for (int i = 0; i < flows.size(); i++) {
            heads[i] = flows.get(i).head;
            steps[i] = flows.get(i).steps;
        }
        tag.put("flowHeads", new LongArrayTag(heads));
        tag.put("flowSteps", new IntArrayTag(steps));
        tag.putInt("flowsStarted", flowsStarted);
        return tag;
    }

    public static Volcano load(CompoundTag tag) {
        Volcano v = new Volcano(VolcanoShape.load(tag.getCompound("shape")));
        v.phase = Phase.byId(tag.getString("phase"));
        v.timer = tag.getInt("timer");
        v.length = Math.max(1, tag.getInt("length"));
        v.ashCells.addElements(0, tag.getIntArray("ash"));
        v.spills.addElements(0, tag.getLongArray("spills"));
        long[] heads = tag.getLongArray("flowHeads");
        int[] steps = tag.getIntArray("flowSteps");
        for (int i = 0; i < Math.min(heads.length, steps.length); i++) {
            v.flows.add(new Flow(heads[i], steps[i]));
        }
        v.flowsStarted = tag.getInt("flowsStarted");
        return v;
    }
}
