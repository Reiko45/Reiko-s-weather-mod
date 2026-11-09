package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;

/**
 * A hanging valley: a glacial trough with a smaller valley joining it high up one wall. The great glacier in the
 * main valley ground it down into a deep U, with a flat floor and steep, curving walls, far faster than the little
 * glacier in the side valley could keep up with. When the ice melted, the side valley was left hanging, its floor
 * ending at a lip high on the trough wall, and its stream now spills off the lip in a long waterfall to a plunge pool
 * on the trough floor.
 *
 * @param angle    the way the trough runs (downhill)
 * @param side     which wall the side valley joins (+1 or -1 across the trough)
 * @param floor    the trough floor's height where the side valley joins it
 * @param drop     how far the side valley's floor hangs above the trough's
 * @param junction where along the trough the side valley joins it
 * @param top      the highest ground about (for the bounds)
 */
public record HangingValley(int cx, int cz, float angle, int side, int floor, int drop, int half, int junction, int top, long seed) implements Landform {
    /** The trough's walls: how wide, and how high they would reach if the mountains were high enough. */
    private static final float WALL = 14.0F, WALL_HEIGHT = 40.0F;
    /** The side valley: half its floor's width, its walls, and how far it runs flat back from the lip. */
    private static final float SIDE_FLOOR = 3.5F, SIDE_WALL = 8.0F, SIDE_HEIGHT = 22.0F, FLAT = 26.0F, STREAM = 18.0F;
    private static final float POOL = 4.0F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 35 || !c.fits(ground, 30, 60)) {
            return null;
        }
        // the trough runs down the valley
        float[] down = c.downhill(32);
        if (down[1] < 6) {
            return null;
        }
        float a = down[0], ca = Mth.cos(a), sa = Mth.sin(a);
        // the side valley joins high on the higher wall
        int left = c.groundAt(c.x() - Math.round(sa * 30), c.z() + Math.round(ca * 30));
        int right = c.groundAt(c.x() + Math.round(sa * 30), c.z() - Math.round(ca * 30));
        int side = left >= right ? 1 : -1;
        int floor = ground - 6 - c.random().nextInt(4);
        int drop = 12 + c.random().nextInt(10);
        int beyond = c.groundAt(c.x() - Math.round(sa * 48 * side), c.z() + Math.round(ca * 48 * side));
        if (Math.max(left, right) < floor + drop + 4 || beyond < floor + drop + 6) {
            return null;
        }
        int half = 46 + c.random().nextInt(10);
        int downEnd = c.groundAt(c.x() + Math.round(ca * half), c.z() + Math.round(sa * half));
        int upEnd = c.groundAt(c.x() - Math.round(ca * half), c.z() - Math.round(sa * half));
        if (downEnd < floor - 30) {
            return null; // the valley falls away too steeply to have held a glacier's floor
        }
        int top = Math.max(Math.max(ground, upEnd), Math.max(Math.max(left, right), beyond)) + 30;
        return new HangingValley(c.x(), c.z(), a, side, floor, drop, half, Math.round((c.random().nextFloat() - 0.5F) * 20.0F), top, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new HangingValley(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("side"), tag.getInt("floor"), tag.getInt("drop"),
                tag.getInt("half"), tag.getInt("junction"), tag.getInt("top"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("side", side);
        tag.putInt("floor", floor);
        tag.putInt("drop", drop);
        tag.putInt("half", half);
        tag.putInt("junction", junction);
        tag.putInt("top", top);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.HANGING_VALLEY;
    }

    @Override
    public BoundingBox bounds() {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (float u : new float[]{-half, half}) {
            for (float p : new float[]{-(floorWidth() + WALL + 3.0F), sideEnd() + 3.0F}) {
                double[] w = Terrain.xz(u, side * p, cx, cz, angle);
                minX = Math.min(minX, Mth.floor(w[0]));
                maxX = Math.max(maxX, Mth.floor(w[0]));
                minZ = Math.min(minZ, Mth.floor(w[1]));
                maxZ = Math.max(maxZ, Mth.floor(w[1]));
            }
        }
        return new BoundingBox(minX, floorAt(half) - 8, minZ, maxX, top, maxZ);
    }

    /** Half the width of the trough's flat floor. */
    private float floorWidth() {
        return 8.0F + 3.0F * Terrain.hash(seed, 1);
    }

    /** How far from the trough's middle the side valley's floor ends at its lip. */
    private float lip() {
        return floorWidth() + WALL * Mth.sqrt(drop / WALL_HEIGHT);
    }

    private float sideEnd() {
        return lip() + FLAT + 20.0F;
    }

    /** The trough floor, sloping gently down the valley (but level round where the side valley joins). */
    private int floorAt(float u) {
        float d = u - junction;
        return floor - Math.round(Math.signum(d) * Math.max(0.0F, Math.abs(d) - 8.0F) * 0.12F);
    }

    /** How far the side valley's stream wanders from the middle of its floor (not at all near the lip). */
    private float meander(float p) {
        return 1.2F * Mth.sin(p * 0.3F + Terrain.hash(seed, 2) * 6.0F) * Mth.clamp((p - lip() - 3.0F) / 4.0F, 0.0F, 1.0F);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float fw = floorWidth(), lip = lip(), end = sideEnd();
        int hang = floor + drop;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float u = l[0], p = side * l[1], q = u - junction, av = Math.abs(l[1]);
                boolean inSide = p > fw && Math.abs(q) < SIDE_FLOOR + SIDE_WALL + 2.0F && p < end;
                if (Math.abs(u) > half || av > fw + WALL + 2.0F && !inSide) continue;
                int ground = Terrain.ground(level, pos, x, z);
                // the U of the trough: a flat floor curving up into steep walls, fading out at both ends
                int f = floorAt(u);
                float wall = Math.max(0.0F, av - fw) / WALL;
                float ends = Terrain.smooth((half - Math.abs(u)) / 16.0F);
                float target = Mth.lerp(ends, ground, f + WALL_HEIGHT * wall * wall);
                boolean pool = false, stream = false;
                float sideFloor = hang + Math.max(0.0F, p - lip - FLAT) * 0.45F;
                if (p >= lip && p < end) {
                    // the hanging valley: a smaller U, its floor level for a way back from the lip and then climbing
                    float aq = Math.abs(q), st = (aq - SIDE_FLOOR) / SIDE_WALL;
                    float sideTarget = aq < SIDE_FLOOR ? sideFloor : sideFloor + SIDE_HEIGHT * st * st;
                    target = Math.min(target, Mth.lerp(Terrain.smooth((end - p) / 12.0F), ground, sideTarget));
                    stream = p < lip + STREAM && Math.abs(q - meander(p)) < 1.0F;
                } else if (p >= fw - 1.0F && p < lip && Math.abs(q) < SIDE_FLOOR + 1.5F) {
                    // the alcove the falls have cut back into the wall, down to the trough floor
                    target = Math.min(target, floor);
                    pool = Mth.sqrt(Mth.square(p - (lip - POOL + 1.0F)) + q * q) < POOL;
                }
                // the pool's rim stands at the trough floor all round, even where the ground was lower
                if (!pool && p < lip && ground < floor && Mth.sqrt(Mth.square(p - (lip - POOL + 1.0F)) + q * q) < POOL + 1.5F) {
                    for (int h = ground + 1; h <= floor; h++) Terrain.set(level, pos, x, h, z, h == floor ? Blocks.GRAVEL.defaultBlockState() : Blocks.STONE.defaultBlockState());
                    continue;
                }
                int t = Math.min(ground, Math.round(target));
                int clear = Math.max(0, Math.min(30, b.maxY() - ground));
                pos.set(x, ground, z);
                BlockState was = level.getBlockState(pos);
                if (stream) {
                    // the stream on the hanging valley's floor, running to the lip and over it
                    int w = hang - 1;
                    Terrain.clearAbove(level, pos, x, ground, z, Math.min(12, clear));
                    for (int h = w + 1; h <= Math.min(ground, b.maxY()); h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    for (int h = ground + 1; h < w - 1; h++) Terrain.set(level, pos, x, h, z, Blocks.STONE.defaultBlockState());
                    Terrain.set(level, pos, x, w - 1, z, Terrain.noise(seed, x, w, z) < 0.6F ? Blocks.GRAVEL.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                    Terrain.set(level, pos, x, w, z, Blocks.WATER.defaultBlockState());
                    if (p < lip + 1.5F) level.scheduleTick(pos.set(x, w, z).immutable(), Fluids.WATER, 1); // set it pouring over the edge
                    continue;
                }
                if (pool) {
                    // the plunge pool the falls have drilled into the trough floor
                    float d = Mth.sqrt(Mth.square(p - (lip - POOL + 1.0F)) + q * q) / POOL;
                    int depth = 1 + Math.round(2.5F * (1.0F - d * d));
                    Terrain.clearAbove(level, pos, x, ground, z, clear);
                    for (int h = floor; h <= Math.min(ground, b.maxY()); h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    for (int h = ground + 1; h < floor - depth - 1; h++) Terrain.set(level, pos, x, h, z, Blocks.STONE.defaultBlockState());
                    for (int h = floor - depth; h <= floor - 1; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                    Terrain.set(level, pos, x, floor - depth - 1, z, Terrain.noise(seed, x, 1, z) < 0.6F ? Blocks.GRAVEL.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                    continue;
                }
                // banks to keep the stream in its channel, where the ground is lower than the valley floor
                if (p >= lip && p < lip + STREAM + 1.5F && Math.abs(q - meander(p)) < 2.6F && ground < hang) {
                    for (int h = ground + 1; h <= hang; h++) Terrain.set(level, pos, x, h, z, Blocks.STONE.defaultBlockState());
                    continue;
                }
                if (t >= ground) continue;
                Terrain.clearAbove(level, pos, x, ground, z, clear);
                for (int h = t + 1; h <= Math.min(ground, b.maxY()); h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                Terrain.set(level, pos, x, t, z, surface(level, pos, x, t, z, was, av, fw, p, lip, Math.abs(q)));
            }
        }
    }

    /** What the newly bared ground is: rock on the steep walls, scree at their feet, turf or snow on the flats. */
    private BlockState surface(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState was, float av, float fw, float p, float lip, float aq) {
        float n = Terrain.noise(seed, x, y, z);
        boolean snowy = was.is(BlockTags.SNOW) || was.is(Blocks.SNOW_BLOCK) || was.is(Blocks.POWDER_SNOW);
        boolean grassy = !snowy && level.getBiome(pos.set(x, y, z)).value().getBaseTemperature() > 0.15F;
        BlockState flat = snowy ? Blocks.SNOW_BLOCK.defaultBlockState() : grassy ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState();
        if (p >= fw - 1.0F && p < lip && aq < SIDE_FLOOR + 1.5F) {
            return n < 0.5F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState(); // the spray-soaked alcove floor
        }
        if (p >= lip) {
            float st = (aq - SIDE_FLOOR) / SIDE_WALL;
            return aq < SIDE_FLOOR + 1.0F || st * SIDE_HEIGHT * 2.0F / SIDE_WALL < 1.4F ? flat
                    : n < 0.7F ? Blocks.STONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
        }
        float wall = Math.max(0.0F, av - fw) / WALL, slope = 2.0F * WALL_HEIGHT * wall / WALL;
        if (av > fw - 2.0F && av < fw + 2.5F && n < 0.6F) return Blocks.GRAVEL.defaultBlockState(); // scree at the foot of the walls
        if (slope > 1.5F) return n < 0.7F ? Blocks.STONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
        if (av < fw && n > 0.985F) return Blocks.MOSSY_COBBLESTONE.defaultBlockState(); // a stone dropped by the ice
        return flat;
    }
}
