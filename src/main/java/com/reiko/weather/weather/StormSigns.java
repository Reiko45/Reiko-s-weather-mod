package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Signs of a storm on its way: livestock bunch together and turn their backs to the wind, and the swallows (drawn by
 * the client) skim low over the fields, after the insects the falling pressure and damp air have brought down.
 */
public final class StormSigns {
    /** How close a storm is to arriving at (x, z), 0 to 1: a storm cell coming up, or a stormy spell due shortly. */
    public static float approach(List<StormCell> cells, double x, double z, WeatherType now, WeatherType next, int remaining) {
        float best = 0.0F;
        for (StormCell cell : cells) {
            if (!stormy(cell.type)) continue;
            double d = cell.distanceTo(x, z);
            // on its way, not yet overhead
            if (d > cell.radius * 0.8 && d < cell.radius + 400.0) {
                best = Math.max(best, (float) (1.0 - Math.max(0.0, d - cell.radius) / 400.0) * cell.strength());
            }
        }
        if (next != null && stormy(next) && !stormy(now) && !now.vanillaRain) {
            best = Math.max(best, Mth.clamp(1.0F - remaining / 3600.0F, 0.0F, 1.0F));
        }
        return best;
    }

    public static boolean stormy(WeatherType type) {
        return type != null && (type.vanillaThunder || type == WeatherType.HAIL || type == WeatherType.SUPERCELL
                || type == WeatherType.DERECHO || type == WeatherType.HURRICANE);
    }

    static void tickPlayer(ServerLevel level, ServerPlayer player, WeatherState state, LocalWeather local) {
        if (!WeatherConfig.STORM_SIGNS.get() || player.tickCount % 40 != 0 || local.profile().rain > 0.1F) return;
        WeatherType next = state.forecast.isEmpty() ? null : state.forecast.get(0).type();
        if (approach(state.cells, player.getX(), player.getZ(), state.type, next, state.remaining) < 0.35F) return;
        // each kind of beast bunches up with its own kind
        List<Animal> beasts = level.getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(32.0),
                a -> (a instanceof Cow || a instanceof Sheep || a instanceof Pig || a instanceof Goat || a instanceof AbstractHorse)
                        && !a.isLeashed() && !a.isVehicle() && !a.isBaby());
        Map<EntityType<?>, List<Animal>> herds = new HashMap<>();
        for (Animal a : beasts) herds.computeIfAbsent(a.getType(), t -> new java.util.ArrayList<>()).add(a);
        for (List<Animal> herd : herds.values()) {
            if (herd.size() < 3) continue;
            Vec3 middle = Vec3.ZERO;
            for (Animal a : herd) middle = middle.add(a.position());
            middle = middle.scale(1.0 / herd.size());
            for (Animal a : herd) {
                if (a.position().distanceToSqr(middle) < 3.5 * 3.5 || !a.getNavigation().isDone()) continue;
                a.getNavigation().moveTo(middle.x + level.random.nextGaussian() * 1.5, middle.y, middle.z + level.random.nextGaussian() * 1.5, 1.0);
            }
        }
    }

    private StormSigns() {}
}
