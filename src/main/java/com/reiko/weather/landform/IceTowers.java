package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.function.BiPredicate;

/**
 * Fumarole ice towers, like those high on Mount Erebus in Antarctica: hollow chimneys of ice standing over steam vents
 * on the flank of a volcano so cold that the steam freezes as it rises. The ice builds up round the plume, layer on
 * layer of frost, into a lumpy, leaning tower, while the warm gas keeps a hole open up the middle; the steam still
 * puffs out of the top. Under the bigger towers the heat has melted out a cave in the snow and ice, warm and dripping,
 * with the vent hissing in its floor. Round the vents the ground is bare: rock, and warm, steaming soil that snow never
 * settles on. Everywhere else it's snow, glazed in places with a crust of ice.
 *
 * @param y      the ground in the middle
 * @param towers {x, z, base, height, radius (tenths), lean x (tenths), lean z (tenths), cave entrance (-1 for none, or a
 *               horizontal direction's 2D value)} for each tower
 */
public record IceTowers(int cx, int cz, int y, int[] towers, long seed) implements Landform {
    private static final int T = 8, SPREAD = 20, FIELD = 27;
    /** Kinds of tower (the big ones store the way into their cave instead). */
    private static final int PLAIN = -1, SQUAT = -2, LEANING = -3;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 40 || !c.fits(ground, 14, 28) || c.relief(SPREAD, 8) > 10) {
            return null;
        }
        int want = 5 + c.random().nextInt(8), placed = 0, samples = 0;
        int[] towers = new int[want * T];
        for (int tries = 0; tries < want * 5 && placed < want && samples < 16; tries++) {
            // the first one or two are the biggest, with a cave melted out under them; of the rest, some are squat and some lean
            float roll = c.random().nextFloat();
            int kind = placed == 0 || placed == 1 && roll < 0.4F ? c.random().nextInt(4) : roll < 0.25F ? SQUAT : roll < 0.5F ? LEANING : PLAIN;
            int h = kind >= 0 ? 8 + c.random().nextInt(3) : kind == SQUAT ? 3 + c.random().nextInt(3) : kind == LEANING ? 5 + c.random().nextInt(5) : 4 + c.random().nextInt(6);
            float r = kind >= 0 ? 2.8F + c.random().nextFloat() * 0.5F : kind == SQUAT ? 2.4F + c.random().nextFloat() * 0.8F
                    : kind == LEANING ? 2.5F + c.random().nextFloat() * 0.5F : 1.9F + c.random().nextFloat() * 0.5F + h * 0.04F;
            // never leaning so far that the bore up the middle doesn't run straight up inside it
            float lean = kind == LEANING ? (0.7F + 0.3F * c.random().nextFloat()) * Math.max(0.0F, r * (1.0F - taper(kind)) - 1.3F) : 0.0F;
            float la = c.random().nextFloat() * Mth.TWO_PI, a = c.random().nextFloat() * Mth.TWO_PI, d = Mth.sqrt(c.random().nextFloat()) * SPREAD;
            int x = c.x() + Math.round(Mth.cos(a) * d), z = c.z() + Math.round(Mth.sin(a) * d);
            boolean room = true;
            for (int j = 0; j < placed && room; j++) {
                float gap = r + towers[j * T + 4] / 10.0F + lean + Mth.sqrt(Mth.square(towers[j * T + 5]) + Mth.square(towers[j * T + 6])) / 10.0F + 3.0F;
                room = Mth.square(x - towers[j * T]) + Mth.square(z - towers[j * T + 1]) >= gap * gap;
            }
            if (!room) continue;
            int base = c.groundAt(x, z);
            samples++;
            if (Math.abs(base - ground) > 10) continue;
            int i = placed++ * T;
            towers[i] = x;
            towers[i + 1] = z;
            towers[i + 2] = base;
            towers[i + 3] = h;
            towers[i + 4] = Math.round(r * 10.0F);
            towers[i + 5] = Math.round(Mth.cos(la) * lean * 10.0F);
            towers[i + 6] = Math.round(Mth.sin(la) * lean * 10.0F);
            towers[i + 7] = kind;
        }
        if (placed < 5) {
            return null;
        }
        return new IceTowers(c.x(), c.z(), ground, java.util.Arrays.copyOf(towers, placed * T), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new IceTowers(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getIntArray("towers"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.put("towers", new IntArrayTag(towers));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.ICE_TOWERS;
    }

    @Override
    public BoundingBox bounds() {
        int min = y, max = y;
        for (int i = 0; i < towers.length; i += T) {
            min = Math.min(min, towers[i + 2]);
            max = Math.max(max, towers[i + 2] + towers[i + 3]);
        }
        int r = FIELD + 4;
        return new BoundingBox(cx - r, min - 14, cz - r, cx + r, max + 8, cz + r);
    }

    /** How much a tower narrows from its foot to its top: squat ones a lot, leaning ones hardly at all. */
    private static float taper(int kind) {
        return kind == SQUAT ? 0.45F : kind == LEANING ? 0.12F : 0.3F;
    }

    private float radius(int i) {
        return towers[i * T + 4] / 10.0F;
    }

    /** How far out from the middle of tower {@code i} anything of it reaches. */
    private float reach(int i) {
        return radius(i) + Mth.sqrt(Mth.square(towers[i * T + 5]) + Mth.square(towers[i * T + 6])) / 10.0F + 0.6F;
    }

    private boolean big(int i) {
        return towers[i * T + 7] >= 0;
    }

    /** Whether tower {@code i} has ice at a block: a lumpy, tapering, maybe leaning cone, with the bore up its middle left open. */
    private boolean ice(int i, int x, int yy, int z) {
        int k = yy - towers[i * T + 2], h = towers[i * T + 3];
        if (k < 1 || k > h) return false;
        float t = k / (float) h;
        float mx = towers[i * T] + 0.5F + towers[i * T + 5] / 10.0F * t, mz = towers[i * T + 1] + 0.5F + towers[i * T + 6] / 10.0F * t;
        float r = radius(i) * (1.0F - taper(towers[i * T + 7]) * t) + 0.5F * (Terrain.noise(seed + i, x, yy, z) - 0.5F);
        if (k == h && Terrain.noise(seed + 50 + i, x, yy, z) < 0.4F) return false; // a ragged rim
        return Mth.sqrt(Mth.square(x + 0.5F - mx) + Mth.square(z + 0.5F - mz)) < r;
    }

    /** Whether a block is in the hollow of tower {@code i}: the bore over the vent, and the cave under the big ones. */
    private boolean hollow(int i, int x, int yy, int z) {
        int k = yy - towers[i * T + 2];
        if (k < 1) return false;
        float dx = x - towers[i * T], dz = z - towers[i * T + 1];
        if (dx == 0 && dz == 0) return k <= towers[i * T + 3] + 1;
        if (big(i) && dx * dx + dz * dz < 1.1F) return k <= towers[i * T + 3] + 1; // the big ones' bore is wider
        if (!big(i) || k > 3) return false;
        if (Mth.sqrt(dx * dx + dz * dz) < radius(i) - 1.1F) return true; // the cave
        // its way in, two blocks high
        Direction d = Direction.from2DDataValue(towers[i * T + 7]);
        float along = dx * d.getStepX() + dz * d.getStepZ(), across = Math.abs(dx * d.getStepZ() - dz * d.getStepX());
        return k <= 2 && along > 0.0F && along < radius(i) + 2.5F && across < 0.6F;
    }

    /** Whether a column is in the way into tower {@code i}'s cave, out past the tower. */
    private boolean entrance(int i, int x, int z) {
        if (!big(i)) return false;
        float dx = x - towers[i * T], dz = z - towers[i * T + 1];
        Direction d = Direction.from2DDataValue(towers[i * T + 7]);
        float along = dx * d.getStepX() + dz * d.getStepZ(), across = Math.abs(dx * d.getStepZ() - dz * d.getStepX());
        return along > 0.0F && along < radius(i) + 2.5F && across < 0.6F;
    }

    /** The tower a column is under or beside, and how far from its middle: {index, distance}. */
    private float[] nearest(int x, int z) {
        float best = Float.MAX_VALUE, which = -1;
        for (int i = 0; i < towers.length / T; i++) {
            float d = Mth.sqrt(Mth.square(x - towers[i * T]) + Mth.square(z - towers[i * T + 1])) - radius(i);
            if (d < best) {
                best = d;
                which = i;
            }
        }
        return new float[]{which, best};
    }

    /** Whether a column is one the towers never change the water in (for ponds beside the way into a cave). */
    private boolean untouched(int x, int z) {
        float[] near = nearest(x, z);
        int i = (int) near[0];
        return Mth.sqrt(Mth.square(x - towers[i * T]) + Mth.square(z - towers[i * T + 1])) >= reach(i) && !entrance(i, x, z);
    }

    /** The towers' ice: packed hard, clear in places, furred with rime on the outside. */
    private BlockState wall(int i, int x, int yy, int z) {
        float n = Terrain.noise(seed + 100 + i, x, yy, z);
        if (n < 0.12F) return Blocks.SNOW_BLOCK.defaultBlockState();
        return n < 0.72F ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.ICE.defaultBlockState();
    }

    private BlockState rock(int x, int yy, int z) {
        float n = Terrain.noise(seed + 7, x, yy, z);
        return n < 0.45F ? Blocks.TUFF.defaultBlockState() : n < 0.8F ? Blocks.BASALT.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState();
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BiPredicate<Integer, Integer> outside = this::untouched;
        BlockState steaming = ModBlocks.STEAMING_GROUND.get().defaultBlockState();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float dc = Mth.sqrt(Mth.square(x + 0.5F - cx) + Mth.square(z + 0.5F - cz));
                // the snowfield round the vents, its edge ragged
                float field = FIELD * (0.8F + 0.3F * Terrain.field(seed + 1, x, z, 10.0));
                float[] near = nearest(x, z);
                int i = (int) near[0];
                float dt = Mth.sqrt(Mth.square(x - towers[i * T]) + Mth.square(z - towers[i * T + 1]));
                boolean tower = dt < reach(i), way = entrance(i, x, z);
                if (dc > field && !tower && !way) continue;
                int ground = Terrain.ground(level, pos, x, z);
                int surface = Terrain.surface(level, pos, x, ground, z);
                if (ground < b.minY() + 2 || surface > b.maxY() - 4) continue;
                float n = Terrain.noise(seed, x, ground, z);
                int base = towers[i * T + 2], h = towers[i * T + 3];
                if (tower || way) {
                    Terrain.clearAbove(level, pos, x, Math.min(ground, base), z, Math.min(b.maxY(), Math.max(surface, base + h + 2)) - Math.min(ground, base));
                    boolean foot = dt < radius(i) + 0.3F;
                    if (foot || way) {
                        // the tower stands on a mound of frozen snow where the ground dips, and on rock round the vent
                        for (int yy = ground + 1; yy < base; yy++) Terrain.set(level, pos, x, yy, z, way && !foot ? rock(x, yy, z) : Blocks.PACKED_ICE.defaultBlockState());
                        Terrain.set(level, pos, x, base, z, rock(x, base, z));
                    }
                    for (int yy = Math.min(ground, base) + 1; yy <= Math.max(surface, base + h + 1); yy++) {
                        if (hollow(i, x, yy, z)) {
                            // (a pond just outside keeps a bank of snow between it and the way into the cave)
                            boolean bank = way && !foot && Terrain.pondBeside(level, pos, x, yy, z, outside);
                            Terrain.set(level, pos, x, yy, z, bank ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
                        } else if (ice(i, x, yy, z)) {
                            Terrain.set(level, pos, x, yy, z, wall(i, x, yy, z));
                        } else if (way && !foot && yy > base && yy <= Math.max(ground, surface)) {
                            // a trench down to the way in, where the ground stands higher than the cave
                            Terrain.set(level, pos, x, yy, z, Terrain.pondBeside(level, pos, x, yy, z, outside) ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
                        } else if (!level.getFluidState(pos.set(x, yy, z)).isEmpty()) {
                            Terrain.set(level, pos, x, yy, z, wall(i, x, yy, z)); // a pond under the tower, frozen solid
                        }
                    }
                    if (x == towers[i * T] && z == towers[i * T + 1]) {
                        // the vent itself, hissing steam up the bore
                        Terrain.set(level, pos, x, base, z, ModBlocks.VOLCANIC_VENT.get().defaultBlockState());
                    } else if (big(i) && hollow(i, x, base + 1, z) && foot) {
                        // the cave's floor, warm and bare
                        Terrain.set(level, pos, x, base, z, n < 0.5F ? steaming : rock(x, base, z));
                    }
                    continue;
                }
                if (surface > ground) continue; // a pond: left as it is
                Terrain.clearAbove(level, pos, x, ground, z, 3);
                // round the vents the ground is warm: bare rock, and patches of steaming soil
                float heat = 1.0F - near[1] / 2.6F + 0.9F * (Terrain.field(seed + 2, x, z, 3.0) - 0.5F);
                if (heat > 0.0F) {
                    Terrain.set(level, pos, x, ground, z, Terrain.field(seed + 3, x, z, 2.5) < 0.5F ? steaming : rock(x, ground, z));
                    continue;
                }
                // the snowfield, glazed with ice in places; the edge blends into the ground round about
                if (dc > field - 4.0F && n < (dc - field + 4.0F) / 4.0F) continue;
                float glaze = Terrain.field(seed + 4, x, z, 5.0);
                if (glaze < 0.25F) {
                    Terrain.set(level, pos, x, ground, z, glaze < 0.1F ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.ICE.defaultBlockState());
                    continue;
                }
                Terrain.set(level, pos, x, ground, z, Blocks.SNOW_BLOCK.defaultBlockState());
                if (glaze > 0.55F) {
                    Terrain.set(level, pos, x, ground + 1, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + Math.round(n * 2.0F)));
                }
            }
        }
    }
}
