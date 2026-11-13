**transform_grimoire report (wave 9)**: the Transformation and its Vows now live inside the spell Grimoire, opened from the Transformação glyph. Everything compiles; nothing was run in game.

Owner's words (2026-11-13): *"vamos tacar a transformação na area do grimorio de magias, colocar como um glifo e colocar como 'vows' la dentro ao invés de tudo separado..."*

## What the player sees and does now
- **Opening the editor.** In menu B → Magia → Grimório, the **Transformação** glyph is pinned as the first tile of the palette, with a violet frame. It shows under the "Tudo" and "Formas" filters, the Arcane chip, and the search words "transformação / votos / moldar".
  - Click it (or press Enter while it is selected): the whole Grimório tab becomes the **Transformation editor**.
  - Drag it into the program: a normal Transformação spell, as before. This only works if the player knows the glyph; otherwise a toast explains.
- **The editor** (title "✦ Transformação", button "◂ Grimório" top-right, Backspace also goes back):
  - **Left:** the list of every transformation the player has: V-key racial form, Shikai/Bankai/Vollständig/Resurrección, Druid/Enchanter/Necro... forms, Kaioken and so on. Each shows its recipe status.
  - **Right:** the editor for the selected one: the recipe strip (body + pieces), the summary, and four buttons.
    - Salvar.
    - Limpar.
    - **Do Grimório**: cycles saved recipes that start with the glyph, and the draft open in the Grimório comes first.
    - **Virar magia** (new): puts "Transformação + these pieces" into the Grimório draft and returns there, so it can be saved on Z/X/C/G and cast like any spell. It is greyed out until the glyph is known.
  - **Palette tabs:** **Efeitos | Aumentos | Votos**.
- **Votos tab** (replaces the old "Leis" icon grid): a readable list of all 13 Transformation Laws. Each row has a checkbox, icon, name, cost and boon; unknown laws are grey and their tooltip says where to learn them.
  - **Racial (V) and Resurrección:** ticking a vow is the same "loose vow" as the old Votos tab. It applies at once with no Save and costs no glyph slot; the server still checks the limit, Circle and level, and refuses changes while you are transformed.
  - **Every other transformation:** ticking puts the law into the recipe, then you click Salvar.
  - Laws already in a recipe show as ticked anywhere.
  - The header shows "Votos: n de max".
- **Removed from view:** the B → Personagem → **Votos** tab is no longer registered. Its "Receita montada" picker and "Votos soltos" view are gone; both are covered by the editor.
- **Shortcuts:**
  - The class buttons "✦ Moldar liberação" (Shinigami, Quincy, Hollow) go through `TransformForge.openShaper`. They now open the Grimório straight into the editor on that slot.
  - The "✦ Transformação" badge in the Grimório status row opens the editor in place.
- **V key, HUD, casting:** unchanged. The V key and class releases still apply the slot recipe and loose vows exactly as before. Nothing was added to the HUD.

## How the glyph is obtained
- **Pinned editor tile, no data change.** The tile shows for anyone who could open the old Votos tab, with the same rule:
  - knows the `transformation` glyph, or
  - knows any Transformation Law, or
  - has any class or fixed transformation slot, or
  - has used the V form (`tforge.slot.race_form`).
  - New: anyone whose race form is unlocked (race level V).
- **Nobody loses access.** Shaping never required knowing the glyph (wave 5), and it still doesn't.
- **Casting is unchanged.** Casting the Transformação as a standalone key spell still needs the glyph to be learned (Arcane path, Circle 5) and keeps all existing costs, cooldowns and Circle checks. I did not auto-teach it, because that would hand a tier-4 Circle-5 form to low-level players.

## Old saves
No data format changed. Slot recipes still live in the `transform_forge` attachment, and loose vows in the `tlaw.race.*` / `tlaw.hollow.*` flags. The editor is only a new front-end that reads and writes them through the existing packets (`tforge_set_recipe`, `SetRacePick`). Old worlds open with every transformation recipe and every vow intact. A remembered `lastTab = "transform_laws"` simply falls back to Status.

## Files changed
- New: `feature/ui_grimoire/client/GrimoireTransform.java`. It is the hook: the `Panel` interface, the install/open/close state and `openAt(slot)`. ui_grimoire does not import transform_laws client classes.
- New: `feature/transform_laws/client/TransformPanel.java`. It is the header and back button around `ShaperView`, plus the `shown(...)` rule.
- Deleted: `feature/transform_laws/client/VowsTab.java`. Its candidate list moved into `ShaperView`.
- Edited:
  - `feature/ui_grimoire/client/GrimoireTab.java`: the pinned tile, click/Enter opens the editor, a full-area render and input routing while the editor is open, widgets hidden, and the badge opens the editor.
  - `feature/transform_laws/client/ShaperView.java`: the Votos list tab, the "Virar magia" button, the candidates now include the Grimório draft, and a `removeAt` helper.
  - `feature/transform_laws/client/TransformLawsClient.java`: no tab registration; it installs the panel and the opener.
  - `feature/transform_laws/TransformForge.java`: javadoc only.
- Lang (pt_br + en_us, keys in parity):
  - `lang/transform_laws`: 17 new keys; 7 texts reworded (5 hints/toasts now point at the Grimório, plus the "Do Grimório" button and its tip).
  - `lang/ui_grimoire`: 7 new keys and 2 updated.
  - `lang/releases`: the "click" text.
  - `lang/story`: the transformation guide text and the Circle-5 journal line, which pointed to "Personagem → Votos".
- Old keys that are now unused were left in place (`screen.isekai_mahou.tlaw.tab`, `tforge.view_*`, `tforge.recipe_*`, `tforge.pal_laws`…).
- No icon was generated; the existing `textures/gui/glyph/transformation.png` is reused.

## Checks
- `bash tools/check-tree.sh grimtf` → **COMPILOU OK (all)**, 3089 files.
- `check-json.py` on the four touched lang folders → 0 errors. pt_br and en_us have the same key sets (transform_laws 193/193, ui_grimoire 498/498).
- `check-module.sh transform_laws` fails **only** because `GrimoireTransform` is new and not yet in `build/classes`. It passes after the coordinator's gradle compile, and the tree check above already covers it.

## How to test in game
```
/mahou admin login <senha>
/mahou level 60
/mahou circle 6
/mahou tforge grant          (teaches the Transformation form + the 13 laws)
/mahou tforge slots
```
1. B → Magia → Grimório: the violet-framed Transformação tile is first in the palette. Hover it to see the tooltip, then click it to open the editor.
2. Pick "Transformação Racial", open **Votos**, and tick a law. It should tick at once, with an action-bar message. `/mahou tlaws status` should list it under the racial picks. Then `/mahou character transform @s`: the vow is sealed.
3. Pick another slot (any fixed transformation you know, e.g. Kaioken, or Shikai as a Shinigami). In Votos, tick a law: it lands in the strip, and the header says to Save. Click Salvar, then `/mahou tforge status` / use the transformation.
4. Efeitos tab: add Incendiar, then Salvar, then V and hit a mob (the strike effect applies).
5. "Virar magia": you return to the spell editor with `Transformação → …` loaded. Save it on Z and cast it.
6. Build `Transformação → Curar` in the Grimório, open the editor, and click "Do Grimório": the draft comes in first.
7. Shinigami/Quincy/Hollow class tab → "✦ Moldar liberação": the Grimório opens in the editor on that slot.
8. "◂ Grimório" and Backspace both return to the spell editor with all widgets back. Closing and reopening the menu remembers which view was open.
9. B → Personagem: the Votos tab is gone.
10. Old world: load a save that had a V recipe and loose vows. Both should show as before in the editor (list status, ticked vows).
11. Fresh character below Circle 5 without the glyph: the tile is visible once the race form is unlocked. Dragging it into the program shows the "need the glyph" toast; "Virar magia" is greyed with the reason.

## Not done / not verified
- **No in-game run**: layout of the editor inside the Grimório on small windows (the Votos list in particular), tooltip placement, and the widget hide/restore on open/close are all untested.
- **The racial V form cannot be put on a spell key.** "Virar magia" makes a cast Transformação spell with the same pieces, a 30 s base form; it is not the race form itself. The V key remains the way to trigger the racial form.
- **Two systems remain for V/Resurrección vows.** They can still be loose vows (instant, free) or laws inside the recipe (cost a glyph slot and mana). Both are shown as ticked in the Votos tab.
- **The editor stays open between menu openings.** If a player leaves it open and later opens the Grimório from the category bar, they land in the editor and must click "◂ Grimório". This is intentional, like the other Grimório remembered state.
- Escape still closes the whole menu rather than going back to the spell editor.
- The class modules' `ReleaseShaper` javadoc still says "aba Votos". Their fallback `StatusScreen.open("transform_laws")` now lands on Status, but it only runs if the reflective `openShaper` call fails. Those files are outside my area and were not touched.

## PEDIDOS
- **Coordinator**: run the gradle build (new class `GrimoireTransform`), then the in-game steps above.
- **integration** (`docs/ARCHITECTURE.md`):
  - The Votos tab (`transform_laws`) is retired.
  - New `ui_grimoire.client.GrimoireTransform` (`install`, `openAt`, `Panel`).
  - New `transform_laws.client.TransformPanel`.
  - `TransformForge.openShaper` now opens the Grimório editor.
