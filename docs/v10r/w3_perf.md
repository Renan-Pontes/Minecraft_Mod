**Job "perf" report**

All four deliverables are built and the whole tree compiles (`bash tools/check-tree.sh perf` → COMPILOU OK, 3037 files). `bash tools/check-module.sh forms_a` as shipped fails with 13 "cannot find symbol" errors, all on the new core symbols (`FxBatch`, `ElementFx.reactionAt`, `ElementReactions.FX_*`), because it compiles against the stale `build/classes/java/main`. Compiled against the fresh check-tree classes, the module gives javac OK / JSON OK / TUDO OK; it will pass as-is after the coordinator's gradle compile. Nothing was run in game.

**What changed**
- **Batched spell particles:** everything spells request during a tick is queued per world and leaves at end of tick as one packet per player. Rings and lines travel as parameters only and the client draws the points at the Graphics Preset density. `ElementFx.burst/ring/line/channeling/sendFar` (and flash, shockwave, runeCircle, pillar, castCircle through it) and all of `FormsFx` go through it, so every module benefits without changing its calls.
- **Lighter reactions, same strength:** damage and statuses are untouched.
  - Targets with no active effect skip the reaction checks.
  - Each reaction is one client-built burst; the 2–3 vanilla particle packets per reaction are gone from the server. The client version uses large cheap pieces (flashes, rings, ground seal, bolt, column) plus ice/stone/sand shards.
  - Electrocute arcs are one batch item each.
  - Per-tick budget: 4 reactions get full burst + sound + text, the next 8 a light burst, the rest mechanics only (event and counters always fire).
- **Music:** re-encoded from the source MP3s at Vorbis q2 with the same cuts and durations, 40.8 MB → 34.3 MB. The sources have nothing above 16 kHz. `sounds.json` and `BossMusicCues.java` were not touched. `--q 1.5` would give about −31% if you want smaller.
- **Graphics Preset:** `REDUCED_PARTICLES` reads swapped for `FxQuality` in 14 files; "reduced" now means LOW or MEDIUM, which matches what the PC presets already set.

**Files touched**
- New: `core/fx/FxBatch.java`, `client/spellfx/FxBatchClient.java`, `feature/forms_a/PerfCommands.java`.
- Edited in my area: `core/ElementFx.java`, `core/ElementReactions.java`, `client/spellfx/FxBurstClient.java` (reaction FX), `feature/forms_a/{FormsAModule,FormsFx}.java`, `feature/forms_a/entity/SpellWaveEntity.java` (homing target search every 2 ticks), `src/main/lang/forms_a/*.json`, `tools/music_import.py`, `sounds/music/**`.
- Edited outside the listed area (small, no owner this wave): `core/status/ReactionFx.java` (particle bursts removed, sound + text kept) and one line in `core/status/StatusEngine.sparkLine` (routed into the batch).
- Preset swap: `client/boss/BossCameraShake`, `armory/client/WeaponAmbience`, `class_gunslinger/{GsFx,client/GunAnimations}`, `creatures_e/CeFx`, `domain/client/DomainClientEffects`, `equipment/client/CastAnimations`, `legends_e/LeUtil`, `legends_f/client/LfFxClient`, `origins_a/client/ShapeClient`, `outfits/client/OutfitAmbience`, `travel/block/TravelStoneBlockEntity`, `ultimates/entity/{MeteorEntity,SpectralColossusEntity}`.

**Swap skipped (owned by other agents — coordinator)**
- boss_polish: `bosses_b/entity/BbBoss`, `bosses_c/entity/BossFx`, `bosses_d/entity/TrialAvatar`, `bosses_e/entity/DragonFx`, `bosses_f/entity/BfFx`, `bosses_g/entity/BgFx`.
- story: `cinematic/client/CinematicSettings`.
- summon_forge: `earth_wind/client/CameraShake`, `earth_wind/entity/{TempestStorm,WindTornado}`, `fire_water/FwUtil`, `fusions_a/FaUtil`, `forms_b/util/FormsBUtil`, `light_shadow/entity/{SacredBarrierZoneEntity,SanctuaryZoneEntity}`.
- integration: `guild/hunt/HuntBanner`, `core/MahouConfig` (the definition).
- Left on purpose: `client/fx/FxQuality` and `client/perf/PcPreset` (they read and write the option itself).

**New ids**
- Payload `isekai_mahou:fx_batch` (registrar `fxbatch-1`).
- Command `/mahou fx stats|stress|reactions` (op). `/mahou perf` already exists as the core tick profiler, so I did not reuse the name.
- Lang keys `command.isekai_mahou.fx.{stats,stress,reactions}`.
- No saved ids or NBT keys changed.

**How to test**
- `/mahou fx stress 60 5`, then read the line printed at the end: items, packets, and how many packets the old path would have used. `/mahou fx stats` prints and resets the same counters at any time.
- `/mahou fx reactions 20` fires 20 reaction bursts in one tick: expect 4 full, 8 light, 8 nothing.
- `/spellfx reaction all` shows each reaction burst; compare on LOW and ULTRA.
- Cast Cone, Slash, Rain, Fall, Mark and Beam and confirm they look the same as before; Fall and Rain should still be visible from about 48 blocks on HIGH.
- Soak several mobs and hit them with a Lightning area spell: arcs between them, at most 4 floating texts.
- Fight any boss with imported music and check the entrance still lands on the drop.
- `py -X utf8 tools/music_import.py --recompress --dry` shows what a re-encode would change.

**Not done / not verified**
- No in-game run. Offline I only checked that the batch packet encodes and decodes correctly (vanilla particle types) and that ring/line point counts scale with the preset.
- q2 audio was not listened to; the choice rests on the 16 kHz source cutoff and unchanged durations.
- `elemental_guardian` and `boss_victory` were left as they are: the first has a 0-byte MP3 in `MUSICAS_DOS_CHEFES` (failed download), the second has no source, so both are still the mod's generated tracks.
- On MEDIUM, effects that used the old "reduced" switch are now sparser than before for players who had MEDIUM without that switch.
- Heads-up for the public release: the boss tracks are third-party songs (anime and game OST covers), unlike the generated ambient music.

**PEDIDOS**
1. Coordinator: do the `REDUCED_PARTICLES` → `!FxQuality.atLeast(MahouConfig.Graphics.HIGH)` swap in the skipped files above.
2. integration (`docs/ARCHITECTURE.md`): add `core.fx.FxBatch` to the core table and note that `ElementFx.burst/ring/line/sendFar` are batched, so modules should prefer them over looping `level.sendParticles`.
3. transform_forge (`glyph/fx/SpellFxCore.init`): optionally call `FxBatch.init(modBus)` there; it is safe to call twice, and today `FormsAModule` does it.
4. Optional: `StatusEngine.hiss` and other per-particle `sendParticles` loops in boss and summon modules can switch to `FxBatch.emit(...)` with the same arguments.