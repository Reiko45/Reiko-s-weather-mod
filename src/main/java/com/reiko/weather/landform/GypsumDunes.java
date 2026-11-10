package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * White gypsum dunes (like White Sands in New Mexico): a field of dazzling white dunes beside a dry lake bed. Rain
 * washes gypsum out of the hills into the basin, where it can't drain away; the lake dries in the sun, leaving its
 * floor crusted with gypsum and great blades of selenite crystal, and the wind breaks the crust into grains and blows
 * them downwind into rows of dunes. Gypsum hardly warms in the sun, so the sand stays cool underfoot. The few plants
 * that live here grow tall enough to keep their heads above the moving sand, and when a dune moves on it leaves them
 * standing on pedestals of sand their roots have bound together.
 *
 * @param angle  the way the wind blows (downwind)
 * @param height the tallest the dunes grow
 * @param y      the dry lake bed's level
 * @param px     the dry lake's middle (upwind of the dunes)
 * @param rx     the dry lake's half-length (across the wind)
 * @param rz     the dry lake's half-width (along the wind)
 */
public record GypsumDunes(int cx, int cz, int radius, float angle, int height, int y, int px, int pz, int rx, int rz, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        int radius = 40 + c.random().nextInt(21);
        if (ground < c.seaLevel() + 2 || c.relief(Math.round(radius * 0.6F), 8) > 10 || !c.fits(ground, 10, 24)) {
            return null;
        }
        float angle = c.random().nextFloat() * Mth.TWO_PI;
        int height = 4 + c.random().nextInt(6);
        // the lake bed lies upwind: the dunes are made of what the wind blows off it
        int px = c.x() - Math.round(Mth.cos(angle) * radius * 0.4F), pz = c.z() - Math.round(Mth.sin(angle) * radius * 0.4F);
        int rx = 12 + c.random().nextInt(8), rz = 8 + c.random().nextInt(6);
        int low = c.groundAt(px, pz);
        for (int i = 0; i < 6; i++) {
            float a = i * Mth.TWO_PI / 6.0F;
            low = Math.min(low, c.groundAt(px + Math.round(Mth.cos(a) * rx), pz + Math.round(Mth.sin(a) * rx)));
        }
        if (low < c.seaLevel() + 1) {
            return null;
        }
        return new GypsumDunes(c.x(), c.z(), radius, angle, height, low - 1, px, pz, rx, rz, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new GypsumDunes(tag.getInt("x"), tag.getInt("z"), tag.getInt("r"), tag.getFloat("angle"), tag.getInt("h"), tag.getInt("y"),
                tag.getInt("px"), tag.getInt("pz"), tag.getInt("rx"), tag.getInt("rz"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("r", radius);
        tag.putFloat("angle", angle);
        tag.putInt("h", height);
        tag.putInt("y", y);
        tag.putInt("px", px);
        tag.putInt("pz", pz);
        tag.putInt("rx", rx);
        tag.putInt("rz", rz);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.GYPSUM_DUNES;
    }

    @Override
    public BoundingBox bounds() {
        int r = Mth.ceil(radius / 0.85F) + 2;
        return new BoundingBox(cx - r, y - 20, cz - r, cx + r, y + 34, cz + r);
    }

    /** How far out from the middle of the dry lake a point is: under 1 on the lake bed. */
    private float lake(double x, double z) {
        float[] l = Terrain.uv(x, z, px, pz, angle);
        return Mth.sqrt(Mth.square(l[1] / rx) + Mth.square(l[0] / rz)) / (0.9F + 0.2F * Terrain.field(seed + 1, x, z, 7.0));
    }

    /** How far the ground round the lake is planed down toward it (1 at its edge, 0 from {@code e} = 1.6 out). */
    private static float flat(float e) {
        return e < 1.6F ? 1.0F - Terrain.smooth((e - 1.0F) / 0.6F) : 0.0F;
    }

    /** The lowest a column {@code e} out from the lake's middle can end up, given its ground. */
    private int lowest(float e, int ground) {
        return e < 1.0F ? y : Math.round(Mth.lerp(flat(e), ground, y));
    }

    /**
     * Turns to calcite any water left standing on a column the dunes leave alone, as high as the ground beside it is
     * planed down below it, so none of it can pour out over the lake bed.
     */
    private void seal(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, int ground) {
        int top = ground;
        while (top < b.maxY() && !level.getFluidState(pos.set(x, top + 1, z)).isEmpty()) top++;
        int low = top;
        for (int[] s : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) low = Math.min(low, lowest(lake(x + s[0] + 0.5, z + s[1] + 0.5), top));
        for (int h = Math.max(ground, low) + 1; h <= top; h++) Terrain.set(level, pos, x, h, z, Blocks.CALCITE.defaultBlockState());
    }

    /** How far out from the middle of the field a point is: under 1 inside it. */
    private float field(double x, double z) {
        return (float) Mth.length(x - cx, z - cz) / (radius * (0.85F + 0.3F * Terrain.field(seed, x, z, 16.0)));
    }

    /** How much sand the dunes pile on the ground at a point. */
    private float dunes(double x, double z, float edge) {
        float[] l = Terrain.uv(x, z, cx, cz, angle);
        float u = l[0], v = l[1];
        float wavelength = 18.0F + 6.0F * Terrain.hash(seed, 2);
        // rows across the wind: a long, gentle climb up the windward side to the crest, then a steep slip face
        float wavy = u + 5.0F * Mth.sin(v * 0.05F + Terrain.hash(seed, 3) * 6.0F) + 2.0F * Mth.sin(v * 0.14F);
        float p = Mth.positiveModulo(wavy / wavelength, 1.0F);
        float profile = p < 0.78F ? p / 0.78F : (1.0F - p) / 0.22F;
        // the crests break up into crescent-shaped stretches with gaps of bare flat between them
        float row = Mth.floor(wavy / wavelength);
        float breaks = Terrain.smooth((Terrain.field(seed + 4, v + row * 37.0F, row * 11.0F, 14.0) - 0.25F) / 0.35F);
        float swell = 0.7F + 0.3F * Mth.sin(v * 0.04F + row);
        float sheet = 0.6F + 0.9F * Terrain.field(seed + 9, x, z, 9.0);
        return Math.max(height * profile * breaks * swell, sheet) * edge;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState gypsum = ModBlocks.GYPSUM_SAND.get().defaultBlockState();
        BlockState mud = ModBlocks.CRACKED_MUD.get().defaultBlockState();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float f = field(x + 0.5, z + 0.5), e = lake(x + 0.5, z + 0.5);
                if (f >= 1.0F && e >= 1.7F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 6 || ground > b.maxY() - 16) continue;
                if (f >= 1.0F && e >= 1.6F) {
                    seal(level, pos, b, x, z, ground);
                    continue;
                }
                // a shallow pool (or a little lava) is buried under the sand rather than left to spill; deeper water is sealed in
                int wet = 0;
                while (wet < 4 && !level.getFluidState(pos.set(x, ground + 1 + wet, z)).isEmpty()) wet++;
                if (wet >= 4 || ground + wet > b.maxY() - 16) {
                    seal(level, pos, b, x, z, ground);
                    continue;
                }
                for (int h = ground + 1; h <= ground + wet; h++) Terrain.set(level, pos, x, h, z, Blocks.CALCITE.defaultBlockState());
                ground += wet;
                float n = Terrain.noise(seed, x, 0, z);
                if (e < 1.0F) {
                    // the dry lake bed: dead flat, cracked mud and gypsum crust
                    Terrain.clearAbove(level, pos, x, ground, z, 16);
                    for (int h = y + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    for (int h = Math.min(ground + 1, y - 2); h < y; h++) Terrain.set(level, pos, x, h, z, h == y - 1 ? mud : Blocks.CALCITE.defaultBlockState());
                    float crust = Terrain.field(seed + 5, x, z, 3.0);
                    Terrain.set(level, pos, x, y, z, crust < 0.3F ? Blocks.WHITE_TERRACOTTA.defaultBlockState() : crust > 0.72F ? gypsum : mud);
                    selenite(level, pos, x, z, e);
                    continue;
                }
                // round the lake the ground is planed down to it, under a sheet of sand
                float flat = flat(e);
                int base = Math.round(Mth.lerp(flat, ground, y));
                float edge = Terrain.smooth((1.0F - f) / 0.25F) * (1.0F - flat);
                int top = base + Math.round(dunes(x + 0.5, z + 0.5, edge) + flat);
                // the field fades out into the desert in patches
                if (top <= ground && n > edge * 3.0F && flat == 0.0F) continue;
                Terrain.clearAbove(level, pos, x, ground, z, Math.min(Math.max(0, top - ground) + 12, b.maxY() - ground));
                for (int h = top + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                // white gypsum all the way down to a hard bed, so none of it can fall away
                int bottom = Math.min(ground, top) - 2;
                for (int h = bottom; h <= top; h++) Terrain.set(level, pos, x, h, z, h == bottom ? Blocks.CALCITE.defaultBlockState() : gypsum);
                // hardy shrubs on the bare flats between the dunes, on pedestals of root-bound gypsum
                if (top - base <= 1 && flat == 0.0F && n > 0.992F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.WHITE_TERRACOTTA.defaultBlockState());
                    Terrain.set(level, pos, x, top + 2, z, Blocks.DEAD_BUSH.defaultBlockState());
                }
            }
        }
    }

    /** Selenite crystals standing on the lake bed, the odd few on a grid of cells across it. */
    private void selenite(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, float e) {
        if (e > 0.85F) return;
        int gx = Math.floorDiv(x - px, 6), gz = Math.floorDiv(z - pz, 6);
        long cell = seed + gx * 341873128712L + gz * 132897987541L;
        if (Terrain.hash(cell, 0) > 0.3F) return;
        int sx = px + gx * 6 + 1 + Math.round(Terrain.hash(cell, 1) * 3.0F), sz = pz + gz * 6 + 1 + Math.round(Terrain.hash(cell, 2) * 3.0F);
        if (x != sx || z != sz) return;
        int tall = 1 + Math.round(Terrain.hash(cell, 3) * 2.0F);
        for (int h = 1; h <= tall; h++) {
            Terrain.set(level, pos, x, y + h, z, h == tall && tall > 1 ? Blocks.QUARTZ_PILLAR.defaultBlockState()
                    : Terrain.hash(cell, 4) < 0.5F ? Blocks.CALCITE.defaultBlockState() : Blocks.QUARTZ_PILLAR.defaultBlockState());
        }
    }
}
