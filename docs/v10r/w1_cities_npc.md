All five `cities_npc` deliverables are built and both checks pass (`check-module.sh cities` → TUDO OK, `check-tree.sh cities_npc` → COMPILOU OK). Nothing was run in game: every behaviour below is compile-verified only.

## Files touched
All under `c:/Users/renan/Music/ROCKET/RocketSimVis/Minecraft_Mod/`.

**New**
- `src/main/java/com/renanpontes/isekaimahou/feature/cities/CityStart.java` — starting city
- `.../feature/cities/CityWorld.java` — new SavedData `isekai_mahou_cities_world`
- `.../feature/cities/CityInn.java` — inn
- `.../feature/cities/CityQuests.java` — sidequests
- `.../feature/cities/CityRoutine.java` — NPC routine
- `.../feature/cities/effect/WellRestedEffect.java`
- `tools/gen/cities_npc.py` — generates `src/main/lang/cities_npc/{pt_br,en_us}.json` (287 keys) and `textures/mob_effect/well_rested.png`

**Edited** (in `feature/cities/`): `CitiesModule`, `CitiesContent`, `CityCommands`, `CityDialogue`, `CityServices`, `CityRumors`, `trade/CityTrades`, `network/CitiesNetwork`, `entity/CityNpc`, `entity/Citizen`, `entity/CityMerchant`, `client/CitiesClient`, `client/CityDialogueScreen`.

**Hand-patched text**: `tools/gen/cities.py` and `src/main/lang/cities/{pt_br,en_us}.json` (two lines that advertised glyph scrolls; "Taverneiro/a" → "Estalajadeiro/a"). I did not re-run `cities.py`, because it regenerates all NPC art.

## New ids
- Effect `isekai_mahou:well_rested`: +15% mana regen, 1 HP per 10 s, 10 min.
- Payload `isekai_mahou:cities_quest`; registrar version bumped `cities-3` → `cities-4`.
- Player tag keys, all optional: `RoomUntil`, `RoomCity`, `RoomForced`, `RoomWarned`, `RoomPrev`, `TipAt`, `Quest`, `QuestDone`, `QuestToday`, `QuestTodayDay`, `StartCityDone`. Old `RoomBed`/`RoomDim`/`RoomDay` are still read.
- Counters `cities.sidequest` and `quest.cities_<id>`.
- Services `inn_rest`, `inn_rent3`, `inn_rent7`, `inn_tip_boss`, `inn_tip_place`, `inn_checkout`. The old `room` service is no longer offered.
- 16 quest ids: `lost_cat`, `fever_tea`, `lost_purse`, `night_noises`, `love_letter`, `extra_patrol`, `lost_recruit`, `wall_arrows`, `gate_rumor`, `late_delivery`, `short_stock`, `apple_thief`, `rival_prices`, `people_voice`, `border_threat`, `grand_feast`.

## What each deliverable does
- **Starting city**: on first server start with this version, the nearest mod city or capital to the world spawn is located and a free plaza spot becomes the world spawn. First-join players and bedless respawns land exactly there.
- **Inn**: rest for 5 coins (full health and mana, clears Magic Exhaustion, weakness and slowness, grants Well Rested). Rooms for 3 or 7 days at 14 or 28 coins set the respawn point, and the previous one is restored on expiry or checkout. Paid rumors (8 for a boss lair, 5 for a dungeon or tower) only charge if something is found. The innkeeper sells `isekai_mahou:home_key` for 128 coins if the item exists.
- **Sidequests**: 5 citizen, 4 guard, 4 merchant and 3 lord templates, each with 2–3 steps and a final two-way choice. Rewards run 0–15 coins, −4 to +10 reputation and 45–110 XP. One active quest, 3 per game day; an accepted quest lapses after 3 days.
- **Routine**: one citizen in three spends early evening near the innkeeper, then goes home. Shops close from 17000 to 23500 ticks; inn, priest and fence stay open.
- **Scribe**: no glyph scrolls; sells paper, ink, feathers, books, maps, bookshelves and lecterns.

## How to test
Run `/mahou admin login` first if the gate is on.
- **Starting city**: create a new world; you should spawn on a city plaza. `/mahou city start info`, `find`, `here`, `tp`, `clear`.
- **Inn**: `/mahou city spawnnpc merchant innkeeper`, then right-click → Serviços. Without an NPC: `/mahou city inn rest`, `rent 3`, `status`, `expire`. Expiry: `/time add 72000`.
- **Quests**: stand near any city NPC, then `/mahou city quest list`, `start lost_purse`, `step`, `status`, `reset`. Talk to the giver via the "Pedido" button to choose an outcome. Natural offers: about 45% of citizens and merchants, 35% of guards and every lord have one per day.
- **Routine**: `/time set 12500` (tavern), `/time set 17500` (shops closed, "Trocar" tooltip), `/mahou city routine off|on|status`.
- **Scribe**: `/mahou city spawnnpc merchant scribe` → Trocar.

## Not done / not verified
- **Old worlds also get the starting city.** I read "Mundo antigo: não mude o spawn de quem já tem cama" as: only the world spawn moves. Players with a bed, anchor or room are untouched and existing players are never teleported, but bedless players in an old world will now respawn in the city. `/mahou city start clear` restores the original spawn. Say if old worlds should be skipped entirely.
- The city search runs synchronously at server start, once per world; the stall length is unmeasured.
- The fence still sells one tier-4 "contraband" glyph scroll at Exalted, and the Lord's Honored reward still gives one. The contract named only the Scribe, so I left them.
- Quest "visit" targets are text-only (no cat or recruit entity spawns).
- No overhead quest marker; givers are found through an occasional speech line and the dialogue button.
- NPCs have no real houses, so "home" is their spawn point.

## PEDIDOS
1. **Server pack**: set `spawn-protection=0` in `server.properties`. The world spawn is now a city plaza, and vanilla spawn protection would stop non-op players from using doors, chests and the Travel Stone within 16 blocks of it.
2. **ui (Hints)**: once `Hints.show` exists, first-time hints for "Pedido" and the inn would fit; nothing is wired.
3. **home_realm**: the item id must be exactly `isekai_mahou:home_key` for the innkeeper offer to appear.
4. **Coordinator**: if `tools/gen/cities.py` is ever re-run, its output for the three hand-patched strings already matches. Do not add the new dialogue groups to `CityLines.COUNTS`, since they live in `cities_npc.py`.