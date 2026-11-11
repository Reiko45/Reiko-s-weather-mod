package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * A kipuka: an island of old ground left standing in a young lava flow. When lava spreads across the country it parts
 * round anything a little higher in its way, or runs short before it can close in, and the patches it never covers
 * keep their soil and their trees, cut off in a sea of bare black rock. The flow's skin is still fresh: smooth, ropy
 * pahoehoe where it ran hot and fluid, and rough, clinkery aa where it cooled and stiffened as it went. Round each
 * island the heat reached a few yards in, so the trees nearest the lava burned where they stood, leaving a rim of
 * baked earth and charred trunks.
 *
 * @param angle   the way the lava flowed (radians)
 * @param islands {x, z, radius} for each kipuka
 */
public record Kipuka(int cx, int cz, int y, int radius, float angle, int[] islands, long seed) implements Landform {
    /** How far in from an island's edge the heat of the lava reached. */
    private static final float SCORCH = 3.0F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 2 || !c.fits(ground, 22, 30)) {
            return null;
        }
        int radius = 35 + c.random().nextInt(16);
        if (c.relief(radius, 12) > 16) {
            return null;
        }
        // the lava came down the lie of the land (any way at all, where it's flat)
        float[] down = c.downhill(radius);
        float angle = down[1] >= 2 ? down[0] : c.random().nextFloat() * Mth.TWO_PI;
        // one or two islands it parted round, each well inside its edge, and on the highest ground it could find
        float room = radius * 0.55F;
        int n = c.random().nextFloat() < 0.45F ? 2 : 1;
        int[] islands = new int[n * 3];
        int k = 0;
        for (int i = 0; i < n; i++) {
            int r = Math.min(i == 0 ? 10 + c.random().nextInt(11) : 10 + c.random().nextInt(5), (int) ((room - 1.0F) / 1.1F));
            float reach = room - r * 1.1F;
            int bx = 0, bz = 0, best = Integer.MIN_VALUE;
            for (int attempt = 0; attempt < 5 - i; attempt++) {
                float a = c.random().nextFloat() * Mth.TWO_PI, d = c.random().nextFloat() * reach;
                int x = c.x() + Math.round(Mth.cos(a) * d), z = c.z() + Math.round(Mth.sin(a) * d);
                if (k > 0 && Mth.sqrt(Mth.square(x - islands[0]) + Mth.square(z - islands[1])) < (r + islands[2]) * 1.1F + 6.0F) continue;
                int h = c.groundAt(x, z);
                if (h > best) {
                    best = h;
                    bx = x;
                    bz = z;
                }
            }
            if (best == Integer.MIN_VALUE) continue;
            islands[k * 3] = bx;
            islands[k * 3 + 1] = bz;
            islands[k * 3 + 2] = r;
            k++;
        }
        return new Kipuka(c.x(), c.z(), ground, radius, angle, Arrays.copyOf(islands, k * 3), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Kipuka(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getFloat("angle"), tag.getIntArray("islands"),
                tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.putFloat("angle", angle);
        tag.put("islands", new IntArrayTag(islands));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.KIPUKA;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * 1.1F) + 2;
        return new BoundingBox(cx - r, y - 20, cz - r, cx + r, y + 28, cz + r);
    }

    /** How far into the flow a point is: 1 in the middle, 0 at its lobed edge, below 0 outside it. */
    private float inside(double x, double z) {
        double dx = x - cx - 0.5, dz = z - cz - 0.5;
        float theta = (float) Math.atan2(dz, dx);
        float edge = radius * (0.88F + 0.08F * Mth.sin(3.0F * theta + Terrain.hash(seed, 1) * Mth.TWO_PI)
                + 0.05F * Mth.sin(5.0F * theta + Terrain.hash(seed, 2) * Mth.TWO_PI) + 0.14F * (Terrain.field(seed + 1, x, z, 12.0) - 0.5F));
        return 1.0F - (float) Math.sqrt(dx * dx + dz * dz) / edge;
    }

    /** How far out from island {@code i}'s middle a point is: under 1 on the island, 1 at its edge. */
    private float island(int i, double x, double z) {
        double dx = x - islands[i * 3] - 0.5, dz = z - islands[i * 3 + 1] - 0.5;
        float theta = (float) Math.atan2(dz, dx);
        float r = islands[i * 3 + 2] * (0.95F + 0.06F * Mth.sin(2.0F * theta + Terrain.hash(seed, 10 + i) * Mth.TWO_PI)
                + 0.08F * (Terrain.field(seed + 20 + i, x, z, 5.0) - 0.5F));
        return (float) Math.sqrt(dx * dx + dz * dz) / r;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                double px = x + 0.5, pz = z + 0.5;
                float f = inside(px, pz);
                if (f <= 0.0F) continue;
                // the nearest island, and how many blocks in from its edge this is (negative out on the lava)
                int near = 0;
                float in = -Float.MAX_VALUE;
                for (int i = 0; i < islands.length / 3; i++) {
                    float d = (1.0F - island(i, px, pz)) * islands[i * 3 + 2];
                    if (d > in) {
                        in = d;
                        near = i;
                    }
                }
                int ground = Terrain.ground(level, pos, x, z);
                // (and under any water that overhanging leaves or weed hid from Terrain.ground)
                while (ground > b.minY() && !level.getFluidState(pos.set(x, ground, z)).isEmpty()) ground--;
                if (ground < b.minY() + 2 || ground > b.maxY() - 8) continue;
                // the lava stopped at the edge of any pond or stream, and never reached the islands' own
                if (!level.getFluidState(pos.set(x, ground + 1, z)).isEmpty()) continue;
                if (in >= 0.0F) {
                    if (in < SCORCH) scorch(level, pos, x, z, ground, near, in);
                    continue;
                }
                flow(level, pos, x, z, f, -in, ground);
            }
        }
    }

    /** Lays the lava over a column: a few blocks of basalt, ropy or clinkery on top. */
    private void flow(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, float f, float out, int ground) {
        float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
        // it ran as pahoehoe near where it came in, and had mostly turned to aa by the time it got further down
        boolean aa = Terrain.field(seed + 3, x, z, 16.0) + 0.4F * l[0] / radius > 0.6F;
        int thick = 1 + Math.round(2.0F * Terrain.smooth(f / 0.35F) * (0.55F + 0.45F * Terrain.field(seed + 2, x, z, 7.0)));
        if (aa && f > 0.06F) thick++; // aa piles up thicker and rubblier
        // it ponded deeper in the hollows, and ran thin over the rises
        if (ground < y - 2) thick++;
        else if (ground > y + 3) thick--;
        thick = Math.min(4, thick);
        // only a low lip where it lapped against an island
        thick = Math.max(1, Math.min(thick, 1 + (int) (out / 2.5F)));
        float n = Terrain.noise(seed, x, 0, z);
        // the broken, jagged clinker of an aa surface, and of every flow front
        boolean clinker = aa || f < 0.06F;
        int top = ground + thick + (clinker && n > 0.78F && f > 0.03F && out > 2.0F ? 1 : 0);
        Terrain.clearAbove(level, pos, x, ground, z, 24);
        // ropes wrinkle the pahoehoe crosswise to the way it flowed, bowed downstream
        Direction.Axis across = Math.abs(Mth.sin(angle)) > Math.abs(Mth.cos(angle)) ? Direction.Axis.X : Direction.Axis.Z;
        boolean rope = !clinker && Mth.sin(l[0] * 1.25F - 0.012F * l[1] * l[1] + 6.0F * Terrain.field(seed + 5, x, z, 7.0)) > 0.62F;
        for (int h = ground + 1; h <= top; h++) {
            float m = Terrain.noise(seed + 6, x, h, z);
            BlockState s;
            if (h == top && clinker) {
                s = m < 0.38F ? Blocks.BLACKSTONE.defaultBlockState() : m < 0.68F ? Blocks.COBBLED_DEEPSLATE.defaultBlockState()
                        : m < 0.92F ? Blocks.BASALT.defaultBlockState() : Blocks.TUFF.defaultBlockState();
            } else if (h == top && rope) {
                s = Blocks.POLISHED_BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, across);
            } else if (h == top) {
                s = m < 0.5F ? Blocks.BASALT.defaultBlockState() : m < 0.82F ? Blocks.SMOOTH_BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
            } else {
                s = m < 0.6F ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
            }
            Terrain.set(level, pos, x, h, z, s);
        }
    }

    /** The island's scorched rim: baked bare earth, burnt stumps and fallen trunks, and the odd splash of lava. */
    private void scorch(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, int i, float in) {
        pos.set(x, ground, z);
        BlockState was = level.getBlockState(pos);
        float n = Terrain.noise(seed + 4, x, ground, z);
        Terrain.clearAbove(level, pos, x, ground, z, 16);
        BlockState surface = was;
        if (was.is(BlockTags.DIRT) && in < SCORCH * (0.55F + 0.55F * Terrain.field(seed + 7, x, z, 4.0))) {
            surface = Blocks.COARSE_DIRT.defaultBlockState();
            Terrain.set(level, pos, x, ground, z, surface);
        }
        if (in < 0.8F && n < 0.16F) {
            // where the lava lapped up over the edge and set
            Terrain.set(level, pos, x, ground + 1, z, n < 0.08F ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
            return;
        }
        // fallen trunks lie round the rim, toppled along it as the lava burned through their roots
        int ix = islands[i * 3], iz = islands[i * 3 + 1], r = islands[i * 3 + 2];
        float theta = (float) Mth.atan2(z - iz, x - ix) + Mth.PI;
        float along = theta / (4.5F / r);
        int arc = Mth.floor(along);
        float frac = along - arc;
        if (in >= 1.0F && in < 2.0F && frac > 0.12F && frac < 0.82F && Terrain.hash(seed, 100 + i * 997 + arc) < 0.3F) {
            Direction.Axis axis = Math.abs(Mth.sin(theta)) > Math.abs(Mth.cos(theta)) ? Direction.Axis.X : Direction.Axis.Z;
            Terrain.set(level, pos, x, ground + 1, z, Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis));
            return;
        }
        if (in >= 0.6F && n > 0.94F) {
            // a burnt stump, still standing
            int height = 1 + (n > 0.975F ? 1 : 0) + (n > 0.993F ? 1 : 0);
            for (int h = 1; h <= height; h++) Terrain.set(level, pos, x, ground + h, z, Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
            return;
        }
        if (n > 0.86F && (surface.is(BlockTags.DIRT) || surface.is(BlockTags.SAND) || surface.is(BlockTags.TERRACOTTA))) {
            Terrain.set(level, pos, x, ground + 1, z, Blocks.DEAD_BUSH.defaultBlockState()); // a shrivelled shrub
        }
    }
}
