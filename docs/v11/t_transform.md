# v1.1 — Agent T: transformations become EFFECTS of the Transformação form

Owner's words: *"A ideia não é bug, é saírem de formas e virarem efeitos pra forma 'transformação'"*. Almighty spec:
5 min, every attack dodged, counter = teleport behind the attacker + hit + "Eu vi tudo...", each counter −30 s, then 5 s
dodging only (no teleport). In short: invulnerable for 5 min or 10 hits.

**Who did what.** Agent T stopped mid-work (~19:54) with no report and left the tree uncompilable (28 errors in
`QuincyFinalForms` / `LetztStilForm`). A second session finished the job by **continuing T's design**, without
reverting any of it. Each item below is tagged **[T]** (already in the tree) or **[fin]** (added when finishing).

Compile: `bash tools/check-tree.sh t11fin` → **COMPILOU OK (all)**, 3247 files. `check-json.py` on the touched lang folders: 0 errors.
pt_br/en_us key sets match in transform_laws, class_quincy, class_shinigami, legends_f, ultimates, character and class_hollow.
Gradle was not run, and nothing was committed.

## Model
- **[T]** `transform_laws/TransformBody` (interface) and `BodyEffect` (base class). A **body** is an EFFECT glyph that only
  works right after the Transformação form: `Transformação → body [augments] → effects → laws`.
  - It starts once, on the caster, when the transformation starts.
  - The longest body sets the duration of the whole transformation (`bodyTicks`). A body can also raise the minimum cooldown (`minCooldown`).
  - `supreme()` = the body was a G form, so it needs the **Supreme Transformation**.
  - `onTransformEnd` undoes the body when the transformation ends for any reason.
  - Cutscene style, colour and title come from the body.
- **[T]** New glyph **`isekai_mahou:transformation_supreme`** (Transformação Suprema). It is ultimate: G key only and spends the Ultimate Charge.
  Tier 5, cost 60. Same session/source as the normal form (`TransformForge.isForm`).
- **[T]** `TransformForge` helpers:
  - `castDuration`, `bodies`, `inForge(ctx)`;
  - `bodyTicks(ctx, target)`, for fixed-transformation effects used as a body;
  - `castRemaining` / `setCastRemaining` / `endCast`, which a body uses to shorten or end the transformation;
  - Grimoire summary lines ("X sets the duration: 5:00", "Supreme: G only").
- **[T]** Slots refuse bodies (`tforge.body_in_slot`). A body inside a slot recipe makes no sense: the body *is* the transformation.
- **[fin]** `TransformForge.role()`: any `TransformBody` is always role BODY, even when it isn't registered in
  `TransformTargets`. Without this, agent K's `legends_k/TitanModeBody` (Modo Titã), which is not registered, would never
  start. No public method names changed; legends_k still compiles untouched.

## Bodies (ids unchanged, so mastery and "known glyph" data stay valid)
| id | was | now | supreme | duration | min cd | cutscene | by |
|---|---|---|---|---|---|---|---|
| `quincy_almighty` | G form, 20 s, 45% dodge | body: **5 min**, counters (below) | yes | 6000 (Encurtar shortens, Estender can't exceed) | 10 min | dark, 0x6FA8FF | T + **fin** |
| `quincy_letzt_stil` | G form | body; the price is charged when the transformation ends | yes | 600 | 2 min | holy | T + **fin** (`stopLetzt`) |
| `quincy_vollstandig` | G form | body | yes | 240 | 30 s | holy | T |
| `zanpakuto_bankai` | G form | body; title = the archetype's Bankai name | yes | 400 | 45 s | spirit | **fin** (T made `BankaiState.stop`) |
| `shinigami_visored` | form (any key) | body (normal Transformação); trained length up to 45 s stretches the transformation | no | 400 (to 900 trained) | 30 s | dark | **fin** (T made `HollowForms.stopForm`) |
| `shinigami_vasto_lorde` | G form | body | yes | 500 | 60 s | beast | **fin** |
| `necro_monarch` | G form (legendary) | body, players only | yes | 300–1200 | 45 s | dark | **fin** (T made `MonarchState.stop`) |
| `transcend` | G form | body; ends with Magic Exhaustion | yes | 300–1200 | 30 s | holy | **fin** |

`colossus` **stays a form**: it is a summon, not a powered state.
- **[T]** The class "shout" announcements are muted inside a Transformação, because the cutscene already shows the name.

### The Almighty — final rules [T design, fin completed]
- Every hit whose source is an entity (melee, projectile, spell) is **cancelled**.
  - Void, `/kill` and environment damage still apply (`BYPASSES_INVULNERABILITY`, or no source entity).
- **Counter**, when ready and the attacker is a living non-ally within 24 blocks in the same level:
  - you teleport to a safe spot **behind** the attacker (checks collision, world border, and ground within 4 blocks);
  - you face them and hit once (`COUNTER_DAMAGE` 14 × body power, spell damage, works on bosses, never breaks blocks);
  - a "Eu vi tudo..." speech bubble (`Announce.chant`, key `message.isekai_mahou.quincy.almighty_saw`);
  - the transformation loses **30 s** (`COUNTER_COST`), and the effect icon shows the new time;
  - **5 s** follow with dodging only (`COUNTER_GAP`).
- **10 counters** (`MAX_COUNTERS`) or running out of time ends the Almighty **and** the transformation.
- The cost and cooldown are marked *before* the hit, so two Almighty users can't loop counters forever.
- If no safe spot is found, it just dodges (side-step afterimage, at most one visual every 8 ticks).
- Leaving the dimension or dying clears it.

### Fixed-transformation effects used as a body [fin]
Owner: "ultra instinto... vão tudo pra transformação". `class_enchanter/Transformations`: when the effect is the body of a
Transformação on its caster, it lasts **the whole transformation**:
- Ultra Instinct (and starts with the maximum of 12 dodges);
- Gear Second and Gear Fourth;
- the Gates (except the 8th);
- Kaioken ×2 (×3/×10 stay short).

Their announcements are muted there. Loose use (Si Mesmo etc.) is unchanged.

## Migration of old saves [T]
- `SpellParser.TransformHook` is installed by transform_laws and only reads recipes; the stored data is never rewritten.
  Any recipe that starts (after triggers) with a body gets `Transformação` / `Transformação Suprema` inserted in front at
  parse time. This covers Z/X/C/G keys, the library, presets, legend scrolls (`QuincyLegends`, `LegendRecipes` shadow_monarch,
  `UltLegends` awakening) and NPC spells.
- Augments that followed the old form now apply to the body, so Estender still stretches it.
- The implicit Transformação form before a known body doesn't need to be learned and doesn't count toward the glyph limit.
- Knowing a body teaches the form(s) silently, both on learn and at login. `/mahou tforge grant` also grants the Supreme form.
- Bodies outside a Transformação, or a supreme body in the normal form, give a parse error with its own message.
- A transformation with a body does **not** resume after relog. The orphan "forged" icon is removed.

## Cutscenes (`Cutscenes.transform`)
- **[T]** A cast Transformação plays the transformation cutscene once per activation, after `SpellCastEvent.Post`.
  The body sets the title, style and colour. Fixed effects have styles too: Kaioken/Gears = fire, Gates/Chainsaw = blood,
  Ultra Instinct = speed, animal forms = beast, Sage Mode = spirit, Lich/Demon Wings = dark.
- **[T]** Race V form: `Cutscenes.transform` with a style per race (Angel = holy). It replaces the old banner and vanilla title.
- **[fin]** Hollow **Resurrección** (`class_hollow/tech/HollowTechniques`): the old `ascension` cutscene is replaced by `Cutscenes.transform`
  (Pantera = beast, Murciélago = storm, Serpiente = dark).

## Jibril (No Game No Life) — Angel race form [T, complete]
- Form name: "Jibril, a Flügel".
- Stats: +30% spell power, +12 health, 0.5 knockback resistance, −15% damage taken in form.
- On activation: +20% heal.
- Pink/violet feathers in the wing particles.
- `RaceFeatureLayer` visuals.
- New racial power **Tengeki (Golpe Celestial)**:
  - 24-tick charge, then a 32-block beam that ends in a 12-block light sphere;
  - only hurts enemies, ×1.5 on undead;
  - never breaks blocks, refused or fizzled in safe zones;
  - 12 s cooldown.
- Lang pt/en updated.

## Files
- **[fin]** Code:
  - `class_quincy/QuincyFinalForms.java`
  - `class_shinigami/glyph/{BankaiForm,VisoredForm,VastoLordeForm}.java`
  - `legends_f/glyph/MonarchForm.java`
  - `ultimates/glyph/TranscendForm.java`
  - `class_enchanter/Transformations.java`
  - `class_hollow/tech/HollowTechniques.java`
  - `transform_laws/TransformForge.java` (`role`)
- **[fin]** Lang (pt_br + en_us):
  - `transform_laws`: Supreme name/desc, `tforge.body_time`, `tforge.supreme`, `tforge.body_in_slot`, `spell_error.*.body_needs_transformation|body_needs_supreme`;
  - `class_quincy`: 3 descs, `almighty_saw|counter|end`, 2 tips;
  - `class_shinigami`: 3 descs, 4 hint texts pointing to "Transformação Suprema → Bankai";
  - `legends_f`, `ultimates`: one desc each.
- **[fin]** Icon `textures/gui/glyph/transformation_supreme.png`: the Transformação icon tinted gold.
- **[T]** The rest of the diff: `transform_laws/{TransformBody,BodyEffect,ForgeBodies}.java` (new),
  `ForgeSessions`, `TlawTargets`, `TlawGlyphs`, `TransformationForm`, `ForgeCommands`, `TransformLawsModule`,
  `glyph/SpellParser.java` (hook only), `class_quincy/{QuincyVollstandig, glyph/*Form}`,
  `class_shinigami/{BankaiState,HollowForms}`, `legends_f/necro/MonarchState`, `character/**`, `lang/character`.

## How to test (OP / cheats)
```
/mahou admin login <senha>                (if the admin commands ask for it)
/mahou level 60
/mahou circle 9
/mahou tforge grant                       (Transformação + Suprema + the 13 laws)
/mahou glyph learn isekai_mahou:quincy_almighty     (also: quincy_letzt_stil, quincy_vollstandig, zanpakuto_bankai,
                                                     shinigami_visored, shinigami_vasto_lorde, necro_monarch, transcend)
```
1. **Old save first.** Load a world where the G key had `O Todo-Poderoso` (or Bankai/Vollständig/Monarca) as the form.
   It should still cast, now shown as `Transformação Suprema → …`.
2. **Almighty.** Play in **survival** (creative players aren't attacked, and creative skips the Ultimate Charge). With a full charge, put
   `Transformação Suprema → O Todo-Poderoso` on G and cast it near zombies/skeletons. Check:
   - the dark cutscene, and the effect icon reads ~5:00;
   - hits never land;
   - first hit: you appear behind the mob, strike, "Eu vi tudo..." bubble, icon drops 30 s;
   - next 5 s: side-steps only;
   - after 10 counters, the transformation ends.
3. Put `O Todo-Poderoso` after `Projétil`: you get the "é uma transformação" error. In the normal `Transformação`, you get the "use a Suprema" error.
4. Bankai / Vasto Lorde / Letzt Stil / Monarch / Transcend inside the Supreme form: each starts its state, and they end with the transformation.
   - Recast Letzt Stil mid-way: the price is charged.
5. `Transformação → Máscara do Vazio` on any key: the mask lasts the trained time, and the transformation follows.
6. Enchanter: `Transformação → Instinto Superior` (and Kaioken, Gears, Gates): the effect lasts the whole transformation.
7. Hollow Resurrección: the transformation cutscene plays (beast/storm/dark).
8. Angel race V: the Jibril cutscene (holy). Racial key in form: Tengeki. In a city: refused.

## NOT tested / open points
- **Nothing was run in game.** Only javac on the whole tree.
- `core/anim/TechniqueGestures` still registers the 8 body ids with `form(...)`. Release gestures are looked up by form path
  first, so the old Bankai/Vollständig/... gestures no longer play: the Transformação gesture plays instead.
  Fix for the coordinator (not T's file): change those 8 lines to `effect(...)`.
- **Balance:**
  - the Transformação mana cost counts pulse copies over the *form* duration (30–60 s), even when a body sets 5 min, so Almighty plus aura effects is cheap per second (it still needs the Ultimate Charge and has a 10 min cooldown);
  - body costs were lowered (40/30/25/25/30/30/20/10), because the Supreme form adds 60.
- The "Eu vi tudo..." bubble is resolved on the server (`Language.getInstance()`). Single player shows the client language. A dedicated server
  falls back to the literal Portuguese text.
- Mobs and legend NPCs that cast a Transformação get the body but no transformation cutscene.
- Shikai (toggled on the sword) has no transformation cutscene and wasn't changed.
- The owner's "Monarch, if it's a state": treated as a state, so it is a body.
- Camera behaviour of each `transform:<style>` belongs to agent C (see `c_cutscenes.md`).
