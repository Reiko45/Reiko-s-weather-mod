package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A coral atoll: a ring of reef round a shallow, sandy lagoon, all that's left of a volcanic island that sank
 * away while the coral kept growing up toward the light. Low sandy islets with palms sit here and there on the
 * reef, and outside it the reef front drops away steeply into the deep.
 */
public record Atoll(int cx, int cz, int radius, int ring, long seed) implements Landform {
    private static final BlockState[] CORAL_BLOCKS = {Blocks.TUBE_CORAL_BLOCK.defaultBlockState(), Blocks.BRAIN_CORAL_BLOCK.defaultBlockState(),
            Blocks.BUBBLE_CORAL_BLOCK.defaultBlockState(), Blocks.FIRE_CORAL_BLOCK.defaultBlockState(), Blocks.HORN_CORAL_BLOCK.defaultBlockState()};
    private static final BlockState[] CORALS = {Blocks.TUBE_CORAL.defaultBlockState(), Blocks.BRAIN_CORAL.defaultBlockState(),
            Blocks.BUBBLE_CORAL.defaultBlockState(), Blocks.FIRE_CORAL.defaultBlockState(), Blocks.HORN_CORAL.defaultBlockState(),
            Blocks.TUBE_CORAL_FAN.defaultBlockState(), Blocks.BRAIN_CORAL_FAN.defaultBlockState(), Blocks.HORN_CORAL_FAN.defaultBlockState()};
    private static final int SLOPE = 14;

    @Nullable
    static Landform plan(PlanContext c) {
        int radius = 30 + c.random().nextInt(19);
        int floor = c.groundAt(c.x(), c.z());
        if (floor > c.seaLevel() - 6 || floor < c.seaLevel() - 40 || !c.fits(floor, 5, 20)) {
            return null;
        }
        // open sea all round: no land in the way
        for (int i = 0; i < 12; i++) {
            float a = i * Mth.TWO_PI / 12.0F;
            if (c.groundAt(c.x() + Math.round(Mth.cos(a) * (radius + SLOPE)), c.z() + Math.round(Mth.sin(a) * (radius + SLOPE))) > c.seaLevel() - 4) {
                return null;
            }
        }
        return new Atoll(c.x(), c.z(), radius, 4 + c.random().nextInt(4), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Atoll(tag.getInt("x"), tag.getInt("z"), tag.getInt("r"), tag.getInt("ring"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("r", radius);
        tag.putInt("ring", ring);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.ATOLL;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * 1.12F) + SLOPE + 2;
        return new BoundingBox(cx - r, -64, cz - r, cx + r, 160, cz + r);
    }

    /** The reef's distance from the middle at an angle (it's not quite round). */
    private float reefRadius(float angle) {
        return radius * (1.0F + 0.07F * Mth.sin(angle * 2.0F + Terrain.hash(seed, 1) * 6.0F) + 0.05F * Mth.sin(angle * 5.0F + Terrain.hash(seed, 2) * 6.0F));
    }

    /** Where along the ring the islets are (0 to 1, above ~0.55 is dry land). */
    private float islet(float angle) {
        return Terrain.field(seed, angle * 6.0 + 100.0, 0.0, 1.0);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int sea = level.getSeaLevel();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
                float angle = (float) Math.atan2(dz, dx), d = (float) Math.sqrt(dx * dx + dz * dz);
                float reef = reefRadius(angle), off = d - reef;
                if (off > ring / 2.0F + SLOPE) continue;
                int floor = Terrain.ground(level, pos, x, z);
                float n = Terrain.noise(seed, x, 0, z);
                int top;
                BlockState surface;
                if (Math.abs(off) <= ring / 2.0F) {
                    // the reef flat, with sandy islets standing just clear of the sea
                    float isle = islet(angle) - 0.55F;
                    float across = 1.0F - Math.abs(off) / (ring / 2.0F);
                    if (isle > 0.0F) {
                        top = sea + (isle > 0.1F && across > 0.4F ? 2 : 1);
                        surface = Blocks.SAND.defaultBlockState();
                    } else {
                        top = sea - 1;
                        surface = n < 0.6F ? Blocks.SAND.defaultBlockState() : CORAL_BLOCKS[(int) (n * 97) % CORAL_BLOCKS.length];
                    }
                } else if (off < 0.0F) {
                    // the lagoon: a few blocks of warm, clear water over white sand, with coral heads here and there
                    float depth = Mth.lerp(Terrain.smooth(-off / (radius * 0.5F)), 2.5F, 6.0F);
                    boolean bommie = Terrain.field(seed, x, z, 4.0) > 0.78F;
                    top = sea - Math.round(bommie ? depth - 2.0F : depth);
                    surface = bommie ? CORAL_BLOCKS[(int) (n * 89) % CORAL_BLOCKS.length] : Blocks.SAND.defaultBlockState();
                } else {
                    // the reef front, dropping away into the deep
                    float t = (off - ring / 2.0F) / SLOPE;
                    top = Math.round(Mth.lerp(t * t, sea - 2, Math.min(floor, sea - 2)));
                    if (top <= floor) continue;
                    surface = n < 0.55F ? CORAL_BLOCKS[(int) (n * 71) % CORAL_BLOCKS.length] : Blocks.SAND.defaultBlockState();
                }
                Terrain.clearAbove(level, pos, x, Math.max(floor, top), z, 20);
                for (int h = Math.min(floor, top) - 2; h < top; h++) {
                    Terrain.set(level, pos, x, h, z, h < top - 3 ? Blocks.SANDSTONE.defaultBlockState() : Blocks.SAND.defaultBlockState());
                }
                Terrain.set(level, pos, x, top, z, surface);
                for (int h = top + 1; h <= Math.max(floor, sea); h++) {
                    Terrain.set(level, pos, x, h, z, h <= sea ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
                if (top < sea - 1) {
                    // living coral, sea grass and sea pickles on the bottom
                    float m = Terrain.noise(seed, x, 5, z);
                    if (surface.is(net.minecraft.tags.BlockTags.CORAL_BLOCKS) && m < 0.55F) {
                        Terrain.set(level, pos, x, top + 1, z, CORALS[(int) (m * 113) % CORALS.length]);
                    } else if (m < 0.18F) {
                        Terrain.set(level, pos, x, top + 1, z, Blocks.SEAGRASS.defaultBlockState());
                    } else if (m > 0.97F) {
                        Terrain.set(level, pos, x, top + 1, z, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + (int) (m * 400) % 4));
                    }
                } else if (top > sea && Terrain.noise(seed, x, 6, z) < 0.12F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                }
            }
        }
        // palms on the islets
        int palms = 10 + (int) (Terrain.hash(seed, 3) * 8);
        for (int i = 0; i < palms; i++) {
            float angle = Terrain.hash(seed, 60 + i) * Mth.TWO_PI;
            if (islet(angle) < 0.68F) continue; // only on the bigger islets, well clear of the water
            float d = reefRadius(angle) + (Terrain.hash(seed, 70 + i) - 0.5F) * ring * 0.4F;
            int px = cx + Math.round(Mth.cos(angle) * d), pz = cz + Math.round(Mth.sin(angle) * d);
            Palms.grow(level, area, pos, px, sea + 2, pz, seed ^ (i * 0x632BE59BD9B4E019L));
        }
    }
}
