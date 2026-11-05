# Reiko's Weather

A complete weather overhaul for **Minecraft 1.21.1** on **NeoForge**. It replaces vanilla's
rain/thunder cycle with 16 weather types. Each one looks and plays differently depending on the
climate you're standing in.

## Weather types

| Weather | What happens |
|---|---|
| **Clear** | Blue skies. After rain clears during the day, a **rainbow** appears opposite the sun. On clear nights in cold biomes, **auroras** can light up the northern sky. |
| **Overcast** | Gray skies hide the sun and stars, and the world gets a little darker. No precipitation. |
| **Fog** | Thick fog cuts visibility to about 10 blocks. In arid biomes it becomes a dusty haze. |
| **Drizzle** | Light, gentle rain. |
| **Rain** | Normal rain. Crops open to the sky grow faster. |
| **Heavy Rain** | A downpour with extra rain streaks and splashes, misty visibility and wind. Cold biomes get heavy snow that piles up. |
| **Thunderstorm** | Rain, thunder and lightning, with gusty wind. In deserts it's a *dry* storm that kicks up dust. |
| **Supercell** | A severe storm under a green-tinted sky: hail, constant lightning and **tornadoes**. |
| **Hurricane** | Hurricane-force wind that shoves players, mobs and items, torrential rain and flying leaves. It becomes a thundersnow whiteout in cold biomes and a sandstorm in deserts. |
| **Hailstorm** | Hailstones bounce off the ground and hurt unsheltered creatures. Helmets protect you, but they take durability damage. Crops can get knocked back. |
| **Sleet** | Icy, slanting rain that chills creatures (slowing them) and glazes still water with thin ice that melts in the sun. |
| **Snow** | Snowfall in cold biomes and cold rain elsewhere. |
| **Blizzard** | A whiteout: driving horizontal snow, near-zero visibility and deep snow drifts (up to 4 layers). Unsheltered creatures **freeze**. Leather armor or standing near a fire or torch keeps you warm. |
| **Gale** | Strong wind blows leaves around, pushes entities and makes arrows drift. Cold biomes get blowing snow, arid biomes get dust. |
| **Sandstorm** | In deserts, badlands and savannas: an orange sky, a wall of sand and dust, and strong wind. Elsewhere it's a dusty gale. |
| **Heatwave** | A hazy, bleached sky. Standing in direct sun costs extra hunger, and crops grow slower. |

### Tornadoes
During a **supercell**, tornadoes can touch down near players, and players nearby get a warning.
A tornado is a rotating funnel of cloud about 70 blocks tall. It wanders with the wind and pulls
in mobs, items and players, spinning them up and flinging them out. It also rips up leaves,
plants, crops and snow (see the block tag `reikos_weather:tornado_liftable`; respects the
`mobGriefing` gamerule). Tornadoes last 1–2 minutes before they dissipate.

### Climate-aware forecasting
The weather is global, but how it appears depends on the climate at your position: **cold**
(snowy biomes), **temperate** (biomes that get rain), or **arid** (biomes with no rain). The
forecast also leans toward weather that suits where players actually are. Desert players see more
sandstorms and heatwaves, and tundra players see more snow and blizzards. Storms tend to build
out of rain, and blizzards out of snow.

The cycle works alongside vanilla. Rain, snow, lightning, cauldrons filling, riptide and fishing all
still work. The vanilla `/weather` command and sleeping through the night are respected too.

## Barometer
Craft a **Barometer** (copper + glass pane + redstone) and right-click it to read the current
weather, how long it will last, the wind speed and direction, and a 3-step forecast.

```
 C        C = Copper Ingot
C G C     G = Glass Pane
 R        R = Redstone Dust
```

## Commands
| Command | Permission | Description |
|---|---|---|
| `/rweather query` | everyone | Current weather, climate, time left and wind |
| `/rweather forecast` | everyone | The next three weathers |
| `/rweather set <type> [seconds]` | op | Change the weather (e.g. `/rweather set blizzard 300`) |
| `/rweather next` | op | Skip to the next forecast weather |
| `/rweather tornado [pos]` | op | Spawn a tornado |

## Configuration
Open the config from the **Mods** screen, or edit the files directly:
- `config/reikos_weather-server.toml` (on the server; synced to players when they join): turn the cycle on or off,
  duration multiplier, the chance of each weather type, hazards (hail damage, freezing, heat
  exhaustion, snow pile-up, sleet ice, wind push, crop effects, extra lightning) and tornado settings.
- `config/reikos_weather-client.toml`: particle density, fog, sky tinting, rainbows, auroras and sounds.

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
  weather/    WeatherType, Climate, WeatherProfiles (the per-climate table), WeatherManager (cycle),
              WeatherHazards (gameplay), Wind, WeatherState (saved data)
  entity/     TornadoEntity
  client/     ClientWeather (smoothed visuals), particles, fog/sky/rainbow/aurora rendering, sounds
  mixin/      Sky and cloud tinting, hiding the sun under cloud cover
  network/    Server → client weather sync
```

To add or tweak a weather type, edit `WeatherType` (vanilla mapping, weight, duration and climate
affinity) and its row in `WeatherProfiles` (visuals and hazards for each climate).
