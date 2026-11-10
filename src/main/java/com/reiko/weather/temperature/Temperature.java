package com.reiko.weather.temperature;

import com.reiko.weather.block.TentBlock;
import com.reiko.weather.farming.Greenhouse;
import com.reiko.weather.registry.ModEffects;
import com.reiko.weather.registry.ModItems;
import com.reiko.weather.weather.LocalWeather;
import com.reiko.weather.weather.Seasons;
import com.reiko.weather.weather.SkyPhenomena;
import com.reiko.weather.weather.WeatherProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Air and body temperature in °C. Shared by the server (effects) and the client (thermometer readout),
 * so both always agree.
 */
public final class Temperature {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public enum Feel {
        FREEZING(ChatFormatting.DARK_AQUA), COLD(ChatFormatting.AQUA), CHILLY(ChatFormatting.BLUE), COMFORTABLE(ChatFormatting.GREEN),
        WARM(ChatFormatting.YELLOW), HOT(ChatFormatting.GOLD), SCORCHING(ChatFormatting.RED);

        public final ChatFormatting color;

        Feel(ChatFormatting color) {
            this.color = color;
        }

        public static Feel of(float celsius) {
            if (celsius < -10) return FREEZING;
            if (celsius < 0) return COLD;
            if (celsius < 10) return CHILLY;
            if (celsius <= 26) return COMFORTABLE;
            if (celsius <= 33) return WARM;
            if (celsius <= 42) return HOT;
            return SCORCHING;
        }

        public MutableComponent displayName() {
            return Component.translatable("temperature.reikos_weather." + name().toLowerCase(Locale.ROOT)).withStyle(color);
        }
    }

    /** Air temperature at a position: biome, season, height, time of day, weather and shelter. */
    public static float ambient(Level level, BlockPos pos, @Nullable LocalWeather local) {
        if (level.dimension() == Level.NETHER) {
            return 36.0F;
        }
        if (level.dimension() == Level.END) {
            return 2.0F;
        }
        float biomeTemp = level.getBiome(pos).value().getBaseTemperature() + Seasons.temperatureShiftForCurrentThread();
        int aboveSea = pos.getY() - (level.getSeaLevel() + 20);
        if (aboveSea > 0) {
            biomeTemp -= aboveSea * 0.0025F; // high ground is colder
        }
        // snowy plains (0.0) about 2 °C, plains (0.8) about 15 °C, desert (2.0) about 34 °C
        float celsius = 2.0F + biomeTemp * 16.0F;
        if (!level.dimensionType().hasSkyLight()) {
            return celsius;
        }
        boolean sky = level.canSeeSky(pos);
        // cooler air behind a cold front, milder behind a warm one
        celsius += com.reiko.weather.weather.Fronts.temperatureOffset(com.reiko.weather.weather.Fronts.cells(level), pos.getX(), pos.getZ());
        float sun = SkyPhenomena.sunHeight(level, 1.0F);
        float daily = sun > 0 ? 4.0F * sun : 6.0F * sun; // +4 at noon, -6 at midnight
        celsius += sky ? daily : daily * 0.4F;
        if (local != null) {
            WeatherProfile p = local.profile();
            float i = Math.max(0.3F, local.intensity());
            celsius -= p.rain * 3.0F * i;
            celsius -= p.overcast * 1.5F * i;
            celsius -= p.freeze * 12.0F * i;
            celsius += p.tempOffset * i; // a cold snap, a föhn wind, cold air pooled in a frost hollow
            if (p.heat) celsius += 8.0F * i;
            if (sky) {
                celsius -= p.wind * 7.0F * i; // wind chill out in the open
                if (sun > 0 && p.rain < 0.2F && p.overcast < 0.4F) celsius += 2.0F; // direct sunshine
            }
        }
        // cold water welling up offshore chills the coast
        float upwelling = com.reiko.weather.weather.Seas.upwelling(level, pos);
        if (upwelling > 0.0F) celsius -= 5.0F * upwelling;
        if (!sky) {
            celsius = Mth.lerp(0.4F, celsius, 16.0F); // buildings and caves even things out
        } else if (Greenhouse.underGlass(level, pos)) {
            celsius = Mth.lerp(0.5F, celsius, sun > 0 ? 24.0F : 14.0F); // glass keeps the warmth in
        }
        return celsius;
    }

    /** Warmth from nearby fires, lava, lit campfires and furnaces, magma, torches. */
    public static float heatSources(Level level, BlockPos center) {
        float heat = 0.0F;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-3, -2, -3), center.offset(3, 2, 3))) {
            float h = heatOf(level.getBlockState(p));
            if (h > 0) {
                heat += h / (1.0F + (float) Math.sqrt(p.distSqr(center)) * 0.6F);
            }
        }
        return Math.min(heat, 15.0F);
    }

    private static float heatOf(BlockState state) {
        if (state.is(Blocks.LAVA)) return 14.0F;
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) return 10.0F;
        if (state.getBlock() instanceof CampfireBlock) return state.getValue(CampfireBlock.LIT) ? 10.0F : 0.0F;
        if (state.getBlock() instanceof AbstractFurnaceBlock) return state.getValue(AbstractFurnaceBlock.LIT) ? 6.0F : 0.0F;
        if (state.is(Blocks.MAGMA_BLOCK)) return 5.0F;
        if (state.is(com.reiko.weather.registry.ModBlocks.VOLCANIC_VENT.get())) return 6.0F;
        if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.LANTERN)) return 2.0F;
        return 0.0F;
    }

    /** Degrees of warmth clothing adds in the cold. */
    public static float insulation(LivingEntity entity) {
        float insulation = 0.0F;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.is(ModItems.WINTER_COAT.get())) insulation += 14.0F;
            else if (stack.is(ModItems.SUN_HAT.get())) insulation += 0.0F;
            else if (stack.is(ItemTags.FREEZE_IMMUNE_WEARABLES)) insulation += 3.0F;
            else if (!stack.isEmpty()) insulation += 1.0F;
        }
        return insulation;
    }

    /** What it feels like to this entity, in °C. */
    public static float felt(Level level, LivingEntity entity, @Nullable LocalWeather local) {
        BlockPos eyes = BlockPos.containing(entity.getEyePosition());
        float celsius = ambient(level, eyes, local) + heatSources(level, entity.blockPosition());
        if (TentBlock.isInside(entity)) {
            celsius = Mth.lerp(0.6F, celsius, 18.0F); // out of the wind, wrapped up in a bedroll
        }
        // a wide straw brim keeps the sun off
        if (com.reiko.weather.compat.Compat.wearing(entity, EquipmentSlot.HEAD, ModItems.SUN_HAT.get()) && celsius > 22.0F && level.isDay() && level.canSeeSky(eyes)) {
            celsius -= Math.min(6.0F, celsius - 22.0F);
        }
        if (entity.hasEffect(ModEffects.WARMTH)) celsius += 10.0F;
        if (entity.hasEffect(ModEffects.REFRESHED)) celsius -= 10.0F;
        float insulation = insulation(entity);
        // clothing keeps you warm in the cold, and makes you hotter in the heat
        celsius += celsius < 18.0F ? Math.min(insulation, 18.0F - celsius) : insulation * 0.5F;
        if (entity.isInWater()) {
            celsius = Mth.lerp(0.6F, celsius, 12.0F);
        } else if (celsius > 24.0F && com.reiko.weather.config.WeatherConfig.HUMIDITY.get()) {
            // sticky, humid heat feels hotter: sweat can't cool you down
            celsius += humidity(level, eyes, local) * (celsius - 24.0F) * 0.6F;
        }
        if (entity.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            celsius = Math.min(celsius, 30.0F);
        }
        return celsius;
    }

    /**
     * How humid the air is, 0 to 1: the biome's own dampness, more after rain and by water, and drier in hot, windy
     * or desert air.
     */
    public static float humidity(Level level, BlockPos pos, @Nullable LocalWeather local) {
        var biome = level.getBiome(pos);
        float h = biome.value().getModifiedClimateSettings().downfall();
        if (biome.is(net.minecraft.tags.BiomeTags.IS_JUNGLE) || biome.is(net.minecraft.world.level.biome.Biomes.SWAMP)
                || biome.is(net.minecraft.world.level.biome.Biomes.MANGROVE_SWAMP)) {
            h = Math.max(h, 0.9F);
        }
        if (local != null) {
            WeatherProfile p = local.profile();
            h += p.rain * 0.4F * local.intensity() + p.fog * 0.2F;
            h -= p.wind * 0.2F * local.intensity();
            if (p.heat) h -= 0.15F;
        }
        // open water close by
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            if (level.getFluidState(pos.below(2).relative(d, 3)).is(net.minecraft.tags.FluidTags.WATER)) {
                h += 0.1F;
                break;
            }
        }
        return Mth.clamp(h, 0.0F, 1.0F);
    }

    public static String format(float celsius, boolean fahrenheit) {
        return fahrenheit ? Math.round(celsius * 9.0F / 5.0F + 32.0F) + "°F" : Math.round(celsius) + "°C";
    }

    private Temperature() {}
}
