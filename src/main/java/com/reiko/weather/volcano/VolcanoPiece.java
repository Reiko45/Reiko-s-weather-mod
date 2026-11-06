package com.reiko.weather.volcano;

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

/** The whole volcano as a single structure piece; it builds whatever part falls in each chunk. */
public class VolcanoPiece extends StructurePiece {
    private final VolcanoShape shape;

    public VolcanoPiece(VolcanoShape shape) {
        super(ModStructures.VOLCANO_PIECE.get(), 0, bounds(shape));
        this.shape = shape;
    }

    public VolcanoPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ModStructures.VOLCANO_PIECE.get(), tag);
        this.shape = VolcanoShape.load(tag.getCompound("volcano"));
    }

    public static BoundingBox bounds(VolcanoShape shape) {
        return new BoundingBox(shape.cx() - shape.radius(), shape.baseY() - 40, shape.cz() - shape.radius(),
                shape.cx() + shape.radius(), shape.peakY() + 32, shape.cz() + shape.radius());
    }

    public VolcanoShape shape() {
        return shape;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.put("volcano", shape.save());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        VolcanoBuilder.build(level, shape, box);
    }
}
