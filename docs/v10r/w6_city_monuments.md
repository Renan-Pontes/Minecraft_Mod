**city_monuments report** — the whole section is in and compiles; nothing was run in game or headless (no Java process was started, so the new building shapes were never generated even once). `check-module.sh settlements` → TUDO OK, `check-module.sh city_arcana` → TUDO OK, `check-tree.sh city_monuments` → COMPILOU OK (with the city_trades files as they were at that moment).

## Files touched
All Java under `src/main/java/com/renanpontes/isekaimahou/feature/settlements/`.
- **New:**
  - `CityMonuments.java` — bonus table, active/dormant logic, player buffs, blessing, panel data, test commands.
  - `build/MonumentBuildings.java` — 9 procedural buildings × 3 levels, plus their blueprints.
  - `client/MonumentPage.java` — the "Bônus" sub-tab.
  - `tools/gen/settlements_w6_monuments_lang.py`.
- **Small edits:** `Settlement`, `SettlementsModule`, `SettlementService` (obelisk aura gate), `CityPerks`, `CityPlanning.upgrade`, `CityActions`, `CityEconomy.stats`, `CityHappiness`, `CityCaravans`, `CityTier.gains`, `CityViewExtra`, `build/Blueprints`, `build/ProcBuildings`, `client/SettlementPanel`.
- **Lang:** `src/main/lang/settlements/{pt_br,en_us}.json` — 153 new keys, and 14 reworded (the Statue and Arch descriptions in the 7 styles). No key removed.

## New ids
- **Blueprints, all 7 styles:** `hanging_garden` (Vila), `eras_obelisk` and `great_lighthouse` (Burgo), `guardian_colossus` (Cidade).
- **Style wonders (Cidade, one per style):** `mead_hall` (Nordic), `grand_pagoda` (Oriental), `world_tree` (Elven), `grand_cathedral` (Gothic), `great_forge` (Dwarven).
- **Optional NBT:** Settlement `Monuments` {`Active`, `Init`, `Manual`}; player persisted tag `isekai_mahou_monument` {`Key`, `Level`, `Day`, `Until`}.
- **Action:** `monument_toggle`. **Hints:** `city_monument`, `city_monument_bless`. **Happiness factor:** `monuments`.
- **Attribute modifiers (transient):** `isekai_mahou:city_monument_<max_mana|mana_regen|armor|health|speed|luck>`.
- **Commands (op):** `/mahou city monuments`, `/mahou city monuments toggle <n>`, `/mahou city bless`, `/mahou city bless clear`.

## Bonuses (levels 1 / 2 / 3)
| Monument | Bonus name | Effect |
|---|---|---|
| Statue | Memória dos Heróis | XP +3/5/7% |
| Arch | Passo do Viajante | speed +4/7/10%, caravan profit +3/5/8% |
| Santuário da Luz | Graça da Luz | heals 1/2/3% of max health every 10 s |
| 6 elemental temples | Bênção do <elemento> | element damage +3% per level (as before), element mastery XP +8/14/20% |
| Arcane temple | Bênção Arcana | all spell damage +2% per level (as before), max mana +2/4/6% |
| Magic Tower | Saber da Torre | max mana +4/7/10% |
| Mana Spire | Fonte de Mana | mana regen +5/10/15% |
| Sun Obelisk | Bênção do Sol | Regeneration I aura (as before), heal 1/2/2% |
| Pyramid | Tesouro do Faraó | luck +1/1/2, XP +2/3/4% |
| Sphinx | Olhar da Esfinge | armor +2/3/4 |
| Castle | Estandarte Real | max health +3/5/8% |
| Obelisco das Eras | Sabedoria das Eras | XP +4/6/8%, all element mastery +4/7/10% |
| Colosso do Guardião | Vigília do Colosso | armor +2/3/4, city security +6/9/12 |
| Grande Farol | Luz do Farol | caravans +8/14/20%, taxes +4/7/10%, speed +3/5/7% |
| Jardim Suspenso | Fartura do Jardim | city food +10/20/30%, happiness +1/1/2 per day, hunger +1/2/3 per minute |
| Salão dos Heróis | Coragem do Salão | max health +3/5/8%, happiness +1/2/2 |
| Grande Pagode | Serenidade do Pagode | mana regen +6/10/15%, all mastery +3/5/8% |
| Árvore-Mãe | Seiva da Árvore-Mãe | heal 1/2/3%, food +10/15/20% |
| Catedral das Sombras | Véu da Catedral | max mana +4/7/10%, security +4/6/8 |
| Grande Forja | Têmpera da Forja | armor +2/3/4, taxes +4/7/10% |

## How it works
- **Who gets it:** players the city protects, while inside the city.
- **Stacking:** equal bonuses add up to a cap — XP 15%, mastery 30%, max mana 15%, mana regen 25%, heal 4%, armor 6, health 10%, speed 12%, luck 3.
- **Active limit by rank:** Vila 1, Burgo 2, Cidade 3, Capital 5. Extra monuments stay "dormant": the building and its ordinary city effects remain, only the named bonus stops.
- **Swapping:** in the panel under Geral → Bônus. A monument consecrated today can only go dormant after dawn.
- **Auto-fill:** a new monument takes a free slot by itself. Old worlds get their existing monuments consecrated on first load, highest level first. Free slots are refilled at dawn until the Lord swaps one by hand.
- **Blessing:** sneak and right-click an active monument's lot with an empty hand, once per day. It gives half of that monument's player bonuses for one Minecraft day, anywhere. Inside the city the larger of the two applies; they do not add.
- **Levels:** monuments stop at level 3. Each upgrade is another multi-day work through the existing planner. Magic Tower and Mana Spire keep 5 building levels (other systems read them), but their bonus stops at 3.
- **Durations:** Statue and Arch raised from 1 to 2 days; Obelisco das Eras and Jardim Suspenso 5; Colosso and Farol 6; style wonders 7.
- **Where the buffs plug in:** transient attribute modifiers, `SpellDamageEvent`, `MagicProgressEvent.ElementXp`, direct heal, and the city's daily numbers. No mixins and no new HUD icons.
- **Feedback:**
  - one action-bar line when entering the city;
  - a few particles on active monuments;
  - a Diário line when one is consecrated;
  - a chat line only when a finished monument stays dormant.
- **Planner:** the catalog, the plot details and the inauguration line all show the named bonus next to the days. The rank "what you gain" list shows the monument limit.

## How to test
1. `/mahou admin login <senha>`, found a city, `/mahou city finish`, `/mahou city tier 5`.
2. Stand on free ground and run `/mahou city free statue`, then `/mahou city free eras_obelisk`, `guardian_colossus`, `great_lighthouse`, `hanging_garden`, `temple_fire`. The catalog should show "N dias" and the bonus.
3. Skip days with `/mahou city days 6` (or sleep, or `/time add 24000`); `/mahou city finish` completes everything at once.
4. `/mahou city monuments` lists monuments, which are active, and what applies to you now.
5. Open the heart → Geral → Bônus. Toggle Ativo/Adormecido; the same-day lock should refuse, and `/mahou city monuments toggle <n>` bypasses it.
6. Limit: `/mahou city tier 2` leaves one active slot. Build a second monument and expect the "adormecido" chat line.
7. Blessing: sneak and right-click an active monument with an empty hand, leave the city, and check `/mahou city monuments`. `/mahou city bless` forces one; `/mahou city bless clear` resets the day.
8. Upgrade a monument to 2 and 3 in the planner (skip days). The panel should show "Nível 3/3" and refuse a fourth.
9. Style wonders: found a Nordic, Oriental, Elven, Gothic or Dwarven city (or pick that style in the planner catalog) and build its wonder.
10. `/mahou city happy` should show the "Monumentos" line with a Jardim Suspenso active.

## Not done / not verified
- **No in-game run.** Unverified: the shapes of all 9 new buildings, the panel layout, attribute values, the blessing gesture and the entry line.
- **Visual levels 4–5 of the old monument types** (gold statue, largest temples) can no longer be reached by upgrading. Old saves keep what they have.
- **Old worlds with more monuments than the limit** lose the bonus of the extras, including temple spell damage and the Obelisk aura, until the Lord picks which ones count.
- **Pyramid +50% taxes and Sphinx/Castle elite guards** are not gated by active/dormant; they still always apply.
- **XP bonus is done by polling** the player's total XP every 2 s, because the core has no XP-gain event. Level jumps by command are ignored.
- **Dormant temples** also lose the old mana trickle.
- **Citizens who cannot plan** see no Bônus tab; they only get the entry line.
- **Jardim Suspenso level 1:** the last stair step is a one-block jump.
- **New buildings have no entry in the city economy table**, so their only effects are the monument bonuses.
- Balance numbers are untested.

## PEDIDOS
- **core (`MagicApi.giveXp`):** post an XP-gain event with a mutable amount so the XP bonus can drop the polling.
- **integration (`docs/ARCHITECTURE.md`):** add `CityMonuments`, `MonumentBuildings`, the 9 blueprint keys, the two hints, the action and the commands.
- **Generators:** if `settlements_lang.py`, `settlements_v14_lang.py` or `settlements_styles_lang.py` are re-run, run `py -I tools/gen/settlements_w6_monuments_lang.py` afterwards.
- **journal / guide texts:** nothing mentions monuments or the daily blessing yet.