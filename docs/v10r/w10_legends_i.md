**legends_i report — anime glyphs (One Piece + Naruto)**

The owner first asked for "mais uns 20 de anime" (haki, Pain, Konan...). Then the direction changed: "Eu não quero magias lendárias, queria efeitos de animes e augments".

So this module adds **no Legendary Spells** and **no unlock feats**. It adds 13 ordinary Grimório glyphs:
- 11 EFFECT glyphs
- 2 AUGMENT glyphs

Players combine them freely with any form. Their anime identity comes from the effect itself, the visuals, the sounds, the name and the description.

How they behave as ordinary glyphs:
- `isLegendary()` is false and there is no origin.
- Element + tier + Circle put each glyph into the existing pipelines:
  - element path tier nodes (`PathRules.tierGlyphs` / `arcaneGlyphs`);
  - Wandering Mage, ruin scrolls, guild, bosses, city reputation (all filter `!isLegendary && origin == null`).
- Mastery and mutations apply automatically, through the core family of each glyph.
- Glyphs of the fusion elements (Gravity, Hellfire, Lightning) only show up as usable for players who have that fusion awakened. This follows the same rule as `fusions_*`.
- **Rasengan was skipped:** `legends_b` already has the common form glyph `spiral_sphere` ("Esfera Espiral", arcane tier 3).

## Glyphs

Base damage numbers are before power, augments and `DAMAGE_SCALE`, like every core glyph. The core reference is Arcane Damage: 6 damage for 10 mana.

| Glyph (id) | Kind | Element | Tier | Circle | Cost |
|---|---|---|---|---|---|
| Haki do Armamento `haki_armament` | augment | Arcane | 3 | 2 | 14 |
| Haki da Observação `haki_observation` | effect (beneficial) | Arcane | 2 | 1 | 14 |
| Haki do Rei `haki_conqueror` | effect | Arcane | 4 | 4 | 30 |
| Gomu Gomu no Pistol `gomu_pistol` | effect | Arcane | 2 | 1 | 10 |
| Shinra Tensei `shinra_tensei` | effect | Gravity | 3 | 3 | 22 |
| Bansho Ten'in `bansho_tenin` | effect | Gravity | 2 | 3 | 12 |
| Chibaku Tensei `chibaku_tensei` | effect | Gravity | 4 | 5 | 40 |
| Shuriken de Papel `paper_shuriken` | effect | Wind | 2 | 1 | 12 |
| Asas de Papel `paper_wings` | augment | Wind | 2 | 2 | 8 |
| Amaterasu `amaterasu` | effect | Hellfire | 3 | 4 | 24 |
| Susanoo `susanoo` | effect (beneficial) | Shadow | 4 | 5 | 36 |
| Chidori `chidori` | effect | Lightning | 3 | 3 | 18 |
| Kage Bunshin `kage_bunshin` | effect | Shadow | 3 | 3 | 26 |

### One Piece

**Armament Haki** (augment)
- Spell damage +10% per copy (max 3 copies).
- Each copy ignores 30% of the armor, protection-enchantment and Resistance reductions, up to 75%. Done with `DamageContainer` reduction modifiers.
- At LOWEST priority, it gives back half of whatever other listeners reduced. This lets it hit "intangible" targets such as the Phantom race or magic skins.
- Strips Invisibility.
- Visual: the caster's arms turn glossy black for 3 s. In third person the arm model parts are redrawn tinted; in first person the hand is replaced.

**Observation Haki** (effect, on self or an ally)
- Lasts 8 s (Extend +).
- Senses everything within 20 blocks (Expand +, max 40).
- Red and white outlines are drawn through walls with a custom no-depth line render type. They are sent only to the player who has the effect and refreshed every 2 s.
- Dodge chance: 25%, plus 8% per power level, max 50%. Halved against bosses. 1.5 s between dodges.
- A dodge cancels the hit and sidesteps.

**Conqueror's Haki** (effect)
- On self: a wave of radius 8 (Expand +, max 24). Otherwise it hits the target.
- Weak creatures (HP ≤ 60% of the caster's max HP × power) are Stunned for 5 s and lose their target.
- Creatures up to 2× that flee for 4 s, with Weakness, and cannot retarget.
- Stronger creatures get Slowness.
- Bosses only get Exposed. Enemy players get Slowness II 3 s and Nausea 4 s.
- Deals no damage.
- Visual: black-red lightning.

**Gomu Gomu no Pistol** (effect)
- Punch for 7 damage (×1.25 bare-handed) with knockback.
- On self: hits the first foe up to 10 blocks away (Expand +, max 20). With other forms, the arm flies to each target within 20 blocks.
- With Split it becomes **Gatling**: 3 + 2 per Split punches (max 12) at 45% damage each, one every 2 ticks.
- Visual: a client-drawn stretched rubber arm with a red sleeve and a fist.

### Pain (Gravity)

**Shinra Tensei** (effect)
- On a target: 4 damage and a strong repel away from the caster or the stage origin.
- On self: a sphere of radius 5 (+50% per Expand, max 20). It repels all foes and reflects enemy projectiles.
- The **big version** (radius ≥ 9) puts the caster on a 5 s lockout: no big Shinra can be cast during it, and an actionbar countdown is shown.
- Bosses are pushed at 20% strength.

**Bansho Ten'in** (effect)
- Pulls the target in front of the caster. Bosses are pulled at 15%.
- On self: pulls up to 8 foes within radius 8.
- With **Invert**, the caster is pulled to the target instead (grapple).

**Chibaku Tensei** (effect)
- A black core appears 5 blocks above the point. On self it appears 6 blocks ahead.
- It lifts every foe within radius 7 (max 16) into a spinning shell for 6 s (Extend +, max 20 s).
- Deals 2.5 damage every 0.5 s.
- Bosses are not lifted; they get Slowness III instead.
- Limits: max 16 held per core, 1 core per caster.
- Debris is particles only, plus client-drawn rock cubes. **No blocks are touched.**

### Konan (Wind)

**Paper Shuriken** (effect)
- 4 cuts over 0.6 s, 2.2 damage each. The last cut adds Bleeding.
- On self: a paper whirlwind of radius 4 (max 12) that hits up to 12 foes.
- With Expand or area forms it becomes the "Paper Ocean".
- Particles are vanilla paper item particles.

**Paper Wings** (augment)
- When the spell is cast: 10 s of wings (+5 s per copy, max 30 s).
- Gliding is client-side (`PaperGlide`): fall speed is capped at 0.12 blocks/tick and the player is pushed toward where they look, up to 0.5 blocks/tick.
- Sneak to fold the wings.
- The server cancels fall damage while the effect lasts.
- Visual: paper-sheet wings that flap.

### Uchiha

**Amaterasu** (Hellfire effect)
- 3 damage on hit, then 6 s of black flames (Extend +, max 20 s; half on bosses).
- Damage every 0.5 s: (0.6 + 0.25 × level) × `DAMAGE_SCALE`, where the level comes from power.
- Water and rain do not put it out; they only hiss.
- On self: it ignites the first foe in your gaze within 24 blocks.
- Kill credit goes to the caster.

**Susanoo** (Shadow effect, self or ally)
- Lasts 10 s (Extend +, max 30 s).
- Damage taken −35% (−20% from bosses). +4 armor and +0.5 knockback resistance.
- Each melee hit releases a spectral slash: 6-block cone, 6 dmg × power, at most once per 0.75 s.
- Recasting while active does a Totsuka strike instead: 9-block crescent, 14 damage.
- When it ends: Weakness for 5 s.
- Visual: a client-drawn ribcage, spine, clavicles and skull.

### Naruto

**Chidori** (Lightning effect)
- On self: a dash of 8 blocks (Accelerate +, max 14). It stops before walls, pierces the first foe and hits up to 3.
- With other forms:
  - If the target is within 12 blocks and in line of sight, the caster blinks in front of it (once per cast).
  - Otherwise only a bolt reaches the target, at 70% damage.
- 10 Lightning damage and 2 stacks of Shocked.
- With Amplify the color turns pale blue (Raikiri).

**Kage Bunshin** (Shadow effect)
- Creates 2 clones (+1 per Split, max 5; max 5 alive per owner), around the caster or around the target hit.
- They last 15 s (Extend +, max 45 s).
- Each clone has 25% of the owner's max HP and 60% of the owner's attack × power.
- The clone holds copies of the owner's gear, with attribute modifiers removed so it is visual only.
- A clone poofs after 2 hits, or when its time ends or the owner is gone or more than 48 blocks away.
- Monsters that were targeting the owner may switch to a clone.
- The clone renders with the owner's skin.
- Clones are `OwnableEntity` (spell allies) and `noSave`. They don't attack in Peace Mode or safe zones.

## Rules and safety
- **Targeting:** everything goes through `LfUtil.isFoe` / `SpellContext.isAlly`, so parties, duels, city wars, PvP off and Peace Mode are respected. Clones and the Susanoo slash use `LiUtil.hostile` and `LiUtil.calm` (Peace Mode or safe zone).
- **No instakills:** no glyph destroys or places blocks.
- **Bosses:** Conqueror never affects bosses; Shinra, Bansho, Chibaku and Amaterasu are all reduced on them.
- **Performance caps:**
  - client Fx packets: at most 32 per tick server-wide;
  - client effects: at most 200 alive, draw range 128;
  - LOW/MEDIUM graphics presets draw fewer bolts, rocks and ribs;
  - other limits: Chibaku 16 held, clones 5, sense 48 entities.
- **Visual states** (Susanoo, wings, haki arms, chidori hand) are re-sent to players who start tracking the entity later.

## Files
- `src/main/java/com/renanpontes/isekaimahou/feature/legends_i/`:
  - `LegendsIModule`, `LiGlyphs`, `LiRegistry`, `LiNet`, `LiUtil`, `LiEvents`
  - `glyph/{OnePieceGlyphs,PainGlyphs,NarutoGlyphs,UchihaGlyphs}`
  - `effect/{LiMobEffect,AmaterasuEffect}`
  - `entity/KageBunshinEntity`
  - `client/{LegendsIClient,LiFxClient,LiShapes,LiRenderTypes,HakiArms,PaperGlide,KageBunshinRenderer}`
- Registration: one line each in `module/Modules.java` and `client/ClientModules.java`, after legends_g.
- Lang: `src/main/lang/legends_i/{pt_br,en_us}.json`, 36 keys each.
- Icons:
  - `tools/gen/legends_i.py` (hand-picked pictograms from `icons.py`; does **not** run the full icon pass);
  - 13 × `textures/gui/glyph/<id>.png`;
  - 4 × `textures/mob_effect/li_*.png`.
- Registered content:
  - effects: `li_susanoo`, `li_amaterasu`, `li_paper_wings`, `li_observation`;
  - entity: `li_kage_bunshin`.

## Test
- `bash tools/check-tree.sh legends_i` → COMPILOU OK (whole tree).
- `py -I tools/check-assets.py . legends_i` → 0 warnings.
- Lang JSON is valid, has no duplicate keys across modules, and pt_br and en_us have the same keys.
- In game:
  - `/mahou glyphs grant legends_i` teaches all 13 glyphs.
  - Suggested recipes to try:
    - `self, shinra_tensei, expand, expand`
    - `projectile, chidori`
    - `self, gomu_pistol, split, split`
    - `self, kage_bunshin, split`
    - `projectile, amaterasu, extend`
    - `self, susanoo` (cast twice for Totsuka)
    - `projectile, bansho_tenin, invert`
    - `self, chibaku_tensei`
    - `projectile, arcane_damage, haki_armament`
    - `self, paper_shuriken, paper_wings`

## Not verified
**Nothing was run in game.** These need a look in game:
- **Haki arms:** whether the third-person arm overlay lines up with the model (crouch, scale and attack swing are approximated), and the first-person black hand.
- **Paper Wings:** how the glide feels, and whether it fights other movement mods.
- **Chibaku:** jitter of held players in multiplayer (server-driven velocity each tick).
- **Kage Bunshin:** AI quality (targeting, following, decoy behaviour), skin lookup for offline or unknown owners, and the armor layer.
- **Conqueror:** whether STUNNED really freezes every mob type, and how well fleeing works.
- **Glyph breakdown:** the numbers shown in the Grimório breakdown (`baseDamage` / `hintRadius` overrides).
- **Balance:** costs and numbers are first-pass estimates against core and fusion glyphs, not playtested.
