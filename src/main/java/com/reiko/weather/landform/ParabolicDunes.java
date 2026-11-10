package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Parabolic dunes: U-shaped dunes that form where the wind blows sand over ground with plants on it, as it does behind
 * many beaches. Where a blowout tears through the plants the bare sand starts moving downwind, but grasses and shrubs
 * keep a grip on its sides. So the middle runs on ahead as a tall, bare nose with a steep slip face, while its flanks
 * lag behind as long, low arms held down by vegetation, pointing back into the wind: the opposite way round to a
 * barchan. Between the arms the wind has scoured the ground down almost to the water table, leaving a low, flat,
 * damp hollow where grass and mosses grow.
 *
 * @param y     the ground in the middle (for the bounds)
 * @param angle the way the wind blows (downwind)
 * @param dunes {u along the wind, v across it, half-width between the arms, arm length, nose height, hollow floor y}
 *              for each dune, from the middle of the field
 */
public record ParabolicDunes(int cx, int cz, int y, float angle, int[] dunes, long seed) implements Landform {
    private static final int F = 6;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 1 || !c.fits(ground, 12, 32) || c.relief(30, 10) > 13) {
            return null;
        }
        float angle = c.random().nextFloat() * Mth.TWO_PI;
        int n = 3 + c.random().nextInt(3);
        int[] dunes = new int[n * F];
        int spacing = n > 4 ? 21 : 24;
        for (int i = 0; i < n; i++) {
            int w = (n > 4 ? 8 : 9) + c.random().nextInt(4);
            int l = 20 + c.random().nextInt(13);
            int u = l / 2 - 4 + c.random().nextInt(13) - 6;
            int v = Math.round((i - (n - 1) / 2.0F) * spacing) + c.random().nextInt(7) - 3;
            dunes[i * F] = u;
            dunes[i * F + 1] = v;
            dunes[i * F + 2] = w;
            dunes[i * F + 3] = l;
            dunes[i * F + 4] = 6 + c.random().nextInt(7);
            // the hollow is scoured a couple of blocks below the ground it started on, but never down to the sea
            double[] p = Terrain.xz(u - l * 0.55F, v, c.x(), c.z(), angle);
            int g = c.groundAt(Mth.floor(p[0]), Mth.floor(p[1]));
            dunes[i * F + 5] = Math.max(c.seaLevel() + 1, Math.min(g, ground + 4) - 2);
        }
        return new ParabolicDunes(c.x(), c.z(), ground, angle, dunes, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new ParabolicDunes(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getFloat("angle"), tag.getIntArray("dunes"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putFloat("angle", angle);
        tag.put("dunes", new IntArrayTag(dunes));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.PARABOLIC_DUNES;
    }

    @Override
    public BoundingBox bounds() {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < dunes.length / F; i++) {
            float u = dunes[i * F], v = dunes[i * F + 1], w = dunes[i * F + 2] * 1.1F + 12.0F, l = dunes[i * F + 3];
            for (float du : new float[]{-l * 1.1F - 8.0F, 18.0F}) {
                for (float dv : new float[]{-w, w}) {
                    double[] p = Terrain.xz(u + du, v + dv, cx, cz, angle);
                    minX = Math.min(minX, Mth.floor(p[0]));
                    maxX = Math.max(maxX, Mth.ceil(p[0]));
                    minZ = Math.min(minZ, Mth.floor(p[1]));
                    maxZ = Math.max(maxZ, Mth.ceil(p[1]));
                }
            }
        }
        return new BoundingBox(minX, y - 14, minZ, maxX, y + 32, maxZ);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                // the tallest ridge here (and how far along it from the nose), and how deep in a hollow
                float ridge = 0.0F, along = 1.0F, hollow = 0.0F;
                int floor = 0;
                for (int i = 0; i < dunes.length / F; i++) {
                    float w = dunes[i * F + 2], len = dunes[i * F + 3], height = dunes[i * F + 4];
                    float a = l[0] - dunes[i * F], bv = l[1] - dunes[i * F + 1] + 2.0F * (Terrain.field(seed + i, x, z, 9.0) - 0.5F);
                    if (a > 20.0F || a < -len - 12.0F || Math.abs(bv) > w * 1.6F) continue;
                    // the ridge line is a parabola: the nose at the front, the arms trailing back upwind
                    float s = Mth.square(bv / w);
                    float f = a + len * s, grad = Mth.sqrt(1.0F + Mth.square(2.0F * len * bv / (w * w)));
                    float t = f / grad; // across the ridge: above 0 downwind of it (outside the U), below 0 inside
                    float hs = height * (1.0F - 0.65F * Math.min(1.0F, s)) * Terrain.smooth((1.05F - s) / 0.2F);
                    // a steep slip face on the outside, a gentler slope inside
                    float width = t > 0.0F ? hs + 1.5F : 1.6F * hs + 2.0F;
                    float h = hs * Terrain.smooth(1.0F - Math.abs(t) / width);
                    if (h > ridge) {
                        ridge = h;
                        along = s;
                    }
                    if (t < 0.0F && a > -len) {
                        float d = Terrain.smooth(-t / 3.0F) * Terrain.smooth((a + len) / (len * 0.3F));
                        if (d > hollow) {
                            hollow = d;
                            floor = dunes[i * F + 5];
                        }
                    }
                }
                if (ridge < 0.5F && hollow < 0.05F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                // dunes stop at the water's edge
                if (ground < b.minY() + 3 || ground > b.maxY() - 16 || !level.getFluidState(pos.set(x, ground + 1, z)).isEmpty()) continue;
                int sea = level.getSeaLevel();
                if (ground <= sea) ridge *= 0.4F;
                float base = Mth.lerp(hollow, ground, floor);
                int top = Math.round(base + ridge);
                float n = Terrain.noise(seed, x, ground, z), m = Terrain.noise(seed + 1, x, ground, z);
                // the arms are held down by plants, the nose is bare and on the move
                boolean grassy = along + 0.3F * (Terrain.field(seed + 9, x, z, 5.0) - 0.5F) > 0.42F;
                boolean flat = hollow > 0.5F && ridge < 1.5F;
                if (top == ground && !flat && !grassy) continue;
                Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, Math.min(12, b.maxY() - Math.max(ground, top)));
                for (int h = top + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                for (int h = ground + 1; h <= top; h++) {
                    BlockState s = flat ? Blocks.DIRT.defaultBlockState() : top - h > 3 ? Blocks.SANDSTONE.defaultBlockState() : Blocks.SAND.defaultBlockState();
                    Terrain.set(level, pos, x, h, z, s);
                }
                if (flat) {
                    // the hollow's floor: damp turf, with soggy patches of mud and moss
                    float wet = Terrain.field(seed + 5, x, z, 4.0);
                    BlockState s = wet > 0.68F ? Blocks.MUD.defaultBlockState() : n < 0.12F ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                    Terrain.set(level, pos, x, top - 1, z, Blocks.DIRT.defaultBlockState());
                    Terrain.set(level, pos, x, top, z, s);
                    if (!s.is(Blocks.MUD) && m > 0.4F) {
                        Terrain.set(level, pos, x, top + 1, z, m > 0.8F ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
                    }
                } else if (grassy) {
                    // marram-like grass binding the sand, with a few low bushes
                    Terrain.set(level, pos, x, top, z, n < 0.08F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
                    if (top - 1 > ground) Terrain.set(level, pos, x, top - 1, z, Blocks.DIRT.defaultBlockState());
                    if (m > 0.985F) {
                        Terrain.set(level, pos, x, top + 1, z, leaves);
                        if (Terrain.noise(seed + 2, x, top, z) < 0.5F) Terrain.set(level, pos, x, top + 2, z, leaves);
                    } else if (m > 0.975F) {
                        Terrain.set(level, pos, x, top + 1, z, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 1 + Math.round(n * 2.0F)));
                    } else if (m > 0.35F) {
                        Terrain.set(level, pos, x, top + 1, z, m > 0.85F ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
                    }
                } else {
                    // the bare, wind-rippled nose
                    Terrain.set(level, pos, x, top, z, Blocks.SAND.defaultBlockState());
                    if (m > 0.993F && along > 0.25F) Terrain.set(level, pos, x, top + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                }
            }
        }
    }
}
