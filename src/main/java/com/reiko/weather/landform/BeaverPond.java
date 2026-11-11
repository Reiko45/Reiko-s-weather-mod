package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.function.BiPredicate;

/**
 * A beaver pond: a stream valley drowned behind a dam that beavers have built across it, a long, curving bank of
 * sticks and logs packed with mud, which they patch every night the water finds a way through. The pond behind it has
 * flooded the woods along the stream, and the trees that stood there have died and lost their bark and branches but
 * still stand in the water. Round the shore are the stumps of the trees the beavers felled for wood, each gnawed to a
 * point, and out in the pond, safe from wolves, is their lodge: a dome of sticks and mud with its doors under water.
 * Below the dam the stream is only a trickle now.
 *
 * @param angle  the way the stream runs (downstream)
 * @param y      the level of the pond
 * @param length the pond's half-length up the valley from the dam
 * @param width  its half-width across the valley
 * @param lodge  {u, v, radius in tenths, height}: where the lodge stands, along the valley from the dam and across it
 */
public record BeaverPond(int cx, int cz, float angle, int y, int length, int width, int[] lodge, long seed) implements Landform {
    /** How far out the banks (and the dam) reach, as a multiple of the pond's size. */
    private static final float RING = 1.35F;
    /** And how far out the beavers have been felling trees. */
    private static final float WOODS = 1.6F;
    /** How far the trickle runs on below the dam. */
    private static final int TRICKLE = 18;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 2 || !c.fits(ground, 14, 26)) {
            return null;
        }
        // a stream valley: falling, but not a cliff
        float[] down = c.downhill(24);
        if (down[1] < 2 || down[1] > 12) {
            return null;
        }
        float angle = down[0];
        int a = 13 + c.random().nextInt(8), b = 9 + c.random().nextInt(5);
        // the pond fills the valley up to where the stream comes in at its head, and no higher than its sides
        int head = at(c, angle, -2 * a + 2, 0), middle = at(c, angle, -a, 0);
        int sides = Math.min(at(c, angle, -a, -b), at(c, angle, -a, b));
        int water = Math.min(Math.min(head - 1, ground + 3), sides + 1);
        if (water < ground + 1 || water < c.seaLevel() + 1 || middle > water) {
            return null;
        }
        // the lodge out in the deep water, toward the dam
        int lu = Math.round(-a * (0.45F + 0.35F * c.random().nextFloat())), lv = Math.round((c.random().nextFloat() - 0.5F) * b * 0.8F);
        if (at(c, angle, lu, lv) > water - 1) {
            lu = Math.round(-a * 0.35F);
            lv = 0;
        }
        int[] lodge = {lu, lv, 22 + c.random().nextInt(11), 2 + c.random().nextInt(3)};
        return new BeaverPond(c.x(), c.z(), angle, water, a, b, lodge, c.random().nextLong());
    }

    /** The ground {@code u} blocks down the valley and {@code v} across it from the dam. */
    private static int at(PlanContext c, float angle, float u, float v) {
        double[] p = Terrain.xz(u, v, c.x() + 0.5, c.z() + 0.5, angle);
        return c.groundAt(Mth.floor(p[0]), Mth.floor(p[1]));
    }

    static Landform load(CompoundTag tag) {
        return new BeaverPond(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("y"), tag.getInt("length"), tag.getInt("width"),
                tag.getIntArray("lodge"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("y", y);
        tag.putInt("length", length);
        tag.putInt("width", width);
        tag.put("lodge", new IntArrayTag(lodge));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.BEAVER_POND;
    }

    @Override
    public BoundingBox bounds() {
        float reach = WOODS * 1.12F;
        float back = -length * (1.0F + reach) - 1.0F, front = Math.max(length * (reach - 1.0F), (RING - 1.0F) * length * 1.12F + TRICKLE) + 2.0F;
        float side = width * reach + 1.0F;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (float u : new float[]{back, front}) {
            for (float v : new float[]{-side, side}) {
                double[] p = Terrain.xz(u, v, cx + 0.5, cz + 0.5, angle);
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minZ = Math.min(minZ, p[1]);
                maxZ = Math.max(maxZ, p[1]);
            }
        }
        return new BoundingBox(Mth.floor(minX) - 1, y - 14, Mth.floor(minZ) - 1, Mth.floor(maxX) + 1, y + 24, Mth.floor(maxZ) + 1);
    }

    private float[] uv(int x, int z) {
        return Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
    }

    /** How far out from the middle of the pond a column is: under 1 in the pond, then its banks and the dam out to {@link #RING}. */
    private float out(int x, int z, float u, float v) {
        return Mth.sqrt(Mth.square((u + length) / length) + Mth.square(v / width)) / (0.9F + 0.2F * Terrain.field(seed, x, z, 9.0));
    }

    /** Where the trickle runs below the dam, across the valley, {@code u} blocks down it. */
    private float trickle(float u) {
        return 2.5F * Mth.sin(u * 0.3F + Terrain.hash(seed, 1) * Mth.TWO_PI);
    }

    /** Whether a column is part of the trickle's bed below the dam. */
    private boolean inTrickle(float u, float v, float e) {
        return e >= RING && u > 0.0F && u < (RING - 1.0F) * length + TRICKLE && Math.abs(v - trickle(u)) < 1.0F;
    }

    private boolean inside(int x, int z) {
        float[] l = uv(x, z);
        float e = out(x, z, l[0], l[1]);
        return e < WOODS || inTrickle(l[0], l[1], e);
    }

    /** Whether a column of the banks is part of the dam (the downstream side of them, across the valley). */
    private boolean dam(float u) {
        return u > -length * 0.55F;
    }

    /**
     * The least height a bank stands to, from how far out it is: a block over the water next to it, the dam's crest a
     * block higher, and then the outer side falling away. Everything within half the bank's width of the water is held
     * at least a block over it.
     */
    private int bank(float u, float e) {
        float t = (e - 1.0F) / (RING - 1.0F);
        if (!dam(u)) return t < 0.5F ? y + 1 : Integer.MIN_VALUE;
        if (t < 0.15F) return y + 1;
        if (t < 0.55F) return y + 2;
        return y + 2 - Math.round((t - 0.55F) / 0.45F * 3.0F);
    }

    /** Whether the pond's water can stand in a column: only where nothing beside it is lower than the banks hold. */
    private boolean held(int x, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int nx = x + d.getStepX(), nz = z + d.getStepZ();
            float[] l = uv(nx, nz);
            float e = out(nx, nz, l[0], l[1]);
            if (e >= 1.0F && (e >= RING || (e - 1.0F) / (RING - 1.0F) >= 0.5F)) return false;
        }
        return true;
    }

    /** How high the lodge stands over the water at a column (MIN_VALUE if it's not there). */
    private int lodge(float u, float v) {
        float d = Mth.sqrt(Mth.square(u - lodge[0]) + Mth.square(v - lodge[1])) / (lodge[2] / 10.0F);
        if (d >= 1.0F) return Integer.MIN_VALUE;
        return Math.round(lodge[3] * (float) Math.pow(1.0F - d * d, 0.6F));
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BiPredicate<Integer, Integer> outside = (nx, nz) -> !inside(nx, nz);
        Direction.Axis along = Math.abs(Mth.cos(angle)) > Math.abs(Mth.sin(angle)) ? Direction.Axis.X : Direction.Axis.Z;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = uv(x, z);
                float u = l[0], v = l[1], e = out(x, z, u, v);
                boolean trickle = inTrickle(u, v, e);
                if (e >= WOODS && !trickle) continue;
                int ground = Goosenecks.ground(level, pos, x, z);
                if (ground > b.maxY() - 4 || trickle && ground < b.minY() + 3) continue;
                int standing = Math.min(b.maxY(), Goosenecks.standing(level, pos, x, ground, z));
                float n = Terrain.noise(seed, x, y, z), m = Terrain.noise(seed + 1, x, y, z);
                // the ground is made sound as deep as the pond goes, with no hollow in it for the water to find
                for (int h = y - 9; h <= Math.min(ground, y); h++) {
                    BlockState here = level.getBlockState(pos.set(x, h, z));
                    if (here.isAir() || !here.getFluidState().isEmpty()) Terrain.set(level, pos, x, h, z, Blocks.DIRT.defaultBlockState());
                }
                int top;
                boolean pond = e < 1.0F && held(x, z), raised = false;
                int lodge = pond ? lodge(u, v) : Integer.MIN_VALUE;
                if (trickle) {
                    top = ground - 1;
                } else if (lodge != Integer.MIN_VALUE) {
                    top = Math.max(ground, y + lodge);
                } else if (pond) {
                    top = ground;
                } else if (e < RING || e < 1.0F) {
                    int bank = e < 1.0F ? y + 1 : bank(u, e);
                    top = Math.max(ground, bank);
                    raised = top > ground;
                } else {
                    top = ground;
                }
                int water = pond && lodge == Integer.MIN_VALUE && top <= y ? y : Integer.MIN_VALUE;
                int open = water != Integer.MIN_VALUE ? water : top;
                if (top != ground || water != Integer.MIN_VALUE || standing > ground) {
                    Terrain.clearAbove(level, pos, x, ground, z, Math.min(14, b.maxY() - ground));
                    for (int h = open + 1; h <= standing; h++) {
                        // (a pond just outside keeps a bank of earth between it and the lowered ground)
                        Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, outside) ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    }
                }
                if (trickle) {
                    // the stream's old bed below the dam, all but dry: stones and mud (and puddles, once the rest is in)
                    Terrain.set(level, pos, x, top - 1, z, Blocks.DIRT.defaultBlockState());
                    Terrain.set(level, pos, x, top, z, n < 0.4F ? Blocks.GRAVEL.defaultBlockState() : n < 0.7F ? Blocks.MUD.defaultBlockState()
                            : n < 0.85F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                    continue;
                }
                if (lodge != Integer.MIN_VALUE) {
                    for (int h = Math.max(ground + 1, y - 9); h <= top; h++) Terrain.set(level, pos, x, h, z, sticks(x, h, z, along, top - h));
                    if (top > y && Terrain.noise(seed + 9, x, top, z) < 0.25F && level.getBlockState(pos.set(x, top, z)).is(BlockTags.DIRT)) {
                        Terrain.set(level, pos, x, top + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                    }
                    continue;
                }
                if (water != Integer.MIN_VALUE) {
                    // drowned ground under the still water (flooding even the flats at its level): soft mud
                    int bed = Math.max(Math.min(ground, y - 1), y - 8);
                    for (int h = Math.max(ground, b.minY()); h < bed; h++) Terrain.set(level, pos, x, h, z, Blocks.DIRT.defaultBlockState());
                    Terrain.set(level, pos, x, bed, z, m < 0.75F ? Blocks.MUD.defaultBlockState() : Blocks.CLAY.defaultBlockState());
                    for (int h = bed + 1; h <= y; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                    if (drowned(x, z)) {
                        // a dead tree still standing in the water, grey and bare
                        Block wood = Terrain.hash(seed, x * 31 + z) < 0.6F ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.STRIPPED_BIRCH_LOG;
                        int tall = y + 2 + Math.round(Terrain.noise(seed + 4, x, 0, z) * 6.0F);
                        for (int h = bed + 1; h <= tall; h++) Terrain.set(level, pos, x, h, z, wood.defaultBlockState());
                    } else if (n < 0.1F) {
                        Terrain.set(level, pos, x, y + 1, z, Blocks.LILY_PAD.defaultBlockState());
                    }
                    continue;
                }
                if (raised) {
                    for (int h = ground + 1; h <= top; h++) {
                        Terrain.set(level, pos, x, h, z, dam(u) ? sticks(x, h, z, damAxis(u, v), top - h) : h == top ? Blocks.GRASS_BLOCK.defaultBlockState()
                                : Blocks.DIRT.defaultBlockState());
                    }
                    if (dam(u)) {
                        topping(level, pos, x, top, z, n);
                    } else if (n < 0.3F) {
                        Terrain.set(level, pos, x, top + 1, z, n < 0.12F ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
                    }
                    continue;
                }
                int tall = Math.max(y + 3, top + 2);
                if (drowned(x, z) && e < 1.0F && clearing(level, pos, x, top + 1, tall, z)) {
                    // a dead tree on the shore, where the water has been and gone
                    for (int h = top + 1; h <= tall; h++) Terrain.set(level, pos, x, h, z, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
                } else if (m > 0.955F && e > 0.75F && level.getBlockState(pos.set(x, top, z)).is(BlockTags.DIRT) && clearing(level, pos, x, top + 1, top + 2, z)) {
                    // a stump, gnawed off where the beavers felled the tree
                    Terrain.set(level, pos, x, top + 1, z, (m > 0.98F ? Blocks.BIRCH_LOG : Blocks.SPRUCE_LOG).defaultBlockState());
                }
            }
        }
        puddles(level, area, pos, b);
    }

    /** Whether there's room for a stump (or a dead tree) on the ground: nothing there but air or a tuft of grass. */
    private static boolean clearing(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int from, int to, int z) {
        for (int h = from; h <= to; h++) {
            BlockState state = level.getBlockState(pos.set(x, h, z));
            if (!state.isAir() && !state.is(Blocks.SHORT_GRASS) && !state.is(Blocks.FERN)) return false;
        }
        return true;
    }

    /** Whether a dead tree stands at a column: at most one to each 5-block square, well out in the pond. */
    private boolean drowned(int x, int z) {
        int i = Math.floorDiv(x, 5), j = Math.floorDiv(z, 5);
        if (x != i * 5 + (int) (Terrain.noise(seed + 5, i, 0, j) * 4.99F) || z != j * 5 + (int) (Terrain.noise(seed + 6, i, 0, j) * 4.99F)) return false;
        float[] l = uv(x, z);
        return Terrain.noise(seed + 7, i, 0, j) < 0.45F && out(x, z, l[0], l[1]) < 0.85F && lodge(l[0], l[1]) == Integer.MIN_VALUE;
    }

    /** Which way the dam runs at a point on it, along the curve of the banks (for the logs laid along it). */
    private Direction.Axis damAxis(float u, float v) {
        float tu = -v / (width * width), tv = (u + length) / (float) (length * length);
        double[] w = Terrain.xz(tu, tv, 0.0, 0.0, angle);
        return Math.abs(w[0]) > Math.abs(w[1]) ? Direction.Axis.X : Direction.Axis.Z;
    }

    /** The stuff of the dam and the lodge: sticks and logs, mostly laid one way, packed with mud. */
    private BlockState sticks(int x, int h, int z, Direction.Axis axis, int depth) {
        float n = Terrain.noise(seed + 3, x, h, z);
        if (n < 0.38F) {
            Direction.Axis a = Terrain.noise(seed + 8, x, h, z) < 0.75F ? axis : axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.Y;
            Block wood = Terrain.hash(seed, h * 7 + x * 3 + z) < 0.6F ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.STRIPPED_OAK_LOG;
            return wood.defaultBlockState().setValue(RotatedPillarBlock.AXIS, a);
        }
        if (n < 0.62F) return Blocks.MUD.defaultBlockState();
        if (n < 0.82F || depth > 1) return Blocks.PACKED_MUD.defaultBlockState();
        return Blocks.COARSE_DIRT.defaultBlockState();
    }

    /** What lies on top of the dam: freshly cut leafy branches and the odd dead bush. */
    private void topping(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int top, int z, float n) {
        if (n > 0.8F) {
            Block leaves = n > 0.9F ? Blocks.OAK_LEAVES : Blocks.SPRUCE_LEAVES;
            Terrain.set(level, pos, x, top + 1, z, leaves.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        } else if (n < 0.12F && level.getBlockState(pos.set(x, top, z)).is(BlockTags.DIRT)) {
            Terrain.set(level, pos, x, top + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
        }
    }

    /**
     * The odd puddle left in the trickle's bed: only where every block round it, in this chunk, is ground (or another
     * puddle) at its level, so none of them can spill.
     */
    private void puddles(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos, BoundingBox b) {
        for (int x = Math.max(area.minX() + 1, b.minX()); x <= Math.min(area.maxX() - 1, b.maxX()); x++) {
            for (int z = Math.max(area.minZ() + 1, b.minZ()); z <= Math.min(area.maxZ() - 1, b.maxZ()); z++) {
                if (Terrain.noise(seed + 10, x, 0, z) > 0.4F) continue;
                float[] l = uv(x, z);
                if (!inTrickle(l[0], l[1], out(x, z, l[0], l[1]))) continue;
                int top = Terrain.ground(level, pos, x, z);
                BlockState bed = level.getBlockState(pos.set(x, top, z));
                if (!bed.is(Blocks.MUD) && !bed.is(Blocks.GRAVEL) || top < b.minY() + 2) continue;
                boolean held = level.getBlockState(pos.set(x, top + 1, z)).isAir();
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockState there = level.getBlockState(pos.set(x + d.getStepX(), top, z + d.getStepZ()));
                    if (there.getFluidState().isEmpty() && !there.blocksMotion()) held = false;
                }
                if (!held) continue;
                Terrain.set(level, pos, x, top - 1, z, Blocks.MUD.defaultBlockState());
                Terrain.set(level, pos, x, top, z, Blocks.WATER.defaultBlockState());
            }
        }
    }
}
