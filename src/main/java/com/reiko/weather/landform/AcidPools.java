package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Acid pools (like Dallol in the Danakil Depression): a hydrothermal field where groundwater heated by magma not far
 * below seeps up through beds of salt and sulphur. It reaches the surface as hot, strongly acid brine that bubbles up
 * in sickly yellow-green pools, and as it evaporates it crusts the ground round them in rings of yellow sulphur, orange
 * iron oxides and white salt. Here and there it simmers up through clay instead, as a pot of grey, plopping mud. Nothing
 * grows anywhere near.
 *
 * @param y     the level of the field
 * @param pools {x, z, radius x 10, depth} for each pool
 * @param pots  {x, z, radius x 10} for each mud pot
 */
public record AcidPools(int cx, int cz, int y, int radius, int[] pools, int[] pots, long seed) implements Landform {
    private static final int EDGE = 6;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        int radius = 18 + c.random().nextInt(9);
        if (ground < c.seaLevel() + 2 || c.relief(radius, 8) > 12 || !c.fits(ground, 12, 20)) {
            return null;
        }
        int sum = ground;
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F + 0.4F;
            sum += c.groundAt(c.x() + Math.round(Mth.cos(a) * radius * 0.6F), c.z() + Math.round(Mth.sin(a) * radius * 0.6F));
        }
        int y = Math.round(sum / 9.0F);
        // the pools and mud pots, kept apart so each has its rings of crust all round it
        int want = 4 + c.random().nextInt(5), potsWanted = 1 + c.random().nextInt(2);
        int[] pools = new int[want * 4], pots = new int[potsWanted * 3];
        int placed = 0, potsPlaced = 0;
        float room = radius * 0.8F;
        for (int attempt = 0; attempt < 200 && (placed < want || potsPlaced < potsWanted); attempt++) {
            boolean pot = placed >= want || placed >= 3 && potsPlaced < potsWanted && c.random().nextInt(3) == 0;
            float r = pot ? 1.5F + c.random().nextFloat() : 2.0F + 3.0F * c.random().nextFloat();
            float a = c.random().nextFloat() * Mth.TWO_PI, d = Mth.sqrt(c.random().nextFloat()) * room;
            int x = c.x() + Math.round(Mth.cos(a) * d), z = c.z() + Math.round(Mth.sin(a) * d);
            boolean clear = (float) Mth.length(x - c.x(), z - c.z()) + r * 1.1F <= room;
            for (int k = 0; k < placed && clear; k++) {
                clear = (float) Mth.length(x - pools[k * 4], z - pools[k * 4 + 1]) > 1.1F * (r + pools[k * 4 + 2] / 10.0F) + 3.0F;
            }
            for (int k = 0; k < potsPlaced && clear; k++) {
                clear = (float) Mth.length(x - pots[k * 3], z - pots[k * 3 + 1]) > r + pots[k * 3 + 2] / 10.0F + 5.0F;
            }
            if (!clear) continue;
            if (pot) {
                pots[potsPlaced * 3] = x;
                pots[potsPlaced * 3 + 1] = z;
                pots[potsPlaced * 3 + 2] = Math.round(r * 10.0F);
                potsPlaced++;
            } else {
                pools[placed * 4] = x;
                pools[placed * 4 + 1] = z;
                pools[placed * 4 + 2] = Math.round(r * 10.0F);
                pools[placed * 4 + 3] = r > 3.0F ? 2 + c.random().nextInt(2) : 1 + c.random().nextInt(2);
                placed++;
            }
        }
        if (placed < 4) {
            return null;
        }
        return new AcidPools(c.x(), c.z(), y, radius, java.util.Arrays.copyOf(pools, placed * 4), java.util.Arrays.copyOf(pots, potsPlaced * 3),
                c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new AcidPools(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getIntArray("pools"), tag.getIntArray("pots"),
                tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.put("pools", new IntArrayTag(pools));
        tag.put("pots", new IntArrayTag(pots));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.ACID_POOLS;
    }

    @Override
    public BoundingBox bounds() {
        // (the field's edge wobbles out by up to 8%, and the ring beyond it where standing water is sealed in)
        int r = Mth.ceil((radius + EDGE + 1.5F) * 1.08F) + 1;
        return new BoundingBox(cx - r, y - 14, cz - r, cx + r, y + 26, cz + r);
    }

    /** How far out from the middle a column is, against the field's radius. */
    private float out(int x, int z) {
        return (float) Mth.length(x + 0.5 - cx, z + 0.5 - cz) / (0.92F + 0.16F * Terrain.field(seed, x, z, 8.0));
    }

    /** The top of a column {@code r} out once it's built, given its ground. */
    private int surface(float r, int ground) {
        return r > radius + EDGE ? ground : Math.round(Mth.lerp(Terrain.smooth((radius + EDGE - r) / EDGE), ground, y));
    }

    /**
     * Turns to tuff any water left standing on a column the field leaves alone, as high as the ground beside it is cut
     * down below it, so none of it can pour out over the field.
     */
    private void seal(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, int ground) {
        int top = ground;
        while (top < b.maxY() && !level.getFluidState(pos.set(x, top + 1, z)).isEmpty()) top++;
        int low = top;
        for (int[] s : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) low = Math.min(low, surface(out(x + s[0], z + s[1]), top));
        for (int h = Math.max(ground, low) + 1; h <= top; h++) Terrain.set(level, pos, x, h, z, Blocks.TUFF.defaultBlockState());
    }

    /** How far a point is outside the edge of the nearest pool (below 0 in it), and which pool that is. */
    private float[] pool(double x, double z) {
        int best = -1;
        float edge = Float.MAX_VALUE;
        for (int i = 0; i < pools.length / 4; i++) {
            float r = pools[i * 4 + 2] / 10.0F * (0.9F + 0.2F * Terrain.field(seed + i, x, z, 3.0));
            float d = (float) Mth.length(x - pools[i * 4] - 0.5, z - pools[i * 4 + 1] - 0.5) - r;
            if (d < edge) {
                edge = d;
                best = i;
            }
        }
        return new float[]{edge, best};
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState sulfur = ModBlocks.SULFUR_CRUST.get().defaultBlockState();
        BlockState vent = ModBlocks.VOLCANIC_VENT.get().defaultBlockState();
        boolean wet = false;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float r = out(x, z);
                if (r > radius + EDGE + 1.5F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground > b.maxY() - 16) continue;
                if (r > radius + EDGE) {
                    seal(level, pos, b, x, z, ground);
                    continue;
                }
                // the field itself is built however low the ground under it, so no pool can find a way out
                if (ground < b.minY() + 6 && r > radius) continue;
                // water in the way is filled in first (anything deeper than a puddle at the edge is sealed in)
                int pond = 0;
                while (pond < (r <= radius ? 12 : 4) && !level.getFluidState(pos.set(x, ground + 1 + pond, z)).isEmpty()) pond++;
                if (pond >= 4 && r > radius || ground + pond > b.maxY() - 16) {
                    seal(level, pos, b, x, z, ground);
                    continue;
                }
                for (int h = Math.max(b.minY(), ground + 1); h <= ground + pond; h++) Terrain.set(level, pos, x, h, z, Blocks.TUFF.defaultBlockState());
                pos.set(x, ground, z);
                BlockState natural = level.getBlockState(pos);
                ground += pond;
                int surface = surface(r, ground);
                // nothing grows anywhere near: the whole field is stripped bare
                Terrain.clearAbove(level, pos, x, ground, z, Math.min(16, b.maxY() - ground));
                for (int h = surface + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                float n = Terrain.noise(seed, x, 0, z);
                if (r > radius) {
                    // the edge: the natural ground brought to the field's level, bare and gravelly
                    for (int h = Math.min(ground, surface) - 1; h < surface; h++) Terrain.set(level, pos, x, h, z, Blocks.TUFF.defaultBlockState());
                    Terrain.set(level, pos, x, surface, z, n < 0.3F ? Blocks.GRAVEL.defaultBlockState() : n < 0.6F ? Blocks.COARSE_DIRT.defaultBlockState()
                            : natural.isAir() || !natural.getFluidState().isEmpty() ? Blocks.COARSE_DIRT.defaultBlockState() : natural);
                    continue;
                }
                // a crust of tuff and salt under it all, solid enough to hold the pools in
                for (int h = Math.max(b.minY(), Math.min(ground, surface) - 4); h < surface; h++) {
                    Terrain.set(level, pos, x, h, z, Terrain.noise(seed, x, h, z) < 0.6F ? Blocks.TUFF.defaultBlockState() : Blocks.CALCITE.defaultBlockState());
                }
                float[] p = pool(x + 0.5, z + 0.5);
                float edge = p[0] + 0.6F * (Terrain.field(seed + 3, x, z, 2.5) - 0.5F);
                int i = (int) p[1];
                if (p[0] < 0.0F) {
                    // the pool: hot brine bubbling up from a vent in the middle
                    wet = true;
                    int depth = Math.max(1, Math.round(pools[i * 4 + 3] * Math.min(1.0F, -p[0] / 1.5F)));
                    float mid = (float) Mth.length(x - pools[i * 4], z - pools[i * 4 + 1]);
                    Terrain.set(level, pos, x, surface - depth, z, mid < 0.9F ? vent : n < 0.5F ? sulfur : Blocks.YELLOW_TERRACOTTA.defaultBlockState());
                    for (int h = surface - depth + 1; h <= surface; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                    continue;
                }
                BlockState top;
                if (edge < 1.0F) {
                    top = sulfur; // brimstone crusted right round the brim
                } else if (edge < 2.0F) {
                    top = n < 0.7F ? Blocks.YELLOW_TERRACOTTA.defaultBlockState() : sulfur;
                } else if (edge < 3.0F) {
                    top = Blocks.ORANGE_TERRACOTTA.defaultBlockState(); // iron oxides
                } else if (edge < 4.2F) {
                    top = n < 0.6F ? Blocks.WHITE_TERRACOTTA.defaultBlockState() : Blocks.CALCITE.defaultBlockState(); // salt
                } else {
                    // dead ground: grit, gravel and old crust
                    float g = Terrain.field(seed + 4, x, z, 4.0);
                    top = g < 0.35F ? Blocks.GRAVEL.defaultBlockState() : g < 0.7F ? Blocks.COARSE_DIRT.defaultBlockState()
                            : n < 0.5F ? Blocks.TUFF.defaultBlockState() : Blocks.WHITE_TERRACOTTA.defaultBlockState();
                }
                Terrain.set(level, pos, x, surface, z, top);
                pot(level, pos, x, z, surface, vent);
            }
        }
        // the acid brine is a sickly yellow-green
        if (wet) Terrain.setBiome(level, area, y - 6, y + 3, (x, z) -> pool(x + 0.5, z + 0.5)[0] < 1.5F, Terrain.ACID_POOL_BIOME);
    }

    /** A mud pot, if there's one here: a little cone of grey mud with a pool of murky water simmering in its throat. */
    private void pot(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int surface, BlockState vent) {
        for (int k = 0; k < pots.length / 3; k++) {
            float d = (float) Mth.length(x - pots[k * 3], z - pots[k * 3 + 1]), r = pots[k * 3 + 2] / 10.0F;
            float throat = r * 0.5F;
            if (d >= throat + 2.5F) continue;
            if (d < throat) {
                // the throat: water held in by the cone round it, over mud and a vent
                Terrain.set(level, pos, x, surface - 1, z, vent);
                Terrain.set(level, pos, x, surface, z, Blocks.MUD.defaultBlockState());
                Terrain.set(level, pos, x, surface + 1, z, Blocks.WATER.defaultBlockState());
            } else if (d < throat + 1.5F) {
                Terrain.set(level, pos, x, surface, z, Blocks.MUD.defaultBlockState());
                Terrain.set(level, pos, x, surface + 1, z, Blocks.MUD.defaultBlockState()); // the cone's rim
            } else {
                Terrain.set(level, pos, x, surface, z, Blocks.MUD.defaultBlockState());
            }
            return;
        }
    }
}
