package com.reiko.weather.landform;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModBlocks;
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
 * bare lower down. Crevasses cut across it, many of them hidden under a bridge of powder snow; up in the snow, some
 * are hidden altogether, roofed over by wind-packed snow bridges that look no different from the snow round them, and
 * give way under the unwary. Where its bed steepens, the ice is stretched until it cracks apart into an icefall, a chaos
 * of toppling ice towers (seracs) and gaping crevasses. Below an icefall, the bare ice of the tongue is striped across
 * with ogives (Forbes bands): arcs of darker and paler ice, one pair for each year's flow through the icefall (ice that
 * came through in summer, dusty and dense, and ice that came through in winter, choked with snow), each arc bowed
 * downstream because the middle of the glacier flows faster than its sides, and fading as they near the snout.
 * Lower down, meltwater streams on its surface have drilled moulins, shafts straight down through the ice to the rock.
 * Here and there on the melting lower half stand glacier tables: boulders that fell onto
 * the ice from the valley sides, each perched on a pillar of ice. The rock shades the ice under it from the sun, so as
 * the surface melts down all round, the ice beneath the boulder is left standing. In the middle and lower reaches the
 * meltwater gathers in hollows of the ice as supraglacial lakes, startlingly blue against the white (clean ice under clear
 * water shows off the deep blue of the water itself), and runs into them along little channels it has melted down into
 * the surface. In summer the lower end melts back a little, meltwater runs off it, and pours roaring down the moulins, and
 * the tables' pedestals melt back with the ice round them, letting their boulders down.
 *
 * @param icefall    where along the glacier its icefall is ({@link #NO_ICEFALL} if it hasn't one, as on glaciers from
 *                   before there were any)
 * @param moulins    whether it has moulins (not on glaciers from before there were any)
 * @param tables     whether it has glacier tables (not on glaciers from before there were any)
 * @param lakes      whether it has meltwater lakes (not on glaciers from before there were any)
 * @param meltwater  the meltwater lakes and channels, as planned: the number of lakes; then for each lake {x, z, its
 *                   radius along the glacier and across it (in tenths of a block), its depth, its water level}; then for
 *                   each channel {which lake it runs into, the x and z of its head, its half-width (in tenths), its depth}
 * @param ogives     whether the tongue below its icefall is banded with ogives (not on glaciers from before there were any)
 * @param bridges    whether some of the crevasses up in the snow are hidden under snow bridges (not on glaciers from before
 *                   there were any, nor while snow bridges are turned off)
 */
public record Glacier(int cx, int cz, float angle, int length, int width, int thickness, int icefall, boolean moulins, boolean tables,
                      boolean lakes, int[] meltwater, boolean ogives, boolean bridges, long seed) implements Landform {
    public static final int NO_ICEFALL = Integer.MIN_VALUE;
    /** The most a table's pedestal stands above the ice round it before it melts down with it. */
    private static final int PEDESTAL = 5;
    /** How far round its water a meltwater lake's hollow reaches, and how far beyond that the moulins and tables keep clear of it. */
    private static final float BOWL = 3.0F, CLEAR = 1.5F;
    private static final int[] DRY = {0, 0, 0, 0, -1};

    public Glacier(int cx, int cz, float angle, int length, int width, int thickness, long seed) {
        this(cx, cz, angle, length, width, thickness, NO_ICEFALL, false, false, seed);
    }

    public Glacier(int cx, int cz, float angle, int length, int width, int thickness, int icefall, boolean moulins, boolean tables, long seed) {
        this(cx, cz, angle, length, width, thickness, icefall, moulins, tables, false, new int[0], seed);
    }

    public Glacier(int cx, int cz, float angle, int length, int width, int thickness, int icefall, boolean moulins, boolean tables,
                   boolean lakes, int[] meltwater, long seed) {
        this(cx, cz, angle, length, width, thickness, icefall, moulins, tables, lakes, meltwater, false, false, seed);
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
        Glacier glacier = new Glacier(c.x(), c.z(), best, length, width, thickness, icefall, true, true, seed);
        // ogives wherever there's an icefall with room enough below it for them; snow bridges unless they're turned off
        // (they're on while the config is still loading, during world creation)
        boolean ogives = icefall != NO_ICEFALL && icefall < length * 0.3F;
        boolean bridges = !WeatherConfig.SPEC.isLoaded() || WeatherConfig.SNOW_BRIDGES.get();
        return new Glacier(c.x(), c.z(), best, length, width, thickness, icefall, true, true, true, glacier.planMeltwater(c), ogives, bridges, seed);
    }

    static Landform load(CompoundTag tag) {
        return new Glacier(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("width"),
                tag.getInt("thick"), tag.contains("icefall") ? tag.getInt("icefall") : NO_ICEFALL, tag.getBoolean("moulins"), tag.getBoolean("tables"),
                tag.getBoolean("lakes"), tag.getIntArray("meltwater"), tag.getBoolean("ogives"), tag.getBoolean("bridges"), tag.getLong("seed"));
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
        tag.putBoolean("lakes", lakes);
        tag.put("meltwater", new net.minecraft.nbt.IntArrayTag(meltwater));
        tag.putBoolean("ogives", ogives);
        tag.putBoolean("bridges", bridges);
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
     * Which crevasses are hidden under snow bridges (by their number, as {@link #crevasse} gives it): of those lying wholly
     * up in the snow and clear of the icefall, a third to a half (one of two, one or two of three...), or a lone one half
     * the time.
     */
    private boolean[] bridged() {
        boolean[] out = new boolean[32];
        if (!bridges) return out;
        float half = length / 2.0F, spacing = 13.0F + 5.0F * Terrain.hash(seed, 7);
        int[] up = new int[out.length];
        int n = 0;
        for (int k = 0; k < out.length; k++) {
            float uk = -half + length * 0.15F + k * spacing;
            // (each wanders up to 2.9 blocks either side of its line)
            if (uk > length * 0.25F || uk + 2.9F >= 0.0F) break;
            if (icefall == NO_ICEFALL || Math.abs(uk - icefall) > 11.0F) up[n++] = k;
        }
        int m = n == 1 ? (Terrain.hash(seed, 200) < 0.5F ? 1 : 0) : n == 0 ? 0 : Math.max(1, Math.round(n * (0.33F + 0.17F * Terrain.hash(seed, 201))));
        // (the m of them that draw the lowest lots)
        for (int a = 0; a < n; a++) {
            int rank = 0;
            for (int b = 0; b < n; b++) {
                if (Terrain.hash(seed, 210 + up[b]) < Terrain.hash(seed, 210 + up[a])) rank++;
            }
            out[up[a]] = rank < m;
        }
        return out;
    }

    /**
     * The ogives at (u, v): how dark the ice is there, from 1 in the heart of a dark band to -1 in the heart of a light
     * one (0 where there are none: above the icefall, up in the snow, near the glacier's sides, or near its snout, where
     * they've faded out). They start just below the icefall, bowed a little; the further the ice carries them, the more
     * the faster flow down the glacier's middle bows them, and the closer they crowd as the ice slows toward the snout.
     */
    private float ogive(float u, float v, int x, int z) {
        if (!ogives || icefall == NO_ICEFALL) return 0.0F;
        float start = icefall + 10.0F, end = length / 2.0F - 1.0F;
        if (u < Math.max(start, 0.0F) || u > end) return 0.0F;
        float hw = halfWidth(u);
        // (the fastest ice needn't run right down the middle)
        float mid = hw * 0.2F * (Terrain.hash(seed, 301) - 0.5F), across = Math.min(1.0F, Math.abs(v - mid) / (hw + Math.abs(mid)));
        float run = u - start, t = run / Math.max(1.0F, end - start);
        float bow = Math.min(hw * 0.6F, 2.0F + 0.32F * run);
        float wave = (7.0F + 4.0F * Terrain.hash(seed, 300)) * (1.0F - 0.2F * t);
        float f = (run - bow * (1.0F - across * across)) / wave + 0.18F * (Terrain.field(seed + 500, x, z, 4.0) - 0.5F);
        float fade = Terrain.smooth(run / 4.0F) * (1.0F - Terrain.smooth((t - 0.45F) / 0.55F)) * Terrain.smooth((1.0F - across) / 0.25F);
        return Mth.cos((f - Mth.floor(f)) * Mth.TWO_PI) * fade;
    }

    /** The ice an ogive band shows at the surface, or null where it's just the glacier's own. */
    @Nullable
    private BlockState band(float ogive, int x, int y, int z) {
        if (ogive > 0.6F) return Blocks.BLUE_ICE.defaultBlockState();
        if (ogive > 0.35F) return Blocks.ICE.defaultBlockState();
        // (the light bands dusted white with the snow that was folded into them)
        if (ogive < -0.55F && Terrain.noise(seed + 2, x, y, z) < 0.8F) return Blocks.SNOW_BLOCK.defaultBlockState();
        return null;
    }

    /**
     * The moulins, {x, z, size, lip x, lip z}: each a shaft 1 or 2 blocks across, and the notch on one side of it
     * where the meltwater pours in. They're only where the ice is thick and whole, clear of crevasses, the ice cave,
     * the icefall and the meltwater lakes, and above the snout that melts back in summer.
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
                    ok = thicknessAt(x + dx + 0.5, z + dz + 0.5) >= 4.0F && crevasse(l[0], l[1]) < 0 && caveDistance(l[0], l[1]) > 4.5F && icefallAt(l[0]) == 0.0F
                            && meltAt(x + dx, z + dz)[0] == 0;
                }
            }
            if (ok) list.add(new int[]{x, z, size, lx, lz});
        }
        return list.toArray(new int[0][]);
    }

    /**
     * Plans the meltwater lakes: one to four, each in a hollow of thick ice in the middle or lower part of the glacier,
     * clear of the icefall and of each other, with its water a block under the lowest of the ice round it (or held in by a
     * low lip of ice on a slope) and clean ice under it; and a channel of meltwater leading into most of them. (Crevasses
     * running into a lake stop short of it, the moulins and tables keep out of its way, and the ice cave keeps below it.)
     * Laid out as {@link #meltwater}.
     */
    private int[] planMeltwater(PlanContext c) {
        RandomSource random = c.random();
        int want = 1 + random.nextInt(4);
        java.util.List<int[]> lakeList = new java.util.ArrayList<>(), channelList = new java.util.ArrayList<>();
        // (the moulins and tables keep out of the lakes' way, rather than the other way about)
        double cs = Mth.cos(angle), sn = Mth.sin(angle);
        // (no more than a hundred or so samples of the ground in all)
        int asked = 0;
        for (int attempt = 0; attempt < 24 && lakeList.size() < want && asked < 100; attempt++) {
            float rv = 3.0F + random.nextFloat() * 5.0F, ru = Math.max(2.5F, rv * (0.5F + 0.4F * random.nextFloat())), pick = random.nextFloat();
            boolean left = random.nextBoolean();
            int deep = 1 + random.nextInt(3);
            // where along the glacier: anywhere in its middle and lower reaches but in the icefall
            float lo = -0.2F * length + ru * 1.12F + 2.0F, hi = 0.38F * length - ru * 1.12F - 2.0F;
            float gap0 = icefall == NO_ICEFALL ? hi : icefall - 9.5F - ru * 1.12F - 2.0F, gap1 = icefall == NO_ICEFALL ? hi : icefall + 9.5F + ru * 1.12F + 2.0F;
            float before = Math.max(0.0F, Math.min(hi, gap0) - lo), after = Math.max(0.0F, hi - Math.max(lo, gap1));
            if (before + after <= 0.0F) break;
            float u = pick * (before + after) < before ? lo + pick * (before + after) : Math.max(lo, gap1) + pick * (before + after) - before;
            // and across it, stretched across the glacier but never wider than it
            float room = halfWidth(u) * 0.85F - 2.0F;
            rv = Math.min(rv, room / 1.12F);
            float v = (left ? -1.0F : 1.0F) * (room - rv * 1.12F) * pick;
            ru = Math.min(ru, Math.max(2.5F, rv));
            if (rv < 3.0F) continue;
            int x = Mth.floor(cx + u * cs - v * sn), z = Mth.floor(cz + u * sn + v * cs);
            int[] others = pack(lakeList, channelList), lake = {x, z, Math.round(ru * 10.0F), Math.round(rv * 10.0F), deep, 0};
            if (!fits(pack(java.util.List.of(lake), java.util.List.of()), others)) continue;
            // its water a block under the lowest of the ice round it
            int rim = Integer.MAX_VALUE, high = Integer.MIN_VALUE, under = Integer.MIN_VALUE, middle = c.groundAt(x, z);
            asked += 9;
            for (int k = 0; k < 8; k++) {
                float a = k * Mth.PI / 4.0F, lu = Mth.cos(a) * (ru * 1.12F + 1.0F), lv = Mth.sin(a) * (rv * 1.12F + 1.0F);
                int px = Mth.floor(x + 0.5 + lu * cs - lv * sn), pz = Mth.floor(z + 0.5 + lu * sn + lv * cs);
                int g = c.groundAt(px, pz), top = g + Math.round(thicknessAt(px + 0.5, pz + 0.5));
                rim = Math.min(rim, top);
                high = Math.max(high, top);
                under = Math.max(under, g);
            }
            // (on a slope, held in by a lip of ice a block or two high on its lower side, so as to have clean ice under all of
            // it; not sunk deep into the slope; and deepest in the middle)
            int water = Math.max(rim - 1, under + 2);
            if (water > rim + 1 || high - water > 6) continue;
            while (lake[4] > 1 && water - lake[4] <= middle) lake[4]--;
            if (water - lake[4] <= middle) continue;
            lake[5] = water;
            int index = lakeList.size();
            if (random.nextFloat() < 0.7F) {
                // a channel the meltwater has melted down into the ice, running into it from up the glacier
                float th = Mth.PI + (random.nextFloat() - 0.5F) * 2.4F, hw = random.nextFloat() < 0.6F ? 0.6F : 1.1F, most = 5.0F + random.nextFloat() * 9.0F;
                int cd = hw > 1.0F && random.nextBoolean() ? 2 : 1;
                float ct = Mth.cos(th), st = Mth.sin(th), edge = 1.12F / Mth.sqrt(Mth.square(ct / ru) + Mth.square(st / rv));
                int[] channel = null;
                for (float k = 2.0F; k <= most; k += 1.0F) {
                    float lu = ct * (edge + k), lv = st * (edge + k);
                    float px = (float) (x + 0.5 + lu * cs - lv * sn), pz = (float) (z + 0.5 + lu * sn + lv * cs);
                    int[] longer = {0, Mth.floor(px), Mth.floor(pz), Math.round(hw * 10.0F), cd};
                    if (!fits(pack(java.util.List.of(lake), java.util.List.of(longer)), others)) break;
                    if ((int) k % 3 == 2) {
                        // (the ice beside it at least a block over its water, and clean ice under it)
                        int g = c.groundAt(Mth.floor(px), Mth.floor(pz)), top = g + Math.round(thicknessAt(px, pz));
                        asked++;
                        if (top < water + 1 || top - water > 6 || water - cd <= g) break;
                    }
                    channel = longer;
                }
                if (channel != null && channel[0] == 0) {
                    float run = Mth.sqrt(Mth.square(channel[1] - x) + Mth.square(channel[2] - z));
                    if (run >= edge + 4.0F) {
                        channel[0] = index;
                        channelList.add(channel);
                    }
                }
            }
            lakeList.add(lake);
        }
        return pack(lakeList, channelList);
    }

    /**
     * Whether the meltwater in {@code planned} (laid out as {@link #meltwater}) fits: its water and the ice holding it in
     * on thick ice clear of the icefall, and the hollow round it on the glacier, above the snout, and clear of the
     * meltwater planned already ({@code others}). (An ice cave running under it keeps below its water.)
     */
    private boolean fits(int[] planned, int[] others) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE, r = 0;
        for (int i = 0; i < planned[0]; i++) {
            int o = 1 + i * 6;
            r = Math.max(r, Math.max(planned[o + 2], planned[o + 3]));
            minX = Math.min(minX, planned[o]);
            maxX = Math.max(maxX, planned[o]);
            minZ = Math.min(minZ, planned[o + 1]);
            maxZ = Math.max(maxZ, planned[o + 1]);
        }
        for (int o = 1 + planned[0] * 6; o + 4 < planned.length; o += 5) {
            minX = Math.min(minX, planned[o + 1]);
            maxX = Math.max(maxX, planned[o + 1]);
            minZ = Math.min(minZ, planned[o + 2]);
            maxZ = Math.max(maxZ, planned[o + 2]);
        }
        int reach = Mth.ceil(r / 10.0F * 1.12F + BOWL + 2.0F);
        for (int x = minX - reach; x <= maxX + reach; x++) {
            for (int z = minZ - reach; z <= maxZ + reach; z++) {
                int[] w = melt(planned, x, z);
                if (w[0] == 0) continue;
                float edge = w[0] == 2 ? 0.0F : w[3] / 10.0F;
                if (edge >= BOWL + 1.0F) continue;
                float[] l = local(x + 0.5, z + 0.5);
                // (the water and the ice holding it in on thick, whole ice; the hollow round it on the glacier, though it
                // stops short of an icefall it comes to, and cuts short any crevasse)
                if (edge < 2.0F && thicknessAt(x + 0.5, z + 0.5) < 3.0F) return false;
                if (thicknessAt(x + 0.5, z + 0.5) < 1.0F || edge < 2.0F && icefallAt(l[0]) >= 0.05F || l[0] > length * 0.38F) return false;
                int[] near = melt(others, x, z);
                if (near[0] == 2 || near[0] == 1 && near[3] < (BOWL + 1.0F) * 10.0F) return false;
            }
        }
        return true;
    }

    private static int[] pack(java.util.List<int[]> lakeList, java.util.List<int[]> channelList) {
        if (lakeList.isEmpty()) return new int[0];
        int[] out = new int[1 + lakeList.size() * 6 + channelList.size() * 5];
        out[0] = lakeList.size();
        int o = 1;
        for (int[] l : lakeList) {
            System.arraycopy(l, 0, out, o, 6);
            o += 6;
        }
        for (int[] ch : channelList) {
            System.arraycopy(ch, 0, out, o, 5);
            o += 5;
        }
        return out;
    }

    /** What a column is to the meltwater lakes and channels (see {@link #melt}). */
    private int[] meltAt(int x, int z) {
        return melt(meltwater, x, z);
    }

    /**
     * What a column is to the meltwater in {@code data} (laid out as {@link #meltwater}): {0 nothing, 1 the ice round a
     * lake or channel, 2 its water; the water's level; how deep the water is here; how far from the water the column is,
     * in tenths; which lake}.
     */
    private int[] melt(int[] data, int x, int z) {
        if (data.length == 0) return DRY;
        int n = data[0];
        float reach = BOWL + CLEAR + 1.0F, nearest = reach;
        double cs = Mth.cos(angle), sn = Mth.sin(angle);
        int[] best = DRY;
        for (int i = 0; i < n; i++) {
            int o = 1 + i * 6;
            double dx = x - data[o], dz = z - data[o + 1];
            float lu = (float) (dx * cs + dz * sn), lv = (float) (-dx * sn + dz * cs), ru = data[o + 2] / 10.0F, rv = data[o + 3] / 10.0F;
            if (Math.abs(lu) > ru * 1.12F + reach || Math.abs(lv) > rv * 1.12F + reach) continue;
            // (its shore a little ragged)
            float e = Mth.sqrt(Mth.square(lu / ru) + Mth.square(lv / rv)) / (0.9F + 0.2F * Terrain.field(seed + 300 + i, x, z, 3.0));
            if (e < 1.0F) return new int[]{2, data[o + 5], 1 + Math.round((data[o + 4] - 1) * Mth.sqrt(1.0F - e * e)), 0, i};
            float edge = Mth.sqrt(lu * lu + lv * lv) * (1.0F - 1.0F / e);
            if (edge < nearest) {
                nearest = edge;
                best = new int[]{1, data[o + 5], 0, Math.round(edge * 10.0F), i};
            }
        }
        for (int o = 1 + n * 6; o + 4 < data.length; o += 5) {
            int i = data[o], lo = 1 + i * 6;
            float ax = data[lo] + 0.5F, az = data[lo + 1] + 0.5F, ex = data[o + 1] + 0.5F - ax, ez = data[o + 2] + 0.5F - az;
            float px = x + 0.5F - ax, pz = z + 0.5F - az, len = ex * ex + ez * ez;
            float f = len > 0.0F ? Mth.clamp((px * ex + pz * ez) / len, 0.0F, 1.0F) : 0.0F;
            float d = Mth.sqrt(Mth.square(px - f * ex) + Mth.square(pz - f * ez)), hw = data[o + 3] / 10.0F;
            if (d < hw) return new int[]{2, data[lo + 5], data[o + 4], 0, i};
            if (d - hw < nearest) {
                nearest = d - hw;
                best = new int[]{1, data[lo + 5], 0, Math.round((d - hw) * 10.0F), i};
            }
        }
        return best;
    }

    /**
     * A meltwater lake's (or channel's) water in one column, over clean ice, or the ice of the hollow round it, sloping up
     * from a block over the water's edge to the glacier's surface; {@code top} is the top of the ice there.
     */
    private static void meltwater(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, int top, int[] melt) {
        int water = melt[1];
        if (melt[0] == 2) {
            int bed = water - melt[2];
            for (int h = top + 1; h < bed; h++) Terrain.set(level, pos, x, h, z, Blocks.PACKED_ICE.defaultBlockState());
            if (bed > ground) Terrain.set(level, pos, x, bed, z, Blocks.BLUE_ICE.defaultBlockState());
            for (int h = bed + 1; h <= water; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
            for (int h = water + 1; h <= top; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
            return;
        }
        int side = Math.max(water + 1, Math.min(top, water + 1 + Math.round(melt[3] / 10.0F * 1.2F)));
        if (side == top) return;
        for (int h = top + 1; h < side; h++) Terrain.set(level, pos, x, h, z, Blocks.PACKED_ICE.defaultBlockState());
        for (int h = side + 1; h <= top; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
        Terrain.set(level, pos, x, side, z, melt[3] < 10 ? Blocks.BLUE_ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState());
    }

    /**
     * The glacier tables, {x, z, the boulder's size, the pedestal's x, its z, its size, its height, the boulder's height,
     * kind of rock}: each a boulder 2 or 3 blocks across (x and z its corner) on a narrower pillar of ice. They stand out
     * on the lower, melting half of the glacier, where the ice is whole (clear of crevasses, moulins, meltwater lakes and
     * the icefall; the ice cave runs under a roof of ice that bears them) but above the snout, where the meltwater runs.
     * Each lies wholly in one chunk, so it's built all at once.
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
                            && l[0] > 0.0F && l[0] < length * 0.42F && meltAt(x + dx, z + dz)[0] == 0
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
            // (where a pond just off the ice comes up against it, or the sea round a snout, the ice is left standing to hold it in)
            for (int h = top; h > floor; h--) {
                if (h < level.getSeaLevel() || Terrain.pondBeside(level, pos, x, h, z, (nx, nz) -> Math.round(thicknessAt(nx + 0.5, nz + 0.5)) < 1)) {
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
        boolean[] hidden = bridged();
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
                int[] melt = meltAt(x, z);
                // (where it runs under a meltwater lake, it keeps below the lake's bed)
                int ceiling = melt[0] == 2 || melt[0] == 1 && melt[3] < 20 ? melt[1] - 4 : Integer.MAX_VALUE;
                if (cave < 2.8F && thick >= 5) {
                    int roof = Math.round(Mth.sqrt(Math.max(0.0F, 2.6F * 2.6F - cave * cave)) * 1.3F) + 1;
                    for (int h = ground + 1; h <= Math.min(ceiling, ground + Math.min(roof, thick - 2)); h++) {
                        // (but never under the sea round a snout, nor beside a pond just off the ice)
                        if (h < level.getSeaLevel() || Terrain.pondBeside(level, pos, x, h, z, (nx, nz) -> Math.round(thicknessAt(nx + 0.5, nz + 0.5)) < 1)) continue;
                        Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    }
                } else if (cave < 4.0F && thick >= 5) {
                    for (int h = ground + 1; h <= ground + Math.min(4, thick - 1); h++) Terrain.set(level, pos, x, h, z, Blocks.BLUE_ICE.defaultBlockState());
                }
                // a meltwater lake in a hollow of the ice, or a channel running into one
                if (melt[0] == 2 || melt[0] == 1 && melt[3] < BOWL * 10.0F && (melt[3] < 20 || fall < 0.05F)) {
                    meltwater(level, pos, x, z, ground, Math.max(top, pond), melt);
                    continue;
                }
                if (fall >= 0.05F && thick >= 2) {
                    icefall(level, pos, x, z, l, ground, top, fall);
                    continue;
                }
                if (moulin(level, pos, shafts, x, z, ground, top)) continue;
                if (crev >= 0) {
                    int floor = top - Mth.clamp(thick + 2, 5, 8);
                    // (never open down into the sea round a snout, nor beside a pond just off the ice)
                    for (int h = top; h > floor; h--) {
                        if (h < level.getSeaLevel() || Terrain.pondBeside(level, pos, x, h, z, (nx, nz) -> Math.round(thicknessAt(nx + 0.5, nz + 0.5)) < 1)) {
                            floor = h;
                            break;
                        }
                    }
                    for (int h = floor + 1; h <= top; h++) {
                        Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    }
                    Terrain.set(level, pos, x, floor, z, Blocks.SNOW_BLOCK.defaultBlockState());
                    if (crev < hidden.length && hidden[crev] && l[0] < 0.0F) {
                        // roofed over by a snow bridge a block or two thick, with the snow lying on it just as on the ice
                        // round it: nothing shows of the crevasse under it
                        int cap = floor < top - 2 && Terrain.field(seed + 501, x, z, 5.0) > 0.45F ? 2 : 1;
                        for (int h = Math.max(floor + 1, top - cap + 1); h <= top; h++) Terrain.set(level, pos, x, h, z, ModBlocks.SNOW_BRIDGE.get().defaultBlockState());
                        if (l[0] < -length * 0.1F && Terrain.noise(seed, x, 0, z) > 0.3F) Terrain.set(level, pos, x, top + 1, z, Blocks.SNOW.defaultBlockState());
                    } else if (floor < top && Terrain.hash(seed, crev * 31 + Mth.floor(l[1] / 4.0F)) < 0.7F) {
                        // most of each crevasse hides under a thin bridge of powder snow
                        Terrain.set(level, pos, x, top, z, Blocks.POWDER_SNOW.defaultBlockState());
                    }
                } else if (l[0] < -length * 0.1F && Terrain.noise(seed, x, 0, z) > 0.3F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SNOW.defaultBlockState());
                } else if (!lining(shafts, x, z)) {
                    // the ogives, in the top of the ice only (an ice cave's roof under them is left as it was)
                    BlockState band = band(ogive(l[0], l[1], x, z), x, top, z);
                    if (band != null) Terrain.set(level, pos, x, top, z, band);
                }
            }
        }
        for (int k = 0; k < tabled.length; k++) {
            if (tops[k] != null) table(level, pos, tabled[k], tops[k]);
        }
        // the meltwater's startling blue, lake by lake
        for (int i = 0; i < (meltwater.length == 0 ? 0 : meltwater[0]); i++) {
            int o = 1 + i * 6, lake = i, water = meltwater[o + 5];
            if (Math.abs(meltwater[o] - area.getCenter().getX()) > 48 || Math.abs(meltwater[o + 1] - area.getCenter().getZ()) > 48) continue;
            Terrain.setBiome(level, area, water - 5, water + 3, (x, z) -> {
                int[] m = meltAt(x, z);
                return m[0] == 2 && m[4] == lake;
            }, Terrain.CLEAR_WATER_BIOME);
        }
    }

    /** A moulin's shaft, its lip, or the blue ice lining it, if this column is one; whether it was. */
    private static boolean moulin(WorldGenLevel level, BlockPos.MutableBlockPos pos, int[][] shafts, int x, int z, int ground, int top) {
        for (int[] m : shafts) {
            if (x >= m[0] && x < m[0] + m[2] && z >= m[1] && z < m[1] + m[2]) {
                // the shaft, straight down to the rock it has bored through to (or to the sea, where the ice has run down into it)
                for (int h = Math.max(ground + 1, level.getSeaLevel()); h <= top; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                return true;
            }
            if (x == m[3] && z == m[4] && top >= level.getSeaLevel()) {
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

    /** Whether a column is in a moulin's blue lining (or its shaft or lip). */
    private static boolean lining(int[][] shafts, int x, int z) {
        for (int[] m : shafts) {
            if (x >= m[0] - 1 && x <= m[0] + m[2] && z >= m[1] - 1 && z <= m[1] + m[2] || x == m[3] && z == m[4]) return true;
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
            // (the ice holding in a meltwater lake or channel is left be: melting it would let the water out)
            if (!level.isLoaded(column) || meltAt(x, z)[0] > 0) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column).below();
            BlockState state = level.getBlockState(top);
            // (an ogive's dark band melts down with the ice round it, and shows on in the ice under it; the snow bridges, far
            // up in the snow, are never reached, and melt through on their own in a thaw)
            float[] l = local(x + 0.5, z + 0.5);
            float ogive = ogive(l[0], l[1], x, z);
            boolean ice = state.is(Blocks.PACKED_ICE) || state.is(Blocks.ICE) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SNOW)
                    || state.is(Blocks.BLUE_ICE) && ogive > 0.35F;
            // never melt right down to the rock: keep two blocks of ice
            if (ice && isIce(level.getBlockState(top.below())) && isIce(level.getBlockState(top.below(2)))) {
                boolean snout = u > half - length * 0.08F;
                level.setBlock(top, snout && random.nextInt(4) == 0 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                BlockState band = ogive > 0.35F ? band(ogive, x, top.getY() - 1, z) : null;
                if (band != null && level.getBlockState(top.below()).is(Blocks.PACKED_ICE)) level.setBlock(top.below(), band, Block.UPDATE_ALL);
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
