# Wave 10 — `legends_j`: anime EFFECT & AUGMENT glyphs

**Direction change (owner, mid-task):** "Eu não quero magias lendárias, queria efeitos de animes e augments".
So `legends_j` ships **no** Legendary Spells, legend recipes or unlock feats. Every anime idea is an ordinary, composable
Grimório glyph (`legendary = false`, no class origin), usable with **any** form; mastery/mutations apply automatically.

## How players get them
Common glyphs with element + tier, so the existing systems pick them up with no extra code:
element path tier nodes (Arcane ones by level on the Arcane path), guild/city/reputation/journal scroll lotteries,
Wandering Mage, elemental guardians (by element), Arcane drops from class/overworld/G bosses, shrines.
Test: `/mahou glyphs grant legends_j [player]` (op) teaches all 13.

## Glyphs
| id | Name (pt_br) | Anime | Kind | Element | Tier | Circle | Cost | Effect (base numbers) |
|---|---|---|---|---|---|---|---|---|
| `boogie_woogie` | Boogie Woogie | Jujutsu Kaisen | Effect | Arcane | 2 | 2 | 10 | Clap and swap places with the creature hit. Self: the creature in your crosshair (24, Expand +) or else your newest projectile still in the world (the "thrown marker"). A swapped enemy is Stunned for 0.5 s. One swap per cast. Never: bosses, NPCs, mounts/riders, players outside your party unless they are PvP enemies; no swapping into or out of active towers/dungeons (warded areas). |
| `kazuma_steal` | Roubar! | Konosuba | Effect | Arcane | 2 | 1 | 8 | Steals 1 item per creature (max 3 per cast). Chance is 50% + 6%/Luck + 8%/Amplify, capped at 90% and halved on bosses. Results: 15% junk ("Striped Sock"), then the item in its hand, else one roll of its loot table (≤3 items). Each mob can only be robbed once (persistent tag). Bosses give gold nuggets only. Never works on players (joke message), NPCs or inside cities. |
| `flame_alchemy` | Alquimia de Fogo | FMA (Mustang) | Effect | Fire | 3 | 3 | 16 | Snap: a spark line runs to the hit point (Self: the crosshair point, ≤32) and explodes 0.15 s later. 9 Fire damage in radius 2.5 (Expand +, max 6 radius, max 12 targets) plus Burning 4 s. Each creature is hit at most once per cast. **No terrain damage, no block fire.** In rain or water: 30% damage, no fire ("useless in the rain"). |
| `alchemy_wall` | Parede de Alquimia | FMA (Ed) | Effect | Earth | 2 | 2 | 14 | On a block: a 5×3 temporary stone wall perpendicular to you (Expand +2 width, max 9) that lasts 8 s (Extend +). Self: the wall rises 3 blocks in front of you. On an enemy: stone spikes do 8 Earth damage, launch it up and Root it for 1 s (0.5 s on bosses). Uses earth_wind `StoneWorks`/`TempBlocks` (raised stone, auto-revert, `canEdit` + warded checks). Skipped in safe zones (cities). |
| `zoltraak` | Zoltraak | Frieren | Effect | Arcane | 2 | 1 | 8 | Cheap pure-magic ray: 6 Arcane damage, then it pierces 8 blocks behind the target and hits 1 more foe (+1 per Pierce) for 60%. Self: snipes the creature in your crosshair (32). |
| `geass` | Geass | Code Geass | Effect | Shadow | 4 | 4 | 20 | Needs eye contact (the target must have line of sight to you). On a weak mob (max HP ≤40, +25% per Amplify; no bosses or NPCs): "OBEY!" makes it fight for you for 12 s (Extend +), or while sneaking "STOP!" stuns it for 5 s. Strong mobs, bosses and PvP-enemy players are only Stunned for 1 s. Works once per creature and never kills. Obeying mobs only target monsters, never hurt or target players (owner, party or anyone), go idle in Peace Mode or cities, and are capped at 64 server-wide. |
| `chain_jail` | Corrente Prisão | HxH (Kurapika) | Effect | Arcane | 3 | 3 | 12 | Does 2 damage, Roots for 3 s (Extend +) and Silences for 1 s. "Emperor Time" against arthropods/undead: 6 damage, double root time, Silenced 3 s. Bosses are rooted 1 s at most. Self: binds the foe in your crosshair (20). The chain visuals pulse up to 4 times. |
| `holy_chain` | Corrente Sagrada | HxH | Effect (beneficial) | Light | 2 | 2 | 10 | Heals 5 and removes 1 bad effect (+1 per Amplify), with a golden coil around the target. |
| `dowsing_chain` | Corrente Rastreadora | HxH | Effect | Arcane | 2 | 1 | 6 | Self: foes within 24 (Expand +, max 48, max 20 foes) glow for 8 s (Extend +). The action bar shows the count plus compass direction and distance of the nearest one. On an enemy: it glows for 15 s and the action bar shows its HP, armor and effect count. |
| `tengai_shinsei` | Tengai Shinsei | Naruto (Madara) | Effect | Earth | 4 | 6 | 40 | A giant rock entity falls from the sky over 2.5 s onto the hit point (Self: crosshair, ≤64), with a ground warning circle. Impact: 30 Earth damage in radius 6 (Expand +, 4–12; max 30 targets; bosses ×0.7), knockback and screen shake. Small crater (≤r4) via `Destruction.crater`, only if spell destruction is allowed (gamerule/config/cities/towers/dungeons respected). One rock per cast. Refuses to fall in cities. Amplify makes the rock bigger. |
| `black_flash` | Kokusen (Raio Negro) | Jujutsu Kaisen | Augment | Shadow | 3 | 3 | 12 | Rhythm crit. After a cast, a private "ting" sounds 0.4–1.2 s after the cooldown ends. Casting the same spell within 0.25 s of it (0.5 s in the Zone) triggers BLACK FLASH: the next 6 hits within 4 s deal ×2.5 (max +60 per hit) with black/red sparks, and you enter the Zone for 10 s (wider window, +10% damage). Pressing early is a normal cast and the ting reschedules. Players only. |
| `starburst_stream` | Starburst Stream | SAO | Augment | Arcane | 4 | 4 | 18 | Each landed hit becomes 16 strikes. 15 extra blue/white slashes follow over 1.5 s (14×5.5% + a final 18% of the hit = +95%) via direct `hurt`, so the extra slashes don't re-fire spell-damage events. The caster gets Slowness IV for 1.7 s. Max 4 flurries per caster at once, 1 per target. |
| `excalibur_light` | Excalibur | Fate | Augment | Light | 4 | 5 | 22 | Adds +0.5 s cast time, Expand +2 and Pierce +3, and +15% damage (+40% vs undead). On beam forms: a huge golden wave, flash, light pillar and screen shake on top of the beam. |

Plus the entity `lj_meteor_rock` (noSave, no physics; rendered with vanilla blocks) and the junk item name
`item.isekai_mahou.lj_striped_sock` (a renamed String).

## Duplicates checked (all legends_*/class/tower lang + Java)
- **Excalibur:** the Knight already has the class G-form `knight_excalibur`. Ours is a generic Light *augment*, so it doesn't clash.
- **Starburst Stream:** the tower Twin Blades item already has "Starlight Torrent" and the gunslinger has "Stellar Burst". Ours is a generic multi-hit *augment*.
- **Kurapika chains:** the Ranger origin glyph `ranger_conjured_chain` does prison and heal, but it is class-locked. The three common glyphs here are distinct; Chain Jail adds the Emperor Time bonus.
- **Zoltraak:** legends_b's `killing_ray` is a pre-built legend recipe made from core glyphs. This is a real effect glyph.
- **Frieren's defensive hexagon:** skipped, because it is identical to legends_f `general_defensive_magic`.
- **Steal:** the Rogue class has a "Light Fingers" mechanic. Ours is a loot-table/luck glyph with no player theft.
- **Tengai Shinsei vs `meteor`/`meteor_shower`:** a single slow giant rock with a warning circle and no fire. It is a different feel.

## Files
- `src/main/java/com/renanpontes/isekaimahou/feature/legends_j/`
  - `LegendsJModule.java`, `LjGlyphs.java`, `LjAugments.java`, `GeassControl.java`, `LjRegistry.java`, `LjUtil.java`, `LjFx.java`
  - `glyph/` contains `TricksterEffects`, `AlchemyEffects`, `ZoltraakEffect`, `GeassEffect`, `ChainEffects` and `TengaiShinseiEffect`
  - `entity/MeteorRockEntity.java`
  - `client/LegendsJClient.java`, `client/MeteorRockRenderer.java`
- Registration (1 line each): `module/Modules.java`, `client/ClientModules.java`
- `src/main/lang/legends_j/{pt_br,en_us}.json` (62 keys each; no duplicates across the tree)
- `tools/gen/legends_j.py` plus 13 icons in `assets/isekai_mahou/textures/gui/glyph/<id>.png`. The icons are generated with `icons.py` specs, for this module's ids only.
- Untouched: `glyph/GlyphMastery`, `MasteryMutations`, `SpellParser`, `SpellCaster`, earth_wind (only its public `StoneWorks`/`EwUtil` were used).

## Test commands
- Compile: `bash tools/check-tree.sh legends_j`. When this ran, the whole-tree check failed **only** because of other agents' half-written `legends_h`/`legends_i`/`settlements` files. Compiling the 16 `legends_j` sources against the source path passed (`COMPILOU OK`).
- In game: `/mahou glyphs grant legends_j`, then build spells in the Grimório:
  - `self + boogie_woogie`: throw a snowball, then cast it.
  - `projectile + kazuma_steal`
  - `self + flame_alchemy`: try it in rain too.
  - `projectile + alchemy_wall`
  - `beam + zoltraak + pierce`
  - `self + geass`: cast it while sneaking too.
  - `self + chain_jail` on a spider.
  - `projectile + holy_chain` on a party member.
  - `self + dowsing_chain`
  - `self + tengai_shinsei`: run it with `/gamerule mahouSpellDestruction` set both on and off.
  - `projectile + arcane_damage + black_flash`: recast on the ting.
  - `projectile + arcane_damage + starburst_stream`
  - `beam + excalibur_light + arcane_damage`

## Not verified (no game run; `./gradlew build` not executed by instruction)
- Visual readability and particle density in game. Meteor renderer scale, rotation and lerp smoothness.
- Black Flash timing feel (is 5 ticks too tight?). Whether `castTicks` from charge/efficient augments shifts the window as intended.
- Geass target hand-off against every vanilla AI goal (e.g. endermen, piglins). Creeper explosions are cancelled against players and party by the damage event.
- How the auto-guessed icons look (both checked icons came out with a lightning pictogram), path auto-teach placement, and lottery frequency.
- Starburst flurry vs i-frames on modded bosses with custom `hurt` handling.
