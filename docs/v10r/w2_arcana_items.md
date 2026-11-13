REPORT — job `arcana_items` (wave 2)

All six deliverables are built and compile: `check-module.sh arcana_items` and `check-module.sh space_magic` end with TUDO OK, and `check-tree.sh arcana_items` ends with COMPILOU OK. Nothing was run in game.

**Files / dirs touched**
- `src/main/java/com/renanpontes/isekaimahou/feature/arcana_items/**` (new code: content, events, commands, net, hooks, `RecipeScrolls`, `ReturnTravel`, `item/*`, `client/InscribeScreen`)
- `src/main/java/com/renanpontes/isekaimahou/feature/space_magic/`: new `RecoverBelongings.java`, `BossLootBox.java`; small edits to `SpaceGlyphs`, `SpaceMagicModule`, `SpaceCommands`
- `src/main/lang/arcana_items/{pt_br,en_us}.json`; `src/main/lang/space_magic/{pt_br,en_us}.json` (keys added)
- `src/main/merge/arcana_items/data/curios/tags/item/necklace.json`
- `tools/gen/arcana_items.py` (7 item textures + models); `tools/gen/space_magic_icons.py` (new icon `recover_belongings`)
- Data under `src/main/resources/data/isekai_mahou/`:
  - `recipe/arcana_items_*.json` (6) and `recipe/space_magic_recover_belongings_scroll.json`, each with a matching `advancement/recipes/` file
  - `tags/item/arcana_items_reagents.json`
  - `guild_shop/arcana_items.json`

**New ids**
- Items: `mana_necklace`, `greater_mana_necklace`, `grand_mana_necklace`, `blank_recipe_scroll`, `recipe_scroll`, `alchemist_bag`, `return_scroll`
- Glyph: `recover_belongings` (Arcane, scroll-only, Circle 7, base cost 200, 10 min cooldown)
- Data components: `arcana_items_stored_mana`, `arcana_items_author`, `arcana_items_bag_collect`
- Attachment: `arcana_items_player` (last city, survives death)
- Packet: `arcana_items_inscribe`
- Item tag: `isekai_mahou:arcana_items_reagents`
- New optional keys in the existing `space_magic_player` data: `death`, `recover_until`
- Item-entity persistent tag: `isekai_mahou_boss_loot`
- Hints: `arcana_items.mana_necklace`, `.alchemist_bag`, `.recipe_scroll`, `.return_scroll`

**What each one does**
- **Mana Necklace** (necklace curio, capacity 400 / 1200 / 3000):
  - Worn with a full bar, it stores 50 / 65 / 80% of the regen that would be wasted.
  - It refills the bar before the bar drops, and absorbs `mana_core` Overcharge 1:1.
  - Right-click in hand pours bar mana into it (never below 10% of the bar).
  - Upgrading at the crafting table carries the stored mana over.
  - No HUD was added; the item shows a thin bar and a tooltip.
- **Recipe Scroll**:
  - The blank scroll needs `abyss_shard` + `shadow_fragment` + arcane essences + mana crystals + paper.
  - Right-click lists your Grimoire library; inscribing costs 8 + 2 per glyph XP levels (max 30) and 60% of max mana.
  - The author must know every glyph.
  - The reader must know every glyph too; it is consumed on read and refuses duplicates.
- **Alchemist's Bag**: 27 slots, only items in the reagents tag or `essence_*`, only Magic-category classes. Sneak + right-click toggles auto-collect of picked-up reagents. It cannot go inside a shulker or the Item Box.
- **Scroll of Return**: hold 3 s, out of combat for 10 s, to go to the last city entered. If nothing is saved yet (old worlds), it uses the nearest known city. Blocked in boss arenas, `trial_realm`, `endless_abyss` and while a spirit.
- **Recover Belongings**: pulls the latest afterlife Corpse and/or the loose drops of the last death from any distance or dimension. Mana is refunded if nothing is found.
- **Boss loot to Dimensional Inventory**: boss drops are tagged. With a full inventory and the `dimensional_inventory` glyph known, they go to the Item Box. Owner-targeted reward overflow does the same for 15 s after a boss dies within 48 blocks.

**How to test** (after `/mahou admin login`)
1. `/mahou arcana_items give` gives all items plus essences and crystals.
2. Necklace: equip it in the necklace slot, wait with a full bar, cast, and watch the bar stay full while the item bar drains. `/mahou arcana_items fill` fills the one in your main hand.
3. Recipe scroll: save a spell to the Grimoire library, right-click the blank scroll and pick it. `/mahou arcana_items scroll 0` gives a written one for free. Read it on a second player, or delete the library entry first.
4. Bag: `/mahou path witch` opens it, `/mahou path knight` is refused. Drop an essence and walk over it to see auto-collect.
5. Return: enter a city, walk away, hold right-click on the scroll. `/mahou arcana_items city` shows the saved city and `/mahou arcana_items return` teleports without a scroll.
6. Recover: `/mahou space_magic learn`, die with items, respawn, then `/mahou space_magic recover` (no cost, no cooldown). For the real cast use `/mahou circle 7`. Craft test: 4 `abyss_shard` + 2 echo shards + 2 arcane essences + totem.
7. Boss loot: learn the glyphs, fill your inventory, `/mahou space_magic bossloot`, walk over the diamonds, then `/mahou space_magic box`.

**Not done / not verified**
- Not verified in game at all: curio ticking, chunk-ticket timing for Recover in a far unloaded chunk, the bag's slot lock, the inscribe screen layout.
- When the Scroll of Return is refused, the client may briefly show the use animation.
- No curio renderer: the necklace is not drawn on the body.
- No journal chapter and no chest-loot entries; sources are recipes plus the guild shop (return scroll, bag, tier-1 necklace).
- `check-assets` still prints a few false-positive "missing translation" warnings for dynamically built keys and vanilla `gui.cancel`.

**PEDIDOS**
1. **core / ui_grimoire, important**: the old cheap path still exists. The Grimoire's "write scroll" button (`GrimoireTab.writeScroll` → `CoreGameplay.libraryEdit` op 2) makes a reusable `spell_scroll` for 1 paper, with no glyph check on the reader. That contradicts his "dificulta fazer os pergaminhos". Please remove or disable that button, or make op 2 require and consume `isekai_mahou:blank_recipe_scroll` and call the same cost.
2. **cities / settlements shops**: sell `isekai_mahou:return_scroll` cheaply at the inn or general store, and optionally `alchemist_bag` at the alchemist.
3. **loot (abyss / towers / end_library)**: optionally add `blank_recipe_scroll` and a `glyph_scroll` with `isekai_mahou:recover_belongings` as rare rewards.
4. **docs**: add the new ids to the `ARCHITECTURE.md` catalog.