**city_pause report** — the whole section is in and compiles, but nothing was run in game or in a JVM. `check-module.sh settlements` → TUDO OK, `check-module.sh city_arcana` → TUDO OK, `check-tree.sh city_pause` → COMPILOU OK (one retry each for settlements and the tree, because another agent was mid-edit on `SkyFx`).

## Files touched
All Java under `C:/Users/renan/Music/ROCKET/RocketSimVis/Minecraft_Mod/src/main/java/com/renanpontes/isekaimahou/feature/settlements/`.
- **New:** `CityPause.java` (rules, dismantle, refund, view), `CityPauseCommands.java`, `client/CityPauseUi.java`, `tools/gen/settlements_w7_pause_lang.py`.
- **Edited (small, pointed):** `build/Project`, `build/ConstructionEngine`, `CityPace`, `SettlementView`, `SettlementService`, `CityActions`, `CityPlots` (one line), `CityDebugCommands`, `magic/CityMagic`, `client/SettlementPanel`.
- **Lang:** `src/main/lang/settlements/{pt_br,en_us}.json` — 61 keys added, none removed or changed.

## New ids
- **Optional Project NBT:** `Paused`, `Undo`, `Moved` (all default false).
- **Actions:** `work_pause`, `work_resume` (arg = queue index or `all`). The existing `cancel` action now works at any stage.
- **Hint:** `city_pause`.
- **Commands (op):** `/mahou city pause [n|all]`, `/mahou city resume [n|all]`, `/mahou city cancel [n]`. `n` is 1-based in Obras order; `cancel` with no number only lists the queue.
- **API:** `CityPause.crewed(s)` is the list of works that have a crew right now, shared by the engine, the panel, hammering and Impulso de Obra.

## What it does
- **Pause / resume:** any work in the queue, per row (small ⏸/▶ button) or with "Pausar tudo / Retomar tudo" in the Obras header.
  - A paused work gets no days (including catch-up while unloaded), takes no materials, hammering or work magic, and cannot be rushed.
  - Its builders go to the other works; scaffolding and stage stay; the sign reads "X — Pausada · dia 2 de 3".
  - The Diário logs pause and resume. The row shows a bluish "⏸ pausada"; no new HUD.
- **Cancel ("Desistir", two clicks):**
  - A work that has not touched the world just leaves the queue, with a full refund.
  - A started new building or wall is dismantled by the builders, back to front, inside the city block budget. Only blocks that still match the template are removed.
  - A started upgrade or restyle is dismantled the same way, then a normal "Consertar X" repair (½ day, can be rushed) puts the old level and style back.
  - Refund is coins only: restyle cost and rank-up cost, times the fraction still missing.
- **Pause only (button greyed, reason in the tooltip):** founding plaza, repairs, wall renovation, Reform plaza, a rank-up once the plaza was touched, a moved building and its demolition.
- **Sky city:** nothing digs. A lowered lot that is paused or cancelled gets its floor sealed where the island has land.
- `/mahou city finish` resumes paused works and completes dismantles at once.

## How to test
1. `/mahou admin login <senha>`, found a city, `/mahou city finish`, queue two houses and a market.
2. `/mahou city pause 1`, then `/mahou city days 2`: work 1 keeps its day, the others advance. Check the sign, the Obras row and the Diário.
3. Restart the server: it is still paused. `/mahou city resume 1`.
4. `/mahou city pause all`, `/mahou city pace` (⏸ marks), `/mahou city resume all`.
5. `/mahou city days 1`, wait for blocks, then `/mahou city cancel 1`: the site is dismantled and the lot is free again.
6. Upgrade a building, let it get past clearing, cancel it: dismantle, then "Consertar X" brings the old level back.
7. Restyle after `/mahou city style <estilo>`, then cancel: partial refund. `/mahou city tier` rank-up: cancel before it starts gives the full cost back; after blocks it is pause-only.
8. Queue a wall, let some pieces go up, cancel it.
9. Panel: Obras tab, ⏸ per row, "Desistir" twice, "Pausar tudo".
10. Repeat steps 2–6 on `/mahou city sky found oriental`, including a house built at height −4 near the rim.

## Not done / not verified
- **No in-game run:** panel layout, the dismantle on real buildings, and the ⏸ glyph (relies on Minecraft's fallback font) are unverified.
- **Materials are not returned:** items players delivered to a cancelled work are gone; only coins come back.
- **Terrain is not restored:** a cancelled building's floor becomes yard grass or sand; a cancelled wall keeps its base course and foundation.
- **Replaced buildings:** a build queued over an existing building and then cancelled does not bring the old one back (it was already unregistered when queued, as before).
- **Demolitions:** cancelling a plain demolition midway leaves the half-demolished ruin, as before.
- **Old moves:** a building move started before this version is recognised only while it still waits for its demolition; after that it can be cancelled like a normal build.
- **Not pausable:** the gradual rank-up renewal of streets and facades is not a queue work.
- **"Pausar tudo"** only pauses what is in the queue at that moment; works queued later run normally.
- **Builder chat line** still talks about the first work in the queue, even if it is paused.

## PEDIDOS
- `docs/ARCHITECTURE.md`: add `CityPause` (pause, cancel, `crewed`), the three Project NBT keys, the two actions, hint `city_pause` and the commands.
- Journal/guide texts about city works could mention pausing and giving up.
- If any older settlements lang generator is re-run, run `py -I tools/gen/settlements_w7_pause_lang.py` afterwards.