package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;

/**
 * A waterfall: a stream running to the edge of a cliff and pouring off it into a deep plunge pool it has dug
 * out at the foot, in a notch it has cut back into the hillside. (The spray, and the rainbow in it, are the
 * client's job: it finds falling water by itself.) Behind most falls there's a cave: the spray and the swirl of the
 * plunge pool wear away the softer rock at the foot of the cliff faster than the hard band at the lip, until the
 * cliff is undercut into a hollow you can walk into along a ledge, behind the curtain of falling water, its stone
 * damp and furred with moss and hung with a few little dripstones.
 *
 * @param lx    the lip, where the water goes over
 * @param top   the height of the ground at the lip
 * @param angle the way the water falls (downhill)
 * @param foot  the height of the ground at the bottom of the drop
 * @param cave  whether there's a cave behind the falls (not on waterfalls from before there were any)
 */
public record Waterfall(int lx, int lz, int top, float angle, int foot, int stream, int pool, boolean cave, long seed) implements Landform {
    public Waterfall(int lx, int lz, int top, float angle, int foot, int stream, int pool, long seed) {
        this(lx, lz, top, angle, foot, stream, pool, false, seed);
    }

    @Nullable
    static Landform plan(PlanContext c) {
        int start = c.groundAt(c.x(), c.z());
        if (start < c.seaLevel() + 8 || !c.fits(start, 10, 30)) {
            return null;
        }
        // look round for the steepest drop
        float bestScore = 0.0F, bestAngle = 0.0F;
        int bestLip = 0, bestTop = 0, bestFoot = 0;
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F;
            float ux = Mth.cos(a), uz = Mth.sin(a);
            int previous = start, lip = -1, lipHeight = 0;
            for (int s = 2; s <= 26; s += 2) {
                int h = c.groundAt(c.x() + Math.round(ux * s), c.z() + Math.round(uz * s));
                if (lip < 0 && previous - h >= 3) {
                    lip = s - 2;
                    lipHeight = previous;
                }
                if (lip >= 0 && previous - h <= 1) {
                    int drop = lipHeight - previous, run = s - 2 - lip;
                    float score = drop / (float) Math.max(2, run);
                    if (drop >= 8 && run <= 10 && previous > c.seaLevel() && score > bestScore) {
                        bestScore = score;
                        bestAngle = a;
                        bestLip = lip;
                        bestTop = lipHeight;
                        bestFoot = previous;
                    }
                    break;
                }
                previous = h;
            }
        }
        if (bestScore <= 0.0F) {
            return null;
        }
        int lx = c.x() + Math.round(Mth.cos(bestAngle) * bestLip), lz = c.z() + Math.round(Mth.sin(bestAngle) * bestLip);
        int stream = 10 + c.random().nextInt(8), pool = 3 + c.random().nextInt(3);
        long seed = c.random().nextLong();
        return new Waterfall(lx, lz, bestTop, bestAngle, bestFoot, stream, pool, c.random().nextFloat() < 0.7F, seed);
    }

    static Landform load(CompoundTag tag) {
        return new Waterfall(tag.getInt("x"), tag.getInt("z"), tag.getInt("top"), tag.getFloat("angle"), tag.getInt("foot"),
                tag.getInt("stream"), tag.getInt("pool"), tag.getBoolean("cave"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", lx);
        tag.putInt("z", lz);
        tag.putInt("top", top);
        tag.putFloat("angle", angle);
        tag.putInt("foot", foot);
        tag.putInt("stream", stream);
        tag.putInt("pool", pool);
        tag.putBoolean("cave", cave);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.WATERFALL;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.max(stream, pool * 2 + 6) + 8;
        return new BoundingBox(lx - r, foot - 6, lz - r, lx + r, top + 30, lz + r);
    }

    private float[] local(double x, double z) {
        double dx = x - lx, dz = z - lz, c = Mth.cos(angle), s = Mth.sin(angle);
        return new float[]{(float) (dx * c + dz * s), (float) (-dx * s + dz * c)};
    }

    /** The stream's meanders above the falls (none right at the lip). */
    private float meander(float u) {
        return u > -3.0F ? 0.0F : 1.6F * Mth.sin(u * 0.35F + Terrain.hash(seed, 1) * 6.0F) * Mth.clamp((-u - 3.0F) / 4.0F, 0.0F, 1.0F);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float poolCentre = pool + 0.5F;
        int surface = foot - 1;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = local(x + 0.5, z + 0.5);
                float u = l[0], v = l[1];
                int ground = Terrain.ground(level, pos, x, z), standing = Terrain.surface(level, pos, x, ground, z);
                float dp = (float) Math.sqrt((u - poolCentre) * (u - poolCentre) + v * v);
                if (dp < pool + 1.6F) {
                    // (no hollow in the rock round the pool for its water to find)
                    for (int h = surface - 4; h <= Math.min(ground, foot); h++) {
                        BlockState here = level.getBlockState(pos.set(x, h, z));
                        if (here.isAir() || !here.getFluidState().isEmpty()) Terrain.set(level, pos, x, h, z, Blocks.STONE.defaultBlockState());
                    }
                }
                if (cave && u <= 0.5F) alcove(level, pos, x, z, u, v, ground);
                if (cave && ledge(x, z, u, v)) {
                    ledge(level, pos, b, x, z, Math.max(ground, standing));
                    continue;
                }
                if (u <= 0.5F && u >= -stream - 1.5F) {
                    // the stream: a channel cut into the cliff top, a block of water over a stony bed
                    float off = Math.abs(v - meander(u));
                    if (off < 3.0F) {
                        // (no hollow in the rock beside it for its water to find)
                        for (int h = top - 3; h <= Math.min(top, ground); h++) {
                            BlockState here = level.getBlockState(pos.set(x, h, z));
                            if (here.isAir() || !here.getFluidState().isEmpty()) Terrain.set(level, pos, x, h, z, Blocks.STONE.defaultBlockState());
                        }
                    }
                    if (ground > top + 4) continue; // where the land rises steeply the stream starts, from a spring
                    if (off < 1.3F && u >= -stream) {
                        Terrain.clearAbove(level, pos, x, Math.max(ground, standing), z, 12);
                        for (int h = top; h <= Math.max(ground, standing); h++) Terrain.set(level, pos, x, h, z, air(level, pos, x, h, z));
                        // (on sound rock, wherever the ground dips under it)
                        for (int h = Math.max(b.minY(), Math.min(ground + 1, top - 3)); h <= top - 3; h++) {
                            BlockState here = level.getBlockState(pos.set(x, h, z));
                            if (h > ground || here.isAir() || !here.getFluidState().isEmpty()) Terrain.set(level, pos, x, h, z, Blocks.STONE.defaultBlockState());
                        }
                        Terrain.set(level, pos, x, top - 2, z, Terrain.noise(seed, x, 0, z) < 0.5F ? Blocks.GRAVEL.defaultBlockState() : Blocks.STONE.defaultBlockState());
                        Terrain.set(level, pos, x, top - 1, z, Blocks.WATER.defaultBlockState());
                        if (u > -0.5F) {
                            level.scheduleTick(pos.set(x, top - 1, z), Fluids.WATER, 1); // set it pouring over the edge
                        }
                    } else if (off < 3.0F && ground < top) {
                        // banks to keep it in its channel (and closing it off at its head)
                        for (int h = Math.max(b.minY(), ground + 1); h <= top; h++) Terrain.set(level, pos, x, h, z, Blocks.STONE.defaultBlockState());
                    }
                    continue;
                }
                if (u <= 0.5F) continue;
                // below the lip: the notch the falls have cut back into the hillside, and the pool at the bottom (taking in
                // every column the water comes down in)
                float gorge = 2.0F + u * 0.35F;
                boolean inPool = dp < pool || besideLip(x, z);
                if (!inPool && dp < pool + 1.6F && ground < foot) {
                    // a lip of rock round the pool, so it doesn't drain away downhill
                    for (int h = Math.max(b.minY(), ground + 1); h <= foot; h++) Terrain.set(level, pos, x, h, z, Blocks.STONE.defaultBlockState());
                    ground = foot;
                }
                if (!inPool && (Math.abs(v) > gorge || u > poolCentre + pool + 3)) continue;
                Terrain.clearAbove(level, pos, x, ground, z, 30);
                int floor = inPool ? surface - (dp < pool * 0.5F ? 3 : 2) : foot;
                // (sound rock under it, with no hollow for the pool's water to find)
                for (int h = surface - 4; h < Math.min(floor, ground + 1); h++) {
                    BlockState here = level.getBlockState(pos.set(x, h, z));
                    if (here.isAir() || !here.getFluidState().isEmpty()) Terrain.set(level, pos, x, h, z, Blocks.STONE.defaultBlockState());
                }
                for (int h = floor + 1; h <= Math.max(Math.max(ground, standing), top + 2); h++) {
                    Terrain.set(level, pos, x, h, z, inPool && h <= surface ? Blocks.WATER.defaultBlockState() : air(level, pos, x, h, z));
                }
                if (floor < ground) {
                    Terrain.set(level, pos, x, floor, z, inPool ? (Terrain.noise(seed, x, 1, z) < 0.6F ? Blocks.GRAVEL.defaultBlockState()
                            : Blocks.MOSSY_COBBLESTONE.defaultBlockState()) : Blocks.STONE.defaultBlockState());
                } else {
                    // build the rim of the pool up so the water stays in
                    for (int h = Math.max(b.minY(), ground + 1); h <= floor; h++) Terrain.set(level, pos, x, h, z, Blocks.STONE.defaultBlockState());
                }
                if (!inPool && Terrain.noise(seed, x, 2, z) < 0.3F) {
                    Terrain.set(level, pos, x, floor, z, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                }
            }
        }
    }

    /**
     * Air, unless a pond in a column beside it that the waterfall never cuts down into would spill into it there (then
     * rock). Water at a height the waterfall always builds such a column up past (its banks and the rim of its pool)
     * doesn't count: it won't be there once the waterfall is built, whichever is built first.
     */
    private BlockState air(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int h, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int nx = x + d.getStepX(), nz = z + d.getStepZ();
            if (untouched(nx, nz) && h > filled(nx, nz) && !level.getFluidState(pos.set(nx, h, nz)).isEmpty()) return Blocks.STONE.defaultBlockState();
        }
        return Blocks.AIR.defaultBlockState();
    }

    /** How high a column the waterfall leaves standing is built up to at least, its banks and the rim of its pool. */
    private int filled(int x, int z) {
        float[] l = local(x + 0.5, z + 0.5);
        float u = l[0], v = l[1];
        if (u <= 0.5F) return u >= -stream - 1.5F && Math.abs(v - meander(u)) < 3.0F ? top : Integer.MIN_VALUE;
        return Mth.square(u - pool - 0.5F) + v * v < Mth.square(pool + 1.6F) ? foot : Integer.MIN_VALUE;
    }

    /** Whether a column beyond the lip is beside one the stream pours over it from (where the falling water comes down). */
    private boolean besideLip(int x, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            float[] n = local(x + 0.5 + d.getStepX(), z + 0.5 + d.getStepZ());
            if (lip(n[0], n[1])) return true;
        }
        return false;
    }

    /** The cave's floor: a block or two over the plunge pool's water. */
    private int caveFloor() {
        return foot + (Terrain.hash(seed, 23) < 0.5F ? 0 : 1);
    }

    /** How high the cave is at its mouth (it gets lower toward the back), leaving sound rock under the stream above it. */
    private int caveHeight() {
        return Math.min(3 + (int) (Terrain.hash(seed, 24) * 2.99F), top - 4 - caveFloor());
    }

    /** Which side of the pool the ledge in behind the falls runs along. */
    private float caveSide() {
        return Terrain.hash(seed, 25) < 0.5F ? -1.0F : 1.0F;
    }

    /** How far into the cave a spot behind the cliff face is (under 1 inside it, 0 in the middle of its mouth). */
    private float inCave(float u, float v) {
        float depth = 3.0F + (int) (Terrain.hash(seed, 21) * 3.99F), half = 1.5F + Terrain.hash(seed, 22);
        return Mth.sqrt(Mth.square((0.5F - u) / depth) + Mth.square(v / half));
    }

    /** Whether a column is one the stream runs over the lip from (where the falls come down beside it). */
    private boolean lip(float u, float v) {
        return u <= 0.5F && u >= -stream && Math.abs(v - meander(u)) < 1.3F;
    }

    /**
     * Whether a column is the ledge in behind the falls: a strip round the near half of the pool on one side, at the
     * cliff foot, where it runs into the cave's mouth (but never where the falls come down).
     */
    private boolean ledge(int x, int z, float u, float v) {
        if (u <= 0.5F || v * caveSide() <= 0.0F || u > 2.0F * pool + 0.5F) return false;
        float dp = Mth.sqrt(Mth.square(u - pool - 0.5F) + v * v);
        if (dp < pool - 1.0F || dp >= pool + 1.0F) return false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            float[] n = local(x + 0.5 + d.getStepX(), z + 0.5 + d.getStepZ());
            if (lip(n[0], n[1])) return false;
        }
        return true;
    }

    /** The ledge: a strip of damp rock a block over the pool's water, open to the sky like the notch the falls have cut. */
    private void ledge(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, int ground) {
        Terrain.clearAbove(level, pos, x, ground, z, 30);
        // (rock right down past the bottom of the pool beside it, holding its water in)
        for (int h = foot - 5; h <= foot; h++) {
            float n = Terrain.noise(seed, x, h, z);
            Terrain.set(level, pos, x, h, z, h < foot ? Blocks.STONE.defaultBlockState()
                    : n < 0.45F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : n < 0.6F ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState());
        }
        for (int h = foot + 1; h <= Math.min(b.maxY(), Math.max(ground, top + 2)); h++) Terrain.set(level, pos, x, h, z, air(level, pos, x, h, z));
    }

    /**
     * Whether a column is one the waterfall never cuts down into (so any pond in it stays): anything but its stream, the
     * notch and pool below the lip, and the ledge.
     */
    private boolean untouched(int x, int z) {
        float[] l = local(x + 0.5, z + 0.5);
        float u = l[0], v = l[1];
        if (u <= 0.5F) return !lip(u, v);
        float dp = Mth.sqrt(Mth.square(u - pool - 0.5F) + v * v);
        boolean notch = Math.abs(v) <= 2.0F + u * 0.35F && u <= pool * 2.0F + 3.5F;
        return !notch && dp >= pool && !besideLip(x, z) && !(cave && ledge(x, z, u, v));
    }

    /**
     * The cave behind the falls, in a column behind the cliff face: its floor and walls of damp, mossy stone, sealed
     * against the plunge pool beside it, with moss underfoot and a little dripstone. It's only cut where the cliff
     * stands high enough over it to leave a sound roof.
     */
    private void alcove(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, float u, float v, int ground) {
        int floor = caveFloor(), height = caveHeight();
        float e = inCave(u, v), side = v * caveSide();
        // (its mouth opens out along the cliff foot toward the ledge)
        boolean mouth = u > -0.5F && side > 0.0F && side < 3.4F;
        int ceiling = e < 1.0F ? floor + Math.max(2, Math.round(height * (1.0F - 0.4F * e * e))) : mouth ? floor + Math.min(3, height) : floor + height;
        if (e >= 1.45F && !mouth || ground < ceiling + 2) return;
        // sound, damp rock under it and round it, down past the bottom of the pool
        for (int h = foot - 5; h < floor; h++) {
            BlockState here = level.getBlockState(pos.set(x, h, z));
            if (here.isAir() || !here.getFluidState().isEmpty()) Terrain.set(level, pos, x, h, z, Blocks.STONE.defaultBlockState());
        }
        if (e >= 1.0F && !mouth) {
            for (int h = floor; h <= ceiling + 1; h++) Terrain.set(level, pos, x, h, z, damp(x, h, z));
            return;
        }
        float n = Terrain.noise(seed + 3, x, floor, z);
        Terrain.set(level, pos, x, floor, z, n < 0.35F ? Blocks.MOSS_BLOCK.defaultBlockState() : n < 0.75F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState());
        for (int h = floor + 1; h <= ceiling; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
        Terrain.set(level, pos, x, ceiling + 1, z, damp(x, ceiling + 1, z));
        float d = Terrain.noise(seed + 4, x, ceiling, z);
        if (n < 0.2F) {
            Terrain.set(level, pos, x, floor + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
        } else if (d < 0.18F && ceiling - floor >= 3) {
            // water dripping from the roof has left a few little stalactites, and a stalagmite under one now and then
            Terrain.set(level, pos, x, ceiling + 1, z, Blocks.DRIPSTONE_BLOCK.defaultBlockState());
            BlockState drip = Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN);
            if (d < 0.07F && ceiling - floor >= 4) {
                Terrain.set(level, pos, x, ceiling, z, drip.setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.FRUSTUM));
                Terrain.set(level, pos, x, ceiling - 1, z, drip.setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP));
            } else {
                Terrain.set(level, pos, x, ceiling, z, drip.setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP));
            }
            if (d < 0.05F) {
                Terrain.set(level, pos, x, floor + 1, z, Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.UP)
                        .setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP));
            }
        }
    }

    /** Damp stone, mossy where the spray reaches it. */
    private BlockState damp(int x, int y, int z) {
        float n = Terrain.noise(seed + 5, x, y, z);
        return n < 0.4F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : n < 0.55F ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                : n < 0.65F ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }
}
