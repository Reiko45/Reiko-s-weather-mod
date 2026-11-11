package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;

/**
 * A tepui: a table mountain of ancient sandstone standing sheer out of the rainforest, like Roraima or Auyán-tepui on
 * the Guiana Shield. They're the last pieces of a sandstone plateau laid down well over a billion years ago, which rain
 * and rivers have been taking apart ever since. The rock is so hard and so cleanly jointed that its walls break away in
 * whole slabs along the joints and stay vertical as they retreat, even leaning out a little at the top, and the broken
 * slabs pile up as scree round the foot. Water seeping down the walls streaks them black with algae, and ferns and vines
 * cling to the ledges. The top is a cold, rain-soaked world of its own, cut off from the forest far below: bare rock
 * pitted with pools and split by deep cracks, patches of moss and sedge, and gnarled, stunted shrubs. The streams that
 * gather on it run to the rim and fall the whole height of the cliffs in long, wavering ribbons into pools at the foot.
 *
 * @param base   the lowest ground sampled round its foot
 * @param top    the height of the plateau
 * @param radius its rough radius at the foot of the cliffs
 * @param falls  {bearing (thousandths of a radian), plunge pool's water level, plunge pool's radius, stream length} for
 *               each waterfall
 */
public record Tepui(int cx, int cz, int base, int top, int radius, int[] falls, long seed) implements Landform {
    /** How wide the scree apron round the foot is, and how far down from the rim the walls lean out. */
    private static final int APRON = 9, LEAN = 12;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 1 || ground > c.seaLevel() + 40) {
            return null;
        }
        int radius = 26 + c.random().nextInt(15), height = 40 + c.random().nextInt(31);
        if (!c.fits(ground, 24, height + 24)) {
            return null;
        }
        // lowland round about: not too rugged, and not under water anywhere
        int min = ground, max = ground;
        for (int i = 0; i < 16; i++) {
            float a = i < 6 ? i * Mth.TWO_PI / 6.0F : (i - 6) * Mth.TWO_PI / 10.0F + 0.3F;
            float d = i < 6 ? radius * 0.6F : radius + 8.0F;
            int h = c.groundAt(c.x() + Math.round(Mth.cos(a) * d), c.z() + Math.round(Mth.sin(a) * d));
            min = Math.min(min, h);
            max = Math.max(max, h);
            if (max - min > 14 || min < c.seaLevel()) {
                return null;
            }
        }
        long seed = c.random().nextLong();
        Tepui shape = new Tepui(c.x(), c.z(), min, max + height, radius, new int[0], seed);
        // the falls pour off the tips of one or two of its lobes
        java.util.List<Float> tips = new java.util.ArrayList<>();
        for (int k = 0; k < 72; k++) {
            float a = k * Mth.TWO_PI / 72.0F, e = shape.edge(a);
            if (e >= shape.edge(a - Mth.TWO_PI / 72.0F) && e >= shape.edge(a + Mth.TWO_PI / 72.0F)) tips.add(a);
        }
        int want = c.random().nextFloat() < 0.45F ? 2 : 1;
        int[] falls = new int[0];
        int low = min;
        for (int tries = 0; tries < 6 && falls.length / 4 < want && !tips.isEmpty(); tries++) {
            float a = tips.get(c.random().nextInt(tips.size()));
            if (falls.length > 0 && Math.abs(wrap(a - falls[0] / 1000.0F)) < 1.3F) continue;
            int milli = Math.round(Mth.positiveModulo(a, Mth.TWO_PI) * 1000.0F), pool = 4 + c.random().nextInt(2);
            float[] p = shape.poolCentre(milli / 1000.0F, pool);
            int foot = c.groundAt(Mth.floor(p[0]), Mth.floor(p[1]));
            if (foot < c.seaLevel() || foot > max + 2) continue;
            falls = java.util.Arrays.copyOf(falls, falls.length + 4);
            falls[falls.length - 4] = milli;
            falls[falls.length - 3] = foot - 1;
            falls[falls.length - 2] = pool;
            falls[falls.length - 1] = 9 + c.random().nextInt(9);
            low = Math.min(low, foot - 1);
        }
        return new Tepui(c.x(), c.z(), low, max + height, radius, falls, seed);
    }

    static Landform load(CompoundTag tag) {
        return new Tepui(tag.getInt("x"), tag.getInt("z"), tag.getInt("base"), tag.getInt("top"), tag.getInt("r"), tag.getIntArray("falls"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("base", base);
        tag.putInt("top", top);
        tag.putInt("r", radius);
        tag.put("falls", new IntArrayTag(falls));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.TEPUI;
    }

    @Override
    public BoundingBox bounds() {
        float r = radius * 1.33F + 1.0F + APRON;
        for (int i = 0; i < falls.length / 4; i++) {
            r = Math.max(r, edge(falls[i * 4] / 1000.0F) + falls[i * 4 + 2] * 2.0F + 1.6F);
        }
        int reach = Mth.ceil(r) + 2;
        return new BoundingBox(cx - reach, base - 12, cz - reach, cx + reach, top + 10, cz + reach);
    }

    /** An angle brought into -PI to PI. */
    private static float wrap(float a) {
        return a - Mth.TWO_PI * Mth.floor(a / Mth.TWO_PI + 0.5F);
    }

    /** How far the foot of the cliffs stands from the middle at a bearing: lobed, and notched by clefts. */
    private float edge(float a) {
        float r = 1.0F + 0.14F * Mth.sin(3.0F * a + Terrain.hash(seed, 2) * Mth.TWO_PI) + 0.09F * Mth.sin(5.0F * a + Terrain.hash(seed, 3) * Mth.TWO_PI)
                + 0.06F * Mth.sin(2.0F * a + Terrain.hash(seed, 4) * Mth.TWO_PI) + 0.04F * Mth.sin(8.0F * a + Terrain.hash(seed, 5) * Mth.TWO_PI);
        for (int k = 0; k < 3; k++) {
            float d = wrap(a - Terrain.hash(seed, 6 + k) * Mth.TWO_PI);
            r -= (0.05F + 0.09F * Terrain.hash(seed, 9 + k)) * Terrain.smooth(1.0F - Math.abs(d) / 0.18F);
        }
        return radius * r;
    }

    /** How far a column stands out beyond the foot of the cliffs (negative under the plateau). */
    private float out(int x, int z) {
        float dx = x + 0.5F - cx, dz = z + 0.5F - cz;
        return Mth.sqrt(dx * dx + dz * dz) - edge((float) Mth.atan2(dz, dx)) - 1.6F * (Terrain.field(seed + 1, x, z, 4.0) - 0.5F);
    }

    private float[] poolCentre(float a, int pool) {
        float d = edge(a) + pool - 1.0F;
        return new float[]{cx + Mth.cos(a) * d, cz + Mth.sin(a) * d};
    }

    /** {index, how far outside its edge} of the plunge pool nearest a column ({-1, a long way} if there's none). */
    private float[] nearestPool(int x, int z) {
        float best = 1.0E6F, which = -1;
        for (int i = 0; i < falls.length / 4; i++) {
            float[] p = poolCentre(falls[i * 4] / 1000.0F, falls[i * 4 + 2]);
            float d = Mth.sqrt(Mth.square(x + 0.5F - p[0]) + Mth.square(z + 0.5F - p[1])) - falls[i * 4 + 2];
            if (d < best) {
                best = d;
                which = i;
            }
        }
        return new float[]{which, best};
    }

    /** Whether a column is in a plunge pool. */
    private boolean inPool(int x, int z) {
        return out(x, z) > 0.0F && nearestPool(x, z)[1] < 0.0F;
    }

    /** 0 by the falls, rising to 1 a little way off: the walls are kept plain round them, so nothing gets in the water's way. */
    private static float calm(float poolEdge) {
        return Terrain.smooth((poolEdge - 3.0F) / 5.0F);
    }

    /** How far the walls lean out over their foot at a height, up by the rim. */
    private float lean(float calm, int y) {
        return (0.6F + 1.6F * Terrain.hash(seed, 1)) * calm * Terrain.smooth((y - (top - LEAN)) / (float) LEAN);
    }

    /** The cliffs' own rock at a height in a column {@code e} out from their foot (ledges aside). */
    private boolean cliff(float e, float calm, int y) {
        return y <= top && (e <= 0.0F || e <= lean(calm, y));
    }

    /** The height of the {@code k}th ledge down the walls. */
    private int ledgeLevel(int k) {
        return top - LEAN - 6 - k * 9 - Math.round(Terrain.hash(seed, 20 + k) * 3.0F);
    }

    /** Whether a ledge juts out at a height in a column: a shelf a block or so wide along a bedding plane. */
    private boolean ledge(int x, int z, float e, float calm, int y) {
        if (calm < 1.0F || e <= 0.0F || e > 1.3F) return false;
        for (int k = 0; ledgeLevel(k) > base + 10; k++) {
            int l = ledgeLevel(k);
            if ((y == l || y == l - 1) && Terrain.field(seed + 30 + k, x, z, 7.0) > 0.6F) return true;
        }
        return false;
    }

    private boolean rock(int x, int z, float e, float calm, int y) {
        return cliff(e, calm, y) || ledge(x, z, e, calm, y);
    }

    /** A column's place {u, v} along and across fall {@code i}'s bearing. */
    private float[] streamUv(int i, int x, int z) {
        return Terrain.uv(x + 0.5, z + 0.5, cx, cz, falls[i * 4] / 1000.0F);
    }

    /** How far a stream wanders across the top (not at all near the rim, where it runs straight over). */
    private float meander(int i, float u, float lip) {
        return 1.2F * Mth.sin(u * 0.3F + Terrain.hash(seed, 40 + i) * 6.0F) * Mth.clamp((lip - 5.0F - u) / 4.0F, 0.0F, 1.0F);
    }

    private float streamWidth(int i) {
        return 0.8F + 0.6F * Terrain.hash(seed, 44 + i);
    }

    private float headPool(int i) {
        return 2.5F + 1.5F * Terrain.hash(seed, 48 + i);
    }

    /** What fall {@code i}'s water does at a column on the top: 2 in the pool it rises from, 1 in its stream, 0 neither. */
    private int stream(int i, int x, int z) {
        float lip = edge(falls[i * 4] / 1000.0F), start = lip - falls[i * 4 + 3];
        float[] l = streamUv(i, x, z);
        if (Mth.sqrt(Mth.square(l[0] - start) + l[1] * l[1]) < headPool(i)) return 2;
        return l[0] >= start && l[0] < lip + 4.0F && Math.abs(l[1] - meander(i, l[0], lip)) < streamWidth(i) ? 1 : 0;
    }

    /** Whether a column is near enough a stream (or the pool it rises from) to be kept clear of cracks and rocks. */
    private boolean nearStream(int x, int z) {
        for (int i = 0; i < falls.length / 4; i++) {
            float lip = edge(falls[i * 4] / 1000.0F), start = lip - falls[i * 4 + 3];
            float[] l = streamUv(i, x, z);
            if (Mth.sqrt(Mth.square(l[0] - start) + l[1] * l[1]) < headPool(i) + 3.0F) return true;
            if (l[0] >= start - 3.0F && l[0] < lip + 6.0F && Math.abs(l[1] - meander(i, l[0], lip)) < streamWidth(i) + 3.0F) return true;
        }
        return false;
    }

    /** How deep a crack splits the top at a column (0 where there's none). */
    private int crack(int x, int z) {
        int count = 2 + Math.round(Terrain.hash(seed, 50) * 3.0F);
        for (int k = 0; k < count; k++) {
            float a = Terrain.hash(seed, 51 + k) * Mth.TWO_PI, d = Mth.sqrt(Terrain.hash(seed, 56 + k)) * radius * 0.6F;
            float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + Mth.cos(a) * d, cz + Mth.sin(a) * d, Terrain.hash(seed, 61 + k) * Mth.PI);
            float half = 6.0F + 12.0F * Terrain.hash(seed, 66 + k), wide = 0.6F + 0.6F * Terrain.hash(seed, 71 + k);
            float taper = Terrain.smooth((half - Math.abs(l[0])) / 5.0F);
            float wobble = 0.8F * Mth.sin(l[0] * 0.4F + k);
            if (Math.abs(l[0]) < half && Math.abs(l[1] - wobble) < wide * (0.4F + 0.6F * taper)) {
                return 1 + Math.round((4.0F + 9.0F * Terrain.hash(seed, 76 + k)) * taper);
            }
        }
        return 0;
    }

    /** Whether a crack runs within a couple of blocks of a column. */
    private boolean nearCrack(int x, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (crack(x + dx, z + dz) > 0) return true;
            }
        }
        return false;
    }

    /** How deep a pool lying in a hollow of the bare rock is at a column (0 where there's none). */
    private int hollow(int x, int z, float e) {
        float f = Terrain.field(seed + 80, x, z, 5.0);
        if (f >= 0.2F || e > -5.0F || nearCrack(x, z)) return 0;
        return f < 0.11F ? 2 : 1;
    }

    /** How high a knob of weathered rock stands up off the top at a column. */
    private int knob(int x, int z, float e) {
        float f = Terrain.field(seed + 81, x, z, 4.0);
        if (f < 0.8F || e > -1.5F || nearStream(x, z) || crack(x, z) > 0) return 0;
        return 1 + Math.min(3, Math.round((f - 0.8F) * 15.0F));
    }

    /** The soil on the top at a column: 2 moss, 1 turf, 0 bare rock. */
    private int soil(int x, int z) {
        float f = Terrain.field(seed + 82, x, z, 5.0);
        return f < 0.26F ? 2 : f < 0.38F ? 1 : 0;
    }

    /** The cliffs' sandstone, in pale beds of uneven thickness, streaked dark with algae where water seeps down the faces. */
    private BlockState wall(int x, int y, int z) {
        float s = Terrain.field(seed + 90, x, z, 1.6);
        if (s > 0.75F && y < top - 1 && y > top - 6 - 50.0F * Terrain.field(seed + 91, x, z, 9.0)) {
            return s > 0.92F ? Blocks.BROWN_TERRACOTTA.defaultBlockState() : Blocks.TUFF.defaultBlockState();
        }
        float bed = Terrain.field(seed + 92, y + 3.0F * Terrain.field(seed + 93, x, z, 30.0), 0.0, 3.5);
        if (Terrain.noise(seed + 94, x, y, z) < 0.08F) bed += 0.15F;
        if (bed < 0.2F) return Blocks.WHITE_TERRACOTTA.defaultBlockState();
        if (bed < 0.42F) return Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        if (bed < 0.9F) return Blocks.SANDSTONE.defaultBlockState();
        return Blocks.TERRACOTTA.defaultBlockState();
    }

    /** Bare rock on the top, blackened by lichen and algae in patches. */
    private BlockState bare(int x, int y, int z) {
        if (Terrain.field(seed + 95, x, z, 3.0) > 0.64F) return Blocks.TUFF.defaultBlockState();
        float n = Terrain.noise(seed, x, y, z);
        return n < 0.45F ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : n < 0.9F ? Blocks.SANDSTONE.defaultBlockState() : Blocks.WHITE_TERRACOTTA.defaultBlockState();
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minX = Math.max(area.minX(), b.minX()), maxX = Math.min(area.maxX(), b.maxX());
        int minZ = Math.max(area.minZ(), b.minZ()), maxZ = Math.min(area.maxZ(), b.maxZ());
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                float e = out(x, z);
                float[] near = nearestPool(x, z);
                int fall = (int) near[0];
                if (e > APRON && near[1] > 1.6F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground > b.maxY() - 4) continue;
                if (e <= 0.0F) {
                    plateau(level, pos, x, z, e, ground, b, fall >= 0 && near[1] < 2.0F ? falls[fall * 4 + 1] : Integer.MAX_VALUE);
                } else {
                    outside(level, pos, x, z, e, calm(near[1]), ground, b, fall, near[1]);
                }
            }
        }
        // stunted shrubs on the top, once the ground under them is all done (and only where they're wholly in this chunk)
        BlockState leaves = Blocks.AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        BlockState flowering = Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int tx = minX; tx <= maxX; tx++) {
            for (int tz = minZ; tz <= maxZ; tz++) {
                if (Terrain.noise(seed + 200, tx, top, tz) > 0.007F) continue;
                int crown = Terrain.noise(seed + 201, tx, top, tz) < 0.6F ? 1 : 2, height = 1 + Math.floorMod(tx * 31 + tz * 17, crown + 1);
                if (!area.isInside(tx - crown, top, tz - crown) || !area.isInside(tx + crown, top, tz + crown)) continue;
                float e = out(tx, tz);
                if (e > -2.0F || soil(tx, tz) == 0 || nearStream(tx, tz) || crack(tx, tz) > 0 || hollow(tx, tz, e) > 0 || knob(tx, tz, e) > 0) continue;
                if (!level.getBlockState(pos.set(tx, top + 2, tz)).isAir()) continue; // a pitcher plant there already
                for (int h = 1; h <= height; h++) Terrain.set(level, pos, tx, top + h, tz, Blocks.DARK_OAK_LOG.defaultBlockState());
                for (int dx = -crown; dx <= crown; dx++) {
                    for (int dz = -crown; dz <= crown; dz++) {
                        for (int dy = 0; dy <= 1; dy++) {
                            if (Math.abs(dx) + Math.abs(dz) > (crown == 1 ? 2 : crown) - dy || dx == 0 && dz == 0 && dy == 0) continue;
                            pos.set(tx + dx, top + height + dy, tz + dz);
                            if (!level.getBlockState(pos).isAir()) continue;
                            level.setBlock(pos, Terrain.noise(seed, tx + dx, top + dy, tz + dz) < 0.3F ? flowering : leaves, Terrain.FLAGS);
                        }
                    }
                }
            }
        }
    }

    /** A column under the plateau: rock right up from the ground, and whatever the top holds. */
    private void plateau(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, float e, int ground, BoundingBox b, int pool) {
        // beside a plunge pool, the rock goes down deep enough to hold its water in
        int bottom = Math.max(b.minY(), Math.min(ground - 2, pool - 6));
        for (int y = bottom; y < top; y++) Terrain.set(level, pos, x, y, z, wall(x, y, z));
        Terrain.clearAbove(level, pos, x, top - 1, z, b.maxY() - top);
        for (int y = top + 1; y <= ground; y++) Terrain.set(level, pos, x, y, z, Blocks.AIR.defaultBlockState());
        float n = Terrain.noise(seed, x, top, z);
        for (int i = 0; i < falls.length / 4; i++) {
            int s = stream(i, x, z);
            if (s == 0) continue;
            int lip = lip(x, z);
            if (s == 2 && lip == 0 && e < -1.5F) {
                // the pool the stream rises from, in a hollow of the rock
                Terrain.set(level, pos, x, top - 3, z, Blocks.SANDSTONE.defaultBlockState());
                Terrain.set(level, pos, x, top - 2, z, Blocks.WATER.defaultBlockState());
                Terrain.set(level, pos, x, top - 1, z, Blocks.WATER.defaultBlockState());
                Terrain.set(level, pos, x, top, z, Blocks.AIR.defaultBlockState());
                return;
            }
            if (s == 1 && lip >= 0) {
                // the stream, cut a block down into the rock, and over the rim at its end
                Terrain.set(level, pos, x, top - 2, z, n < 0.5F ? Blocks.GRAVEL.defaultBlockState() : Blocks.SMOOTH_SANDSTONE.defaultBlockState());
                Terrain.set(level, pos, x, top - 1, z, Blocks.WATER.defaultBlockState());
                Terrain.set(level, pos, x, top, z, Blocks.AIR.defaultBlockState());
                if (lip > 0) level.scheduleTick(pos.set(x, top - 1, z).immutable(), Fluids.WATER, 1); // set it pouring over the edge
                return;
            }
        }
        int crack = e < -2.0F && !nearStream(x, z) ? crack(x, z) : 0;
        if (crack > 0) {
            // a deep, narrow crack along a joint, damp and mossy in the bottom
            for (int y = top - crack + 1; y <= top; y++) Terrain.set(level, pos, x, y, z, Blocks.AIR.defaultBlockState());
            boolean moss = n < 0.55F;
            Terrain.set(level, pos, x, top - crack, z, moss ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
            if (moss && n < 0.25F) Terrain.set(level, pos, x, top - crack + 1, z, Blocks.FERN.defaultBlockState());
            return;
        }
        int hollow = hollow(x, z, e);
        if (hollow > 0) {
            // rainwater lying in a hollow of the bare rock
            for (int y = top - hollow; y < top; y++) Terrain.set(level, pos, x, y, z, Blocks.WATER.defaultBlockState());
            Terrain.set(level, pos, x, top, z, Blocks.AIR.defaultBlockState());
            return;
        }
        int knob = knob(x, z, e);
        if (knob > 0) {
            // a knob of rock the weather has worn round
            for (int y = top; y <= top + knob; y++) Terrain.set(level, pos, x, y, z, bare(x, y, z));
            return;
        }
        surface(level, pos, x, z, n);
    }

    /** The top of the plateau: bare rock, or moss and turf with plants growing on it. */
    private void surface(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, float n) {
        int soil = soil(x, z);
        if (soil == 2) {
            Terrain.set(level, pos, x, top, z, Blocks.MOSS_BLOCK.defaultBlockState());
            if (n < 0.03F) {
                // a pitcher plant, catching insects since the soil's too poor to feed it
                Terrain.set(level, pos, x, top + 1, z, Blocks.PITCHER_PLANT.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
                Terrain.set(level, pos, x, top + 2, z, Blocks.PITCHER_PLANT.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
            } else if (n < 0.2F) {
                Terrain.set(level, pos, x, top + 1, z, Blocks.FERN.defaultBlockState());
            } else if (n < 0.35F) {
                Terrain.set(level, pos, x, top + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
            }
        } else if (soil == 1) {
            Terrain.set(level, pos, x, top, z, Blocks.GRASS_BLOCK.defaultBlockState());
            if (n < 0.4F) Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
        } else {
            Terrain.set(level, pos, x, top, z, bare(x, top, z));
        }
    }

    /**
     * Whether a column on the top can hold water: -1 where it can't (it's at the rim, and water spilling over there
     * wouldn't land in a plunge pool), 1 where it pours over the rim into one, 0 where it's away from the rim.
     */
    private int lip(int x, int z) {
        int result = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int nx = x + d.getStepX(), nz = z + d.getStepZ();
            if (out(nx, nz) <= 0.0F) continue;
            if (!inPool(nx, nz)) return -1;
            result = 1;
        }
        return result;
    }

    /** A column out beyond the foot of the cliffs: scree, a plunge pool, or the walls leaning out overhead. */
    private void outside(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, float e, float calm, int ground, BoundingBox b, int fall, float poolEdge) {
        int surface = Terrain.surface(level, pos, x, ground, z);
        float n = Terrain.noise(seed, x, ground, z);
        if (fall >= 0 && poolEdge < 1.6F) {
            int water = falls[fall * 4 + 1];
            Terrain.clearAbove(level, pos, x, Math.min(ground, water), z, top + 2 - Math.min(ground, water));
            if (poolEdge < 0.0F) {
                // the plunge pool the falls have drilled into the ground at the foot
                float d = 1.0F + poolEdge / falls[fall * 4 + 2];
                int floor = water - 1 - Math.round(2.5F * (1.0F - d * d));
                // solid under the floor, as deep as the water goes anywhere beside it
                for (int h = water - 5; h < floor; h++) {
                    BlockState was = level.getBlockState(pos.set(x, h, z));
                    if (h > ground || h == floor - 1 || !was.blocksMotion() || !was.getFluidState().isEmpty()) Terrain.set(level, pos, x, h, z, Blocks.STONE.defaultBlockState());
                }
                Terrain.set(level, pos, x, floor, z, n < 0.6F ? Blocks.GRAVEL.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                for (int h = floor + 1; h <= water; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                for (int h = water + 1; h <= Math.max(ground, surface); h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                return;
            }
            // its rim: built up where the ground is low, and sealed below, so the water stays in
            int rim = Math.max(Math.max(ground, surface), water + 1);
            for (int h = water - 5; h <= rim; h++) {
                pos.set(x, h, z);
                BlockState was = level.getBlockState(pos);
                if (h <= ground && was.blocksMotion() && was.getFluidState().isEmpty()) continue;
                Terrain.set(level, pos, x, h, z, h == rim ? (n < 0.5F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState())
                        : Blocks.STONE.defaultBlockState());
            }
            return;
        }
        if (ground < b.minY() + 4) return;
        // the scree apron: slabs fallen off the walls, piled against the foot and grown over further out
        int foot = ground;
        if (e < APRON) {
            float t = 1.0F - e / APRON;
            int scree = Math.round((3.0F + 5.0F * Terrain.field(seed + 96, x, z, 6.0)) * calm * t * t);
            if (scree > 0 && Terrain.field(seed + 97, x, z, 2.5) > 0.8F) scree += 1 + Math.round(n); // a fallen block lying on it
            if (scree > 0 && (surface == ground || ground + scree > surface)) {
                Terrain.clearAbove(level, pos, x, ground, z, Math.min(16, b.maxY() - ground));
                foot = ground + scree;
                boolean grown = e > APRON * 0.45F;
                for (int h = ground + 1; h <= foot; h++) {
                    float m = Terrain.noise(seed + 1, x, h, z);
                    BlockState s = m < 0.25F ? Blocks.GRAVEL.defaultBlockState() : m < 0.4F ? Blocks.COBBLESTONE.defaultBlockState()
                            : m < 0.62F ? Blocks.SANDSTONE.defaultBlockState() : m < 0.75F ? Blocks.SMOOTH_SANDSTONE.defaultBlockState()
                            : m < 0.88F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.WHITE_TERRACOTTA.defaultBlockState();
                    if (h == foot && grown) s = n < 0.4F ? Blocks.GRASS_BLOCK.defaultBlockState() : n < 0.7F ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
                    Terrain.set(level, pos, x, h, z, s);
                }
                if (grown && n < 0.7F && Terrain.noise(seed + 2, x, foot, z) < 0.35F) Terrain.set(level, pos, x, foot + 1, z, Blocks.FERN.defaultBlockState());
            }
        }
        // where the walls lean out, the rock overhead; and the ledges, with ferns rooted on them
        if (cliff(e, calm, top)) {
            Terrain.clearAbove(level, pos, x, top - LEAN - 1, z, b.maxY() - (top - LEAN));
            for (int y = top - LEAN; y < top; y++) {
                if (y > foot + 1 && cliff(e, calm, y)) Terrain.set(level, pos, x, y, z, wall(x, y, z));
            }
            surface(level, pos, x, z, Terrain.noise(seed, x, top, z));
        }
        for (int k = 0; ledgeLevel(k) > base + 10; k++) {
            int l = ledgeLevel(k);
            if (l <= foot + 2 || !ledge(x, z, e, calm, l)) continue;
            Terrain.clearAbove(level, pos, x, l - 2, z, 3);
            Terrain.set(level, pos, x, l - 1, z, wall(x, l - 1, z));
            Terrain.set(level, pos, x, l, z, Terrain.noise(seed + 3, x, l, z) < 0.6F ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
            if (Terrain.noise(seed + 4, x, l, z) < 0.45F) Terrain.set(level, pos, x, l + 1, z, Blocks.FERN.defaultBlockState());
        }
        vines(level, pos, x, z, e, calm, foot);
    }

    /** Curtains of vines hanging down the walls, from the rim and from under the ledges and overhangs. */
    private void vines(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, float e, float calm, int foot) {
        if (calm < 1.0F || e > 4.0F) return;
        Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        float[] beside = new float[4], calms = new float[4];
        for (int k = 0; k < 4; k++) {
            int nx = x + dirs[k].getStepX(), nz = z + dirs[k].getStepZ();
            beside[k] = out(nx, nz);
            calms[k] = calm(nearestPool(nx, nz)[1]);
        }
        int left = 0;
        for (int y = top; y > foot + 1; y--) {
            if (rock(x, z, e, calm, y)) {
                left = 0;
                continue;
            }
            if (left <= 0 && (y == top || rock(x, z, e, calm, y + 1)) && Terrain.noise(seed + 5, x, y, z) < 0.45F) {
                left = 3 + Math.round(Terrain.noise(seed + 6, x, y, z) * 14.0F);
            }
            if (left <= 0) continue;
            left--;
            BlockState vine = null;
            for (int k = 0; k < 4; k++) {
                if (!rock(x + dirs[k].getStepX(), z + dirs[k].getStepZ(), beside[k], calms[k], y)) continue;
                vine = (vine == null ? Blocks.VINE.defaultBlockState() : vine).setValue(VineBlock.getPropertyForFace(dirs[k]), true);
            }
            pos.set(x, y, z);
            if (vine == null || !level.getBlockState(pos).isAir()) {
                left = 0;
                continue;
            }
            level.setBlock(pos, vine, Terrain.FLAGS);
        }
    }
}
