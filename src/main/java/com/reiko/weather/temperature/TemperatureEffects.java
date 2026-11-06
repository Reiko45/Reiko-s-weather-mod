package com.reiko.weather.temperature;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModDamageTypes;
import com.reiko.weather.weather.LocalWeather;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.network.TemperaturePayload;
import com.reiko.weather.weather.WeatherAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;

/**
 * Cold and heat for players. Cold works through vanilla's freezing (so leather armor and the winter
 * coat, which vanilla treats as freeze-proof, protect you); heat costs hunger, and extreme heat causes
 * heatstroke unless you cool off in water or drink fire resistance.
 */
public final class TemperatureEffects {
    private static final String FELT = "reikos_weather.felt";
    private static final String BODY = "reikos_weather.body";
    private static final float COMFORT_LOW = 10.0F;
    private static final float COMFORT_HIGH = 26.0F;
    public static final float HEATSTROKE = 48.0F;
    public static final float DEADLY_COLD = -12.0F;

    public static boolean enabled() {
        // with Tough As Nails installed (and allowed to), its body temperature takes over from ours
        return WeatherConfig.SPEC.isLoaded() && WeatherConfig.TEMPERATURE.get() && !com.reiko.weather.compat.Compat.toughAsNailsHandlesTemperature();
    }

    public static void tick(ServerPlayer player) {
        if (!enabled() || player.isSpectator() || player.getAbilities().invulnerable || !player.isAlive()) {
            return;
        }
        var data = player.getPersistentData();
        if (player.tickCount % 20 == 0 || !data.contains(FELT) || !data.contains(BODY)) {
            LocalWeather local = WeatherManager.local(player.level(), player.blockPosition());
            float target = Temperature.felt(player.level(), player, local);
            data.putFloat(FELT, target);
            // your body warms up and cools down gradually: quickly back toward comfort, slowly away from it
            float body = data.contains(BODY) ? data.getFloat(BODY) : target;
            boolean recovering = body < COMFORT_LOW && target > body || body > COMFORT_HIGH && target < body;
            float rate = recovering ? 2.5F : 0.6F;
            body += Mth.clamp(target - body, -rate, rate);
            data.putFloat(BODY, body);
            PacketDistributor.sendToPlayer(player, new TemperaturePayload(body, target));
        }
        float felt = data.getFloat(BODY);

        if (felt < 0.0F && player.canFreeze() && player.level().getBrightness(LightLayer.BLOCK, player.blockPosition()) < 11) {
            float rate = Mth.clamp(-felt / 25.0F, 0.05F, 1.0F);
            int required = player.getTicksRequiredToFreeze();
            // frost creeps in; it stays just short of vanilla's "fully frozen" (which hurts every 2 seconds)
            int cap = felt < DEADLY_COLD ? required - 1 : (int) (required * Mth.clamp(rate, 0.2F, 0.9F));
            int add = 2 + (player.getRandom().nextFloat() < rate * 0.4F ? 1 : 0);
            if (player.getTicksFrozen() < cap) {
                player.setTicksFrozen(Math.min(cap, player.getTicksFrozen() + add));
            }
            // deadly cold hurts more slowly than powder snow, so there's time to find shelter or a fire
            if (felt < DEADLY_COLD && player.getTicksFrozen() >= required - 1 && player.tickCount % 100 == 0) {
                player.hurt(player.damageSources().freeze(), 1.0F);
                WeatherAdvancements.award(player, "frostbite");
            }
        }
        // heat makes you hungry: about a drumstick a minute at 40 °C, two at 48 °C
        if (felt > 32.0F && player.tickCount % 20 == 0) {
            player.causeFoodExhaustion(Math.min(0.15F, 0.006F * (felt - 32.0F)));
        }
        // and in extreme heat, slow heatstroke unless you cool off in water or rain
        if (felt > HEATSTROKE && player.tickCount % 300 == 0 && !player.isInWaterOrRain()) {
            player.hurt(player.damageSources().source(ModDamageTypes.HEATSTROKE), 1.0F);
            WeatherAdvancements.award(player, "heatstroke");
        }
    }

    /** The last felt temperature computed for a player (server side). */
    public static float lastFelt(ServerPlayer player) {
        return player.getPersistentData().getFloat(FELT);
    }

    /** The player's body temperature: the felt temperature, smoothed over time. */
    public static float body(ServerPlayer player) {
        return player.getPersistentData().getFloat(BODY);
    }

    private TemperatureEffects() {}
}
