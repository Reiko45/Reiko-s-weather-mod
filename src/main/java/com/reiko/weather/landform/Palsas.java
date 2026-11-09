package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Palsas: low, oval mounds of peat standing out of a subarctic bog, each held up by a lens of ice inside it. Where the
 * winter snow blew thin over the bog, the frost got deep enough into the peat to stay frozen through the summer; the
 * ice grew and pushed the peat up, which kept it drier and colder still, so the ice grew more. In time the tops split
 * open along their crests, the ice starts to melt, and the mound slumps in on itself until all that's left is a round
 * pond with a broken rim of peat.
 *
 * @param mounds {x, z, half-length, half-width, height, angle in degrees} for each palsa
 * @param ponds  {x, z, radius} for each palsa that has collapsed into a pond
 */
public record Palsas(int cx, int cz, int y, int radius, float angle, int[] mounds, int[] ponds, long seed) implements Landform {
    private static final float EDGE = 1.3F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        int radius = 24 + c.random().nextInt(10);
        int ring = radius * 3 / 4;
        if (ground < c.seaLevel() || c.relief(ring, 8) > 8 || !c.fits(ground, 10, 20)) {
            return null;
        }
        // the bog lies level, at the land's average height (the same points the relief was taken at)
        int sum = ground;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI * 2.0 / 8;
            sum += c.groundAt(c.x() + (int) Math.round(Math.cos(a) * ring), c.z() + (int) Math.round(Math.sin(a) * ring));
        }
        int y = Math.max(c.seaLevel(), Math.round(sum / 9.0F));
        float angle = c.random().nextFloat() * Mth.TWO_PI;
        int n = 4 + c.random().nextInt(4);
        int[] mounds = new int[n * 6];
        for (int i = 0; i < n; i++) {
            float a = c.random().nextFloat() * Mth.TWO_PI, d = Mth.sqrt(c.random().nextFloat()) * radius * 0.6F;
            mounds[i * 6] = c.x() + Math.round(Mth.cos(a) * d);
            mounds[i * 6 + 1] = c.z() + Math.round(Mth.sin(a) * d);
            mounds[i * 6 + 2] = 3 + c.random().nextInt(5);
            mounds[i * 6 + 3] = 2 + c.random().nextInt(3);
            mounds[i * 6 + 4] = 2 + c.random().nextInt(4);
            mounds[i * 6 + 5] = c.random().nextInt(180);
        }
        int m = 1 + c.random().nextInt(2);
        int[] ponds = new int[m * 3];
        for (int i = 0; i < m; i++) {
            float a = c.random().nextFloat() * Mth.TWO_PI, d = Mth.sqrt(c.random().nextFloat()) * radius * 0.4F;
            ponds[i * 3] = c.x() + Math.round(Mth.cos(a) * d);
            ponds[i * 3 + 1] = c.z() + Math.round(Mth.sin(a) * d);
            ponds[i * 3 + 2] = 3 + c.random().nextInt(3);
        }
        return new Palsas(c.x(), c.z(), y, radius, angle, mounds, ponds, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Palsas(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getFloat("angle"), tag.getIntArray("mounds"),
                tag.getIntArray("ponds"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.putFloat("angle", angle);
        tag.put("mounds", new IntArrayTag(mounds));
        tag.put("ponds", new IntArrayTag(ponds));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.PALSAS;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * EDGE * 1.15F) + 3;
        return new BoundingBox(cx - r, y - 12, cz - r, cx + r, y + 24, cz + r);
    }

    /** How far out toward the bog's edge a point is (1 at the edge of the bog itself). */
    private float ellipse(float px, float pz) {
        float[] l = Terrain.uv(px, pz, cx, cz, angle);
        float wobble = 1.0F + 0.15F * (Terrain.field(seed, px, pz, 11.0) - 0.5F) * 2.0F;
        return Mth.sqrt(Mth.square(l[0] / radius) + Mth.square(l[1] / (radius * 0.7F))) / wobble;
    }

    /** Palsa {@code i} at a point: {how far out from its middle (0-1), across its crest} or null outside it. */
    @Nullable
    private float[] palsa(int i, float px, float pz) {
        float a = mounds[i * 6 + 5] * Mth.DEG_TO_RAD;
        float dx = px - mounds[i * 6] - 0.5F, dz = pz - mounds[i * 6 + 1] - 0.5F;
        float along = dx * Mth.cos(a) + dz * Mth.sin(a), across = -dx * Mth.sin(a) + dz * Mth.cos(a);
        float q = Mth.sqrt(Mth.square(along / mounds[i * 6 + 2]) + Mth.square(across / mounds[i * 6 + 3])) * (0.9F + 0.2F * Terrain.field(seed + 1 + i, px, pz, 3.0));
        return q < 1.0F ? new float[]{q, across, along / mounds[i * 6 + 2]} : null;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState peat = ModBlocks.PEAT.get().defaultBlockState(), ice = Blocks.PACKED_ICE.defaultBlockState();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float px = x + 0.5F, pz = z + 0.5F;
                float e = ellipse(px, pz);
                if (e > EDGE) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 4 || ground > b.maxY() - 12) continue;
                // water already standing here: if it stands higher than the bog, it's filled in rather than let drain into it
                int surface = ground;
                for (pos.set(x, ground + 1, z); surface < ground + 8 && !level.getFluidState(pos).isEmpty(); pos.move(Direction.UP)) surface++;
                float n = Terrain.noise(seed, x, 0, z);
                if (e > 1.0F || surface > y) {
                    // the margin: the land eases down (or up) to the bog's level
                    int target = surface > y ? surface : Math.round(Mth.lerp(Terrain.smooth((e - 1.0F) / (EDGE - 1.0F)), y, ground));
                    if (target == ground) continue;
                    Terrain.clearAbove(level, pos, x, Math.max(ground, target), z, Math.min(12, b.maxY() - Math.max(ground, target)));
                    for (int h = target + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    for (int h = ground + 1; h < target; h++) Terrain.set(level, pos, x, h, z, surface > y ? peat : Blocks.DIRT.defaultBlockState());
                    Terrain.set(level, pos, x, target, z, surface > y || n < 0.4F ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
                    continue;
                }
                Terrain.clearAbove(level, pos, x, Math.max(ground, y), z, Math.min(16, b.maxY() - Math.max(ground, y)));
                for (int h = y + 1; h <= Math.max(ground, surface); h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                // deep peat under the whole bog
                for (int h = Math.min(ground, y) - 3; h <= y; h++) Terrain.set(level, pos, x, h, z, h == y - 4 ? Blocks.CLAY.defaultBlockState() : peat);

                // a collapsed palsa: a round pond with a broken rim of slumped peat
                float pd = Float.MAX_VALUE;
                for (int i = 0; i < ponds.length / 3; i++) {
                    pd = Math.min(pd, Mth.sqrt(Mth.square(px - ponds[i * 3] - 0.5F) + Mth.square(pz - ponds[i * 3 + 1] - 0.5F))
                            / (ponds[i * 3 + 2] * (0.9F + 0.2F * Terrain.field(seed + 20 + i, px, pz, 3.0))));
                }
                if (pd < 1.0F) {
                    int bed = y - 1 - Math.round(2.0F * (1.0F - pd * pd));
                    Terrain.set(level, pos, x, bed, z, Blocks.MUD.defaultBlockState());
                    for (int h = bed + 1; h <= y; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                    continue;
                }
                if (pd < 1.45F) {
                    int rim = y + (n > 0.45F ? 1 : 0) + (n > 0.85F ? 1 : 0);
                    for (int h = y + 1; h <= rim; h++) Terrain.set(level, pos, x, h, z, h == rim && n > 0.7F ? Blocks.MOSS_BLOCK.defaultBlockState() : peat);
                    continue;
                }

                // the tallest palsa here
                float best = 0.0F;
                float[] at = null;
                int which = -1;
                for (int i = 0; i < mounds.length / 6; i++) {
                    float[] p = palsa(i, px, pz);
                    if (p == null) continue;
                    // steep sides and a broad, rounded top
                    float h = mounds[i * 6 + 4] * (float) Math.pow(Math.min(1.0F, (1.0F - p[0]) * 2.5F), 0.7);
                    if (h > best) {
                        best = h;
                        at = p;
                        which = i;
                    }
                }
                int top = y + Math.round(best);
                if (top > y && at != null) {
                    float hgt = mounds[which * 6 + 4];
                    // the crest has split open along its length, showing the peat inside
                    boolean crack = Math.abs(at[1]) < 0.6F && Math.abs(at[2]) < 0.7F && at[0] < 0.6F && hgt >= 3;
                    boolean side = best < hgt * 0.8F;
                    int coreTop = top - 2 - (crack ? 1 : 0);
                    for (int h = y - 1; h <= top; h++) {
                        BlockState s;
                        if (crack && h > top - 2) s = Blocks.AIR.defaultBlockState();
                        else if (at[0] < 0.72F && h <= coreTop) s = ice; // the ice lens holding it up
                        else if (h == top && !side) s = n < 0.65F ? Blocks.MOSS_BLOCK.defaultBlockState() : n < 0.85F ? peat : Blocks.PODZOL.defaultBlockState();
                        else s = peat;
                        Terrain.set(level, pos, x, h, z, s);
                    }
                    int surf = crack ? top - 2 : top;
                    float p = Terrain.noise(seed, x, 1, z);
                    pos.set(x, surf, z);
                    if (!crack && level.getBlockState(pos).is(Blocks.MOSS_BLOCK) && p < 0.4F) {
                        Terrain.set(level, pos, x, surf + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                    } else if (!crack && p > 0.92F) {
                        Terrain.set(level, pos, x, surf + 1, z, Blocks.DEAD_BUSH.defaultBlockState()); // dwarf shrubs, dry up on the mound
                    }
                    continue;
                }

                // the bog between: wet moss and mud, with shallow pools
                float pool = Terrain.field(seed + 2, x, z, 4.0);
                if (pool > 0.74F && e < 0.85F) {
                    Terrain.set(level, pos, x, y - 1, z, Blocks.MUD.defaultBlockState());
                    Terrain.set(level, pos, x, y, z, Blocks.WATER.defaultBlockState());
                    continue;
                }
                float moss = Terrain.field(seed + 3, x, z, 3.0);
                Terrain.set(level, pos, x, y, z, moss > 0.42F ? Blocks.MOSS_BLOCK.defaultBlockState() : moss > 0.3F ? Blocks.MUD.defaultBlockState()
                        : n < 0.5F ? Blocks.GRASS_BLOCK.defaultBlockState() : peat);
                if (n > 0.93F) {
                    Terrain.set(level, pos, x, y + 1, z, Blocks.LILY_OF_THE_VALLEY.defaultBlockState()); // cotton grass
                } else if (n > 0.89F) {
                    Terrain.set(level, pos, x, y + 1, z, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 1)); // cloudberries
                } else if (n > 0.6F) {
                    Terrain.set(level, pos, x, y + 1, z, moss > 0.42F ? Blocks.MOSS_CARPET.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
                }
            }
        }
    }
}
