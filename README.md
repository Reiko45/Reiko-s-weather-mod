# Reiko's Weather

A complete weather overhaul for **Minecraft 1.21.1** on **NeoForge**. It replaces vanilla's
rain/thunder cycle with 36 weather types, seasons, storms and weather fronts that roll across the map, tornadoes,
waterspouts and dust devils. It also adds body temperature and ways to stay comfortable, local
climates shaped by the terrain, droughts and wildfires, winter hazards, storm surges and tides,
meteor showers and blood moons, volcanoes that wake up and erupt, landforms shaped by the weather (hot
springs and geysers, salt flats, glaciers, dunes and more), seasonal wildlife, weather fishing and seasonal crops, a paraglider,
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
| **Hurricane** | Forms over the open sea and comes in on a track that curves round. Warm sea strengthens it (category 1 to 5), land and cold water weaken it until it's just a big rainstorm. Spiral rain bands sweep round a calm, clear **eye** ringed by the eyewall. Hurricane-force wind that shoves players, mobs and items, torrential rain and flying leaves; a thundersnow whiteout in cold biomes, a sandstorm in deserts. With storm cells on, hurricanes only ever arrive as storm cells. |
| **Hailstorm** | Hailstones hurt unsheltered creatures. Helmets and umbrellas protect you, and livestock gets bruised but survives. Crops can be knocked back. |
| **Sleet** | Icy, slanting rain that chills and slows creatures, and glazes still water with thin ice. |
| **Ice Storm** | Freezing rain that coats the ground and treetops in slippery **ice glaze**, which melts once the storm passes. |
| **Snow** | Snowfall in cold places, cold rain elsewhere. |
| **Blizzard** | A whiteout with driving horizontal snow and snow drifts up to 4 layers. Unprotected players **freeze**. Leather armor, a winter coat or standing near a fire keeps you warm. |
| **Gale** | Strong wind blows leaves around, pushes entities and makes arrows drift. |
| **Sandstorm** | A wall of sand and an orange sky in deserts and badlands. Storm goggles let you see through it. |
| **Heatwave** | A hazy amber sky. Standing in the sun costs extra hunger (double in a winter coat), crops grow slower, and there's silent heat lightning at night. |
| **Volcanic Ash Cloud** | Only from an erupting volcano (never in the forecast): the ash cloud drifts downwind, turning the sky dark grey. Ash sifts down and piles up to 3 layers, crops struggle without the sun, and breathing it leaves you weak unless you wear a helmet. |
| **Cold Front** | A long, fast squall line of wind, downpours and thunder racing across the map (a snow squall in the cold). Behind it the air is about 7 °C colder and the wind swings round. Arrives as a storm cell, most often in spring and autumn. |
| **Warm Front** | A wide, slow band of low cloud and drizzle, with milder air (about 4 °C) behind it. Arrives as a storm cell. |
| **Sea Fog Bank** | On cool, calm mornings fog builds up out at sea and rolls in over the coast, then burns off by mid-morning. |
| **Smoke Plume** | Big wildfires send smoke drifting downwind: an orange-brown haze that turns the sun into a dull red ball. |
| **Smog Cloud** | Volcanic smog left behind by an eruption: a yellowish haze with a stinging acid drizzle that weathers copper, withers leaves, slows crops and stings bare heads. |
| **Cold Snap** | Bitterly cold, clear, still air for a day or two, mostly in winter: lakes and rivers freeze over even where they usually don't (they thaw again afterwards), crops get frosted, animals huddle under cover, and white frost covers everything by morning. |
| **Dust Haze** | Dust carried hundreds of blocks downwind of a sandstorm: a tawny haze that dims the sun. It turns the snow pink, leaves a film of **desert dust** on rock and roofs, and rain falling through it comes down reddish-brown. Never in the forecast. |
| **Haboob** | Desert thunderstorms drive a towering wall of dust out ahead of them: dark, orange and roaring, with sand blasting along the ground. Arrives as a storm cell. |
| **Atmospheric River** | Now and then, mostly in autumn and winter, a long, narrow river of tropical moisture comes ashore and stalls: days of heavy, mild rain that floods rivers (heavy snow in the cold). Arrives as a storm cell. |
| **Heat Burst** | At night, a dying thunderstorm can collapse into a sudden gale of hot, bone-dry air, the temperature jumping 10 °C or more for a few minutes. |
| **Fire Cloud** | Over the biggest wildfires a smoke-stained thunderhead builds (a pyrocumulonimbus) and throws dry lightning downwind, starting new fires. |
| **Graupel** | Showers of soft, white snow pellets that bounce and roll, mostly in chilly spring and autumn weather. |
| **Thundersnow** | A snowstorm heavy enough to throw lightning, the thunder muffled by the falling snow (sleet in milder places). |
| **Freezing Drizzle** | Fine drizzle falling through freezing air, glazing the ground and still water with ice. |
| **Sun Shower** | A brief, light shower out of a mostly clear sky with the sun still shining (and often a rainbow). The sky stays blue and bright. |
| **Polar Low** | A small, fierce winter storm that spins up over cold seas: gales, blinding snow and sometimes a clear eye like a hurricane's. It falls apart into an ordinary snowstorm soon after reaching land. Arrives as a storm cell. |
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

**Day length** changes through the year: about 12 minutes of sun at midsummer and 8 at midwinter (10 at the
equinoxes), with the nights the other way round. Noon and midnight stay where they are.

**Snow caps:** the air cools with height, so the mountains wear snow caps whose snowline creeps down in autumn and
winter and climbs back up in spring. In summer, old snow high up turns pink with snow algae (**watermelon snow**).
Up in the mountains the leaves and grass also run on their own calendar: they green up later in spring and turn
earlier in autumn.

If **Serene Seasons** is installed, its calendar is used instead (including the day of the season),
and its own foliage colours take over.

## Storms that roll in
Besides the world-wide weather, **storm cells** form on the horizon and drift across the map with
the wind. Players far apart can have different weather. You can watch a storm approach as a
low, dark cloud deck with shafts of rain hanging beneath it. Inside a cell, everything works the
way rain normally does: riptide, cauldrons filling, fires going out, snow piling up and lightning.

Thunderstorms tower up into tall, anvil-topped clouds you can watch from far away. On **summer
afternoons** (any day in the tropics) the sun heats the ground, the clouds build through the day and
some break into thunderstorms (even hailstorms), dying away by dusk.

## Phenomena
- **St. Elmo's fire:** a few seconds before the storm's lightning strikes, blue sparks crackle around
  the spot and on anyone near it ("your hair stands on end"): time to move. Lightning rods and metal
  tools held up in a thunderstorm spark too.
- **Hail breaks glass:** big hail cracks or smashes glass open to the sky (tinted glass holds), so
  greenhouses take a beating. Needs `mobGriefing`.
- **Mudslides:** after a couple of minutes of pouring rain, soil on steep slopes can slide downhill as
  mud (needs `mobGriefing`).
- **Tornadoes** (supercells): a 70-block funnel that wanders with the wind. Air rushes in along the ground and
  spins up toward the funnel: mobs, items and players are dragged in, whirled round and carried up 20 to 30
  blocks, then flung out of its side (a toss hurts, but a full-strength tornado's fall is capped at about four
  hearts), and the funnel rips up leaves, plants, crops and snow (tag `reikos_weather:tornado_liftable`;
  respects `mobGriefing`). Smaller whirls (landspouts, waterspouts, dust devils) lift you less, or not at all.
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
- **Steam fog:** on bitterly cold, still mornings, unfrozen lakes and rivers steam.
- **Ice breakup:** in the spring thaw, river ice breaks up into **ice floes** that drift downstream
  (you can stand on one, like a boat). Floes that jam up in a narrow stretch back the river up over its
  banks until the jam melts.
- **Waterfall spray:** mist billows up at the foot of waterfalls, with a little rainbow in it when the
  sun is behind you.
- **Breezes:** on calm, sunny days a cool sea breeze blows in off the water in the afternoon, turning
  round into a land breeze at night; in the mountains, valley winds blow uphill by day and cold
  mountain winds slide back down at night, strongest off snow and ice.
- **Microbursts:** a thunderstorm can drop a violent downdraft that slams into the ground and blasts
  outward, flattening crops and stripping leaves in a circle (block damage needs `mobGriefing`).
- **Virga:** in dry climates, grey streaks of rain hang under the clouds and dry up before they reach
  the ground.
- **Rime ice:** in freezing fog, feathers of **rime** grow on the windward side of trees and rocks, more
  of it higher up. It melts when it warms up.
- **Snow rollers:** strong wind over wet, sticky snow rolls it up into **snow rollers**, leaving a bare
  track behind each one.
- **Lake ice:** thick lake ice booms, pings and sings around dawn and dusk, and in hard frosts it cracks
  and buckles up into **pressure ridges**.
- **Valley inversions:** on cold, still, clear mornings, haze and smoke pool in the valleys under a flat
  lid of warmer air.
- **Glowing waves:** on warm coasts at night the breaking waves glow blue, and so does the water round
  anything swimming.
- **Fertile floods:** flood water leaves silt behind, and crops on ground that was flooded grow faster
  for a few days.
- **Icebergs and sea ice:** glaciers that reach the sea calve **icebergs** (you can ride one), bergs
  drift by out in the cold seas, and sea ice breaks up into floes in spring.
- **Coastal erosion:** storm waves slowly wear back natural cliffs until the rock above comes down,
  and drag sand off beaches to drop it as a bar just offshore. Natural blocks only (needs
  `mobGriefing`).
- **Quicksand** lurks on tidal flats, deltas, salt marshes, oasis shores and the beds of dry washes:
  you sink in to your waist and can barely move. Sneak to work your way out.
- **Snowpack:** over the winter, snow builds up into a deep snowpack (deepest high up, in the coldest places and
  among trees) and melts away in spring. Rain falling on deep snow melts it fast and floods the rivers.
- **Needle ice and frost flowers:** on frosty mornings needle ice pushes up out of bare soil (and can heave young
  crops out of farmland), and frost flowers grow on new ice. Both melt in the sun.
- **Cornices:** wind-blown snow builds overhanging lips on ridges, which give way under anyone who walks out on
  them and bring the snow below down (needs `mobGriefing`). **Sastrugi:** strong wind carves open snowfields into
  ridges and furrows.
- **Whirlwinds:** thin **landspouts** under growing storms on the plains, **gustnadoes** along gust fronts,
  **steam devils** over unfrozen water in bitter cold, and **snow devils** on sunny, windy snowfields.
- **Humid heat:** sticky, humid air (jungles, swamps, after rain) makes hot days feel hotter. **Dew** sparkles on
  the grass on clear, calm mornings, and on dry spring days **pollen** hazes the air near woods and films still
  water.
- **Good years and bad:** the climate swings slowly between wet and dry years, shifting the odds of rain, storms,
  hurricanes, heatwaves and droughts. `/rweather query` says which kind of year it is.
- **Rip currents** pull swimmers out from beaches (swim along the shore to escape). **Swell** runs far out ahead of
  hurricanes, **rogue waves** sometimes capsize boats in a big sea, and storms throw **sea foam** up the beach.
- **Tidal bores:** at spring tides, a breaking wave surges up rivers near the sea. Ride it in a boat.
- **Whirlpools** spin up in some narrow straits while the tide is running, dragging boats and swimmers round and
  down; they calm at slack water.
- **Algal blooms:** long, hot, still summers turn the warm seas murky rust-red; the fish die off or leave and
  fishing brings up only weed, until a storm breaks it up. **Pumice rafts** drift on the sea after big undersea
  earthquakes.
- **Seasonal beaches:** winter storms strip sand off beaches and calm summers build it back (needs `mobGriefing`).
- **Puddles**, **wet-looking ground** and **raindrop ripples** on water.

## Local climates
The terrain shapes the weather around you:
- **Valleys** are calmer and collect fog and mist.
- **Peaks and high ground** are windier.
- Land in the **rain shadow** downwind of a mountain range gets less rain.
- In cold biomes, **lake-effect snow** falls downwind of big lakes, even on otherwise calm days.
- A strong wind coming down off a mountain range is a **föhn** (chinook): warm and bone-dry, and the
  snow melts away fast.
- On clear, calm nights cold air pools in valley bottoms, making **frost hollows** several degrees
  colder than the slopes above.

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
- **Heatwaves:** dry grass in deserts and savannas can catch fire (**wildfires**), crops on unwatered
  farmland wilt, and water cauldrons out in the sun dry up.
- **Droughts:** after a dry spell (mostly in summer) the rain stays away for days on end. The grass
  browns, crops wilt, wildfires catch easily even in temperate lands, and shallow ponds dry up to
  **cracked mud** (water next to farmland is left alone). It ends with a storm, and the rain fills the
  ponds again. `/rweather drought` tells you about it.
- **Sinkholes:** after a long downpour, the ground over a cave close under the surface can cave in
  (never near anything built; needs `mobGriefing`).
- **Rockfall:** in the spring thaw, and whenever it's thawing, rocks break off cliffs and pile up as
  scree at the bottom (needs `mobGriefing`).
- **Peat fires:** lightning (or fire) can set **peat** smouldering. A peat fire creeps slowly through
  the bog for a long time, sends up smoke (a big one sends smoke downwind) and burns whatever walks on
  it, until it burns out or water puts it out.
- **Fire whirls:** a big enough wildfire spins up whirling columns of flame that wander about
  spreading the fire, and builds a **fire cloud** overhead.
- **Earthquakes and tsunamis** (both off by default): rare quakes shake the screen, bring rocks down
  off cliffs and break the odd pane of glass (block damage needs `mobGriefing`). A big quake under the
  sea sends a **tsunami** onto the nearest coast a minute or two later: watch for the sea drawing back,
  then run for high ground. The water drains away afterwards.
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
- **Hail drifts:** hail lies on the ground in drifts after a hailstorm, deepest against walls, until it melts;
  on warm days a low fog rises off it.
- **Soil erosion:** downpours wash bare soil and farmland off steep slopes into rills, spreading mud at the
  foot of the slope. Grass holds the soil, and so does level (terraced) farmland. Needs `mobGriefing`.
- **Snow-laden branches** snap under heavy, wet snow and come down with their load, and hurricane and
  derecho winds can tear whole trees out of the ground (**blowdowns**), leaving the root plate standing on
  end. Both need `mobGriefing`.
- **Frost quakes:** in a hard freeze the ground can crack with a loud boom and a jolt.
- **Lake ice:** waves spurting through the edge of shore ice build little **ice volcanoes**, and strong
  onshore winds **shove lake ice** up onto the beach. **Ice discs** turn slowly in cold rivers, and
  **pancake ice** forms on cold seas.
- **Bolts from the blue:** lightning can strike well out from under a thunderstorm, from a clear sky. A
  struck tree has a strip of bark blasted off down to the ground, and a hard strike splits its top.
- **Snow glare:** out on a sunny snowfield without goggles the light gets dazzling.
- **Seiches:** after a gale a lake sloshes from shore to shore, washing up each bank in turn.
- **Longshore currents** carry swimmers, boats and flotsam slowly along beaches, the way the wind slants.
- **Upwelling:** on some coasts, an offshore wind in spring and summer brings cold water up from the deep:
  cooler air on the shore, more sea fog, and better fishing.
- **Lahars:** heavy rain on a volcano's ash-covered slopes sends rivers of mud roaring down its valleys,
  flattening trees and burying the ground. **Pyroclastic flows:** in big eruptions, scalding avalanches of ash and
  gas pour down the volcano's flanks, charring trees and leaving everything buried in ash. (Block changes need
  `mobGriefing`.)
- **Liquefaction:** strong earthquakes turn waterlogged sand near water soft as quicksand for a few minutes and
  throw up sand boils.
- **Black ice:** after a thaw, a hard frost glazes stone and paths with slippery ice by dawn. **Frozen waterfalls:**
  in a long, hard freeze, waterfalls freeze into hanging curtains of ice and thaw again later.
- **Ground blizzards:** strong wind over loose, dry snow lifts it into a blinding river a few blocks deep, even under
  a clear sky. **Katabatic winds:** on clear nights, bitter gales pour down off glaciers and ice fields.
- **Ember spotting:** big wildfires throw embers ahead on the wind and start new fires downwind.
- **Village heat islands:** villages are a few degrees warmer than the country round them, with less frost.
- **Salt spray:** gales off the sea scorch the leaves off the seaward side of coastal trees. **Marine heatwaves:**
  in long, hot summers the sea warms, fish move away and coral bleaches.
- **Bog bursts:** after long, heavy rain a sodden peat bog on a hillside can burst and slide downhill as a slow black
  river of peat. **Playa lakes:** a storm over a dry lakebed floods it with a sheet of shallow water that lingers for
  days, drying faster in the heat (and leaving its salt behind on salt flats).
- **Recovery after fire:** wildfires leave the ground charred; over the growing seasons it recovers, fireweed first,
  then grass and the odd young birch. **Wrack lines:** storms throw kelp and seagrass along the top of the beaches.
- **Icy waters:** gales over a freezing sea glaze the shore in **freezing spray**; under the sea ice in bitter cold,
  **brinicles** grow down to the sea floor; **polynyas**, patches of open water, never freeze over all winter; and
  fast rivers clog with slushy **frazil ice**, with anchor ice on their beds. (Block changes need `mobGriefing`.)
- **Slush and snow crust:** rain on lying snow soaks it into heavy grey **slush** that drags at your feet; if the
  night turns cold again it freezes into a glassy, slippery **crust**, until fresh snow buries it.
- **Dusty snowfall:** snow that falls while desert dust is still in the air comes down tinted, and dust-darkened
  snow melts fast in the sun.
- **Hair ice:** on damp nights just below freezing, fine white hair ice grows on fallen logs, gone by mid-morning.
  **Frozen bubbles:** marsh gas freezes into the ice of boggy lakes in stacks of white discs (**bubble ice**).
- **Fog drip:** in fog, trees comb water out of the air: their leaves drip, and crops and farmland under them stay
  watered.
- **Cave breath:** cave mouths steam in cold weather, and on hot days a cool draught blows out of them (standing in
  a cave mouth cools you down).
- **Mud season:** in the spring thaw and long rains, bare earth and dirt paths churn into sticky **mud** that drags at
  your feet and holds footprints, and dries out again as it warms up. On frosty mornings **puddles freeze over**,
  and the ice cracks underfoot.
- **Indian summers and false springs:** some autumns, after the first frosts, a run of warm, still, hazy golden days;
  some winters, a warm spell that melts the snow and wakes the crops early, then a hard frost that kills the new
  growth. The weather report says when one is on.
- **Midges and mayflies:** midges dance over the water on still, warm evenings, and on spring and early-summer evenings
  the mayflies hatch over the rivers and the fish rise to them (a good time to fish).
- **Singing dunes:** slide down a big dune's steep face in dry weather and the sand booms with a deep, droning hum.
- **Snow bridges and aufeis:** in snowy weather, snow drifts over narrow streams from bank to bank. A **snow bridge**
  looks like any other snow: it holds in hard frost, but gives way under you in a thaw, or if you run or jump onto it.
  In a hard winter, water seeping from the banks freezes layer on layer over the stream ice into thick sheets of
  slippery, milky **aufeis** that last into spring.
- **Tumbleweeds and driftwood:** on windy days in deserts, badlands and dry savanna, **tumbleweeds** come bouncing
  across the open ground and pile up against fences (hit one and it breaks into sticks). Storms wash **driftwood** up
  onto beaches.
- **Old weather signs:** on summer mornings, turreted **castles in the sky** (altocumulus castellanus) warn that the
  afternoon will turn thundery; as a storm comes on, **swallows** skim low over the fields and livestock bunch
  together.
- **Aftershocks and fault scarps:** a big earthquake is followed by a few smaller aftershocks, none in the first
  few minutes and further apart as time goes on (each with its own "aftershock" message), and
  can tear a fresh crack across the land with the ground heaved up along one side (natural ground only, away from
  anything built; needs `mobGriefing`).
- **Hard winters:** in winter, on coasts below mountains, a **bora** pours icy gusts down to the sea; on high, cold
  ridges rime and snow plaster the spruces into white **snow ghosts** until the thaw; and in a hard frost at night,
  trees **split with a crack** like a gunshot. In the spring thaw, lake ice rots into **candle ice** that the wind
  piles ashore, tinkling.
- **Lake whitings:** late in a hot summer, some lakes turn milky turquoise for a few weeks.
- **The thaw:** in a sunny thaw, snow goes first from slopes facing the sun (south) and lingers on shady north-facing
  slopes and in the shadow of trees and cliffs; below the melting snow, **meltwater rills** trickle down the hillsides,
  drying up once the snow above has gone (and freezing to a glaze on frosty nights).
- **Small signs:** on still autumn mornings, **gossamer** (sheets of spider silk over the grass, silver with dew, and
  threads drifting on the air); in a hard frost, snow **squeaks** underfoot instead of crunching; on bitter nights,
  **frost ferns** grow over windows of plain glass (between open sky and a roof) and clear when it warms up, always
  turning back into the same glass; and the wind combs dry sand into **ripples** running across it, which rain smooths
  away.
- **Foxfire and sun cups:** fallen logs in wet woods glow faint green at night with **foxfire**, and in summer sun
  high snowfields melt into a pitted surface of **sun cups**.
- **Supermoons:** every fifth full moon is a supermoon, noticeably bigger and brighter, and its king tide washes a
  block further up the beach.
- **Superblooms:** in a wet year, the desert flowers in spring, with drifts of **desert gold**, **sand verbena** and
  **desert poppies** across the sand that die back in the summer heat.
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
- **Diamond dust:** on bitterly cold, clear, still mornings the air glitters with ice crystals, and
  a **sun pillar** stands straight up from the low sun.
- **Red sprites** flicker high above distant thunderstorms on dark nights, and on some summer nights
  **noctilucent clouds** glow electric blue low in the north long after sunset.
- Squall lines come in under a dark **shelf cloud**, and **mammatus** pouches hang under the anvils of
  big storms.
- After night rain under a full moon, look for a pale **moonbow**; when a low sun shines through thin
  fog, a broad white **fogbow**.
- **Double rainbows:** a fainter second bow outside a bright rainbow, colours reversed, with the darker
  sky of **Alexander's dark band** between them.
- **Glories:** stand above a sea of fog with the sun low behind you and your shadow falls on the fog, a
  huge **Brocken spectre** with a little rainbow ring round its head.
- **Lenticular clouds** sit still as stacked saucers over the mountains in a strong wind.
- **Green flash:** the last sliver of the sun can flash green as it sets into (or rises out of) the sea.
- **Earth's shadow and the Belt of Venus:** at twilight, a dark blue band low in the sky opposite the
  sun, with a pink glow above it.
- **Light pillars** stand up over lanterns, torches and fires on frigid, clear, still nights.
- **Iridescent clouds:** thin cloud near the sun shimmers with pastel colours.
- **The Milky Way** arches across dark, clear, moonless skies (washed out by the moon and by village lights), and
  faint **zodiacal light** rises after dusk where the sun went down.
- **Converging sunbeams** meet again at the point opposite a low sun.
- **Fire rainbows and circumzenithal arcs:** spectral bands in thin ice cloud, low under a high summer sun or high
  over a low one. **Nacreous clouds** glow mother-of-pearl in polar winter twilight.
- **Roll clouds**, rows of **breaking-wave clouds**, smooth **pileus caps** over growing thunderheads, and
  **hole-punch clouds**; at night, **spider lightning** crawls along the underside of storm clouds.
- **Red sky at night:** a fiery sunset comes before fair weather, a red sunrise before rain.
- **Eclipses:** now and then at new moon the moon passes across the sun around noon. In a **total eclipse**
  the day goes dark, stars come out, the corona springs out round the black moon and the horizon glows like
  sunset all round; **ring** and **partial** eclipses leave a ring or a crescent of sun. At full moon a
  **lunar eclipse** turns the moon copper red around midnight. The weather radio warns of them, and
  `/rweather query` gives the date of the next one.
- **Comets:** some years a bright comet hangs low in the twilight for a week, its tails pointing away from the sun.
- **Airglow:** a faint green band of light a little above the horizon on the darkest nights.
- **Fata Morgana:** on calm, clear days over a cold sea, the horizon stretches into towering, shifting mirages.
- **Blue jets and elves** flash high above the biggest storms at night; **STEVE**, a mauve ribbon of light, arcs
  across the sky on some geomagnetic storm nights; and the faint **gegenschein** glows opposite the sun on the darkest
  nights. When the sun comes out after a summer shower, **steam** rises off warm roads and fields.
- **Planets:** Venus (the evening or morning star), Jupiter, Mars and Saturn wander along the sun's path, and Mars
  and the moon meet now and then. **Earthshine** lights the dark part of a crescent moon, and a low moon rises
  orange: deepest of all the **harvest moon**, the full moon in autumn.
- **Dewbows and the heiligenschein:** on dewy mornings a glow rings the shadow of your head on the grass, and a pale
  bow lies on the grass in front of you. On bright days with a rough sea, rainbows stand in the spray off the waves.
- **Banner clouds** stream off sharp peaks on windy days; a **blue haze** hangs over big forests on hot summer days;
  and on still, cold mornings and evenings, smoke from fires and furnaces spreads out in a flat **woodsmoke** layer.
- **Wall clouds and funnel clouds:** a dark, turning wall cloud hangs under the back of a supercell, and now
  and then a funnel cloud snakes down from it and draws back up.
- **Clouds:** real, volumetric clouds drifting on the wind replace Minecraft's blocky ones. Each pixel of sky is
  traced through a layer of 3D cloud, lit by the sun (and shaded by the cloud between it and the sun), so they
  have bright, sunlit tops, grey flat bases and glowing silver edges toward the sun; gold and pink at sunrise and
  sunset and grey under the moon. Thin fair-weather patches, heaped cumulus with cauliflower tops and the thick,
  ragged decks of storms all come out of the same layer, set by the weather around you (a storm on the horizon
  shows as a wall of cloud there), and they slowly change shape as they drift. `volumetricClouds` in the client
  settings picks LOW, MEDIUM (the default; both worked out at half resolution) or HIGH (full resolution, more
  detail), or OFF for the older, flat clouds. With a shader pack on, the flat clouds come back (most packs draw
  their own). (Turn `replaceVanillaClouds` off for Minecraft's blocky ones; Minecraft's "Clouds: Off" hides all.)
- **Cloud streets:** on warm, sunny days, puffy fair-weather clouds build through the morning, and in a steady
  breeze they line up in long rows along the wind. When one drifts over the sun, the light dims for a moment.
- **Asperitas:** around some thunderstorms the cloud base heaves and rolls overhead like a rough sea seen from below.
- **Chinook arch:** when a föhn wind pours down off the mountains, a band of clear sky arches over them under a
  sharp-edged deck of cloud.
- **Echoing thunder:** among mountains and valleys, thunder comes rolling back off the slopes round you.
- **Cloud waterfalls:** fog and low cloud pushed against a ridge spill over the crest and tumble down the far side.
- **Green storm skies:** the daylight turns an eerie green under big hailstorms.
- **Mares' tails and mackerel skies:** a day or so before the rain, high cirrus streams in, in fine hooked streaks,
  then the rippled rows of a mackerel sky, then a thin milky veil that puts a halo round the sun.
- **Scud:** ragged shreds of low cloud race along under a storm's dark base.
- **Hail fog:** after a hailstorm, a low fog lies over the drifts of hail until they melt.
- **Rare halos and snow plumes:** on the best halo days, the parhelic circle (a white ring round the whole sky at the
  sun's height) and the faint 46-degree halo; on windy winter days, snow streams off the summits in long plumes.
- **The sun's path:** the sun no longer passes straight overhead. The sky leans toward the south as it would some way
  north of the equator (40 degrees by default, set with `sunLatitude`), so the noon sun stands high at midsummer
  and low at midwinter, and the moon and stars wheel round a pole star high in the north. It still rises due east
  and sets due west on time, so daylight and darkness are unchanged. The halos, rainbows, sunbeams and other sky
  sights follow the sun wherever it is. It turns itself off while a shader pack is in use (shader packs place the
  sun themselves).
- **Coronas:** soft pastel rings tight round the moon (and, lost in its glare, the sun) shining through a thin veil of
  cloud.
- **Moon dogs and volcanic twilight:** bright spots either side of a bright moon on icy nights; and for a couple of
  weeks after a big eruption, purple twilights and a pale ring round the sun.

## Volcanoes
Volcanoes rise out of new land now and then: a cone of basalt, tuff and blackstone up to 70 blocks
high, with a bowl-shaped crater and a lava lake at the top. Out at sea they form volcanic islands.
Old lava flows streak the flanks, **steam vents** hiss on the upper slopes, crusted with yellow
**sulfur**, and a dusting of **volcanic ash** lies lower down. They never form on top of villages.

A volcano sleeps for days at a time (only while someone is within about 1,000 blocks), then:
- **Rumbling** (3 to 5 minutes): earthquakes shake the ground (and your view, scaled by the screen
  effect strength setting), smoke rises from the crater and a deep rumble carries for a long way.
  This is your warning.
- **Eruption** (5 to 8 minutes): a blast you hear late if you're far away, lava fountains, and a
  towering **eruption column** of ash that leans downwind, glows at the base and can be seen from
  well beyond your render distance. **Lava bombs** arc out of the crater and burst where they land,
  **volcanic lightning** strikes the upper slopes, and lava **pours over the rim** in streams down the
  mountain. The **ash cloud** drifts downwind as the Volcanic Ash Cloud weather.
- **Cooling** (about 3 minutes): the smoke thins out, and the lava streams crust over into **lava
  fields** of basalt, with some blackstone, magma and the odd bit of obsidian.

Lava streams and bomb craters need the `mobGriefing` gamerule. The barometer, weather radio and
`/rweather query` name the nearest volcano you know of and what it's doing, the radio warns you when
one starts rumbling or erupting, and the radar shows volcanoes as triangles.

**Finds:** volcanic ash is a fertilizer (use it on a crop like bone meal, or place it in layers like
snow); sulfur crust drops **sulfur**, which makes gunpowder with charcoal and bone meal; and steam
vents are warm to stand near but scald you unless you sneak (Silk Touch picks one up).

Active volcanoes have **lava tubes** running down under their flanks (look for skylights where the
roof has fallen in), and volcanic islands have **black sand** beaches. As an eruption dies down it
leaves **volcanic smog** drifting downwind (see Smog Cloud above).

## Landforms
New parts of the world also get landforms shaped by the weather. None of them form on top of a
village or a volcano, or on top of each other: where two would overlap, the bigger one forms and
the smaller one doesn't. (A landform placed with `/place structure` goes where it's put.)
- **Hot springs:** white sinter with steaming pools ringed in orange and yellow, bubbling mud and
  hissing vents. Soak in a pool to warm up and slowly heal. **Geysers** on little mounds erupt every
  few minutes, throwing anything above them high into the air (a huge lift for a paraglider); a
  comparator reads a geyser's eruptions.
- **Crater lakes:** long-extinct volcanoes, grassy and wooded, with a deep lake in the crater.
- **Meteor craters:** old, grassed-over impact craters with a raised rim (sometimes a pond in the
  bottom) and the meteorite buried under the middle.
- **Salt flats:** dead-flat dry lake beds in deserts, crusted white with **salt crust** (mine it for
  salt). When a storm passes over they flood into a shallow lake that mirrors the sky, then dry back
  out. On hot days a **mirage** shimmers across them (and any flat desert) like water.
- **Glaciers:** tongues of packed and blue ice flowing down cold mountains, with **crevasses** cut
  across them, many hidden under a bridge of powder snow. In summer the lower end melts back a little.
  Some have a blue **ice cave** running into their foot, and where one reaches the sea it calves
  icebergs.
- **Tidal flats:** wide mud flats at the sea's edge, level with the water: every high tide washes over
  them. Tide pools hold seagrass, sea pickles and **suspicious sand** to brush for treasure.
- **Dune fields:** rows of tall dunes across the prevailing wind, each with a long, gentle slope and a
  steep face. In sandstorms the sand blows off the crests and the dunes creep downwind.
- **Arroyos:** dry, sandy riverbeds winding across deserts and badlands. When a real storm breaks over
  one, a **flash flood** races down the channel, and drains away soon after the rain stops.
- **Cenotes:** round, sheer-sided sinkholes in the jungle with deep, clear water at the bottom, hung
  with vines, roots and stalactites.
- **Oases:** a pool in a hollow among the dunes, ringed with grass, reeds and palm trees.
- **Waterfalls:** streams pouring off cliffs into a deep plunge pool, in a notch cut back into the
  hillside.
- **Basalt columns:** a coast of polygonal basalt columns stepping down into the sea.
- **Sea stacks:** layered rock pillars standing off rocky coasts, notched at the waterline and capped
  with turf, and a natural **arch** with the sea running through it.
- **Atolls:** in warm seas, a ring of coral reef round a shallow sandy lagoon, with islets and palms.
- **Fjords:** long, deep, sheer-walled inlets of the sea cut into cold mountains.
- **Erratics and moraines:** big boulders of out-of-place rock and long rubble ridges left by the ice
  sheets. Glaciers have a moraine round their foot too.
- **Peat bogs:** wet, mossy flats over deep **peat** (a slow-burning furnace fuel), with dark pools,
  cotton grass and cranberries.
- **Travertine terraces:** white rims holding a staircase of warm, steaming blue pools down a hillside.
- **Tufa lakes:** salty lakes with a salt-crusted shore and knobbly pale towers standing in the shallows.
- **Hoodoos:** banded rock spires with caprocks, and **rock arches**, in deserts and badlands.
- **Slot canyons:** deep, narrow, corkscrewing cracks in banded sandstone, with flood logs jammed high
  overhead, and a deadly flash flood when a storm breaks over them.
- **Limestone pavements:** bare **limestone** slabs split by deep cracks with ferns and moss in them.
- **Tower karst:** sheer limestone towers rising out of the jungle, hung with vines and tufted with trees.
- **Oxbow lakes:** crescent lakes left behind by rivers, with reed beds at the silted-up ends.
- **Braided rivers:** shallow channels weaving across a wide gravel plain in cold lands, with drift logs
  stranded on the bars.
- **Deltas:** a fan of muddy marsh and channels where a river meets the sea, with raised banks, sand bars
  and quicksand. The high tide creeps over the marsh.
- **Cirques:** armchair-shaped hollows in mountainsides with a sheer headwall, scree and a tarn.
- **Kettle lakes:** deep, round ponds among gravel mounds (kames), left where buried ice melted.
- **Drumlins and eskers:** swarms of streamlined hills all lying the same way, and a long winding gravel
  ridge.
- **Permafrost:** tundra netted with ice-filled polygon cracks, ice-cored **pingos** split open at the
  top, and thaw lakes.
- **Chalk cliffs:** sheer white cliffs banded with flint over a wave-cut platform, with sea caves, and a
  **blowhole** on top that booms and spouts spray (and anyone standing on it) when the waves surge in.
- **Barrier islands:** long, thin islands of sand and dunes off a low coast, with a lagoon behind and an
  inlet through them.
- **Salt marshes:** flat coastal marsh threaded with winding creeks, with salt pans on the high marsh;
  the low marsh floods at every high tide.
- **Cinder cones:** steep cones of black and red cinders with a crater, breached by a lava flow.
- **Maars:** broad explosion craters sunk into the land, ringed with tuff and holding a deep lake.
- **Mud volcanoes:** little cones of grey mud with bubbling pools on top, among cracked mud flows.
- **Termite mounds:** earthen spires and domes dotted across the savanna.
- **Karst:** **tiankeng** (giant sinkholes with a forest on the floor), **blue holes** in shallow tropical seas,
  **stone forests** of limestone pinnacles, **natural bridges** over streams, and **sinking streams** that vanish
  into a cave mouth and come out at a spring further down.
- **Volcanic:** **volcanic necks** of columnar basalt, **spatter cones** along a fissure in a young lava field,
  **obsidian flows** crusted with pumice, and **pillow lava** on the sea bed.
- **Ice and cold:** **nunataks** poking through an ice sheet, **rock glaciers**, frozen peat **palsas**, frost-sorted
  **stone circles**, and fields of snow spikes (**penitentes**) on high peaks.
- **Desert:** **yardangs**, **inselbergs**, **mesas and buttes**, **mushroom rocks**, **sailing stones** with their
  tracks, **fairy circles**, and **tinajas** (rock potholes that fill after rain and dry out again). Dune fields now
  come as rows, crescent barchans, long seif ridges or star dunes.
- **Mountains:** **horns** with knife-edge aretes, **talus cones**, **alluvial fans**, **avalanche chutes**,
  **landslide scars**, **hanging valleys** with waterfalls, and turquoise **glacial lakes** behind moraines that can
  burst in summer and flood the valley below. Glaciers now have moulins and icefalls.
- **Rivers:** **river terraces**, **rapids** with potholes, and deep **gorges**.
- **Coasts:** **sand spits**, **tombolos**, **shingle beaches**, **raised beaches**, **rias**, and tidal
  **whirlpools** in narrow straits.
- **Volcanic country:** **calderas** with a lake and a new cone inside, hot turquoise **acid crater lakes**
  crusted with sulphur (their vents burn blue at night), **young lava fields** with tumuli, hornitos, lava tube
  skylights and lava trees, and **rift valleys** stepping down between fault cliffs.
- **More karst:** **doline fields**, jungle **cockpit karst**, and **poljes**: walled limestone basins that flood
  into lakes in the wet seasons and drain in summer.
- **Mountains and ice:** **paternoster lakes** stepping down a glacial valley, ice-smoothed **roches moutonnées**,
  and needle-sharp granite **aiguilles**.
- **Colourful deserts:** striped **rainbow mountains**, deep **canyons** streaked with desert varnish, swirling
  sandstone **wave rocks**, white **gypsum dunes** (**gypsum sand**), **coppice dunes** held round bushes, **salt domes**
  of **rock salt** with salt glaciers, and **crusted flats** of living desert soil crust.
- **Coloured waters:** **pink lakes**, **spotted lakes** of many-coloured pools, and banded **acid pools**.
- **More shores:** **shell**, **green (olivine)** and **pink** sand beaches, **Moeraki boulders**, **parabolic dunes**,
  **ghost forests** of dead trees, and **stromatolites** in warm, salty shallows.
- **Woods and meadows:** **fairy rings** of mushrooms, and **vernal pools** that fill in autumn, winter and spring
  and dry out in summer.
- **More volcanic country:** **kipukas** (islands of old forest left in a lava flow), **volcanic dikes** (long walls of
  dark rock), and **steaming ground** that snow never settles on.
- **The deep sea:** **hydrothermal vents** (black smokers) and **brine pools**, lakes lying on the sea floor.
- **The far north:** stunted, wind-bent **krummholz** at the treeline, and **thermokarst slumps** where permafrost thaws.
- **Rivers and wetlands:** **goosenecks** (deep river loops cut into rock), **floodplains** with levees and backswamps,
  **beaver ponds**, **patterned fens**, and **cypress swamps** with their knees.
- **Dry lands and coasts:** **loess cliffs** and gullies, **Mima mounds**, honeycombed **tafoni** sea cliffs, and
  **fringing reefs** with spurs and grooves.
- **Mountains and ice:** **tepuis** (sheer table mountains with long waterfalls), **fumarole ice towers** over steam
  vents on snowy peaks, and **solifluction lobes** creeping down tundra slopes.
- **Bent and broken rock:** **hogbacks and flatirons**, **folded cliffs** with their layers bent into arches, **rift
  fissures** full of crystal-clear water, **inverted channels** (old riverbeds left standing as ridges), and the
  stepped **ancient shorelines** of a long-gone lake.
- **Water in the hills:** **hanging gardens** on seeping canyon walls, **landslide lakes** drowning mountain valleys
  behind old rockslide dams, and **fairy pools** stepping down a mountain stream, with little waterfalls pouring from
  one clear pool into the next.
- **Ancient things:** **petrified forests** of stone logs (**petrified wood**), **fossil beds** with great bones
  weathering out (brush the gravel below), and **nurse logs** and root mounds in old forests.
- **Springs and coasts:** deep, vivid **blue springs** welling out of a cave at the foot of a cliff, streams pouring
  straight off **sea cliffs**, swarms of low, ice-smoothed **skerries** off cold coasts, and **dune slacks**: damp,
  green hollows with pools between coastal dunes.
- **Fire, cloud and frost:** **burning coal seams** that have smouldered underground for centuries, baking the rock
  into red **clinker**, with smoke seeping from hot cracks in the **smouldering ground**; misty **cloud forests**
  high on jungle mountains, dripping with moss, ferns and vines; the horseshoe cliffs of ancient **dry falls**, with
  lakes in their old plunge pools; and boulder-strewn **frost-shattered summits**. Glaciers now carry **glacier
  tables**: boulders on pedestals of ice.
- **Fire, crystal and deep water:** **crystal caves** of giant **selenite** beams deep under desert mountains;
  burning **gas craters** (fire on **gas vents** that never goes out); steep, steaming **lava domes**; **boiling
  rivers** that steam through the jungle and scald anyone who falls in; **ice shelves** ending in towering ice cliffs
  along frozen coasts; **seamounts** rising from the deep ocean floor almost to the surface; and **submarine canyons**
  cut deep into the sea floor. Glaciers now hold blue **meltwater lakes** in summer, and many **waterfalls** have a
  cave behind them you can walk into.
- **Bogs, caves and rust:** **glowworm grottoes**, stream caves in limestone hills whose roofs glow with thousands of
  tiny blue lights; **red rivers** running blood-red with iron and acid from rusty **ochre** hillsides, with nothing
  growing along their banks; **string bogs**, long curving ridges of peat with narrow pools between them in flat
  northern country; and **earth hummocks**, cold grassland heaved by frost into countless little mounds. Peat bogs now
  have **quaking mats** of floating moss that give way if you run or jump onto them, and glaciers have curved
  **ogive** bands below their icefalls and **hidden crevasses** bridged over with snow.
- **Stone, ice and shore:** **desert pavements** (gibber plains of dark, wind-polished pebbles, with wind-carved
  **ventifacts**); **glacier mice**, balls of moss creeping over the lower ice of glaciers; and rocky shores banded
  with **black and orange lichen** above the waterline.
- **Silk, frost and sand:** **terracettes**, narrow step-like tracks running along steep grassy hillsides; **beach
  cusps**, evenly spaced crescent scallops along the edge of sandy beaches; and on the lower ice of glaciers,
  **cryoconite holes** melted into the ice by dark grit, and **dirt cones** of ice shielded from the sun by a coat of it.
- **Tar pits:** natural asphalt seeping up into sticky black pools in plains, savannas and deserts, ringed with a
  hard, cracked crust and full of the bones of animals caught long ago; some lie hidden under a skin of rainwater.
  Wade in and you sink to the knees and stick fast: you can barely jump, and the only way out is to wade to the
  edge and heave yourself up and over it. Tar and asphalt can both be dug up for building.

## The Nether and the End
The other dimensions get weather of their own.
- **Nether:** choking **ash storms**, harmless glowing **ember falls**, and **fire rain** that sets
  exposed creatures and flammable blocks alight.
- **End:** violet **void storms** with gusts every few seconds that push you toward the edge of the island.

## Weather radar
The **handheld radar** and the **weather radar** block open a radar scope. It shows storm cells
colour-coded by type, which way they're moving, when they'll reach you, any tornadoes in range and
known volcanoes.
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
| **Seismograph** | copper, paper, redstone, amethyst shard, smooth stone slabs | Redstone signal from volcanic tremors: weak while a volcano within 1,024 blocks rumbles (rising as the eruption nears, and the closer you are), up to 15 during an eruption. An early warning. |
| **Volcanic Ash** | falls from ash clouds | Fertilizes crops like bone meal, or place it in layers. 2 ash make a bone meal. |
| **Sulfur** | mine sulfur crust | Sulfur + charcoal + bone meal make 2 gunpowder. |
| **Geyser** | Silk Touch one from hot springs | Erupts every few minutes; comparators read 15 while it erupts. |
| **Salt Crust** | mine it on salt flats | Drops salt. |
| **Black Sand** | volcanic island beaches | Decorative sand; smelts into glass. |
| **Gypsum, Shell, Olivine and Pink Sand** | gypsum dunes and coloured beaches | Decorative sands; smelt into glass. |
| **Rock Salt** | salt domes | A pale, glassy salt rock for building. |
| **Steaming Ground** | geothermal ground | Warm, stained earth that steams and melts snow off itself. |
| **Fireweed** | grows back on burnt ground | A magenta flower; makes magenta dye and goes in a flower pot. |
| **Charred Soil** | left by wildfires | Burnt ground that recovers over the seasons. |
| **Beach Wrack** | washed up after storms | Kelp and seagrass along the tideline; gather it for kelp. |

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
A "Reiko's Weather" advancement tab with 105 entries, including Storm Chaser, We're Not in Kansas
Anymore, Practically Perfect (an umbrella glide), Northern Lights, Whiteout, Four Seasons, Bottled
Lightning, Eye of the Storm, Make a Wish, Once in a Blood Moon, Skating on Thin Ice, Riding the
Thermals, Fishing in Troubled Waters, Supercharged, Straight-Line Winds, Hot Spot (stand on a
crater's rim), Ring of Fire (witness an eruption), Hair-Raising, Old Faithful, Hot Tub, Mind the
Gap, In the Eye (of a hurricane), Floe Rider, Flash Flood, Parched, Bitter Cold, Field Geologist
(visit five kinds of landform), Downburst, Firenado, Shaken, Not Stirred, Iceberg, Right Ahead!,
A Sinking Feeling, Thar She Blows, Caught in a Rip, Surf's Up (the River), Into the Maelstrom, Dam
Busters, Totality, The Great Comet, The Fox's Wedding, A Bolt from the Blue, Timber!, Cryoseism, Ice Shove,
Back and Forth, Rich Waters, Mud River, Pompeii, Narrowly, Solid Ground?, Off the Ice, Frozen Falls, Hot Water, Black River,
A Lake in the Desert, Finger of Death, The Desert in Bloom, Hot Foot, Head in the Clouds, True Blue, The Door to Hell, Cave of Crystals, Too Hot to Swim, Sea Mount, Stars Underground, River of Rust, Walking on Water, Gone With the Wind, Gibber Plain, Glacier Mouse, Foxfire, Ice Chimes, Combed by the Wind and Stuck in the Tar.

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
  `glaze_water`, `glaze_ground`, `floods`, `heat_lightning`, `tornadoes`, `waterspouts`, `debris`,
  `ash_layers` (0-8), `acid` and `precipitation` (rates for `hail`, `sleet`, `downpour`, `snow`,
  `blizzard`, `sand`, `dust`, `leaves`, `ash`).
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
- **Iris / Oculus:** the heat shimmer and the volumetric clouds step aside while a shader pack is on (shader
  packs run their own post-processing, and most draw their own clouds).
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
| `/rweather storm <type> [here]` | op | Start a storm cell upwind (or right overhead). Also takes `sea_fog`, `wildfire_smoke`, `volcanic_smog`, `volcanic_ash` and `dust_haze` |
| `/rweather drought` | everyone | Whether there's a drought, and how far into it we are (`/rweather drought start [days]` or `end`, op) |
| `/rweather next` | op | Skip to the next forecast weather |
| `/rweather tornado [pos] [tornado\|waterspout\|dust_devil]` | op | Spawn a whirlwind |
| `/rweather meteor [pos]` | op | Bring a meteor down at a position (or near you) |
| `/rweather wind <from>` | op | Set where the wind blows from (`n`, `ne`, `e`, ... `nw`) |
| `/rweather volcano` | everyone | Known volcanoes within 2 km and what each is doing |
| `/rweather volcano create [pos]` | op | Raise a volcano at a position (or where you stand); handy for worlds made before volcanoes |
| `/rweather volcano wake\|erupt\|calm` | op | Start the nearest volcano rumbling, set it erupting, or cool it down |
| `/rweather volcano lahar\|pyroclastic` | op | Send a lahar or a pyroclastic flow down the nearest volcano |
| `/rweather quake [strength] [aftershocks]` | op | An earthquake centred where you stand (0 to 1; 0.5 by default); add `aftershocks` for a natural quake's aftershocks to follow |

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
  volcanoes (generation, eruptions and how often, lava flows, lava bombs, ash fall, lava tubes, smog
  and acid rain), afternoon storms, sea fog, wildfire smoke, lightning warnings, hail breaking glass,
  mudslides, droughts (and drying ponds), ice breakup, desert dust, sinkholes, rockfall, peat fires,
  breezes, microbursts, haboobs, fire whirls, atmospheric rivers, heat bursts, rime, snow rollers,
  lake ice, fertile floods, earthquakes and tsunamis, coastal erosion, icebergs, quicksand, the snowpack, humid
  heat, wet and dry years, whirlwinds, needle ice, cornices, sastrugi, rip currents, swell and rogue waves, tidal
  bores, algal blooms, pumice rafts, seasonal beaches, glacial outburst floods,
  each landform's generation and whether landforms change with the weather, the almanac
  on first join, and Tough As Nails.
- `config/reikos_weather-client.toml`: particle density, fog, sky tinting, storm clouds (and how far
  out they're drawn), wet ground, mist, rainbows, auroras, heat lightning, seasonal colours, falling
  leaves, sounds (delayed thunder, muffling indoors, roof rain and wind whistle), birdsong and
  crickets, breath, footprints, screen raindrops, heat shimmer, dripping leaves, shooting stars,
  halos and sunbeams, hoarfrost, fireflies, blossoms, bird flocks, whitecaps, towering storm clouds,
  sea fog banks, smoke columns, diamond dust, St. Elmo's fire sparks, mirages, steam fog, waterfall
  spray, dusty rain, red sprites, noctilucent clouds, shelf clouds and mammatus, moonbows, fogbows,
  virga, valley inversions, singing ice, glowing waves, double rainbows, glories, lenticular clouds,
  the green flash, twilight bands, light pillars, iridescent clouds, dew, pollen, the Milky Way, converging
  sunbeams, sky arcs, cloud forms, nacreous clouds, spider lightning, red skies, algal bloom water,
  accessibility
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
./gradlew runGameTestServer        # run the automated in-game tests (in their own folder, run/gametest)
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
              StormDebris, Fronts, Convection (afternoon storms), SeaFog, FireTracker (wildfire smoke),
              LightningWarnings (St. Elmo's fire), HailGlass, AcidRain, Breezes, Downdrafts (microbursts, haboobs,
              heat bursts), AtmosphericRivers, Frostwork (rime, snow rollers, pressure ridges), Earthquakes, Coasts
              (erosion, icebergs, fertile floods), WinterGround (snowpack, needle ice, cornices, sastrugi), Seas (rip
              currents, swell, tidal bores, algal blooms, pumice, beaches), ClimateYears
  temperature/ Temperature (air and felt temperature), TemperatureEffects (body temperature, cold and heat)
  farming/    Greenhouses, crop frost and food spoilage
  nature/     Autumn leaf piles; wild crops, earthworms, weather fishing and leaf-pile landings (NatureEvents)
  sky/        Meteors, blood moons
  volcano/    VolcanoShape (the cone's geometry), VolcanoBuilder, VolcanoStructure and VolcanoPiece (world
              generation), VolcanoManager (discovery, the eruption cycle, lava flows, ash clouds, seismic signal)
  landform/   LandformStructure and LandformPiece (one structure type for every landform), hot springs, crater
              lakes, meteor craters, salt flats, glaciers, tidal flats, dunes and 30-odd more; Terrain and PlanContext
              (shared helpers); LandformManager (weather changing them)
  otherworld/ Nether and End weather
  entity/     TornadoEntity (tornado, waterspout, dust devil), ball lightning, cloud seeding rocket, meteor,
              lava bomb
  block/      Weather station, tornado siren, rain barrel, puddles, ice glaze, icicles, radar, vane, windmill, altar,
              tent, lightning battery, forecast board, storm probe, leaf pile, fallen branch, rice, kale, icebox,
              volcanic ash, steam vent, sulfur crust, seismograph, geyser, salt crust, rime, snow roller, quicksand,
              blowhole, petrified wood, pumice, whirlpool, sea foam, needle ice, frost flowers
  item/       Barometer, umbrella, thermometer, handheld radar, rocket, drinks, salt, paraglider, weather radio,
              rake, ice saw, almanac, charged tools and gear events
  compat/     JEI, Jade and WTHIT plugins, Tough As Nails, Curios, shader detection, Distant Horizons
  mob/        Weather-themed spawns, animals seeking shelter, WeatherCreatures (villagers, polar bears, wool, meteorologist trades)
  client/     Smoothed visuals, particles, fog/sky/rainbow/aurora/halos/sunbeams/blood moon, storm cloud deck, wet
              ground, forecast and temperature HUD, radar screen, seasonal colours and hoarfrost, Nether/End visuals,
              block renderers, ambient and wildlife sound, shooting stars, breath, footprints, screen raindrops, heat
              shimmer, rough seas, fireflies, blossoms, worms, bird flocks, paraglider, volcano plumes and tremors,
              towering clouds and fog banks (PuffRenderer), diamond dust, mirages, St. Elmo's fire, SkySights
              (sprites, noctilucent clouds, moonbows, fogbows, inversions), GroundOptics (glories, light pillars),
              ShoreAndIce (glowing waves, singing ice), AirAndWater (dew, pollen, foam, light pollution), CloudForms,
              the almanac screen (guide/),
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
