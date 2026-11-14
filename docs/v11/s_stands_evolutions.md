# v1.1 — Agent S: earlier mutations, glyph evolutions, Stands (Steel Ball only)

Compiles, but nothing was run in game.
- `bash tools/check-tree.sh stands` fails only in agent T's half-edited `class_quincy/QuincyFinalForms.java` and `LetztStilForm.java` (28 errors, none in my files).
- The same whole tree **without** `class_quincy` (quincy is resolved from `build/classes`) → **COMPILOU OK (all)**, 3195 files.

## 1. Earlier mutations: the new curve

`GlyphMastery` changes:
- New classic table `{0,100,300,600,1000,1600,2500,3800,5600,8000}`.
- After level X, each step is `2500 × 1.06^n` (was `10000 × 1.08^n`).
- Every new value is ≤ the old one (checked by script), so saved XP never loses a level, it only gains.
- Examples: old X (26 000 XP) → now XV. Old ★30 → ★53. Old ★50 → ★81.
- The level cap stored in the int rises from 135 to 195.

| Level | Total XP | Enemy hits | Effect power | Mana discount |
|---|---|---|---|---|
| III | 300 | 150 | +3% | 4% |
| **IV (first mutation)** | 600 | **300** | +4.5% | 6% |
| V | 1 000 | 500 | +6% | 8% |
| VII | 2 500 | 1 250 | +9% | 12% |
| X | 8 000 | 4 000 | +13.5% | 18% |
| XII | 13 459 | 6.7k | +23.6% | 18.6% |
| XV | 22 939 | 11.5k | +33.8% | 19.4% |
| XX | 42 930 | 21.5k | +44.8% | 20.3% |
| ★25 | 69 681 | 34.8k | +52.5% | 21.0% |
| ★30 | 105 482 | 52.7k | +58.3% | 21.5% |
| ★50 | 418 118 | 209k | +73.4% | 22.7% |
| ★100 | 8.33M | 4.17M | +92.4% | 23.7% |
| ★195 (cap) | 2.12B | – | +110% | 24.3% |

The power and discount formulas are unchanged; they apply per level.

New mutation levels in `MasteryMutations`:

| Family | Mutation levels |
|---|---|
| Projectile form | fragment 4, echo 7, fragment II 10, echo II 16, swarm 25, echo III 40 |
| Area form | aftershock 4, vortex 7, aftershock II 10, area echo 16, singularity 25, aftershock III 40 |
| Close form | pulse 4, reveal 7, pulse II 10, abyssal pulse 25 |
| Harm effect | heavy 4, arc 6, residue 9, arc II 13, rupture 18, arc III 30, residue II 40 |
| Help effect | blessing 4, aegis 8, blessing II 16, grace 30 |
| Spell (average level) | halo 6, cosmic 12, eldritch 20, eclipse 35, quake 50, singular 75 |
| **Aberrations (new, endless)** | ★45 and every 5 levels up to the cap (31 in total), cycling through five kinds |

The five aberration kinds:
- **Crooked Gravity:** levitation.
- **Stuttering Time:** a hard slow.
- **Mirrored Strike:** a ghost repeat of 12% per rank, up to 50%.
- **Void Bloom:** a pull.
- **Starfall:** star strikes of 10% per rank, up to 40%.

Each repeat of the same kind is one rank higher and is shown as "II", "III" and so on. Each kind has its own 20-tick cooldown per player. Aberrations count at the higher level of the form and the effect that hit, and get the usual half damage against players and bosses.

UI and command changes:
- **Tooltip:** the mutation line now appears from level III. It shows only the last 4 mutations plus "+N".
- **Detail panel:** shows the last 6 mutations, a "+N older" line, and the "next ???" teaser as before.
- **`/mahou mastery curve`:** new levels, plus two lines about when the first mutation and the aberrations start, and how many glyphs evolve.
- **`/mahou mastery info <glyph>`:** shows every evolution stage (unreached ones as ???), the last 8 awakened mutations and the next 2 placeholders.

## 2. Glyph evolutions ("like Tusk's Acts")

The core API is `MasteryMutations.evolve(id).stage(key, level, color)…done()`, which returns `Stage(glyph, index, key, level, color)`.

Lookups:
- `stages(id)`, `stage(id, level)`, `currentStage` and `nextStage`;
- `stageOf(caster, id)`: the player's own mastery level; mobs, pedestals and legends always get 0.

Names use the keys `mastery.isekai_mahou.evo.<glyph>.<stage>` plus `.desc` and `.hint`.

How the stages are defined and run:
- **Data:** each stage's behaviour is a list of `Perk`s in `feature/stands/Evolutions.java`: DMG, ARCS, SKY, BLACK_FLAME, SHOCK, VOLLEY, PIERCE, FREEZE, BUFF and BERSERK.
- **Execution:** `EvolutionEffects` runs the perks on spell events. Effects are matched by stats identity; augments are matched by their `stats.custom(id)` mark.
- **No edits to other modules:** the legends_i and legends_j glyphs needed no changes.
- **Glyphs I own:** the legends_h glyphs with their own rules read `stageOf` in their own code.

Shown to the player:
- the stage name and colour in the tooltip, in the Glyphs tab (next to the level label and in the detail block) and in the `/mahou mastery info` output;
- the next stage as "Next evolution at level N: ??? + hint";
- on the level-up that reaches a stage: a chat line, the action bar and a totem sound;
- reaching the final stage: a server-wide broadcast;
- on cast: a short aura in the stage colour, and the stage name on the action bar at most once every 20 s per glyph;
- on hit: an impact in the stage colour.

| Glyph | Stages (level → name: effect) |
|---|---|
| boost_gear (augment) | V Boosted Gear ×1.10 · XII Balance Breaker ×1.2 + Resistance I 4 s on cast (cooldown 12 s) · XXV Juggernaut Drive ×1.35 + shock r3 + Strength/Speed 6 s, costs 2 HP and never kills (cooldown 10 s) |
| chidori | V Raikiri ×1.15 + arc to 1 enemy · XII Chidori Nagashi ×1.2 + 2 arcs + 0.5 s freeze · XXII Kirin ×1.25 + 2 arcs + sky bolt (100% in r3, cooldown 10 s, visual-only lightning) |
| amaterasu | VI Enton: black-flame ring r2 for 3 s · XVIII Kagutsuchi ×1.2 + ring r3 for 5 s |
| susanoo | VII Armored: Absorption II on cast (cooldown 30 s) · XX Perfect ×1.3 + Absorption II + Resistance I + shock r3 |
| killer_queen | V Sheer Heart Attack: a mini SHA tank (1 contact, 6 s) rolls out of each detonation; cooldown 10 s, never while you have one out · XV Bites the Dust: the same blast repeats 1.5 s later at 50% (enemies only, never in safe zones) |
| king_crimson | VI Awakened Epitaph: self radius +4; target erase ×1.5 · XVIII Erased Kingdom: self +2 s; first strike ×2 (was ×1.5) |
| ora_rush | V ORA ORA ORA: 11 punches instead of 7 · XII Star Finger: +1 nearby enemy at 50% · XXII The World: the last punch stops the target's time for 1.5 s (bosses 0.5 s) |
| gomu_pistol | V Gear 2 Jet Pistol ×1.2 · XII Gear 3 Gigant Pistol ×1.2 + shock r2.5 · XXII Gear 4 Kong Gun ×1.4 + shock r3.5 + camera shake |
| haki_armament (augment) | V Hardened ×1.12 · XV Ryou (Emission) ×1.15 + 25% armor-piercing · ★28 Full Emission ×1.2 + 35% piercing + shock |
| zoltraak | V Refined ×1.1 + 1 extra beam · XII Fern's Volley + 3 beams · XXII Zoltraak Storm ×1.15 + 5 beams (45% each) |
| full_cowl | V Shoot Style: +1 safe step · XII Forged Body: +2 steps · XX Full Bullet: +4 steps (100% safe at Circle 8+). Also applies to the One For All self-strain check. |
| detroit_smash | V Texas Smash: shock r2.5 · XVIII Detroit 100% ×1.35 + shock r4 + shake |

Balance and performance caps:
- **Damage multipliers:** count half against players.
- **Hit perks:** each glyph's hit perks fire at most once every 8 ticks per player.
- **Extra hits:** at most 3 arcs and 5 volley beams.
- **Black-flame rings:** at most 3 per player and 30 on the server.
- **Who extra damage hits:** only enemies (monsters, or mobs targeting the caster), at half against players and bosses.
- **Blocks and Peace Mode:** nothing breaks blocks, and nothing runs while Peace Mode is on.

## 3. Stands (Steel Ball class only)

The gate is `StandTriggers.canManifest`: the player's `MagicApi.get(p).path() == MagicPath.STEELBALL`, and the cast must be a real player cast (not a FakePlayer or spectator). Every other class uses the same glyphs with no stand.

On `SpellCastEvent.Post`:
1. The highest-priority JoJo glyph in the recipe picks the stand and its action.
2. The server sends one tiny packet, `StandNet.Show` (entity, stand, action, ticks).
3. King Crimson is sent **only to the caster**, so the KC invisibility isn't revealed. Every other stand goes to the players tracking the caster.
4. There is a minimum gap of 8 ticks per player.

| Glyph | Stand | Action |
|---|---|---|
| ora_rush | Star Platinum (purple, spiky hair) | barrage, in front; ghost arms on HIGH+; lasts longer with the ORA stage |
| king_crimson / epitaph | King Crimson (red, forehead face) | erase (flickering, arms out) / guard |
| killer_queen, bites_the_dust, sheer_heart_attack | Killer Queen (pink-white, cat ears) | pressing the switch / pointing |
| tusk_act1/2/3, infinite_rotation, spin_nail (by the current Act) | Tusk ACT1–4 (pink → gold; small → human-size with star and helmet; spiral "nail" aura) | pointing; Act 4 slams |
| ball_breaker | Ball Breaker (steel-gold, helmet) | slam |
| steelball_love_train, `lk_d4c` | D4C (blue, long ear-horns) | arms spread |
| `lk_return_to_zero` ("Ponto Zero (Réquiem)") | Gold Experience Requiem (gold) | slam |

Unknown ids containing "d4c", "dirty_deeds", "requiem", "gold_experience", "ponto_zero" or "return_to_zero" are matched by name. Other modules can call `StandTriggers.register(id, stand, action, ticks, priority)`.

How it is drawn (`feature/stands/client`):
- **Model:** a translucent humanoid (`StandModel`, its own layer definition) with per-stand parts: ears, horns, crest, hair, helmet and the Tusk star.
- **Colour:** tinted per stand, full-bright, alpha at most 0.55, fading in and out.
- **Position:** it floats behind and to the right, and slides in front of the user for barrage, point and slam.
- **First person:** it sits low to the right at 0.32 alpha, so it does not cover the crosshair.
- **Where it is drawn:** in `RenderLevelStageEvent.AFTER_PARTICLES` with a fresh PoseStack, the same pattern as `CorpseMarker` and `StatusWorldRenderer`.
- **Limits:** at most 8 stands drawn per frame, only within `FxQuality.range()`. The Tusk and KC dust uses `FxQuality.count`. There is no HUD or text.
- **Texture:** `textures/entity/stands/stand.png`, 64×64, generated with a throwaway Java script: greyscale panels and eyes that take the tint.

**"7 páginas":** nothing in the code or lang files is called "7 pages" or "Seven Pages". The closest match is Ball Breaker, whose unlock is "Lição V + **7 Partes**", so it gets its own stand. If he meant something else, adding it is one `register(...)` line in `StandTriggers`.

## Files
- **New, in `feature/stands/`:**
  - `StandsModule`, `Stand`, `StandNet`, `StandTriggers`, `Evolutions`, `Perk`, `EvolutionEffects`;
  - `client/StandsClient`, `client/StandModel`, `client/StandRenderer`.
- **New resources:**
  - `src/main/lang/stands/{pt_br,en_us}.json` (94 keys each);
  - `assets/isekai_mahou/textures/entity/stands/stand.png`.
- **Edited:**
  - `glyph/GlyphMastery.java`: curve, evolution announcements, curve and info commands;
  - `glyph/MasteryMutations.java`: levels, aberrations, the evolution registry and `stageOf`;
  - `ui_grimoire/client/GlyphIcons.java` and `GlyphsTab.java`: mastery display only;
  - in `legends_h`: `glyph/JojoGlyphs.java` (King Crimson and ORA stages), `jojo/TimeSkip.java` (`setFirstStrike`), `jojo/Bombs.java` (Killer Queen stages), `entity/SheerHeartAttackEntity.java` (`hasActive`), `mha/FullCowl.java` (`safeFor` and `safeStepFor`), `glyph/MhaGlyphs.java` and `Augments.java` (use `safeFor`).
- **One line each:** `module/Modules.java` and `client/ClientModules.java`.
- **Lang:** `core_spells` (+21 keys) and `ui_grimoire` (+4 keys), pt_br and en_us. No key is duplicated across modules.

## How to test
- **Curve:** `/mahou mastery curve`, then `/mahou mastery info isekai_mahou:chidori`.
- **Evolutions:**
  1. `/mahou glyphs grant legends_h` (and grant the legends_i and legends_j glyphs).
  2. `/mahou mastery set isekai_mahou:chidori 22`.
  3. Hit a group of zombies: expect arcs, a 0.5 s freeze and a sky bolt every 10 s. The tooltip and Glyphs tab should read "⟡ Kirin (3/3)".
  4. Try `/mahou mastery add isekai_mahou:zoltraak 2000` from level IV: expect the "EVOLVED: Refined Zoltraak" chat line.
- **Killer Queen:** `/mahou mastery set isekai_mahou:killer_queen 15`, then plant a bomb on a mob. Expect a mini SHA tank from the blast and a second pink blast 1.5 s later.
- **Full Cowl:** `/mahou mastery set isekai_mahou:full_cowl 20` at Circle 8. Full Cowl with 4 Amplify (100%) should no longer break your body.
- **Stands:**
  1. Pick the Steel Ball class and cast ORA, King Crimson (self), Killer Queen, Tusk Acts and Love Train.
  2. On another class, the same glyphs should show no stand.
  3. To preview the visuals on any class: `/mahou stand <star_platinum|king_crimson|killer_queen|tusk_act1..4|ball_breaker|d4c|gold_experience_requiem> [barrage|point|switch|erase|spread|slam|guard]` (op).

## Not verified (no in-game run)
- **Stand rendering:** pose-stack and camera assumptions, translucency sorting against particles, the first-person placement, and how good the tinted texture and extra parts look.
- **Evolution perks:** feel and balance in game; whether the Kirin visual-only lightning sound is too loud.
- **Killer Queen evolution:** whether the SHA it spawns behaves sensibly. It replays the whole recipe on contact, capped by the 10 s cooldown.
- **Existing worlds:** old saves jump levels (for example X becomes XV), so some old glyphs suddenly have many mutations and aberrations. No chat spam, because announcements happen only on a real level-up.
- **Guide text:** `story` lang `guide.isekai_mahou.glyph_mastery.how` still describes mutations as coming "after X". It is not my module.
