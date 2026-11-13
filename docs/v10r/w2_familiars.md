REPORT — job "familiars" (wave 2)

Every deliverable in the section is built and compiles: `bash tools/check-module.sh familiars` ends in TUDO OK and `bash tools/check-tree.sh familiars` ends in COMPILOU OK. Nothing was run in game, so behaviour and visuals are unverified.

**Files/dirs touched (all inside my area)**
- `c:/Users/renan/Music/ROCKET/RocketSimVis/Minecraft_Mod/src/main/java/com/renanpontes/isekaimahou/feature/familiars/` — module code (11 common classes) plus `client/` (renderer, B-menu tab, client state, client module).
- `c:/Users/renan/Music/ROCKET/RocketSimVis/Minecraft_Mod/src/main/lang/familiars/pt_br.json` and `en_us.json` — 129 keys each, generated; no key collides with another module.
- `c:/Users/renan/Music/ROCKET/RocketSimVis/Minecraft_Mod/tools/gen/familiars.py` — generates all art, the recipe and the lang files (`py -I tools/gen/familiars.py`).
- Under `src/main/resources/assets/isekai_mahou/`:
  - `geo/entity/fam_<form>.geo.json` (10)
  - `animations/entity/fam_<form>.animation.json` (10)
  - `textures/entity/fam_<form>_<1|2|3>.png` plus `_glowmask` (60)
  - `textures/item/bond_incense.png`, `models/item/bond_incense.json`
  - `textures/gui/tab/familiar.png`
- `src/main/resources/data/isekai_mahou/recipe/bond_incense.json`

**New ids**
- Entity `familiar_spirit`: one type for all forms, not saved with the world, MISC category.
- Item `bond_incense`: shapeless recipe (essence_arcane + mana_crystal + feather + bone + honeycomb), in the Rituals creative tab.
- Player attachment `familiars`, copied on death.
- Packets `familiars_action` and `familiars_state`.
- B-menu tab `familiar`: Character category, hidden until the player has a familiar.
- Key `key.isekai_mahou.familiar_call`: unbound by default (most letters are taken); the tab does the same thing.
- Form ids (saved): `ember_fox`, `tide_otter`, `stone_tortoise`, `gale_owl`, `dawn_hare`, `shade_cat`, `rune_wisp`, `verdant_sprite`, `bone_raven`, `fortune_drake`.
- MagicData flag `familiars.awakened` and counter `familiars.stage` (1–3), for other modules to read by text.
- Hints (via `Hints.show`): `familiar_ritual`, `familiar_incense`, `familiar_awakened`, `familiar_shifted`, `familiar_stash`, and `familiar_help_<fetch|soothe|mend|mana|forage|watch|ambush|death_guide|ore|treasure>`.

**What it does**
- **Ritual:** hold the Bond Incense for 4 s near a lit campfire, with a class chosen and level 10+. Used again later, it renews the bond and re-shapes the familiar immediately.
- **Form from playstyle:** score = class 35 + race 15 + share of casts in the element 30 + feats 40. Feats come from MagicData counters and vanilla statistics (mining, fishing, deaths, chests and so on).
- **Changes with you:** re-evaluated every 2 minutes. The familiar shifts when another form leads clearly for 5 evaluations in a row and at least one Minecraft day has passed since the last shift.
- **Stages:** stage 2 at level 30 + bond 120, stage 3 at level 75 + bond 400. Bond comes from time together, level-ups and one petting per day. Each stage is bigger, has its own texture and adds ornaments.
- **Help, one per form, no fighting:** fetch dropped items, put out fire / restore air / light heal, ore sense, far danger warning, light heal plus poison cure, ambush warning, resource trickle up to 50%, hunger snack, trail to last death plus undead warning, unopened-loot-chest sense.
- **Bag:** every familiar carries 1–3 rows of items; sneak + click it, or use the tab.
- **Safety:** it is invulnerable, never targeted, ignores pressure plates, lets projectiles through, and cannot be clicked while the owner is in combat. Item fetch and treasure sense skip safe zones; nothing touches blocks.

**How to test in game** (after `/mahou admin login`)
1. `/mahou familiars scores` — shows the 10 form scores for you.
2. `/mahou familiars incense`, place and light a campfire, hold right-click on the incense. Or skip the ritual with `/mahou familiars awaken` or `/mahou familiars awaken stone_tortoise`.
3. Open B → Character → Familiar: portrait, rename (Enter), Call/Dismiss, Help on/off, Open bag, "why this form".
4. `/mahou familiars stage 2`, then `stage 3` — size, texture, ornaments, bag rows.
5. `/mahou familiars form <id>` for each of the 10 forms; `/mahou familiars drift` forces the playstyle re-shape.
6. `/mahou familiars help` — fires ore sense, treasure sense or the death trail without cooldown. Right-click the familiar to pet it; sneak + right-click for the bag.
7. `/mahou familiars bond 400`, `/mahou familiars info`, `/mahou familiars reset` (reset returns bag items).
8. Passive helps: let a mob target you from behind with the owl or cat; drop to low HP out of combat with the hare or otter; drop items with the fox; go hungry with the sprite.

**Not done / not verified**
- Model looks, animation directions and the flying follow were only checked with an offline 2D preview of the geometry and textures. In-game they may need small tweaks.
- The 10 models reuse four animation rigs (walker, flyer, wisp, sprite) with per-form bones.
- No spawn egg, on purpose: the body is owner-bound and re-created from player data.
- No journal chapter: adding one to the main route could gate the story, so I left it for the lore/story job.
- If the server refuses the ritual for level or class, the client still plays the 4 s use animation (only the campfire check is client-side); the action-bar message explains why.
- Touch-range spell raycasts and melee swings can still land on the familiar when the owner is out of combat.

**PEDIDOS**
- journal (`feature/journal/bestiary/Bestiary.java`): `familiar_spirit` is not in the Bestiary. If wanted, add it under `Category.ALLY` with its description key.
- renown (optional): titles can read counter `familiars.stage` and flag `familiars.awakened`.
- cities / market (optional): sell `isekai_mahou:bond_incense` at an inn or guild shop as a second source besides the recipe.
- docs (coordinator): add `familiar_spirit`, `bond_incense` and tab `familiar` to the id catalog in `docs/ARCHITECTURE.md`.