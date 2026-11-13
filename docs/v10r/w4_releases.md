**Job "releases" (wave 4, stage 2): nothing is missing in code — the earlier agent had already finished every deliverable before it was cut off, and I changed no files.** The relayed user request is the status question "O que falta?", so I audited and compiled rather than adding code. Nothing has been run in game.

## Compile checks (run now)
- `bash tools/check-module.sh class_shinigami` → TUDO OK
- `bash tools/check-module.sh class_quincy` → TUDO OK
- `bash tools/check-module.sh class_hollow` → TUDO OK
- `bash tools/check-tree.sh releases` → COMPILOU OK (all, 3045 files)
- The only warnings are dynamic key prefixes (`screen.isekai_mahou.release_forge.`, `screen.isekai_mahou.tforge.slot.`, and older Hollow ones).
- Lang: 0 duplicate keys across all folders; pt_br and en_us have the same 14 keys.

## What is on disk (all by the earlier agent)
All paths are under `c:/Users/renan/Music/ROCKET/RocketSimVis/Minecraft_Mod/`.
- **`src/main/java/com/renanpontes/isekaimahou/feature/class_shinigami/`**
  - New: `ReleaseForge.java`, `client/ReleaseShaper.java`.
  - Edited: `Release.java`, `ShinigamiEvents.java`, `ShinigamiCommands.java`, `client/ShinigamiTab.java`.
  - Shikai and Bankai each call `TransformLaws.begin/end` with source paths `shikai` / `bankai`; Shikai's recipe ends when Bankai starts.
  - `Release.java` also gained a fix: a Bankai that lost its state on a dimension change is now sealed.
- **`.../feature/class_quincy/`**
  - New: `QuincyForge.java`, `client/ReleaseShaper.java`.
  - Edited: `QuincyVollstandig.java`, `QuincyEvents.java`, `QuincyCommands.java`, `client/QuincyTab.java`.
  - Vollständig uses source path `vollstandig`.
- **`.../feature/class_hollow/`**
  - New: `HollowForge.java`, `client/ReleaseShaper.java`.
  - Edited: `HollowEvents.java`, `HollowCommands.java`, `client/HollowTab.java`.
  - Button, hint and debug only; it does not call begin again, as stage 1 asked.
- **`src/main/lang/releases/pt_br.json` and `en_us.json`**: the 14 shared keys.
- The "Moldar liberação" button is in each class tab (two side-by-side buttons for a Shinigami with Bankai). It is greyed out, with the reason in the tooltip, when the player lacks the Transformation form. It opens the Vows tab at the slot.

## New ids
- Hints: `shinigami_shape_release`, `quincy_shape_release`, `hollow_shape_release`.
- Lang prefixes: `screen.isekai_mahou.release_forge.*`, `message.isekai_mahou.release_forge.debug`.
- Op commands: `/mahou shinigami forge`, `/mahou shinigami shikai`, `/mahou quincy forge`, `/mahou hollow forge`.
- No new items, glyphs, NBT keys or slots; it reuses stage-1 slots `shikai`, `bankai`, `vollstandig`, `hollow_resurreccion` and flags `tforge.slot.<id>`.

## How to test in game
```
/mahou admin login <senha>
/mahou level 60
/mahou circle 6
/mahou tforge grant
```
1. As a Shinigami with Shikai: menu B → Shinigami → "Moldar liberação", pick a Transformation recipe in the Vows tab (or `/mahou tforge use shikai 1`).
2. `/mahou shinigami shikai` to release, then `/mahou shinigami forge` should show "active now: yes"; hit a mob to see the recipe's strike effects.
3. Cast the Bankai form and run `/mahou shinigami forge` again: the Shikai line should be off and the Bankai line on.
4. Quincy: cast Vollständig, then `/mahou quincy forge`.
5. Hollow (Arrancar): release the Resurrección, then `/mahou hollow forge`.
6. With no recipe chosen, every release should behave exactly as before.

## Not done / not verified
- **No in-game run**: button layout on small windows, the hint firing, mana charge and the Shikai → Bankai hand-over are untested.
- **No recipe slot for the similar forms**: Máscara do Vazio (`shinigami_visored`), Vasto Lorde (`shinigami_vasto_lorde`) and Letzt Stil (`quincy_letzt_stil`). They are forms cast inside a spell and accept laws there, but have no "Moldar" slot. This is the one open decision against the contract's "e parecidas".
- With a slot recipe, duration and cooldown stay those of the base release; only mana comes from the recipe (stage-1 behaviour).
- The three `ReleaseShaper` classes are near-identical copies, one per module.
- The lang keys live in `src/main/lang/releases/`, not in the three class lang folders. The gradle merge is recursive, so they are picked up.

## PEDIDOS
- **Coordinator**: decide whether Máscara do Vazio, Vasto Lorde and Letzt Stil should get their own slots (via `TransformForge.registerSlot`), then run the gradle build and the in-game test above.
- **integration** (`docs/ARCHITECTURE.md`): add `ReleaseForge`, `QuincyForge`, `HollowForge`, the three hint ids and the four debug commands to the catalogue.
- **story/journal**: one Diary line saying class releases accept a Transformation recipe through "Moldar liberação".