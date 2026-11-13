**sky_access report** — the whole section is in and compiles (`check-module.sh settlements` → TUDO OK, `check-module.sh city_arcana` → TUDO OK, `check-tree.sh sky_access` → COMPILOU OK on the last run; two earlier runs failed only in files another agent was mid-edit on). Nothing was run in game or in a JVM.

## Files touched
All Java under `src/main/java/com/renanpontes/isekaimahou/feature/settlements/`.
- **New:** `SkyAccess.java`, `tools/gen/settlements_w7_access_lang.py`.
- **Edited (small, pointed):**
  - `Settlement` — one field, saved and loaded.
  - `SettlementService` — one line in `heartbeat`, one in `dissolve`.
  - `SettlementsModule` — `SkyAccess.register()`.
  - `SettlementView` — one line.
  - `CityRaids` — `spawnWave` and `steer`.
  - `build/CityLayout` — `arrivalPoint`.
  - `CityDebugCommands` — three subcommands and one method.
- **Lang:** `src/main/lang/settlements/{pt_br,en_us}.json`, 14 keys added, none removed or changed.

## New ids
- **Optional Settlement NBT:** `Sky.Access` {`Shape`, `Rim`, `ColX`, `ColZ`, `Ground`, `Look`, `Pier`, `PierDone`, `Mark`, `MarkDone`, `Gone`}.
- **Client city NBT:** `SkyColX`, `SkyColZ`, `SkyColY`, `SkyPier`.
- **Hints:** `city_sky_updraft`, `city_sky_fall` (sky_ui's generator carries the same ids as a fallback, same PT text).
- **Commands (op):** `/mahou city sky pier`, `/mahou city sky down`, `/mahou city sky drop`.
- **API for other code (static in `SkyAccess`):** `column(s)`, `pierBuilt(s)`, `reserved(s, x, z)`, `mayRide(sl, s, sp)`, `onLand(s, pos)`, `raidSpot`, `raidFx`, `raidRecover`, `rescue`.

## What it does
- **Updraft:** a 3×3 air column just past the pier end, on the plaza-front side.
  - Walking in lifts you and sets you on the pier; sneaking lets you float down.
  - With gates closed (or the night decree at night) only city members go up; going down is always allowed.
  - While the pier does not exist yet, you are set on the current land rim instead.
  - If something blocks the column for 1.5 s, you are delivered to the pier anyway.
- **Sky Pier:** a 5-wide deck reaching 6 blocks past the rim, with rails and lanterns, in the blocks of each of the 7 styles.
  - It only fills air, and every placed block is tracked.
  - When the island starts growing, the old pier is removed before new land reaches it, and a new one is built at the new rim once the land is complete.
  - It is also rebuilt on a style change, and missing blocks are restored about once a minute.
- **Ground marker:** a ring of 12 slabs laid on top of the terrain under the column; nothing is replaced or dug.
  - It is skipped over water and inside another place's territory.
  - The column glows only for players within 56 blocks.
  - When the pier is done, the governors get one action-bar line and a diary entry with the marker's x/z.
- **Protection:** pier and marker blocks cannot be broken in survival and are excluded from explosions.
- **Falling:** anyone who falls off the island, or leaves the column mid-ride, gets Slow Falling until they touch ground.
- **Trait:** city members take no fall damage anywhere in the island's column, from the top down to the ground below.
- **NPCs:** sky-city NPCs take no fall damage. On each heartbeat (5 s) an NPC below the island, or stranded off it, is returned to its home floor or the plaza. Militia following an online Lord are left alone.
- **Arrivals:** `arrivalPoint` now always returns a spot that has land, which covers residents, guards, pedlars and craft traders.
- **Raids:** attackers spawn on island land in the strip outside the wall, in a puff of cloud; one that falls off is brought back to the rim.
- **Checked, no change needed:**
  - `Conquest` — player cities are not conquerable, so no champion spawns there.
  - `WarService` — militia spawn on the plaza; territory checks use the sky box.
  - `CityCaravans` — nothing physical; the 3D distance makes trips from a sky city slightly longer and better paid.
  - Fast travel — arrival is searched around the Heart's altitude.
- **Dissolved city:** the marker is removed; the island and pier stay.
- **Ground cities:** every hook exits on `!s.sky`.

## How to test
1. `/mahou admin login <senha>`, then `/mahou city sky found kingdom` and `/mahou city sky finish`. An action-bar line gives the marker's x/z.
2. `/mahou city sky pier` prints the column position, ground height and block counts.
3. `/mahou city sky down` puts you beside the stone ring. Walk into the glow: you rise and land on the pier.
4. Sneak and step off the pier end: you float down.
5. Jump off the rim anywhere in survival: Slow Falling and one action-bar line.
6. Set gates to closed in the panel and ride with a second, non-citizen account: refused with a message.
7. `/mahou city sky drop` near any city NPC: it should be back within 5 s.
8. `/mahou city sky grow`: the old pier and ring vanish, and new ones appear at the new rim when the land finishes.
9. `/mahou city style oriental` (or any other): the pier is rebuilt in the new blocks within 2 s.
10. `/mahou city level 2`, then force a raid with the existing raid test command: the wave appears on the island in cloud puffs.
11. Try breaking a pier block in survival: refused.
12. Restart the server: pier, ring and column persist.

## Not done / not verified
- **No in-game run:** ride feel, landing spot, pier look per style, ring placement on rough terrain and NPC return are all unverified.
- **Trait wording:** the no-fall-damage trait also covers the ground under the island, not only the top.
- **Leftover blocks:** pier or marker blocks in unloaded chunks at the moment a city is dissolved stay behind.
- **Marker:** on steep terrain or water the ring is partial or absent; only the glow marks the spot.
- **Raids:** if a player dug a hole through the island exactly at a spawn point, that attacker is skipped.
- **Arrivals:** new NPCs still arrive at the gates, not at the pier.
- **Fast travel:** it refuses "already here" when you stand within 12 blocks horizontally of the Heart, even 100 blocks below it (core code).
- **Decorations:** sky_ui keeps waterfalls and islets away from the pier side by angle; it does not call `SkyAccess.reserved`.

## PEDIDOS
- `docs/ARCHITECTURE.md`: add `SkyAccess` (the APIs above), the two hints and the three commands.
- `core/onboard/TravelService`: skip the `ALREADY_HERE` horizontal check when the destination is far above or below the player.
- sky_ui / panel: `SkyColX`, `SkyColZ`, `SkyColY` and `SkyPier` are in the city NBT if a line such as "Corrente Ascendente: x, z" is wanted.
- Journal/guide (other modules): a short entry on the Updraft, the Sky Pier and the stone ring.
- If any older settlements lang generator is re-run, run `py -I tools/gen/settlements_w7_access_lang.py` afterwards.