package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A fringing reef: a coral reef growing along a warm coast just offshore. Next to the beach lies the reef flat, a
 * shallow shelf of coral heads, rubble and pockets of sand only a few feet under the water, where sea pickles glow at
 * night. Along its outer edge runs the reef crest, where the waves break and only the toughest coral grows, and the
 * highest knobs of it, left out of the water, have died white. Beyond the crest the reef front drops steeply to the sea
 * floor, combed into spurs and grooves: ridges of living coral running straight out to sea, with channels of sand
 * between them down which storms sweep the sand and broken coral off the reef.
 *
 * @param angle  the way out to sea
 * @param flat   how far out the reef crest is
 * @param period how far apart the spurs are
 */
public record FringingReef(int cx, int cz, int sea, float angle, int length, int flat, int period, long seed) implements Landform {
    private static final BlockState[] CORAL = {Blocks.TUBE_CORAL_BLOCK.defaultBlockState(), Blocks.BRAIN_CORAL_BLOCK.defaultBlockState(),
            Blocks.BUBBLE_CORAL_BLOCK.defaultBlockState(), Blocks.FIRE_CORAL_BLOCK.defaultBlockState(), Blocks.HORN_CORAL_BLOCK.defaultBlockState()};
    private static final BlockState[] DEAD = {Blocks.DEAD_TUBE_CORAL_BLOCK.defaultBlockState(), Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState(),
            Blocks.DEAD_BUBBLE_CORAL_BLOCK.defaultBlockState(), Blocks.DEAD_FIRE_CORAL_BLOCK.defaultBlockState(), Blocks.DEAD_HORN_CORAL_BLOCK.defaultBlockState()};
    private static final BlockState[] PLANTS = {Blocks.TUBE_CORAL.defaultBlockState(), Blocks.BRAIN_CORAL.defaultBlockState(),
            Blocks.BUBBLE_CORAL.defaultBlockState(), Blocks.FIRE_CORAL.defaultBlockState(), Blocks.HORN_CORAL.defaultBlockState()};
    private static final BlockState[] FANS = {Blocks.TUBE_CORAL_FAN.defaultBlockState(), Blocks.BRAIN_CORAL_FAN.defaultBlockState(),
            Blocks.BUBBLE_CORAL_FAN.defaultBlockState(), Blocks.FIRE_CORAL_FAN.defaultBlockState(), Blocks.HORN_CORAL_FAN.defaultBlockState()};
    /** How far out the reef front runs past the crest. */
    private static final int FRONT = 14, SHORE = -2;

    @Nullable
    static Landform plan(PlanContext c) {
        return c.search(12, 12, FringingReef::planAt);
    }

    @Nullable
    private static Landform planAt(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        // right at the water's edge
        if (ground < c.seaLevel() - 3 || ground > c.seaLevel() + 3) {
            return null;
        }
        float seaward = c.seaward(24, 1);
        if (Float.isNaN(seaward)) {
            return null;
        }
        int flat = 12 + c.random().nextInt(10);
        // and deep enough water off the reef for its front to drop into
        int reach = flat + 3 + FRONT;
        if (c.groundAt(c.x() + Math.round(Mth.cos(seaward) * reach), c.z() + Math.round(Mth.sin(seaward) * reach)) > c.seaLevel() - 7) {
            return null;
        }
        return new FringingReef(c.x(), c.z(), c.seaLevel(), seaward, 50 + c.random().nextInt(36), flat, 7 + c.random().nextInt(4), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new FringingReef(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("flat"),
                tag.getInt("period"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("sea", sea);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putInt("flat", flat);
        tag.putInt("period", period);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.FRINGING_REEF;
    }

    @Override
    public BoundingBox bounds() {
        float half = length / 2.0F + 1.0F;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (float u : new float[]{SHORE - 1.0F, flat + 3 + FRONT + 1.0F}) {
            for (float v : new float[]{-half, half}) {
                double[] p = Terrain.xz(u, v, cx, cz, angle);
                minX = Math.min(minX, Mth.floor(p[0]));
                maxX = Math.max(maxX, Mth.ceil(p[0]));
                minZ = Math.min(minZ, Mth.floor(p[1]));
                maxZ = Math.max(maxZ, Mth.ceil(p[1]));
            }
        }
        return new BoundingBox(minX, sea - 34, minZ, maxX, sea + 16, maxZ);
    }

    /** Whether the reef reaches a column at all. */
    private boolean inside(int x, int z) {
        float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
        return Math.abs(l[1]) <= length / 2.0F && l[0] >= SHORE && l[0] <= flat + 3 + FRONT;
    }

    /** Whether a column out on the reef front is on a spur (rather than in a groove). */
    private boolean spur(float u, float v, int x, int z) {
        float wave = Mth.sin(Mth.TWO_PI * (v + 1.8F * Mth.sin(u * 0.2F + Terrain.hash(seed, 1) * 6.0F)) / period + Terrain.hash(seed, 2) * 6.0F);
        return wave > -0.2F + 0.5F * (Terrain.field(seed + 3, x, z, 3.0) - 0.5F);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float half = length / 2.0F;
        int crest = flat + 3;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                if (!inside(x, z)) continue;
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float u = l[0], v = l[1];
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 4 || ground > b.maxY() - 4) continue;
                int surface = Terrain.surface(level, pos, x, ground, z);
                float patch = Terrain.field(seed + 5, x, z, 4.0);
                // 0 the sandy moat by the beach, 1 the reef flat, 2 the crest, 3 a spur, 4 a groove
                int zone;
                int target;
                if (u < 5.0F) {
                    zone = 0;
                    target = sea - 3 - (patch > 0.6F ? 1 : 0);
                } else if (u < flat) {
                    zone = 1;
                    // one to three blocks of water over it: coral heads rising nearly to the surface, sandy hollows between
                    target = sea - 2 - Math.round(2.0F * Terrain.field(seed + 4, x, z, 6.0)) + (patch > 0.62F ? 1 : 0);
                    target = Math.min(target, sea - 2);
                } else if (u < crest) {
                    zone = spur(u, v, x, z) || u < flat + 1.0F ? 2 : 4;
                    // the crest, the shallowest of all, with knobs of it out of the water; grooves cut back into it a little
                    target = zone == 2 ? sea - 2 + (Terrain.field(seed + 6, x, z, 3.0) > 0.72F ? 1 : 0) : sea - 4;
                } else {
                    // the reef front, plunging to the sea floor
                    float s = (u - crest) / FRONT;
                    boolean ridge = spur(u, v, x, z);
                    zone = ridge ? 3 : 4;
                    float drop = (float) Math.pow(s, 0.7);
                    target = Math.round(Mth.lerp(drop, sea - 2, ground));
                    if (!ridge) target = Math.round(Mth.lerp(drop, sea - 6, ground));
                    target = Math.max(target, ground);
                }
                // fading into the shore behind and the sea floor at either end, and never quarrying away land that stands well clear of the sea
                float fade = Terrain.smooth((u - SHORE) / 4.0F) * Terrain.smooth((half - Math.abs(v)) / 10.0F);
                if (ground > sea + 1) fade *= Mth.clamp(1.0F - (ground - sea - 1) / 3.0F, 0.0F, 1.0F);
                int top = Math.round(Mth.lerp(fade, ground, target));
                if (top == ground && (surface == ground || surface < sea)) continue;
                Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, Math.min(12, b.maxY() - Math.max(ground, top)));
                for (int h = top + 1; h <= Math.max(Math.max(ground, surface), sea - 1); h++) {
                    BlockState s = h <= sea - 1 ? Blocks.WATER.defaultBlockState() : Terrain.pondBeside(level, pos, x, h, z, (nx, nz) -> !inside(nx, nz))
                            ? Blocks.SANDSTONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    Terrain.set(level, pos, x, h, z, s);
                }
                // the reef rock underneath: old coral, long dead and cemented into limestone
                for (int h = Math.min(ground, top) - 3; h < top; h++) {
                    float m = Terrain.noise(seed + 7, x, h, z);
                    Terrain.set(level, pos, x, h, z, m < 0.55F ? ModBlocks.LIMESTONE.get().defaultBlockState() : m < 0.85F
                            ? DEAD[Math.floorMod(Math.round(m * 70.0F), DEAD.length)] : Blocks.SANDSTONE.defaultBlockState());
                }
                float n = Terrain.noise(seed, x, top, z);
                // each kind of coral grows in colonies of its own
                int kind = Mth.clamp((int) (Terrain.field(seed + 9, x, z, 5.0) * 5.0F + (Terrain.field(seed + 10, x, z, 2.0) - 0.5F)), 0, CORAL.length - 1);
                boolean wet = top <= sea - 2; // living coral needs water over it, even at low tide
                boolean sandy = zone == 0 || zone == 4 || zone == 1 && patch < 0.38F || top < ground + 1 && fade < 0.5F;
                // the crest's coral is mostly the tough, encrusting fire and horn coral
                if (zone == 2) kind = 3 + Math.floorMod(kind, 2);
                boolean coral = !sandy && wet && !(zone == 2 && n < 0.35F || zone == 1 && n < 0.1F);
                BlockState surfaceBlock;
                if (sandy) {
                    surfaceBlock = zone == 4 && n < 0.15F ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState();
                } else {
                    surfaceBlock = coral ? CORAL[kind] : DEAD[kind]; // bleached rubble, and the crest's knobs out of the water
                }
                Terrain.set(level, pos, x, top, z, surfaceBlock);
                // what grows on it: coral and sea fans where there's water over them too, sea pickles, sea grass in the sand
                float m = Terrain.noise(seed + 8, x, top, z);
                if (coral && top <= sea - 3 && m < (zone == 3 ? 0.45F : 0.3F)) {
                    int which = Math.floorMod(kind + (m < 0.08F ? 1 : 0), CORAL.length);
                    Terrain.set(level, pos, x, top + 1, z, m < 0.15F ? FANS[which] : PLANTS[which]);
                } else if (coral && m > 0.95F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + Math.floorMod(Math.round(m * 400.0F), 4)));
                } else if (sandy && top <= sea - 3 && m < 0.25F && surfaceBlock.is(Blocks.SAND)) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SEAGRASS.defaultBlockState());
                }
            }
        }
    }
}
