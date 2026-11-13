# Isekai Mahou v1.5 — "Torres Arcanas"

NeoForge 1.21.1 mod (Java 21, Mojang mappings + Parchment, GeckoLib, Curios). The player is Renan (PT-BR). His request, in his words (translated):

> 1. Magical buildings in the city: protect the city from spells, put nullification on the city, etc.
> 2. Magical buildings such as Pedestals: put a spell in one and it casts in an area around it. Not damage spells, but mining spells ("indo descendo": dig down layer by layer), planting, harvesting, etc. Add triggers like "on max grow" and "on empty", and a way to choose the seed type. We must be able to build mining farms and other farms with this, using magic: put it in a glyph and apply it to the pedestal.
> 3. Maybe a mana CORE nearby, plus an item that conducts mana to it, "wireless".
>    - I put my mana in the core. Towers run while the core has mana to spend.
>    - I can switch each tower on and off.
>    - I keep filling the core. If the towers use the spell, it spends from the core. If not, mana accumulates.
>    - I can take it back and end up with mana ABOVE my limit. But for every 300 mana above the limit I get a "Mana Intoxication" effect that deals damage until I'm at 1 HP.

## What already exists (READ it before designing — `feature/automation/**`)
- **Runic Pedestal** (`block/RunicPedestalBlockEntity`, `RunicPedestalMenu`, `client/RunicPedestalScreen`):
  - holds a Spell Scroll and auto-casts it every N seconds through a FakePlayer of the owner (`Pedestals`, `PedestalCaster`);
  - has a direction and an offset;
  - redstone modes;
  - pays from **Mana Batteries** within 8 blocks (`ManaNet`).
- Other blocks:
  - **Mana Battery**: sneak-click deposits ALL your mana; there is no way to withdraw.
  - **Mana Condenser**.
  - **Arcane Collector**: item buffer.
- Existing pedestal glyphs:
  - `harvest_replant`, `till`, `sow` (seeds from nearby inventories), `collect_items`, `smelt_items`, `shear`, `breed`, `call_rain`, `clear_sky`, `call_thunder`;
  - helpers in `glyph/AutoArea`, `AreaEffect`.
- Glyph system: `glyph/` — `Glyph`, `GlyphKind`, `TriggerGlyph`, `EffectGlyph`, `AugmentGlyph`, `GlyphRegistry`, `SpellParser`, `ParsedSpell`, `SpellCaster`, `SpellContext`.

## Coordinator-made seams (already in the tree)
- `feature/automation/PedestalCondition` (interface) — a placeable `TriggerGlyph` at the START of a recipe that also implements it is checked by the pedestal BEFORE casting, with no mana spent.
  - **auto_glyphs** implements the triggers.
  - **mana_core** adds the call inside the pedestal and a status "Aguardando condição" (waiting for condition).
- `Pedestals.currentFilter()` and `RunicPedestalBlockEntity.filter()` (stub returns EMPTY):
  - **mana_core** adds a real FILTER slot to the pedestal and implements `filter()`;
  - **auto_glyphs** reads `Pedestals.currentFilter()`: Sow uses it as the seed type, Dig uses it as "keep only / skip" etc.
- Empty, registered modules:
  - `feature/mana_core`, `feature/auto_glyphs`, `feature/city_arcana`, each with a `client/` class;
  - lang folders `src/main/lang/<module>/{pt_br,en_us}.json`.

## Shared rules (ALL agents)
- **Text.** Player-facing text is PT-BR. Write both `pt_br.json` and `en_us.json` in your lang folder. Code comments are in PT-BR, matching the existing style.
- **Never run gradle.** Other agents edit the tree in parallel.
  - Compile with `bash tools/check-module.sh <module>`.
  - Run `bash tools/check-tree.sh <tag>` if you touch files outside your module.
  - Your own files must compile when you finish.
- **Ownership.**
  - Edit only what you own (table below).
  - Use other code read-only through its public API.
  - If you need a change elsewhere, list it under "PEDIDOS" in your report.
- **Saved worlds.**
  - Never rename or remove registry, glyph or NBT ids. Old worlds must load.
  - New NBT fields are optional, with defaults.
- **Performance.**
  - No per-tick big `getEntitiesOfClass` or block scans. Throttle to every 5–20 ticks, spread work over ticks, cap blocks per operation.
  - Only touch loaded chunks (`getChunkNow`). Never load chunks.
  - Client FX go through `client/fx/FxQuality`.
- **Balance.** Levelling to 100 must stay hard; absurd power only at 100. Gate strong things behind Circles/ranks.
  - Overcharge (mana above max) must be risky, not free power.
  - Farms must cost mana per cast proportional to the work.
- **Obtainable in survival.** Every block and item has a recipe (or another real source) and a creative-tab entry. Every glyph has an unlock path, following how automation glyphs are learned today.
  - Blocks need models and textures. Generate them with your own `tools/gen/<module>_*.py`, in the existing pixel-art style. **Never** run the full `tools/gen/icons.py`.
  - Glyphs need grimoire icons (see `feature/ui_grimoire/client/GlyphIcons`).
- **Debug.** Give the coordinator op test commands under `/mahou <module> ...`, e.g. give items, fill a core, set overcharge.
- **Final report**, in English and short:
  - files touched;
  - new ids (blocks, items, glyphs, effects), one line each;
  - how to test in game (commands);
  - known gaps;
  - PEDIDOS.

## Ownership
| Job | Owns (may edit) | Deliverable |
|---|---|---|
| **mana_core** | `feature/mana_core/**`; `feature/automation/**` EXCEPT `glyph/**` (pedestal, menu, screen, ManaNet, Pedestals, battery…); `core/MagicData.java` + its sync/HUD ONLY to add the overcharge pool; `feature/ui_hud/**` ONLY for drawing the overcharge on the mana bar | Núcleo de Mana block, Condutor item (wireless link), pedestal on/off + filter slot + PedestalCondition hook, withdraw → overcharge, Intoxicação por Mana effect |
| **auto_glyphs** | `feature/auto_glyphs/**`; `feature/automation/glyph/**` (may improve existing farm glyphs, e.g. Sow using the filter) | Dig-down (quarry) glyph, Lumberjack glyph, more farm/utility effects, pedestal triggers (Ao Amadurecer, Ao Esvaziar, …) |
| **city_arcana** | `feature/city_arcana/**`; in `feature/settlements/`: `build/Blueprints.java`, `build/ProcBuildings.java`, `CityEconomy.java` (fx table only), `tools/gen/settlements_v14_lang.py` (add keys, then run it) | Arcane city buildings: Arcane Barrier (ward), Nullification Obelisk (+ governor setting), and more |

The coordinator owns `feature/settlements/build/CityLayout.java`, `CityPlanning.java`, `CityViewExtra.java`, `client/SettlementPanel.java`, `Settlement.java` and `SettlementService.java` (just reworked for city expansion and multiple walls). Do NOT edit them; ask in PEDIDOS.
