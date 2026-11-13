**REPORT — job "dungeons" (ONDA 3)**

All four deliverables are built and compile: `bash tools/check-module.sh depths` (and `nether_citadel`, `end_library`, `dwarf_hold`) end "TUDO OK", and `bash tools/check-tree.sh dungeons` ends "COMPILOU OK (all)". Nothing was run in game.

**Files / dirs touched**
- `feature/depths/puzzle/**` (new): `PuzzleBlocks`, `PuzzleCoreBlock`, `PuzzleCoreBlockEntity`, `RuneLitBlock`, `RuneStoneBlock`, `LightMirrorBlock`.
- `feature/depths/`: `DepthsAffixes.java` (new); edits in `DepthsDungeon`, `DepthsEvents`, `DepthsModule`, `DepthsCommands`, `item/DragonslayerItem`.
- `feature/nether_citadel/`: `NcPact`, `NcDemons`, `NcCommands`, `block/PactAltarBlock`, `item/SunKatanaItem`.
- `feature/end_library/item/OverlordStaffItem.java`.
- `feature/dwarf_hold/**`: no Java changed (only the city template).
- Generators: `tools/gen/depths_puzzles.py` (new, shared puzzle stamps + art), `depths.py`, `nether_citadel.py`, `end_library_struct.py`, `dwarf_hold.py`.
- Regenerated data:
  - `structure/depths_dungeon/*` (entrance, 8 floors, 4 new `puzzle_<kind>` demo rooms)
  - `structure/nether_citadel/{keep,road}.nbt`, `structure/end_library/library.nbt`, `structure/dwarf_hold/city.nbt`
  - `worldgen/structure/nether_citadel.json`, `worldgen/template_pool/nether_citadel/road.json`
  - `loot_table/chests/depths_puzzle{,_deep}.json`, `journal/depths.json`
  - puzzle block textures, models and blockstates; `lang/depths/*`, `lang/nether_citadel/*`

**New ids**
- Blocks + items: `depths_puzzle_core` (also a block entity), `depths_rune_lamp`, `depths_rune_tile`, `depths_rune_stone`, `depths_light_mirror`, `depths_puzzle_gate`. Unbreakable in survival; in the creative World tab.
- Structure piece and pool: `nether_citadel/road`.
- Loot tables: `chests/depths_puzzle`, `chests/depths_puzzle_deep`.
- Flag `depths.puzzle`; counters `puzzle.solved`, `depths.best_time` (seconds), `depths.clears`.
- Mob persistentData `isekai_mahou.depths_affixes`; attribute modifiers `depths_affix_<id>`.
- Hints `depths_puzzle`, `depths_affixes`; journal step `depths.puzzle` (optional).

**What was built**
- **Citadel half-buried:**
  - The template grew from 47×34×55 to 67×40×75, with an explicit open esplanade around the keep.
  - A paved road leaves the gate as up to 4 jigsaw segments (64 blocks): tunnel through rock, causeway over open space. The road can still dead-end in solid netherrack.
  - Added basalt spikes, soul braziers and wall buttresses.
- **Puzzles:** the Rune Seal detects the puzzle type from nearby pieces; solving removes the Sealed Grates caging a chest and gives 150 XP.
  - Types: levers flipping lamp groups, step-on-every-plate-once, a light beam through 3 mirrors, and a rune sequence.
  - Placement: one per Layered Dungeon floor (type varies by floor and variant); light puzzle in the Citadel's old depot; rune sequence in the Library archive; levers in the Dwarf vault, where a grate wall now seals the treasure.
- **Affixes:** each floor rolls 1–2 of Fortified, Frenzied, Brutal, Volatile, Vampiric, Draining, rotating every 7 game days. They are shown on the action bar with the floor title and never apply to the boss or summons. A descent timer runs from the first floor to the Worm King's death and keeps a personal record.
- **Visuals:** besides the Citadel, the dungeon entrance got a cracked obelisk with a signal fire, visible from afar.
- **Code review fixes:**
  - Pact Altar: the Debt was applied while the player's effect list was being iterated. Drinking milk or popping a totem during a pact should have thrown a ConcurrentModificationException, and on normal expiry the message came twice. It is now applied on the next tick.
  - Pact Altar: an offering in the off-hand now works.
  - Sun Katana dash and Antimagic slash no longer hit villagers, citizens or passive animals.
  - Dragonslayer charged slash and Katana dash no longer apply their bonus twice.
  - Overlord Staff Skyfall skips non-hostiles inside safe zones.
  - Rune Forge and Infernal Forge: reviewed, no bug found.

**How to test** (after `/mahou admin login`)
1. `/mahou depths puzzle demo levers` (also `tiles`, `light`, `sequence`) builds a test room 3 blocks south of you. Right-click the Seal for a hint.
2. `/mahou depths puzzle solve` force-solves the nearest loaded Seal; `/mahou depths puzzle tp` teleports to it.
3. `/mahou depths tp 1` through `4`: find the puzzle room on each floor; the affix line appears on entering.
4. `/mahou depths affixes`, `/mahou depths record`; `/mahou depths tp 5` then kill the Worm King for the time message.
5. `/mahou nether_citadel tp` in a new Nether area: check the esplanade, the road south of the gate, and the Seal Chamber (south-east building, by the sign).
6. `/mahou nether_citadel pact`, then drink milk: no crash, Debt arrives one tick later.
7. `/mahou end_library tp` (or `place`): archive floor, runes on the central column.
8. `/mahou dwarf_hold tp`: vault north of the hall, lever panel in front of the grate.

Only newly generated chunks get the new templates.

**Not done / not verified**
- The Citadel esplanade and road in real Nether terrain, including whether `size: 4` with `max_distance_from_center: 116` places all four segments. Lava from surrounding pockets can still flow in.
- Mirror model diagonal matching the beam logic, and mirror behaviour in rotated jigsaw pieces.
- The plate puzzle can be cheesed by jumping.
- Affixed mobs have no visual marker (kept the screen clean).
- No visual changes to the End Library or Dwarf Hold beyond their puzzles.

**PEDIDOS**
- integration (`docs/ARCHITECTURE.md`): add the six puzzle block ids to the catalog. Any structure can use them by id via `tools/gen/depths_puzzles.py`; the Seal takes block-entity NBT `Range` and scans −3..+7 in y.
- biomes: your `spacing`/`separation` values were kept. `depths.py`, `nether_citadel.py` and `dwarf_hold.py` now preserve whatever is in the existing `structure_set` file when regenerating.
- story/journal: counters `puzzle.solved`, `depths.best_time` and `depths.clears` are available if you want them shown.