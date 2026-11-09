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
    DUNES(Dunes::plan, Dunes::load, () -> WeatherConfig.DUNES.get()),
    ARROYO(Arroyo::plan, Arroyo::load, () -> WeatherConfig.ARROYOS.get()),
    CENOTE(Cenote::plan, Cenote::load, () -> WeatherConfig.CENOTES.get()),
    OASIS(Oasis::plan, Oasis::load, () -> WeatherConfig.OASES.get()),
    WATERFALL(Waterfall::plan, Waterfall::load, () -> WeatherConfig.WATERFALLS.get()),
    BASALT_COLUMNS(BasaltColumns::plan, BasaltColumns::load, () -> WeatherConfig.BASALT_COLUMNS.get()),
    SEA_STACKS(SeaStacks::plan, SeaStacks::load, () -> WeatherConfig.SEA_STACKS.get()),
    ATOLL(Atoll::plan, Atoll::load, () -> WeatherConfig.ATOLLS.get()),
    FJORD(Fjord::plan, Fjord::load, () -> WeatherConfig.FJORDS.get()),
    ERRATICS(Erratics::plan, Erratics::load, () -> WeatherConfig.ERRATICS.get()),
    PEAT_BOG(PeatBog::plan, PeatBog::load, () -> WeatherConfig.PEAT_BOGS.get()),
    TRAVERTINE(Travertine::plan, Travertine::load, () -> WeatherConfig.TRAVERTINE.get()),
    TUFA_LAKE(TufaLake::plan, TufaLake::load, () -> WeatherConfig.TUFA_LAKES.get()),
    HOODOOS(Hoodoos::plan, Hoodoos::load, () -> WeatherConfig.HOODOOS.get()),
    ROCK_ARCH(RockArch::plan, RockArch::load, () -> WeatherConfig.ROCK_ARCHES.get()),
    SLOT_CANYON(SlotCanyon::plan, SlotCanyon::load, () -> WeatherConfig.SLOT_CANYONS.get()),
    LIMESTONE_PAVEMENT(LimestonePavement::plan, LimestonePavement::load, () -> WeatherConfig.LIMESTONE_PAVEMENT.get()),
    TOWER_KARST(TowerKarst::plan, TowerKarst::load, () -> WeatherConfig.TOWER_KARST.get()),
    OXBOW_LAKE(OxbowLake::plan, OxbowLake::load, () -> WeatherConfig.OXBOW_LAKES.get()),
    BRAIDED_RIVER(BraidedRiver::plan, BraidedRiver::load, () -> WeatherConfig.BRAIDED_RIVERS.get()),
    DELTA(Delta::plan, Delta::load, () -> WeatherConfig.DELTAS.get()),
    CIRQUE(Cirque::plan, Cirque::load, () -> WeatherConfig.CIRQUES.get()),
    KETTLE_LAKES(KettleLakes::plan, KettleLakes::load, () -> WeatherConfig.KETTLE_LAKES.get()),
    DRUMLINS(Drumlins::plan, Drumlins::load, () -> WeatherConfig.DRUMLINS.get()),
    PERMAFROST(Permafrost::plan, Permafrost::load, () -> WeatherConfig.PERMAFROST.get()),
    CHALK_CLIFFS(ChalkCliffs::plan, ChalkCliffs::load, () -> WeatherConfig.CHALK_CLIFFS.get()),
    BARRIER_ISLAND(BarrierIsland::plan, BarrierIsland::load, () -> WeatherConfig.BARRIER_ISLANDS.get()),
    SALT_MARSH(SaltMarsh::plan, SaltMarsh::load, () -> WeatherConfig.SALT_MARSHES.get()),
    CINDER_CONE(CinderCone::plan, CinderCone::load, () -> WeatherConfig.CINDER_CONES.get()),
    MAAR(Maar::plan, Maar::load, () -> WeatherConfig.MAARS.get()),
    MUD_VOLCANOES(MudVolcanoes::plan, MudVolcanoes::load, () -> WeatherConfig.MUD_VOLCANOES.get()),
    TERMITE_MOUNDS(TermiteMounds::plan, TermiteMounds::load, () -> WeatherConfig.TERMITE_MOUNDS.get());

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
