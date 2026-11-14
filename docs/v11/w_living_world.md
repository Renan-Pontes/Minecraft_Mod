# v1.1 — "Deixe o mundo vivo" (living world pass)

Owner's ask (2026-11-13): improve buildings, biomes, make the world feel alive — always light. Nothing here adds HUD
elements; every visual is client-side and gated by `FxQuality`; every server loop is throttled.
**Nothing was run in game.** Compile: `bash tools/check-tree.sh world` — my files compile; the tree failed only on
agent T's in-progress files (`class_quincy/QuincyFinalForms`, `LetztStilForm`) at the time of writing.

## 1. Ambient life particles (client) — `feature/ambience/client/AmbientLife.java`
Per-biome, per-time-of-day particles around the player, reusing existing particle types (no new registrations):
- **Fireflies** (`structures_b:fairy_firefly`) on warm, dry-free nights in plains/forests/swamps/rivers/meadows/cherry,
  `sakura_fields`, `flat_lands_*`, `crystal_forest`, `twilight_grove`. Only where sky light ≥10 and block light ≤7
  (not inside lit cities), with ground ≤4 below.
- **Falling leaves** (`fx_leaf`, tinted with the biome foliage colour) dropping from the underside of real canopies
  near the player (forest/jungle/taiga + magic forests).
- **Pollen** (`fx_glow`, gold) by day over floral biomes/meadows/kingdom meadows/sakura.
- **Mana motes** (`fx_mote`) in `crystal_forest`/`crystal_cavern` (cyan), `twilight_grove`/`astral_gardens` (violet).
- **Wisps** (pale `fx_glow`) at dusk/night in `haunted_swamp` and `twilight_grove`.
- **Embers** (`fx_ember`) in `ember_crystal_wastes`, nether wastes, crimson forest.
- **Shooting stars**: clear Overworld nights, outdoors, ≈1/min; many during the new Star Shower (item 2). Drawn as a
  trail of always-visible `fx_glow`/`fx_mote` 45–75 blocks up, kept inside fog distance.

Cost: one pass every 4 ticks, 1–4 attempts with a few block/light reads; biome profile cached per biome. **Off on LOW**;
providers still thin by preset. New client option **`worldLife`** (Mods › Isekai Mahou › Config, file
`isekai_mahou-ambience-client.toml`). Files: `AmbientLife.java` (new), `AmbienceClient.java`, `AmbienceConfig.java`.

## 2. Star Shower world event — `feature/world_events/event/StarShower.java`
New `WorldEventType.STAR_SHOWER` (6th event). Rolled at dusk with the other night events (4.5%, 10% on a new moon, ×
the world-events chance), ends at dawn. Peaceful: no mobs, no combat bonus. Every 40–70 s each outdoor player gets
one glowing drop 5–14 blocks away with a falling light trail + chime: 60% Star Metal, 34% Mana Crystal, 6%
`isekai_mahou:star_fragment` (by id; falls back to Star Metal). Same city rule as Mana Storm (never in/near Places or
safe zones). Client: shooting stars every ~1.25 s. Files: `StarShower.java` (new), `WorldEventType`, `WeManager`
(tick/start/stop/roll), `client/WeClientState` (`active()` getter).
Test: `/time set 14000`, `/mahou world_events start star_shower`, stand outdoors (drops start after ~10–40 s).

## 3. Road encounters — `feature/world_events/WeEncounters.java`
Small, rare meetings in the wilderness (surface, outside Places + 48-block margin, outside structures). Each player
is rolled every 4–8 min (first after 3 min), 45% chance; ≤3 encounters in the world, 1 per player; skipped during
Blood Moon; follows `/mahou world_events auto`.
- **Travellers** (day): either a kingdom caravan (travelling `city_merchant` — grocer/alchemist/tailor/jeweler/
  blacksmith, trades as in town — escorted by 1–2 `city_guard`) or 2–3 pilgrims (`citizen`, 50% with a bard who
  plays flute notes). They spawn 26–40 blocks away and walk toward the nearest NPC city (≤2000 blocks) in 14-block
  legs every 3 s (detour 45° around water/cliffs); stop and greet when you come within 7–10 blocks. Kingdom = that
  city's faction, else by biome (desert/savanna → Sahra, snowy → Nordheim). Vanish when nobody is within 128, after
  4–6 min with nobody within 48, or once they reach the city and you are away.
- **Wounded adventurer** (day/night): a citizen at 25% HP who cannot walk (Slowness X), calls for help every 15 s.
  Right-click with any food, potion or honey bottle: heals, bows, gives 6–12 guild coins (emeralds if no coin item),
  60 magic XP and a rumor of a nearby tower/crypt/den/lair/camp/ruin (uses `CityRumors.search` ×3 tries + the rumor
  tracker). Leaves 40 s later. One-time hint.
- **Fairy ring** (clear nights, floral/forest/plains/meadow/cherry/flat lands/sakura/crystal forest, on grass): a
  batched `FxBatch.ring` of light (1 item/s) + a few motes + chime, with 3 `isekai_mahou:fairy` (by id) restricted to
  the ring. Stand inside ~3 s → Luck 5 min + Regeneration 15 s + 30 XP, counter `encounter.fairy_ring`; the ring
  fades 8 s later. One-time hint.

City safety: spawn spots reject Places/safe zones/24-block margin, water and non-sturdy ground; nothing places or
breaks blocks. Encounter NPCs carry persistentData `isekai_mahou.encounter`; any that reload after a restart are
discarded on the next tick. Counters: `encounter.fairy_ring`, `encounter.wounded_helped`.
Test (op): `/mahou encounter travellers|wounded|fairy_ring`, `/mahou encounter clear`.

## 4. NPC city life — `feature/cities`
- **Children** (`entity/Citizen.java`): ~1 in 6 newly spawned citizens (NPC city templates, city-heart
  repopulation, player-city residents) is a child: `Attributes.SCALE` −0.38 and +15% speed (permanent modifiers,
  GeckoLib renders the scale), a `KidPlayGoal` that dashes to random nearby spots by day, 6 own speech lines, never
  goes to the tavern. NBT `Child`/`ChildSet`; citizens saved before v1.1 stay adults (detected by the `CityBorn` key);
  encounter NPCs are never children.
- **Bells** (`CityBells.java`, new): at 06:00 and 18:00 each city/capital with a player inside tolls a bell three
  times at its center (volume 5 ≈ 80-block reach; higher pitch at dawn). One check per second only inside the two
  windows; respects `/mahou city routine off`.
Test: `/time set 23950` inside a city → bells; `/summon isekai_mahou:citizen` a few times (1/6 are kids).

## 5. Small points of interest + biome decoration (data only)
One new generator, **`tools/gen/living_world_pois.py`** (`py -X utf8 -I tools/gen/living_world_pois.py`); it writes
only its own files and touches no older generator or spacing.

| structure | size | spacing/sep/salt | biomes | loot |
|---|---|---|---|---|
| `wayside_shrine` | 7×8×7 | 34/12/1946307201 | plains, sunflower plains, meadow, forest, birch, flower forest, taiga, cherry, `flat_lands_kingdom_meadows`, `sakura_fields` | offering chest |
| `wizard_hut` | 9×11×9 | 40/14/1946307202 | dark forest, birch/old birch, taiga/old spruce, swamp, `twilight_grove`, `haunted_swamp`, `crystal_forest` | books, potions, mana crystal, T1–2 glyph scroll |
| `abandoned_camp` | 11×5×11 | 32/11/1946307203 | plains, savanna, forest, taiga, snowy plains, meadow, windswept hills, `flat_lands_golden_steppe` | barrel |
| `fallen_sword` | 9×15×9 | 48/18/1946307204 | plains, savanna, badlands, desert, windswept savanna, snowy plains, meadow, `flat_lands_*` | half-buried chest (rare T2 scroll) |

- Jigsaw size 1, `beard_thin`, `random_spread_multi` with the mage-camp exclusion list (guild_city 10, three
  capitals 13) plus `guild_hall_village` 6 and `minecraft:villages` 4. Campfires unlit; no mobs/spawners.
- Files: `structure/living_world/*.nbt`, `worldgen/{structure,template_pool,structure_set}/<id>`, `loot_table/chests/living_world_<id>.json`,
  biome tags in `src/main/merge/living_world/.../has_structure/<id>.json`, lang `src/main/lang/living_world_pois/`
  (`structure.`, `rumor.…cities.structure.`, `map.…structure.` names — so they also show up in rumors).
- Not map markers on purpose (small finds; no minimap clutter).

**Decorations** (NeoForge `add_features`, step `vegetal_decoration`, vanilla feature types only):
`living_world_ruin_pillar` (broken stone-brick column, 1/40 chunks, forests/taiga/meadow/windswept/crystal & twilight),
`living_world_mossy_boulder` (1/14, forests/taiga/meadow/windswept), `living_world_mana_outcrop` (patch of
`luminous_crystal_cluster` on mountain stone, 1/24), `living_world_glow_mushrooms` (mushrooms + glow lichen, 1/6, dark
forest/swamp/twilight/haunted). Only on dirt/grass/podzol ground, no plains/savanna (city biomes), and skipped when any
of 20 sampled nearby blocks is in the new block tag `#isekai_mahou:living_world_built` (planks, bricks, path, glass,
lanterns, wool…) — a cheap "not inside a city" check (reduces, cannot fully exclude, decoration on big grass city lots).

Test: `/locate structure isekai_mahou:wayside_shrine` (…`wizard_hut`, `abandoned_camp`, `fallen_sword`),
`/place structure isekai_mahou:<id>`, `/place feature isekai_mahou:living_world_ruin_pillar` (skips filters).

## Lang
`tools/gen/living_world.py` → `src/main/lang/living_world/{pt_br,en_us}.json` (32 keys: option, Star Shower, encounters,
kid lines). Safe to re-run; touches nothing else. POI names: `src/main/lang/living_world_pois/` (24 keys).
Hand-patched (generator + json, not re-run): `command.isekai_mahou.world_events.unknown` now lists `star_shower`.

## Not done / not verified
- Nothing tested in game: particle densities/colours, star visibility vs. fog at low render distance, traveller
  pathing over rough terrain (they may get stuck on cliffs/rivers; they just vanish later), guard escorts vs. Wanted
  players (they behave like city guards), child scale on the NPC model and name plate height.
- POIs/decorations never loaded by the game: datapack parsing, feature-order safety (looks safe: new ids only via
  modifiers), how the shapes sit on slopes, how many mushrooms/crystals survive placement.
- Compile: with agent T's two broken `class_quincy` files swapped for their HEAD copies the rest of the tree
  (including every file here) attributes cleanly; re-run `bash tools/check-tree.sh world` once T finishes.
- No new NPC-city building templates (they come from `tools/gen/city_build.py`, which also regenerates all city art;
  not re-run). Chimneys already have campfire smoke; lamps/banners left as they are.
- Existing weak structures were not reworked (their generators hold pre-change spacing); new POIs added instead.
- Roads between cities: not built.
