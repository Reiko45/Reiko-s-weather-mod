package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.util.function.BiPredicate;

/**
 * A sea cliff waterfall, like McWay Falls on the Big Sur coast or the falls that pour off the cliffs of the Faroes: a
 * stream that runs across high ground to the very edge of a sea cliff and drops straight off it, onto the beach or into
 * the sea. The waves cut the cliff back faster than the little stream can cut its bed down, so the stream's valley ends
 * in mid-air, and the water falls the whole height of the cliff in a single leap. At the foot it has scoured out a pool
 * in the beach (or lands straight in the surf), and the rock round about is kept dark and wet with spray and furred with
 * moss.
 *
 * @param cx     the lip of the falls, where the stream goes over the edge
 * @param angle  the way the cliff faces (out to sea)
 * @param sea    the sea level
 * @param level  the stream's water level on the clifftop
 * @param length how far the stream runs back from the lip to the pool it rises from
 * @param half   half the length of the stretch of cliff
 * @param top    the highest ground about (for the bounds)
 */
public record SeaCliffFalls(int cx, int cz, float angle, int sea, int level, int length, int half, int top, long seed) implements Landform {
    /** What each column is: untouched, the cut-back shore, the face of the cliff, a bank of the stream, the shore round the plunge pool, the stream, the plunge pool. */
    private static final int NONE = 0, CUT = 1, FACE = 2, BANK = 3, RIM = 4, STREAM = 5, PLUNGE = 6;
    /** How far out from the face the shore is cut back to a beach, how far it takes to fade back into the old shore, and how wide the stream's banks are. */
    private static final float BEACH = 9.0F, FADE = 6.0F, BANKS = 2.5F;

    @Nullable
    static Landform plan(PlanContext c) {
        return c.search(12, 12, SeaCliffFalls::planAt);
    }

    @Nullable
    private static Landform planAt(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        int sea = c.seaLevel();
        if (ground < sea + 9 || ground > sea + 32 || !c.fits(ground, 20, 30)) {
            return null;
        }
        // (the sea a few steps off one way or another, before looking all round for it)
        int near = Integer.MAX_VALUE;
        for (int i = 0; i < 4; i++) near = Math.min(near, c.groundAt(c.x() + (i == 0 ? 18 : i == 2 ? -18 : 0), c.z() + (i == 1 ? 18 : i == 3 ? -18 : 0)));
        if (near > sea + 3) {
            return null;
        }
        float a = c.seaward(18, 8);
        if (Float.isNaN(a)) {
            return null;
        }
        // out towards the sea: the last of the high ground, and then the shore below it, which has to come soon after
        int lip = -1, shore = -1, prev = ground;
        for (int k = 1; k <= 12; k++) {
            int h = at(c, c.x(), c.z(), a, 2.0F * k, 0.0F);
            if (lip < 0 && h < sea + 9) lip = k - 1;
            if (lip >= 0 && h <= sea - 1) {
                shore = k;
                break;
            }
            if (lip < 0) prev = h;
        }
        if (lip < 0 || shore < 0 || (shore - lip) * 2 > BEACH + 2) {
            return null;
        }
        int level = prev - 1;
        if (level - (sea - 1) < 8 || level - (sea - 1) > 25) {
            return null;
        }
        double[] l = Terrain.xz(2.0F * lip, 0.0, c.x() + 0.5, c.z() + 0.5, a);
        int lx = Mth.floor(l[0]), lz = Mth.floor(l[1]);
        // the cliff lined up with the coast, from where its edge is a little way along either side
        float[] edge = new float[2];
        for (int s = 0; s < 2; s++) {
            edge[s] = Float.NaN;
            for (int k = -4; k <= 6 && Float.isNaN(edge[s]); k++) {
                if (at(c, lx, lz, a, 2.0F * k, s == 0 ? -8.0F : 8.0F) < (level + sea) / 2) edge[s] = 2.0F * k - 1.0F;
            }
        }
        if (!Float.isNaN(edge[0]) && !Float.isNaN(edge[1])) a += Mth.clamp((float) Math.atan2(edge[0] - edge[1], 16.0F), -0.6F, 0.6F);
        // back from the lip: the stream runs level until the ground rises well above it (where it rises from a spring), and never over a dip
        int length = 0, top = prev;
        int most = 8 + c.random().nextInt(9);
        for (int k = 1; 2 * k <= most; k++) {
            int h = at(c, lx, lz, a, -2.0F * k, 0.0F);
            if (h < level - 3 || h > level + 7) break;
            length = 2 * k;
            top = Math.max(top, h);
        }
        if (length < 6) {
            return null;
        }
        // the cliff runs on along the coast either side, not just the end of a narrow point
        for (int s = -1; s <= 1; s += 2) {
            int h = at(c, lx, lz, a, -3.0F, 12.0F * s);
            if (h < sea + 4) return null;
            top = Math.max(top, h);
        }
        return new SeaCliffFalls(lx, lz, a, sea, level, length, 16 + c.random().nextInt(10), top + 4, c.random().nextLong());
    }

    /** The ground {@code u} along a way and {@code v} across it from a spot. */
    private static int at(PlanContext c, int x, int z, float a, float u, float v) {
        double[] p = Terrain.xz(u, v, x + 0.5, z + 0.5, a);
        return c.groundAt(Mth.floor(p[0]), Mth.floor(p[1]));
    }

    static Landform load(CompoundTag tag) {
        return new SeaCliffFalls(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("sea"), tag.getInt("level"), tag.getInt("length"),
                tag.getInt("half"), tag.getInt("top"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("sea", sea);
        tag.putInt("level", level);
        tag.putInt("length", length);
        tag.putInt("half", half);
        tag.putInt("top", top);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.SEA_CLIFF_FALLS;
    }

    @Override
    public BoundingBox bounds() {
        float back = length + spring() * 1.12F + BANKS + 3.0F, out = BEACH + FADE + 2.0F, across = half + 2.0F;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (float u : new float[]{-back, out}) {
            for (float v : new float[]{-across, across}) {
                double[] p = Terrain.xz(u, v, cx + 0.5, cz + 0.5, angle);
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minZ = Math.min(minZ, p[1]);
                maxZ = Math.max(maxZ, p[1]);
            }
        }
        return new BoundingBox(Mth.floor(minX) - 1, sea - 24, Mth.floor(minZ) - 1, Mth.floor(maxX) + 1, Math.max(top, level + 4) + 14, Mth.floor(maxZ) + 1);
    }

    /** The water level of the plunge pool (the sea's own). */
    private int pool() {
        return sea - 1;
    }

    /** Half the width of the stream. */
    private float channel() {
        return 1.0F + 0.4F * Terrain.hash(seed, 1);
    }

    /** The radius of the pool the stream rises from. */
    private float spring() {
        return 2.0F + 1.2F * Terrain.hash(seed, 2);
    }

    /** The radius of the plunge pool. */
    private float plunge() {
        return 3.5F + 1.5F * Terrain.hash(seed, 3);
    }

    /** Where the face of the cliff stands, out from the lip: straight across where the falls come over, wavering along the coast beyond. */
    private float face(float v) {
        float w = Terrain.smooth((Math.abs(v) - 4.0F) / 5.0F);
        return w * (1.6F * Mth.sin(v * 0.13F + Terrain.hash(seed, 4) * Mth.TWO_PI) + 0.9F * Mth.sin(v * 0.31F + Terrain.hash(seed, 5) * Mth.TWO_PI));
    }

    /** 1 along the cliff, fading to 0 towards its ends, where it merges back into the old shore. */
    private float ends(float v) {
        return Terrain.smooth((half - Math.abs(v)) / 8.0F);
    }

    /** Where the stream's line wanders across the clifftop, {@code u} along it (it runs straight for the last few blocks to the lip). */
    private float meander(float u) {
        return Terrain.smooth((-u - 4.0F) / 4.0F) * (0.8F + 0.8F * Terrain.hash(seed, 6)) * Mth.sin(u * 0.3F + Terrain.hash(seed, 7) * Mth.TWO_PI);
    }

    /** How far into the plunge pool a spot is (under 1 inside it). */
    private float inPlunge(int x, int z, float u, float v) {
        float r = plunge();
        return Mth.sqrt(Mth.square(u - r * 0.5F) + v * v) / (r * (0.88F + 0.24F * Terrain.field(seed + 8, x, z, 2.5)));
    }

    /** How far into the spring pool a spot is (under 1 inside it). */
    private float inSpring(int x, int z, float u, float v) {
        return Mth.sqrt(Mth.square(u + length) + Mth.square(v - meander(-length))) / (spring() * (0.88F + 0.24F * Terrain.field(seed + 9, x, z, 2.0)));
    }

    /** What a column is (one of the kinds above). */
    private int classify(int x, int z, float u, float v) {
        float f = face(v);
        if (u >= f) {
            // out in front of the face: the plunge pool, the shore round it, and the beach the cliff has been cut back from
            float p = inPlunge(x, z, u, v);
            if (p < 1.0F) return PLUNGE;
            if (p < 1.0F + 1.6F / plunge()) return RIM;
            return u - f < BEACH + FADE && Math.abs(v) < half ? CUT : NONE;
        }
        // on top: the stream, its banks, and the face of the cliff
        float off = Math.abs(v - meander(u));
        float s = inSpring(x, z, u, v);
        if (s < 1.0F || u >= -length && off < channel()) return STREAM;
        if (s < 1.0F + BANKS / spring() || u >= -length && off < channel() + BANKS) return BANK;
        return f - u < 2.0F && Math.abs(v) < half ? FACE : NONE;
    }

    private int classify(int x, int z) {
        float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
        return classify(x, z, l[0], l[1]);
    }

    /**
     * Whether the stream's water can stand in a column: where every column beside it is the stream too, or a bank, or the
     * plunge pool far below, which the water pours over the lip into. Elsewhere the stream's bed is a lip of rock.
     */
    private boolean held(int x, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int k = classify(x + d.getStepX(), z + d.getStepZ());
            if (k != STREAM && k != BANK && k != PLUNGE) return false;
        }
        return true;
    }

    /** Whether a stream column pours over the lip (it has the plunge pool beside it). */
    private boolean pours(int x, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (classify(x + d.getStepX(), z + d.getStepZ()) == PLUNGE) return true;
        }
        return false;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // (the face keeps whatever stands on its clifftop, so a pond there is left alone like one outside, as is one far above or below)
        BiPredicate<Integer, Integer> outside = (nx, nz) -> {
            int k = classify(nx, nz);
            return k == NONE || k == FACE || skipped(Goosenecks.ground(level, new BlockPos.MutableBlockPos(), nx, nz), b);
        };
        int p = pool();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
                float u = l[0], v = l[1];
                int k = classify(x, z, u, v);
                if (k == NONE) continue;
                int ground = Goosenecks.ground(level, pos, x, z);
                int standing = Math.min(b.maxY(), Goosenecks.standing(level, pos, x, ground, z));
                if (skipped(ground, b)) continue;
                BlockState was = level.getBlockState(pos.set(x, ground, z));
                int clear = Math.max(0, Math.min(30, b.maxY() - Math.max(ground, standing)));
                float spray = Mth.sqrt(Mth.square(u - plunge() * 0.5F) + v * v);
                // sound rock round the plunge pool, with no hollow for its water to find
                if (spray < plunge() * 1.12F + 2.0F) {
                    for (int h = p - 6; h <= Math.min(ground, p); h++) {
                        BlockState here = level.getBlockState(pos.set(x, h, z));
                        if (here.isAir() || k != PLUNGE && k != RIM && !here.getFluidState().isEmpty()) Terrain.set(level, pos, x, h, z, rock(x, h, z, spray));
                    }
                }
                switch (k) {
                    case PLUNGE -> plunge(level, pos, b, x, z, u, v, ground, standing, clear, spray, outside);
                    case RIM, CUT -> shore(level, pos, b, x, z, u, v, k, ground, standing, was, clear, spray, outside);
                    case FACE -> face(level, pos, x, z, v, ground, was, spray);
                    case STREAM -> stream(level, pos, b, x, z, u, v, ground, standing, clear, outside);
                    default -> bank(level, pos, b, x, z, ground, standing, was, clear, outside);
                }
            }
        }
    }

    /** The plunge pool the falls land in: scoured out of the beach, or the surf itself where the cliff drops straight into the sea. */
    private void plunge(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, float u, float v, int ground, int standing, int clear,
                        float spray, BiPredicate<Integer, Integer> outside) {
        int p = pool();
        float d = inPlunge(x, z, u, v);
        int bed = Math.min(ground, p - 1 - Math.round(2.5F * Mth.sqrt(Math.max(0.0F, 1.0F - d * d))));
        clearAbove(level, pos, x, Math.max(ground, standing), z, clear, outside);
        for (int h = p + 1; h <= Math.max(ground, standing); h++) {
            if (level.getBlockState(pos.set(x, h, z)).isAir()) continue;
            Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, outside) ? rock(x, h, z, spray) : Blocks.AIR.defaultBlockState());
        }
        if (bed < ground) {
            Terrain.set(level, pos, x, bed, z, Terrain.noise(seed + 10, x, bed, z) < 0.5F ? Blocks.GRAVEL.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
            if (!level.getBlockState(pos.set(x, bed - 1, z)).blocksMotion()) Terrain.set(level, pos, x, bed - 1, z, rock(x, bed - 1, z, spray));
        }
        // (the water here is the landform's own, even where it was the sea's: it's what the falls land in)
        for (int h = bed + 1; h <= p; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
    }

    /** The shore in front of the cliff: cut back to a narrow beach of sand, shingle and boulders, wet and mossy round the foot of the falls. */
    private void shore(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, float u, float v, int k, int ground, int standing,
                       BlockState was, int clear, float spray, BiPredicate<Integer, Integer> outside) {
        if (ground < sea) return; // (the sea's own: left alone)
        float out = u - face(v);
        float e = k == RIM ? 1.0F : ends(v) * Terrain.smooth((BEACH + FADE - out) / FADE);
        int tgt = Math.min(ground, Math.round(Mth.lerp(e, ground, sea)));
        if (tgt == ground && standing == ground && k != RIM) return;
        clearAbove(level, pos, x, Math.max(ground, standing), z, clear, outside);
        for (int h = tgt + 1; h <= Math.max(ground, standing); h++) {
            Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, outside) ? rock(x, h, z, spray) : Blocks.AIR.defaultBlockState());
        }
        BlockState s;
        float n = Terrain.noise(seed + 11, x, tgt, z);
        if (spray < plunge() + 4.0F) {
            // kept wet by the spray
            s = n < 0.35F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : n < 0.55F ? Blocks.MOSS_BLOCK.defaultBlockState() : n < 0.75F ? Blocks.GRAVEL.defaultBlockState()
                    : rock(x, tgt, z, spray);
        } else if (tgt > sea + 1) {
            s = was.is(BlockTags.DIRT) ? Blocks.GRASS_BLOCK.defaultBlockState() : rock(x, tgt, z, spray);
        } else {
            float f = Terrain.field(seed + 12, x, z, 4.0);
            s = f < 0.3F ? Blocks.GRAVEL.defaultBlockState() : f > 0.75F ? n < 0.5F ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState()
                    : Blocks.SAND.defaultBlockState();
        }
        if (s.getBlock() instanceof FallingBlock && !level.getBlockState(pos.set(x, tgt - 1, z)).blocksMotion()) s = rock(x, tgt, z, spray);
        Terrain.set(level, pos, x, tgt, z, s);
        // a boulder fallen from the cliff, here and there
        if (tgt == sea && n > 0.95F && out > 1.5F) Terrain.set(level, pos, x, tgt + 1, z, spray < plunge() + 4.0F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : rock(x, tgt + 1, z, spray));
    }

    /** The face of the cliff: the turf left on top, and sheer rock below, dark and mossy where the spray reaches it. */
    private void face(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, float v, int ground, BlockState was, float spray) {
        if (ends(v) < 0.3F || ground < sea + 3) return;
        int soil = was.is(BlockTags.DIRT) ? 3 : 0;
        for (int h = sea - 2; h <= ground - soil; h++) Terrain.set(level, pos, x, h, z, wall(x, h, z, spray));
    }

    /** The stream on the clifftop: a level channel cut into the turf, running from its spring to the lip and over it. */
    private void stream(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, float u, float v, int ground, int standing, int clear,
                        BiPredicate<Integer, Integer> outside) {
        int w = this.level;
        boolean spring = inSpring(x, z, u, v) < 1.0F;
        boolean wet = held(x, z), pour = wet && pours(x, z);
        int bed = w - (spring ? 2 + (inSpring(x, z, u, v) < 0.5F ? 1 : 0) : 1);
        for (int h = Math.max(b.minY(), w - 5); h <= Math.min(ground, w); h++) {
            BlockState here = level.getBlockState(pos.set(x, h, z));
            if (here.isAir() || !here.getFluidState().isEmpty()) Terrain.set(level, pos, x, h, z, rock(x, h, z, 99.0F));
        }
        clearAbove(level, pos, x, Math.max(ground, standing), z, clear, outside);
        for (int h = w + 1; h <= Math.max(ground, standing); h++) {
            Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, outside) ? rock(x, h, z, 99.0F) : Blocks.AIR.defaultBlockState());
        }
        float n = Terrain.noise(seed + 13, x, w, z);
        for (int h = Math.min(ground + 1, bed - 1); h <= (wet ? bed : w); h++) {
            BlockState s = rock(x, h, z, 99.0F);
            if (h == bed && wet) s = n < 0.45F ? Blocks.GRAVEL.defaultBlockState() : n < 0.75F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : s;
            else if (h == w) s = n < 0.5F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : s; // (a lip of rock, where the water can't stand)
            Terrain.set(level, pos, x, h, z, s);
        }
        if (!wet) return;
        for (int h = bed + 1; h <= w; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
        if (pour) level.scheduleTick(pos.set(x, w, z).immutable(), Fluids.WATER, 1); // set it pouring over the edge
    }

    /** The stream's banks: turf, built up a block over the water where the ground dips, and mossy rock where the stream has cut down through it. */
    private void bank(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, int ground, int standing, BlockState was, int clear,
                      BiPredicate<Integer, Integer> outside) {
        int w = this.level, tgt = Math.max(ground, w + 1);
        for (int h = Math.max(b.minY(), w - 5); h <= Math.min(ground, w + 1); h++) {
            BlockState here = level.getBlockState(pos.set(x, h, z));
            if (here.isAir() || !here.getFluidState().isEmpty()) Terrain.set(level, pos, x, h, z, rock(x, h, z, 99.0F));
        }
        if (tgt > ground || standing > ground) clearAbove(level, pos, x, Math.max(ground, standing), z, clear, outside);
        for (int h = ground + 1; h < tgt; h++) Terrain.set(level, pos, x, h, z, h < tgt - 1 ? rock(x, h, z, 99.0F) : Blocks.DIRT.defaultBlockState());
        for (int h = tgt + 1; h <= standing; h++) {
            Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, outside) ? rock(x, h, z, 99.0F) : Blocks.AIR.defaultBlockState());
        }
        float n = Terrain.noise(seed + 14, x, tgt, z);
        BlockState surface = tgt > ground ? n < 0.3F ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState()
                : was.is(BlockTags.DIRT) && n < 0.25F ? Blocks.MOSS_BLOCK.defaultBlockState() : was;
        if (surface != was || tgt > ground) Terrain.set(level, pos, x, tgt, z, surface);
        if (surface.is(BlockTags.DIRT) && level.getBlockState(pos.set(x, tgt + 1, z)).isAir()) {
            float m = Terrain.noise(seed + 15, x, tgt, z);
            if (m < 0.2F) {
                Terrain.set(level, pos, x, tgt + 1, z, m < 0.08F ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
            } else if (surface.is(Blocks.MOSS_BLOCK) && m < 0.4F) {
                Terrain.set(level, pos, x, tgt + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
            }
        }
    }

    private static boolean skipped(int ground, BoundingBox b) {
        return ground > b.maxY() - 2 || ground < b.minY() + 4;
    }

    /** The cliff's rock: banded grey stone, with dark beds of tuff and andesite. */
    private BlockState rock(int x, int y, int z, float spray) {
        float bed = Terrain.field(seed + 16, y + 2.0F * Terrain.field(seed + 17, x, z, 20.0), 0.0, 3.0);
        if (spray < plunge() + 3.0F && Terrain.noise(seed + 18, x, y, z) < 0.25F) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        return bed < 0.25F ? Blocks.ANDESITE.defaultBlockState() : bed > 0.8F ? Blocks.TUFF.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    /** The face of the cliff: its banded rock, darkened and furred with moss round the falls, where the spray keeps it wet. */
    private BlockState wall(int x, int y, int z, float spray) {
        float n = Terrain.noise(seed + 19, x, y, z);
        float wet = 1.0F - Terrain.smooth((spray - plunge() - 1.0F) / 5.0F);
        if (n < 0.55F * wet) return n < 0.25F * wet ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        return rock(x, y, z, 99.0F);
    }

    /** Clears trees and plants out of a column above y (as {@link Terrain#clearAbove}), but leaves anything that's walling in a pond just outside. */
    private static void clearAbove(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, int height, BiPredicate<Integer, Integer> outside) {
        int max = Math.min(level.getMaxBuildHeight() - 1, y + height);
        for (int h = y + 1; h <= max; h++) {
            BlockState s = level.getBlockState(pos.set(x, h, z));
            if (s.isAir() || !Terrain.cover(s) || !s.getFluidState().isEmpty() || Terrain.pondBeside(level, pos, x, h, z, outside)) continue;
            Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
        }
    }
}
