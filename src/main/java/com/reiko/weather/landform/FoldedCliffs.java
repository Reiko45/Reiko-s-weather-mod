package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.function.BiPredicate;

/**
 * A folded cliff (like Stair Hole and the Lulworth Crumple on the Dorset coast): a cliff face where the layers of the rock,
 * laid down flat as limestones, sands and muds on an old sea floor, can be seen bent into great folds, arching up in
 * anticlines and sagging down into synclines, with tighter crumples between. They were squeezed that way, slowly and
 * deep underground, when distant continents pushed together and raised mountains. Long after, the sea (or a river cutting
 * down) carved the land back into a sheer face that cuts straight through the folds and lays them bare. The harder beds
 * stand out on the face, the softer ones crumble back between them, and the rock that falls lies in an apron of rubble at
 * the foot.
 *
 * @param angle the way the face looks out (downhill, or out to sea)
 * @param base  the level of the foot of the cliff
 * @param top   the level the clifftop is made up to (the land behind can stand higher)
 * @param hi    the highest ground seen behind it
 * @param warm  sandstones and terracotta (otherwise grey limestones and shales)
 */
public record FoldedCliffs(int cx, int cz, float angle, int length, int base, int top, int hi, int sea, boolean warm, long seed) implements Landform {
    /** How far back from the face the land is changed, how far out in front, and how wide the flat foot is. */
    private static final float BACK = 22.0F, FRONT = 26.0F, FLAT = 10.0F, APRON = 8.0F;
    /** How far back from the face the folded beds are laid in. */
    private static final float PAINT = 4.0F;
    private static final BlockState[] GREY = {
            Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState(),
            Blocks.DEEPSLATE.defaultBlockState(), Blocks.CALCITE.defaultBlockState(), Blocks.CALCITE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(),
            Blocks.ANDESITE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(), Blocks.TUFF.defaultBlockState(), Blocks.TUFF.defaultBlockState(),
            Blocks.DIORITE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState(),
            Blocks.CALCITE.defaultBlockState(), Blocks.TUFF.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(),
            Blocks.DIORITE.defaultBlockState(), Blocks.DIORITE.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState()};
    private static final BlockState[] WARM = {
            Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState(),
            Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.WHITE_TERRACOTTA.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
            Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(),
            Blocks.TERRACOTTA.defaultBlockState(), Blocks.BROWN_TERRACOTTA.defaultBlockState(), Blocks.YELLOW_TERRACOTTA.defaultBlockState(),
            Blocks.YELLOW_TERRACOTTA.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState(),
            Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
            Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.WHITE_TERRACOTTA.defaultBlockState()};

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 4 || !c.fits(ground, 30, 70)) {
            return null;
        }
        // it needs a real drop: a steep slope, or high land falling to the sea
        float[] down = c.downhill(16);
        if (down[1] < 8) {
            return null;
        }
        float a = down[0];
        int front = ground - Math.round(down[1]);
        int back = c.groundAt(c.x() - (int) Math.round(Math.cos(a) * 16), c.z() - (int) Math.round(Math.sin(a) * 16));
        boolean coast = front < c.seaLevel();
        int base = coast ? c.seaLevel() : front + 1;
        int behind = Math.max(ground, back);
        // (the land behind has to make most of the cliff's height itself)
        if (behind < base + 8) {
            return null;
        }
        int top = Mth.clamp(behind, base + 15, base + 40);
        int length = 60 + c.random().nextInt(41), hi = behind;
        // the cliff runs only as far as the high ground does
        int ends = 0;
        for (int s = -1; s <= 1; s += 2) {
            double[] p = Terrain.xz(0.0, s * length * 0.5, c.x(), c.z(), a);
            int g = c.groundAt(Mth.floor(p[0]), Mth.floor(p[1]));
            if (g < base + 6) ends++;
            hi = Math.max(hi, g);
        }
        if (ends == 2) length = Math.max(44, length * 2 / 3);
        return new FoldedCliffs(c.x(), c.z(), a, length, base, top, hi, c.seaLevel(), c.random().nextFloat() < 0.4F, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new FoldedCliffs(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("base"), tag.getInt("top"),
                tag.getInt("hi"), tag.getInt("sea"), tag.getBoolean("warm"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putInt("base", base);
        tag.putInt("top", top);
        tag.putInt("hi", hi);
        tag.putInt("sea", sea);
        tag.putBoolean("warm", warm);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.FOLDED_CLIFFS;
    }

    @Override
    public BoundingBox bounds() {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (float u : new float[]{-BACK - 6.0F, FRONT + 2.0F}) {
            for (float v : new float[]{-length / 2.0F - 1.0F, length / 2.0F + 1.0F}) {
                double[] p = Terrain.xz(u, v, cx + 0.5, cz + 0.5, angle);
                minX = Math.min(minX, Mth.floor(p[0]));
                maxX = Math.max(maxX, Mth.ceil(p[0]));
                minZ = Math.min(minZ, Mth.floor(p[1]));
                maxZ = Math.max(maxZ, Mth.ceil(p[1]));
            }
        }
        return new BoundingBox(minX, Math.min(base, sea) - 12, minZ, maxX, Math.max(top, hi) + 26, maxZ);
    }

    /** Where the face stands, {@code v} blocks along it: nearly straight, with shallow bays. */
    private float face(float v) {
        return -2.0F + 1.5F * Mth.sin(v * 0.09F + Terrain.hash(seed, 1) * 6.0F) + Mth.sin(v * 0.23F + Terrain.hash(seed, 2) * 6.0F);
    }

    private boolean inside(float u, float v) {
        if (Math.abs(v) > length / 2.0F) return false;
        float d = u - face(v);
        return d >= -BACK && d <= FRONT;
    }

    /**
     * How far the folded beds are bent up or down at a point on (or behind) the face: broad anticlines and synclines,
     * strongest in the middle of the cliff, with tight crumples where the squeezing was worst. The folds plunge gently
     * into the cliff, so the pattern shifts a little from the face back.
     */
    private float fold(float u, float v) {
        float big = 4.0F + 8.0F * Terrain.hash(seed, 3), wave = 30.0F + 34.0F * Terrain.hash(seed, 4);
        float small = 1.0F + 1.8F * Terrain.hash(seed, 5), ripple = 9.0F + 7.0F * Terrain.hash(seed, 6);
        float swell = 0.65F + 0.35F * Mth.sin(v * 0.035F + Terrain.hash(seed, 7) * 6.0F);
        float crumple = 0.4F + 1.2F * Terrain.smooth((Terrain.field(seed + 9, v, 0.0, 22.0) - 0.3F) / 0.4F);
        return big * swell * Mth.sin(v * Mth.TWO_PI / wave + Terrain.hash(seed, 8) * 6.0F + u * 0.04F)
                + small * crumple * Mth.sin(v * Mth.TWO_PI / ripple + Terrain.hash(seed, 10) * 6.0F + u * 0.1F)
                + (Terrain.hash(seed, 11) - 0.5F) * 0.12F * v;
    }

    private BlockState bed(int y, float fold) {
        BlockState[] beds = warm ? WARM : GREY;
        return beds[Math.floorMod(Mth.floor(y - base - fold), beds.length)];
    }

    /** The beds that weather back into the face: shales, mudstones and soft clays. */
    private static boolean soft(BlockState s) {
        return s.is(Blocks.DEEPSLATE) || s.is(Blocks.TUFF) || s.is(BlockTags.TERRACOTTA);
    }

    /** What lay on the clifftop before (turf, sand or snow) to put back over the rock, or null for bare rock. */
    @Nullable
    private static BlockState soil(BlockState was) {
        if (was.is(Blocks.GRASS_BLOCK)) return Blocks.GRASS_BLOCK.defaultBlockState();
        if (was.is(BlockTags.DIRT) || was.is(BlockTags.SAND) || was.is(Blocks.SNOW_BLOCK)) return was;
        return null;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float half = length / 2.0F;
        BiPredicate<Integer, Integer> outside = (nx, nz) -> {
            float[] o = Terrain.uv(nx + 0.5, nz + 0.5, cx + 0.5, cz + 0.5, angle);
            return !inside(o[0], o[1]);
        };
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
                float u = l[0], v = l[1];
                if (!inside(u, v)) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 8 || ground > b.maxY() - 16) continue;
                // a pond on the land goes (the sea, and rivers at its level, are left alone)
                int surface = Terrain.surface(level, pos, x, ground, z);
                boolean wet = surface > ground && surface >= sea;
                float ends = Terrain.smooth((half - Math.abs(v)) / 14.0F);
                float dist = u - face(v);
                float n = Terrain.noise(seed, x, 3, z);
                // (where the land stands much higher than planned the foot rises with it, so the face never grows too tall)
                int foot = Math.max(base, ground - (top - base) - 6);
                int rock, newTop;
                if (dist < 0.0F) {
                    // the clifftop, made up to the cliff's height right at the edge and fading into the land behind
                    float back = Terrain.smooth((-dist - 5.0F) / 15.0F);
                    newTop = Math.max(ground, Math.round(Mth.lerp(ends, ground, Mth.lerp(back, top, ground))));
                    rock = newTop;
                    if (newTop == ground && !wet && dist < -PAINT) continue;
                } else {
                    // the foot: cut down flat, then ramping back up to the ground further out
                    int cut = foot + Math.max(0, Math.round((dist - FLAT) * 1.2F));
                    rock = Math.round(Mth.lerp(ends, ground, Math.min(ground, cut)));
                    newTop = rock;
                    if (dist < APRON) {
                        float heap = (4.0F + 4.0F * Terrain.field(seed + 12, v, 0.0, 20.0)) * (float) Math.pow(1.0F - dist / APRON, 1.5)
                                * (0.75F + 0.5F * Terrain.field(seed + 13, x, z, 3.0)) * ends;
                        if (heap >= 0.5F) newTop = Math.max(rock, foot + Math.round(heap));
                    }
                    if (newTop == ground && !wet) continue;
                }
                pos.set(x, ground, z);
                BlockState was = level.getBlockState(pos);
                float fold = fold(u, v);
                int highest = Math.max(ground, newTop);
                Terrain.clearAbove(level, pos, x, highest, z, Math.min(14, b.maxY() - highest));
                // cutting down (a pond here goes too, though one just outside keeps a wall of rock)
                for (int h = newTop + 1; h <= Math.max(ground, wet ? surface : ground); h++) {
                    if (h < sea) continue;
                    Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, outside) ? bed(h, fold) : Blocks.AIR.defaultBlockState());
                }
                if (dist < 0.0F) {
                    // the folded beds right through the face (and under whatever the clifftop was built up with)
                    BlockState soil = soil(was);
                    int from = dist >= -PAINT ? Math.min(ground, foot) - 3 : ground + 1;
                    for (int h = from; h <= newTop; h++) {
                        BlockState s = bed(h, fold);
                        if (soil != null && h == newTop) s = soil;
                        else if (soil != null && h >= newTop - 1 && soil.is(BlockTags.DIRT)) s = Blocks.DIRT.defaultBlockState();
                        Terrain.set(level, pos, x, h, z, s);
                    }
                    // the softer beds crumble back a block into the face, under the harder ones
                    if (dist >= -0.75F && ends > 0.6F) {
                        for (int h = foot + 10; h <= newTop - 4; h++) {
                            if (soft(bed(h, fold)) && Terrain.noise(seed + 14, x, h, z) < 0.85F) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                        }
                    }
                    if (soil != null && soil.is(Blocks.GRASS_BLOCK) && n > 0.55F && level.getBlockState(pos.set(x, newTop + 1, z)).isAir()) {
                        Terrain.set(level, pos, x, newTop + 1, z, n > 0.96F ? Blocks.OXEYE_DAISY.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
                    }
                    continue;
                }
                // the rock floor at the foot shows the folds where they cross it, under the apron of fallen rubble
                if (rock >= sea - 3) {
                    for (int h = rock - 1; h <= rock; h++) Terrain.set(level, pos, x, h, z, bed(h, fold));
                }
                for (int h = rock + 1; h <= newTop; h++) {
                    float m = Terrain.noise(seed + 15, x, h, z);
                    BlockState s = m < 0.3F ? Blocks.COBBLESTONE.defaultBlockState() : m < 0.42F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                            : m < 0.62F ? Blocks.GRAVEL.defaultBlockState() : bed(h + 5 + Math.round(m * 20.0F), fold);
                    Terrain.set(level, pos, x, h, z, s);
                }
            }
        }
    }
}
