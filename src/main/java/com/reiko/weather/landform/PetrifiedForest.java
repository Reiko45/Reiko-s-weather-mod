package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A petrified forest: the trunks of a forest that fell over two hundred million years ago, turned to stone and
 * now weathering back out of the ground. Floods buried the fallen trees in mud and volcanic ash, and water
 * seeping through it slowly replaced the wood with quartz, cell by cell. Now the soft, banded clays that buried
 * them (blue, grey, white and brown, rounded into bare little hills) are washing away again and leaving the logs
 * lying about the badlands, broken into neat segments as if sawn, with here and there a stump still standing.
 *
 * @param low  the lowest and highest ground sampled (for the bounds)
 */
public record PetrifiedForest(int cx, int cz, int low, int high, long seed) implements Landform {
    private static final float RADIUS = 38.0F;
    private static final BlockState[] BANDS = {Blocks.LIGHT_BLUE_TERRACOTTA.defaultBlockState(), Blocks.LIGHT_BLUE_TERRACOTTA.defaultBlockState(),
            Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState(), Blocks.WHITE_TERRACOTTA.defaultBlockState(), Blocks.GRAY_TERRACOTTA.defaultBlockState(),
            Blocks.LIGHT_BLUE_TERRACOTTA.defaultBlockState(), Blocks.BROWN_TERRACOTTA.defaultBlockState(), Blocks.WHITE_TERRACOTTA.defaultBlockState(),
            Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState(), Blocks.CYAN_TERRACOTTA.defaultBlockState(), Blocks.LIGHT_BLUE_TERRACOTTA.defaultBlockState(),
            Blocks.BROWN_TERRACOTTA.defaultBlockState(), Blocks.PURPLE_TERRACOTTA.defaultBlockState(), Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState()};

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 2 || !c.fits(ground, 10, 14)) {
            return null;
        }
        int low = ground, high = ground;
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F;
            int g = c.groundAt(c.x() + Math.round(Mth.cos(a) * 32), c.z() + Math.round(Mth.sin(a) * 32));
            low = Math.min(low, g);
            high = Math.max(high, g);
        }
        if (high - low > 12) {
            return null;
        }
        return new PetrifiedForest(c.x(), c.z(), low, high, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new PetrifiedForest(tag.getInt("x"), tag.getInt("z"), tag.getInt("low"), tag.getInt("high"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("low", low);
        tag.putInt("high", high);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.PETRIFIED_FOREST;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(RADIUS * 1.15F) + 4;
        return new BoundingBox(cx - r, low - 10, cz - r, cx + r, high + 20, cz + r);
    }

    /** The low banded hills: {x, z, radius, height}. */
    private float[][] mounds() {
        int count = 5 + Math.round(Terrain.hash(seed, 0) * 4.0F);
        float[][] list = new float[count][];
        for (int i = 0; i < count; i++) {
            float a = Terrain.hash(seed, 10 + i) * Mth.TWO_PI, r = Mth.sqrt(Terrain.hash(seed, 20 + i)) * RADIUS * 0.85F;
            list[i] = new float[]{cx + Mth.cos(a) * r, cz + Mth.sin(a) * r, 5.0F + Terrain.hash(seed, 30 + i) * 6.0F, 2.0F + Terrain.hash(seed, 40 + i) * 4.0F};
        }
        return list;
    }

    /**
     * The fallen logs: {x, z, length, along x (1) or z (0), thick (1) or not (0), id}. Each is broken up into
     * segments, a few of them knocked a block out of line.
     */
    private int[][] logs() {
        int count = 16 + Math.round(Terrain.hash(seed, 1) * 12.0F);
        int[][] list = new int[count][];
        for (int i = 0; i < count; i++) {
            float a = Terrain.hash(seed, 100 + i) * Mth.TWO_PI, r = Mth.sqrt(Terrain.hash(seed, 200 + i)) * RADIUS * 0.85F;
            int length = 5 + Math.round(Terrain.hash(seed, 300 + i) * 11.0F);
            boolean along = Terrain.hash(seed, 400 + i) < 0.5F;
            int x = Mth.floor(cx + Mth.cos(a) * r) - (along ? length / 2 : 0), z = Mth.floor(cz + Mth.sin(a) * r) - (along ? 0 : length / 2);
            list[i] = new int[]{x, z, length, along ? 1 : 0, Terrain.hash(seed, 500 + i) < 0.25F ? 1 : 0, i};
        }
        return list;
    }

    /** Whether log {@code log} has a piece at a column, and if so how far it's been knocked aside (or -100 for a gap). */
    private int piece(int[] log, int k) {
        int start = 0, seg = 0;
        while (start < log[2]) {
            int len = 2 + Math.round(Terrain.hash(seed, 1000 + log[5] * 40 + seg) * 3.0F);
            if (k < start + len) {
                float h = Terrain.hash(seed, 3000 + log[5] * 40 + seg);
                return h < 0.12F ? -1 : h > 0.88F ? 1 : 0;
            }
            start += len;
            // the cracks between the segments, some opened up into gaps
            int gap = Terrain.hash(seed, 2000 + log[5] * 40 + seg) < 0.55F ? 1 : 0;
            if (k < start + gap) return -100;
            start += gap;
            seg++;
        }
        return -100;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float[][] mounds = mounds();
        int[][] logs = logs();
        int offset = Math.round(Terrain.hash(seed, 2) * BANDS.length);
        BlockState wood = ModBlocks.PETRIFIED_WOOD.get().defaultBlockState();
        int stumps = 3 + Math.round(Terrain.hash(seed, 3) * 3.0F);
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float e = Mth.sqrt(Mth.square(x + 0.5F - cx) + Mth.square(z + 0.5F - cz)) / (RADIUS * (0.85F + 0.3F * Terrain.field(seed, x, z, 14.0)));
                if (e > 1.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 3 || ground > b.maxY() - 13 || !level.getFluidState(pos.set(x, ground + 1, z)).isEmpty()) continue;
                pos.set(x, ground, z);
                BlockState was = level.getBlockState(pos);
                boolean soil = was.is(BlockTags.DIRT) || was.is(BlockTags.SAND) || was.is(BlockTags.TERRACOTTA) || was.is(Blocks.GRAVEL);
                // the bentonite hills, gullied all over by the rain
                float mound = 0.0F;
                for (float[] m : mounds) {
                    float dx = x + 0.5F - m[0], dz = z + 0.5F - m[1];
                    float d = Mth.sqrt(dx * dx + dz * dz) / m[2];
                    if (d >= 1.0F) continue;
                    float rills = d > 0.3F ? 1.0F - 0.3F * (float) Math.pow(Math.max(0.0F, Mth.sin((float) Mth.atan2(dz, dx) * 11.0F + m[0])), 4.0) : 1.0F;
                    mound = Math.max(mound, m[3] * (float) Math.pow(1.0F - d * d, 1.2) * rills);
                }
                int top = ground + Math.round(mound);
                Terrain.clearAbove(level, pos, x, ground, z, 8);
                for (int h = ground + 1; h <= top; h++) {
                    Terrain.set(level, pos, x, h, z, BANDS[Math.floorMod(h + offset, BANDS.length)]);
                }
                if (top == ground && soil) {
                    // the flats between: pale washes of clay and grit
                    float f = Terrain.field(seed + 4, x, z, 5.0);
                    float n = Terrain.noise(seed, x, ground, z);
                    BlockState flat = f < 0.3F ? Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState() : f < 0.45F ? Blocks.WHITE_TERRACOTTA.defaultBlockState()
                            : f < 0.75F ? Blocks.RED_SAND.defaultBlockState() : f < 0.88F ? Blocks.TERRACOTTA.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                    if (n > 0.92F) flat = Blocks.GRAVEL.defaultBlockState();
                    Terrain.set(level, pos, x, ground, z, flat);
                    if (n < 0.012F && !flat.is(Blocks.GRAVEL)) Terrain.set(level, pos, x, ground + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                } else if (top > ground) {
                    Terrain.set(level, pos, x, ground, z, BANDS[Math.floorMod(ground + offset, BANDS.length)]);
                }
                // the logs: half buried where they're still weathering out of a hill, lying loose on the flats
                int lay = mound > 0.5F ? top : top + 1;
                for (int[] log : logs) {
                    boolean along = log[3] == 1;
                    int k = along ? x - log[0] : z - log[1];
                    if (k < 0 || k >= log[2]) continue;
                    int shift = piece(log, k);
                    if (shift == -100) continue;
                    int side = along ? z - log[1] - shift : x - log[0] - shift;
                    if (side < 0 || side > log[4]) continue;
                    BlockState w = wood.setValue(RotatedPillarBlock.AXIS, along ? Direction.Axis.X : Direction.Axis.Z);
                    Terrain.set(level, pos, x, lay, z, w);
                    if (log[4] == 1) Terrain.set(level, pos, x, lay + 1, z, w); // the great trunks stand two blocks thick
                }
                // a few stumps still standing where they grew
                for (int i = 0; i < stumps; i++) {
                    float a = Terrain.hash(seed, 600 + i) * Mth.TWO_PI, r = Mth.sqrt(Terrain.hash(seed, 610 + i)) * RADIUS * 0.75F;
                    int sx = Mth.floor(cx + Mth.cos(a) * r), sz = Mth.floor(cz + Mth.sin(a) * r);
                    boolean big = Terrain.hash(seed, 620 + i) < 0.4F;
                    if (x < sx || z < sz || x > sx + (big ? 1 : 0) || z > sz + (big ? 1 : 0)) continue;
                    int height = 1 + Math.round(Terrain.hash(seed, 630 + i) * 2.0F) + (big ? 1 : 0);
                    for (int h = top; h <= top + height; h++) {
                        Terrain.set(level, pos, x, h, z, wood.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
                    }
                }
                // and chips of petrified wood strewn about
                if (Terrain.noise(seed + 9, x, top, z) > 0.988F) {
                    pos.set(x, top + 1, z);
                    if (level.getBlockState(pos).isAir()) {
                        Terrain.set(level, pos, x, top + 1, z, wood.setValue(RotatedPillarBlock.AXIS, Terrain.noise(seed + 10, x, top, z) < 0.5F ? Direction.Axis.X : Direction.Axis.Z));
                    }
                }
            }
        }
    }
}
