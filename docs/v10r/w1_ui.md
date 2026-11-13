**Job "ui" report**

All four deliverables are built and compile. `bash tools/check-tree.sh ui` → COMPILOU OK (2832 files). `bash tools/check-module.sh ui_hud` as shipped fails with 18 "cannot find symbol" errors, all on the new `MahouConfig` members, because it compiles against the old `build/classes/java/main`. I did not touch that shared folder; compiling the module against a private build of `MahouConfig` + `client/title` gives javac OK, JSON OK, TUDO OK. It will pass as-is after the coordinator's `./gradlew compileJava --offline`. Nothing was run in game.

**Files touched**
- `core/MahouConfig.java` — 13 new client options, static getters, per-player prefs store, translated enum names.
- `feature/ui_hud/` — new `Hints`, `HintPayloads`, `HintTriggers`; `UiHudModule` now registers them.
- `feature/ui_hud/client/` — new `HintOverlay`, `HudScale`, `MahouSettings`; edited `WorldHealthBars`, `BossBarTracker`, `MahouHudLayer`, `MahouTopLayer`, `UiHudClient`, `StatusWindowTab`, `TopSpace`, `HudCard`, `HudPlace`, `HudManaBar`, `HudHealthBar`, `HudSpellBar`, `HudCastBar`, `HudPlayerStatuses`.
- `client/title/` — new `GameTips`, `LoadingArt`; edited `MahouTitleScreen`, `TitleHook`.
- `src/main/lang/ui_hud/{pt_br,en_us}.json` — 391 keys each.

**What changed**
- **Boss bars:** bosses never get a floating bar (anything tagged `c:bosses`, or whose name matches a boss bar currently on screen); status icons over them stay. The entity search runs every 5 ticks from a cached list instead of every tick.
- **Settings in the Minecraft menu:** Mods → Isekai Mahou → Config now opens the NeoForge config screen, and Options (main menu and Esc) has a "✦ Isekai Mahou..." button to the same screen. That screen has a corner button to the old PC-mode screen.
- **Status tab:** its six toggles are replaced by one shortcut to that screen, following his "no menu do Minecraft, não do mod".
- **New options:** `hudCard`, `hudPlaceName`, `hudResourceBar`, `hudHealthBar` (off = vanilla hearts return), `hudSpellBar`, `hudCastBar`, `hudLevelBanners`, `hudScalePercent` (70–130), `showHints`, `loadingTips`, `guideTutorial`, `peaceModeAuto`, `screenShakePercent`. All `MahouConfig` options, old and new, have PT-BR and EN names and tooltips.
- **Hints:** `Hints.show(player, "id", args...)` on the server, text key `hint.isekai_mahou.<id>`. One discreet line above the hotbar, one at a time, 25 s gap, waits for screens, dialogue and casting, and is only marked seen once actually displayed. Thirteen built-in hints, silent for the first 4 minutes of play time.
- **Title and loading:** rotating gameplay tip on the main menu (30 tips); loading screens get the mod's magic sky, a small logo and a tip.

**New ids**
- Attachment `ui_hud_hints`.
- Payloads `ui_hud_hint_show`, `ui_hud_hint_seen`, `ui_hud_hint_request`, `ui_hud_prefs`.
- GUI layer `ui_hud_hints`.
- Command `/mahou hints`.
- Lang prefixes `hint.isekai_mahou.*`, `tip.isekai_mahou.0..29`, `options.isekai_mahou.*`, `isekai_mahou.configuration.*`.

**How to test**
- `/mahou hints all` queues all 13 hints; `/mahou hints show low_mana` shows one; `/mahou hints reset` clears the seen list.
- Esc → Options → "✦ Isekai Mahou..." → "Interface e efeitos": toggle HUD parts, move "Tamanho do HUD", turn "Dicas de primeira vez" off.
- `/summon isekai_mahou:goblin_king` and hit it: only the top bar should show; a normal mob still gets the floating bar.
- Main menu shows a tip under the buttons; create a world to see the loading art.
- `guideTutorial` off + new character → the Guide Spirit tutorial is skipped.

**Not done / not verified**
- `peaceModeAuto` and `screenShakePercent` do nothing until the PEDIDOS below are applied.
- HUD scale above 100% can overlap the party list or Stack panel by a few pixels, because the core layout uses a fixed card height. The top-centre layer (Analytic Eye, banners) is not scaled.
- The loading sky does not appear on the "loading terrain" screen (it draws its own background without the event); logo and tip still do. The startup Mojang overlay is untouched.
- The loading sky uses `ScreenEvent.BackgroundRendered`, which is deprecated for removal but still fired in 21.1.265.
- Tips 15–17 (glyph sources, builders, city needs) describe other wave-1 jobs as specified in the contract; adjust if those change.
- I added section labels for all eight config files, including combat, lock, spellfx and afterlife. If an owner adds the same `isekai_mahou.configuration.section.*` key, drop mine.

**PEDIDOS**
1. `core/ux/PeaceMode.onTick`: guard the two automatic `set(sp, true, false)` calls with `MahouConfig.peaceAuto(sp)`.
2. Multiply shake strength by `MahouConfig.shakeScale()` in:
   - `client/boss/BossCameraShake.onCameraAngles` (after `k = Math.min(k, MAX_TOTAL)`) — the main one;
   - `feature/cinematic/client/CameraShake.offsets`;
   - `feature/earth_wind/client/CameraShake.onCameraAngles`;
   - `feature/domain/client/DomainClientEffects.onCameraAngles`;
   - `feature/class_hollow/client/HollowFxClient.onCamera`;
   - `feature/character/client/FormFx.onCamera`.
3. `client/IsekaiMahouClient` line 43–44: remove the old `IConfigScreenFactory` → `PerfScreen` registration (mine overrides it at runtime; this is tidiness).
4. `client/menu/StatusScreen` gear: point it at `MahouSettings.open(this)` or remove it, so settings live only in the Minecraft menu.
5. Owners of `AfterlifeConfig`, `LockConfig`, `SpellFxConfig`: add `isekai_mahou.configuration.<key>` names and tooltips so their options aren't raw keys.
6. Later waves: call `Hints.show(player, "<id>")` for city, market and familiar first-times, and use `MahouConfig.shakeScale()` for any new camera shake.