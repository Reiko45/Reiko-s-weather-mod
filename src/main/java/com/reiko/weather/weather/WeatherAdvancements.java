package com.reiko.weather.weather;

import com.reiko.weather.ReikosWeather;
import com.reiko.weather.entity.TornadoEntity;
import com.reiko.weather.registry.ModEntities;
import com.reiko.weather.registry.ModItems;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Awards the weather advancements. They use "impossible" criteria in JSON and are granted from code,
 * since none of them map onto vanilla triggers.
 */
public final class WeatherAdvancements {
    private static final String SEASONS_SEEN = "reikos_weather.seasons_seen";

    public static void award(ServerPlayer player, String name) {
        AdvancementHolder holder = player.server.getAdvancements().get(ReikosWeather.id("weather/" + name));
        if (holder != null && !player.getAdvancements().getOrStartProgress(holder).isDone()) {
            player.getAdvancements().award(holder, "code");
        }
    }

    /** Checked once a second for each player in the overworld. */
    static void check(ServerLevel level, WeatherState state, ServerPlayer player, LocalWeather local, Climate climate) {
        boolean exposed = level.canSeeSky(player.blockPosition().above());
        WeatherType type = local.type();
        if (exposed && local.intensity() > 0.5F) {
            if (type.vanillaThunder || type.isSevere()) award(player, "under_the_weather");
            if (type == WeatherType.BLIZZARD && climate == Climate.COLD) {
                award(player, "whiteout");
                if (player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.WINTER_COAT.get())) award(player, "dressed_for_it");
            }
            if (type == WeatherType.SANDSTORM && climate == Climate.ARID) award(player, "dust_in_the_wind");
            if (type == WeatherType.DERECHO && local.intensity() > 0.5F) award(player, "straight_line_winds");
            if (type == WeatherType.WILDFIRE_SMOKE && level.isDay()) award(player, "red_sky");
            if (type == WeatherType.COLD_FRONT) award(player, "front_line");
        }

        for (TornadoEntity tornado : level.getEntities(ModEntities.TORNADO.get(), new AABB(player.blockPosition()).inflate(64), e -> true)) {
            double dx = tornado.getX() - player.getX(), dz = tornado.getZ() - player.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            switch (tornado.kind()) {
                case TORNADO -> {
                    if (distance < 32) award(player, "storm_chaser");
                    int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ());
                    if (distance < tornado.influenceRadius() && !player.onGround() && player.getY() - ground > 10) award(player, "wizard_of_oz");
                }
                case WATERSPOUT -> award(player, "waterspout");
                case DUST_DEVIL -> {}
            }
        }

        float sun = SkyPhenomena.sunHeight(level, 1.0F);
        if (exposed && climate != Climate.ARID && state.rainbowTicks > 0 && type.isCalm() && SkyPhenomena.rainbowSunAngle(sun)) {
            award(player, "over_the_rainbow");
        }
        if (exposed && climate == Climate.COLD && sun < -0.25F && SkyPhenomena.isAuroraNight(level.getDayTime())
                && (type == WeatherType.CLEAR || type == WeatherType.WINDY)) {
            award(player, "northern_lights");
        }

        Season season = WeatherManager.seasonInfo().season();
        if (season != null) {
            CompoundTag data = player.getPersistentData();
            int seen = data.getInt(SEASONS_SEEN) | 1 << season.ordinal();
            data.putInt(SEASONS_SEEN, seen);
            if (seen == 0b1111) award(player, "four_seasons");
        }
    }

    private WeatherAdvancements() {}
}
