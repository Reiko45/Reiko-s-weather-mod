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

    @GameTest(template = PLATFORM)
    public static void coldFrontsLeaveColderAirBehind(GameTestHelper helper) {
        // a cold front at the origin moving east (+x)
        var cell = new com.reiko.weather.weather.StormCell(1, WeatherType.COLD_FRONT, 0, 0, 80, 0.3, 0.0, 20000);
        cell.age = 5000;
        var cells = java.util.List.of(cell);
        float behind = com.reiko.weather.weather.Fronts.temperatureOffset(cells, -600, 0);
        float ahead = com.reiko.weather.weather.Fronts.temperatureOffset(cells, 600, 0);
        float beyondTheEnd = com.reiko.weather.weather.Fronts.temperatureOffset(cells, -600, 2000);
        helper.assertTrue(behind < -4.0F, "colder behind the front (" + behind + ")");
        helper.assertTrue(ahead == 0.0F && beyondTheEnd == 0.0F, "no change ahead of it or past its end");
        helper.assertTrue(WeatherType.COLD_FRONT.cellOnly() && WeatherType.COLD_FRONT.isStorm() && !WeatherType.SEA_FOG.natural(),
                "fronts only come as storm cells; sea fog is never in the forecast");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void dunesHaveASteepLeeFace(GameTestHelper helper) {
        var dunes = new com.reiko.weather.landform.Dunes(0, 0, 200, 0.0F, 30, 10, 0L);
        // walk downwind along the middle of the field and find the steepest climb and the steepest drop
        float maxRise = 0.0F, maxDrop = 0.0F, last = dunes.duneHeight(-60, 0);
        for (int x = -59; x <= 60; x++) {
            float h = dunes.duneHeight(x, 0);
            maxRise = Math.max(maxRise, h - last);
            maxDrop = Math.max(maxDrop, last - h);
            last = h;
        }
        helper.assertTrue(maxDrop > maxRise * 2.0F, "the lee face is much steeper than the windward slope (" + maxRise + " up, " + maxDrop + " down)");
        helper.assertTrue(dunes.duneHeight(250, 0) == 0.0F, "no dunes outside the field");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void landformShapes(GameTestHelper helper) {
        var flat = new com.reiko.weather.landform.SaltFlat(0, 0, 64, 50, 30, 0.0F, 1L);
        helper.assertTrue(flat.ellipse(0, 0) < 0.01F && flat.ellipse(45, 0) < 1.0F && flat.ellipse(0, 40) > 1.0F, "salt flat is an ellipse");
        var glacier = new com.reiko.weather.landform.Glacier(0, 0, 0.0F, 100, 30, 8, 1L);
        helper.assertTrue(glacier.thicknessAt(0, 0) > 6.0F, "thick in the middle");
        helper.assertTrue(glacier.thicknessAt(0, 13) < glacier.thicknessAt(0, 0), "thinner toward its sides");
        helper.assertTrue(glacier.thicknessAt(0, 40) == 0.0F && glacier.thicknessAt(80, 0) == 0.0F, "no ice outside it");
        var crater = new com.reiko.weather.landform.MeteorCrater(0, 0, 70, 15, 6, false, 1L);
        helper.assertTrue(crater.profile(0) == 64.0F && crater.profile(16) > 70.0F && crater.profile(40) < 70.5F, "a bowl with a raised rim");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void geysersShootUpAndDieBack(GameTestHelper helper) {
        float start = com.reiko.weather.block.GeyserBlock.height(40), middle = com.reiko.weather.block.GeyserBlock.height(20),
                end = com.reiko.weather.block.GeyserBlock.height(0);
        helper.assertTrue(start == 0.0F && end == 0.0F && middle > 15.0F, "the jet rises, holds and falls (" + middle + ")");
        helper.assertTrue(com.reiko.weather.weather.HailGlass.breakable(net.minecraft.world.level.block.Blocks.GLASS_PANE.defaultBlockState())
                && !com.reiko.weather.weather.HailGlass.breakable(net.minecraft.world.level.block.Blocks.TINTED_GLASS.defaultBlockState()),
                "hail breaks glass panes but not tinted glass");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void hurricanesHaveACalmEye(GameTestHelper helper) {
        var cell = new StormCell(1, WeatherType.HURRICANE, 0, 0, 600, -0.15, 0.0, 20000);
        cell.age = 5000;
        cell.power = 1.0F;
        float eye = cell.weightAt(0, 0), wall = cell.weightAt(90, 0), bands = cell.weightAt(420, 0);
        helper.assertTrue(eye < 0.05F, "calm in the eye (" + eye + ")");
        helper.assertTrue(wall > 0.9F, "the worst of it in the eyewall (" + wall + ")");
        helper.assertTrue(bands > 0.3F && bands <= wall, "rain bands farther out (" + bands + ")");
        helper.assertTrue(cell.inEye(10, 10) && !cell.inEye(90, 0) && cell.category() == 5, "a category 5 has an eye");
        cell.power = 0.3F;
        helper.assertTrue(cell.weightAt(0, 0) > 0.4F && cell.eyeFraction() == 0.0F && cell.category() == 2, "a weak one has no eye");
        // the track curves round
        cell.turn = 0.001F;
        for (int i = 0; i < 1000; i++) cell.tick();
        double heading = Math.atan2(cell.vz, cell.vx);
        helper.assertTrue(Math.abs(Mth.wrapDegrees((float) Math.toDegrees(heading)) - (180.0F + 57.3F - 360.0F)) < 2.0F,
                "it turned by about a radian (" + Math.toDegrees(heading) + ")");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void newWeathers(GameTestHelper helper) {
        var snap = com.reiko.weather.weather.WeatherProfiles.get(WeatherType.COLD_SNAP, Climate.TEMPERATE);
        helper.assertTrue(snap.tempOffset < -8.0F && snap.rain == 0.0F && snap.overcast == 0.0F, "a cold snap is clear and bitter");
        helper.assertTrue(WeatherType.COLD_SNAP.natural() && !WeatherType.COLD_SNAP.cellOnly(), "cold snaps are in the forecast");
        var haze = com.reiko.weather.weather.WeatherProfiles.get(WeatherType.DUST_HAZE, Climate.TEMPERATE);
        helper.assertTrue(!WeatherType.DUST_HAZE.natural() && WeatherType.DUST_HAZE.cellOnly() && haze.dustLayers > 0, "dust haze only drifts in from sandstorms");
        var state = new com.reiko.weather.weather.WeatherState();
        state.droughtLength = state.droughtDaysLeft = 6;
        float first = com.reiko.weather.weather.Drought.severity(state);
        state.droughtDaysLeft = 3;
        float later = com.reiko.weather.weather.Drought.severity(state);
        state.droughtDaysLeft = 0;
        helper.assertTrue(first > 0.0F && first < 0.5F && later == 1.0F && com.reiko.weather.weather.Drought.severity(state) == 0.0F,
                "a drought worsens over its first days (" + first + ", " + later + ")");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void droughtsDryUpShallowPonds(GameTestHelper helper) {
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                boolean pond = x >= 1 && x <= 3 && z >= 1 && z <= 3;
                helper.setBlock(x, 1, z, pond ? Blocks.DIRT : Blocks.STONE);
                helper.setBlock(x, 2, z, pond ? Blocks.WATER : Blocks.STONE);
            }
        }
        var state = new com.reiko.weather.weather.WeatherState();
        boolean dried = com.reiko.weather.weather.Drought.dryPond(helper.getLevel(), state, helper.absolutePos(new BlockPos(2, 3, 2)));
        helper.assertTrue(dried, "the pond dried up");
        helper.assertBlockPresent(Blocks.AIR, 2, 2, 2);
        helper.assertBlockPresent(ModBlocks.CRACKED_MUD.get(), 1, 1, 3);
        helper.assertTrue(state.driedPonds.size() == 9, "every source is remembered, to refill it (" + state.driedPonds.size() + ")");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void peatSmoulders(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, ModBlocks.PEAT.get());
        helper.setBlock(3, 1, 1, ModBlocks.PEAT.get());
        helper.setBlock(3, 2, 1, Blocks.WATER);
        var level = helper.getLevel();
        helper.assertTrue(com.reiko.weather.block.PeatBlock.ignite(level, helper.absolutePos(new BlockPos(1, 1, 1))), "dry peat catches");
        helper.assertFalse(com.reiko.weather.block.PeatBlock.ignite(level, helper.absolutePos(new BlockPos(3, 1, 1))), "wet peat doesn't");
        helper.assertTrue(helper.getBlockState(new BlockPos(1, 1, 1)).getValue(com.reiko.weather.block.PeatBlock.SMOULDERING), "it's smouldering");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void landformsPlanOnTheRightGround(GameTestHelper helper) {
        var level = helper.getLevel();
        int sea = level.getSeaLevel();
        java.util.function.Function<java.util.function.IntBinaryOperator, com.reiko.weather.landform.PlanContext> at = ground ->
                new com.reiko.weather.landform.PlanContext(net.minecraft.util.RandomSource.create(7), 0, 0, ground, sea, level);
        // a cliff to the east: a waterfall goes over it, heading east
        var fall = com.reiko.weather.landform.LandformKind.WATERFALL.plan(at.apply((x, z) -> x < 10 ? sea + 40 : x < 16 ? sea + 40 - (x - 10) * 20 / 6 : sea + 20));
        helper.assertTrue(fall instanceof com.reiko.weather.landform.Waterfall w && Math.abs(w.angle()) < 0.01F && w.top() - w.foot() >= 10,
                "a waterfall over the cliff (" + fall + ")");
        helper.assertTrue(com.reiko.weather.landform.LandformKind.WATERFALL.plan(at.apply((x, z) -> sea + 40)) == null, "no waterfall on the flat");
        // atolls need open sea all round
        helper.assertTrue(com.reiko.weather.landform.LandformKind.ATOLL.plan(at.apply((x, z) -> sea - 20)) != null, "an atoll in the open sea");
        helper.assertTrue(com.reiko.weather.landform.LandformKind.ATOLL.plan(at.apply((x, z) -> x > 40 ? sea + 5 : sea - 20)) == null, "but not by land");
        // arroyos never run uphill
        var wash = com.reiko.weather.landform.LandformKind.ARROYO.plan(at.apply((x, z) -> sea + 12 + (x + z) / 20));
        helper.assertTrue(wash instanceof com.reiko.weather.landform.Arroyo, "an arroyo on the desert");
        int[] floor = ((com.reiko.weather.landform.Arroyo) wash).floor();
        for (int i = 1; i < floor.length; i++) helper.assertTrue(floor[i] <= floor[i - 1], "the bed only ever falls downstream");
        // coasts: basalt columns where the land meets the sea, and a fjord from the sea into the mountains
        helper.assertTrue(com.reiko.weather.landform.LandformKind.BASALT_COLUMNS.plan(at.apply((x, z) -> x < 2 ? sea + 6 : sea - 8)) != null, "basalt on the coast");
        helper.assertTrue(com.reiko.weather.landform.LandformKind.FJORD.plan(at.apply((x, z) -> x < 10 ? sea - 15 : sea + 60)) instanceof com.reiko.weather.landform.Fjord,
                "a fjord into the mountains");
        helper.assertTrue(com.reiko.weather.landform.LandformKind.FJORD.plan(at.apply((x, z) -> sea - 15)) == null, "no fjord without mountains");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void stormCellWeathers(GameTestHelper helper) {
        for (WeatherType t : new WeatherType[]{WeatherType.HABOOB, WeatherType.ATMOSPHERIC_RIVER, WeatherType.HEAT_BURST, WeatherType.PYROCUMULUS}) {
            helper.assertTrue(!t.natural() && t.cellOnly(), t + " only ever arrives as a storm cell");
        }
        var burst = com.reiko.weather.weather.WeatherProfiles.get(WeatherType.HEAT_BURST, Climate.ARID);
        helper.assertTrue(burst.tempOffset > 10.0F && burst.rain == 0.0F && burst.wind > 0.5F, "a heat burst is a hot, dry gale");
        var river = com.reiko.weather.weather.WeatherProfiles.get(WeatherType.ATMOSPHERIC_RIVER, Climate.TEMPERATE);
        helper.assertTrue(river.floods && river.rain > 0.8F && river.tempOffset > 0.0F, "an atmospheric river is heavy, mild, flooding rain");
        var haboob = com.reiko.weather.weather.WeatherProfiles.get(WeatherType.HABOOB, Climate.ARID);
        helper.assertTrue(haboob.fog > 0.7F && haboob.wind > 0.7F, "a haboob is a blinding, roaring wall of dust");
        helper.assertTrue(WeatherType.PYROCUMULUS.vanillaThunder, "fire clouds throw lightning");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void moreLandformsPlanOnTheRightGround(GameTestHelper helper) {
        var level = helper.getLevel();
        int sea = level.getSeaLevel();
        java.util.function.Function<java.util.function.IntBinaryOperator, com.reiko.weather.landform.PlanContext> at = ground ->
                new com.reiko.weather.landform.PlanContext(net.minecraft.util.RandomSource.create(11), 0, 0, ground, sea, level);
        // slot canyons cut deep into a high plateau, and only ever fall downstream
        var slot = com.reiko.weather.landform.LandformKind.SLOT_CANYON.plan(at.apply((x, z) -> sea + 34 - x / 16));
        helper.assertTrue(slot instanceof com.reiko.weather.landform.SlotCanyon, "a slot canyon in the plateau (" + slot + ")");
        int[] floor = ((com.reiko.weather.landform.SlotCanyon) slot).floor();
        for (int i = 1; i < floor.length; i++) helper.assertTrue(floor[i] <= floor[i - 1], "the slot's bed only ever falls downstream");
        helper.assertTrue(floor[floor.length / 2] <= sea + 34 - 10, "and it's deep");
        helper.assertTrue(com.reiko.weather.landform.LandformKind.SLOT_CANYON.plan(at.apply((x, z) -> sea + 4)) == null, "no slot canyon in low ground");
        // the coast runs north-south with the sea to the east
        var delta = com.reiko.weather.landform.LandformKind.DELTA.plan(at.apply((x, z) -> x < 10 ? sea + 2 : sea - 8));
        helper.assertTrue(delta instanceof com.reiko.weather.landform.Delta d && Math.abs(d.angle()) < 0.01F && d.cx() < 0,
                "a delta building out to sea from just inland (" + delta + ")");
        helper.assertTrue(com.reiko.weather.landform.LandformKind.DELTA.plan(at.apply((x, z) -> sea + 20)) == null, "no delta inland");
        var cliffs = com.reiko.weather.landform.LandformKind.CHALK_CLIFFS.plan(at.apply((x, z) -> x < 10 ? sea + 4 : sea - 8));
        helper.assertTrue(cliffs instanceof com.reiko.weather.landform.ChalkCliffs c && Math.abs(c.angle()) < 0.01F, "chalk cliffs facing the sea (" + cliffs + ")");
        helper.assertTrue(com.reiko.weather.landform.LandformKind.SALT_MARSH.plan(at.apply((x, z) -> x < 10 ? sea : sea - 8)) != null, "a salt marsh on the low coast");
        helper.assertTrue(com.reiko.weather.landform.LandformKind.SALT_MARSH.plan(at.apply((x, z) -> sea)) == null, "but not away from the sea");
        // a cirque bites into a mountainside, opening downhill (east)
        var cirque = com.reiko.weather.landform.LandformKind.CIRQUE.plan(at.apply((x, z) -> sea + 60 - x));
        helper.assertTrue(cirque instanceof com.reiko.weather.landform.Cirque c && Math.abs(c.angle()) < 0.01F && c.y() < sea + 60,
                "a cirque cut into the slope (" + cirque + ")");
        helper.assertTrue(com.reiko.weather.landform.LandformKind.CIRQUE.plan(at.apply((x, z) -> sea + 60)) == null, "no cirque on a plateau");
        // lakes sit below the land round them
        var maar = com.reiko.weather.landform.LandformKind.MAAR.plan(at.apply((x, z) -> sea + 10));
        helper.assertTrue(maar instanceof com.reiko.weather.landform.Maar m && m.y() < sea + 10, "a maar's lake is sunk below the land");
        var kettles = com.reiko.weather.landform.LandformKind.KETTLE_LAKES.plan(at.apply((x, z) -> sea + 8));
        helper.assertTrue(kettles instanceof com.reiko.weather.landform.KettleLakes, "kettle lakes on the till plain");
        int[] ponds = ((com.reiko.weather.landform.KettleLakes) kettles).ponds();
        for (int i = 0; i < ponds.length; i += 4) helper.assertTrue(ponds[i + 3] < sea + 8, "each kettle's water lies below its rim");
        var oxbow = com.reiko.weather.landform.LandformKind.OXBOW_LAKE.plan(at.apply((x, z) -> sea + 3));
        helper.assertTrue(oxbow instanceof com.reiko.weather.landform.OxbowLake o && o.y() == sea + 2, "an oxbow lake on the floodplain");
        helper.assertTrue(com.reiko.weather.landform.LandformKind.OXBOW_LAKE.plan(at.apply((x, z) -> sea + 3 + Math.abs(x) / 2)) == null,
                "but not on a slope");
        var karst = com.reiko.weather.landform.LandformKind.TOWER_KARST.plan(at.apply((x, z) -> sea + 5));
        helper.assertTrue(karst instanceof com.reiko.weather.landform.TowerKarst t && t.towers().length >= 25, "a cluster of karst towers");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void snowSkyAndSea(GameTestHelper helper) {
        var level = helper.getLevel();
        for (WeatherType t : new WeatherType[]{WeatherType.GRAUPEL, WeatherType.THUNDERSNOW, WeatherType.FREEZING_DRIZZLE}) {
            helper.assertTrue(t.natural() && !t.cellOnly() && t.vanillaRain, t + " comes in the forecast and falls as precipitation");
        }
        helper.assertTrue(com.reiko.weather.weather.WeatherProfiles.get(WeatherType.GRAUPEL, Climate.COLD).rateOf(com.reiko.weather.weather.Precip.GRAUPEL) > 0.5F,
                "graupel falls as snow pellets");
        helper.assertTrue(WeatherType.THUNDERSNOW.vanillaThunder && com.reiko.weather.weather.WeatherProfiles.get(WeatherType.THUNDERSNOW, Climate.COLD).snowLayers > 0,
                "thundersnow is a thundering snowstorm");
        helper.assertTrue(com.reiko.weather.weather.WeatherProfiles.get(WeatherType.FREEZING_DRIZZLE, Climate.TEMPERATE).glazeGround, "freezing drizzle glazes the ground");
        helper.assertTrue(com.reiko.weather.weather.ClimateYears.weatherMultiplier(null, WeatherType.RAIN) == 1.0F, "no world, no climate swing");
        float l = com.reiko.weather.weather.ClimateYears.leaning(level);
        helper.assertTrue(l >= -1.0F && l <= 1.0F, "a year leans somewhere between dry and wet (" + l + ")");
        float tide = com.reiko.weather.block.WhirlpoolBlock.strength(level);
        helper.assertTrue(tide >= 0.0F && tide <= 1.0F, "a whirlpool's pull is between nothing and full strength (" + tide + ")");
        // needle ice only grows out of soil, and frost flowers only on ice
        helper.setBlock(1, 1, 1, Blocks.DIRT);
        helper.setBlock(3, 1, 1, Blocks.STONE);
        helper.setBlock(5, 1, 1, Blocks.ICE);
        var needle = ModBlocks.NEEDLE_ICE.get().defaultBlockState();
        helper.assertTrue(needle.canSurvive(level, helper.absolutePos(new BlockPos(1, 2, 1))), "needle ice on soil");
        helper.assertFalse(needle.canSurvive(level, helper.absolutePos(new BlockPos(3, 2, 1))), "not on bare stone");
        helper.assertTrue(ModBlocks.FROST_FLOWERS.get().defaultBlockState().canSurvive(level, helper.absolutePos(new BlockPos(5, 2, 1))), "frost flowers on ice");
        // the snowpack lies deeper in a cold climate than a mild one
        BlockPos high = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.assertTrue(com.reiko.weather.weather.WinterGround.targetDepth(level, high, Climate.COLD)
                > com.reiko.weather.weather.WinterGround.targetDepth(level, high, Climate.TEMPERATE), "deeper snow in the cold");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void stillMoreLandformsPlan(GameTestHelper helper) {
        var level = helper.getLevel();
        int sea = level.getSeaLevel();
        java.util.function.Function<java.util.function.IntBinaryOperator, com.reiko.weather.landform.PlanContext> at = ground ->
                new com.reiko.weather.landform.PlanContext(net.minecraft.util.RandomSource.create(5), 0, 0, ground, sea, level);
        // a blue hole needs shallow sea
        helper.assertTrue(com.reiko.weather.landform.LandformKind.BLUE_HOLE.plan(at.apply((x, z) -> sea - 8)) != null, "a blue hole in the shallows");
        helper.assertTrue(com.reiko.weather.landform.LandformKind.BLUE_HOLE.plan(at.apply((x, z) -> sea + 10)) == null, "no blue hole on land");
        // a horn is carved from a summit, never from flat ground
        helper.assertTrue(com.reiko.weather.landform.LandformKind.HORN.plan(at.apply((x, z) -> sea + 110 - (int) Math.sqrt(x * x + z * z))) != null,
                "a horn on a mountain top");
        helper.assertTrue(com.reiko.weather.landform.LandformKind.HORN.plan(at.apply((x, z) -> sea + 10)) == null, "no horn on the plain");
        // a tidal whirlpool between two shores
        helper.assertTrue(com.reiko.weather.landform.LandformKind.WHIRLPOOL.plan(at.apply((x, z) -> Math.abs(x) > 12 ? sea + 5 : sea - 14)) != null,
                "a whirlpool in a strait");
        helper.assertTrue(com.reiko.weather.landform.LandformKind.WHIRLPOOL.plan(at.apply((x, z) -> sea + 20)) == null, "no whirlpool inland");
        // dry-country rocks stay off the sea
        helper.assertTrue(com.reiko.weather.landform.LandformKind.MUSHROOM_ROCKS.plan(at.apply((x, z) -> sea - 20)) == null, "no mushroom rocks at sea");
        helper.succeed();
    }
    @GameTest(template = PLATFORM)
    public static void skyCalendarAndWinter(GameTestHelper helper) {
        var level = helper.getLevel();
        helper.assertTrue(WeatherType.SUN_SHOWER.natural() && !WeatherType.SUN_SHOWER.cellOnly() && WeatherType.SUN_SHOWER.vanillaRain,
                "a sun shower is a natural, rainy weather");
        helper.assertTrue(com.reiko.weather.weather.WeatherProfiles.get(WeatherType.SUN_SHOWER, Climate.TEMPERATE).overcast < 0.2F, "and the sky stays clear");
        helper.assertTrue(WeatherType.POLAR_LOW.cellOnly() && WeatherType.POLAR_LOW.isStorm() && WeatherType.POLAR_LOW.isSevere(),
                "a polar low is a severe storm that only comes as a cell");
        helper.assertTrue(com.reiko.weather.weather.Season.SUMMER.weatherMultiplier(WeatherType.POLAR_LOW) == 0.0F, "no polar lows in summer");
        // eclipses only at new or full moon, never on a blood moon
        int eclipses = 0;
        for (long day = 0; day < 800; day++) {
            var e = com.reiko.weather.weather.SkyCalendar.eclipseOn(day);
            if (e.solar()) helper.assertTrue(day % 8 == 4, "a solar eclipse at new moon (day " + day + ")");
            if (e.lunar()) {
                helper.assertTrue(day % 8 == 0, "a lunar eclipse at full moon (day " + day + ")");
                helper.assertFalse(com.reiko.weather.weather.SkyPhenomena.isBloodMoonNight(day * 24000L), "not on a blood moon (day " + day + ")");
            }
            if (e != com.reiko.weather.weather.SkyCalendar.Eclipse.NONE) eclipses++;
            float comet = com.reiko.weather.weather.SkyCalendar.cometBrightness(day);
            helper.assertTrue(comet >= 0.0F && comet <= 1.0F, "a comet's brightness is 0-1");
        }
        helper.assertTrue(eclipses > 5, "eclipses come round now and then (" + eclipses + " in 800 days)");
        // long summer days, short winter ones
        var summer = new com.reiko.weather.weather.Seasons.Info(com.reiko.weather.weather.Season.SUMMER, 4, 8, 0.0F);
        var winter = new com.reiko.weather.weather.Seasons.Info(com.reiko.weather.weather.Season.WINTER, 4, 8, 0.0F);
        float longDay = com.reiko.weather.weather.SkyCalendar.daylightFraction(summer, 0L), shortDay = com.reiko.weather.weather.SkyCalendar.daylightFraction(winter, 0L);
        helper.assertTrue(longDay > 0.55F && longDay <= 0.61F && shortDay < 0.45F && shortDay >= 0.39F, "summer days long, winter days short ("
                + longDay + ", " + shortDay + ")");
        helper.assertTrue(com.reiko.weather.weather.SkyCalendar.daylightFraction(com.reiko.weather.weather.Seasons.Info.NONE, 0L) == 0.5F, "no seasons, even days");
        // lightning strips the bark off a log
        helper.assertTrue(com.reiko.weather.weather.LightningEvents.stripped(Blocks.OAK_LOG.defaultBlockState()).is(Blocks.STRIPPED_OAK_LOG), "a struck oak loses its bark");
        helper.assertTrue(com.reiko.weather.weather.LightningEvents.stripped(Blocks.STRIPPED_OAK_LOG.defaultBlockState()) == null, "and there's no more to lose");
        // hail lies on solid ground, in layers
        helper.setBlock(1, 1, 1, Blocks.STONE);
        helper.setBlock(1, 2, 1, ModBlocks.HAILSTONES.get());
        helper.assertTrue(helper.getBlockState(new BlockPos(1, 2, 1)).getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) == 1, "a thin layer of hail");
        // upwelling never happens inland
        helper.assertTrue(com.reiko.weather.weather.Seas.upwelling(level, helper.absolutePos(new BlockPos(1, 2, 1))) >= 0.0F, "upwelling is 0 or more");
        helper.succeed();
    }
    @GameTest(template = PLATFORM)
    public static void tenthLandformsPlan(GameTestHelper helper) {
        var level = helper.getLevel();
        int sea = level.getSeaLevel();
        java.util.function.Function<java.util.function.IntBinaryOperator, com.reiko.weather.landform.PlanContext> at = ground ->
                new com.reiko.weather.landform.PlanContext(net.minecraft.util.RandomSource.create(7), 0, 0, ground, sea, level);
        // flat, gentle country suits the meadow landforms, and they save and load as they were
        for (var kind : new com.reiko.weather.landform.LandformKind[]{com.reiko.weather.landform.LandformKind.FAIRY_RINGS,
                com.reiko.weather.landform.LandformKind.VERNAL_POOLS, com.reiko.weather.landform.LandformKind.PARABOLIC_DUNES}) {
            var planned = kind.plan(at.apply((x, z) -> sea + 8 + (int) (2 * Math.sin(x * 0.07))));
            helper.assertTrue(planned != null, kind + " on gentle ground");
            var again = kind.load(planned.save());
            helper.assertTrue(again.bounds().equals(planned.bounds()), kind + " saves and loads");
            var b = planned.bounds();
            helper.assertTrue(b.getXSpan() < 230 && b.getZSpan() < 230, kind + " stays within its reach");
        }
        // dry-country and mountain landforms never form out at sea
        for (var kind : new com.reiko.weather.landform.LandformKind[]{com.reiko.weather.landform.LandformKind.GYPSUM_DUNES,
                com.reiko.weather.landform.LandformKind.SALT_DOME, com.reiko.weather.landform.LandformKind.CANYON,
                com.reiko.weather.landform.LandformKind.CALDERA, com.reiko.weather.landform.LandformKind.POLJE,
                com.reiko.weather.landform.LandformKind.FAIRY_RINGS}) {
            helper.assertTrue(kind.plan(at.apply((x, z) -> sea - 20)) == null, "no " + kind + " at sea");
        }
        // and the shore landforms need a shore
        for (var kind : new com.reiko.weather.landform.LandformKind[]{com.reiko.weather.landform.LandformKind.COLOURED_BEACH,
                com.reiko.weather.landform.LandformKind.MOERAKI_BOULDERS, com.reiko.weather.landform.LandformKind.STROMATOLITES}) {
            helper.assertTrue(kind.plan(at.apply((x, z) -> sea + 25)) == null, "no " + kind + " far inland");
        }
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void eleventhLandformsPlan(GameTestHelper helper) {
        var level = helper.getLevel();
        int sea = level.getSeaLevel();
        // (some only take on a share of the spots they're offered, so give each a few tries on suitable ground)
        java.util.function.BiFunction<com.reiko.weather.landform.LandformKind, java.util.function.IntBinaryOperator, com.reiko.weather.landform.Landform> plan = (kind, ground) -> {
            for (int seed = 1; seed <= 60; seed++) {
                var planned = kind.plan(new com.reiko.weather.landform.PlanContext(net.minecraft.util.RandomSource.create(seed), 0, 0, ground, sea, level));
                if (planned != null) return planned;
            }
            return null;
        };
        java.util.function.IntBinaryOperator gentle = (x, z) -> sea + 8 + (int) (2 * Math.sin(x * 0.07));
        // gentle lowland suits the lava fields, dikes, mounds, loess and steaming ground, and the deep-sea floor suits
        // vents and brine pools; each saves and loads as it was, and stays within reach
        java.util.Map<com.reiko.weather.landform.LandformKind, java.util.function.IntBinaryOperator> suits = new java.util.LinkedHashMap<>();
        for (var kind : new com.reiko.weather.landform.LandformKind[]{com.reiko.weather.landform.LandformKind.KIPUKA,
                com.reiko.weather.landform.LandformKind.VOLCANIC_DIKES, com.reiko.weather.landform.LandformKind.MIMA_MOUNDS,
                com.reiko.weather.landform.LandformKind.LOESS_CLIFFS, com.reiko.weather.landform.LandformKind.STEAMING_GROUND}) {
            suits.put(kind, gentle);
        }
        suits.put(com.reiko.weather.landform.LandformKind.HYDROTHERMAL_VENTS, (x, z) -> sea - 30);
        suits.put(com.reiko.weather.landform.LandformKind.BRINE_POOL, (x, z) -> sea - 30);
        suits.put(com.reiko.weather.landform.LandformKind.KRUMMHOLZ, (x, z) -> sea + 60);
        for (var e : suits.entrySet()) {
            var planned = plan.apply(e.getKey(), e.getValue());
            helper.assertTrue(planned != null, e.getKey() + " on suitable ground");
            var again = e.getKey().load(planned.save());
            helper.assertTrue(again.bounds().equals(planned.bounds()), e.getKey() + " saves and loads");
            var b = planned.bounds();
            helper.assertTrue(b.getXSpan() < 240 && b.getZSpan() < 240, e.getKey() + " stays within its reach");
        }
        // the dry-land ones never form out at sea
        for (var kind : new com.reiko.weather.landform.LandformKind[]{com.reiko.weather.landform.LandformKind.KIPUKA,
                com.reiko.weather.landform.LandformKind.VOLCANIC_DIKES, com.reiko.weather.landform.LandformKind.KRUMMHOLZ,
                com.reiko.weather.landform.LandformKind.LOESS_CLIFFS, com.reiko.weather.landform.LandformKind.MIMA_MOUNDS,
                com.reiko.weather.landform.LandformKind.STEAMING_GROUND, com.reiko.weather.landform.LandformKind.GOOSENECKS,
                com.reiko.weather.landform.LandformKind.THERMOKARST_SLUMP}) {
            helper.assertTrue(plan.apply(kind, (x, z) -> sea - 20) == null, "no " + kind + " at sea");
        }
        // the sea-floor, shore and river ones never form far inland
        for (var kind : new com.reiko.weather.landform.LandformKind[]{com.reiko.weather.landform.LandformKind.HYDROTHERMAL_VENTS,
                com.reiko.weather.landform.LandformKind.BRINE_POOL, com.reiko.weather.landform.LandformKind.TAFONI,
                com.reiko.weather.landform.LandformKind.FRINGING_REEF, com.reiko.weather.landform.LandformKind.FLOODPLAIN}) {
            helper.assertTrue(plan.apply(kind, (x, z) -> sea + 25) == null, "no " + kind + " far inland");
        }
        // and the treeline is high up
        helper.assertTrue(plan.apply(com.reiko.weather.landform.LandformKind.KRUMMHOLZ, gentle) == null, "no krummholz in the lowlands");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void twelfthLandformsPlan(GameTestHelper helper) {
        var level = helper.getLevel();
        int sea = level.getSeaLevel();
        java.util.function.BiFunction<com.reiko.weather.landform.LandformKind, java.util.function.IntBinaryOperator, com.reiko.weather.landform.Landform> plan = (kind, ground) -> {
            for (int seed = 1; seed <= 60; seed++) {
                var planned = kind.plan(new com.reiko.weather.landform.PlanContext(net.minecraft.util.RandomSource.create(seed), 0, 0, ground, sea, level));
                if (planned != null) return planned;
            }
            return null;
        };
        java.util.function.IntBinaryOperator gentle = (x, z) -> sea + 8 + (int) (2 * Math.sin(x * 0.07));
        java.util.function.IntBinaryOperator slope = (x, z) -> sea + 40 - (int) Math.floor(x * 0.4);
        java.util.Map<com.reiko.weather.landform.LandformKind, java.util.function.IntBinaryOperator> suits = new java.util.LinkedHashMap<>();
        for (var kind : new com.reiko.weather.landform.LandformKind[]{com.reiko.weather.landform.LandformKind.TEPUI,
                com.reiko.weather.landform.LandformKind.RIFT_FISSURE, com.reiko.weather.landform.LandformKind.HOGBACKS,
                com.reiko.weather.landform.LandformKind.INVERTED_CHANNEL}) {
            suits.put(kind, gentle);
        }
        suits.put(com.reiko.weather.landform.LandformKind.ICE_TOWERS, (x, z) -> sea + 60);
        suits.put(com.reiko.weather.landform.LandformKind.SOLIFLUCTION_LOBES, slope);
        for (var e : suits.entrySet()) {
            var planned = plan.apply(e.getKey(), e.getValue());
            helper.assertTrue(planned != null, e.getKey() + " on suitable ground");
            var again = e.getKey().load(planned.save());
            helper.assertTrue(again.bounds().equals(planned.bounds()), e.getKey() + " saves and loads");
            var b = planned.bounds();
            helper.assertTrue(b.getXSpan() < 256 && b.getZSpan() < 256, e.getKey() + " stays within its reach");
        }
        // none of them form out at sea
        for (var kind : new com.reiko.weather.landform.LandformKind[]{com.reiko.weather.landform.LandformKind.TEPUI,
                com.reiko.weather.landform.LandformKind.RIFT_FISSURE, com.reiko.weather.landform.LandformKind.HOGBACKS,
                com.reiko.weather.landform.LandformKind.INVERTED_CHANNEL, com.reiko.weather.landform.LandformKind.ICE_TOWERS,
                com.reiko.weather.landform.LandformKind.SOLIFLUCTION_LOBES, com.reiko.weather.landform.LandformKind.FOLDED_CLIFFS,
                com.reiko.weather.landform.LandformKind.FAIRY_POOLS, com.reiko.weather.landform.LandformKind.HANGING_GARDEN,
                com.reiko.weather.landform.LandformKind.ANCIENT_SHORELINES}) {
            helper.assertTrue(plan.apply(kind, (x, z) -> sea - 20) == null, "no " + kind + " at sea");
        }
        // cliffs, valleys, slopes and basins need relief: none on dead-flat land
        for (var kind : new com.reiko.weather.landform.LandformKind[]{com.reiko.weather.landform.LandformKind.FOLDED_CLIFFS,
                com.reiko.weather.landform.LandformKind.HANGING_GARDEN, com.reiko.weather.landform.LandformKind.LANDSLIDE_LAKE,
                com.reiko.weather.landform.LandformKind.FAIRY_POOLS, com.reiko.weather.landform.LandformKind.SOLIFLUCTION_LOBES,
                com.reiko.weather.landform.LandformKind.ANCIENT_SHORELINES}) {
            helper.assertTrue(plan.apply(kind, (x, z) -> sea + 25) == null, "no " + kind + " on flat land");
        }
        // and fumarole ice towers are high up
        helper.assertTrue(plan.apply(com.reiko.weather.landform.LandformKind.ICE_TOWERS, gentle) == null, "no ice towers in the lowlands");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void landformsKeepApart(GameTestHelper helper) {
        // a big lava field, a smaller headland overlapping it, and a little cenote overlapping only the headland
        var big = new com.reiko.weather.landform.LandformSpacing.Claim(com.reiko.weather.landform.LandformKind.KIPUKA,
                new net.minecraft.world.level.ChunkPos(0, 0), new net.minecraft.world.level.levelgen.structure.BoundingBox(-100, 50, -100, 100, 120, 100));
        var middle = new com.reiko.weather.landform.LandformSpacing.Claim(com.reiko.weather.landform.LandformKind.TAFONI,
                new net.minecraft.world.level.ChunkPos(8, 0), new net.minecraft.world.level.levelgen.structure.BoundingBox(60, 50, -40, 160, 120, 40));
        var small = new com.reiko.weather.landform.LandformSpacing.Claim(com.reiko.weather.landform.LandformKind.CENOTE,
                new net.minecraft.world.level.ChunkPos(12, 0), new net.minecraft.world.level.levelgen.structure.BoundingBox(150, 50, -15, 180, 120, 15));
        var apart = new com.reiko.weather.landform.LandformSpacing.Claim(com.reiko.weather.landform.LandformKind.MIMA_MOUNDS,
                new net.minecraft.world.level.ChunkPos(0, 20), new net.minecraft.world.level.levelgen.structure.BoundingBox(-30, 50, 290, 30, 120, 350));
        var all = java.util.List.of(big, middle, small, apart);
        java.util.function.Function<com.reiko.weather.landform.LandformSpacing.Claim, java.util.List<com.reiko.weather.landform.LandformSpacing.Claim>> rivals =
                c -> all.stream().filter(o -> o != c && o.clashes(c)).toList();
        helper.assertTrue(big.beats(middle) && middle.beats(small), "the bigger landform wins");
        helper.assertTrue(big.clashes(middle) && middle.clashes(small) && !big.clashes(small), "overlaps are found (and only those)");
        helper.assertFalse(com.reiko.weather.landform.LandformSpacing.yields(big, rivals), "the biggest is always built");
        helper.assertTrue(com.reiko.weather.landform.LandformSpacing.yields(middle, rivals), "one overlapping a bigger one gives way");
        // the headland isn't built, so the cenote it would have crowded out stands after all
        helper.assertFalse(com.reiko.weather.landform.LandformSpacing.yields(small, rivals), "nothing is crowded out by a landform that isn't built");
        helper.assertFalse(com.reiko.weather.landform.LandformSpacing.yields(apart, rivals), "one with room to itself is built");
        // two the same size: settled the same way whichever asks
        var twin = new com.reiko.weather.landform.LandformSpacing.Claim(com.reiko.weather.landform.LandformKind.TAFONI,
                new net.minecraft.world.level.ChunkPos(3, 3), new net.minecraft.world.level.levelgen.structure.BoundingBox(-100, 50, -100, 100, 120, 100));
        helper.assertTrue(big.beats(twin) != twin.beats(big), "a tie still has exactly one winner");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void icyWatersAndAftermath(GameTestHelper helper) {
        var level = helper.getLevel();
        var random = net.minecraft.util.RandomSource.create(5);
        // slushy frazil ice melts back to water in mild air (the test platform's plains are well above freezing)
        helper.setBlock(1, 1, 1, ModBlocks.FRAZIL_ICE.get());
        BlockPos slush = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.assertTrue(level.getFluidState(slush).isSource(), "frazil ice is still water underneath");
        level.getBlockState(slush).randomTick(level, slush, random);
        helper.assertBlockPresent(Blocks.WATER, new BlockPos(1, 1, 1));
        // a brinicle hanging from ice melts away to water too
        helper.setBlock(3, 3, 1, Blocks.ICE);
        helper.setBlock(3, 2, 1, ModBlocks.BRINICLE.get());
        BlockPos tube = helper.absolutePos(new BlockPos(3, 2, 1));
        level.getBlockState(tube).randomTick(level, tube, random);
        helper.assertBlockPresent(Blocks.WATER, new BlockPos(3, 2, 1));
        // charred soil greens over (outside winter)
        if (com.reiko.weather.weather.WeatherManager.seasonInfo().season() != com.reiko.weather.weather.Season.WINTER) {
            helper.setBlock(5, 1, 1, ModBlocks.CHARRED_SOIL.get());
            BlockPos burnt = helper.absolutePos(new BlockPos(5, 1, 1));
            for (int i = 0; i < 400 && level.getBlockState(burnt).is(ModBlocks.CHARRED_SOIL.get()); i++) {
                level.getBlockState(burnt).randomTick(level, burnt, random);
            }
            helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(5, 1, 1));
        }
        // wrack needs ground under it
        helper.assertFalse(ModBlocks.BEACH_WRACK.get().defaultBlockState().canSurvive(level, helper.absolutePos(new BlockPos(1, 5, 4))), "no wrack in mid-air");
        // a dry lakebed is what floods into a playa lake
        helper.assertTrue(com.reiko.weather.weather.StormWake.lakebed(ModBlocks.CRACKED_MUD.get().defaultBlockState()), "cracked mud is a lakebed");
        helper.assertFalse(com.reiko.weather.weather.StormWake.lakebed(Blocks.GRASS_BLOCK.defaultBlockState()), "grass isn't a lakebed");
        // polynyas lie in the same places every time, and there are some in any stretch of sea
        boolean found = false;
        for (int x = 0; x < 512 && !found; x += 4) {
            for (int z = 0; z < 512 && !found; z += 4) {
                found = com.reiko.weather.weather.IcyWaters.polynya(x, z);
            }
        }
        helper.assertTrue(found, "polynyas are scattered through the sea");
        // fireweed goes in a flower pot
        helper.assertTrue(((net.minecraft.world.level.block.FlowerPotBlock) Blocks.FLOWER_POT).getFullPotsView().containsKey(ModBlocks.FIREWEED.getId()),
                "fireweed can be potted");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void thirteenthLandformsPlan(GameTestHelper helper) {
        var level = helper.getLevel();
        int s = level.getSeaLevel();
        // (plans try a spread of spots, as the structure sets would)
        java.util.function.BiFunction<com.reiko.weather.landform.LandformKind, java.util.function.IntBinaryOperator, com.reiko.weather.landform.Landform> plan = (kind, ground) -> {
            for (int i = 0; i < 80; i++) {
                int x = Math.floorMod(i * 37, 400) - 200, z = Math.floorMod(i * 53, 400) - 200;
                var planned = kind.plan(new com.reiko.weather.landform.PlanContext(net.minecraft.util.RandomSource.create(i + 1), x, z, ground, s, level));
                if (planned != null) return planned;
            }
            return null;
        };
        java.util.Map<com.reiko.weather.landform.LandformKind, java.util.function.IntBinaryOperator> suits = new java.util.LinkedHashMap<>();
        suits.put(com.reiko.weather.landform.LandformKind.CLOUD_FOREST, (x, z) -> s + 7 + (int) Math.round(55 * (0.5 + 0.5 * Math.sin(x * 0.021)) * (0.5 + 0.5 * Math.cos(z * 0.018))
                + 5 * Math.sin(x * 0.09 + z * 0.07) + 2 * Math.cos(z * 0.13 - x * 0.05)));
        suits.put(com.reiko.weather.landform.LandformKind.BLUE_SPRING, (x, z) -> {
            double e = x - 18 * Math.sin(z * 0.025);
            return s + 7 + (int) Math.round(16 / (1 + Math.exp(e * 0.5)) - 0.08 * Math.max(-40, Math.min(60, e)) + 1.5 * Math.sin(x * 0.07 + z * 0.05));
        });
        suits.put(com.reiko.weather.landform.LandformKind.SEA_CLIFF_FALLS, (x, z) -> {
            double e = x - 30 - 14 * Math.sin(z * 0.03);
            return s - 9 + (int) Math.round(26 / (1 + Math.exp(e * 0.6)) + 2 * Math.sin(x * 0.06 + z * 0.08) * (e < 0 ? 1 : 0.3) - 0.03 * Math.min(0, e));
        });
        suits.put(com.reiko.weather.landform.LandformKind.COAL_SEAM_FIRE, (x, z) -> (int) Math.round(s + 33 - 0.28 * x + 3 * Math.sin(z * 0.05) + 1.5 * Math.sin((x + z) * 0.09)));
        suits.put(com.reiko.weather.landform.LandformKind.DRY_FALLS, (x, z) -> {
            double xc = Math.floorMod(x + 100, 200) - 100;
            return s + 13 + (int) Math.round(26 / (1 + Math.exp((Math.abs(xc) - 50 - 8 * Math.sin(z * 0.03)) * 0.35)) + 1.5 * Math.sin(x * 0.07 + z * 0.05));
        });
        suits.put(com.reiko.weather.landform.LandformKind.FROST_SHATTERED_SUMMIT, (x, z) -> s + 57 + (int) Math.round(6 * Math.sin(x * 0.03) * Math.cos(z * 0.035) + 2 * Math.sin((x + z) * 0.09)));
        suits.put(com.reiko.weather.landform.LandformKind.SKERRIES, (x, z) -> {
            double q = Math.floorMod(x + (int) Math.round(25 * Math.sin(z * 0.021)), 240) - 120, u = Math.abs(q) - 60;
            double h = u < 0 ? s + 1 + Math.min(14, -u * 0.3) : s - 1 - (1 + u * 0.2 + Math.max(0, u - 40) * 0.6);
            return (int) Math.round(h + 1.5 * Math.sin(x * 0.11 + z * 0.07) + 1.2 * Math.cos(z * 0.13 - x * 0.05));
        });
        suits.put(com.reiko.weather.landform.LandformKind.DUNE_SLACKS, (x, z) -> {
            double q = Math.floorMod(x + (int) Math.round(18 * Math.sin(z * 0.017)), 240) - 120, u = Math.abs(q) - 60;
            double h = u < 0 ? s + Math.min(6, -u * 0.09) : s - 1 - (0.5 + u * 0.25);
            return (int) Math.round(h + 0.8 * Math.sin(x * 0.09 + z * 0.05) + 0.6 * Math.cos(z * 0.11 - x * 0.07));
        });
        for (var e : suits.entrySet()) {
            var planned = plan.apply(e.getKey(), e.getValue());
            helper.assertTrue(planned != null, e.getKey() + " on suitable ground");
            var again = e.getKey().load(planned.save());
            helper.assertTrue(again.bounds().equals(planned.bounds()), e.getKey() + " saves and loads");
            var b = planned.bounds();
            helper.assertTrue(b.getXSpan() < 256 && b.getZSpan() < 256, e.getKey() + " stays within its reach");
        }
        // none of them where they don't belong: out at sea, or on flat land
        for (var kind : suits.keySet()) {
            helper.assertTrue(plan.apply(kind, (x, z) -> s - 20) == null, "no " + kind + " out at sea");
            helper.assertTrue(plan.apply(kind, (x, z) -> s + 25) == null, "no " + kind + " on flat land");
        }
        // glaciers now carry glacier tables, and keep them through a save
        var glacier = plan.apply(com.reiko.weather.landform.LandformKind.GLACIER, (x, z) -> s + 37 + (int) Math.round(-0.12 * x + 4 * Math.sin(z * 0.04) + 2 * Math.sin((x + z) * 0.07)));
        helper.assertTrue(glacier instanceof com.reiko.weather.landform.Glacier g && g.tables(), "glaciers have tables");
        helper.assertTrue(com.reiko.weather.landform.LandformKind.GLACIER.load(glacier.save()) instanceof com.reiko.weather.landform.Glacier g2 && g2.tables(), "tables are saved");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void fourteenthLandformsPlan(GameTestHelper helper) {
        var level = helper.getLevel();
        int s = level.getSeaLevel();
        java.util.function.BiFunction<com.reiko.weather.landform.LandformKind, java.util.function.IntBinaryOperator, com.reiko.weather.landform.Landform> plan = (kind, ground) -> {
            for (int i = 0; i < 80; i++) {
                int x = Math.floorMod(i * 37, 400) - 200, z = Math.floorMod(i * 53, 400) - 200;
                var planned = kind.plan(new com.reiko.weather.landform.PlanContext(net.minecraft.util.RandomSource.create(i + 1), x, z, ground, s, level));
                if (planned != null) return planned;
            }
            return null;
        };
        // a frozen coast with deep water off it, the deep ocean floor, and a shelf sloping away offshore
        java.util.function.IntBinaryOperator frozenCoast = (x, z) -> {
            double d = Math.abs(Math.floorMod(x, 200) - 100) + 6 * Math.sin(z * 0.03);
            return (int) Math.round(d > 55 ? Math.min(s + 9, s + (d - 55) * 0.4) : Math.max(s - 35, s - (55 - d) * 0.8)) + (int) Math.round(1.5 * Math.sin(z * 0.06) + Math.cos(x * 0.11 + z * 0.07));
        };
        java.util.function.IntBinaryOperator deepOcean = (x, z) -> s - 36 + (int) Math.round(3 * Math.sin(x * 0.05) * Math.cos(z * 0.04) + 1.5 * Math.sin((x + z) * 0.09));
        java.util.function.IntBinaryOperator shelf = (x, z) -> (int) Math.round(Math.min(s + 7, s - 7 - 0.15 * (x + 120)) + 1.5 * Math.sin(x * 0.07 + z * 0.05) + Math.cos(z * 0.13));
        java.util.function.IntBinaryOperator dryLand = (x, z) -> s + 9 + (int) Math.round(3 * Math.sin(x * 0.04) * Math.sin(z * 0.05));
        java.util.function.IntBinaryOperator lagoon = (x, z) -> s - 6 + (int) Math.round(1.2 * Math.sin(x * 0.06) * Math.cos(z * 0.05));
        java.util.function.IntBinaryOperator hills = (x, z) -> s + 15 + (int) Math.round(5 * Math.sin(x * 0.03 + 1) * Math.cos(z * 0.027) + 2 * Math.sin((x - z) * 0.09));
        java.util.function.IntBinaryOperator seaFloor = (x, z) -> s - 15 + (int) Math.round(2 * Math.sin(x * 0.05) * Math.cos(z * 0.04));
        java.util.function.IntBinaryOperator lowland = (x, z) -> s + 1 + (int) Math.round(1.2 * Math.sin(x * 0.05) * Math.cos(z * 0.04) + 0.8 * Math.sin((x + z) * 0.1));
        java.util.function.IntBinaryOperator mountainside = (x, z) -> s + 37 + (int) Math.round(0.9 * Math.abs(Math.floorMod(x, 160) - 80) + 3 * Math.sin(z * 0.05) + 1.5 * Math.sin((x + z) * 0.09));
        java.util.function.IntBinaryOperator jungleSlope = (x, z) -> (int) Math.round(s + 19 - 0.14 * x + 2 * Math.sin(z * 0.05) * Math.cos(x * 0.04) + 1.5 * Math.sin((x + z) * 0.09));
        // each kind: the ground it forms on, then ground it never forms on
        record Fit(com.reiko.weather.landform.LandformKind kind, java.util.function.IntBinaryOperator good, java.util.List<java.util.function.IntBinaryOperator> bad) {}
        var fits = java.util.List.of(
                new Fit(com.reiko.weather.landform.LandformKind.ICE_SHELF, frozenCoast, java.util.List.of(dryLand, lagoon)),
                new Fit(com.reiko.weather.landform.LandformKind.SEAMOUNT, deepOcean, java.util.List.of(dryLand, lagoon)),
                new Fit(com.reiko.weather.landform.LandformKind.SUBMARINE_CANYON, shelf, java.util.List.of(dryLand, lagoon)),
                new Fit(com.reiko.weather.landform.LandformKind.CRYSTAL_CAVE, hills, java.util.List.of(seaFloor, lowland)),
                new Fit(com.reiko.weather.landform.LandformKind.GAS_CRATER, dryLand, java.util.List.of(seaFloor, mountainside)),
                new Fit(com.reiko.weather.landform.LandformKind.LAVA_DOME, hills, java.util.List.of(seaFloor, mountainside)),
                // (a stream can find its way down gently rolling ground, but not across a dead-flat plain)
                new Fit(com.reiko.weather.landform.LandformKind.BOILING_RIVER, jungleSlope, java.util.List.of(seaFloor,
                        (x, z) -> s + 9 + (int) Math.round(0.8 * Math.sin(x * 0.07) * Math.cos(z * 0.05)))));
        for (Fit f : fits) {
            var planned = plan.apply(f.kind(), f.good());
            helper.assertTrue(planned != null, f.kind() + " on suitable ground");
            var again = f.kind().load(planned.save());
            helper.assertTrue(again.bounds().equals(planned.bounds()), f.kind() + " saves and loads");
            var b = planned.bounds();
            helper.assertTrue(b.getXSpan() < 256 && b.getZSpan() < 256, f.kind() + " stays within its reach");
            for (int i = 0; i < f.bad().size(); i++) {
                helper.assertTrue(plan.apply(f.kind(), f.bad().get(i)) == null, "no " + f.kind() + " on unsuitable ground #" + i);
            }
        }
        // glaciers now carry meltwater lakes, and waterfalls caves behind the falling water (both kept through a save)
        boolean lakes = false;
        for (int k = 0; k < 6 && !lakes; k++) {
            int shift = k * 41;
            var glacier = plan.apply(com.reiko.weather.landform.LandformKind.GLACIER, (x, z) -> s + 37 + (int) Math.round(-0.12 * (x + shift) + 4 * Math.sin(z * 0.04) + 2 * Math.sin((x + z) * 0.07)));
            if (glacier instanceof com.reiko.weather.landform.Glacier g && g.lakes()) {
                lakes = com.reiko.weather.landform.LandformKind.GLACIER.load(glacier.save()) instanceof com.reiko.weather.landform.Glacier g2 && g2.lakes()
                        && java.util.Arrays.equals(g.meltwater(), g2.meltwater());
            }
        }
        helper.assertTrue(lakes, "glaciers have meltwater lakes, and keep them through a save");
        boolean cave = false;
        for (int k = 0; k < 6 && !cave; k++) {
            int shift = k * 37;
            var falls = plan.apply(com.reiko.weather.landform.LandformKind.WATERFALL, (x, z) -> {
                double xc = Math.floorMod(x + shift + 100, 200) - 100;
                return s + 13 + (int) Math.round(26 / (1 + Math.exp((Math.abs(xc) - 50 - 8 * Math.sin(z * 0.03)) * 0.35)) + 1.5 * Math.sin(x * 0.07 + z * 0.05));
            });
            cave = falls instanceof com.reiko.weather.landform.Waterfall w && w.cave()
                    && com.reiko.weather.landform.LandformKind.WATERFALL.load(falls.save()) instanceof com.reiko.weather.landform.Waterfall w2 && w2.cave();
        }
        helper.assertTrue(cave, "waterfalls have caves behind them, and keep them through a save");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void fifteenthLandformsPlan(GameTestHelper helper) {
        var level = helper.getLevel();
        int s = level.getSeaLevel();
        java.util.function.BiFunction<com.reiko.weather.landform.LandformKind, java.util.function.IntBinaryOperator, com.reiko.weather.landform.Landform> plan = (kind, ground) -> {
            for (int i = 0; i < 80; i++) {
                int x = Math.floorMod(i * 37, 400) - 200, z = Math.floorMod(i * 53, 400) - 200;
                var planned = kind.plan(new com.reiko.weather.landform.PlanContext(net.minecraft.util.RandomSource.create(i + 1), x, z, ground, s, level));
                if (planned != null) return planned;
            }
            return null;
        };
        // limestone hills, a badlands slope, flat northern ground and gentle cold grassland; and where none of them belong
        java.util.function.IntBinaryOperator karst = (x, z) -> s + 19 + (int) Math.round(13 * Math.sin(x * 0.045 + 0.3) * Math.cos(z * 0.04) + 3 * Math.sin(x * 0.12 + z * 0.09) + 1.5 * Math.cos(z * 0.23));
        java.util.function.IntBinaryOperator slope = (x, z) -> (int) Math.round(s + 27 - 0.18 * x + 3 * Math.sin(z * 0.05) + 1.5 * Math.sin((x + z) * 0.09));
        java.util.function.IntBinaryOperator flat = (x, z) -> s + 5 + (int) Math.round(1.5 * Math.sin(x * 0.05) * Math.cos(z * 0.04) + Math.sin((x + z) * 0.1));
        java.util.function.IntBinaryOperator gentle = (x, z) -> s + 7 + (int) Math.round(3 * Math.sin(x * 0.05) * Math.cos(z * 0.043) + 2 * Math.sin((x + z) * 0.11));
        java.util.function.IntBinaryOperator seaFloor = (x, z) -> s - 15 + (int) Math.round(2 * Math.sin(x * 0.05) * Math.cos(z * 0.04));
        java.util.function.IntBinaryOperator mountainside = (x, z) -> s + 37 + (int) Math.round(0.9 * Math.abs(Math.floorMod(x, 160) - 80) + 3 * Math.sin(z * 0.05) + 1.5 * Math.sin((x + z) * 0.09));
        record Fit(com.reiko.weather.landform.LandformKind kind, java.util.function.IntBinaryOperator good, java.util.List<java.util.function.IntBinaryOperator> bad) {}
        var fits = java.util.List.of(
                new Fit(com.reiko.weather.landform.LandformKind.GLOWWORM_GROTTO, karst, java.util.List.of(flat, seaFloor)),
                new Fit(com.reiko.weather.landform.LandformKind.RED_RIVER, slope, java.util.List.of(flat, seaFloor)),
                new Fit(com.reiko.weather.landform.LandformKind.STRING_BOG, flat, java.util.List.of(seaFloor, mountainside)),
                new Fit(com.reiko.weather.landform.LandformKind.EARTH_HUMMOCKS, gentle, java.util.List.of(seaFloor, mountainside)));
        for (Fit f : fits) {
            var planned = plan.apply(f.kind(), f.good());
            helper.assertTrue(planned != null, f.kind() + " on suitable ground");
            var again = f.kind().load(planned.save());
            helper.assertTrue(again.bounds().equals(planned.bounds()), f.kind() + " saves and loads");
            var b = planned.bounds();
            helper.assertTrue(b.getXSpan() < 256 && b.getZSpan() < 256, f.kind() + " stays within its reach");
            for (int i = 0; i < f.bad().size(); i++) {
                helper.assertTrue(plan.apply(f.kind(), f.bad().get(i)) == null, "no " + f.kind() + " on unsuitable ground #" + i);
            }
        }
        // peat bogs now often have quaking mats, glaciers ogives and snow-bridged crevasses (all kept through a save)
        boolean quaking = false;
        for (int k = 0; k < 8 && !quaking; k++) {
            int shift = k * 29;
            var bog = plan.apply(com.reiko.weather.landform.LandformKind.PEAT_BOG, (x, z) -> s + 3 + (int) Math.round(1.5 * Math.sin((x + shift) * 0.05) * Math.cos(z * 0.04)));
            quaking = bog instanceof com.reiko.weather.landform.PeatBog p && p.quaking()
                    && com.reiko.weather.landform.LandformKind.PEAT_BOG.load(bog.save()) instanceof com.reiko.weather.landform.PeatBog p2 && p2.quaking();
        }
        helper.assertTrue(quaking, "peat bogs have quaking mats");
        boolean ogives = false, bridges = false;
        for (int k = 0; k < 8 && !(ogives && bridges); k++) {
            int shift = k * 41;
            // (a valley floor that drops in steps, so the ice breaks up into icefalls over them)
            var glacier = plan.apply(com.reiko.weather.landform.LandformKind.GLACIER, (x, z) -> s + 60 + (int) Math.round(-0.1 * x
                    - 9 * Math.floor((x + shift) / 55.0) + 3 * Math.sin(z * 0.04)));
            if (glacier instanceof com.reiko.weather.landform.Glacier g
                    && com.reiko.weather.landform.LandformKind.GLACIER.load(glacier.save()) instanceof com.reiko.weather.landform.Glacier g2) {
                ogives |= g.ogives() && g2.ogives();
                bridges |= g.bridges() && g2.bridges();
            }
        }
        helper.assertTrue(ogives, "glaciers have ogives below their icefalls");
        helper.assertTrue(bridges, "glaciers hide crevasses under snow bridges");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void hardWintersAndFaults(GameTestHelper helper) {
        var level = helper.getLevel();
        var random = net.minecraft.util.RandomSource.create(17);
        // candle ice melts away in mild air, and snow ghosts thaw back to green spruce (keeping whether they were placed)
        helper.setBlock(1, 2, 1, ModBlocks.CANDLE_ICE.get());
        BlockPos candles = helper.absolutePos(new BlockPos(1, 2, 1));
        for (int i = 0; i < 400 && level.getBlockState(candles).is(ModBlocks.CANDLE_ICE.get()); i++) level.getBlockState(candles).randomTick(level, candles, random);
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(1, 2, 1));
        var spruce = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        helper.setBlock(3, 2, 1, com.reiko.weather.block.SnowGhostLeavesBlock.plastered(ModBlocks.SNOW_GHOST_LEAVES.get(), spruce));
        BlockPos ghost = helper.absolutePos(new BlockPos(3, 2, 1));
        level.getBlockState(ghost).randomTick(level, ghost, random);
        helper.assertTrue(level.getBlockState(ghost).is(Blocks.SPRUCE_LEAVES) && level.getBlockState(ghost).getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT),
                "a snow ghost thaws back to the spruce it was");
        // a bora needs the sea downwind, mountains upwind and winter
        var gale = com.reiko.weather.weather.WeatherProfiles.get(com.reiko.weather.weather.WeatherType.WINDY, com.reiko.weather.weather.Climate.TEMPERATE);
        var coast = new com.reiko.weather.weather.Microclimate.Info(0, 0, 0.9F, 0, 0.0F, 0, 0.3F);
        var inland = new com.reiko.weather.weather.Microclimate.Info(0, 0, 0.9F, 0, Float.NaN, 0, 0.3F);
        boolean winter = com.reiko.weather.weather.WeatherManager.seasonInfo().season() == com.reiko.weather.weather.Season.WINTER;
        if (com.reiko.weather.config.WeatherConfig.BORA.get() && gale.wind > 0.4F) {
            helper.assertTrue((com.reiko.weather.weather.Microclimate.bora(level, coast, gale, 0.0F) > 0.0F) == winter, "a bora blows out to sea in winter only");
        }
        helper.assertTrue(com.reiko.weather.weather.Microclimate.bora(level, inland, gale, 0.0F) == 0.0F, "no bora away from the sea");
        helper.assertTrue(com.reiko.weather.weather.Microclimate.bora(level, coast, gale, Mth.PI) == 0.0F, "no bora blowing in off the sea");
        // a fault tears open across the rock: a crack along the line, the ground heaved up beside it
        // (the test arena has a barrier roof, which would stand in for the ground: open it over the line)
        for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 2, 2)), helper.absolutePos(new BlockPos(8, 12, 9)))) {
            if (level.getBlockState(p).is(Blocks.BARRIER)) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        }
        int changed = com.reiko.weather.weather.FaultScarps.tear(level, helper.absolutePos(new BlockPos(1, 0, 4)), 0.0F, 5, random);
        BlockPos probe = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, helper.absolutePos(new BlockPos(5, 0, 4)));
        helper.assertTrue(changed > 0, "the fault tears the ground (top " + helper.relativePos(probe) + " " + level.getBlockState(probe.below()) + ")");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void winterGroundAndMoons(GameTestHelper helper) {
        var level = helper.getLevel();
        var random = net.minecraft.util.RandomSource.create(15);
        // a block landing on a drift of hail (or any of the other snow-like layers) must not loop forever
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        var landing = new net.minecraft.world.item.context.DirectionalPlaceContext(level, at, net.minecraft.core.Direction.DOWN,
                net.minecraft.world.item.ItemStack.EMPTY, net.minecraft.core.Direction.UP);
        for (var block : java.util.List.of(ModBlocks.HAILSTONES, ModBlocks.DUSTY_SNOW, ModBlocks.PINK_SNOW, ModBlocks.SLUSH, ModBlocks.SNOW_CRUST)) {
            var deep = block.get().defaultBlockState().setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, 3);
            var thin = block.get().defaultBlockState().setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, 1);
            helper.assertFalse(deep.canBeReplaced(landing), block.getId() + ": a deep layer holds");
            helper.assertTrue(thin.canBeReplaced(landing), block.getId() + ": a thin layer is swept aside");
        }
        // every fifth full moon is a supermoon
        helper.assertTrue(com.reiko.weather.weather.Supermoons.strength(16 * 24000L + 18000L) > 0.5F, "a supermoon on the 16th night");
        helper.assertTrue(com.reiko.weather.weather.Supermoons.strength(8 * 24000L + 18000L) == 0.0F, "an ordinary full moon on the 8th");
        helper.assertTrue(com.reiko.weather.weather.Supermoons.strength(17 * 24000L + 18000L) == 0.0F, "no supermoon the night after");
        // aufeis melts away a layer at a time in mild air (the platform's plains are well above freezing)
        helper.setBlock(3, 1, 1, Blocks.STONE);
        helper.setBlock(3, 2, 1, ModBlocks.AUFEIS.get().defaultBlockState().setValue(com.reiko.weather.block.AufeisBlock.LAYERS, 3));
        BlockPos ice = helper.absolutePos(new BlockPos(3, 2, 1));
        for (int i = 0; i < 400 && level.getBlockState(ice).is(ModBlocks.AUFEIS.get()); i++) level.getBlockState(ice).randomTick(level, ice, random);
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 2, 1));
        // a snow bridge, once it starts to give way, falls in (and takes the rest of the span with it)
        helper.setBlock(5, 3, 1, ModBlocks.SNOW_BRIDGE.get());
        helper.setBlock(6, 3, 1, ModBlocks.SNOW_BRIDGE.get());
        com.reiko.weather.block.SnowBridgeBlock.giveWay(level, helper.absolutePos(new BlockPos(5, 3, 1)), 1);
        helper.runAfterDelay(20, () -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(5, 3, 1));
            helper.succeed();
        });
    }

    @GameTest(template = PLATFORM)
    public static void oldSignsAndShores(GameTestHelper helper) {
        var level = helper.getLevel();
        // thundery days are the same answer every time, about half of summer days (three in four in the tropics)
        int thundery = 0, tropical = 0;
        for (long day = 0; day < 2000; day++) {
            helper.assertTrue(com.reiko.weather.weather.Convection.thunderyDay(day, false) == com.reiko.weather.weather.Convection.thunderyDay(day, false), "the same answer twice");
            if (com.reiko.weather.weather.Convection.thunderyDay(day, false)) thundery++;
            if (com.reiko.weather.weather.Convection.thunderyDay(day, true)) tropical++;
        }
        helper.assertTrue(thundery > 850 && thundery < 1150, "about half of days are thundery (" + thundery + ")");
        helper.assertTrue(tropical > thundery, "more thundery days in the tropics");
        // a big quake leaves aftershocks to come
        if (com.reiko.weather.config.WeatherConfig.AFTERSHOCKS.get()) {
            int before = com.reiko.weather.weather.Earthquakes.pendingAftershocks();
            BlockPos far = helper.absolutePos(new BlockPos(1, 1, 1)).offset(5000, 0, 5000);
            com.reiko.weather.weather.Earthquakes.quake(level, far.getX(), far.getZ(), 0.8F);
            helper.assertTrue(com.reiko.weather.weather.Earthquakes.pendingAftershocks() > before, "aftershocks follow a big quake");
        }
        // foxfire only on a log; lichen only on a face of rock; a glacier mouse only on something firm
        helper.setBlock(1, 1, 3, Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.X));
        helper.assertTrue(ModBlocks.FOXFIRE.get().defaultBlockState().canSurvive(level, helper.absolutePos(new BlockPos(1, 2, 3))), "foxfire on a log");
        helper.assertFalse(ModBlocks.FOXFIRE.get().defaultBlockState().canSurvive(level, helper.absolutePos(new BlockPos(3, 2, 3))), "no foxfire on bare ground");
        helper.setBlock(5, 2, 3, Blocks.STONE);
        var lichen = ModBlocks.ORANGE_LICHEN.get().defaultBlockState().setValue(net.minecraft.world.level.block.MultifaceBlock.getFaceProperty(net.minecraft.core.Direction.WEST), true);
        helper.assertTrue(lichen.canSurvive(level, helper.absolutePos(new BlockPos(6, 2, 3))), "lichen on the face of a rock");
        helper.assertFalse(lichen.canSurvive(level, helper.absolutePos(new BlockPos(7, 2, 3))), "no lichen hanging in the air");
        helper.assertTrue(ModBlocks.GLACIER_MOUSE.get().defaultBlockState().canSurvive(level, helper.absolutePos(new BlockPos(5, 3, 3))), "a glacier mouse on a firm top");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void pavementsAndMice(GameTestHelper helper) {
        var level = helper.getLevel();
        int s = level.getSeaLevel();
        java.util.function.BiFunction<com.reiko.weather.landform.LandformKind, java.util.function.IntBinaryOperator, com.reiko.weather.landform.Landform> plan = (kind, ground) -> {
            for (int i = 0; i < 80; i++) {
                int x = Math.floorMod(i * 37, 400) - 200, z = Math.floorMod(i * 53, 400) - 200;
                var planned = kind.plan(new com.reiko.weather.landform.PlanContext(net.minecraft.util.RandomSource.create(i + 1), x, z, ground, s, level));
                if (planned != null) return planned;
            }
            return null;
        };
        // a desert pavement on flat, dry ground; never on a mountainside or out at sea
        var kind = com.reiko.weather.landform.LandformKind.DESERT_PAVEMENT;
        var pavement = plan.apply(kind, (x, z) -> s + 7 + (int) Math.round(3 * Math.sin(x * 0.05) * Math.cos(z * 0.043) + 2 * Math.sin((x + z) * 0.11)));
        helper.assertTrue(pavement != null, "a desert pavement on flat ground");
        helper.assertTrue(kind.load(pavement.save()).bounds().equals(pavement.bounds()), "a desert pavement saves and loads");
        helper.assertTrue(pavement.bounds().getXSpan() < 256 && pavement.bounds().getZSpan() < 256, "a desert pavement stays within its reach");
        helper.assertTrue(plan.apply(kind, (x, z) -> s - 15 + (int) Math.round(2 * Math.sin(x * 0.05) * Math.cos(z * 0.04))) == null, "no desert pavement at sea");
        helper.assertTrue(plan.apply(kind, (x, z) -> s + 37 + (int) Math.round(0.9 * Math.abs(Math.floorMod(x, 160) - 80) + 3 * Math.sin(z * 0.05))) == null,
                "no desert pavement on a mountainside");
        // most new glaciers carry glacier mice, and keep them through a save
        boolean mice = false;
        for (int k = 0; k < 8 && !mice; k++) {
            int shift = k * 41;
            var glacier = plan.apply(com.reiko.weather.landform.LandformKind.GLACIER, (x, z) -> s + 60 + (int) Math.round(-0.1 * x
                    - 9 * Math.floor((x + shift) / 55.0) + 3 * Math.sin(z * 0.04)));
            mice = glacier instanceof com.reiko.weather.landform.Glacier g && g.mice()
                    && com.reiko.weather.landform.LandformKind.GLACIER.load(glacier.save()) instanceof com.reiko.weather.landform.Glacier g2 && g2.mice();
        }
        helper.assertTrue(mice, "glaciers have glacier mice");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void mudIceAndSpells(GameTestHelper helper) {
        var level = helper.getLevel();
        var random = net.minecraft.util.RandomSource.create(17);
        // spring mud dries back to earth once it stops raining (the platform's plains are mild)
        helper.setBlock(1, 1, 1, ModBlocks.SOFT_MUD.get());
        helper.setBlock(1, 2, 1, Blocks.AIR);
        BlockPos mud = helper.absolutePos(new BlockPos(1, 1, 1));
        if (!level.isRainingAt(mud.above())) {
            for (int i = 0; i < 600 && level.getBlockState(mud).is(ModBlocks.SOFT_MUD.get()); i++) level.getBlockState(mud).randomTick(level, mud, random);
            helper.assertBlockPresent(Blocks.DIRT, new BlockPos(1, 1, 1));
        }
        // and a muddy path dries back to a path
        helper.setBlock(3, 1, 1, ModBlocks.MUDDY_PATH.get());
        BlockPos path = helper.absolutePos(new BlockPos(3, 1, 1));
        if (!level.isRainingAt(path.above())) {
            for (int i = 0; i < 600 && level.getBlockState(path).is(ModBlocks.MUDDY_PATH.get()); i++) level.getBlockState(path).randomTick(level, path, random);
            helper.assertBlockPresent(Blocks.DIRT_PATH, new BlockPos(3, 1, 1));
        }
        // ice on a puddle melts back to water in mild air
        helper.setBlock(5, 1, 1, Blocks.STONE);
        helper.setBlock(5, 2, 1, ModBlocks.FROZEN_PUDDLE.get());
        BlockPos skin = helper.absolutePos(new BlockPos(5, 2, 1));
        level.getBlockState(skin).randomTick(level, skin, random);
        helper.assertBlockPresent(ModBlocks.PUDDLE.get(), new BlockPos(5, 2, 1));
        // a gas vent relights the fire over it
        if (level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOFIRETICK)) {
            helper.setBlock(1, 1, 4, ModBlocks.GAS_VENT.get());
            helper.setBlock(1, 2, 4, Blocks.AIR);
            BlockPos vent = helper.absolutePos(new BlockPos(1, 1, 4));
            for (int i = 0; i < 60 && level.getBlockState(vent.above()).isAir(); i++) level.getBlockState(vent).randomTick(level, vent, random);
            helper.assertTrue(level.getBlockState(vent.above()).is(net.minecraft.tags.BlockTags.FIRE), "the gas vent relights its fire");
            helper.setBlock(1, 2, 4, Blocks.AIR);
        }
        // the mayflies hatch on spring evenings, not at noon or in the rain
        var spring = com.reiko.weather.weather.Season.SPRING;
        helper.assertTrue(com.reiko.weather.weather.InsectHatches.mayflies(spring, 20, 24, 12000L, 0.1F, false), "a spring-evening hatch");
        helper.assertFalse(com.reiko.weather.weather.InsectHatches.mayflies(spring, 20, 24, 6000L, 0.1F, false), "no hatch at noon");
        helper.assertFalse(com.reiko.weather.weather.InsectHatches.mayflies(spring, 20, 24, 12000L, 0.1F, true), "no hatch in the rain");
        // out-of-season spells never push the temperature far
        float offset = com.reiko.weather.weather.SeasonalSpells.temperatureOffset(level);
        helper.assertTrue(offset >= -9.0F && offset <= 9.0F, "spells shift the temperature a few degrees at most");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void thawFrostAndBloom(GameTestHelper helper) {
        var level = helper.getLevel();
        var random = net.minecraft.util.RandomSource.create(13);
        // slush melts away in mild air (the test platform's plains are well above freezing)
        helper.setBlock(1, 1, 1, ModBlocks.SLUSH.get().defaultBlockState().setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, 3));
        BlockPos slush = helper.absolutePos(new BlockPos(1, 1, 1));
        for (int i = 0; i < 200 && level.getBlockState(slush).is(ModBlocks.SLUSH.get()); i++) level.getBlockState(slush).randomTick(level, slush, random);
        helper.assertBlockNotPresent(ModBlocks.SLUSH.get(), new BlockPos(1, 1, 1));
        // and crusted snow thaws back to slush
        helper.setBlock(3, 1, 1, ModBlocks.SNOW_CRUST.get().defaultBlockState().setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, 2));
        BlockPos crust = helper.absolutePos(new BlockPos(3, 1, 1));
        for (int i = 0; i < 50 && level.getBlockState(crust).is(ModBlocks.SNOW_CRUST.get()); i++) level.getBlockState(crust).randomTick(level, crust, random);
        helper.assertBlockNotPresent(ModBlocks.SNOW_CRUST.get(), new BlockPos(3, 1, 1));
        // snow won't lie on the smouldering ground over a burning coal seam
        helper.setBlock(5, 1, 1, ModBlocks.SMOULDERING_GROUND.get());
        helper.setBlock(5, 2, 1, Blocks.SNOW);
        BlockPos hot = helper.absolutePos(new BlockPos(5, 1, 1));
        level.getBlockState(hot).randomTick(level, hot, random);
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(5, 2, 1));
        // hair ice grows only on wood
        helper.setBlock(1, 1, 4, Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.X));
        helper.assertTrue(ModBlocks.HAIR_ICE.get().defaultBlockState().canSurvive(level, helper.absolutePos(new BlockPos(1, 2, 4))), "hair ice on a log");
        helper.setBlock(3, 1, 4, Blocks.STONE);
        helper.assertFalse(ModBlocks.HAIR_ICE.get().defaultBlockState().canSurvive(level, helper.absolutePos(new BlockPos(3, 2, 4))), "no hair ice on stone");
        // desert wildflowers grow in sand, and go in flower pots
        helper.setBlock(5, 1, 4, Blocks.SAND);
        helper.assertTrue(ModBlocks.DESERT_POPPY.get().defaultBlockState().canSurvive(level, helper.absolutePos(new BlockPos(5, 2, 4))), "poppies grow in sand");
        helper.assertFalse(ModBlocks.DESERT_GOLD.get().defaultBlockState().canSurvive(level, helper.absolutePos(new BlockPos(3, 2, 4))), "not on bare stone");
        var pots = ((net.minecraft.world.level.block.FlowerPotBlock) Blocks.FLOWER_POT).getFullPotsView();
        helper.assertTrue(pots.containsKey(ModBlocks.DESERT_GOLD.getId()) && pots.containsKey(ModBlocks.SAND_VERBENA.getId()) && pots.containsKey(ModBlocks.DESERT_POPPY.getId()),
                "desert flowers can be potted");
        // bubble ice melts like lake ice
        helper.setBlock(1, 1, 6, Blocks.STONE);
        helper.setBlock(1, 2, 6, ModBlocks.BUBBLE_ICE.get());
        BlockPos bubbles = helper.absolutePos(new BlockPos(1, 2, 6));
        for (int i = 0; i < 50 && level.getBlockState(bubbles).is(ModBlocks.BUBBLE_ICE.get()); i++) level.getBlockState(bubbles).randomTick(level, bubbles, random);
        helper.assertBlockNotPresent(ModBlocks.BUBBLE_ICE.get(), new BlockPos(1, 2, 6));
        // out in the open there's no cave draught
        helper.assertFalse(com.reiko.weather.weather.LocalTouches.caveDraught(level, helper.absolutePos(new BlockPos(4, 3, 6))), "no cave draught in the open");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void fireIceAndHighlands(GameTestHelper helper) {
        var level = helper.getLevel();
        var random = net.minecraft.util.RandomSource.create(11);
        // the extra cold of height only starts high up, and never warms anything
        helper.assertTrue(com.reiko.weather.weather.Highlands.lapse(new BlockPos(0, 60, 0)) == 0.0F, "no snow-cap cold down low");
        helper.assertTrue(com.reiko.weather.weather.Highlands.lapse(new BlockPos(0, 220, 0)) >= 0.0F, "high ground is never warmer");
        // ground liquefied by an earthquake settles back into sand
        helper.setBlock(1, 1, 1, ModBlocks.QUICKSAND.get());
        BlockPos quick = helper.absolutePos(new BlockPos(1, 1, 1));
        level.getBlockState(quick).tick(level, quick, random);
        helper.assertBlockPresent(Blocks.SAND, new BlockPos(1, 1, 1));
        // a frozen waterfall melts away in mild air (the test platform's plains are well above freezing)
        helper.setBlock(3, 2, 1, ModBlocks.FROZEN_WATERFALL.get());
        BlockPos ice = helper.absolutePos(new BlockPos(3, 2, 1));
        level.getBlockState(ice).randomTick(level, ice, random);
        helper.assertBlockNotPresent(ModBlocks.FROZEN_WATERFALL.get(), new BlockPos(3, 2, 1));
        // snow won't lie on steaming ground
        helper.setBlock(5, 1, 1, ModBlocks.STEAMING_GROUND.get());
        helper.setBlock(5, 2, 1, Blocks.SNOW);
        BlockPos warm = helper.absolutePos(new BlockPos(5, 1, 1));
        level.getBlockState(warm).randomTick(level, warm, random);
        helper.assertBlockNotPresent(Blocks.SNOW, new BlockPos(5, 2, 1));
        // watermelon snow is still snow underfoot, in layers
        helper.setBlock(1, 1, 3, Blocks.STONE);
        helper.setBlock(1, 2, 3, ModBlocks.PINK_SNOW.get().defaultBlockState().setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, 3));
        helper.assertTrue(helper.getBlockState(new BlockPos(1, 2, 3)).getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) == 3, "pink snow keeps its depth");
        // no village out here, so no heat island
        helper.assertFalse(com.reiko.weather.temperature.Temperature.inVillage(level, helper.absolutePos(new BlockPos(1, 2, 1))), "no village on the test platform");
        helper.succeed();
    }
}
