**transform_edit report** — every deliverable has a compiling core, but nothing was run in game, and the whole-tree check currently fails in another agent's folder.

## Compile status
- `bash tools/check-module.sh` → TUDO OK for `transform_laws`, `character`, `ui_grimoire`, `class_shinigami`, `class_quincy`, `class_hollow`.
- `bash tools/check-tree.sh transform_edit` → COMPILOU OK (all) mid-session, after the core and main changes. The last three runs fail **only** in `feature/settlements/` (`CityTier.java`, `CityRenewal.java`, `build/ConstructionEngine.java`), where city_tiers is mid-edit.
- The same tree compile with the `feature/settlements` sources excluded (their classes taken from `build/classes`) → COMPILOU OK, 2988 files.

## Files touched
All under `c:/Users/renan/Music/ROCKET/RocketSimVis/Minecraft_Mod/src/main/`.
- **Core `glyph/`**: `FormGlyph.java` (new `cooldownMult(SpellStats)`, default 1) and `SpellParser.java` (the cooldown line uses it).
- **`feature/transform_laws/`**:
  - New: `client/ShaperView.java`.
  - Edited: `TransformForge`, `TransformationForm`, `ForgeSessions`, `ForgeData`, `ForgeCommands`, `TlawSessions`, `TlawRules`, `TlawRace`, `TlawTargets`, `client/VowsTab`, `client/TransformLawsClient`.
- **`feature/class_shinigami/`**: `ReleaseForge`, `Release`, `HollowForms`.
- **`feature/class_quincy/`**: `QuincyForge`, `QuincyFinalForms`.
- **`feature/class_hollow/`**: `HollowForge`.
- **Lang**: `lang/transform_laws/*` (+32 keys, 2 texts updated) and `lang/releases/*` (5 texts updated).
- `feature/character/**` and `feature/ui_grimoire/**` were not edited.

## What changed
- **Every transformation has a slot.** Besides `race_form`, `hollow_resurreccion`, `shikai`, `bankai`, `vollstandig`, there are 20 new slots whose id is the glyph path:
  - Druid: `wolf_form`, `bear_form`, `falcon_form`, `dolphin_form`, `spider_form`, `elemental_beast_form`, `druid_sage_mode`.
  - Necromancer / Warlock: `lich_form`, `necro_monarch`, `demon_wings`, `warlock_chainsaw`.
  - Enchanter / Arcane: `eight_gates`, `kaioken`, `enchanter_gear_second`, `enchanter_gear_fourth`, `ultra_instinct`, `transcend`.
  - Bleach forms: `shinigami_visored`, `shinigami_vasto_lorde`, `quincy_letzt_stil`.
  - A slot shows for whoever knows the glyph, and its recipe layers on when that glyph is cast.
- **No closed effect list, and the Transformation form is no longer required.** The form is the implicit "body"; everything after it must be a known effect, augment or Transformation Law. `TransformForge.validate` applies the spell rules: Circle, elements, law limit, and glyph count (the body counts as one).
- **One place to edit.** The Vows tab opens on "Moldar": a list of all the player's transformations on the left, and an editor on the right (recipe strip, palette of known Effects / Augments / Laws, summary, Save / Clear / load from Grimoire). A top-right button switches to the old loose-vows view. Class-tab "Moldar liberação" buttons open the editor on that slot and are no longer greyed out.
- **Duration and cooldown from augments on the body.** Extend / Shorten / Persistent give ×0.5–×2 duration; Accelerate gives −10% cooldown each (max −40%); Decelerate gives +15% each. A flat mana tax is added (120 per +50% duration or −12.5% cooldown), and Accelerate makes effect pulses 20% dearer each. This is applied to:
  - Race V: duration and cooldown.
  - Shikai: duration and cooldown.
  - Bankai: cooldown only.
  - Every transformation tracked by a MobEffect (Druid forms, Sage Mode, Lich, Monarch, Demon Wings, Chainsaw, the Enchanter ones, Transcendence): duration.
  - The cast Transformation form: both.
- **Relog.** Sessions bound to an effect (cast form, race form, the fixed transformations above) are saved at logout and resumed at login without charging mana again.
- **Hint and tab text.** New hint `tforge_shape`, fired the first time a transformation starts with no recipe, plus a subtitle line in both tab views.
- **No recipe = unchanged behaviour**, and wave-4 slot recipes still load.

## New ids
- Hint `tforge_shape`.
- Optional NBT key `#resume` inside the existing `transform_forge` attachment.
- Commands `/mahou tforge slots` and `/mahou tforge shape <slot> <glyph ids…>`.
- No new items, glyphs, packets or flags.

## How to test in game
```
/mahou admin login <senha>
/mahou level 60
/mahou circle 6
/mahou tforge slots
/mahou tforge shape race_form extend accelerate arcane_damage
/mahou character transform @s
/mahou tforge status
```
1. Hit a mob in the V form: the strike effect should land, the form should last about 67 s instead of 45 s, and the V cooldown should be about 4.9 min.
2. Menu B → Personagem → Votos: the Moldar view lists the transformations. Pick one, click palette glyphs, Save; right-click a strip icon to remove it.
3. Fixed transformation: `/mahou tforge shape kaioken ignite`, then cast any spell with Kaioken and hit a mob.
4. Shikai: `/mahou tforge shape shikai extend ignite`, then `/mahou shinigami shikai` (should last ×1.5), then `/mahou shinigami forge`.
5. Relog while the V form is active: `/mahou tforge status` should still list the session and mana should not drop again.
6. `/mahou tforge remove race_form` restores the old behaviour.

## Not done / not verified
- **No in-game run**: the editor layout on small windows, the view toggle re-opening the tab, FX, balance numbers and the relog resume are all untested.
- **Slot duration is not applied** to Bankai, Vollständig, Máscara do Vazio, Vasto Lorde, Letzt Stil (their own cast recipe's Extend already controls them) or to the Resurrección. There, Extend on the body still costs the tax but only changes how long the recipe's effects last.
- **Slot cooldown is not applied** to fixed transformations cast as spells (the cooldown is the spell's), nor to Vollständig or the Resurrección.
- **Relog does not restore** Shikai, Bankai, Vollständig, Resurrección, Máscara, Vasto Lorde or Letzt Stil, because their own state lives only in memory. Law sessions still end at logout with their end price; only the race form re-applies its laws.
- **What is still excluded from a slot recipe**: other forms, triggers and links (a transformation fires no stages), and Domain laws. Block-only effects can be placed but do nothing, since strikes and aura only target entities.
- **Behaviour changes outside slots**:
  - The cast `transformation` form now accepts zero effects (laws or time only).
  - The Máscara do Vazio base in `TransformTargets` went from 400 to 900 ticks, with the class ending it at the real end.
  - Casting a transformation that has a slot recipe waits until its effect is actually on the player before charging.
- Casting the Máscara while Vasto Lorde is active still charges the mask's slot mana; the session is ended on the next tick.
- Recipes built in the editor have no name, so class-tab tooltips show "Receita sem nome".
- The Hollow has only the Resurrección slot; the retired `hollowfied_resurreccion` glyph gets none.

## PEDIDOS
- **Coordinator**: run the gradle build (`FormGlyph.cooldownMult` is new core; `TransformationForm` overrides it without `@Override` so module checks pass against the old foundation), then re-run `check-tree` once city_tiers finishes.
- **integration** (`docs/ARCHITECTURE.md`): add `TransformForge.validate`, `slotMana`, `durationMult`, `cooldownMult`, `plannedDurationMult`, `activeDurationMult`, `activeCooldownMult`, `slotIds`, `FormGlyph.cooldownMult`, the 20 slot ids, hint `tforge_shape`, and the two commands.
- **journal_end / story**: one Diary line saying every transformation is shaped in B → Personagem → Votos → Moldar, with no Transformation form needed.
- **Class owners (later)**: to honour slot duration in Bankai, Vollständig, Resurrección and the Hollow forms, call `TransformForge.plannedDurationMult(player, slot)` before computing their ticks, as `Release.toggleShikai` now does.