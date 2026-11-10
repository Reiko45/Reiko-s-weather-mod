package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Cockpit karst: limestone country in the wet tropics, worn into a tight egg-box of steep, rounded, wooded hills
 * packed shoulder to shoulder. Rain soaking into limestone dissolves it fastest wherever the water gathers to drain
 * away underground, so the low spots sank deeper and deeper into closed, star-shaped hollows between the hills (the
 * "cockpits", named in Jamaica after the pits where cockfights were held), and the hills were left standing as
 * cones between them, hung with vines on their steepest sides.
 *
 * @param cell how far apart the hills stand
 */
public record CockpitKarst(int cx, int cz, int y, int cell, long seed) implements Landform {
    private static final int SPREAD = 64;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 1 || c.relief(SPREAD / 2, 10) > 14 || !c.fits(ground, 10, 64)) {
            return null;
        }
        return new CockpitKarst(c.x(), c.z(), ground, 14 + c.random().nextInt(5), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new CockpitKarst(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("cell"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("cell", cell);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.COCKPIT_KARST;
    }

    @Override
    public BoundingBox bounds() {
        int r = SPREAD + 3; // (the bushes' leaves reach a block past the hills)
        return new BoundingBox(cx - r, y - 8, cz - r, cx + r, y + 64, cz + r);
    }

    /**
     * How high the hills rise over the ground at a point. One hill stands in each cell of a jittered grid, so they fill
     * the country; where two meet there's a saddle, and where three or four meet, a cockpit sunk far below their tops.
     */
    private float rise(double x, double z) {
        int gx = Mth.floor((x - cx) / cell), gz = Mth.floor((z - cz) / cell);
        float best = 0.0F;
        for (int kx = gx - 1; kx <= gx + 1; kx++) {
            for (int kz = gz - 1; kz <= gz + 1; kz++) {
                int id = (kx * 7919 + kz * 31337) * 8;
                float hx = cx + (kx + 0.5F + (Terrain.hash(seed, id) - 0.5F) * 0.7F) * cell;
                float hz = cz + (kz + 0.5F + (Terrain.hash(seed, id + 1) - 0.5F) * 0.7F) * cell;
                float hr = cell * (0.75F + 0.2F * Terrain.hash(seed, id + 2));
                float hh = 12.0F + 13.0F * Terrain.hash(seed, id + 3);
                float dx = (float) (x - hx), dz = (float) (z - hz);
                float theta = (float) Mth.atan2(dz, dx);
                float wobble = 1.0F + 0.12F * Mth.sin(3.0F * theta + Terrain.hash(seed, id + 4) * Mth.TWO_PI)
                        + 0.06F * Mth.sin(5.0F * theta + Terrain.hash(seed, id + 5) * Mth.TWO_PI);
                float t = Mth.sqrt(dx * dx + dz * dz) / (hr * wobble);
                // a steep cone, rounded off at the top
                if (t < 1.0F) best = Math.max(best, hh * (1.0F - (float) Math.pow(t, 1.4)));
            }
        }
        // the hills dwindle away at the edge of the karst
        double ex = x - cx, ez = z - cz;
        float edge = (SPREAD - (float) Math.sqrt(ex * ex + ez * ez) * (1.0F + 0.25F * Terrain.field(seed, x, z, 20.0))) / 18.0F;
        return best * Terrain.smooth(edge);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState limestone = ModBlocks.LIMESTONE.get().defaultBlockState();
        int minX = Math.max(area.minX(), b.minX()), maxX = Math.min(area.maxX(), b.maxX());
        int minZ = Math.max(area.minZ(), b.minZ()), maxZ = Math.min(area.maxZ(), b.maxZ());
        if (minX > maxX || minZ > maxZ) return;
        int w = maxX - minX + 1;
        int[] tops = new int[w * (maxZ - minZ + 1)];
        byte[] kind = new byte[tops.length]; // 0 untouched, 1 grassy slope, 2 bare cliff
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int i = (x - minX) + (z - minZ) * w;
                float e = rise(x + 0.5, z + 0.5);
                int ground = Terrain.ground(level, pos, x, z);
                // (and under any water, which leaves overhanging it can hide from Terrain.ground)
                while (ground > b.minY() && !level.getFluidState(pos.set(x, ground, z)).isEmpty()) ground--;
                tops[i] = ground;
                int add = Math.round(e);
                if (add < 1 || ground < b.minY() + 2 || ground + add > b.maxY() - 8) continue;
                int top = ground + add;
                // anything under water stays bare: grass won't grow there
                int surf = ground;
                for (pos.set(x, ground + 1, z); surf < ground + 12 && !level.getFluidState(pos).isEmpty(); pos.move(Direction.UP)) surf++;
                Terrain.clearAbove(level, pos, x, ground, z, Math.min(add + 20, b.maxY() - ground));
                float slope = (Math.abs(rise(x + 1.5, z + 0.5) - rise(x - 0.5, z + 0.5)) + Math.abs(rise(x + 0.5, z + 1.5) - rise(x + 0.5, z - 0.5))) / 2.0F;
                boolean cliff = slope > 2.2F, drowned = top <= surf;
                for (int h = ground + 1; h <= top; h++) {
                    BlockState s;
                    if (!cliff && h == top) {
                        s = drowned ? Blocks.GRAVEL.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                    } else if (!cliff && h >= top - 2) {
                        s = Blocks.DIRT.defaultBlockState();
                    } else {
                        // streaks of darker, wetter rock down the faces, and moss where the water seeps out
                        float streak = Terrain.field(seed + 1, x * 3.0 + z * 3.0, h * 0.3, 4.0);
                        s = streak > 0.8F ? Blocks.STONE.defaultBlockState()
                                : cliff && h > top - 3 && streak < 0.14F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : limestone;
                    }
                    Terrain.set(level, pos, x, h, z, s);
                }
                tops[i] = top;
                kind[i] = (byte) (drowned ? 0 : cliff ? 2 : 1);
            }
        }
        // once the hills are all in: the undergrowth on top, and the vines down the steep sides
        BlockState leaves = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int i = (x - minX) + (z - minZ) * w;
                if (kind[i] == 0) continue;
                int top = tops[i];
                float n = Terrain.noise(seed, x, top, z);
                if (kind[i] == 1 && n > 0.95F) {
                    // a jungle bush: a stub of trunk in a ball of leaves
                    Terrain.set(level, pos, x, top + 1, z, Blocks.JUNGLE_LOG.defaultBlockState());
                    Terrain.set(level, pos, x, top + 2, z, leaves);
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        int nx = x + d.getStepX(), nz = z + d.getStepZ();
                        for (int h = top + 1; h <= top + 2; h++) {
                            if (h == top + 2 && Terrain.noise(seed + 2, nx, h, nz) < 0.5F || !area.isInside(nx, h, nz)) continue;
                            pos.set(nx, h, nz);
                            if (level.getBlockState(pos).isAir()) level.setBlock(pos, leaves, Terrain.FLAGS);
                        }
                    }
                } else if (kind[i] == 1 && n < 0.07F) {
                    pos.set(x, top + 1, z);
                    if (level.getBlockState(pos).isAir()) Terrain.set(level, pos, x, top + 1, z, Blocks.FERN.defaultBlockState());
                }
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    int nx = x + d.getStepX(), nz = z + d.getStepZ();
                    if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) continue;
                    int below = tops[(nx - minX) + (nz - minZ) * w];
                    if (below > top - 3 || Terrain.noise(seed + 3 + d.ordinal(), x, 5, z) > 0.35F) continue;
                    int start = top - 1 - Math.round(Terrain.noise(seed, x, 6, z) * 3.0F);
                    int end = Math.max(Math.max(below + 1, b.minY()), start - 3 - Math.round(Terrain.noise(seed, x, 7, z) * 10.0F));
                    BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(d.getOpposite()), true);
                    for (int h = start; h >= end; h--) {
                        if (!area.isInside(nx, h, nz)) continue;
                        pos.set(nx, h, nz);
                        if (!level.getBlockState(pos).isAir()) break;
                        level.setBlock(pos, vine, Terrain.FLAGS);
                    }
                }
            }
        }
    }
}
