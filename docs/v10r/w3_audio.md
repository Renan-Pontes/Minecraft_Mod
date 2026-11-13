**Job "audio" — report**

Everything compiles (`check-module.sh ambience` → TUDO OK, `check-tree.sh audio` → COMPILOU OK), but nothing was run in game and nobody has listened to the audio. I only checked levels, spectrum and loop seams numerically.

**Files / dirs touched (all mine)**
- `src/main/java/com/renanpontes/isekaimahou/feature/ambience/`:
  - server/common: `AmbienceModule`, `AmbienceConfig`, `AmbienceSounds`, `AmbienceNet`, `ZoneTracker`, `SpellSounds`
  - `client/`: `AmbienceClient`, `AmbientPlayer`, `AmbientTrack`, `SpellSfxClient`, `EmissiveModels`
- `src/main/lang/ambience/pt_br.json`, `en_us.json`
- `tools/gen/ambience_sfx.py`, `tools/gen/ambience_music.py` (numpy + ffmpeg, nothing downloaded)
- `src/main/resources/assets/isekai_mahou/sounds/ambience/spell/*.ogg` (50 files, about 1 MB) and `sounds/ambience/music/*.ogg` (7 tracks, about 6.1 MB)
- `src/main/resources/assets/isekai_mahou/ambience/emissive.json`
- Shared file `assets/isekai_mahou/sounds.json`: 57 keys added by read-modify-write, nothing else changed.

**What was built**
- **Spell sounds:** 10 timbre families (fire, water, earth, wind, light, shadow, lightning, ice, nature, arcane); the 21 elements map onto them, fusions change the pitch.
  - Cast sounds come in three sizes (by base cost / ultimate), impact sounds in two.
  - They fire from `SpellCastEvent.Post` and `SpellDamageEvent.Post`, so no glyph was edited. The "silent" augment is respected, and impacts are throttled.
  - They go through their own packet so each player has their own volume and on/off.
- **Ambient music:** 7 generated loops of about 96–102 s (dungeon, citadel, library, tower, city, abyss, home), all mastered to the same low level.
  - The server detects the zone every 2 s from the dimension, the structure id by name pattern, and `Places` city types (NPC cities and player settlements).
  - The client fades in over 4 s, crossfades between zones, and fades out 5 s after leaving.
  - Any other mod music (boss themes, victory sting, Las Noches) takes priority; vanilla music is muted while a track plays.
  - A one-time hint (`ambience_music`) tells the player where the volume option is.
- **Glow in the dark:** blocks and items listed in `emissive.json` (crystals, runes, ores, portals, essences, cores) get a minimum light level on their faces, with no texture, model or other-module edits. I added the biomes agent's new crystal blocks by id.
- **Options** (`config/isekai_mahou-ambience-client.toml`, with PT-BR/EN names and tooltips): `ambientMusic`, `ambientMusicVolume` (default 45), `spellSounds`, `spellSoundVolume` (default 70), `glowInDark`.

**New ids**
- Sound events: `ambience.spell.<family>.<cast_s|cast_m|cast_l|impact_s|impact_l>`, `ambience.music.<dungeon|citadel|library|tower|city|abyss|home>`
- Payloads: `ambience_zone`, `ambience_sfx` (registrar `ambience-1`)
- Hint: `ambience_music`
- No items, blocks, entities or saved data.

**How to test** (after `/mahou admin login`)
- `/mahou ambience zone` shows the zone detected where you stand.
- `/mahou ambience music city` (or `dungeon`, `citadel`, `library`, `tower`, `abyss`, `home`) forces a track; `/mahou ambience music off` forces silence; `/mahou ambience music auto` returns to normal.
- `/mahou ambience sfx fire cast 2` or `/mahou ambience sfx ice impact 0` plays a test spell sound (family, cast|impact, size 0–2).
- Real flow: walk into a tower, the depths dungeon, a city or the Endless Abyss and wait about 2–4 s; spawn a boss and the ambient track should duck out, returning about 10 s after the boss music ends.
- Glow: place `mana_crystal_block`, `void_crystal` or `rune_stone` in a dark cave, or hold an essence. Toggling the option needs F3+T.

**Not done / not verified**
- Glow lights the whole face, so ores (levels 5–6) have faintly lit stone as well; there are no per-pixel glow masks and no entity glow layers. Behaviour under shader or Sodium-like mods is unknown.
- The vanilla cast sound from `ElementFx.castSound` still plays underneath the new ones.
- Structures get music only if their id matches the name patterns; the towers agent's new floating islands will be silent unless the id contains "tower".
- `check-assets` prints a false-positive warning for the dynamic key prefix `ambience.isekai_mahou.zone.`; all 8 real keys exist.

**PEDIDOS**
- **perf (`core/ElementFx.castSound`):** lower the vanilla cast sound to about 0.3, or drop it, now that custom sounds exist.
- **perf (music recompress):** if desired, include `sounds/ambience/music/*.ogg` (stereo, 32 kHz, q2); keep the file names.
- **towers / dungeons / biomes:** new structure ids should contain `tower`, `dungeon`, `depths`, `crypt`, `citadel`, `library` and so on to get music automatically; send me or the coordinator any new glowing block ids for `emissive.json`.
- **integration (`docs/ARCHITECTURE.md`):** add the `ambience` module to the catalog: sound ids, `emissive.json` format, the structure-name convention, `/mahou ambience` commands.
- **Coordinator:** when editing `sounds.json`, keep the `ambience.*` keys; both generators can re-add them with `--json`.