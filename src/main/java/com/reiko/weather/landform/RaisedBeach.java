package com.reiko.weather.landform;

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
 * Raised beaches: old shorelines left high and dry above today's sea. The ice sheets of the last ice age pressed the
 * land down under their weight, and when they melted it slowly rose again, lifting its beaches out of the waves. So
 * behind the shore the land climbs in flat terraces of old beach sand and shingle, grassed over now, each backed by
 * the sea cliff the waves were cutting when the sea stood at that level, with the notch they wore into its foot.
 *
 * @param levels the height of each old beach, lowest first
 * @param top    the height of the land above the last old cliff
 */
public record RaisedBeach(int cx, int cz, int sea, float angle, int length, int[] levels, int top, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        return c.search(12, 12, RaisedBeach::planAt);
    }

    @Nullable
    private static Landform planAt(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() - 2 || ground > c.seaLevel() + 4) {
            return null;
        }
        float seaward = c.seaward(24, 3);
        if (Float.isNaN(seaward)) {
            return null;
        }
        // not under mountains
        int behind = c.groundAt(c.x() - Math.round(Mth.cos(seaward) * 40), c.z() - Math.round(Mth.sin(seaward) * 40));
        if (behind > c.seaLevel() + 36) {
            return null;
        }
        int[] levels;
        if (behind >= c.seaLevel() + 9 || c.random().nextFloat() < 0.4F) {
            int low = c.seaLevel() + 4 + c.random().nextInt(2);
            levels = new int[]{low, Math.min(c.seaLevel() + 10, low + 4 + c.random().nextInt(2))};
        } else {
            levels = new int[]{c.seaLevel() + 5 + c.random().nextInt(3)};
        }
        int top = levels[levels.length - 1] + 4 + c.random().nextInt(3);
        return new RaisedBeach(c.x(), c.z(), c.seaLevel(), seaward, 50 + c.random().nextInt(31), levels, top, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new RaisedBeach(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getFloat("angle"), tag.getInt("length"), tag.getIntArray("levels"),
                tag.getInt("top"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("sea", sea);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.put("levels", new IntArrayTag(levels));
        tag.putInt("top", top);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.RAISED_BEACH;
    }

    @Override
    public BoundingBox bounds() {
        float half = length / 2.0F + 1.0F, inland = cliff(levels.length - 1, 0.0F) + 28.0F;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (float u : new float[]{-inland, 5.0F}) {
            for (float v : new float[]{-half, half}) {
                double[] w = Terrain.xz(u, v, cx, cz, angle);
                minX = Math.min(minX, Mth.floor(w[0]));
                maxX = Math.max(maxX, Mth.floor(w[0]));
                minZ = Math.min(minZ, Mth.floor(w[1]));
                maxZ = Math.max(maxZ, Mth.floor(w[1]));
            }
        }
        return new BoundingBox(minX, sea - 6, minZ, maxX, sea + 54, maxZ);
    }

    /** How far the waterline sits out to sea of the middle, {@code v} along the coast. */
    private float shore(float v) {
        return 1.5F * Mth.sin(v * 0.05F + Terrain.hash(seed, 1) * 6.0F);
    }

    /** Where today's beach ends and the bank up to the first old beach begins. */
    private float beach(float v) {
        return 5.0F + 2.0F * Terrain.field(seed, v, 0.0, 14.0);
    }

    /** How far inland the foot of the old cliff behind terrace {@code k} is. */
    private float cliff(int k, float v) {
        float w = beach(v) + 3.0F;
        for (int i = 0; i <= k; i++) {
            w += (i == 0 ? 9.0F : 8.0F) + 5.0F * Terrain.hash(seed, 10 + i) + 2.0F * Mth.sin(v * (0.07F + 0.02F * i) + Terrain.hash(seed, 20 + i) * 6.0F);
        }
        return w;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float half = length / 2.0F;
        int n = levels.length;
        float[] cliffs = new float[n];
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float v = l[1], w = shore(v) - l[0];
                if (Math.abs(v) > half || w < 0.0F) continue;
                for (int k = 0; k < n; k++) cliffs[k] = cliff(k, v);
                float last = cliffs[n - 1];
                if (w > last + 18.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground > b.maxY() - 12) continue;
                float bw = beach(v);
                // how many old cliffs lie between here and the sea, and the level of the step this is on
                int passed = 0;
                while (passed < n && w >= cliffs[passed]) passed++;
                int under = passed == 0 ? levels[0] : passed < n ? levels[passed] : top;
                float target;
                int kind; // 0 today's beach, 1 the bank up to the first old beach, 2 a terrace, 3 the land behind
                if (w < bw) {
                    target = sea - 1 + 2.0F * w / bw;
                    kind = 0;
                } else if (w < bw + 3.0F) {
                    target = Mth.lerp(Terrain.smooth((w - bw) / 3.0F), sea + 1, levels[0]);
                    kind = 1;
                } else if (passed < n || w < last + 3.0F) {
                    target = under;
                    kind = 2;
                } else {
                    target = Mth.lerp(Terrain.smooth((w - last - 3.0F) / 12.0F), top, ground);
                    kind = 3;
                }
                float ends = Terrain.smooth((half - Math.abs(v)) / 14.0F);
                int t = Math.round(Mth.lerp(ends, ground, target));
                if (t == ground && kind == 3) continue;
                // the land behind is left alone where it's under a river or a lake
                if (kind == 3 && (!level.getFluidState(pos.set(x, ground + 1, z)).isEmpty() || t <= b.minY())) continue;
                pos.set(x, ground, z);
                BlockState was = level.getBlockState(pos);
                Terrain.clearAbove(level, pos, x, ground, z, Math.max(0, t - ground) + 8);
                for (int h = t + 1; h <= Math.min(ground, b.maxY()); h++) {
                    Terrain.set(level, pos, x, h, z, h <= sea - 1 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
                float noise = Terrain.noise(seed, x, t, z);
                if (kind == 0) {
                    for (int h = Math.max(b.minY(), Math.min(ground + 1, t - 2)); h <= t; h++) Terrain.set(level, pos, x, h, z, Blocks.SAND.defaultBlockState());
                    continue;
                }
                if (kind == 3) {
                    // the land rolling back from the clifftop, in whatever it was made of
                    for (int h = Math.max(b.minY(), ground + 1); h < t; h++) Terrain.set(level, pos, x, h, z, Blocks.DIRT.defaultBlockState());
                    Terrain.set(level, pos, x, t, z, was.isAir() || !was.getFluidState().isEmpty() ? Blocks.GRASS_BLOCK.defaultBlockState() : was);
                    if (t > ground) Terrain.set(level, pos, x, t - 1, z, Blocks.DIRT.defaultBlockState());
                    continue;
                }
                // rock, the old wave-cut platform, then a bench of beach sand and shingle under thin turf
                int prev = passed == 0 ? sea : levels[passed - 1];
                int rockFrom = Math.max(b.minY(), Math.min(t, Math.min(ground + 1, (kind == 1 ? sea : prev) - 1)));
                boolean dry = t >= sea; // (where it fades into the ordinary shore at the ends it can dip under the water)
                for (int h = rockFrom; h <= t; h++) {
                    BlockState s;
                    if (h == t) {
                        s = dry ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.SAND.defaultBlockState();
                    } else if (h >= t - 2) {
                        s = Terrain.noise(seed, x, h, z) < 0.55F ? Blocks.SAND.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                    } else {
                        s = Terrain.field(seed + 2, x + h * 0.6, z - h * 0.4, 5.0) > 0.62F ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    }
                    Terrain.set(level, pos, x, h, z, s);
                }
                if (!dry) continue;
                if (kind == 1 && t - ground > 0 && noise < 0.3F) Terrain.set(level, pos, x, t, z, Blocks.COARSE_DIRT.defaultBlockState());
                // the notch the waves cut into the foot of the old cliff
                if (passed > 0 && ends > 0.9F && w - cliffs[passed - 1] < 1.5F && t - prev >= 4) {
                    Terrain.set(level, pos, x, prev + 1, z, Blocks.AIR.defaultBlockState());
                    Terrain.set(level, pos, x, prev + 2, z, Blocks.AIR.defaultBlockState());
                    Terrain.set(level, pos, x, prev + 3, z, Blocks.STONE.defaultBlockState()); // a lip of hard rock over it
                    continue;
                }
                if (noise > 0.985F) {
                    Terrain.set(level, pos, x, t + 1, z, noise > 0.993F ? Blocks.OXEYE_DAISY.defaultBlockState() : Blocks.CORNFLOWER.defaultBlockState());
                } else if (noise > 0.7F) {
                    Terrain.set(level, pos, x, t + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                }
            }
        }
    }
}
