package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A brine pool: a lake at the bottom of the sea. Where a bed of salt buried under the sea floor (left by a sea that
 * dried up ages ago) dissolves into the water seeping through it, the brine that oozes out is so much heavier than
 * the sea water that it doesn't mix, but gathers in a hollow of the bed as a lake with a surface and a shore of its
 * own. It holds no oxygen and poisons anything that swims into it, so its floor is littered with the bones of the
 * unlucky; but the methane that seeps up with it feeds bacteria living inside mussels, and the mussels crowd its shore
 * in a thick, pale bed.
 *
 * @param y     the level of the brine's surface
 * @param depth how deep the brine is in the middle
 */
public record BrinePool(int cx, int cz, int y, int radius, int depth, long seed) implements Landform {
    /** How wide the bank of mussels round the shore is, and how far out round it the sea bed is made good. */
    private static final float SHORE = 3.5F, SEAL = 2.5F;

    @Nullable
    static Landform plan(PlanContext c) {
        int floor = c.groundAt(c.x(), c.z());
        if (floor > c.seaLevel() - 14 || !c.fits(floor, 12, 8)) {
            return null;
        }
        int radius = 6 + c.random().nextInt(9);
        // a fairly level stretch of the deep sea bed, with nothing rising anywhere near the surface
        int low = floor, high = floor, sum = floor;
        for (int i = 0; i < 10; i++) {
            float a = i * Mth.TWO_PI / 10.0F;
            int h = c.groundAt(c.x() + Math.round(Mth.cos(a) * (radius + 3)), c.z() + Math.round(Mth.sin(a) * (radius + 3)));
            low = Math.min(low, h);
            high = Math.max(high, h);
            sum += h;
        }
        if (high - low > 7 || high > c.seaLevel() - 13) {
            return null;
        }
        // the brine stands a little under the lie of the sea bed round it, cut down into it on the high side and banked up on the low
        return new BrinePool(c.x(), c.z(), Math.round(sum / 11.0F) - 1, radius, 2 + c.random().nextInt(4), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new BrinePool(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getInt("depth"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.putInt("depth", depth);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.BRINE_POOL;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * 1.12F + SHORE + SEAL) + 2;
        return new BoundingBox(cx - r, y - depth - 6, cz - r, cx + r, y + 14, cz + r);
    }

    /** How far out from the middle a point is: under 1 in the brine, 1 at its shore. */
    private float out(double x, double z) {
        double dx = x - cx - 0.5, dz = z - cz - 0.5;
        float theta = (float) Math.atan2(dz, dx);
        float r = radius * (0.95F + 0.07F * Mth.sin(2.0F * theta + Terrain.hash(seed, 1) * Mth.TWO_PI)
                + 0.04F * Mth.sin(5.0F * theta + Terrain.hash(seed, 2) * Mth.TWO_PI) + 0.12F * (Terrain.field(seed + 1, x, z, 5.0) - 0.5F));
        return (float) Math.sqrt(dx * dx + dz * dz) / r;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int bottom = y - depth - 1;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float d = out(x + 0.5, z + 0.5);
                float beyond = (d - 1.0F) * radius; // blocks out from the shore
                if (beyond > SHORE + SEAL) continue;
                int ground = Terrain.ground(level, pos, x, z);
                while (ground > b.minY() && !level.getFluidState(pos.set(x, ground, z)).isEmpty()) ground--;
                // only on the open sea bed, well under the water
                if (ground < b.minY() + 2 || ground > b.maxY() - 4 || level.getFluidState(pos.set(x, ground + 1, z)).isEmpty()) continue;
                float n = Terrain.noise(seed, x, 0, z);
                // first, nothing round the pool may open into a cave or a crack under the bed
                for (int h = bottom; h <= ground; h++) {
                    pos.set(x, h, z);
                    if (!level.getBlockState(pos).blocksMotion()) level.setBlock(pos, Blocks.TUFF.defaultBlockState(), Terrain.FLAGS);
                }
                if (d < 1.0F) {
                    pool(level, pos, x, z, ground, d, n);
                } else if (beyond < SHORE) {
                    shore(level, pos, x, z, ground, beyond, n);
                }
            }
        }
        // the brine, dark and murky in the hollow under the clear sea water
        Terrain.setBiome(level, area, bottom - 2, y, (px, pz) -> out(px + 0.5, pz + 0.5) < 1.0F, Terrain.BRINE_POOL_BIOME);
    }

    /** A column of the pool: the hollow, its floor of black ooze (and the odd bone), and the brine filling it. */
    private void pool(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, float d, float n) {
        int floor = y - Math.max(1, Math.round(depth * Mth.sqrt(1.0F - d * d)));
        // where the sea bed stands higher than the brine, the hollow's side shelves down into it rather than dropping sheer
        if (ground > y && d > 0.7F) floor = Math.max(floor, Math.min(ground, y + Math.round((ground - y) * Terrain.smooth((d - 0.7F) / 0.3F))));
        clearWeed(level, pos, x, Math.min(ground, floor), z, y + 14);
        for (int h = floor + 1; h <= Math.max(ground, y); h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
        float m = Terrain.noise(seed + 3, x, floor, z);
        BlockState bed;
        if (floor >= y) {
            bed = m < 0.4F ? Blocks.CALCITE.defaultBlockState() : m < 0.75F ? Blocks.MUD.defaultBlockState() : Blocks.TUFF.defaultBlockState();
        } else {
            bed = m < 0.55F ? Blocks.MUD.defaultBlockState() : m < 0.85F ? Blocks.CLAY.defaultBlockState() : Blocks.TUFF.defaultBlockState();
            if (n > 0.985F && d < 0.85F) bed = Blocks.BONE_BLOCK.defaultBlockState();
        }
        Terrain.set(level, pos, x, floor, z, bed);
        if (floor >= y && n > 0.7F) {
            Terrain.set(level, pos, x, floor + 1, z, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + (int) (n * 400.0F) % 4));
        }
        // (and where the old bed dipped deeper than the pool's floor, ooze fills it up)
        for (int h = Math.min(ground + 1, floor - 1); h < floor; h++) Terrain.set(level, pos, x, h, z, Blocks.MUD.defaultBlockState());
    }

    /** A column of the shore: a low bank of mussel shells and mud, crowded with life. */
    private void shore(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, float beyond, float n) {
        // heaped highest just back from the brine's edge, and never lower than a block over it
        float hump = Terrain.smooth(1.0F - Math.abs(beyond - 1.0F) / (SHORE - 1.0F));
        float heap = hump * (0.6F + 1.2F * Terrain.field(seed + 4, x, z, 4.0));
        int lip = Math.max(y + 1 + Math.round(heap), ground + Math.round(heap * 0.8F));
        int top = beyond < 1.6F ? lip : Math.max(ground, Math.round(Mth.lerp(Terrain.smooth((beyond - 1.6F) / (SHORE - 1.6F)), lip, ground)));
        clearWeed(level, pos, x, ground, z, y + 14);
        for (int h = ground; h <= top; h++) {
            float m = Terrain.noise(seed + 5, x, h, z);
            BlockState s;
            if (h == top) {
                // the mussel bed: drifts of pale shell, with dark mud between
                s = m < 0.5F ? Blocks.CALCITE.defaultBlockState() : m < 0.78F ? Blocks.TUFF.defaultBlockState() : Blocks.MUD.defaultBlockState();
            } else {
                s = m < 0.6F ? Blocks.TUFF.defaultBlockState() : Blocks.MUD.defaultBlockState();
            }
            Terrain.set(level, pos, x, h, z, s);
        }
        if (n > (beyond < 2.2F ? 0.55F : 0.85F)) {
            Terrain.set(level, pos, x, top + 1, z, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + (int) (n * 400.0F) % 4));
        }
    }

    /** Takes out any sea grass or kelp rooted in a column's old bed above {@code from}, which would be left buried or hanging. */
    private static void clearWeed(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int from, int z, int to) {
        for (int h = from + 1; h <= to; h++) {
            pos.set(x, h, z);
            BlockState was = level.getBlockState(pos);
            if (was.is(Blocks.KELP) || was.is(Blocks.KELP_PLANT) || was.is(Blocks.SEAGRASS) || was.is(Blocks.TALL_SEAGRASS)) {
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), Terrain.FLAGS);
            }
        }
    }
}
