package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;

/**
 * A seamount: an undersea volcano rising alone from the deep ocean floor, like the thousands that stud the bed of the
 * Pacific. Each was built by eruption after eruption over a hot spot or a crack in the young crust, pillows of lava heaped
 * on beds of glassy ash, into a steep cone that stopped short of the surface. Some did break the surface for a while, as
 * volcanic islands, and the waves planed their tops off flat; then, as the plate carried them away from the hot spot and
 * the crust under them cooled and sank, they drowned again. These flat-topped ones are called guyots. Gullies run down
 * the flanks where rubble has slid away, and old lava flows stand out as rocky ledges. Though it lies far out in the open
 * ocean, a seamount teems with life: currents striking its flanks are thrown upward, lifting food from the deep into the
 * light, so its top is a garden of coral in warm seas and a forest of kelp in cold ones.
 *
 * @param floor   the sea bed at the middle
 * @param low     the deepest sea bed found round the foot (for the bounds)
 * @param radius  how far the foot spreads from the middle
 * @param top     the y of the summit
 * @param crown   the radius of the summit: broad and flat on a guyot, little more than a point otherwise
 * @param angle   the way it's drawn out, along the crack it grew over
 * @param stretch how much longer it is that way than across
 */
public record Seamount(int cx, int cz, int sea, int floor, int low, int radius, int top, float crown, float angle, float stretch, long seed) implements Landform {
    private static final BlockState[] CORAL = {Blocks.TUBE_CORAL_BLOCK.defaultBlockState(), Blocks.BRAIN_CORAL_BLOCK.defaultBlockState(),
            Blocks.BUBBLE_CORAL_BLOCK.defaultBlockState(), Blocks.FIRE_CORAL_BLOCK.defaultBlockState(), Blocks.HORN_CORAL_BLOCK.defaultBlockState()};
    private static final BlockState[] FANS = {Blocks.TUBE_CORAL_FAN.defaultBlockState(), Blocks.BRAIN_CORAL_FAN.defaultBlockState(),
            Blocks.BUBBLE_CORAL_FAN.defaultBlockState(), Blocks.FIRE_CORAL_FAN.defaultBlockState(), Blocks.HORN_CORAL_FAN.defaultBlockState()};
    private static final BlockState[] PLANTS = {Blocks.TUBE_CORAL.defaultBlockState(), Blocks.BRAIN_CORAL.defaultBlockState(),
            Blocks.BUBBLE_CORAL.defaultBlockState(), Blocks.FIRE_CORAL.defaultBlockState(), Blocks.HORN_CORAL.defaultBlockState()};
    /** How far down the flanks (as a share of the way to the foot) the life on the upper part of the cone reaches. */
    private static final float LIVING = 0.42F;

    @Nullable
    static Landform plan(PlanContext c) {
        int sea = c.seaLevel();
        int floor = c.groundAt(c.x(), c.z());
        if (floor > sea - 22 || !c.fits(floor, 30, 10)) {
            return null;
        }
        // open deep sea all round its foot: no shoals, islands or coast within reach
        int radius = 20 + c.random().nextInt(21);
        int low = floor;
        for (int attempt = 0; ; attempt++) {
            boolean open = true;
            low = floor;
            for (int i = 0; i < 8 && open; i++) {
                double a = i * Math.PI / 4.0 + 0.3;
                int inner = c.groundAt(c.x() + (int) Math.round(Math.cos(a) * radius * 0.55), c.z() + (int) Math.round(Math.sin(a) * radius * 0.55));
                int outer = c.groundAt(c.x() + (int) Math.round(Math.cos(a) * radius), c.z() + (int) Math.round(Math.sin(a) * radius));
                open = inner <= sea - 15 && outer <= sea - 10;
                low = Math.min(low, Math.min(inner, outer));
            }
            if (open) break;
            // (a smaller one may still fit where a big one would run into the shallows)
            if (attempt > 0 || radius <= 24) return null;
            radius = 20 + c.random().nextInt(4);
        }
        // its summit stays a few blocks under the waves
        int top = sea - 1 - (3 + c.random().nextInt(6));
        if (top - floor < 12) {
            return null;
        }
        boolean guyot = c.random().nextFloat() < 0.4F;
        float crown = guyot ? 5.0F + c.random().nextFloat() * Math.min(6.0F, radius * 0.22F) : 1.5F + c.random().nextFloat() * 1.5F;
        float angle = c.random().nextFloat() * Mth.TWO_PI, stretch = 1.0F + c.random().nextFloat() * 0.25F;
        return new Seamount(c.x(), c.z(), sea, floor, low, radius, top, crown, angle, stretch, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Seamount(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getInt("floor"), tag.getInt("low"), tag.getInt("radius"),
                tag.getInt("top"), tag.getFloat("crown"), tag.getFloat("angle"), tag.getFloat("stretch"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("sea", sea);
        tag.putInt("floor", floor);
        tag.putInt("low", low);
        tag.putInt("radius", radius);
        tag.putInt("top", top);
        tag.putFloat("crown", crown);
        tag.putFloat("angle", angle);
        tag.putFloat("stretch", stretch);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.SEAMOUNT;
    }

    @Override
    public BoundingBox bounds() {
        int r = Mth.ceil(stretch * (radius + 1.0F) * 1.19F) + 2;
        return new BoundingBox(cx - r, low - 10, cz - r, cx + r, sea - 1, cz + r);
    }

    private boolean guyot() {
        return crown >= 4.0F;
    }

    /** Where a point lies on the cone: {0 at the edge of the summit to 1 at the foot (more beyond it), the angle round the middle, the distance out, the same made lumpy}. */
    private float[] where(float px, float pz) {
        float[] l = Terrain.uv(px, pz, cx + 0.5, cz + 0.5, angle);
        float u = l[0] / stretch, v = l[1];
        float r = Mth.sqrt(u * u + v * v);
        float a = (float) Mth.atan2(v, u);
        // spurs and embayments round the flanks (the noise is read round a circle, so it has no seam)
        float lump = 1.0F + 0.36F * (Terrain.field(seed + 1, 20.0 + 3.0 * Mth.cos(a), 20.0 + 3.0 * Mth.sin(a), 1.3) - 0.5F);
        float rr = r / lump + 1.6F * (Terrain.field(seed + 2, px, pz, 4.0) - 0.5F);
        return new float[]{(rr - crown) / (radius - crown), a, r, rr};
    }

    /** How deep gully {@code k}'s groove is at a point, as a share of its full depth. */
    private float gully(int k, float a, float r) {
        float centre = Terrain.hash(seed, 10 + k) * Mth.TWO_PI + 0.012F * r * (Terrain.hash(seed, 20 + k) - 0.5F) * 10.0F + 0.15F * Mth.sin(r * 0.17F + k);
        float off = Mth.wrapDegrees((a - centre) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * r;
        float half = 2.0F + 1.6F * Terrain.hash(seed, 30 + k);
        return Math.max(0.0F, 1.0F - Mth.square(off / half));
    }

    /** The shape of the cone over one column: {the y of its top, how far down the flanks, how deep in a gully (0-1), 1 on a ledge}. */
    private float[] shape(int x, int z, int ground) {
        float[] w = where(x + 0.5F, z + 0.5F);
        float t = w[0], a = w[1], r = w[2];
        if (t >= 1.0F) return new float[]{ground, t, 0.0F, 0.0F};
        // concave flanks, steepest near the summit; a guyot breaks away more sharply from its flat top
        float f = t <= 0.0F ? 1.0F : (float) Math.pow(1.0F - t, guyot() ? 1.2 : 1.6);
        float raw = ground + (top - ground) * f;
        if (!guyot() && w[3] < crown * 0.6F) raw -= 1.0F; // a little pit in the summit, its last vent
        // ledges where harder lava flows stand out, at heights that wander round the cone
        float step = 4.0F + Mth.floor(Terrain.hash(seed, 1) * 2.99F);
        float phase = step * Terrain.field(seed + 3, 20.0 + 2.0 * Mth.cos(a), 20.0 + 2.0 * Mth.sin(a), 1.0) + Terrain.field(seed + 4, x, z, 3.0);
        float q = (raw + phase) / step, fr = q - Mth.floor(q);
        float terraced = (Mth.floor(q) + Terrain.smooth((fr - 0.55F) / 0.45F)) * step - phase;
        float ledges = 0.7F * Terrain.smooth(t / 0.2F) * (1.0F - Terrain.smooth((t - 0.7F) / 0.3F));
        float h = Mth.lerp(ledges, raw, Math.min(raw, terraced));
        // gullies down the flanks, deepest halfway down
        int n = 6 + Mth.floor(Terrain.hash(seed, 2) * 4.99F);
        float g = 0.0F;
        for (int k = 0; k < n; k++) g = Math.max(g, gully(k, a, r));
        float depth = (2.5F + 2.5F * Terrain.hash(seed, 3)) * Terrain.smooth(t / 0.25F) * (1.0F - Terrain.smooth((t - 0.75F) / 0.25F));
        h -= g * depth;
        return new float[]{Math.min(top, h), t, g * Math.min(1.0F, depth / 1.5F), ledges > 0.3F && fr < 0.55F ? 1.0F : 0.0F};
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                if (where(x + 0.5F, z + 0.5F)[0] >= 1.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                // only on the open sea bed (not down a trench too deep for the bounds, nor on a shoal it would only bury)
                if (ground < b.minY() + 1 || ground >= top || level.getFluidState(pos.set(x, ground + 1, z)).isEmpty()) continue;
                float[] s = shape(x, z, ground);
                int peak = Math.round(s[0]);
                if (peak <= ground) continue;
                // weed rooted in the old sea bed would be left buried or cut off, so it goes
                for (int h = peak + 1; h <= sea - 1; h++) {
                    pos.set(x, h, z);
                    BlockState was = level.getBlockState(pos);
                    if (!(was.is(Blocks.KELP) || was.is(Blocks.KELP_PLANT) || was.is(Blocks.SEAGRASS) || was.is(Blocks.TALL_SEAGRASS))) break;
                    level.setBlock(pos, Blocks.WATER.defaultBlockState(), Terrain.FLAGS);
                }
                boolean warm = warm(level.getBiome(pos.set(x, peak + 1, z)));
                for (int h = ground + 1; h < peak; h++) Terrain.set(level, pos, x, h, z, rock(x, h, z));
                Terrain.set(level, pos, x, peak, z, surface(x, peak, z, s, warm));
                if (s[1] < LIVING && level.getFluidState(pos.set(x, peak + 1, z)).is(Fluids.WATER)) life(level, pos, x, peak, z, s[1], warm);
            }
        }
    }

    /** Whether the sea over a column is warm enough for coral (the vanilla oceans are all equally "temperate", so it goes by which sea it is). */
    private static boolean warm(Holder<Biome> biome) {
        return biome.is(Biomes.WARM_OCEAN) || biome.is(Biomes.LUKEWARM_OCEAN) || biome.is(Biomes.DEEP_LUKEWARM_OCEAN)
                || biome.value().getBaseTemperature() >= 0.9F;
    }

    /** The rock of the cone: lava flows of basalt and stone, with beds of ash (tuff) between them and a dark blackstone dyke cutting through them. */
    private BlockState rock(int x, int h, int z) {
        float n = Terrain.noise(seed + 5, x, h, z);
        int bed = Math.floorMod(h + Math.round(4.0F * Terrain.field(seed + 6, x, z, 11.0)), 9);
        float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle + Terrain.hash(seed, 4) * 2.0F);
        // (the dyke runs out from under the summit down one flank)
        if (l[0] > -2.0F && Math.abs(l[1] + 0.06F * l[0] * (Terrain.hash(seed, 5) - 0.5F) + 0.8F * Mth.sin(l[0] * 0.2F)) < 0.7F && n < 0.85F) {
            return Blocks.BLACKSTONE.defaultBlockState();
        }
        if (bed < 2) return n < 0.8F ? Blocks.TUFF.defaultBlockState() : Blocks.STONE.defaultBlockState();
        if (n < 0.1F) return Blocks.BLACKSTONE.defaultBlockState();
        if (n < 0.6F) return Blocks.BASALT.defaultBlockState();
        if (n < 0.82F) return Blocks.STONE.defaultBlockState();
        return n < 0.93F ? Blocks.SMOOTH_BASALT.defaultBlockState() : Blocks.TUFF.defaultBlockState();
    }

    /** The top block of a column: rubble down the gullies and on the ledges, wave-worn sand on a guyot, coral on a warm summit. */
    private BlockState surface(int x, int h, int z, float[] s, boolean warm) {
        float n = Terrain.noise(seed + 7, x, h, z);
        float t = s[1];
        if (warm && t < LIVING * 0.8F && Terrain.field(seed + 8, x, z, 5.0) > 0.4F) {
            int kind = Mth.clamp((int) (Terrain.field(seed + 9, x, z, 4.0) * 5.0F + (n - 0.5F)), 0, CORAL.length - 1);
            return CORAL[kind];
        }
        if (s[2] > 0.6F) return n < 0.7F ? Blocks.GRAVEL.defaultBlockState() : ModBlocks.BLACK_SAND.get().defaultBlockState();
        if (s[3] > 0.0F && n < 0.5F) return Blocks.GRAVEL.defaultBlockState();
        if (guyot() && t <= 0.05F) {
            // planed off by the waves when it stood at the surface, and strewn with their sand and shingle
            return n < 0.45F ? Blocks.SAND.defaultBlockState() : n < 0.8F ? Blocks.GRAVEL.defaultBlockState() : rock(x, h, z);
        }
        return n < 0.15F ? Blocks.GRAVEL.defaultBlockState() : rock(x, h, z);
    }

    /** The life on the summit and upper flanks, in the water over a column whose top is at {@code h}. */
    private void life(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int h, int z, float t, boolean warm) {
        int deep = sea - 1 - h;
        float n = Terrain.noise(seed + 10, x, h, z);
        // thinning out further down, where less light reaches
        float thin = 1.0F - 0.5F * Terrain.smooth(t / LIVING);
        if (warm) {
            int kind = (int) (Terrain.noise(seed + 11, x, 0, z) * 4.99F);
            if (n < 0.2F * thin) {
                Terrain.set(level, pos, x, h + 1, z, FANS[kind]);
            } else if (n < 0.34F * thin) {
                Terrain.set(level, pos, x, h + 1, z, PLANTS[kind]);
            } else if (n < 0.4F * thin) {
                Terrain.set(level, pos, x, h + 1, z, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + Math.floorMod(Math.round(n * 400.0F), 4)));
            } else if (n > 0.93F) {
                Terrain.set(level, pos, x, h + 1, z, Blocks.SEAGRASS.defaultBlockState());
            }
            return;
        }
        float bed = Terrain.field(seed + 12, x, z, 6.0);
        if (deep >= 3 && bed > 0.42F && n < 0.65F * thin) {
            // a frond of kelp, reaching up toward the light but stopping short of the surface
            int tall = 1 + Math.round(Terrain.noise(seed + 13, x, 0, z) * Math.min(14, deep - 2));
            for (int k = 1; k <= tall; k++) {
                BlockState s = k < tall ? Blocks.KELP_PLANT.defaultBlockState()
                        : Blocks.KELP.defaultBlockState().setValue(KelpBlock.AGE, 20 + Math.round(Terrain.noise(seed + 14, x, 0, z) * 5.0F));
                Terrain.set(level, pos, x, h + k, z, s);
            }
        } else if (n < 0.4F * thin) {
            if (deep >= 3 && n < 0.07F) {
                Terrain.set(level, pos, x, h + 1, z, Blocks.TALL_SEAGRASS.defaultBlockState());
                Terrain.set(level, pos, x, h + 2, z, Blocks.TALL_SEAGRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
            } else {
                Terrain.set(level, pos, x, h + 1, z, Blocks.SEAGRASS.defaultBlockState());
            }
        }
    }
}
