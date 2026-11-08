package com.reiko.weather.mob;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.weather.LocalWeather;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.WeatherType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;

/** During rough weather, animals out in the open walk to the nearest cover (under trees, roofs, overhangs). */
public class SeekShelterGoal extends Goal {
    private final PathfinderMob mob;
    @Nullable
    private Vec3 shelter;

    public SeekShelterGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.getRandom().nextInt(40) != 0 || !WeatherConfig.SPEC.isLoaded() || !WeatherConfig.ANIMALS_SEEK_SHELTER.get()
                || mob.isVehicle() || mob.isLeashed() || !mob.level().canSeeSky(mob.blockPosition())) {
            return false;
        }
        LocalWeather local = WeatherManager.local(mob.level(), mob.blockPosition());
        if (local == null || local.intensity() < 0.4F || !rough(local)) {
            return false;
        }
        shelter = findShelter();
        return shelter != null;
    }

    private static boolean rough(LocalWeather local) {
        WeatherType type = local.type();
        return type.vanillaThunder || type.isSevere() || type == WeatherType.HAIL || type == WeatherType.HEAVY_RAIN || type == WeatherType.SLEET
                || type == WeatherType.COLD_SNAP; // out of the bitter cold
    }

    @Nullable
    private Vec3 findShelter() {
        BlockPos origin = mob.blockPosition();
        for (int i = 0; i < 16; i++) {
            BlockPos candidate = origin.offset(mob.getRandom().nextInt(24) - 12, mob.getRandom().nextInt(6) - 3, mob.getRandom().nextInt(24) - 12);
            if (!mob.level().canSeeSky(candidate) && mob.level().getBlockState(candidate).isAir()
                    && mob.level().getBlockState(candidate.below()).isSolidRender(mob.level(), candidate.below())) {
                return Vec3.atBottomCenterOf(candidate);
            }
        }
        return null;
    }

    @Override
    public boolean canContinueToUse() {
        return !mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        if (shelter != null) {
            mob.getNavigation().moveTo(shelter.x, shelter.y, shelter.z, 1.25);
        }
    }
}
