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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * A landslide lake: a mountain valley dammed by the rubble of a great landslide, like Lake Sarez in the Pamirs (held back
 * by a slide an earthquake shook down in 1911) or Quake Lake in Montana. A whole mountainside let go at once and poured
 * across the valley floor, heaping up a hummocky dam of shattered rock, boulders and broken trees from one side to the
 * other. The river, with nowhere left to go, rose behind it into a long, deep lake that drowned the woods along the old
 * valley floor; the dead trunks, stripped bare, still stand up out of the water. High on the valley side the scar it tore
 * away from is still bare rock.
 *
 * @param angle  the way the valley runs (downstream)
 * @param floor  the old valley floor where the dam lies across it
 * @param crest  the dam's crest
 * @param water  the lake's level, a block or two below the crest
 * @param length how far up the valley from the dam the lake's basin reaches
 * @param width  half the width of the lake's basin at the dam
 * @param side   which side of the valley the slide came down (+1 or -1 across it)
 * @param scar   how far up the valley side the scar runs
 * @param low    the lowest ground the dam reaches down the valley (for the bounds)
 * @param high   the highest ground about (for the bounds)
 */
public record LandslideLake(int cx, int cz, float angle, int floor, int crest, int water, int length, int width, int side, int scar, int low, int high,
                            long seed) implements Landform {
    /** How far out the lake's banks reach (as a multiple of the basin's size), and the part of them always built up over the water. */
    private static final float RING = 1.3F, BANK = 1.15F;
    /** How wide the dam's crest is, and how far down the valley the rubble runs. */
    private static final int CREST = 5, DOWN = 30;
    /** How many 4-block steps up and across the valley the plan looks behind the dam, and the least lake (in blocks of its surface) worth a dam. */
    private static final int GRID = 13, LAKE = 224;
    /** How many 8-block steps across the plan looks for a valley to dam, and the ways a valley can run across them. */
    private static final int SURVEY = 13;
    /** How far a valley's sides must stand above its floor for a dam across it to hold a lake. */
    private static final int DEEP = 6;
    private static final int[][] AXES = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

    @Nullable
    static Landform plan(PlanContext c) {
        // the lie of the land round about, every 8 blocks out to 48 each way
        int mid = SURVEY / 2;
        int[][] g = new int[SURVEY][SURVEY];
        for (int i = 0; i < SURVEY; i++) {
            for (int j = 0; j < SURVEY; j++) g[i][j] = c.groundAt(c.x() + 8 * (i - mid), c.z() + 8 * (j - mid));
        }
        // the likeliest places for the dam: on a valley's floor, with its sides standing well above it both ways across,
        // best where the valley is deepest and its floor climbs most gently behind the dam (where the lake will reach furthest)
        List<int[]> sites = new ArrayList<>();
        for (int i = 2; i < SURVEY - 2; i++) {
            for (int j = 2; j < SURVEY - 2; j++) {
                int floor = g[i][j];
                if (Mth.square(i - mid) + Mth.square(j - mid) > 16 || floor < c.seaLevel() + 4 || !c.fits(floor, 30, 50)) continue;
                int best = Integer.MIN_VALUE;
                for (int[] s : AXES) {
                    // (its sides 16 blocks out either way, or for a broader valley, 24)
                    int sides = Math.min(g[i - 2 * s[1]][j + 2 * s[0]], g[i + 2 * s[1]][j - 2 * s[0]]);
                    if (i >= 3 && j >= 3 && i < SURVEY - 3 && j < SURVEY - 3) sides = Math.max(sides, Math.min(g[i - 3 * s[1]][j + 3 * s[0]], g[i + 3 * s[1]][j - 3 * s[0]]));
                    int up = Math.max(g[i + 2 * s[0]][j + 2 * s[1]], g[i - 2 * s[0]][j - 2 * s[1]]);
                    if (sides - floor >= DEEP && up < sides) best = Math.max(best, Math.min(sides - floor, 16) - Math.max(0, up - floor) / 2);
                }
                if (best > Integer.MIN_VALUE) sites.add(new int[]{best, i, j});
            }
        }
        sites.sort((p, q) -> q[0] != p[0] ? q[0] - p[0] : p[1] != q[1] ? p[1] - q[1] : p[2] - q[2]);
        for (int k = 0; k < Math.min(3, sites.size()); k++) {
            int[] s = sites.get(k);
            Landform lake = dam(c, c.x() + 8 * (s[1] - mid), c.z() + 8 * (s[2] - mid));
            if (lake != null) return lake;
        }
        return null;
    }

    /** A landslide lake with its dam across the valley at a spot (or none, if the valley there won't hold one). */
    @Nullable
    private static Landform dam(PlanContext c, int dx, int dz) {
        Landform lake = dam(c, dx, dz, 16);
        return lake != null ? lake : dam(c, dx, dz, 24);
    }

    /** The same, looking for the valley's sides {@code r} blocks out from the spot. */
    @Nullable
    private static Landform dam(PlanContext c, int dx, int dz, int r) {
        int floor = c.groundAt(dx, dz);
        // the ground all round
        int[] ring = new int[12];
        for (int i = 0; i < 12; i++) {
            float t = i * (float) Math.PI * 2.0F / 12.0F;
            ring[i] = c.groundAt(dx + (int) Math.round(Math.cos(t) * r), dz + (int) Math.round(Math.sin(t) * r));
        }
        // the valley runs along the lowest line through here, and both its sides should stand well above its floor
        int axis = 0;
        for (int i = 1; i < 6; i++) {
            if (Math.max(ring[i], ring[i + 6]) < Math.max(ring[axis], ring[axis + 6])) axis = i;
        }
        // (downstream is the lower end, and "left" is the side across from it a quarter turn round)
        int along = ring[axis] <= ring[axis + 6] ? axis : axis + 6;
        int left = ring[(along + 3) % 12], right = ring[(along + 9) % 12], fall = Math.abs(ring[axis] - ring[axis + 6]);
        float a = along * Mth.TWO_PI / 12.0F;
        if (Math.min(left, right) < floor + DEEP || fall < 1) {
            return null; // on a valley's side, or in a basin
        }
        PlanContext d = c.at(dx, dz);
        // the valley behind where the dam will lie, every 4 blocks up it and across it
        int[][] behind = new int[GRID][GRID];
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) behind[i][j] = at(d, a, -4 * i, 4 * (j - GRID / 2));
        }
        // the dam, and the lake a block or two below its crest (and below the valley sides)
        int sides = Math.min(left, right) - 2;
        int water = Math.min(floor + 6 + c.random().nextInt(7) - c.random().nextInt(2), sides);
        int over = 1 + c.random().nextInt(2), length, width, crest;
        // where the valley floor climbs steeply behind the dam, the dam has to stand higher before the river drowns any
        // real stretch of it (it can't stand higher than the valley's sides, though, or the lake would find a way round)
        for (int tries = 0; ; tries++) {
            // the lake reaches up the valley until the floor rises out of it
            length = 0;
            for (int i = 4; i < GRID; i++) {
                if (behind[i][GRID / 2] > water) {
                    length = 4 * i;
                    break;
                }
            }
            if (length == 0) {
                water = Math.min(water, behind[GRID - 1][GRID / 2] - 1);
                length = 4 * (GRID - 1);
            }
            crest = water + over;
            // how wide the valley is at the dam: where its sides rise above the crest
            width = 0;
            for (int s = -1; s <= 1; s += 2) {
                int far = s > 0 ? left : right;
                width = Math.max(width, at(d, a, 0, 10 * s) >= crest ? 10 : far >= crest ? 16 : 22);
            }
            width += 2;
            if (water - floor >= 4 && water >= c.seaLevel() + 1 && flooded(behind, water, length, width) >= LAKE) break;
            if (water >= sides || tries >= 8) return null;
            water++;
        }
        // the slide came down the higher side
        int side = left >= right ? 1 : -1, scar = 12 + c.random().nextInt(9);
        int head = at(d, a, 2, side * (width * 1.45F + 3.0F + scar));
        // (and the bounds reach down as far as the valley falls away under the rubble below the dam)
        int below = Math.min(at(d, a, DOWN * 0.5F, 0), at(d, a, DOWN, 0));
        int low = Math.min(Math.min(floor - fall * 2 - 6, water - 17), below - 6);
        int high = Math.max(Math.max(left, right), Math.max(head, crest + 6));
        return new LandslideLake(dx, dz, a, floor, crest, water, length + 4, width, side, scar, low, high, c.random().nextLong());
    }

    /**
     * About how many blocks of the lake's surface would lie over drowned ground rather than ground standing out of it,
     * from the valley floor sampled every 4 blocks behind the dam.
     */
    private static int flooded(int[][] behind, int water, int length, int width) {
        int wet = 0;
        for (int i = 1; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                float along = 4.0F * i / length, across = 4.0F * (j - GRID / 2) / width;
                if (along * along + across * across < 0.8F && behind[i][j] < water) wet++;
            }
        }
        return wet * 16;
    }

    /** The ground {@code u} blocks down the valley and {@code v} across it from the dam. */
    private static int at(PlanContext c, float angle, float u, float v) {
        double[] p = Terrain.xz(u, v, c.x() + 0.5, c.z() + 0.5, angle);
        return c.groundAt(Mth.floor(p[0]), Mth.floor(p[1]));
    }

    static Landform load(CompoundTag tag) {
        return new LandslideLake(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("floor"), tag.getInt("crest"), tag.getInt("water"),
                tag.getInt("length"), tag.getInt("width"), tag.getInt("side"), tag.getInt("scar"), tag.getInt("low"), tag.getInt("high"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("floor", floor);
        tag.putInt("crest", crest);
        tag.putInt("water", water);
        tag.putInt("length", length);
        tag.putInt("width", width);
        tag.putInt("side", side);
        tag.putInt("scar", scar);
        tag.putInt("low", low);
        tag.putInt("high", high);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.LANDSLIDE_LAKE;
    }

    @Override
    public BoundingBox bounds() {
        float near = width * RING / 0.9F + 3.0F, us = scarU(), sh = scarHalf() / 0.9F + 2.0F, v0 = scarStart() - 2.0F, v1 = scarStart() + scar / 0.9F + 2.0F;
        // the basin and its banks, the dam, and the scar
        float[][] boxes = {{-(length * RING / 0.9F + 2.0F), 0.0F, -near, near}, {0.0F, DOWN + 2.0F, -(width * RING + 4.0F), width * RING + 4.0F},
                {us - sh, us + sh, side > 0 ? v0 : -v1, side > 0 ? v1 : -v0}};
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (float[] box : boxes) {
            for (int k = 0; k < 4; k++) {
                double[] p = Terrain.xz(box[k & 1], box[2 + (k >> 1)], cx + 0.5, cz + 0.5, angle);
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minZ = Math.min(minZ, p[1]);
                maxZ = Math.max(maxZ, p[1]);
            }
        }
        return new BoundingBox(Mth.floor(minX) - 1, low, Mth.floor(minZ) - 1, Mth.floor(maxX) + 1, high + 34, Mth.floor(maxZ) + 1);
    }

    private float[] uv(int x, int z) {
        return Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
    }

    /** How far out from the lake's basin a column is (under 1 inside it): widest at the dam, narrowing up the valley. */
    private float out(int x, int z, float u, float v) {
        float along = Math.max(0.0F, -u) / length;
        return Mth.sqrt(along * along + Mth.square(v / width)) / (0.9F + 0.2F * Terrain.field(seed, x, z, 9.0));
    }

    private boolean inDam(float u, float v) {
        return u >= 0.0F && u < DOWN && Math.abs(v) < width * RING + 2.0F;
    }

    /** Where the scar starts, across the valley from its middle (on the slide's side), clear of the lake's banks. */
    private float scarStart() {
        return width * 1.45F + 3.0F;
    }

    /** How far into the scar a column is (under 1 inside it), and whether it's in the upper half, where the headscarp is. */
    private float scarAt(int x, int z, float u, float v) {
        float vs = side * v - scarStart() - scar * 0.5F;
        return Mth.sqrt(Mth.square((u - scarU()) / scarHalf()) + Mth.square(vs / (scar * 0.5F))) / (0.9F + 0.2F * Terrain.field(seed + 2, x, z, 5.0));
    }

    /** Where the scar's middle is, down the valley from the dam's crest, and half its width. */
    private float scarU() {
        return 2.0F + (Terrain.hash(seed, 1) - 0.5F) * 6.0F;
    }

    private float scarHalf() {
        return 7.0F + 4.0F * Terrain.hash(seed, 2);
    }

    /** Where the slide ran down the valley side from the scar to the valley floor. */
    private boolean track(int x, int z, float u, float v) {
        float vs = side * v;
        return vs > width * 0.8F && vs < scarStart() + 2.0F && Math.abs(u - scarU()) < scarHalf() * (0.7F + 0.3F * Terrain.field(seed + 4, x, z, 5.0));
    }

    private static boolean skipped(int ground, BoundingBox b) {
        return ground >= b.maxY() - 4 || ground <= b.minY() + 1;
    }

    private boolean scarLeft(WorldGenLevel level, int x, int z, BoundingBox b) {
        float[] l = uv(x, z);
        float u = l[0], v = l[1];
        if (inDam(u, v) || u < 0.0F && out(x, z, u, v) < RING || scarAt(x, z, u, v) >= 1.0F) return false;
        return skipped(Goosenecks.ground(level, new BlockPos.MutableBlockPos(), x, z), b);
    }

    /** Whether a column is one the landform may lower or drain (where it only roughens the surface, it doesn't count). */
    private boolean inside(int x, int z) {
        float[] l = uv(x, z);
        float u = l[0], v = l[1];
        return inDam(u, v) || u < 0.0F && out(x, z, u, v) < RING || scarAt(x, z, u, v) < 1.0F;
    }

    /**
     * Whether the lake's water can stand in a column of its basin: only where every column beside it is basin too, or is
     * certain to be built up over the water (the inner part of the banks, or the dam's crest).
     */
    private boolean held(int x, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int nx = x + d.getStepX(), nz = z + d.getStepZ();
            float[] l = uv(nx, nz);
            if (l[0] >= 0.0F) {
                if (l[0] < CREST && Math.abs(l[1]) < width * RING) continue;
                return false;
            }
            if (out(nx, nz, l[0], l[1]) >= BANK) return false;
        }
        return true;
    }

    /** The top of the rubble heaped across the valley: a level crest, a steep face into the lake, and a long, hummocky slope below. */
    private float rubble(int x, int z, float u, float v) {
        float top, hum = 2.6F * (Terrain.field(seed + 1, x, z, 4.0) - 0.5F) + 1.2F * (Terrain.field(seed + 3, x, z, 2.0) - 0.5F);
        if (u < 0.0F) {
            top = crest + 0.5F * hum + 1.2F * u;
        } else if (u < CREST) {
            top = crest + Math.max(0.0F, hum); // (the crest is never lower than the dam's crest, so the lake can't find a way over)
        } else {
            top = crest + hum - (u - CREST) * 0.45F;
        }
        float over = Math.abs(v) - width * RING;
        return over > 0.0F ? top - over * 3.0F : top;
    }

    /** Boulders standing out of the rubble: {u, v, radius, height}. */
    private float[][] boulders() {
        int count = 8 + Math.round(Terrain.hash(seed, 3) * 6.0F);
        float[][] list = new float[count][];
        for (int i = 0; i < count; i++) {
            float u = -1.0F + Terrain.hash(seed, 10 + i) * DOWN * 0.7F, v = (Terrain.hash(seed, 30 + i) - 0.5F) * width * 2.0F;
            list[i] = new float[]{u, v, 1.2F + 1.4F * Terrain.hash(seed, 50 + i), 1.0F + 2.5F * Terrain.hash(seed, 70 + i)};
        }
        return list;
    }

    /** Trees carried down in the slide, lying broken on the rubble: {x, z, length, along x (1) or z (0)}. */
    private int[][] logs() {
        int count = 5 + Math.round(Terrain.hash(seed, 4) * 4.0F);
        int[][] logs = new int[count][];
        for (int i = 0; i < count; i++) {
            float u = CREST + Terrain.hash(seed, 90 + i) * DOWN * 0.6F, v = (Terrain.hash(seed, 110 + i) - 0.5F) * width * 1.6F;
            double[] p = Terrain.xz(u, v, cx + 0.5, cz + 0.5, angle);
            logs[i] = new int[]{Mth.floor(p[0]), Mth.floor(p[1]), 2 + Math.round(Terrain.hash(seed, 130 + i) * 3.0F), Terrain.hash(seed, 150 + i) < 0.5F ? 1 : 0};
        }
        return logs;
    }

    private static Block wood(Holder<Biome> biome, boolean stripped, float n) {
        if (biome.is(BiomeTags.IS_TAIGA) || n < 0.45F) return stripped ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.SPRUCE_LOG;
        if (biome.is(BiomeTags.IS_JUNGLE)) return stripped ? Blocks.STRIPPED_JUNGLE_LOG : Blocks.JUNGLE_LOG;
        if (n > 0.8F) return stripped ? Blocks.STRIPPED_BIRCH_LOG : Blocks.BIRCH_LOG;
        return stripped ? Blocks.STRIPPED_OAK_LOG : Blocks.OAK_LOG;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // (a column of the scar left alone, far above or below where it was planned, keeps any pond in it)
        BiPredicate<Integer, Integer> outside = (nx, nz) -> !inside(nx, nz) || scarLeft(level, nx, nz, b);
        float[][] boulders = boulders();
        int[][] logs = logs();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = uv(x, z);
                float u = l[0], v = l[1], e = out(x, z, u, v);
                boolean dam = inDam(u, v), lake = u < 0.0F && e < 1.0F, bank = u < 0.0F && !lake && e < RING;
                float s = !dam && !lake && !bank ? scarAt(x, z, u, v) : 2.0F;
                boolean track = !dam && !lake && !bank && s >= 1.0F && track(x, z, u, v);
                if (!dam && !lake && !bank && s >= 1.0F && !track) continue;
                int ground = Goosenecks.ground(level, pos, x, z);
                int standing = Math.min(b.maxY(), Goosenecks.standing(level, pos, x, ground, z));
                pos.set(x, ground, z);
                Holder<Biome> biome = level.getBiome(pos);
                BlockState was = level.getBlockState(pos);
                boolean grassy = was.is(Blocks.GRASS_BLOCK) || was.is(Blocks.PODZOL) || was.is(Blocks.MYCELIUM);
                float n = Terrain.noise(seed, x, water, z);
                int clear = Math.max(0, Math.min(30, b.maxY() - Math.max(ground, standing)));
                if (s < 1.0F) {
                    if (!skipped(ground, b)) scar(level, pos, x, z, v, s, ground, standing, clear, n, outside);
                    continue;
                }
                if (track) {
                    // the path the slide took down the valley side, stripped of its trees and strewn with rubble
                    if (ground > b.maxY() - 4 || ground < b.minY() + 1) continue;
                    Terrain.clearAbove(level, pos, x, Math.max(ground, standing), z, clear);
                    BlockState rough = debris(x, ground, z, biome, true, grassy);
                    if (rough.is(Blocks.GRAVEL) && !level.getBlockState(pos.set(x, ground - 1, z)).blocksMotion()) rough = Blocks.COBBLESTONE.defaultBlockState();
                    Terrain.set(level, pos, x, ground, z, rough);
                    if (n > 0.9F && standing == ground) Terrain.set(level, pos, x, ground + 1, z, boulder(x, ground + 1, z, true));
                    continue;
                }
                // the ground is made sound as deep as the lake goes, with no hollow in it for the water to find
                for (int h = Math.max(b.minY(), water - 14); h <= Math.min(ground, water + 1); h++) {
                    BlockState here = level.getBlockState(pos.set(x, h, z));
                    if (here.isAir() || !here.getFluidState().isEmpty()) Terrain.set(level, pos, x, h, z, Blocks.DIRT.defaultBlockState());
                }
                int top = ground;
                boolean rubble = false;
                if (dam) {
                    float fill = Math.max(0.0F, rubble(x, z, u, v) - ground) * Terrain.smooth((DOWN - u) / 8.0F);
                    top = ground + Math.round(fill);
                    rubble = top > ground;
                } else if (lake && !held(x, z)) {
                    top = Math.max(ground, water + 1); // the edge of the basin, where it isn't held in, is banked up
                } else if (lake) {
                    int face = Math.round(rubble(x, z, u, v));
                    if (face > ground) {
                        top = face; // the dam's face running down under the water
                        rubble = true;
                    }
                } else {
                    float t = (e - BANK) / (RING - BANK);
                    top = Math.max(ground, t < 0.0F ? water + 1 : water + 1 - Math.round(t * 3.0F));
                }
                int bump = 0;
                if (dam || rubble) {
                    for (float[] o : boulders) {
                        float d = Mth.sqrt(Mth.square(u - o[0]) + Mth.square(v - o[1])) / o[2];
                        if (d < 1.0F) bump = Math.max(bump, Math.round(o[3] * Mth.sqrt(1.0F - d * d)));
                    }
                }
                boolean wet = lake && held(x, z) && top + bump < water;
                if (wet) bump = 0;
                int solid = top + bump, bed = wet ? Math.max(top, water - 14) : solid;
                int open = wet ? water : solid;
                if (solid == ground && !wet && standing == ground) {
                    // where the rubble didn't pile up above the valley side, the slide still scoured it on its way down
                    if (dam && track(x, z, u, v) && ground <= b.maxY() - 4) {
                        Terrain.clearAbove(level, pos, x, ground, z, clear);
                        BlockState rough = debris(x, ground, z, biome, true, grassy);
                        if (!rough.is(Blocks.GRAVEL) || level.getBlockState(pos.set(x, ground - 1, z)).blocksMotion()) Terrain.set(level, pos, x, ground, z, rough);
                    }
                    continue;
                }
                Terrain.clearAbove(level, pos, x, Math.max(ground, standing), z, clear);
                for (int h = open + 1; h <= standing; h++) {
                    // (a pond just outside keeps a bank of earth between it and the lowered ground)
                    Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, outside) ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
                for (int h = Math.max(b.minY(), ground + 1); h <= Math.min(bed, b.maxY()); h++) {
                    BlockState fill;
                    if (h > top && !wet) fill = boulder(x, h, z, h == bed);
                    else if (rubble) fill = debris(x, h, z, biome, h == top && !wet, grassy);
                    else fill = h == bed && !wet && grassy ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                    // (where the ground lies below the bounds, nothing loose at the bottom of the heap)
                    if (h == b.minY() && h > ground + 1 && fill.is(Blocks.GRAVEL)) fill = Blocks.STONE.defaultBlockState();
                    Terrain.set(level, pos, x, h, z, fill);
                }
                if (wet) {
                    lakeBed(level, pos, x, z, ground, top, bed, rubble, biome, n);
                    continue;
                }
                if (!rubble && top > ground) {
                    shore(level, pos, x, top, z, n, was);
                    continue;
                }
                if (rubble && bump == 0 && solid >= b.minY()) litter(level, pos, x, z, solid, biome, logs, n);
            }
        }
    }

    /** The lake's bed: drowned soil (or the dam's rubble), and the bare trunks of the drowned woods standing up out of the water. */
    private void lakeBed(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, int top, int bed, boolean rubble,
                         Holder<Biome> biome, float n) {
        if (!rubble) {
            float m = Terrain.noise(seed + 5, x, bed, z);
            Terrain.set(level, pos, x, bed - 1, z, Blocks.DIRT.defaultBlockState());
            Terrain.set(level, pos, x, bed, z, m < 0.35F ? Blocks.MUD.defaultBlockState() : m < 0.6F ? Blocks.GRAVEL.defaultBlockState()
                    : m < 0.8F ? Blocks.DIRT.defaultBlockState() : Blocks.CLAY.defaultBlockState());
        }
        for (int h = bed + 1; h <= water; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
        if (!rubble && water - bed >= 2 && drowned(x, z)) {
            Block trunk = wood(biome, true, Terrain.noise(seed + 6, x, 0, z));
            int tall = water + 1 + Math.round(Terrain.noise(seed + 7, x, 0, z) * 5.0F);
            for (int h = bed + 1; h <= tall; h++) Terrain.set(level, pos, x, h, z, trunk.defaultBlockState());
        }
    }

    /** Whether a drowned tree stands at a column: at most one to each 5-block square. */
    private boolean drowned(int x, int z) {
        int i = Math.floorDiv(x, 5), j = Math.floorDiv(z, 5);
        if (x != i * 5 + (int) (Terrain.noise(seed + 8, i, 0, j) * 4.99F) || z != j * 5 + (int) (Terrain.noise(seed + 9, i, 0, j) * 4.99F)) return false;
        return Terrain.noise(seed + 10, i, 0, j) < 0.55F;
    }

    /** The landslide's rubble: shattered rock, boulders, gravel and earth, with broken wood buried in it here and there. */
    private BlockState debris(int x, int h, int z, Holder<Biome> biome, boolean surface, boolean grassy) {
        float n = Terrain.noise(seed + 11, x, h, z);
        if (surface) {
            // the slide is old: moss on the stones, and turf creeping back over the earth
            return n < 0.22F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : n < 0.4F ? Blocks.COARSE_DIRT.defaultBlockState()
                    : n < 0.58F && grassy ? Blocks.GRASS_BLOCK.defaultBlockState() : n < 0.68F ? Blocks.STONE.defaultBlockState()
                    : n < 0.78F ? Blocks.ANDESITE.defaultBlockState() : n < 0.88F ? Blocks.GRAVEL.defaultBlockState()
                    : n < 0.95F ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
        }
        if (n < 0.03F) {
            Direction.Axis axis = Direction.Axis.values()[Math.floorMod(x * 7 + h * 3 + z, 3)];
            return wood(biome, false, Terrain.noise(seed + 12, x, h, z)).defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
        }
        return n < 0.22F ? Blocks.COBBLESTONE.defaultBlockState() : n < 0.32F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                : n < 0.5F ? Blocks.STONE.defaultBlockState() : n < 0.64F ? Blocks.ANDESITE.defaultBlockState()
                : n < 0.76F ? Blocks.GRAVEL.defaultBlockState() : n < 0.9F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState();
    }

    private BlockState boulder(int x, int h, int z, boolean top) {
        float n = Terrain.noise(seed + 13, x, h, z);
        if (top && n < 0.45F) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        return n < 0.4F ? Blocks.STONE.defaultBlockState() : n < 0.75F ? Blocks.ANDESITE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
    }

    /** The banks built up round the lake where the ground dipped: the same turf (or sand, or snow) as round about, and a little gravel. */
    private void shore(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int top, int z, float n, BlockState was) {
        boolean natural = was.is(BlockTags.DIRT) || was.is(BlockTags.SAND) || was.is(Blocks.SNOW_BLOCK);
        BlockState surface = n < 0.15F ? Blocks.GRAVEL.defaultBlockState() : natural ? was : Blocks.COARSE_DIRT.defaultBlockState();
        Terrain.set(level, pos, x, top, z, surface);
        if (surface.is(Blocks.GRASS_BLOCK) && n > 0.85F) Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
    }

    /** What lies on the rubble: trees carried down in the slide and left lying where they fell, broken stumps, and grass. */
    private void litter(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int top, Holder<Biome> biome, int[][] logs, float n) {
        for (int[] log : logs) {
            boolean alongX = log[3] == 1;
            int k = alongX ? x - log[0] : z - log[1];
            if ((alongX ? z != log[1] : x != log[0]) || k < 0 || k >= log[2]) continue;
            Block w = wood(biome, false, Terrain.hash(seed, 170 + log[0] * 31 + log[1]));
            Terrain.set(level, pos, x, top + 1, z, w.defaultBlockState().setValue(RotatedPillarBlock.AXIS, alongX ? Direction.Axis.X : Direction.Axis.Z));
            return;
        }
        BlockState ground = level.getBlockState(pos.set(x, top, z));
        float m = Terrain.noise(seed + 14, x, top, z);
        if (m > 0.985F) {
            // the stump of a trunk snapped off as it was carried down
            int tall = 1 + Math.round(Terrain.noise(seed + 15, x, top, z));
            for (int h = 1; h <= tall; h++) Terrain.set(level, pos, x, top + h, z, wood(biome, false, n).defaultBlockState());
        } else if (ground.is(BlockTags.DIRT) && m < 0.2F) {
            Terrain.set(level, pos, x, top + 1, z, m < 0.06F ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
        }
    }

    /**
     * The scar high on the valley side that the slide tore away from: a spoon-shaped hollow of bare, broken rock, cut
     * deepest under the steep headscarp at its top.
     */
    private void scar(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, float v, float s, int ground, int standing, int clear,
                      float n, BiPredicate<Integer, Integer> outside) {
        float up = Mth.clamp((side * v - scarStart()) / scar, 0.0F, 1.0F);
        float shape = up > 0.5F ? (float) Math.pow(Math.max(0.0F, 1.0F - s * s), 0.3) : 1.0F - s * s;
        int top = Math.max(ground - Math.round(6.0F * shape * (0.45F + 0.55F * up)), Math.min(ground, floor + 1));
        Terrain.clearAbove(level, pos, x, Math.max(ground, standing), z, clear);
        for (int h = top + 1; h <= standing; h++) {
            Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, outside) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        Terrain.set(level, pos, x, top - 1, z, Blocks.STONE.defaultBlockState());
        BlockState bare = n < 0.45F ? Blocks.STONE.defaultBlockState() : n < 0.7F ? Blocks.ANDESITE.defaultBlockState()
                : n < 0.82F ? Blocks.GRAVEL.defaultBlockState() : n < 0.94F ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
        Terrain.set(level, pos, x, top, z, bare);
        if (n > 0.97F && level.getBlockState(pos.set(x, top + 1, z)).isAir()) {
            Terrain.set(level, pos, x, top + 1, z, Blocks.COBBLESTONE.defaultBlockState()); // a block fallen from the headscarp
        }
    }
}
