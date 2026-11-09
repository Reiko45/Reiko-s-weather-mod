package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A slot canyon: a crack in the sandstone only an arm's span wide but many times deeper, worn smooth by flash
 * floods into flowing, corkscrewing walls of banded rock. Logs jammed high between the walls show how deep the
 * last flood ran, and when a storm breaks upstream there's nowhere to climb out.
 */
public record SlotCanyon(int cx, int cz, float angle, int length, int[] floor, long seed) implements Landform, FloodChannel {
    private static final int CONTROL = 12;

    @Nullable
    static Landform plan(PlanContext c) {
        int length = 70 + c.random().nextInt(41);
        long seed = c.random().nextLong();
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 10 || !c.fits(ground, 30, 20)) {
            return null;
        }
        // run it the way the land falls, if it falls at all
        float angle = c.downhill(length / 2)[0];
        int[] floor = new int[CONTROL + 1];
        SlotCanyon probe = new SlotCanyon(c.x(), c.z(), angle, length, floor, seed);
        int previous = Integer.MAX_VALUE, shallow = Integer.MAX_VALUE, deep = 0;
        for (int i = 0; i <= CONTROL; i++) {
            float u = (i / (float) CONTROL - 0.5F) * length;
            double[] p = probe.point(u);
            int h = c.groundAt((int) p[0], (int) p[1]);
            if (h < c.seaLevel() + 6) return null;
            previous = Math.min(previous, h - 12 - c.random().nextInt(5)); // never climbing as it goes downstream
            floor[i] = Math.max(previous, c.seaLevel() + 2);
            if (i > 1 && i < CONTROL - 1) {
                shallow = Math.min(shallow, h - floor[i]);
                deep = Math.max(deep, h - floor[i]);
            }
        }
        // a slot needs a good thickness of rock over it all the way, and not a mountain
        return shallow >= 8 && deep <= 36 ? probe : null;
    }

    static Landform load(CompoundTag tag) {
        return new SlotCanyon(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("length"), tag.getIntArray("floor"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.put("floor", new IntArrayTag(floor));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.SLOT_CANYON;
    }

    @Override
    public BoundingBox bounds() {
        int r = length / 2 + 12;
        int min = Integer.MAX_VALUE;
        for (int f : floor) min = Math.min(min, f);
        return new BoundingBox(cx - r, min - 2, cz - r, cx + r, min + 50, cz + r);
    }

    private double wiggle(float u) {
        float p1 = Terrain.hash(seed, 1) * Mth.TWO_PI, p2 = Terrain.hash(seed, 2) * Mth.TWO_PI;
        return 4.0 * Mth.sin(u * 0.09F + p1) + 1.6 * Mth.sin(u * 0.27F + p2);
    }

    /** The middle of the slot just above the bed (where a flood runs). */
    @Override
    public double[] point(float u) {
        return Terrain.xz(u, wiggle(u) + shift(u, 1.0F), cx, cz, angle);
    }

    @Override
    public float floorAt(float u) {
        float t = Mth.clamp((u / length + 0.5F) * CONTROL, 0.0F, CONTROL);
        int i = Math.min(CONTROL - 1, (int) t);
        return Mth.lerp(t - i, floor[i], floor[i + 1]);
    }

    @Override
    public float halfWidth(float u) {
        return 1.1F;
    }

    @Override
    public float[] local(double x, double z) {
        float[] l = Terrain.uv(x, z, cx, cz, angle);
        l[1] -= (float) wiggle(l[0]);
        return l;
    }

    /** The slot at a height above the bed: how far its middle has swung aside, and how wide it is. */
    private float shift(float u, float above) {
        return 1.3F * Mth.sin(above * 0.33F + u * 0.06F + Terrain.hash(seed, 3) * 6.0F) + 0.6F * Mth.sin(above * 0.71F - u * 0.13F);
    }

    private float[] slot(float u, float above, float depth) {
        float shift = shift(u, above);
        float width = 0.8F + 0.5F * Terrain.field(seed, u, above, 6.0) + 0.35F * Mth.sin(above * 0.9F + u * 0.2F);
        // flaring open toward the rim, and a little wider at the bed where the floods scour
        width += Math.max(0.0F, above - (depth - 4.0F)) * 0.65F + (above < 2.0F ? 0.4F : 0.0F);
        return new float[]{shift, width};
    }

    private static BlockState band(int h, float wobble, boolean red) {
        return switch (Math.floorMod(h + Math.round(wobble * 3.0F), 9)) {
            case 0, 1 -> red ? Blocks.RED_SANDSTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState();
            case 2 -> Blocks.ORANGE_TERRACOTTA.defaultBlockState();
            case 3, 4 -> red ? Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState() : Blocks.SMOOTH_SANDSTONE.defaultBlockState();
            case 5 -> Blocks.TERRACOTTA.defaultBlockState();
            case 6 -> red ? Blocks.RED_SANDSTONE.defaultBlockState() : Blocks.CUT_SANDSTONE.defaultBlockState();
            case 7 -> Blocks.RED_SANDSTONE.defaultBlockState();
            default -> Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        };
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float half = length / 2.0F;
        // logs jammed across the slot run the other way to it
        Direction.Axis across = Math.abs(Mth.cos(angle)) > Math.abs(Mth.sin(angle)) ? Direction.Axis.Z : Direction.Axis.X;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = local(x + 0.5, z + 0.5);
                float u = l[0], v = l[1];
                if (Math.abs(u) > half || Math.abs(v) > 7.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                // it starts as a crack and opens out where it leaves the rock
                float ends = Terrain.smooth((half - Math.abs(u)) / 10.0F);
                int bed = Math.round(Mth.lerp(ends, ground - 1, floorAt(u)));
                if (bed >= ground - 1) continue;
                boolean red = level.getBiome(pos.set(x, ground, z)).is(BiomeTags.IS_BADLANDS);
                float wobble = Terrain.field(seed, x, z, 9.0);
                float depth = ground - bed;
                boolean any = false;
                for (int h = bed - 1; h <= ground; h++) {
                    float above = h - bed;
                    float[] s = slot(u, above, depth);
                    float off = Math.abs(v - s[0]);
                    // past the ends the slot is a broad wash
                    float width = s[1] + (1.0F - ends) * 4.0F;
                    pos.set(x, h, z);
                    if (h > bed && off <= width) {
                        BlockState here = level.getBlockState(pos);
                        if (!here.isAir()) any = true;
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Terrain.FLAGS);
                    } else if (off <= width + 2.5F && h < ground && !level.getBlockState(pos).isAir() && level.getFluidState(pos).isEmpty()) {
                        // the walls: banded sandstone, whatever the ground was made of
                        level.setBlock(pos, band(h, wobble, red), Terrain.FLAGS);
                    }
                }
                if (!any) continue;
                Terrain.clearAbove(level, pos, x, ground, z, 10);
                float[] s0 = slot(u, 1.0F, depth);
                if (Math.abs(v - s0[0]) <= s0[1]) {
                    Terrain.set(level, pos, x, bed, z, Terrain.noise(seed, x, bed, z) > 0.8F ? Blocks.GRAVEL.defaultBlockState()
                            : red ? Blocks.RED_SAND.defaultBlockState() : Blocks.SAND.defaultBlockState());
                    Terrain.set(level, pos, x, bed - 1, z, band(bed - 1, wobble, red));
                }
                // flood debris: a log wedged across the slot, high over your head
                int jam = bed + 6 + Math.round(Terrain.hash(seed, 50 + Math.round(u / 9.0F)) * 4.0F);
                if (Terrain.hash(seed, 80 + Math.round(u / 9.0F)) < 0.35F && Math.floorMod(Math.round(u), 9) == 0 && jam < ground - 2) {
                    float[] sj = slot(u, jam - bed, depth);
                    if (Math.abs(v - sj[0]) <= sj[1] && sj[1] < 1.6F) {
                        Terrain.set(level, pos, x, jam, z, Blocks.STRIPPED_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, across));
                    }
                }
            }
        }
    }

    @Override
    public void tick(ServerLevel level, ServerPlayer player) {
        Arroyo.flood(level, player, this, BlockPos.asLong(cx, 1, cz), length / 2.0F, bounds(), 3);
    }
}
