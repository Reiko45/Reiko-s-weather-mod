package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A tiankeng ("heavenly pit"): a giant collapse sinkhole. An underground river hollowed a cavern so vast out of the
 * limestone that its roof finally fell in, leaving a round shaft far deeper than it is wide, its sheer walls leaning
 * back a little as they go down and hung with curtains of vines. Down on the floor, on the heaped rubble of the old
 * roof, grows a forest of its own, cut off from the one above, and the river still runs across the bottom, out of one
 * cave and into another.
 *
 * @param y     the lip: the lowest ground round the rim
 * @param river the way the underground river crosses the floor
 */
public record Tiankeng(int cx, int cz, int y, int radius, int depth, float river, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 4) {
            return null;
        }
        int radius = 18 + c.random().nextInt(9);
        int depth = 40 + c.random().nextInt(31);
        int low = ground, high = ground;
        for (int i = 0; i < 12; i++) {
            float a = i * Mth.TWO_PI / 12.0F;
            int h = c.groundAt(c.x() + Math.round(Mth.cos(a) * (radius + 4)), c.z() + Math.round(Mth.sin(a) * (radius + 4)));
            low = Math.min(low, h);
            high = Math.max(high, h);
        }
        // a broad, level stretch of country round it, and room underneath for the whole drop
        if (high - low > 12 || low < c.seaLevel() + 2 || !c.fits(low, depth + 6, high - low + 40)) {
            return null;
        }
        return new Tiankeng(c.x(), c.z(), low, radius, depth, c.random().nextFloat() * Mth.TWO_PI, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Tiankeng(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getInt("depth"), tag.getFloat("river"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.putInt("depth", depth);
        tag.putFloat("river", river);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.TIANKENG;
    }

    private int reach() {
        return Math.round(radius * 1.35F) + 9;
    }

    @Override
    public BoundingBox bounds() {
        int r = reach() + 1;
        return new BoundingBox(cx - r, y - depth - 5, cz - r, cx + r, y + 48, cz + r);
    }

    private float wobble(float theta) {
        return 1.0F + 0.07F * Mth.sin(3.0F * theta + Terrain.hash(seed, 1) * Mth.TWO_PI) + 0.05F * Mth.sin(5.0F * theta + Terrain.hash(seed, 2) * Mth.TWO_PI)
                + 0.025F * Mth.sin(9.0F * theta + Terrain.hash(seed, 3) * Mth.TWO_PI);
    }

    /** Smooth 0-1 noise in three dimensions: layers of {@link Terrain#field}, blended up the height. */
    private static float field3(long seed, double x, double h, double z, double scale) {
        double g = h / scale;
        int k = Mth.floor(g);
        float t = Terrain.smooth((float) (g - k));
        return Mth.lerp(t, Terrain.field(seed + k * 7919L, x, z, scale), Terrain.field(seed + (k + 1) * 7919L, x, z, scale));
    }

    /**
     * How far the wall stands from the middle at a height: sheer at the lip, then leaning back as it goes down (the
     * old cavern's walls), roughened by scars where slabs have fallen away and ledges where harder beds stand out.
     */
    private float wall(int x, int h, int z, float theta) {
        float down = Terrain.smooth((y - 3 - h) / (depth * 0.55F));
        float rough = (field3(seed + 7, x, h, z, 5.0) - 0.5F) * 2.4F;
        if (Math.floorMod(h + Math.round(Terrain.hash(seed, 8) * 7.0F), 7) == 0 && Terrain.field(seed + 9, x, z, 6.0) > 0.5F) rough -= 1.0F;
        return radius * wobble(theta) * (1.0F + 0.13F * down) + rough;
    }

    private float meander(float u) {
        return 2.0F * Mth.sin(u * 0.14F + Terrain.hash(seed, 4) * Mth.TWO_PI);
    }

    /** How far from the middle the river's caves run back into the rock, downstream (u > 0) and upstream. */
    private float caveEnd(float u) {
        return radius * wobble(u > 0.0F ? river : river + Mth.PI) * 1.13F + 6.0F;
    }

    private boolean inCave(float u, float vm) {
        return Math.abs(vm) < 2.6F && Math.abs(u) < caveEnd(u);
    }

    /** How high the cave's roof is over the water, across it and toward its blind end. */
    private int caveRoof(float u, float vm) {
        float arch = 3.2F * Mth.sqrt(Math.max(0.0F, 1.0F - Mth.square(vm / 2.6F))) * Terrain.smooth((caveEnd(u) - Math.abs(u)) / 2.5F);
        return Math.round(arch);
    }

    private float poolU() {
        return (Terrain.hash(seed, 5) - 0.5F) * radius * 0.6F;
    }

    /** How deep the water is at a spot on the floor (0 for dry ground): the river, and the pool it widens into. */
    private int wet(int x, int z, float u, float vm) {
        float pr = 3.5F + Terrain.hash(seed, 6) * 2.5F;
        float pd = Mth.sqrt(Mth.square(u - poolU()) + vm * vm) / (pr * (0.85F + 0.3F * Terrain.field(seed + 4, x, z, 4.0)));
        if (pd < 1.0F) return pd < 0.55F ? 3 : 2;
        return Math.abs(vm) < 1.3F ? 1 : 0;
    }

    /** The top of the floor: rubble from the fallen roof heaped against the walls, cleared by the river from its course. */
    private int floorTop(int x, int z, float d, float vm) {
        float talus = 7.0F * Terrain.smooth((d / radius - 0.5F) / 0.65F) + 3.0F * Terrain.field(seed + 3, x, z, 7.0);
        talus *= Terrain.smooth((Math.abs(vm) - 1.0F) / 5.0F);
        return y - depth + 1 + Math.round(talus);
    }

    private BlockState rock(int x, int h, int z, int water) {
        float streak = Terrain.field(seed + 11, x * 3.0 + z * 3.0, h * 0.25, 4.0);
        float n = Terrain.noise(seed, x, h, z);
        if (streak > 0.8F) return Blocks.STONE.defaultBlockState(); // darker streaks where water runs down the face
        if (h < water + 10 && n < 0.25F) return Blocks.MOSS_BLOCK.defaultBlockState(); // the damp foot of the walls
        if (streak < 0.08F) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        return ModBlocks.LIMESTONE.get().defaultBlockState();
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int water = y - depth, reach = reach();
        int minX = Math.max(area.minX(), b.minX()), maxX = Math.min(area.maxX(), b.maxX());
        int minZ = Math.max(area.minZ(), b.minZ()), maxZ = Math.min(area.maxZ(), b.maxZ());
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                float dx = x + 0.5F - cx, dz = z + 0.5F - cz;
                float d = Mth.sqrt(dx * dx + dz * dz);
                if (d > reach) continue;
                float theta = (float) Mth.atan2(dz, dx);
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, river);
                float u = l[0], vm = l[1] - meander(l[0]);
                boolean cave = inCave(u, vm);
                boolean shell = Math.abs(vm) < 4.6F && Math.abs(u) < caveEnd(u) + 2.0F;
                if (d > radius * wobble(theta) * 1.13F + 6.5F && !shell) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground > b.maxY() - 14) continue;
                // a stream or pond running over the rim is dammed with rock, so it can't pour in
                int flood = ground;
                for (pos.set(x, ground + 1, z); flood < ground + 12 && !level.getFluidState(pos).isEmpty(); pos.move(Direction.UP)) flood++;
                if (d < wall(x, ground, z, theta) + 1.0F) {
                    Terrain.clearAbove(level, pos, x, Math.max(ground, flood), z, Math.min(30, b.maxY() - Math.max(ground, flood)));
                }
                int floor = floorTop(x, z, d, vm), wet = wet(x, z, u, vm), roof = caveRoof(u, vm);
                for (int h = Math.max(b.minY(), water - 3); h <= Math.max(ground, flood); h++) {
                    float w = wall(x, h, z, theta);
                    BlockState s;
                    if (d < w && h == water - 3) {
                        s = ModBlocks.LIMESTONE.get().defaultBlockState(); // sound rock under it all, so nothing loose can drop away
                    } else if (d < w) {
                        // the pit itself: the floor, the river and its pool, and open air above
                        if (wet > 0) {
                            if (h > water) s = Blocks.AIR.defaultBlockState();
                            else if (h > water - wet) s = Blocks.WATER.defaultBlockState();
                            else s = Terrain.noise(seed, x, h, z) < 0.5F ? Blocks.GRAVEL.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                        } else if (h > floor) {
                            s = Blocks.AIR.defaultBlockState();
                        } else if (h == floor) {
                            float n = Terrain.noise(seed, x, 1, z);
                            s = n < 0.45F ? Blocks.GRASS_BLOCK.defaultBlockState() : n < 0.65F ? Blocks.MOSS_BLOCK.defaultBlockState()
                                    : n < 0.8F ? Blocks.PODZOL.defaultBlockState() : n < 0.9F ? Blocks.ROOTED_DIRT.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
                        } else if (h >= floor - 2) {
                            s = Blocks.DIRT.defaultBlockState();
                        } else {
                            // the rubble of the old roof
                            float n = Terrain.noise(seed, x, h, z);
                            s = n < 0.4F ? ModBlocks.LIMESTONE.get().defaultBlockState() : n < 0.7F ? Blocks.COBBLESTONE.defaultBlockState()
                                    : n < 0.85F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                        }
                    } else if (cave && h >= water - 2 && h <= water + roof && roof > 0) {
                        // the river's caves, running back into the rock out of sight
                        if (h > water) s = Blocks.AIR.defaultBlockState();
                        else if (h == water && Math.abs(vm) < 1.3F) s = Blocks.WATER.defaultBlockState();
                        else s = Terrain.noise(seed, x, h, z) < 0.6F ? Blocks.GRAVEL.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
                    } else if (d < w + 4.0F || shell && h <= water + 8) {
                        // the walls; back from the lip, the soil and turf on top are left alone
                        if (h > ground) {
                            s = ModBlocks.LIMESTONE.get().defaultBlockState();
                        } else if (h > ground - 2 && d > w + 1.5F) {
                            continue;
                        } else {
                            s = rock(x, h, z, water);
                        }
                    } else {
                        continue;
                    }
                    Terrain.set(level, pos, x, h, z, s);
                }
            }
        }
        // once the pit is dug: the forest on its floor, and the vines down its walls
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                float dx = x + 0.5F - cx, dz = z + 0.5F - cz;
                float d = Mth.sqrt(dx * dx + dz * dz);
                if (d > radius * 1.32F + 2.0F) continue;
                float theta = (float) Mth.atan2(dz, dx);
                if (d > radius * 0.7F) vines(level, b, pos, x, z, water);
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, river);
                float u = l[0], vm = l[1] - meander(l[0]);
                int floor = floorTop(x, z, d, vm), wet = wet(x, z, u, vm);
                if (d >= wall(x, floor + 1, z, theta)) continue;
                forest(level, area, pos, x, z, d, vm, wet > 0 ? water : floor, wet);
            }
        }
    }

    /** The undergrowth and the few small trees on the floor. */
    private void forest(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos, int x, int z, float d, float vm, int top, int wet) {
        pos.set(x, top + 1, z);
        if (!level.getBlockState(pos).isAir()) return;
        float n = Terrain.noise(seed, x, 2, z);
        if (wet > 0) {
            if (wet > 1 && n > 0.88F) Terrain.set(level, pos, x, top + 1, z, Blocks.LILY_PAD.defaultBlockState());
            return;
        }
        boolean jungle = level.getBiome(pos.set(x, y, z)).is(BiomeTags.IS_JUNGLE);
        BlockState leaves = (jungle ? Blocks.JUNGLE_LEAVES : n < 0.5F ? Blocks.OAK_LEAVES : Blocks.AZALEA_LEAVES).defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        pos.set(x, top, z);
        BlockState soil = level.getBlockState(pos);
        // a small tree, where its crown has room (and falls wholly in this chunk, so it's never cut in half)
        if (n > 0.975F && d < radius * 0.7F - 2.0F && Math.abs(vm) > 3.5F && x - 2 >= area.minX() && x + 2 <= area.maxX() && z - 2 >= area.minZ() && z + 2 <= area.maxZ()) {
            int height = 4 + Math.floorMod(x * 31 + z * 17, 3);
            BlockState log = (jungle ? Blocks.JUNGLE_LOG : Blocks.OAK_LOG).defaultBlockState();
            Terrain.set(level, pos, x, top, z, Blocks.DIRT.defaultBlockState());
            for (int k = 1; k <= height; k++) Terrain.set(level, pos, x, top + k, z, log);
            for (int ox = -2; ox <= 2; ox++) {
                for (int oz = -2; oz <= 2; oz++) {
                    for (int oy = -2; oy <= 1; oy++) {
                        int r = Math.abs(ox) + Math.abs(oz);
                        if (r + Math.max(0, oy) * 2 > 3 || r > 2 && oy < -1 || ox == 0 && oz == 0 && oy < 1) continue;
                        if (!area.isInside(x + ox, top + height + oy, z + oz) || !level.getBlockState(pos.set(x + ox, top + height + oy, z + oz)).isAir()) continue;
                        level.setBlock(pos, leaves, Terrain.FLAGS);
                    }
                }
            }
            return;
        }
        // bushes in clumps, ferns and grass between them, moss carpeting the moss
        float bush = Terrain.field(seed + 12, x, z, 3.0);
        if (bush > 0.7F) {
            Terrain.set(level, pos, x, top + 1, z, leaves);
            if (bush > 0.8F && level.getBlockState(pos.set(x, top + 2, z)).isAir()) Terrain.set(level, pos, x, top + 2, z, leaves);
        } else if (soil.is(Blocks.MOSS_BLOCK) && n < 0.6F) {
            Terrain.set(level, pos, x, top + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
        } else if (n > 0.93F && level.getBlockState(pos.set(x, top + 2, z)).isAir()) {
            Terrain.set(level, pos, x, top + 1, z, Blocks.LARGE_FERN.defaultBlockState());
            Terrain.set(level, pos, x, top + 2, z, Blocks.LARGE_FERN.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
        } else if (n > 0.55F) {
            Terrain.set(level, pos, x, top + 1, z, Blocks.FERN.defaultBlockState());
        } else if (n > 0.3F) {
            Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
        }
    }

    /** Vines trailing down from the lip, and from under the ledges further down, in long curtains. */
    private void vines(WorldGenLevel level, BoundingBox b, BlockPos.MutableBlockPos pos, int x, int z, int water) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (Terrain.noise(seed + dir.ordinal(), x, 3, z) > 0.6F) continue;
            int nx = x + dir.getStepX(), nz = z + dir.getStepZ();
            float ndx = nx + 0.5F - cx, ndz = nz + 0.5F - cz;
            float nd = Mth.sqrt(ndx * ndx + ndz * ndz), ntheta = (float) Mth.atan2(ndz, ndx);
            float[] nl = Terrain.uv(nx + 0.5, nz + 0.5, cx, cz, river);
            boolean nearCave = inCave(nl[0], nl[1] - meander(nl[0]));
            BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(dir), true);
            int run = 0;
            boolean lip = true;
            for (int h = Math.min(y - 2, b.maxY()); h > water + 1; h--) {
                pos.set(x, h, z);
                if (!level.getBlockState(pos).isAir()) {
                    run = 0;
                    continue;
                }
                if (run == 0 && nd >= wall(nx, h, nz, ntheta) && !(nearCave && h <= water + 5)) {
                    float n = Terrain.noise(seed + 30 + dir.ordinal(), x, h, z);
                    if (lip ? n < 0.75F : n < 0.07F) run = 3 + Math.round(Terrain.noise(seed + 40, x, h, z) * (lip ? 22.0F : 9.0F));
                    lip = false;
                }
                if (run > 0) {
                    level.setBlock(pos, vine, Terrain.FLAGS);
                    run--;
                }
            }
        }
    }
}
