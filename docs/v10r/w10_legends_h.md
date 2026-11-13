# Wave 10 — `legends_h`: anime glyphs (JoJo, DxD, MHA, Mob, DB, JJK)

Owner request (2026-11-13): "time erase (Diavolo), time rewind (Kira), Boost Gear, Deku, and more".
**Direction change mid-task:** the owner does not want legendary spells. Everything here is an **ordinary Grimório glyph**
(17 glyphs: 12 effects, 4 augments, 1 form). No `Legends.register`, no unlock feats, no scrolls handed out by the module.
`isLegendary()` is false, `origin` is null, and tier/cost/minCircle are normal, so the glyphs work with any compatible form,
mastery and mutations.

## How players get them
This uses the normal acquisition path (no special code):
- **Arcane** glyphs: taught by the Arcane path when the player's level unlocks the tier (`PathProgress.teachArcane`; T3 → level 10, T4 → level 18) and their Circle reaches `minCircle`.
- **Fire/Wind/Shadow** glyphs: taught by that element's path nodes (`PathRules.tierGlyphs`).
- **All of them** also appear in the shared random pools: Wandering Mage stock (`sellableGlyphs`), guild rewards (`GuildService.randomGlyph`), journal, reputation and level milestones. Those pools pick any glyph that has no origin, isn't legendary and has the matching tier.

## Glyphs
| id | kind | element | tier / C | cost | what it does (base numbers; power, mastery and augments scale them) |
|---|---|---|---|---|---|
| `king_crimson` | effect | Arcane | 4 / C5 | 30 | **Self:** you are outside time for 2.5 s (Extend+, max 7 s). You are invisible and not rendered for other players, mobs drop you as a target, and attacks and projectiles pass through you. You can't deal damage. When it ends: a flash where you reappear, your first hit within 3 s deals ×1.5, and a 3 s Epitaph forecast on mobs within 16 blocks. **Target:** its time is erased for 2 s (bosses 1/3, players ≤2.5 s). It keeps moving but can't target anyone and its damage is cancelled; it leaves afterimage particles. Players get a red/purple screen tint, with no input lock. Lava, falls and drowning still damage a phased player. |
| `epitaph` | augment | Arcane | 3 / C4 | 6 | Each target the spell damages is "foreseen" for 6 s (effect Extend+). Particles visible only to you show its next-second path, plus a red line when it targets you. An action-bar line tells you which side the attack comes from. Its first attack on you is cancelled and you sidestep. |
| `bites_the_dust` | effect | Arcane | 4 / C5 | 28 | Marks a target's time. After 8 s (Extend+, max 30 s) it is pulled back to where it was marked. **Self/ally:** heals the health lost since marking (cap 40% of max × power, never above 60%), restores hunger and up to 30% mana (caster only). If a marked self/ally would die, the death is cancelled and the rewind happens at once (once per 2 min per entity; void and `/kill` still kill). **Foe:** moved back and stunned 1 s; its health is NOT restored, bosses aren't moved. Position only rewinds within the same dimension and the same `ArenaBounds` arena. Kills, items, blocks and XP are never undone. Visuals: sepia tint, backwards clock (reuses the legends_d clock texture), pink trail back along the path. |
| `killer_queen` | effect | Fire | 3 / C3 | 16 | Touch-bomb. **Creature:** explodes after 3 s (Extend+), or at once when the caster melee-hits it: 9 to it, 4.5 splash in r2.5 (Expand+). **Block:** a mine for 20 s that triggers when a foe comes within 1.6 blocks; small crater through `Destruction.crater`, which respects the game rule, cities, towers and dungeons. **Self:** mines the block you look at (≤6 blocks). Max 3 bombs per caster; nothing explodes in safe zones; only the owner sees the pink glow. |
| `sheer_heart_attack` | **form** | Fire | 4 / C4 | 22 | Releases an indestructible ground tank that hunts the hottest foe within 24 blocks (burning > fire-immune > players > others). On contact it applies the spell's effects (`resolveOnEntity`) with an explosion, then keeps hunting. 3 contacts (Split +1 each, max 6) or 15 s (Extend+, max 40 s); Accelerate makes it faster. Cost counts 3–6 copies. One tank per caster; it only follows the owner while the owner is in Peace Mode or a city. Rendered from vanilla blocks plus a skeleton skull. |
| `ora_rush` | effect | Arcane | 3 / C3 | 14 | Star Platinum barrage: 7 hits of 1 plus a final hit of 3 with knockback, over 0.8 s. The target's i-frames are reset per hit. **Self:** hits the nearest foe in front within 5 blocks. Stops if the caster moves more than 8 blocks away. |
| `boost_gear` | augment | Arcane | 3 / C3 | 8 (+0.25 s cast) | "Boost!": re-cast a Boost spell within 2.5 s, or keep channelling, to step up the power multiplier: ×1 → 1.25 → 1.5 → 2 → 3. Cap by Circle: C1–3 ×1.5, C4–5 ×2, C6+ ×3; mobs cap at ×1.25. A pause resets it. The action bar shows "[Boost Gear] Boost! ×N", then "Explosion!" at the cap. |
| `boost_transfer` | effect (beneficial) | Arcane | 3 / C3 | 10 | The target (ally or self) gets `lh_boosted` for 15 s (Extend+): +15% melee and spell damage per level. Level = 1 + the caster's current Boost step + Amplify (max 5). Resets the caster's Boost step. |
| `balance_breaker` | effect (beneficial) | Fire | 4 / C5 | 26 | Scale Mail for 20 s (Extend+, ×1.5 if the caster is at Boost step 2+): +8 armor, +4 toughness, +3 attack, +10% speed, +0.5 knockback resistance. Weakness for 5 s afterwards. Red aura and a big banner. |
| `full_cowl` | effect (beneficial) | Arcane | 3 / C3 | 14 | One For All for 30 s (Extend+, max 120 s) at the caster's **safe %** by Circle (C1–3 5, C4 8, C5 15, C6 20, C7 30, C8+ 45). Each Amplify climbs one rung of the ladder 5/8/15/20/30/45/60/80/100%. Transient attribute bonuses scaled by p: speed +(8+50p)%, attack +(1+10p), attack speed +30p%, jump, safe-fall, step height from 20%, knockback resistance. Above safe: non-lethal self-damage every 2 s; at 25% HP it drops back to safe with a broken arm (Weakness II + Mining Fatigue). Green spark particles. |
| `one_for_all` | augment | Arcane | 3 / C3 | 6 per copy | 1/2/3 copies = 20/45/100% → spell power ×1.3/×1.68/×2.5. After the cast, if the % is above safe (+10% with Full Cowl active): non-lethal self-damage of (over × 30% of max HP) and a broken arm for 5–20 s. |
| `delaware_smash` | effect | Wind | 2 / C2 | 10 | 4 damage and a huge push away from the caster (bosses barely move); One For All adds push. **Self:** "Air Force" air bullet along your look (20 blocks) that hits the first 3 foes. |
| `detroit_smash` | effect | Wind | 4 / C5 | 22 | 8 damage and launches the target up and away. The shockwave (r3, Expand+) hits other foes for half damage. With One For All 3 copies (100%) it becomes **United States of Smash**: double radius, ×1.5 damage, screen shake and a wind pillar. **Self:** the impact lands 3 blocks ahead. No terrain damage. |
| `blackwhip` | effect | Shadow | 2 / C2 | 10 | Pulls the target to the caster (bosses only slowed), 2 damage, Slowness III for 2 s. **On a block** (≥3 blocks away): grapples the caster to it, with slow-fall for 3 s. **Self:** whips the first foe in sight (20 blocks) or grapples the block you look at (24 blocks). Twisting black tendril particles. |
| `psycho_hundred` | effect | Arcane | 4 / C5 | 24 | Mob Psycho: lifts the target, then 0.7 s later slams it down for 4 + 0.05 × emotion. Emotion = 10 + 90 × the caster's missing HP fraction. At ≤25% HP it becomes **"???%"**: ×1.4 damage, bigger area, and the caster gets Slowness + Weakness for 6 s two seconds later. Bosses aren't lifted and take 70%. **Self:** burst in r 4 + 0.04 × emotion, up to 10 foes. |
| `genki_dama` | augment | Arcane | 4 / C5 | 12 (+1.5 s cast) | Gathers energy within 32 blocks: allied players 3, owned allied pets 2, animals and villagers 1 (max 30 points). Power ×(1 + 0.05 per point), up to ×2.5 (mobs ×1.2). +1 Expand, and the spell is forced to enemies-only (spell damage to non-foes is cancelled). Contributors get an action-bar notice; particle lines run from them to a sphere above the caster. |
| `cursed_speech` | effect | Shadow | 3 / C3 | 14 | Inumaki. Default: **"Don't move"** stuns for 1.5 s (Extend+; bosses 1/3, players ≤1.5 s). With Amplify: **"Blast away"** deals 6 damage and pushes. Cost to the caster: Silenced for 1.5 s (3 s for Blast) and 1 (2) HP of non-lethal self-damage. **Self:** every foe within 8 blocks hears it (max 8). |

Status effects (icons in `textures/mob_effect/`): `lh_full_cowl`, `lh_scale_mail`, `lh_boosted`, `lh_rewind_mark`, `lh_time_skip`. Entity: `lh_sheer_heart_attack` (`noSave`).

## Rules respected
- Targets use `LfUtil.isFoe` / `ctx.isAlly`, so Peace Mode, PvP off, parties, duels and city wars apply as in legends_f/g.
- No terrain damage outside `Destruction` (Killer Queen block mine only). Killer Queen and Sheer Heart Attack deal no damage in `Places.isSafe` zones, and the tank stays calm there and in Peace Mode.
- Self-damage (`LhUtil.strain`) never kills. Every crowd-control effect is shortened for bosses and capped for players. No instant kills.
- Bites the Dust can't move anyone across dimensions or arenas. Teleports go through `ArcaneUtil.teleport`, so `EntityTeleportEvent` can block them (e.g. the No-Escape domain).
- Particle counts are capped (afterimages ≤12 mobs, tendrils ≤60 points, lines ≤24 points). Screen effects are tints and vignettes only, with no input lock.

## Files
- `src/main/java/com/renanpontes/isekaimahou/feature/legends_h/`: `LegendsHModule`, `LhRegistry`, `LhNet`, `LhUtil`, `LhIds`, `Augments` (cast/damage hooks for Boost / One For All / Spirit Bomb / Epitaph / Transfer)
  - `jojo/TimeSkip` (King Crimson + Epitaph), `jojo/Rewind` (Bites the Dust), `jojo/Bombs` (Killer Queen), `mha/FullCowl`
  - `glyph/JojoGlyphs`, `glyph/DxdGlyphs`, `glyph/MhaGlyphs`, `glyph/OtherGlyphs`, `glyph/SheerHeartAttackForm`, `entity/SheerHeartAttackEntity`
  - `client/LegendsHClient`, `client/LhScreenFx` (tints, backwards clock, hides phased players), `client/SheerHeartAttackRenderer`
- `src/main/lang/legends_h/{pt_br,en_us}.json` (82 keys each, no duplicates with other modules).
- `tools/gen/legends_h_icons.py` → 17 glyph icons (`textures/gui/glyph/<id>.png`) + 5 effect icons.
- One line each added to `module/Modules.java` and `client/ClientModules.java` (after legends_g).

## Test
- Compile: `bash tools/check-tree.sh legends_h` (passes for the whole tree) and `bash tools/check-module.sh legends_h` (passes, including the JSON and asset checks).
- Icons: `py -I tools/gen/legends_h_icons.py --preview build/lh_preview`
- In game (op): `/mahou glyphs grant legends_h`, then build recipes in the Grimório, e.g.
  - `self + king_crimson` · `projectile + king_crimson` · `projectile + arcane_damage + epitaph`
  - `self + bites_the_dust + extend` · `projectile + bites_the_dust`
  - `touch + killer_queen` · `sheer_heart_attack + explosion` (Fire spell)
  - `projectile + arcane_damage + boost_gear` (cast repeatedly) · `self + boost_transfer` · `self + balance_breaker`
  - `self + full_cowl + amplify + amplify` · `touch + detroit_smash + one_for_all ×3` (Wind spell) · `self + delaware_smash` · `self + blackwhip`
  - `self + psycho_hundred` at low HP · `projectile + arcane_damage + genki_dama` near animals · `self + cursed_speech` / `+ amplify`

## Not verified (nothing was run in game)
- In-game behaviour, balance and visuals of all 17 glyphs. Only javac and the JSON/asset checks were run.
- The Sheer Heart Attack tank's pathing (step-up, water, getting stuck) and how its block-built model looks.
- Whether hiding phased players with `RenderPlayerEvent.Pre` also hides held items and armor correctly with other render mods (e.g. animation layers).
- Interaction with legends_d Time Stop: no special handling, so a phased player inside a frozen zone is untested.
- The order of `SpellCastEvent.Pre` listeners: our multiplier runs at LOW priority. If another module cancels the cast, no Boost step is consumed; this wasn't tested.
- Bites the Dust death-save against mods that also cancel `LivingDeathEvent` (e.g. Chainsaw Devil / afterlife).
