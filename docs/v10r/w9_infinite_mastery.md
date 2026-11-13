**w9 — infinite glyph mastery.** Mastery no longer stops at X. Levels keep going (XI, XII … XX, then "★ 21", "★ 57"), each level costs more than the one before, power keeps rising on a log curve, and at marked levels the spell **mutates** (it changes how it behaves and looks). The full tree compiles. Nothing was run in-game.

## Checks
- `bash tools/check-tree.sh mastery_inf` → **COMPILOU OK (all)**, 3092 files.
- A throwaway JVM test of `GlyphMastery` passed (since deleted):
  - Old XP values map to exactly the same levels as before: 0 → I, 100 → II, 25 999 → IX, 26 000 → X.
  - `level(Integer.MAX_VALUE)` = 135 (the cap), and the progress bar there is 1.0.
  - Roman numerals run up to XX, then "★ N". Compact numbers display correctly (152.3k, 137.44M).
- All 6 lang files edited (pt_br + en_us for core_spells, ui_grimoire and story) are valid JSON, and no new key appears in more than one module.

## Save compatibility
- The save key is unchanged (`extra.glyph_mastery`, still a map of `int` values).
- Classes I–X keep exactly the same XP table, so existing saves load unchanged. A level-X glyph stays at X and keeps gaining XP; the old 26 000 XP cap is gone.
- XP is capped at `Integer.MAX_VALUE`. That is level 135, more than a billion uses, so in practice nobody reaches it. Additions use long arithmetic, so the number cannot wrap around.

## XP curve
- The step from X to XI is `10 000 × 1.08`. Every step after that is ×1.08 the previous one (`GlyphMastery.GROWTH`).
- "Uses" below means landed enemy hits at 2 XP each. The anti-farm rules are **unchanged**:
  - XP is only gained when an effect really lands.
  - Each glyph gains at most once per 40 ticks (100 ticks for block work).
  - Casting into the air gives nothing.
- I kept the per-glyph time window instead of counting once per cast:
  - The window already makes "one use" roughly one landed cast for normal spells.
  - Counting per cast would let fast or channelled spells farm XP and would speed up the classic I–X ladder, which existing saves were balanced on.
- "Hours" is the absolute minimum: one landed use every 2 s, non-stop.

| Level | Total XP | Step to next | Uses (enemy hits) | Hours at max rate |
|---|---|---|---|---|
| X (10) | 26 000 | 10 800 | 13 000 | 7.2 |
| XV (15) | 89 359 | 15 869 | 44 679 | 24.8 |
| XX (20) | 182 454 | 23 316 | 91 227 | 50.7 |
| ★30 | 520 228 | 50 338 | 260 114 | 145 |
| ★50 | 2 823 810 | 234 625 | 1.41 M | 784 |
| ★100 | 137 444 532 | 11.0 M | 68.7 M | 38 000 |
| ★135 (cap) | 2 033 667 401 | – | 1.02 B | – |

What "10 000 uses" means in practice: 10 000 enemy hits is 20 000 XP, which is level IX (+12% power, −16% mana). A fresh learner is at I. Levels past X then add the bonuses and mutations below, which a newcomer cannot get without the same amount of use.

## Bonus curve
Player level only adds spell power above level 100. That means a veteran of one glyph at a lower player level out-damages a higher-level newcomer using the same glyph.

| Mastery | Effect power | Power vs players | Mana discount |
|---|---|---|---|
| X (10) | ×1.135 | ×1.135 | 18.0% |
| XX (20) | ×1.448 | ×1.292 | 20.3% |
| ★50 | ×1.734 | ×1.435 | 22.7% |
| ★100 | ×1.924 | ×1.530 | 23.7% |
| ★135 | ×2.003 | ×1.569 | 24.0% |

- **Power after X:** `+0.25·ln(1 + (L−10)/4)`. It has no ceiling but grows ever more slowly, and is only about ×2 at the absolute cap. For comparison, one Amplify augment is +40%.
- **Mana discount after X:** `0.18 + 0.07·(1 − 1/(1 + (L−10)/20))`. It approaches 25% but never reaches it.
- **PvP:** a `SpellDamageEvent.Pre` listener counts the part of the power earned after X at half strength when the target is a player.
- **Damage caps:** the mod has no global cap on spell damage. The only cap I found is `ArmoryArrows.ARCANE_POWER_CAP`, which applies to arrows only. Sanity is kept by the log curve, by the 50% PvP rule above, and by mutation damage being halved against players and bosses.

## Mutations
- **Which level counts:**
  - Form mutations use the form glyph's level.
  - Damage and help mutations use the level of the effect glyph that caused the hit.
  - "Spell" mutations use the average level of the form and effects across all stages (the same rule as `visualTier`).
- **Determinism:** every mutation is fixed by glyph family and level. There is no randomness.
- **Families:** `MasteryMutations.family(glyph)` decides the family from:
  - `stageable` and `hintRange`, for projectile forms;
  - `hintRadius` and `isPersistent`, for area forms;
  - ultimate or law forms, which count as close forms;
  - `isBeneficial`, `baseDamage` and `wantsBlocks`, for effects.
- **Glyphs with no mutations:** augments, triggers, links and laws only get the cost discount. Block-work effects only get the spell visuals.

| Family | Levels → mutation |
|---|---|
| Spell (average; visuals) | 11 Awakened Circle (rune ring, colour shift) · 20 Cosmic Circle (star ring, blue) · 35 Eldritch Circle (second green circle, dark wave) · 50 **Eclipse** (dark screen tint for players within 32 blocks, ~sky darkening; every 10 s) · 75 The World Trembles (camera shake within 24 blocks; every 5 s) · 100 Singular (40-block white pillar; every 5 s) |
| Projectile / beam form | 12 Fragment (+1 ghost projectile at ±8°, 30% power) · 18 Delayed Echo (recast from the same spot 1.5 s later, 35%) · 25 Fragment II (+2) · 40 Echo II (50%) · 60 Swarm (+3) · 100 Endless Echo (2 echoes, at 1.5 s and 3 s) |
| Area form | 12 Aftershock (burst of 20% of the hit, radius 3, 0.6 s later) · 18 Vortex (pulls enemies within 5 blocks; bosses are skipped) · 25 Aftershock II (2 bursts) · 40 Area Echo (35%; stageable, non-persistent forms only) · 60 Singularity (vortex radius 8, stronger) · 100 Final Aftershock (30%) |
| Close form (touch, self, domain, ultimate) | 12 Pulse (pushes enemies within 3 blocks away, no damage) · 18 Revelation (enemies within 12 blocks glow for 4 s) · 25 Pulse II (radius 5 + Slowness) · 60 Abyssal Pulse (radius 7, strong push, Slowness II) |
| Damage effect | 11 Heavy Impact (camera shake on hit) · 15 Arc (25% jumps to 1 enemy within 6 blocks) · 22 Residue (3 ground pulses of 8%, radius 2) · 30 Arc II (2 enemies) · 50 Rupture (a kill bursts for 50% within 4 blocks) · 75 Arc III (3 enemies, 30%) · 100 Eternal Residue (6 pulses of 10%) |
| Help effect | 15 Echoing Blessing (Regeneration I for 3 s to you and allies within 6 blocks; every 10 s) · 30 Aegis (+Absorption for 5 s; every 20 s) · 50 Blessing II (Regeneration II, 8 blocks) · 100 Grace (+Resistance for 3 s) |

- **How echoes and fragments are cast:** they use `SpellCaster.castFrom(..., postEvents=false)`. They cost no mana, give no mastery XP and cannot trigger mutations again, because they are not player casts.
- **When echoes and fragments are skipped:**
  - channelled spells;
  - ultimate, domain and law forms;
  - casts where the player has died, changed dimension or turned on Peace Mode before the echo fires.

## Performance and safety caps
- **Echoes and fragments:** at most 6 extra casts per player per 40 ticks (`ECHO_BUDGET`/`ECHO_WINDOW`). At most 3 fragments and 2 delayed echoes per cast.
- **Hit mutations:** each one has its own per-player cooldown, so a rain spell hitting 30 targets does not produce 30 aftershocks.

  | Mutation | Cooldown per player |
  |---|---|
  | Aftershock | 10 ticks |
  | Vortex | 20 ticks |
  | Arc | 6 ticks |
  | Residue | 20 ticks |
  | Heavy Impact | 10 ticks |
  | Rupture | 10 ticks |
  | Pulse | 20 ticks |
  | Revelation | 100 ticks |
  | Spell visuals | 10 ticks |

- **Arcs and residues:** at most 3 arcs per hit (`MAX_ARCS`). At most 3 active residues per player and 40 on the server.
- **Who extra damage can hit:** only `Enemy` mobs or mobs targeting the caster. Never players, allies, villagers, animals or armour stands.
  - The one exception is the aftershock: it also hits the original target, even a player, at half damage.
  - Mutation damage is halved against players and bosses (`BossKit.isBossLike`).
- **Blocks:** no mutation changes blocks, so this does not interfere with the indestructible tower/dungeon work.
- **Visuals:** everything goes through `ElementFx` (FxBatch/FxBurst, assembled on the client according to FxQuality), `ElementFx.shake` (at most 2 per tick, scaled by `FxQuality.shake()`) or `BossKit.flashNear`. I added no raw particle spam and no new network packets.

## UI and messages
- **Glyphs tab, detail panel:**
  - level label and progress bar;
  - compact progress text ("Towards level XII: 1.2k/11.7k");
  - current bonus;
  - a line explaining infinite mastery once past X;
  - awakened mutations with descriptions;
  - **"Next mutation at level XVIII: ???" plus a vague hint** (for example "do you hear your spell twice?").
- **Glyph tooltips** (Glyphs tab and Grimoire palette):
  - the existing head and bonus lines;
  - one compact line of mutation names plus the teaser, shown from level X on, or always in the extended tooltip.
- **Collection bar:** below X it shows the path from I to X. From X on it refills every level, in band colours: gold 10–19, cyan 20–34, green 35–99, crimson 100+.
- **On level-up:**
  - The usual action bar (cyan text and a lower-pitched sound after X).
  - Each newly awakened mutation adds one chat line for that player only, with the description and a portal-frame sound.
  - At levels 25, 50, 75 and 100 the **whole server** gets a chat line: "✦ X took mastery of Y to level ★50. Something in the world shifts."
  - The HUD is not touched.

## Commands
| Command | Who | What changed |
|---|---|---|
| `/mahou mastery set <glyph> <1..135>` | op | now accepts any level up to the cap |
| `/mahou mastery all <1..135>` | op | now accepts any level up to the cap |
| `/mahou mastery add <glyph> <±2 000 000 000>` | op | now fires the same level-up, mutation and legend messages as real use |
| `/mahou mastery curve` | any player | new: prints the curve table |
| `/mahou mastery info <glyph>` | any player | new: level, mutation family, awakened mutations and "???" placeholders for the next ones |
| `/mahou mastery list` | any player | progress now uses compact numbers |

## Files
- **New:**
  - `src/main/java/com/renanpontes/isekaimahou/glyph/MasteryMutations.java`: mutation table (client and server) plus the server listeners `SpellCastEvent.Post`, `SpellDamageEvent.Pre` and `SpellDamageEvent.Post`.
- **Edited:**
  - `glyph/GlyphMastery.java`: generated infinite table, binary-search `level`, new `costDiscount`/`powerMult`, `powerMultPvp`, `roman` with ★, `compact`, `averageLevel`, mutation and legend announcements, commands.
  - `feature/ui_grimoire/client/GlyphIcons.java`: colour bands, bar, `masteryProgressText`, `mutationLines`, tooltip lines.
  - `feature/ui_grimoire/client/GlyphsTab.java`: the mastery block of the detail panel only.
- **Lang (pt_br + en_us):**
  - `src/main/lang/core_spells/`: 111 new keys (messages, commands, family names, mutation name/desc/hint).
  - `src/main/lang/ui_grimoire/`: 3 new keys, and new wording for `mastery.max` and `mastery.maxed`.
  - `src/main/lang/story/`: `guide.isekai_mahou.glyph_mastery.how` rewritten.
- **Not touched:**
  - `SpellContext`, `SpellParser`, `SpellCaster`, `SpellVisuals`, `MagicData`;
  - `GrimoireTab` (the Transformation editor is intact);
  - settlements and cities.

## How to test in game
1. `/mahou mastery curve`, then `/mahou mastery info isekai_mahou:projectile`.
2. **Projectile form:** `/mahou mastery set isekai_mahou:projectile 25` and cast a projectile spell at mobs. Expect 2 ghost projectiles at ±8°, and after 1.5 s a faint flash where you cast with a 35% repeat.
3. **Damage effect:** `/mahou mastery set isekai_mahou:arcane_damage 30` and hit a group of zombies.
   - Expect: a small camera shake, arcs to 2 nearby zombies, and a residue ring that pulses 3 times.
   - Villagers and players nearby must take nothing.
4. **Spell visuals:** `/mahou mastery all 50`, then cast anything. Expect the cosmic, eldritch and green circles plus a dark screen tint for about 2.5 s (at most once every 10 s). `/mahou mastery all 100` adds the shake and the pillar.
5. **Messages:**
   - `/mahou mastery set isekai_mahou:projectile 10`, then `/mahou mastery add isekai_mahou:projectile 2000000`. Expect the action bar, mutation chat lines for 12/18/25… and server-wide legend lines at 25.
   - Open B → Glyphs: expect the ★/roman label, the band-coloured bar, the mutation list and the "Next mutation at level …: ???" teaser.
6. **PvP:** in a duel at mastery 50 the damage gain past X should be about half (×1.43 instead of ×1.73).
7. **Old save:** load a world where a glyph is at X. It should still show X, and the bar should now fill toward XI.

## Not verified
- **No in-game run.** None of these has been seen working:
  - echo/fragment aim for every projectile and beam form (some forms may ignore the `castFrom` origin or direction);
  - how strong the vortex push looks;
  - the eclipse tint;
  - text clipping in the detail panel at high levels with 10+ mutation lines (lines past the bottom are cut off).
- **Families are classified from glyph hints.** A module form with missing hints (no radius or range) falls into the close family, so it gets Pulse instead of Fragments.
- **Effects that never call `ctx.damage`** (pure status or control effects) are classed as damage effects but never trigger hit mutations. They still get the spell visuals.
- **Echoes still feed ultimate charge** through `MagicApi.onSpellDamage`, because the caster is the player. The amounts are small, but this is a minor exploit vector worth watching.
- **The guide and journal text** was only updated in `story` lang. The journal entry has no new section.
