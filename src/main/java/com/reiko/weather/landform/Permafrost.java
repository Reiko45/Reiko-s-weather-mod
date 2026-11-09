package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Permafrost country: ground frozen solid a long way down. Each winter it cracks as it contracts in the cold,
 * and meltwater freezing in the cracks wedges them wider, until the tundra is netted with ice-filled troughs
 * in a pattern of polygons. Here and there a pingo has pushed up, a hill with a core of solid ice that's split
 * open at the top, and where the ice has thawed the ground has slumped into a shallow thaw lake.
 *
 * @param pingo {x, z, base y, radius, height}
 * @param lake {x, z, water y, radius}
 */
public record Permafrost(int cx, int cz, int y, int[] pingo, int[] lake, long seed) implements Landform {
    private static final int RADIUS = 44;
    private static final int CELL = 9;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 1 || c.relief(RADIUS / 2, 10) > 9 || !c.fits(ground, 8, 20)) {
            return null;
        }
        float a = c.random().nextFloat() * Mth.TWO_PI;
        int px = c.x() + Math.round(Mth.cos(a) * 14), pz = c.z() + Math.round(Mth.sin(a) * 14);
        int pr = 9 + c.random().nextInt(5);
        int base = Math.min(c.groundAt(px, pz), Math.min(c.groundAt(px + pr, pz), c.groundAt(px - pr, pz)));
        int[] pingo = {px, pz, base, pr, Math.min(7 + c.random().nextInt(6), pr)};
        int lx = c.x() - Math.round(Mth.cos(a) * 22), lz = c.z() - Math.round(Mth.sin(a) * 22);
        int lr = 7 + c.random().nextInt(6);
        int low = Integer.MAX_VALUE;
        for (int k = 0; k < 10; k++) {
            float b = k * Mth.TWO_PI / 10.0F;
            low = Math.min(low, c.groundAt(lx + Math.round(Mth.cos(b) * (lr + 1)), lz + Math.round(Mth.sin(b) * (lr + 1))));
        }
        int[] lake = {lx, lz, low - 1, low < c.seaLevel() + 1 ? 0 : lr};
        return new Permafrost(c.x(), c.z(), ground, pingo, lake, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Permafrost(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getIntArray("pingo"), tag.getIntArray("lake"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.put("pingo", new IntArrayTag(pingo));
        tag.put("lake", new IntArrayTag(lake));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.PERMAFROST;
    }

    @Override
    public BoundingBox bounds() {
        int r = RADIUS + 8;
        return new BoundingBox(cx - r, y - 16, cz - r, cx + r, y + 28, cz + r);
    }

    /** How near a point is to the edge between two frost polygons (0 right on the crack). */
    private float crack(int x, int z) {
        int gx = Math.floorDiv(x, CELL), gz = Math.floorDiv(z, CELL);
        float d1 = Float.MAX_VALUE, d2 = Float.MAX_VALUE;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                float px = (gx + i + 0.15F + 0.7F * Terrain.noise(seed, gx + i, 1, gz + j)) * CELL;
                float pz = (gz + j + 0.15F + 0.7F * Terrain.noise(seed, gx + i, 2, gz + j)) * CELL;
                float d = Mth.sqrt(Mth.square(x + 0.5F - px) + Mth.square(z + 0.5F - pz));
                if (d < d1) {
                    d2 = d1;
                    d1 = d;
                } else if (d < d2) {
                    d2 = d;
                }
            }
        }
        return (d2 - d1) * 0.5F;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState packed = Blocks.PACKED_ICE.defaultBlockState();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float dx = x + 0.5F - cx, dz = z + 0.5F - cz;
                float d = Mth.sqrt(dx * dx + dz * dz) / (RADIUS * (0.8F + 0.3F * Terrain.field(seed, x, z, 14.0)));
                if (d > 1.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                pos.set(x, ground + 1, z);
                if (!level.getFluidState(pos).isEmpty()) continue;
                pos.set(x, ground, z);
                boolean snowy = level.getBlockState(pos).is(Blocks.SNOW_BLOCK) || level.getBlockState(pos.above()).is(Blocks.SNOW);
                BlockState turf = snowy ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                float n = Terrain.noise(seed, x, ground, z);

                // the pingo
                float pd = Mth.sqrt(Mth.square(x + 0.5F - pingo[0]) + Mth.square(z + 0.5F - pingo[1])) / pingo[3];
                if (pd < 1.0F) {
                    float h = pingo[4] * (1.0F - (float) Math.pow(pd, 1.6));
                    // split open on top, the ice core showing in the cracks
                    boolean crater = pd < 0.22F;
                    if (crater) h -= 2.5F * (1.0F - pd / 0.22F);
                    int top = Math.max(ground, pingo[2] + Math.round(h));
                    float theta = (float) Mth.atan2(z + 0.5F - pingo[1], x + 0.5F - pingo[0]);
                    boolean split = pd < 0.55F && Math.abs(Mth.sin(theta * 2.5F + Terrain.hash(seed, 9) * 6.0F)) < 0.12F + 0.1F * (1.0F - pd);
                    Terrain.clearAbove(level, pos, x, top, z, 8);
                    for (int k = ground + 1; k <= top; k++) {
                        boolean core = pd < 0.75F && k < top - 2 + Math.round(pd * 2.0F);
                        Terrain.set(level, pos, x, k, z, core ? packed : Blocks.DIRT.defaultBlockState());
                    }
                    if (crater || split) {
                        Terrain.set(level, pos, x, top, z, crater && pd < 0.12F ? Blocks.ICE.defaultBlockState() : packed);
                        Terrain.set(level, pos, x, top - 1, z, packed);
                    } else if (top > ground) {
                        Terrain.set(level, pos, x, top, z, n < 0.15F ? Blocks.COARSE_DIRT.defaultBlockState() : turf);
                        if (!snowy && n > 0.8F) Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                    }
                    continue;
                }

                // the thaw lake: shallow, with slumped, muddy shores
                if (lake[3] > 0) {
                    float ld = Mth.sqrt(Mth.square((x + 0.5F - lake[0]) / 1.3F) + Mth.square(z + 0.5F - lake[1]))
                            / (lake[3] * (0.85F + 0.3F * Terrain.field(seed + 1, x, z, 5.0)));
                    if (ld < 1.4F) {
                        int water = lake[2];
                        if (ld < 1.0F) {
                            int depth = 1 + Math.round(2.0F * (1.0F - ld * ld));
                            Terrain.clearAbove(level, pos, x, Math.max(ground, water), z, 10);
                            for (int k = water + 1; k <= ground; k++) Terrain.set(level, pos, x, k, z, Blocks.AIR.defaultBlockState());
                            for (int k = Math.min(ground + 1, water - depth - 1); k <= water - depth; k++) Terrain.set(level, pos, x, k, z, Blocks.MUD.defaultBlockState());
                            for (int k = water - depth + 1; k <= water; k++) Terrain.set(level, pos, x, k, z, Blocks.WATER.defaultBlockState());
                        } else {
                            int bank = Math.max(water + 1, Math.round(Mth.lerp((ld - 1.0F) / 0.4F, water + 1, ground)));
                            Terrain.clearAbove(level, pos, x, Math.max(ground, bank), z, 10);
                            for (int k = bank + 1; k <= ground; k++) Terrain.set(level, pos, x, k, z, Blocks.AIR.defaultBlockState());
                            for (int k = ground + 1; k < bank; k++) Terrain.set(level, pos, x, k, z, Blocks.DIRT.defaultBlockState());
                            Terrain.set(level, pos, x, bank, z, ld < 1.15F || n < 0.3F ? Blocks.MUD.defaultBlockState() : turf);
                        }
                        continue;
                    }
                }

                // the frost polygons: a trough along each crack, with a wedge of ice beneath it
                float c = crack(x, z);
                if (c < 0.9F && d < 0.95F) {
                    Terrain.clearAbove(level, pos, x, ground, z, 6);
                    for (int k = ground - 4; k < ground; k++) Terrain.set(level, pos, x, k, z, packed);
                    Terrain.set(level, pos, x, ground, z, c < 0.45F ? (snowy ? Blocks.ICE.defaultBlockState() : Blocks.WATER.defaultBlockState()) : Blocks.MOSS_BLOCK.defaultBlockState());
                    if (snowy && c >= 0.45F) Terrain.set(level, pos, x, ground + 1, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 2));
                } else if (c < 1.6F) {
                    // the raised rims either side of the trough
                    Terrain.clearAbove(level, pos, x, ground, z, 6);
                    Terrain.set(level, pos, x, ground, z, Blocks.DIRT.defaultBlockState());
                    Terrain.set(level, pos, x, ground + 1, z, turf);
                } else if (!snowy && n > 0.93F && level.getBlockState(pos.set(x, ground, z)).is(Blocks.GRASS_BLOCK)) {
                    Terrain.set(level, pos, x, ground + 1, z, Blocks.SWEET_BERRY_BUSH.defaultBlockState()); // cloudberry-ish
                }
            }
        }
    }
}
