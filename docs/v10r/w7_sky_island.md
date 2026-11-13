**sky_island report** — the whole section is in and compiles (`check-module.sh settlements` → TUDO OK, `check-module.sh city_arcana` → TUDO OK, `check-tree.sh sky_island` → COMPILOU OK). Nothing was run in game or in a JVM; only the island shape maths was checked, in a Python port.

This stage covers his message 1 (SkyLand). His message 2 (pausing works) is `city_pause` in stage 2.

## Files touched
All Java under `src/main/java/com/renanpontes/isekaimahou/feature/settlements/`.
- **New:** `SkyCity.java`, `item/SkyCoreItem.java`, `tools/gen/settlements_w7_sky_island_lang.py`, `tools/gen/settlements_w7_sky_island.py`.
- **Generated:** `textures/item/sky_core.png`, `models/item/sky_core.json`, `data/isekai_mahou/recipe/sky_core.json`.
- **Edited (small, pointed):** `Settlement`, `SettlementService`, `SettlementsContent`, `SettlementsModule`, `SettlementView`, `SettlementCommands`, `CityDebugCommands`, `CityPlanning`, `CityPace` (`working`), `build/ConstructionEngine`, `build/CityTerrain`, `network/SettlementsNetwork`, `client/FoundingScreen` (one line).
- **Lang:** `src/main/lang/settlements/{pt_br,en_us}.json`, 28 keys added, none removed or changed.

## New ids
- **Item:** `isekai_mahou:sky_core` (Núcleo Celeste / Sky Core), in the World creative tab.
  - Recipe: phantom membrane ×2, feather ×2, amethyst shard ×2, `essence_wind` ×2, `mana_crystal` ×1.
- **Terrain type ids:** `ground`, `skyland` (`SkyCity.GROUND` / `SkyCity.SKYLAND`).
- **Optional Settlement NBT:** compound `Sky` {`Land`, `Goal`, `Ring`, `Idx`, `Start`, `Skip`, `Lift`}. Absent means a ground city.
- **Hint:** `city_sky_found`.
- **Commands (op):**
  - `/mahou settlements found sky [style]` and `/mahou city sky found [style]` (same thing; give the Charter and Core if missing).
  - `/mahou city sky` (state), `sky finish`, `sky grow`, `sky regen`, `sky tp`.

## What it does
- **Founding:** the Charter is still used on the ground. The Heart is placed at `clamp(highest terrain within ±96 + 64, 160, 208)`; founding is refused if that leaves less than 48 blocks of clearance.
  - Distance rules are checked against the island's box in the sky, extended down to bedrock for towns so an island never shades another city or village.
  - Sky structures whose id contains `sky_island` or `floating`, and existing blocks in the island volume, also refuse it.
  - It consumes the Charter and one Sky Core.
- **Island:** a rounded square covering the territory plus at least 14 blocks, with a noisy rim, a flat top at plaza height and an inverted-cone belly with stone spikes. Each of the 7 styles has its own layer palette.
- **Gradual generation:** ring by ring from the Heart, 320 blocks per tick taken from the shared construction budget, loaded chunks only, cursor saved.
  - The founder is teleported to the plaza and the first two builders arrive when ring 12 is done (about 2 s).
  - The first island is about 265k blocks, roughly 40 s.
- **Never destroys:** only air, fluids and replaceable plants are filled. Land that already existed only gains blocks underneath when the island grows.
- **Works wait for land:** a work whose lot has no land yet does not start, does not accrue days and does not hold a builder. Its Obras row reads "Aguardando a terra se formar". Foundations never go below the island's underside, and the skirt and stair phases are skipped.
- **Growth:** `CityPlanning.expand`, `/mahou city radius` and `sky grow` all start it (295k–770k blocks, about 45 s to 2 min).
- **Shadow:** natural monster spawns are blocked on open surface under the footprint, day and night; caves are unaffected.

## APIs for stage 2 (all static in `SkyCity`)
- **Is it a sky city:** `isSky(s)` or the field `s.sky`.
- **Height:** `topY(s)` is the block you stand on (`heart.getY() - 1`); feet are at `topY + 1`. Altitude for display is `s.heart.getY()`.
- **Land at a column:** `hasLand(s, x, z)` — world coordinates, pure maths, always true for ground cities.
  - `landReady(s, cell)`, `landReady(s, cx, cz, half)`, `waitingLand(s, project)`.
  - `floorY(s, x, z)` gives the lowest island block of the column.
- **Footprint:** `inFootprint(s, x, z)` is the planned shape of the current radius.
  - `inside(heart, radius, dx, dz)` and `thickness(heart, radius, dx, dz)` take offsets from the Heart and are safe on the client.
  - `reach(radius)` is the maximum rim distance on an axis; `box(heart, radius)`; `shapeRadius(s)`.
- **Rim / pier:** `rimPoint(s, Direction)` or `rimPoint(s, stepX, stepZ)` returns the feet position of the last column with land, or null.
  - `rimPlanned(s, Direction)` is where the rim will be once generation ends.
  - `pierSide(s)` is the plaza's front and is stable; `pierPoint(s)` is the rim point on that side.
- **Generation state:** `generating(s)`; `percent(s)` returns 0–99 while forming, 100 when complete, -1 for a ground city.
- **Hook:** `SkyCity.onLand((sl, s, oldRadius, newRadius, finished) -> …)`.
  - It fires at the start of a growth (`finished=false`) and when all the land is in place (`finished=true`).
  - The first island has `oldRadius = 0`.
- **Found packet:** `Found(BlockPos pos, String style, String name, int rot, String terrain)`. `pos` is always the ground point.
  - Server side: `SettlementService.found(sp, at, style, name, rot, terrain)`, `foundingProblem(sp, pos, terrain)`, `findSkyCore(sp)`.
  - `SkyCity.heartY(sl, groundPos)` gives the altitude for the preview, or -1.
  - `FoundingScreen` always sends `ground` for now, and the `found` probe still checks ground rules only.
- **Client state:** the city NBT carries `Sky`, `SkyY`, `SkyPct`, `SkyLand`, `SkyGoal`, `SkyRing`, `SkySkip`; each project carries `NoLand`.
- **Lang keys ready to reuse:** `sky.terrain.ground`, `sky.terrain.skyland`, `sky.wait.land`, `sky.found.announce`.
- **For city_pause:** "this work is skipped" is decided in `CityPace.working` (days) and `ConstructionEngine.build` (crews).

## How to test
1. `/mahou admin login <senha>`, stand on open ground, `/mahou city sky found oriental` (any of the 7 styles).
2. Watch the land form; you are lifted after about 2 s. `/mahou city sky` shows ring and percent.
3. `/mahou city sky finish`, then `/mahou city finish` to complete the plaza.
4. Plan houses, `/mahou city level 3`, queue a wall, `/mahou city finish`.
5. `/mahou city sky grow` and plan a lot in the new ring while it forms: the row should read "Aguardando a terra se formar".
6. Go down (creative flight) and `/mahou city sky tp` to come back.
7. `/time set night` under the island: no monsters on open ground.
8. Restart the server mid-generation: it should resume.
9. Found a ground city elsewhere: it should behave exactly as before.

## Not done / not verified
- **No in-game run:** island look, speed, lighting cost, the lift, and walls, renewal and monuments on the island are unverified.
- **No UI:** a sky city can only be founded by command until `sky_ui` adds the choice.
- **No access:** the only ways up are the founder's lift and `sky tp`.
- **Not adapted (sky_access):** NPCs can fall off, and raids, caravans, war and tradesman spawns still read ground heightmaps (`CityRaids`:269, `CityTrades`:656, `Conquest`:203, `CityDecor`:179/255).
- **Outer rings** only generate while their chunks are loaded and the Heart is ticking; the cursor retries skipped columns every 10 s.
- **Radius steps** 3→4, 5→6, 7→8, 9→10 and 11→12 add no land, because the territory size is the same; nothing is announced for them.
- **Finishing at once:** `/mahou city finish` and `sky finish` place all the remaining land in one tick, which will hitch on a big island.
- **Style change (Reforma)** does not re-skin the island.
- **Heart removed by command:** the island stays but loses the no-spawn rule.
- **Free-plot preview** colours (`CityPlots.terrainAt`) still read heightmaps.
- **Recipe balance** is untested.

## PEDIDOS
- `docs/ARCHITECTURE.md`: add `SkyCity` (APIs above), item `sky_core`, hint `city_sky_found`, the new commands and the `terrain` field of `settlements_found`.
- Journal/guide: a short entry on the Sky Island and the Sky Core recipe.
- `item.isekai_mahou.founding_charter.desc` still says "Reino, Desértico ou Nórdico" (older text); I did not change it.