**REPORT — job "summon_forge" (wave 4)**

Both deliverables are built and compile, but nothing was run in game, so behaviour and visuals are unverified. `check-module.sh` ends in TUDO OK for `forms_b`, `familiars`, `fire_water`, `earth_wind`, `light_shadow` and `fusions_a`; `check-tree.sh summon_forge` ends in COMPILOU OK.

**What it does**
- **Mountable summons:** a recipe `Invocação (conjure) + [summon glyph] + effects` now spawns that creature instead of the spirit, and the rest of the recipe commands it.
  - Bodies: salamander, undine, stone golem, sylph, guardian fairy, living shadow, ent.
  - Effects fire on the creature's hits, once per 2 s per creature (same pace as the spirit). Beneficial effects go to the owner; a support-only recipe is cast on the owner when hurt or in combat. The fairy casts the effects itself, since it rarely hits.
  - Split = how many (cap 2, fairy 3, shadow 4), Expand/Concentrate = size (60–175%, with HP), Extend = duration, Amplify = strength.
  - The body is tinted with the recipe's element colour, only when that differs from the body's own element or the player picked a spell colour.
- **Orders:** follow (default), guard a point, hunt the marked target, stay put — for bodies and for the old spirit.
  - By glyph, placed right after `conjure` or the body glyph: `summon_guard`, `summon_hunt`, `summon_hold`.
  - By key O (cycles all your summons), by sneak + right-click on one summon, or by `/mahou summons <follow|guard|hunt|stay>` (any player).
  - Hunt marks what you look at; with nothing in sight, the next enemy you hit. Allies, PvP-off or Peace Mode players, and peaceful NPCs inside safe zones cannot be marked.
- **Familiar support recipe:** B → Character → Familiar → "Magia de apoio" lists only the player's spells that qualify; one click teaches, "Esquecer" forgets.
  - Allowed effects are a fixed list of 16: heal, greater_heal, regenerate, mend, spin_heal, cleanse, purify, shield, stoneskin, protective_bubble, slow_fall, water_breathing, conjure_light, detect, reveal, mage_mana_sense. No Invert or Overload, at most 2 effects, single stage, no laws, triggers, ultimates or transformations — so no damage recipe can be taught.
  - The familiar casts it on the owner by itself when the first effect makes sense (hurt, bad effect, taking hits, falling, drowning, dark, or on recharge outside cities).
  - It is weak and slow on purpose: 50/65/80% power by stage, recharge of at least 30 s, no mana, XP or mastery for the owner, never touches blocks.

**Behaviour changes to existing recipes**
- `conjure + summon_x` used to make the spirit cast the summon at enemies; it now spawns the creature directly.
- Split on a summon glyph in any form now gives a second golem, sylph, ent, salamander or undine; before it did nothing for those five.
- Recasting `conjure` now replaces all of the owner's old spirits in the world, not only those within 32 blocks.
- Saved worlds: no ids or NBT keys renamed; all new fields are optional.

**Files touched (all inside my area)**
- `src/main/java/com/renanpontes/isekaimahou/feature/forms_b/`:
  - new `summon/` (`SummonForge`, `SummonBodies`, `SummonOrder`, `SummonNet`, `SummonCommands`), `glyph/OrderGlyphs.java`, `client/SummonForgeClient.java`
  - edited `glyph/ConjureForm.java`, `entity/SpellConjurerEntity.java`, `FormsBModule.java`, `client/FormsBClient.java`
- `feature/fire_water/`: `FireGlyphs.java`, `WaterGlyphs.java` (summon glyphs only), `client/SalamanderRenderer.java`, `client/UndineRenderer.java`
- `feature/earth_wind/`: `entity/SummonedStoneGolem.java`, `entity/SummonedSylph.java`, `glyph/earth/SummonGolemEffect.java`, `glyph/wind/SummonSylphEffect.java`, both renderers
- `feature/light_shadow/`: `entity/SummonUtil.java`, `entity/GuardianFairyEntity.java`, `entity/LivingShadowEntity.java`, both renderers
- `feature/fusions_a/`: `entity/SummonedEnt.java`, `glyph/NatureGlyphs.java` (SummonEnt only), `client/SummonedEntRenderer.java`
- `feature/familiars/`: new `FamiliarSpell.java`; edited `FamiliarData`, `FamiliarNetwork`, `FamiliarService`, `FamiliarEntity`, `FamiliarCommands`, `client/FamiliarTab`
- `src/main/lang/forms_b/` and `src/main/lang/familiars/` (pt_br + en_us); `tools/gen/familiars.py` (same lang lines added; generator not re-run)
- new `tools/gen/summon_forge_icons.py` and `textures/gui/glyph/summon_guard.png`, `summon_hunt.png`, `summon_hold.png`

**New ids**
- Glyphs (arcane augments, tier 4, Circle 3, auto-taught like `conjure`): `summon_guard`, `summon_hunt`, `summon_hold`
- Key: `key.isekai_mahou.summon_order` (default O)
- Packets: `forms_b_summon_order`, `forms_b_summon_look`, `familiars_teach`; the `familiars_state` packet gained two fields
- Hints: `summon_forge`, `summon_forge_tip`, `familiar_spell`, `familiar_spell_cast`
- Entity persistentData key `isekai_mahou_forge` (server: order, gx/gy/gz, mark, spell, color, caster) and `isekai_mahou_forge_color` (client). The elemental modules read these by text.
- FamiliarData NBT: optional `spell`, `spell_ready`
- Attribute modifier ids: `summon_forge_size`, `summon_forge_hold`

**How to test** (after `/mahou admin login`; be in creative or run `/mahou glyph learn all`)
1. `/mahou summons test summon_golem ignite` — a golem appears; its punches ignite every 2 s.
2. `/mahou summons test summon_golem split expand ignite` — two bigger golems.
3. `/mahou summons test summon_salamander summon_guard arcane_damage` — guards the spot where it appeared.
4. `/mahou summons test spirit summon_hunt arcane_damage` while looking at a mob — the spirit chases only that mob.
5. `/mahou summons test summon_undine heal` — heals you when hurt.
6. Press O, or `/mahou summons guard|hunt|stay|follow`; sneak + right-click one summon; `/mahou summons info` lists orders.
7. `/mahou familiars awaken`, then `/mahou familiars spell teach self heal`; drop below 75% HP and it casts (first use after 10 s). `/mahou familiars spell` casts now; `/mahou familiars spell clear` forgets.
8. `/mahou familiars spell teach self ignite` must be refused.
9. B → Character → Familiar → "Magia de apoio" for the picker.

**Not done / not verified**
- Other summons (imp, demon, undead servant, bone warrior, ritual spirits) are not bodies; they are outside my area.
- The player's own summons are not blocked in `isekai_mahou:home_realm`, same as the existing summon glyphs.
- Sneak + right-click with an item in hand may also use the item, because the client cannot tell who owns the creature.
- A golem or sylph that reloads from an unloaded chunk can now rejoin as the owner's second one instead of crumbling.
- The familiar ignores the recipe's form and always applies the effects to the owner.
- Key O may clash with another mod's binding.

**PEDIDOS**
- integration (`docs/ARCHITECTURE.md`): add to the catalog the three order glyphs, the key, `/mahou summons`, the `isekai_mahou_forge` persistentData convention and the new packets.
- origins_b / ritual modules (optional): to become bodies they need one line in `SummonBodies` and, if they follow the owner outside goals, a read of `isekai_mahou_forge` (`order` 1 or 3 plus `gx/gy/gz`), as `light_shadow/entity/SummonUtil.forgePost` does.
- ui_grimoire (optional): show "body + order" in the summary of a `conjure` recipe.