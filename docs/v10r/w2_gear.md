**Job "gear" report**

All four deliverables are implemented and both checks pass (`bash tools/check-module.sh gear_plus` → TUDO OK; `bash tools/check-tree.sh gear` → COMPILOU OK). Nothing was run in game, so behaviour is compile-verified only.

**Files / dirs touched** (all new or skeleton fill-in; nothing outside the module)
- `src/main/java/com/renanpontes/isekaimahou/feature/gear_plus/**` — 19 Java files: `GearPlusModule`, `GearRegistry`, `GearData`, `GearNet`, `GearText`, `GearCommands`, `SetBonuses`, `GearBonuses`, `KitRecovery`, `GemType`, `Gems`, `GemDrops`, `WeaponBond`, `item/GemItem`, `item/GemChiselItem`, `client/GearPlusClient`, `client/ClientGear`, `client/GearTab`, `client/GearTooltips`
- `src/main/lang/gear_plus/pt_br.json`, `en_us.json`
- `tools/gen/gear_plus.py` (already run), which generated:
  - `assets/isekai_mahou/textures/item/gem_*.png` and `models/item/gem_*.json` (18 gems + chisel)
  - `assets/isekai_mahou/textures/gui/tab/gear_plus.png`
  - `data/isekai_mahou/recipe/gear_plus_*.json` (13 recipes)
- `feature/outfits`, `feature/armory`, `feature/equipment` were left untouched; the module only reads them.

**What each deliverable does**
- **Set bonus:** 4 pieces of the same set give a small attribute bonus, scaled by the lowest tier worn. Covers the 15 `outfits` sets, the 10 class-module armors, mithril/star/adamantite and vanilla sets. Shown in the armor tooltip (n/4) and the tab.
- **Kit recovery (menu B):**
  - Returns only the kit pieces the player is not carrying; a higher-tier piece of the same set/slot or a tier weapon counts as carrying it.
  - Free every 40 min of game time, or 4 Mana Crystals to skip the wait.
  - No duplication: delivered pieces are sealed with owner + delivery number, and older sealed copies crumble when they land in any inventory.
  - Consumables (food, arrows, ingots) do not come back.
- **Gems:** only Smith, Rune Knight and Enchanter can socket, and gems only work while one of those classes holds/wears the item.
  - Weapons have 3 sockets (levels 10/35/60); armor has 1 per piece (level 20). Cut and flawless gems need level 30 / 55.
  - Socket by right-clicking the gem onto the item in the inventory; the chisel takes the last gem out the same way.
  - Sources: ore mining (small chance), bosses (1 cut gem), monsters (rare), and crafting 3 → next cut (flawless also needs `abyss_shard`).
- **Awakening weapons:** Smith, Rogue, Knight, Quincy, Gunslinger, Ranger, Gambler, Steel Ball.
  - Kills with the class weapon in hand raise a per-class Bond 1→10 (bosses count 10), +1.5% damage per level.
  - Level 10 needs 800 kills and player level 50, and unlocks one class ability.
  - The ability has no key or HUD: 12 hits charge it, the next hit releases it, then it rests 15 s. It only affects hostile mobs and never blocks.
  - Progress is stored on the player, so it survives weapon upgrades and kit recovery.

**New ids**
- Items: `gem_chisel`; `gem_<ruby|sapphire|emerald|topaz|amethyst|onyx>` plus `_chipped` and `_flawless` variants
- Attachment `gear_plus_data`; SavedData `isekai_mahou_gear_plus_kits`
- Payloads `gear_plus_sync`, `gear_plus_recover`
- Menu tab `gear_plus` ("Equipamento", Character category)
- Item custom-data keys `isekai_mahou_gems`, `isekai_mahou_kit`
- Hints `gear_plus.set_bonus`, `gear_plus.gem`, `gear_plus.bond`, `gear_plus.kit`

**How to test** (after `/mahou admin login`)
1. `/mahou outfit mage` (or any 4 vanilla iron pieces), then `/mahou gear_plus status` → should print the set and tier; hover a piece for "Conjunto: … (4/4)".
2. As Smith, Knight or Enchanter: `/mahou gear_plus gems`, open the inventory, pick up a gem and right-click a sword or armor piece. Check the tooltip sockets and `status` ("Gemas valendo"). Right-click with the chisel to extract. Try the same as a mage → refused.
3. Throw away the kit weapon/armor, open B → Personagem → Equipamento → "Recuperar kit". Repeat to see the cooldown and the crystal skip. `/mahou gear_plus kit reset` clears the cooldown; `/mahou gear_plus kit recover` forces it.
4. Anti-dupe: put a recovered piece in a chest, recover again, take the old one out → it crumbles within 2 s.
5. As one of the 8 bond classes holding the class weapon: `/mahou gear_plus bond add 100`, then `bond awaken` (the player must be level 50+ for level 10), then `bond charge` and hit a monster → the ability fires. `bond reset` clears it.

**Not done / not verified**
- Creative-inventory socketing and the tab layout at very small GUI scales are untested; the left panel may clip its last line.
- Recovery gives the basic (tier I) kit only, as the contract says "o básico".
- Old-world kit items have no seal, so one extra copy of the original kit is possible there. Mod ritual blocks in the kit (research table, anvil, sapling) come back whenever the player is not carrying one, so one extra block per recovery is possible.
- Shinigami (already has Shikai/Bankai), Enchanter (gets gems), Hollow and the magic classes have no awakening weapon.
- Six class modules (knight, rogue, enchanter, gambler, gunslinger, ranger) already had their own full-set effect; mine adds a light attribute on top rather than replacing it.
- No journal chapter and no loot-table or trade entries for gems; drops are code-driven.

**PEDIDOS**
- None required. Optional: a city or market vendor selling `gem_chisel` and chipped gems (settlements/market owners).