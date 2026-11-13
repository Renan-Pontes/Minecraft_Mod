REPORT — job "fixes" (wave 1)

All four deliverables are coded and compile (`check-module.sh afterlife` = TUDO OK, `check-tree.sh fixes` = COMPILOU OK). Nothing was run in game — I could not launch a client or server.

**1. Soul / near-death not triggering**
- **Cause found in his logs:** the only player deaths recorded since afterlife shipped are two `/kill` deaths on 12 Nov ("NZoroark morreu" plus the `/kill` feedback line). `/kill` was deliberately a true death, so no soul appeared.
- **Hidden rules removed:** bleeding, corrupted, frozen, petrified, wither, overkill above 35% of max health, and "hit not measured this tick" all silently forced a true death. Boss fights hit these constantly.
- **Rules now:** any death becomes the soul challenge except void, stuck in wall / world border, fire (Carbonizado, unchanged), the 60 s window after returning, creative/spectator, keepInventory, or a player still choosing class/race.
- **`/kill` now drops you to a soul;** a second `/kill` as a soul kills for real. Config `killCommandNearDeath` (default true) turns this off.
- **Config key replaced:** `overkillThreshold` (0.35, pinned in his existing toml) is gone; `overkillLimit` defaults to 0 = no limit. His file self-corrects on next launch.
- Every player death now logs `[afterlife] <name> caiu (<type>): ...` with the reason, and a failure while starting the challenge falls back to a normal death instead of crashing.

**2. Entry protection (class/race)**
- **Race screen without class:** creation steps counted as pending even with no class, so respawning classless opened the race screen. Fixed in `OnboardService.pending`.
- **New `EntryGuard`:** while a player has no class, or has a class with the race step pending, they take no damage (void and `/kill` still pass), deal none, and mobs drop them as a target.
- **Screen reopens:** every 2 s the server pings; a client with no screen open asks the server to reopen the class screen or the pending step. Open screens (class, race, death, chat, pause) are never touched.

**3. Primordial Dragon and `/kill`**
- I did not find why `/kill` was ignored; the old path looked correct on paper. I replaced it rather than patched it.
- `kill()` is now overridden: the dragon vanishes at once with no 10 s cinematic and no rewards, taking bar, domain, shades, chains, curses, temporary terrain and the sealed arena with it. `hurt(genericKill)` routes to the same path.

**4. Summons count as a boss fight**
- New `ArenaAllies` scans sealed arenas once a second for player-owned mobs (owner online) that target the boss or traded hits with it in the last 10 s.
- If any are found it renews the arena seal, and a boss with no target takes the nearest such summon as its target, which stops the "no target → heal/reset" routines. It never pulls a boss off a player. Trial bosses are skipped.

**Files touched**
- `feature/afterlife/`: `AfterlifeManager.java`, `AfterlifeModule.java`, `AfterlifeConfig.java`
- `core/onboard/`: new `EntryGuard.java`; `OnboardService.java`, `OnboardCore.java`, `OnboardPayloads.java`, `OnboardTasks.java`, `OnboardCommands.java`
- `client/onboard/`: `ClientOnboarding.java`, `OnboardClientHandlers.java`
- `core/boss/`: new `ArenaAllies.java`; `BossToolkit.java`, `BossCommands.java`
- `feature/bosses_e/entity/PrimordialDragon.java`
- `src/main/lang/{afterlife,core_boss,core_onboard}/{pt_br,en_us}.json`

**New ids**
- Payloads `isekai_mahou:core_onboard_entry_nudge` (S→C) and `isekai_mahou:core_onboard_entry_resume` (C→S).
- Config keys `overkillLimit`, `killCommandNearDeath`.
- No new items, blocks, entities or NBT keys; no saved key renamed.

**How to test** (after `/mahou admin login`)
- **Soul:** in survival, `/mahou afterlife hit mob`, then `boss`, `fall`, `kill` — each should start the challenge and print the decision. `hit fire` should give Carbonizado. `/mahou afterlife why` shows the last decision. Plain `/kill` → soul; `/kill` again → true death with corpse.
- **Entry:** new world, leave the class screen open and let a zombie hit you → no damage, no targeting. From the server console run `kill <player>` → after respawn the class screen reopens within about 0.5–2 s, never the race screen. Pick a class, press Esc on the race screen → it reopens in ≤2 s. `/mahou onboarding status` shows class, pending steps and protection.
- **Dragon:** `/mahou spawnboss primordial_dragon`, wake it, then `/kill @e[type=isekai_mahou:primordial_dragon]` → gone immediately, no rewards, terrain restores.
- **Summons:** start a boss fight with summons out, leave the arena or die; `/mahou boss arenas` should show "player allies fighting: N" and the arena should stay sealed with the boss fighting the summons.

**Not done / not verified**
- The 60 s "died again right after returning" window is kept as it was.
- Bosses whose reset checks "any player nearby" rather than "has a target" are covered only through the auto-target; `ArenaAllies.fighting(boss)` exists for them but I did not wire it into other modules' bosses.
- A boss can still lose its target and start regenerating while its only opponent is a soul with no summons out.

**PEDIDOS**
1. Run `./gradlew compileJava` before wave 2 so `check-module.sh bosses_e` sees the new core classes.
2. Optional: expose `OnboardService.inEntry` through `core/api/Onboarding` so modules can hide hints or HUD during character creation.
3. Optional: have `BossArenaService.inCombat` (`core/social`) and the per-boss reset routines in `bosses_a/b/c/d/f/g` and `creatures_b` call `ArenaAllies.fighting(boss)` directly.
4. Tell Renan in the release notes that `/kill` now makes him a soul first, and a second `/kill` finishes it.