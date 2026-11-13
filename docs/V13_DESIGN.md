# Isekai Mahou v1.3 — "Desempenho, Espetáculo e Las Noches"

Mod NeoForge 1.21.1 (Java 21, Mojang mappings + Parchment, GeckoLib, Curios, playerAnimator `dev.kosmx.playerAnim`).
The player (Renan, PT-BR) asked for:
1. a low-end PC mode and an OK PC mode;
2. less lag;
3. better spells and particles, everywhere — Senbonzakura is already good, leave it;
4. more animations;
5. big, frightening race transformations;
6. boss fights with better models, attack animations, theme music and unique effects (example: Barragan's Respira ages you);
7. Las Noches, Ulquiorra, Murciélago and Segunda Etapa;
8. a Hollow "class": a Shinigami who loses to the Visored inner hollow becomes fully hollowfied;
9. more render distance without lag;
10. statuses that last longer, based on exposure;
11. general polish.

Item 10 is DONE by the coordinator (environmental Wet accumulates up to 2 min, in `core/status/StatusEngine`).

## Shared rules (ALL agents)
- **Text and comments:** all player-facing text in PT-BR. Write `pt_br.json` and `en_us.json` in `src/main/lang/<your folder>/` (merged at build time). Comments in PT-BR, matching the existing style.
- **Do NOT run gradle** (`./gradlew` anything). Other agents are editing in parallel.
  - Compile your module with `bash tools/check-module.sh <module>`.
  - For edits outside `feature/`, use `bash tools/check-tree.sh <yourtag>` (whole-tree javac into its own dir).
  - Errors in OTHER agents' in-progress files are not yours: ignore them, but your own files must compile.
- **Only edit files you OWN** (table below). Otherwise use the public APIs read-only. If you need a change in someone else's file, write it in your final report under "PEDIDOS".
- **Effects quality:** every client visual effect goes through `client/fx/FxQuality`:
  - `count(n)` for particle counts;
  - `atLeast(Graphics.HIGH)` for expensive extras;
  - `range()` for draw distance.

  Server-side `sendParticles` cannot see the client preset, so keep counts moderate, prefer client-side effects via a payload, or send fewer particles and let the client multiply.
- **Performance is a first-class requirement:**
  - no per-tick `getEntitiesOfClass` with big AABBs;
  - no registry scans in tick handlers;
  - throttle to every 5–20 ticks;
  - cache;
  - clean up maps when an entity or player leaves.
- **Sounds:** there is no `sounds.json` yet. The `boss_music` agent creates `src/main/resources/assets/isekai_mahou/sounds.json` FIRST. Others who add sounds:
  - register their own `SoundEvent`s in their own module;
  - add entries to `sounds.json` with a quick read-modify-write merge (python: load, add your keys, dump) — never overwrite the whole file;
  - put the `.ogg` files under `assets/isekai_mahou/sounds/<module>/`.

  Synthesize SFX with python + numpy and encode with ffmpeg (`ffmpeg -i x.wav -c:a libvorbis -q:a 4 x.ogg`; ffmpeg is on PATH). NEVER download copyrighted audio.
- **Player animations:**
  - `core/anim/MahouAnims.play(entity, "<id>")` plays an animation built in `client/anim/AnimLibrary`, owned by the anim agent.
  - The anim agent ADDS these ids, which other agents may call by string literal (unknown ids must be a safe no-op):
    - `transform_roar`, `transform_rise`, `transform_burst`;
    - `hollow_mask_tear`, `cero_charge`, `cero_fire`, `sonido_step`, `resurreccion`;
    - `bow_charge_big`, `throw_overhand`, `spin_slash`, `ground_slam`, `kneel_channel`, `taunt`.
- **Boss music:** client module `boss_music` plays a theme while a boss is near.
  - The theme id is the entity type path (e.g. `ulquiorra`), unless the entity implements `core/api/BossThemed`, whose `musicTheme()` is read on the CLIENT (so it must use synched data).
  - Planned theme ids:
    - `elemental_guardian`, `abyss_sovereign`, `seraph`, `twilight_herald`, `primordial_dragon`, `lich`, `beast` (default for hydra/basilisk/wyvern/manticore/colossus), `mimic_king`;
    - `hollow_king`, `ulquiorra`, `ulquiorra_segunda_etapa`, `barragan`, `grimmjow`;
    - `las_noches` (ambient, while inside the palace), `generic_boss`. Ambient themes are requested by other modules' CLIENT code via `feature/boss_music/client/BossMusicApi.requestAmbient(theme)` (pre-created stub; boss_music implements playback).
- **Entity ids reserved:** `isekai_mahou:ulquiorra`, `isekai_mahou:barragan`, `isekai_mahou:grimmjow` (module `espada`).
  - Las Noches spawns them only by registry lookup of these ids (`BuiltInRegistries.ENTITY_TYPE.getOptional`), so the two modules stay decoupled.
- **Tools:** generators go in `tools/gen/<module>*.py` (python + numpy is available; no PIL? check `py -c "import PIL"`).
  - Existing generators show the art style: `tools/gen/fx_shinigami.py`, `hueco_mundo_models.py`, `fx_spells.py`.
  - **Do NOT run `tools/gen/icons.py` fully** (it overwrites hand-made icons).
- **Saved worlds:** never rename existing registry ids. Old worlds must keep loading.
- **Final report** (your last message), in English, short:
  - files touched;
  - how to test in game (commands);
  - known gaps;
  - "PEDIDOS" (changes needed in files you do not own).

## Ownership
| Job | Owns (may edit) |
|---|---|
| **perf** | `client/fx/FxQuality`; `core/MahouConfig` (client section); NEW `client/perf/**`; the settings UI wherever the client config is shown (find it — B menu settings / `client/menu/**`); `client/status/**`; `client/ux/**`; `client/hud/**`; `client/objectives/**`; `build.gradle` (the qolMods/deploy list ONLY); the server-side perf of modules nobody else owns: `cities`, `settlements`, `guild`, `journal` (perf only), `structures_*`, `creatures_*`, `world_v09`, `travel`, `automation`, `farm`, `ui_hud`, `ui_grimoire`, `mounts`, `equipment`, `potions`, `afterlife`, `trials`, `armory`, `paths`, `ascension`, `dimensions`, `core/**` except the folders owned below |
| **fx_classes** | `feature/class_smith`, `class_rogue`, `class_knight`, `class_enchanter`, `class_gunslinger`, `class_ranger`, `class_gambler`, `class_steelball` (VFX/SFX/feel; do not rebalance) |
| **fx_magic** | `glyph/fx/**`; `client/spellfx/**`; `core/ElementFx.java`; `core/ElementReactions` (visuals only); `feature/fire_water`, `earth_wind`, `light_shadow`, `arcane`, `forms_a`, `forms_b`, `fusions_a..d`, `legends_a..f`, `ultimates`, `domain`, `ritual_*`, `origins_a`, `origins_b`, `arcane_duel`, `class_quincy` (only if something is clearly missing; Shinigami/Quincy VFX were done in v1.2) |
| **anim** | `core/anim/**`; `client/anim/**`; `assets/isekai_mahou/player_animations/anim_*.json` (new) |
| **races** | `feature/character/**` |
| **boss_music** | `feature/boss_music/**`; `assets/isekai_mahou/sounds/music/**`; creates `sounds.json` |
| **bosses_1** | `feature/bosses_a`, `bosses_b`, `bosses_c`, `bosses_d`; `core/boss/**`; `client/boss/**`; their geo/animation/texture assets |
| **bosses_2** | `feature/bosses_e`, `bosses_f`, `bosses_g`; their assets |
| **las_noches** | `feature/las_noches/**`; `feature/hueco_mundo/**` (all of it, including polishing its 3 bosses); `feature/journal/**` (only for the Las Noches route entries) |
| **espada** | `feature/espada/**` |
| **hollow** | `feature/class_hollow/**`; `feature/class_shinigami/inner/**`; `feature/class_shinigami/HollowForms.java` (hooks only; keep Visored/Vasto working); `core/MagicPath.java` + `core/ClassKits.java` + `feature/paths/**` + `feature/outfits/**` (ONLY to add the HOLLOW class) |

Pre-created by the coordinator (already registered in `Modules`/`ClientModules`/`tools/modules.json`):
- module skeletons `las_noches`, `espada`, `class_hollow`, `boss_music` (with `*Module` and `client/*Client`);
- empty lang folders;
- `client/fx/FxQuality`, config `graphicsPreset`, `core/api/BossThemed`.
