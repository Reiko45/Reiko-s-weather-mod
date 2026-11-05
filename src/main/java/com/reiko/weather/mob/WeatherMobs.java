package com.reiko.weather.mob;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.weather.Climate;
import com.reiko.weather.weather.LocalWeather;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.WeatherType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

import javax.annotation.Nullable;

/** Weather-themed spawns, and teaching animals to get out of the storm. */
public final class WeatherMobs {
    /** Swaps naturally spawning monsters for ones that suit the weather. */
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL || !WeatherConfig.SPEC.isLoaded() || !WeatherConfig.WEATHER_MOBS.get()) {
            return;
        }
        Mob mob = event.getEntity();
        if (!(event.getLevel().getLevel() instanceof ServerLevel level)) {
            return;
        }
        // only plain zombies and skeletons, not their variants (which extend them)
        boolean zombie = mob.getType() == EntityType.ZOMBIE, skeleton = mob.getType() == EntityType.SKELETON;
        if (!zombie && !skeleton) {
            return;
        }
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        LocalWeather local = WeatherManager.local(level, pos);
        if (local == null || !level.canSeeSky(pos)) {
            return;
        }
        EntityType<? extends Mob> replacement = replacement(level, pos, local.type(), zombie);
        if (replacement == null || level.random.nextFloat() > 0.7F) {
            return;
        }
        Mob swapped = replacement.create(level);
        if (swapped == null) {
            return;
        }
        swapped.moveTo(event.getX(), event.getY(), event.getZ(), mob.getYRot(), 0.0F);
        swapped.finalizeSpawn(level, event.getDifficulty(), MobSpawnType.NATURAL, null);
        event.setSpawnCancelled(true);
        level.addFreshEntityWithPassengers(swapped);
    }

    @Nullable
    private static EntityType<? extends Mob> replacement(ServerLevel level, BlockPos pos, WeatherType weather, boolean zombie) {
        Climate climate = Climate.at(level, pos);
        if (!zombie && (weather == WeatherType.BLIZZARD || weather == WeatherType.SNOW || weather == WeatherType.ICE_STORM)) {
            return EntityType.STRAY;
        }
        if (zombie && (weather == WeatherType.SANDSTORM || weather == WeatherType.HEATWAVE) && climate == Climate.ARID) {
            return EntityType.HUSK;
        }
        if (zombie && (weather == WeatherType.HEAVY_RAIN || weather == WeatherType.HURRICANE) && nearWater(level, pos)) {
            return EntityType.DROWNED;
        }
        return null;
    }

    private static boolean nearWater(ServerLevel level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-6, -3, -6), pos.offset(6, 1, 6))) {
            if (level.getFluidState(p).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Animal animal) {
            animal.goalSelector.addGoal(2, new SeekShelterGoal(animal));
        }
    }

    private WeatherMobs() {}
}
