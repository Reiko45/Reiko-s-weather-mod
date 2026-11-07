package com.reiko.weather.landform;

import com.mojang.serialization.Codec;
import com.reiko.weather.config.WeatherConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.StringRepresentable;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Every kind of landform, with how to plan one and load one back. */
public enum LandformKind implements StringRepresentable {
    HOT_SPRINGS(HotSprings::plan, HotSprings::load, () -> WeatherConfig.HOT_SPRINGS.get()),
    CRATER_LAKE(CraterLake::plan, CraterLake::load, () -> WeatherConfig.CRATER_LAKES.get()),
    METEOR_CRATER(MeteorCrater::plan, MeteorCrater::load, () -> WeatherConfig.METEOR_CRATERS.get()),
    SALT_FLAT(SaltFlat::plan, SaltFlat::load, () -> WeatherConfig.SALT_FLATS.get()),
    GLACIER(Glacier::plan, Glacier::load, () -> WeatherConfig.GLACIERS.get()),
    TIDAL_FLAT(TidalFlat::plan, TidalFlat::load, () -> WeatherConfig.TIDAL_FLATS.get()),
    DUNES(Dunes::plan, Dunes::load, () -> WeatherConfig.DUNES.get());

    public static final Codec<LandformKind> CODEC = StringRepresentable.fromEnum(LandformKind::values);

    private final Function<PlanContext, Landform> planner;
    private final Function<CompoundTag, Landform> loader;
    private final BooleanSupplier enabled;

    LandformKind(Function<PlanContext, Landform> planner, Function<CompoundTag, Landform> loader, BooleanSupplier enabled) {
        this.planner = planner;
        this.loader = loader;
        this.enabled = enabled;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean enabled() {
        return !WeatherConfig.SPEC.isLoaded() || enabled.getAsBoolean();
    }

    /** A landform of this kind around a column, or null if it doesn't fit there. */
    @Nullable
    public Landform plan(PlanContext context) {
        return planner.apply(context);
    }

    public Landform load(CompoundTag tag) {
        return loader.apply(tag);
    }

    @Nullable
    public static LandformKind byName(String name) {
        for (LandformKind kind : values()) {
            if (kind.getSerializedName().equals(name)) return kind;
        }
        return null;
    }
}
