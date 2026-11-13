**city_build report** — compiles clean (`check-module.sh settlements` → TUDO OK, `check-tree.sh city_build` → COMPILOU OK); nothing was run in game.

## Files touched (all under `feature/settlements/**`, plus lang and generator)
- **New** `build/CityTerrain.java` — street level, full-plot leveling surfaces, skirt, stair links, lamp posts, flag and yard plants.
- **New** `CityDecor.java` — festival decoration API.
- `build/ConstructionEngine.java` — crews, block budget, new skirt phase, new finish step.
- `build/Project.java` (`PHASE_SKIRT = 4`), `build/CityLayout.java` (`inWallBand`), `build/ProcBuildings.java` (smoking chimney on gabled roofs, pergola on desert flat roofs).
- `Settlement.java`, `SettlementService.java`, `CityPlanning.java`, `Lordship.java`, `SettlementView.java`, `CityDebugCommands.java`, `entity/BuilderNpc.java`, `client/SettlementPanel.java`.
- `tools/gen/settlements_v14_lang.py` (new v1.0 block, re-run) → `src/main/lang/settlements/{pt_br,en_us}.json`.

## New ids
No registry ids. New optional NBT only; no existing key was renamed or removed:
- Settlement: `Decor`, `DecorUntil`, `DecorBlocks`.
- Project: phase value 4; `ClearHalf = 8` now marks a "flat" project.

## What changed
- **Leveling**:
  - New builds, upgrades and manual leveling flatten the whole plot plus the street line around it (17×17).
  - A plot takes the height of its inward neighbour when natural terrain is within 3 blocks; otherwise it becomes a 2-block terrace.
  - Outside the plot, natural terrain is smoothed in 1-block steps over 6 columns. It never touches leveled plots, wall bands or non-terrain blocks.
  - Stairs are built only between a plot and its inward neighbour, only when the difference is over 1, always at the same spot, and never inside a building footprint.
  - Existing plots keep their saved height; in-flight old projects follow the old path.
- **Builders**:
  - Cap is 2 + (center level − 1), + 2 per Builders' Hall level, + 1 per Workshop (2 at level 3+), max 24.
  - One work per builder; spare builders help and add +50% speed each.
  - Base speed went from 1.5 to 4 steps per action, the queue from 6 to 24, and missing builders arrive every 10 s (two at a time).
- **Budget**: 260 block changes per city per 5 ticks, 520 per tick server-wide.
- **Visual**: continuous street ring (dirt path, paved from center level 3), yard with plants, two lamp posts and a style-colour flag per lot, plus the roof details above.
- **Panel**: the Works tab now scrolls and shows "Builders x/y · Works n/24".

## APIs for city_life
- `CityDecor.set(city, on)`, `setUntil(city, lastDay)`, `isOn`, `placed`, `refresh`, `forgetArea`, `clearNow(level, city)`.
  - `tick` is already wired in `SettlementService.tickHeart`.
  - It places only in air on street/terrain, records every block, and removes only its own blocks. Real fireworks launch at night.
  - `Lordship.festival` already calls `setUntil(today + 1)`, so decorations last two days; bonuses are left for city_life.
- `Settlement.builderCap()`, `Settlement.MAX_BUILDERS`, `SettlementService.MAX_QUEUE`, `ConstructionEngine.HELPER_BONUS / CITY_BUDGET / TICK_BUDGET / flat(p)`.
- `CityTerrain.levelOf`, `reference`, `streetLevel`, `inward`, `link`, `ground`, `street`, `yard`.
- View tag `QueueMax`.

## How to test
1. `/mahou admin login <senha>`, found a city with the Charter, then `/mahou city level 3` and `/mahou city builders`.
2. Queue several buildings on flat and sloped ground; watch the crews, or use `/mahou city finish`.
3. `/mahou city terrain` while standing in a plot shows its height, reference and computed street level.
4. `/mahou city decor on`, then `/time set night` for fireworks; `/mahou city decor off` (gradual) or `/mahou city decor clear` (instant).
5. `/mahou city radius 7` and queue 20+ works to check the budget and the scrolling Works tab.

## Not done / not verified
- No in-game run: stair geometry, skirt look, helper pathing and decoration placement are checked by compile and reasoning only.
- The `.nbt` houses and plaza were not regenerated, because their generator depends on `tools/gen/city_build.py`, which cities_npc owns. Roof improvements apply only to the procedural buildings.
- Steep mountains: the city follows at most 2 blocks per ring, so far rings on a steep slope get deep cuts or tall retaining walls.
- No lateral stairs between side-by-side plots at different heights (a retaining wall stays).
- A height difference over 2 between full-size (13×13) buildings gets no stairs, because they would not fit outside the footprints.
- Streets exist only around built or leveled plots.
- Plots older than v1.4 with no saved height get a recomputed level on upgrade.

## PEDIDOS
None.