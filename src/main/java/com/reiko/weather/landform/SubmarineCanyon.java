package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;

/**
 * A submarine canyon, like the Monterey Canyon off California or the Nazaré Canyon off Portugal: a deep, winding gorge
 * cut into the sea floor off a coast, deepening as it runs out across the continental shelf and down its slope. Some were
 * begun by rivers when the sea stood lower in the ice ages, but what carves them is the sea itself: now and then the mud
 * and sand piled up near the canyon's head lets go, and an underwater avalanche of muddy water, a turbidity current,
 * pours down the canyon far faster than any river, scouring its floor and undercutting its walls. Its steep sides lay
 * bare the beds of sediment the shelf is built from, side gullies feed it from the slopes round about, and where it opens
 * out onto the deep sea floor the currents slow and drop their load in a broad fan. The upwelling of cold, rich water
 * from its depths feeds forests of kelp along its rims.
 *
 * @param cx     the head of the canyon
 * @param angle  the way it runs, out to sea
 * @param length how far it runs from its head to where it opens out
 * @param w0     its width at the top, at its head
 * @param w1     its width at the top, where it opens out
 * @param floor  the y of its floor at even steps from its head to its mouth
 * @param rim    the sea bed beside it at the same steps, as surveyed
 * @param low    the lowest floor (for the bounds)
 */
public record SubmarineCanyon(int cx, int cz, int sea, float angle, int length, int w0, int w1, int[] floor, int[] rim, int low,
                              long seed) implements Landform {
    /** How far past its mouth its floor takes to climb back to the sea bed, and how far the fan reaches beyond that. */
    private static final float RAMP = 14.0F, FAN = 16.0F;
    /** How much of the canyon's width is flat floor rather than wall. */
    private static final float FLOOR = 0.38F;
    /** Far above or below anything (for a column with nothing beside it). */
    private static final int NONE = 100000;

    @Nullable
    static Landform plan(PlanContext c) {
        int sea = c.seaLevel();
        int here = c.groundAt(c.x(), c.z());
        if (here > sea - 11 || here < sea - 80 || !c.fits(here, 40, 10)) {
            return null;
        }
        // which way the sea bed falls away
        int[] ring = new int[12];
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6.0;
            ring[i] = c.groundAt(c.x() + (int) Math.round(Math.cos(a) * 36), c.z() + (int) Math.round(Math.sin(a) * 36));
        }
        int best = -1, drop = 0;
        for (int i = 0; i < 12; i++) {
            int d = ring[(i + 6) % 12] - ring[i];
            if (d > drop) {
                drop = d;
                best = i;
            }
        }
        if (best < 0 || drop < 6) {
            return null;
        }
        // (leaning toward whichever way either side of it falls further)
        int before = ring[(best + 5) % 12] - ring[(best + 11) % 12], after = ring[(best + 7) % 12] - ring[(best + 1) % 12];
        float lean = Mth.clamp((after - before) / (2.0F * Math.max(1, 2 * drop - before - after)), -0.5F, 0.5F);
        float angle = (best + lean) * Mth.PI / 6.0F + (c.random().nextFloat() - 0.5F) * 0.2F;
        int length = 70 + c.random().nextInt(41);
        int w0 = 8 + c.random().nextInt(3), w1 = 14 + c.random().nextInt(7);
        long seed = c.random().nextLong();
        float dh = 6.0F + c.random().nextFloat() * 3.0F, dm = 15.0F + c.random().nextFloat() * 10.0F;
        // the head lies landward of here, offshore in at least 8 blocks of water; moved out to sea a little if need be
        for (int shift = 0; shift <= 24; shift += 12) {
            SubmarineCanyon probe;
            while (true) {
                double[] h = Terrain.xz(-length / 2.0F + shift, 0.0, c.x() + 0.5, c.z() + 0.5, angle);
                probe = new SubmarineCanyon(Mth.floor(h[0]), Mth.floor(h[1]), sea, angle, length, w0, w1, new int[0], new int[0], 0, seed);
                if (probe.reach(c.x(), c.z()) <= 106 || length <= 70) break;
                length -= 10;
            }
            if (probe.reach(c.x(), c.z()) > 106) return null;
            if (probe.at(c, 0.0F) > sea - 9) continue;
            // the sea bed along its course: never shoaling, and falling away by the mouth
            int n = Mth.ceil(length / 10.0F) + 1;
            int[] rim = new int[n], floor = new int[n];
            int mono = Integer.MAX_VALUE, lowest = Integer.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                float u = i * length / (float) (n - 1);
                rim[i] = probe.at(c, u);
                if (rim[i] > sea - 6) return null;
                // the floor falls all the way down: it's cut deeper where the sea bed rises again
                mono = Math.min(mono, rim[i]);
                floor[i] = mono - Math.round(dh + (dm - dh) * (float) Math.pow(u / length, 0.8));
                lowest = Math.min(lowest, floor[i]);
            }
            if (rim[n - 1] > rim[0] - 5 || !c.fits(lowest, 6, 0)) return null;
            return new SubmarineCanyon(probe.cx(), probe.cz(), sea, angle, length, w0, w1, floor, rim, lowest, seed);
        }
        return null;
    }

    /** The sea bed under the line of the canyon, {@code u} along it from its head. */
    private int at(PlanContext c, float u) {
        double[] p = Terrain.xz(u, centre(u), cx + 0.5, cz + 0.5, angle);
        return c.groundAt(Mth.floor(p[0]), Mth.floor(p[1]));
    }

    static Landform load(CompoundTag tag) {
        return new SubmarineCanyon(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("w0"),
                tag.getInt("w1"), tag.getIntArray("floor"), tag.getIntArray("rim"), tag.getInt("low"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("sea", sea);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putInt("w0", w0);
        tag.putInt("w1", w1);
        tag.put("floor", new IntArrayTag(floor));
        tag.put("rim", new IntArrayTag(rim));
        tag.putInt("low", low);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.SUBMARINE_CANYON;
    }

    @Override
    public BoundingBox bounds() {
        float[] e = extent();
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int k = 0; k < 4; k++) {
            double[] p = Terrain.xz(k < 2 ? e[0] : e[1], k % 2 == 0 ? -e[2] : e[2], cx + 0.5, cz + 0.5, angle);
            minX = Math.min(minX, Mth.floor(p[0]));
            maxX = Math.max(maxX, Mth.floor(p[0]));
            minZ = Math.min(minZ, Mth.floor(p[1]));
            maxZ = Math.max(maxZ, Mth.floor(p[1]));
        }
        return new BoundingBox(minX - 1, low - 3, minZ - 1, maxX + 1, sea - 1, maxZ + 1);
    }

    /** The box round everything it touches, in its own frame: {first u, last u, half-width}. */
    private float[] extent() {
        float back = -w0 / 2.0F - 3.0F;
        float span = Math.abs(centre(length)) + halfWidth(length) * 3.4F;
        for (float u = 0.0F; u <= length + RAMP; u += 2.0F) span = Math.max(span, Math.abs(centre(u)) + halfWidth(u) * 1.6F + 1.0F);
        for (int j = 0; j < gullies(); j++) {
            float[] g = gully(j);
            float reach = g[3] + Math.abs(g[6]) * g[3] * 0.3F + g[4] + 1.0F;
            span = Math.max(span, Math.abs(g[1]) + reach);
            back = Math.min(back, g[0] - reach);
        }
        return new float[]{back, length + RAMP + FAN + 2.0F, span + 2.0F};
    }

    /** How far its bounds reach from a point (as the spacing between landforms measures it). */
    private int reach(int x, int z) {
        BoundingBox b = bounds();
        return Math.max(Math.max(Math.abs(b.minX() - x), Math.abs(b.maxX() - x)), Math.max(Math.abs(b.minZ() - z), Math.abs(b.maxZ() - z)));
    }

    private float amplitude() {
        return 3.0F + 3.0F * Terrain.hash(seed, 1);
    }

    /** How far the line of the canyon swings to one side, {@code u} along it: it leaves its head straight and winds more as it goes. */
    private float centre(float u) {
        float k1 = Mth.TWO_PI / (50.0F + 30.0F * Terrain.hash(seed, 2)), k2 = Mth.TWO_PI / (24.0F + 10.0F * Terrain.hash(seed, 3));
        float wind = Mth.sin(u * k1 + Terrain.hash(seed, 4) * Mth.TWO_PI) + 0.35F * Mth.sin(u * k2 + Terrain.hash(seed, 5) * Mth.TWO_PI);
        float start = Mth.sin(Terrain.hash(seed, 4) * Mth.TWO_PI) + 0.35F * Mth.sin(Terrain.hash(seed, 5) * Mth.TWO_PI);
        return amplitude() * Terrain.smooth(u / 25.0F) * (wind - start);
    }

    private float halfWidth(float u) {
        float t = Mth.clamp(u / length, 0.0F, 1.0F);
        return (w0 + (w1 - w0) * t) / 2.0F + 0.8F * (Terrain.field(seed + 1, u, 0.0, 9.0) - 0.5F);
    }

    /** A value from one of the even steps along the canyon, read between them. */
    private float along(int[] values, float u) {
        float s = Mth.clamp(u / length, 0.0F, 1.0F) * (values.length - 1);
        int i = Math.min(Mth.floor(s), values.length - 2);
        return Mth.lerp(s - i, values[i], values[i + 1]);
    }

    private int gullies() {
        return 2 + Mth.floor(Terrain.hash(seed, 6) * 3.99F);
    }

    /** Side gully {@code j}: {where it joins along the canyon, where across, the way it runs up from there, its length, its half-width, its depth, how it bends}. */
    private float[] gully(int j) {
        float u = length * (0.15F + 0.7F * Terrain.hash(seed, 40 + j));
        float side = Terrain.hash(seed, 50 + j) < 0.5F ? -1.0F : 1.0F;
        float v = centre(u) + side * halfWidth(u) * 0.7F;
        // they come down the slopes on either side, so run up from the canyon landward and out
        float a = side * (30.0F + 25.0F * Terrain.hash(seed, 60 + j)) * Mth.DEG_TO_RAD;
        return new float[]{u, v, a, 10.0F + 12.0F * Terrain.hash(seed, 70 + j), 2.2F + 1.4F * Terrain.hash(seed, 80 + j),
                3.0F + 3.0F * Terrain.hash(seed, 90 + j), (Terrain.hash(seed, 100 + j) - 0.5F) * 2.0F};
    }

    /** How far out from the line of the canyon a point is, as a share of its half-width there (with the head rounded off). */
    private float out(float u, float v) {
        float slope = centre(u + 0.5F) - centre(u - 0.5F);
        float d = Math.abs(v - centre(u)) / Mth.sqrt(1.0F + slope * slope);
        if (u < 0.0F) d = Mth.sqrt(d * d + u * u);
        float hw = halfWidth(u);
        if (u > length) hw *= 1.0F + 0.6F * Terrain.smooth((u - length) / RAMP);
        return d / hw;
    }

    /** How deep side gully {@code j} cuts at a point (0 outside it). */
    private float gullyDepth(int j, float u, float v) {
        float[] g = gully(j);
        float du = u - g[0], dv = v - g[1];
        float ca = -Mth.cos(g[2]), sa = Mth.sin(g[2]);
        float s = du * ca + dv * sa;
        if (s < -g[4] || s > g[3]) return 0.0F;
        float f = Math.max(0.0F, s) / g[3];
        // curving a little as it climbs, and narrowing and shallowing to nothing at its upper end
        float d = Math.abs(-du * sa + dv * ca - g[6] * g[3] * 0.3F * f * f);
        float half = g[4] * (1.0F - 0.45F * f);
        if (d >= half) return 0.0F;
        return g[5] * (float) Math.pow(1.0F - f, 0.7) * (1.0F - Mth.square(d / half));
    }

    /** Whether the canyon (or one of its gullies) cuts into a column at all, by the plan alone. */
    private boolean cuts(float u, float v) {
        if (u >= -w0 && u <= length + RAMP && out(u, v) < 1.0F) return true;
        for (int j = 0; j < gullies(); j++) if (gullyDepth(j, u, v) > 0.0F) return true;
        return false;
    }

    /** The y the canyon cuts a column down to, given its sea bed: no lower than its floor there. */
    private int bottom(int x, int z, float u, float v, int ground) {
        float fl = along(floor, u);
        float best = ground;
        if (u >= -w0 && u <= length + RAMP) {
            float dn = out(u, v);
            if (dn < 1.0F) {
                float w = dn < FLOOR ? 0.0F : (float) Math.pow((dn - FLOOR) / (1.0F - FLOOR), 0.8);
                // the walls stepped a little where harder beds stand out
                float base = fl + (ground - fl) * w + (w > 0.0F ? 1.2F * (Terrain.noise(seed + 2, x, 0, z) - 0.5F) : 0.0F);
                if (u > length) base = Mth.lerp(Terrain.smooth((u - length) / RAMP), base, ground);
                best = Math.min(best, base);
            }
        }
        for (int j = 0; j < gullies(); j++) {
            float d = gullyDepth(j, u, v);
            if (d > 0.0F) best = Math.min(best, ground - d);
        }
        return Math.max(Mth.floor(fl), Math.min(ground, Math.round(best)));
    }

    /** How much sediment the fan lays down at a point, beyond the mouth. */
    private float fan(int x, int z, float u, float v) {
        if (u < length - 8.0F) return 0.0F;
        float e = (u - length + 8.0F) / (RAMP + FAN + 8.0F);
        float spread = halfWidth(length) * (1.0F + 2.2F * Mth.clamp(e, 0.0F, 1.0F));
        float d = Mth.sqrt(e * e + Mth.square((v - centre(length)) / spread));
        return Math.max(0.0F, 3.4F * (1.0F - d) + 0.8F * (Terrain.field(seed + 3, x, z, 5.0) - 0.5F));
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
                float u = l[0], v = l[1];
                boolean cut = cuts(u, v);
                // the lowest water this column or any beside it may hold, and how low the canyon may lay bare its sides
                // (both from the plan alone, so that every chunk agrees on them)
                int wet = cut ? Mth.floor(along(floor, u)) + 1 : NONE, face = NONE;
                for (int[] s : sides) {
                    float[] n = Terrain.uv(x + s[0] + 0.5, z + s[1] + 0.5, cx + 0.5, cz + 0.5, angle);
                    if (!cuts(n[0], n[1])) continue;
                    wet = Math.min(wet, Mth.floor(along(floor, n[0])) + 1);
                    face = Math.min(face, bottom(x + s[0], z + s[1], n[0], n[1], Math.round(along(rim, n[0]))));
                }
                float fan = fan(x, z, u, v);
                if (wet == NONE && fan < 0.5F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 3) continue;
                // the canyon only cuts the sea bed well under the water, so it never reaches the shore
                boolean sea = ground <= this.sea - 4 && level.getFluidState(pos.set(x, ground + 1, z)).is(Fluids.WATER);
                int bottom = sea && cut ? bottom(x, z, u, v, ground) : ground;
                int lay = sea ? Math.round(fan) : 0;
                boolean carved = bottom < ground;
                int solid = carved ? Math.min(ground, bottom + lay) : lay > 0 ? Math.max(ground, Math.min(this.sea - 4, ground + Math.min(2, lay))) : ground;
                if (carved) {
                    // weed rooted on the old sea bed would be left floating
                    for (int h = ground + 1; h <= this.sea - 1; h++) {
                        pos.set(x, h, z);
                        BlockState was = level.getBlockState(pos);
                        if (!(was.is(Blocks.KELP) || was.is(Blocks.KELP_PLANT) || was.is(Blocks.SEAGRASS) || was.is(Blocks.TALL_SEAGRASS))) break;
                        level.setBlock(pos, Blocks.WATER.defaultBlockState(), Terrain.FLAGS);
                    }
                }
                float dn = u >= -w0 && u <= length + RAMP ? out(u, v) : 9.0F;
                int from = Math.min(Math.min(wet - 2, face + 1), Math.min(solid, bottom + 1));
                for (int h = Math.max(from, b.minY()); h <= Math.max(ground, solid); h++) {
                    if (h > solid) {
                        // everything the canyon cuts away fills with the sea
                        Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                        continue;
                    }
                    // (nothing it cuts reaches higher than this, so the shore above is left be)
                    if (h > this.sea - 4) break;
                    BlockState was = level.getBlockState(pos.set(x, h, z));
                    BlockState s = null;
                    if (h > bottom) {
                        s = sediment(x, h, z, true); // the fan, spread over the floor and the sea bed beyond
                    } else if (carved && h == bottom) {
                        s = dn < FLOOR + 0.12F ? sediment(x, h, z, false) : bed(x, h, z);
                    } else if (h > face && h > solid - 30 && h < ground) {
                        s = bed(x, h, z); // the beds the shelf is built from, laid bare in the walls
                    } else if (!was.blocksMotion() && h >= wet - 2) {
                        s = rock(h); // sealing off a cave behind the wall or under the floor
                    }
                    if (s == null) continue;
                    if (s.getBlock() instanceof FallingBlock && !level.getBlockState(pos.set(x, h - 1, z)).blocksMotion()) {
                        s = rock(h);
                    }
                    Terrain.set(level, pos, x, h, z, s);
                }
                if (sea && !carved && solid == ground && dn >= 1.0F && dn < 1.8F && u > 0.0F && u < length) weed(level, pos, x, ground, z);
            }
        }
    }

    /** The beds the shelf is made of, a couple of blocks thick each: stone, sand, clay and gravel, in a sequence that repeats with variations. */
    private BlockState bed(int x, int h, int z) {
        int layer = Math.floorDiv(h + Math.round(2.0F * Terrain.field(seed + 4, x, z, 13.0)), 2);
        float p = Terrain.hash(seed, 1000 + layer), n = Terrain.noise(seed + 5, x, h, z);
        int kind = Math.floorMod(layer + Mth.floor(Terrain.hash(seed, 7) * 8.0F) + (p < 0.2F ? 3 : 0), 8);
        if (n < 0.06F) return rock(h);
        return switch (kind) {
            case 0, 5 -> n < 0.3F ? Blocks.ANDESITE.defaultBlockState() : rock(h);
            case 1, 6 -> Blocks.SAND.defaultBlockState();
            case 2 -> rock(h);
            case 3 -> Blocks.CLAY.defaultBlockState();
            default -> Blocks.GRAVEL.defaultBlockState();
        };
    }

    /** Plain rock: stone, or deepslate as deep down as that lies. */
    private static BlockState rock(int h) {
        return h < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    /** The floor of the canyon (gravel and sand with a little clay), or the sand of the fan. */
    private BlockState sediment(int x, int h, int z, boolean fan) {
        float n = Terrain.noise(seed + 6, x, h, z);
        if (fan) return n < 0.2F ? Blocks.GRAVEL.defaultBlockState() : n < 0.92F ? Blocks.SAND.defaultBlockState() : Blocks.CLAY.defaultBlockState();
        return n < 0.45F ? Blocks.GRAVEL.defaultBlockState() : n < 0.88F ? Blocks.SAND.defaultBlockState() : Blocks.CLAY.defaultBlockState();
    }

    /** Kelp and seagrass on the sea bed along the rims (seagrass and sea pickles in warm seas, where kelp doesn't grow). */
    private void weed(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int ground, int z) {
        int deep = sea - 1 - ground;
        if (deep < 3 || !level.getBlockState(pos.set(x, ground, z)).blocksMotion()) return;
        float n = Terrain.noise(seed + 7, x, ground, z), bed = Terrain.field(seed + 8, x, z, 6.0);
        Holder<Biome> biome = level.getBiome(pos.set(x, ground + 1, z));
        boolean warm = biome.is(Biomes.WARM_OCEAN) || biome.is(Biomes.LUKEWARM_OCEAN) || biome.is(Biomes.DEEP_LUKEWARM_OCEAN)
                || biome.value().getBaseTemperature() >= 0.9F;
        if (!warm && bed > 0.45F && n < 0.6F && deep >= 5) {
            int tall = 2 + Math.round(Terrain.noise(seed + 9, x, 0, z) * Math.min(16, deep - 4));
            for (int k = 1; k <= tall; k++) {
                BlockState s = k < tall ? Blocks.KELP_PLANT.defaultBlockState()
                        : Blocks.KELP.defaultBlockState().setValue(KelpBlock.AGE, 20 + Math.round(Terrain.noise(seed + 10, x, 0, z) * 5.0F));
                Terrain.set(level, pos, x, ground + k, z, s);
            }
        } else if (n < 0.35F) {
            Terrain.set(level, pos, x, ground + 1, z, Blocks.SEAGRASS.defaultBlockState());
        } else if (warm && n > 0.95F) {
            Terrain.set(level, pos, x, ground + 1, z, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + Math.floorMod(Math.round(n * 400.0F), 4)));
        }
    }
}
