package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Tafoni: a sandstone headland whose seaward face has been eaten into a honeycomb of hollows. Salt spray soaks into
 * the rock and dries there, and the salt crystals growing in its pores prise the grains apart. A hollow that starts
 * this way holds the damp and the salt longer than the rock round it, so it deepens faster than the face wears back:
 * pits grow into cavities, cavities run together into little caves, and the thin ribs and lips left between them,
 * toughened by a crust of iron and lime, stand out like the walls of a wasps' nest. Rust from the iron in the sandstone
 * stains the insides of the hollows orange.
 *
 * @param angle  the way out to sea
 * @param height how high the clifftop stands above the sea
 * @param bulge  how far the headland juts out to sea
 */
public record Tafoni(int cx, int cz, int sea, float angle, int length, int height, int bulge, long seed) implements Landform {
    private static final BlockState SANDSTONE = Blocks.SANDSTONE.defaultBlockState(), SMOOTH = Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
            CUT = Blocks.CUT_SANDSTONE.defaultBlockState(), STAIN = Blocks.ORANGE_TERRACOTTA.defaultBlockState();
    private static final float CELL_V = 3.2F, CELL_Y = 3.0F;

    @Nullable
    static Landform plan(PlanContext c) {
        return c.search(12, 12, Tafoni::planAt);
    }

    @Nullable
    private static Landform planAt(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        // right on the shore
        if (ground < c.seaLevel() - 3 || ground > c.seaLevel() + 8) {
            return null;
        }
        float seaward = c.seaward(20, 2);
        if (Float.isNaN(seaward)) {
            return null;
        }
        return new Tafoni(c.x(), c.z(), c.seaLevel(), seaward, 46 + c.random().nextInt(28), 8 + c.random().nextInt(13), 4 + c.random().nextInt(8),
                c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Tafoni(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("height"),
                tag.getInt("bulge"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("sea", sea);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putInt("height", height);
        tag.putInt("bulge", bulge);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.TAFONI;
    }

    private static final float BACK = 30.0F, FRONT = 20.0F;

    @Override
    public BoundingBox bounds() {
        float half = length / 2.0F + 1.0F;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (float u : new float[]{-BACK - 1.0F, bulge + FRONT + 1.0F}) {
            for (float v : new float[]{-half, half}) {
                double[] p = Terrain.xz(u, v, cx, cz, angle);
                minX = Math.min(minX, Mth.floor(p[0]));
                maxX = Math.max(maxX, Mth.ceil(p[0]));
                minZ = Math.min(minZ, Mth.floor(p[1]));
                maxZ = Math.max(maxZ, Mth.ceil(p[1]));
            }
        }
        return new BoundingBox(minX, sea - 16, minZ, maxX, sea + height + 18, maxZ);
    }

    /** Whether the headland reaches a column at all. */
    private boolean inside(int x, int z) {
        float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
        return Math.abs(l[1]) <= length / 2.0F && l[0] >= -BACK && l[0] <= bulge + FRONT;
    }

    /** Where the cliff face stands, {@code v} along the coast: jutting out into a headland in the middle. */
    private float face(float v) {
        float t = v / (length / 2.0F);
        return bulge * Math.max(0.0F, 1.0F - t * t) + 1.2F * Mth.sin(v * 0.13F + Terrain.hash(seed, 1) * 6.0F)
                + 0.8F * Mth.sin(v * 0.31F + Terrain.hash(seed, 2) * 6.0F);
    }

    /**
     * How far inside the nearest hollow a block of the face is (under 1 is inside it), {@code e} blocks behind the face.
     * The hollows sit in a loose honeycomb across the face, crowded together in some patches and missing from others.
     */
    private float hollow(float v, int h, float e) {
        int ci = Mth.floor(v / CELL_V), cj = Mth.floor(h / CELL_Y);
        float best = 9.0F;
        for (int i = ci - 1; i <= ci + 1; i++) {
            for (int j = cj - 1; j <= cj + 1; j++) {
                float low = Mth.clamp(1.0F - (j * CELL_Y - sea) / (height + 2.0F), 0.0F, 1.0F);
                // where they crowd together they're bigger too, with only thin ribs left between them
                float dense = Terrain.smooth((Terrain.field(seed + 12, i * CELL_V, j * CELL_Y, 10.0) - 0.3F + 0.15F * low) / 0.3F);
                if (Terrain.noise(seed + 13, i, 1, j) > 0.12F + 0.88F * dense) continue;
                float vc = (i + 0.25F + 0.5F * Terrain.noise(seed + 14, i, 2, j)) * CELL_V;
                float yc = (j + 0.25F + 0.5F * Terrain.noise(seed + 15, i, 3, j)) * CELL_Y;
                float r = 0.7F + 0.35F * Terrain.noise(seed + 16, i, 4, j) + 0.4F * dense;
                // the biggest, lowest down in the spray, have hollowed right back into little caves
                boolean cave = r > 1.2F && Terrain.noise(seed + 17, i, 5, j) < 0.3F * low;
                float depth = cave ? 3.5F + 2.0F * Terrain.noise(seed + 18, i, 6, j) : r * (1.0F + 0.6F * Terrain.noise(seed + 18, i, 6, j));
                float q = Mth.square((v - vc) / r) + Mth.square((h + 0.5F - yc) / (r * 0.85F)) + Mth.square(e / depth);
                best = Math.min(best, q);
            }
        }
        return best;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float half = length / 2.0F;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                if (!inside(x, z)) continue;
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float u = l[0], v = l[1];
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 3 || ground > b.maxY() - 6) continue;
                int surface = Terrain.surface(level, pos, x, ground, z);
                // the cliffs fall away at both ends to the ordinary shore
                float ends = Terrain.smooth((half - Math.abs(v)) / 14.0F);
                float f = face(v), e = f - u;
                boolean land = e >= 0.0F;
                int top;
                if (land) {
                    // the clifftop, rolling back into the land behind
                    int cliff = sea + height + Math.round(1.5F * Terrain.field(seed, x, z, 10.0));
                    top = Math.max(ground, Math.round(Mth.lerp(Terrain.smooth((e - 12.0F) / 18.0F), cliff, ground)));
                } else {
                    // a bench of wave-cut rock and fallen blocks at the cliff foot, then a ledge under the water, then the sea bed
                    float out = -e;
                    top = out < 2.5F ? sea : out < 7.0F ? sea - 2 : Math.round(Mth.lerp(Terrain.smooth((out - 7.0F) / 10.0F), sea - 3, ground));
                }
                top = Math.round(Mth.lerp(ends, ground, top));
                boolean honeycomb = land && e < 7.0F && ends > 0.5F && top - sea >= 6;
                // leave alone the columns it doesn't change (but not a pond it would leave standing above lowered ground)
                if (top == ground && !honeycomb && (surface == ground || surface < sea)) continue;
                Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, Math.min(14, b.maxY() - Math.max(ground, top)));
                for (int h = top + 1; h <= Math.max(Math.max(ground, surface), sea - 1); h++) {
                    BlockState s = h <= sea - 1 ? Blocks.WATER.defaultBlockState() : Terrain.pondBeside(level, pos, x, h, z, (nx, nz) -> !inside(nx, nz))
                            ? SANDSTONE : Blocks.AIR.defaultBlockState();
                    Terrain.set(level, pos, x, h, z, s);
                }
                pos.set(x, ground, z);
                boolean turf = level.getBlockState(pos).is(BlockTags.DIRT);
                float n = Terrain.noise(seed, x, top, z);
                int from = land && e < 7.0F ? Math.min(ground, sea - 4) : Math.min(ground, top) - 1;
                for (int h = from; h <= top; h++) {
                    BlockState s;
                    float q = honeycomb ? hollow(v, h, e) : 9.0F;
                    if (honeycomb && h > sea && h < top - 1 && q < 1.0F) {
                        // a hollow, unless a pond just outside would drain into it
                        s = Terrain.pondBeside(level, pos, x, h, z, (nx, nz) -> !inside(nx, nz)) ? SANDSTONE : Blocks.AIR.defaultBlockState();
                    } else if (honeycomb && e < 1.0F && h > sea && (q > 1.3F || Terrain.noise(seed + 3, x, h, z) > 0.3F)) {
                        s = Terrain.noise(seed + 4, x, h, z) < 0.55F ? CUT : SMOOTH; // the pale, hardened crust of the ribs between
                    } else if (honeycomb && h > sea && q < 1.9F && Terrain.noise(seed + 3, x, h, z) < 0.8F) {
                        s = STAIN; // iron leached out and rusted on the hollows' inner walls
                    } else {
                        // beds of sandstone, some finer and smoother than others
                        s = Math.floorMod(h + Math.round(v * 0.08F), 5) == 0 ? SMOOTH : SANDSTONE;
                    }
                    Terrain.set(level, pos, x, h, z, s);
                }
                if (land && top > sea + 1) {
                    if (turf && e > 3.0F + 2.0F * n) {
                        // turf on the clifftop, back from the salt-scoured edge
                        Terrain.set(level, pos, x, top - 1, z, Blocks.DIRT.defaultBlockState());
                        Terrain.set(level, pos, x, top, z, Blocks.GRASS_BLOCK.defaultBlockState());
                        if (n > 0.6F) Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                    } else {
                        Terrain.set(level, pos, x, top, z, n < 0.4F ? SMOOTH : SANDSTONE);
                    }
                } else if (!land) {
                    if (top == sea) {
                        // the bench: fallen blocks and pockets of sand
                        Terrain.set(level, pos, x, top, z, n < 0.25F ? Blocks.SAND.defaultBlockState() : n < 0.4F ? CUT : n < 0.6F ? SMOOTH : SANDSTONE);
                        if (n > 0.93F) Terrain.set(level, pos, x, top + 1, z, n > 0.97F ? CUT : SANDSTONE);
                    } else if (top < sea - 1) {
                        Terrain.set(level, pos, x, top, z, n < 0.35F ? Blocks.SAND.defaultBlockState() : n < 0.5F ? Blocks.GRAVEL.defaultBlockState() : SANDSTONE);
                        if (n > 0.8F) Terrain.set(level, pos, x, top + 1, z, Blocks.SEAGRASS.defaultBlockState());
                    }
                }
            }
        }
    }
}
