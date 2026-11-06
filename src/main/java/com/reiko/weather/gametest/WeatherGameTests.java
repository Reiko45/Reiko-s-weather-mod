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

    @GameTest(template = PLATFORM)
    public static void derechoCellsAreLongBands(GameTestHelper helper) {
        // moving east (+x): the band stretches north-south
        StormCell cell = new StormCell(1, WeatherType.DERECHO, 0, 0, 100, 0.4, 0.0, 10000);
        cell.age = 5000;
        helper.assertTrue(cell.weightAt(0, 300) > 0.0F, "a derecho reaches far across its path");
        helper.assertTrue(cell.weightAt(150, 0) == 0.0F, "but it is narrow along it");
        helper.assertTrue(WeatherType.DERECHO.isStorm() && WeatherType.DERECHO.isSevere(), "derechos are severe storm cells");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void bloodMoonsOnlyOnLaterFullMoons(GameTestHelper helper) {
        int found = 0;
        for (long day = 0; day < 400; day++) {
            boolean blood = com.reiko.weather.weather.SkyPhenomena.isBloodMoonNight(day * 24000L + 18000L);
            if (blood) {
                found++;
                helper.assertTrue(day % 8 == 0 && day >= 8, "blood moon on day " + day + " isn't a full moon after the first week");
            }
        }
        helper.assertTrue(found > 5 && found < 40, "some full moons are blood moons (" + found + ")");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void tidesPeakAtNoonAndMidnight(GameTestHelper helper) {
        helper.assertTrue(com.reiko.weather.weather.Tides.cycle(6000L) > 0.99F, "high tide at noon");
        helper.assertTrue(com.reiko.weather.weather.Tides.cycle(18000L) > 0.99F, "high tide at midnight");
        helper.assertTrue(com.reiko.weather.weather.Tides.cycle(0L) < -0.99F, "low tide at sunrise");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void thinIceNeedsWaterUnderIt(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, Blocks.WATER);
        helper.setBlock(1, 2, 1, Blocks.ICE);
        helper.setBlock(3, 2, 3, Blocks.ICE);
        BlockPos overWater = helper.absolutePos(new BlockPos(1, 2, 1)), overStone = helper.absolutePos(new BlockPos(3, 2, 3));
        helper.assertTrue(com.reiko.weather.weather.ThinIce.isThin(helper.getLevel(), overWater, false), "ice over water is thin");
        helper.assertFalse(com.reiko.weather.weather.ThinIce.isThin(helper.getLevel(), overWater, true), "unless it's bitterly cold");
        helper.assertFalse(com.reiko.weather.weather.ThinIce.isThin(helper.getLevel(), overStone, false), "ice on stone is solid");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void leafPilesSoftenFalls(GameTestHelper helper) {
        BlockState pile = ModBlocks.LEAF_PILE.get().defaultBlockState();
        helper.assertTrue(com.reiko.weather.block.LeafPileBlock.cushion(pile) > 0.19F, "one layer softens a little");
        helper.assertTrue(com.reiko.weather.block.LeafPileBlock.cushion(pile.setValue(com.reiko.weather.block.LeafPileBlock.LAYERS, 4)) > 0.79F,
                "a deep pile takes off most of the damage");
        helper.setBlock(2, 2, 2, pile);
        helper.assertTrue(com.reiko.weather.block.LeafPileBlock.addLayer(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)),
                ModBlocks.LEAF_PILE.get(), 4), "leaves pile up");
        helper.assertBlockProperty(new BlockPos(2, 2, 2), com.reiko.weather.block.LeafPileBlock.LAYERS, 2);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void foodSpoilsInTheHeatButNotOnIce(GameTestHelper helper) {
        java.util.List<ItemStack> stacks = java.util.List.of(new ItemStack(net.minecraft.world.item.Items.BEEF, 64), new ItemStack(net.minecraft.world.item.Items.BREAD, 64));
        int spoiled = 0;
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(1);
        for (int i = 0; i < 40; i++) {
            spoiled += com.reiko.weather.farming.Spoilage.spoil(stacks, random, 40.0F, rotten -> {});
        }
        helper.assertTrue(spoiled > 0 && stacks.get(0).getCount() == 64 - spoiled, "raw beef goes off in the heat");
        helper.assertTrue(stacks.get(1).getCount() == 64, "bread doesn't");
        helper.setBlock(2, 2, 2, Blocks.CHEST);
        helper.assertFalse(com.reiko.weather.farming.Spoilage.iceNextTo(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2))), "no ice yet");
        helper.setBlock(3, 2, 2, Blocks.PACKED_ICE);
        helper.assertTrue(com.reiko.weather.farming.Spoilage.iceNextTo(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2))), "an ice house");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void lightningChargesTools(GameTestHelper helper) {
        ItemStack pick = new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
        long now = helper.getLevel().getGameTime();
        helper.assertFalse(com.reiko.weather.item.ChargedTools.isCharged(pick, helper.getLevel()), "not charged yet");
        com.reiko.weather.item.ChargedTools.charge(pick, now);
        helper.assertTrue(com.reiko.weather.item.ChargedTools.isCharged(pick, helper.getLevel()), "charged");
        helper.assertTrue(com.reiko.weather.item.ChargedTools.remaining(pick, helper.getLevel()) > 5900, "for about five minutes");
        com.reiko.weather.item.ChargedTools.charge(pick, now - 7000);
        helper.assertFalse(com.reiko.weather.item.ChargedTools.isCharged(pick, helper.getLevel()), "and then it runs out");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void iceboxHoldsAChestfulOfFood(GameTestHelper helper) {
        helper.setBlock(2, 2, 2, ModBlocks.ICEBOX.get());
        var box = helper.getBlockEntity(new BlockPos(2, 2, 2));
        helper.assertTrue(box instanceof com.reiko.weather.block.IceboxBlockEntity icebox && icebox.getContainerSize() == 27, "27 slots");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void volcanoesAreConesWithACrater(GameTestHelper helper) {
        var shape = new com.reiko.weather.volcano.VolcanoShape(0, 0, 64, 50, 90, 9, 14, 12345L);
        int rim = Integer.MIN_VALUE;
        for (int r = 12; r <= 14; r++) rim = Math.max(rim, shape.surfaceY(r, 0));
        helper.assertTrue(shape.surfaceY(0, 0) == shape.craterFloorY(), "the crater floor is at the middle");
        helper.assertTrue(shape.lavaY() < shape.peakY() - 5, "the lava lake sits well below the rim");
        helper.assertTrue(rim > shape.peakY() - 6, "the rim is near the peak (got " + rim + ")");
        helper.assertTrue(shape.surfaceY(80, 0) < shape.baseY() + 10, "the foot is near the base");
        helper.assertTrue(shape.surfaceY(91, 0) == com.reiko.weather.volcano.VolcanoShape.OUTSIDE, "nothing past the radius");
        // the flanks fall away steadily
        int last = shape.peakY() + 10;
        for (int r = 20; r <= 88; r += 4) {
            int y = shape.surfaceY(0, r);
            helper.assertTrue(y <= last + 3, "slopes go downhill (r=" + r + ")");
            last = y;
        }
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void seismographReadsVolcanicTremors(GameTestHelper helper) {
        helper.assertTrue(com.reiko.weather.volcano.VolcanoManager.signalFor(com.reiko.weather.volcano.Volcano.Phase.DORMANT, 0.5F, 10) == 0, "quiet when dormant");
        int early = com.reiko.weather.volcano.VolcanoManager.signalFor(com.reiko.weather.volcano.Volcano.Phase.RUMBLING, 0.0F, 100);
        int late = com.reiko.weather.volcano.VolcanoManager.signalFor(com.reiko.weather.volcano.Volcano.Phase.RUMBLING, 0.9F, 100);
        helper.assertTrue(early >= 1 && late > early && late < 15, "rumbling builds up (" + early + " -> " + late + ")");
        helper.assertTrue(com.reiko.weather.volcano.VolcanoManager.signalFor(com.reiko.weather.volcano.Volcano.Phase.ERUPTING, 0.5F, 200) == 15, "eruptions max it out");
        helper.assertTrue(com.reiko.weather.volcano.VolcanoManager.signalFor(com.reiko.weather.volcano.Volcano.Phase.ERUPTING, 0.5F, 2000) == 0, "too far to feel");
        helper.assertTrue(com.reiko.weather.volcano.Volcano.activity(com.reiko.weather.volcano.Volcano.Phase.ERUPTING, 0.4F) > 0.9F, "full fury mid-eruption");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void volcanicAshPilesUpInLayers(GameTestHelper helper) {
        helper.setBlock(2, 1, 2, net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(2, 2, 2, ModBlocks.VOLCANIC_ASH.get());
        helper.assertBlockProperty(new BlockPos(2, 2, 2), com.reiko.weather.block.VolcanicAshBlock.LAYERS, 1);
        helper.setBlock(3, 1, 2, net.minecraft.world.level.block.Blocks.FARMLAND);
        var ash = ModBlocks.VOLCANIC_ASH.get().defaultBlockState();
        helper.assertFalse(ash.canSurvive(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 2))), "ash can't settle on farmland (or the crops in it)");
        helper.succeed();
    }
}
