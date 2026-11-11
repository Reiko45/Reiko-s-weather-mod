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
    TERMITE_MOUNDS(TermiteMounds::plan, TermiteMounds::load, () -> WeatherConfig.TERMITE_MOUNDS.get()),
    TIANKENG(Tiankeng::plan, Tiankeng::load, () -> WeatherConfig.TIANKENG.get()),
    BLUE_HOLE(BlueHole::plan, BlueHole::load, () -> WeatherConfig.BLUE_HOLES.get()),
    STONE_FOREST(StoneForest::plan, StoneForest::load, () -> WeatherConfig.STONE_FORESTS.get()),
    NATURAL_BRIDGE(NaturalBridge::plan, NaturalBridge::load, () -> WeatherConfig.NATURAL_BRIDGES.get()),
    SINKING_STREAM(SinkingStream::plan, SinkingStream::load, () -> WeatherConfig.SINKING_STREAMS.get()),
    VOLCANIC_NECK(VolcanicNeck::plan, VolcanicNeck::load, () -> WeatherConfig.VOLCANIC_NECKS.get()),
    SPATTER_CONES(SpatterCones::plan, SpatterCones::load, () -> WeatherConfig.SPATTER_CONES.get()),
    PILLOW_LAVA(PillowLava::plan, PillowLava::load, () -> WeatherConfig.PILLOW_LAVA.get()),
    OBSIDIAN_FLOW(ObsidianFlow::plan, ObsidianFlow::load, () -> WeatherConfig.OBSIDIAN_FLOWS.get()),
    NUNATAKS(Nunataks::plan, Nunataks::load, () -> WeatherConfig.NUNATAKS.get()),
    ROCK_GLACIER(RockGlacier::plan, RockGlacier::load, () -> WeatherConfig.ROCK_GLACIERS.get()),
    PALSAS(Palsas::plan, Palsas::load, () -> WeatherConfig.PALSAS.get()),
    STONE_CIRCLES(StoneCircles::plan, StoneCircles::load, () -> WeatherConfig.STONE_CIRCLES.get()),
    TORS(Tors::plan, Tors::load, () -> WeatherConfig.TORS.get()),
    YARDANGS(Yardangs::plan, Yardangs::load, () -> WeatherConfig.YARDANGS.get()),
    INSELBERG(Inselberg::plan, Inselberg::load, () -> WeatherConfig.INSELBERGS.get()),
    MESA(Mesa::plan, Mesa::load, () -> WeatherConfig.MESAS.get()),
    MUSHROOM_ROCKS(MushroomRocks::plan, MushroomRocks::load, () -> WeatherConfig.MUSHROOM_ROCKS.get()),
    SAILING_STONES(SailingStones::plan, SailingStones::load, () -> WeatherConfig.SAILING_STONES.get()),
    FAIRY_CIRCLES(FairyCircles::plan, FairyCircles::load, () -> WeatherConfig.FAIRY_CIRCLES.get()),
    TINAJAS(Tinajas::plan, Tinajas::load, () -> WeatherConfig.TINAJAS.get()),
    ALLUVIAL_FAN(AlluvialFan::plan, AlluvialFan::load, () -> WeatherConfig.ALLUVIAL_FANS.get()),
    TALUS_CONES(TalusCones::plan, TalusCones::load, () -> WeatherConfig.TALUS_CONES.get()),
    HORN(Horn::plan, Horn::load, () -> WeatherConfig.HORNS.get()),
    AVALANCHE_CHUTES(AvalancheChutes::plan, AvalancheChutes::load, () -> WeatherConfig.AVALANCHE_CHUTES.get()),
    LANDSLIDE_SCAR(LandslideScar::plan, LandslideScar::load, () -> WeatherConfig.LANDSLIDE_SCARS.get()),
    PETRIFIED_FOREST(PetrifiedForest::plan, PetrifiedForest::load, () -> WeatherConfig.PETRIFIED_FORESTS.get()),
    FOSSIL_BED(FossilBed::plan, FossilBed::load, () -> WeatherConfig.FOSSIL_BEDS.get()),
    SPIT(Spit::plan, Spit::load, () -> WeatherConfig.SPITS.get()),
    TOMBOLO(Tombolo::plan, Tombolo::load, () -> WeatherConfig.TOMBOLOS.get()),
    SHINGLE_BEACH(ShingleBeach::plan, ShingleBeach::load, () -> WeatherConfig.SHINGLE_BEACHES.get()),
    RAISED_BEACH(RaisedBeach::plan, RaisedBeach::load, () -> WeatherConfig.RAISED_BEACHES.get()),
    RIA(Ria::plan, Ria::load, () -> WeatherConfig.RIAS.get()),
    RIVER_TERRACES(RiverTerraces::plan, RiverTerraces::load, () -> WeatherConfig.RIVER_TERRACES.get()),
    RAPIDS(Rapids::plan, Rapids::load, () -> WeatherConfig.RAPIDS.get()),
    GORGE(Gorge::plan, Gorge::load, () -> WeatherConfig.GORGES.get()),
    HANGING_VALLEY(HangingValley::plan, HangingValley::load, () -> WeatherConfig.HANGING_VALLEYS.get()),
    NURSE_LOGS(NurseLogs::plan, NurseLogs::load, () -> WeatherConfig.NURSE_LOGS.get()),
    PENITENTES(Penitentes::plan, Penitentes::load, () -> WeatherConfig.PENITENTES.get()),
    GLACIAL_LAKE(GlacialLake::plan, GlacialLake::load, () -> WeatherConfig.GLACIAL_LAKES.get()),
    WHIRLPOOL(Whirlpool::plan, Whirlpool::load, () -> WeatherConfig.WHIRLPOOLS.get()),
    CALDERA(Caldera::plan, Caldera::load, () -> WeatherConfig.CALDERAS.get()),
    ACID_LAKE(AcidLake::plan, AcidLake::load, () -> WeatherConfig.ACID_LAKES.get()),
    LAVA_FIELD(LavaField::plan, LavaField::load, () -> WeatherConfig.LAVA_FIELDS.get()),
    DOLINE_FIELD(DolineField::plan, DolineField::load, () -> WeatherConfig.DOLINE_FIELDS.get()),
    COCKPIT_KARST(CockpitKarst::plan, CockpitKarst::load, () -> WeatherConfig.COCKPIT_KARST.get()),
    POLJE(Polje::plan, Polje::load, () -> WeatherConfig.POLJES.get()),
    PATERNOSTER_LAKES(PaternosterLakes::plan, PaternosterLakes::load, () -> WeatherConfig.PATERNOSTER_LAKES.get()),
    ROCHES_MOUTONNEES(RochesMoutonnees::plan, RochesMoutonnees::load, () -> WeatherConfig.ROCHES_MOUTONNEES.get()),
    RIFT_VALLEY(RiftValley::plan, RiftValley::load, () -> WeatherConfig.RIFT_VALLEYS.get()),
    AIGUILLES(Aiguilles::plan, Aiguilles::load, () -> WeatherConfig.AIGUILLES.get()),
    RAINBOW_MOUNTAINS(RainbowMountains::plan, RainbowMountains::load, () -> WeatherConfig.RAINBOW_MOUNTAINS.get()),
    CANYON(Canyon::plan, Canyon::load, () -> WeatherConfig.CANYONS.get()),
    GYPSUM_DUNES(GypsumDunes::plan, GypsumDunes::load, () -> WeatherConfig.GYPSUM_DUNES.get()),
    COPPICE_DUNES(CoppiceDunes::plan, CoppiceDunes::load, () -> WeatherConfig.COPPICE_DUNES.get()),
    SALT_DOME(SaltDome::plan, SaltDome::load, () -> WeatherConfig.SALT_DOMES.get()),
    PINK_LAKE(PinkLake::plan, PinkLake::load, () -> WeatherConfig.PINK_LAKES.get()),
    SPOTTED_LAKE(SpottedLake::plan, SpottedLake::load, () -> WeatherConfig.SPOTTED_LAKES.get()),
    ACID_POOLS(AcidPools::plan, AcidPools::load, () -> WeatherConfig.ACID_POOLS.get()),
    WAVE_ROCK(WaveRock::plan, WaveRock::load, () -> WeatherConfig.WAVE_ROCKS.get()),
    CRUSTED_FLATS(CrustedFlats::plan, CrustedFlats::load, () -> WeatherConfig.CRUSTED_FLATS.get()),
    PARABOLIC_DUNES(ParabolicDunes::plan, ParabolicDunes::load, () -> WeatherConfig.PARABOLIC_DUNES.get()),
    MOERAKI_BOULDERS(MoerakiBoulders::plan, MoerakiBoulders::load, () -> WeatherConfig.MOERAKI_BOULDERS.get()),
    COLOURED_BEACH(ColouredBeach::plan, ColouredBeach::load, () -> WeatherConfig.COLOURED_BEACHES.get()),
    GHOST_FOREST(GhostForest::plan, GhostForest::load, () -> WeatherConfig.GHOST_FORESTS.get()),
    STROMATOLITES(Stromatolites::plan, Stromatolites::load, () -> WeatherConfig.STROMATOLITES.get()),
    FAIRY_RINGS(FairyRings::plan, FairyRings::load, () -> WeatherConfig.FAIRY_RINGS.get()),
    VERNAL_POOLS(VernalPools::plan, VernalPools::load, () -> WeatherConfig.VERNAL_POOLS.get()),
    KIPUKA(Kipuka::plan, Kipuka::load, () -> WeatherConfig.KIPUKAS.get()),
    VOLCANIC_DIKES(VolcanicDikes::plan, VolcanicDikes::load, () -> WeatherConfig.VOLCANIC_DIKES.get()),
    STEAMING_GROUND(SteamingGround::plan, SteamingGround::load, () -> WeatherConfig.STEAMING_GROUND.get()),
    HYDROTHERMAL_VENTS(HydrothermalVents::plan, HydrothermalVents::load, () -> WeatherConfig.HYDROTHERMAL_VENTS.get()),
    BRINE_POOL(BrinePool::plan, BrinePool::load, () -> WeatherConfig.BRINE_POOLS.get()),
    KRUMMHOLZ(Krummholz::plan, Krummholz::load, () -> WeatherConfig.KRUMMHOLZ.get()),
    THERMOKARST_SLUMP(ThermokarstSlump::plan, ThermokarstSlump::load, () -> WeatherConfig.THERMOKARST.get()),
    GOOSENECKS(Goosenecks::plan, Goosenecks::load, () -> WeatherConfig.GOOSENECKS.get()),
    FLOODPLAIN(Floodplain::plan, Floodplain::load, () -> WeatherConfig.FLOODPLAINS.get()),
    BEAVER_POND(BeaverPond::plan, BeaverPond::load, () -> WeatherConfig.BEAVER_PONDS.get()),
    PATTERNED_FEN(PatternedFen::plan, PatternedFen::load, () -> WeatherConfig.PATTERNED_FENS.get()),
    CYPRESS_SWAMP(CypressSwamp::plan, CypressSwamp::load, () -> WeatherConfig.CYPRESS_SWAMPS.get()),
    LOESS_CLIFFS(LoessCliffs::plan, LoessCliffs::load, () -> WeatherConfig.LOESS_CLIFFS.get()),
    MIMA_MOUNDS(MimaMounds::plan, MimaMounds::load, () -> WeatherConfig.MIMA_MOUNDS.get()),
    TAFONI(Tafoni::plan, Tafoni::load, () -> WeatherConfig.TAFONI.get()),
    FRINGING_REEF(FringingReef::plan, FringingReef::load, () -> WeatherConfig.FRINGING_REEFS.get()),
    SOLIFLUCTION_LOBES(SolifluctionLobes::plan, SolifluctionLobes::load, () -> WeatherConfig.SOLIFLUCTION.get()),
    HOGBACKS(Hogbacks::plan, Hogbacks::load, () -> WeatherConfig.HOGBACKS.get()),
    FOLDED_CLIFFS(FoldedCliffs::plan, FoldedCliffs::load, () -> WeatherConfig.FOLDED_CLIFFS.get()),
    TEPUI(Tepui::plan, Tepui::load, () -> WeatherConfig.TEPUIS.get()),
    RIFT_FISSURE(RiftFissure::plan, RiftFissure::load, () -> WeatherConfig.RIFT_FISSURES.get()),
    ANCIENT_SHORELINES(AncientShorelines::plan, AncientShorelines::load, () -> WeatherConfig.ANCIENT_SHORELINES.get()),
    HANGING_GARDEN(HangingGarden::plan, HangingGarden::load, () -> WeatherConfig.HANGING_GARDENS.get()),
    LANDSLIDE_LAKE(LandslideLake::plan, LandslideLake::load, () -> WeatherConfig.LANDSLIDE_LAKES.get()),
    ICE_TOWERS(IceTowers::plan, IceTowers::load, () -> WeatherConfig.ICE_TOWERS.get()),
    INVERTED_CHANNEL(InvertedChannel::plan, InvertedChannel::load, () -> WeatherConfig.INVERTED_CHANNELS.get()),
    FAIRY_POOLS(FairyPools::plan, FairyPools::load, () -> WeatherConfig.FAIRY_POOLS.get());

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
