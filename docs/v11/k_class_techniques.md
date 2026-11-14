# legends_k — anime techniques in the classes (agent K, 2026-11-13)

Owner request: "vamos adicionar técnicas dos animes nas classes?" — Kamui/Valentine's dimension, Spiritual Pressure,
Titan Mode, Giorno's Return to Zero, Orihime, Naoya's frames, Accelerator, plus more creative ones, all light.

Every technique is an ordinary **EFFECT glyph** (Titan Mode is a **Transformation body**) with `origin(MagicPath.X)`:
only that class learns it on its own, when it reaches the listed **Circle** (`LkUnlocks`, checked on level-up, Circle change,
class choice and login, with a chat line). Other classes can only learn it through a Lesson, which the core already handles.
Glyph mastery and mutations work automatically. Ids use the `lk_` prefix so they can't collide with the parallel agents' ids.

## Glyphs (19)

| id | Anime | Class | Kind / element | Tier · Circle · cost | What it does |
|---|---|---|---|---|---|
| `lk_kamui` | Naruto (Obito) | Rogue | Effect · Void | T4 · C4 · 22 | Self: toggle the pocket dimension. Blue screen tint (client), intangible both ways (hits, projectiles, spells, effects from others), mobs drop you as a target. Exits after 2 min, on a quick double-sneak (½ s), or on recast. Weak mob (≤40 max HP, +25%/Amplify): sucked in for 8 s (Extend), then returns to the same spot, slowed. Strong mob/boss: 5 dmg + Slowness. Enemy player (PvP): trapped 5 s (no sneak exit). Does nothing to others while you're inside. |
| `lk_d4c` | JoJo SBR (Valentine) | Steel Ball | Effect · Void | T4 · C5 · 24 | Same core as Kamui with a light-blue tint. Weak mob (≤50): 3 dmg, stashed 5 s, returns with Weakness + Slowness. Strong: 7 dmg + Slowness III. PvP trap 3 s. |
| `lk_spiritual_pressure` | Bleach (reiatsu) | Shinigami | Effect · Shadow | T3 · C3 · 18 | Pressure = (10 + 1.2×level) × power. Self: 10 s aura, radius 6, pulses every ½ s, max 12 targets. A target with max HP ≤ ½ the pressure kneels (Rooted, Slowness V, Weakness II). ≤ the pressure: Slowness II + Weakness. ≤ 2× the pressure: Slowness I. Bosses get Slowness I only. Players are affected only in PvP, and only with Slowness I. On a target: the same rule for 5 s. Visual: a dark-red ground shockwave each second. |
| `lk_titan_mode` | Attack on Titan (War Hammer) | Smith | **Transformation body** (`BodyEffect`) · Earth | T4 · C5 · 26 | Inside Transformação only, 30 s (Extend). Transient attribute modifiers: scale ×2.2, +20 max HP (healed on start), +2.5 entity reach, +2 block reach, +4 damage, +0.6 knockback resistance, +1 step height, +6 safe fall, −15% speed. Melee hits stomp: 40% splash within 2.5 blocks, at most every 15 ticks; only hits monsters and whoever is fighting you. Steam while active, a big steam burst on exit. Cutscene style `beast`. Learning the body auto-teaches the Transformation form (ForgeBodies). |
| `lk_return_to_zero` | JoJo (GER) | Steel Ball | Effect · Light, beneficial | T5 · C6 · 30 | Arms you or an ally for 20 s (Extend). The next attack or harmful effect that comes from anyone is cancelled: damage, projectile (discarded), spell, or harmful MobEffect. The attacker is Stunned 2 s (bosses 0.75 s), loses its target and is pushed back. One use. The caster is locked out for 60 s: while locked, the cast is cancelled in `SpellCastEvent.Pre`, so no mana is spent. |
| `lk_santen_kesshun` | Bleach (Orihime) | Enchanter | Effect · Light, beneficial | T2 · C2 · 14 | Triangle shield on you or an ally: blocks the next 3 hits from anyone (+1 per Amplify) for 15 s. Arrows are discarded. |
| `lk_soten_kisshun` | Bleach (Orihime) | Enchanter | Effect · Light, beneficial | T3 · C3 · 16 | Heals 6, removes all harmful effects and fire, repairs 8% durability (×power, max ×2) on every equipped slot. |
| `lk_koten_zanshun` | Bleach (Orihime) | Enchanter | Effect · Light | T3 · C4 · 14 | One target per cast: 8 dmg, strips absorption and the target's strongest beneficial effect. |
| `lk_projection` | JJK (Naoya) | Gunslinger | Effect · Wind | T3 · C3 · 16 | Self: dash in frames, 4 steps of 1.75 blocks every 2 ticks (+2 steps per Extend); stops at walls and at warded borders. Foes you touch get "framed". On a target: 4 dmg. Weak (≤30): Stunned 1.5 s (+0.5 s/Amplify) with a particle frame panel. PvP 0.75 s. Strong: Slowness. |
| `lk_vector_reflect` | Index (Accelerator) | Mage | Effect · Wind, beneficial | T3 · C3 · 16 | 4 s (Extend): projectiles are reversed (`ProjectileDeflection.REVERSE`, ×1.25 speed, owner = you), knockback on you is cancelled, melee damage −40% and the attacker is thrown back. |
| `lk_vector_punch` | Index | Mage | Effect · Wind | T2 · C2 · 12 | 7 dmg + push of 2.2 (+0.4/Amplify) along your look direction (bosses ×0.25). |
| `lk_vector_burst` | Index | Mage | Effect · Wind | T4 · C4 · 20 | At the target, or at you with Self: pulls foes within radius 4 inward, then after 7 ticks blasts them out: 5 dmg + push. Max 16 foes. |
| `lk_shadow_toad` | JJK (Megumi, Ten Shadows) | Necromancer | Effect · Shadow | T2 · C2 · 12 | Enemy: 2 dmg, pulled in front of you by the tongue (bosses only slowed). Self: grapple to the block you aim at, up to 16 blocks. |
| `lk_mahoraga_wheel` | JJK (Mahoraga) | Warlock | Effect · Shadow, beneficial | T4 · C5 · 22 | 30 s: each hit of a damage type (msgId) turns the wheel for that type. The next hit of that type takes −20% per turn, up to 4 turns (−80%). |
| `lk_dead_calm` | Demon Slayer (Water, 11th form) | Knight | Effect · Water | T4 · C4 · 18 | Self only, 3 s: melee (≤4.5 blocks) and projectiles are parried, and the attacker takes a 6×power counter cut. You get Slowness III while it lasts. |
| `lk_return_by_death` | Re:Zero (Subaru) | Witch | Effect · Shadow | T5 · C6 · 26 | Self only (players): saves a checkpoint (pos, dimension, HP, food) for 60 s (Extend). Lethal death is cancelled (`LivingDeathEvent`; totems still go first): restores HP, clears harmful effects, teleports back, 2 s blackout. Lock: 10 min after it saves you (cast cancelled without mana). |
| `lk_flower_field` | Frieren | Druid | Effect · Nature, beneficial, wantsBlocks | T3 · C3 · 16 | 10 s field, radius 5, on you, an ally, or where the spell hits the ground. Allies regain 1 HP/s (×power, max ×3). Ordinary mobs drop their target and get Slowness II. Petal particles only, no blocks placed. Max 24 fields server-wide. |
| `lk_shadow_possession` | Naruto (Shikamaru) | Ranger | Effect · Shadow | T3 · C3 · 16 | 4 s (bosses 1 s, PvP 2 s): the target copies your horizontal movement each tick and its AI navigation stays stopped. Breaks beyond 16 blocks. |
| `lk_aschente` | No Game No Life | Gambler | Effect · Light | T3 · C3 · 14 | 8 s duel that counts real damage dealt between the two (`LivingDamageEvent.Post`). Win: target takes 25% max HP (bosses 5%, ×power ≤2) as magic damage + Weakness II. Lose: you get Weakness + lose 20% mana. Tie: nothing. One game per caster. |

For all 19 glyphs, the Circle at which the class learns it (`LkUnlocks`) is the same as the glyph's minimum Circle in the table.

### Rules applied
- **Target rules:** reuses `LfUtil.isFoe`, which covers Peace Mode, party, PvP, duels and city wars. Players are never stashed or frozen outside PvP. Bosses always get the reduced version.
- **No block breaking:** nothing places or breaks blocks. The dash and the toad grapple refuse to cross warded or indestructible borders.
- **Light on performance:** all visuals use core `ElementFx.glow`/`sendFar` (the client respects FxQuality). Helpers are capped at about 40 points, pulses happen every 10–20 ticks, and areas hit at most 12–16 targets.
- **State:** everything lives in server-side maps keyed by UUID and is cleared on logout, death and server stop. The only saved data is the persistent tag on a Kamui/D4C-stashed mob (`isekai_mahou.lk_pocket`). If the world closes mid-stash, the mob is released when it loads again. A `MobDespawnEvent` DENY keeps a stashed mob from despawning, since it is parked below min build height.
- **No screen clutter:** feedback is short action-bar lines. The only screen effects are the blue tint (Kamui/D4C) and the Return-by-Death blackout, sent only to the affected player.

## Files
- `src/main/java/com/renanpontes/isekaimahou/feature/legends_k/`
  - `LegendsKModule.java`, `LkGlyphs.java`, `LkUnlocks.java`, `LkNet.java`, `LkUtil.java`, `LkFx.java`
  - `state/` holds the server state:
    - `PocketDimension`
    - `Guards` (Return to Zero, Santen, Dead Calm, Vector Reflect, Mahoraga, Return by Death, long cooldowns)
    - `Auras` (Pressure, Flower Field)
    - `Bindings` (Possession, Aschente)
    - `TitanState`
  - `glyph/` holds the glyph classes:
    - `DimensionEffects` (Kamui, D4C)
    - `SpiritualPressureEffect`
    - `TitanModeBody`
    - `ReturnToZeroEffect`
    - `RikkaEffects`
    - `ProjectionEffect`
    - `VectorEffects`
    - `ExtraTechniques` (toad, Mahoraga, Dead Calm, RbD, flowers, possession, Aschente)
  - `client/` holds `LegendsKClient`, `LkScreenFx`
- `src/main/lang/legends_k/pt_br.json`, `en_us.json` (69 keys each, no duplicates across the tree)
- `tools/gen/legends_k.py` is the module-only icon script. It wrote 19 icons to `textures/gui/glyph/lk_*.png`, coloured by class.
- One line each in `module/Modules.java` and `client/ClientModules.java`, after legends_j.

## Test commands
- `/mahou glyphs grant legends_k` teaches all 19.
- Natural unlock path: `/mahou path <class>`, then raise the Circle. Each glyph arrives with a "✦ Técnica de classe desbloqueada" line.
- Suggested spells:
  - `Self → lk_kamui` (double-sneak to leave), `Projectile → lk_kamui` on a zombie
  - `Self → lk_spiritual_pressure` in a mob crowd
  - `Transformação → lk_titan_mode`
  - `Self → lk_return_to_zero`, then let a skeleton shoot you
  - `Self → lk_santen_kesshun`
  - `Self → lk_projection` into a group of silverfish or zombies
  - `Self → lk_vector_reflect` vs skeletons
  - `Self → lk_shadow_toad` (grapple)
  - `Self → lk_return_by_death`, then `/kill`. Note: `/kill` bypasses it on purpose, so test with real damage or lava instead.

## PEDIDOS (hooks I could not add)
1. **transform_laws (agent T):** `TitanModeBody` extends `feature/transform_laws/BodyEffect` and uses the `TransformBody` API as it is today: `bodyTicks`, `cutsceneStyle`, `cutsceneRgb`, `onTransformEnd`, `start`. If T renames these, legends_k needs the same rename. No registry hook was needed, because ForgeBodies detects bodies by `instanceof`.
2. **Optional:** if the Grimório ever shows "how to learn" hints for origin glyphs, `LkUnlocks.circleFor(id)` returns the unlock Circle. Today each description ends with "Desbloqueio: <classe>, Círculo N".

## Not verified
- Nothing was run in game. Only javac was run: the module compiles clean in isolation (`-sourcepath`). The full `check-tree.sh legends_k` currently fails only in agent T's in-progress `class_quincy/QuincyFinalForms.java` and `glyph/LetztStilForm.java` (28 errors, none in legends_k).
- `mergeLang` duplicate check was not run (no gradle), but a Python scan found no key collisions.
- Behaviour that most needs an in-game check:
  - Stashed-mob parking below the world, and the release after reload.
  - Projectile reversal for mod spell projectiles.
  - Possession velocity copying on players (needs PvP).
  - Titan scale ×2.2 in tight spaces and doors.
  - Return-by-Death interplay with other revive effects (legends_f Transformations `onDeath` runs at HIGH too; order between modules is not guaranteed).
  - Dead Calm's 4.5-block melee heuristic.
