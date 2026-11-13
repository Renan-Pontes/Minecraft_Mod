# W10 — Anime effects & augments (`feature/legends_g`)

The owner changed direction ("não quero magias lendárias, queria efeitos de animes e augments"). The first version
built legend recipes with feat unlocks. It was refactored into **11 ordinary Grimório glyphs**: 9 EFFECTS and 2 AUGMENTS.
Players combine them freely with any form.

What changed in the refactor:
- No legend recipes. `LgRecipes` and the feat/unlock system `LgUnlocks` (plus `/mahou anime`) were deleted.
- `isLegendary()` is false and there is no `origin`.
- Every glyph uses element + tier + minCircle, so it gets the normal acquisition path, the same as `glyphs_elements_a`:
  - element path node by tier (Arcane: by player level via the Arcane path);
  - the normal scroll pools (guild, cities, elemental guardians, ruins, Wandering Mage — all of these accept non-legendary, origin-less glyphs);
  - learning from a scroll costs 1 essence of the glyph's element.
- Mastery and mutations apply automatically. All damage goes through `ctx.damage` with `SpellStats`, and augments use `SpellStats.custom`.

Skipped because something similar already exists: Megumin Explosion (`legends_a`), Rasengan (`legends_b`), dragon roar
(`legends_a`). The Knight's class `knight_anti_magic` stays the class version; mine is a common glyph. Nothing from the
legends_i/j list was made.

## Glyphs

Costs are base glyph mana; the core applies cost scaling and cooldown. "Self" means behaviour with the Si Mesmo form.

| Glyph id | Name (pt) | Anime | Kind · Element · Tier · Circle · Cost | Effect | How obtained |
|---|---|---|---|---|---|
| `lg_mana_core` | Núcleo de Mana | 7th Prince (Lloyd) | AUGMENT · Arcane · 4 · C4 · 10 | Previous glyph's damage +30% per copy (max 3). On cast it pays 3% max mana per copy and shows a cyan core flash in the chest. Placed after the form, it applies to the whole spell | Arcane path, level 18 + C4; scrolls |
| `lg_chantless` | Sem Encantamento | 7th Prince (Lloyd) | AUGMENT · Arcane · 4 · C4 · 6 | Sets the core INSTANT marker (cast time 0, +50% cost, never on the Stack) and `silent` (no chant or announce). Rune flash at the hand | Arcane path, level 18 + C4; scrolls |
| `lg_pure_magic` | Magia Pura | 7th Prince (Lloyd) | EFFECT · Arcane · 4 · C4 · 24 | 10 damage × mastery multiplier `1+0.3·ln(1+S/10)` (max ×2.5), where S = Σ(level−1) over known glyphs (read-only `GlyphMastery.level`); half the bonus vs players. Self: 0.5 s charge, then a 24-block beam piercing up to 8 | Arcane path, level 18 + C4; scrolls |
| `lg_grimoire` | Grimório Demoníaco | 7th Prince (Grim) | EFFECT (beneficial) · Shadow · 4 · C4 · 30 | Grim (floating crimson book entity) on the target's shoulder for 20 s. 5-damage shot every 1.5 s; devours hostile spell entities within 5 blocks every 0.5 s (+3% mana each); swallows one pending hostile cast every ≥3 s. Quiet in Peace Mode and safe zones; one per owner | Shadow Master node; scrolls |
| `lg_stone_cannon` | Canhão de Pedra | Mushoku Tensei | EFFECT · Earth · 3 · C3 · 20 | Spinning stone-shell entity: 16 Earth damage plus knockback; Pierce passes through at −30% per target. On a target it forms 3 blocks away and drills in (max 3 per cast). Self fires from the hand. A block hit runs `Destruction.sphere` BREAK, r ≤ 2.6 | Earth Adept node; scrolls |
| `lg_quagmire` | Atoleiro | Mushoku Tensei | EFFECT · Earth · 2 · C2 · 12 | Mud zone (r 3.5, 6 s; in memory only, no block edits): Slowness IV, Wet, no jumping (bosses: Slowness I). Max 2 per caster | Earth Apprentice node; scrolls |
| `lg_demon_slayer_sword` | Espada Matadora de Demônios | Black Clover | EFFECT · Shadow · 3 · C3 · 22 | Anti-magic cut: 10 damage (×1.5 vs magical targets), strips 2 buffs, interrupts and silences for 2 s, counters the target's own pending casts (power ×2), devours hostile spell entities within 2.5. Shatters the enemy domain the target stands in, once per domain per cast; boss domains lose 20% barrier instead. Self: 10-block line slash. ×1.3 in Black Form | Shadow Adept node; scrolls |
| `lg_black_form` | Forma Negra | Black Clover | EFFECT (beneficial) · Shadow · 4 · C4 · 28 | 10 s buff on the target: +4 attack, +15% attack speed, +20% speed, +3 armor, 0.3 knockback resistance. Spell damage taken −40% (−25% from bosses). Silenced and Corrupted cleared. Melee strips 1 buff and interrupts. Weakness 6 s when it ends | Shadow Master node; scrolls |
| `lg_fairy_law` | Lei das Fadas | Fairy Tail | EFFECT · Light · 4 · C4 · 26 | Only hits true foes (`LfUtil.isFoe`); bystanders, villagers and allies are untouched. 12 Light damage (×1.25 with line of sight, bosses ×0.7) plus a gold pillar (max 8 per cast). Self: judges foes in sight within r 8 (Expand +), max 20 | Light Master node; scrolls |
| `lg_serious_punch` | Soco Sério | One Punch Man | EFFECT · Arcane · 4 · C4 · 24 | 10 + ATTACK_DAMAGE (capped at 30), ×1.3 bare-handed, huge launch (bosses barely move). Self: 16-block cone (max 16 targets), `Destruction.line` BREAK for 10 blocks. If raining in the Overworld, sets clear weather for 5 min | Arcane path, level 18 + C4; scrolls |
| `lg_hinokami` | Dança do Deus do Fogo | Demon Slayer | EFFECT · Fire · 3 · C3 · 20 | Three fire strikes on the target (6 / 4 / 6 over 0.6 s), each with Burning 4 s; undead ×1.5. Self: dash ≤6 blocks via `ArcaneUtil.teleport`, then an arc and a halo. No block fire | Fire Adept node; scrolls |

**Rules followed:**
- **Targets:** all targeting uses `LfUtil.isFoe` / `ctx.isAlly`, which covers PvP-off, party, duel and war rules. Peace Mode is enforced by `SpellCaster`, and Grim also checks `PeaceMode` and `Places.isSafe`.
- **Terrain:** only via `Destruction`, which handles cities, towers, dungeons, home realm, the game rule and mobGriefing.
- **Visuals:** done through `ElementFx`, which respects FxQuality. Custom renderers exist only for Grim and the stone shell.
- **Entities:** both are `noSave`.
- **Balance:** no instakills; target caps and per-cast `once` guards limit area-form spam.

## Files
- `src/main/java/.../feature/legends_g/`: `LegendsGModule` (registers glyphs, `/mahou glyphs grant legends_g`), `LgRegistry` (2 entities, effect `lg_black_form`), `LgEvents` (Mana Core and Chantless hooks, Black Form rules), `LgUtil`, `effect/LgMobEffect`, `glyph/{Lloyd,Rudeus,Asta,Hero}Techniques`, `entity/{GrimoireEntity,StoneShellEntity}`, `client/{LegendsGClient,GrimoireRenderer,StoneShellRenderer}`.
- One-line registrations in `module/Modules.java` and `client/ClientModules.java`, placed after legends_f.
- Lang: `src/main/lang/legends_g/{pt_br,en_us}.json` (35 keys each, no duplicates with other modules).
- Icons: `textures/gui/glyph/lg_*.png` (11; not legendary, the 2 augments use the augment frame). These were drawn with `tools/gen/icons.py` drawing functions through a throwaway wrapper; the repo script was not changed or run in full, and a full run will re-guess them. `textures/mob_effect/lg_black_form.png` is a copy of `anti_magic.png`.
- Cross-module calls (public API only): `legends_f.LfUtil`, `arcane.ArcaneUtil`, `domain.entity.DomainDomeEntity.shatter`, `SpellCaster.interrupt`, `core.boss.BossKit.schedule`. No mastery, parser or caster files were edited.

## Testing
- `/mahou glyphs grant legends_g [player]` (op) teaches all 11 glyphs. Alternatively `/mahou glyph learn isekai_mahou:lg_<x>`.
- `/mahou circle 4` to meet Circle requirements, then build spells in the Grimório. Examples:
  - `self + lg_demon_slayer_sword`
  - `projectile + lg_stone_cannon + pierce`
  - `self + lg_fairy_law + expand`
  - `projectile + lg_pure_magic + lg_mana_core`
  - `projectile + lg_quagmire` then `lg_chantless` on any spell.
- Compile:
  - A copy of `check-tree.sh` that skips the other agents' unfinished `legends_h`/`legends_i`/`legends_j` → COMPILOU OK.
  - The official `bash tools/check-tree.sh legends_g` currently fails only in `legends_h/LhRegistry.java` and `legends_j/LjGlyphs.java`, which belong to the other agents.

## Not verified (no in-game run)
- Visual look and orientation of Grim's book and the stone shell.
- Whether the guessed icon art reads well.
- Mana Core's per-cast mana drain and damage bonus in practice.
- Chantless with every form: it relies on the core `instant` and `silent` handling.
- Balance numbers in general.
- Serious Punch's weather change is global; remove the `setWeatherParameters` line in `HeroTechniques.SeriousPunch.cone` if that is unwanted on servers.
