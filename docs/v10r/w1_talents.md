Report (job `talents`): all deliverables are built and both checks pass (`check-module.sh talents` → TUDO OK, `check-tree.sh talents` → COMPILOU OK). Nothing was tested in game, since gradle is off-limits.

**Files/dirs touched**
- `src/main/java/com/renanpontes/isekaimahou/feature/talents/**` (13 files): `TalentsModule`, `Talents` (API), `TalentData`, `TalentTree`, `TalentTrees`, `TalentStats`, `TalentText`, `TalentCombat`, `TalentsNetwork`, `TalentsCommands`, `client/TalentsClient`, `client/ClientTalents`, `client/TalentsTab`.
- `src/main/java/com/renanpontes/isekaimahou/core/classes/ClassTalents.java` (new core facade; no existing core file edited).
- `tools/gen/talents.py` (generator; rerun with `py -I tools/gen/talents.py`).
- Generated: `src/main/resources/data/isekai_mahou/talents/<class>.json` (16 files), `src/main/lang/talents/{pt_br,en_us}.json` (420 keys), `src/main/resources/assets/isekai_mahou/textures/gui/tab/talents.png`.

**New ids**
- Attachment `isekai_mahou:talents` (copied on death, stored per class id, not in MagicData).
- Payloads `talents_sync`, `talents_spend`, `talents_pick`.
- Permanent attribute modifier ids `talents_add`, `talents_mult_base`, `talents_mult_total`.
- Menu tab `talents` (Character category, appears at level 2).
- Datapack folder `data/<ns>/talents/<class>.json`.
- Subclass ids `<class>_<name>`, e.g. `mage_battlemage`, `rogue_assassin`, `knight_paladin`; `/mahou talents list` prints them.

**What it does**
- **Tree:** 16 trees (the 15 playable classes plus Hollow), each 3 branches × 5 tiers, 69 ranks total.
- **Points:** 1 per 2 levels, derived from level, so old saves get theirs automatically. Each node needs a minimum level (1/10/25/45/70) and 3 ranks in the node above.
- **Nodes:** small passives, about 1–2% per rank, either attribute modifiers or combat passives (crit, lifesteal, dodge, resistances, on-hit effects, summon damage and so on). Dodge is capped at 25%, total resistance at 50%, lifesteal at 6 HP/s. Nothing grants XP or touches blocks.
- **Subclasses:** 32 (2 per class), 3–4 passives each, chosen in the same tab at Circle 5 with a two-click confirm. Permanent until reset.
- **Reset:** `Talents.reset(ServerPlayer)` refunds the current class's points and clears its subclass; `reset(sp, false)` keeps the subclass, and a `reset(Player)` overload exists for reflection. The same is reachable without reflection through `core.classes.ClassTalents` (`reset`, `subclass`, `hasSubclass`, `stat`, `unspent`).
- **Class change:** the new class gets a fresh tree; the old class's allocation is kept if the player switches back.

**How to test in game** (after `/mahou admin login`)
1. `/mahou talents info` and `/mahou talents list` show points, nodes and subclass ids.
2. `/mahou talents points 30`, then B → Personagem → Talentos: click nodes (Shift = up to 5), check tooltips and the "Bônus ativos" panel.
3. `/mahou talents max` fills the tree; `/mahou talents set <node> <rank>` sets one node; `/mahou talents info` lists summed passives.
4. Subclass: reach Circle 5, open the Especialização view, click Escolher then Confirmar. To force it: `/mahou talents subclass <id|none>`.
5. `/mahou talents reset` does what the Altar do Recomeço will do.
6. Relog and die/respawn to confirm bonuses (e.g. max health) persist.
7. `/reload` should re-sync trees.

**Not done / not verified**
- Never run in game: tab layout, attribute persistence across relog/respawn, and the feel of each combat passive are unverified. Outside the game I only ran javac, JSON validation, a check that every icon item and attribute id exists, and a parser + NBT round-trip test on all 16 trees.
- Subclasses are passive-only (as the contract asked); no class module reacts to them yet.
- No journal chapter and no first-time hint, because the `ui` agent's `Hints` API is from the same wave.
- Balance numbers are a first pass.

**PEDIDOS**
- `character` (wave 2, Altar do Recomeço): call `ClassTalents.reset(serverPlayer)` or, by reflection, `com.renanpontes.isekaimahou.feature.talents.Talents.reset(ServerPlayer)`. The tab text names the altar "Altar do Recomeço" / "Altar of New Beginnings"; keep that name or tell me to change the two lang lines.
- `ui`: optionally show the subclass in the Status window via `ClassTalents.subclass(player)` + lang key `subclass.isekai_mahou.<id>`, and add a first-point hint.
- Class modules: can add subclass-specific flavor with `ClassTalents.hasSubclass(player, "<id>")`.
- Coordinator: `Talents.addBonusPoints(sp, n)` exists for rare rewards; nothing uses it yet.