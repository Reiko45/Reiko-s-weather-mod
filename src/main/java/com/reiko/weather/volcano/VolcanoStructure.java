package com.reiko.weather.volcano;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.util.RandomSource;

import java.util.Optional;
import java.util.function.IntBinaryOperator;
import javax.annotation.Nullable;

/** A volcano rising out of the land (or the sea, as an island). See {@link VolcanoShape} for its geometry. */
public class VolcanoStructure extends Structure {
    public static final MapCodec<VolcanoStructure> CODEC = simpleCodec(VolcanoStructure::new);

    public VolcanoStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (WeatherConfig.SPEC.isLoaded() && !WeatherConfig.VOLCANO_GENERATION.get()) {
            return Optional.empty();
        }
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
        VolcanoShape shape = plan(context.random(), x, z, context.chunkGenerator(), context.heightAccessor(), context.randomState());
        if (shape == null) {
            return Optional.empty();
        }
        return Optional.of(new GenerationStub(new BlockPos(x, shape.baseY(), z), builder -> builder.addPiece(new VolcanoPiece(shape))));
    }

    /** Picks a size for a volcano centred on (x, z), sitting on the land around it. Null if it wouldn't fit. */
    public static VolcanoShape plan(RandomSource random, int x, int z, ChunkGenerator generator, LevelHeightAccessor heights, RandomState state) {
        return plan(random, x, z, (px, pz) -> generator.getFirstOccupiedHeight(px, pz, Heightmap.Types.OCEAN_FLOOR_WG, heights, state),
                generator.getSeaLevel(), heights);
    }

    /** @param ground the height of the ground (under any water) at a column */
    @Nullable
    public static VolcanoShape plan(RandomSource random, int x, int z, IntBinaryOperator ground, int seaLevel, LevelHeightAccessor heights) {
        int height = 40 + random.nextInt(31);
        int radius = Mth.clamp((int) (height * 1.7F) + random.nextInt(16), 64, 120);
        int sum = 0;
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F;
            sum += ground.applyAsInt(x + Math.round(Mth.cos(a) * radius * 0.7F), z + Math.round(Mth.sin(a) * radius * 0.7F));
        }
        int center = ground.applyAsInt(x, z);
        // sits on the surrounding land; out at sea it rises from the sea bed (an island), but not from the deepest trenches
        int base = Math.max((sum / 8 + center) / 2, seaLevel - 24);
        return fits(heights, base, height) ? new VolcanoShape(x, z, base, height, radius, 7 + random.nextInt(6), 10 + random.nextInt(8), random.nextLong()) : null;
    }

    public static boolean fits(LevelHeightAccessor heights, int base, int height) {
        return base + height + 40 < heights.getMaxBuildHeight() && base - 40 > heights.getMinBuildHeight();
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.VOLCANO.get();
    }
}
