**story — report**

All four deliverables plus the six notes are built and compile; nothing was run in game. `bash tools/check-module.sh journal` → TUDO OK, `bash tools/check-module.sh cinematic` → TUDO OK, `bash tools/check-tree.sh story` → COMPILOU OK (3052 files).

The earlier agent left nothing in the repo for this job — only an entity dump in the shared scratchpad (`ents.py`, `ents.json`), which I reused for the Bestiary. Everything below is from this run.

**What was built**
- **Campaign (optional, skippable):** 33 short beats from `docs/LORE.md` §14, fired at milestones (chapter opened/closed, step done, Circle broken, boss killed).
  - Beats use the core dialogue box (never locks, Backspace skips); skipping one part closes the rest of that beat.
  - Beats wait for the tutorial, other dialogue and combat, with at least 15 s between them; more than 3 queued go straight to the Journal.
  - Nothing fires retroactively on old characters.
  - Hidden Truths from the Encyclopedia are not spoiled; the Echo only hints and points at Lost Pages.
- **Milestone cutscenes:** 8 of the beats open with a 6.5 s cutscene (new kind `story`: slow orbit, no shake, controls never locked, Enter skips).
  - Boss beats wait 180+ ticks server-side, and the client holds a `story` scene behind any boss or locked scene, so it never fights the 2 s kill cam.
  - Long subtitles now wrap in the cutscene overlay.
- **Journal tab:** chapters are grouped under Act headers (hover shows the act's question); each chapter's chronicle keeps its "Ecos" for re-reading; a small ✦ button next to "Chamar Espírito Guia" toggles the campaign.
- **Options in the Minecraft menu:** `storyCampaign` and `storyMilestoneCutscenes`. With the campaign off, beats are only stored in the Journal.
- **"Onde?" section in the Travel Guide:** 30 topics saying where and how, covering every system in note 4 plus the player city (Charter preview/pin/confirm, builders, Felicidade, 2-day festival, caravans, defense).
  - Its first row, "Seu próximo passo", shows the current story and class steps with their hints.
  - The section appears once chapter 1 is done.
- **"No caminho" lines:** 9 story steps get an extra line about what that milestone unlocks (glyph mastery, lock/guard/parry, city services, Familiar, summons at Circle 3, Transformation and Vows at Circle 5, Endless Abyss at 100).
- **Bestiary:** 55 ids added to the catalog with categories as requested in note 3; 31 missing descriptions written.
- **Note 2:** no "apoio/afinidade" text for player cities was left in journal or guide lang; the new city topics use "Felicidade".

**Files touched** (all under `c:/Users/renan/Music/ROCKET/RocketSimVis/Minecraft_Mod/`)
- New in `src/main/java/com/renanpontes/isekaimahou/feature/journal/`: `story/StoryBook.java`, `story/StoryService.java`, `story/StoryConfig.java`, `story/StoryCommands.java`, `guide/GuideTopics.java`, `client/StoryClient.java`, `client/WhereTab.java`
- Edited in the same folder: `JournalModule.java`, `JournalNetwork.java`, `JournalService.java`, `bestiary/Bestiary.java`, `client/JournalClient.java`, `client/JournalTab.java`
- Edited in `src/main/java/com/renanpontes/isekaimahou/feature/cinematic/`: `CinematicModule.java`, `client/CutsceneShot.java`, `client/CutsceneDirector.java`, `client/CutsceneOverlay.java`, `client/CinematicClient.java`
- New: `tools/gen/journal_story.py` (safe to re-run; fails if a beat, topic or catalog creature has no text), `src/main/lang/story/pt_br.json` and `en_us.json` (279 keys each), `src/main/resources/assets/isekai_mahou/textures/gui/tab/where.png`
- Text lives in `src/main/lang/story/` because `tools/gen/journal.py` rewrites `src/main/lang/journal/` whole.

**New ids**
- Payload `journal_story_prefs` (C→S); journal registrar bumped `journal-3` → `journal-4`
- Config file `isekai_mahou-story-client.toml`
- Menu section `where`; cutscene kind `story`; hint `journal_where`
- Flags `story.<beat>` and `story.q.<beat>` (new, optional)
- Commands `/mahou story status|list|play <beat>|reset`
- Lang prefixes `story.isekai_mahou.*`, `guide.isekai_mahou.*`, `journal.isekai_mahou.<chapter>.<step>.extra`
- No existing id, flag or NBT key renamed.

**How to test** (after `/mahou admin login`)
1. `/mahou story list`, then `/mahou story play a1_call` (cutscene, then the Echo speaks). Press Enter during the scene and hold Backspace during the talk to check both skips.
2. `/mahou story play a6_called` for a three-speaker beat; `/mahou story play a2_watcher` for a talk-only one.
3. `/mahou cinematic cutscene story` for the bare scene.
4. `/mahou spawnboss ignis`, then `/mahou bosspolish kill` and immediately `/mahou story play a4_plane`: the story scene should start only after the kill cam.
5. `/mahou journal complete` a few times: act headers appear in B → Guia de Viagem → Missões, and "Ecos da campanha" shows in the chapter chronicle; queued beats play about 15 s apart.
6. Click the ✦ button in the Journal (or Esc → Options → Isekai Mahou → "Campanha e história"), then `/mahou story status` → campaign off; complete another chapter: no talk, but the echo is in the Journal.
7. B → Guia de Viagem → "Onde?": check "Seu próximo passo" and a few topics; click a step such as "Rompa o Quinto Círculo" in the Journal to see the "No caminho:" line.
8. Bestiary: `/summon isekai_mahou:land_drake` and right-click it; check ALLY for Familiar, city folk and dwarves, BOSS for the Espada and Star Herald.
9. `/mahou story reset` clears campaign markers.

**Not done / not verified**
- Nothing seen in a client: cutscene framing, wrapped-subtitle layout at small GUI scales, the Act header tooltip, the Where tab layout and the tab icon in the side column.
- The `x_city` beat fires only on `settlements.lord` today; there is no counter for founding a city (see PEDIDOS).
- If a story scene is held behind a boss scene on the client, its follow-up talk can start before the scene, because the server only knows its own delay.
- Bestiary: I skipped `shrine_champion`, `trial_elite` and `steelball_steed` (their names contain `%s` and would show raw in the list), and non-creature entities. Catalog entries only show if the entity is living with attributes, which I could not confirm statically for every id.
- The dungeon, tower and biome chapters have order 61–66, after the Dragon (60), in the single linear story track, so they only open post-finale. I did not move them; the files belong to those modules.
- City-magic and tower topic texts are based on hints that existed while those agents were still working; re-check after they finish.
- I overwrote a `patch1.py` in the shared scratchpad by accident (another agent's temp script, content unknown), then moved my files to `scratchpad/story_tmp/`. No repo file was affected.

**PEDIDOS**
1. **settlements:** `MagicApi.addCounter(player, "settlements.founded", 1)` when a player founds a city, so the "Uma Cidade Sua" beat fires.
2. **integration (`docs/ARCHITECTURE.md`):** add the ids above, `StoryService.onStep/onChapterBegin/onChapterDone`, and the `story` cutscene kind to the catalog (also list it in the `core/api/Cutscenes` javadoc).
3. **Coordinator:** decide whether `depths`, `dwarf_hold`, `nether_citadel`, `end_library`, `towers` and `magic_biomes` should keep order 61–66 or move before the finale.
4. **Coordinator:** if `tools/gen/journal.py` is ever re-run, re-run `py -I tools/gen/journal_story.py` afterwards (it drops any key another folder already defines).
5. **Renan:** read the campaign lines in `tools/gen/journal_story.py` (section "Campanha — batidas") before release; the Echo's voice and the hints at hidden truths are my writing on top of `docs/LORE.md`.