# Reiko's Weather

A complete weather overhaul for **Minecraft 1.21.1** on **NeoForge**. It replaces vanilla's
rain/thunder cycle with 17 weather types, seasons, storms that roll across the map, tornadoes,
waterspouts and dust devils. It also adds temperature, droughts and wildfires, winter hazards,
weather in the Nether and the End, a weather radar, ways to control the weather, weather gear,
weather-aware blocks, a full set of sky, ground and sound effects, and datapack-defined weather.

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
- **Puddles**, **wet-looking ground** and **raindrop ripples** on water.

## Temperature
Every spot has an air temperature, made up of the biome, the season, the height, the time of day and
the weather (rain cools, wind chills, sunshine and heatwaves warm). How warm *you* feel also depends
on your clothes, on being in water, and on heat sources nearby: campfires, fires, lava, lit furnaces,
magma and even torches. Buildings and caves even things out.

- Below 0 °C frost creeps in. In deadly cold (a blizzard, for example) you start to freeze and take
  damage slowly, so there's time to find shelter or a fire. Leather armor and the winter coat keep
  you warm.
- Above 32 °C the heat makes you hungry, and above 48 °C you get heatstroke unless you cool off in
  water or rain. A winter coat in a heatwave is a bad idea.
- Hold a **thermometer** to see the air temperature and how warm you feel (Celsius or Fahrenheit,
  set in the client config).

## Extreme weather
- **Droughts:** during heatwaves, dry grass in deserts and savannas can catch fire (**wildfires**),
  crops on unwatered farmland wilt, and water cauldrons out in the sun dry up.
- **Winter:** ponds and rivers ice over from the banks, **icicles** grow under the edges of roofs
  and can break off onto whoever is standing below, and blizzards set off **avalanches** of snow on
  steep slopes.
- **Lightning** turns the sand it strikes into glass (fulgurite), and in big storms you might meet
  **ball lightning**: a crackling orb that drifts around, zaps anything that gets too close and
  pops after a while.

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
  (fog), ghast tear (supercell) or heart of the sea (hurricane). It needs a few minutes to recover
  between uses.

## Sound
Thunder arrives after the flash, later the further away the strike is. Distant storms rumble on the
horizon. Indoors, the weather sounds muffled and rain drums on the roof. In strong wind, the wind
whistles around corners and between buildings.

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

## Weather & mobs
- Weather changes which monsters spawn: **strays** in blizzards and snow, **husks** in desert
  sandstorms and heatwaves, and **drowned** near water in heavy rain and hurricanes.
- **Animals look for shelter** under trees and roofs during rough weather.

## Advancements
A "Reiko's Weather" advancement tab with 14 entries, including Storm Chaser, We're Not in Kansas
Anymore, Practically Perfect (an umbrella glide), Northern Lights, Whiteout and Four Seasons.

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
  `glaze_water`, `glaze_ground`, `floods`, `heat_lightning`, `tornadoes`, `waterspouts` and
  `precipitation` (rates for `hail`, `sleet`, `downpour`, `snow`, `blizzard`, `sand`, `dust`, `leaves`).
- `climate_profiles`: extra overrides for `cold`, `temperate` or `arid`.

The mod ships two examples: **Pollen Haze**, a spring haze in temperate places, and **Ashfall**
(command only: `/rweather set reikos_weather:ashfall`).

## Compatibility
- **JEI:** every item has an information page.
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

## Configuration
Open the config from the **Mods** screen, or edit the files directly:
- `config/reikos_weather-server.toml` (on the server; synced to players when they join): weather
  chances, durations, seasons (length, temperature shift, crop slowdown), storm cells (how often,
  how many), every hazard, flash floods, puddles, ice glaze, tornadoes, waterspouts, dust devils,
  weather mobs, animal sheltering, temperature, wildfires, crop wilting, drying cauldrons, freezing
  rivers, icicles, avalanches, fulgurite, ball lightning, the altar's cooldown, Nether and End
  weather, and datapack weather.
- `config/reikos_weather-client.toml`: particle density, fog, sky tinting, storm clouds (and how far
  out they're drawn), wet ground, mist, rainbows, auroras, heat lightning, seasonal colours, falling
  leaves, sounds (delayed thunder, muffling indoors, roof rain and wind whistle), Fahrenheit, and the
  forecast panel (`BAROMETER`, `ALWAYS` or `NEVER`).

## Building
Requires JDK 21.

```bash
./gradlew build          # jar ends up in build/libs/
./gradlew runClient      # launch a dev client
./gradlew runServer      # launch a dev server
./gradlew runClient -PwithCompat   # with JEI, Serene Seasons and Distant Horizons installed
```

## Project layout
```
src/main/java/com/reiko/weather/
  weather/    WeatherType, Climate, WeatherProfiles (per-climate table), WeatherManager (cycle and storm cells),
              LocalWeather (what you get at one spot), Seasons, WeatherHazards (gameplay), WeatherAdvancements
              ExtremeWeather (droughts, wildfires, icicles, avalanches), LightningEvents, CustomWeather(s) (datapacks)
  temperature/ Temperature (air and felt temperature), TemperatureEffects (cold and heat)
  otherworld/ Nether and End weather
  entity/     TornadoEntity (tornado, waterspout, dust devil), ball lightning, cloud seeding rocket
  block/      Weather station, tornado siren, rain barrel, puddles, ice glaze, icicles, radar, vane, windmill, altar
  item/       Barometer, umbrella, thermometer, handheld radar, rocket and gear events
  compat/     JEI plugin, Distant Horizons
  mob/        Weather-themed spawns, animals seeking shelter
  client/     Smoothed visuals, particles, fog/sky/rainbow/aurora, storm cloud deck, wet ground, forecast and
              temperature HUD, radar screen, seasonal colours, Nether/End visuals, block renderers, ambient sound
  mixin/      Sky tinting, seasonal biome temperature and colours, storm cells in vanilla rain checks
  network/    Server → client weather sync
```

To add or tweak a weather type, edit `WeatherType` (vanilla mapping, weight, duration and climate
affinity) and its row in `WeatherProfiles` (visuals and hazards for each climate).
