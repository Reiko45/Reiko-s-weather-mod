package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A tidal whirlpool (a maelstrom): where the tide is squeezed through a narrow strait between an island and the
 * shore, it races through so hard that it scours a deep hole in the sea bed, and over the hole the water spins into
 * a great slow vortex, strongest when the tide runs fastest and quite still at slack water.
 *
 * @param cx       the middle of the hole
 * @param angle    the way across the strait, from the shore out toward the islet
 * @param strait   how wide the strait is
 * @param islet    how far the islet's middle lies from the hole (0 if the land there already makes a strait)
 * @param isletR   the islet's radius across the strait (it's longer along it)
 */
public record Whirlpool(int cx, int cz, int sea, float angle, int strait, int pit, int depth, int islet, int isletR, int isletH, long seed)
        implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        return c.search(12, 12, Whirlpool::planAt);
    }

    @Nullable
    private static Landform planAt(PlanContext c) {
        int sea = c.seaLevel();
        int ground = c.groundAt(c.x(), c.z());
        if (ground > sea - 3 || ground < sea - 28) {
            return null;
        }
        // land on two opposite sides? (look round at 18 blocks)
        int[] round = new int[6];
        for (int k = 0; k < 6; k++) {
            float a = k * Mth.PI / 3.0F;
            round[k] = c.groundAt(c.x() + Math.round(Mth.cos(a) * 18), c.z() + Math.round(Mth.sin(a) * 18));
        }
        // at least 8 below the surface, and well below the sea bed round about
        int depth = Math.max(9 + c.random().nextInt(4), sea - 1 - ground + 4);
        for (int k = 0; k < 3; k++) {
            if (round[k] < sea || round[k + 3] < sea) continue;
            // a strait already: put the hole in the middle of it
            float a = k * Mth.PI / 3.0F;
            int d1 = shore(c, a), d2 = shore(c, a + Mth.PI);
            int width = d1 + d2;
            if (width < 12) continue;
            float shift = (d1 - d2) / 2.0F;
            int x = c.x() + Math.round(Mth.cos(a) * shift), z = c.z() + Math.round(Mth.sin(a) * shift);
            return new Whirlpool(x, z, sea, a, width, Math.min(6, width / 2 - 2), depth, 0, 0, 0, c.random().nextLong());
        }
        for (int k = 0; k < 6; k++) {
            if (round[k] < sea || round[(k + 3) % 6] >= sea - 2) continue;
            // a coast with open sea beyond it: raise an islet offshore, so the tide has a strait to race through
            float land = k * Mth.PI / 3.0F, out = land + Mth.PI;
            int ds = shore(c, land);
            int strait = 12 + c.random().nextInt(9);
            float toPit = strait / 2.0F - ds;
            int x = c.x() + Math.round(Mth.cos(out) * toPit), z = c.z() + Math.round(Mth.sin(out) * toPit);
            int isletR = 6 + c.random().nextInt(3), islet = Math.round(strait / 2.0F) + isletR;
            if (c.groundAt(x + Math.round(Mth.cos(out) * islet), z + Math.round(Mth.sin(out) * islet)) < sea - 22) {
                return null; // too deep to build on
            }
            return new Whirlpool(x, z, sea, out, strait, Math.min(6, strait / 2 - 2), depth, islet, isletR, 6 + c.random().nextInt(8), c.random().nextLong());
        }
        return null;
    }

    /** How far the shore is from here, a given way (18 if no nearer). */
    private static int shore(PlanContext c, float a) {
        for (int d = 6; d <= 12; d += 6) {
            if (c.groundAt(c.x() + Math.round(Mth.cos(a) * d), c.z() + Math.round(Mth.sin(a) * d)) >= c.seaLevel()) return d;
        }
        return 18;
    }

    static Landform load(CompoundTag tag) {
        return new Whirlpool(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getFloat("angle"), tag.getInt("strait"), tag.getInt("pit"),
                tag.getInt("depth"), tag.getInt("islet"), tag.getInt("isletR"), tag.getInt("isletH"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("sea", sea);
        tag.putFloat("angle", angle);
        tag.putInt("strait", strait);
        tag.putInt("pit", pit);
        tag.putInt("depth", depth);
        tag.putInt("islet", islet);
        tag.putInt("isletR", isletR);
        tag.putInt("isletH", isletH);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.WHIRLPOOL;
    }

    @Override
    public BoundingBox bounds() {
        float near = strait / 2.0F + 4.0F, far = islet > 0 ? islet + isletR * 1.4F + 2.0F : near, along = islet > 0 ? isletR * 1.8F * 1.4F + 2.0F : near;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (float u : new float[]{-near, far}) {
            for (float v : new float[]{-along, along}) {
                double[] w = Terrain.xz(u, v, cx + 0.5, cz + 0.5, angle);
                minX = Math.min(minX, Mth.floor(w[0]));
                maxX = Math.max(maxX, Mth.floor(w[0]));
                minZ = Math.min(minZ, Mth.floor(w[1]));
                maxZ = Math.max(maxZ, Mth.floor(w[1]));
            }
        }
        return new BoundingBox(minX, Math.min(sea - 1 - depth - 2, sea - 32), minZ, maxX, sea + isletH + 4, maxZ);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int bottom = sea - 1 - depth;
        float half = strait / 2.0F;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
                float u = l[0], v = l[1];
                int ground = Terrain.ground(level, pos, x, z);
                if (ground > b.maxY() - 8 || ground <= b.minY()) continue;
                float n = Terrain.noise(seed, x, sea, z);
                int top = ground;
                BlockState surface = null;
                // the hole the tide has scoured, steep-sided and deep
                float dp = Mth.sqrt(u * u + v * v) / (pit * (0.92F + 0.16F * Terrain.field(seed, x, z, 4.0)));
                boolean hole = dp < 1.0F;
                if (hole) {
                    top = Math.min(ground, Math.round(Mth.lerp((float) Math.pow(1.0F - dp * dp, 0.6), ground, bottom)));
                    if (x == cx && z == cz) top = bottom;
                    surface = n < 0.6F ? Blocks.STONE.defaultBlockState() : n < 0.85F ? Blocks.GRAVEL.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
                } else if (Math.abs(u) < half + 1.0F && ground < sea - 1) {
                    // the floor of the strait, swept down to bare rock and stones by the racing tide
                    surface = n < 0.45F ? Blocks.STONE.defaultBlockState() : n < 0.75F ? Blocks.GRAVEL.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
                }
                boolean rock = false;
                float di = 9.0F;
                if (islet > 0) {
                    // the islet: a steep knob of rock, longer along the strait than across it
                    di = Mth.sqrt(Mth.square((u - islet) / isletR) + Mth.square(v / (isletR * 1.8F))) / (0.88F + 0.24F * Terrain.field(seed + 1, x, z, 5.0));
                    if (di < 1.0F) {
                        int knob = sea - 1 + Math.round((isletH + 1) * Terrain.smooth((1.0F - di) / 0.35F));
                        if (knob > top) {
                            top = knob;
                            rock = true;
                        }
                    } else if (di < 1.4F && !hole) {
                        int foot = Math.round(Mth.lerp(Terrain.smooth((di - 1.0F) / 0.4F), sea - 2, ground));
                        if (foot > top) {
                            top = foot;
                            surface = n < 0.5F ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                        }
                    }
                }
                if (top == ground && surface == null) continue;
                Terrain.clearAbove(level, pos, x, ground, z, Math.max(0, top - ground) + 8);
                for (int h = top + 1; h <= ground; h++) {
                    Terrain.set(level, pos, x, h, z, h <= sea - 1 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
                if (rock) {
                    // bare rock, with a scrap of turf on the flattest part of the top
                    boolean turf = top >= sea + 3 && di < 0.5F && Terrain.field(seed + 3, x, z, 3.0) < 0.6F;
                    for (int h = Math.max(b.minY(), ground + 1); h <= top; h++) {
                        BlockState s = Terrain.field(seed + 2, x + h * 0.8, z - h * 0.6, 3.0) > 0.55F ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
                        if (turf && h == top) s = Blocks.GRASS_BLOCK.defaultBlockState();
                        else if (turf && h == top - 1) s = Blocks.DIRT.defaultBlockState();
                        Terrain.set(level, pos, x, h, z, s);
                    }
                    if (turf && n > 0.6F) Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                    continue;
                }
                for (int h = Math.max(b.minY(), ground + 1); h < top; h++) Terrain.set(level, pos, x, h, z, Blocks.GRAVEL.defaultBlockState());
                Terrain.set(level, pos, x, top, z, surface == null ? Blocks.GRAVEL.defaultBlockState() : surface);
                if (x == cx && z == cz) {
                    // the hole in the sea bed the tide spins round
                    Terrain.set(level, pos, x, bottom, z, ModBlocks.WHIRLPOOL.get().defaultBlockState());
                    level.scheduleTick(pos.immutable(), ModBlocks.WHIRLPOOL.get(), 20);
                }
            }
        }
    }
}
