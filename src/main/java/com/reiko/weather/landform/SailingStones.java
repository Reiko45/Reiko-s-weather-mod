package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A racetrack playa and its sailing stones: a dry lakebed so flat and so hard that its cracked mud barely shows a
 * footprint, with boulders lying out on it, each at the end of a long, faint groove. Nobody saw them move for
 * years: on rare winter mornings, a skin of rainwater on the playa freezes into thin sheets of ice, and as it
 * breaks up in the sun a light wind pushes the panes along, shoving the stones ahead of them and dragging their
 * tracks through the soft mud underneath. So the tracks all run roughly downwind, wandering and turning as the
 * ice did.
 *
 * @param y     the level of the playa
 * @param wind  the way the tracks run (towards the stones)
 */
public record SailingStones(int cx, int cz, int y, int rx, int rz, float angle, float wind, long seed) implements Landform {
    private static final float BLEND = 1.35F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        int rx = 20 + c.random().nextInt(11), rz = 16 + c.random().nextInt(10);
        if (ground < c.seaLevel() + 2 || !c.fits(ground, 8, 16)) {
            return null;
        }
        int min = ground, max = ground;
        for (int i = 0; i < 10; i++) {
            float a = i * Mth.TWO_PI / 10.0F;
            int g = c.groundAt(c.x() + Math.round(Mth.cos(a) * rx), c.z() + Math.round(Mth.sin(a) * rx));
            min = Math.min(min, g);
            max = Math.max(max, g);
        }
        // (and well clear of any river or lake it could let in)
        if (max - min > 14 || min < c.seaLevel() + 1) {
            return null;
        }
        // a basin: the playa lies at the lowest of the ground round it
        return new SailingStones(c.x(), c.z(), min, rx, rz, c.random().nextFloat() * Mth.PI, c.random().nextFloat() * Mth.TWO_PI, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new SailingStones(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("rx"), tag.getInt("rz"), tag.getFloat("angle"),
                tag.getFloat("wind"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("rx", rx);
        tag.putInt("rz", rz);
        tag.putFloat("angle", angle);
        tag.putFloat("wind", wind);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.SAILING_STONES;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(Math.max(rx, rz) * BLEND * 1.08F) + 2;
        return new BoundingBox(cx - r, y - 6, cz - r, cx + r, y + 14, cz + r);
    }

    /** How far out on the playa a point is: under 1 on the flat, with a slightly ragged shore. */
    private float shore(double x, double z) {
        float[] l = Terrain.uv(x, z, cx, cz, angle);
        return Mth.sqrt(Mth.square(l[0] / rx) + Mth.square(l[1] / rz)) / (0.93F + 0.14F * Terrain.field(seed, x, z, 11.0));
    }

    /**
     * Each stone and its track: {stone x, stone z, direction, length, wiggle, wiggle frequency, phase, turn start,
     * turn, half width, track block (0 mud, 1 brown terracotta), stone block (0 andesite, 1 stone)}.
     */
    private float[][] tracks() {
        int count = 8 + Math.round(Terrain.hash(seed, 0) * 7.0F);
        float[][] tracks = new float[count][];
        for (int i = 0; i < count; i++) {
            // the stones have mostly ended up on the downwind half of the playa
            float a = Terrain.hash(seed, 10 + i) * Mth.TWO_PI, r = 0.15F + 0.6F * Mth.sqrt(Terrain.hash(seed, 20 + i));
            double[] p = Terrain.xz(Mth.cos(a) * r * rx, Mth.sin(a) * r * rz, cx, cz, angle);
            float[] w = Terrain.uv(p[0], p[1], cx, cz, wind);
            if (w[0] < -rx * 0.2F) {
                p = Terrain.xz(-w[0] * 0.8F, w[1], cx, cz, wind);
            }
            float length = 14.0F + Terrain.hash(seed, 30 + i) * 22.0F;
            boolean big = Terrain.hash(seed, 40 + i) < 0.3F;
            boolean turns = Terrain.hash(seed, 50 + i) < 0.35F;
            tracks[i] = new float[]{(float) p[0], (float) p[1], wind + (Terrain.hash(seed, 60 + i) - 0.5F) * 0.55F, length,
                    0.5F + Terrain.hash(seed, 70 + i) * 1.6F, 0.07F + Terrain.hash(seed, 80 + i) * 0.12F, Terrain.hash(seed, 90 + i) * Mth.TWO_PI,
                    turns ? length * (0.3F + 0.4F * Terrain.hash(seed, 100 + i)) : length, (Terrain.hash(seed, 110 + i) - 0.5F) * 1.0F,
                    big ? 1.0F : 0.55F, Terrain.hash(seed, 120 + i) < 0.6F ? 0 : 1, Terrain.hash(seed, 130 + i) < 0.6F ? 0 : 1};
        }
        return tracks;
    }

    /** How far the track swings to the side {@code s} blocks along it from its start (0 at the stone, at the end). */
    private static float wander(float[] t, float s) {
        return side(t, s) - side(t, t[3]);
    }

    private static float side(float[] t, float s) {
        // a gentle wobble, and sometimes a sharp turn partway along where the wind shifted
        return t[4] * Mth.sin(s * t[5] + t[6]) + Math.max(0.0F, s - t[7]) * t[8];
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float[][] tracks = tracks();
        BlockState crust = ModBlocks.CRACKED_MUD.get().defaultBlockState();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float e = shore(x + 0.5, z + 0.5);
                if (e > BLEND) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 2 || ground > b.maxY() - 2 || !level.getFluidState(pos.set(x, ground + 1, z)).isEmpty()) continue;
                boolean red = level.getBiome(pos.set(x, ground, z)).is(BiomeTags.IS_BADLANDS);
                BlockState sand = red ? Blocks.RED_SAND.defaultBlockState() : Blocks.SAND.defaultBlockState();
                float n = Terrain.noise(seed, x, y, z);
                if (e >= 1.0F) {
                    // the shore: the land round about sloping gently down to the flat, washed with sand and grit
                    int top = Math.round(Mth.lerp(Terrain.smooth((e - 1.0F) / (BLEND - 1.0F)), y, ground));
                    if (top == ground) continue;
                    Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, Math.min(10, b.maxY() - Math.max(ground, top)));
                    for (int h = top + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    for (int h = ground + 1; h <= top; h++) Terrain.set(level, pos, x, h, z, red ? Blocks.RED_SANDSTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState());
                    Terrain.set(level, pos, x, top, z, n < 0.25F ? Blocks.GRAVEL.defaultBlockState() : sand);
                    continue;
                }
                // the playa: dead flat, a hard crust of cracked mud with paler patches of salt and silt
                Terrain.clearAbove(level, pos, x, Math.max(ground, y), z, Math.min(10, b.maxY() - Math.max(ground, y)));
                for (int h = y + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                for (int h = Math.min(ground + 1, y - 3); h <= y - 2; h++) Terrain.set(level, pos, x, h, z, Blocks.WHITE_TERRACOTTA.defaultBlockState());
                Terrain.set(level, pos, x, y - 1, z, Blocks.CLAY.defaultBlockState());
                BlockState surface = crust;
                if (Terrain.field(seed + 1, x, z, 6.0) > 0.76F) surface = Blocks.WHITE_TERRACOTTA.defaultBlockState();
                else if (Terrain.field(seed + 2, x, z, 8.0) < 0.18F) surface = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
                int stone = -1;
                boolean middle = false;
                for (int i = 0; i < tracks.length; i++) {
                    float[] t = tracks[i];
                    float dx = x + 0.5F - t[0], dz = z + 0.5F - t[1];
                    // the stone sits at the far end of its track (the bigger ones spread over a few blocks)
                    boolean centre = x == Mth.floor(t[0]) && z == Mth.floor(t[1]);
                    if (centre || t[9] > 0.9F && dx * dx + dz * dz < 1.1F) {
                        stone = i;
                        middle = centre;
                        continue;
                    }
                    float c = Mth.cos(t[2]), s = Mth.sin(t[2]);
                    float along = t[3] + dx * c + dz * s, across = -dx * s + dz * c;
                    if (along < 0.0F || along > t[3] || e > 0.97F) continue;
                    if (Math.abs(across - wander(t, along)) < t[9] && Terrain.noise(seed + i, x, 5, z) < 0.92F) {
                        surface = t[10] == 0 ? Blocks.MUD.defaultBlockState() : Blocks.BROWN_TERRACOTTA.defaultBlockState();
                    }
                }
                Terrain.set(level, pos, x, y, z, surface);
                if (stone >= 0) {
                    float[] t = tracks[stone];
                    BlockState rock = t[11] == 0 ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    Terrain.set(level, pos, x, y + 1, z, rock);
                    // the bigger stones stand two blocks high in the middle
                    if (t[9] > 0.9F && middle) Terrain.set(level, pos, x, y + 2, z, rock);
                }
            }
        }
    }
}
