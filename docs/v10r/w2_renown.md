REPORT — job "renown" (wave 2)

All three deliverables (ranking, titles, boss time records) are implemented and compile; nothing was run in game. `bash tools/check-module.sh renown` ends with "TUDO OK (renown)" and `bash tools/check-tree.sh renown` ends with "COMPILOU OK (all)".

**Files/dirs touched (all new, nothing outside my area edited)**
- `c:/Users/renan/Music/ROCKET/RocketSimVis/Minecraft_Mod/src/main/java/com/renanpontes/isekaimahou/feature/renown/`
  - `RenownModule`, `RenownContent`, `RenownData`, `RenownNetwork`, `RenownRanking`, `RenownTitles`, `BossTimer`, `RenownChat`, `BoardPlacer`, `RenownCommands`
  - `block/RenownBoardBlock`
  - `client/RenownClient`, `client/RenownTab`, `client/RenownClientState`, `client/TitleTags`, `client/FightTimerHud`
- `src/main/lang/renown/pt_br.json`, `en_us.json`
- `tools/gen/renown.py`, which generated:
  - textures `block/renown_board_front.png`, `block/renown_board_side.png`, `gui/tab/renown.png`
  - `blockstates/renown_board.json`, `models/block/renown_board.json`, `models/item/renown_board.json`
  - `data/isekai_mahou/loot_table/blocks/renown_board.json`, `data/isekai_mahou/recipe/renown_board.json`
  - `src/main/merge/renown/data/minecraft/tags/block/mineable/axe.json`

**New ids**
- Block and item: `isekai_mahou:renown_board` (shaped recipe: paper, gold ingot, planks, sticks; creative tab World).
- Menu B tab: `renown` (Social category), with three internal sections: Ranking / Títulos / Recordes.
- SavedData file: `isekai_mahou_renown`.
- Payloads: `renown_request`, `renown_board`, `renown_titles`, `renown_title_sync`, `renown_timer`.
- GUI layer: `renown_fight_timer`.
- Counter: `renown.world_records`.
- Hints: `renown.title`, `renown.boss_time`.
- 39 feat titles with keys `renown.isekai_mahou.title.<id>` (+ `.desc`). They are stored in the existing core title pool (`Reputation.grantTitle` / `rep.title:` flags), so a player has one worn title across reputation and feats.

**What it does**
- **Ranking:** six categories, top 10 each plus your own position.
  - Categories: highest level, bosses killed, Abyss floor (from `AbyssRecords.top`), towers conquered, richest city (treasury), most populous city.
  - Offline players stay listed through a per-player snapshot saved in `RenownData`.
  - The board block opens the tab on Ranking.
  - Board placement in NPC cities: the first time someone uses a `quest_board` or `faction_board` there, a board appears in the air block beside it. It never replaces a block, and happens once per city.
- **Titles:** unlocked by counters, level/Circle, the player's own city, and boss records.
  - Chosen in B → Renome → Títulos (also lists reputation titles).
  - Shown as a smaller line above other players' names, within 24 blocks, hidden when sneaking.
  - Shown in chat as `<[Título] Nome>`. The title is added to the display name only for 6 ticks around a chat message, so it does not leak into saved names.
  - Several titles earned at once produce a single chat line.
- **Boss timer:** starts on `SocialEvent.ArenaSealed` (or first player hit for bosses without an arena).
  - A small clock sits beside the boss bar and shows your personal best.
  - On death it saves a personal best and a server best per boss type, with one chat line per fighter.
  - A server-wide line is sent only when an existing record is beaten.
  - A time counts only if players dealt at least 60% of the boss's max health in that fight and nobody was in creative.
  - The `endless_abyss` dimension is excluded.

**How to test (after `/mahou admin login`)**
1. `/give @s isekai_mahou:renown_board`, place it, right-click → the Renome tab opens on Ranking. `/mahou renown board` does the same.
2. `/mahou renown fake 20` fills the ranking with test players; `/mahou renown unfake` removes them. `/mahou renown status` prints the data.
3. Titles:
   - `/mahou renown grant all`, or `grant founder` for one → B → Renome → Títulos, click to wear or remove.
   - `/mahou level 50` then `/mahou renown check` exercises the real unlock path.
   - `/mahou renown revoke all` removes them.
   - Type in chat to see `[Título]`; a second player is needed to see the line above the name.
4. Timer HUD: `/mahou renown timer on` and `timer off`.
5. Records: `/mahou renown record isekai_mahou:ignis 150` writes a time (see the Recordes section); `/mahou renown clearrecords` wipes them. For the real flow, summon any boss in survival and kill it; kill it again faster for the "novo recorde pessoal" line.
6. Auto-board: in an NPC city, right-click a quest or faction board → a Renown Board appears next to it if there is room.

**Not done / not verified**
- Unverified visually: title line height above the name, timer position next to the ui_hud boss bar, tab layout at small GUI scales, and the board texture/model in-world.
- The chat title relies on the 6-tick display-name window; it was not tested on a lagging dedicated server.
- Your own title is not drawn above your own head in third person (vanilla hides your own name tag).
- Multi-entity or phase-swap bosses are timed per entity type. Group and solo kills share one record per boss.
- Auto-placement only covers NPC cities that contain a quest or faction board. Standalone guild halls outside a `Places` city get no automatic board.
- No journal chapter and no title in the tab list.
- City titles and rankings read `Settlement.treasury`, `people`, `radius`, `festivals`, `caravansSent`, `raidsWon` directly. The 15×15 title assumes `radius` 7.

**PEDIDOS**
- guild / cities_npc / structures: add `isekai_mahou:renown_board` (same shape and FACING as `quest_board`) to the guild hall and city plaza templates, so the board exists without relying on the runtime auto-placement.
- ui (optional): a `MahouConfig` toggle for the boss fight timer and for titles above names; both are always on today.
- ui_hud (optional): reserve the spot to the right of the first boss bar for `renown_fight_timer` if anything else gets drawn there.