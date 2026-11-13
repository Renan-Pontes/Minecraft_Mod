**city_plan report** — both checks pass (`bash tools/check-module.sh settlements` → TUDO OK, `bash tools/check-tree.sh city_plan` → COMPILOU OK). Nothing was run in game; only the placement rules were exercised headlessly (id round-trip, no grid plot blocked by the new rules, overlap/plaza/outside cases, NBT round-trip).

## Files touched
All under `src/main/java/com/renanpontes/isekaimahou/feature/settlements/`, plus lang and its generator. `feature/city_arcana/**` was not touched.
- **New:** `CityPlots.java` (free-plot rules, move, preview probes), `client/CityPlacer.java` (in-world hologram/ghost and controls).
- **Edited:** `build/CityLayout.java`, `build/CityTerrain.java`, `build/ConstructionEngine.java`, `build/Project.java`, `Settlement.java`, `SettlementService.java`, `SettlementsModule.java`, `CityPlanning.java`, `CityActions.java`, `CityViewExtra.java`, `CityDebugCommands.java`, `network/SettlementsNetwork.java`, `item/FoundingCharterItem.java` (comment only), `client/FoundingScreen.java`, `client/SettlementsClient.java`, `client/SettlementPanel.java`.
- **Lang:** `tools/gen/settlements_v14_lang.py` (new city_plan block, re-run) → `src/main/lang/settlements/{pt_br,en_us}.json` (+26 keys, none removed; `item.isekai_mahou.founding_charter.use` and `ui.plan.hint` reworded).

## New ids
- No registry ids. Packets `settlements_probe` (C→S) and `settlements_probe_result` (S→C); `settlements_found` gained an `int rot` field.
- Optional NBT only: Settlement `PlazaRot`, Project `After`. No existing key renamed or removed.
- Free plots need no new storage: the plot id encodes the position (`FREE | rot<<20 | (dx+512)<<10 | (dz+512)`), so the existing `Buildings`, `BLevels`, `BStyles`, `PlotBase`, `Damaged` maps hold them. Grid ids are unchanged.
- Panel actions `plan_free` ("key|dx|dz|rot|style") and `plan_move` ("cell|dx|dz|rot"); hints `city_preview`, `city_free_plot`.

## What it does
- **Preview before founding:** with the Charter in the main hand, a hologram follows the crosshair (up to 72 blocks) showing the plaza, the 24 starting plots and the first wall line, draped on the ground.
  - Green = good, yellow = big slope, blue = water, all red = cannot found there (reason shown in one line above the hotbar).
  - Right-click pins; arrows move 1 block (Ctrl = 4); R or sneak+scroll rotates the plaza; right-click or Enter opens the style/name screen; sneak+right-click releases.
  - The Charter is spent only when the city is actually founded.
- **Free building position:** Planejar has a new "Posição livre…" button: pick a building and style, the screen closes, and a ghost (15×15 lot, rotated building box, entrance arrow) is placed with the same controls. Left-click or Backspace cancels.
  - The server decides green/red: inside the city area, off the plaza, off wall bands (built or future), 16 blocks centre-to-centre from any other building or work, and only where the nearest grid plot is already unlocked.
  - A spot exactly on a grid plot with its default facing becomes that grid plot. Grid plots covered by a free building show as blocked and are skipped by auto-placement.
  - Free buildings appear on the plan map at their real position (light-blue border, gold tick on the entrance side) and support upgrade, repair, replace and demolish like any plot.
- **Move / rotate a finished building:** new button on a built plot, costing 10 + 10 × building level coins from the treasury. Builders demolish the old lot, then rebuild at the new spot with the same level and style. Chests must be empty; the rebuild cannot be cancelled.

## APIs for city_magic (stage 2)
- `CityPlots.plotAt(s, x, z)` — plot (free or grid) at a world column, or -1.
- `CityPlots.spotProblem(s, dx, dz, ignore)`, `cellProblem(s, cell, ignore)`, `gridBlocked(s, cell)`, `occupied(s)`, `freeOccupied(s)`, `cellFor(dx, dz, rot)`, `terrainAt(sl, x, z, refY)`.
- `CityPlots.build(sp, s, key, styleId, dx, dz, rot)`, `move(sp, s, from, dx, dz, rot)`, `moveProblem`, `moveCost(level)`.
- `CityLayout.isFree / freeCell / freeDx / freeDz / freeRot / offsetX / offsetZ / nearestIndex`; `cellCenter` and `facingHeart` work for both kinds of plot.
- `CityPlanning.queueTerrain(s, cell, y)` and `clearCell` are now package-visible; `ConstructionEngine.waiting(s, p)`; `Settlement.plazaRotation()`.
- `city_arcana` needs no change: it reads building positions through `CityLayout.cellCenter`, which handles free plots.

## How to test
1. `/mahou admin login <senha>`, hold a Founding Charter: check the hologram over flat land, water, a hill, and near a village (red). Pin, nudge, rotate, confirm, and found.
2. `/mahou city level 3`, `/mahou city finish`, open the heart → Planejar → "Posição livre…" → pick a house → place between two grid plots (green) and on top of a building or the plaza (red). `/mahou city finish`.
3. `/mahou city free house_small 1` queues a free building centred where you stand (rotation 0–3); `/mahou city plots` lists them.
4. Select a built plot → "Mover / girar"; or stand in a plot and run `/mahou city rotate` (in-place 90° move, cost refunded). Then `/mahou city finish`.
5. Old world: load a save with a city and confirm the grid plots, works in progress and plaza are unchanged.

## Not done / not verified
- No in-game run: hologram look, key handling, ghost alignment with the real footprint and the map overlay are unverified.
- Free plots get automatic link stairs only when exactly one plot away from a levelled neighbour on the same axis; otherwise they take the nearest levelled plot's height and rely on the 1-block skirt.
- A moved building may come back as a different variant of the same building (variant is derived from the plot id).
- With the Charter in the main hand, right-click on blocks (chests, doors) is swallowed by the preview; entities still work. Charter in the off hand uses the old direct flow with no preview.
- R and the arrow keys are read raw while a preview is active (no entries in the controls menu).
- Founding/placing reach on the server went from 12 to 96 blocks to match the preview.

## PEDIDOS
- integration (`docs/ARCHITECTURE.md` catalog): add `CityPlots`, the free-plot id scheme in `CityLayout`, and the hints `city_preview` / `city_free_plot`.
- Any tutorial or journal text that says the Charter founds on a plain right-click should mention the preview (pin, then confirm).