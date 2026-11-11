package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * Volcanic dikes: long, narrow walls of dark rock striding out across the country from the stump of an old volcano,
 * like the spokes of a wheel. They began as cracks the magma split open as it forced its way up, fanning out from the
 * volcano's throat, and the lava that filled them set harder than anything round it. The volcano and the soft rock it
 * stood in have long since worn away, leaving the plug of its throat as a low, rocky knob and the dikes standing
 * proud as ridges, crumbled through here and there. As the lava cooled against the cold walls of each crack it shrank
 * into columns, and since a column grows away from the surface that cools it, they lie on their sides, stacked across
 * the wall like a woodpile.
 *
 * @param lo    the lowest ground seen along the dikes
 * @param hi    the highest ground seen along them
 * @param knob  the radius of the old neck
 * @param rise  how high the neck stands
 * @param dikes {angle in hundredths of a radian, length, height, width in tenths} for each dike
 */
public record VolcanicDikes(int cx, int cz, int y, int lo, int hi, int knob, int rise, int[] dikes, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 2 || !c.fits(ground, 34, 46) || c.relief(14, 8) > 10) {
            return null;
        }
        int n = 2 + c.random().nextInt(3);
        // spread round the compass, though never evenly
        float a = c.random().nextFloat() * Mth.TWO_PI;
        int[] dikes = new int[n * 4];
        int k = 0, lo = ground, hi = ground;
        for (int i = 0; i < n; i++) {
            if (i > 0) a += (0.6F + 0.8F * c.random().nextFloat()) * Mth.TWO_PI / (n + 1);
            int length = 50 + c.random().nextInt(51);
            // a dike runs out where it reaches the sea, which has worn it away
            for (int s = 1; s <= 3; s++) {
                int d = length * s / 3;
                int h = c.groundAt(c.x() + Math.round(Mth.cos(a) * d), c.z() + Math.round(Mth.sin(a) * d));
                if (h < c.seaLevel()) {
                    length = length * (s - 1) / 3 + 6;
                    break;
                }
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
            }
            if (length < 30) continue;
            dikes[k * 4] = Math.round(a * 100.0F);
            dikes[k * 4 + 1] = length;
            dikes[k * 4 + 2] = 6 + c.random().nextInt(7);
            dikes[k * 4 + 3] = 12 + c.random().nextInt(16);
            k++;
        }
        if (k < 2) {
            return null;
        }
        int knob = 4 + c.random().nextInt(4), rise = 6 + c.random().nextInt(9);
        return new VolcanicDikes(c.x(), c.z(), ground, lo, hi, knob, rise, Arrays.copyOf(dikes, k * 4), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new VolcanicDikes(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("lo"), tag.getInt("hi"), tag.getInt("knob"),
                tag.getInt("rise"), tag.getIntArray("dikes"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("lo", lo);
        tag.putInt("hi", hi);
        tag.putInt("knob", knob);
        tag.putInt("rise", rise);
        tag.put("dikes", new IntArrayTag(dikes));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.VOLCANIC_DIKES;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(knob * 1.8F) + 2;
        for (int i = 0; i < dikes.length; i += 4) r = Math.max(r, dikes[i + 1] + 4);
        return new BoundingBox(cx - r, lo - 16, cz - r, cx + r, Math.max(hi + 26, y + rise + 6), cz + r);
    }

    /** How tall dike {@code i} stands {@code u} blocks out along it (0 in the gaps where it has crumbled away). */
    private float height(int i, float u) {
        int length = dikes[i * 4 + 1];
        if (u < knob * 0.6F || u > length) return 0.0F;
        float whole = Terrain.field(seed + 31 + i, u, i * 37.0, 6.0);
        if (whole < 0.32F) return 0.0F;
        // tallest near the old volcano, lower further out, and never much under a few blocks where it stands at all
        float h = Mth.clamp(dikes[i * 4 + 2] * (1.0F - 0.45F * u / length) * (0.55F + 0.6F * Terrain.field(seed + 41 + i, u, i * 37.0, 4.0)), 3.0F, dikes[i * 4 + 2]);
        // the broken ends slope down into the gaps
        return h * Terrain.smooth((whole - 0.32F) / 0.1F) * Terrain.smooth((length - u) / 6.0F);
    }

    /** Dike {@code i}'s half-width {@code u} blocks along it. */
    private float halfWidth(int i, float u) {
        return Math.max(0.55F, dikes[i * 4 + 3] / 20.0F * (0.75F + 0.5F * Terrain.field(seed + 51 + i, u, i * 37.0, 8.0)));
    }

    /** A point's place by dike {@code i}: {along, across}, across measured from its gently wandering line. */
    private float[] along(int i, float px, float pz) {
        float[] l = Terrain.uv(px, pz, cx + 0.5, cz + 0.5, dikes[i * 4] / 100.0F);
        l[1] -= 3.0F * (Terrain.field(seed + 61 + i, l[0], i * 37.0, 20.0) - 0.5F);
        return l;
    }

    /** The top of the old neck at a point, or the lowest int outside it: a low, rough knob, with its rubble round it. */
    private int knobTop(int x, int z) {
        float dx = x + 0.5F - cx - 0.5F, dz = z + 0.5F - cz - 0.5F;
        float theta = (float) Mth.atan2(dz, dx);
        float wobble = 1.0F + 0.14F * Mth.sin(3.0F * theta + Terrain.hash(seed, 3) * Mth.TWO_PI) + 0.2F * (Terrain.field(seed + 71, x, z, 3.0) - 0.5F);
        float d = Mth.sqrt(dx * dx + dz * dz) / (knob * wobble);
        if (d >= 1.7F) return Integer.MIN_VALUE;
        float h;
        if (d < 1.0F) {
            // jointed into rough columns two blocks across, each broken off at its own height
            float broken = Terrain.noise(seed + 72, Mth.floor(x / 2.0F), 0, Mth.floor(z / 2.0F)) * 3.0F * (0.4F + d);
            h = rise * (1.0F - 0.3F * d * d) - broken;
        } else {
            h = rise * 0.35F * Terrain.smooth((1.7F - d) / 0.7F) + (Terrain.noise(seed + 73, x, 0, z) - 0.5F) * 1.5F;
        }
        return y + Math.round(h);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int count = dikes.length / 4;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float px = x + 0.5F, pz = z + 0.5F;
                // the dike standing tallest here, if any, and the nearest one's rubble
                int wall = -1;
                float wallH = 0.0F, face = 0.0F, scatter = 0.0F;
                for (int i = 0; i < count; i++) {
                    float[] l = along(i, px, pz);
                    if (l[0] < 0.0F || l[0] > dikes[i * 4 + 1] + 1) continue;
                    float hw = halfWidth(i, l[0]), v = Math.abs(l[1]);
                    if (v >= hw + 2.0F) continue;
                    float h = height(i, l[0]);
                    if (v < hw) {
                        float hc = h * (1.0F - 0.25F * Mth.square(v / hw));
                        if (hc > wallH) {
                            wallH = hc;
                            wall = i;
                            face = hw - v; // how far in from the wall's face
                        }
                        // the crumbled stretches between the walls are strewn with what's left of them
                        if (h <= 0.0F && l[0] > knob) scatter = Math.max(scatter, 0.3F);
                    } else if (l[0] > knob) {
                        scatter = Math.max(scatter, (0.16F + 0.3F * Math.min(1.0F, h / 10.0F)) * (1.0F - (v - hw) / 2.0F));
                    }
                }
                int knobTop = knobTop(x, z);
                if (wall < 0 && scatter <= 0.0F && knobTop == Integer.MIN_VALUE) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 2 || ground > b.maxY() - 16) continue;
                // where a stream or a pond has cut through, the wall is gone
                if (!level.getFluidState(pos.set(x, ground + 1, z)).isEmpty()) continue;
                float n = Terrain.noise(seed, x, 3, z);
                int top = wall >= 0 ? ground + Math.round(wallH + (n - 0.5F) * 1.6F) : ground;
                if (knobTop > top) {
                    neck(level, pos, x, z, ground, knobTop);
                    continue;
                }
                if (top > ground) {
                    dike(level, pos, x, z, ground, top, wall, face);
                } else if (scatter > 0.0F && Terrain.noise(seed + 7, x, 0, z) < scatter) {
                    rubble(level, pos, x, z, ground);
                }
            }
        }
    }

    /** One column of a dike's wall, from the ground up to {@code top}. */
    private void dike(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, int top, int i, float face) {
        Terrain.clearAbove(level, pos, x, ground, z, top - ground + 8);
        float a = dikes[i * 4] / 100.0F;
        // the columns lie across the wall, at right angles to its faces
        Direction.Axis axis = Math.abs(Mth.sin(a)) > Math.abs(Mth.cos(a)) ? Direction.Axis.X : Direction.Axis.Z;
        boolean jointed = top - ground >= 6 && face < 0.8F;
        float kind = Terrain.hash(seed, 5 + i); // each dike's lava was a little different
        for (int h = ground; h <= top; h++) {
            float m = Terrain.noise(seed + 9, x, h, z);
            BlockState s;
            if (h == top) {
                s = m < 0.45F ? Blocks.BLACKSTONE.defaultBlockState() : m < 0.85F ? Blocks.BASALT.defaultBlockState() : Blocks.COBBLED_DEEPSLATE.defaultBlockState();
            } else if (jointed && h > ground && m > 0.12F) {
                s = Blocks.POLISHED_BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
            } else if (kind < 0.3F && m < 0.45F + 0.4F * (top - h) / (float) (top - ground)) {
                s = Blocks.TUFF.defaultBlockState(); // an altered, greyer dike, weathering most toward its foot
            } else {
                s = m < (kind < 0.65F ? 0.6F : 0.3F) ? Blocks.BASALT.defaultBlockState() : m < 0.9F ? Blocks.BLACKSTONE.defaultBlockState()
                        : Blocks.SMOOTH_BASALT.defaultBlockState();
            }
            Terrain.set(level, pos, x, h, z, s);
        }
    }

    /** A column of the old volcano's neck: upright columns of basalt, or the rubble round its foot. */
    private void neck(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, int top) {
        Terrain.clearAbove(level, pos, x, ground, z, top - ground + 8);
        float dx = x + 0.5F - cx - 0.5F, dz = z + 0.5F - cz - 0.5F;
        boolean core = dx * dx + dz * dz < Mth.square(knob * 1.05F);
        for (int h = ground; h <= top; h++) {
            float m = Terrain.noise(seed + 11, x, h, z);
            BlockState s;
            if (core) {
                s = h == top && m < 0.5F ? Blocks.SMOOTH_BASALT.defaultBlockState() : m < 0.7F ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
            } else {
                s = m < 0.35F ? Blocks.BLACKSTONE.defaultBlockState() : m < 0.6F ? Blocks.COBBLED_DEEPSLATE.defaultBlockState()
                        : m < 0.85F ? Blocks.BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, m < 0.72F ? Direction.Axis.X : Direction.Axis.Z)
                        : Blocks.TUFF.defaultBlockState();
            }
            Terrain.set(level, pos, x, h, z, s);
        }
    }

    /** A block or two broken off a wall, lying at its foot: lengths of column, lumps of rock and grit. */
    private void rubble(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground) {
        Terrain.clearAbove(level, pos, x, ground, z, 6);
        float m = Terrain.noise(seed + 13, x, ground, z);
        BlockState s = m < 0.3F ? Blocks.BLACKSTONE.defaultBlockState() : m < 0.5F ? Blocks.COBBLED_DEEPSLATE.defaultBlockState()
                : m < 0.8F ? Blocks.POLISHED_BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, m < 0.65F ? Direction.Axis.X : Direction.Axis.Z)
                : Blocks.GRAVEL.defaultBlockState();
        Terrain.set(level, pos, x, ground + 1, z, s);
        if (m < 0.12F) Terrain.set(level, pos, x, ground + 2, z, Blocks.BASALT.defaultBlockState());
    }
}
