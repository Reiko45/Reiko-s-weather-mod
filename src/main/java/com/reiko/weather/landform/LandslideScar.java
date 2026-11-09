package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A landslide scar: a spoon-shaped hollow torn out of a hillside, where the soil, sodden by days of rain, gave
 * way all at once and slid. At its top a raw, steep headscarp of bare earth and rock marks where it broke away;
 * below, everything that came down lies heaped in a hummocky lobe of mud, stones and toppled trees spread out over
 * the lower slope, and nothing much has grown back yet.
 *
 * @param y      the ground at the middle of the scar
 * @param slope  how far the hillside falls per block downhill
 * @param length the scar's half length down the slope
 * @param width  its half width
 * @param lobe   how far the debris runs out below it
 */
public record LandslideScar(int cx, int cz, int y, float angle, float slope, int length, int width, int lobe, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 4 || !c.fits(ground, 40, 30)) {
            return null;
        }
        // a steep hillside: falling away below, and still rising behind
        float[] down = c.downhill(16);
        if (down[1] < 7) {
            return null;
        }
        float a = down[0];
        int behind = c.groundAt(c.x() - Math.round(Mth.cos(a) * 16), c.z() - Math.round(Mth.sin(a) * 16));
        if (behind < ground + 3) {
            return null;
        }
        float slope = Mth.clamp((behind - (ground - down[1])) / 32.0F, 0.3F, 1.5F);
        return new LandslideScar(c.x(), c.z(), ground, a, slope, 9 + c.random().nextInt(7), 6 + c.random().nextInt(5), 12 + c.random().nextInt(11),
                c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new LandslideScar(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getFloat("angle"), tag.getFloat("slope"), tag.getInt("length"),
                tag.getInt("width"), tag.getInt("lobe"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putFloat("angle", angle);
        tag.putFloat("slope", slope);
        tag.putInt("length", length);
        tag.putInt("width", width);
        tag.putInt("lobe", lobe);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.LANDSLIDE_SCAR;
    }

    @Override
    public BoundingBox bounds() {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        float v = width * 1.8F + 3.0F, front = length + lobe + 4.0F, back = length * 1.5F + 4.0F;
        for (float u : new float[]{-back, front}) {
            for (float s : new float[]{-v, v}) {
                double[] p = Terrain.xz(u, s, cx, cz, angle);
                minX = Math.min(minX, Mth.floor(p[0]));
                maxX = Math.max(maxX, Mth.ceil(p[0]));
                minZ = Math.min(minZ, Mth.floor(p[1]));
                maxZ = Math.max(maxZ, Mth.ceil(p[1]));
            }
        }
        return new BoundingBox(minX, Mth.floor(y - slope * front) - 14, minZ, maxX, Mth.ceil(y + slope * back) + 40, maxZ);
    }

    /** The toppled trees lying on the debris: {x, z, length, along x (1) or z (0)}. */
    private int[][] logs() {
        int count = 3 + Math.round(Terrain.hash(seed, 0) * 3.0F);
        int[][] logs = new int[count][];
        for (int i = 0; i < count; i++) {
            float u = length * 0.8F + Terrain.hash(seed, 10 + i) * lobe * 0.8F, v = (Terrain.hash(seed, 20 + i) - 0.5F) * width * 1.6F;
            double[] p = Terrain.xz(u, v, cx, cz, angle);
            logs[i] = new int[]{Mth.floor(p[0]), Mth.floor(p[1]), 3 + Math.round(Terrain.hash(seed, 30 + i) * 3.0F), Terrain.hash(seed, 40 + i) < 0.5F ? 1 : 0};
        }
        return logs;
    }

    private static BlockState wood(Holder<Biome> biome) {
        if (biome.is(BiomeTags.IS_JUNGLE)) return Blocks.JUNGLE_LOG.defaultBlockState();
        if (biome.is(BiomeTags.IS_TAIGA)) return Blocks.SPRUCE_LOG.defaultBlockState();
        if (biome.is(BiomeTags.IS_SAVANNA)) return Blocks.ACACIA_LOG.defaultBlockState();
        return Blocks.OAK_LOG.defaultBlockState();
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[][] logs = logs();
        int depth = 3 + Math.round(length * 0.25F);
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float u = l[0], v = l[1];
                float wobble = 0.9F + 0.2F * Terrain.field(seed, x, z, 6.0);
                // the scar: a scoop out of the hillside, deepest just below its head
                float e = Mth.sqrt(Mth.square((u + length * 0.25F) / (length * 1.25F)) + Mth.square(v / width)) / wobble;
                boolean scar = e < 1.0F && u > -length * 1.5F;
                // the lobe: spreading out below, thickest well down it, with a steep toe
                float s = (u - length * 0.5F) / (lobe + length * 0.5F);
                float spread = width * (1.0F + 0.6F * Mth.clamp(s, 0.0F, 1.0F)) * wobble;
                float lobeT = 0.0F;
                if (s > 0.0F && s < 1.0F && Math.abs(v) < spread) {
                    float q = Math.abs(v) / spread;
                    lobeT = (2.6F + 1.6F * Terrain.hash(seed, 2)) * Terrain.smooth(s / 0.45F) * Terrain.smooth((1.0F - s) / 0.12F)
                            * (float) Math.pow(1.0F - q * q, 0.6);
                    lobeT += 2.0F * (Terrain.field(seed + 1, x, z, 3.5) - 0.5F) * Terrain.smooth(lobeT); // hummocks
                }
                if (!scar && lobeT < 0.5F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 4 || ground > b.maxY() - 34) continue;
                pos.set(x, ground, z);
                Holder<Biome> biome = level.getBiome(pos);
                BlockState was = level.getBlockState(pos);
                boolean grassy = was.is(Blocks.GRASS_BLOCK) || was.is(Blocks.PODZOL) || was.is(Blocks.MYCELIUM);
                int top = ground;
                float n = Terrain.noise(seed, x, ground, z);
                if (scar) {
                    // cut to a smooth spoon below the hillside's plane: a steep headscarp up top, easing out lower down
                    float plane = y - slope * u;
                    float shape = u < -length * 0.25F ? (float) Math.pow(Math.max(0.0F, 1.0F - e * e), 0.3) : 1.0F - e * e;
                    top = Math.min(ground, Math.max(Math.round(plane - depth * shape), ground - depth - 3));
                }
                int fill = Math.round(lobeT);
                int surface = top + Math.max(0, fill);
                // the slide took the trees with it
                Terrain.clearAbove(level, pos, x, Math.max(ground, surface), z, 30);
                for (int h = surface + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                if (fill > 0) {
                    for (int h = top + 1; h <= surface; h++) {
                        float m = Terrain.noise(seed + 2, x, h, z);
                        Terrain.set(level, pos, x, h, z, m < 0.35F ? Blocks.DIRT.defaultBlockState() : m < 0.58F ? Blocks.COARSE_DIRT.defaultBlockState()
                                : m < 0.7F ? Blocks.ROOTED_DIRT.defaultBlockState() : m < 0.84F ? Blocks.GRAVEL.defaultBlockState()
                                : m < 0.93F ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState());
                    }
                    // the top of the heap: churned earth and stones, and the odd raft of turf carried down intact (mostly too
                    // loose and stony for the forest to have taken root in again yet)
                    BlockState turf = grassy && n < 0.08F ? Blocks.GRASS_BLOCK.defaultBlockState() : n < 0.25F ? Blocks.COARSE_DIRT.defaultBlockState()
                            : n < 0.48F ? Blocks.PACKED_MUD.defaultBlockState() : n < 0.56F ? Blocks.ROOTED_DIRT.defaultBlockState()
                            : n < 0.8F ? Blocks.GRAVEL.defaultBlockState() : n < 0.92F ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                    Terrain.set(level, pos, x, surface, z, turf);
                    if (turf.is(Blocks.GRASS_BLOCK) && n < 0.04F) Terrain.set(level, pos, x, surface + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                    else if (n > 0.95F) Terrain.set(level, pos, x, surface + 1, z, n > 0.98F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                } else if (top < ground || scar) {
                    // the bare floor of the scar: raw, packed earth and rock, stonier up at the head where it cut deepest (too
                    // bare for anything to grow in yet)
                    float rock = (ground - top) / (float) depth * 0.5F + (u < 0.0F ? 0.15F : 0.0F);
                    BlockState bare = n < rock * 0.6F ? Blocks.STONE.defaultBlockState() : n < 0.42F ? Blocks.PACKED_MUD.defaultBlockState()
                            : n < 0.6F ? Blocks.COARSE_DIRT.defaultBlockState() : n < 0.68F ? Blocks.DIRT.defaultBlockState()
                            : n < 0.88F ? Blocks.GRAVEL.defaultBlockState() : n < 0.95F ? Blocks.STONE.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState();
                    if (!was.is(BlockTags.DIRT) && !was.is(BlockTags.SAND) && !was.is(Blocks.GRAVEL) && top == ground) bare = was; // already bare rock
                    Terrain.set(level, pos, x, top, z, bare);
                    if (top - 1 > b.minY()) {
                        pos.set(x, top - 1, z);
                        if (level.getBlockState(pos).is(BlockTags.DIRT)) Terrain.set(level, pos, x, top - 1, z, Blocks.DIRT.defaultBlockState());
                    }
                }
                // trees knocked flat and carried down on the lobe
                for (int[] log : logs) {
                    boolean along = log[3] == 1;
                    int k = along ? x - log[0] : z - log[1];
                    if ((along ? z != log[1] : x != log[0]) || k < 0 || k >= log[2]) continue;
                    BlockState w = wood(biome).setValue(RotatedPillarBlock.AXIS, along ? Direction.Axis.X : Direction.Axis.Z);
                    Terrain.set(level, pos, x, surface + 1, z, w);
                }
            }
        }
    }
}
