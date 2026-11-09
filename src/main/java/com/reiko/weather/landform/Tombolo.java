package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A tombolo: a bar of sand tying a small island to the mainland. Waves bending round the island from both sides meet
 * in its shelter and drop their sand there, and the bar grows out from the beach until it reaches the island. It
 * lies so low that the sea washes over its edges, with open water on either side, so at high tide the island is
 * nearly an island again.
 *
 * @param angle the way out to sea, toward the island
 * @param dist  how far offshore the middle of the island is
 */
public record Tombolo(int cx, int cz, int sea, float angle, int dist, int radius, int height, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        return c.search(12, 12, Tombolo::planAt);
    }

    @Nullable
    private static Landform planAt(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() - 2 || ground > c.seaLevel() + 4) {
            return null;
        }
        float seaward = c.seaward(24, 2);
        if (Float.isNaN(seaward)) {
            return null;
        }
        // there has to be open water between the beach and the island
        if (out(c, seaward, 8) >= c.seaLevel()) {
            return null;
        }
        int radius = 5 + c.random().nextInt(4);
        int dist = -1;
        for (int d = 16; d <= 32; d += 4) {
            if (out(c, seaward, d) >= c.seaLevel()) {
                dist = Math.min(34, d + radius / 2); // an island already there: tie the bar to it
                break;
            }
        }
        if (dist < 0) {
            dist = 16 + 4 * c.random().nextInt(4);
            if (out(c, seaward, dist) < c.seaLevel() - 14) {
                return null; // too deep to build an island in
            }
        }
        return new Tombolo(c.x(), c.z(), c.seaLevel(), seaward, dist, radius, 6 + c.random().nextInt(5), c.random().nextLong());
    }

    private static int out(PlanContext c, float angle, int d) {
        return c.groundAt(c.x() + Math.round(Mth.cos(angle) * d), c.z() + Math.round(Mth.sin(angle) * d));
    }

    static Landform load(CompoundTag tag) {
        return new Tombolo(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getFloat("angle"), tag.getInt("dist"), tag.getInt("r"),
                tag.getInt("height"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("sea", sea);
        tag.putFloat("angle", angle);
        tag.putInt("dist", dist);
        tag.putInt("r", radius);
        tag.putInt("height", height);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.TOMBOLO;
    }

    @Override
    public BoundingBox bounds() {
        float far = dist + radius * 1.6F + 3.0F, wide = Math.max(radius * 1.6F + 4.0F, 14.0F);
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (float u : new float[]{-8.0F, far}) {
            for (float v : new float[]{-wide, wide}) {
                double[] w = Terrain.xz(u, v, cx, cz, angle);
                minX = Math.min(minX, Mth.floor(w[0]));
                maxX = Math.max(maxX, Mth.floor(w[0]));
                minZ = Math.min(minZ, Mth.floor(w[1]));
                maxZ = Math.max(maxZ, Mth.floor(w[1]));
            }
        }
        return new BoundingBox(minX, sea - 24, minZ, maxX, sea + height + 10, maxZ);
    }

    /** How far the bar has swung aside {@code u} blocks out from the beach. */
    private float bend(float u) {
        return 4.0F * (Terrain.hash(seed, 1) - 0.5F) * Mth.sin(Mth.PI * Mth.clamp(u / dist, 0.0F, 1.0F));
    }

    /** Half the bar's width: narrowest in the middle, splaying out where it meets the beach and the island. */
    private float barWidth(float u) {
        return 2.2F + 0.9F * Terrain.field(seed, u, 0.0, 9.0) + 3.0F * Terrain.smooth((3.0F - u) / 5.0F)
                + 2.5F * Terrain.smooth((u - (dist - radius - 3.0F)) / 4.0F);
    }

    /** How far into the island a column is (below 1 is the island itself). */
    private float island(int x, int z, float u, float v) {
        return Mth.sqrt(Mth.square(u - dist) + v * v) / (radius * (0.85F + 0.3F * Terrain.field(seed, x, z, 5.0)));
    }

    /** The rocky knob's height at a point on the island. */
    private int knob(float d) {
        return sea + Math.round(height * Mth.sqrt(Math.max(0.0F, 1.0F - d * d)));
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState sand = Blocks.SAND.defaultBlockState();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float u = l[0], v = l[1];
                float di = island(x, z, u, v);
                float off = Math.abs(v - bend(u)), hw = barWidth(u);
                boolean onBar = u > -6.0F && u < dist && off < hw + 5.0F;
                if (di > 1.5F && !onBar) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground > b.maxY() - 10) continue;
                float n = Terrain.noise(seed, x, sea, z);
                int top = ground;
                BlockState surface = null;
                if (onBar) {
                    if (off < hw) {
                        // the bar: its crest just clear of the water, its edges awash
                        top = Math.max(ground, off < hw * 0.55F ? sea : sea - 1);
                        surface = n < 0.12F ? Blocks.GRAVEL.defaultBlockState() : sand;
                    } else if (u > 4.0F && u < dist - radius) {
                        // the sea either side of it, never shallower than a couple of blocks
                        float t = Terrain.smooth((off - hw) / 5.0F);
                        top = ground > sea - 2 ? sea - 2 : Math.max(ground, Math.round(Mth.lerp(t, sea - 2, ground)));
                        surface = sand;
                    }
                }
                boolean rock = false;
                if (di < 1.0F) {
                    int k = knob(di);
                    if (k > ground) {
                        top = Math.max(top, k);
                        rock = true;
                    }
                } else if (di < 1.5F) {
                    // tumbled rocks round the island's foot, down to the sea bed
                    int foot = Math.round(Mth.lerp(Terrain.smooth((di - 1.0F) / 0.5F), sea - 1, ground));
                    if (foot > top) {
                        top = foot;
                        surface = n < 0.4F ? Blocks.COBBLESTONE.defaultBlockState() : n < 0.7F ? Blocks.GRAVEL.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
                    }
                }
                if (top == ground && surface == null && !rock) continue;
                Terrain.clearAbove(level, pos, x, ground, z, Math.max(0, top - ground) + 8);
                for (int h = top + 1; h <= ground; h++) {
                    Terrain.set(level, pos, x, h, z, h <= sea - 1 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
                if (rock) {
                    // the knob: grey stone and andesite, with a skin of turf where it isn't too steep
                    boolean turf = top >= sea + 2 && di < 0.72F;
                    for (int h = Math.max(ground + 1, b.minY()); h <= top; h++) {
                        BlockState s = Terrain.field(seed + 1, x * 1.3 + h * 0.7, z * 1.3 - h * 0.5, 4.0) > 0.55F ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
                        if (turf && h == top) s = Blocks.GRASS_BLOCK.defaultBlockState();
                        else if (turf && h == top - 1) s = Blocks.DIRT.defaultBlockState();
                        Terrain.set(level, pos, x, h, z, s);
                    }
                    if (turf && n > 0.55F) Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                    continue;
                }
                for (int h = Math.max(ground + 1, b.minY()); h < top; h++) Terrain.set(level, pos, x, h, z, sand);
                Terrain.set(level, pos, x, top, z, surface == null ? sand : surface);
                if (top < sea - 2 && n > 0.85F) Terrain.set(level, pos, x, top + 1, z, Blocks.SEAGRASS.defaultBlockState());
            }
        }
        // a couple of small trees on the island's top (once the ground is all in, so nothing clears them away again)
        for (int k = 0; k < 2; k++) {
            double[] w = Terrain.xz(dist + (Terrain.hash(seed, 20 + k) - 0.5F) * radius * 0.7F, (Terrain.hash(seed, 30 + k) - 0.5F) * radius * 0.7F, cx, cz, angle);
            int tx = Mth.floor(w[0]), tz = Mth.floor(w[1]);
            if (tx < area.minX() - 2 || tx > area.maxX() + 2 || tz < area.minZ() - 2 || tz > area.maxZ() + 2) continue;
            float[] l = Terrain.uv(tx + 0.5, tz + 0.5, cx, cz, angle);
            float di = island(tx, tz, l[0], l[1]);
            int k0 = knob(di);
            // only on rock this built (a tree's other half may be built from the next chunk, so both have to agree)
            if (di > 0.6F || k0 < sea + 2 || Terrain.ground(level, pos, tx, tz) > k0) continue;
            boolean cold = level.getBiome(pos.set(tx, k0, tz)).value().getBaseTemperature() < 0.3F;
            tree(level, area, pos, tx, k0 + 1, tz, 3 + Math.round(Terrain.hash(seed, 40 + k)), cold);
        }
    }

    /** A small tree, its leaves only placed into air and inside the chunk being built. */
    private static void tree(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos, int x, int y, int z, int height, boolean spruce) {
        BlockState log = (spruce ? Blocks.SPRUCE_LOG : Blocks.OAK_LOG).defaultBlockState();
        BlockState leaves = (spruce ? Blocks.SPRUCE_LEAVES : Blocks.OAK_LEAVES).defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int k = 0; k < height; k++) Terrain.setIn(level, area, pos, x, y + k, z, log);
        int crown = y + height - 1;
        for (int dy = -2; dy <= 2; dy++) {
            int r = spruce ? (dy >= 1 ? 0 : dy == 0 ? 1 : dy == -1 ? 2 : 1) : (dy == -2 ? -1 : dy <= 0 ? 2 : dy == 1 ? 1 : 0);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (r >= 2 && Math.abs(dx) == r && Math.abs(dz) == r) continue;
                    if (dx == 0 && dz == 0 && dy <= 0) continue; // the trunk
                    if (!area.isInside(x + dx, crown + dy, z + dz)) continue;
                    pos.set(x + dx, crown + dy, z + dz);
                    if (level.getBlockState(pos).isAir()) level.setBlock(pos, leaves, Terrain.FLAGS);
                }
            }
        }
    }
}
