package com.reiko.weather.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.reiko.weather.volcano.Volcano;
import com.reiko.weather.volcano.VolcanoManager;
import com.reiko.weather.volcano.VolcanoShape;
import com.reiko.weather.weather.WeatherReport;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** /rweather volcano [create [pos] | wake | erupt | calm] */
final class VolcanoCommands {
    private static final SimpleCommandExceptionType OVERWORLD_ONLY =
            new SimpleCommandExceptionType(Component.translatable("commands.reikos_weather.overworld_only"));
    private static final SimpleCommandExceptionType NONE_NEAR =
            new SimpleCommandExceptionType(Component.translatable("commands.reikos_weather.volcano.none"));
    private static final SimpleCommandExceptionType NO_ROOM =
            new SimpleCommandExceptionType(Component.translatable("commands.reikos_weather.volcano.no_room"));

    private static ServerLevel overworld(CommandSourceStack source) throws CommandSyntaxException {
        if (source.getLevel().dimension() != Level.OVERWORLD) {
            throw OVERWORLD_ONLY.create();
        }
        return source.getLevel();
    }

    /** Lists the known volcanoes within 2 km. */
    static int report(CommandSourceStack source) throws CommandSyntaxException {
        ServerLevel level = overworld(source);
        Vec3 pos = source.getPosition();
        List<Volcano> list = VolcanoManager.near(level, pos.x, pos.z, 2048);
        if (list.isEmpty()) {
            throw NONE_NEAR.create();
        }
        for (Volcano v : list) {
            VolcanoShape s = v.shape;
            int distance = (int) s.horizontalDistance(pos.x, pos.z);
            Component direction = Component.translatable("direction.reikos_weather." + WeatherReport.compass(s.cx() - pos.x, s.cz() - pos.z));
            Component phase = Component.translatable("volcano.reikos_weather.phase." + v.phase.id()).withStyle(ChatFormatting.GOLD);
            source.sendSuccess(() -> Component.translatable("commands.reikos_weather.volcano.entry", s.cx(), s.cz(), s.peakY(), distance, direction, phase,
                    WeatherReport.duration(v.timer).withStyle(ChatFormatting.GRAY)), false);
        }
        return list.size();
    }

    static int create(CommandSourceStack source, Vec3 at) throws CommandSyntaxException {
        ServerLevel level = overworld(source);
        Volcano v = VolcanoManager.create(level, (int) Math.floor(at.x), (int) Math.floor(at.z), level.random);
        if (v == null) {
            throw NO_ROOM.create();
        }
        VolcanoShape s = v.shape;
        source.sendSuccess(() -> Component.translatable("commands.reikos_weather.volcano.created", s.cx(), s.cz(), s.peakY(), s.radius()), true);
        return 1;
    }

    /** Wakes up, sets off or calms down the nearest volcano. */
    static int phase(CommandSourceStack source, Volcano.Phase phase) throws CommandSyntaxException {
        ServerLevel level = overworld(source);
        Vec3 pos = source.getPosition();
        Volcano v = VolcanoManager.nearest(level, pos.x, pos.z, 2048);
        if (v == null) {
            throw NONE_NEAR.create();
        }
        VolcanoManager.setPhase(level, VolcanoManager.data(level), v, phase);
        source.sendSuccess(() -> Component.translatable("commands.reikos_weather.volcano.phase", v.shape.cx(), v.shape.cz(),
                Component.translatable("volcano.reikos_weather.phase." + phase.id())), true);
        return phase.ordinal();
    }

    /** Sets off a lahar or a pyroclastic flow down a random flank of the nearest volcano. */
    static int flow(CommandSourceStack source, com.reiko.weather.volcano.MassFlows.Kind kind) throws CommandSyntaxException {
        ServerLevel level = overworld(source);
        Vec3 pos = source.getPosition();
        Volcano v = VolcanoManager.nearest(level, pos.x, pos.z, 2048);
        if (v == null) {
            throw NONE_NEAR.create();
        }
        VolcanoShape s = v.shape;
        float a = level.random.nextFloat() * net.minecraft.util.Mth.TWO_PI;
        double r = kind == com.reiko.weather.volcano.MassFlows.Kind.LAHAR ? s.radius() * 0.4 : s.craterRadius() + 3;
        com.reiko.weather.volcano.MassFlows.start(level, kind, s.cx() + Math.cos(a) * r, s.cz() + Math.sin(a) * r, Math.cos(a), Math.sin(a));
        // which flank it comes down (the way it's heading, as a compass point)
        Component flank = Component.translatable("direction.reikos_weather." + com.reiko.weather.weather.Wind.compassFrom(a + net.minecraft.util.Mth.PI));
        source.sendSuccess(() -> Component.translatable("commands.reikos_weather.volcano." + (kind == com.reiko.weather.volcano.MassFlows.Kind.LAHAR ? "lahar" : "pyroclastic"),
                s.cx(), s.cz(), flank), true);
        return 1;
    }

    private VolcanoCommands() {}
}
