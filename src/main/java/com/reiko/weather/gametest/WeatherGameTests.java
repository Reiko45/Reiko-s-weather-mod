package com.reiko.weather.gametest;

import com.google.gson.JsonParser;
import com.reiko.weather.ReikosWeather;
import com.reiko.weather.block.LightningBatteryBlock;
import com.reiko.weather.block.StormProbeBlock;
import com.reiko.weather.block.TentBlock;
import com.reiko.weather.farming.Frost;
import com.reiko.weather.farming.Greenhouse;
import com.reiko.weather.item.SaltItem;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.registry.ModItems;
import com.reiko.weather.temperature.Temperature;
import com.reiko.weather.weather.Climate;
import com.reiko.weather.weather.CustomWeather;
import com.reiko.weather.weather.Precip;
import com.reiko.weather.weather.StormCell;
import com.reiko.weather.weather.WeatherHazards;
import com.reiko.weather.weather.WeatherType;
import com.reiko.weather.weather.Wind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the mod's building blocks. Run them with {@code ./gradlew runGameTestServer}.
 * They all use a small stone platform ({@code data/reikos_weather/structure/platform.nbt}). Note that the
 * helper's y = 0 is the layer under the structure, so the floor is at y = 1.
 */
@GameTestHolder(ReikosWeather.MOD_ID)
@PrefixGameTestTemplate(false)
public class WeatherGameTests {
    private static final String PLATFORM = "platform";

    @GameTest(template = PLATFORM)
    public static void campfireWarmsTheAir(GameTestHelper helper) {
        helper.setBlock(1, 2, 1, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        float near = Temperature.heatSources(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)));
        helper.setBlock(1, 2, 1, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        float out = Temperature.heatSources(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)));
        helper.assertTrue(near > 4.0F, "a lit campfire should warm the air (got " + near + ")");
        helper.assertTrue(out == 0.0F, "an unlit campfire shouldn't (got " + out + ")");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void clothesInsulate(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(Temperature.insulation(player) == 0.0F, "no clothes, no insulation");
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.WINTER_COAT.get()));
        helper.assertTrue(Temperature.insulation(player) >= 14.0F, "a winter coat keeps you warm");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void greenhouseNeedsAGlassRoof(GameTestHelper helper) {
        BlockPos crop = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.setBlock(2, 1, 2, Blocks.FARMLAND);
        helper.setBlock(2, 2, 2, Blocks.WHEAT);
        helper.assertFalse(Greenhouse.underGlass(helper.getLevel(), crop), "open sky is not a greenhouse");
        helper.setBlock(2, 5, 2, Blocks.GLASS);
        helper.assertTrue(Greenhouse.underGlass(helper.getLevel(), crop), "a glass roof makes a greenhouse");
        helper.setBlock(2, 5, 2, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
        helper.assertTrue(Greenhouse.underGlass(helper.getLevel(), crop), "glass panes count too");
        helper.setBlock(2, 4, 2, Blocks.STONE);
        helper.assertFalse(Greenhouse.underGlass(helper.getLevel(), crop), "only the first thing overhead counts");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void frostNipsCrops(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, Blocks.FARMLAND); // replaces the stone floor
        helper.setBlock(3, 1, 1, Blocks.FARMLAND);
        CropBlock wheat = (CropBlock) Blocks.WHEAT;
        helper.setBlock(1, 2, 1, wheat.getStateForAge(0));
        helper.setBlock(3, 2, 1, wheat.getStateForAge(5));
        BlockPos seedling = helper.absolutePos(new BlockPos(1, 2, 1)), grown = helper.absolutePos(new BlockPos(3, 2, 1));
        Frost.nip(helper.getLevel(), seedling, helper.getLevel().getBlockState(seedling), wheat);
        Frost.nip(helper.getLevel(), grown, helper.getLevel().getBlockState(grown), wheat);
        helper.assertBlockPresent(Blocks.AIR, 1, 2, 1);
        helper.assertTrue(wheat.getAge(helper.getLevel().getBlockState(grown)) == 3, "frost sets crops back two stages");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void saltMeltsIce(GameTestHelper helper) {
        helper.setBlock(1, 2, 1, ModBlocks.ICE_GLAZE.get());
        helper.setBlock(2, 2, 1, Blocks.SNOW);
        helper.setBlock(3, 1, 1, Blocks.ICE);
        int melted = SaltItem.melt(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 1)));
        helper.assertTrue(melted == 3, "salt should melt the glaze, the snow and the ice (melted " + melted + ")");
        helper.assertBlockPresent(Blocks.WATER, 3, 1, 1);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void lightningBatteryStoresAndReleases(GameTestHelper helper) {
        BlockPos battery = new BlockPos(2, 2, 2);
        helper.setBlock(battery, ModBlocks.LIGHTNING_BATTERY.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        BlockPos abs = helper.absolutePos(battery);
        LightningBatteryBlock.charge(helper.getLevel(), abs, helper.getLevel().getBlockState(abs));
        helper.assertBlockProperty(battery, LightningBatteryBlock.CHARGE, 1);
        helper.setBlock(1, 2, 2, Blocks.REDSTONE_BLOCK); // input on the west side
        helper.runAfterDelay(2, () -> {
            helper.assertBlockProperty(battery, LightningBatteryBlock.CHARGE, 0);
            helper.assertBlockProperty(battery, LightningBatteryBlock.DISCHARGING, true);
            // what a block on the east side receives from the battery
            int out = helper.getLevel().getSignal(abs, Direction.WEST);
            helper.assertTrue(out == 15, "the pulse comes out of the front (got " + out + ")");
            helper.succeed();
        });
    }

    @GameTest(template = PLATFORM)
    public static void sandbagsHoldBackFloods(GameTestHelper helper) {
        helper.setBlock(1, 2, 1, ModBlocks.SANDBAG.get());
        helper.assertTrue(WeatherHazards.protectedBySandbags(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 1))), "two blocks away is protected");
        helper.assertFalse(WeatherHazards.protectedBySandbags(helper.getLevel(), helper.absolutePos(new BlockPos(5, 2, 5))), "further away is not");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void tentsShelterTheirOccupants(GameTestHelper helper) {
        BlockState tent = ModBlocks.TENT.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        helper.setBlock(2, 2, 2, tent.setValue(BedBlock.PART, BedPart.FOOT));
        helper.setBlock(2, 2, 1, tent.setValue(BedBlock.PART, BedPart.HEAD));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos inside = helper.absolutePos(new BlockPos(2, 2, 2));
        player.moveTo(inside.getX() + 0.5, inside.getY() + 0.1, inside.getZ() + 0.5);
        helper.assertTrue(TentBlock.isInside(player), "standing in a tent counts as inside");
        player.moveTo(inside.getX() + 3.5, inside.getY() + 0.1, inside.getZ() + 0.5);
        helper.assertFalse(TentBlock.isInside(player), "next to it doesn't");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void stormProbeRecordsReadings(GameTestHelper helper) {
        helper.setBlock(1, 2, 1, ModBlocks.STORM_PROBE.get());
        helper.setBlock(3, 2, 1, ModBlocks.STORM_PROBE.get());
        StormProbeBlock.record(helper.getLevel(), helper.absolutePos(new BlockPos(1, 2, 1)), 4.0);
        StormProbeBlock.record(helper.getLevel(), helper.absolutePos(new BlockPos(1, 2, 1)), 12.0);
        StormProbeBlock.record(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 1)), 12.0);
        helper.assertBlockProperty(new BlockPos(1, 2, 1), StormProbeBlock.READINGS, 2);
        helper.assertBlockProperty(new BlockPos(3, 2, 1), StormProbeBlock.READINGS, 1);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void datapackWeatherParses(GameTestHelper helper) {
        CustomWeather weather = CustomWeather.parse(ResourceLocation.fromNamespaceAndPath("test", "dust_bowl"), JsonParser.parseString(
                "{\"name\": \"Dust Bowl\", \"base\": \"windy\", \"weight\": 4, \"duration\": [60, 120], \"seasons\": [\"summer\"],"
                        + " \"profile\": {\"fog\": 0.5, \"tint\": \"#C08040\", \"tint_strength\": 0.4, \"precipitation\": {\"dust\": 0.9}},"
                        + " \"climate_profiles\": {\"cold\": {\"precipitation\": {\"snow\": 0.3}}}}").getAsJsonObject());
        helper.assertTrue(weather.base() == WeatherType.WINDY, "base");
        helper.assertTrue(weather.minDuration() == 1200 && weather.maxDuration() == 2400, "durations are in seconds");
        helper.assertTrue(Math.abs(weather.profile(Climate.TEMPERATE).fog - 0.5F) < 1.0E-4, "fog override");
        helper.assertTrue(weather.profile(Climate.TEMPERATE).tint == 0xC08040, "tint colour");
        helper.assertTrue(weather.profile(Climate.TEMPERATE).rateOf(Precip.DUST) > 0.89F, "dust");
        helper.assertTrue(weather.profile(Climate.COLD).rateOf(Precip.SNOW) > 0.29F, "per-climate override");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void stormCellsFadeAtTheirEdge(GameTestHelper helper) {
        StormCell cell = new StormCell(1, WeatherType.THUNDERSTORM, 0, 0, 200, 0.1, 0.0, 10000);
        cell.age = 5000;
        helper.assertTrue(cell.weightAt(0, 0) > 0.99F, "full strength in the middle");
        helper.assertTrue(cell.weightAt(250, 0) == 0.0F, "nothing outside");
        helper.assertTrue(cell.weightAt(150, 0) < cell.weightAt(50, 0), "weaker toward the edge");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void windCompassNamesTheSource(GameTestHelper helper) {
        helper.assertTrue("n".equals(Wind.compassFrom(Mth.HALF_PI)), "wind blowing south comes from the north");
        helper.assertTrue("e".equals(Wind.compassFrom(Mth.PI)), "wind blowing west comes from the east");
        helper.succeed();
    }
}
