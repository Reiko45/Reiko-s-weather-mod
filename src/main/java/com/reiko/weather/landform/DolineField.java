package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * A doline field: country pocked with dolines (sinkholes), round hollows where rainwater, made faintly acid as it
 * soaked through the soil, has dissolved the limestone underneath, and the ground has slowly sagged into the gaps.
 * Most are dry, grassy bowls with bare rock showing on their steeper sides. In a few, clay washed in has sealed the
 * bottom and a pond has gathered; and in one the drain is still open, a narrow shaft dropping into a small cave.
 *
 * @param dolines {x, z, radius, bottom, depth, water (at the bottom when dry), kind (0 dry, 1 pond, 2 shaft), cave y}
 *                for each doline
 */
public record DolineField(int cx, int cz, int y, int[] dolines, long seed) implements Landform {
    private static final int SPREAD = 44, STRIDE = 8, DRY = 0, POND = 1, SHAFT = 2;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 4 || c.relief(SPREAD / 2, 8) > 12 || !c.fits(ground, 30, 26)) {
            return null;
        }
        int n = 6 + c.random().nextInt(7), ponds = 1 + c.random().nextInt(2);
        int[] d = new int[n * STRIDE];
        int k = 0, samples = 0;
        for (int tries = 0; k < n && tries < 40 && samples < 20; tries++) {
            // the one with the shaft first, then the ponds, then dry ones
            int kind = k == 0 ? SHAFT : k <= ponds ? POND : DRY;
            int r = kind == SHAFT ? 6 + c.random().nextInt(5) : kind == POND ? 5 + c.random().nextInt(6) : 4 + c.random().nextInt(7);
            float a = c.random().nextFloat() * Mth.TWO_PI, dist = Mth.sqrt(c.random().nextFloat()) * SPREAD * (k == 0 ? 0.6F : 1.0F);
            int x = c.x() + Math.round(Mth.cos(a) * dist), z = c.z() + Math.round(Mth.sin(a) * dist);
            // clear of the others (and further still of a pond, so its banks never reach another hollow)
            boolean clear = true;
            for (int j = 0; j < k && clear; j++) {
                float need = (kind == POND || d[j * STRIDE + 6] == POND ? 1.6F : 1.15F) * (r + d[j * STRIDE + 2]) + 2.0F;
                clear = Mth.square(x - d[j * STRIDE]) + Mth.square(z - d[j * STRIDE + 1]) >= need * need;
            }
            if (!clear) continue;
            int g = c.groundAt(x, z);
            samples++;
            int depth = Math.min(2 + c.random().nextInt(6), r - 1);
            if (kind == POND) depth = Math.max(depth, 3);
            depth = Math.min(depth, g - c.seaLevel()); // never sunk below a river, which could pour in
            if (depth < 2) continue;
            int bottom = g - depth;
            int water = bottom;
            if (kind == POND) {
                // the pond stands a block or two deep, and always under the lowest point of its rim
                int rim = g;
                for (int q = 0; q < 4; q++) {
                    float b = q * Mth.HALF_PI + a;
                    rim = Math.min(rim, c.groundAt(x + Math.round(Mth.cos(b) * (r + 1.5F)), z + Math.round(Mth.sin(b) * (r + 1.5F))));
                }
                samples += 4;
                water = Math.min(bottom + 1 + c.random().nextInt(2), rim - 1);
                if (water <= bottom) {
                    kind = DRY;
                    water = bottom;
                }
            }
            int o = k * STRIDE;
            d[o] = x;
            d[o + 1] = z;
            d[o + 2] = r;
            d[o + 3] = bottom;
            d[o + 4] = depth;
            d[o + 5] = water;
            d[o + 6] = kind;
            d[o + 7] = kind == SHAFT ? bottom - 9 - c.random().nextInt(4) : 0;
            k++;
        }
        if (k < 6) {
            return null;
        }
        return new DolineField(c.x(), c.z(), ground, Arrays.copyOf(d, k * STRIDE), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new DolineField(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getIntArray("dolines"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.put("dolines", new IntArrayTag(dolines));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.DOLINE_FIELD;
    }

    @Override
    public BoundingBox bounds() {
        int r = SPREAD + 18, lo = y, hi = y;
        for (int o = 0; o < dolines.length; o += STRIDE) {
            lo = Math.min(lo, dolines[o + 6] == SHAFT ? dolines[o + 7] - 5 : dolines[o + 3] - 4);
            hi = Math.max(hi, dolines[o + 3] + dolines[o + 4]);
        }
        return new BoundingBox(cx - r, lo, cz - r, cx + r, hi + 24, cz + r);
    }

    /** How far out from the middle of doline {@code i} a point is: below 1 is the hollow itself. */
    private float dist(int i, double x, double z) {
        int o = i * STRIDE;
        double dx = x - dolines[o] - 0.5, dz = z - dolines[o + 1] - 0.5;
        return (float) Math.sqrt(dx * dx + dz * dz) / (dolines[o + 2] * (0.9F + 0.2F * Terrain.field(seed + i, x, z, 5.0)));
    }

    /** The small cave's radius under a shaft doline. */
    private int caveRadius(int o) {
        return Math.min(5, dolines[o + 2] - 3);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState limestone = ModBlocks.LIMESTONE.get().defaultBlockState();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                int best = -1, shaft = -1;
                float bd = Float.MAX_VALUE, sd = Float.MAX_VALUE;
                for (int i = 0; i < dolines.length / STRIDE; i++) {
                    float d = dist(i, x + 0.5, z + 0.5);
                    if (d < bd) {
                        bd = d;
                        best = i;
                    }
                    int o = i * STRIDE;
                    if (dolines[o + 6] == SHAFT) {
                        float s = Mth.sqrt(Mth.square(x - dolines[o]) + Mth.square(z - dolines[o + 1]));
                        if (s < caveRadius(o) + 1.5F) {
                            shaft = i;
                            sd = s;
                        }
                    }
                }
                if (best < 0) continue;
                int o = best * STRIDE, kind = dolines[o + 6];
                boolean hollow = bd < 1.0F || kind == POND && bd < 1.4F;
                if (!hollow && shaft < 0) continue;
                int ground = Terrain.ground(level, pos, x, z);
                // (and under any water, which leaves overhanging it can hide from Terrain.ground)
                while (ground > b.minY() && !level.getFluidState(pos.set(x, ground, z)).isEmpty()) ground--;
                if (ground > b.maxY() - 4) continue;
                int surf = ground;
                for (pos.set(x, ground + 1, z); surf < ground + 12 && !level.getFluidState(pos).isEmpty(); pos.move(Direction.UP)) surf++;
                int top = hollow ? hollow(level, pos, b, x, z, o, bd, ground, surf, limestone) : ground;
                if (shaft >= 0) cave(level, pos, b, x, z, shaft * STRIDE, sd, top, limestone);
            }
        }
    }

    /** Shapes a column of a doline (or a pond's bank); returns its new top. */
    private int hollow(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, int o, float bd, int ground, int surf, BlockState limestone) {
        int r = dolines[o + 2], bottom = dolines[o + 3], depth = dolines[o + 4], water = dolines[o + 5], kind = dolines[o + 6];
        // a bowl, flattish at the bottom and steepening up its sides into the ground round it
        int bowl = Math.round(bottom + (ground - bottom) * bd * bd);
        int top;
        boolean wet = false;
        if (bd >= 1.0F) {
            top = Math.max(ground, water + 1); // the pond's bank, built up wherever the ground round it dips
        } else if (kind == POND) {
            float dw = Mth.clamp(Mth.sqrt((water - bottom + 0.5F) / Math.max(1, depth)), 0.3F, 0.8F);
            wet = bd < dw;
            top = wet ? Math.min(bowl, water - 1) : Math.max(bowl, water + 1);
        } else {
            top = bowl;
        }
        // any water already standing here (a stream, a pond) is drained where the hollow cuts below it, or filled in where
        // it doesn't; and nothing is cut away from under water standing just outside the hollow, so none can pour in
        int high = Math.min(b.maxY(), Math.max(ground, surf));
        if (!wet) top = Math.max(top, hold(level, x, z, top, high));
        if (top == ground && surf == ground) return ground;
        float n = Terrain.noise(seed, x, 0, z);
        boolean steep = !wet && bd < 1.0F && (ground - bottom) * 2.0F * bd / r > 1.1F;
        high = Math.max(high, top);
        Terrain.clearAbove(level, pos, x, high, z, Math.min(12, b.maxY() - high));
        for (int h = top + 1; h <= high; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
        for (int h = Math.max(b.minY(), Math.min(ground + 1, top - 3)); h <= top; h++) {
            BlockState s;
            if (wet) {
                // the clay that sealed the bottom
                s = h < top ? Blocks.CLAY.defaultBlockState() : n < 0.45F ? Blocks.CLAY.defaultBlockState() : n < 0.8F ? Blocks.MUD.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
            } else if (steep) {
                // bare limestone on the steep sides, with a little moss in the cracks
                float m = Terrain.noise(seed + 1, x, h, z);
                s = m < 0.15F ? Blocks.STONE.defaultBlockState() : m < 0.25F && h == top ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : limestone;
            } else if (h == top) {
                float f = Terrain.field(seed + 2, x, z, 6.0);
                s = f > 0.68F || bd < 0.3F && n < 0.4F ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
            } else {
                s = h >= top - 2 ? Blocks.DIRT.defaultBlockState() : limestone;
            }
            Terrain.set(level, pos, x, h, z, s);
        }
        if (wet) {
            for (int h = top + 1; h <= water; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
            if (n > 0.9F) Terrain.set(level, pos, x, water + 1, z, Blocks.LILY_PAD.defaultBlockState());
            return water;
        }
        if (!steep) {
            float g = Terrain.noise(seed + 3, x, 1, z);
            if (g > 0.75F) Terrain.set(level, pos, x, top + 1, z, (g > 0.96F ? Blocks.FERN : Blocks.SHORT_GRASS).defaultBlockState());
        }
        return top;
    }

    /** Whether a column is part of any doline (or a pond's bank), where this landform may change it. */
    private boolean inHollow(int x, int z) {
        int best = -1;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < dolines.length / STRIDE; i++) {
            float d = dist(i, x + 0.5, z + 0.5);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best >= 0 && (bd < 1.0F || dolines[best * STRIDE + 6] == POND && bd < 1.4F);
    }

    /**
     * The top of any water standing in a column just outside the dolines beside this one, between {@code from} and
     * {@code to} (or MIN_VALUE): this column has to stay solid up to there. Nothing here ever changes a column outside
     * them, so every chunk sees the same water.
     */
    private int hold(WorldGenLevel level, int x, int z, int from, int to) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int hold = Integer.MIN_VALUE;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int nx = x + d.getStepX(), nz = z + d.getStepZ();
            if (inHollow(nx, nz)) continue;
            for (int h = to; h > from && h > hold; h--) {
                if (!level.getFluidState(p.set(nx, h, nz)).isEmpty()) {
                    hold = h;
                    break;
                }
            }
        }
        return hold;
    }

    /**
     * The shaft down from the bottom of a doline, and the little cave it drops into: both walled in sound limestone,
     * so no water seeping through the rock round about can find its way in. A pool at the foot of the shaft breaks
     * the fall.
     */
    private void cave(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, int o, float sd, int top, BlockState limestone) {
        int cy = dolines[o + 7], rc = caveRadius(o);
        float half = sd < rc ? 2.6F * Mth.sqrt(1.0F - Mth.square(sd / rc)) : -1.0F;
        int lowAir = half > 0.0F ? Math.max(cy - 1, Math.round(cy - half)) : Integer.MAX_VALUE;
        int highAir = half > 0.0F ? Math.round(cy + half) : Integer.MIN_VALUE;
        boolean shaft = sd < 1.3F, lining = sd < 2.4F, pool = sd < 1.6F;
        float n = Terrain.noise(seed + 4, x, 0, z);
        for (int h = Math.max(b.minY(), cy - 4); h <= top; h++) {
            BlockState s;
            if (shaft && h > cy || h >= lowAir && h <= highAir) {
                s = Blocks.AIR.defaultBlockState();
            } else if (pool && h == cy - 2) {
                s = Blocks.WATER.defaultBlockState();
            } else if (h == lowAir - 1) {
                // the cave floor: rubble and silt washed down the shaft
                s = n < 0.3F ? Blocks.GRAVEL.defaultBlockState() : n < 0.55F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                        : n < 0.75F ? Blocks.CLAY.defaultBlockState() : limestone;
            } else if (h <= cy + 4 || lining) {
                s = Terrain.noise(seed + 5, x, h, z) < 0.15F ? Blocks.STONE.defaultBlockState() : limestone;
            } else {
                continue;
            }
            Terrain.set(level, pos, x, h, z, s);
        }
        // (nothing left growing on the bare rock round the hole, nor hanging over it)
        if (lining) Terrain.set(level, pos, x, top + 1, z, Blocks.AIR.defaultBlockState());
        // a few patches of glow lichen on the floor
        if (half > 0.0F && !pool && n > 0.85F) {
            Terrain.set(level, pos, x, lowAir, z, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true));
        }
    }
}
