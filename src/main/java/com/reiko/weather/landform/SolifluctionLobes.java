package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Solifluction lobes: tongues of turf creeping down a tundra hillside. Under the tundra the ground is frozen solid
 * all year, and only the top metre or so thaws each summer. Water from the melting snow and ice can't drain down
 * through the frozen ground below, so the thawed soil gets soaked and heavy and slowly slides over it, a few
 * centimetres a year, held together by the mat of roots on top. Where it slows down it piles up into a lobe with a
 * flat-ish tread and a steep, rounded front a metre or two high, the turf rolled over at its edge and the bare soil
 * showing in it. Lobe after lobe overrides the one below, so the whole slope ends up stepped like a flight of stairs.
 *
 * @param angle the way the slope falls
 * @param low   the lowest ground sampled
 * @param high  the highest ground sampled
 * @param lobes {root along, root across, length, width, front height, bearing off the slope (thousandths of a radian)}
 *              for each lobe
 */
public record SolifluctionLobes(int cx, int cz, float angle, int low, int high, int[] lobes, long seed) implements Landform {
    private static final int L = 6, ALONG = 38, ACROSS = 32;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 2 || !c.fits(ground, 34, 34)) {
            return null;
        }
        // a real hillside: falling away steadily one way and still climbing the other
        float[] down = c.downhill(30);
        if (down[1] < 7 || down[1] > 24) {
            return null;
        }
        float a = down[0], ca = Mth.cos(a), sa = Mth.sin(a);
        int up = c.groundAt(c.x() - Math.round(ca * 30), c.z() - Math.round(sa * 30));
        int left = c.groundAt(c.x() - Math.round(sa * 26), c.z() + Math.round(ca * 26));
        int right = c.groundAt(c.x() + Math.round(sa * 26), c.z() - Math.round(ca * 26));
        int bottom = ground - Math.round(down[1]);
        if (up < ground + 3 || Math.abs(left - right) > 12 || bottom < c.seaLevel() + 1) {
            return null;
        }
        // trains of lobes, each overriding the root of the one below it, and side by side not quite touching
        int want = 8 + c.random().nextInt(18);
        int[] lobes = new int[want * L];
        int n = 0;
        for (int tries = 0; tries < 40 && n < want; tries++) {
            float u = -ALONG + c.random().nextFloat() * ALONG, v = (c.random().nextFloat() * 2.0F - 1.0F) * (ACROSS - 4);
            int previous = -1;
            while (n < want) {
                int length = 8 + c.random().nextInt(18), width = 5 + c.random().nextInt(11), height = 1 + c.random().nextInt(3);
                float turn = (c.random().nextFloat() - 0.5F) * 0.5F, mid = v + turn * length / 2.0F, spread = width / 2.0F + Math.abs(turn) * length / 2.0F;
                if (u + length > ALONG || Math.abs(mid) + spread > ACROSS) break;
                boolean clear = true;
                for (int j = 0; j < n && clear; j++) {
                    if (j == previous) continue;
                    float ju = lobes[j * L], jl = lobes[j * L + 2], jt = lobes[j * L + 5] / 1000.0F;
                    float jmid = lobes[j * L + 1] + jt * jl / 2.0F, jspread = lobes[j * L + 3] / 2.0F + Math.abs(jt) * jl / 2.0F;
                    clear = u > ju + jl || u + length < ju || Math.abs(mid - jmid) > spread + jspread + 1.5F;
                }
                if (!clear) break;
                lobes[n * L] = Math.round(u);
                lobes[n * L + 1] = Math.round(v);
                lobes[n * L + 2] = length;
                lobes[n * L + 3] = width;
                lobes[n * L + 4] = height;
                lobes[n * L + 5] = Math.round(turn * 1000.0F);
                previous = n++;
                // the next one down starts a little behind this one's front, so this one's front rides up on it
                u += length - 2 - c.random().nextInt(4);
                v += turn * length + (c.random().nextFloat() - 0.5F) * 4.0F;
                if (c.random().nextFloat() < 0.25F) break;
            }
        }
        if (n < 8) {
            return null;
        }
        int min = Math.min(Math.min(bottom, ground), Math.min(left, right)), max = Math.max(Math.max(up, ground), Math.max(left, right));
        return new SolifluctionLobes(c.x(), c.z(), a, min, max, java.util.Arrays.copyOf(lobes, n * L), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new SolifluctionLobes(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("low"), tag.getInt("high"), tag.getIntArray("lobes"),
                tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("low", low);
        tag.putInt("high", high);
        tag.put("lobes", new IntArrayTag(lobes));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.SOLIFLUCTION_LOBES;
    }

    @Override
    public BoundingBox bounds() {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (float u : new float[]{-ALONG - 2, ALONG + 2}) {
            for (float v : new float[]{-ACROSS - 2, ACROSS + 2}) {
                double[] p = Terrain.xz(u, v, cx, cz, angle);
                minX = Math.min(minX, Mth.floor(p[0]));
                maxX = Math.max(maxX, Mth.ceil(p[0]));
                minZ = Math.min(minZ, Mth.floor(p[1]));
                maxZ = Math.max(maxZ, Mth.ceil(p[1]));
            }
        }
        return new BoundingBox(minX, low - 12, minZ, maxX, high + 14, maxZ);
    }

    /** How thick lobe {@code i} lies at a point (u, v) on the slope: a tongue thickening to a steep, rounded front. */
    private float thickness(int i, float u, float v) {
        float turn = lobes[i * L + 5] / 1000.0F, s = u - lobes[i * L];
        float q = v - lobes[i * L + 1] - turn * s;
        float length = lobes[i * L + 2], half = lobes[i * L + 3] / 2.0F;
        if (s < 0.0F || s > length) return 0.0F;
        // narrow where it breaks away up the slope, broad at the front
        float w = half * (0.55F + 0.45F * Terrain.smooth(s / (length * 0.5F))) * (0.9F + 0.2F * Terrain.field(seed + i, u, v, 5.0));
        float aq = Math.abs(q);
        if (aq >= w) return 0.0F;
        // the front bows out downhill in the middle
        float front = length - half * 0.9F * (1.0F - Mth.sqrt(Math.max(0.0F, 1.0F - Mth.square(aq / w))));
        if (s > front) return 0.0F;
        return lobes[i * L + 4] * Terrain.smooth(s / (length * 0.6F)) * Terrain.smooth((w - aq) / 1.6F) + 0.4F;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float thick = 0.0F;
                for (int i = 0; i < lobes.length / L; i++) thick = Math.max(thick, thickness(i, l[0], l[1]));
                int raise = Math.round(thick - 0.4F);
                if (raise < 1) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 2 || ground + raise > b.maxY() - 4) continue;
                int surface = Terrain.surface(level, pos, x, ground, z);
                int top = ground + raise;
                if (surface > ground && top <= surface) continue; // a pond the lobe stops short of
                BlockState was = level.getBlockState(pos.set(x, ground, z));
                BlockState over = level.getBlockState(pos.set(x, ground + 1, z));
                boolean snowy = was.is(Blocks.SNOW_BLOCK) || was.is(Blocks.POWDER_SNOW) || over.is(Blocks.SNOW);
                Terrain.clearAbove(level, pos, x, ground, z, Math.min(16, b.maxY() - ground));
                float n = Terrain.noise(seed, x, ground, z);
                // the thawed soil that has crept down, bared in the front of the lobe
                for (int h = ground; h < top; h++) {
                    float m = Terrain.noise(seed + 1, x, h, z);
                    BlockState s = m < 0.55F ? Blocks.DIRT.defaultBlockState() : m < 0.9F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState();
                    if (h == ground && !was.is(BlockTags.DIRT) && !snowy) continue; // what it's sliding over stays as it was
                    Terrain.set(level, pos, x, h, z, s);
                }
                // the turf on top: grass, moss where it's wettest behind the front, and a stone it has carried down here and there
                float wet = Terrain.field(seed + 2, x, z, 4.0);
                BlockState turf;
                if (Terrain.field(seed + 3, x, z, 2.0) > 0.86F) {
                    turf = n < 0.5F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : n < 0.8F ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState();
                } else if (wet < 0.32F) {
                    turf = Blocks.MOSS_BLOCK.defaultBlockState();
                } else {
                    turf = Blocks.GRASS_BLOCK.defaultBlockState().setValue(SnowyDirtBlock.SNOWY, snowy);
                }
                Terrain.set(level, pos, x, top, z, turf);
                if (snowy) {
                    if (!turf.is(Blocks.MOSS_BLOCK)) Terrain.set(level, pos, x, top + 1, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1));
                    continue;
                }
                if (!turf.is(BlockTags.DIRT)) continue;
                float p = Terrain.noise(seed + 4, x, top, z);
                if (p < 0.012F && turf.is(Blocks.GRASS_BLOCK)) {
                    Terrain.set(level, pos, x, top + 1, z, ModBlocks.FIREWEED.get().defaultBlockState());
                } else if (p < 0.2F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                } else if (p < 0.26F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.FERN.defaultBlockState());
                }
            }
        }
    }
}
