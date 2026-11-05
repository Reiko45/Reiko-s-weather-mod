# Reiko's Weather

A complete weather overhaul for **Minecraft 1.21.1** on **NeoForge**. It replaces vanilla's
rain/thunder cycle with 17 weather types, seasons, storms that roll across the map, tornadoes,
waterspouts and dust devils. It also adds weather gear, weather-aware blocks and a full set of
sky and ground effects.

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
If **Serene Seasons** is installed, its calendar is used instead.

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

## Weather & mobs
- Weather changes which monsters spawn: **strays** in blizzards and snow, **husks** in desert
  sandstorms and heatwaves, and **drowned** near water in heavy rain and hurricanes.
- **Animals look for shelter** under trees and roofs during rough weather.

## Advancements
A "Reiko's Weather" advancement tab with 14 entries, including Storm Chaser, We're Not in Kansas
Anymore, Practically Perfect (an umbrella glide), Northern Lights, Whiteout and Four Seasons.

## Commands
| Command | Permission | Description |
|---|---|---|
| `/rweather query` | everyone | Weather where you stand, wind, season and approaching storms |
| `/rweather forecast` | everyone | The next three weathers |
| `/rweather storms` | everyone | List storm cells (`/rweather storms clear` to remove them, op) |
| `/rweather season` | everyone | The current season (`/rweather season <season>` to change it, op) |
| `/rweather set <type> [seconds]` | op | Change the world-wide weather |
| `/rweather storm <type> [here]` | op | Start a storm cell upwind (or right overhead) |
| `/rweather next` | op | Skip to the next forecast weather |
| `/rweather tornado [pos] [tornado\|waterspout\|dust_devil]` | op | Spawn a whirlwind |

## Configuration
Open the config from the **Mods** screen, or edit the files directly:
- `config/reikos_weather-server.toml` (on the server; synced to players when they join): weather
  chances, durations, seasons (length, temperature shift, crop slowdown), storm cells (how often,
  how many), every hazard, flash floods, puddles, ice glaze, tornadoes, waterspouts, dust devils,
  weather mobs and animal sheltering.
- `config/reikos_weather-client.toml`: particle density, fog, sky tinting, storm clouds, wet ground,
  mist, rainbows, auroras, heat lightning, sounds and the forecast panel (`BAROMETER`, `ALWAYS`
  or `NEVER`).

## Building
Requires JDK 21.

```bash
./gradlew build          # jar ends up in build/libs/
./gradlew runClient      # launch a dev client
./gradlew runServer      # launch a dev server
```

## Project layout
```
src/main/java/com/reiko/weather/
  weather/    WeatherType, Climate, WeatherProfiles (per-climate table), WeatherManager (cycle and storm cells),
              LocalWeather (what you get at one spot), Seasons, WeatherHazards (gameplay), WeatherAdvancements
  entity/     TornadoEntity (tornado, waterspout, dust devil)
  block/      Weather station, tornado siren, rain barrel, puddles, ice glaze
  item/       Barometer, umbrella and gear events
  mob/        Weather-themed spawns, animals seeking shelter
  client/     Smoothed visuals, particles, fog/sky/rainbow/aurora, storm cloud deck, wet ground, forecast HUD, sounds
  mixin/      Sky tinting, seasonal biome temperature, storm cells in vanilla rain checks
  network/    Server → client weather sync
```

To add or tweak a weather type, edit `WeatherType` (vanilla mapping, weight, duration and climate
affinity) and its row in `WeatherProfiles` (visuals and hazards for each climate).
