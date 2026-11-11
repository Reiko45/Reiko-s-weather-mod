package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.function.BiPredicate;

/**
 * A hanging garden: a curtain of green clinging to a bare desert cliff where groundwater seeps out of the rock, as at
 * Weeping Rock in Zion. Rain and snowmelt soak down through the thick, porous sandstone of the cliff top for years, until
 * they reach a bed of tighter rock they can't get through; then they creep sideways along the top of it and out of the
 * cliff face as a seep. The wet rock there crumbles faster than the dry rock round it, and freezes and thaws all winter, so
 * the seep has slowly eaten an arched alcove back into the cliff under an overhang. In its shade and its steady damp grows
 * a little world of its own: moss and ferns along the seep line, vines trailing down the wet rock, roots and spore
 * blossoms under the roof, and water dripping from the overhang into a small pool at the foot.
 *
 * @param cx     the middle of the alcove's mouth, on the cliff face
 * @param angle  the way the cliff faces (out of the rock, downhill)
 * @param floor  the alcove's floor and the foot of the cliff
 * @param top    the top of the cliff
 * @param half   half the width of the alcove
 * @param depth  how far it reaches back into the rock
 * @param height how high its arch is
 * @param seep   the height of the seep line: the top of the tight bed under the sandstone
 */
public record HangingGarden(int cx, int cz, float angle, int floor, int top, int half, int depth, int height, int seep, long seed) implements Landform {
    /** How far back from the face the cliff is shaped, how far out in front the ground is levelled, and how far the wall runs past the alcove. */
    private static final int BEHIND = 16, FRONT = 14, SIDE = 14;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 2 || !c.fits(ground, 30, 40)) {
            return null;
        }
        // the ground 7 and 14 blocks out along eight ways from here
        int[][] ring = new int[8][3];
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F;
            ring[i][0] = ground;
            for (int k = 1; k <= 2; k++) ring[i][k] = c.groundAt(c.x() + Math.round(Mth.cos(a) * 7 * k), c.z() + Math.round(Mth.sin(a) * 7 * k));
        }
        // the steepest fall over 14 blocks along any line through here
        int bestDrop = 0, way = -1, start = 0;
        for (int i = 0; i < 8; i++) {
            int[] p = {ring[(i + 4) % 8][2], ring[(i + 4) % 8][1], ground, ring[i][1], ring[i][2]};
            for (int s = 0; s + 2 < p.length; s++) {
                if (p[s] - p[s + 2] > bestDrop) {
                    bestDrop = p[s] - p[s + 2];
                    way = i;
                    start = s;
                }
            }
        }
        if (bestDrop < 13) {
            return null;
        }
        // a closer look along that stretch, every 2 blocks, for where the face is and how high it stands
        float a = way * Mth.TWO_PI / 8.0F, ca = Mth.cos(a), sa = Mth.sin(a);
        int s0 = (start - 2) * 7;
        int[] q = new int[8];
        for (int k = 0; k < 8; k++) q[k] = c.groundAt(c.x() + Math.round(ca * (s0 + 2 * k)), c.z() + Math.round(sa * (s0 + 2 * k)));
        int k0 = 0;
        for (int k = 1; k < 7; k++) {
            if (q[k] - q[k + 1] > q[k0] - q[k0 + 1]) k0 = k;
        }
        int top = Integer.MIN_VALUE, foot = Integer.MAX_VALUE;
        for (int k = 0; k <= k0; k++) top = Math.max(top, q[k]);
        for (int k = k0 + 1; k < 8; k++) foot = Math.min(foot, q[k]);
        if (top - foot < 15 || foot < c.seaLevel() + 2 || !c.fits(foot, 12, top - foot + 24)) {
            return null;
        }
        float sf = s0 + 2 * k0 + 1.0F;
        int fx = c.x() + Math.round(ca * sf), fz = c.z() + Math.round(sa * sf);
        int half = 4 + c.random().nextInt(7);
        // the cliff has to run on past the alcove either side, not be the end of a narrow spur or the head of a gully
        for (int side = -1; side <= 1; side += 2) {
            double[] hi = Terrain.xz(-5.0, side * (half + 3.0), fx + 0.5, fz + 0.5, a), lo = Terrain.xz(5.0, side * (half + 3.0), fx + 0.5, fz + 0.5, a);
            if (c.groundAt(Mth.floor(hi[0]), Mth.floor(hi[1])) - c.groundAt(Mth.floor(lo[0]), Mth.floor(lo[1])) < 8) return null;
        }
        int height = Mth.clamp(Math.round(half * 1.4F) + c.random().nextInt(4), 7, top - foot - 4);
        int depth = 3 + c.random().nextInt(4);
        int seep = foot + 2 + c.random().nextInt(Math.max(1, height / 4));
        return new HangingGarden(fx, fz, a, foot, top, half, depth, height, seep, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new HangingGarden(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("floor"), tag.getInt("top"), tag.getInt("half"),
                tag.getInt("depth"), tag.getInt("height"), tag.getInt("seep"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("floor", floor);
        tag.putInt("top", top);
        tag.putInt("half", half);
        tag.putInt("depth", depth);
        tag.putInt("height", height);
        tag.putInt("seep", seep);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.HANGING_GARDEN;
    }

    @Override
    public BoundingBox bounds() {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (float u : new float[]{-(BEHIND + 2.0F), FRONT + 2.0F}) {
            for (float v : new float[]{-(half + SIDE), half + SIDE}) {
                double[] p = Terrain.xz(u, v, cx + 0.5, cz + 0.5, angle);
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minZ = Math.min(minZ, p[1]);
                maxZ = Math.max(maxZ, p[1]);
            }
        }
        return new BoundingBox(Mth.floor(minX) - 1, floor - 14, Mth.floor(minZ) - 1, Mth.floor(maxX) + 1, top + 20, Mth.floor(maxZ) + 1);
    }

    private float[] uv(int x, int z) {
        return Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
    }

    /** Where the cliff face stands, out from the alcove's mouth: straight across the mouth, wavering a little beyond it. */
    private float face(float v) {
        float out = Terrain.smooth((Math.abs(v) - half - 1.0F) / 3.0F);
        return out * (1.2F * Mth.sin(v * 0.35F + Terrain.hash(seed, 1) * Mth.TWO_PI) + 0.6F * Mth.sin(v * 0.9F + Terrain.hash(seed, 2) * Mth.TWO_PI));
    }

    /** 1 along the cliff for a way either side of the alcove, fading to 0 where it merges back into the hillside. */
    private float along(float v) {
        return Terrain.smooth((half + SIDE - Math.abs(v)) / 6.0F);
    }

    /** Whether a column is one the landform reshapes at all. */
    private boolean inside(int x, int z) {
        float[] l = uv(x, z);
        float r = face(l[1]) - l[0];
        return along(l[1]) > 0.0F && r <= BEHIND && -r <= FRONT;
    }

    /**
     * Whether a column is left as it is because its ground lies far below the cliff's foot (the bottom of a ravine, say),
     * so a pond in it is never let out and must be walled off like one outside.
     */
    private boolean left(WorldGenLevel level, int x, int z, BoundingBox b) {
        float[] l = uv(x, z);
        float r = face(l[1]) - l[0];
        return !(r > 0.0F ? skin(r) : flat(l[0], l[1])) && Goosenecks.ground(level, new BlockPos.MutableBlockPos(), x, z) < b.minY() + 2;
    }

    /**
     * The alcove's air in a column, {lowest, highest}, or null: a half dome hollowed back into the face, with a bench
     * below the seep line where the tighter rock hasn't been eaten back so far.
     */
    @Nullable
    private int[] alcove(int x, int z, float u, float v) {
        float f = face(v);
        if (u >= f || Math.abs(v) >= half) return null;
        float r = Math.max(0.0F, f - u + 0.9F * (Terrain.field(seed + 3, x, z, 3.0) - 0.5F));
        float q = 1.0F - Mth.square(r / depth) - Mth.square(v / half);
        if (q <= 0.0F) return null;
        int ceil = floor + Mth.ceil(height * Mth.sqrt(q)) - 1;
        float qb = 1.0F - Mth.square(r / (depth * 0.45F)) - Mth.square(v / half);
        int low = qb > 0.0F ? floor + 1 : seep;
        return low > ceil ? null : new int[]{low, ceil};
    }

    /** Whether a column is part of the rock rebuilt round the face and the alcove (rather than just filled up behind). */
    private boolean skin(float r) {
        return r > 0.0F && r < depth + 3.0F;
    }

    /** Whether a column is part of the level ground out in front of the cliff, which is always cut or built to the floor. */
    private boolean flat(float u, float v) {
        float r = face(v) - u;
        return r <= 0.0F && along(v) >= 1.0F && -r <= FRONT - 6;
    }

    /** Whether the cliff's rock certainly stands at a spot once it's built (so a plant can cling to it). */
    private boolean rockAt(int x, int y, int z) {
        float[] l = uv(x, z);
        float u = l[0], v = l[1];
        if (along(v) < 1.0F || !skin(face(v) - u) || y < floor - 3 || y > top - 1) return false;
        int[] air = alcove(x, z, u, v);
        return air == null || y < air[0] || y > air[1];
    }

    /** Whether a spot is certainly open air once it's built: in the alcove, or out in front of the face. */
    private boolean airAt(int x, int y, int z) {
        float[] l = uv(x, z);
        float u = l[0], v = l[1];
        if (flat(u, v)) return y > floor + 2 && y < top + 4;
        int[] air = alcove(x, z, u, v);
        return air != null && y >= air[0] && y <= air[1];
    }

    /** The pool at the foot: how far into it a column is (under 1 inside). */
    private float pool(int x, int z, float u, float v) {
        float pu = 0.5F + 1.5F * Terrain.hash(seed, 5), pv = (Terrain.hash(seed, 6) - 0.5F) * half * 0.5F;
        float ru = 2.0F + 1.5F * Terrain.hash(seed, 7), rv = Math.min(half * 0.75F, 2.5F + 2.0F * Terrain.hash(seed, 8));
        return Mth.sqrt(Mth.square((u - pu) / ru) + Mth.square((v - pv) / rv)) / (0.88F + 0.24F * Terrain.field(seed + 9, x, z, 3.0));
    }

    /** Whether the pool may fill a column: on the level floor, in front of the face or in the alcove's mouth. */
    private boolean poolable(int x, int z) {
        float[] l = uv(x, z);
        float u = l[0], v = l[1];
        if (pool(x, z, u, v) >= 1.0F) return false;
        if (flat(u, v)) return true;
        int[] air = alcove(x, z, u, v);
        return air != null && air[0] == floor + 1;
    }

    /**
     * Whether the pool's water stands in a column: only where every column beside it is pool too, or is certain to be solid
     * at the water's level (the level ground in front, or the rock rebuilt round the alcove), so it can't run out.
     */
    private boolean held(int x, int z) {
        if (!poolable(x, z)) return false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int nx = x + d.getStepX(), nz = z + d.getStepZ();
            if (poolable(nx, nz)) continue;
            float[] l = uv(nx, nz);
            if (!flat(l[0], l[1]) && !(along(l[1]) >= 1.0F && skin(face(l[1]) - l[0]))) return false;
        }
        return true;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BiPredicate<Integer, Integer> outside = (nx, nz) -> !inside(nx, nz) || left(level, nx, nz, b);
        int water = floor - 1;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = uv(x, z);
                float u = l[0], v = l[1], w = along(v);
                if (w <= 0.0F) continue;
                float r = face(v) - u;
                if (r > BEHIND || -r > FRONT) continue;
                int ground = Goosenecks.ground(level, pos, x, z);
                int standing = Math.min(b.maxY(), Goosenecks.standing(level, pos, x, ground, z));
                pos.set(x, ground, z);
                BlockState was = level.getBlockState(pos);
                boolean red = level.getBiome(pos).is(BiomeTags.IS_BADLANDS);
                float n = Terrain.noise(seed, x, floor, z);
                int clear = Math.max(0, Math.min(30, b.maxY() - Math.max(ground, standing)));
                if (r > 0.0F) {
                    // the cliff: built up to its full height behind the face, and rebuilt in its beds of rock round the alcove
                    int tgt = ground + Math.round(w * Terrain.smooth((BEHIND - r) / 5.0F) * Math.max(0, top - ground));
                    int[] air = alcove(x, z, u, v);
                    boolean pool = air != null && held(x, z), rebuild = skin(r);
                    if (tgt == ground && !rebuild && standing == ground || !rebuild && ground < b.minY() + 2) continue;
                    Terrain.clearAbove(level, pos, x, Math.max(ground, standing), z, clear);
                    int from = Math.max(b.minY(), rebuild ? Math.min(ground + 1, floor - 3) : ground + 1), to = Math.min(Math.min(tgt, top), b.maxY());
                    BlockState turf = turf(was, red, x, tgt, z);
                    for (int h = from; h <= to; h++) {
                        BlockState s;
                        if (air != null && h >= air[0] && h <= air[1]) {
                            s = Blocks.AIR.defaultBlockState();
                        } else if (h == tgt && tgt <= top) {
                            s = turf;
                        } else if (h == tgt - 1 && tgt <= top && turf.is(Blocks.GRASS_BLOCK)) {
                            s = Blocks.DIRT.defaultBlockState();
                        } else if (air != null && h == air[0] - 1) {
                            // the alcove's floor, and the bench along the seep line: damp, mossy ground
                            s = h == floor ? floorBlock(n) : Blocks.MOSS_BLOCK.defaultBlockState();
                        } else {
                            s = rebuild ? wall(x, h, z, red) : rock(x, h, z, red);
                        }
                        if (h == from && (s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL)) && !level.getBlockState(pos.set(x, h - 1, z)).blocksMotion()) {
                            s = rock(x, h, z, red); // (nothing loose with nothing under it)
                        }
                        Terrain.set(level, pos, x, h, z, s);
                    }
                    // anything left standing above the new top (a pond) is let out, unless that would empty a pond beside
                    drain(level, pos, x, z, Math.max(to, ground), standing, outside, red);
                    if (pool) poolColumn(level, pos, x, z, ground, water, n);
                    if (air != null) decorate(level, pos, x, z, u, v, air, pool);
                    continue;
                }
                // out in front: the ground levelled off at the foot of the cliff, fading back into the hillside
                float df = -r;
                int tgt = Math.round(Mth.lerp(w * Terrain.smooth((FRONT - df) / 6.0F), ground, floor));
                boolean even = flat(u, v), pool = held(x, z);
                if (tgt == ground && standing == ground && !even && !pool || !even && ground < b.minY() + 2) continue;
                Terrain.clearAbove(level, pos, x, Math.max(ground, standing), z, clear);
                if (even) {
                    // sound ground under the floor, with no hollow in it for the pool to find
                    for (int h = Math.max(b.minY(), floor - 4); h <= Math.min(ground, tgt); h++) {
                        BlockState here = level.getBlockState(pos.set(x, h, z));
                        if (here.isAir() || !here.getFluidState().isEmpty()) Terrain.set(level, pos, x, h, z, rock(x, h, z, red));
                    }
                }
                for (int h = Math.max(b.minY(), ground + 1); h < tgt; h++) {
                    Terrain.set(level, pos, x, h, z, h < tgt - 2 ? rock(x, h, z, red) : Blocks.DIRT.defaultBlockState());
                }
                drain(level, pos, x, z, tgt, Math.max(standing, ground), outside, red);
                boolean wet = df < 4.0F && Math.abs(v) < half + 2.0F || pool(x, z, u, v) < 1.7F;
                BlockState surface = wet ? floorBlock(n) : turf(was, red, x, tgt, z);
                Terrain.set(level, pos, x, tgt, z, surface);
                if ((surface.is(BlockTags.SAND) || surface.is(Blocks.GRAVEL)) && !level.getBlockState(pos.set(x, tgt - 1, z)).blocksMotion()) {
                    Terrain.set(level, pos, x, tgt - 1, z, rock(x, tgt - 1, z, red));
                }
                if (pool) {
                    poolColumn(level, pos, x, z, ground, water, n);
                    continue;
                }
                if (even) {
                    plants(level, pos, x, tgt, z, surface, u, v);
                    if (df < 1.0F) faceVines(level, pos, x, z);
                }
            }
        }
    }

    /** Lets out water standing in a column above its new top, but keeps a wall where a pond just outside would drain into it. */
    private void drain(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int top, int standing, BiPredicate<Integer, Integer> outside,
                       boolean red) {
        for (int h = Math.max(top + 1, floor - 14); h <= standing; h++) {
            Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, outside) ? rock(x, h, z, red) : Blocks.AIR.defaultBlockState());
        }
    }

    /** The little pool at the foot of the seep: clear, shallow water over gravel and clay, a step down from the floor. */
    private void poolColumn(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, int water, float n) {
        float[] l = uv(x, z);
        int bed = water - (pool(x, z, l[0], l[1]) < 0.5F ? 3 : 2);
        for (int h = Math.max(floor - 14, Math.min(ground + 1, bed - 1)); h < bed; h++) Terrain.set(level, pos, x, h, z, Blocks.SANDSTONE.defaultBlockState());
        Terrain.set(level, pos, x, bed, z, n < 0.5F ? Blocks.GRAVEL.defaultBlockState() : n < 0.8F ? Blocks.CLAY.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState());
        for (int h = bed + 1; h <= water; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
        for (int h = water + 1; h <= floor; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
    }

    /** What the new ground on top is: much what it was before (turf, sand or snow), or bare rock. */
    private BlockState turf(BlockState was, boolean red, int x, int y, int z) {
        if (was.is(BlockTags.DIRT) && !was.is(Blocks.MOSS_BLOCK) && !was.is(Blocks.MUD)) return Blocks.GRASS_BLOCK.defaultBlockState();
        if (was.is(BlockTags.SAND) || was.is(Blocks.SNOW_BLOCK) || was.is(Blocks.GRAVEL) || was.is(BlockTags.TERRACOTTA) || was.is(Blocks.MOSS_BLOCK)) return was;
        return rock(x, y, z, red);
    }

    /** The damp floor of the alcove and the ground round the pool. */
    private static BlockState floorBlock(float n) {
        return n < 0.5F ? Blocks.MOSS_BLOCK.defaultBlockState() : n < 0.65F ? Blocks.COARSE_DIRT.defaultBlockState()
                : n < 0.78F ? Blocks.ROOTED_DIRT.defaultBlockState() : n < 0.9F ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.CLAY.defaultBlockState();
    }

    /** The cliff's rock: thick beds of sandstone above the seep line, and thin beds of tight mudstone below it. */
    private BlockState rock(int x, int y, int z, boolean red) {
        float n = Terrain.noise(seed + 4, x, y, z);
        if (y < seep) {
            float h = Terrain.hash(seed, 300 + Math.floorDiv(y - seep, 2));
            if (n < 0.08F) h = n * 12.0F;
            if (red) {
                return h < 0.35F ? Blocks.RED_TERRACOTTA.defaultBlockState() : h < 0.6F ? Blocks.BROWN_TERRACOTTA.defaultBlockState()
                        : h < 0.85F ? Blocks.TERRACOTTA.defaultBlockState() : Blocks.ORANGE_TERRACOTTA.defaultBlockState();
            }
            return h < 0.35F ? Blocks.TERRACOTTA.defaultBlockState() : h < 0.6F ? Blocks.ORANGE_TERRACOTTA.defaultBlockState()
                    : h < 0.8F ? Blocks.BROWN_TERRACOTTA.defaultBlockState() : Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState();
        }
        float h = Terrain.hash(seed, 200 + Math.floorDiv(y - seep + Math.round(Terrain.hash(seed, 3) * 3.0F), 3));
        if (n < 0.1F) h = Math.min(0.89F, n * 9.0F); // (cross-bedding: the odd block of another bed's stone)
        if (red) {
            return h < 0.5F ? Blocks.RED_SANDSTONE.defaultBlockState() : h < 0.8F ? Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState()
                    : h < 0.92F ? Blocks.CUT_RED_SANDSTONE.defaultBlockState() : Blocks.ORANGE_TERRACOTTA.defaultBlockState();
        }
        return h < 0.5F ? Blocks.SANDSTONE.defaultBlockState() : h < 0.8F ? Blocks.SMOOTH_SANDSTONE.defaultBlockState()
                : h < 0.92F ? Blocks.CUT_SANDSTONE.defaultBlockState() : Blocks.TERRACOTTA.defaultBlockState();
    }

    /** The rock of the face and the alcove's walls: wet and mossy along the seep line, and streaked below it where the water runs down. */
    private BlockState wall(int x, int y, int z, boolean red) {
        if (y < floor - 1 || y > seep + 1 || !exposed(x, y, z)) return rock(x, y, z, red);
        float n = Terrain.noise(seed + 5, x, y, z);
        float streak = Terrain.field(seed + 6, x * 1.7 + z * 1.7, 0.0, 2.5);
        if (y < seep - 1 && streak < 0.55F) return rock(x, y, z, red);
        if (n < 0.42F) return Blocks.MOSS_BLOCK.defaultBlockState();
        if (n < 0.62F) return Blocks.BROWN_TERRACOTTA.defaultBlockState();
        if (n < 0.78F) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        return rock(x, y, z, red);
    }

    /** Whether a spot in the rock is open to the air on some side (part of the face or the alcove's walls). */
    private boolean exposed(int x, int y, int z) {
        if (airAt(x, y + 1, z) || airAt(x, y - 1, z)) return true;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (airAt(x + d.getStepX(), y, z + d.getStepZ())) return true;
            float[] l = uv(x + d.getStepX(), z + d.getStepZ());
            if (y > floor && face(l[1]) - l[0] <= 0.0F) return true; // (out in front of the face)
        }
        return false;
    }

    /** The ground out in front: a few ferns and grasses, and big dripleaf where it's wet beside the pool. */
    private void plants(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState surface, float u, float v) {
        if (!surface.is(BlockTags.DIRT)) return;
        float m = Terrain.noise(seed + 7, x, y, z);
        if (pool(x, z, u, v) < 1.6F && m > 0.78F) {
            dripleaf(level, pos, x, y + 1, z, m);
        } else if (m < 0.18F) {
            Terrain.set(level, pos, x, y + 1, z, m < 0.08F ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
        } else if (surface.is(Blocks.MOSS_BLOCK) && m < 0.35F) {
            Terrain.set(level, pos, x, y + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
        }
    }

    /** A big dripleaf, on a stem now and then (it returns how many blocks tall it is). */
    private static int dripleaf(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, float m) {
        Direction facing = Direction.from2DDataValue(Math.round(m * 400.0F) & 3);
        BlockState leaf = Blocks.BIG_DRIPLEAF.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
        if (m > 0.9F) {
            Terrain.set(level, pos, x, y, z, Blocks.BIG_DRIPLEAF_STEM.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing));
            Terrain.set(level, pos, x, y + 1, z, leaf);
            return 2;
        }
        Terrain.set(level, pos, x, y, z, leaf);
        return 1;
    }

    /** Vines trailing down the face of the cliff from its top, as far as there's rock to hold them. */
    private void faceVines(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
        if (Terrain.noise(seed + 8, x, 0, z) > 0.3F) return;
        int start = top - 1 - Math.round(Terrain.noise(seed + 8, x, 1, z) * 3.0F);
        int end = start - 4 - Math.round(Terrain.noise(seed + 8, x, 2, z) * 12.0F);
        for (int y = start; y > Math.max(end, floor + 2); y--) {
            Direction on = clinging(x, y, z);
            if (on == null) break;
            Terrain.set(level, pos, x, y, z, Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(on), true));
        }
    }

    /** Which way there's sound rock beside a spot for a plant to cling to, if any. */
    @Nullable
    private Direction clinging(int x, int y, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (rockAt(x + d.getStepX(), y, z + d.getStepZ())) return d;
        }
        return null;
    }

    /**
     * The garden in the alcove: ferns, moss and dripleaf on its floor and its bench, vines hanging from the seep line,
     * glow lichen on the shaded walls, roots and spore blossoms under the roof, and here and there a pocket of water held
     * in the rock over the roof, which drips through it.
     */
    private void decorate(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, float u, float v, int[] air, boolean pool) {
        int low = air[0], ceil = air[1], start = pool ? floor + 1 : low + 1;
        float m = Terrain.noise(seed + 7, x, low, z);
        BlockState under = level.getBlockState(pos.set(x, low - 1, z));
        if (!pool && ceil > low) {
            // on the floor and the bench
            if (low == floor + 1 && m > 0.82F && pool(x, z, u, v) < 1.8F && ceil > low + 1 && under.is(BlockTags.BIG_DRIPLEAF_PLACEABLE)) {
                start = low + dripleaf(level, pos, x, low, z, m);
            } else if (m < 0.3F && under.is(BlockTags.DIRT)) {
                Terrain.set(level, pos, x, low, z, Blocks.FERN.defaultBlockState());
            } else if (m < 0.55F) {
                Terrain.set(level, pos, x, low, z, Blocks.MOSS_CARPET.defaultBlockState());
            } else if (m < 0.65F && under.is(BlockTags.DIRT)) {
                Terrain.set(level, pos, x, low, z, Blocks.SHORT_GRASS.defaultBlockState());
            }
        }
        for (int y = ceil; y >= start; y--) {
            float k = Terrain.noise(seed + 10, x, y, z);
            if (y == ceil && ceil >= floor + 3) {
                // under the roof
                if (k < 0.16F) {
                    Terrain.set(level, pos, x, y, z, Blocks.HANGING_ROOTS.defaultBlockState());
                    continue;
                }
                if (k > 0.9F) {
                    Terrain.set(level, pos, x, y, z, Blocks.SPORE_BLOSSOM.defaultBlockState());
                    continue;
                }
                if (k < 0.3F) {
                    Terrain.set(level, pos, x, y, z, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.UP), true));
                    continue;
                }
            }
            Direction on = clinging(x, y, z);
            if (on == null) continue;
            if (y <= seep + 2 && k < 0.55F) {
                // vines hanging from the seep line, down over the bench and the wet rock below
                Terrain.set(level, pos, x, y, z, Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(on), true));
            } else if (k < 0.2F) {
                Terrain.set(level, pos, x, y, z, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(on), true));
            }
        }
        // a seep over the roof: water held in the rock just above it, which drips down through it
        if (ceil >= floor + 3 && ceil + 3 <= top - 1 && Terrain.noise(seed + 11, x, 0, z) < 0.12F) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (!rockAt(x + d.getStepX(), ceil + 2, z + d.getStepZ())) return;
            }
            Terrain.set(level, pos, x, ceil + 2, z, Blocks.WATER.defaultBlockState());
        }
    }
}
