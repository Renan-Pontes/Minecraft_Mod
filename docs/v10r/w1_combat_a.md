## combat_a report

All three deliverables are built and compile, but none of it has been run in the game. `bash tools/check-module.sh combat_lock` ends with TUDO OK and `bash tools/check-tree.sh combat_a` ends with COMPILOU OK (all).

### Files touched (all inside the job's area)
- **`core/anim/`**
  - New: `GuardService.java`, `GuardEvent.java`.
  - Edited: `AnimConfig.java`, `AnimPayloads.java`, `MahouAnims.java`, `DodgeService.java`, `FeelService.java`.
- **`client/anim/`**
  - New: `GuardInput.java`, `AnimGuard.java`.
  - Edited: `AnimLibrary.java`, `MahouAnimsClient.java`.
- **`feature/combat_lock/`**
  - `CombatLockModule.java`, `LockConfig.java`, `LockPayloads.java`, `LockServer.java`, `ThreatWarnings.java`.
  - `client/`: `CombatLockClient.java`, `LockOn.java`, `LockMarker.java`, `LockCameraAccess.java`, `ThreatHud.java`.
- **`src/main/lang/combat_lock/`**: `pt_br.json`, `en_us.json`.

### What was built
- **Lock target**
  - Middle mouse locks on the enemy nearest the crosshair; pressing again releases.
  - The player's view is turned toward the target every frame, so melee and spells go at it; the server also snaps spell aim exactly onto the target at cast time.
  - Third person: the camera moves over the shoulder and frames player and target. First person: softer aim tracking.
  - A small "V" marker floats above the target; nothing is added to the HUD.
  - Mouse wheel (or the unbound "next target" key) switches target.
  - Auto-release when the target dies, vanishes, goes beyond 32 blocks, or stays behind a wall for 2.5 s.
- **Block with parry**
  - Holding Mouse 4 with a weapon, staff, bow or class weapon in the main hand blocks hits from the front. Empty hand cannot block.
  - Block absorbs 50% (30% for magic and explosions), costs hunger, slows movement by 45%, and breaks for 2 s on a hit of 14 or more.
  - A direct hit within 6 ticks (0.3 s) of pressing is a parry: hit cancelled, attacker stunned 30 ticks (the status system halves it for bosses) and knocked back, with a sound and a short edge flash.
  - Anti-abuse: one parry per press, and no parry window when the key is mashed (under 14 ticks between presses). Dodging drops the guard and blocks parry for 16 ticks. Attacking or casting also drops the guard.
  - Peace Mode blocks it on both client and server, like the dodge.
- **"!!!" warning**
  - Shown for a common (non-boss) mob that is targeting the player in three cases: a heavy hitter getting into reach, a creeper swelling, or a melee approach or bow draw from behind.
  - It appears over the mob if on screen, otherwise on the screen edge toward the mob (behind = bottom). It lasts 0.9 s, at most two at once, with a soft tick sound.

### New ids
- **Keys:** `key.isekai_mahou.lock_target` (Mouse Middle), `key.isekai_mahou.lock_next` (unbound), `key.isekai_mahou.guard` (Mouse 4).
- **Payloads:** `anim_guard`, `anim_guard_fx`, `combat_lock_target`, `combat_lock_warn`.
- **Animations:** `guard_weapon`, `parry`.
- **Attribute modifier:** `isekai_mahou:guard_slow` (transient).
- **Counters:** `feat.parry`, `feat.guard_block`.
- **GUI layers:** `anim_parry_flash`, `combat_lock_warn`.
- **Config files:** `isekai_mahou-lock-common.toml`, `isekai_mahou-lock-client.toml`, plus new entries in the existing combat common/client files.
- **Hooks for later waves:**
  - `GuardEvent.Parry` and `GuardEvent.Block` (server events).
  - `GuardService.isGuarding` and `GuardService.interrupt`.
  - `MahouAnimsClient.onLocalParry(Runnable)`, intended for the combat_feel hit-stop.
  - `LockServer.target(player)` and `CombatLockClient.lockedTarget()`.
- No saved data or NBT was added or changed.

### How to test in game
Use survival; mobs ignore creative players and the training hit does nothing in creative. Run `/mahou admin login` first.
1. **Lock:** `/summon zombie ~ ~ ~5`, middle-click, press F5 for the shoulder camera, cast Z/X/C. Summon a second zombie and scroll to switch. Kill the target to see the release. `/mahou combat_lock info` shows the server-side target.
2. **Block and parry:** hold a sword or staff, then run `/mahou guard hit 6 40`. It gives two beeps and then a frontal hit, and reports health lost.
   - Hold Mouse 4 through it: about half damage.
   - Press Mouse 4 right at the hit: 0 damage plus flash.
   - `/mahou guard hit 20 40` while holding: guard breaks.
   - `/mahou guard info` shows counts and whether the held item can block.
3. **Warning:** `/mahou combat_lock warn heavy` and `/mahou combat_lock warn behind`. For the real trigger, `/summon vindicator ~ ~ ~8`, or let a zombie approach from behind.

### Not done / not verified
- **Feel values are untuned.** Shoulder offset, tracking speed, the first-person weapon tilt, the guard and parry poses, and marker size were chosen blind.
- **The 0.3 s parry window is unverified against real mobs and network lag.** Vanilla mobs have no wind-up, so a parry is anticipation; the "!!!" is the cue.
- **Mouse 4 as the block default** follows the existing Mouse 5 loadout key. Players without side buttons must rebind it in Controls.
- **Middle mouse is shared with Pick Block on purpose.**
  - The click that locks or releases swallows the pick; in creative, aiming at a block still picks the block.
  - The lock key is deliberately hidden from conflict highlighting in the Controls screen, so rebinding it onto a used key will not show red.
- **Scroll switching takes over the hotbar scroll while locked.** It can be turned off with `lockScrollSwitch`.
- **Ranged warnings only cover mobs that visibly draw a bow or crossbow.**
- **No journal chapter was added.** I could not confirm how a new chapter interacts with the main route.
- **Two one-time action-bar hints exist** (lock on first attack, block on first hit taken). They do not go through the ui agent's new hint system.

### PEDIDOS
- **ui:** move the two hints (`message.isekai_mahou.combat_lock.hint`, `message.isekai_mahou.guard.hint`) into the ui_hud hint system, and add loading tips for lock, block and parry. The ui_hud lang already titles my two `lock` config files, so those names must stay.
- **combat_feel (wave 2):** hook hit-stop on parry through `MahouAnimsClient.onLocalParry` and `GuardEvent.Parry`.
- **renown (wave 2):** `feat.parry` and `feat.guard_block` are available for titles.
- **coordinator:** rebuild `build/classes` before wave 2, so modules can compile against the new `core/anim` classes (`GuardService`, `GuardEvent`).