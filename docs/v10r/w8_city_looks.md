**city_looks report** — the whole section is in and compiles, but nothing was run in game or in a JVM, so every new shape is unverified. `check-module.sh settlements` → TUDO OK, `check-module.sh city_arcana` → TUDO OK, `check-tree.sh city_looks` → COMPILOU OK.

## Files touched
All Java under `C:/Users/renan/Music/ROCKET/RocketSimVis/Minecraft_Mod/src/main/java/com/renanpontes/isekaimahou/feature/settlements/`.
- **New:**
  - `build/LookBuildings.java` — new-look engine: materials per style and level, yards, houses, tavern, builders' hall, town hall.
  - `build/LookWorks.java` — farm, market, barracks, watchtower, temple, smithy, stable, well, garden, castle, obelisk, pyramid, sphinx.
  - `CityLooks.java` — per-lot look generation, "Rebuild in the new look", showcase commands.
  - `CityScenery.java` — street furniture and plaza per rank.
  - `C:/Users/renan/Music/ROCKET/RocketSimVis/Minecraft_Mod/tools/gen/settlements_w8_looks_lang.py`.
- **Owned files edited:** `build/ProcBuildings` (helpers made package-private only), `build/TierLook` (`pave`, wider `isFurniture`), `build/TierWalls` (sweep, `isWallBlock`, `resolveSwap`, royal grade and gates).
- **Small pointed edits:** `build/Blueprint`, `build/Templates`, `build/MonumentBuildings`, `build/CityTerrain`, `build/ConstructionEngine`, `Settlement`, `SettlementService`, `CityPlanning`, `CityPause`, `CityRenewal`, `CityActions`, `CityViewExtra`, `CityDebugCommands`, `client/SettlementPanel`.
- **Lang:** `src/main/lang/settlements/{pt_br,en_us}.json` — 81 keys added, none removed or changed.

## New ids
- **Template ids:** `settlements_look/<style>/<key>/<level>/<variant>`; wall swap ids may now start with `any.0.` (rebuild from scratch).
- **Optional Settlement NBT:** `BLooks` (lot → look generation and variant; absent = pre-wave-8 building), `LookGen`.
- **`WallLooks` value** `mixed:0` for a wall whose pieces ended in different styles or grades.
- **Action:** `plan_relook`. **Hint:** `city_looks`. No registry ids, no new project kind (the rebuild reuses `restyle`).
- **Commands (op):** `/mahou city showcase <key|wall> [style]`, `/mahou city showcase clear`, `/mahou city looks`, `/mahou city looks redo`.

## What it does
- **Buildings:** every new build, upgrade, restyle or move uses the new look, where each level is a different building.
  - Materials go raw logs → timber on stone → dressed stone → plastered upper floor with stained glass → the style's noble material, with a different roof per level.
  - The yard goes stumps → fence → low wall → walled garden → paved courtyard.
  - The 18 plants that were fixed `.nbt` moulds have their own generators with function details (farm: patch and scarecrow → barn, silo, windmill; smithy: lean-to → two storeys, twin chimneys, weapons yard).
  - The older procedural plants are placed in a 13×13 lot with the level's yard.
- **Monuments (3 levels):** they now use visuals 1, 3 and 5, so the richest form is reachable again. The World Tree starts bigger and gains a second canopy tier.
- **Old buildings do not change by themselves.** Repairs and street renewal keep using the mould they were built with. The planner shows "Rebuild in the new look (N coins)", costing 10 × level + 5 × city rank, with the same rules as a restyle.
- **Planner:** one line per lot with the next level's look, and "Starts as: …" in the catalog tooltip.
- **City ranks:**
  - Streets: dirt trail → gravel with kerb → stone with sidewalk → avenue with polished sidewalks → noble paving with mosaic stones.
  - Street furniture: barrels and carts → benches and planters → trees, benches and flower planters → trees with lanterns and banner poles.
  - Plaza: campfire and tents → stalls and well → benches (plus the existing fountains) → statue and four entrance arcades → crown on the Heart Monument, floor mosaic and corner banners.
  - All of it is tracked in `tierBlocks`, placed only on firm street pavement.
  - Existing cities re-run the renewal once to get this.
- **Walls:** the royal grade is taller with buttresses, a second row of arrow slits and a banner mast. Gates get side turrets that grow per grade, arched corners from grade 3 and hanging lights at grade 4.
- **Wall renovation:** I could not reproduce the bug in game; these are the causes found by reading the code, and each is fixed.
  - The renovation went to the end of the queue, and was only queued at the start of a renewal if no other wall work existed. It is now placed right after the running works and re-checked during the renewal.
  - A piece whose saved old mould did not match the world was skipped silently. Now the engine identifies which style and grade is actually standing; if nothing matches, it rebuilds the piece from scratch at the neighbouring height.
  - Every swap first removes all wall-material blocks in the piece's footprint, so no old wall is left over.
  - A wall still under construction at the time of a Reform or rank-up builds its remaining pieces in the new style and grade; the pieces already up are swapped afterwards.
  - The panel already listed "Wall N renovation" with days and stage, and it stays pause-only per the wave-7 rules.

## How to test
1. `/mahou admin login <senha>`, stand on open ground outside any city.
2. `/mahou city showcase house_small gothic` — levels 1–5 side by side, west to east. Try `farm`, `smithy`, `market`, `barracks`, `watchtower`, `well`, `garden`, `tavern`, `town_hall`, `library`, then `temple`, `statue`, `world_tree`, `castle`, `pyramid` (3 levels).
3. `/mahou city showcase wall gothic` — the four wall grades. `/mahou city showcase clear` removes the showcase.
4. In his existing city: `/mahou city looks` counts old-look buildings. Open the planner on one, click "Rebuild in the new look", then `/mahou city finish`. `/mahou city looks redo` does the same for free on the lot you stand on.
5. Upgrade a building in the planner, checking the "Level N look" line, then `/mahou city finish`.
6. Ranks: `/mahou city tier 1` … `5`, each followed by `/mahou city renew now`; compare streets, furniture and plaza.
7. Finished wall: at rank 2+ queue a wall, `/mahou city finish`, `/mahou city style oriental`, wait a few seconds, check Obras for the renovation, `/mahou city finish`.
8. Wall in progress: queue a wall, `/mahou city days 1`, `/mahou city style gothic`, `/mahou city finish` twice (the second finishes the swap of the first pieces).
9. Unknown wall: break or rebuild a few segments by hand, `/mahou city tier 4`, `/mahou city finish`.
10. Repeat steps 4–8 on `/mahou city sky found elven`.

## Not done / not verified
- **No run at all:** I did not run a headless generator test either, because of the no-Java rule. If a generator throws, the work falls back to the old mould and logs `falha ao gerar o visual novo`.
- **Panel layout:** the extra line and button in the plot details may overflow on small windows.
- **Wall grades:** there are still four (palisade, stone, fortified, royal), one per rank that can have walls; the Camp cannot build walls.
- **Avenue:** the "central planter" became trees and planters on one sidewalk (east and south of each lot). A block in the middle of a 3-wide street would stop mounts.
- **Stairs between lots:** new moulds fill the whole lot, so link stairs for height differences of 3 or more often no longer fit. Only the gate path at the front counts as room. Differences of 2 still work.
- **Scratch-rebuilt wall pieces** get no foundation, so there can be a gap underneath on uneven ground.
- **Plaza scenery** uses fixed offsets from the Heart and is skipped wherever something already stands; it can come out partial.
- **Floor mosaic** only replaces street blocks or the plaza mould's own floor block.
- **Water City:** street furniture needs street-type pavement, so decks get it only if their floor is one of those blocks. Wall base height on decks uses `s.water` from the other agent's field.
- **`/mahou city looks redo`** costs nothing, but cancelling that work still refunds coins (op test command only).
- **Castle, obelisk, pyramid and sphinx** are new block designs, not copies of the old moulds.
- No wave-7 bug was found in the code I depend on.

## PEDIDOS
- `docs/ARCHITECTURE.md`: add `LookBuildings`, `LookWorks`, `CityLooks`, `CityScenery`, the `BLooks` / `LookGen` keys, action `plan_relook`, hint `city_looks` and the new commands.
- Journal/guide texts that describe building levels or wall grades could mention the new looks.
- If an older settlements lang generator is re-run, run `py -I tools/gen/settlements_w8_looks_lang.py` afterwards.