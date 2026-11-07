package com.reiko.weather.landform;

import com.reiko.weather.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** A whole landform as one structure piece; it builds whatever part falls in each chunk. */
public class LandformPiece extends StructurePiece {
    private final Landform landform;

    public LandformPiece(Landform landform) {
        super(ModStructures.LANDFORM_PIECE.get(), 0, landform.bounds());
        this.landform = landform;
    }

    public LandformPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ModStructures.LANDFORM_PIECE.get(), tag);
        LandformKind kind = LandformKind.byName(tag.getString("landform"));
        this.landform = (kind == null ? LandformKind.METEOR_CRATER : kind).load(tag.getCompound("shape"));
    }

    public Landform landform() {
        return landform;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("landform", landform.kind().getSerializedName());
        tag.put("shape", landform.save());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        landform.build(level, box);
    }
}
