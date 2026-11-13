# talents rework (2026-11-13)

User's words: "Talents e especializations estão ruim... melhore! A ideia é boa, mas termino lvl 150 com 6 pontos a mais por exemplo".

Agent report (`check-module.sh talents` passes; never run in game; migration/reachability checked in a Python mirror of the Java rules).

## Structure
- Level cap 150 (200 after the Primordial Dragon); 1 point per 2 levels → 75 points at 150, 100 at 200.
- Tree: 3 branches × 7 tiers + a Mastery node per branch. Per branch: 6 small nodes (5 ranks), 2 notables (2 ranks), 1 either/or choice (1 rank), 1 capstone (1 rank) = 36 ranks.
- Budget: 108 class ranks + 9 in the specialization row = 117 buyable. 75 points = 64% at level 150; 100 points = 85% at 200. Three capstones cost 87 points, so at 150 only two are reachable.
- Gates: tier level 1/6/14/26/36/50/70 and points spent above in the same branch 0/3/6/9/15/20/28.
- Sink: Mastery node per branch, 50 ranks, each rank 0.95× the previous; opens at level 20 with 5 points in the branch (about +7.4% damage, +9 HP or +74 mana at 50 ranks).
- Specializations (32 ids unchanged): signature granted on pick, a "how it plays" line, exclusive 4-node row (3+3+2+1 ranks, levels 40/40/50/60).

## Caps
Dodge 25%; damage reductions summed 50%; lifesteal 6 HP/s; additive damage +100% (combo share +40%); spell-only +60% (separate multiplier, outside the ×4 final cap); final hit ×4; crit chance 50%, crit damage +100%, lucky hit 15%; echo and cast refund 30% each; instant kill threshold 12% health (never bosses or players); kill blast 60, guard reflect 20, thorns 8; cooldown advance 2 s per crit, 5 s per kill; ultimate charge 5 per kill; barrier 20 absorption; death refusal once per 5 min.

## Old saves
All 15 old node ids per class still exist with the same or higher max; a level-150 player keeps 69 ranks and has 6 free points to spend. Anything the tree no longer accepts is refunded once with one chat line. Attachment and NBT keys unchanged; `Talents.reset`, `ClassTalents` keep their signatures.

## Files
`feature/talents/**` (Talents, TalentTree, TalentData, TalentStats, TalentCombat, TalentText, TalentsCommands, TalentsModule, client/TalentsTab, client/ClientTalents), `tools/gen/talents.py` (rewritten, re-runnable; `--totals` prints maxima per class), generated `data/isekai_mahou/talents/*.json` (16) and `src/main/lang/talents/*.json` (991 keys).

## Test
`/mahou talents validate`, `/mahou talents budget`, `/mahou talents check`, `/mahou talents points <n>`, `max`, `subclass <id|none>`, `reset`; then B → Personagem → Talentos.

## Not done / not verified
- Never run in game: layout, feel, balance.
- No real transformation or summon-count synergy (no generic core API): "transform" talents trigger on casting the ultimate (G); summon talents go through the owner of the attacking entity.
- Plain gun bullets, steel balls, thrown cards and knives are not spell damage: echo and spell-damage bonuses skip them.
- Parry/block talents depend on the core guard events firing for that class's weapon.

## PEDIDOS
- Core: a generic "player is transformed" query and "living summons of player" count.
- Docs: `docs/ARCHITECTURE.md` with the layout-2 JSON fields and new passive keys.
