package com.reiko.weather.item;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.entity.TornadoEntity;
import com.reiko.weather.registry.ModDataComponents;
import com.reiko.weather.registry.ModItems;
import com.reiko.weather.registry.ModSounds;
import com.reiko.weather.temperature.Temperature;
import com.reiko.weather.weather.SkyPhenomena;
import com.reiko.weather.weather.StormCell;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.WeatherReport;
import com.reiko.weather.weather.WeatherState;
import com.reiko.weather.weather.WeatherType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * A weather radio in your inventory (switched on) broadcasts warnings: severe storms closing in,
 * tornadoes nearby, dangerous weather coming up in the forecast, extreme cold or heat, and tonight's
 * sky events (blood moons, meteor showers, geomagnetic storms). Each warning is only given once.
 */
public final class WeatherRadio {
    private static final String SENT = "reikos_weather.radio_sent";
    /** How long before a warning about the same thing can be repeated. */
    private static final long REPEAT_AFTER = 6000L;

    public static void tickPlayer(ServerPlayer player) {
        if (player.tickCount % 100 != 0 || !hasRadioOn(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        long day = level.getDayTime() / 24000L;
        long time = Math.floorMod(level.getDayTime(), 24000L);
        BlockPos pos = player.blockPosition();

        if (level.dimension() == Level.OVERWORLD && WeatherManager.handles(level)) {
            WeatherState state = WeatherManager.state(level);
            // storms closing in
            for (StormCell cell : state.cells) {
                if (!(cell.type.isSevere() || cell.type.vanillaThunder || cell.type == WeatherType.HAIL)) continue;
                double edge = cell.distanceTo(pos.getX(), pos.getZ()) - cell.radius;
                if (edge <= 0 || !cell.approaching(pos.getX(), pos.getZ())) continue;
                double dx = pos.getX() - cell.x, dz = pos.getZ() - cell.z, len = Math.max(1, Math.hypot(dx, dz));
                double closing = (cell.vx * dx + cell.vz * dz) / len;
                double minutes = closing > 0.01 ? edge / closing / 1200.0 : 99;
                if (minutes < 4 && cell.type == WeatherType.HURRICANE) {
                    alert(player, "storm:" + cell.id, now, Component.translatable("radio.reikos_weather.hurricane", cell.category(),
                            Component.translatable("direction.reikos_weather." + WeatherReport.compass(cell.x - pos.getX(), cell.z - pos.getZ())),
                            Math.max(1, Math.round(minutes))), true);
                } else if (minutes < 4) {
                    alert(player, "storm:" + cell.id, now, Component.translatable("radio.reikos_weather.storm", cell.type.displayName(),
                            Component.translatable("direction.reikos_weather." + WeatherReport.compass(cell.x - pos.getX(), cell.z - pos.getZ())),
                            Math.max(1, Math.round(minutes))), cell.type.isSevere());
                }
            }
            // a drought setting in
            if (com.reiko.weather.weather.Drought.severity(state) > 0.0F) {
                long started = day - (state.droughtLength - state.droughtDaysLeft);
                alert(player, "drought:" + started, now, Component.translatable("radio.reikos_weather.drought"), false);
            }
            // the weather turning nasty
            if (!state.forecast.isEmpty() && state.remaining < 1200) {
                WeatherType next = state.forecast.get(0).type();
                if (next.isSevere() || next == WeatherType.HEATWAVE || next == WeatherType.COLD_SNAP || next.vanillaThunder) {
                    alert(player, "forecast:" + next.id() + ":" + now / 24000L, now,
                            Component.translatable("radio.reikos_weather.forecast", next.displayName()), next.isSevere());
                }
            }
        }
        // volcanoes waking up
        if (level.dimension() == Level.OVERWORLD) {
            for (com.reiko.weather.volcano.Volcano v : com.reiko.weather.volcano.VolcanoManager.near(level, pos.getX(), pos.getZ(), 1024)) {
                if (v.phase != com.reiko.weather.volcano.Volcano.Phase.RUMBLING && v.phase != com.reiko.weather.volcano.Volcano.Phase.ERUPTING) continue;
                int distance = (int) v.shape.horizontalDistance(pos.getX(), pos.getZ());
                Component direction = Component.translatable("direction.reikos_weather." + WeatherReport.compass(v.shape.cx() - pos.getX(), v.shape.cz() - pos.getZ()));
                if (v.phase == com.reiko.weather.volcano.Volcano.Phase.RUMBLING) {
                    alert(player, "volcano:" + v.shape.key() + ":rumbling", now,
                            Component.translatable("radio.reikos_weather.volcano_rumbling", distance, direction), false);
                } else {
                    net.minecraft.world.phys.Vec3 wind = com.reiko.weather.weather.Wind.direction(WeatherManager.state(level).windAngle);
                    alert(player, "volcano:" + v.shape.key() + ":erupting", now, Component.translatable("radio.reikos_weather.volcano_erupting",
                            distance, direction, Component.translatable("direction.reikos_weather." + WeatherReport.compass(wind.x, wind.z))), true);
                }
            }
        }
        // tornadoes
        for (TornadoEntity tornado : level.getEntitiesOfClass(TornadoEntity.class, new AABB(pos).inflate(250, 128, 250))) {
            if (tornado.kind().small() || tornado.kind() == TornadoEntity.Kind.FIRE_WHIRL) continue;
            alert(player, "tornado:" + tornado.getUUID(), now, tornado.rating() >= 0 ? Component.translatable("radio.reikos_weather.tornado_rated",
                    tornado.rating(), (int) Math.sqrt(tornado.distanceToSqr(player)),
                    Component.translatable("direction.reikos_weather." + WeatherReport.compass(tornado.getX() - pos.getX(), tornado.getZ() - pos.getZ())))
                    : Component.translatable("radio.reikos_weather.tornado",
                    (int) Math.sqrt(tornado.distanceToSqr(player)),
                    Component.translatable("direction.reikos_weather." + WeatherReport.compass(tornado.getX() - pos.getX(), tornado.getZ() - pos.getZ()))), true);
        }
        // tonight's sky
        if (level.dimension() == Level.OVERWORLD && time > 11000 && time < 13500) {
            if (WeatherConfig.BLOOD_MOONS.get() && SkyPhenomena.isBloodMoonNight(level.getDayTime())) {
                alert(player, "blood:" + day, now, Component.translatable("radio.reikos_weather.blood_moon"), true);
            }
            if (WeatherConfig.METEORS.get() && SkyPhenomena.isMeteorShowerNight(level.getDayTime())) {
                alert(player, "meteors:" + day, now, Component.translatable("radio.reikos_weather.meteors"), false);
            }
            if (WeatherConfig.GEOMAGNETIC_STORMS.get() && SkyPhenomena.isGeomagneticStormNight(level.getDayTime())) {
                alert(player, "geomagnetic:" + day, now, Component.translatable("radio.reikos_weather.geomagnetic"), false);
            }
            com.reiko.weather.weather.SkyCalendar.Eclipse tonight = com.reiko.weather.weather.SkyCalendar.eclipseToday(level);
            if (tonight.lunar()) {
                alert(player, "eclipse:" + day, now, Component.translatable("radio.reikos_weather.lunar_eclipse",
                        Component.translatable("eclipse.reikos_weather." + tonight.id())), false);
            }
            com.reiko.weather.weather.SkyCalendar.Eclipse tomorrow = com.reiko.weather.weather.SkyCalendar.eclipsesOn()
                    ? com.reiko.weather.weather.SkyCalendar.eclipseOn(day + 1) : com.reiko.weather.weather.SkyCalendar.Eclipse.NONE;
            if (tomorrow.solar()) {
                alert(player, "eclipse:" + (day + 1), now, Component.translatable("radio.reikos_weather.eclipse_tomorrow",
                        Component.translatable("eclipse.reikos_weather." + tomorrow.id())), false);
            }
            if (com.reiko.weather.weather.SkyCalendar.cometBrightness(day) > 0.3F && com.reiko.weather.weather.SkyCalendar.cometShape(day)[0] > 0.5F) {
                alert(player, "comet:" + day, now, Component.translatable("radio.reikos_weather.comet_evening"), false);
            }
        }
        if (level.dimension() == Level.OVERWORLD && time < 5000) {
            com.reiko.weather.weather.SkyCalendar.Eclipse today = com.reiko.weather.weather.SkyCalendar.eclipseToday(level);
            if (today.solar()) {
                alert(player, "eclipse:" + day, now, Component.translatable("radio.reikos_weather.eclipse_today",
                        Component.translatable("eclipse.reikos_weather." + today.id())), true);
            }
        }
        if (level.dimension() == Level.OVERWORLD && time > 21000 && time < 23500 && com.reiko.weather.weather.SkyCalendar.cometBrightness(day) > 0.3F
                && com.reiko.weather.weather.SkyCalendar.cometShape(day)[0] < 0.5F) {
            alert(player, "comet:" + day, now, Component.translatable("radio.reikos_weather.comet_morning"), false);
        }
        // dangerous temperatures
        if (WeatherConfig.TEMPERATURE.get()) {
            float felt = Temperature.felt(level, player, WeatherManager.local(level, pos));
            if (felt < -10.0F) {
                alert(player, "cold", now, Component.translatable("radio.reikos_weather.cold"), false);
            } else if (felt > 40.0F) {
                alert(player, "heat", now, Component.translatable("radio.reikos_weather.heat"), false);
            }
        }
    }

    private static void alert(ServerPlayer player, String key, long now, Component text, boolean urgent) {
        CompoundTag sent = player.getPersistentData().getCompound(SENT);
        if (sent.contains(key) && now - sent.getLong(key) < REPEAT_AFTER) {
            return;
        }
        // forget old keys so the tag doesn't grow forever
        for (String k : java.util.List.copyOf(sent.getAllKeys())) {
            if (now - sent.getLong(k) > REPEAT_AFTER * 4) sent.remove(k);
        }
        sent.putLong(key, now);
        player.getPersistentData().put(SENT, sent);
        MutableComponent line = Component.translatable("radio.reikos_weather.prefix").withStyle(urgent ? ChatFormatting.RED : ChatFormatting.GOLD)
                .append(Component.literal(" ")).append(text.copy().withStyle(ChatFormatting.WHITE));
        player.sendSystemMessage(line);
        player.playNotifySound(ModSounds.RADIO_ALERT.get(), SoundSource.PLAYERS, urgent ? 0.9F : 0.6F, 1.0F);
    }

    public static boolean hasRadioOn(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.WEATHER_RADIO.get()) && stack.getOrDefault(ModDataComponents.RADIO_ON.get(), true)) {
                return true;
            }
        }
        return false;
    }

    private WeatherRadio() {}
}
