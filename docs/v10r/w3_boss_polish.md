REPORT — job "boss_polish" (wave 3)

Both deliverables are coded and compile: `check-module.sh` is TUDO OK for bosses_a, b, c, d, f, g and espada, and `check-tree.sh boss_polish` is COMPILOU OK (3037 files). Nothing was run in game, and the animations were edited by script without being seen in a client.

**What was built**

1. **Attack animations (100 retouched).** `tools/gen/boss_polish.py` finds the fastest swing in each attack and adds a deeper wind-up, acceleration into the hit, overshoot and a slight whole-body squash/stretch. No existing keyframe time moved, so server hit ticks still line up.
2. **Death animations (18 rewritten).** Three new beats come before the old fall: hit recoil, doubling over and shuddering, and a last roar using the boss's own roar pose.
3. **Death show in code.**
   - A final-blow flash plus a short "kill cam" cutscene (kind `boss_phase`, never locks controls).
   - Energy leaking with an accelerating heartbeat, then a closing ring and a final shockwave with a light pillar.
   - The body fades out over the last 14 ticks.
   - The running attack animation is stopped on death so it cannot cover the death animation.
4. **Lines.**
   - Every boss in a, b, c, f, g plus the Trial Avatar gets a taunt when it kills a player and a "last stand" line at 15% HP, once per fight.
   - The three Espada get the taunt only.
   - Abyss Sovereign and Seraph get spoken last words; their "defeated" narration moves to the final burst.
   - All of these are throttled to at most one every 15 s.
5. **Phase change.** Every phase change in c, f, g now has a burst (ring, dust, sound, light shake), including the Patron's phases 1 and 3, which have no cutscene.
6. **Entry/phase cutscenes.** The audit found every boss already had them, so none were added. The Trial Sentinel was left without one on purpose: it is an elite that spawns several at a time.

**Behaviour changes to know about**
- Death lasts longer so the fade happens after the animation:

  | Boss group | Before (ticks) | Now (ticks) |
  |---|---|---|
  | Guardians (a) | 60 | 74 |
  | Abyss Sovereign, Seraph | 80 | 94 |
  | Class bosses (c) | 50 | 64 |
  | Overworld bosses (f), Swamp Hydra | 50 | 64 |
  | Ruin Colossus | 70 | 84 |
  | bosses_g | 50 | 64 |

- The five bosses_c death animations are now all 2.5 s (Lich was 1.5 s).
- Loot still drops at the moment of death.

**Files touched**
- New: `feature/bosses_{a,b,c,d,f,g}/polish/BossShow.java` (same class copied per module so modules stay independent), `feature/bosses_a/polish/PolishCommands.java`.
- New: `tools/gen/boss_polish.py`, `tools/gen/boss_polish_lang.py` (both safe to re-run).
- Edited base classes: `ElementalGuardian`, `BbBoss`, `ClassBossEntity`, `TrialAvatar`, `OverworldBoss`, `GBossEntity`, `EspadaBoss`.
- Edited bosses: `TwilightHerald`, `AbyssSovereign`, `Seraph`, `RuinColossus`, `SwampHydra`, `CorruptedWitch`, `ImprisonedPatron`.
- Edited the seven `*Module.java` files and the renderers `GuardianRenderer`, `BbBossRenderer`, `BossGeoRenderer`, `BfGeoRenderer`, `BgGeoRenderer`.
- Animation JSON for 21 bosses under `assets/isekai_mahou/animations/entity/`.
- `src/main/lang/{bosses_a,b,c,d,f,g,espada}/{pt_br,en_us}.json` (98 keys appended).
- bosses_e was not touched.

**New ids**
- Command `/mahou bosspolish <kill|laststand|taunt>`.
- Lang keys: `boss.isekai_mahou.fallen`, `command.isekai_mahou.bosspolish.{none,survival,done}`, and per boss `…<id>.taunt`, `…<id>.last_stand`, plus `last_words` for abyss_sovereign and seraph.
- No new entities, items, blocks, payloads or NBT keys; no boss id renamed.

**How to test** (after `/mahou admin login`)
- `/mahou spawnboss <id>`, for example `ignis`, `seraph`, `lich`, `wyvern_king`, `manticore`. Wake it and watch the attacks.
- `/mahou bosspolish laststand` sets the nearest boss to 14% HP: phase bursts on the way, then the last-stand line.
- `/mahou bosspolish kill` shows the kill cam, the new death animation, the fade and the finale.
- `/mahou bosspolish taunt`, in survival: the boss kills you and says its taunt. You become a soul through the afterlife system.

**Not done / not verified**
- Limb directions and the 3–4% squash amount in the retouched animations need a look in a client.
- The fade relies on switching to a translucent render type; I copied the pattern the Ulquiorra renderer already uses but did not see it on these models.
- The death and phase particles are sent by the server, so they do not scale with the client graphics preset. Counts are kept low (roughly 300 over a death, 130 in the finale). Shake goes through `BossKit.shake`, which already applies `shakeScale()`.
- No per-hit impact code was added beyond two bosses_c attacks (Witch pool burst, Patron claw); the other heavy hits already shook the camera.
- Twilight Herald's and the Primordial Dragon's deaths, and the Espada deaths, were left as they are.
- The kill cam on the Trial Avatar was not checked against the trials victory sequence.

**PEDIDOS**
1. After re-running any `tools/gen/bosses_*.py`, run `py -I tools/gen/boss_polish.py` again, since those generators overwrite the animation files.
2. Mention in the release notes that boss kills now have a 2-second kill cam, which the existing cutscene option turns off.
3. Optional, for the perf/core owner: a batched boss-FX packet in core so the death show can respect `FxQuality` on the client.