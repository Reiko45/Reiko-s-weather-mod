package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.BaseCoralPlantTypeBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A beach of coloured sand: most beach sand is grains of pale quartz, but where something else is close at hand, the
 * waves grind that up instead. A shell beach is made of the broken shells of sea creatures, cream-white and crunchy
 * underfoot, with bits of bleached shell and dead coral washed up along the strand line. A green beach lies in a cove
 * under a crumbling headland of old volcanic rock: the waves wash away the lighter ash and leave behind the heavy grains
 * of olivine it held, so the sand is olive-green. A pink beach is the dust of red corals and shells from a reef close
 * offshore, ground fine and mixed in with the white.
 *
 * @param angle   the way out to sea
 * @param variant 0 a shell beach, 1 a green beach under its cliff, 2 a pink beach
 * @param height  how high the green beach's cliff stands above the sea
 */
public record ColouredBeach(int cx, int cz, int sea, float angle, int length, int variant, int height, long seed) implements Landform {
    public static final int SHELL = 0, GREEN = 1, PINK = 2;
    private static final int BACK = 10;
    private static final BlockState[] DEAD_CORALS = {Blocks.DEAD_TUBE_CORAL_FAN.defaultBlockState(), Blocks.DEAD_BRAIN_CORAL_FAN.defaultBlockState(),
            Blocks.DEAD_HORN_CORAL_FAN.defaultBlockState(), Blocks.DEAD_FIRE_CORAL_FAN.defaultBlockState(), Blocks.DEAD_BUBBLE_CORAL.defaultBlockState(),
            Blocks.DEAD_BRAIN_CORAL.defaultBlockState()};
    private static final BlockState[] CORAL_BLOCKS = {Blocks.FIRE_CORAL_BLOCK.defaultBlockState(), Blocks.BRAIN_CORAL_BLOCK.defaultBlockState(),
            Blocks.TUBE_CORAL_BLOCK.defaultBlockState(), Blocks.HORN_CORAL_BLOCK.defaultBlockState()};
    private static final BlockState[] CORALS = {Blocks.FIRE_CORAL_FAN.defaultBlockState(), Blocks.FIRE_CORAL.defaultBlockState(),
            Blocks.BRAIN_CORAL_FAN.defaultBlockState(), Blocks.TUBE_CORAL.defaultBlockState(), Blocks.HORN_CORAL_FAN.defaultBlockState(),
            Blocks.BUBBLE_CORAL.defaultBlockState()};

    @Nullable
    static Landform plan(PlanContext c) {
        return c.search(12, 12, ColouredBeach::planAt);
    }

    @Nullable
    private static Landform planAt(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() - 2 || ground > c.seaLevel() + 4) {
            return null;
        }
        float seaward = c.seaward(18, 2);
        if (Float.isNaN(seaward)) {
            return null;
        }
        int variant = c.random().nextInt(3);
        return new ColouredBeach(c.x(), c.z(), c.seaLevel(), seaward, 44 + c.random().nextInt(27), variant, 6 + c.random().nextInt(5), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new ColouredBeach(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("variant"),
                tag.getInt("height"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("sea", sea);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putInt("variant", variant);
        tag.putInt("height", height);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.COLOURED_BEACH;
    }

    /** How far up from the waterline the beach (or for the green beach, its cliff and the slope above) reaches. */
    private float reach() {
        return variant == GREEN ? BACK + 18.0F : BACK + 8.0F;
    }

    @Override
    public BoundingBox bounds() {
        float half = length / 2.0F + 1.0F;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (float u : new float[]{-(reach() + 3.0F), 16.0F}) {
            for (float v : new float[]{-half, half}) {
                double[] p = Terrain.xz(u, v, cx, cz, angle);
                minX = Math.min(minX, Mth.floor(p[0]));
                maxX = Math.max(maxX, Mth.ceil(p[0]));
                minZ = Math.min(minZ, Mth.floor(p[1]));
                maxZ = Math.max(maxZ, Mth.ceil(p[1]));
            }
        }
        return new BoundingBox(minX, sea - 18, minZ, maxX, sea + height + 24, maxZ);
    }

    /** How far the waterline sits out to sea of the middle, {@code v} along the coast. */
    private float shore(float v) {
        return 1.5F * Mth.sin(v * 0.05F + Terrain.hash(seed, 1) * 6.0F) + 0.8F * Mth.sin(v * 0.13F + Terrain.hash(seed, 2) * 6.0F);
    }

    /** The beach's height {@code w} blocks up from the waterline. */
    private float profile(float w) {
        if (w < 0.0F) return sea - 1 + Math.max(w, -9.0F) * 0.45F;
        return sea - 1 + 3.0F * Terrain.smooth(Math.min(w, BACK) / BACK);
    }

    private BlockState sand() {
        return switch (variant) {
            case GREEN -> ModBlocks.OLIVINE_SAND.get().defaultBlockState();
            case PINK -> ModBlocks.PINK_SAND.get().defaultBlockState();
            default -> ModBlocks.SHELL_SAND.get().defaultBlockState();
        };
    }

    /** The green beach's headland: dark basalt, banded with the paler tuff of old ash falls. */
    private BlockState rock(int x, int h, int z, float v) {
        float n = Terrain.noise(seed + 3, x, h, z);
        if (Math.floorMod(h + Math.round(v * 0.08F + 2.0F * Terrain.field(seed + 4, x, z, 9.0)), 5) < 2) {
            return n < 0.85F ? Blocks.TUFF.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
        }
        return n < 0.75F ? Blocks.BASALT.defaultBlockState() : n < 0.9F ? Blocks.SMOOTH_BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
    }

    /** Whether water stands at this height in a column next to this one that the beach never reaches (and so never changes). */
    private boolean pondBeside(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int h, int z, float half, float reach) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int nx = x + d.getStepX(), nz = z + d.getStepZ();
            float[] l = Terrain.uv(nx + 0.5, nz + 0.5, cx, cz, angle);
            float w = shore(l[1]) - l[0];
            boolean outside = Math.abs(l[1]) > half || w < -14.0F || w > reach;
            if (outside && !level.getFluidState(pos.set(nx, h, nz)).isEmpty()) return true;
        }
        return false;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState sand = sand();
        float half = length / 2.0F, reach = reach();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float v = l[1], w = shore(v) - l[0];
                if (Math.abs(v) > half || w < -14.0F || w > reach) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground > b.maxY() - 8 || ground < b.minY() + 4) continue;
                boolean wet = !level.getFluidState(pos.set(x, ground + 1, z)).isEmpty() || w < 0.0F;
                float ends = Terrain.smooth((half - Math.abs(v)) / 10.0F);
                // the cove's back wall, for the green beach: the headland the sand came from
                float back = BACK + 1.5F * (Terrain.field(seed + 5, x, z, 7.0) - 0.5F);
                boolean cliff = variant == GREEN && w > back;
                float target;
                if (w < -8.0F) {
                    target = Mth.lerp(Terrain.smooth((-w - 8.0F) / 6.0F), profile(-8.0F), ground); // shelving off to the sea bed
                } else if (cliff) {
                    float crest = sea + height + 2.0F * Terrain.field(seed + 6, x, z, 6.0);
                    float face = Mth.lerp(Terrain.smooth((w - back) / 2.5F), profile(BACK) + 1.0F, crest);
                    target = Mth.lerp(Terrain.smooth((w - back - 5.0F) / 12.0F), face, ground); // the headland's top, rolling back into the land
                } else if (w <= BACK) {
                    target = profile(w);
                } else {
                    target = Mth.lerp(Terrain.smooth((w - BACK) / 8.0F), profile(BACK), ground); // into the land behind
                }
                int top = Math.round(Mth.lerp(ends, ground, target));
                // any pond standing here above the sea goes (the sea itself stays)
                int surface = ground;
                for (pos.set(x, ground + 1, z); surface < ground + 8 && !level.getFluidState(pos).isEmpty(); pos.move(Direction.UP)) surface++;
                if (top == ground && w > BACK + 4.0F && !cliff && surface < sea) continue;
                Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, Math.min(12, b.maxY() - Math.max(ground, top)));
                for (int h = top + 1; h <= Math.max(Math.max(ground, surface), sea - 1); h++) {
                    BlockState fill = h <= sea - 1 && wet ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    // a pond just beyond the beach keeps a bank between it and the lowered sand
                    if (fill.isAir() && pondBeside(level, pos, x, h, z, half, reach)) fill = Blocks.SANDSTONE.defaultBlockState();
                    Terrain.set(level, pos, x, h, z, fill);
                }
                float n = Terrain.noise(seed, x, top, z), m = Terrain.noise(seed + 1, x, top, z);
                if (cliff && top > profile(BACK) + 1.5F) {
                    // bare rock in the cliff face, turf on the gentler slope above it
                    boolean face = w < back + 5.0F && ends > 0.4F;
                    for (int h = Math.max(b.minY(), Math.min(ground, top) - 1); h <= top; h++) {
                        BlockState s = rock(x, h, z, v);
                        if (!face && h == top) s = Blocks.GRASS_BLOCK.defaultBlockState();
                        else if (!face && h == top - 1) s = Blocks.DIRT.defaultBlockState();
                        Terrain.set(level, pos, x, h, z, s);
                    }
                    if (!face && n > 0.6F) Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                    continue;
                }
                // two or three blocks of the coloured sand, over the beach and the sea floor off it
                int bottom = Math.max(b.minY() + 1, Math.min(ground + 1, top - 2));
                for (int h = bottom; h <= top; h++) Terrain.set(level, pos, x, h, z, sand);
                pos.set(x, bottom - 1, z);
                BlockState under = level.getBlockState(pos);
                if (under.isAir() || !under.getFluidState().isEmpty()) {
                    level.setBlock(pos, variant == GREEN ? Blocks.TUFF.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState(), Terrain.FLAGS);
                }
                boolean dry = top >= sea;
                switch (variant) {
                    case SHELL -> {
                        // bits of bleached shell in the sand, and dead coral washed up along the strand line
                        if (dry && n < 0.03F) {
                            Terrain.set(level, pos, x, top, z, Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS,
                                    m < 0.5F ? Direction.Axis.X : Direction.Axis.Z));
                        } else if (dry && n < 0.06F) {
                            Terrain.set(level, pos, x, top, z, Blocks.CALCITE.defaultBlockState());
                        } else if (dry && w > 0.0F && w < 4.0F + 2.0F * Terrain.field(seed + 7, x, z, 5.0) && m > 0.9F) {
                            BlockState coral = DEAD_CORALS[Math.floorMod((int) (n * 1000), DEAD_CORALS.length)];
                            Terrain.set(level, pos, x, top + 1, z, coral.setValue(BaseCoralPlantTypeBlock.WATERLOGGED, false));
                        }
                    }
                    case GREEN -> {
                        // lumps of the headland's rock fallen out onto the sand
                        if (dry && w > BACK - 4.0F && m > 0.93F) Terrain.set(level, pos, x, top + 1, z, rock(x, top + 1, z, v));
                    }
                    default -> {
                        // the living reef the pink sand comes from, in warm water a little way out
                        if (!wet || top > sea - 3 || w > -2.0F || ends < 0.5F) break;
                        var biome = level.getBiome(pos.set(x, top, z));
                        if (!biome.is(Biomes.WARM_OCEAN) && !biome.is(Biomes.LUKEWARM_OCEAN) && !biome.is(Biomes.DEEP_LUKEWARM_OCEAN)) break;
                        float reef = Terrain.field(seed + 8, x, z, 4.0);
                        if (reef > 0.6F) Terrain.set(level, pos, x, top, z, CORAL_BLOCKS[Math.floorMod((int) (n * 1000), CORAL_BLOCKS.length)]);
                        if (reef > 0.5F && m > 0.45F) Terrain.set(level, pos, x, top + 1, z, CORALS[Math.floorMod((int) (m * 1000), CORALS.length)]);
                    }
                }
                if (wet && top < sea - 2 && variant != PINK && n > 0.88F) Terrain.set(level, pos, x, top + 1, z, Blocks.SEAGRASS.defaultBlockState());
            }
        }
    }
}
