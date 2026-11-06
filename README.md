# Reiko's Weather

A complete weather overhaul for **Minecraft 1.21.1** on **NeoForge**. It replaces vanilla's
rain/thunder cycle with 18 weather types, seasons, storms that roll across the map, tornadoes,
waterspouts and dust devils. It also adds body temperature and ways to stay comfortable, local
climates shaped by the terrain, droughts and wildfires, winter hazards, storm surges and tides,
meteor showers and blood moons, seasonal wildlife, weather fishing and seasonal crops, a paraglider,
weather in the Nether and the End, a weather radar and radio, ways to control the weather, a
meteorologist villager, weather gear, weather-aware and redstone blocks, a full set of sky, ground
and sound effects, an in-game guidebook, and datapack-defined weather.

New players get a **Weather Almanac**, the in-game guide to everything below (with recipes).

## Weather types

| Weather | What happens |
|---|---|
| **Clear** | Blue skies. After rain, a **rainbow** appears opposite the sun. On some clear nights in cold biomes, **auroras** shimmer over the northern sky. |
| **Overcast** | Gray skies hide the sun and stars, and the world gets a little darker. |
| **Fog** | Thick fog, about 24 blocks of visibility. A dusty haze in arid biomes. |
| **Drizzle** | Light, gentle rain. |
| **Rain** | Normal rain. Crops open to the sky grow faster, and puddles form on flat ground. |
| **Heavy Rain** | A downpour with extra rain streaks and splashes, misty visibility and wind. Heavy snow in cold biomes. |
| **Thunderstorm** | Rain, thunder and lightning, with gusty wind. A *dry* dust storm in deserts. Can spawn waterspouts over open water. |
| **Supercell** | A severe storm under a green-tinted sky: hail, constant lightning, **tornadoes** and waterspouts. |
| **Hurricane** | Hurricane-force wind that shoves players, mobs and items, torrential rain and flying leaves. A thundersnow whiteout in cold biomes, a sandstorm in deserts. |
| **Hailstorm** | Hailstones hurt unsheltered creatures. Helmets and umbrellas protect you, and livestock gets bruised but survives. Crops can be knocked back. |
| **Sleet** | Icy, slanting rain that chills and slows creatures, and glazes still water with thin ice. |
| **Ice Storm** | Freezing rain that coats the ground and treetops in slippery **ice glaze**, which melts once the storm passes. |
| **Snow** | Snowfall in cold places, cold rain elsewhere. |
| **Blizzard** | A whiteout with driving horizontal snow and snow drifts up to 4 layers. Unprotected players **freeze**. Leather armor, a winter coat or standing near a fire keeps you warm. |
| **Gale** | Strong wind blows leaves around, pushes entities and makes arrows drift. |
| **Sandstorm** | A wall of sand and an orange sky in deserts and badlands. Storm goggles let you see through it. |
| **Heatwave** | A hazy amber sky. Standing in the sun costs extra hunger (double in a winter coat), crops grow slower, and there's silent heat lightning at night. |
| **Derecho** | A long line of violent thunderstorms that races across the map several times faster than any other storm, in summer. The wind is ferocious and breaks branches off trees. On the radar it's a long orange band. |

Each weather looks and plays differently in **cold**, **temperate** and **arid** climates, and the
forecast leans toward weather that suits where players actually are.

## Seasons
Spring, summer, autumn and winter each last 7 days by default. Each season reshapes the forecast:
spring storms, summer heatwaves and hurricanes, autumn fog and gales, winter blizzards and ice
storms. Seasons also shift biome temperatures, so temperate plains **get snow in winter**, and
the snow and frozen lake surfaces **melt again** when it warms up. Crops grow more slowly in winter.
World generation is never affected.

Trees change with the seasons too: leaves turn **gold, orange and red** through autumn and drift
down from the branches, and grass and leaves look dull and dormant in winter before greening up
again in spring.

Seasonal life:
- **Spring:** blossom petals drift down from the trees, and geese fly north overhead in a V.
- **Summer:** **fireflies** glow over the grass on warm evenings.
- **Autumn:** fallen leaves pile up under the trees as **leaf piles** (1 to 4 layers), and geese fly
  south. Wade through them, land in a deep pile to soften a fall, or gather them with a **rake**. They
  rot away over the winter.
- After rain, **frogs** croak from the water and **earthworms** come up onto the grass.

If **Serene Seasons** is installed, its calendar is used instead (including the day of the season),
and its own foliage colours take over.

## Storms that roll in
Besides the world-wide weather, **storm cells** form on the horizon and drift across the map with
the wind. Players far apart can have different weather. You can watch a storm approach as a
low, dark cloud deck with shafts of rain hanging beneath it. Inside a cell, everything works the
way rain normally does: riptide, cauldrons filling, fires going out, snow piling up and lightning.

## Phenomena
- **Tornadoes** (supercells): a 70-block funnel that wanders with the wind. It pulls in mobs,
  items and players, lifts them 20+ blocks and flings them out, and rips up leaves, plants, crops
  and snow (tag `reikos_weather:tornado_liftable`; respects `mobGriefing`).
- **Waterspouts**: form over open water in thunderstorms, supercells and hurricanes, and collapse
  when they reach land.
- **Dust devils**: small, mostly harmless whirlwinds in arid biomes on hot, clear days.
- **Morning mist** over rivers, lakes, swamps and beaches at dawn.
- **Heat lightning**: silent flashes on hot nights and from distant storms.
- **Flash floods** (off by default): heavy storms raise low-lying water so it spills over its
  banks. Every flood source is tracked, and the water recedes after the storm.
- **Spring snowmelt:** for the first few days of spring, melting snow swells the rivers. The water
  goes down again afterwards.
- **Tides:** the sea rises and falls twice a day, highest around noon and midnight. Tides are
  strongest around full and new moons (spring tides); the highest of them wash over low beaches for a
  while. Near the coast, the barometer and `/rweather query` tell you the tide.
- **Rough seas:** storms toss boats around on open water, with whitecaps and spray, and the view rolls
  with the waves.
- **Thin ice:** ice over open water is thin unless it's bitterly cold. It cracks under you if you
  sprint across it or stand around on it, and eventually gives way. Crouch to spread your weight.
- **Freezing fog** coats grass and leaves in white **hoarfrost**, which melts off once it warms up.
- **Storm debris:** derechos, hurricanes and supercells break **branches** off trees (they drop
  sticks, or rot away after a few days) and strip their leaves.
- **Puddles**, **wet-looking ground** and **raindrop ripples** on water.

## Local climates
The terrain shapes the weather around you:
- **Valleys** are calmer and collect fog and mist.
- **Peaks and high ground** are windier.
- Land in the **rain shadow** downwind of a mountain range gets less rain.
- In cold biomes, **lake-effect snow** falls downwind of big lakes, even on otherwise calm days.

`/rweather query`, the barometer and the F3 screen all show the local climate where you stand.

Jungles and savannas have a **monsoon**: summer is their rainy season, and savannas, which normally
never see rain, get it in summer too. Dry weather is less likely there while it lasts.

## Temperature
Every spot has an air temperature, made up of the biome, the season, the height, the time of day and
the weather (rain cools, wind chills, sunshine and heatwaves warm). How warm *you* feel also depends
on your clothes, on being in water, and on heat sources nearby: campfires, fires, lava, lit furnaces,
magma and even torches. Buildings and caves even things out.

Your **body temperature** follows the air temperature gradually: you cool down over half a minute or
so out in the cold, and warm back up quickly once you're by a fire. The temperature HUD shows your
body temperature and whether you're warming up or cooling down. The effects below depend on your body
temperature, not the air.

- Below 0 °C frost creeps in. In deadly cold (a blizzard, for example) you start to freeze and take
  damage slowly, so there's time to find shelter or a fire. Leather armor and the winter coat keep
  you warm.
- Above 32 °C the heat makes you hungry, and above 48 °C you get heatstroke unless you cool off in
  water or rain. A winter coat in a heatwave is a bad idea.
- Hold a **thermometer** to see the air temperature and how warm you feel (Celsius or Fahrenheit,
  set in the client config).

Ways to stay comfortable:
- **Hot cocoa** gives *Warmth* (+10 °C) for 3 minutes. A **melon cooler** gives *Refreshed* (−10 °C).
- The **sun hat** shades you from the midday sun (up to −6 °C on hot days).
- A **tent** is a portable shelter: it's much milder inside, and the weather can't reach you.
  You can sleep in it to skip the night, but it never sets your spawn point.
- **Glass greenhouses** are much milder inside than out, warmest by day.

## Farming
- On freezing nights (or in any hard frost), **frost** damages crops open to the sky and knocks them
  back a few growth stages. Seedlings can die.
- A crop with **glass or glass panes** above it is in a greenhouse: it's safe from frost and hail, and
  keeps growing through winter. It doesn't get the rain bonus, though.
- **Rice** turns up in the grass of jungles, savannas and swamps. It races ahead in the rain and the
  monsoon, keeps going next to water, and stalls in dry weather. Three handfuls make a **bowl of rice**.
- **Kale** turns up in the grass of cold places. It's a hardy winter green: frost doesn't hurt it and
  it keeps growing through the winter, but it slows right down in the heat. It goes into a **winter
  stew** that warms you up.
- **Food spoils in the heat:** above about 27 °C, raw meat and fish go off a piece at a time, in your
  inventory and in chests. Carrying ice keeps your own food fresh; an **icebox**, or a chest or barrel
  with ice or snow next to it (an ice house), keeps stored food fresh. The **ice saw** cuts whole
  blocks of ice out of frozen lakes.

## Fishing
- Fish bite better in the rain: sometimes a second fish comes up.
- **Stormfish** bite in thunderstorms, supercells, hurricanes and derechos (cooked: a burst of Haste).
- **Icefish** bite when you fish through a hole in the ice in winter or the cold (cooked: cools you).
- **Earthworms** come up after rain: dig wet grass or dirt for them. Hold them in your off hand when
  you cast to bait the hook, so fish bite sooner and better ones come up.

## Extreme weather
- **Droughts:** during heatwaves, dry grass in deserts and savannas can catch fire (**wildfires**),
  crops on unwatered farmland wilt, and water cauldrons out in the sun dry up.
- **Winter:** ponds and rivers ice over from the banks, **icicles** grow under the edges of roofs
  and can break off onto whoever is standing below, and blizzards set off **avalanches** of snow on
  steep slopes.
- **Lightning** turns the sand it strikes into glass (fulgurite), and in big storms you might meet
  **ball lightning**: a crackling orb that drifts around, zaps anything that gets too close and
  pops after a while.
- **Dry lightning:** lightning that strikes where it isn't raining can set dry grass and wood alight,
  and summer heatwaves bring the odd dry thunderstorm strike. It respects `doFireTick` and
  `mobGriefing`.
- **Storm surges:** hurricanes push the sea up onto low beaches and coastal flats. The water drains
  away once the storm has passed. **Sandbags** hold it back: no surge or flood water appears within 2
  blocks of one.
- **Salt** melts ice glaze, snow, icicles, powder snow and ice in a 3×3×3 area when you use it on a
  block. Smelt a water bottle to get some, and cauldrons that dry out in the sun sometimes leave salt
  behind.
- **Lightning-charged tools:** leave a tool or weapon lying on an anvil or a copper block in a
  thunderstorm (a lightning rod nearby helps). If lightning strikes it, it's charged for five minutes:
  it mines faster and hits harder, with a crackle of sparks.

## Sky events
- **Shooting stars** streak across clear night skies.
- On **meteor shower** nights there are dozens of them, and now and then a **meteor** comes down.
  It glows, roars, leaves a small crater (only if `mobGriefing` is on) and a **meteorite** block,
  which drops raw iron, gold nuggets and sometimes a diamond. Silk touch keeps the block.
- On some aurora nights a **geomagnetic storm** brings auroras to temperate skies, makes compasses
  spin and fills radar screens with static.
- Some full moons rise as a **blood moon**: the moon and the night sky turn red, monsters swarm, and
  nobody can sleep until dawn.
- **Halos** ring the sun or moon on icy days and under thin, high cloud, and when the sun is low,
  bright **sun dogs** sit level with it on either side. When the sun shines through broken cloud or
  morning mist, **sunbeams** fan out below the clouds.

## The Nether and the End
The other dimensions get weather of their own.
- **Nether:** choking **ash storms**, harmless glowing **ember falls**, and **fire rain** that sets
  exposed creatures and flammable blocks alight.
- **End:** violet **void storms** with gusts every few seconds that push you toward the edge of the island.

## Weather radar
The **handheld radar** and the **weather radar** block open a radar scope. It shows storm cells
colour-coded by type, which way they're moving, when they'll reach you and any tornadoes in range.
Scroll to zoom out to 3,200 blocks. A comparator next to the radar block reads how close the nearest
storm is.

## Controlling the weather
- The **cloud seeding rocket** bursts in the sky and seeds a rain cloud (sometimes a thunderstorm)
  overhead.
- The **weather altar** is end-game weather control. Offer it an item to call up a weather:
  sunflower (clear), water bucket (rain), lightning rod (thunderstorm), snow block (snow), packed ice
  (blizzard), ice (ice storm), blaze powder (heatwave), feather (gale), sand (sandstorm), cobweb
  (fog), ghast tear (supercell), heart of the sea (hurricane) or breeze rod (derecho). It needs a few
  minutes to recover between uses.

## Weather radio
Keep a **weather radio** in your inventory (switched on) and it warns you, with an alert tone, about
severe storms closing in (with their direction and how long until they arrive), tornadoes nearby,
dangerous weather coming up in the forecast, extreme cold and heat, and tonight's blood moon, meteor
shower or geomagnetic storm. Use it to switch it on or off; sneak and use it for a bulletin.

## Paraglider
Hold use while falling to open the **paraglider**. You glide where you look (look down to dive, up to
slow down) and the wind carries you. Warm air rising off lava, fires, campfires, sun-baked sand on hot
days and windy ridges lifts you up, so you can ride thermals a long way. No fall damage while you
glide, but it wears out over time.

## Sound
Thunder arrives after the flash, later the further away the strike is. Distant storms rumble on the
horizon. Indoors, the weather sounds muffled and rain drums on the roof. In strong wind, the wind
whistles around corners and between buildings.

## Atmosphere
- Your **breath** shows in the cold.
- Leaves keep **dripping** for a while after the rain stops.
- The air **shimmers** over hot ground on sunny days above about 28 °C.
- You leave **footprints** in snow, which fade over time.
- **Raindrops** run down the screen when you look up into the rain.
- The F3 screen has lines with the local weather, the local climate, your body temperature and the
  sky effects.
- The forecast and temperature panels hide while a menu is open.

## Accessibility
Under **Accessibility** in the client settings:
- **Reduce flashing** turns off lightning sky flashes and heat lightning (vanilla's "Hide Lightning
  Flashes" option does the same for this mod's flashes).
- **Screen effect strength** (0 to 1) scales raindrops on the screen, the heat shimmer and the roll of
  a boat on rough seas; 0 turns them off.
- The weather radio's alert tone can be switched off.

## Gear & blocks
| | Recipe | What it does |
|---|---|---|
| **Barometer** | copper, glass pane, redstone | Right-click for the weather, wind, season, forecast and approaching storms. While held, a forecast panel shows in the corner. |
| **Umbrella** | 3 wool + 2 sticks | Blocks hail. Hold it while falling to drift down slowly with no fall damage. |
| **Winter Coat** | wool + leather | Immune to freezing. Too warm for a heatwave. |
| **Storm Goggles** | leather, glass panes, iron nugget | See much further in sandstorms and blizzards. |
| **Weather Station** | copper, glass pane, redstone, barometer | Outputs redstone. Right-click to switch between precipitation, storm danger, wind, a 0–15 weather code and the season. |
| **Tornado Siren** | iron, note blocks, redstone | Wails and powers redstone while a tornado or waterspout is within 160 blocks. |
| **Rain Barrel** | planks + iron ingot | Fills with rainwater. Take it out with a bucket when full, or with bottles. |
| **Thermometer** | iron nugget, glass pane, redstone | Hold it to see the temperature. Right-click for a reading. |
| **Handheld Radar** | iron, glass pane, compass, redstone | Opens the radar scope, centred on you. |
| **Weather Radar** | iron, lightning rod, glass panes, handheld radar, redstone block | Radar scope as a block. Comparators read storm distance (15 = overhead). |
| **Weather Vane** | copper, lightning rod, smooth stone slab | Points into the wind. Comparators read the wind direction (1 = north ... 8 = north-west). |
| **Windmill** | wool, sticks, redstone, planks | Turns into the wind and gives off redstone as strong as the wind. Works best high up and in the open. |
| **Cloud Seeding Rocket** | paper, gunpowder, snowball (makes 2) | Fire it straight up to seed a rain cloud. |
| **Weather Altar** | diamonds, nether star, gold blocks, barometer, crying obsidian | End-game weather control (see above). |
| **Hot Cocoa** | 2 cocoa beans, sugar, milk bucket, bowl | A warm drink: *Warmth* for 3 minutes. Always drinkable. |
| **Melon Cooler** | 2 melon slices, ice, sugar, glass bottle | A cold drink: *Refreshed* for 3 minutes. |
| **Sun Hat** | 5 wheat | Shade from the sun on hot days. |
| **Tent** | 3 wool over 2 sticks (sticks in the corners) | Shelter from the cold and the weather. Sleep in it to skip the night (doesn't set your spawn). |
| **Sandbag** | 3 sand + string (makes 2) | Keeps storm surges and flash floods away within 2 blocks. |
| **Salt** | smelt a water bottle | Use on a block to melt ice and snow around it. |
| **Lightning Battery** | copper, lightning rod, redstone block | Put it under a lightning rod (up to 4 blocks below the strike) to store up to 4 strikes. A redstone pulse from the side, back or bottom lets one out as a strong 2-second pulse from the front. Comparators read the charge, and it glows brighter as it fills. |
| **Forecast Board** | 5 planks + barometer | A wall board that shows the current weather, the season and the next three weathers. |
| **Storm Probe** | barometer, iron, redstone | Leave it in a tornado's path. Probes near a tornado record readings and drop **storm data** when broken (more from close to the funnel). Meteorologists buy storm data. |
| **Weather Almanac** | book + feather | The guidebook. New players get one when they first join. |
| **Weather Radio** | iron, copper, redstone, note block | Warns you about severe weather (see above). |
| **Paraglider** | phantom membranes, wool, string, leather | Glide and ride thermals (see above). |
| **Rake** | 5 sticks | Gathers leaf piles and fallen branches in a 5×5 area. |
| **Ice Saw** | 3 iron + stick | Cuts whole blocks of ice, packed ice and blue ice. |
| **Icebox** | planks, packed ice, barrel | A chest-sized box where food never spoils. |
| **Leaf Pile** | 1 leaves (makes 2) | Fallen leaves; stack up to 4 layers. Compostable. |
| **Bowl of Rice** | bowl + 3 rice | A filling meal. |
| **Winter Stew** | bowl, 2 kale, carrot, baked potato | Warmth for a minute and a half. |
| **Cooked Stormfish / Icefish** | cook the raw fish | Haste / Refreshed. |

## Weather & mobs
- Weather changes which monsters spawn: **strays** in blizzards and snow, **husks** in desert
  sandstorms and heatwaves, and **drowned** near water in heavy rain and hurricanes.
- **Animals look for shelter** under trees and roofs during rough weather.
- **Villagers go indoors** during storms.
- **Polar bears** sometimes wander in out of blizzards in cold biomes.
- Sheep grow a thicker coat in winter and in cold biomes: shearing them gives **extra wool**.
- **Birdsong** on spring and summer days, **crickets** on summer nights, **frogs** after rain, and
  **geese** honking as they fly over in spring and autumn.
- Under a **blood moon**, extra monsters spawn in the dark around every player.

### The meteorologist
A villager who claims a **weather station** becomes a meteorologist. They buy paper, copper and storm
data, and sell thermometers, barometers, umbrellas, cloud seeding rockets, salt, sandbags, storm
probes, goggles, winter coats, handheld radars and, at master level, lightning batteries and weather
radars.

## Advancements
A "Reiko's Weather" advancement tab with 38 entries, including Storm Chaser, We're Not in Kansas
Anymore, Practically Perfect (an umbrella glide), Northern Lights, Whiteout, Four Seasons, Bottled
Lightning, Eye of the Storm, Make a Wish, Once in a Blood Moon, Skating on Thin Ice, Riding the
Thermals, Fishing in Troubled Waters, Supercharged and Straight-Line Winds.

## Datapack weather
Datapacks can add new weather types. Put a JSON file at
`data/<namespace>/reikos_weather/weather/<name>.json`. Each one is built on a built-in weather (its
`base`), which decides what vanilla sees (rain, thunder) and supplies every value the file doesn't
change:

```json
{
  "name": "Ashfall",
  "base": "overcast",
  "weight": 2,
  "duration": [300, 900],
  "seasons": ["autumn", "winter"],
  "climates": ["temperate", "cold"],
  "profile": {
    "overcast": 0.95, "fog": 0.45, "fog_color": "#6E6A66", "tint": "#8A8178", "tint_strength": 0.45,
    "wind": 0.2, "precipitation": { "dust": 0.8 }
  },
  "climate_profiles": { "cold": { "precipitation": { "dust": 0.5, "snow": 0.3 } } }
}
```

- `name` (or `translate` for a translation key): what players see.
- `weight`: how likely it is to turn up in the forecast. 0 means only by command.
- `duration`: seconds, either `[min, max]` or a single number.
- `seasons` and `climates`: where it can happen (leave them out for anywhere).
- `profile`: any of `rain`, `thunder`, `overcast`, `fog`, `fog_color`, `tint`, `tint_strength`,
  `wind`, `clouds`, `freeze`, `lightning`, `snow_layers`, `hail`, `heat`, `crop_boost`, `puddles`,
  `glaze_water`, `glaze_ground`, `floods`, `heat_lightning`, `tornadoes`, `waterspouts`, `debris` and
  `precipitation` (rates for `hail`, `sleet`, `downpour`, `snow`, `blizzard`, `sand`, `dust`, `leaves`).
- `climate_profiles`: extra overrides for `cold`, `temperate` or `arid`.

The mod ships two examples: **Pollen Haze**, a spring haze in temperate places, and **Ashfall**
(command only: `/rweather set reikos_weather:ashfall`).

## Compatibility
- **JEI:** every item has an information page.
- **Jade** and **WTHIT:** looking at the mod's blocks shows their state: a lightning battery's
  charge, a storm probe's readings, a rain barrel's water, a weather station's mode and signal, a
  vane's wind direction, a leaf pile's layers and so on.
- **Tough As Nails:** its body temperature takes over from this mod's (so the two don't stack), and
  this mod's weather feeds into it: blizzards and freezing storms chill a spot, heatwaves warm it, and
  the warm and cold drinks, the sun hat and the tent work on its scale. Can be turned off in the
  server config.
- **Curios:** the sun hat and storm goggles can be worn in a Curios head slot (and show on your head).
- **Iris / Oculus:** the heat shimmer steps aside while a shader pack is on (shader packs run their
  own post-processing).
- **Serene Seasons:** its calendar drives the seasons (see above).
- **Distant Horizons:** the storm cloud deck reaches as far as Distant Horizons draws terrain.

## Commands
| Command | Permission | Description |
|---|---|---|
| `/rweather query` | everyone | Weather where you stand, wind, season and approaching storms |
| `/rweather forecast` | everyone | The next three weathers |
| `/rweather storms` | everyone | List storm cells (`/rweather storms clear` to remove them, op) |
| `/rweather season` | everyone | The current season (`/rweather season <season>` to change it, op) |
| `/rweather set <type> [seconds]` | op | Change the world-wide weather. Also takes datapack weathers (`namespace:name`), and in the Nether or End sets that dimension's weather (`ash_storm`, `ember_fall`, `fire_rain`, `void_storm`, `calm`) |
| `/rweather storm <type> [here]` | op | Start a storm cell upwind (or right overhead) |
| `/rweather next` | op | Skip to the next forecast weather |
| `/rweather tornado [pos] [tornado\|waterspout\|dust_devil]` | op | Spawn a whirlwind |
| `/rweather meteor [pos]` | op | Bring a meteor down at a position (or near you) |
| `/rweather wind <from>` | op | Set where the wind blows from (`n`, `ne`, `e`, ... `nw`) |

## Configuration
Open the settings from the **Mods** screen. The first page has one-click **presets** for the visual
effects (Performance, Default, Cinematic) and a button for every setting. Or edit the files directly:
- `config/reikos_weather-server.toml` (on the server; synced to players when they join): weather
  chances, durations, seasons (length, temperature shift, crop slowdown), storm cells (how often,
  how many), every hazard, flash floods, puddles, ice glaze, tornadoes, waterspouts, dust devils,
  weather mobs, animal sheltering, temperature, wildfires, crop wilting, drying cauldrons, freezing
  rivers, icicles, avalanches, fulgurite, ball lightning, the altar's cooldown, Nether and End
  weather, datapack weather, crop frost, local climates, the monsoon, storm surges, dry lightning,
  meteors, geomagnetic storms, villagers sheltering, winter wool, polar bears, blood moons (and
  whether you can sleep through them), snowmelt floods, thin ice, rough seas, tides, storm debris,
  leaf piles, wild rice and kale, food spoilage, weather fishing, earthworms, lightning-charged tools,
  the almanac on first join, and Tough As Nails.
- `config/reikos_weather-client.toml`: particle density, fog, sky tinting, storm clouds (and how far
  out they're drawn), wet ground, mist, rainbows, auroras, heat lightning, seasonal colours, falling
  leaves, sounds (delayed thunder, muffling indoors, roof rain and wind whistle), birdsong and
  crickets, breath, footprints, screen raindrops, heat shimmer, dripping leaves, shooting stars,
  halos and sunbeams, hoarfrost, fireflies, blossoms, bird flocks, whitecaps, accessibility
  (reduce flashing, screen effect strength, the radio's alert tone), Fahrenheit, and the forecast
  panel (`BAROMETER`, `ALWAYS` or `NEVER`).

## Building
Requires JDK 21.

```bash
./gradlew build          # jar ends up in build/libs/
./gradlew runClient      # launch a dev client
./gradlew runServer      # launch a dev server
./gradlew runClient -PwithCompat   # with JEI, Jade, Curios, Tough As Nails, Serene Seasons and Distant Horizons
./gradlew runClient -PwithWthit    # with WTHIT (instead of comparing against Jade)
./gradlew runGameTestServer        # run the automated in-game tests
```

To check the visuals with shader packs, put the Sodium and Iris jars in `run/client/mods/` (Sodium
doesn't start when added through Gradle in the dev environment).

## Project layout
```
src/main/java/com/reiko/weather/
  weather/    WeatherType, Climate, WeatherProfiles (per-climate table), WeatherManager (cycle and storm cells),
              LocalWeather (what you get at one spot), Seasons, WeatherHazards (gameplay), WeatherAdvancements
              ExtremeWeather (droughts, wildfires, icicles, avalanches), LightningEvents, CustomWeather(s) (datapacks),
              Microclimate (valleys, peaks, rain shadow, lake effect), Monsoon, SkyPhenomena, Tides, ThinIce,
              StormDebris
  temperature/ Temperature (air and felt temperature), TemperatureEffects (body temperature, cold and heat)
  farming/    Greenhouses, crop frost and food spoilage
  nature/     Autumn leaf piles; wild crops, earthworms, weather fishing and leaf-pile landings (NatureEvents)
  sky/        Meteors, blood moons
  otherworld/ Nether and End weather
  entity/     TornadoEntity (tornado, waterspout, dust devil), ball lightning, cloud seeding rocket, meteor
  block/      Weather station, tornado siren, rain barrel, puddles, ice glaze, icicles, radar, vane, windmill, altar,
              tent, lightning battery, forecast board, storm probe, leaf pile, fallen branch, rice, kale, icebox
  item/       Barometer, umbrella, thermometer, handheld radar, rocket, drinks, salt, paraglider, weather radio,
              rake, ice saw, almanac, charged tools and gear events
  compat/     JEI, Jade and WTHIT plugins, Tough As Nails, Curios, shader detection, Distant Horizons
  mob/        Weather-themed spawns, animals seeking shelter, WeatherCreatures (villagers, polar bears, wool, meteorologist trades)
  client/     Smoothed visuals, particles, fog/sky/rainbow/aurora/halos/sunbeams/blood moon, storm cloud deck, wet
              ground, forecast and temperature HUD, radar screen, seasonal colours and hoarfrost, Nether/End visuals,
              block renderers, ambient and wildlife sound, shooting stars, breath, footprints, screen raindrops, heat
              shimmer, rough seas, fireflies, blossoms, worms, bird flocks, paraglider, the almanac screen (guide/),
              config presets and accessibility
  mixin/      Sky tinting, seasonal biome temperature and colours, storm cells in vanilla rain checks, monsoon rain,
              fishing bait
  gametest/   Automated in-game tests
  network/    Server → client weather sync
```

To add or tweak a weather type, edit `WeatherType` (vanilla mapping, weight, duration and climate
affinity) and its row in `WeatherProfiles` (visuals and hazards for each climate).

The almanac's text lives in `assets/reikos_weather/guide/en_us.json` (chapters → entries → pages, each
page with optional `items` and a `recipe` to show). Resource packs can replace or translate it by
adding `guide/<language>.json`.
