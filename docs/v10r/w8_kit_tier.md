# kit_tier (2026-11-13) — recovery kit at the player's tier

User's words: "o recobery kit tem que ser do lvl que estiver, se meus itens forem tier alto, serão de tier alto".

Agent report (compiles: `check-module.sh gear_plus` TUDO OK, `check-tree.sh kit_tier` COMPILOU OK; nothing run in game).

## What "tier" is per slot
A tier is always a different item, never a number stored inside the item.
- Armor slots: a piece of the same set family in the same slot, grades I–IV.
- Main class weapon: the kit weapon (tier 0) or `<weaponPrefix>_t1..t4`. Magic classes have no kit weapon, so their weapon slot only appears once they have carried their tiered focus.
- Kit tools: the same item; the Smith's Forged Blade carries quality and parts in a component.
- Blacksmith reinforcement is Unbreaking I–III, so it travels with the enchantments. Bond level is stored per player, untouched.

## Recorded and restored
- Every 2 s the server stores, per class and slot, item id, rank, tie-break score and a whitelist of components, in the `gear_plus_data` attachment under the optional key `kit_best` (survives death, relog, restart). The record only improves.
- Restored: enchantments, custom name, Forged Blade parts, Knight engraved runes, dwarf rune, Infernal Forge infusion.
- Not restored: gems (the chisel removes them intact — restoring would duplicate them), durability, ammo, souls.
- A slot is "missing" when the player carries nothing of at least the recorded rank.
- The seal now covers every tier; a lost high-tier original dissolves like a basic one, and its socketed gems drop back to the holder first.
- Unchanged: 40-minute cooldown, 4 Mana Crystals skip.

## Files (all in `feature/gear_plus`)
`KitBest.java` (new), `KitRecovery`, `GearData`, `SetBonuses`, `GearNet`, `GearCommands`, `client/ClientGear`, `client/GearTab`; lang `src/main/lang/gear_plus/*.json` edited by hand.

## Test
1. Knight: `/give @s isekai_mahou:runic_blade_t3`, enchant it, wear a higher-grade suit piece.
2. `/mahou gear_plus kit best` prints each slot and what would come back.
3. Drop the blade; B → Equipamento shows the t3 outlined in red; `/mahou gear_plus kit recover`.
4. Pick up the dropped original: it dissolves within 2 s, gems return.
5. `/mahou gear_plus kit forget` (clear records), `/mahou gear_plus kit reset` (clear cooldown).

## Not verified
No in-game run. Pieces in shulkers, ender chest or Curios are not scanned. Records are made in creative too.

## Coordinator follow-ups (done)
- Guide line `guide.isekai_mahou.gear.how` updated in `story` lang and in `tools/gen/journal_story.py`.
- `core/api/Rewards.upgradeInPlace` now carries the item's custom data (socketed gems, kit seal, runes) to the next-tier weapon; before, tier-up dropped the gems.
