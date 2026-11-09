package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Avalanche chutes (avalanche paths): straight swathes cut down a forested mountainside, where avalanches come
 * down the same way winter after winter. Every big slide snaps or uproots whatever trees have grown up since the
 * last, so the track never gets the chance to become forest again: it's a strip of shrubs, saplings, grass and
 * flowers running straight down the fall line between the walls of old trees, with a jumble of smashed trunks
 * and boulders heaped up where the snow came to rest at the bottom.
 *
 * @param angle  the way down the slope
 * @param start  how far up the slope (from the middle) the chutes begin
 * @param foot   how far down it they end
 * @param low    the lowest and highest ground sampled (for the bounds)
 * @param chutes {offset across the slope, half width x 10} for each chute
 */
public record AvalancheChutes(int cx, int cz, float angle, int start, int foot, int low, int high, int[] chutes, long seed) implements Landform {
    private static final int TREES = 32;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 8 || !c.fits(ground, 50, 50)) {
            return null;
        }
        // a steep slope
        float[] down = c.downhill(20);
        if (down[1] < 10) {
            return null;
        }
        float a = down[0], ca = Mth.cos(a), sa = Mth.sin(a);
        int up = c.groundAt(c.x() - Math.round(ca * 24), c.z() - Math.round(sa * 24));
        // the chutes start from up near the top of the slope, if it carries on rising
        int start = up >= ground + 6 ? -24 : -8;
        int[] heights = {ground - Math.round(down[1]), c.groundAt(c.x() + Math.round(ca * 40), c.z() + Math.round(sa * 40)),
                c.groundAt(c.x() + Math.round(ca * 60), c.z() + Math.round(sa * 60))};
        // and run down to where it levels out
        int foot = 64;
        for (int k = 0; k < 2; k++) {
            if (heights[k] - heights[k + 1] <= 4) {
                foot = 20 * (k + 1) + 4;
                break;
            }
        }
        int low = Math.min(heights[0], Math.min(heights[1], heights[2])), high = Math.max(ground, up);
        if (Math.max(ground, up) - low < 18 || low < c.seaLevel() + 1) {
            return null;
        }
        int n = 2 + c.random().nextInt(3);
        float spacing = 13.0F + c.random().nextFloat() * 7.0F;
        int[] chutes = new int[n * 2];
        for (int i = 0; i < n; i++) {
            chutes[i * 2] = Math.round((i - (n - 1) / 2.0F) * spacing + (c.random().nextFloat() - 0.5F) * 4.0F);
            chutes[i * 2 + 1] = Math.round((2.5F + c.random().nextFloat() * 2.0F) * 10.0F);
        }
        return new AvalancheChutes(c.x(), c.z(), a, start, foot, low, high, chutes, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new AvalancheChutes(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("start"), tag.getInt("foot"), tag.getInt("low"),
                tag.getInt("high"), tag.getIntArray("chutes"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("start", start);
        tag.putInt("foot", foot);
        tag.putInt("low", low);
        tag.putInt("high", high);
        tag.put("chutes", new IntArrayTag(chutes));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.AVALANCHE_CHUTES;
    }

    private float across() {
        float m = 0.0F;
        for (int i = 0; i < chutes.length; i += 2) m = Math.max(m, Math.abs(chutes[i]) + chutes[i + 1] / 10.0F * 1.8F);
        return m + 3.0F;
    }

    @Override
    public BoundingBox bounds() {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        float v = across();
        for (float u : new float[]{start - 3.0F, foot + 11.0F}) {
            for (float s : new float[]{-v, v}) {
                double[] p = Terrain.xz(u, s, cx, cz, angle);
                minX = Math.min(minX, Mth.floor(p[0]));
                maxX = Math.max(maxX, Mth.ceil(p[0]));
                minZ = Math.min(minZ, Mth.floor(p[1]));
                maxZ = Math.max(maxZ, Mth.ceil(p[1]));
            }
        }
        return new BoundingBox(minX, low - 12, minZ, maxX, high + 16 + TREES, maxZ);
    }

    /** How wide chute {@code i} is, {@code u} blocks down the slope: narrow where it starts, opening out lower down. */
    private float halfWidth(int i, float u, int x, int z) {
        float w = chutes[i * 2 + 1] / 10.0F;
        float t = (u - start) / (float) (foot - start);
        return w * (0.55F + 0.45F * Terrain.smooth(t / 0.25F) + 0.25F * t) * (0.9F + 0.2F * Terrain.field(seed + i, x, z, 7.0));
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState bush = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        // logs lying across the slope, the way the avalanche flung them
        Direction.Axis lying = Math.abs(Mth.cos(angle)) > Math.abs(Mth.sin(angle)) ? Direction.Axis.Z : Direction.Axis.X;
        Direction.Axis other = lying == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float u = l[0], v = l[1];
                if (u < start - 2 || u > foot + 10) continue;
                int chute = -1;
                float dv = 0.0F, heap = Float.MAX_VALUE;
                for (int i = 0; i < chutes.length / 2; i++) {
                    float d = v - chutes[i * 2];
                    float hw = halfWidth(i, Math.min(u, foot), x, z);
                    // the debris heap where the avalanche stopped, a little wider than the track
                    float e = Mth.square((u - foot - 2.0F) / 7.0F) + Mth.square(d / (hw * 1.5F));
                    if (e < heap) heap = e;
                    if (u <= foot && Math.abs(d) < hw + (Terrain.noise(seed + i, x, 0, z) - 0.5F) * 0.8F) {
                        chute = i;
                        dv = d;
                    }
                }
                if (chute < 0 && heap >= 1.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 4 || ground > b.maxY() - TREES - 4) continue;
                pos.set(x, ground, z);
                BlockState surface = level.getBlockState(pos);
                boolean cold = level.getBiome(pos).value().coldEnoughToSnow(pos);
                // sweep away the forest: trunks, branches, leaves and all
                Terrain.clearAbove(level, pos, x, ground, z, TREES);
                if (heap < 1.0F) {
                    debris(level, pos, x, z, ground, heap, lying, other, cold);
                    continue;
                }
                // the regrowth along the track: thickets of shrubs and saplings, grass, berries and flowers (snow up high)
                boolean soil = surface.is(BlockTags.DIRT) || surface.is(Blocks.SNOW_BLOCK) || surface.is(Blocks.POWDER_SNOW);
                if (!soil) continue;
                float n = Terrain.noise(seed, x, ground, z);
                float rock = Terrain.field(seed + 3, x, z, 4.0);
                if (rock > 0.8F) {
                    // bare patches where the snow scoured the slope down to rock
                    Terrain.set(level, pos, x, ground, z, n < 0.4F ? Blocks.STONE.defaultBlockState() : n < 0.75F ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
                    if (cold && n < 0.5F) Terrain.set(level, pos, x, ground + 1, z, Blocks.SNOW.defaultBlockState());
                    continue;
                }
                BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
                if (!surface.is(BlockTags.DIRT) || surface.is(Blocks.GRASS_BLOCK)) {
                    Terrain.set(level, pos, x, ground, z, grass);
                }
                if (n < 0.38F) {
                    Terrain.set(level, pos, x, ground + 1, z, bush);
                    if (n < 0.08F) Terrain.set(level, pos, x, ground + 2, z, bush);
                } else if (n < 0.48F) {
                    Terrain.set(level, pos, x, ground + 1, z, Blocks.SPRUCE_SAPLING.defaultBlockState());
                } else if (cold) {
                    Terrain.set(level, pos, x, ground, z, grass.setValue(SnowyDirtBlock.SNOWY, true));
                    Terrain.set(level, pos, x, ground + 1, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, n < 0.8F ? 1 : 2));
                } else if (n < 0.56F) {
                    Terrain.set(level, pos, x, ground + 1, z, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 1 + Math.round(n * 100) % 3));
                } else if (n < 0.66F) {
                    Terrain.set(level, pos, x, ground + 1, z, n < 0.6F ? Blocks.ALLIUM.defaultBlockState() : n < 0.63F ? Blocks.CORNFLOWER.defaultBlockState() : Blocks.DANDELION.defaultBlockState());
                } else {
                    Terrain.set(level, pos, x, ground + 1, z, n < 0.88F ? Blocks.SHORT_GRASS.defaultBlockState() : Blocks.FERN.defaultBlockState());
                }
            }
        }
    }

    /** A column of the debris heap: smashed trunks lying every which way among boulders, soil and old snow. */
    private void debris(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, float heap,
                        Direction.Axis lying, Direction.Axis other, boolean cold) {
        int height = Math.round(2.8F * (1.0F - heap) + 1.4F * (Terrain.field(seed + 5, x, z, 3.0) - 0.5F));
        for (int h = ground + 1; h <= ground + height; h++) {
            float n = Terrain.noise(seed + 6, x, h, z);
            Direction.Axis axis = Terrain.field(seed + 7, x, h * 3.0, 3.0) < 0.65F ? lying : other;
            BlockState s;
            if (n < 0.32F) s = Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
            else if (n < 0.42F) s = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
            else if (n < 0.62F) s = Terrain.noise(seed, x, h, z) < 0.5F ? Blocks.DIRT.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
            else if (n < 0.8F) s = n < 0.72F ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            else if (n < 0.9F) s = Blocks.GRAVEL.defaultBlockState();
            else s = Blocks.STONE.defaultBlockState();
            Terrain.set(level, pos, x, h, z, s);
        }
        int top = ground + Math.max(0, height);
        float n = Terrain.noise(seed + 8, x, top, z);
        // the odd whole trunk lying on top, a boulder, or a patch of old avalanche snow
        if (n < 0.12F) {
            Terrain.set(level, pos, x, top + 1, z, Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, lying));
        } else if (n > 0.93F) {
            Terrain.set(level, pos, x, top + 1, z, n > 0.97F ? Blocks.STONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
        } else if (cold && n > 0.6F) {
            Terrain.set(level, pos, x, top + 1, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + Math.round(n * 10.0F) % 3));
        }
    }
}
