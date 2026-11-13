**city_life report** — compiles clean (`bash tools/check-module.sh settlements` → TUDO OK, `bash tools/check-tree.sh city_life` → COMPILOU OK). Nothing was run in game; everything below is checked by compile and reasoning only.

## Files touched
All under `src/main/java/com/renanpontes/isekaimahou/feature/settlements/`, plus lang and the generator. `feature/city_arcana/**` was not touched.
- **New:** `CityHappiness.java`, `CityGrowth.java`, `CityCaravans.java`, `CityDefense.java`, `CityGoals.java`, `CityStash.java`, `CityLifeView.java`, `client/CityLifePages.java`.
- **Edited:** `Settlement.java`, `CityEconomy.java`, `Lordship.java`, `CityRaids.java`, `CityPlanning.java`, `CityActions.java`, `CityViewExtra.java`, `SettlementView.java`, `SettlementService.java`, `CityDebugCommands.java`, `build/Blueprints.java`, `build/ProcBuildings.java`, `client/SettlementPanel.java`.
- **Lang:** `tools/gen/settlements_v14_lang.py` (new v1.0 city_life block, re-run) → `src/main/lang/settlements/{pt_br,en_us}.json` (+188 keys, none removed).

## New ids
- No registry ids. One new building key: `trap_field` (procedural, all three styles).
- New optional Settlement NBT: `Caravans`, `CaravanLast`, `CaravanMarket`, `Stash`, `Goals`, `GoalsDone`, `LifeStats`, `ScoutDay`. No existing key renamed or removed.
- New panel actions: `caravan_send`, `collect`.

## What changed
- **Happiness:** the old approval value (0–100) is now shown as "Felicidade" with a mood, each of today's reasons as a +/− line, and a hint for the worst one.
  - A happy city (70+) produces more, builds 10–20% faster, gets double immigration and one extra caravan slot.
  - It never drops more than 10 in a day; revolt now takes 3 days below 10, with a warning each day.
  - The daily chat report is cut to the summary plus one alert.
- **Growth:** opening a ring now needs residents (12, 24, 40, 60, 84, 112…), happiness (40–60), half the current plots built, and center level 3 / 4 / 5 (rings ≤4 / ≤6 / 7+).
  - Building limits scale with city size: repeatable buildings multiply per ring, district services get +1 per 2 rings. Before this, a city could not actually get past about 11×11.
  - NPC entity caps: 40 residents, 20 guards, 8 merchants.
  - The plan packet is slimmer for big cities.
- **Festival:** lasts 2 game days with the `CityDecor` decorations and fireworks. It gives +8 happiness at the start and +4 per day, taxes +30% and caravans +20%. A new one is allowed 5 days after the last began (it used to be +15 every day).
- **Caravans:** a city with a Market sends a merchant to one of the 10 nearest cities or villages, including other players' cities.
  - Travel is a percentage (going → trading → returning) and works with the city unloaded.
  - Five missions: sell or buy food or fuel, or trade goods.
  - Prices differ per city; far pays more; repeated same-day trips to one city flood its market.
  - Bandit risk is small and never takes everything.
- **Siege defense:** during raids, watchtowers, wall corner towers and the Magic Tower shoot the horde, the new Trap Field damages and slows it, and Barracks buff guards and militia. With a watchtower, scouts warn a day ahead; the 2-minute warning also shows as a top banner.
- **Quality of life:** up to 3 "the city needs" tips, two residents' requests at a time with a reward, daily goods from mine/sawmill/farm/port into a storehouse, and a "Coletar tudo" button that takes taxes and goods from anywhere inside the city.
- **Panel:** tabs are now Geral, Planejar, Felicidade, Recursos, Obras, Caravanas, Defesa, Decretos. Conselho and Guerra became sub-tabs under Decretos and Defesa. "Apoio/afinidade" is renamed "Felicidade" in all city text.

## How to test
1. `/mahou admin login <senha>`, found a city, `/mahou city level 3`, queue buildings, `/mahou city finish`.
2. Happiness: open the Felicidade tab; `/mahou city happy` prints the same reasons. Try `/mahou city approval 95` and `15`, then `/mahou city day`.
3. Growth: `/mahou city people 30`, `/mahou city approval 60`, build 12 plots, then Planejar → expand (requirements show when no plot is selected). `/mahou city radius 7` checks the 15×15 map.
4. Festival: use the button in Felicidade, or `/mahou city festival`; `/time set night` for fireworks; `/mahou city day` twice; `/mahou city festival end`.
5. Caravans: build a Market, open Caravanas, pick a destination, a size and a mission. `/mahou city caravan` brings them all back now.
6. Storehouse: `/mahou city stash`, then "Coletar tudo" in Geral or "Coletar" in Caravanas.
7. Defense: build a Torre de Vigia, a Campo de Armadilhas and a Quartel, then `/mahou city raid now` and open Defesa. `/mahou city day` on the day before a raid shows the scout warning.
8. Requests: `/mahou city goals` completes the current ones within 10 s; `/mahou city goals new` rerolls.

## Not done / not verified
- All panel layouts, the Trap Field's look, tower line of sight and caravan balance are unverified in game.
- Tower "archers" are hit-scan (particle trail, sound, damage); no archer NPC stands on the tower.
- Caravans have no visible NPC leaving or arriving, by design ("vai como %").
- Desert has no watchtower of its own; it is built through the style selector in the catalog, as before.
- Conquered kingdom cities get happiness, tips and the festival, but not caravans, defense, requests or the storehouse.
- No recall or cancel for a caravan in transit.
- Terrain warnings in the plan view are computed only for rings ≤5.

## PEDIDOS
- Any text outside `settlements` (journal, guide, tutorial) that still says "apoio/afinidade" or "festival +15 por dia" should be updated to "Felicidade" and the 2-day festival.
- If `tools/gen/settlements_lang.py` is ever re-run, run `tools/gen/settlements_v14_lang.py` afterwards; it re-applies the v1.0 keys and the rename.