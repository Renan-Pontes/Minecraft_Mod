**integration — report**

All deliverables of the section are built and compile; nothing was run in game.

- `bash tools/check-module.sh cities` and `bash tools/check-module.sh guild` both end in TUDO OK.
- `bash tools/check-tree.sh integration` did **not** reach COMPILOU OK in four runs. Every error was in files other agents are editing (`feature/transform_laws`, `forms_b/summon`, `lore`, `ambience`, `depths/puzzle`, `settlements/client/FoundingScreen`).
- My own set (`MahouConfig`, the three client classes, all of `feature/cities` and `feature/guild`, 101 files) compiles clean in a separate javac run against `build/classes`.

**Files touched** (under `c:/Users/renan/Music/ROCKET/RocketSimVis/Minecraft_Mod/`)
- `src/main/java/com/renanpontes/isekaimahou/feature/cities/CityFixtures.java` (new)
- `feature/cities/block/CityHeartBlockEntity.java`, `CityCommands.java`, `CitiesModule.java`, `CityQuests.java`, `CityInn.java`, `trade/CityTrades.java`
- `feature/guild/client/GuildScreen.java`
- `core/MahouConfig.java` (three new client options only)
- One-line option read in `client/social/PartyMarkers.java`, `feature/renown/client/TitleTags.java`, `feature/renown/client/FightTimerHud.java`
- `src/main/lang/integration/{pt_br,en_us}.json` (new folder), `src/main/lang/ui_hud/{pt_br,en_us}.json` (six option keys each)
- `docs/ARCHITECTURE.md` (new section 6b)

**What was built**
- **Blocks in NPC cities:** the city heart places them at runtime, once per city, so it works in new and existing worlds.
  - `market_board` goes in every city plaza, beside an avenue and facing it.
  - `new_beginnings_altar` and `reincarnation_altar` go only in capitals, on opposite sides of the plaza (34+ blocks apart preferred, never under 24).
  - Placement is only into air (or a weather snow layer) on sturdy floor, off the avenues, away from doors. Nothing is broken, and a removed block does not come back.
  - `home_realm` is skipped.
- **Merchant trades**, all by id (offer disappears if the item is missing):

  | Merchant | Offer | Coins | Condition |
  |---|---|---|---|
  | Innkeeper, Grocer | `return_scroll` ×2 | 3 | — |
  | Innkeeper | `bond_incense` | 14 | — |
  | Innkeeper | `land_drake_reins` | 45 | Friendly |
  | Alchemist | `alchemist_bag` | 26 | — |
  | Jeweler | `gem_chisel` | 16 | — |
  | Jeweler | 2 random chipped gems per day, 1 each | 22 | Friendly |
  | Blacksmith | buys `world_events_star_metal` | pays 5 | — |

- **Guild:** "Mercado" button under the tabs in `GuildScreen`, calling `StatusScreen.open("market")`. It falls back to the Reception list on short screens and is hidden on faction boards or if the `market` tab is not registered.
- **Minecraft-menu options:** `showPartyMarkers`, `showFightTimer`, `showPlayerTitles`, with PT-BR/EN names and tooltips; the three client classes read them.
- **Hints:** `cities.market_board`, `cities.capital_altars`, `cities.inn`, `cities.request` (the last two were the wave-1 cities_npc request).
- **Docs:** section 6b lists the wave 1–2 ids, tabs, counters, hints, config keys and public APIs named in the contract.

**New ids**
- Config keys `showPartyMarkers`, `showFightTimer`, `showPlayerTitles`
- Optional city-heart NBT keys `FixDone`, `FixTries`, `FixMarket`, `FixRenewal`, `FixReinc`
- Command `/mahou city fixtures [retry]`
- The four hint ids above; lang key `screen.isekai_mahou.guild.go_market`
- Trade stock keys `return_scroll`, `bond_incense`, `land_drake_reins`, `alchemist_bag`, `gem_chisel`, `gem_<type>_chipped`, `buy_star_metal`

**How to test** (after `/mahou admin login <senha>`)
1. `/mahou city place capital`, wait about 10 s in the plaza, then `/mahou city fixtures`. It lists the three blocks with coordinates; click a line for a `/tp` suggestion.
2. `/mahou city fixtures retry` re-places anything missing. In a normal city (`/mahou city place city`) only the Market Board appears and the altars report "só nas capitais".
3. In an old world, walk into an existing city plaza and run the same command.
4. `/mahou city spawnnpc merchant innkeeper` (also `grocer`, `alchemist`, `jeweler`, `blacksmith`) and open each shop. Raise reputation to Friendly to see the reins and chipped gems.
5. Talk to a Guild Receptionist: the "Mercado" button sits under the tabs and opens B → Mercado.
6. Esc → Options → Isekai Mahou → "Interface e efeitos": toggle the three new options with a party member 20+ blocks away, a boss fight running, and another player wearing a title.
7. `/mahou hints reset`, then enter a capital, open an innkeeper and accept a "Pedido" to see the four hints.

**Not done / not verified**
- Altar and board positions were chosen from the plaza generator's layout, not seen in game.
- A city whose plaza has no free candidate spot gives up after about 20 minutes loaded; `retry` reopens it.
- City and capital templates (.nbt) were not changed; placement is runtime only.
- `renown_board` was not added to templates; the existing runtime `BoardPlacer` still covers it.

**PEDIDOS**
- **Coordinator:** re-run `bash tools/check-tree.sh <tag>` once the wave 3–4 agents finish.
- **Coordinator:** new lang lives in `src/main/lang/integration/` because `lang/cities` and `lang/guild` are rewritten by `tools/gen/cities.py` and `tools/gen/guild.py`.
- **Server pack:** set `spawn-protection=0` (wave-1 cities_npc request, still open).
- **Not in my area, still open from the wave 1–2 reports:**
  - `core/ux/PeaceMode.onTick` should guard its automatic toggles with `MahouConfig.peaceAuto(sp)`.
  - Six camera-shake sites still need `MahouConfig.shakeScale()`: `BossCameraShake`, cinematic `CameraShake`, earth_wind `CameraShake`, `DomainClientEffects`, `HollowFxClient`, `FormFx`.
  - `AdminAuth` shows "admin required" instead of the Travel Stone message on `/tp`.
  - The Grimoire "write scroll" button still makes a cheap reusable `spell_scroll`.
  - `endless_abyss` should register its `PortalGuide.register` entry.
- **story agent (journal):** Bestiary entries for `familiar_spirit`, `land_drake`, `nightmare`, `aurora_stag`, and the four `world_events_alien_*`.
- **settlements agents:** "apoio/afinidade" text outside `settlements` should read "Felicidade".