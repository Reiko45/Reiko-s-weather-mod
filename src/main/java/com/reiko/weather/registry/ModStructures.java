package com.reiko.weather.registry;

import com.reiko.weather.ReikosWeather;
import com.reiko.weather.volcano.VolcanoPiece;
import com.reiko.weather.volcano.VolcanoStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, ReikosWeather.MOD_ID);
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, ReikosWeather.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<VolcanoStructure>> VOLCANO = TYPES.register("volcano",
            () -> () -> VolcanoStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> VOLCANO_PIECE = PIECES.register("volcano",
            () -> VolcanoPiece::new);

    public static final DeferredHolder<StructureType<?>, StructureType<com.reiko.weather.landform.LandformStructure>> LANDFORM = TYPES.register("landform",
            () -> () -> com.reiko.weather.landform.LandformStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> LANDFORM_PIECE = PIECES.register("landform",
            () -> com.reiko.weather.landform.LandformPiece::new);

    /** The volcano structure itself (defined by data, in worldgen/structure/volcano.json). */
    public static final ResourceKey<Structure> VOLCANO_KEY = ResourceKey.create(Registries.STRUCTURE, ReikosWeather.id("volcano"));

    public static final ResourceKey<Structure> SALT_FLAT_KEY = ResourceKey.create(Registries.STRUCTURE, ReikosWeather.id("salt_flat"));
    public static final ResourceKey<Structure> GLACIER_KEY = ResourceKey.create(Registries.STRUCTURE, ReikosWeather.id("glacier"));
    public static final ResourceKey<Structure> DUNES_KEY = ResourceKey.create(Registries.STRUCTURE, ReikosWeather.id("dune_field"));

    private ModStructures() {}
}
