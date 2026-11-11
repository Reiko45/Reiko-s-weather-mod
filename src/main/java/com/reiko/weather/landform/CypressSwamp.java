package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MangroveRootsBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.function.BiPredicate;

/**
 * A cypress swamp: a forest standing in still, shallow, tea-dark water. Bald cypresses are among the few trees that
 * can live with their roots drowned for months on end: they grow huge and slow, their trunks swelling into broad
 * buttresses at the waterline, and up into wide, flat crowns hung with grey curtains of moss. All round each tree,
 * "knees" of wood stick up out of the water from the roots below; no one is quite sure what they're for (breathing,
 * or anchoring the tree in the soft mud). Between the trees the water is covered in lily pads, with hummocks of mud
 * and ferns rising out of it here and there.
 *
 * @param y     the level of the water
 * @param trees {x, z, height over the water, crown radius, kind} for each cypress (kinds: 0 and 1 have trunks two
 *              blocks thick, of dark oak and spruce; 2 and 3 single trunks flaring at the foot)
 */
public record CypressSwamp(int cx, int cz, int y, int radius, int[] trees, long seed) implements Landform {
    private static final int F = 5;
    /** How far out the banks reach, as a multiple of the swamp's (wavering) radius. */
    private static final float RING = 1.3F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() - 3 || ground > c.seaLevel() + 8 || !c.fits(ground, 10, 32)) {
            return null;
        }
        int radius = 26 + c.random().nextInt(13);
        // low, level ground
        if (c.relief(radius, 12) > 7) {
            return null;
        }
        // the water stands a block under the lowest point of the land round it (and never below the sea's level)
        int low = Integer.MAX_VALUE;
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2.0 / 12;
            low = Math.min(low, c.groundAt(c.x() + (int) Math.round(Math.cos(a) * radius), c.z() + (int) Math.round(Math.sin(a) * radius)));
        }
        int water = Math.max(low - 1, c.seaLevel() - 1);
        if (ground > water + 6) {
            return null;
        }
        int n = 11 + c.random().nextInt(8);
        int[] trees = new int[n * F];
        int placed = 0;
        for (int attempt = 0; attempt < 60 && placed < n; attempt++) {
            float a = c.random().nextFloat() * Mth.TWO_PI, d = Mth.sqrt(c.random().nextFloat()) * radius * 0.68F;
            int x = c.x() + Math.round(Mth.cos(a) * d), z = c.z() + Math.round(Mth.sin(a) * d);
            int height = 12 + c.random().nextInt(9), crown = height > 16 ? 5 + c.random().nextInt(2) : 4 + c.random().nextInt(2);
            // spaced so their crowns just about meet
            boolean clear = true;
            for (int k = 0; k < placed && clear; k++) {
                if (Mth.sqrt(Mth.square(x - trees[k * F]) + Mth.square(z - trees[k * F + 1])) < crown + trees[k * F + 3] - 1.0F) clear = false;
            }
            if (!clear) continue;
            trees[placed * F] = x;
            trees[placed * F + 1] = z;
            trees[placed * F + 2] = height;
            trees[placed * F + 3] = crown;
            trees[placed * F + 4] = height > 15 ? c.random().nextInt(2) : c.random().nextInt(4);
            placed++;
        }
        if (placed < 6) {
            return null;
        }
        return new CypressSwamp(c.x(), c.z(), water, radius, java.util.Arrays.copyOf(trees, placed * F), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new CypressSwamp(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getIntArray("trees"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.put("trees", new IntArrayTag(trees));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.CYPRESS_SWAMP;
    }

    @Override
    public BoundingBox bounds() {
        int r = Mth.ceil(radius * RING * 1.18F) + 2;
        return new BoundingBox(cx - r, y - 8, cz - r, cx + r, y + 26, cz + r);
    }

    /** How far out a column is: under 1 in the swamp, out to {@link #RING} on its banks. */
    private float out(int x, int z) {
        return Mth.sqrt(Mth.square(x + 0.5F - cx - 0.5F) + Mth.square(z + 0.5F - cz - 0.5F)) / (radius * (0.82F + 0.36F * Terrain.field(seed, x, z, 18.0)));
    }

    /** Whether a column is in the swamp or close enough to its edge to hold its water in (and so a block over it at least). */
    private boolean nearWater(float e) {
        return e < 1.0F || (e - 1.0F) / (RING - 1.0F) < 0.5F;
    }

    /** The muddy floor under the water at a column of the swamp. */
    private int bed(int x, int z) {
        float f = Terrain.field(seed + 1, x, z, 7.0);
        return y - 1 - (f > 0.5F ? 1 : 0) - (f > 0.75F ? 1 : 0);
    }

    /** How high a hummock of mud rises over the water at a column of the swamp (0 for none), more of them toward its edge. */
    private int hummock(int x, int z, float e) {
        float f = Terrain.field(seed + 2, x, z, 6.0), at = 0.72F - Math.max(0.0F, e - 0.8F) * 2.0F;
        return f > at + 0.12F ? 2 : f > at ? 1 : 0;
    }

    /** Whether the water stands in a column: in the swamp, off the hummocks, and only where every side of it is held. */
    private boolean wet(int x, int z) {
        float e = out(x, z);
        if (e >= 1.0F || hummock(x, z, e) > 0) return false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (!nearWater(out(x + d.getStepX(), z + d.getStepZ()))) return false;
        }
        return true;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BiPredicate<Integer, Integer> outside = (nx, nz) -> out(nx, nz) >= RING;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float e = out(x, z);
                if (e >= RING) continue;
                int ground = Goosenecks.ground(level, pos, x, z);
                if (ground > b.maxY() - 4) continue;
                int standing = Math.min(b.maxY(), Goosenecks.standing(level, pos, x, ground, z));
                // the swamp's own water running on out into the water round it (at the sea's level) is left as it is
                if (e >= 1.0F && y == level.getSeaLevel() - 1 && ground < y && standing == y) continue;
                boolean wet = wet(x, z);
                int top;
                if (e < 1.0F) {
                    top = wet ? bed(x, z) : y + Math.max(1, hummock(x, z, e));
                } else {
                    // the banks, never lower than a block over the water near it, easing back into the land round about
                    float t = (e - 1.0F) / (RING - 1.0F);
                    // (and never below the water's level at all, so no water round about at that level is let out)
                    top = Math.max(Math.round(Mth.lerp(Terrain.smooth(t), y + 1, ground)), nearWater(e) ? y + 1 : y);
                }
                int open = wet ? y : top;
                boolean changed = e < 1.0F || top != ground || standing > ground;
                if (changed) {
                    Terrain.clearAbove(level, pos, x, ground, z, Math.min(12, b.maxY() - ground));
                    for (int h = open + 1; h <= standing; h++) {
                        // (a pond just outside keeps a bank of mud between it and the lowered ground)
                        Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, outside) ? Blocks.MUD.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    }
                }
                // sound mud and clay under it all, filling any hollow the water could find
                for (int h = Math.max(b.minY(), y - 5); h < top; h++) {
                    BlockState here = level.getBlockState(pos.set(x, h, z));
                    if (h > ground || changed && h >= top - 2 || here.isAir() || !here.getFluidState().isEmpty() || Terrain.cover(here)) {
                        Terrain.set(level, pos, x, h, z, h < top - 2 ? Blocks.CLAY.defaultBlockState() : Blocks.MUD.defaultBlockState());
                    }
                }
                float n = Terrain.noise(seed, x, y, z), m = Terrain.noise(seed + 3, x, y, z);
                if (wet) {
                    Terrain.set(level, pos, x, top, z, n < 0.75F ? Blocks.MUD.defaultBlockState() : Blocks.CLAY.defaultBlockState());
                    for (int h = top + 1; h <= y; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                    // lily pads in drifts across the open water
                    if (Terrain.field(seed + 4, x, z, 8.0) > 0.55F && m < 0.45F) Terrain.set(level, pos, x, y + 1, z, Blocks.LILY_PAD.defaultBlockState());
                    continue;
                }
                if (top == ground && e >= 1.0F) continue;
                // the hummocks and banks: mud, moss and grass, with ferns
                float f = Terrain.field(seed + 5, x, z, 4.0);
                Terrain.set(level, pos, x, top, z, f > 0.6F ? Blocks.MOSS_BLOCK.defaultBlockState() : f > 0.35F || e >= 1.0F ? Blocks.GRASS_BLOCK.defaultBlockState()
                        : Blocks.MUD.defaultBlockState());
                if (m < 0.4F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.FERN.defaultBlockState());
                } else if (m < 0.55F) {
                    Terrain.set(level, pos, x, top + 1, z, f > 0.6F ? Blocks.MOSS_CARPET.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
                }
            }
        }
        // the cypresses, once the swamp is in round them (any that reach into this chunk): all their trunks, then all
        // their crowns, then the moss hanging from them, so that every strand of it hangs from a leaf
        for (int stage = 0; stage < 4; stage++) {
            for (int i = 0; i < trees.length / F; i++) {
                int tx = trees[i * F], tz = trees[i * F + 1], crown = trees[i * F + 3];
                if (tx + crown + 2 < area.minX() || tx - crown - 2 > area.maxX() || tz + crown + 2 < area.minZ() || tz - crown - 2 > area.maxZ()) continue;
                tree(level, area, pos, i, stage);
            }
        }
    }

    /**
     * A bald cypress: a thick trunk flaring at the waterline (stage 0), a broad flat crown (1) hung with moss (2), and
     * knees all round it (3).
     */
    private void tree(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos, int i, int stage) {
        int tx = trees[i * F], tz = trees[i * F + 1], height = trees[i * F + 2], crown = trees[i * F + 3], kind = trees[i * F + 4];
        boolean dark = kind % 2 == 0, thick = kind < 2;
        Block wood = dark ? Blocks.DARK_OAK_LOG : Blocks.SPRUCE_LOG;
        BlockState leaves = (dark ? Blocks.DARK_OAK_LEAVES : Blocks.SPRUCE_LEAVES).defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        int top = y + height, span = thick ? 2 : 1;
        float mx = tx + span / 2.0F, mz = tz + span / 2.0F;
        if (stage == 0) trunk(level, area, pos, i, tx, tz, top, span, thick, wood);
        if (stage == 1) {
            // the crown: broad and flat, a few blocks deep, ragged at the edge
            for (int dy = -1; dy <= 1; dy++) {
                for (int x = Mth.floor(mx - crown - 1); x <= Mth.ceil(mx + crown + 1); x++) {
                    for (int z = Mth.floor(mz - crown - 1); z <= Mth.ceil(mz + crown + 1); z++) {
                        if (crown(x, z, top, dy, mx, mz, crown)) leaf(level, area, pos, x, top + dy, z, leaves);
                    }
                }
            }
        }
        if (stage == 2) {
            // moss hanging in curtains from the rim of the crown
            for (int x = Mth.floor(mx - crown - 1); x <= Mth.ceil(mx + crown + 1); x++) {
                for (int z = Mth.floor(mz - crown - 1); z <= Mth.ceil(mz + crown + 1); z++) {
                    if (!crown(x, z, top, -1, mx, mz, crown)) continue;
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        int vx = x + d.getStepX(), vz = z + d.getStepZ();
                        if (Terrain.noise(seed + 9 + d.ordinal(), x, top, z) > 0.4F || !area.isInside(vx, top - 1, vz)) continue;
                        BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(d.getOpposite()), true);
                        int length = 2 + Math.round(Terrain.noise(seed + 13, vx, top, vz) * 7.0F);
                        for (int h = top - 1; h > top - 1 - length && h > y + 1; h--) {
                            pos.set(vx, h, vz);
                            if (!level.getBlockState(pos).isAir()) break;
                            level.setBlock(pos, vine, Terrain.FLAGS);
                        }
                    }
                }
            }
        }
        if (stage == 3) knees(level, area, pos, i, mx, mz, span, dark);
    }

    /** A cypress's trunk, from down in the mud, with buttresses flaring round its foot and great limbs reaching out up top. */
    private void trunk(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos, int i, int tx, int tz, int top, int span, boolean thick,
                       Block wood) {
        int crown = trees[i * F + 3];
        for (int dx = 0; dx < span; dx++) {
            for (int dz = 0; dz < span; dz++) {
                for (int h = y - 3; h <= top; h++) Terrain.setIn(level, area, pos, tx + dx, h, tz + dz, wood.defaultBlockState());
            }
        }
        // the buttresses flaring out round its foot
        for (int dx = -1; dx <= span; dx++) {
            for (int dz = -1; dz <= span; dz++) {
                boolean side = (dx == -1 || dx == span) != (dz == -1 || dz == span);
                if (!side || Terrain.noise(seed + 6, tx + dx, i, tz + dz) > 0.75F) continue;
                int rise = y + 1 + Math.round(Terrain.noise(seed + 7, tx + dx, i, tz + dz) * (thick ? 3.0F : 2.0F));
                for (int h = y - 3; h <= rise; h++) Terrain.setIn(level, area, pos, tx + dx, h, tz + dz, wood.defaultBlockState());
                // (a fern left standing on top of it, where it rose through a hummock, is taken away)
                if (area.isInside(tx + dx, rise + 1, tz + dz)) {
                    BlockState above = level.getBlockState(pos.set(tx + dx, rise + 1, tz + dz));
                    if (!above.isAir() && above.canBeReplaced() && above.getFluidState().isEmpty()) Terrain.set(level, pos, tx + dx, rise + 1, tz + dz, Blocks.AIR.defaultBlockState());
                }
            }
        }
        // a few great limbs reaching out under the crown
        for (int k = 0; k < 4; k++) {
            if (Terrain.hash(seed, 300 + i * 8 + k) < 0.3F) continue;
            Direction d = Direction.from2DDataValue(k);
            int h = top - 2 - Math.round(Terrain.hash(seed, 310 + i * 8 + k));
            BlockState limb = wood.defaultBlockState().setValue(RotatedPillarBlock.AXIS, d.getAxis());
            int bx = d.getStepX() > 0 ? tx + span - 1 : tx, bz = d.getStepZ() > 0 ? tz + span - 1 : tz;
            for (int s = 1; s <= crown - 2; s++) Terrain.setIn(level, area, pos, bx + d.getStepX() * s, h, bz + d.getStepZ() * s, limb);
        }
    }

    /** The knees: knobs of root standing up out of the water all round a cypress. */
    private void knees(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos, int i, float mx, float mz, int span, boolean dark) {
        for (int x = Mth.floor(mx - 5); x <= Mth.ceil(mx + 5); x++) {
            for (int z = Mth.floor(mz - 5); z <= Mth.ceil(mz + 5); z++) {
                float d = Mth.sqrt(Mth.square(x + 0.5F - mx) + Mth.square(z + 0.5F - mz));
                if (d < span + 0.8F || d > 4.8F || Terrain.noise(seed + 14, x, i, z) > 0.24F || !area.isInside(x, y, z) || !wet(x, z)) continue;
                pos.set(x, y, z);
                if (!level.getBlockState(pos).is(Blocks.WATER)) continue;
                int rise = y + 1 + (Terrain.noise(seed + 15, x, i, z) < 0.35F ? 1 : 0);
                boolean roots = Terrain.noise(seed + 16, x, i, z) < 0.4F;
                for (int h = bed(x, z) + 1; h <= rise; h++) {
                    BlockState knee = roots ? Blocks.MANGROVE_ROOTS.defaultBlockState().setValue(MangroveRootsBlock.WATERLOGGED, h <= y)
                            : (dark ? Blocks.STRIPPED_DARK_OAK_LOG : Blocks.STRIPPED_SPRUCE_LOG).defaultBlockState();
                    Terrain.set(level, pos, x, h, z, knee);
                }
            }
        }
    }

    /** Whether a block {@code dy} above the top of a cypress's trunk is part of its crown (something solid, if not a leaf). */
    private boolean crown(int x, int z, int top, int dy, float mx, float mz, int crown) {
        float r = dy < 0 ? crown : dy == 0 ? crown - 0.8F : crown - 2.5F;
        return Mth.sqrt(Mth.square(x + 0.5F - mx) + Mth.square(z + 0.5F - mz)) <= r + 0.6F * (Terrain.noise(seed + 8, x, top + dy, z) - 0.5F);
    }

    /** Puts a leaf in a block of air in this chunk. */
    private static void leaf(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState leaves) {
        if (!area.isInside(x, y, z)) return;
        pos.set(x, y, z);
        if (level.getBlockState(pos).isAir()) level.setBlock(pos, leaves, Terrain.FLAGS);
    }
}
