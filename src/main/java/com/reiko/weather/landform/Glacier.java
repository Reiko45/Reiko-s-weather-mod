package com.reiko.weather.landform;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.WeatherAdvancements;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A tongue of ice flowing down a mountainside: packed ice with veins of blue ice, snow-covered higher up and
 * bare lower down. Crevasses cut across it, many of them hidden under a bridge of powder snow. Where its bed
 * steepens, the ice is stretched until it cracks apart into an icefall, a chaos of toppling ice towers (seracs)
 * and gaping crevasses. Lower down, meltwater streams on its surface have drilled moulins, shafts straight down
 * through the ice to the rock. Here and there on the melting lower half stand glacier tables: boulders that fell onto
 * the ice from the valley sides, each perched on a pillar of ice. The rock shades the ice under it from the sun, so as
 * the surface melts down all round, the ice beneath the boulder is left standing. In summer the lower end melts back a
 * little, meltwater runs off it, and pours roaring down the moulins, and the tables' pedestals melt back with the ice
 * round them, letting their boulders down.
 *
 * @param icefall where along the glacier its icefall is ({@link #NO_ICEFALL} if it hasn't one, as on glaciers from
 *                before there were any)
 * @param moulins whether it has moulins (not on glaciers from before there were any)
 * @param tables  whether it has glacier tables (not on glaciers from before there were any)
 */
public record Glacier(int cx, int cz, float angle, int length, int width, int thickness, int icefall, boolean moulins, boolean tables,
                      long seed) implements Landform {
    public static final int NO_ICEFALL = Integer.MIN_VALUE;
    /** The most a table's pedestal stands above the ice round it before it melts down with it. */
    private static final int PEDESTAL = 5;

    public Glacier(int cx, int cz, float angle, int length, int width, int thickness, long seed) {
        this(cx, cz, angle, length, width, thickness, NO_ICEFALL, false, false, seed);
    }

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 8 || !c.fits(ground, 30, 40)) {
            return null;
        }
        // flow downhill: find the steepest way down
        float best = 0.0F;
        int drop = 0;
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F;
            int h = c.groundAt(c.x() + Math.round(Mth.cos(a) * 30), c.z() + Math.round(Mth.sin(a) * 30));
            if (ground - h > drop) {
                drop = ground - h;
                best = a;
            }
        }
        if (drop < 6) {
            return null;
        }
        int length = 70 + c.random().nextInt(41), width = 24 + c.random().nextInt(13), thickness = 5 + c.random().nextInt(5);
        long seed = c.random().nextLong();
        // where the bed steepens most (if it steepens enough), the ice breaks up into an icefall
        int icefall = NO_ICEFALL, steepest = 5, previous = 0;
        float step = length * 0.8F / 6.0F;
        for (int i = 0; i <= 6; i++) {
            float u = -length * 0.4F + i * step;
            int h = c.groundAt(c.x() + Math.round(Mth.cos(best) * u), c.z() + Math.round(Mth.sin(best) * u));
            if (i > 0 && previous - h > steepest) {
                steepest = previous - h;
                icefall = Math.round(u - step / 2.0F);
            }
            previous = h;
        }
        return new Glacier(c.x(), c.z(), best, length, width, thickness, icefall, true, true, seed);
    }

    static Landform load(CompoundTag tag) {
        return new Glacier(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("width"),
                tag.getInt("thick"), tag.contains("icefall") ? tag.getInt("icefall") : NO_ICEFALL, tag.getBoolean("moulins"), tag.getBoolean("tables"),
                tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putInt("width", width);
        tag.putInt("thick", thickness);
        tag.putInt("icefall", icefall);
        tag.putBoolean("moulins", moulins);
        tag.putBoolean("tables", tables);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.GLACIER;
    }

    @Override
    public BoundingBox bounds() {
        // (this takes in the glacier tables too: they all stand on the ice, which lies well within it)
        int r = length / 2 + width / 2 + 3;
        return new BoundingBox(cx - r, -64, cz - r, cx + r, 319, cz + r);
    }

    /** Along the glacier (downhill, 0 in the middle) and across it. */
    private float[] local(double x, double z) {
        double dx = x - cx, dz = z - cz, c = Mth.cos(angle), s = Mth.sin(angle);
        return new float[]{(float) (dx * c + dz * s), (float) (-dx * s + dz * c)};
    }

    private float halfWidth(float u) {
        float half = length / 2.0F;
        return width / 2.0F * (1.0F - 0.45F * Math.max(0.0F, u) / half) * (1.0F + 0.1F * Mth.sin(u * 0.08F + seed % 100));
    }

    /** How thick the ice is here (0 outside the glacier). */
    public float thicknessAt(double x, double z) {
        float[] l = local(x, z);
        float half = length / 2.0F, hw = halfWidth(l[0]);
        if (Math.abs(l[0]) > half || Math.abs(l[1]) > hw) return 0.0F;
        float across = Mth.sqrt(Math.max(0.0F, 1.0F - (l[1] / hw) * (l[1] / hw)));
        float ends = Terrain.smooth((half - l[0]) / (length * 0.12F)) * Terrain.smooth((l[0] + half) / (length * 0.08F));
        return thickness * across * ends;
    }

    /** How far (u, v) is from the line of the ice cave (a big number if this glacier hasn't got one). */
    private float caveDistance(float u, float v) {
        float half = length / 2.0F;
        if (Terrain.hash(seed, 11) > 0.65F || u > half - 2.0F || u < half - 34.0F) return 99.0F;
        float centre = 3.0F * Mth.sin(u * 0.15F + Terrain.hash(seed, 12) * 6.0F);
        return Math.abs(v - centre);
    }

    /** Whether (u, v) is in a crevasse, and which one (or -1). */
    private int crevasse(float u, float v) {
        float half = length / 2.0F, spacing = 13.0F + 5.0F * Terrain.hash(seed, 7);
        for (int k = 0; ; k++) {
            float uk = -half + length * 0.15F + k * spacing;
            if (uk > length * 0.25F) return -1;
            if (Math.abs(u - uk - 2.0F * Mth.sin(v * 0.18F + k)) < 0.9F && Math.abs(v) < halfWidth(u) * 0.8F) return k;
        }
    }

    /** How much a point is in the icefall (0-1). */
    private float icefallAt(float u) {
        return icefall == NO_ICEFALL ? 0.0F : Terrain.smooth((10.0F - Math.abs(u - icefall)) / 4.0F);
    }

    /**
     * The moulins, {x, z, size, lip x, lip z}: each a shaft 1 or 2 blocks across, and the notch on one side of it
     * where the meltwater pours in. They're only where the ice is thick and whole, clear of crevasses, the ice cave
     * and the icefall, and above the snout that melts back in summer.
     */
    private int[][] moulinList() {
        if (!moulins) return new int[0][];
        int n = 2 + Math.round(Terrain.hash(seed, 70) * 2.0F);
        java.util.List<int[]> list = new java.util.ArrayList<>();
        double c = Mth.cos(angle), s = Mth.sin(angle);
        for (int i = 0; i < 16 && list.size() < n; i++) {
            float u = length * (-0.1F + 0.33F * Terrain.hash(seed, 71 + i)), v = (Terrain.hash(seed, 90 + i) - 0.5F) * halfWidth(u) * 0.9F;
            int x = Mth.floor(cx + u * c - v * s), z = Mth.floor(cz + u * s + v * c);
            int size = Terrain.hash(seed, 110 + i) < 0.5F ? 1 : 2;
            int side = Math.min(3, (int) (Terrain.hash(seed, 130 + i) * 4.0F));
            int lx = side == 0 ? x - 1 : side == 1 ? x + size : x, lz = side == 2 ? z - 1 : side == 3 ? z + size : z;
            boolean ok = true;
            for (int dx = -2; dx <= size + 1 && ok; dx++) {
                for (int dz = -2; dz <= size + 1 && ok; dz++) {
                    float[] l = local(x + dx + 0.5, z + dz + 0.5);
                    ok = thicknessAt(x + dx + 0.5, z + dz + 0.5) >= 4.0F && crevasse(l[0], l[1]) < 0 && caveDistance(l[0], l[1]) > 4.5F && icefallAt(l[0]) == 0.0F;
                }
            }
            if (ok) list.add(new int[]{x, z, size, lx, lz});
        }
        return list.toArray(new int[0][]);
    }

    /**
     * The glacier tables, {x, z, the boulder's size, the pedestal's x, its z, its size, its height, the boulder's height,
     * kind of rock}: each a boulder 2 or 3 blocks across (x and z its corner) on a narrower pillar of ice. They stand out
     * on the lower, melting half of the glacier, where the ice is whole (clear of crevasses, moulins and the icefall; the
     * ice cave runs under a roof of ice that bears them) but above the snout, where the meltwater runs. Each lies wholly
     * in one chunk, so it's built all at once.
     */
    int[][] tableList() {
        if (!tables) return new int[0][];
        int n = 3 + Math.round(Terrain.hash(seed, 150) * 5.0F);
        java.util.List<int[]> list = new java.util.ArrayList<>();
        int[][] shafts = moulinList();
        double c = Mth.cos(angle), s = Mth.sin(angle);
        for (int i = 0; i < 128 && list.size() < n; i++) {
            long ts = seed + 7919L * (i + 1);
            float u = length * 0.42F * Terrain.hash(ts, 1), v = (Terrain.hash(ts, 2) - 0.5F) * halfWidth(u) * 1.6F;
            int size = Terrain.hash(ts, 3) < 0.5F ? 2 : 3;
            int x = Mth.floor(cx + u * c - v * s), z = Mth.floor(cz + u * s + v * c);
            if (Math.floorMod(x, 16) > 16 - size || Math.floorMod(z, 16) > 16 - size) continue;
            boolean ok = true;
            // (the ice whole round it, and under it clear of a crevasse's walls too)
            for (int dx = -1; dx <= size && ok; dx++) {
                for (int dz = -1; dz <= size && ok; dz++) {
                    float[] l = local(x + dx + 0.5, z + dz + 0.5);
                    boolean under = dx >= 0 && dz >= 0 && dx < size && dz < size;
                    ok = thicknessAt(x + dx + 0.5, z + dz + 0.5) >= 3.0F && crevasse(l[0], l[1]) < 0 && icefallAt(l[0]) == 0.0F
                            && l[0] > 0.0F && l[0] < length * 0.42F
                            && (!under || crevasse(l[0] + 1.2F, l[1]) < 0 && crevasse(l[0] - 1.2F, l[1]) < 0);
                }
            }
            for (int[] m : shafts) ok &= x + size + 1 < m[0] - 1 || x - 2 > m[0] + m[2] || z + size + 1 < m[1] - 1 || z - 2 > m[1] + m[2];
            for (int[] t : list) ok &= x + size + 2 < t[0] || x - 3 > t[0] + t[2] || z + size + 2 < t[1] || z - 3 > t[1] + t[2];
            if (!ok) continue;
            // the pedestal under the middle of a big boulder, or under one corner or side of a smaller one
            int ps = size == 2 ? 1 : Terrain.hash(ts, 4) < 0.6F ? 1 : 2;
            int ox = ps == 1 && size == 3 ? 1 : Terrain.hash(ts, 5) < 0.5F ? 0 : size - ps;
            int oz = ps == 1 && size == 3 ? 1 : Terrain.hash(ts, 6) < 0.5F ? 0 : size - ps;
            int ph = 2 + Math.round(Terrain.hash(ts, 7) * 2.0F), bh = Terrain.hash(ts, 8) < 0.55F ? 2 : 1;
            list.add(new int[]{x, z, size, x + ox, z + oz, ps, ph, bh, Math.min(2, (int) (Terrain.hash(ts, 9) * 3.0F))});
        }
        return list.toArray(new int[0][]);
    }

    private static boolean boulder(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.ANDESITE) || state.is(Blocks.GRANITE);
    }

    /** A glacier table's boulder and its pedestal, standing on the ice whose top in each of its columns is {@code tops}. */
    private void table(WorldGenLevel level, BlockPos.MutableBlockPos pos, int[] t, int[] tops) {
        int size = t[2], rest = Integer.MIN_VALUE;
        for (int top : tops) rest = Math.max(rest, top);
        // the pedestal: the ice the boulder has kept in its shade while the surface melted down round it
        rest += t[6];
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                int x = t[0] + dx, z = t[1] + dz;
                boolean pillar = x >= t[3] && x < t[3] + t[5] && z >= t[4] && z < t[4] + t[5];
                if (pillar) {
                    for (int h = tops[dx * size + dz] + 1; h <= rest; h++) Terrain.set(level, pos, x, h, z, Blocks.PACKED_ICE.defaultBlockState());
                }
                // the boulder, chunky, its upper layer rounded off at the corners
                boolean corner = (dx == 0 || dx == size - 1) && (dz == 0 || dz == size - 1);
                for (int h = 1; h <= t[7]; h++) {
                    if (h == 2 && !pillar && (size == 3 ? corner : Terrain.noise(seed + 9, x, rest, z) < 0.3F)) continue;
                    float n = Terrain.noise(seed + 10, x, rest + h, z);
                    Block rock = switch (t[8]) {
                        case 0 -> n < 0.25F ? Blocks.ANDESITE : Blocks.STONE;
                        case 1 -> n < 0.3F ? Blocks.STONE : Blocks.ANDESITE;
                        default -> n < 0.2F ? Blocks.STONE : Blocks.GRANITE;
                    };
                    Terrain.set(level, pos, x, rest + h, z, rock.defaultBlockState());
                }
            }
        }
    }

    /**
     * Summer: where the ice round a glacier table has melted down, its pedestal melts back too, letting the boulder down a
     * block onto what's left of it, so that it never stands more than a few blocks proud of the ice. (The surface melt
     * itself never touches a table: the top of each of its columns is rock.)
     */
    private void settleTable(ServerLevel level) {
        int[][] list = tableList();
        if (list.length == 0) return;
        int[] t = list[level.random.nextInt(list.length)];
        int x0 = t[0], z0 = t[1], size = t[2], m = size / 2;
        if (!level.isLoaded(new BlockPos(x0, 0, z0))) return;
        // the ice round it, a block out from each side of the boulder
        int around = Integer.MIN_VALUE;
        for (int[] r : new int[][]{{x0 - 1, z0 + m}, {x0 + size, z0 + m}, {x0 + m, z0 - 1}, {x0 + m, z0 + size}}) {
            BlockPos column = new BlockPos(r[0], 0, r[1]);
            if (!level.isLoaded(column)) return;
            around = Math.max(around, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column).getY() - 1);
        }
        // where the boulder rests now: down through it from its top, over the pedestal
        BlockPos.MutableBlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(t[3], 0, t[4])).below().mutable();
        if (!boulder(level.getBlockState(pos))) return;
        while (pos.getY() > level.getMinBuildHeight() && boulder(level.getBlockState(pos.move(net.minecraft.core.Direction.DOWN)))) {
            // (down to the first block under the boulder)
        }
        // (the boulder, and any snow lying on it, all come down together)
        int rest = pos.getY(), high = t[7] + 1;
        if (!level.getBlockState(pos).is(Blocks.PACKED_ICE) || rest - around <= PEDESTAL) return;
        // (only if it's still just rock over the pedestal's ice or air: leave it be if anyone has been at it)
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                BlockState under = level.getBlockState(pos.set(x0 + dx, rest, z0 + dz));
                if (!under.isAir() && !isIce(under) && !under.is(Blocks.SNOW)) return;
                for (int h = 1; h <= high; h++) {
                    BlockState s = level.getBlockState(pos.set(x0 + dx, rest + h, z0 + dz));
                    if (!s.isAir() && !boulder(s) && !s.is(Blocks.SNOW)) return;
                }
                if (!level.getBlockState(pos.set(x0 + dx, rest + high + 1, z0 + dz)).isAir()) return;
            }
        }
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                for (int h = 0; h < high; h++) {
                    BlockState above = level.getBlockState(pos.set(x0 + dx, rest + h + 1, z0 + dz));
                    level.setBlock(pos.set(x0 + dx, rest + h, z0 + dz), above, Block.UPDATE_ALL);
                }
                level.setBlock(pos.set(x0 + dx, rest + high, z0 + dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private static BlockState iceMix(float n) {
        return n < 0.35F ? Blocks.BLUE_ICE.defaultBlockState() : n < 0.75F ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.ICE.defaultBlockState();
    }

    /** The icefall: the ice cracked into blocks that lean and topple into towers of bare ice, with crevasses gaping between. */
    private void icefall(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, float[] l, int ground, int top, float fall) {
        float cu = (l[0] - icefall) / 4.0F;
        int iu = Mth.floor(cu);
        float cv = l[1] / 4.0F + 0.5F * iu; // each row of blocks staggered against the last
        int iv = Mth.floor(cv);
        float fu = (cu - iu) * 4.0F, fv = (cv - iv) * 4.0F;
        long cell = seed + iu * 73428767L + iv * 912931L;
        if (fu < 1.0F || fv < 0.9F) {
            int floor = Math.max(ground + 1, top - 3 - Math.round(Terrain.hash(cell, 1) * 3.0F * fall));
            // (where a pond just off the ice comes up against it, the ice is left standing to hold it in)
            for (int h = top; h > floor; h--) {
                if (Terrain.pondBeside(level, pos, x, h, z, (nx, nz) -> Math.round(thicknessAt(nx + 0.5, nz + 0.5)) < 1)) {
                    floor = h;
                    break;
                }
            }
            for (int h = floor + 1; h <= top; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
            Terrain.set(level, pos, x, floor, z, Terrain.noise(seed, x, floor, z) < 0.5F ? Blocks.BLUE_ICE.defaultBlockState() : Blocks.SNOW_BLOCK.defaultBlockState());
            return;
        }
        // a serac, its shoulders broken off
        int rise = Math.round(fall * (1.0F + 4.0F * Terrain.hash(cell, 2)));
        if (fu < 1.6F || fu > 3.4F || fv < 1.5F || fv > 3.4F) rise = Math.max(0, rise - 1 - Math.round(Terrain.hash(cell, 3)));
        for (int h = Math.max(ground + 1, top - 1); h <= top + rise; h++) Terrain.set(level, pos, x, h, z, iceMix(Terrain.noise(seed + 1, x, h, z)));
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[][] shafts = moulinList();
        // the glacier tables in this chunk, and the top of the ice in each of their columns (from the ground as it is now)
        int[][] tabled = tableList();
        int[][] tops = new int[tabled.length][];
        for (int k = 0; k < tabled.length; k++) {
            int[] t = tabled[k];
            if (!Terrain.column(area, t[0], t[1])) continue;
            tops[k] = new int[t[2] * t[2]];
            for (int dx = 0; dx < t[2]; dx++) {
                for (int dz = 0; dz < t[2]; dz++) {
                    int x = t[0] + dx, z = t[1] + dz;
                    tops[k][dx * t[2] + dz] = Terrain.ground(level, pos, x, z) + Math.round(thicknessAt(x + 0.5, z + 0.5));
                }
            }
        }
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                int thick = Math.round(thicknessAt(x + 0.5, z + 0.5));
                float[] l = local(x + 0.5, z + 0.5);
                if (thick < 1) {
                    if (WeatherConfig.SPEC.isLoaded() && WeatherConfig.ERRATICS.get()) moraine(level, pos, x, z, l);
                    continue;
                }
                int ground = Terrain.ground(level, pos, x, z);
                int pond = Terrain.surface(level, pos, x, ground, z);
                Terrain.clearAbove(level, pos, x, ground, z, 24);
                int top = ground + thick;
                float fall = icefallAt(l[0]);
                int crev = thick >= 3 && fall < 0.05F ? crevasse(l[0], l[1]) : -1;
                boolean wall = crev < 0 && thick >= 3 && fall < 0.05F && (crevasse(l[0] + 1.2F, l[1]) >= 0 || crevasse(l[0] - 1.2F, l[1]) >= 0);
                for (int h = ground + 1; h <= top; h++) {
                    float n = Terrain.noise(seed, x, h, z);
                    BlockState ice = wall || n > 0.88F && h < top - 1 ? Blocks.BLUE_ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
                    if (h == top) {
                        ice = l[0] < 0.0F ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
                    }
                    Terrain.set(level, pos, x, h, z, ice);
                }
                // a pond the ice has overridden is frozen into it (the sea round a snout is left alone)
                for (int h = Math.max(top + 1, level.getSeaLevel()); h <= pond; h++) Terrain.set(level, pos, x, h, z, Blocks.PACKED_ICE.defaultBlockState());
                // an ice cave: a blue tunnel melted up into the glacier from its foot
                float cave = caveDistance(l[0], l[1]);
                if (cave < 2.8F && thick >= 5) {
                    int roof = Math.round(Mth.sqrt(Math.max(0.0F, 2.6F * 2.6F - cave * cave)) * 1.3F) + 1;
                    for (int h = ground + 1; h <= ground + Math.min(roof, thick - 2); h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                } else if (cave < 4.0F && thick >= 5) {
                    for (int h = ground + 1; h <= ground + Math.min(4, thick - 1); h++) Terrain.set(level, pos, x, h, z, Blocks.BLUE_ICE.defaultBlockState());
                }
                if (fall >= 0.05F && thick >= 2) {
                    icefall(level, pos, x, z, l, ground, top, fall);
                    continue;
                }
                if (moulin(level, pos, shafts, x, z, ground, top)) continue;
                if (crev >= 0) {
                    int depth = Mth.clamp(thick + 2, 5, 8);
                    for (int h = top - depth + 1; h <= top; h++) {
                        Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    }
                    Terrain.set(level, pos, x, top - depth, z, Blocks.SNOW_BLOCK.defaultBlockState());
                    // most of each crevasse hides under a thin bridge of powder snow
                    if (Terrain.hash(seed, crev * 31 + Mth.floor(l[1] / 4.0F)) < 0.7F) {
                        Terrain.set(level, pos, x, top, z, Blocks.POWDER_SNOW.defaultBlockState());
                    }
                } else if (l[0] < -length * 0.1F && Terrain.noise(seed, x, 0, z) > 0.3F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SNOW.defaultBlockState());
                }
            }
        }
        for (int k = 0; k < tabled.length; k++) {
            if (tops[k] != null) table(level, pos, tabled[k], tops[k]);
        }
    }

    /** A moulin's shaft, its lip, or the blue ice lining it, if this column is one; whether it was. */
    private static boolean moulin(WorldGenLevel level, BlockPos.MutableBlockPos pos, int[][] shafts, int x, int z, int ground, int top) {
        for (int[] m : shafts) {
            if (x >= m[0] && x < m[0] + m[2] && z >= m[1] && z < m[1] + m[2]) {
                // the shaft, straight down to the rock it has bored through to
                for (int h = ground + 1; h <= top; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                return true;
            }
            if (x == m[3] && z == m[4]) {
                // the notch at its lip the meltwater runs in by, frozen up until summer
                Terrain.set(level, pos, x, top, z, Blocks.AIR.defaultBlockState());
                Terrain.set(level, pos, x, top - 1, z, Blocks.ICE.defaultBlockState());
                return true;
            }
            if (x >= m[0] - 1 && x <= m[0] + m[2] && z >= m[1] - 1 && z <= m[1] + m[2]) {
                // its walls, scoured clean to blue ice
                for (int h = ground + 1; h < top; h++) Terrain.set(level, pos, x, h, z, Blocks.BLUE_ICE.defaultBlockState());
                return false;
            }
        }
        return false;
    }

    /** Meltwater pours into the moulins in summer; the rest of the year their lips are frozen up. */
    private void moulinWater(ServerLevel level, boolean summer) {
        int[][] shafts = moulinList();
        if (shafts.length == 0) return;
        int[] m = shafts[level.random.nextInt(shafts.length)];
        BlockPos column = new BlockPos(m[3], 0, m[4]);
        if (!level.isLoaded(column)) return;
        BlockPos lip = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column).below();
        // the shaft column beside the lip
        int sx = Mth.clamp(m[3], m[0], m[0] + m[2] - 1), sz = Mth.clamp(m[4], m[1], m[1] + m[2] - 1);
        BlockPos shaft = new BlockPos(sx, lip.getY(), sz);
        BlockState state = level.getBlockState(lip);
        if (summer && state.is(Blocks.ICE) && level.getBlockState(shaft).isAir()) {
            // only if the water can go nowhere but down the shaft
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos side = lip.relative(d);
                if (!side.equals(shaft) && !level.getBlockState(side).isFaceSturdy(level, side, d.getOpposite())) return;
            }
            level.setBlock(lip, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        } else if (!summer && state.is(Blocks.WATER) && level.getFluidState(lip).isSource()) {
            level.setBlock(lip, Blocks.ICE.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * The terminal moraine: a horseshoe-shaped ridge of rubble round the glacier's foot, bulldozed up by the ice
     * when it reached a little further than it does now.
     */
    private void moraine(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, float[] l) {
        float half = length / 2.0F;
        float mu = l[0] - (half + 3.5F), side = halfWidth(half) + 6.0F;
        float lateral = Math.abs(l[1]) - (halfWidth(l[0]) + 2.5F);
        float rise;
        if (Math.abs(mu) < 4.5F && Math.abs(l[1]) < side) {
            float p = Mth.cos(mu / 4.5F * Mth.HALF_PI);
            rise = 3.5F * p * p * Terrain.smooth((side - Math.abs(l[1])) / 5.0F);
        } else if (l[0] > half * 0.2F && l[0] < half + 2.0F && Math.abs(lateral) < 2.5F) {
            // and lower ridges along its sides
            float p = Mth.cos(lateral / 2.5F * Mth.HALF_PI);
            rise = 2.0F * p * p * Terrain.smooth((l[0] - half * 0.2F) / (half * 0.3F));
        } else {
            return;
        }
        int r = Math.round(rise * (0.7F + 0.6F * Terrain.field(seed, x, z, 6.0)));
        if (r < 1) return;
        int ground = Terrain.ground(level, pos, x, z);
        Terrain.clearAbove(level, pos, x, ground, z, 8);
        for (int h = ground + 1; h <= ground + r; h++) {
            float n = Terrain.noise(seed, x, h, z);
            Terrain.set(level, pos, x, h, z, n < 0.45F ? Blocks.GRAVEL.defaultBlockState() : n < 0.8F ? Blocks.COBBLESTONE.defaultBlockState()
                    : n < 0.93F ? Blocks.STONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState());
        }
    }

    @Override
    public void tick(ServerLevel level, ServerPlayer player) {
        // fallen into a crevasse? (well below the ice on every side)
        int px = player.getBlockX(), pz = player.getBlockZ();
        if (thicknessAt(px + 0.5, pz + 0.5) >= 3.0F) {
            int around = Integer.MIN_VALUE;
            for (int[] d : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
                around = Math.max(around, level.getHeight(Heightmap.Types.MOTION_BLOCKING, px + d[0], pz + d[1]));
            }
            if (around - player.getY() >= 3.5 && player.onGround()) {
                WeatherAdvancements.award(player, "mind_the_gap");
            }
        }
        calve(level);
        if (moulins && WeatherConfig.CHANGING_LANDFORMS.get()) {
            moulinWater(level, WeatherManager.seasonInfo().season() == Season.SUMMER);
        }
        if (!WeatherConfig.CHANGING_LANDFORMS.get() || WeatherManager.seasonInfo().season() != Season.SUMMER) {
            return;
        }
        // summer: the lower end melts back, and meltwater runs off it
        RandomSource random = level.random;
        float half = length / 2.0F;
        for (int i = 0; i < 6; i++) {
            float u = half - random.nextFloat() * length * 0.25F, v = (random.nextFloat() * 2.0F - 1.0F) * halfWidth(u);
            double c = Mth.cos(angle), s = Mth.sin(angle);
            int x = Mth.floor(cx + u * c - v * s), z = Mth.floor(cz + u * s + v * c);
            BlockPos column = new BlockPos(x, 0, z);
            if (!level.isLoaded(column)) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column).below();
            BlockState state = level.getBlockState(top);
            boolean ice = state.is(Blocks.PACKED_ICE) || state.is(Blocks.ICE) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SNOW);
            // never melt right down to the rock: keep two blocks of ice
            if (ice && isIce(level.getBlockState(top.below())) && isIce(level.getBlockState(top.below(2)))) {
                boolean snout = u > half - length * 0.08F;
                level.setBlock(top, snout && random.nextInt(4) == 0 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        if (tables) settleTable(level);
    }

    /** A glacier that ends in the sea (or a lake) breaks off icebergs at its front now and then. */
    private void calve(ServerLevel level) {
        if (!WeatherConfig.ICEBERGS.get() || level.random.nextInt(150) != 0) return;
        float half = length / 2.0F;
        for (int attempt = 0; attempt < 6; attempt++) {
            float u = half + 2.0F + level.random.nextFloat() * 6.0F, v = (level.random.nextFloat() * 2.0F - 1.0F) * halfWidth(half);
            double c = Mth.cos(angle), s = Mth.sin(angle);
            BlockPos column = BlockPos.containing(cx + u * c - v * s, 0, cz + u * s + v * c);
            if (!level.isLoaded(column)) continue;
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column).below();
            if (!level.getFluidState(surface).is(net.minecraft.tags.FluidTags.WATER) || !level.getFluidState(surface.below(3)).is(net.minecraft.tags.FluidTags.WATER)) continue;
            com.reiko.weather.entity.IceFloeEntity.spawnBerg(level, surface, 1.6F + level.random.nextFloat() * 1.6F);
            level.playSound(null, surface, net.minecraft.sounds.SoundEvents.GENERIC_SPLASH, net.minecraft.sounds.SoundSource.AMBIENT, 4.0F, 0.4F);
            level.playSound(null, surface, com.reiko.weather.registry.ModSounds.AVALANCHE.get(), net.minecraft.sounds.SoundSource.AMBIENT, 3.0F, 0.8F);
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(surface.getX(), surface.getY(), surface.getZ()) < 128 * 128) {
                    p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.reikos_weather.calving")
                            .withStyle(st -> st.withColor(0xBFE6FF)), true);
                }
            }
            return;
        }
    }

    private static boolean isIce(BlockState state) {
        return state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) || state.is(Blocks.ICE) || state.is(Blocks.SNOW_BLOCK);
    }
}
