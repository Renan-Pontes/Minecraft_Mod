**city_styles report** — four new player-city styles (Oriental, Elven, Gothic, Dwarven) plus the style "Reforma" are in and compile. `check-module.sh settlements` → TUDO OK, `check-tree.sh city_styles` → COMPILOU OK, `check-module.sh city_arcana` → TUDO OK. Nothing was run in game. A headless test passed: every TierLook complete, 84 wall pieces (7 styles × 4 grades × 3) in bounds with empty gate openings, 875 procedural buildings (7 × 25 × 5) built without exception, and all new `.nbt` templates found with known blocks.

## Files touched
All Java under `src/main/java/com/renanpontes/isekaimahou/feature/settlements/`.
- **New Java:** `CityReform.java`.
- **Edited Java:** `SettlementStyle`, `SettlementsNames`, `Settlement`, `SettlementService`, `CityEconomy`, `CityHappiness`, `CityStash`, `CityRaids`, `CityPlanning`, `CityActions`, `CityViewExtra`, `CityDebugCommands`, `block/SettlementHeartBlock`, `build/{TierLook,TierWalls,ProcBuildings,Blueprint,Project,CityTerrain,ConstructionEngine}`, `client/{FoundingScreen,SettlementPanel,SettlementHeartRenderer}`, `entity/{SettlementNpc,GuardNpc,MilitiaNpc,MerchantNpc,BuilderNpc,ResidentNpc}`.
- **New generators:** `tools/gen/settlements_styles.py` and `tools/gen/settlements_styles_lang.py`. Both only add files/keys; the three original styles' files are not rewritten.
- **Generated resources (140 files):**
  - 84 templates `data/isekai_mahou/structure/settlements/<style>_<piece>.nbt`, including `<style>_lot_{smithy,stable,well,garden}`.
  - 48 NPC textures `textures/entity/settlement_npc/<role>_<style>_<n>.png` and 4 `city_champion_<style>.png`.
  - 4 founding-screen pictures `textures/gui/settlements/style_<style>.png`.
- **Lang:** `src/main/lang/settlements/{pt_br,en_us}.json` (44 new keys plus building names per new style).
- **Side effect:** running the generators touched tracked `tools/gen/__pycache__/*.pyc`.

## New ids
- **Enum values (appended):** `oriental`, `elven`, `gothic`, `dwarven`. No NBT keys added or renamed.
- **Project kind:** `restyle`.
- **Actions:** `reform_style` (Lord only), `plan_restyle`.
- **Hint:** `city_reform`.
- **Command (op):** `/mahou city style` (info) and `/mahou city style <id>` (instant style change, no cost).

## What each style is
| Style | Look | Bonus | Weak point |
|---|---|---|---|
| Oriental | curved eaves, dark tile, red timber, paper windows, cherry trees, torii gate on every wall grade | +1 happiness/day, +15% taxes | building 15% slower, guard armor +2 |
| Elven | pale wood, mossy stone, green roofs with leaves, froglight lamps, leaves on walls | +30% food, half fuel use | rank needs 25% more prosperity, guard armor 0 |
| Gothic | dark stone, steep roofs with iron spires, purple glass, soul lights everywhere | guard armor +6, +30% security | −1 happiness/day, wither skeletons in raids from Burgo |
| Dwarven | cut stone with copper bands, copper roofs, forge braziers | mines ×2 plus copper, building 20% faster, guard armor +4 | −25% food, +25% fuel, rank needs 10% more prosperity |

Each also has its own streets, lamps, plaza kerb, monument, fountain, facade trim, four wall palettes, NPC names, merchant trades and resident lines.

## Reforma (style change)
- **Where:** heart → Geral → Porte → "Reforma de estilo"; arrows pick the style, the button needs a second click to confirm.
- **Cost (treasury):** 150 / 230 / 530 / 1330 / 3130 coins for ranks 1–5.
- **What changes gradually:** the plaza is rebuilt through the work queue; streets, lamps, monument and trim through the existing renewal; walls one at a time through the existing wall swap.
- **Existing buildings stay in the old style.** Each gets a "Reformar no estilo…" button in the planner (10 × building level + 5 × city level coins, same rules as an upgrade, refunded if cancelled before it starts). Castle and wonders stay as they are.
- **Immediate block removal:** the old plaza's four corner planters are removed at once, only where the block is still the template's.

## UI changes
- Founding screen shows 3 cards at a time with arrows, mouse wheel, ←/→ and position dots.
- The planner's building catalog uses ◀ name ▶ instead of three style buttons.

## How to test
1. `/mahou admin login <senha>`, use the Founding Charter, page with ▶ and found one city per new style.
2. `/mahou city finish`, queue a few houses plus temple/tavern/library/arch, `/mahou city finish` again.
3. `/mahou city tier 3`, then `4`, `5`, each followed by `/mahou city renew now` — compare streets, lamps, monument, fountains.
4. Walls: at rank 2 queue a wall and `/mahou city finish`; then `/mahou city tier 4` and `/mahou city finish` when "Reforma da muralha" appears. Check the torii gate (Oriental) and tower spires (Gothic).
5. Reforma, quick path: in a Kingdom city run `/mahou city style gothic`, then `/mahou city finish` and `/mahou city renew now`.
6. Reforma, real path: Porte → pick a style → click twice; the treasury must drop. Then select an old building in the planner → "Reformar no estilo…".
7. `/mahou city style` prints the current style, Reforma cost and every style's bonus line.
8. `/mahou city happy` should show the "Jeito da cidade (estilo)" line for Oriental/Gothic.

## Not done / not verified
- No in-game run: roof shapes, torii, spires, founding carousel layout and the Reforma flow on real terrain are unverified.
- No fourth-style extras beyond Dwarven itself, and no unique building per new style (no castle/wonder equivalent).
- NPC and champion textures are hue-shifted copies (Oriental/Elven/Gothic from Kingdom, Dwarven from Nordic), not hand-drawn; visible gear is a subset of the source style's.
- Small procedural pieces (arcane buildings, arena, port, trap field) mostly keep a base-style stone palette; Gothic and Dwarven reuse the Nordic deepslate set there.
- Old-style plaza blocks at positions the new plaza template leaves empty remain after a Reforma.
- The Nordic founding text still doesn't mention its existing +50% fuel use.
- The founding terrain preview has no per-style variant (style is chosen after it).

## PEDIDOS
- `feature/city_arcana/CityArcana.wardColor`: the new styles fall to the default blue; optionally add red/green/purple/orange cases.
- `docs/ARCHITECTURE.md`: add the four style ids, `CityReform`, project kind `restyle`, hint `city_reform` and `/mahou city style`.
- If `tools/gen/settlements.py`, `settlements_lang.py` or `settlements_v14_lang.py` are re-run, run `py -I tools/gen/settlements_styles.py` and `py -I tools/gen/settlements_styles_lang.py` afterwards: `settlements.py` wipes every `.nbt` in the folder and rewrites the lang files.