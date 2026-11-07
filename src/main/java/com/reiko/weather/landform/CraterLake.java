package com.reiko.weather.landform;

import com.reiko.weather.volcano.VolcanoBuilder;
import com.reiko.weather.volcano.VolcanoPiece;
import com.reiko.weather.volcano.VolcanoShape;
import com.reiko.weather.volcano.VolcanoStructure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/** A long-extinct volcano: a gentle, grassy cone (trees grow on it) with a deep lake filling its crater. */
public record CraterLake(VolcanoShape shape) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        int height = 22 + c.random().nextInt(19);
        int radius = Mth.clamp((int) (height * 1.9F) + c.random().nextInt(12), 48, 96);
        int center = c.groundAt(c.x(), c.z());
        if (center < c.seaLevel() - 2) {
            return null;
        }
        int sum = 0;
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F;
            sum += c.groundAt(c.x() + Math.round(Mth.cos(a) * radius * 0.7F), c.z() + Math.round(Mth.sin(a) * radius * 0.7F));
        }
        int base = Math.max((sum / 8 + center) / 2, c.seaLevel() - 6);
        if (!VolcanoStructure.fits(c.heights(), base, height)) {
            return null;
        }
        return new CraterLake(new VolcanoShape(c.x(), c.z(), base, height, radius, 10 + c.random().nextInt(7), 8 + c.random().nextInt(5), c.random().nextLong()));
    }

    static Landform load(CompoundTag tag) {
        return new CraterLake(VolcanoShape.load(tag));
    }

    @Override
    public CompoundTag save() {
        return shape.save();
    }

    @Override
    public LandformKind kind() {
        return LandformKind.CRATER_LAKE;
    }

    @Override
    public BoundingBox bounds() {
        return VolcanoPiece.bounds(shape);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        VolcanoBuilder.build(level, shape, area, VolcanoBuilder.Style.EXTINCT);
    }
}
