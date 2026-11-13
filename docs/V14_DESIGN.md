# Isekai Mahou v1.4 — "Glifos, Leis e Cidades"

NeoForge 1.21.1 mod (Java 21, Mojang mappings + Parchment, GeckoLib, Curios, playerAnimator). The player is Renan, who speaks PT-BR. His requests, in his own words:

1. "os hollows, gran rey cero etc vc colocou tudo como forma, ficou meio merda" — the Hollow class techniques (Cero, Gran Rey Cero...) were all made as glyph FORMS. That feels bad: rework them.
2. "vamos adicionar mais modificadores pras magias, os augments lá, ao menos 1 por elemento e classe" — at least one new AUGMENT per element and per class.
3. "assim como triggers, um pra cada tbm" — at least one new TRIGGER per element and per class too.
4. "alem de domain laws, vamos ter regras pra transformação" — besides domain laws, add rules (laws) for transformations.
5. "vamos melhorar como funcionam as cidades, que as pessoas querem escolher como cada coisa vai ficar, os recursos pra construir não precisamos, mas elas tem que gerir comida e outras coisas da cidade! se não fica mto amplo..." — players want to choose what each part of their city becomes. Construction needs no materials, but players must manage food and other city needs. Without those needs it gets too loose.

## Shared rules (ALL agents)
- **Language.** All player-facing text is PT-BR. Write both `pt_br.json` and `en_us.json` in `src/main/lang/<your module>/`; they are merged at build. Comments are in PT-BR, matching the existing style.
- **Never run gradle.** Other agents edit the tree in parallel.
  - Compile your module with `bash tools/check-module.sh <module>`.
  - Run `bash tools/check-tree.sh <yourtag>` when you touch anything outside `feature/`.
  - Other agents' errors are not yours, but your own files must compile when you finish.
- **Ownership.** Edit only the files you OWN (table below). Use other code read-only through its public API. If you need a change in a file you don't own, put it in your final report under "PEDIDOS".
- **Saved worlds.** Never rename or remove existing registry or glyph ids. Old worlds and old saved spells must keep loading. To retire something, keep the id registered and hide it, or convert it.
- **Performance.**
  - No per-tick `getEntitiesOfClass` with big boxes. Throttle work to every 5–20 ticks.
  - Cache results, and clean up maps when an entity or player leaves.
  - Client effects go through `client/fx/FxQuality`: `count(n)`, `atLeast(Graphics.HIGH)`, `range()`.
- **Glyph system.** Read these before designing:
  - `glyph/` — `Glyph`, `GlyphKind`, `AugmentGlyph`, `TriggerGlyph`, `LawGlyph`, `FormGlyph`, `GlyphRegistry`, `SpellParser`, `SpellCaster`, `SpellStats`, `SpellContext`, `CoreGlyphs`.
  - Existing examples:
    - augments: `feature/legends_a/glyph/AllInAugment`, `ColossalAugment`, and gambler/steelball/race augments;
    - triggers: `glyph/MoveTrigger`, the link triggers, `glyph/weapon` parry, and the class triggers;
    - laws: `feature/domain/glyph/*`, `glyph/LawGlyph`.
  - How a player learns a glyph:
    - common glyphs come from temples, bosses and exploration (scroll and essence rules in `feature/paths`, `feature/arcane`, `glyph/GlyphHints`);
    - class glyphs come from class progression / Origin techniques;
    - glyphs of other races and classes can only be learned through teaching, Lessons and relics.

  Follow the same model for your glyphs and wire them into the right unlock path, so the player can actually obtain each one in survival.
- **Icons.** Every new glyph needs a grimoire icon.
  - See `feature/ui_grimoire/client/GlyphIcons` and where glyph textures live.
  - Generate yours with your own script `tools/gen/<module>_icons.py`, in the existing pixel-art style.
  - **Never run the full `tools/gen/icons.py`.** It overwrites hand-made icons.
- **Glyph design quality.** Each new augment and trigger must be **meaningfully different**, not "+10% damage". It must fit its element or class fantasy, have a clear tooltip (what it does and what it costs), sensible mana/complexity cost, and visible feedback (a small effect or sound when it acts).
- **Balance.** The player said: until level 100, leveling up must be hard; only at level 100 should absurd spells be possible. New glyphs must not trivialize progression. Gate the strongest behind higher Circles or ranks.
- **Debug command.** Give the coordinator an op test command to grant all your new glyphs to a player, e.g. `/mahou glyphs grant <module>`. Reuse an existing grant command if one exists.
- **Final report.** Your last message, in English and short:
  - files touched;
  - the list of new glyph ids, each with a one-line description;
  - how to test in game (commands);
  - known gaps;
  - "PEDIDOS".

## Elements and classes
- **Base elements (7):** arcane, fire, water, earth, wind, light, shadow.
- **Fusion elements (15):** magma, steam, lightning, sun, hellfire, nature, ice, purity, abyss, sand, crystal, gravity, astral, void, twilight. See `core/Element.java`.
- **Classes (16):** mage, witch, warlock, druid, necromancer, smith, rogue, knight, enchanter, shinigami, quincy, gunslinger, ranger, gambler, steelball, hollowfied. See `core/MagicPath.java`.

## Ownership
| Job | Owns (may edit) |
|---|---|
| **glyphs_elements_a** | `feature/glyphs_elements_a/**` (pre-created, registered). Deliverable: 1 augment + 1 trigger for each of the 7 base elements (14 glyphs). |
| **glyphs_elements_b** | `feature/glyphs_elements_b/**`. Deliverable: 1 augment + 1 trigger for each of the 15 fusion elements (30 glyphs). |
| **glyphs_classes_a** | `feature/glyphs_classes_a/**`. Deliverable: 1 augment + 1 trigger each for mage, witch, warlock, druid, necromancer, smith, rogue, knight (16 glyphs). They are class glyphs: learned through that class's progression, or taught to others. |
| **glyphs_classes_b** | `feature/glyphs_classes_b/**`. Deliverable: 1 augment + 1 trigger each for enchanter, shinigami, quincy, gunslinger, ranger, gambler, steelball (14 glyphs). |
| **transform_laws** | `feature/transform_laws/**`; `glyph/**` core files (ONLY to add the transformation-law concept; keep domain laws working exactly as now); `feature/forms_a/**`, `feature/forms_b/**` (hooks so transformation forms accept transformation laws); `feature/character/**` (hooks so race transformations use the player's chosen transformation laws); `feature/ui_grimoire/**` (UI to pick and show transformation laws). |
| **hollow** | `feature/class_hollow/**`; `feature/class_shinigami/inner/**`. Deliverables: rework the Hollow techniques, plus the Hollow class's own 1 augment + 1 trigger. |
| **cities** | `feature/cities/**`, `feature/settlements/**`, plus the city-building code wherever it lives (find it). Deliverable: city management. |

The coordinator owns `core/social/**` and `core/status/**`. A separate agent may still be finishing `feature/espada/**`, `feature/hueco_mundo/**` and `feature/las_noches/**`. Don't touch those.
