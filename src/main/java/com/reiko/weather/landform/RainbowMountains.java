package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Rainbow mountains (like Vinicunca in the Andes, or the Danxia hills of Zhangye): rounded hills striped red, ochre,
 * yellow, white, pink, green and lavender. Each stripe began as a flat layer of mud, sand or silt on the floor of an
 * old lake or sea, coloured by its own minerals: iron oxides for the reds and yellows, chlorite for the greens, gypsum
 * and calcite for the whites. Mountain building later tilted and folded the beds, and rain and frost have worn the
 * hills into soft domes that cut across them, so the stripes sweep and dip over the slopes rather than lying flat.
 *
 * @param hills {x, z, radius, height, base} for each hill
 */
public record RainbowMountains(int cx, int cz, int[] hills, long seed) implements Landform {
    private static final int SPREAD = 26;
    /** The beds in order, a block each (so a colour repeated is a thicker bed). */
    private static final BlockState[] BANDS = {
            Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState(),
            Blocks.YELLOW_TERRACOTTA.defaultBlockState(), Blocks.YELLOW_TERRACOTTA.defaultBlockState(), Blocks.WHITE_TERRACOTTA.defaultBlockState(),
            Blocks.PINK_TERRACOTTA.defaultBlockState(), Blocks.PINK_TERRACOTTA.defaultBlockState(), Blocks.LIME_TERRACOTTA.defaultBlockState(),
            Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.PURPLE_TERRACOTTA.defaultBlockState(), Blocks.PURPLE_TERRACOTTA.defaultBlockState(),
            Blocks.CALCITE.defaultBlockState(), Blocks.MAGENTA_TERRACOTTA.defaultBlockState(), Blocks.RED_TERRACOTTA.defaultBlockState(),
            Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState(), Blocks.CYAN_TERRACOTTA.defaultBlockState(), Blocks.LIME_TERRACOTTA.defaultBlockState(),
            Blocks.YELLOW_TERRACOTTA.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState(),
            Blocks.TERRACOTTA.defaultBlockState(), Blocks.PINK_TERRACOTTA.defaultBlockState(), Blocks.WHITE_TERRACOTTA.defaultBlockState(),
            Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.PURPLE_TERRACOTTA.defaultBlockState(), Blocks.GREEN_TERRACOTTA.defaultBlockState(),
            Blocks.YELLOW_TERRACOTTA.defaultBlockState()};

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 3 || c.relief(SPREAD, 8) > 14 || !c.fits(ground, 14, 50)) {
            return null;
        }
        int n = 2 + c.random().nextInt(3);
        int[] hills = new int[n * 5];
        float start = c.random().nextFloat() * Mth.TWO_PI;
        for (int i = 0; i < n; i++) {
            // one hill near the middle, the rest in a loose cluster round it
            float a = start + i * Mth.TWO_PI / Math.max(1, n - 1) + (c.random().nextFloat() - 0.5F) * 0.8F;
            float d = i == 0 ? c.random().nextFloat() * 6.0F : 16.0F + c.random().nextFloat() * (SPREAD - 10);
            int x = c.x() + Math.round(Mth.cos(a) * d), z = c.z() + Math.round(Mth.sin(a) * d);
            int h = 15 + c.random().nextInt(16);
            int r = Math.round(h * 0.9F) + 4 + c.random().nextInt(6);
            // each stands on the lowest ground round its foot
            int low = c.groundAt(x, z);
            for (int k = 0; k < 4; k++) {
                float b = k * Mth.HALF_PI + 0.4F;
                low = Math.min(low, c.groundAt(x + Math.round(Mth.cos(b) * r * 0.8F), z + Math.round(Mth.sin(b) * r * 0.8F)));
            }
            hills[i * 5] = x;
            hills[i * 5 + 1] = z;
            hills[i * 5 + 2] = r;
            hills[i * 5 + 3] = h;
            hills[i * 5 + 4] = Math.max(low, c.seaLevel());
        }
        return new RainbowMountains(c.x(), c.z(), hills, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new RainbowMountains(tag.getInt("x"), tag.getInt("z"), tag.getIntArray("hills"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.put("hills", new IntArrayTag(hills));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.RAINBOW_MOUNTAINS;
    }

    @Override
    public BoundingBox bounds() {
        int minX = cx, maxX = cx, minZ = cz, maxZ = cz, low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int i = 0; i < hills.length; i += 5) {
            int r = Mth.ceil(hills[i + 2] * 1.36F) + 2;
            minX = Math.min(minX, hills[i] - r);
            maxX = Math.max(maxX, hills[i] + r);
            minZ = Math.min(minZ, hills[i + 1] - r);
            maxZ = Math.max(maxZ, hills[i + 1] + r);
            low = Math.min(low, hills[i + 4]);
            high = Math.max(high, hills[i + 4] + hills[i + 3]);
        }
        return new BoundingBox(minX, low - 20, minZ, maxX, high + 16, maxZ);
    }

    /** How high hill {@code i} stands above its base at a point (0 off it). */
    private float rise(int i, float x, float z) {
        float dx = x - hills[i * 5], dz = z - hills[i * 5 + 1];
        float r = hills[i * 5 + 2], h = hills[i * 5 + 3];
        // a little longer one way than the other, and lumpy round the shoulders
        float a = Terrain.hash(seed, 10 + i) * Mth.PI, stretch = 1.0F + 0.25F * Terrain.hash(seed, 20 + i);
        float ca = Mth.cos(a), sa = Mth.sin(a);
        float p = (dx * ca + dz * sa) / stretch, q = -dx * sa + dz * ca;
        float d2 = (p * p + q * q) / (r * r) * (0.85F + 0.3F * Terrain.field(seed + i, x, z, 11.0));
        if (d2 >= 1.0F) return 0.0F;
        float dome = (float) Math.pow(1.0F - d2, 1.4);
        // rills fanning down the flanks from the top, where the rain runs off
        float theta = (float) Mth.atan2(q, p);
        float rill = Mth.sin(theta * (7 + i) + 3.0F * Terrain.field(seed + 40 + i, x, z, 8.0));
        dome *= 1.0F - 0.14F * Math.max(0.0F, rill) * Terrain.smooth(Mth.sqrt(d2) / 0.7F);
        return h * dome;
    }

    /**
     * The bed at a block. The beds dip one way and are folded into broad waves across that, so where a hill cuts
     * through them they show as curving stripes rather than level ones.
     */
    private BlockState band(int x, int y, int z) {
        float dip = Terrain.hash(seed, 1) * Mth.TWO_PI;
        float dx = x - cx, dz = z - cz;
        float u = dx * Mth.cos(dip) + dz * Mth.sin(dip), v = -dx * Mth.sin(dip) + dz * Mth.cos(dip);
        float tilt = 0.18F + 0.22F * Terrain.hash(seed, 2);
        float k = 0.035F + 0.035F * Terrain.hash(seed, 3);
        // the fold axes bend a little as they run across the hills
        float fold = (3.0F + 4.0F * Terrain.hash(seed, 4)) * Mth.sin(u * k + 1.5F * Mth.sin(v * 0.03F + Terrain.hash(seed, 5) * 6.0F));
        float s = y + u * tilt + fold + 3.0F * Terrain.field(seed + 7, x, z, 9.0);
        return BANDS[Math.floorMod(Mth.floor(s), BANDS.length)];
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int count = hills.length / 5;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                int top = Integer.MIN_VALUE;
                float most = 0.0F;
                for (int i = 0; i < count; i++) {
                    float r = rise(i, x + 0.5F, z + 0.5F);
                    if (r <= 0.0F) continue;
                    most = Math.max(most, r);
                    top = Math.max(top, hills[i * 5 + 4] + Math.round(r));
                }
                // a ragged edge where the striped ground gives way to the land round about
                if (top == Integer.MIN_VALUE || most + 2.0F * Terrain.noise(seed, x, 1, z) < 1.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 4 || ground > b.maxY() - 14) continue;
                int surface = Math.max(ground, top);
                Terrain.clearAbove(level, pos, x, ground, z, 12);
                // built up out of the beds, with the ground under its foot stripped back to them as well
                for (int h = ground - 2; h <= surface; h++) {
                    pos.set(x, h, z);
                    if (h <= ground) {
                        BlockState was = level.getBlockState(pos);
                        if (was.isAir() || !was.getFluidState().isEmpty()) continue;
                    }
                    level.setBlock(pos, band(x, h, z), Terrain.FLAGS);
                }
                // a dead bush here and there on the bare lower slopes
                BlockState face = band(x, surface, z);
                if (face.is(BlockTags.TERRACOTTA) && most < 8.0F && Terrain.noise(seed, x, surface, z) > 0.985F) {
                    pos.set(x, surface + 1, z);
                    if (level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.DEAD_BUSH.defaultBlockState(), Terrain.FLAGS);
                }
            }
        }
    }
}
