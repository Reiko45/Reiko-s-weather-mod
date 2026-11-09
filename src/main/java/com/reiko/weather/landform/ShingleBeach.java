package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A shingle beach: a steep beach of rounded pebbles and cobbles rather than sand, on a coast open to big storm waves.
 * Each great storm throws the stones higher than the ordinary waves can reach and leaves them heaped in a ridge (a
 * berm), so the beach climbs from the water in a flight of ridges, the highest from the worst storm, with bleached
 * driftwood stranded along its top.
 *
 * @param ridges how many storm ridges it climbs in (2 or 3)
 */
public record ShingleBeach(int cx, int cz, int sea, float angle, int length, int ridges, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        return c.search(12, 12, ShingleBeach::planAt);
    }

    @Nullable
    private static Landform planAt(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() - 2 || ground > c.seaLevel() + 4) {
            return null;
        }
        float seaward = c.seaward(20, 2);
        if (Float.isNaN(seaward)) {
            return null;
        }
        return new ShingleBeach(c.x(), c.z(), c.seaLevel(), seaward, 50 + c.random().nextInt(31), 2 + c.random().nextInt(2), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new ShingleBeach(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("ridges"),
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
        tag.putInt("ridges", ridges);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.SHINGLE_BEACH;
    }

    @Override
    public BoundingBox bounds() {
        float half = length / 2.0F + 1.0F;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (float u : new float[]{-(back() + 11.0F), 16.0F}) {
            for (float v : new float[]{-half, half}) {
                double[] w = Terrain.xz(u, v, cx, cz, angle);
                minX = Math.min(minX, Mth.floor(w[0]));
                maxX = Math.max(maxX, Mth.floor(w[0]));
                minZ = Math.min(minZ, Mth.floor(w[1]));
                maxZ = Math.max(maxZ, Mth.floor(w[1]));
            }
        }
        return new BoundingBox(minX, sea - 20, minZ, maxX, sea + 40, maxZ);
    }

    /** How far the waterline sits out to sea of the middle, {@code v} along the coast: a gently wavering line. */
    private float shore(float v) {
        return 1.5F * Mth.sin(v * 0.05F + Terrain.hash(seed, 1) * 6.0F) + 0.8F * Mth.sin(v * 0.13F + Terrain.hash(seed, 2) * 6.0F);
    }

    /** How far up the beach the crest of storm ridge {@code k} is. */
    private float crest(int k) {
        return 2.5F + 5.0F * k + Terrain.hash(seed, 10 + k) - 0.5F;
    }

    private int crestHeight(int k) {
        return sea + 1 + Math.round(k * 1.5F);
    }

    /** The back of the top ridge, where the beach gives way to the land. */
    private float back() {
        return crest(ridges - 1) + 2.0F;
    }

    /** The beach's height {@code w} blocks up from the waterline: each ridge a steep face to the sea and a gentle back. */
    private float profile(float w) {
        if (w < 0.0F) return sea - 1 + w * 0.5F;
        float lastW = 0.0F, lastH = sea - 1;
        for (int k = 0; k < ridges; k++) {
            float cw = crest(k), ch = crestHeight(k);
            if (w < cw) return Mth.lerp((w - lastW) / (cw - lastW), lastH, ch);
            if (w < cw + 2.0F) return Mth.lerp((w - cw) / 2.0F, ch, ch - 0.6F);
            lastW = cw + 2.0F;
            lastH = ch - 0.6F;
        }
        return lastH;
    }

    /** Shingle: mostly gravel, coarser cobbles heaped in patches, and the odd pale or dark pebble. */
    private BlockState shingle(int x, int y, int z) {
        float n = Terrain.noise(seed, x, y, z), coarse = Terrain.field(seed + 1, x, z, 4.0);
        if (n < 0.05F) return Blocks.DIORITE.defaultBlockState();
        if (n < 0.13F) return Blocks.ANDESITE.defaultBlockState();
        if (n < 0.13F + 0.4F * coarse) return Blocks.COBBLESTONE.defaultBlockState();
        return Blocks.GRAVEL.defaultBlockState();
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float half = length / 2.0F, back = back();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float v = l[1], w = shore(v) - l[0];
                if (Math.abs(v) > half || w < -12.0F || w > back + 8.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground > b.maxY() - 10 || ground < b.minY() + 3) continue;
                float target;
                if (w < -6.0F) {
                    target = Mth.lerp(Terrain.smooth((-w - 6.0F) / 6.0F), sea - 4, ground); // shelving off to the sea bed
                } else if (w <= back) {
                    target = profile(w);
                } else {
                    target = Mth.lerp(Terrain.smooth((w - back) / 8.0F), profile(back), ground); // into the land behind
                }
                // the beach fades back into the ordinary shore at both ends
                float ends = Terrain.smooth((half - Math.abs(v)) / 12.0F);
                int top = Math.round(Mth.lerp(ends, ground, target));
                if (top == ground && (w > back + 4.0F || ends < 0.3F)) continue;
                Terrain.clearAbove(level, pos, x, ground, z, Math.max(0, top - ground) + 8);
                for (int h = top + 1; h <= ground; h++) {
                    Terrain.set(level, pos, x, h, z, h <= sea - 1 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
                // a deep skin of shingle over whatever was there
                for (int h = Math.max(b.minY(), Math.min(ground + 1, top - 2)); h <= top; h++) Terrain.set(level, pos, x, h, z, shingle(x, h, z));
                if (top < sea - 2 && Terrain.noise(seed, x, 3, z) > 0.9F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SEAGRASS.defaultBlockState());
                }
            }
        }
        // driftwood: bleached logs the storms have left lying along the top ridge
        Direction.Axis along = Math.abs(Mth.sin(angle)) > Math.abs(Mth.cos(angle)) ? Direction.Axis.X : Direction.Axis.Z;
        int logs = 2 + Math.round(Terrain.hash(seed, 40) * 2.0F);
        for (int k = 0; k < logs; k++) {
            float v = (Terrain.hash(seed, 41 + k) - 0.5F) * (length - 30.0F);
            float w = crest(ridges - 1) + 0.3F;
            double[] p = Terrain.xz(shore(v) - w, v, cx, cz, angle);
            int len = 3 + Math.round(Terrain.hash(seed, 50 + k) * 2.0F);
            int sx = Mth.floor(p[0]) - (along == Direction.Axis.X ? len / 2 : 0), sz = Mth.floor(p[1]) - (along == Direction.Axis.Z ? len / 2 : 0);
            // it rests on the highest stones under it
            int y = Integer.MIN_VALUE;
            for (int i = 0; i < len; i++) {
                int bx = sx + (along == Direction.Axis.X ? i : 0), bz = sz + (along == Direction.Axis.Z ? i : 0);
                float[] lb = Terrain.uv(bx + 0.5, bz + 0.5, cx, cz, angle);
                y = Math.max(y, Math.round(profile(Math.min(back, shore(lb[1]) - lb[0]))) + 1);
            }
            float pick = Terrain.hash(seed, 60 + k);
            BlockState wood = (pick < 0.4F ? Blocks.STRIPPED_OAK_LOG : pick < 0.7F ? Blocks.STRIPPED_SPRUCE_LOG : pick < 0.9F ? Blocks.STRIPPED_BIRCH_LOG
                    : Blocks.STRIPPED_DARK_OAK_LOG).defaultBlockState().setValue(RotatedPillarBlock.AXIS, along);
            for (int i = 0; i < len; i++) {
                int bx = sx + (along == Direction.Axis.X ? i : 0), bz = sz + (along == Direction.Axis.Z ? i : 0);
                if (!area.isInside(bx, y, bz)) continue;
                pos.set(bx, y, bz);
                if (level.getBlockState(pos).isAir()) level.setBlock(pos, wood, Terrain.FLAGS);
            }
        }
    }
}
