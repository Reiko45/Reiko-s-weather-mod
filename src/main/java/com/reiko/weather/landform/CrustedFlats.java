package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Crusted flats: desert ground knitted together by biological soil crust, a living skin of cyanobacteria, lichens and
 * mosses that binds the sand grains with sticky threads. It grows a fraction of a millimetre a year into dark, lumpy
 * little pinnacles, and holds the desert down against the wind, but one footprint can break what took decades to grow,
 * so it lies in dark patches between bare sand. Here and there low outcrops of sandstone break through, their faces
 * stained near black with desert varnish (a film of clay and manganese laid down by bacteria over thousands of years),
 * and long ago people pecked figures through the varnish to the pale rock underneath.
 *
 * @param y        the ground at the middle (for the bounds)
 * @param outcrops {x, z, radius x 10, top} for each outcrop
 */
public record CrustedFlats(int cx, int cz, int y, int radius, int[] outcrops, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        int radius = 34 + c.random().nextInt(17);
        if (ground < c.seaLevel() + 2 || c.relief(Math.round(radius * 0.7F), 8) > 10 || !c.fits(ground, 14, 20)) {
            return null;
        }
        int n = 2 + c.random().nextInt(3);
        int[] outcrops = new int[n * 4];
        float start = c.random().nextFloat() * Mth.TWO_PI;
        for (int i = 0; i < n; i++) {
            float a = start + i * Mth.TWO_PI / n + (c.random().nextFloat() - 0.5F) * 1.2F, d = (0.25F + 0.45F * c.random().nextFloat()) * radius;
            int x = c.x() + Math.round(Mth.cos(a) * d), z = c.z() + Math.round(Mth.sin(a) * d);
            // a low, flat-topped knuckle of rock standing a few blocks above the ground at its foot
            outcrops[i * 4] = x;
            outcrops[i * 4 + 1] = z;
            outcrops[i * 4 + 2] = Math.round((2.5F + 3.0F * c.random().nextFloat()) * 10.0F);
            outcrops[i * 4 + 3] = c.groundAt(x, z) + 2 + c.random().nextInt(4);
        }
        return new CrustedFlats(c.x(), c.z(), ground, radius, outcrops, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new CrustedFlats(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getIntArray("outcrops"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.put("outcrops", new IntArrayTag(outcrops));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.CRUSTED_FLATS;
    }

    @Override
    public BoundingBox bounds() {
        int r = Mth.ceil(radius / 0.85F) + 2, high = y;
        for (int i = 0; i < outcrops.length; i += 4) high = Math.max(high, outcrops[i + 3]);
        return new BoundingBox(cx - r, y - 18, cz - r, cx + r, Math.max(high, y) + 22, cz + r);
    }

    private float field(double x, double z) {
        return (float) Mth.length(x - cx, z - cz) / (radius * (0.85F + 0.3F * Terrain.field(seed, x, z, 15.0)));
    }

    /** The top of the outcrops at a point: blocky, flat-topped knuckles of rock, stepped down at their edges. */
    private int outcrop(int x, int z) {
        int top = Integer.MIN_VALUE;
        for (int i = 0; i < outcrops.length / 4; i++) {
            float dx = x + 0.5F - outcrops[i * 4], dz = z + 0.5F - outcrops[i * 4 + 1];
            float theta = (float) Mth.atan2(dz, dx);
            float r = outcrops[i * 4 + 2] / 10.0F * (1.0F + 0.25F * Mth.sin(3.0F * theta + Terrain.hash(seed, 10 + i) * 6.0F)
                    + 0.12F * Mth.sin(5.0F * theta + Terrain.hash(seed, 20 + i) * 6.0F));
            float d = Mth.sqrt(dx * dx + dz * dz) / r;
            if (d >= 1.0F) continue;
            int h = 2 + Math.round(Terrain.hash(seed, 30 + i) * 3.0F);
            top = Math.max(top, outcrops[i * 4 + 3] - (d < 0.55F ? 0 : Math.round(h * Terrain.smooth((d - 0.55F) / 0.45F))));
        }
        return top;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float f = field(x + 0.5, z + 0.5);
                if (f >= 1.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 4 || ground > b.maxY() - 14) continue;
                if (!level.getFluidState(pos.set(x, ground + 1, z)).isEmpty()) continue;
                boolean red = level.getBiome(pos.set(x, ground, z)).is(BiomeTags.IS_BADLANDS);
                float n = Terrain.noise(seed, x, 0, z);
                int rock = outcrop(x, z);
                if (rock > ground) {
                    outcrop(level, pos, x, z, ground, rock, red);
                    continue;
                }
                // the crust thins out into the land round about in patches
                if (n > Terrain.smooth((1.0F - f) / 0.2F) * 1.5F) continue;
                Terrain.clearAbove(level, pos, x, ground, z, 12);
                BlockState sand = red ? Blocks.RED_SAND.defaultBlockState() : Blocks.SAND.defaultBlockState();
                // the crust: small dark lumps, thickest and darkest where it's been left alone longest
                float age = Terrain.field(seed + 1, x, z, 12.0);
                float lump = Terrain.field(seed + 2, x, z, 1.7);
                BlockState top = sand;
                boolean raised = false;
                if (lump < 0.25F + 0.4F * age) {
                    float k = Terrain.noise(seed + 3, x, 0, z);
                    top = age > 0.6F ? k < 0.5F ? Blocks.PODZOL.defaultBlockState() : Blocks.MUD_BRICKS.defaultBlockState()
                            : age > 0.35F ? k < 0.6F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState()
                            : k < 0.7F ? Blocks.PACKED_MUD.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
                    // the oldest crust grows up into little pinnacles
                    raised = lump < 0.12F + 0.15F * age && age > 0.45F;
                }
                Terrain.set(level, pos, x, ground - 1, z, red ? Blocks.RED_SANDSTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState());
                Terrain.set(level, pos, x, ground, z, top);
                int surface = ground;
                if (raised) {
                    Terrain.set(level, pos, x, ground + 1, z, top);
                    surface++;
                }
                // the odd dead bush, rooted in the sand or the softer crust
                if (n > 0.012F || !(top.is(BlockTags.SAND) || top.is(BlockTags.DIRT))) continue;
                Terrain.set(level, pos, x, surface + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
            }
        }
    }

    /**
     * An outcrop: pale sandstone, with its faces (the blocks the next column down leaves open) stained dark with
     * varnish, and a figure or two pecked through it on the faces of the first outcrops.
     */
    private void outcrop(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, int top, boolean red) {
        Terrain.clearAbove(level, pos, x, ground, z, top - ground + 8);
        int low = top;
        for (int[] s : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int o = outcrop(x + s[0], z + s[1]);
            low = Math.min(low, o == Integer.MIN_VALUE ? ground : o);
        }
        BlockState stone = red ? Blocks.RED_SANDSTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState();
        BlockState carved = red ? Blocks.CHISELED_RED_SANDSTONE.defaultBlockState() : Blocks.CHISELED_SANDSTONE.defaultBlockState();
        // which outcrop this is, and how far round it, for the carvings
        int which = -1;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < outcrops.length / 4; i++) {
            float d = (float) Mth.length(x + 0.5F - outcrops[i * 4], z + 0.5F - outcrops[i * 4 + 1]);
            if (d < best) {
                best = d;
                which = i;
            }
        }
        float theta = (float) Mth.atan2(z + 0.5F - outcrops[which * 4 + 1], x + 0.5F - outcrops[which * 4]);
        float facing = Terrain.hash(seed, 40 + which) * Mth.TWO_PI;
        boolean panel = which < 2 && Math.abs(Mth.wrapDegrees((theta - facing) * Mth.RAD_TO_DEG)) < 35.0F;
        for (int h = ground - 1; h <= top; h++) {
            BlockState s = stone;
            if (h < top && h >= low && h > ground - 1) {
                // varnish, streaked darker where water runs down from the top
                float v = Terrain.noise(seed + 5, x, h, z);
                boolean streak = Terrain.noise(seed + 6, x, 0, z) < 0.4F;
                s = streak || v < 0.3F ? Blocks.BLACK_TERRACOTTA.defaultBlockState() : Blocks.BROWN_TERRACOTTA.defaultBlockState();
                // petroglyphs: figures pecked through the varnish to the pale stone
                if (panel && h >= top - 3 && Terrain.noise(seed + 7, x, h, z) < 0.45F) s = carved;
            } else if (h == top && Terrain.noise(seed + 8, x, h, z) < 0.25F) {
                s = red ? Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState() : Blocks.SMOOTH_SANDSTONE.defaultBlockState();
            }
            Terrain.set(level, pos, x, h, z, s);
        }
    }
}
