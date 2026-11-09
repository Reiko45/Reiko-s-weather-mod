package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * White chalk cliffs: soft, pale rock built from the shells of countless tiny sea creatures, cut back by the
 * waves into a sheer wall above a flat wave-cut platform, banded with dark nodules of flint. The sea has
 * hollowed caves into the foot of the cliff, and from the end of one a shaft runs up to the clifftop: a
 * blowhole, which booms and spouts spray when the waves surge in.
 */
public record ChalkCliffs(int cx, int cz, int sea, float angle, int length, int height, long seed) implements Landform {
    private static final int CAVES = 3;

    @Nullable
    static Landform plan(PlanContext c) {
        return c.search(12, 12, ChalkCliffs::planAt);
    }

    @Nullable
    private static Landform planAt(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() - 3 || ground > c.seaLevel() + 10) {
            return null;
        }
        float seaward = c.seaward(24, 3);
        if (Float.isNaN(seaward)) {
            return null;
        }
        return new ChalkCliffs(c.x(), c.z(), c.seaLevel(), seaward, 70 + c.random().nextInt(40), 14 + c.random().nextInt(10), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new ChalkCliffs(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("height"),
                tag.getLong("seed"));
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
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.CHALK_CLIFFS;
    }

    @Override
    public BoundingBox bounds() {
        int r = length / 2 + 20;
        return new BoundingBox(cx - r, sea - 16, cz - r, cx + r, sea + height + 30, cz + r);
    }

    /** Where the cliff face stands, {@code u} blocks seaward of the middle, {@code v} along the coast. */
    private float face(float v) {
        return 2.5F * Mth.sin(v * 0.06F + Terrain.hash(seed, 1) * 6.0F) + 1.5F * Mth.sin(v * 0.17F + Terrain.hash(seed, 2) * 6.0F);
    }

    private float caveAlong(int k) {
        return (k - (CAVES - 1) / 2.0F) * length * 0.22F + (Terrain.hash(seed, 10 + k) - 0.5F) * 8.0F;
    }

    private int caveDepth(int k) {
        return 9 + Math.round(Terrain.hash(seed, 20 + k) * 6.0F);
    }

    /** The blowhole sits at the inner end of the first cave: {u, v}. */
    private int[] blowhole() {
        float v = caveAlong(0);
        return new int[]{Math.round(face(v)) - caveDepth(0) + 2, Math.round(v)};
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState chalk = Blocks.CALCITE.defaultBlockState();
        float half = length / 2.0F;
        int[] hole = blowhole();
        double[] hw = Terrain.xz(hole[0], hole[1], cx, cz, angle);
        int bx = Mth.floor(hw[0]), bz = Mth.floor(hw[1]);
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float u = l[0], v = l[1];
                if (Math.abs(v) > half || u < -34.0F || u > 22.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                // the cliffs fall away at both ends to the ordinary shore
                float ends = Terrain.smooth((half - Math.abs(v)) / 16.0F);
                float f = face(v);
                int top;
                boolean land = u <= f;
                if (land) {
                    // the downs rolling back from the clifftop, fading into the land behind
                    float back = Terrain.smooth((-u + f - 12.0F) / 20.0F);
                    int cliff = sea + height + Math.round(2.0F * Terrain.field(seed, x, z, 12.0));
                    top = Math.max(ground, Math.round(Mth.lerp(back, cliff, ground)));
                    // a hollow round the blowhole, so its shaft isn't too deep
                    float hd = Mth.sqrt(Mth.square(u - hole[0]) + Mth.square(v - hole[1]));
                    if (hd < 5.0F) top = Math.min(top, sea + 13 + Math.round(hd));
                } else {
                    // the wave-cut platform, a couple of blocks under the water, then the old sea bed
                    float out = u - f;
                    top = out < 9.0F ? sea - 2 - (out > 6.0F ? 1 : 0) : Math.round(Mth.lerp(Terrain.smooth((out - 9.0F) / 8.0F), sea - 3, ground));
                }
                top = Math.round(Mth.lerp(ends, ground, top));
                Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, 12);
                for (int h = top + 1; h <= Math.max(ground, sea - 1); h++) {
                    Terrain.set(level, pos, x, h, z, h <= sea - 1 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
                // chalk all through the cliff (with bands of flint nodules), turf on top
                int from = land && u > f - 5.0F ? Math.min(ground, sea - 6) : Math.min(ground, top) - (land ? 0 : 1);
                for (int h = from; h <= top; h++) {
                    BlockState s = chalk;
                    if (land && h >= top - 1 && top > sea) {
                        s = h == top ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                    } else if (Math.floorMod(h + Math.round(v * 0.04F), 6) == 0 && Terrain.noise(seed, x, h, z) < 0.3F) {
                        s = Blocks.COBBLED_DEEPSLATE.defaultBlockState(); // flint
                    } else if (!land && Terrain.noise(seed, x, h, z) > 0.9F) {
                        s = Blocks.DIORITE.defaultBlockState();
                    }
                    Terrain.set(level, pos, x, h, z, s);
                }
                float n = Terrain.noise(seed, x, top, z);
                if (land && top > sea && n > 0.6F) {
                    Terrain.set(level, pos, x, top + 1, z, n > 0.97F ? Blocks.OXEYE_DAISY.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
                } else if (!land && top < sea - 1 && n > 0.75F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SEAGRASS.defaultBlockState());
                } else if (!land && n < 0.04F && top == sea - 2) {
                    Terrain.set(level, pos, x, top + 1, z, chalk); // a fallen block of chalk on the platform
                }
                if (!land || ends < 0.6F) continue;
                // the sea caves, hollowed into the foot of the cliff
                for (int k = 0; k < CAVES; k++) {
                    float dv = Math.abs(v - caveAlong(k)) / (1.6F + 0.4F * Terrain.hash(seed, 30 + k));
                    if (dv > 1.0F || u < f - caveDepth(k)) continue;
                    int roof = sea + 1 + Math.round(2.5F * (1.0F - dv * dv) * Terrain.smooth((u - f + caveDepth(k)) / 3.0F));
                    for (int h = sea - 3; h <= Math.min(roof, top - 2); h++) {
                        Terrain.set(level, pos, x, h, z, h <= sea - 1 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    }
                    Terrain.set(level, pos, x, sea - 4, z, Blocks.GRAVEL.defaultBlockState());
                }
                if (x == bx && z == bz && top - (sea - 1) <= 16) {
                    // the shaft up from the end of the cave to the clifftop, and the blowhole's mouth
                    for (int h = sea - 3; h < top; h++) Terrain.set(level, pos, x, h, z, h <= sea - 1 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    Terrain.set(level, pos, x, top, z, ModBlocks.BLOWHOLE.get().defaultBlockState());
                    Terrain.set(level, pos, x, top + 1, z, Blocks.AIR.defaultBlockState());
                    level.scheduleTick(pos.set(x, top, z).immutable(), ModBlocks.BLOWHOLE.get(), 100);
                }
            }
        }
    }
}
