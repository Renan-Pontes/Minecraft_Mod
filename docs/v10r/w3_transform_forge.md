**transform_forge report (stage 1 of 2)** — every deliverable is built and compiles; nothing was run in game (gradle is off-limits).

- `bash tools/check-module.sh transform_laws` → TUDO OK. The only warnings are the two dynamic key prefixes `screen.isekai_mahou.tforge.` and `.slot.`.
- `bash tools/check-tree.sh transform_forge` → COMPILOU OK (all). One earlier run failed only in `feature/familiars/FamiliarSpell.java` (summon_forge mid-edit); the retry passed.

## Files touched
- **Core `glyph/`** (three small changes; Domain and existing laws behave as before):
  - `FormGlyph.java`: new `allowsLinks()`, default true.
  - `SpellParser.java`: a link after a form with `allowsLinks()==false` fails with `spell_error.isekai_mahou.form_no_links`.
  - `TransformLaws.java`: `begin` now forwards empty law lists to the backend, which still does nothing when there are no laws and no recipe.
- **`feature/transform_laws/`**:
  - New: `TransformForge.java` (public API, rules, summary, slots), `TransformationForm.java`, `ForgeSessions.java`, `ForgeData.java`, `ForgeNetwork.java`, `ForgeCommands.java`.
  - Edited: `TlawSessions`, `TlawRace`, `TlawHollow`, `TlawTargets`, `TlawGlyphs`, `TlawContent`, `TransformLawsModule`, `client/VowsTab`, `client/TransformLawsClient`.
- **`feature/ui_grimoire/client/GrimoireTab.java`**: a "✦ Transformação" badge on the stat row; its tooltip is the summary (strikes, aura, each law's boon and price, duration, cooldown, mana).
- **Lang**: `src/main/lang/transform_laws/*` (+36 keys each), `src/main/lang/ui_grimoire/*` (+2 each).
- **Art**: `tools/gen/transform_forge_icons.py` → `textures/gui/glyph/transformation.png`, `textures/mob_effect/forged_form.png`.
- `feature/character/**` was not touched; the V-key hook lives in `TlawRace`.

## New ids
- Glyph `isekai_mahou:transformation`: Arcane FORM, tier 4, min Circle 5 (Domain is 6), not an ultimate. Survival source is the existing Arcane path auto-teach (level 18 and Circle 5).
- MobEffect `forged_form` (marker with timer icon); attachment `transform_forge` (slot → recipe, copied on death).
- Packets `tforge_set_recipe` (C→S), `tforge_sync` (S→C).
- Flags `tforge.slot.<id>`; hints `tforge_learned`, `tforge_cast`, `tforge_slot`.
- Recipe slots `race_form`, `hollow_resurreccion`, `shikai`, `bankai`, `vollstandig`; command `/mahou tforge`.

## How it behaves
- **Recipe**: `transformation [augments] → effects [augments] → transformation laws`. The element comes from the recipe and drives aura, particles and colour.
- **Strikes**: offensive effects land on melee and projectile hits at 60% power, at most once per 10 ticks.
- **Aura**: beneficial effects pulse on the player every 3 s at 50% power. With Expand the aura gets a radius (4/6/8…, max 12): allies receive the beneficial effects and enemies the offensive ones at 35%.
- **Cities**: the hostile aura never pulses in a `Places` safe zone and only hits monsters, mobs targeting the player, and enemy players. Nothing edits blocks or spawns mobs.
- **Fixed transformations**: a fixed-transformation effect (Kaioken, Wolf Form…) placed inside the recipe is applied once at the start as the "body".
- **Numbers**: 30 s duration (Extend/Persistent up to 60 s), 120 s base cooldown, 1.5 s cast. Effects are costed as pulses over the duration, like the Domain.
- **V key**: the Vows tab has a "Receita montada" block. Left-click cycles the player's saved Transformation recipes (Library, presets, keys); right-click removes. The recipe goes on top of the racial form, its mana is charged when the form enters, and its laws come first with loose vows filling up to the player's limit. No recipe = same as today.

## API for stage 2 (releases)
The slot id is the path of the release's source id. Slots `shikai`, `bankai`, `vollstandig` are already registered and become visible to a player after their first release goes through either call. Hollow Resurrección is already wired through the existing `HollowRelease.ON_START/ON_END` hooks — class_hollow only needs the button.

- **Old core signatures** (compile against the current `build/classes`):
  - `TransformLaws.begin(sp, IsekaiMahou.id("bankai"), looseLawsOrListOf(), ticks, probeOrNull)`
  - `TransformLaws.end(sp, IsekaiMahou.id("bankai"))`
  - The backend looks up the slot with that path, re-validates the player's recipe, charges its mana and layers it.
- **New class** `feature.transform_laws.TransformForge`:
  - `beginSlot(sp, "bankai", ticks, probe, looseLaws)` → `Result(ok, error, laws)`
  - `end(sp, slot, natural)`, `active(entity[, slot])`, `recipe(player, slot)`
  - `registerSlot(id, titleSupplier, Predicate<MagicData>)` for other releases (call in common init)
  - `summary(parsedSpell, castTimes)`
  - Client: `TransformForge.openShaper("bankai")` for the "Moldar liberação" button, or the old `StatusScreen.open("transform_laws")`.
- The player needs the `transformation` glyph (Circle 5) to build a recipe.

## How to test
```
/mahou admin login <senha>
/mahou level 60
/mahou circle 6
/mahou tforge grant
```
1. Menu B → Grimoire: build `Transformação → Incendiar → Curar → Lei: Corpo de Vidro`, hover the "✦ Transformação" badge, save to a key, cast it, hit a mob.
2. `/mahou tforge status` shows the active session.
3. `/mahou tforge use race_form 1` (key 1–4 of the active loadout), or pick it in B → Personagem → Votos; then `/mahou character transform @s` for the V form with the recipe on top.
4. `/mahou tforge test race_form 30` runs the slot recipe without the base form; `/mahou tforge remove race_form`; `/mahou tforge stop`.
5. Adding "Ao Acertar" after the form should show the new parser error.

## Not done / not verified
- No in-game run: FX look, balance numbers, the Vows-tab layout on small windows, and the tab's right-click are untested.
- `check-module.sh ui_grimoire` was not run. `GrimoireTab` now imports the new `TransformForge` class, which is not in `build/classes`, so it will fail until gradle recompiles. The same applies to stage-2 modules that use the new class instead of the old `TransformLaws` signatures.
- Fixed transformations accept effects only through the Transformation form. `Self + Kaioken + effects` recipes are unchanged.
- Sessions are not saved: relog or death ends them and the mana is not refunded. Relogging with the racial form still active charges the recipe again.
- Recasting the form while it is active ends the previous one naturally, so its laws charge their end prices.
- With a recipe on the V key or a release, duration and cooldown stay those of the base transformation; only mana comes from the recipe.

## PEDIDOS
- **Coordinator**: run the gradle compile before stage 2 so the new classes are in `build/classes`.
- **integration** (`docs/ARCHITECTURE.md`): add glyph `transformation`, effect `forged_form`, attachment `transform_forge`, `TransformForge`, `FormGlyph.allowsLinks()`, the slot ids, and the fact that `TransformLaws.begin` with a slot-path source layers the recipe.
- **story/journal**: one Diary line saying the Transformation form comes from the Arcane path at Circle 5 and is assigned in the Vows tab.
- **releases (stage 2)**: use source paths `shikai`, `bankai`, `vollstandig` (or register your own slots). Do not call begin again for Hollow.