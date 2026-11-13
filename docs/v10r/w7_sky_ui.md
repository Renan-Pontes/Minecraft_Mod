**sky_ui report** — every item of the section is in and compiles (`check-module.sh settlements` → TUDO OK, `check-module.sh city_arcana` → TUDO OK, `check-tree.sh sky_ui` → COMPILOU OK). Nothing was run in game or in a JVM; only the waterfall and decoration geometry was checked, in a Python port of the island maths.

One `check-tree` run in between failed with 13 errors in `TalentCombat` (another agent's file, mid-edit); the retry passed.

## Files touched
Java under `src/main/java/com/renanpontes/isekaimahou/feature/settlements/`.
- **New:** `SkyFounding.java`, `SkyDecor.java`, `network/SkyPackets.java`, `client/SkyPreview.java`, `client/SkyPanel.java`, `client/SkyFx.java`, `tools/gen/settlements_w7_sky_lang.py`.
- **Rewritten:** `client/FoundingScreen.java`.
- **Edited:** `client/CityPlacer.java` (terrain choice, sky hologram).
- **Small edits in shared files:**
  - `CityPlots.probe` — 2 lines
  - `SettlementService.tickHeart` — 1 line
  - `network/SettlementsNetwork.register` — 1 line
  - `client/SettlementsClient` — 1 line
  - `client/SettlementPanel` — 4 lines, none in the works list
  - `CityDebugCommands` — `sky decor`
- **Lang:** `src/main/lang/settlements/{pt_br,en_us}.json`, 21 keys added (`sky.ui.*`, `sky.place.*`, `sky.decor.cmd.*`). The two hint texts were already written by sky_access; my generator only adds them if missing.

## New ids
- **Packet:** `isekai_mahou:settlements_sky_near` (server to client).
- **Saved data:** `data/isekai_mahou_sky_decor.dat` (tracked decoration blocks per city).
- **Probe fields:** `found` and `found_open` now carry the terrain in `arg`; the answer adds `Sky` and `SkyY`.
- **Commands (op):** `/mahou city sky decor`, `sky decor now`, `sky decor redo`.
- **Hints used:** `city_sky_found`, `city_sky_updraft`, `city_sky_fall` (no new ids).

## What it does
- **Founding screen:** two buttons, "No chão" / "Ilha Celeste (SkyLand)", on the name-label row.
  - Without a Sky Core the sky button is greyed, with "Requer um Núcleo Celeste" beside it and the recipe in the tooltip.
  - One short phrase beside the buttons shows the server's altitude or the reason it cannot be founded there; "Fundar" is disabled while there is a known problem.
  - Style, name and terrain are kept while the Charter stays in hand, so Esc goes back to the preview and returns without losing them.
  - Cards lose 12 px of height; text that no longer fits shows whole in a hover tooltip.
- **Preview:** with the sky chosen, the hologram shows a ground marker, a beam up, the real island outline at the server's altitude, the belly cone, plaza, 24 lots and wall line. Move and rotate work as before.
  - **Tab** toggles ground/sky in the preview for players holding a Sky Core. I used Tab because V is the racial-ability key.
- **Panel:** two lines at the bottom-left of Geral — "Ilha Celeste · altitude N", then either the trait ("Cidadãos não sofrem dano de queda aqui", matching sky_access's `onFall`) or "A terra está se formando: N%" with a thin bar.
  - Planejar: lots still waiting for land get a bluish veil and "Aguardando a terra se formar" in the tooltip.
  - The Obras row text was already done by sky_island.
- **Island beauty (`SkyDecor`):** built once the land of a radius finishes, 48 blocks per tick from the shared budget, loaded chunks only, air or loose plants only.
  - 2–4 waterfalls: the source is boxed on all four sides so a single column falls into its own rimmed 3×3 pond 12–16 blocks below. Ice instead of water for Nordic and cold biomes.
  - 2–4 satellite islets with the style's tree (cherry, snowy spruce, acacia, azalea, dark oak, oak).
  - Belly: glowing crystals plus roots, glow-berry vines, dripstone, weeping vines or spore blossoms, by style.
  - Everything is tracked; when the island starts growing to a new shape the old pieces are removed (only blocks still ours) and rebuilt on the new rim.
- **Mist (`SkyFx`):** client-only cloud wisps along the rim and spray at waterfall feet, scaled by `FxQuality`. No camera shake added.

## How to test
1. `/mahou admin login <senha>`, `/give @s isekai_mahou:founding_charter`, hold it: normal ground preview.
2. Right-click twice to open the screen: the sky button is greyed; hover it for the recipe.
3. `/give @s isekai_mahou:sky_core`: the hint line now ends with "Tab: Ilha Celeste". Press Tab, look up, pin, move with arrows, rotate with R.
4. Right-click to open the screen, pick a style, check the altitude phrase, press Esc and come back (choices kept), then "Fundar a cidade".
5. `/mahou city sky finish`, wait about 5 s, then `/mahou city sky decor` (state), `decor now`, `decor redo`. Fly around the rim and under the belly.
6. B → Cidade → Geral: the two lines at bottom-left.
7. `/mahou city sky grow`: old decorations vanish; in Planejar the outer lots are bluish. After `sky finish` the decorations return on the new rim.
8. Found a ground city elsewhere: screen and preview should behave as before.

## Not done / not verified
- **No in-game run:** screen layout, hologram look, panel line positions, mist density and the decoration look are all unverified.
- **Waterfall containment** was reasoned from the decompiled `FlowingFluid.spread`, not observed. If a player breaks the spout or pond rim, water can reach the ground.
- **Borderline-cold biomes:** a water source can freeze, leaving that fall dry.
- **Style change (Reforma):** decorations keep the old style until `/mahou city sky decor redo`.
- **Unloaded chunks:** decorations there wait until loaded, both for building and removal; `decor redo` can leave old blocks behind in them.
- **Tab** also shows the player list while held in multiplayer.

## PEDIDOS
- **journal / guide:** a short chapter on the Sky Island (Sky Core recipe, choosing it in the Charter, Tab in the preview, the Updraft).
- **docs/ARCHITECTURE.md:** add the packet `settlements_sky_near` and the saved file `isekai_mahou_sky_decor` to the catalog.