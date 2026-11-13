**city_pace report** — the whole section is in and compiles; nothing was run in game or headless. `check-module.sh settlements` → TUDO OK, `check-module.sh city_arcana` → TUDO OK, `check-tree.sh city_pace` → COMPILOU OK.

## Files touched
All Java under `src/main/java/com/renanpontes/isekaimahou/feature/settlements/`.
- **New:** `CityPace.java` (durations table, time accrual, hooks, diary, inauguration, player help), `CityPaceConfig.java`, `tools/gen/settlements_w6_pace_lang.py`.
- **Edited:** `Settlement`, `SettlementService`, `SettlementsModule`, `SettlementView`, `CityViewExtra`, `CityActions`, `CityPlanning`, `CityDebugCommands`, `build/Project`, `build/ConstructionEngine`, `magic/CityMagic`, `client/SettlementPanel`.
- **Lang:** `src/main/lang/settlements/{pt_br,en_us}.json` — 63 keys added, 5 existing ones reworded (the two work-magic glyph descriptions, `city_magic_build` hint, `ui.plan.upgrade.tip`, `ui.page.works.tip`). No key removed.

## New ids
- **Config:** `config/isekai_mahou-city-common.toml`, option `cityPace` (default 1.0, range 0.05–10), with name and tooltip in the Minecraft config menu.
- **Optional NBT:**
  - Settlement: `PaceTime`, `PaceDay`, `Diary`, `PaceDone`.
  - Project: `Days`, `Worked`, `Gift`, `Hands`, `HandsDay`, `Magic`, `MagicDay`, `Stage`, `Scaf`.
- **Action:** `pace_deliver`.
- **Hints:** `city_pace`, `city_pace_help`.
- **Commands (op):** `/mahou city days <1-40>`, `/mahou city pace`, `/mahou city pace <0.05-10>`.

## Durations (Minecraft days at pace 1; all in the table at the top of `CityPace`)
| Work | Days |
|---|---|
| house_small, well, garden, statue, arch, trap_field, watchtower, arcane_beacon | 1 |
| granary, farm | 1.5 |
| house_large, builders_hall, port, stable, sawmill, workshop, mine, smithy, hospital | 2 |
| market, barracks, tavern, library, arena, guild, mana_spire | 3 |
| temple, town_hall, 7 elemental temples, arcane_ward, null_obelisk, obelisk | 4 |
| magic_tower | 5 |
| sphinx | 7 |
| castle, pyramid | 8 |
| Unlisted key / unlisted wonder | 3 / 7 |
| Wall ring 1 / 2 / 3+ | 3 / 4 / 5 |
| Upgrade to level N | base × (0.6 + 0.3·N), rounded to ½, max 10 |
| Rank-up to Vila / Burgo / Cidade / Capital | 5 / 6 / 8 / 10 |
| Founding plaza, flatten or demolish | 0.25 |
| Repair | 0.5 |
| Wall renovation, plaza restyle | 3 |
| Building restyle | base × 0.75 |
| Moved building | base × (1 + 0.25 per level above 1) |

- **Speed bonuses:** style build speed × (1 + building and happiness %), festival ×1.15, miserable city ×0.75, clamped to 0.5–2.0. Spare builders add 10% each, split over active works, up to +50%.
- **Player help:**
  - Materials (button in Obras): up to 25% of a work; 192 points = 1 day.
  - Hammering (sneak + empty-hand click inside the site): 0.004 day per swing, 0.15 day per work per day.
  - Work magic: 0.75 day per work per day, shared by both spells. Erguer Obra now adds 0.3–0.65 day by Circle instead of finishing; Moldar Lote is still instant.

## What it does
- **Works run on world time.** Every second the server adds elapsed Overworld day-time to all founded cities, loaded or not. Sleeping and `/time add` count; with `doDaylightCycle false` it falls back to game time.
- **Blocks follow the days.** Builders only place blocks up to the fraction of days worked; after days away they catch up at the old speed.
- **Queue carry-over.** When a work's days are done, leftover time goes to the next in the queue.
- **Old worlds.** Works in progress get a duration on first sight and keep the fraction already built.
- **Site.** The floating sign reads "Mercado — Paredes · dia 2 de 3". Scaffolding goes up at the four outer corners when blocks start and comes down at finishing (only into air, only the recorded blocks are removed).
- **Dawn.** One chat line after the daily account: active works with day X of Y, queue size, finished yesterday.
- **Panel.**
  - Obras rows show day X of Y, stage, a Materiais button and the current pace %.
  - New Diário sub-tab under Obras.
  - The planner shows days before queueing: catalog rows, upgrade, walls, rank-up.
- **Inauguration.** Bell, fireworks and one "before → after" chat line. Works of 5+ days also get a 50-tick non-locking banner for players nearby.
- **Rank requirements** were reviewed and left unchanged; none becomes impossible.

## APIs for city_trades / city_monuments (all in `CityPace`)
- **Duration of a work:**
  - `CityPace.register("<blueprint key>", days)` in module init sets the level-1 build time; upgrades follow `upgradeDays(key, toLevel)` automatically.
  - Alternatively set `project.days` yourself before adding it to `s.projects`.
  - Read with `baseDays(key)`, `upgradeDays(key, level)`, `nominal(s, p)`.
  - `real(days)` applies the server pace; `daysText(realDays)` gives "2½ dias".
- **Work finished:** `CityPace.onWorkDone((ServerLevel sl, Settlement s, Project p, boolean grand) -> …)`.
  - Fires after the city has recorded the result, for every work kind — filter by `p.kind`.
  - `p.key`, `p.kind`, `p.cell` and `p.level` (resulting level) are available.
  - It does not fire if the blueprint key no longer resolves.
- **New city day:** `CityPace.onNewDay((ServerLevel sl, Settlement s, long day, int elapsed) -> …)`.
  - Founded cities only, and only with the heart chunk loaded.
  - It is one call with `elapsed` = days since the last call (1 normally, more after the city was unloaded or time jumped) — not one call per missed day.
  - For data that must change while unloaded, compare against `CityPace.today(server)`.
- **Server pace multiplier:** `CityPace.paceMult()` multiplies duration (2 = twice as long).
- **Extras:** `diary(server, s, Component)`, `work(server, s, p, days)`, `workAt(server, s, cell)`, `fraction(p)`, `dayNumber(p)`, `totalDays(p)`, `stageText(p)`, `speed(server, s)`.

## How to test
1. `/mahou admin login <senha>`, found a city, `/mahou city pace` — the plaza shows "dia 1 de 1".
2. Skip time with any of: sleep, `/time add 24000`, or `/mahou city days 1` (does not move the world clock; also fires the dawn line and the new-day hook). `/mahou city finish` still completes everything at once.
3. Queue a house and a market from the planner: the catalog shows "1 dia" / "3 dias", the sign shows stage and day, scaffolding appears when blocks start.
4. Obras tab: day X of Y, stage, pace %; with planks or stone in the inventory click Materiais. Sneak + empty-hand click inside the site to hammer.
5. `/mahou city days 3` → inauguration line, bell and fireworks; open Obras → Diário.
6. Upgrade a building and skip days → chat shows "nível 1 → 2 (before → after)".
7. `/mahou city magic hasten` and `erect` near a site: partial progress; repeating `erect` hits the daily limit.
8. `/mahou city pace 0.1` makes everything ten times faster; `/mahou city pace 1` restores it.
9. Catch-up: queue works, walk far away, `/time add 72000`, return — builders rebuild quickly what was due.
10. Old world with a work in progress: it keeps its built fraction and shows a day count.

## Not done / not verified
- Nothing was executed, not even a headless JVM test: panel layout, sign text, scaffolding on real terrain, catch-up speed and the dawn line are unverified.
- Scaffolding only appears on corners inside the lot with a sturdy floor (not on dirt path), so buildings wider than 13 blocks get none.
- The dawn summary is a third chat line after the two existing daily lines.
- With the daylight cycle frozen, works advance but the daily caps (hammering, magic) never reset because the day number does not change.
- "Each building level visibly changes the building and its service" relies on the existing per-level templates and effect multipliers; no new per-level visuals were added.
- Erguer Obra no longer has the "small works only" limit; `CityMagic.erectLimit` and the `magic.too_big` / `magic.erect` / `plan.done.upgrade` keys are now unused but kept.
- Balance of every number above is untested.

## PEDIDOS
- `docs/ARCHITECTURE.md`: add `CityPace` (the APIs above), `CityPaceConfig`, the config file, hints `city_pace` / `city_pace_help` and the new commands.
- If `tools/gen/settlements_lang.py`, `settlements_v14_lang.py` or `settlements_styles_lang.py` are re-run, run `py -I tools/gen/settlements_w6_pace_lang.py` afterwards; the v14 generator would restore the old "finishes instantly" glyph texts.
- Journal/guide texts elsewhere that say Erguer Obra finishes a work instantly should be reworded.