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
 * Mud volcanoes: where gas and water under pressure deep underground force their way up through soft clay, they
 * bring a slurry of grey mud with them, which builds little cones round each vent. A bubbling pool sits in the
 * top of each, and flows of mud spread out round them, drying and cracking in the sun.
 */
public record MudVolcanoes(int cx, int cz, int y, int count, long seed) implements Landform {
    private static final int SPREAD = 22;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 1 || c.relief(SPREAD, 10) > 8 || !c.fits(ground, 6, 12)) {
            return null;
        }
        return new MudVolcanoes(c.x(), c.z(), ground, 4 + c.random().nextInt(5), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new MudVolcanoes(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("count"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("count", count);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.MUD_VOLCANOES;
    }

    @Override
    public BoundingBox bounds() {
        int r = SPREAD + 22;
        return new BoundingBox(cx - r, y - 8, cz - r, cx + r, y + 12, cz + r);
    }

    private float[] cone(int i) {
        float a = Terrain.hash(seed, i) * Mth.TWO_PI, d = Mth.sqrt(Terrain.hash(seed, 100 + i)) * SPREAD;
        float r = 2.5F + 3.5F * Terrain.hash(seed, 200 + i);
        return new float[]{cx + 0.5F + Math.round(Mth.cos(a) * d), cz + 0.5F + Math.round(Mth.sin(a) * d), r, Math.max(1.0F, r * (0.45F + 0.4F * Terrain.hash(seed, 300 + i)))};
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState mud = Blocks.MUD.defaultBlockState();
        float[][] cones = new float[count][];
        for (int i = 0; i < count; i++) cones[i] = cone(i);
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float px = x + 0.5F, pz = z + 0.5F;
                float rise = 0.0F, flows = 0.0F;
                boolean vent = false;
                for (float[] c : cones) {
                    float d = Mth.sqrt(Mth.square(px - c[0]) + Mth.square(pz - c[1]));
                    if (d < 0.5F) vent = true;
                    if (d < c[2]) rise = Math.max(rise, c[3] * (1.0F - d / c[2]));
                    // the mud that's spilled out round each cone, in lobes
                    float theta = (float) Mth.atan2(pz - c[1], px - c[0]);
                    float reach = c[2] * (1.6F + 0.9F * Mth.sin(theta * 3.0F + c[0]) + 0.5F * Mth.sin(theta * 5.0F + c[1]));
                    if (d < reach) flows = Math.max(flows, 1.0F - d / reach);
                }
                if (flows <= 0.0F && rise <= 0.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                float n = Terrain.noise(seed, x, y, z);
                int top = ground + Math.round(rise);
                Terrain.clearAbove(level, pos, x, ground, z, 10);
                for (int k = ground - 1; k <= top; k++) Terrain.set(level, pos, x, k, z, k < ground ? Blocks.CLAY.defaultBlockState() : mud);
                if (vent) {
                    // the vent: gas bubbling up through a little pool of mud and water
                    Terrain.set(level, pos, x, top - 2, z, ModBlocks.VOLCANIC_VENT.get().defaultBlockState());
                    Terrain.set(level, pos, x, top - 1, z, Blocks.WATER.defaultBlockState());
                    Terrain.set(level, pos, x, top, z, Blocks.AIR.defaultBlockState());
                    continue;
                }
                // fresh mud is wet and grey; older flows have dried and cracked
                boolean dry = flows < 0.45F && rise < 0.5F && Terrain.field(seed + 1, x, z, 4.0) > 0.35F;
                if (dry) Terrain.set(level, pos, x, top, z, n < 0.5F ? ModBlocks.CRACKED_MUD.get().defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState());
                else if (rise > 0.0F && n < 0.25F) Terrain.set(level, pos, x, top, z, Blocks.PACKED_MUD.defaultBlockState());
            }
        }
    }
}
