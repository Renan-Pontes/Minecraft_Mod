**mastery job report** — all three deliverables are built and the full tree compiles, but nothing was run in-game and `check-module.sh ui_grimoire` does not pass yet.

## Checks
- `bash tools/check-tree.sh mastery` → **COMPILOU OK (all)**, 2832 files.
- `bash tools/check-module.sh ui_grimoire` → **fails (49 errors)**. It compiles against `build/classes/java/main`, which predates the new core classes (`CircleStyle`, `GlyphMastery`, `MagicData.glyphXp`). It should pass after the coordinator's `./gradlew compileJava --offline`; I did not touch `build/classes`.
- Substitute check: my files (glyph, spellfx, `MagicData`, ui_grimoire) compiled together against the old foundation without errors. Lang and particle JSON are valid, with 0 asset warnings and no duplicate keys.
- Two throwaway JVM tests passed:
  - Old recipes and old saves load with defaults, and the default circle is not written to disk.
  - A 99-mana spell costs 81 with every glyph at X, effect power is ×1.135, and cooldown/base cost are unchanged.

## What was built
- **Glyph mastery (I–X)**: every glyph in a recipe gains use-XP when an effect really lands.
  - XP: enemy 2, other living 1, self/ally 1 only during combat, block work 1 only if a block actually changed. Each glyph gains at most once per 40 ticks (100 for blocks); casting into the air gives nothing.
  - Curve: 100 / 300 / 700 / 1500 / 3000 / 5500 / 9500 / 16000 / 26000 total XP — at the rate cap, level X is about 7 hours of continuous enemy hits per glyph.
  - Bonuses: −2% mana per level on that glyph's share (−18% at X); effect glyphs +1.5% power per level (+13.5% at X). Cooldown, cast time and XP per cast are untouched.
  - Visuals: richer cast circle at average level 4 / 7 / 10.
  - UI: thin bar under icons in the Glyphs tab and Grimoire palette, level + bar + numbers in the detail panel, mastery lines in every glyph tooltip, and a mastery line in the mana tooltip.
- **Magic circle per spell**: optional `circle` field on `SpellRecipe` (7 shapes, 12 centre symbols, own colour). Three small buttons after the hue bar in the Grimoire open pickers; mouse wheel cycles, right-click resets, and a rotating preview shows on hover. A `/spellfx circle` demo command was added for testing without the Grimoire.
- **Channel aura**: client-only motes around anyone holding a cast longer than 25 ticks or meditating. Toggle is `channelAura` in a new `isekai_mahou-spellfx-client.toml`.
- **Behaviour change**: a player holding a cast now shows only the magic circle and gather effect; the loose server-side channel particles were removed for players (mobs unchanged).

## Files touched (under `c:/Users/renan/Music/ROCKET/RocketSimVis/Minecraft_Mod/`)
- New:
  - `src/main/java/com/renanpontes/isekaimahou/glyph/CircleStyle.java`
  - `src/main/java/com/renanpontes/isekaimahou/glyph/GlyphMastery.java`
  - `src/main/java/com/renanpontes/isekaimahou/client/spellfx/ChannelAura.java`
  - `src/main/java/com/renanpontes/isekaimahou/client/spellfx/SpellFxConfig.java`
  - `src/main/java/com/renanpontes/isekaimahou/feature/ui_grimoire/client/CircleArt.java`
  - `tools/gen/mastery_circles.py`
  - `src/main/resources/assets/isekai_mahou/particles/fx_circle_style.json`
  - `src/main/resources/assets/isekai_mahou/textures/particle/fx_circle_s1..s7.png` and `fx_circle_y1..y12.png`
- Edited in `src/main/java/com/renanpontes/isekaimahou/`:
  - `core/MagicData.java` (new optional field only)
  - `glyph/`: `SpellRecipe`, `SpellStats`, `SpellParser`, `SpellContext`, `SpellCaster`
  - `glyph/fx/`: `SpellVisuals`, `SpellFxParticles`, `SpellFxCore`
  - `client/spellfx/`: `FlatFxParticle`, `SpellFxClient`
  - `feature/ui_grimoire/client/`: `GrimoireDraft`, `GrimoireTab`, `GlyphIcons`, `GlyphsTab`
- Lang: `src/main/lang/core_spells/{pt_br,en_us}.json` and `src/main/lang/ui_grimoire/{pt_br,en_us}.json`

## New ids
- Particle type `isekai_mahou:fx_circle_style` (only loads the circle art into the atlas; never spawned).
- Save keys, both optional: `extra.glyph_mastery` in `MagicData`, `circle {shape, symbol, color}` in recipes. Nothing renamed or removed.
- Config file `isekai_mahou-spellfx-client.toml`, key `channelAura`.
- Commands: `/mahou mastery [list]` (any player); `/mahou mastery set|add|all|reset` (op); `/spellfx circle <shape> <symbol>` (op).

## How to test in game
1. `/mahou mastery set isekai_mahou:arcane_damage 10`, then open B → Glyphs: gold bar under the icon, "Maestria X" in the detail panel and tooltip. The Grimoire mana tooltip shows the discount.
2. `/mahou mastery all 7` gives a bigger cast circle with inner seal and a ground wave on cast; `/mahou mastery all 10` adds the outer layer and rune ring. `/mahou mastery reset` clears it.
3. Real gain: `/mahou mastery add isekai_mahou:projectile 95`, then hit a zombie three times a few seconds apart → action bar "Maestria: … nível II". Casting at air, or a self-heal out of combat, should give nothing; `/mahou mastery list` shows the numbers.
4. Circle: `/spellfx circle 2 9` (pentagram + skull for 4 s). In B → Grimoire use the three buttons after the hue bar, save to a slot, and hold a spell with cast time: the circle at the feet and hand uses the chosen shape, symbol and colour.
5. Aura: hold a long cast for more than 1.25 s, or meditate (sneak still) → slow rising motes. Set `channelAura = false` in `config/isekai_mahou-spellfx-client.toml` → gone.

## Not done / not verified
- **No in-game run.** Unverified visually: symbol orientation in the world, the GUI preview and pickers, aura density.
- The Grimoire colour strip now has a 100 px minimum width (was 60), so on very narrow windows the element seals collapse to "+N" sooner.
- Zones and turrets reloaded from disk keep mastery only if the caster is online at load.
- Module-built contexts that re-parse a recipe themselves (e.g. weapon seal) get no mastery bonus.
- No kill bonus XP; no first-time hint (the `ui` job's `Hints` API was not available to compile against).
- The config-screen tooltip key `isekai_mahou.configuration.channelAura.tooltip` was added on the assumption that the `ui` job's NeoForge config screen picks it up.

## PEDIDOS
1. **Coordinator**: run `./gradlew compileJava --offline` before re-running `check-module.sh ui_grimoire`.
2. **`core/CoreEvents.java`** (meditation block, ~line 96): the server still sends `ParticleTypes.ENCHANT` every 5 ticks while meditating, which the aura option cannot turn off. Suggest removing it, since `ChannelAura` now draws the meditation aura client-side.
3. **`ui` job**: confirm the config screen lists `isekai_mahou-spellfx-client.toml`; optionally add a one-time hint on the first mastery level-up.
4. **`docs/ARCHITECTURE.md`**: document `GlyphMastery`, the 4-argument `SpellParser.parse(recipe, element, data, mastery)`, and `SpellRecipe.circle()`. Modules that rebuild a recipe field by field should use the `withX` methods so the circle is kept; none do today except `GamblerLegends`, which builds from definitions.
5. **Journal/lore job**: a short guide entry explaining mastery and the per-spell circle.