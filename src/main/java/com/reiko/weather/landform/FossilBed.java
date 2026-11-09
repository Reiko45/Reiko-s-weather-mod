package com.reiko.weather.landform;

import com.reiko.weather.ReikosWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;

import javax.annotation.Nullable;

/**
 * A fossil bed: a low cliff of layered rock along the side of a dry wash, with the skeleton of a dinosaur (or two)
 * weathering out of its face. The animals died on an ancient river's floodplain and were buried quickly in mud and
 * sand, which slowly hardened into these beds; now every flash flood down the wash undercuts the bank, a little
 * more of the bones comes to light, and broken fragments wash out into the gravel at its foot.
 *
 * @param base      the floor of the wash in front of the bank
 * @param top       the top of the bank
 * @param low       the lowest and highest ground sampled (for the bounds)
 * @param angle     the way the face looks out, over the wash
 * @param half      half the length of the bank
 * @param skeletons {middle along the bank, spine height above the wash, spine length, facing (1 or -1)} for each
 */
public record FossilBed(int cx, int cz, int base, int top, int low, int high, float angle, int half, int[] skeletons, long seed) implements Landform {
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, ReikosWeather.id("archaeology/fossil_bed"));
    private static final float BACK = 26.0F, FRONT = 24.0F;
    private static final BlockState[] BADLANDS = {Blocks.WHITE_TERRACOTTA.defaultBlockState(), Blocks.WHITE_TERRACOTTA.defaultBlockState(),
            Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState(), Blocks.BROWN_TERRACOTTA.defaultBlockState(), Blocks.BROWN_TERRACOTTA.defaultBlockState(),
            Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.YELLOW_TERRACOTTA.defaultBlockState(),
            Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(),
            Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState()};
    private static final BlockState[] DESERT = {Blocks.SANDSTONE.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
            Blocks.WHITE_TERRACOTTA.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(),
            Blocks.YELLOW_TERRACOTTA.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
            Blocks.BROWN_TERRACOTTA.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState(),
            Blocks.SANDSTONE.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.WHITE_TERRACOTTA.defaultBlockState()};
    /** How far each bed (in either list) stands out from the face (the sandstones) or is cut back into it (the softer clays). */
    private static final float[] LEDGE = {0.0F, 0.0F, -0.6F, 0.0F, -0.6F, 0.0F, 0.7F, 0.0F, -0.6F, 0.0F, -0.6F, 0.7F, 0.0F, 0.0F};

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 3 || !c.fits(ground, 12, 22)) {
            return null;
        }
        int low = ground, high = ground;
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F;
            int g = c.groundAt(c.x() + Math.round(Mth.cos(a) * 30), c.z() + Math.round(Mth.sin(a) * 30));
            low = Math.min(low, g);
            high = Math.max(high, g);
        }
        if (high - low > 16 || low < c.seaLevel() + 1) {
            return null;
        }
        // the bank faces down the slope, if there is one
        float[] down = c.downhill(20);
        float angle = down[1] >= 3 ? down[0] : c.random().nextFloat() * Mth.TWO_PI;
        int base = ground - 3, top = base + 14 + c.random().nextInt(3);
        int half = 40 + c.random().nextInt(7);
        boolean two = c.random().nextFloat() < 0.5F;
        int n = two ? 2 : 1;
        int[] skeletons = new int[n * 4];
        for (int k = 0; k < n; k++) {
            int s = two ? (k == 0 ? -21 : 21) + c.random().nextInt(3) - 1 : c.random().nextInt(7) - 3;
            skeletons[k * 4] = s;
            skeletons[k * 4 + 1] = 8 + c.random().nextInt(2);
            skeletons[k * 4 + 2] = 18 + c.random().nextInt(7);
            // two of them lie head to head in the middle of the bank, their tails off towards its ends
            skeletons[k * 4 + 3] = two ? (s < 0 ? 1 : -1) : c.random().nextBoolean() ? 1 : -1;
        }
        return new FossilBed(c.x(), c.z(), base, top, low, high, angle, half, skeletons, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new FossilBed(tag.getInt("x"), tag.getInt("z"), tag.getInt("base"), tag.getInt("top"), tag.getInt("low"), tag.getInt("high"),
                tag.getFloat("angle"), tag.getInt("half"), tag.getIntArray("skeletons"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("base", base);
        tag.putInt("top", top);
        tag.putInt("low", low);
        tag.putInt("high", high);
        tag.putFloat("angle", angle);
        tag.putInt("half", half);
        tag.put("skeletons", new IntArrayTag(skeletons));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.FOSSIL_BED;
    }

    @Override
    public BoundingBox bounds() {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (float u : new float[]{-BACK - 4.0F, FRONT + 4.0F}) {
            for (float v : new float[]{-half, half}) {
                double[] p = Terrain.xz(u, v, cx, cz, angle);
                minX = Math.min(minX, Mth.floor(p[0]));
                maxX = Math.max(maxX, Mth.ceil(p[0]));
                minZ = Math.min(minZ, Mth.floor(p[1]));
                maxZ = Math.max(maxZ, Mth.ceil(p[1]));
            }
        }
        return new BoundingBox(minX, Math.min(base, low) - 8, minZ, maxX, Math.max(top, high) + 8, maxZ);
    }

    /** Where the foot of the face stands, {@code v} blocks along the bank: it wanders in and out a little. */
    private float face(float v) {
        return 2.0F * Mth.sin(v * 0.08F + Terrain.hash(seed, 1) * 6.0F) + 1.0F * Mth.sin(v * 0.21F + Terrain.hash(seed, 2) * 6.0F);
    }

    private static float segment(float p, float t, float ap, float at, float bp, float bt) {
        float dp = bp - ap, dt = bt - at;
        float k = Mth.clamp(((p - ap) * dp + (t - at) * dt) / (dp * dp + dt * dt), 0.0F, 1.0F);
        return Mth.sqrt(Mth.square(p - ap - k * dp) + Mth.square(t - at - k * dt));
    }

    /** How high skeleton {@code k}'s backbone lies at {@code p} blocks from its middle (towards its head). */
    private float spine(int k, float p) {
        float length = skeletons[k * 4 + 2], t0 = skeletons[k * 4 + 1];
        float tail = -length / 2.0F, hip = -length / 6.0F, shoulder = length / 3.0F;
        if (p < hip) return t0 - 2.5F * (float) Math.pow(Math.min(1.0F, (hip - p) / (hip - tail)), 1.5); // the tail drooping away
        if (p < shoulder) return t0 + 0.8F * Mth.sin(Mth.PI * (p - hip) / (shoulder - hip));
        return t0 + (p - shoulder) * 0.8F; // the neck thrown back, the way so many are found
    }

    /**
     * Whether a block of the face (at {@code s} along the bank and {@code t} above the wash) is bone of skeleton
     * {@code k}: 0 if not, 1 for bones lying along the bank (backbone, pelvis, skull), 2 for upright ones (ribs, legs).
     */
    private int bone(int k, float s, int t) {
        float length = skeletons[k * 4 + 2];
        float p = (s - skeletons[k * 4]) * skeletons[k * 4 + 3];
        float tail = -length / 2.0F, hip = -length / 6.0F, shoulder = length / 3.0F, neck = shoulder + 4.5F;
        if (p < tail - 0.5F || p > neck + 6.0F) return 0;
        // the backbone, with the spines along the top of the vertebrae over the back
        if (p <= neck + 0.5F) {
            int sp = Math.round(spine(k, p));
            if (t == sp) return 1;
            if (t == sp + 1 && p > hip && p < shoulder && Math.floorMod(Math.round(p), 2) == 1) return 2;
        }
        // the ribs hanging down from the backbone, longest in the middle of the chest
        for (int pr = Mth.floor(p); pr <= Mth.floor(p) + 1; pr++) {
            if (Math.floorMod(pr, 2) != 0 || pr < hip + 1.5F || pr > shoulder - 1.5F || Math.abs(p - pr) >= 0.5F) continue;
            int below = Math.round(spine(k, pr)) - t;
            int rib = Math.round(1.5F + 3.5F * Mth.sin(Mth.PI * (pr - hip) / (shoulder - hip)));
            if (below >= 1 && below <= rib) return 2;
        }
        // the pelvis, and the hind leg folded back under it
        float sh = spine(k, hip);
        if (p - hip > -1.5F && p - hip < 1.0F && t == Math.round(sh) - 1) return 1;
        if (segment(p, t, hip - 0.5F, sh - 1.5F, hip - 1.5F, sh - 4.5F) < 0.55F || segment(p, t, hip - 1.5F, sh - 4.5F, hip + 0.5F, sh - 7.5F) < 0.55F
                || segment(p, t, hip - 0.5F, sh - 8.0F, hip + 2.0F, sh - 8.0F) < 0.5F) return 2;
        // the smaller foreleg
        float ss = spine(k, shoulder);
        if (segment(p, t, shoulder + 0.5F, ss - 1.5F, shoulder + 1.5F, ss - 4.0F) < 0.55F || segment(p, t, shoulder + 1.5F, ss - 4.0F, shoulder + 0.5F, ss - 6.0F) < 0.55F
                || segment(p, t, shoulder, ss - 6.5F, shoulder + 2.0F, ss - 6.5F) < 0.5F) return 2;
        // the skull, on the end of the neck: cranium, eye socket and long jaw
        int st = Math.round(spine(k, neck));
        if (t == st + 1 && p >= neck + 0.5F && p <= neck + 3.0F) return 1;
        if (t == st && p >= neck && p <= neck + 5.0F && Math.abs(p - (neck + 2.0F)) >= 0.5F) return 1;
        if (t == st - 1 && p >= neck + 1.0F && p <= neck + 5.5F) return 1;
        return 0;
    }

    /** Where a bed's face stands at a block, relative to the foot of the face: harder beds jut out, and it's all a little ragged. */
    private float edge(int band, int x, int h, int z) {
        return LEDGE[band] + 0.3F * (Terrain.noise(seed, x, h, z) - 0.5F);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int offset = Math.round(Terrain.hash(seed, 3) * BADLANDS.length);
        // the bones lying along the bank run the way it does
        Direction.Axis along = Math.abs(Mth.cos(angle)) > Math.abs(Mth.sin(angle)) ? Direction.Axis.Z : Direction.Axis.X;
        BlockState lying = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, along);
        BlockState upright = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float u = l[0], v = l[1];
                if (Math.abs(v) > half || u < -BACK || u > FRONT) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 3 || ground > b.maxY() - 3) continue;
                boolean red = level.getBiome(pos.set(x, ground, z)).is(BiomeTags.IS_BADLANDS);
                BlockState[] bands = red ? BADLANDS : DESERT;
                BlockState sand = red ? Blocks.RED_SAND.defaultBlockState() : Blocks.SAND.defaultBlockState();
                // the bank and the wash both fade back into the land at the ends, and away from the face
                float ends = Terrain.smooth((half - Math.abs(v)) / 12.0F);
                float fd = u - face(v);
                int hi = Math.round(Mth.lerp(ends, ground, Mth.lerp(Terrain.smooth((-fd - 10.0F) / 14.0F), top, ground)));
                int lo = Math.round(Mth.lerp(ends, ground, Mth.lerp(Terrain.smooth((fd - 10.0F) / 12.0F), base, ground)));
                lo = Math.min(lo, hi);
                if (fd < -1.5F && hi == ground || fd > 1.0F && lo == ground) continue;
                int rockTop = lo;
                Terrain.clearAbove(level, pos, x, Math.max(ground, hi), z, Math.min(8, b.maxY() - Math.max(ground, hi)));
                for (int h = Math.min(ground, lo) - 2; h <= Math.max(ground, hi); h++) {
                    int band = Math.floorMod(h + offset, bands.length);
                    boolean rock = h <= lo || h <= hi && fd <= edge(band, x, h, z);
                    if (rock) {
                        Terrain.set(level, pos, x, h, z, bands[band]);
                        rockTop = Math.max(rockTop, h);
                    } else if (h <= ground) {
                        Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    }
                }
                float n = Terrain.noise(seed, x, rockTop, z);
                if (rockTop > lo && rockTop == hi) {
                    // the top of the bank
                    Terrain.set(level, pos, x, rockTop, z, n < 0.6F ? sand : bands[Math.floorMod(rockTop + offset, bands.length)]);
                    if (n < 0.025F) Terrain.set(level, pos, x, rockTop + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                }
                // the skeletons, a block or so into the face: some bones laid bare, others still just under the surface
                if (fd > -3.0F && fd < 1.5F && ends > 0.5F) {
                    for (int h = lo + 1; h <= rockTop; h++) {
                        float front = edge(Math.floorMod(h + offset, bands.length), x, h, z) - fd;
                        if (front < 0.0F || front >= 2.0F) continue;
                        boolean bare = Terrain.field(seed + 9, v, h, 4.0) < 0.62F;
                        if (bare != front < 1.0F) continue;
                        for (int k = 0; k < skeletons.length / 4; k++) {
                            int kind = bone(k, v, h - base);
                            if (kind == 0) continue;
                            pos.set(x, h, z);
                            if (level.getBlockState(pos).isAir()) continue;
                            Terrain.set(level, pos, x, h, z, kind == 1 ? lying : upright);
                        }
                    }
                }
                if (rockTop != lo || fd < 0.0F) continue;
                // the floor of the wash: gravel and sand, with rubble fallen from the bank piled along its foot
                BlockState floor = n < 0.45F ? Blocks.GRAVEL.defaultBlockState() : sand;
                int surface = lo;
                if (fd < 2.2F && ends > 0.3F && n < 0.7F) {
                    surface = lo + 1;
                    Terrain.set(level, pos, x, lo, z, bands[Math.floorMod(lo + offset, bands.length)]);
                }
                Terrain.set(level, pos, x, surface, z, floor);
                // pieces of bone washed out of the bank, buried in the gravel below the skeletons
                boolean below = false;
                for (int k = 0; k < skeletons.length / 4; k++) {
                    float p = (v - skeletons[k * 4]) * skeletons[k * 4 + 3];
                    if (p > -skeletons[k * 4 + 2] / 2.0F - 2.0F && p < skeletons[k * 4 + 2] / 3.0F + 11.0F) below = true;
                }
                if (below && fd < 4.5F && ends > 0.5F) {
                    float m = Terrain.noise(seed + 11, x, surface, z);
                    if (m < 0.24F) {
                        Terrain.set(level, pos, x, surface, z, Blocks.SUSPICIOUS_GRAVEL.defaultBlockState());
                        if (level.getBlockEntity(pos.set(x, surface, z)) instanceof BrushableBlockEntity brushable) {
                            brushable.setLootTable(LOOT, seed ^ BlockPos.asLong(x, surface, z));
                        }
                    } else if (m > 0.97F) {
                        Terrain.set(level, pos, x, surface + 1, z, upright);
                    }
                }
            }
        }
    }
}
