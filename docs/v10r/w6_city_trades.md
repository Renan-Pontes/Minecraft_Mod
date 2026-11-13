**city_trades report** — the whole section is in and compiles; nothing was run in game. `check-module.sh settlements` → TUDO OK, `check-module.sh city_arcana` → TUDO OK, `check-tree.sh city_trades` → COMPILOU OK. One headless test passed: the catalog of 8 of the 9 trades across all 7 styles and 130 days had no duplicate keys and no invalid stacks, prices or stock. It ran with vanilla items only (mod-item offers were absent there), and the Arcane Merchant could not be covered (needs the mod's mana crystal registered).

## Files touched
All Java under `src/main/java/com/renanpontes/isekaimahou/feature/settlements/`.
- **New Java:** `CityTrades.java` (trades, catalog, daily stock, Lord's fee, arrival/respawn, services, panel data, commands), `entity/TraderNpc.java`, `client/CityTradesPage.java`.
- **Small edits in shared files:** `Settlement` (optional `trades` tag), `SettlementsContent` (entity, egg, attributes, creative tab), `SettlementsModule` (`CityTrades.register()`), `SettlementService.heartbeat` (2 lines), `CityViewExtra` (panel data and the "what you get" line), `CityTier.gains` (one line), `client/SettlementsClient` (renderer), `client/SettlementPanel` (sub-tab).
- **Generators:** `tools/gen/settlements_w6_trades.py` (18 textures `textures/entity/settlement_npc/trader_<trade>_<0|1>.png` and the egg model) and `tools/gen/settlements_w6_trades_lang.py` (151 keys added to `src/main/lang/settlements/{pt_br,en_us}.json`, none removed or reworded).
- **Side effect:** running the generators may have rewritten tracked `tools/gen/__pycache__/*.pyc`.

## New ids
- **Entity:** `settlement_tradesman`; item `settlement_tradesman_spawn_egg`.
- **Trade ids (saved, append-only):** `blacksmith`, `healer`, `scribe`, `innkeeper`, `stablehand`, `jeweler`, `lumberjack`, `miner`, `arcanist`.
- **Optional NBT:**
  - Settlement `Trades`: `Npc`, `Names`, `Day`, `Used`, `FeeCents`, `FeeDay`, `FeeLast`, `FeeTotal`, `Sales`, `SalesLast`, `HookDay`.
  - Entity: `TradeJob`.
- **Hints:** `city_trades`, `city_trades_new`.
- **Panel page:** `shops` (sub-tab "Comerciantes" under Caravanas).
- **Commands (op):** `/mahou city trades`, `/mahou city trades spawn`, `/mahou city trades restock`, `/mahou city trades clear`.

## What it does
- **One tradesman per trade per city**, tied to a building; the highest-level building of that kind is the shop.

  | Trade | Building | Sells | Sneak + click service |
  |---|---|---|---|
  | Ferreiro | smithy | iron tools, weapons, armor | repair; reinforce (Unbreaking I/II/III at smithy level 2/3/5) |
  | Curandeiro | hospital | health potions, remedies | full heal and harmful effects removed, 3 coins |
  | Escriba | library | paper, ink, books, blank recipe scrolls, lost pages | — |
  | Taverneiro | tavern | food, return scrolls, bond incense | free rumor (raid forecast or a true city tip) |
  | Estribeiro | stable | saddle, leads, hay, horse armor, mount whistle, drake reins | — |
  | Joalheiro | workshop | gem chisel, chipped gems (rotate daily) | — |
  | Lenhador | sawmill | the style's own wood, other woods rotating | — |
  | Minerador | mine | stone, coal, ores | — |
  | Mercador Arcano | magic_tower or mana_spire | mana crystals, mana potions, essences (rotate daily) | — |

- **Stock grows with the building and the rank.** Building level opens more offers and adds 25% stock per level; city rank adds 10% per rank and opens the rare items (e.g. mithril ingot and cut gem at Cidade, arcane smithing template at Capital). Buy offers grow only 12.5% per level so raw materials are not an easy coin source.
- **Stock belongs to the city and refills at dawn.** It is stamped with the world day, so it refills even while the city is unloaded; the city_pace new-day hook also refills it and writes yesterday's deals in the Diário.
- **Currency and screen** are the ones `MerchantNpc` uses (guild coin, vanilla trade screen); the price decree applies.
- **The Lord gets 10%** of every sale and service into the treasury.
- **Services** show the price first and need a second sneak-click within 6 s.
- **Look and routine:** own outfit per trade (male and female), name with the trade above the head only when a player is within 10 blocks, stays within 5 blocks of the shop by day and 12 at night.
- **Never gone for good:** a dead tradesman is replaced after about 1 minute, a lost one after 2; one leaves if his building is demolished.
- **Panel:** Caravanas → Comerciantes has one line per trade (who the city has with shop level and open offers, or who is missing and which building and rank brings him) plus the fee summary.
- **Planner and rank tab:** the building's effect line now includes e.g. "Ferreiro (8 ofertas)" and the Porte gains list has a tradesmen line.
- **All 7 styles** share the same outfits; names and the lumberjack's wood follow the city style.

## How to test
1. `/mahou admin login <senha>`, found a city, `/mahou city finish`, `/mahou city tier 4`.
2. Queue smithy, hospital, library, tavern, stable, workshop, sawmill, mine and mana_spire in the planner, then `/mahou city finish`.
3. Tradesmen arrive one every ~10 s (action bar). `/mahou city trades spawn` brings all at once; `/mahou city trades` lists them.
4. Right-click one and buy until an offer is crossed out. Skip the day with sleep, `/time add 24000` or `/mahou city trades restock`. `/mahou city days 1` restocks only when the new-day hook has already fired once that world day, so use it twice if the first does nothing.
5. Heart → Caravanas → Comerciantes: check the lines and the fee; the treasury should rise by 10% of coins spent.
6. Blacksmith: sneak + click twice with a damaged iron tool. Stand in the smithy lot, `/mahou city blevel 3`, then sneak + click twice with an undamaged tool for Unbreaking.
7. Healer: take damage, sneak + click twice. Innkeeper: sneak + click for a rumor.
8. `/mahou city blevel 5` in a shop lot plus `/mahou city tier 5`, reopen the shop: more offers and the rare ones.
9. Kill a tradesman in creative: another arrives in about a minute. `/mahou city trades clear` removes all.
10. `/mahou city style <id>`: the lumberjack's wood changes.

## Not done / not verified
- No in-game run: outfits on the model, the trade screen, panel layout, walking from the gate and the day/night radius are unverified.
- Mod items are looked up by id and silently skipped if absent; the ids were checked against the item model list, not in game.
- Prices, stock numbers and service costs are untested for balance.
- Only three trades have a service; the other six open a short talk on sneak + click.
- A tradesman is not shown to plain visitors in the panel (the page is for Lord and council).
- A shop in a damaged building serves as level 1 until repaired.
- A Desert city has no tavern of its own style; the Innkeeper line names another style's tavern, which the planner's cross-style catalog offers.
- Tradesmen are skipped while their shop is outside the entity-ticking area, to avoid piling up replacements.
- No new journal chapter.

## PEDIDOS
- **journal** (`feature/journal/bestiary/Bestiary.java`, the `Category.ALLY` list around line 96): add `"settlement_tradesman"` next to `settlement_merchant`.
- **integration** (`docs/ARCHITECTURE.md`): add `CityTrades`, entity `settlement_tradesman`, hints `city_trades` and `city_trades_new`, and `/mahou city trades`.
- **Lang generators:** `settlements_w6_trades_lang.py` and `settlements_w6_monuments_lang.py` rewrite the same JSON; do not run them at the same time, and re-run mine after any older settlements lang generator.