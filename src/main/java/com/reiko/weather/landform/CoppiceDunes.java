package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Coppice dunes (nebkhas): a scatter of little mounds of sand, each heaped round a shrub. Where the wind strips the
 * loose sand off a flat, a bush slows the air passing through it, and the sand it was carrying drops in its shelter.
 * The bush grows up through the pile to stay above it, and its roots hold the sand together, so the mound and the
 * shrub grow together, with a tail of sand trailing downwind. Between them the ground is scoured hard and bare.
 *
 * @param y     the ground at the middle (for the bounds)
 * @param angle the way the wind blows (downwind)
 */
public record CoppiceDunes(int cx, int cz, int y, int radius, float angle, long seed) implements Landform {
    private static final int CELL = 8;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        int radius = 30 + c.random().nextInt(16);
        if (ground < c.seaLevel() + 2 || c.relief(Math.round(radius * 0.7F), 8) > 10 || !c.fits(ground, 16, 20)) {
            return null;
        }
        return new CoppiceDunes(c.x(), c.z(), ground, radius, c.random().nextFloat() * Mth.TWO_PI, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new CoppiceDunes(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getFloat("angle"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.putFloat("angle", angle);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.COPPICE_DUNES;
    }

    @Override
    public BoundingBox bounds() {
        int r = Mth.ceil(radius / 0.85F) + 2;
        return new BoundingBox(cx - r, y - 18, cz - r, cx + r, y + 24, cz + r);
    }

    private float field(double x, double z) {
        return (float) Mth.length(x - cx, z - cz) / (radius * (0.85F + 0.3F * Terrain.field(seed, x, z, 14.0)));
    }

    /**
     * The mound in a cell of a loose grid round the middle, as {x, z, radius x 10, height}, or null if the cell has
     * none. Each cell holds at most one, kept clear of the cell's edges so they seldom run together.
     */
    @Nullable
    private int[] mound(int gx, int gz) {
        long cell = seed + gx * 341873128712L + gz * 132897987541L;
        if (Terrain.hash(cell, 0) > 0.72F) return null;
        int x = cx + gx * CELL + 2 + Math.round(Terrain.hash(cell, 1) * (CELL - 4)), z = cz + gz * CELL + 2 + Math.round(Terrain.hash(cell, 2) * (CELL - 4));
        if (field(x + 0.5, z + 0.5) > 0.9F) return null;
        float r = 2.0F + Terrain.hash(cell, 3) * 2.8F;
        int h = 1 + Math.round(Terrain.hash(cell, 4) * Math.min(3.0F, r - 1.0F));
        return new int[]{x, z, Math.round(r * 10.0F), h};
    }

    /** How much sand mound {@code m} heaps on a point: a dome round the shrub with a tail trailing downwind. */
    private float rise(int[] m, double x, double z) {
        float[] l = Terrain.uv(x, z, m[0] + 0.5, m[1] + 0.5, angle);
        float a = l[0] > 0.0F ? l[0] / 1.4F : l[0];
        float d2 = (a * a + l[1] * l[1]) / Mth.square(m[2] / 10.0F);
        return d2 >= 1.0F ? 0.0F : m[3] * (float) Math.pow(1.0F - d2, 1.2);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float f = field(x + 0.5, z + 0.5);
                if (f >= 1.0F) continue;
                // the mounds whose sand reaches here (from this cell and the ones round it), and the shrub nearest
                int gx = Math.floorDiv(x - cx, CELL), gz = Math.floorDiv(z - cz, CELL);
                float rise = 0.0F;
                int[] shrub = null;
                float near = Float.MAX_VALUE;
                for (int i = gx - 1; i <= gx + 1; i++) {
                    for (int k = gz - 1; k <= gz + 1; k++) {
                        int[] m = mound(i, k);
                        if (m == null) continue;
                        rise = Math.max(rise, rise(m, x + 0.5, z + 0.5));
                        float d = (float) Mth.length(x - m[0], z - m[1]);
                        if (d < near) {
                            near = d;
                            shrub = m;
                        }
                    }
                }
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 4 || ground > b.maxY() - 12) continue;
                if (!level.getFluidState(pos.set(x, ground + 1, z)).isEmpty()) continue;
                float n = Terrain.noise(seed, x, 0, z);
                // the scoured flat fades out into the land round about in patches
                float edge = Terrain.smooth((1.0F - f) / 0.2F);
                if (rise < 0.5F && n > edge * 2.0F) continue;
                boolean red = level.getBiome(pos.set(x, ground, z)).is(BiomeTags.IS_BADLANDS);
                BlockState sand = red ? Blocks.RED_SAND.defaultBlockState() : Blocks.SAND.defaultBlockState();
                int top = ground + Math.round(rise);
                Terrain.clearAbove(level, pos, x, ground, z, 12);
                // bare, wind-scoured ground: hard crusts of mud and coarse dirt between drifts of sand, and a lag of gravel
                float scour = Terrain.field(seed + 1, x, z, 4.0);
                BlockState flat = n < 0.04F ? Blocks.GRAVEL.defaultBlockState() : scour < 0.38F ? Blocks.COARSE_DIRT.defaultBlockState()
                        : scour < 0.6F ? Blocks.PACKED_MUD.defaultBlockState() : sand;
                if (top > ground || flat.is(Blocks.GRAVEL) || flat == sand) {
                    // anything loose needs something solid under it
                    Terrain.set(level, pos, x, ground - 1, z, red ? Blocks.RED_SANDSTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState());
                }
                Terrain.set(level, pos, x, ground, z, top > ground ? sand : flat);
                for (int h = ground + 1; h <= top; h++) Terrain.set(level, pos, x, h, z, sand);
                if (shrub == null || top <= ground) continue;
                // the shrub the mound grew round: a stout stem and a low crown of leaves over the top of the sand
                boolean big = shrub[3] >= 2 && shrub[2] >= 28;
                long cell = seed + shrub[0] * 31L + shrub[1] * 17L;
                boolean acacia = Terrain.hash(cell, 5) < 0.65F;
                BlockState leaves = (acacia ? Blocks.ACACIA_LEAVES : Blocks.OAK_LEAVES).defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                if (x == shrub[0] && z == shrub[1]) {
                    if (big) {
                        Terrain.set(level, pos, x, top + 1, z, (acacia ? Blocks.ACACIA_LOG : Blocks.OAK_LOG).defaultBlockState());
                        Terrain.set(level, pos, x, top + 2, z, leaves);
                        if (Terrain.hash(cell, 6) < 0.5F) Terrain.set(level, pos, x, top + 3, z, leaves);
                    } else {
                        Terrain.set(level, pos, x, top + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                    }
                } else if (big && near < 1.0F + 0.8F * Terrain.hash(cell, 7) && Terrain.noise(seed, x, top, z) < 0.8F) {
                    Terrain.set(level, pos, x, top + 1, z, leaves);
                }
            }
        }
    }
}
