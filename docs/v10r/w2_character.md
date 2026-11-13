**Job "character" — report**

Both deliverables are built and compile; nothing was run in game. `bash tools/check-module.sh character` ends with TUDO OK (the only warning is the old dynamic key `character.isekai_mahou.dive.`), and `bash tools/check-tree.sh character` ends with COMPILOU OK.

**What was built**
- **Altar do Recomeço** (new block): opens a confirm screen, then refunds all talent points and undoes the specialization via `ClassTalents.reset`. Cost: 12 Mana Crystals.
- **Altar da Reencarnação** (existing block, extended): now opens a "Change Race / Change Class" screen. Race costs 32 Mana Crystals + 1 Guardian Core (it was 3 crystals); class costs 64 + 1 Guardian Core.
- **Class change**: level, Circle and elements are kept. The old class loses talents and specialization, origin glyphs (and loadout spells using them), `ritual_rank.<class>` and `<class>.*` counters and flags, and class items in the inventory. The new class gets its kit and the normal `PathChosen` event. The confirm button needs two clicks.
- **Where to find them**: two rare shrines generate in the world, `renewal_shrine` and `reincarnation_shrine`, and become minimap markers when found. Both altars also have crafting recipes and are in the creative tab.
- **Transformations, per race (all 11)**:
  - a race sigil drawn on the ground during the charge (pentagram, web, crescent, paw, sun and so on) that bursts outward on transform;
  - the same sigil, fainter, under the feet while the form lasts, plus a light shell around the body on HIGH graphics (never in the player's own first person);
  - an own particle sprite with its own motion;
  - an entry sound when the charge starts;
  - a reveal pose at the burst.
- **First-time hint** `character.altars`, shown through `Hints.show` at level 10 or on first altar use.

**Files touched**
- `src/main/java/.../feature/character/`
  - new: `Reincarnation.java`, `block/NewBeginningsAltarBlock.java`, `client/AltarScreen.java`, `client/FormAura.java`, `client/RacePoses.java`, `client/RaceMoteParticle.java`
  - edited: `CharacterContent`, `CharacterService`, `CharacterNetwork`, `CharacterModule`, `block/ReincarnationAltarBlock`, `client/CharacterClient`, `client/CreationScreen`, `client/FormFx`
- `tools/gen/character_release.py` (new, already run) and `tools/gen/character.py` (now merges the new lang keys and the pickaxe tag, so re-running it loses nothing)
- `src/main/lang/character/{pt_br,en_us}.json` (50 keys added or updated)
- Resources: altar textures, model, blockstate, loot and recipe; 11 particle textures and JSONs; 2 structure `.nbt` files with their worldgen JSON
- `src/main/merge/character/` (biome tags for the shrines, pickaxe tag)

**New ids**
- Block/item: `new_beginnings_altar`
- Particles: `race_mote_<human|elf|dwarf|half_orc|demon|dragonborn|beastfolk|arachnid|phantom|angel|slime>`
- Structures: `renewal_shrine`, `reincarnation_shrine`
- Payload: `character_altar_action`; open mode `MODE_RENEWAL = 3`; network registrar bumped from "5" to "6"
- Hint: `character.altars`

**How to test** (after `/mahou admin login`)
- `/mahou character altars` gives both altars; place and right-click them.
- `/give @s isekai_mahou:mana_crystal 64` and `/give @s isekai_mahou:guardian_core_fire` cover the costs.
- `/mahou character renew @s` does a free talent reset; `/mahou talents reset` is the wave-1 equivalent.
- `/mahou character class @s knight` does a free class change.
- `/mahou character race @s demon` then `/mahou character transform @s` shows a transformation; repeat for each race.
- `/particle isekai_mahou:race_mote_angel ~ ~1 ~` shows one particle on its own.
- `/locate structure isekai_mahou:renewal_shrine` and `/place structure isekai_mahou:reincarnation_shrine` cover the shrines.

**Not done / not verified**
- Poses, sigils, particle motion, the altar screens and shrine generation need an in-game look.
- A Hollow (story class) cannot change class at the altar; I blocked it on purpose because of its extra state. Say if you want an exit.
- Class modules' private data (zanpakutō, ranger companions and similar) is not wiped; it goes dormant and comes back if the player returns to that class.
- Class items stored in chests are not removed, only those in the inventory.
- `ClassKits.give` hands out the common starter pack again on every class change.
- The three costs are my own numbers; they are constants in `Reincarnation` (`RENEWAL`, `RACE`, `CLASS`).
- The old cheap recipe for the Reincarnation Altar is kept and a recipe for the new altar was added, so old worlds can still get them. Remove both recipes if the altars should be found only in the world.
- No journal chapter was added.

**PEDIDOS**
- cities / cities_npc: place `isekai_mahou:new_beginnings_altar` and `isekai_mahou:reincarnation_altar` in the capitals (by block id); the contract allows "em capitais" and I could not touch city structures.
- coordinator: do not re-run `tools/gen/icons.py`; `character_release.py` has already been run and its output is in the tree.