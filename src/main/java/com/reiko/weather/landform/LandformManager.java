package com.reiko.weather.landform;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.registry.ModEffects;
import com.reiko.weather.weather.WeatherAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;


/**
 * Lets the weather work on the landforms near players (salt flats flooding and drying, glaciers melting
 * back, dunes creeping downwind, arroyos flash-flooding) and runs the hot springs' soak.
 */
public final class LandformManager {
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || level.getGameTime() % 20L != 7L
                || !WeatherConfig.SPEC.isLoaded()) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            BlockPos pos = player.blockPosition();
            if (!level.isLoaded(pos)) continue;
            // the structures with a reference in this chunk, then whether we're actually inside one of their pieces
            for (Structure structure : level.structureManager().getAllStructuresAt(pos).keySet()) {
                if (!(structure instanceof LandformStructure)) continue;
                StructureStart start = level.structureManager().getStructureWithPieceAt(pos, structure);
                if (!start.isValid()) continue;
                for (StructurePiece piece : start.getPieces()) {
                    if (piece instanceof LandformPiece landform) {
                        WeatherAdvancements.visitedLandform(player, landform.landform().kind());
                        landform.landform().tick(level, player);
                    }
                }
            }
        }
    }

    /** Soaking in a hot spring (water with a heat vent under it) warms you up and slowly heals you. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 40 != 0 || !player.isInWater()) {
            return;
        }
        BlockPos.MutableBlockPos pos = player.blockPosition().mutable();
        for (int i = 0; i < 5; i++) {
            if (player.level().getBlockState(pos).is(ModBlocks.VOLCANIC_VENT.get())) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, true, true));
                player.addEffect(new MobEffectInstance(ModEffects.WARMTH, 1200, 0, true, true, true));
                WeatherAdvancements.award(player, "hot_tub");
                return;
            }
            pos.move(0, -1, 0);
        }
    }

    private LandformManager() {}
}
