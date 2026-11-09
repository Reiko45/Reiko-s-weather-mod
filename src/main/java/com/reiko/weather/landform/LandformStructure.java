package com.reiko.weather.landform;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reiko.weather.ReikosWeather;
import com.reiko.weather.registry.ModStructures;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * A landform shaped by the weather (hot springs, a salt flat, a glacier...). One structure type covers them
 * all; each structure's JSON names its {@code landform}.
 */
public class LandformStructure extends Structure {
    public static final MapCodec<LandformStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            LandformKind.CODEC.fieldOf("landform").forGetter(s -> s.kind)
    ).apply(i, LandformStructure::new));

    private final LandformKind kind;

    public LandformStructure(StructureSettings settings, LandformKind kind) {
        super(settings);
        this.kind = kind;
    }

    public LandformKind kind() {
        return kind;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!kind.enabled()) {
            return Optional.empty();
        }
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
        // working out the ground's height means running the terrain noise for a whole column, so never do a column twice
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap heights = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        PlanContext plan = new PlanContext(context.random(), x, z,
                (px, pz) -> heights.computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(px, pz), k -> context.chunkGenerator()
                        .getFirstOccupiedHeight(px, pz, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState())),
                context.chunkGenerator().getSeaLevel(), context.heightAccessor());
        Landform landform = kind.plan(plan);
        if (landform == null) {
            return Optional.empty();
        }
        // never on (or half-buried by) a volcano; villages are kept clear by the structure sets themselves
        var b = landform.bounds();
        if (nearVolcano(context, x, z, 125 + Math.max(b.getXSpan(), b.getZSpan()) / 2)) {
            return Optional.empty();
        }
        int y = Math.max(plan.groundAt(x, z), plan.seaLevel() - 1);
        return Optional.of(new GenerationStub(new BlockPos(x, y, z), builder -> builder.addPiece(new LandformPiece(landform))));
    }

    private static final ResourceKey<StructureSet> VOLCANOES = ResourceKey.create(Registries.STRUCTURE_SET, ReikosWeather.id("volcanoes"));

    /**
     * Whether a volcano could be centred within {@code reach} blocks of (x, z). It goes by the volcano structure
     * set's candidate chunks (where one would go if the biome allowed it), so it errs on the side of keeping clear.
     */
    private static boolean nearVolcano(GenerationContext context, int x, int z, int reach) {
        StructureSet set = context.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(VOLCANOES);
        if (set == null || !(set.placement() instanceof RandomSpreadStructurePlacement placement)) {
            return false;
        }
        int spacing = placement.spacing(), range = reach / 16 + 1;
        int cx = x >> 4, cz = z >> 4;
        for (int rx = Math.floorDiv(cx - range, spacing); rx <= Math.floorDiv(cx + range, spacing); rx++) {
            for (int rz = Math.floorDiv(cz - range, spacing); rz <= Math.floorDiv(cz + range, spacing); rz++) {
                ChunkPos p = placement.getPotentialStructureChunk(context.seed(), rx * spacing, rz * spacing);
                double dx = p.getMiddleBlockX() - x, dz = p.getMiddleBlockZ() - z;
                if (dx * dx + dz * dz < (double) reach * reach) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.LANDFORM.get();
    }
}
