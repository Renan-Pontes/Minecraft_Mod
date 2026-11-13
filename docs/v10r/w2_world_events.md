All five `world_events` deliverables are built and compile, but nothing was run in game. `bash tools/check-module.sh world_events` ends in TUDO OK and `bash tools/check-tree.sh world_events` ends in COMPILOU OK.

## Files touched (all inside my area)
- `src/main/java/com/renanpontes/isekaimahou/feature/world_events/**` (21 Java files, including `event/`, `entity/`, `item/`, `client/`)
- `src/main/lang/world_events/{pt_br,en_us}.json` (63 keys, written by the generator)
- `tools/gen/world_events.py` (textures, block/item models, GeckoLib geo, animations and glowmasks, loot, recipes, tags, lang). It imports the model builder from `tools/gen/depths_geo.py` read-only.
- `src/main/merge/world_events/data/minecraft/tags/block/{mineable/pickaxe,needs_stone_tool,needs_iron_tool}.json`
- `src/main/resources/{assets,data}/isekai_mahou/...`, every file prefixed `world_events_`

## What each event does
- **Meteor Fall**: a 6 s streak in the sky, then a radius-5 crater with meteorite and 4–6 Star Ore. About 2.5 s after impact, 2–3 scouts, 1–2 spitters, 3–5 larvae and the Star Herald mini-boss come out.
- **Blood Moon**: dusk to dawn, extra biome monsters around players in the wild. Kills give ×1.5 vanilla XP, 1–4 magic XP, and a 20% chance of one extra stackable drop.
- **Arcane Storm**: 5 min, water and wind spell damage ×1.30 (fusions included). Any natural thunderstorm gives ×1.10.
- **Earthquake**: about 11 s of shaking and 1–3 small fissures with common ores exposed; diamond only at the bottom, rarely.
- **Mana Storm**: 4 min, all spell damage ×1.20 for players and mobs, a little extra mana regen, and roughly one mana crystal per player every 25–40 s outdoors.

One event at a time, overworld only. Rolls happen once per day and once at dusk, with a 3-day rest after each event and none in the first 2 days of a world. Each start shows one banner line plus a one-time `Hints.show` line.

## City safety
- **Terrain edits** (crater, fissures) go through `WeSites`: at least 64 blocks from every `Places` box, no structure references in surrounding chunks, no chunk where a player ever placed a block, no built-looking blocks nearby, at least 48 blocks from online players' beds. The meteor re-checks at impact and burns up in the air if the spot is no longer free.
- **Player-placed chunks** are only recorded from this version on; older builds rely on the block scan.
- **Blood Moon mobs** never spawn in or near Places or in player-built chunks, are never creepers or endermen, and turn to smoke if they follow a player into a safe zone.
- **Arcane Storm** turns on rain only, not vanilla thunder; its lightning is visual-only.
- **Aliens** break no blocks and target only players.
- **Camera shake** uses the existing `BossKit.shake`, which already applies `MahouConfig.shakeScale()`.

## New ids
- **Blocks**: `world_events_meteorite`, `world_events_star_ore`, `world_events_star_metal_block`, `world_events_star_lamp`
- **Items**: `world_events_star_metal`, `world_events_alien_core`, `world_events_omen_lens`, plus four `*_spawn_egg`
- **Entities**: `world_events_alien_grunt`, `world_events_alien_spitter`, `world_events_alien_larva`, `world_events_alien_overseer`
- **Payload**: `world_events_state`
- **SavedData**: `isekai_mahou_world_events`
- **Counters**: `world_event.<meteor|blood_moon|arcane_storm|earthquake|mana_storm>` (witnessed), `world_event.blood_moon_kills`, `boss.world_events_alien_overseer`
- **Hints**: `world_events.<event id>`
- **Recipes**: `world_events_star_metal_block`, `world_events_star_metal_from_block`, `world_events_star_lamp`, `world_events_omen_lens`

## How to test (after `/mahou admin login`)
Stand in open wilderness, roughly 150+ blocks from any city, structure or build.
- `/mahou world_events status`
- `/mahou world_events site` reports whether a free spot exists nearby.
- `/mahou world_events start meteor` then walk 40–90 blocks toward the smoke.
- `/mahou world_events start earthquake`
- `/mahou world_events start blood_moon` works in daytime when started by command.
- `/mahou world_events start arcane_storm` and `/mahou world_events start mana_storm`
- `/mahou world_events stop`
- `/mahou world_events aliens` spawns a pack on you.
- `/mahou world_events auto <true|false>` and `/mahou world_events chance <0..1000>`; then `/time set 13000` forces the dusk roll.
- Negative test: run `start meteor` inside a city; it should fail with "no free spot".
- Omen Lens: right-click during an event.

## Not done / not verified
- Nothing was verified in game. The least certain parts are how visible the meteor streak is from a distance, the alien models and animations, and the fog tint strength.
- No journal chapter: a quest chain does not fit rare random events. Aliens show up in the bestiary automatically under "Other".
- The storm bonuses apply to spell damage only, not to heals, durations or mana cost.
- No map marker for the crater: `MapMarkers` only handles structures and Places. The smoke column and Omen Lens point to it instead.
- The moon and sky are not recoloured for Blood Moon, only the fog.
- Event chances live in saved data and are changed by command, not in `MahouConfig`.
- Star Metal and the Alien Core only feed decoration (metal block, lamp) and the Omen Lens.

## PEDIDOS
- **journal** (`Bestiary.java`): optionally move the four aliens from "Other" to WORLD and `world_events_alien_overseer` to BOSS.
- **renown**: the counters above are available for titles and ranking, read by id.
- **core `MahouConfig`**: optional options for event auto-roll and chance, if he wants them in the Minecraft settings menu.
- **guild / market**: optionally buy or sell `world_events_star_metal` to give it more use.