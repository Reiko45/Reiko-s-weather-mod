package com.reiko.weather.client;

import com.reiko.weather.weather.SkyPhenomena;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;

/** During a geomagnetic storm compasses swing around wildly (lodestone and recovery compasses too). */
public final class Geomagnetic {
    public static void registerCompass() {
        CompassItemPropertyFunction vanilla = new CompassItemPropertyFunction((level, stack, entity) -> {
            LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
            return tracker != null ? tracker.target().orElse(null) : CompassItem.getSpawnPosition(level);
        });
        ItemProperties.register(Items.COMPASS, ResourceLocation.withDefaultNamespace("angle"), (stack, level, entity, seed) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && SkyPhenomena.geomagneticStormNow(mc.level)) {
                float t = (mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false)) * 0.05F + seed * 0.01F;
                float swing = 0.5F + 0.35F * Mth.sin(t * 1.7F) + 0.15F * Mth.sin(t * 5.3F + 1.0F);
                return Mth.positiveModulo(swing + t * 0.08F, 1.0F);
            }
            return vanilla.unclampedCall(stack, level, entity, seed);
        });
    }

    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && SkyPhenomena.geomagneticStormNow(mc.level);
    }

    private Geomagnetic() {}
}
