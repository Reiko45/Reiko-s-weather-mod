package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A stone forest: a crowd of tall, thin pinnacles of grey limestone packed so close that the ways between them are
 * only a shoulder wide. Rain soaking down the joints in the rock dissolved it into deep slots, first under a cover of
 * soil and then in the open once the soil washed away, until only these blades and spires were left, fluted from top
 * to bottom by the rain running down them. Ferns and grass grow in the shade at their feet, and here and there the
 * water collects in a still pool.
 *
 * @param pools {x, z, radius, water y} for each pool (radius 0 for none)
 */
public record StoneForest(int cx, int cz, int y, int radius, int[] pools, long seed) implements Landform {
    private static final float CELL = 4.6F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 2) {
            return null;
        }
        int radius = 25 + c.random().nextInt(11);
        if (c.relief(radius * 2 / 3, 8) > 12 || !c.fits(ground, 10, 42)) {
            return null;
        }
        int n = 2 + c.random().nextInt(2);
        int[] pools = new int[n * 4];
        for (int i = 0; i < n; i++) {
            float a = c.random().nextFloat() * Mth.TWO_PI, d = (0.2F + 0.55F * c.random().nextFloat()) * radius;
            int x = c.x() + Math.round(Mth.cos(a) * d), z = c.z() + Math.round(Mth.sin(a) * d);
            int r = 2 + c.random().nextInt(3);
            // the water stands a block under the lowest point of its rim
            int low = c.groundAt(x, z);
            for (int k = 0; k < 4; k++) {
                float b = k * Mth.HALF_PI + 0.4F;
                low = Math.min(low, c.groundAt(x + Math.round(Mth.cos(b) * (r + 1)), z + Math.round(Mth.sin(b) * (r + 1))));
            }
            boolean dry = low < c.seaLevel() + 1 || low < ground - 12;
            // never so close to another pool that their banks run into each other
            for (int k = 0; k < i; k++) {
                if (pools[k * 4 + 2] > 0 && Mth.square(x - pools[k * 4]) + Mth.square(z - pools[k * 4 + 1]) < Mth.square(2.2F * (r + pools[k * 4 + 2]) + 3.0F)) dry = true;
            }
            pools[i * 4] = x;
            pools[i * 4 + 1] = z;
            pools[i * 4 + 2] = dry ? 0 : r;
            pools[i * 4 + 3] = low - 1;
        }
        return new StoneForest(c.x(), c.z(), ground, radius, pools, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new StoneForest(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getIntArray("pools"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.put("pools", new IntArrayTag(pools));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.STONE_FOREST;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * 1.15F) + 6;
        return new BoundingBox(cx - r, y - 16, cz - r, cx + r, y + 46, cz + r);
    }

    private static float field3(long seed, double x, double h, double z, double scale, double vertical) {
        double g = h / vertical;
        int k = Mth.floor(g);
        float t = Terrain.smooth((float) (g - k));
        return Mth.lerp(t, Terrain.field(seed + k * 7919L, x, z, scale), Terrain.field(seed + (k + 1) * 7919L, x, z, scale));
    }

    /** How far toward the middle of the field a point is (1 in the middle, 0 at the edge): the pinnacles shrink toward it. */
    private float inner(float px, float pz) {
        float d = Mth.sqrt(Mth.square(px - cx) + Mth.square(pz - cz)) / (radius * (0.85F + 0.3F * Terrain.field(seed + 1, px, pz, 12.0)));
        return 1.0F - d;
    }

    /** How high pinnacle (i, j) of the grid stands over the ground at a point (0 outside it). */
    private float pinnacle(int i, int j, float px, float pz) {
        if (Terrain.noise(seed, i, 0, j) < 0.12F) return 0.0F; // a little clearing among the stones
        float ox = (i + 0.2F + 0.6F * Terrain.noise(seed, i, 1, j)) * CELL, oz = (j + 0.2F + 0.6F * Terrain.noise(seed, i, 2, j)) * CELL;
        float in = inner(ox, oz);
        if (in <= 0.0F) return 0.0F;
        float h = (8.0F + 17.0F * Terrain.noise(seed, i, 4, j)) * (0.3F + 0.7F * Terrain.smooth(in / 0.35F));
        if (h < 2.5F) return 0.0F;
        // some are round spires, some long blades along a joint in the rock
        float r = 0.7F + 1.3F * Terrain.noise(seed, i, 3, j);
        float stretch = Math.min(1.0F + 0.6F * Terrain.noise(seed, i, 6, j), 3.0F / r);
        float a = Terrain.noise(seed, i, 5, j) * Mth.PI, ca = Mth.cos(a), sa = Mth.sin(a);
        float dx = px - ox, dz = pz - oz;
        float along = dx * ca + dz * sa, across = -dx * sa + dz * ca;
        // fluted: grooves run down the sides, so the outline's scalloped
        float flute = 1.0F + 0.14F * Mth.sin(6.0F * (float) Mth.atan2(across, along) + i * 1.7F + j * 2.3F);
        float q = Mth.sqrt(Mth.square(along / stretch) + across * across) / (r * flute);
        if (q >= 1.2F) return 0.0F;
        if (q >= 1.0F) return Math.min(h, 2.0F) * (1.2F - q) / 0.2F; // a little foot where it meets the ground
        // near-vertical sides drawing in to a sharp point at the top
        return h * (float) Math.pow(1.0F - Math.pow(q, 1.43F), 0.33F);
    }

    private float tallest(float px, float pz) {
        int gi = Mth.floor(px / CELL), gj = Mth.floor(pz / CELL);
        float best = 0.0F;
        for (int i = gi - 1; i <= gi + 1; i++) {
            for (int j = gj - 1; j <= gj + 1; j++) {
                best = Math.max(best, pinnacle(i, j, px, pz));
            }
        }
        return best;
    }

    private BlockState rock(int x, int h, int z, int ground) {
        // grey limestone streaked down its length with darker rock
        float streak = field3(seed + 9, x, h, z, 2.0, 8.0);
        if (streak > 0.8F) return Blocks.STONE.defaultBlockState();
        if (streak < 0.12F) return Blocks.ANDESITE.defaultBlockState();
        if (h <= ground + 1 && Terrain.noise(seed, x, h, z) < 0.3F) return Blocks.MOSS_BLOCK.defaultBlockState(); // damp at the foot
        return ModBlocks.LIMESTONE.get().defaultBlockState();
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float px = x + 0.5F, pz = z + 0.5F;
                if (inner(px, pz) < -0.12F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 4 || ground > b.maxY() - 30) continue;
                int wet = ground;
                for (pos.set(x, ground + 1, z); wet < ground + 8 && !level.getFluidState(pos).isEmpty(); pos.move(net.minecraft.core.Direction.UP)) wet++;
                boolean flooded = wet > ground;
                // the nearest pool
                int pool = -1;
                float pd = Float.MAX_VALUE;
                for (int k = 0; k < pools.length / 4; k++) {
                    if (pools[k * 4 + 2] == 0) continue;
                    float dd = Mth.sqrt(Mth.square(px - pools[k * 4]) + Mth.square(pz - pools[k * 4 + 1]))
                            / (pools[k * 4 + 2] * (0.85F + 0.3F * Terrain.field(seed + 2 + k, px, pz, 3.0)));
                    if (dd < pd) {
                        pd = dd;
                        pool = k;
                    }
                }
                float tall = tallest(px, pz);
                int rockTop = tall >= 0.5F ? ground + Math.round(tall) : Integer.MIN_VALUE;
                int surface = ground;
                int water = pool >= 0 ? pools[pool * 4 + 3] : 0;
                boolean inPool = pool >= 0 && pd < 1.0F && !flooded;
                if (inPool) {
                    // a still pool among the stones, a block or two deep
                    int bed = water - (pd < 0.5F ? 2 : 1);
                    Terrain.clearAbove(level, pos, x, Math.max(ground, water), z, 12);
                    for (int h = water + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    for (int h = Math.min(ground + 1, bed - 1); h <= bed; h++) {
                        Terrain.set(level, pos, x, h, z, Terrain.noise(seed, x, h, z) < 0.6F ? Blocks.MUD.defaultBlockState() : Blocks.CLAY.defaultBlockState());
                    }
                    for (int h = bed + 1; h <= water; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                    surface = bed;
                } else if (pool >= 0 && pd < 2.0F) {
                    // its banks, never lower than a block over the water (and damming any other water there)
                    int bank = flooded ? Math.max(water + 1, wet) : Math.round(Mth.lerp(Math.max(0.0F, pd - 1.0F), water + 1, Math.max(ground, water + 1)));
                    if (bank != ground) {
                        Terrain.clearAbove(level, pos, x, Math.max(ground, bank), z, 12);
                        for (int h = bank + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                        for (int h = ground; h < bank; h++) Terrain.set(level, pos, x, h, z, Blocks.DIRT.defaultBlockState());
                        Terrain.set(level, pos, x, bank, z, Blocks.GRASS_BLOCK.defaultBlockState());
                        surface = bank;
                    }
                    flooded = false;
                }
                if (rockTop > Integer.MIN_VALUE) {
                    // a pinnacle, rising out of the ground (or the pool)
                    rockTop = Math.max(rockTop, surface + 1);
                    Terrain.clearAbove(level, pos, x, surface, z, Math.min(rockTop - surface + 6, b.maxY() - surface));
                    for (int h = Math.min(surface, ground) - 1; h <= Math.min(rockTop, b.maxY()); h++) Terrain.set(level, pos, x, h, z, rock(x, h, z, surface));
                    continue;
                }
                if (inPool) {
                    if (Terrain.noise(seed, x, 6, z) > 0.9F && water + 1 <= b.maxY()) Terrain.set(level, pos, x, water + 1, z, Blocks.LILY_PAD.defaultBlockState());
                    continue;
                }
                if (flooded) continue;
                plant(level, area, pos, x, z, surface, tall);
            }
        }
    }

    /** Grass and ferns at the feet of the stones, a few bushes in the gaps. */
    private void plant(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos, int x, int z, int surface, float tall) {
        pos.set(x, surface, z);
        if (!level.getBlockState(pos).is(BlockTags.DIRT)) return;
        pos.set(x, surface + 1, z);
        if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).is(Blocks.SHORT_GRASS)) return;
        float n = Terrain.noise(seed, x, 7, z);
        if (Terrain.field(seed + 8, x, z, 3.0) > 0.78F) {
            Terrain.set(level, pos, x, surface + 1, z, (n < 0.5F ? Blocks.OAK_LEAVES : Blocks.JUNGLE_LEAVES).defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        } else if (n > 0.96F && level.getBlockState(pos.set(x, surface + 2, z)).isAir()) {
            Terrain.set(level, pos, x, surface + 1, z, Blocks.LARGE_FERN.defaultBlockState());
            Terrain.set(level, pos, x, surface + 2, z, Blocks.LARGE_FERN.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
        } else if (n > 0.6F || tall > 0.0F && n > 0.35F) {
            Terrain.set(level, pos, x, surface + 1, z, Blocks.FERN.defaultBlockState()); // ferns most of all in the shade of the stones
        } else if (n > 0.3F) {
            Terrain.set(level, pos, x, surface + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
        }
    }
}
