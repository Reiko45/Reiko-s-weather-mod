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
 * A cinder cone: the simplest kind of volcano, a steep cone of loose black and rust-red cinders (scoria) built
 * up round a single vent by one eruption of lava fountains, with a bowl-shaped crater on top. When the lava
 * finally broke out it breached the cone on one side and ran away across the land as a rough black flow.
 */
public record CinderCone(int cx, int cz, int y, int radius, int height, float breach, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        int radius = 14 + c.random().nextInt(9);
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 1 || c.relief(radius, 10) > 8 || !c.fits(ground, 6, 30)) {
            return null;
        }
        // cinder piles up as steep as it will stand
        int height = Math.round(radius * (0.55F + 0.15F * c.random().nextFloat()));
        return new CinderCone(c.x(), c.z(), ground, radius, height, c.random().nextFloat() * Mth.TWO_PI, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new CinderCone(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getInt("h"), tag.getFloat("breach"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.putInt("h", height);
        tag.putFloat("breach", breach);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.CINDER_CONE;
    }

    @Override
    public BoundingBox bounds() {
        int r = radius + 36;
        return new BoundingBox(cx - r, y - 10, cz - r, cx + r, y + height + 6, cz + r);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float crater = radius * 0.34F;
        float rim = height * (1.0F - crater / radius);
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float dx = x + 0.5F - cx, dz = z + 0.5F - cz;
                float dist = Mth.sqrt(dx * dx + dz * dz);
                float theta = Mth.wrapDegrees((float) Math.toDegrees(Mth.atan2(dz, dx) - breach)) * Mth.DEG_TO_RAD;
                float wobble = 0.93F + 0.14F * Terrain.field(seed, x, z, 8.0);
                float d = dist / (radius * wobble);
                int ground = Terrain.ground(level, pos, x, z);
                float n = Terrain.noise(seed, x, y, z);
                // the lava flow that broke out through the breach, spreading as it ran downhill
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, breach);
                // it spreads as it runs downhill, then pinches out in a rounded tongue at the far end
                float reach = radius + 30.0F + 6.0F * Terrain.field(seed + 4, x, z, 9.0);
                float flowWidth = (2.5F + l[0] * 0.18F + 2.0F * Terrain.field(seed + 1, x, z, 7.0))
                        * Mth.sqrt(Math.max(0.0F, 1.0F - Mth.square(Math.max(0.0F, l[0] - reach + 12.0F) / 12.0F)));
                boolean flow = l[0] > crater && l[0] < reach && Math.abs(l[1] + 4.0F * Mth.sin(l[0] * 0.08F)) < flowWidth;
                if (d > 1.0F && !flow) continue;
                float h = 0.0F;
                if (d < 1.0F) {
                    // straight, steep sides, a little concave at the foot
                    h = height * Math.min(1.0F - d, 1.0F - d * 0.9F) * (d > 0.8F ? Terrain.smooth((1.0F - d) / 0.2F) * 0.9F + 0.1F : 1.0F);
                    float cd = dist / crater;
                    if (cd < 1.0F) h = rim - height * 0.42F * (1.0F - cd * cd); // the crater
                    // the breach: the rim torn away on one side, down toward the flow
                    float notch = Math.max(0.0F, 1.0F - Math.abs(theta) / 0.35F);
                    if (notch > 0.0F && cd > 0.5F) h = Math.min(h, Mth.lerp(notch, h, rim * 0.45F * Math.max(0.0F, 1.0F - d)));
                }
                int top = y + Math.round(h);
                if (flow) {
                    // the flow: a rough, blocky surface a couple of blocks thick
                    int crust = 1 + Math.round(1.5F * Terrain.field(seed + 2, x, z, 3.0) * Mth.clamp((flowWidth - Math.abs(l[1])) / 2.0F, 0.0F, 1.0F));
                    top = Math.max(top, ground + crust);
                }
                if (top <= ground) continue;
                Terrain.clearAbove(level, pos, x, ground, z, top - ground + 10);
                for (int k = ground + 1; k <= top; k++) {
                    BlockState s;
                    if (flow && d >= 1.0F || flow && k > y + h) {
                        s = Terrain.noise(seed, x, k, z) < 0.6F ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
                    } else if (k > top - 2) {
                        // the loose cinders on the surface: black, rusted red near the crater where the steam oxidised them
                        float red = Terrain.field(seed + 3, x, k * 2.0, 5.0) + (d < 0.5F ? 0.3F : 0.0F);
                        s = red > 0.85F ? Blocks.RED_TERRACOTTA.defaultBlockState() : red > 0.7F ? Blocks.BROWN_TERRACOTTA.defaultBlockState()
                                : n < 0.5F ? ModBlocks.BLACK_SAND.get().defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
                    } else {
                        s = Terrain.noise(seed, x, k, z) < 0.3F ? Blocks.TUFF.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
                    }
                    Terrain.set(level, pos, x, k, z, s);
                }
                // sparse pioneer plants on the old flow
                if (flow && d >= 1.0F && n > 0.985F) Terrain.set(level, pos, x, top + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
            }
        }
    }
}
