package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Roches moutonnées: a cluster of bare rock knobs shaped by an ice sheet as it rode over them, all lying the same
 * way. On the side the ice came from it ground the rock into a long, smooth, gently rising ramp, polished and scored
 * with fine parallel striations by the stones frozen into its sole. On the far side, where the ice pulled away, it
 * froze into the cracks and plucked out whole blocks, leaving a steep, broken, blocky face with rubble at its foot.
 * From a distance the cluster looks like a flock of sheep lying down (the name is French), and on a couple of the
 * knobs sit erratic boulders of some quite different rock, set down there as the ice melted.
 *
 * @param angle    the way the ice flowed
 * @param knobs    {x, z, half-length, half-width, height} for each knob
 * @param erratics {x, z, the knob's top under it, radius in tenths, kind of rock} for each perched boulder
 */
public record RochesMoutonnees(int cx, int cz, int y, float angle, int[] knobs, int[] erratics, long seed) implements Landform {
    private static final int SPREAD = 34;
    /** Where along a knob (from -1 up-glacier to 1 down-glacier) its crest is. */
    private static final float CREST = 0.4F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() || c.relief(SPREAD / 2, 10) > 12 || !c.fits(ground, 14, 30)) {
            return null;
        }
        float angle = c.random().nextFloat() * Mth.TWO_PI;
        int n = 5 + c.random().nextInt(6);
        int[] knobs = new int[n * 5];
        for (int i = 0; i < n; i++) {
            float a = c.random().nextFloat() * Mth.TWO_PI, d = Mth.sqrt(c.random().nextFloat()) * SPREAD;
            int hl = 5 + c.random().nextInt(8);
            knobs[i * 5] = c.x() + Math.round(Mth.cos(a) * d);
            knobs[i * 5 + 1] = c.z() + Math.round(Mth.sin(a) * d);
            knobs[i * 5 + 2] = hl;
            knobs[i * 5 + 3] = Math.max(3, Math.round(hl * (0.45F + 0.2F * c.random().nextFloat())));
            knobs[i * 5 + 4] = Math.min(4 + c.random().nextInt(7), Math.round(hl * 0.8F));
        }
        long seed = c.random().nextLong();
        RochesMoutonnees probe = new RochesMoutonnees(c.x(), c.z(), ground, angle, knobs, new int[0], seed);
        // perched on the crests of the two biggest knobs
        int first = 0, second = -1;
        for (int j = 1; j < n; j++) if (knobs[j * 5 + 4] > knobs[first * 5 + 4]) first = j;
        for (int j = 0; j < n; j++) if (j != first && (second < 0 || knobs[j * 5 + 4] > knobs[second * 5 + 4])) second = j;
        int[] erratics = new int[10];
        int[] on = {first, second};
        for (int e = 0; e < 2; e++) {
            int i = on[e];
            double[] p = Terrain.xz(knobs[i * 5 + 2] * (CREST - 0.15F), (c.random().nextFloat() - 0.5F) * knobs[i * 5 + 3] * 0.5F,
                    knobs[i * 5] + 0.5, knobs[i * 5 + 1] + 0.5, angle);
            int x = Mth.floor(p[0]), z = Mth.floor(p[1]);
            erratics[e * 5] = x;
            erratics[e * 5 + 1] = z;
            erratics[e * 5 + 2] = c.groundAt(x, z) + Math.round(probe.rise(x + 0.5F, z + 0.5F)[0]);
            erratics[e * 5 + 3] = 12 + c.random().nextInt(8);
            erratics[e * 5 + 4] = c.random().nextInt(3);
        }
        return new RochesMoutonnees(c.x(), c.z(), ground, angle, knobs, erratics, seed);
    }

    static Landform load(CompoundTag tag) {
        return new RochesMoutonnees(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getFloat("angle"), tag.getIntArray("knobs"),
                tag.getIntArray("erratics"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putFloat("angle", angle);
        tag.put("knobs", new IntArrayTag(knobs));
        tag.put("erratics", new IntArrayTag(erratics));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.ROCHES_MOUTONNEES;
    }

    @Override
    public BoundingBox bounds() {
        int r = SPREAD + 18, high = y + 34;
        for (int e = 0; e < erratics.length; e += 5) high = Math.max(high, erratics[e + 2] + 6);
        return new BoundingBox(cx - r, y - 16, cz - r, cx + r, high, cz + r);
    }

    /**
     * How high the knobs raise the rock at a point: {height, which knob, where along it (-1 to 1), where across it}.
     * Each is a whaleback: a long, gentle ramp up-glacier to a crest, then a steep plucked face down-glacier.
     */
    private float[] rise(float x, float z) {
        float best = 0.0F, which = -1.0F, along = 0.0F, across = 0.0F;
        for (int i = 0; i < knobs.length / 5; i++) {
            float[] l = Terrain.uv(x, z, knobs[i * 5] + 0.5, knobs[i * 5 + 1] + 0.5, angle);
            // the plucked face is ragged, where blocks have come away
            float pluck = 1.0F - 0.12F * Terrain.field(seed + i, x, z, 3.0);
            float s = l[0] / knobs[i * 5 + 2], w = l[1] / knobs[i * 5 + 3];
            if (s <= -1.0F || s >= pluck || Math.abs(w) >= 1.0F) continue;
            float p = s < CREST ? (float) Math.pow(Terrain.smooth((s + 1.0F) / (1.0F + CREST)), 0.8)
                    : Mth.sqrt(Math.max(0.0F, 1.0F - Mth.square((s - CREST) / (pluck - CREST))));
            float h = knobs[i * 5 + 4] * p * (float) Math.pow(1.0F - w * w, 0.6);
            if (h > best) {
                best = h;
                which = i;
                along = s;
                across = l[1];
            }
        }
        return new float[]{best, which, along, across};
    }

    /** Whether a point is in the rubble of plucked blocks lying at the foot of a knob's steep face. */
    private boolean rubble(float x, float z) {
        for (int i = 0; i < knobs.length / 5; i++) {
            float[] l = Terrain.uv(x, z, knobs[i * 5] + 0.5, knobs[i * 5 + 1] + 0.5, angle);
            float s = l[0] / knobs[i * 5 + 2], w = l[1] / knobs[i * 5 + 3];
            if (s > 0.85F && s < 1.4F && Math.abs(w) < 0.85F) return true;
        }
        return false;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] r = rise(x + 0.5F, z + 0.5F);
                int add = Math.round(r[0]);
                int boulder = erratic(x, z);
                boolean loose = add < 1 && rubble(x + 0.5F, z + 0.5F) && Terrain.noise(seed, x, 3, z) > 0.72F;
                if (add < 1 && boulder < 0 && !loose) continue;
                int ground = Terrain.ground(level, pos, x, z);
                // (and under any water, which leaves overhanging it can hide from Terrain.ground)
                while (ground > b.minY() && !level.getFluidState(pos.set(x, ground, z)).isEmpty()) ground--;
                if (ground < b.minY() + 2 || ground + add > b.maxY() - 8) continue;
                if (loose) {
                    // a block the ice plucked off the face, lying where it fell
                    // (only on open ground: not in water, nor at the foot of a tree)
                    BlockState above = level.getBlockState(pos.set(x, ground + 1, z));
                    if (!level.getFluidState(pos).isEmpty() || !above.isAir() && !above.canBeReplaced()) continue;
                    Terrain.clearAbove(level, pos, x, ground, z, 2);
                    float n = Terrain.noise(seed, x, 4, z);
                    Terrain.set(level, pos, x, ground + 1, z, (n < 0.4F ? Blocks.COBBLESTONE : n < 0.7F ? Blocks.MOSSY_COBBLESTONE : Blocks.STONE).defaultBlockState());
                    continue;
                }
                int top = ground + add;
                if (add >= 1) knob(level, pos, b, x, z, r, ground, top);
                if (boulder >= 0) boulder(level, pos, b, x, z, boulder, add >= 1 ? top : ground);
            }
        }
    }

    private void knob(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, float[] r, int ground, int top) {
        int i = (int) r[1];
        float s = r[2], v = r[3];
        Terrain.clearAbove(level, pos, x, ground, z, Math.min(top - ground + 14, b.maxY() - ground));
        boolean lee = s > CREST + 0.05F;
        // striations: fine grooves scored along the way the ice moved, so straight down the length of the knob
        float spacing = 3.0F + 2.0F * Terrain.hash(seed, 20 + i);
        float stripe = Mth.frac(v / spacing + Terrain.hash(seed, 40 + i));
        boolean striated = !lee && (stripe < 0.5F / spacing || stripe > 1.0F - 0.5F / spacing) && Terrain.noise(seed, x, 8, z) < 0.85F;
        for (int h = ground; h <= top; h++) {
            float n = Terrain.noise(seed + 1, x, h, z);
            BlockState st;
            if (h < top - 1 || h < top && !lee) {
                st = Terrain.field(seed + 2, x * 2.0 + h, z * 2.0, 5.0) < 0.6F ? Blocks.STONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
            } else if (lee) {
                // the plucked face: broken, blocky, cobbled
                st = n < 0.3F ? Blocks.COBBLESTONE.defaultBlockState() : n < 0.45F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                        : n < 0.8F ? Blocks.STONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
            } else if (striated) {
                st = n < 0.5F ? Blocks.DIORITE.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState();
            } else {
                // the polished ramp
                st = Terrain.field(seed + 3, x, z, 6.0) < 0.55F ? Blocks.SMOOTH_STONE.defaultBlockState()
                        : n < 0.7F ? Blocks.STONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
            }
            Terrain.set(level, pos, x, h, z, st);
        }
    }

    /** Which perched boulder (if any) covers a column, or -1. */
    private int erratic(int x, int z) {
        for (int e = 0; e < erratics.length / 5; e++) {
            float rad = erratics[e * 5 + 3] / 10.0F;
            if (Mth.square(x - erratics[e * 5]) + Mth.square(z - erratics[e * 5 + 1]) < Mth.square(rad + 0.3F)) return e;
        }
        return -1;
    }

    /** A rounded boulder of foreign rock, sitting on the knob's crest on a few points of contact. */
    private void boulder(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, int e, int top) {
        int o = e * 5, base = erratics[o + 2];
        float rad = erratics[o + 3] / 10.0F;
        float d = Mth.sqrt(Mth.square(x - erratics[o]) + Mth.square(z - erratics[o + 1]));
        float hb = rad * Mth.sqrt(Math.max(0.0F, 1.0F - Mth.square(d / (rad + 0.3F))));
        // (resting on its middle; round the edges it stands a little clear of the rock)
        int bottom = base + 1 + (d > rad * 0.5F ? 1 : 0), cap = base + Math.max(1, Math.round(hb * 1.7F));
        Terrain.clearAbove(level, pos, x, Math.max(top, base), z, Math.min(8, b.maxY() - Math.max(top, base)));
        for (int h = bottom; h <= cap; h++) {
            float n = Terrain.noise(seed + 5, x, h, z);
            BlockState s = switch (erratics[o + 4]) {
                case 0 -> n < 0.7F ? Blocks.GRANITE.defaultBlockState() : Blocks.POLISHED_GRANITE.defaultBlockState();
                case 1 -> n < 0.75F ? Blocks.DIORITE.defaultBlockState() : Blocks.CALCITE.defaultBlockState();
                default -> n < 0.6F ? Blocks.TUFF.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState();
            };
            Terrain.set(level, pos, x, h, z, s);
        }
    }
}
