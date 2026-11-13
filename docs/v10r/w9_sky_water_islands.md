**sky/water islands report** — sky and water cities founded from now on are **archipelagos**: a central island (or platform) plus one island per plot, separated by open sky/water and joined by bridges. Islands are created only when the city needs a plot. Old saved cities keep their old layout.

`check-tree.sh skycity` → COMPILOU OK (whole tree, last run after all edits). Nothing was run in game or in a JVM. The geometry (gaps, coverage of the ±8 plot, shaft inside the core island) was checked by hand from the formulas, not observed.

## What the owner asked → what was done
| Owner's words | Implementation |
|---|---|
| "a cidade voadora tem que ser bem mais alta" | New sky cities: Heart at **y = max(220, highest terrain within ±96 + 100)**, capped at **256**. Founding is refused if that leaves less than 48 blocks of clearance. Old rule was 160–208 (typically 160). Constants: `SkyIsles.ALTITUDE` / `CLEARANCE` / `MAX_Y` / `MIN_GAP`. 256 + ~56 for the tallest buildings stays under the 320 build limit. |
| "um local pra subir no meio que nem as sky island" | The Updraft (existing `SkyAccess` mechanism) now rises through a **3×3 shaft in the middle of the central island**, beside the plaza on the plaza-front side (11 out, 5 to the side). The ground ring marker sits under it. Riders are set down beside the shaft; sneaking lets you float down through it. The shaft has a railing (style rail + 4 corner lanterns) with an opening toward the main street. There is no edge pier in this mode. |
| "ir criando ilhas com pontes... não ser que nem o tradicional" | Plots are spaced **30 blocks apart** (`CityLayout.ISLE_PITCH`) instead of 16. Each plot that gets a work gets its own floating island: a rounded, noisy rim, a cone belly with one hanging spike, and hanging roots underneath. It uses the style's layer palette. Each island is joined to the island of its "inner neighbour" (`CityTerrain.inward`, the same rule as streets and door direction) by a **5-wide bridge with rails**. The bridge floor is planks at ranks 1–2 and the style's paving from rank 3 (`WaterCity.walk`). Lanterns sit at both ends, plus one mid-span from rank 3 on long spans. If the inner neighbour has no plot yet, a small **stepping islet** (7×7) is made there and the chain continues to the plaza. |
| "tem que ir expandindo conforme a necessidade dele" | **No pre-made grid.** At founding only the central island forms. Every 1 s, `SkyIsles.sync` creates islands for any plot that has a building, a levelled height, or **any queued work** (build, flatten, upgrade, restyle, repair...). This catches every path (planner, auto-queue, magic, debug commands), and it also runs at the start of `/mahou city finish`. Expanding the radius (`CityPlanning.expand`) only unlocks plots; it no longer generates a land ring. When a building is ordered on a stepping islet, the islet grows into a full plot island. |
| Water city, same philosophy | New water cities use the same spaced plots. Each work raises its own deck on stilts, as before (`WaterCity.deckColumn`), and open water stays between platforms. `WaterWorks.islePlan` links each platform to its inner neighbour with a boardwalk that has an **arched section mid-span** (boats pass under). A 5×5 pontoon is added where the inner plot is still water. The quay, boats and decorations (fountain, Oriental torii, Nordic longship) are in an **inner harbour** at a plaza corner, so they never move when the city grows. |

## Walls (item 7)
- **Sky archipelago:** a full ring wall floating in the void made no sense. Wall k is now **only its 4 towers and 4 gates**, each on its own **watch islet** (R6), placed in the gap between plot rings 2k and 2k+1.
  - Gates sit on the axis bridges. Gate islets merge with the axis plot islands when those exist.
  - It still counts as a wall for rank requirements, security, shooting towers and renovation. `CityLayout.wallPieces(Settlement, ...)` drops the segment pieces for sky archipelagos.
- **Water archipelago:** the full breakwater ring is kept (it makes sense on water), moved to the gap between rings 2k and 2k+1. Bridges that cross a wall band are built flat (no arch) and the gate is the passage.
- **Arrivals:** sky archipelago arrivals and raid spawns never use the watch islets, which have no bridge (`SkyIsles.hasTownLand`).

## Old saves
- New optional NBT on `Settlement`: `Isles` (bool) and `IsleData` {`L` islands `[kind,dx,dz,cell,done]`, `G`/`Ring`/`Idx` generation cursor, `B` tracked bridge blocks, `Pop`}. These are written only for archipelago cities.
- `Isles` is read only when the city is also sky or water.
- **Old sky and water cities load and behave exactly as before** (same grid ids, same land rings, same pier, canals and walls). They do **not** convert, and new expansion of an old city also stays on the old layout. Converting a built city would mean moving buildings, so it was not attempted.
- Plot ids of archipelago cities are a new id range: `CityLayout.ISLE = 1<<26 | (i+64)<<8 | (j+64)`, with the plaza still cell 12. Every city map (buildings, levels, heights) uses them like any other id.
  - A grid id sent to an archipelago city, or an island id sent to a grid city, is refused (`plan.bad_cell`), so a building can never end up in the wrong place.

## Fix from the w8 report
- The new-look yards (`LookBuildings`/`LookWorks`) put grass/sand on water decks.
- `ConstructionEngine.place` now turns template-floor soil (grass/sand/dirt...) into the style's deck in **any** water city, old or new. Yard flowers and grass that would sit on a deck are skipped.
- Sand can no longer fall into the sea.

## Files
Java, under `src/main/java/com/renanpontes/isekaimahou/feature/settlements/`:
- **New:** `SkyIsles.java` (island registry, shapes, on-demand creation, gradual generation, bridges, shaft geometry).
- **Edited:**
  - `build/CityLayout.java`: island ids and spacing, `cellAt`/`indexAt`/`cellAtOffset`/`axis`/`half`/`territory`/`inWallBand`/`wallDist`/`wallPieces`/`wallSwapPieces` taking the Settlement, `cellsFor`, `arrivalPoint`.
  - `SkyCity.java`: island branches, new `heartY`, helpers made package-private.
  - `SkyAccess.java`: shaft column, railing, landing, bridge style accessors, NPCs on bridges, arrivals/raids.
  - `WaterWorks.java`: `islePlan`, harbour.
  - `WaterCity.java`: preview cells, `Isles` flag in the view.
  - `Settlement.java`: two fields, save and load.
  - `SettlementService.java`: `isles` set at founding, place box, wall pieces.
  - `build/ConstructionEngine.java`: wall bands, neighbour lookup, water yard fix.
  - `build/CityTerrain.java`: `inward` keeps island ids, neighbour loops.
  - `CityPlots.java`: `plotAt`; free position refused in archipelagos.
  - Small one-line changes: `CityViewExtra`, `CityRenewal`, `CityScenery`, `CityDefense`, `entity/GuardNpc`, `CityDecor`, `CityLooks`, `CityPlanning` (expansion box), `CityDebugCommands`, `SkyDecor` (skipped for archipelagos).
  - Client: `client/CityPlacer` (founding preview shows the central island, the shaft beam and spaced plots; the water preview shows spaced platforms, bridges and the harbour), `client/SkyPreview`, `client/SkyPanel`, `client/WaterPanel` (archipelago tooltips).
- **Lang:** `tools/gen/settlements_w9_isles_lang.py` → `src/main/lang/settlements/{pt_br,en_us}.json`.
  - 14 keys added (`isle.*`, `cmd.isle.*`).
  - 7 texts reworded to fit the new layout while staying true for old cities: hints `city_sky_found`, `city_sky_updraft`, `city_sky_fall`, `city_water_found`, `city_water_boat`, plus `sky.ui.pick.tip` and `water.ui.pick.tip`. Hints show once, so old cities already saw the old text.

## How to test (op, `/mahou admin login <senha>` first)
**Sky**
1. Stand on open ground: `/mahou city sky found elven`. The central island forms in about 2 s around y≈220; you are lifted and two builders arrive. `/mahou city sky` prints islands, ready count, bridge blocks and %.
2. `/mahou city finish` builds the plaza. Look beside the plaza on the front side: the 3×3 shaft with railing. `/mahou city sky down` takes you to the ground marker; walk into the glow and you rise and land beside the shaft. Sneak in the shaft to go down.
3. Open the planner and order a house on any unlocked plot. Within about 1 s an island forms 30 blocks out, then a bridge.
   - Order a plot two steps out with nothing between (e.g. after `/mahou city level 2`, a diagonal): a stepping islet appears on the way.
   - `/mahou city finish` completes everything.
4. `/mahou city sky isle 3 0` forces an island (plus islets and bridges) without a work; `/mahou city sky isle 0 0` is refused.
5. `/mahou city level 3`, queue a wall, `/mahou city finish`: 4 towers and 4 gatehouses on watch islets around the second ring.
6. `/mahou city style oriental`: bridges are re-planned at once and switch to the new floor, rail and lantern blocks over the next seconds (or at once with `/mahou city finish`). `/mahou city tier 3`: bridge floor changes to paving.
7. Free placement or moving a building in the planner is refused with `isle.no_free`.
8. `/mahou city sky regen` refills holes in every island. `/mahou city sky drop` near an NPC: it is returned.

**Water**
1. `/mahou city water found nordic` on open sea. The founding preview (Tab to water) shows spaced platforms and the harbour.
2. Queue houses, then `/mahou city finish`: separate decks with open water between them, arched boardwalks to the plaza, and pontoons where an inner plot is empty.
3. `/mahou city water finish` completes boardwalks, harbour, longship (Nordic) or torii (Oriental), and boats.
4. Queue a wall at rank 2+: the breakwater ring sits between rings 2 and 3, and the bridges there are flat.

**Old cities:** load a save with an old sky or water city. Nothing moves, and `/mahou city sky` shows the old "raio/anel" line.

## Not verified / known limits
- **No in-game run at all.** Unverified:
  - how the islands, bridges, shaft and railing look;
  - ride feel through the shaft;
  - whether NPCs path across 3-wide bridges;
  - whether builders reach far islands (the existing builder teleport should cover it);
  - boats under the mid-span arches;
  - how large wall templates fit on R6 watch islets.
- **Move / free position** are unavailable in archipelago cities (refused with a message). Making them island-aware needs client planner work.
- **Height offset −4..+4** still applies to a plot. A raised or lowered plot meets its bridge with a step, because bridges stay at plaza height.
- **Legacy island decorations** (waterfalls, satellite islets, rim mist from `SkyDecor`/`SkyFx`) are not used on archipelagos. Only hanging roots under each island.
- **Under-island monster suppression** (`onSpawnCheck`) is off for archipelagos, since the islands are small and very high.
- **Island stuck on unloaded chunks:** an island whose columns are in an unloaded chunk waits 10 s and retries. Its work waits ("Aguardando a terra se formar").
- **Bridge tracking:** blocks are tracked in NBT. A bridge block in an unloaded chunk when a stepping islet grows is removed later.
- **`city_arcana/CityArcanaCommands`** (another module) still maps the player's position with grid math. In an archipelago that command finds no plot and is refused, which is harmless.
- **Water founding rule** ("60% wet") still samples the old 5×5 start area.
- **Balance:** the 30-block spacing makes cities wider (radius 12 ≈ 375 blocks from the Heart). Untested for distance between cities.

## PEDIDOS
- `docs/ARCHITECTURE.md`: add `SkyIsles`, the `Isles`/`IsleData` NBT keys, island cell ids (`CityLayout.ISLE`, `ISLE_PITCH`, `cellAt`/`indexAt(Settlement, ..)`), the `/mahou city sky isle <i> <j>` command and the generator `settlements_w9_isles_lang.py`.
- Journal/guide: the Sky Island is now an archipelago above the clouds with the Updraft in the middle; the Water City is separate platforms with arched bridges and an inner harbour.
- If an older settlements lang generator is re-run, run `py -I tools/gen/settlements_w9_isles_lang.py` afterwards.

# Easier founding
Owner (2026-11-13): *"devemos facilitar começar a cidade agora... inclusive a sky e a water"*.

`check-tree.sh skycity` → COMPILOU OK after these changes. Not run in game.

## What stood in the way, and what changed
| Friction | Before | Now |
|---|---|---|
| Founding Charter recipe | 2 **emerald blocks**, 2 **gold blocks**, 4 paper, book and quill (≈18 emeralds + 18 gold). The recipe unlocked only after holding an emerald block. | **2 paper + 2 gold ingots + book and quill** (`" P " / "GBG" / " P "`). The recipe unlocks on holding paper. |
| Getting the Charter without crafting | Not possible. | The **Scribe** in the starting city sells **1 a day for 12 guild coins**, with no reputation needed (`CityTrades.scribe`). |
| Sky Core (Núcleo Celeste) | 2 phantom membranes, 2 Wind Essences, 2 feathers, 2 amethyst shards, 1 mana crystal (mid game). | **2 feathers + 2 amethyst shards + 1 Mana Crystal** (`" F " / "AMA" / " F "`). The **Alchemist** in the starting city sells **1 a day for 16 coins**. The Mana Crystal itself costs 4–5 coins from the Scribe or Alchemist. |
| Water: how much water | ≥ 60% of the start area had to be deep water. | **≥ 35%** (`WaterCity.MIN_WET`): a bay, big lake or wide river is enough. The refusal still says the % found and the % needed. Depth (2+) and "no ocean monument or mod structure in the area" are unchanged. |
| Sky: ground point | The clicked point had to be solid, dry ground (same as a land city). | Any column works (water, leaves, a hill top). Only real conflicts remain: another place's land under the island, sky structures, terrain within 48 blocks of y 256. |
| Distance to villages | 220 blocks between hearts, same as cities. | **140 for villages** (`CityLayout.MIN_VILLAGE_DISTANCE`). Cities stay at 220. The rule that the territory box may never overlap any place is unchanged (cities are sacred). |
| "Too close" message | "Perto demais de X (N blocos)" — no hint of how far to go. | "Perto demais de X (N blocos). **Afaste-se mais uns M blocos** dali" (`found.move_away`). M = the larger of the shortest way out of the box overlap and what is missing to the minimum distance, plus 2. |
| Silent failures | `/mahou settlements found`, `sky found` and `water found` did nothing visible when the inventory was full (the Charter could not be added) or the spot was bad (the reason only flashed above the hotbar). | The commands now print the reason **in chat**. Full inventory gives `found.full`. `SettlementService.found(...)` now returns the reason (null = founded); the screen path still shows it above the hotbar as before. |
| Knowing how to start | Only the journal guide entry. | One-time hint **`city_how`**: after 1 in-game day of play time, a player with no city gets one short hint (where to buy or craft the Charter, hold it, right-click; Tab for water/sky). Uses the normal `Hints` once-only system. |
| Texts | The Charter tooltip still said "Reino, Desértico ou Nórdico"; the sky-core screen tooltip, the water "60%" text and the guide recipe line were outdated. | Rewritten: Charter tooltip (all 3 terrains, sold by the Scribe), Sky Core use line (sold by the Alchemist), `sky.ui.core.recipe`, `water.ui.pick.cost` (35%), journal guide `city_found.where`. |

**Checked and left as is:**
- **One city per player.** Abandoning in the menu frees it; the message already says so.
- **Overworld only.**
- **No rank/level gate and no material cost to found.**
- **Founding steps:** hold the Charter → right-click pin → right-click (or Enter) → style/name → Found. It is already short, and changing the screen without a test run was not worth the risk.

## Archipelago size vs. distance rules
- **Before:**
  - Archipelago territory jumped by about 30 blocks at every odd radius. Wall slot `(r+1)/2` could put a wall around a ring that didn't exist yet: at radius 3, wall 2 sat 135 blocks out while the last island ended at ~102.
  - Two archipelagos founded 220 apart (the minimum) blocked each other's growth at radius 2. Two old-layout cities could reach radius 4.
- **Now:**
  - In archipelagos a wall only goes around a complete even ring (`CityLayout.isleWallSlots(r) = max(1, r/2)`, used by `nextWall`, `wallProblem` and the panel), and `isleHalf` follows it.
  - Territory half-width by radius: 79, 106, 139, 166, 199... up to 379 at radius 12. Neighbours 220 apart can now both reach **radius 3** before `expandProblem` names the other city.
  - Rank-ups still fit: Burgo needs wall 1 (radius ≥ 2), Capital needs 2 walls at 11×11 (radius 5 → 2 slots).
- **Kept:**
  - 220-block founding distance and 30-block plot spacing. Shrinking the spacing would merge the core island with ring 1 and leave no room for the shaft. Raising the founding distance would make founding harder, which is the opposite of the request.
  - Water founding now checks the archipelago's real start box (±79 + 8) instead of the old ±46.
- **Limit:** cities founded close together still limit each other's growth. That is intended (cities never overlap), and the expansion refusal names the neighbour.

## Old saves
- Nothing stored changes: recipes and trades are data and code only.
- Old cities keep the old wall-slot rule (it only applies to `s.isles` cities).
- Existing Charters and Sky Cores still work.

## Files (this section)
- **Java:**
  - `SettlementService` (founding rules, `found` returns the reason, `giveFoundingItems`, `isleStart`, `escape`, `foundHint`)
  - `SettlementCommands` (`report`), `CityDebugCommands`, `WaterCommands`
  - `WaterCity` (`MIN_WET`)
  - `build/CityLayout` (`MIN_VILLAGE_DISTANCE`, `isleWallSlots`, `wallSlots(Settlement)`, `isleHalf`)
  - `CityViewExtra`
  - `feature/cities/trade/CityTrades` (Scribe and Alchemist offers)
- **Data:** `recipe/founding_charter.json`, `recipe/sky_core.json`, `advancement/recipes/misc/founding_charter.json`.
- **Generators kept in sync so a re-run won't revert:** `tools/gen/settlements.py` (Charter recipe + advancement), `settlements_w7_sky_island.py` (Sky Core recipe), `journal_story.py` (guide line).
- **Lang:** `tools/gen/settlements_w9_isles_lang.py`.
  - Settlements: 4 new keys (`found.move_away`, `found.full`, `found.far`, hint `city_how`) and 4 rewritten (Charter desc, Sky Core use, `sky.ui.core.recipe`, `water.ui.pick.cost`).
  - Story: 1 key rewritten in place (`guide.isekai_mahou.city_found.where`; that file is not sorted, so order is kept).

## How to test
1. New world: craft the Charter with 2 paper + 2 gold ingots + book and quill. Or open the Scribe in the starting city: the Charter is in the list. The Alchemist lists the Sky Core.
2. Fill the inventory, then `/mahou settlements found`: the chat says the inventory is full.
3. Stand ~100 blocks from a village and `/mahou settlements found`: the chat says how many more blocks to walk. Walk that far and repeat: it founds.
4. Sky: hold the Charter and a Sky Core, aim at a lake or a tree, Tab to sky: the preview turns green (it was red before because the ground was not solid).
5. Water: a big lake or a bay with ~40% water is now accepted.
6. New player with no city: after one in-game day the `city_how` hint appears once.
7. Archipelago: `/mahou city sky found`, then `/mahou city sky grow` once: the place box is 106, not 139. At radius 3 the panel offers only wall I; at radius 4, wall II.

## Not verified
- Prices (12 and 16 coins) and the 35% water share are guesses, untested for balance.
- The hint timing uses `PLAY_TIME` (≥ 24000 ticks); a returning old player with no city will also see it once.
- The founding screen's sky button still needs the Sky Core in the inventory (that is the cost); only how you get it changed.
