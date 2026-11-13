**city_tiers report** — all checks pass (`check-module.sh settlements` → TUDO OK, `check-module.sh city_arcana` → TUDO OK, `check-tree.sh city_tiers` → COMPILOU OK). Nothing was run in game. The only runtime check was a headless test that generated all 36 wall pieces (3 styles × 4 grades × 3 pieces) and confirmed footprints, empty gate openings and complete palettes.

## Files touched
All under `src/main/java/com/renanpontes/isekaimahou/feature/settlements/`, plus lang and its generator.
- **New:** `CityTier.java`, `CityRenewal.java`, `build/TierLook.java`, `build/TierWalls.java`.
- **Edited:** `Settlement.java`, `SettlementService.java`, `CityPlanning.java`, `CityEconomy.java`, `CityGrowth.java`, `CityViewExtra.java`, `CityDebugCommands.java`, `CityDecor.java`, `CityCaravans.java`, `CityDefense.java`, `build/CityTerrain.java`, `build/CityLayout.java`, `build/ConstructionEngine.java`, `build/Project.java`, `build/Templates.java`, `client/SettlementPanel.java`.
- **Lang:** `tools/gen/settlements_v14_lang.py` (new wave-5 block, re-run) → `src/main/lang/settlements/{pt_br,en_us}.json`. No keys removed; `ui.center.tip` and `ui.def.calm` reworded.

## New ids
- No registry ids. The rank is the existing center level 1–5, now named **Acampamento → Vila → Burgo → Cidade → Capital** (Camp, Village, Town, City, Capital).
- Optional Settlement NBT: `Look`, `LookGoal`, `LookCursor`, `WallLooks`, `TierBlocks`, `TierDay`. Nothing renamed or removed.
- Project kind `tier` (wall renovation).
- Generated template ids `settlements_wall/<style>.<grade>.<piece>` and `settlements_swap/<oldStyle>.<oldGrade>.<newStyle>.<newGrade>.<piece>`.
- Hint `city_tier`.
- Commands (op):
  - `/mahou city tier` — rank, renewal progress, requirements and gains.
  - `/mahou city tier up` and `/mahou city tier <1-5>` — skip requirements, with ceremony.
  - `/mahou city renew` (gradual) and `/mahou city renew now` (instant).

## What it does
- **Name:** shown on the place sign and map ("Brisa · Vila"), the panel header, the upgrade banner and a server-wide announcement.
- **Whole-city renewal:** on a rank change, and once for existing cities, the builders go lot by lot from the plaza outward.
  - Streets are repaved (dirt → gravel → stone → paved → noble).
  - Lamp posts change per rank, with corner lamps from Burgo.
  - Facades get flowers or hedges under windows, banners beside doors, doormats and garden corners.
  - The plaza gets a new kerb, a four-pillar Heart Monument that grows each rank, and fountains from Burgo.
  - It runs one lot every 0.5–2 s, drawing on the same per-tick block budget as construction.
  - Player blocks are kept: decoration goes only into air, is recorded, and only recorded blocks are swapped later.
- **Walls:** palisade → stone → fortified (roofed towers, 5×5 gate) → royal, on the same footprints as before.
  - New walls are built at the rank's grade.
  - Existing walls are renovated one at a time as a queued work that removes only the builders' own blocks.
  - Higher-grade wall towers shoot farther and harder.
- **Requirements** (on top of the existing ones): two key buildings per rank, city size (7×7, 9×9, 11×11), a treasury cost (40, 150, 400, 900 coins, spent on upgrade) and one grand project each:
  - Vila: hold a festival.
  - Burgo: first wall.
  - Cidade: win 2 sieges (6 caravans if raids are disabled in the world).
  - Capital: 2 walls.
- **Unlocks:** the panel lists new buildings, building level cap, +1 builder, more rings (7×7, 11×11, 17×17, 25×25), more residents, +1 guard, peddlers at Burgo and Capital, +1 caravan at Cidade, wall grade and look.
- **Panel:** new "Porte" sub-tab under Geral with the rank trail, the missing requirements, the upgrade button with cost, the gains and the renewal progress. Geral itself now shows only a one-line summary.
- **Ceremony:** 60-tick non-locking cutscene (skippable), fireworks, festival decorations until the end of the day, bell.

## Where a style plugs in (for city_styles)
`build/TierLook.java` is the single plug point: build a `TierLook.Look` and call `TierLook.register(style, look)`. A style with no registered look falls back to Kingdom, so new enum values work immediately.

| Field | Controls |
|---|---|
| `street[1..5]` | street palette per rank (weighted, via `mix`) |
| `lamp[1..5]` | lamp post column, bottom to top; last block must be a light |
| `plazaEdge[1..5]` | plaza kerb |
| `pillar`, `cap`, `light` `[1..5]` | Heart Monument |
| `fountainRim`, `fountainPost` | plaza fountain |
| `hedge`, `flowers`, `pots`, `wallBanner`, `banner`, `carpet` | facade trim and gardens |
| `wall[1..4]` | wall, tower and gate palette per grade (via `wallPal`) |

- Wall shapes are shared by all styles in `TierWalls`. A style that wants its own gate (e.g. a torii) should branch inside `TierWalls.gatehouse`.
- Style change ("Reforma") is already supported for walls: `CityRenewal.queueWall` compares the saved "style:grade" of each wall with the city's, and `CityLayout.wallSwapPieces` swaps between styles. After changing `s.style`, call `CityRenewal.restart(s)`.
- Fountains replace whatever the style's plaza template has in the 3×3 boxes at ±3 from the heart (`Templates.plazaCorner`).
- Still switching on `SettlementStyle` outside `TierLook`: `SettlementStyle.foundation/portcullis`, `CityTerrain.yard/stairs`, `ConstructionEngine.terraceTop/stairs`, `ProcBuildings.pal`, `Blueprint.template` (small-lot palette), NPC textures.

## How to test
1. `/mahou admin login <senha>`, found a city, `/mahou city finish`, build a few houses, `/mahou city finish`.
2. `/mahou city tier` to read requirements and gains; open the heart → Geral → Porte.
3. `/mahou city tier up` — expect the announcement, cutscene, fireworks and the plaza changing first, then lots one by one. `/mahou city renew now` does it all at once.
4. Repeat `tier up` to 5 and compare streets, lamps, monument, fountains and facades.
5. Walls: at rank 2 queue a wall and `/mahou city finish` (palisade). Then `/mahou city tier 4`, wait for "Reforma da muralha I" in Obras, `/mahou city finish` (fortified). Close the gates by decree to check the 5-wide portcullis.
6. Real path: `/mahou city people 30`, `/mahou city approval 80`, `/mahou city festival`, then use the Porte button; the treasury must drop by the cost.
7. Old world: load a save with a city and a wall. The level is unchanged, the sign gains the rank, the city is renewed silently and the wall renovation is queued.

## Not done / not verified
- No in-game run: looks, panel layout, cutscene, fountain water and wall renovation on real terrain are unverified.
- No new NPC types; only more residents, guards and peddlers.
- The plaza footprint does not grow (the center lot is fixed); "bigger" is the kerb, monument and fountains.
- Building walls are not re-skinned; facade work is trim only, and only where the template's door or window is still found in the world.
- A wall piece whose old blocks cannot be matched is left as is.
- Lots without a saved height (pre-1.4) are skipped by the renewal.
- Level-1 cities get torch posts in place of lantern posts.
- Fountain water can spill if a player breaks the rim.
- Building unlock levels were not reshuffled; the panel lists them as they are.
- Requirement numbers and costs are untested for balance.

## PEDIDOS
- **integration** (`docs/ARCHITECTURE.md`): add `CityTier`, `CityRenewal`, `TierLook`, `TierWalls`, the hint `city_tier` and the new commands.
- **journal / guide texts:** anything saying "Aldeia / Vila / Cidade / Metrópole" for the center level should use the new names.