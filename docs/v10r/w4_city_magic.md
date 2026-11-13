**city_magic report** — all three checks pass after my changes (`bash tools/check-module.sh settlements` → TUDO OK, `bash tools/check-module.sh city_arcana` → TUDO OK, `bash tools/check-tree.sh city_magic` → COMPILOU OK). Nothing was run in game.

## What was missing (found on resume)
The earlier agent had written the Java core of both deliverables. Still missing, now done:
- **Player text:** no lang keys existed for any of it (glyph names and descriptions, building-magic messages, Barrier recipe messages, two hints), in either language.
- **Glyph icons:** the three new glyphs had none.
- **City requirements on Raise Work:** it did not check residents or happiness.
- **Debug commands:** building magic had none.
- **FX:** the Work Surge glow used a raw particle loop instead of the batched FX path.

## Files touched
Earlier agent (by timestamps, 23:44–23:49):
- `src/main/java/com/renanpontes/isekaimahou/feature/settlements/magic/CityMagic.java` (new)
- `feature/settlements/`: `SettlementsModule.java`, `CityActions.java`, `CityPlanning.java` (`terrainByMagic`), `build/Project.java` (`magicMult`, `magicUntil`, memory only), `build/ConstructionEngine.java`
- `feature/city_arcana/`: `WardRecipe.java` (new), `CityArcanaData.java`, `CityArcanaCommands.java`, `CityArcanaModule.java`

Me:
- `feature/settlements/magic/CityMagic.java` — Raise Work now needs at least one builder and happiness not miserable; new `debug(...)` entry.
- `feature/settlements/CityDebugCommands.java` — `/mahou city magic ...`.
- `feature/settlements/build/ConstructionEngine.java` — glow goes through `ElementFx.burst`.
- `tools/gen/settlements_v14_lang.py` — new city_magic block and Arcane Barrier description, re-run; `src/main/lang/settlements/{pt_br,en_us}.json` gained 22 keys and 3 reworded descriptions, none removed.
- `tools/gen/city_arcana_lang.py` (new) → `src/main/lang/city_arcana/{pt_br,en_us}.json`, +27 keys, none removed.
- `tools/gen/city_magic_icons.py` (new) → `textures/gui/glyph/city_hasten.png`, `city_shape.png`, `city_erect.png`.

## New ids
- **Glyphs** (Arcane effects, taught by the Arcane path):

  | Glyph | Name | Unlock | Does |
  |---|---|---|---|
  | `isekai_mahou:city_hasten` | Impulso de Obra / Work Surge | level 5 | Speeds up nearby works that already have a builder, up to 4×. |
  | `isekai_mahou:city_shape` | Moldar Lote / Shape Plot | level 10, Circle 2 | Levels an empty plot; Amplify raises it, Dampen lowers it. |
  | `isekai_mahou:city_erect` | Erguer Obra / Raise Work | level 18, Circle 3 | Finishes the nearest small work instantly for half of max mana. |

- **Hints:** `city_magic_build`, `city_arcana_ward`.
- **Saved data:** optional key `Wards` in `isekai_mahou_city_arcana` (per city: `Recipe`, `Element`, `By`, `Mana`). No existing key renamed or removed.
- **Commands:**
  - `/mahou city_arcana ward [record <1-4> | clear | pour]`
  - `/mahou city_arcana debug mana <n>` and `debug pulse` (op)
  - `/mahou city magic hasten|erect|shape [n]` (op)

## What it does
- **Build with magic:** the three effects work only in a founded city where the caster is Lord or council, and never in `home_realm`. They act only on works that planning already accepted, so center level and plot unlocks still apply.
- **Barrier defense:** the Lord records a spell on the Arcane Barrier catalyst (click with a Spell Scroll, or empty-handed for chat buttons per slot Z/X/C/G).
  - During a raid, or with an enemy player inside, the Barrier applies the recipe's effects and status-type Domain laws to invaders every 2 s.
  - It spends the Mana Core, which refills slowly and by donation (sneak + click, or the chat button).
  - It refuses block-changing effects and never targets residents, allies or bosses.

## How to test
1. `/mahou admin login <senha>`, found a city, `/mahou city level 3`, plan a small house.
2. `/mahou city magic hasten 2` near the site: expect the action-bar message and faster progress.
3. `/mahou city magic erect`: the house finishes and half of max mana is spent. Try it on a wall (refused as too big), and after `/mahou city approval 10` (refused as unhappy).
4. Stand on an empty unlocked plot: `/mahou city magic shape`, then `shape 1`, then `shape -1`.
5. Real path: `/mahou glyph learn isekai_mahou:city_hasten`, build Toque + Impulso de Obra in the Grimoire, cast on the site. Check the three icons and descriptions.
6. Barrier: stand on an empty plot, `/mahou city_arcana debug grant arcane_ward 3`, `/mahou city_arcana debug refresh`.
7. Put a spell such as Projétil + Lentidão + Queimar in slot Z, then `/mahou city_arcana ward` and click [Z], or `/mahou city_arcana ward record 1`.
8. `/mahou city_arcana debug mana 400`, spawn zombies inside the city, `/mahou city_arcana debug pulse`. For the real trigger use `/mahou city raid now`.
9. Check that villagers and city NPCs are untouched and no block changes. Try recording a spell with Quebrar: it must be refused.
10. `/mahou city_arcana debug outsider` makes you count as an enemy player, to test the PvP trigger.

## Not done / not verified
- No in-game run of any of it: Grimoire display, hints, the catalyst click area (3×3 column, 1–20 blocks above the Barrier's centre), zap FX and effect behaviour without a caster are unverified.
- The debug `grant` places only the building data, not blocks, so the catalyst click needs a really built Barrier; the commands work either way.
- The Barrier recipe has no screen of its own: status and buttons are chat lines, to keep the screen clean.
- Raise Work's size limit (300 + 180 × Circle template blocks) was not checked against real building sizes; it may need tuning.
- The happiness gate on Raise Work (refused below 20) is my reading of "sem pular os requisitos (felicidade, moradores)". Work Surge and Shape Plot have no happiness gate.
- No journal chapter was written for building magic.

## PEDIDOS
- **integration** (`docs/ARCHITECTURE.md` catalog): add `settlements/magic/CityMagic`, `city_arcana/WardRecipe`, the three glyph ids, the `Wards` saved key and the two hints.
- **story** (`feature/journal`): a short entry or tip for building magic and for recording the Barrier's defense.
- **coordinator:** if `tools/gen/settlements_lang.py` is ever re-run, re-run `settlements_v14_lang.py` afterwards. `city_arcana_lang.py` is independent.