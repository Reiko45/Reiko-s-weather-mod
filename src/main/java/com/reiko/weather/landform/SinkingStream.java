package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * A sinking stream: a brook that runs off ordinary ground onto limestone and vanishes. It comes down the slope in a
 * string of still pools, each held behind a lip of stones, to the foot of a limestone scarp at the head of its little
 * blind valley, and there it slips into a dark cave mouth (a swallet, or swallow hole) and down a flooded passage into
 * the rock. Below the surface it finds its own way through the limestone, and a good way further down the hill it
 * comes out again at a karst spring: a clear pool welling up at the mouth of a small cave in the hillside.
 *
 * @param steps {u where it ends, water y} for each pool in turn, down the stream (the last ends at the scarp)
 * @param scarp the height of the scarp's crest
 * @param spring how far down the stream's line the spring's pool is
 * @param rise  the spring pool's water level
 */
public record SinkingStream(int cx, int cz, float angle, int[] steps, int scarp, int spring, int rise, long seed) implements Landform {
    private static final int SCARP = 10, TUNNEL = 13;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 6 || !c.fits(ground, 26, 24)) {
            return null;
        }
        // down a gentle slope
        float[] down = c.downhill(32);
        if (down[1] < 2 || down[1] > 20) {
            return null;
        }
        float angle = down[0], ux = Mth.cos(angle), uz = Mth.sin(angle);
        int n = 3 + c.random().nextInt(3);
        int[] steps = new int[n * 2];
        int u = 0, water = 0;
        for (int i = 0; i < n; i++) {
            int len = 5 + c.random().nextInt(3);
            int g = c.groundAt(c.x() + Math.round(ux * (u + len / 2.0F)), c.z() + Math.round(uz * (u + len / 2.0F)));
            // each pool a step or two down from the one above it
            water = i == 0 ? g - 1 : Math.min(g - 1, water - 1 - c.random().nextInt(2));
            u += len;
            steps[i * 2] = u;
            steps[i * 2 + 1] = water;
        }
        if (water < c.seaLevel() + 1) {
            return null;
        }
        int behind = c.groundAt(c.x() + Math.round(ux * (u + 4)), c.z() + Math.round(uz * (u + 4)));
        int scarp = Math.max(behind, water + 1) + 6 + c.random().nextInt(4);
        // the spring, a good way further down and lower
        for (int dist = 32; dist <= 52; dist += 10) {
            int p = u + dist;
            int g = c.groundAt(c.x() + Math.round(ux * p), c.z() + Math.round(uz * p));
            int rise = Math.min(g - 1, water - 3);
            if (g - 1 - rise <= 5 && rise > c.seaLevel()) {
                return new SinkingStream(c.x(), c.z(), angle, steps, scarp, p, rise, c.random().nextLong());
            }
        }
        return null;
    }

    static Landform load(CompoundTag tag) {
        return new SinkingStream(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getIntArray("steps"), tag.getInt("scarp"), tag.getInt("spring"),
                tag.getInt("rise"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.put("steps", new IntArrayTag(steps));
        tag.putInt("scarp", scarp);
        tag.putInt("spring", spring);
        tag.putInt("rise", rise);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.SINKING_STREAM;
    }

    @Override
    public BoundingBox bounds() {
        float u0 = -8.0F, u1 = spring + springR() + 7.0F;
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (float u : new float[]{u0, u1}) {
            for (float v : new float[]{-17.0F, 17.0F}) {
                double[] p = Terrain.xz(u, v, cx, cz, angle);
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minZ = Math.min(minZ, p[1]);
                maxZ = Math.max(maxZ, p[1]);
            }
        }
        return new BoundingBox(Mth.floor(minX), Math.min(sink() - 17, rise - 10), Mth.floor(minZ), Mth.ceil(maxX), Math.max(steps[1], scarp) + 30, Mth.ceil(maxZ));
    }

    private int pools() {
        return steps.length / 2;
    }

    /** Where the scarp's face stands across the stream's line. */
    private int face() {
        return steps[steps.length - 2];
    }

    /** The water level of the last pool, at the swallet. */
    private int sink() {
        return steps[steps.length - 1];
    }

    private float springR() {
        return 2.5F + Terrain.hash(seed, 2);
    }

    /** Where the spring's pool meets the hillside, and its cave goes in. */
    private float mouth() {
        return spring - springR();
    }

    /** The stream's wanderings (straightening out as it comes to the scarp, so it runs square into the cave). */
    private float meander(float u) {
        return 1.5F * Mth.sin(u * 0.25F + Terrain.hash(seed, 1) * Mth.TWO_PI) * Terrain.smooth((face() - 3.0F - u) / 5.0F);
    }

    private int pool(float u) {
        for (int i = 0; i < pools(); i++) {
            if (u < steps[i * 2]) return i;
        }
        return pools() - 1;
    }

    /** How wide pool {@code i} is here: narrow at its lips, wider between (and for the last, widest against the scarp). */
    private float width(int i, float u) {
        float start = i == 0 ? 0.0F : steps[i * 2 - 2], end = steps[i * 2];
        float t = Mth.clamp((u - start) / (end - start), 0.0F, 1.0F);
        float wide = 2.0F + 1.2F * Terrain.hash(seed, 10 + i);
        float shape = i == pools() - 1 ? Mth.sin(Mth.HALF_PI * Math.min(1.0F, t * 1.6F)) : Mth.sin(Mth.PI * t);
        return 1.0F + (wide - 1.0F) * (float) Math.pow(Math.max(0.0F, shape), 0.7);
    }

    private float scarpHalf(float u) {
        return 10.0F + 3.0F * Terrain.field(seed + 6, u, 0.0, 6.0);
    }

    /** The middle of the swallet's passage, sloping down into the rock, and how wide it is (closing at its far end). */
    private float sinkHeight(float u) {
        return sink() + 1.4F - Math.max(0.0F, u - face() - 0.5F) * 0.9F;
    }

    private float sinkRadius(float u) {
        float r = 2.0F - 0.3F * Terrain.smooth((u - face()) / 6.0F);
        return r * Mth.sqrt(Mth.clamp((face() + TUNNEL - u) / 2.0F, 0.0F, 1.0F));
    }

    private float riseHeight(float u) {
        return rise + 1.3F - Math.max(0.0F, mouth() + 0.5F - u) * 0.6F;
    }

    private float riseRadius(float u) {
        return 1.6F * Mth.sqrt(Mth.clamp((u - (mouth() - 8.0F)) / 2.0F, 0.0F, 1.0F));
    }

    private static boolean tube(float vm, float h, float centre, float r) {
        return r > 0.0F && Mth.square(vm / r) + Mth.square((h - centre) / (r * 1.15F)) < 1.0F;
    }

    private static boolean shell(float vm, float h, float centre, float r) {
        return Mth.square(vm / (r + 2.0F)) + Mth.square((h - centre) / (r * 1.15F + 2.0F)) < 1.0F;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState limestone = ModBlocks.LIMESTONE.get().defaultBlockState();
        int face = face(), sink = sink();
        float sr = springR(), mouth = mouth();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float u = l[0], v = l[1], vm = v - meander(u), off = Math.abs(vm);
                if (u < -7.0F || u > spring + sr + 6.0F || Math.abs(v) > 16.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground > b.maxY() - 12 || ground < b.minY() + 3) continue;
                // any water already standing here: it's dammed rather than let into the stream's pools
                int surface = ground;
                for (pos.set(x, ground + 1, z); surface < ground + 8 && !level.getFluidState(pos).isEmpty(); pos.move(Direction.UP)) surface++;
                boolean flooded = surface > ground;
                float rough = (Terrain.field(seed + 3, x, z, 4.0) - 0.5F) * 1.5F;
                int top = Integer.MIN_VALUE, water = Integer.MIN_VALUE, bed = 0, kind = 0;
                if (u < face) {
                    // the stream, as a chain of pools
                    int i = pool(u), w0 = steps[i * 2 + 1];
                    float w = u < 0.0F ? 0.0F : width(i, u);
                    boolean lip = i < pools() - 1 && u >= steps[i * 2] - 1.4F;
                    if (off < w && lip) {
                        top = flooded ? Math.max(w0, surface) : w0; // flush with the water it holds back
                        kind = 2;
                    } else if (off < w && !flooded) {
                        water = w0;
                        bed = w0 - (off < w * 0.5F ? 2 : 1);
                    } else {
                        // its banks, and the sides of the shallow valley it's cut (rising at its head, above the first pool)
                        float target = w0 + 1 + Math.max(0.0F, off - w - 1.0F) * 0.9F + Math.max(0.0F, -u - 1.0F) * 1.2F + rough;
                        int t = Math.max(w0 + 1, Math.round(target));
                        if (flooded) top = Math.max(t, surface);
                        else if (ground > t) top = t;
                        else if (off < w + 2.5F && ground < w0 + 1) top = w0 + 1;
                        kind = 1;
                    }
                } else if (u <= face + SCARP) {
                    if (Math.abs(v) >= scarpHalf(u)) continue;
                    // the scarp: a sheer face over the swallet, its top sloping back into the land behind
                    float back = Terrain.smooth((u - face - 1.5F) / (SCARP - 1.5F));
                    float side = Terrain.smooth((scarpHalf(u) - Math.abs(v)) / 4.0F);
                    int crest = scarp + Math.round((Terrain.field(seed + 7, x, z, 2.5) - 0.5F) * 3.0F);
                    int st = Math.round(Mth.lerp(side, ground, Mth.lerp(back, crest, ground)));
                    top = Math.max(Math.max(ground, surface), st);
                    kind = u < face + 2.5F ? 3 : 4;
                } else {
                    // the spring: a pool at the foot of a knoll, where the cave comes out
                    float ds = Mth.sqrt(Mth.square(u - spring) + v * v), wob = 0.85F + 0.3F * Terrain.field(seed + 8, x, z, 3.0);
                    float dp = ds / (sr * wob);
                    boolean knoll = u >= mouth - 12.0F && u <= mouth + 0.5F && Math.abs(v) < 6.5F;
                    if (dp < 1.0F && !flooded) {
                        water = rise;
                        bed = rise - (dp < 0.5F ? 2 : 1);
                    } else if (knoll) {
                        float hgt = 7.0F * Terrain.smooth((u - (mouth - 12.0F)) / 6.0F) * Terrain.smooth((6.5F - Math.abs(v)) / 3.0F);
                        top = Math.max(Math.max(ground, surface), Math.max(rise + 1, rise + Math.round(hgt + rough)));
                        kind = u > mouth - 1.5F ? 3 : 4;
                    } else if (ds < sr * wob + 6.0F) {
                        int t = Math.max(rise + 1, Math.round(rise + 1 + Math.max(0.0F, ds - sr * wob - 0.8F) * 0.8F + rough));
                        if (flooded) top = Math.max(t, surface);
                        else if (ground > t) top = t;
                        else if (ds < sr * wob + 2.5F && ground < rise + 1) top = rise + 1;
                        kind = 1;
                    }
                }
                if (water > Integer.MIN_VALUE) {
                    Terrain.clearAbove(level, pos, x, Math.max(ground, water), z, Math.min(16, b.maxY() - Math.max(ground, water)));
                    for (int h = water + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    for (int h = Math.min(ground + 1, bed - 1); h <= bed; h++) {
                        float n = Terrain.noise(seed, x, h, z);
                        Terrain.set(level, pos, x, h, z, h < bed ? Blocks.STONE.defaultBlockState() : n < 0.45F ? Blocks.GRAVEL.defaultBlockState()
                                : n < 0.75F ? Blocks.STONE.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                    }
                    for (int h = bed + 1; h <= water; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                } else if (top > Integer.MIN_VALUE && (top != ground || kind == 2)) {
                    Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, Math.min(16, b.maxY() - Math.max(ground, top)));
                    for (int h = top + 1; h <= Math.max(ground, surface); h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    for (int h = Math.min(ground + 1, top); h <= top; h++) Terrain.set(level, pos, x, h, z, material(kind, x, h, z, top, limestone));
                    if (kind == 1 && top > ground) Terrain.set(level, pos, x, ground, z, Blocks.DIRT.defaultBlockState());
                }
                // the swallet's passage, sloping down and in under the scarp, flooded below the pool's level
                if (u >= face - 1.0F && u <= face + TUNNEL + 2.0F && off < 4.5F) {
                    float c = sinkHeight(u), r = sinkRadius(u);
                    for (int h = Math.max(b.minY(), Mth.floor(c - 5.0F)); h <= Math.min(b.maxY(), Mth.ceil(c + 5.0F)); h++) {
                        if (tube(vm, h, c, r)) Terrain.set(level, pos, x, h, z, h <= sink ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                        else if (u >= face && shell(vm, h, c, 1.7F)) Terrain.set(level, pos, x, h, z, limestone);
                    }
                }
                // and the spring's, coming up out of the knoll
                if (u >= mouth - 10.0F && u <= mouth + 1.5F && Math.abs(v) < 4.0F) {
                    float c = riseHeight(u), r = riseRadius(u);
                    for (int h = Math.max(b.minY(), Mth.floor(c - 5.0F)); h <= Math.min(b.maxY(), Mth.ceil(c + 5.0F)); h++) {
                        if (tube(v, h, c, r)) Terrain.set(level, pos, x, h, z, h <= rise ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                        else if (u <= mouth + 0.5F && shell(v, h, c, 1.6F)) Terrain.set(level, pos, x, h, z, limestone);
                    }
                }
                if ((kind == 1 || kind == 4) && top > Integer.MIN_VALUE) plant(level, b, pos, x, z, top);
            }
        }
    }

    private BlockState material(int kind, int x, int h, int z, int top, BlockState limestone) {
        float n = Terrain.noise(seed, x, h, z);
        return switch (kind) {
            // banks: turf, with moss and stones by the water
            case 1 -> h < top ? Blocks.DIRT.defaultBlockState() : n < 0.6F ? Blocks.GRASS_BLOCK.defaultBlockState() : n < 0.8F ? Blocks.MOSS_BLOCK.defaultBlockState()
                    : n < 0.92F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
            // the lips the pools spill over: stones and gravel, green with moss
            case 2 -> h < top ? Blocks.STONE.defaultBlockState() : n < 0.45F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : n < 0.75F ? Blocks.COBBLESTONE.defaultBlockState()
                    : Blocks.GRAVEL.defaultBlockState();
            // bare limestone faces
            case 3 -> n < 0.15F ? Blocks.MOSS_BLOCK.defaultBlockState() : n < 0.25F ? Blocks.STONE.defaultBlockState() : limestone;
            // limestone under turf
            default -> h == top ? Blocks.GRASS_BLOCK.defaultBlockState() : h == top - 1 && n < 0.5F ? Blocks.DIRT.defaultBlockState() : limestone;
        };
    }

    private void plant(WorldGenLevel level, BoundingBox b, BlockPos.MutableBlockPos pos, int x, int z, int top) {
        if (top + 1 > b.maxY()) return;
        pos.set(x, top, z);
        if (!level.getBlockState(pos).is(BlockTags.DIRT)) return;
        pos.set(x, top + 1, z);
        if (!level.getBlockState(pos).isAir()) return;
        float n = Terrain.noise(seed, x, 9, z);
        if (n > 0.8F) Terrain.set(level, pos, x, top + 1, z, Blocks.FERN.defaultBlockState());
        else if (n > 0.45F) Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
    }
}
