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
 * A caldera: the collapsed summit of an old volcano. A huge eruption emptied the magma chamber beneath the mountain,
 * and with nothing left to hold it up the whole summit sank into the void, leaving a vast round hollow ringed by
 * cliffs of lava flows and ash beds laid one on another. Rain and snowmelt have since filled part of the flat floor
 * with a lake, and a later, much smaller eruption built a cinder cone that stands out of the water as an island.
 * Outside, the old volcano's flanks fall gently away into the country round it.
 *
 * @param y     the ground round about, which the ring's outer slopes run back down to
 * @param floor the flat floor inside the ring (the lake stands a block below it)
 * @param crest the ring's crest, before its rises and dips
 * @param lake  {x, z, radius} of the lake on the floor
 * @param cone  {x, z, radius, height above the water} of the cinder cone island
 */
public record Caldera(int cx, int cz, int y, int radius, int floor, int crest, int[] lake, int[] cone, long seed) implements Landform {
    /** How far the ring's outer slopes run out beyond its crest. */
    private static final int OUTER = 36;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 14) {
            return null;
        }
        int radius = 45 + c.random().nextInt(14);
        int low = ground, high = ground, sum = ground;
        for (int i = 0; i < 12; i++) {
            float a = i * Mth.TWO_PI / 12.0F;
            int h = c.groundAt(c.x() + Math.round(Mth.cos(a) * radius), c.z() + Math.round(Mth.sin(a) * radius));
            low = Math.min(low, h);
            high = Math.max(high, h);
            sum += h;
        }
        if (high - low > 30 || low < c.seaLevel() + 4) {
            return null;
        }
        // and nothing standing up in the middle too tall to have fallen in with the rest
        for (int i = 0; i < 6; i++) {
            float a = (i + 0.5F) * Mth.TWO_PI / 6.0F;
            int h = c.groundAt(c.x() + Math.round(Mth.cos(a) * radius * 0.5F), c.z() + Math.round(Mth.sin(a) * radius * 0.5F));
            low = Math.min(low, h);
            high = Math.max(high, h);
        }
        int base = sum / 13;
        int floor = base - 3 - c.random().nextInt(5);
        int crest = Math.max(base + 14 + c.random().nextInt(9), high + 5);
        if (high - low > 34 || floor < c.seaLevel() + 3 || !c.fits(floor, 16, crest - floor + 30)) {
            return null;
        }
        // the lake lies off to one side of the floor, the cone island a little off the middle of the lake
        float la = c.random().nextFloat() * Mth.TWO_PI;
        float off = radius * (0.1F + 0.18F * c.random().nextFloat());
        int lr = Math.round(Math.min(radius * (0.34F + 0.1F * c.random().nextFloat()), (radius * 0.72F - off) / 1.1F));
        int lx = c.x() + Math.round(Mth.cos(la) * off), lz = c.z() + Math.round(Mth.sin(la) * off);
        int rc = Mth.clamp(Math.round(lr * 0.3F), 5, 8);
        float ca = c.random().nextFloat() * Mth.TWO_PI, cd = c.random().nextFloat() * Math.max(0.0F, lr * 0.75F - rc * 1.1F) * 0.5F;
        int[] cone = {lx + Math.round(Mth.cos(ca) * cd), lz + Math.round(Mth.sin(ca) * cd), rc, 5 + c.random().nextInt(5)};
        return new Caldera(c.x(), c.z(), base, radius, floor, crest, new int[]{lx, lz, lr}, cone, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Caldera(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getInt("floor"), tag.getInt("crest"),
                tag.getIntArray("lake"), tag.getIntArray("cone"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.putInt("floor", floor);
        tag.putInt("crest", crest);
        tag.put("lake", new IntArrayTag(lake));
        tag.put("cone", new IntArrayTag(cone));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.CALDERA;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * 1.08F) + OUTER + 2;
        return new BoundingBox(cx - r, floor - 30, cz - r, cx + r, crest + 26, cz + r);
    }

    /** How far out from the middle a point is, where 1 is the crest of the ring. */
    private float ring(double x, double z) {
        double dx = x - cx, dz = z - cz;
        return (float) Math.sqrt(dx * dx + dz * dz) / (radius * (0.94F + 0.12F * Terrain.field(seed, x, z, 18.0)));
    }

    /** The crest at a point on the ring, rising and dipping along it. */
    private int crestAt(double x, double z) {
        return crest + Math.round((Terrain.field(seed + 1, x, z, 24.0) - 0.5F) * 9.0F);
    }

    /** The top of the floor and the inner cliffs (for {@code d} below 1), which don't depend on the ground. */
    private int inner(double x, double z, float d) {
        if (d < 0.8F) return floor;
        float t = (d - 0.8F) / 0.2F;
        // a scree apron at the foot, sheer cliffs above, and a rounded crest
        return Math.round(floor + (crestAt(x, z) - floor) * (float) Math.pow(Terrain.smooth(t), 0.8));
    }

    /** How far out from the middle of the lake a point is: below 1 is the lake. */
    private float lake(double x, double z) {
        double dx = x - lake[0] - 0.5, dz = z - lake[1] - 0.5;
        return (float) Math.sqrt(dx * dx + dz * dz) / (lake[2] * (0.9F + 0.2F * Terrain.field(seed + 3, x, z, 9.0)));
    }

    /** The cinder cone's surface over the lake's water, going on down under the water past its shore. */
    private float cone(double x, double z) {
        double dx = x - cone[0] - 0.5, dz = z - cone[1] - 0.5;
        float kd = (float) Math.sqrt(dx * dx + dz * dz) / (cone[2] * (0.9F + 0.2F * Terrain.field(seed + 4, x, z, 4.0)));
        if (kd < 0.32F) {
            // the little crater in its top
            float rim = cone[3] * (1.0F - (float) Math.pow(0.32F, 1.3));
            return rim - 2.5F * Terrain.smooth(1.0F - kd / 0.32F);
        }
        return cone[3] * (1.0F - (float) Math.pow(kd, 1.3));
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int water = floor - 1;
        int band = Math.round(Terrain.hash(seed, 1) * 9.0F);
        float reach = 1.0F + OUTER / (float) radius;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float d = ring(x + 0.5, z + 0.5);
                if (d >= reach) continue;
                int ground = Terrain.ground(level, pos, x, z);
                // (and under any water, which leaves overhanging it can hide from Terrain.ground)
                while (ground > b.minY() && !level.getFluidState(pos.set(x, ground, z)).isEmpty()) ground--;
                if (ground > b.maxY() - 4) continue; // (never with the ground the plan saw; and left standing, it still holds the lake in)
                pos.set(x, ground, z);
                BlockState was = level.getBlockState(pos);
                // the top of any water standing over the ground
                int surf = ground;
                for (pos.set(x, ground + 1, z); surf < ground + 12 && !level.getFluidState(pos).isEmpty(); pos.move(Direction.UP)) surf++;
                if (d < 1.0F) {
                    hollow(level, pos, b, x, z, d, ground, surf, water, band);
                } else {
                    flank(level, pos, b, x, z, d, ground, was, reach, band);
                }
            }
        }
    }

    /** Inside the ring: the floor, the lake and its island, and the cliffs. Anything standing in the hollow is swept away. */
    private void hollow(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, float d, int ground, int surf, int water, int band) {
        float n = Terrain.noise(seed, x, 0, z);
        float dl = lake(x + 0.5, z + 0.5), ch = cone(x + 0.5, z + 0.5);
        boolean island = d < 0.8F && ch >= 1.0F, wet = !island && d < 0.74F && dl < 1.0F;
        int top = island ? water + Math.round(ch) : wet ? water : inner(x + 0.5, z + 0.5, d);
        int surface = Math.min(b.maxY(), Math.max(ground, surf));
        Terrain.clearAbove(level, pos, x, surface, z, Math.min(16, b.maxY() - surface));
        for (int h = top + 1; h <= surface; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
        if (wet) {
            // deepest out in the middle, shelving up round the foot of the island
            int bed = water - 1 - Math.round(9.0F * (float) Math.pow(Math.max(0.0F, 1.0F - dl * dl), 0.6));
            bed = Math.min(water - 1, Math.max(bed, water - 1 + Math.round(Math.min(0.0F, ch))));
            for (int h = Math.max(b.minY(), Math.min(ground + 1, bed - 2)); h <= bed; h++) {
                BlockState s = h < bed ? Blocks.TUFF.defaultBlockState()
                        : n < 0.35F ? Blocks.GRAVEL.defaultBlockState() : n < 0.6F ? Blocks.CLAY.defaultBlockState()
                        : n < 0.8F ? ModBlocks.BLACK_SAND.get().defaultBlockState() : Blocks.TUFF.defaultBlockState();
                Terrain.set(level, pos, x, h, z, s);
            }
            for (int h = bed + 1; h <= water; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
            return;
        }
        if (island) {
            // loose black and rust-red cinders, with black sand along the shore
            for (int h = Math.max(b.minY(), Math.min(ground + 1, water - 3)); h <= top; h++) {
                float m = Terrain.noise(seed + 5, x, h, z);
                BlockState s = h < top ? (m < 0.5F ? Blocks.BLACKSTONE.defaultBlockState() : m < 0.8F ? Blocks.BASALT.defaultBlockState() : Blocks.TUFF.defaultBlockState())
                        : top <= water + 1 ? ModBlocks.BLACK_SAND.get().defaultBlockState()
                        : m < 0.3F ? Blocks.RED_TERRACOTTA.defaultBlockState() : m < 0.45F ? Blocks.BROWN_TERRACOTTA.defaultBlockState()
                        : m < 0.8F ? Blocks.BLACKSTONE.defaultBlockState() : ModBlocks.BLACK_SAND.get().defaultBlockState();
                Terrain.set(level, pos, x, h, z, s);
            }
            return;
        }
        if (d < 0.8F) {
            // the floor: grassy, with a beach of black sand and gravel round the lake
            boolean beach = dl < 1.18F && d < 0.78F;
            BlockState surfaceBlock = beach ? (n < 0.5F ? ModBlocks.BLACK_SAND.get().defaultBlockState() : n < 0.8F ? Blocks.GRAVEL.defaultBlockState() : Blocks.TUFF.defaultBlockState())
                    : n < 0.1F ? Blocks.COARSE_DIRT.defaultBlockState() : n < 0.15F ? Blocks.TUFF.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
            boolean soil = surfaceBlock.is(BlockTags.DIRT);
            for (int h = Math.max(b.minY(), Math.min(ground + 1, top - 3)); h <= top; h++) {
                BlockState s = h == top ? surfaceBlock : soil && h >= top - 2 ? Blocks.DIRT.defaultBlockState() : Blocks.TUFF.defaultBlockState();
                Terrain.set(level, pos, x, h, z, s);
            }
            float g = Terrain.noise(seed + 6, x, 1, z);
            if (surfaceBlock.is(Blocks.GRASS_BLOCK) && g > 0.7F) {
                Terrain.set(level, pos, x, top + 1, z, (g > 0.96F ? Blocks.FERN : Blocks.SHORT_GRASS).defaultBlockState());
            }
            return;
        }
        // the cliffs: the layered lavas and ashes of the old volcano, with a little rock still glowing deep inside them
        float t = (d - 0.8F) / 0.2F;
        boolean buried = d > 0.84F && d < 0.95F;
        int cover = top;
        if (buried) {
            // (only where it's well covered on every side, so it never shows)
            for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                cover = Math.min(cover, inner(x + 0.5 + o[0], z + 0.5 + o[1], ring(x + 0.5 + o[0], z + 0.5 + o[1])));
            }
        }
        for (int h = Math.max(b.minY(), Math.min(ground + 1, top - 8)); h <= top; h++) {
            BlockState s;
            if (buried && h <= top - 5 && h <= cover - 4 && Terrain.noise(seed + 7, x, h, z) < 0.05F) {
                s = Blocks.MAGMA_BLOCK.defaultBlockState();
            } else if (h == top && (t < 0.12F || t > 0.9F)) {
                // scree at the foot of the cliffs, and broken rock along the crest
                s = n < 0.4F ? Blocks.GRAVEL.defaultBlockState() : n < 0.7F ? Blocks.TUFF.defaultBlockState() : rock(x, h, z, band);
            } else {
                s = rock(x, h, z, band);
            }
            Terrain.set(level, pos, x, h, z, s);
        }
    }

    /** Outside the ring: the old volcano's flanks, falling gently away into the country round it. */
    private void flank(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, float d, int ground, BlockState was, float reach, int band) {
        float t = (d - 1.0F) / (reach - 1.0F);
        int top = Math.round(ground + (crestAt(x + 0.5, z + 0.5) - ground) * (1.0F - t) * (1.0F - t));
        if (top <= ground) return;
        Terrain.clearAbove(level, pos, x, ground, z, Math.min(top - ground + 14, b.maxY() - ground));
        // bare rock near the crest; further down, the soil (or snow) the ground had before
        boolean soil = t > 0.3F && (was.is(BlockTags.DIRT) || was.is(Blocks.SNOW_BLOCK));
        float n = Terrain.noise(seed, x, 0, z);
        for (int h = ground + 1; h <= top; h++) {
            BlockState s;
            if (h == top) {
                s = soil ? was : n < 0.3F ? Blocks.GRAVEL.defaultBlockState() : n < 0.6F ? Blocks.TUFF.defaultBlockState() : rock(x, h, z, band);
            } else if (soil && h >= top - 2) {
                s = Blocks.DIRT.defaultBlockState();
            } else {
                s = Terrain.noise(seed + 8, x, h, z) < 0.6F ? Blocks.TUFF.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
            }
            Terrain.set(level, pos, x, h, z, s);
        }
        if (soil && was.is(Blocks.GRASS_BLOCK) && Terrain.noise(seed + 6, x, 1, z) > 0.8F) {
            Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
        }
    }

    /** The cliffs' rock: bands of dark lava flows between paler beds of ash. */
    private BlockState rock(int x, int h, int z, int band) {
        int layer = Math.floorMod(h + band + Math.round(Terrain.field(seed + 2, x, z, 14.0) * 4.0F), 9);
        float n = Terrain.noise(seed + 3, x, h, z);
        if (layer < 3) return n < 0.8F ? Blocks.TUFF.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
        if (layer == 3) return n < 0.5F ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
        return n < 0.55F ? Blocks.BASALT.defaultBlockState() : n < 0.85F ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState();
    }
}
