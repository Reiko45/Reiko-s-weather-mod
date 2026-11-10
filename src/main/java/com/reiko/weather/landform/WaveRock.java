package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * The Wave (like the one in the Coyote Buttes of Arizona): a trough of bare sandstone worn into smooth, swelling
 * curves, striped with thin bands of red, orange, yellow and cream that sweep and swirl across it like frozen
 * surf. The bands are the layers of dunes that drifted across a desert two hundred million years ago, each slip face
 * laid down at its own angle and then buried and turned to stone. Rain running through the trough and wind-blown sand
 * have since scoured it smooth, cutting across the layers so their curves show wherever you look.
 *
 * @param y     the level of the slickrock round about
 * @param angle the way the trough runs
 */
public record WaveRock(int cx, int cz, int y, int radius, int depth, float angle, long seed) implements Landform {
    private static final BlockState[] BANDS = {Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(),
            Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState(),
            Blocks.YELLOW_TERRACOTTA.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState(),
            Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
            Blocks.YELLOW_TERRACOTTA.defaultBlockState(), Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState()};
    /** How far out the bare rock round the trough reaches, and the ring beyond it where standing water is sealed in. */
    private static final float APRON = 1.6F, SEAL = 1.7F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        int radius = 15 + c.random().nextInt(11);
        if (ground < c.seaLevel() + 3 || !c.fits(ground, 16, 16)) {
            return null;
        }
        int low = ground, high = ground, sum = ground;
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F;
            int h = c.groundAt(c.x() + Math.round(Mth.cos(a) * radius * 1.2F), c.z() + Math.round(Mth.sin(a) * radius * 1.2F));
            low = Math.min(low, h);
            high = Math.max(high, h);
            sum += h;
        }
        if (high - low > 10) {
            return null;
        }
        return new WaveRock(c.x(), c.z(), Math.round(sum / 9.0F), radius, 5 + c.random().nextInt(5), c.random().nextFloat() * Mth.PI, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new WaveRock(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getInt("depth"), tag.getFloat("angle"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.putInt("depth", depth);
        tag.putFloat("angle", angle);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.WAVE_ROCK;
    }

    @Override
    public BoundingBox bounds() {
        int r = Mth.ceil(radius * SEAL / 0.88F) + 2;
        return new BoundingBox(cx - r, y - depth - 16, cz - r, cx + r, y + 46, cz + r);
    }

    /** How far out from the middle of the trough a point is (under 1 inside it): longer along it than across. */
    private float trough(double x, double z) {
        float[] l = Terrain.uv(x, z, cx, cz, angle);
        return Mth.sqrt(Mth.square(l[0] / radius) + Mth.square(l[1] / (radius * 0.62F))) / (0.88F + 0.24F * Terrain.field(seed, x, z, 12.0));
    }

    /** The rock's surface at a point inside the trough: a smooth hollow, swelling in rounded waves along it. */
    private float hollow(double x, double z, float d) {
        float[] l = Terrain.uv(x, z, cx, cz, angle);
        float bowl = depth * (float) Math.pow(Math.max(0.0F, 1.0F - d * d), 1.2);
        float swell = 1.6F * Mth.sin(l[0] * 0.3F + 2.5F * Terrain.field(seed + 1, x, z, 9.0)) * Terrain.smooth(1.0F - d);
        return y - bowl + swell;
    }

    /** The lowest a column {@code d} out can end up once it's built, given its ground. */
    private int lowest(int x, int z, int ground) {
        float d = trough(x + 0.5, z + 0.5);
        if (d < 1.0F) return Math.round(hollow(x + 0.5, z + 0.5, d));
        return d < APRON ? Math.round(Mth.lerp(Terrain.smooth((d - 1.0F) / (APRON - 1.0F)), y, ground)) : ground;
    }

    /**
     * Turns to sandstone any water left standing on a column the trough leaves alone, as high as the rock beside it is
     * worn down below it, so none of it can pour out into the trough.
     */
    private void seal(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, int ground) {
        int top = ground;
        while (top < b.maxY() && !level.getFluidState(pos.set(x, top + 1, z)).isEmpty()) top++;
        int low = top;
        for (int[] s : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) low = Math.min(low, lowest(x + s[0], z + s[1], top));
        for (int h = Math.max(ground, low) + 1; h <= top; h++) Terrain.set(level, pos, x, h, z, Blocks.SANDSTONE.defaultBlockState());
    }

    /**
     * The band at a block. Flat beds would make plain contour lines; these are warped twice over by smooth noise, as the
     * dunes' slip faces were, so wherever the rock is cut they swirl.
     */
    private BlockState band(int x, int h, int z) {
        double dx = x - cx, dz = z - cz;
        float q1 = Terrain.field(seed + 2, dx, dz, 16.0) - 0.5F, q2 = Terrain.field(seed + 3, dx, dz, 16.0) - 0.5F;
        float r = Terrain.field(seed + 4, dx + 22.0 * q1, dz + 22.0 * q2, 10.0);
        float[] l = Terrain.uv(x, z, cx, cz, angle);
        float s = h + 9.0F * r + 5.0F * q1 + 0.12F * l[0];
        return BANDS[Math.floorMod(Mth.floor(s), BANDS.length)];
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float d = trough(x + 0.5, z + 0.5);
                if (d >= SEAL) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 6 || ground > b.maxY() - 16) continue;
                // a puddle is filled in; deeper water, and any just outside, is sealed in where the rock beside it is worn down
                int pond = 0;
                while (pond < 4 && !level.getFluidState(pos.set(x, ground + 1 + pond, z)).isEmpty()) pond++;
                if (pond >= 4 || d >= APRON) {
                    seal(level, pos, b, x, z, ground);
                    continue;
                }
                for (int h = ground + 1; h <= ground + pond; h++) Terrain.set(level, pos, x, h, z, Blocks.SANDSTONE.defaultBlockState());
                ground += pond;
                int top;
                if (d < 1.0F) {
                    top = Math.round(hollow(x + 0.5, z + 0.5, d));
                } else {
                    // bare slickrock round the trough, in low rounded swells, easing into the land beyond
                    float t = Terrain.smooth((d - 1.0F) / (APRON - 1.0F));
                    float swell = 1.5F * Terrain.field(seed + 5, x, z, 7.0);
                    top = Math.round(Mth.lerp(t, y + swell, ground));
                    // the outer edge breaks up into patches of bare rock
                    if (t > 0.6F && Terrain.noise(seed, x, 0, z) < (t - 0.6F) * 2.5F) continue;
                }
                Terrain.clearAbove(level, pos, x, ground, z, Math.min(12, b.maxY() - ground));
                for (int h = top + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                // smooth, solid rock right through, banded all the way down
                for (int h = Math.min(ground, top) - 3; h <= top; h++) Terrain.set(level, pos, x, h, z, band(x, h, z));
            }
        }
    }
}
