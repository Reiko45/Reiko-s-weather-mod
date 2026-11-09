package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
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

/**
 * A fissure eruption with spatter cones: the ground split open in a long crack and lava welled up along it in a
 * curtain of fire. Where the fountains kept playing, gobbets of half-molten spatter fell back round the vents and
 * welded together into a row of small, steep cones, rusted red by the hot gases; the rest of the lava spread out
 * from the crack as a sheet of young, glossy pahoehoe, wrinkled into ropes where its skin was dragged along by the
 * flow underneath. The fissure is still warm: down in the crack, and deep in the cones' throats, the rock glows.
 *
 * @param cones {u along the fissure, radius, height, ground y} for each cone
 */
public record SpatterCones(int cx, int cz, int y, float angle, int length, int[] cones, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 2 || !c.fits(ground, 12, 24)) {
            return null;
        }
        int length = 50 + c.random().nextInt(31);
        if (c.relief(length / 2, 10) > 10) {
            return null;
        }
        float angle = c.random().nextFloat() * Mth.PI;
        int n = 3 + c.random().nextInt(4);
        int[] cones = new int[n * 4];
        float span = length - 12.0F;
        for (int i = 0; i < n; i++) {
            float u = ((i + 0.5F) / n - 0.5F) * span + (c.random().nextFloat() - 0.5F) * span / n * 0.6F;
            int r = 3 + c.random().nextInt(4);
            cones[i * 4] = Math.round(u);
            cones[i * 4 + 1] = r;
            cones[i * 4 + 2] = Math.round(r * (0.9F + 0.5F * c.random().nextFloat()));
            cones[i * 4 + 3] = 0;
        }
        SpatterCones probe = new SpatterCones(c.x(), c.z(), ground, angle, length, cones, c.random().nextLong());
        for (int i = 0; i < n; i++) {
            double[] p = probe.vent(i);
            cones[i * 4 + 3] = c.groundAt(Mth.floor(p[0]), Mth.floor(p[1]));
        }
        return probe;
    }

    static Landform load(CompoundTag tag) {
        return new SpatterCones(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getFloat("angle"), tag.getInt("length"), tag.getIntArray("cones"),
                tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.put("cones", new IntArrayTag(cones));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.SPATTER_CONES;
    }

    @Override
    public BoundingBox bounds() {
        int r = length / 2 + 22;
        return new BoundingBox(cx - r, y - 20, cz - r, cx + r, y + 34, cz + r);
    }

    /** The fissure's line, zigzagging a little as cracks do. */
    private float zig(float u) {
        return 0.8F * Mth.sin(u * 0.35F + Terrain.hash(seed, 1) * Mth.TWO_PI) + 0.5F * Mth.sin(u * 0.9F + Terrain.hash(seed, 2) * Mth.TWO_PI);
    }

    private double[] vent(int i) {
        float u = cones[i * 4];
        return Terrain.xz(u, zig(u), cx, cz, angle);
    }

    /** How far into the lava field a point is: 1 on the fissure, 0 at the edge, below 0 outside. */
    private float inside(float u, float v) {
        float side = 8.0F + 9.0F * Terrain.field(seed + (v > 0.0F ? 3 : 4), u, 0.0, 12.0);
        float over = Math.max(0.0F, Math.abs(u) - length / 2.0F) / 9.0F;
        return 1.0F - Mth.sqrt(Mth.square(Math.abs(v) / side) + over * over);
    }

    /** Spatter: black, and rusted red and brown where the hot gases oxidised it. */
    private BlockState spatter(int x, int h, int z, float red) {
        float n = Terrain.noise(seed + 7, x, h, z);
        if (n < red * 0.5F) return Blocks.RED_TERRACOTTA.defaultBlockState();
        if (n < red * 0.75F) return Blocks.BROWN_TERRACOTTA.defaultBlockState();
        return n < 0.75F ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.BASALT.defaultBlockState();
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float half = length / 2.0F;
        // ropes of lava lie along the flow front, roughly parallel to the fissure
        Direction.Axis ropes = Math.abs(Mth.cos(angle)) > Math.abs(Mth.sin(angle)) ? Direction.Axis.X : Direction.Axis.Z;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float u = l[0], v = l[1], off = Math.abs(v - zig(u));
                float f = inside(u, v);
                // the nearest cone
                int cone = -1;
                float cd = Float.MAX_VALUE;
                for (int i = 0; i < cones.length / 4; i++) {
                    double[] p = vent(i);
                    float dd = Mth.sqrt(Mth.square((float) (x + 0.5 - p[0])) + Mth.square((float) (z + 0.5 - p[1])));
                    if (dd / cones[i * 4 + 1] < cd) {
                        cd = dd / cones[i * 4 + 1];
                        cone = i;
                    }
                }
                if (f <= 0.0F && cd >= 1.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 8 || ground > b.maxY() - 16) continue;
                pos.set(x, ground + 1, z);
                if (!level.getFluidState(pos).isEmpty()) continue; // it doesn't flow into rivers or ponds here
                // the lava sheet: a couple of blocks thick, ropy and humped, with a low, lobed front
                int top = ground;
                float n = Terrain.noise(seed, x, 0, z);
                boolean rope = false;
                if (f > 0.0F) {
                    int thick = 1 + Math.round(2.2F * (float) Math.pow(f, 0.6));
                    rope = f > 0.12F && Mth.sin(Math.abs(v) * 1.25F + 6.0F * Terrain.field(seed + 5, x, z, 7.0)) > 0.55F;
                    int tumulus = Terrain.field(seed + 6, x, z, 5.0) > 0.8F ? 1 : 0;
                    top = ground + thick + (rope ? 1 : 0) + tumulus;
                }
                // a spatter cone, steep-sided, with its throat open down the middle
                int coneTop = Integer.MIN_VALUE, base = ground;
                float dd = 0.0F;
                if (cone >= 0 && cd < 1.0F) {
                    int r = cones[cone * 4 + 1], hc = cones[cone * 4 + 2];
                    base = cones[cone * 4 + 3];
                    dd = cd * r;
                    coneTop = base + 3 + Math.round(hc * (1.0F - (float) Math.pow(cd, 1.4)) + (Terrain.noise(seed, x, 1, z) - 0.5F) * 1.5F);
                    top = Math.max(top, coneTop);
                }
                Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, Math.min(14, b.maxY() - Math.max(ground, top)));
                boolean crack = off < 0.85F && Math.abs(u) < half - 1.0F;
                boolean throat = coneTop > Integer.MIN_VALUE && dd < 1.25F;
                boolean walls = off < 3.0F && Math.abs(u) < half + 1.0F || coneTop > Integer.MIN_VALUE && dd < 2.4F;
                int floor = throat ? base - 5 : ground - 2 - Math.round(2.0F * Terrain.noise(seed, x, 2, z));
                // the rock round the crack and the throats is all lava, not soil, right down below where they glow
                int from = walls ? Math.min(ground, base) - 6 : ground + 1;
                for (int h = Math.max(b.minY(), from); h <= top; h++) {
                    BlockState s;
                    if ((crack || throat) && h > floor) {
                        s = Blocks.AIR.defaultBlockState();
                    } else if ((crack || throat) && h == floor) {
                        float g = Terrain.noise(seed, x, 3, z);
                        s = g < 0.45F ? Blocks.MAGMA_BLOCK.defaultBlockState() : g > 0.95F && crack ? ModBlocks.VOLCANIC_VENT.get().defaultBlockState()
                                : Blocks.BLACKSTONE.defaultBlockState();
                    } else if (coneTop > Integer.MIN_VALUE && h > ground + (f > 0.0F ? 1 : 0) && h <= coneTop) {
                        // spatter, and down in the walls of the throat, rock still glowing
                        float red = Mth.clamp((h - base) / (float) Math.max(1, coneTop - base), 0.0F, 1.0F) * 0.8F;
                        s = dd < 2.0F && h < base + 1 && Terrain.noise(seed + 8, x, h, z) < 0.3F ? Blocks.MAGMA_BLOCK.defaultBlockState() : spatter(x, h, z, red);
                    } else if (h > ground) {
                        // the sheet: glassy black on top, ropes of it lying across the flow
                        if (h == top && rope) s = Blocks.POLISHED_BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, ropes);
                        else if (h == top) s = n < 0.55F ? Blocks.BASALT.defaultBlockState() : n < 0.85F ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState();
                        else s = Terrain.noise(seed, x, h, z) < 0.5F ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
                    } else {
                        s = Terrain.noise(seed, x, h, z) < 0.6F ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
                    }
                    Terrain.set(level, pos, x, h, z, s);
                }
            }
        }
    }
}
