# v1.1: Agent D, the Dark Self (level 100 Shadow)

Owner's request: "Dark Link at lvl 100... sometimes while you walk your shadow disappears (only with full mana and far
from cities), and you fight a shadow of yourself to get past lvl 100... it should be easy to spawn, just a cool detail."

## Mechanics

**1. Level gate (core hook).** `Progression.levelCap(MagicData)` now returns **100** in Circle 10 while
`Progression.darkSelfGate(data)` is true. The gate is true when `level <= 100` and the flag `darkself.passed`
(`Progression.DARK_SELF_FLAG`) is missing. Above the cap, XP goes into the existing XP bank. The bank holds up to
`bankCap(100)`, about 64k XP (5 levels). On victory the bank is applied right away through the new
`MagicApi.releaseXpBank`.
- Grandfathering: a save with level > 100 is never gated (`level <= 100` check). The module also sets the flag on
  the next tick so it stays permanent.
- Circle 9 at level 100: the Circle cap still applies. The Shadow can already appear and be beaten there. In that
  case the bank stays for the Ascension, which applies it as before.
- Hints, each shown once: at level 100, `dark_self.cap` ("Something follows you... full mana, far from cities,
  watch your shadow"). When the shadow first vanishes, `dark_self.vanish` ("Something follows you... your shadow
  looks strange"). When the Shadow first rises, `dark_self.rise`.

**2. Trigger** (`DarkSelfService`, checked every 40 ticks per player, cheap).
- Conditions:
  - level ≥ 100 and not passed;
  - survival/adventure, on the ground, not riding, flying, gliding or swimming;
  - walked ≥ 1.5 blocks since the last check;
  - not recently hurt;
  - **mana ≥ max**;
  - **not in Peace Mode**;
  - **≥ 48 blocks from the box of any `Places` place** (cities, player cities, villages, lairs, hubs, hunting
    grounds);
  - not inside an `ArenaBounds` arena;
  - not on cooldown.
- Each valid check has a **4%** chance (about 1 every 50 s of walking). It is **guaranteed after 60 valid checks
  (about 2 min)**.
- **Vanish phase:** all clients near the player stop drawing his ground-shadow circle. They do this by setting the
  player renderer's `shadowStrength` to 0 per player, through reflection in `RenderPlayerEvent.Pre`, and then
  restoring it. Dark ink wisps appear at his feet. The owner gets a subtle screen veil (alpha 0.13) and a faint
  cave-echo sound. Nothing shader-heavy is involved.
- **First-person omen** (`client/DarkSelfOmen`, owner only, skipped in third person where the real shadow-hide
  already shows). Vanilla never draws your own shadow in first person, so the client draws a fake one in three
  stages:
  1. **Attached** (3–6.5 s; 1.5 s via `/mahou darkself spawn`): a soft dark humanoid decal (alpha 0.62, one quad)
     lies on the ground from his feet. It points away from the sun and its length is 1.8 × tan(zenith), clamped to
     about 1–4 blocks. At night it uses the moon. With the light overhead, at the horizon, or in Nether/End it is
     short and points forward. The same `DarkSelfService.shadowVector` formula is used on the server.
  2. **Detached:** the server picks the rise spot (`omenSpot`): 4 blocks along the shadow direction if that is
     roughly in front of him, otherwise straight ahead, so he sees it leave. It sends the spot to the owner, plays a
     sculk-spread sound, and the decal slides there over 25 ticks, turning and rippling.
  3. **Sinks** (30 ticks): the decal shrinks into the spot and fades with expanding ring decals (FxQuality: LOW 1
     ring and no particles, MEDIUM 2 rings, HIGH+ 3) and ink particles.

  There is no world scanning: the height comes from the player's last ground Y and the server's spot.
- **3 s after the omen sinks** (about 6–9.5 s after the vanish), the Shadow rises from the ground at that spot. If
  the player is already more than 20 blocks away, it rises about 3 blocks behind him instead. It takes 40 ticks to
  rise, sinks out of the ground on the client, and is invulnerable while rising. A warden-emerge sound plays (pitched up) with an
  action-bar line.
- If the player enters a city, Peace Mode or an arena before the Shadow rises: the vanish is cancelled, the shadow
  returns, and there is a 30 s cooldown.

**3. The Shadow** (`DarkSelfEntity`, a `Monster`, rendered with the vanilla player model).
- Look:
  - the owner's skin (slim or wide) multiplied by dark purple `0xEB3A2852`, slightly translucent;
  - glowing red/white eyes (the `eyes` render type; texture `textures/entity/dark_self_eyes.png`, painted on both
    the head and hat layers);
  - wears **visual copies** of the owner's armor and held items. The copies have their attributes replaced by one
    neutral modifier and their enchantments removed; the glint is kept. Drop chance is 0.
- Stats, from the owner's max HP (`ownerHp`):
  - effective HP = clamp(ownerHp × 4, 300, 1500). The base is divided by `MOB_HEALTH_SCALE` because the core
    doubles the HP of mod mobs.
  - melee = ownerHp × 7% effective per hit (the base is divided by `MOB_DAMAGE_SCALE`);
  - armor = clamp(owner armor × 0.5, 4, 14);
  - speed 0.32, knockback resistance 0.5.

  A level 100 player with about 100 HP faces about 400 HP and about 7 per hit.
- Combat (adapted from the Ascension Shadow's duel AI):
  - casts the owner's **active-loadout** recipes via `SpellCaster.castAsMob` at power **0.8**, with cooldowns
    ×1.3 (minimum 2 s). Fighter classes with no recipes fall back to an arcane bolt and touch-push.
  - **ultimate (G) once**, below 50% HP.
  - **below 35% HP it throws back the owner's last non-ultimate spell**, every 8 s, with a chat line.
  - keeps its distance and strafes, fights melee when close, and blinks behind the owner if he hides or runs.
  - **mirrors dodges:** a 40% chance on each owner cast and 20% on owner melee hits to sidestep, with a 2.5 s
    cooldown.
  - purple notched boss bar, visible only to the owner.
- Duel rules:
  - only the owner (and his tamed/owned creatures) can damage it. Other players get "This is not your fight".
  - its damage to anyone else is cancelled (`LivingIncomingDamageEvent`).
  - it only targets the owner.
  - **No block breaking:** a `Destruction.addProtection` predicate blocks spell terrain destruction within 40 blocks
    of an active Shadow. This applies to its spells and the owner's.
- **Fades and can return later** (3 min cooldown):
  - the owner dies (`LivingDeathEvent` at LOWEST, ignoring cancelled deaths);
  - the owner logs out or changes dimension;
  - the owner is more than **48** blocks away;
  - the owner turns on Peace Mode.

  The Shadow is never saved to disk (`shouldBeSaved=false`). Duel state is in memory only, per server session.

**4. Victory.** When the owner (or his pet) kills it, or it dies with `lastHurtByPlayer` = owner:
- the shadow returns;
- flag `darkself.passed` is set and counter `feat.dark_self` +1;
- the XP bank is applied (Circle 10);
- the cutscene `"ascension"` plays: "You accepted your shadow" / "The level 100 limit is broken";
- a chat line and a challenge-complete sound;
- title **"Master of One's Shadow"** (`title.isekai_mahou.dark_self`, via `Reputation.grantTitle`; it shows up in
  the normal title list);
- 50 vanilla XP orbs.

A journal entry was **not** added. The journal is a linear, data-driven main-story chain (`data/.../journal/*.json`),
and a new chapter would interfere with story order and finale counting. The counter `feat.dark_self` can be used by a
future journal or renown step.

## Debug commands (op)

`/mahou darkself status|spawn|reset|pass [targets]`
- `status`: level/cap, passed, phase, valid checks / 60, cooldown, banked XP, and each trigger condition shown as
  ✔/✘.
- `spawn`: vanish now; the omen detaches after 1.5 s and the Shadow rises about 5 s after the command, ignoring the conditions. Works in creative too, but creative players take no
  damage.
- `reset`: removes the flag, kills any active Shadow or vanish, and clears the cooldown (gates level 100 again).
- `pass`: unlocks without fighting (no cutscene or title) and applies the bank.

Test path: `/mahou circle 10`, `/mahou level 100`, `/mahou darkself status`, then fill mana (`/mahou fill`) and walk in
the wild, or use `/mahou darkself spawn`. Note that `/mahou level 120` clamps to 100 until the player has passed.

## Files

| File | What |
|---|---|
| `feature/dark_self/DarkSelfModule.java` (new) | Registration, attributes, payload, commands |
| `feature/dark_self/DarkSelfRegistry.java` (new) | Entity type `isekai_mahou:dark_self` |
| `feature/dark_self/DarkSelfService.java` (new) | Trigger state machine, vanish/rise/fade/victory, duel damage rules, no-grief zone, command helpers |
| `feature/dark_self/DarkSelfNetwork.java` (new) | `ShadowState(uuid, hidden)` S2C (self + trackers, resent on StartTracking); `Omen(detach, x, y, z)` S2C (owner only) |
| `feature/dark_self/entity/DarkSelfEntity.java` (new) | The Shadow mob (AI, mirror, dodge, boss bar, leash/fade) |
| `feature/dark_self/client/DarkSelfClient.java` (new) | Renderer registration, ground-shadow hiding, ink wisps, owner veil |
| `feature/dark_self/client/DarkSelfOmen.java` (new) | First-person omen decal (attached, slide, sink, ripples); own decal render types (translucent, no depth write, polygon offset) |
| `assets/isekai_mahou/textures/entity/dark_self_silhouette.png`, `dark_self_ripple.png` (new) | Soft humanoid silhouette (32×64) and ring (32×32) alpha masks |
| `feature/dark_self/client/DarkSelfRenderer.java` (new) | Player-model renderer, dark tint, slim/wide swap, armor layer, eyes layer, rise offset |
| `assets/isekai_mahou/textures/entity/dark_self_eyes.png` (new) | 64×64 eye glow mask (skin layout) |
| `src/main/lang/dark_self/{pt_br,en_us}.json` (new) | All strings |
| `core/Progression.java` | `levelCap(MagicData)` +1 line; `DARK_SELF_FLAG`, `darkSelfGate()` |
| `core/MagicApi.java` | `releaseXpBank(ServerPlayer)` (public wrapper around the private `applyBank`) |
| `module/Modules.java`, `client/ClientModules.java` | One registration line each |

## Compile

- My files plus `Progression` and `MagicApi` compile cleanly in isolation (javac with `-sourcepath`).
- The whole-tree `bash tools/check-tree.sh darkself` currently fails with 28 errors. All of them are in
  `feature/class_quincy/QuincyFinalForms.java` and `glyph/LetztStilForm.java`, which agent T is editing. There are 0
  errors in dark_self or core.

## Not verified (no in-game run)

- Nothing was tested in game.
- Shadow hiding uses reflection on `EntityRenderer.shadowStrength`; production NeoForge uses Mojang names, so it
  should work. If it fails, a warning is logged and only the wisps and veil remain.
- First-person omen: the sun math (east = +X) follows vanilla's sky rotation but has not been checked on screen. The
  decal is drawn at the `AFTER_PARTICLES` stage, the same as the core telegraphs. With shader packs (Iris) it may
  not show, or may draw over things. If he jumps or falls during the attached stage, the decal stays at his last
  ground height. If the shadow points behind him, he only sees the attached stage by looking back, but the slide
  always goes toward his front half.
- Eye pixels assume the default eye position (row 12). Custom skins may have their eyes elsewhere.
- Tinted player model with translucent render type plus normal-colored armor: the look still needs checking.
  Mod armors with custom renderers may look different.
- Balance (HP ×4, power 0.8, 7% melee) is untested against real level 100 builds.
- Existing Circle 10 saves sitting at exactly level 100 lose their partial XP toward 101 on the next gain. The core
  sets `xp=0` when at the cap.
- If the Shadow is killed with `/kill` after the owner has hit it, that counts as a victory.
- An afterlife "spirit" (a cancelled death) keeps the duel going.
