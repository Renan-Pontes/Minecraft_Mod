**water_city report** — every item of the section has a compiling core, but nothing was run in game or in a JVM. `check-module.sh settlements` → TUDO OK, `check-module.sh city_arcana` → TUDO OK, `check-tree.sh water_city` → COMPILOU OK. The only runtime check was a Python port of the boardwalk planner (radius 2–12, random built plots, walls on and off): every plot and the quay reach the plaza and no flat floor lands in a canal.

## Files touched
All Java under `C:/Users/renan/Music/ROCKET/RocketSimVis/Minecraft_Mod/src/main/java/com/renanpontes/isekaimahou/feature/settlements/`.
- **New:** `WaterCity.java` (rules, founding, deck/stilts, palettes), `WaterWorks.java` (boardwalks, bridges, quay, boats, decorations), `WaterLife.java` (trait, NPC rescue, raids, boat hint), `WaterCommands.java`, `client/WaterPanel.java`, `tools/gen/settlements_w8_water_lang.py`.
- **Edited (small, pointed):** `Settlement`, `SettlementService`, `SettlementsModule`, `SettlementView`, `CityPlots`, `CityPlanning`, `CityRaids`, `CityRenewal` (street repave line), `CityEconomy`, `CityDecor`, `CityDebugCommands` (one `.then`), `build/ConstructionEngine`, `build/CityTerrain`, `build/CityLayout`, `client/CityPlacer`, `client/FoundingScreen`, `client/SettlementPanel` (one line).
- **Not touched:** `ProcBuildings`, `TierLook`, `TierWalls` — no hook was needed there.
- **Lang:** `src/main/lang/settlements/{pt_br,en_us}.json`, 28 keys added, none changed or removed.

## New ids
- **Terrain id:** `water` (`WaterCity.WATER`), sent in the existing `Found` packet's terrain field.
- **Settlement NBT (optional):** `Terrain` ("water" / "skyland") and compound `Water` {`Decor`, `BoatAt`}. The old `Sky` compound is still what marks a sky city.
- **Client city NBT:** `Terrain`, `Water`, `WaterFish`. Probe answer: `Water`, `WaterY`, `WaterPct`.
- **Hints:** `city_water_found`, `city_water_boat`.
- **Entity tag:** `isekai_mahou.water_boat` (quay boats).
- **Commands (op):** `/mahou city water`, `water found [style]`, `water finish`, `water redo`, `water grow`, `water tp`, `water drop`.

## What it does
- **Founding:** third choice "On the Water" in the charter screen; no extra item.
  - In the preview, Tab now cycles ground → water → sky (sky only with a Sky Core), and the aim stops at the water surface.
  - The server requires water at the aimed spot, deep water (2+ blocks) under at least 60% of the starting territory, and no ocean monument or mod structure in the territory. The usual distance rules still apply.
  - The Heart sits 2 blocks above the water; a 9×9 deck appears at once for the founder and the first two builders.
- **Floor:** the city is flat at deck height, one block above the water.
  - Each plot's deck is laid during its building's foundation stage: floor block per column, a stilt every 4 blocks down to the seabed, capped at 10 blocks with a foundation stone.
  - The sea is never filled and nothing is dug below deck height; a negative height offset is clamped to 0. Dry-land columns use the normal terrain code.
- **Canals:** plots with no building stay water. A 3-wide canal circles every wall and never gets flat floor.
  - It is crossed only on the 4 axes, by arched bridges with two blocks of air over the water.
  - Plots beyond a canal reach the axis along a 2-wide quay on the outer bank.
- **Boardwalks:** every built plot is linked to the plaza through unbuilt neighbours by a 3-wide walkway.
  - The planner redoes the layout when plots, radius, rank, style or walls change.
  - It grows about two rows per tick from the shared construction budget, loaded chunks only.
- **Walls:** same footprint, as a breakwater — the base goes 2 blocks into the water with stilts under it.
- **Quay:** a T-shaped pier past the outer canal on the plaza's front side, with 2 boats (3 from Town rank), replaced one at a time about every 2 minutes. It moves outward when the city grows.
- **Decorations (tracked, removed cleanly):**
  - Rank lamp posts (from `TierLook`) beside boardwalks, and lily pads.
  - Barrels, a crate and hay on the pier; seagrass at some stilt feet.
  - A sea fountain past the pier.
  - A torii in the water for Oriental and a moored longship for Nordic.
- **Rank and style changes:** boardwalk floor is planks at ranks 1–2 and the style's paving from Town up; the rank renewal repaves plot street rings, and the planner repaves boardwalks, bridges and lamp posts.
- **Life:**
  - Harbor trait: +2 +2×rank food a day, and citizens lose air at half speed underwater in the territory. One panel line.
  - NPCs in the water are returned to the nearest firm floor on their next heartbeat; spawn and arrival points are always on firm floor.
  - Raids spawn at the platform edge with splashes, and Drowned join the cast.
- **Ground and sky cities:** every hook exits on `!s.water`. The one visible change for them is the unpinned preview hint line, which now ends with "Tab: change terrain".

## How to test
1. `/mahou admin login <senha>`, go to open sea or a big lake, in a boat or on the shore.
2. `/give @s isekai_mahou:founding_charter`, hold it, press Tab once (line reads "Water City"), aim at the water, right-click to pin, right-click again, pick a style, "Found the city". Aim at land to see the refusal.
3. Or skip the screen: `/mahou city water found oriental` (any of the 7 styles).
4. Watch the main boardwalk grow from the plaza to the bridge and pier. `/mahou city water` prints the state; `/mahou city water finish` completes it now.
5. Queue houses, then `/mahou city finish`: stilts, deck, building. Leave some plots empty and row between them.
6. `/mahou city level 3`, queue a wall, `/mahou city finish`: breakwater, gate, bridge over the canal. Row around the wall and under the bridge.
7. `/mahou city tier 4`, then `/mahou city renew now`: boardwalk floor and lamp posts change.
8. `/mahou city style nordic`: the longship appears by the pier; `oriental`: the torii.
9. `/mahou city water grow` twice: bridges on all four sides of the first canal and the pier moves out.
10. `/mahou city water drop` near an NPC: back on the deck within 5 s.
11. Force a raid with the existing raid command. Dive inside the territory as a citizen and watch the air bar.
12. `/mahou city pause 1`, `resume 1`, `cancel 1`, and a free-position building from the panel.
13. Restart the server; found a ground city and a sky city elsewhere to confirm they are unchanged.

## Not done / not verified
- **No in-game run:** deck and stilt look, bridge clearance for a boat, boat spawn height, preview drawing, the three-button row in the charter screen, panel line position and the sea fountain's water flow are all unverified.
- **Plaza fountain:** not added inside the plaza (flowing water there would flood the neighbouring decks). I put a sea fountain past the pier instead; the existing rank-3 plaza fountains still apply.
- **Shore ramp:** no dedicated ramp or stair. Plots on land are levelled to deck height as usual; a boardwalk only continues over land that is at deck height with free space above.
- **Canals between individual plots:** there are none inside a block when every plot is built; the guaranteed canals are the rings around each wall.
- **Water gates:** gates are the normal gate piece plus the bridge and pier; no separate boat gate through the wall.
- **Builders** may swim during the first rows of a plot's deck when no floor is within 9 blocks.
- **No protection:** boardwalks, bridges, pier and boats can be broken or taken by players (boats come back).
- **Pier nets** were not made; the old pier head stays as a small platform after the pier moves, until a plot is built over it.
- **Yard decks** change style only when that building is restyled.
- **Expansion** does not re-check the water share or structures in the new ring.
- **Frozen biomes:** ice columns count as land, and the fountain may freeze.
- **Balance** of the Harbor food bonus and the 60% rule is untested.

## PEDIDOS
- `docs/ARCHITECTURE.md`: add `WaterCity`, `WaterWorks`, `WaterLife`, terrain id `water`, the two hints, the boat tag and the commands.
- Journal/guide (other modules): a short entry on the Water City (Tab in the preview, canals, quay boats, Harbor trait).
- `item.isekai_mahou.founding_charter.desc` could mention the three terrain types; I did not change it.
- If any older settlements lang generator is re-run, run `py -I tools/gen/settlements_w8_water_lang.py` afterwards.